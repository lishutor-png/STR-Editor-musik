package com.example.audio

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

object AudioProcessor {

    /**
     * Trims PCM audio data between [startMs] and [endMs].
     */
    fun trim(pcm: PcmData, startMs: Long, endMs: Long): PcmData {
        if (pcm.samples.isEmpty()) return pcm

        val sampleRate = pcm.sampleRate
        val channels = pcm.channelCount
        val totalFrames = pcm.samples.size / channels

        val safeStartMs = startMs.coerceIn(0L, pcm.durationMs)
        val safeEndMs = endMs.coerceIn(safeStartMs, pcm.durationMs)

        val startFrame = ((safeStartMs * sampleRate) / 1000).toInt().coerceIn(0, totalFrames)
        val endFrame = ((safeEndMs * sampleRate) / 1000).toInt().coerceIn(startFrame, totalFrames)

        val startIndex = startFrame * channels
        val endIndex = endFrame * channels

        if (startIndex >= endIndex) {
            return PcmData(sampleRate, channels, ShortArray(0))
        }

        val trimmedSamples = pcm.samples.copyOfRange(startIndex, endIndex)
        return PcmData(sampleRate, channels, trimmedSamples)
    }

    /**
     * Applies volume gain (0.0f = mute, 1.0f = normal, up to 3.0f = boost)
     * using a soft-clipping knee to prevent harsh digital clipping.
     */
    fun applyGain(pcm: PcmData, gain: Float): PcmData {
        if (pcm.samples.isEmpty() || gain == 1.0f) return pcm

        val output = ShortArray(pcm.samples.size)
        val threshold = 28000f // soft clip threshold
        val maxAmp = 32767f

        for (i in pcm.samples.indices) {
            val sample = pcm.samples[i].toFloat() * gain
            val clamped = when {
                sample > threshold -> {
                    val excess = sample - threshold
                    threshold + (maxAmp - threshold) * (excess / (excess + 10000f))
                }
                sample < -threshold -> {
                    val excess = -sample - threshold
                    -(threshold + (maxAmp - threshold) * (excess / (excess + 10000f)))
                }
                else -> sample
            }
            output[i] = clamped.coerceIn(-32768f, 32767f).toInt().toShort()
        }

        return PcmData(pcm.sampleRate, pcm.channelCount, output)
    }

    /**
     * Applies fade-in and fade-out effects on PCM data.
     * Uses a smooth quarter-sine curve for natural audio fading.
     */
    fun applyFades(pcm: PcmData, fadeInMs: Long, fadeOutMs: Long): PcmData {
        if (pcm.samples.isEmpty() || (fadeInMs <= 0L && fadeOutMs <= 0L)) return pcm

        val sampleRate = pcm.sampleRate
        val channels = pcm.channelCount
        val totalFrames = pcm.samples.size / channels
        val output = pcm.samples.copyOf()

        val fadeInFrames = if (fadeInMs > 0) {
            ((fadeInMs * sampleRate) / 1000).toInt().coerceAtMost(totalFrames)
        } else 0

        val fadeOutFrames = if (fadeOutMs > 0) {
            ((fadeOutMs * sampleRate) / 1000).toInt().coerceAtMost(totalFrames)
        } else 0

        // Apply Fade In
        if (fadeInFrames > 0) {
            for (frame in 0 until fadeInFrames) {
                val progress = frame.toFloat() / fadeInFrames
                val factor = sin(progress * (PI.toFloat() / 2f)) // smooth sine in
                for (ch in 0 until channels) {
                    val idx = frame * channels + ch
                    output[idx] = (output[idx] * factor).toInt().toShort()
                }
            }
        }

        // Apply Fade Out
        if (fadeOutFrames > 0) {
            val startOutFrame = max(0, totalFrames - fadeOutFrames)
            for (frame in startOutFrame until totalFrames) {
                val progress = (totalFrames - 1 - frame).toFloat() / fadeOutFrames
                val factor = sin(progress * (PI.toFloat() / 2f)) // smooth sine out
                for (ch in 0 until channels) {
                    val idx = frame * channels + ch
                    output[idx] = (output[idx] * factor).toInt().toShort()
                }
            }
        }

        return PcmData(sampleRate, channels, output)
    }

    /**
     * Combines multiple PCM tracks sequentially into one track with optional gap (silence).
     */
    fun mergeTracks(tracks: List<PcmData>, gapMs: Long = 0L): PcmData {
        if (tracks.isEmpty()) return PcmData()
        if (tracks.size == 1 && gapMs == 0L) return tracks[0]

        // Target standard: 44100Hz stereo
        val targetSampleRate = 44100
        val targetChannels = 2

        // Standardize all tracks to 44100Hz stereo
        val standardized = tracks.map { standardizePcm(it, targetSampleRate, targetChannels) }

        val gapSamples = if (gapMs > 0) {
            ((gapMs * targetSampleRate) / 1000).toInt() * targetChannels
        } else 0

        val totalSampleSize = standardized.sumOf { it.samples.size } + (standardized.size - 1).coerceAtLeast(0) * gapSamples
        val merged = ShortArray(totalSampleSize)

        var currentOffset = 0
        standardized.forEachIndexed { index, track ->
            System.arraycopy(track.samples, 0, merged, currentOffset, track.samples.size)
            currentOffset += track.samples.size
            if (index < standardized.size - 1 && gapSamples > 0) {
                // Gap is already 0 initialized (silence)
                currentOffset += gapSamples
            }
        }

        return PcmData(targetSampleRate, targetChannels, merged)
    }

    /**
     * Standardizes PCM data to target sample rate and channel count.
     */
    fun standardizePcm(pcm: PcmData, targetRate: Int = 44100, targetChannels: Int = 2): PcmData {
        if (pcm.samples.isEmpty()) return PcmData(targetRate, targetChannels, ShortArray(0))
        if (pcm.sampleRate == targetRate && pcm.channelCount == targetChannels) return pcm

        // First adjust channels if needed
        val channelAdjustedSamples: ShortArray = if (pcm.channelCount == 1 && targetChannels == 2) {
            // Mono to Stereo: duplicate each sample
            val stereo = ShortArray(pcm.samples.size * 2)
            for (i in pcm.samples.indices) {
                stereo[i * 2] = pcm.samples[i]
                stereo[i * 2 + 1] = pcm.samples[i]
            }
            stereo
        } else if (pcm.channelCount == 2 && targetChannels == 1) {
            // Stereo to Mono: average left and right
            val mono = ShortArray(pcm.samples.size / 2)
            for (i in mono.indices) {
                val l = pcm.samples[i * 2].toInt()
                val r = pcm.samples[i * 2 + 1].toInt()
                mono[i] = ((l + r) / 2).toShort()
            }
            mono
        } else {
            pcm.samples
        }

        // Then adjust sample rate if needed
        if (pcm.sampleRate == targetRate) {
            return PcmData(targetRate, targetChannels, channelAdjustedSamples)
        }

        // Linear interpolation resampling
        val sourceFrames = channelAdjustedSamples.size / targetChannels
        val durationSec = sourceFrames.toDouble() / pcm.sampleRate
        val targetFrames = (durationSec * targetRate).toInt()
        val resampled = ShortArray(targetFrames * targetChannels)

        val ratio = pcm.sampleRate.toDouble() / targetRate.toDouble()

        for (frame in 0 until targetFrames) {
            val srcPos = frame * ratio
            val srcFrame0 = srcPos.toInt().coerceIn(0, sourceFrames - 1)
            val srcFrame1 = (srcFrame0 + 1).coerceIn(0, sourceFrames - 1)
            val frac = (srcPos - srcFrame0).toFloat()

            for (ch in 0 until targetChannels) {
                val s0 = channelAdjustedSamples[srcFrame0 * targetChannels + ch].toFloat()
                val s1 = channelAdjustedSamples[srcFrame1 * targetChannels + ch].toFloat()
                val interpolated = (s0 * (1f - frac) + s1 * frac).toInt().toShort()
                resampled[frame * targetChannels + ch] = interpolated
            }
        }

        return PcmData(targetRate, targetChannels, resampled)
    }

    /**
     * Extracts normalized amplitude points (0.0f .. 1.0f) for waveform visualization.
     */
    fun extractWaveform(pcm: PcmData, barCount: Int = 120): FloatArray {
        if (pcm.samples.isEmpty()) return FloatArray(barCount) { 0.1f }

        val channels = pcm.channelCount
        val totalFrames = pcm.samples.size / channels
        if (totalFrames <= 0) return FloatArray(barCount) { 0.1f }

        val framesPerBar = max(1, totalFrames / barCount)
        val waveform = FloatArray(barCount)
        var maxObserved = 1f

        for (i in 0 until barCount) {
            val startFrame = i * framesPerBar
            val endFrame = min(totalFrames, (i + 1) * framesPerBar)
            var sumPeak = 0f
            var count = 0

            for (f in startFrame until endFrame) {
                for (ch in 0 until channels) {
                    val sampleVal = abs(pcm.samples[f * channels + ch].toFloat())
                    if (sampleVal > sumPeak) {
                        sumPeak = sampleVal
                    }
                    count++
                }
            }

            waveform[i] = sumPeak
            if (sumPeak > maxObserved) maxObserved = sumPeak
        }

        // Normalize
        for (i in 0 until barCount) {
            waveform[i] = (waveform[i] / maxObserved).coerceIn(0.05f, 1.0f)
        }

        return waveform
    }

    /**
     * Applies a 3-band Equalizer (Bass, Mid, Treble) to the entire PCM data.
     */
    fun applyEqualizer(pcm: PcmData, settings: EqualizerSettings): PcmData {
        if (pcm.samples.isEmpty()) return pcm
        if (settings.bassDb == 0f && settings.midDb == 0f && settings.trebleDb == 0f) {
            return pcm
        }

        val gBass = 10.0f.pow(settings.bassDb / 20f)
        val gMid = 10.0f.pow(settings.midDb / 20f)
        val gTreble = 10.0f.pow(settings.trebleDb / 20f)

        val sampleRate = pcm.sampleRate.toFloat()
        val channels = pcm.channelCount

        // Crossover filter coefficients
        val fLow = 320f
        val fHigh = 3600f

        val alphaLow = (2 * PI.toFloat() * fLow) / (sampleRate + 2 * PI.toFloat() * fLow)
        val alphaHigh = sampleRate / (sampleRate + 2 * PI.toFloat() * fHigh)

        val output = ShortArray(pcm.samples.size)

        // Per-channel filter states
        val lowStates = FloatArray(channels)
        val highStates = FloatArray(channels)
        val prevInputs = FloatArray(channels)

        val threshold = 28000f
        val maxAmp = 32767f

        for (i in pcm.samples.indices) {
            val ch = i % channels
            val input = pcm.samples[i].toFloat()

            // 1. Low-pass filter (Bass)
            lowStates[ch] += alphaLow * (input - lowStates[ch])
            val low = lowStates[ch]

            // 2. High-pass filter (Treble)
            val high = alphaHigh * (highStates[ch] + input - prevInputs[ch])
            highStates[ch] = high
            prevInputs[ch] = input

            // 3. Band-pass (Mid)
            val mid = input - low - high

            // 4. Combined filtered sample
            val processed = (low * gBass) + (mid * gMid) + (high * gTreble)

            // Soft-clipping knee
            val clamped = when {
                processed > threshold -> {
                    val excess = processed - threshold
                    threshold + (maxAmp - threshold) * (excess / (excess + 10000f))
                }
                processed < -threshold -> {
                    val excess = -processed - threshold
                    -(threshold + (maxAmp - threshold) * (excess / (excess + 10000f)))
                }
                else -> processed
            }

            output[i] = clamped.coerceIn(-32768f, 32767f).toInt().toShort()
        }

        return PcmData(pcm.sampleRate, pcm.channelCount, output)
    }
}

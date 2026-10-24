package com.example.audio

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

object AudioProcessor {

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

        val trimmed = pcm.samples.copyOfRange(startIndex, endIndex)
        return PcmData(sampleRate, channels, trimmed)
    }

    fun applyGain(pcm: PcmData, gain: Float): PcmData {
        if (pcm.samples.isEmpty() || gain == 1.0f) return pcm

        val output = ShortArray(pcm.samples.size)
        for (i in pcm.samples.indices) {
            val sample = pcm.samples[i] * gain
            val clamped = when {
                sample > 28000f -> {
                    val excess = sample - 28000f
                    28000f + (32767f - 28000f) * (excess / (10000f + excess))
                }
                sample < -28000f -> {
                    val excess = -sample - 28000f
                    -(28000f + (32767f - 28000f) * (excess / (10000f + excess)))
                }
                else -> sample
            }
            output[i] = clamped.coerceIn(-32768f, 32767f).toInt().toShort()
        }
        return PcmData(pcm.sampleRate, pcm.channelCount, output)
    }

    fun applyFades(pcm: PcmData, fadeInMs: Long, fadeOutMs: Long): PcmData {
        if (pcm.samples.isEmpty() || (fadeInMs <= 0L && fadeOutMs <= 0L)) return pcm

        val sampleRate = pcm.sampleRate
        val channels = pcm.channelCount
        val totalFrames = pcm.samples.size / channels

        val fadeInFrames = min(((fadeInMs * sampleRate) / 1000).toInt(), totalFrames)
        val fadeOutFrames = min(((fadeOutMs * sampleRate) / 1000).toInt(), totalFrames)

        val output = pcm.samples.clone()

        if (fadeInFrames > 0) {
            for (f in 0 until fadeInFrames) {
                val factor = (f.toFloat() / fadeInFrames.toFloat())
                for (c in 0 until channels) {
                    val idx = f * channels + c
                    output[idx] = (output[idx] * factor).toInt().toShort()
                }
            }
        }

        if (fadeOutFrames > 0) {
            val startFadeOut = max(0, totalFrames - fadeOutFrames)
            for (f in startFadeOut until totalFrames) {
                val factor = ((totalFrames - f).toFloat() / fadeOutFrames.toFloat())
                for (c in 0 until channels) {
                    val idx = f * channels + c
                    output[idx] = (output[idx] * factor).toInt().toShort()
                }
            }
        }

        return PcmData(sampleRate, channels, output)
    }

    fun applyEqualizer(pcm: PcmData, settings: EqualizerSettings): PcmData {
        if (pcm.samples.isEmpty()) return pcm
        if (settings.bassGain == 0f && settings.midGain == 0f && settings.trebleGain == 0f) {
            return pcm
        }

        val bassLinear = 10.0.pow(settings.bassGain / 20.0).toFloat()
        val midLinear = 10.0.pow(settings.midGain / 20.0).toFloat()
        val trebleLinear = 10.0.pow(settings.trebleGain / 20.0).toFloat()

        val output = ShortArray(pcm.samples.size)
        val channels = pcm.channelCount
        val prevSample = FloatArray(channels)
        val prevMid = FloatArray(channels)

        for (i in pcm.samples.indices) {
            val c = i % channels
            val x = pcm.samples[i].toFloat()

            // 1st order RC lowpass filter ~250Hz for bass
            val low = prevSample[c] + 0.05f * (x - prevSample[c])
            prevSample[c] = low

            // High pass approximation
            val high = x - low

            // Mid band
            val mid = high - (prevMid[c] + 0.15f * (high - prevMid[c]))
            prevMid[c] = high

            val processed = (low * bassLinear) + (mid * midLinear) + ((high - mid) * trebleLinear)
            output[i] = processed.coerceIn(-32768f, 32767f).toInt().toShort()
        }

        return PcmData(pcm.sampleRate, pcm.channelCount, output)
    }

    fun extractWaveform(pcm: PcmData, barCount: Int = 100): FloatArray {
        if (pcm.samples.isEmpty() || barCount <= 0) return FloatArray(barCount) { 0.1f }

        val bars = FloatArray(barCount)
        val window = pcm.samples.size / barCount

        var maxAmp = 1f
        for (i in 0 until barCount) {
            val start = i * window
            val end = min(start + window, pcm.samples.size)
            var sum = 0L
            var peak = 0
            for (j in start until end) {
                val absVal = abs(pcm.samples[j].toInt())
                sum += absVal
                if (absVal > peak) peak = absVal
            }
            val count = max(1, end - start)
            val avg = sum.toFloat() / count.toFloat()
            val amp = (avg * 0.4f) + (peak * 0.6f)
            bars[i] = amp
            if (amp > maxAmp) maxAmp = amp
        }

        for (i in bars.indices) {
            bars[i] = (bars[i] / maxAmp).coerceIn(0.08f, 1.0f)
        }
        return bars
    }

    fun mergeTracks(tracks: List<PcmData>, gapMs: Long = 500L): PcmData {
        if (tracks.isEmpty()) return PcmData(44100, 2, ShortArray(0))
        if (tracks.size == 1) return tracks[0]

        val targetRate = 44100
        val targetChannels = 2

        val standardized = tracks.map { standardizePcm(it, targetRate, targetChannels) }
        val gapSamples = ((gapMs * targetRate) / 1000).toInt() * targetChannels

        var totalLen = 0
        for ((idx, t) in standardized.withIndex()) {
            totalLen += t.samples.size
            if (idx < standardized.size - 1) totalLen += gapSamples
        }

        val merged = ShortArray(totalLen)
        var offset = 0
        for ((idx, t) in standardized.withIndex()) {
            System.arraycopy(t.samples, 0, merged, offset, t.samples.size)
            offset += t.samples.size
            if (idx < standardized.size - 1) {
                offset += gapSamples
            }
        }

        return PcmData(targetRate, targetChannels, merged)
    }

    fun standardizePcm(pcm: PcmData, targetRate: Int = 44100, targetChannels: Int = 2): PcmData {
        var result = pcm
        if (result.channelCount == 1 && targetChannels == 2) {
            val stereo = ShortArray(result.samples.size * 2)
            for (i in result.samples.indices) {
                stereo[i * 2] = result.samples[i]
                stereo[i * 2 + 1] = result.samples[i]
            }
            result = PcmData(result.sampleRate, 2, stereo)
        } else if (result.channelCount == 2 && targetChannels == 1) {
            val mono = ShortArray(result.samples.size / 2)
            for (i in mono.indices) {
                val left = result.samples[i * 2].toInt()
                val right = result.samples[i * 2 + 1].toInt()
                mono[i] = ((left + right) / 2).toShort()
            }
            result = PcmData(result.sampleRate, 1, mono)
        }
        return result
    }
}

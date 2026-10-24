package com.example.audio

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

object SampleAudioGenerator {

    fun deleteSampleTracks(context: Context) {
        try {
            val dir = File(context.cacheDir, "sample_audio")
            if (dir.exists()) {
                dir.deleteRecursively()
            }
        } catch (e: Exception) {
            // ignore
        }
    }

    suspend fun getOrCreateSampleTracks(context: Context): List<AudioTrackInfo> = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "sample_audio")
        if (!dir.exists()) {
            dir.mkdirs()
        }

        val track1 = File(dir, "Sunset_Synthwave_Demo.wav")
        if (!track1.exists()) {
            val pcm1 = generateSynthwave()
            AudioEncoder.encodeWav(pcm1, track1)
        }

        val track2 = File(dir, "Acoustic_Piano_Demo.wav")
        if (!track2.exists()) {
            val pcm2 = generateAcousticPiano()
            AudioEncoder.encodeWav(pcm2, track2)
        }

        val track3 = File(dir, "Lofi_Beat_Master.wav")
        if (!track3.exists()) {
            val pcm3 = generateLofiBeat()
            AudioEncoder.encodeWav(pcm3, track3)
        }

        listOf(
            AudioDecoder.extractMetadata(context, file = track1, fallbackTitle = "Sunset Synthwave Demo"),
            AudioDecoder.extractMetadata(context, file = track2, fallbackTitle = "Acoustic Piano Demo"),
            AudioDecoder.extractMetadata(context, file = track3, fallbackTitle = "Lofi Beat Master")
        )
    }

    private fun generateSynthwave(durationSec: Int = 16, sampleRate: Int = 44100): PcmData {
        val totalFrames = durationSec * sampleRate
        val samples = ShortArray(totalFrames * 2)

        val bpm = 120.0
        val beatDurationSec = 60.0 / bpm
        val sixteenthSec = beatDurationSec / 4.0

        val notes = doubleArrayOf(
            220.0, 261.63, 329.63, 440.0,
            174.61, 220.0, 261.63, 349.23,
            130.81, 164.81, 196.0, 261.63,
            196.0, 246.94, 293.66, 392.0
        )

        for (f in 0 until totalFrames) {
            val t = f.toDouble() / sampleRate.toDouble()
            val sixteenthIdx = (t / sixteenthSec).toInt()
            val noteFreq = notes[sixteenthIdx % notes.size]

            // Synth lead (sawtooth-like via harmonics)
            val osc1 = sin(2.0 * PI * noteFreq * t) * 0.4
            val osc2 = sin(2.0 * PI * (noteFreq * 2.0) * t) * 0.2
            val osc3 = sin(2.0 * PI * (noteFreq * 0.5) * t) * 0.3

            // Bassline (sub 55Hz - 110Hz)
            val bassFreq = noteFreq / 2.0
            val bass = sin(2.0 * PI * bassFreq * t) * 0.4

            // Kick drum on beats (t % beatDurationSec)
            val beatPhase = (t % beatDurationSec) / beatDurationSec
            val kick = if (beatPhase < 0.15) {
                sin(2.0 * PI * (120.0 - beatPhase * 400.0) * beatPhase) * exp(-beatPhase * 25.0) * 0.7
            } else 0.0

            // Hihat on offbeats
            val hihatPhase = ((t + beatDurationSec / 2.0) % beatDurationSec) / beatDurationSec
            val hihat = if (hihatPhase < 0.05) {
                (Math.random() * 2.0 - 1.0) * exp(-hihatPhase * 80.0) * 0.2
            } else 0.0

            val left = (osc1 + osc2 + bass * 0.7 + kick + hihat * 0.6).coerceIn(-1.0, 1.0)
            val right = (osc1 + osc3 + bass * 0.7 + kick + hihat * 0.8).coerceIn(-1.0, 1.0)

            samples[f * 2] = (left * 28000.0).toInt().toShort()
            samples[f * 2 + 1] = (right * 28000.0).toInt().toShort()
        }

        return PcmData(sampleRate, 2, samples)
    }

    private fun generateAcousticPiano(durationSec: Int = 14, sampleRate: Int = 44100): PcmData {
        val totalFrames = durationSec * sampleRate
        val samples = ShortArray(totalFrames * 2)

        val chords = listOf(
            doubleArrayOf(261.63, 329.63, 392.0, 523.25), // C maj
            doubleArrayOf(220.0, 261.63, 329.63, 440.0),  // Am
            doubleArrayOf(174.61, 220.0, 261.63, 349.23), // F maj
            doubleArrayOf(196.0, 246.94, 293.66, 392.0)   // G maj
        )

        val chordDur = 3.5

        for (f in 0 until totalFrames) {
            val t = f.toDouble() / sampleRate.toDouble()
            val chordIdx = (t / chordDur).toInt() % chords.size
            val chord = chords[chordIdx]
            val chordTime = t % chordDur

            var sum = 0.0
            for (freq in chord) {
                val decay = exp(-chordTime * 1.8)
                sum += (sin(2.0 * PI * freq * t) + 0.3 * sin(2.0 * PI * freq * 2.0 * t)) * decay * 0.2
            }

            val clamped = sum.coerceIn(-1.0, 1.0)
            samples[f * 2] = (clamped * 26000.0).toInt().toShort()
            samples[f * 2 + 1] = (clamped * 26000.0).toInt().toShort()
        }

        return PcmData(sampleRate, 2, samples)
    }

    private fun generateLofiBeat(durationSec: Int = 18, sampleRate: Int = 44100): PcmData {
        val totalFrames = durationSec * sampleRate
        val samples = ShortArray(totalFrames * 2)

        val bpm = 85.0
        val beatSec = 60.0 / bpm

        val eMinorFrequencies = doubleArrayOf(164.81, 196.0, 246.94, 329.63)

        for (f in 0 until totalFrames) {
            val t = f.toDouble() / sampleRate.toDouble()
            val beatPhase = (t % beatSec) / beatSec

            // Warm Rhodes piano tone
            var epiano = 0.0
            for (freq in eMinorFrequencies) {
                val decay = exp(-beatPhase * 2.0)
                epiano += sin(2.0 * PI * freq * t) * decay * 0.15
            }

            // Snare / rimshot on beat 2 & 4
            val beatNumber = (t / beatSec).toInt() % 4
            val isSnareBeat = beatNumber == 1 || beatNumber == 3
            val snare = if (isSnareBeat && beatPhase < 0.1) {
                (Math.random() * 2.0 - 1.0) * exp(-beatPhase * 35.0) * 0.35
            } else 0.0

            // Vinyl noise
            val vinyl = (Math.random() * 2.0 - 1.0) * 0.015

            val out = (epiano + snare + vinyl).coerceIn(-1.0, 1.0)
            samples[f * 2] = (out * 24000.0).toInt().toShort()
            samples[f * 2 + 1] = (out * 24000.0).toInt().toShort()
        }

        return PcmData(sampleRate, 2, samples)
    }
}

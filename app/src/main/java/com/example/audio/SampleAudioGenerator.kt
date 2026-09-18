package com.example.audio

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.sin

object SampleAudioGenerator {

    suspend fun getOrCreateSampleTracks(context: Context): List<AudioTrackInfo> = withContext(Dispatchers.IO) {
        val sampleDir = File(context.cacheDir, "sample_audio")
        if (!sampleDir.exists()) sampleDir.mkdirs()

        val track1File = File(sampleDir, "synthwave_groove.wav")
        val track2File = File(sampleDir, "acoustic_piano.wav")
        val track3File = File(sampleDir, "lofi_ambient_beat.wav")

        if (!track1File.exists()) {
            val pcm1 = generateSynthwave(durationSec = 16)
            AudioEncoder.encodeWav(pcm1, track1File)
        }

        if (!track2File.exists()) {
            val pcm2 = generateAcousticPiano(durationSec = 16)
            AudioEncoder.encodeWav(pcm2, track2File)
        }

        if (!track3File.exists()) {
            val pcm3 = generateLofiBeat(durationSec = 16)
            AudioEncoder.encodeWav(pcm3, track3File)
        }

        listOf(
            AudioTrackInfo(
                id = track1File.absolutePath,
                title = "Synthwave Groove",
                durationMs = 16000L,
                format = "WAV",
                sizeBytes = track1File.length(),
                sampleRate = 44100,
                channels = 2,
                file = track1File,
                isSample = true
            ),
            AudioTrackInfo(
                id = track2File.absolutePath,
                title = "Acoustic Piano Melody",
                durationMs = 16000L,
                format = "WAV",
                sizeBytes = track2File.length(),
                sampleRate = 44100,
                channels = 2,
                file = track2File,
                isSample = true
            ),
            AudioTrackInfo(
                id = track3File.absolutePath,
                title = "Lo-Fi Ambient Beat",
                durationMs = 16000L,
                format = "WAV",
                sizeBytes = track3File.length(),
                sampleRate = 44100,
                channels = 2,
                file = track3File,
                isSample = true
            )
        )
    }

    private fun generateSynthwave(durationSec: Int = 16, sampleRate: Int = 44100): PcmData {
        val totalFrames = durationSec * sampleRate
        val samples = ShortArray(totalFrames * 2)

        val bpm = 120.0
        val beatDurationSec = 60.0 / bpm
        val sixteenthSec = beatDurationSec / 4.0

        // Chord progression: Am, F, C, G
        val notes = doubleArrayOf(
            220.0, 261.63, 329.63, 440.0, // Am
            174.61, 220.0, 261.63, 349.23, // F
            130.81, 164.81, 196.0, 261.63, // C
            196.0, 246.94, 293.66, 392.0   // G
        )

        for (frame in 0 until totalFrames) {
            val t = frame.toDouble() / sampleRate
            val currentBeat = t / beatDurationSec
            val chordIdx = ((currentBeat / 4).toInt() % 4) * 4
            val sixteenthIdx = (t / sixteenthSec).toInt() % 4
            val currentFreq = notes[chordIdx + sixteenthIdx]

            // Arpeggio synth (saw-like harmonics)
            val noteTime = t % sixteenthSec
            val envelope = exp(-noteTime * 6.0)
            val synth = (sin(2 * PI * currentFreq * t) + 0.5 * sin(4 * PI * currentFreq * t)) * envelope

            // Bassline
            val bassFreq = notes[chordIdx] / 2.0
            val bassEnv = exp(-(t % (beatDurationSec / 2.0)) * 4.0)
            val bass = sin(2 * PI * bassFreq * t) * bassEnv * 0.8

            // Kick on each beat
            val kickTime = t % beatDurationSec
            val kickFreq = 120.0 * exp(-kickTime * 25.0) + 40.0
            val kick = sin(2 * PI * kickFreq * kickTime) * exp(-kickTime * 12.0) * 0.9

            // Snare on beats 2 and 4
            val snareTime = (t - beatDurationSec) % (beatDurationSec * 2.0)
            val snare = if (snareTime in 0.0..0.25) {
                val noise = (Math.random() * 2.0 - 1.0)
                noise * exp(-snareTime * 18.0) * 0.6
            } else 0.0

            val left = (synth * 0.45 + bass * 0.35 + kick * 0.4 + snare * 0.35).coerceIn(-1.0, 1.0)
            val right = (synth * 0.5 + bass * 0.35 + kick * 0.4 + snare * 0.35).coerceIn(-1.0, 1.0)

            samples[frame * 2] = (left * 28000).toInt().toShort()
            samples[frame * 2 + 1] = (right * 28000).toInt().toShort()
        }

        return PcmData(sampleRate, 2, samples)
    }

    private fun generateAcousticPiano(durationSec: Int = 16, sampleRate: Int = 44100): PcmData {
        val totalFrames = durationSec * sampleRate
        val samples = ShortArray(totalFrames * 2)

        val chordDur = 4.0 // 4 seconds per chord
        // Cmaj7, Am7, Fmaj7, G7
        val chordFreqs = listOf(
            listOf(261.63, 329.63, 392.0, 493.88),
            listOf(220.0, 261.63, 329.63, 392.0),
            listOf(174.61, 220.0, 261.63, 329.63),
            listOf(196.0, 246.94, 293.66, 349.23)
        )

        for (frame in 0 until totalFrames) {
            val t = frame.toDouble() / sampleRate
            val chordIndex = ((t / chordDur).toInt()) % chordFreqs.size
            val freqs = chordFreqs[chordIndex]
            val chordTime = t % chordDur

            var soundL = 0.0
            var soundR = 0.0

            freqs.forEachIndexed { idx, f ->
                val arpegTime = max(0.0, chordTime - idx * 0.35)
                val decay = exp(-arpegTime * 1.5)
                // Piano harmonic series
                val harmonic1 = sin(2 * PI * f * arpegTime)
                val harmonic2 = 0.4 * sin(4 * PI * f * arpegTime)
                val harmonic3 = 0.15 * sin(6 * PI * f * arpegTime)
                val noteVal = (harmonic1 + harmonic2 + harmonic3) * decay * 0.3

                // Stereo spread
                val panL = 1.0 - (idx * 0.2)
                val panR = 0.4 + (idx * 0.2)
                soundL += noteVal * panL
                soundR += noteVal * panR
            }

            samples[frame * 2] = (soundL.coerceIn(-1.0, 1.0) * 26000).toInt().toShort()
            samples[frame * 2 + 1] = (soundR.coerceIn(-1.0, 1.0) * 26000).toInt().toShort()
        }

        return PcmData(sampleRate, 2, samples)
    }

    private fun generateLofiBeat(durationSec: Int = 16, sampleRate: Int = 44100): PcmData {
        val totalFrames = durationSec * sampleRate
        val samples = ShortArray(totalFrames * 2)

        val bpm = 80.0
        val beatSec = 60.0 / bpm

        // Jazzy chords (Dm9, G13, Cmaj9, A7b13)
        val chords = listOf(
            listOf(146.83, 220.0, 261.63, 329.63, 392.0),
            listOf(98.0, 196.0, 246.94, 329.63, 349.23),
            listOf(130.81, 196.0, 246.94, 293.66, 329.63),
            listOf(110.0, 220.0, 277.18, 329.63, 415.30)
        )

        for (frame in 0 until totalFrames) {
            val t = frame.toDouble() / sampleRate
            val chordIndex = ((t / (beatSec * 4)).toInt()) % chords.size
            val freqs = chords[chordIndex]
            val chordT = t % (beatSec * 4)

            // Warm Rhodes piano tone with gentle tremolo
            val tremolo = 1.0 + 0.15 * sin(2 * PI * 4.5 * t)
            var rhodes = 0.0
            freqs.forEach { f ->
                val decay = exp(-chordT * 0.8)
                rhodes += (sin(2 * PI * f * t) + 0.25 * sin(4 * PI * f * t)) * decay * 0.22
            }
            rhodes *= tremolo

            // Vinyl dust / crackle texture
            val crackle = (Math.random() * 2.0 - 1.0) * 0.015

            // Muffled Lo-fi Kick
            val kickT = t % beatSec
            val kick = if (kickT < 0.2) sin(2 * PI * (65.0 * exp(-kickT * 15.0)) * kickT) * exp(-kickT * 10.0) * 0.55 else 0.0

            // Lo-fi Rimshot Snare on 2nd and 4th beat
            val snareT = (t - beatSec) % (beatSec * 2.0)
            val snare = if (snareT in 0.0..0.12) ((Math.random() * 2.0 - 1.0) * 0.3 + sin(2 * PI * 180.0 * snareT) * 0.25) * exp(-snareT * 22.0) else 0.0

            val mix = (rhodes + kick + snare + crackle).coerceIn(-1.0, 1.0)
            samples[frame * 2] = (mix * 26000).toInt().toShort()
            samples[frame * 2 + 1] = (mix * 26000).toInt().toShort()
        }

        return PcmData(sampleRate, 2, samples)
    }
}

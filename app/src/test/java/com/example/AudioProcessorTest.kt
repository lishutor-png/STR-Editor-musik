package com.example

import com.example.audio.AudioProcessor
import com.example.audio.EqualizerSettings
import com.example.audio.PcmData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioProcessorTest {

    private fun createTestTone(sampleRate: Int = 44100, channels: Int = 2, durationSeconds: Float = 2.0f): PcmData {
        val totalSamples = (sampleRate * channels * durationSeconds).toInt()
        val pcm = ShortArray(totalSamples)
        for (i in pcm.indices) {
            pcm[i] = 10000 // constant amplitude for testing
        }
        return PcmData(sampleRate = sampleRate, channelCount = channels, samples = pcm)
    }

    @Test
    fun testPcmDuration() {
        val pcm = createTestTone(sampleRate = 44100, channels = 2, durationSeconds = 3.0f)
        assertEquals(3000L, pcm.durationMs)
    }

    @Test
    fun testTrimAudio() {
        val original = createTestTone(sampleRate = 44100, channels = 2, durationSeconds = 4.0f)
        val trimmed = AudioProcessor.trim(original, startMs = 1000L, endMs = 3000L)

        assertEquals(2000L, trimmed.durationMs)
        assertEquals(original.sampleRate, trimmed.sampleRate)
        assertEquals(original.channelCount, trimmed.channelCount)
        assertEquals((44100 * 2 * 2), trimmed.samples.size)
    }

    @Test
    fun testVolumeGain() {
        val pcm = createTestTone(sampleRate = 44100, channels = 1, durationSeconds = 1.0f)
        val amplified = AudioProcessor.applyGain(pcm, 1.5f)

        assertEquals(pcm.samples.size, amplified.samples.size)
        assertEquals(15000, amplified.samples[0].toInt())

        // Test mute (gain = 0)
        val muted = AudioProcessor.applyGain(pcm, 0.0f)
        assertEquals(0, muted.samples[0].toInt())
    }

    @Test
    fun testFadeEffects() {
        val pcm = createTestTone(sampleRate = 44100, channels = 1, durationSeconds = 4.0f)
        val faded = AudioProcessor.applyFades(pcm, fadeInMs = 1000L, fadeOutMs = 1000L)

        // First sample should be close to 0 (fade-in start)
        assertEquals(0, faded.samples[0].toInt())

        // Middle sample should have full amplitude (10000)
        val midSampleIndex = 44100 * 2
        assertEquals(10000, faded.samples[midSampleIndex].toInt())

        // Last sample should be close to 0 (fade-out end)
        assertTrue(faded.samples.last().toInt() < 100)
    }

    @Test
    fun testMergeTracks() {
        val track1 = createTestTone(sampleRate = 44100, channels = 2, durationSeconds = 2.0f)
        val track2 = createTestTone(sampleRate = 44100, channels = 2, durationSeconds = 3.0f)

        val merged = AudioProcessor.mergeTracks(listOf(track1, track2), gapMs = 500L)

        assertEquals(5500L, merged.durationMs)
        assertEquals(track1.sampleRate, merged.sampleRate)
        assertEquals(track1.channelCount, merged.channelCount)
    }

    @Test
    fun testWaveformExtraction() {
        val pcm = createTestTone(sampleRate = 44100, channels = 2, durationSeconds = 1.0f)
        val bars = AudioProcessor.extractWaveform(pcm, barCount = 50)

        assertEquals(50, bars.size)
        bars.forEach { bar ->
            assertTrue(bar in 0.0f..1.0f)
        }
    }

    @Test
    fun testEqualizerProcessing() {
        val pcm = createTestTone(sampleRate = 44100, channels = 2, durationSeconds = 1.0f)
        val equalized = AudioProcessor.applyEqualizer(pcm, EqualizerSettings(bassDb = 6f, midDb = 0f, trebleDb = -3f))

        assertEquals(pcm.durationMs, equalized.durationMs)
        assertEquals(pcm.samples.size, equalized.samples.size)
        assertTrue(equalized.samples.isNotEmpty())
    }
}

package com.example.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.min

class RealtimeAudioPlayer(private val scope: CoroutineScope) {
    private val TAG = "RealtimeAudioPlayer"

    private var audioTrack: AudioTrack? = null
    private var playbackJob: Job? = null

    private var currentPcm: PcmData? = null
    private var startMs: Long = 0L
    private var endMs: Long = 0L
    private var volumeGain: Float = 1.0f
    private var fadeInMs: Long = 0L
    private var fadeOutMs: Long = 0L
    private var equalizerSettings = EqualizerSettings()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentPositionMs = MutableStateFlow(0L)
    val currentPositionMs: StateFlow<Long> = _currentPositionMs.asStateFlow()

    private val _isLooping = MutableStateFlow(false)
    val isLooping: StateFlow<Boolean> = _isLooping.asStateFlow()

    fun setSource(pcm: PcmData, startPositionMs: Long = 0L, endPositionMs: Long = pcm.durationMs) {
        stop()
        this.currentPcm = pcm
        this.startMs = startPositionMs
        this.endMs = endPositionMs
        _currentPositionMs.value = startPositionMs
    }

    fun updateSelection(start: Long, end: Long) {
        this.startMs = start
        this.endMs = end
        if (_currentPositionMs.value < start || _currentPositionMs.value > end) {
            _currentPositionMs.value = start
        }
    }

    fun updateEffects(
        gain: Float,
        fadeIn: Long,
        fadeOut: Long,
        eq: EqualizerSettings = equalizerSettings
    ) {
        this.volumeGain = gain
        this.fadeInMs = fadeIn
        this.fadeOutMs = fadeOut
        this.equalizerSettings = eq
    }

    fun toggleLoop() {
        _isLooping.value = !_isLooping.value
    }

    fun seekTo(positionMs: Long) {
        val safePos = positionMs.coerceIn(startMs, endMs)
        _currentPositionMs.value = safePos
        if (_isPlaying.value) {
            playFrom(safePos)
        }
    }

    fun togglePlayPause() {
        if (_isPlaying.value) {
            pause()
        } else {
            play()
        }
    }

    fun play() {
        val pos = if (_currentPositionMs.value >= endMs) startMs else _currentPositionMs.value
        playFrom(pos)
    }

    private fun playFrom(initialMs: Long) {
        val pcm = currentPcm ?: return
        playbackJob?.cancel()

        playbackJob = scope.launch(Dispatchers.Default) {
            try {
                // Apply DSP effects on the selected slice
                val trimmed = AudioProcessor.trim(pcm, startMs, endMs)
                val processed = AudioProcessor.applyEqualizer(
                    AudioProcessor.applyFades(
                        AudioProcessor.applyGain(trimmed, volumeGain),
                        fadeInMs,
                        fadeOutMs
                    ),
                    equalizerSettings
                )

                val sampleRate = processed.sampleRate
                val channels = processed.channelCount
                val channelConfig = if (channels == 1) AudioFormat.CHANNEL_OUT_MONO else AudioFormat.CHANNEL_OUT_STEREO

                val minBufferSize = AudioTrack.getMinBufferSize(
                    sampleRate,
                    channelConfig,
                    AudioFormat.ENCODING_PCM_16BIT
                )

                releaseAudioTrack()

                audioTrack = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(sampleRate)
                            .setChannelMask(channelConfig)
                            .build()
                    )
                    .setBufferSizeInBytes(minBufferSize * 4)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()

                audioTrack?.play()
                _isPlaying.value = true

                do {
                    val frameOffset = (((initialMs - startMs) * sampleRate) / 1000).toInt().coerceAtLeast(0)
                    var sampleOffset = frameOffset * channels
                    val totalSamples = processed.samples.size
                    val chunkSize = 2048

                    while (sampleOffset < totalSamples && _isPlaying.value) {
                        val count = min(chunkSize, totalSamples - sampleOffset)
                        val written = audioTrack?.write(processed.samples, sampleOffset, count) ?: 0
                        if (written > 0) {
                            sampleOffset += written
                            val currentFrames = sampleOffset / channels
                            val elapsedMs = (currentFrames * 1000L) / sampleRate
                            _currentPositionMs.value = startMs + elapsedMs
                        } else {
                            break
                        }
                    }

                    if (_isPlaying.value && _isLooping.value) {
                        _currentPositionMs.value = startMs
                    }
                } while (_isPlaying.value && _isLooping.value)

            } catch (e: CancellationException) {
                // normal coroutine cancellation
            } catch (e: Exception) {
                Log.e(TAG, "Playback error: ${e.message}", e)
            } finally {
                _isPlaying.value = false
                releaseAudioTrack()
            }
        }
    }

    fun pause() {
        _isPlaying.value = false
        playbackJob?.cancel()
        releaseAudioTrack()
    }

    fun stop() {
        _isPlaying.value = false
        playbackJob?.cancel()
        _currentPositionMs.value = startMs
        releaseAudioTrack()
    }

    private fun releaseAudioTrack() {
        try {
            audioTrack?.pause()
            audioTrack?.flush()
            audioTrack?.stop()
            audioTrack?.release()
        } catch (e: Exception) {
            // ignore
        }
        audioTrack = null
    }

    fun release() {
        stop()
    }
}

package com.example.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

class RealtimeAudioPlayer(
    private val scope: CoroutineScope
) {
    private val TAG = "RealtimeAudioPlayer"

    private var audioTrack: AudioTrack? = null
    private var playbackJob: Job? = null

    private var currentPcm: PcmData? = null
    private var startMs: Long = 0L
    private var endMs: Long = 0L
    private var volumeGain: Float = 1.0f
    private var fadeInMs: Long = 0L
    private var fadeOutMs: Long = 0L
    private var equalizerSettings: EqualizerSettings = EqualizerSettings()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentPositionMs = MutableStateFlow(0L)
    val currentPositionMs: StateFlow<Long> = _currentPositionMs.asStateFlow()

    private val _isLooping = MutableStateFlow(false)
    val isLooping: StateFlow<Boolean> = _isLooping.asStateFlow()

    fun setSource(
        pcm: PcmData,
        startPositionMs: Long = 0L,
        endPositionMs: Long = pcm.durationMs
    ) {
        stop()
        currentPcm = pcm
        startMs = startPositionMs.coerceIn(0L, pcm.durationMs)
        endMs = endPositionMs.coerceIn(startMs, pcm.durationMs)
        _currentPositionMs.value = startMs
    }

    fun updateSelection(start: Long, end: Long) {
        val duration = currentPcm?.durationMs ?: 0L
        startMs = start.coerceIn(0L, duration)
        endMs = end.coerceIn(startMs, duration)
        if (_currentPositionMs.value < startMs || _currentPositionMs.value > endMs) {
            _currentPositionMs.value = startMs
        }
    }

    fun updateEffects(gain: Float, fadeIn: Long, fadeOut: Long, eq: EqualizerSettings = equalizerSettings) {
        volumeGain = gain
        fadeInMs = fadeIn
        fadeOutMs = fadeOut
        equalizerSettings = eq
    }

    fun toggleLoop() {
        _isLooping.value = !_isLooping.value
    }

    fun seekTo(positionMs: Long) {
        val duration = currentPcm?.durationMs ?: 0L
        val clamped = positionMs.coerceIn(startMs, endMs.coerceAtLeast(duration))
        _currentPositionMs.value = clamped
        if (_isPlaying.value) {
            // Restart playback from new position
            playFrom(clamped)
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
        val pcm = currentPcm ?: return
        if (pcm.samples.isEmpty()) return

        var playStart = _currentPositionMs.value
        if (playStart >= endMs || playStart < startMs) {
            playStart = startMs
        }
        playFrom(playStart)
    }

    private fun playFrom(initialMs: Long) {
        playbackJob?.cancel()
        releaseAudioTrack()

        val pcm = currentPcm ?: return
        if (pcm.samples.isEmpty()) return

        val sampleRate = pcm.sampleRate
        val channels = pcm.channelCount
        val channelConfig = if (channels == 1) AudioFormat.CHANNEL_OUT_MONO else AudioFormat.CHANNEL_OUT_STEREO
        val encoding = AudioFormat.ENCODING_PCM_16BIT

        val minBufSize = AudioTrack.getMinBufferSize(sampleRate, channelConfig, encoding)
        val bufSize = max(minBufSize * 2, 8192)

        try {
            audioTrack = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(encoding)
                            .setSampleRate(sampleRate)
                            .setChannelMask(channelConfig)
                            .build()
                    )
                    .setBufferSizeInBytes(bufSize)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()
            } else {
                @Suppress("DEPRECATION")
                AudioTrack(
                    AudioManager.STREAM_MUSIC,
                    sampleRate,
                    channelConfig,
                    encoding,
                    bufSize,
                    AudioTrack.MODE_STREAM
                )
            }
            audioTrack?.play()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create AudioTrack: ${e.message}")
            return
        }

        _isPlaying.value = true

        playbackJob = scope.launch(Dispatchers.Default) {
            val totalFrames = pcm.samples.size / channels
            val startFrame = ((startMs * sampleRate) / 1000).toInt().coerceIn(0, totalFrames)
            val endFrame = ((endMs * sampleRate) / 1000).toInt().coerceIn(startFrame, totalFrames)
            var currentFrame = ((initialMs * sampleRate) / 1000).toInt().coerceIn(startFrame, endFrame)

            val chunkSizeFrames = 1024
            val chunkBuffer = ShortArray(chunkSizeFrames * channels)

            val lowStates = FloatArray(channels)
            val highStates = FloatArray(channels)
            val prevInputs = FloatArray(channels)
            val fLow = 320f
            val fHigh = 3600f
            val srFloat = sampleRate.toFloat()
            val alphaLow = (2 * PI.toFloat() * fLow) / (srFloat + 2 * PI.toFloat() * fLow)
            val alphaHigh = srFloat / (srFloat + 2 * PI.toFloat() * fHigh)

            try {
                while (isActive && _isPlaying.value) {
                    val selectionTotalFrames = max(1, endFrame - startFrame)
                    val fadeInFrames = if (fadeInMs > 0) ((fadeInMs * sampleRate) / 1000).toInt().coerceAtMost(selectionTotalFrames) else 0
                    val fadeOutFrames = if (fadeOutMs > 0) ((fadeOutMs * sampleRate) / 1000).toInt().coerceAtMost(selectionTotalFrames) else 0

                    val framesToRead = min(chunkSizeFrames, endFrame - currentFrame)
                    if (framesToRead <= 0) {
                        if (_isLooping.value) {
                            currentFrame = startFrame
                            _currentPositionMs.value = startMs
                            continue
                        } else {
                            // Reached end of selection
                            _isPlaying.value = false
                            _currentPositionMs.value = startMs
                            break
                        }
                    }

                    val eq = equalizerSettings
                    val isEqActive = eq.bassDb != 0f || eq.midDb != 0f || eq.trebleDb != 0f
                    val gBass = if (isEqActive) 10.0f.pow(eq.bassDb / 20f) else 1f
                    val gMid = if (isEqActive) 10.0f.pow(eq.midDb / 20f) else 1f
                    val gTreble = if (isEqActive) 10.0f.pow(eq.trebleDb / 20f) else 1f

                    // Process samples with real-time volume gain, fade in/out, and equalizer
                    for (f in 0 until framesToRead) {
                        val frameIdx = currentFrame + f
                        val relFrame = frameIdx - startFrame

                        // Fade factor calculation
                        var fadeFactor = 1.0f
                        if (fadeInFrames > 0 && relFrame < fadeInFrames) {
                            val progress = relFrame.toFloat() / fadeInFrames
                            fadeFactor = sin(progress * (PI.toFloat() / 2f))
                        } else if (fadeOutFrames > 0 && relFrame >= (selectionTotalFrames - fadeOutFrames)) {
                            val progress = (selectionTotalFrames - 1 - relFrame).toFloat() / fadeOutFrames
                            fadeFactor = sin(progress * (PI.toFloat() / 2f)).coerceIn(0f, 1f)
                        }

                        val effectiveGain = volumeGain * fadeFactor

                        for (ch in 0 until channels) {
                            val rawSample = pcm.samples[frameIdx * channels + ch].toFloat()

                            val eqSample = if (isEqActive) {
                                lowStates[ch] += alphaLow * (rawSample - lowStates[ch])
                                val low = lowStates[ch]
                                val high = alphaHigh * (highStates[ch] + rawSample - prevInputs[ch])
                                highStates[ch] = high
                                prevInputs[ch] = rawSample
                                val mid = rawSample - low - high
                                (low * gBass) + (mid * gMid) + (high * gTreble)
                            } else {
                                rawSample
                            }

                            val scaled = eqSample * effectiveGain
                            val clamped = scaled.coerceIn(-32768f, 32767f).toInt().toShort()
                            chunkBuffer[f * channels + ch] = clamped
                        }
                    }

                    val samplesToWrite = framesToRead * channels
                    val written = audioTrack?.write(chunkBuffer, 0, samplesToWrite) ?: 0
                    if (written < 0) {
                        break
                    }

                    currentFrame += framesToRead
                    val posMs = ((currentFrame.toLong() * 1000L) / sampleRate).coerceIn(startMs, endMs)
                    _currentPositionMs.value = posMs
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error during playback loop: ${e.message}")
            } finally {
                if (!isActive || !_isPlaying.value) {
                    _isPlaying.value = false
                }
            }
        }
    }

    fun pause() {
        _isPlaying.value = false
        playbackJob?.cancel()
        playbackJob = null
        try {
            audioTrack?.pause()
            audioTrack?.flush()
        } catch (e: Exception) {
            // ignore
        }
    }

    fun stop() {
        _isPlaying.value = false
        playbackJob?.cancel()
        playbackJob = null
        _currentPositionMs.value = startMs
        releaseAudioTrack()
    }

    private fun releaseAudioTrack() {
        try {
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

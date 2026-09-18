package com.example.audio

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

object AudioDecoder {
    private const val TAG = "AudioDecoder"
    private const val TIMEOUT_US = 5000L

    /**
     * Decodes an audio file or URI into [PcmData].
     */
    suspend fun decode(context: Context, fileOrUri: Any): PcmData = withContext(Dispatchers.IO) {
        // If it's a WAV file, we can try direct WAV parsing first for speed
        if (fileOrUri is File && fileOrUri.name.endsWith(".wav", ignoreCase = true)) {
            try {
                return@withContext parseWavFile(fileOrUri)
            } catch (e: Exception) {
                Log.w(TAG, "Direct WAV parse failed, falling back to MediaCodec: ${e.message}")
            }
        }

        // Use Android MediaExtractor & MediaCodec to decode any format (MP3, AAC, etc.)
        val extractor = MediaExtractor()
        try {
            when (fileOrUri) {
                is File -> extractor.setDataSource(fileOrUri.absolutePath)
                is Uri -> extractor.setDataSource(context, fileOrUri, null)
                else -> throw IllegalArgumentException("Expected File or Uri")
            }

            var audioTrackIndex = -1
            var audioFormat: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("audio/")) {
                    audioTrackIndex = i
                    audioFormat = format
                    break
                }
            }

            if (audioTrackIndex < 0 || audioFormat == null) {
                throw IllegalStateException("No audio track found in media source")
            }

            extractor.selectTrack(audioTrackIndex)
            val mime = audioFormat.getString(MediaFormat.KEY_MIME)!!
            val sampleRate = if (audioFormat.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                audioFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            } else 44100
            val channelCount = if (audioFormat.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                audioFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            } else 2

            val codec = MediaCodec.createDecoderByType(mime)
            codec.configure(audioFormat, null, null, 0)
            codec.start()

            val pcmBytes = ByteArrayOutputStream()
            val bufferInfo = MediaCodec.BufferInfo()
            var isInputEOS = false
            var isOutputEOS = false

            while (!isOutputEOS) {
                if (!isInputEOS) {
                    val inputBufIndex = codec.dequeueInputBuffer(TIMEOUT_US)
                    if (inputBufIndex >= 0) {
                        val inputBuf = codec.getInputBuffer(inputBufIndex) ?: continue
                        inputBuf.clear()
                        val sampleSize = extractor.readSampleData(inputBuf, 0)
                        if (sampleSize < 0) {
                            codec.queueInputBuffer(inputBufIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            isInputEOS = true
                        } else {
                            val presentationTimeUs = extractor.sampleTime
                            codec.queueInputBuffer(inputBufIndex, 0, sampleSize, presentationTimeUs, 0)
                            extractor.advance()
                        }
                    }
                }

                val outputBufIndex = codec.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)
                if (outputBufIndex >= 0) {
                    val outputBuf = codec.getOutputBuffer(outputBufIndex)
                    if (outputBuf != null && bufferInfo.size > 0) {
                        outputBuf.position(bufferInfo.offset)
                        outputBuf.limit(bufferInfo.offset + bufferInfo.size)
                        val chunk = ByteArray(bufferInfo.size)
                        outputBuf.get(chunk)
                        pcmBytes.write(chunk)
                    }
                    codec.releaseOutputBuffer(outputBufIndex, false)
                    if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        isOutputEOS = true
                    }
                } else if (outputBufIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    val newFormat = codec.outputFormat
                    Log.d(TAG, "Decoder output format changed: $newFormat")
                }
            }

            codec.stop()
            codec.release()

            // Convert PCM 16-bit little-endian byte array to ShortArray
            val rawBytes = pcmBytes.toByteArray()
            val shortBuffer = ByteBuffer.wrap(rawBytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
            val shortArray = ShortArray(shortBuffer.remaining())
            shortBuffer.get(shortArray)

            return@withContext PcmData(sampleRate, channelCount, shortArray)
        } finally {
            try {
                extractor.release()
            } catch (e: Exception) {
                // ignore
            }
        }
    }

    /**
     * Parses standard 16-bit PCM RIFF WAV file directly.
     */
    fun parseWavFile(file: File): PcmData {
        FileInputStream(file).use { fis ->
            return parseWavStream(fis)
        }
    }

    fun parseWavStream(stream: InputStream): PcmData {
        val header = ByteArray(12)
        if (stream.read(header) < 12) throw IllegalStateException("Invalid WAV file: header too short")
        val riff = String(header, 0, 4)
        val wave = String(header, 8, 4)
        if (riff != "RIFF" || wave != "WAVE") {
            throw IllegalStateException("Not a valid RIFF WAVE audio stream")
        }

        var sampleRate = 44100
        var channels = 2
        var audioFormat = 1 // 1 = PCM
        var bitsPerSample = 16
        var pcmData: ByteArray? = null

        val chunkHeader = ByteArray(8)
        while (stream.read(chunkHeader) == 8) {
            val chunkId = String(chunkHeader, 0, 4)
            val chunkSize = ByteBuffer.wrap(chunkHeader, 4, 4).order(ByteOrder.LITTLE_ENDIAN).int

            when (chunkId) {
                "fmt " -> {
                    val fmtData = ByteArray(chunkSize)
                    stream.read(fmtData)
                    val bb = ByteBuffer.wrap(fmtData).order(ByteOrder.LITTLE_ENDIAN)
                    audioFormat = bb.short.toInt()
                    channels = bb.short.toInt()
                    sampleRate = bb.int
                    bb.int // byteRate
                    bb.short // blockAlign
                    bitsPerSample = bb.short.toInt()
                }
                "data" -> {
                    pcmData = ByteArray(chunkSize)
                    var bytesRead = 0
                    while (bytesRead < chunkSize) {
                        val read = stream.read(pcmData, bytesRead, chunkSize - bytesRead)
                        if (read == -1) break
                        bytesRead += read
                    }
                    break
                }
                else -> {
                    // Skip unknown chunk
                    var remaining = chunkSize.toLong()
                    while (remaining > 0) {
                        val skipped = stream.skip(remaining)
                        if (skipped <= 0) break
                        remaining -= skipped
                    }
                }
            }
        }

        if (pcmData == null) {
            throw IllegalStateException("No PCM data chunk found in WAV")
        }

        if (bitsPerSample != 16) {
            Log.w(TAG, "WAV has $bitsPerSample bits per sample, handling standard 16-bit")
        }

        val shortBuf = ByteBuffer.wrap(pcmData).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
        val shortArray = ShortArray(shortBuf.remaining())
        shortBuf.get(shortArray)

        return PcmData(sampleRate, channels, shortArray)
    }

    /**
     * Extracts track metadata using MediaMetadataRetriever.
     */
    fun extractMetadata(context: Context, uri: Uri?, file: File?, fallbackTitle: String): AudioTrackInfo {
        val retriever = MediaMetadataRetriever()
        var title = fallbackTitle
        var durationMs = 0L
        var sampleRate = 44100
        var channels = 2
        var format = "AUDIO"

        try {
            if (file != null) {
                retriever.setDataSource(file.absolutePath)
                format = file.extension.uppercase()
            } else if (uri != null) {
                retriever.setDataSource(context, uri)
            }

            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)?.let {
                if (it.isNotBlank()) title = it
            }
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.let {
                durationMs = it.toLongOrNull() ?: 0L
            }
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_SAMPLERATE)?.let {
                sampleRate = it.toIntOrNull() ?: 44100
            }
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_NUM_TRACKS)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to retrieve full metadata: ${e.message}")
        } finally {
            try {
                retriever.release()
            } catch (e: Exception) {
                // ignore
            }
        }

        val size = file?.length() ?: 0L

        return AudioTrackInfo(
            id = file?.absolutePath ?: uri.toString(),
            title = title,
            durationMs = durationMs,
            format = format,
            sizeBytes = size,
            sampleRate = sampleRate,
            channels = channels,
            uri = uri,
            file = file
        )
    }
}

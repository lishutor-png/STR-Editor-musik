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
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale

object AudioDecoder {
    private const val TAG = "AudioDecoder"
    private const val TIMEOUT_US = 5000L

    suspend fun decode(context: Context, fileOrUri: Any): PcmData = withContext(Dispatchers.IO) {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null

        try {
            when (fileOrUri) {
                is File -> {
                    if (fileOrUri.name.endsWith(".wav", ignoreCase = true)) {
                        return@withContext parseWavFile(fileOrUri)
                    }
                    extractor.setDataSource(fileOrUri.absolutePath)
                }
                is Uri -> {
                    val scheme = fileOrUri.scheme
                    val path = fileOrUri.path
                    if (path != null && path.endsWith(".wav", ignoreCase = true)) {
                        context.contentResolver.openInputStream(fileOrUri)?.use { input ->
                            return@withContext parseWavStream(input)
                        }
                    }
                    extractor.setDataSource(context, fileOrUri, null)
                }
                else -> throw IllegalArgumentException("fileOrUri must be File or Uri")
            }

            var audioTrackIndex = -1
            var inputFormat: MediaFormat? = null

            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("audio/")) {
                    audioTrackIndex = i
                    inputFormat = format
                    break
                }
            }

            if (audioTrackIndex == -1 || inputFormat == null) {
                throw IllegalStateException("No audio track found in media")
            }

            extractor.selectTrack(audioTrackIndex)
            val mime = inputFormat.getString(MediaFormat.KEY_MIME) ?: "audio/raw"
            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(inputFormat, null, null, 0)
            codec.start()

            val sampleRate = if (inputFormat.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                inputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            } else 44100

            val channels = if (inputFormat.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                inputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            } else 2

            val bufferInfo = MediaCodec.BufferInfo()
            val sampleChunks = ArrayList<ShortArray>()
            var isEos = false

            while (!isEos) {
                val inIndex = codec.dequeueInputBuffer(TIMEOUT_US)
                if (inIndex >= 0) {
                    val inBuffer = codec.getInputBuffer(inIndex)
                    if (inBuffer != null) {
                        val sampleSize = extractor.readSampleData(inBuffer, 0)
                        if (sampleSize < 0) {
                            codec.queueInputBuffer(inIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        } else {
                            val timeUs = extractor.sampleTime
                            codec.queueInputBuffer(inIndex, 0, sampleSize, timeUs, 0)
                            extractor.advance()
                        }
                    }
                }

                val outIndex = codec.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)
                if (outIndex >= 0) {
                    val outBuffer = codec.getOutputBuffer(outIndex)
                    if (outBuffer != null && bufferInfo.size > 0) {
                        outBuffer.position(bufferInfo.offset)
                        outBuffer.limit(bufferInfo.offset + bufferInfo.size)
                        outBuffer.order(ByteOrder.LITTLE_ENDIAN)

                        val shortBuffer = outBuffer.asShortBuffer()
                        val shorts = ShortArray(shortBuffer.remaining())
                        shortBuffer.get(shorts)
                        sampleChunks.add(shorts)
                    }
                    codec.releaseOutputBuffer(outIndex, false)

                    if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        isEos = true
                    }
                }
            }

            val totalSamples = sampleChunks.sumOf { it.size }
            val flatSamples = ShortArray(totalSamples)
            var offset = 0
            for (chunk in sampleChunks) {
                System.arraycopy(chunk, 0, flatSamples, offset, chunk.size)
                offset += chunk.size
            }

            PcmData(sampleRate, channels, flatSamples)
        } catch (e: Exception) {
            Log.e(TAG, "Audio decoding failed: ${e.message}", e)
            throw e
        } finally {
            try {
                codec?.stop()
                codec?.release()
            } catch (e: Exception) {
                // ignore
            }
            try {
                extractor.release()
            } catch (e: Exception) {
                // ignore
            }
        }
    }

    fun parseWavFile(file: File): PcmData {
        FileInputStream(file).use { return parseWavStream(it) }
    }

    fun parseWavStream(stream: InputStream): PcmData {
        val header = ByteArray(12)
        if (stream.read(header) < 12) throw IllegalStateException("Invalid WAV file: header too short")
        val riff = String(header, 0, 4, Charsets.UTF_8)
        val wave = String(header, 8, 4, Charsets.UTF_8)
        if (riff != "RIFF" || wave != "WAVE") {
            throw IllegalStateException("Invalid WAV format: missing RIFF/WAVE header")
        }

        var sampleRate = 44100
        var channels = 2
        var bitsPerSample = 16
        val chunkHeader = ByteArray(8)

        while (stream.read(chunkHeader) == 8) {
            val chunkId = String(chunkHeader, 0, 4, Charsets.UTF_8)
            val chunkSize = ByteBuffer.wrap(chunkHeader, 4, 4).order(ByteOrder.LITTLE_ENDIAN).int

            if (chunkId == "fmt ") {
                val fmtData = ByteArray(chunkSize)
                stream.read(fmtData)
                val buf = ByteBuffer.wrap(fmtData).order(ByteOrder.LITTLE_ENDIAN)
                val audioFormat = buf.short.toInt()
                channels = buf.short.toInt()
                sampleRate = buf.int
                buf.int // byteRate
                buf.short // blockAlign
                bitsPerSample = buf.short.toInt()
            } else if (chunkId == "data") {
                val pcmBytes = ByteArray(chunkSize)
                var readTotal = 0
                while (readTotal < chunkSize) {
                    val count = stream.read(pcmBytes, readTotal, chunkSize - readTotal)
                    if (count <= 0) break
                    readTotal += count
                }

                val shortsCount = readTotal / 2
                val shorts = ShortArray(shortsCount)
                ByteBuffer.wrap(pcmBytes, 0, readTotal).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(shorts)
                return PcmData(sampleRate, channels, shorts)
            } else {
                stream.skip(chunkSize.toLong())
            }
        }
        throw IllegalStateException("No data chunk found in WAV stream")
    }

    fun extractMetadata(
        context: Context,
        uri: Uri? = null,
        file: File? = null,
        fallbackTitle: String = "Track"
    ): AudioTrackInfo {
        val retriever = MediaMetadataRetriever()
        try {
            if (file != null) {
                retriever.setDataSource(file.absolutePath)
            } else if (uri != null) {
                retriever.setDataSource(context, uri)
            }

            val title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                ?: file?.name?.substringBeforeLast(".")
                ?: fallbackTitle
            val artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST) ?: "Artis Tidak Diketahui"
            val durStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            val durationMs = durStr?.toLongOrNull() ?: 0L
            val bitStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)
            val bitrateKbps = (bitStr?.toIntOrNull() ?: 192000) / 1000

            val mime = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE) ?: ""
            val format = when {
                mime.contains("mpeg") || (file?.name?.endsWith(".mp3", true) == true) -> AudioFormat.MP3
                mime.contains("mp4") || (file?.name?.endsWith(".m4a", true) == true) -> AudioFormat.M4A
                mime.contains("wav") || (file?.name?.endsWith(".wav", true) == true) -> AudioFormat.WAV
                mime.contains("flac") || (file?.name?.endsWith(".flac", true) == true) -> AudioFormat.FLAC
                else -> AudioFormat.MP3
            }

            val coverArt = when {
                format == AudioFormat.WAV -> null
                file != null && AudioTagManager.isMp3File(file) -> AudioTagManager.extractCoverFromFile(file)
                else -> {
                    try {
                        val hasImage = retriever.extractMetadata(32) // METADATA_KEY_HAS_IMAGE (API 28+)
                        if (hasImage == "yes") {
                            retriever.embeddedPicture
                        } else null
                    } catch (e: Exception) {
                        null
                    }
                }
            }
            val size = file?.length() ?: 0L

            return AudioTrackInfo(
                id = file?.absolutePath ?: uri.toString(),
                title = title,
                artist = artist,
                durationMs = durationMs,
                uri = uri,
                file = file,
                format = format,
                sizeBytes = size,
                bitrateKbps = bitrateKbps,
                coverArtBytes = coverArt
            )
        } catch (e: Exception) {
            return AudioTrackInfo(
                id = file?.absolutePath ?: (uri?.toString() ?: fallbackTitle),
                title = fallbackTitle,
                file = file,
                uri = uri
            )
        } finally {
            try {
                retriever.release()
            } catch (e: Exception) {
                // ignore
            }
        }
    }
}

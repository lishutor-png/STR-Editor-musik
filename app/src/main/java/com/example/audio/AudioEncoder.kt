package com.example.audio

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import android.media.MediaMuxer
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

object AudioEncoder {
    private const val TAG = "AudioEncoder"
    private const val TIMEOUT_US = 5000L

    /**
     * Checks if a hardware or software encoder exists for the given MIME type.
     */
    fun hasEncoderForMime(mime: String): Boolean {
        val codecList = MediaCodecList(MediaCodecList.REGULAR_CODECS)
        for (info in codecList.codecInfos) {
            if (!info.isEncoder) continue
            for (type in info.supportedTypes) {
                if (type.equals(mime, ignoreCase = true)) {
                    return true
                }
            }
        }
        return false
    }

    /**
     * Encodes [pcm] data to the specified [options] and saves to [outputFile].
     */
    suspend fun encode(
        pcm: PcmData,
        outputFile: File,
        options: ExportOptions,
        onProgress: (Float) -> Unit = {}
    ) = withContext(Dispatchers.IO) {
        if (outputFile.parentFile?.exists() == false) {
            outputFile.parentFile?.mkdirs()
        }

        when (options.format) {
            AudioFormat.WAV -> {
                encodeWav(pcm, outputFile, onProgress)
            }
            AudioFormat.M4A -> {
                encodeAacM4a(pcm, outputFile, options.bitrateKbps, onProgress)
            }
            AudioFormat.FLAC -> {
                if (hasEncoderForMime(MediaFormat.MIMETYPE_AUDIO_FLAC)) {
                    try {
                        encodeFlac(pcm, outputFile, onProgress)
                    } catch (e: Exception) {
                        Log.w(TAG, "FLAC encode failed, fallback to WAV: ${e.message}")
                        encodeWav(pcm, outputFile, onProgress)
                    }
                } else {
                    encodeWav(pcm, outputFile, onProgress)
                }
            }
            AudioFormat.MP3 -> {
                // If device has an MP3 encoder, use MediaCodec
                if (hasEncoderForMime("audio/mpeg")) {
                    try {
                        encodeGenericCodec(pcm, outputFile, "audio/mpeg", options.bitrateKbps, onProgress)
                    } catch (e: Exception) {
                        Log.w(TAG, "Hardware MP3 encoder failed, encoding AAC/M4A with mp3 extension: ${e.message}")
                        encodeAacM4a(pcm, outputFile, options.bitrateKbps, onProgress)
                    }
                } else {
                    // Export as high-quality AAC/M4A (industry standard compressed format)
                    encodeAacM4a(pcm, outputFile, options.bitrateKbps, onProgress)
                }
            }
        }
    }

    /**
     * Exports standard uncompressed 16-bit PCM RIFF WAV file.
     */
    fun encodeWav(pcm: PcmData, outputFile: File, onProgress: (Float) -> Unit = {}) {
        val sampleRate = pcm.sampleRate
        val channels = pcm.channelCount
        val totalAudioLen = (pcm.samples.size * 2).toLong() // 16 bits = 2 bytes per sample
        val totalDataLen = totalAudioLen + 36
        val byteRate = (sampleRate * channels * 2).toLong()

        FileOutputStream(outputFile).use { fos ->
            val header = ByteArray(44)
            header[0] = 'R'.code.toByte()
            header[1] = 'I'.code.toByte()
            header[2] = 'F'.code.toByte()
            header[3] = 'F'.code.toByte()
            header[4] = (totalDataLen and 0xffL).toByte()
            header[5] = ((totalDataLen shr 8) and 0xffL).toByte()
            header[6] = ((totalDataLen shr 16) and 0xffL).toByte()
            header[7] = ((totalDataLen shr 24) and 0xffL).toByte()
            header[8] = 'W'.code.toByte()
            header[9] = 'A'.code.toByte()
            header[10] = 'V'.code.toByte()
            header[11] = 'E'.code.toByte()
            header[12] = 'f'.code.toByte() // 'fmt ' chunk
            header[13] = 'm'.code.toByte()
            header[14] = 't'.code.toByte()
            header[15] = ' '.code.toByte()
            header[16] = 16 // 16 bytes for PCM format chunk
            header[17] = 0
            header[18] = 0
            header[19] = 0
            header[20] = 1 // Format = 1 (PCM)
            header[21] = 0
            header[22] = channels.toByte()
            header[23] = 0
            header[24] = (sampleRate and 0xff).toByte()
            header[25] = ((sampleRate shr 8) and 0xff).toByte()
            header[26] = ((sampleRate shr 16) and 0xff).toByte()
            header[27] = ((sampleRate shr 24) and 0xff).toByte()
            header[28] = (byteRate and 0xffL).toByte()
            header[29] = ((byteRate shr 8) and 0xffL).toByte()
            header[30] = ((byteRate shr 16) and 0xffL).toByte()
            header[31] = ((byteRate shr 24) and 0xffL).toByte()
            header[32] = (channels * 2).toByte() // block align
            header[33] = 0
            header[34] = 16 // bits per sample
            header[35] = 0
            header[36] = 'd'.code.toByte() // 'data' chunk
            header[37] = 'a'.code.toByte()
            header[38] = 't'.code.toByte()
            header[39] = 'a'.code.toByte()
            header[40] = (totalAudioLen and 0xffL).toByte()
            header[41] = ((totalAudioLen shr 8) and 0xffL).toByte()
            header[42] = ((totalAudioLen shr 16) and 0xffL).toByte()
            header[43] = ((totalAudioLen shr 24) and 0xffL).toByte()

            fos.write(header)

            val buffer = ByteArray(4096)
            var bufferIdx = 0
            val totalSamples = pcm.samples.size

            for (i in 0 until totalSamples) {
                val s = pcm.samples[i]
                buffer[bufferIdx++] = (s.toInt() and 0xff).toByte()
                buffer[bufferIdx++] = ((s.toInt() shr 8) and 0xff).toByte()

                if (bufferIdx >= buffer.size) {
                    fos.write(buffer, 0, bufferIdx)
                    bufferIdx = 0
                    if (i % 8192 == 0) {
                        onProgress(i.toFloat() / totalSamples)
                    }
                }
            }

            if (bufferIdx > 0) {
                fos.write(buffer, 0, bufferIdx)
            }

            onProgress(1.0f)
        }
    }

    /**
     * Encodes PCM data to AAC inside an MP4 (.m4a) container using MediaCodec + MediaMuxer.
     */
    fun encodeAacM4a(
        pcm: PcmData,
        outputFile: File,
        bitrateKbps: Int = 192,
        onProgress: (Float) -> Unit = {}
    ) {
        val sampleRate = pcm.sampleRate
        val channels = pcm.channelCount
        val bitrate = bitrateKbps * 1000

        val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, sampleRate, channels)
        format.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
        format.setInteger(MediaFormat.KEY_BIT_RATE, bitrate)
        format.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384)

        val encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
        encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        encoder.start()

        val muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var audioTrackIndex = -1
        var muxerStarted = false

        val bufferInfo = MediaCodec.BufferInfo()
        val totalBytes = pcm.samples.size * 2
        var bytesRead = 0
        var isInputEOS = false
        var isOutputEOS = false

        val rawByteBuffer = ByteBuffer.allocate(pcm.samples.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        for (sample in pcm.samples) {
            rawByteBuffer.putShort(sample)
        }
        rawByteBuffer.flip()

        try {
            while (!isOutputEOS) {
                if (!isInputEOS) {
                    val inputBufIndex = encoder.dequeueInputBuffer(TIMEOUT_US)
                    if (inputBufIndex >= 0) {
                        val inputBuf = encoder.getInputBuffer(inputBufIndex) ?: continue
                        inputBuf.clear()

                        val remaining = rawByteBuffer.remaining()
                        val toWrite = minOf(remaining, inputBuf.capacity())

                        if (toWrite <= 0) {
                            encoder.queueInputBuffer(inputBufIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            isInputEOS = true
                        } else {
                            val chunk = ByteArray(toWrite)
                            rawByteBuffer.get(chunk)
                            inputBuf.put(chunk)
                            bytesRead += toWrite

                            // Presentation time in microseconds
                            val framesWritten = (bytesRead / 2) / channels
                            val ptsUs = (framesWritten.toLong() * 1_000_000L) / sampleRate

                            encoder.queueInputBuffer(inputBufIndex, 0, toWrite, ptsUs, 0)
                            onProgress(bytesRead.toFloat() / totalBytes * 0.9f)
                        }
                    }
                }

                val outputBufIndex = encoder.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)
                if (outputBufIndex >= 0) {
                    val outputBuf = encoder.getOutputBuffer(outputBufIndex)
                    if (outputBuf != null && (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0 && bufferInfo.size > 0) {
                        if (muxerStarted) {
                            outputBuf.position(bufferInfo.offset)
                            outputBuf.limit(bufferInfo.offset + bufferInfo.size)
                            muxer.writeSampleData(audioTrackIndex, outputBuf, bufferInfo)
                        }
                    }
                    encoder.releaseOutputBuffer(outputBufIndex, false)
                    if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        isOutputEOS = true
                    }
                } else if (outputBufIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    if (!muxerStarted) {
                        val newFormat = encoder.outputFormat
                        audioTrackIndex = muxer.addTrack(newFormat)
                        muxer.start()
                        muxerStarted = true
                    }
                }
            }
        } finally {
            try {
                encoder.stop()
                encoder.release()
            } catch (e: Exception) {
                // ignore
            }
            try {
                if (muxerStarted) {
                    muxer.stop()
                }
                muxer.release()
            } catch (e: Exception) {
                // ignore
            }
        }

        onProgress(1.0f)
    }

    /**
     * Encodes to FLAC format if supported by device encoder.
     */
    fun encodeFlac(pcm: PcmData, outputFile: File, onProgress: (Float) -> Unit = {}) {
        encodeGenericCodec(pcm, outputFile, MediaFormat.MIMETYPE_AUDIO_FLAC, 0, onProgress)
    }

    private fun encodeGenericCodec(
        pcm: PcmData,
        outputFile: File,
        mime: String,
        bitrateKbps: Int = 192,
        onProgress: (Float) -> Unit = {}
    ) {
        val sampleRate = pcm.sampleRate
        val channels = pcm.channelCount
        val format = MediaFormat.createAudioFormat(mime, sampleRate, channels)
        if (bitrateKbps > 0) {
            format.setInteger(MediaFormat.KEY_BIT_RATE, bitrateKbps * 1000)
        }

        val encoder = MediaCodec.createEncoderByType(mime)
        encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        encoder.start()

        val fos = FileOutputStream(outputFile)
        val bufferInfo = MediaCodec.BufferInfo()
        val totalBytes = pcm.samples.size * 2
        var bytesRead = 0
        var isInputEOS = false
        var isOutputEOS = false

        val rawByteBuffer = ByteBuffer.allocate(pcm.samples.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        for (sample in pcm.samples) {
            rawByteBuffer.putShort(sample)
        }
        rawByteBuffer.flip()

        try {
            while (!isOutputEOS) {
                if (!isInputEOS) {
                    val inputBufIndex = encoder.dequeueInputBuffer(TIMEOUT_US)
                    if (inputBufIndex >= 0) {
                        val inputBuf = encoder.getInputBuffer(inputBufIndex) ?: continue
                        inputBuf.clear()

                        val remaining = rawByteBuffer.remaining()
                        val toWrite = minOf(remaining, inputBuf.capacity())

                        if (toWrite <= 0) {
                            encoder.queueInputBuffer(inputBufIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            isInputEOS = true
                        } else {
                            val chunk = ByteArray(toWrite)
                            rawByteBuffer.get(chunk)
                            inputBuf.put(chunk)
                            bytesRead += toWrite

                            val framesWritten = (bytesRead / 2) / channels
                            val ptsUs = (framesWritten.toLong() * 1_000_000L) / sampleRate

                            encoder.queueInputBuffer(inputBufIndex, 0, toWrite, ptsUs, 0)
                            onProgress(bytesRead.toFloat() / totalBytes * 0.9f)
                        }
                    }
                }

                val outputBufIndex = encoder.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)
                if (outputBufIndex >= 0) {
                    val outputBuf = encoder.getOutputBuffer(outputBufIndex)
                    if (outputBuf != null && bufferInfo.size > 0) {
                        outputBuf.position(bufferInfo.offset)
                        outputBuf.limit(bufferInfo.offset + bufferInfo.size)
                        val encodedChunk = ByteArray(bufferInfo.size)
                        outputBuf.get(encodedChunk)
                        fos.write(encodedChunk)
                    }
                    encoder.releaseOutputBuffer(outputBufIndex, false)
                    if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        isOutputEOS = true
                    }
                }
            }
        } finally {
            try {
                encoder.stop()
                encoder.release()
            } catch (e: Exception) {
                // ignore
            }
            try {
                fos.close()
            } catch (e: Exception) {
                // ignore
            }
        }

        onProgress(1.0f)
    }
}

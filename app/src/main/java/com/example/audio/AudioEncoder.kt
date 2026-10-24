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

    fun hasEncoderForMime(mime: String): Boolean {
        val list = MediaCodecList(MediaCodecList.REGULAR_CODECS)
        for (info in list.codecInfos) {
            if (info.isEncoder) {
                for (type in info.supportedTypes) {
                    if (type.equals(mime, ignoreCase = true)) return true
                }
            }
        }
        return false
    }

    suspend fun encode(
        pcm: PcmData,
        outputFile: File,
        options: ExportOptions,
        onProgress: (Float) -> Unit = {}
    ) = withContext(Dispatchers.IO) {
        when (options.format) {
            AudioFormat.WAV -> encodeWav(pcm, outputFile, onProgress)
            AudioFormat.M4A -> encodeAacM4a(pcm, outputFile, options.bitrateKbps, onProgress)
            AudioFormat.FLAC -> encodeFlac(pcm, outputFile, onProgress)
            AudioFormat.MP3 -> {
                // If system has MP3 encoder, use it; otherwise fallback to AAC .m4a or WAV
                if (hasEncoderForMime(MediaFormat.MIMETYPE_AUDIO_MPEG)) {
                    encodeGenericCodec(pcm, outputFile, MediaFormat.MIMETYPE_AUDIO_MPEG, options.bitrateKbps, onProgress)
                } else {
                    // Modern Android devices provide AAC M4A reliably
                    val aacFile = File(outputFile.parentFile, outputFile.nameWithoutExtension + ".m4a")
                    encodeAacM4a(pcm, aacFile, options.bitrateKbps, onProgress)
                    if (aacFile.exists()) {
                        aacFile.renameTo(outputFile)
                    }
                }
            }
        }
    }

    fun encodeWav(pcm: PcmData, outputFile: File, onProgress: (Float) -> Unit = {}) {
        val sampleRate = pcm.sampleRate
        val channels = pcm.channelCount
        val totalAudioLen = pcm.samples.size * 2
        val totalDataLen = totalAudioLen + 36
        val byteRate = sampleRate * channels * 2

        FileOutputStream(outputFile).use { out ->
            val header = ByteArray(44)
            header[0] = 'R'.code.toByte()
            header[1] = 'I'.code.toByte()
            header[2] = 'F'.code.toByte()
            header[3] = 'F'.code.toByte()

            header[4] = (totalDataLen and 0xff).toByte()
            header[5] = ((totalDataLen shr 8) and 0xff).toByte()
            header[6] = ((totalDataLen shr 16) and 0xff).toByte()
            header[7] = ((totalDataLen shr 24) and 0xff).toByte()

            header[8] = 'W'.code.toByte()
            header[9] = 'A'.code.toByte()
            header[10] = 'V'.code.toByte()
            header[11] = 'E'.code.toByte()

            header[12] = 'f'.code.toByte()
            header[13] = 'm'.code.toByte()
            header[14] = 't'.code.toByte()
            header[15] = ' '.code.toByte()

            header[16] = 16
            header[17] = 0
            header[18] = 0
            header[19] = 0

            header[20] = 1 // PCM format
            header[21] = 0

            header[22] = channels.toByte()
            header[23] = 0

            header[24] = (sampleRate and 0xff).toByte()
            header[25] = ((sampleRate shr 8) and 0xff).toByte()
            header[26] = ((sampleRate shr 16) and 0xff).toByte()
            header[27] = ((sampleRate shr 24) and 0xff).toByte()

            header[28] = (byteRate and 0xff).toByte()
            header[29] = ((byteRate shr 8) and 0xff).toByte()
            header[30] = ((byteRate shr 16) and 0xff).toByte()
            header[31] = ((byteRate shr 24) and 0xff).toByte()

            header[32] = (channels * 2).toByte() // block align
            header[33] = 0

            header[34] = 16 // bits per sample
            header[35] = 0

            header[36] = 'd'.code.toByte()
            header[37] = 'a'.code.toByte()
            header[38] = 't'.code.toByte()
            header[39] = 'a'.code.toByte()

            header[40] = (totalAudioLen and 0xff).toByte()
            header[41] = ((totalAudioLen shr 8) and 0xff).toByte()
            header[42] = ((totalAudioLen shr 16) and 0xff).toByte()
            header[43] = ((totalAudioLen shr 24) and 0xff).toByte()

            out.write(header, 0, 44)

            val pcmBytes = ByteArray(pcm.samples.size * 2)
            ByteBuffer.wrap(pcmBytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().put(pcm.samples)
            out.write(pcmBytes)
            out.flush()
        }
        onProgress(1.0f)
    }

    fun encodeAacM4a(pcm: PcmData, outputFile: File, bitrateKbps: Int = 192, onProgress: (Float) -> Unit = {}) {
        val mime = MediaFormat.MIMETYPE_AUDIO_AAC
        val format = MediaFormat.createAudioFormat(mime, pcm.sampleRate, pcm.channelCount).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, bitrateKbps * 1000)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384)
        }

        val codec = MediaCodec.createEncoderByType(mime)
        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        codec.start()

        val muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var audioTrackIndex = -1
        var muxerStarted = false

        val bufferInfo = MediaCodec.BufferInfo()
        val pcmByteBuf = ByteBuffer.allocate(pcm.samples.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        pcmByteBuf.asShortBuffer().put(pcm.samples)
        pcmByteBuf.flip()

        var sampleIndex = 0
        val totalBytes = pcmByteBuf.remaining()
        var isEos = false

        try {
            while (!isEos) {
                val inIndex = codec.dequeueInputBuffer(TIMEOUT_US)
                if (inIndex >= 0) {
                    val inBuffer = codec.getInputBuffer(inIndex)
                    if (inBuffer != null) {
                        inBuffer.clear()
                        val bytesToCopy = minOf(inBuffer.capacity(), pcmByteBuf.remaining())
                        if (bytesToCopy > 0) {
                            val temp = ByteArray(bytesToCopy)
                            pcmByteBuf.get(temp)
                            inBuffer.put(temp)
                            val presentationTimeUs = (sampleIndex.toLong() * 1000000L) / (pcm.sampleRate * pcm.channelCount * 2)
                            codec.queueInputBuffer(inIndex, 0, bytesToCopy, presentationTimeUs, 0)
                            sampleIndex += bytesToCopy
                            onProgress(sampleIndex.toFloat() / totalBytes.toFloat() * 0.8f)
                        } else {
                            codec.queueInputBuffer(inIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        }
                    }
                }

                var outIndex = codec.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)
                while (outIndex >= 0) {
                    val outBuffer = codec.getOutputBuffer(outIndex)
                    if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                        bufferInfo.size = 0
                    }

                    if (bufferInfo.size != 0 && outBuffer != null) {
                        if (!muxerStarted) {
                            audioTrackIndex = muxer.addTrack(codec.outputFormat)
                            muxer.start()
                            muxerStarted = true
                        }
                        outBuffer.position(bufferInfo.offset)
                        outBuffer.limit(bufferInfo.offset + bufferInfo.size)
                        muxer.writeSampleData(audioTrackIndex, outBuffer, bufferInfo)
                    }

                    codec.releaseOutputBuffer(outIndex, false)
                    if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        isEos = true
                        break
                    }
                    outIndex = codec.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)
                }

                if (outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED && !muxerStarted) {
                    audioTrackIndex = muxer.addTrack(codec.outputFormat)
                    muxer.start()
                    muxerStarted = true
                }
            }
        } finally {
            try {
                codec.stop()
                codec.release()
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

    fun encodeFlac(pcm: PcmData, outputFile: File, onProgress: (Float) -> Unit = {}) {
        if (hasEncoderForMime(MediaFormat.MIMETYPE_AUDIO_FLAC)) {
            encodeGenericCodec(pcm, outputFile, MediaFormat.MIMETYPE_AUDIO_FLAC, 0, onProgress)
        } else {
            encodeWav(pcm, outputFile, onProgress)
        }
    }

    private fun encodeGenericCodec(
        pcm: PcmData,
        outputFile: File,
        mime: String,
        bitrateKbps: Int = 192,
        onProgress: (Float) -> Unit = {}
    ) {
        val format = MediaFormat.createAudioFormat(mime, pcm.sampleRate, pcm.channelCount).apply {
            if (bitrateKbps > 0) {
                setInteger(MediaFormat.KEY_BIT_RATE, bitrateKbps * 1000)
            }
        }

        val codec = MediaCodec.createEncoderByType(mime)
        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        codec.start()

        val bufferInfo = MediaCodec.BufferInfo()
        val pcmByteBuf = ByteBuffer.allocate(pcm.samples.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        pcmByteBuf.asShortBuffer().put(pcm.samples)
        pcmByteBuf.flip()

        FileOutputStream(outputFile).use { fos ->
            var isEos = false
            while (!isEos) {
                val inIndex = codec.dequeueInputBuffer(TIMEOUT_US)
                if (inIndex >= 0) {
                    val inBuffer = codec.getInputBuffer(inIndex)
                    if (inBuffer != null) {
                        inBuffer.clear()
                        val bytesToCopy = minOf(inBuffer.capacity(), pcmByteBuf.remaining())
                        if (bytesToCopy > 0) {
                            val temp = ByteArray(bytesToCopy)
                            pcmByteBuf.get(temp)
                            inBuffer.put(temp)
                            codec.queueInputBuffer(inIndex, 0, bytesToCopy, 0L, 0)
                        } else {
                            codec.queueInputBuffer(inIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        }
                    }
                }

                var outIndex = codec.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)
                while (outIndex >= 0) {
                    val outBuffer = codec.getOutputBuffer(outIndex)
                    if (outBuffer != null && bufferInfo.size > 0) {
                        val chunk = ByteArray(bufferInfo.size)
                        outBuffer.position(bufferInfo.offset)
                        outBuffer.get(chunk)
                        fos.write(chunk)
                    }
                    codec.releaseOutputBuffer(outIndex, false)
                    if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        isEos = true
                        break
                    }
                    outIndex = codec.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)
                }
            }
        }
        codec.stop()
        codec.release()
        onProgress(1.0f)
    }
}

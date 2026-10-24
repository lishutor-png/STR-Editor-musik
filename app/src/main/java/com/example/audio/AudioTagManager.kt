package com.example.audio

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.charset.StandardCharsets
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

object AudioTagManager {
    private const val TAG = "AudioTagManager"

    enum class CoverFitMode {
        FILL,
        FIT,
        CUSTOM
    }

    data class PresetCoverInfo(
        val id: Int,
        val name: String,
        val subtitle: String,
        val primaryColorHex: Long
    )

    val PRESET_COVERS = listOf(
        PresetCoverInfo(0, "Sunset Melodi", "Gradien Hangat & Piringan Hitam", 0xFFFF6F00),
        PresetCoverInfo(1, "Neon Cyber", "Gelombang Vokal & Spektrum Ungu", 0xFF7C4DFF),
        PresetCoverInfo(2, "Akustik Alami", "Nuansa Hijau Botani & Akustik", 0xFF00796B),
        PresetCoverInfo(3, "Golden Studio", "Kemewahan Hitam & Emas Studio", 0xFFFFD700)
    )

    suspend fun readMetadata(
        context: Context,
        uri: Uri? = null,
        file: File? = null
    ): SongMetadata = withContext(Dispatchers.IO) {
        var title = ""
        var artist = ""
        var album = ""
        var albumArtist = ""
        var genre = ""
        var year = ""
        var trackNumber = ""
        var composer = ""
        var comment = ""
        var coverBytes: ByteArray? = null
        var coverMime = "image/jpeg"
        var durationMs = 0L
        var bitrate = 0
        var sampleRate = 44100
        var format = "MP3"
        val fileName = file?.name ?: uri?.lastPathSegment ?: "Unknown"

        // 1. First try native ID3v2 extraction from File if MP3
        if (file != null && file.exists() && isMp3File(file)) {
            try {
                val parsed = extractId3v2Tags(file)
                if (parsed != null) {
                    if (parsed.title.isNotBlank()) title = parsed.title
                    if (parsed.artist.isNotBlank()) artist = parsed.artist
                    if (parsed.album.isNotBlank()) album = parsed.album
                    if (parsed.albumArtist.isNotBlank()) albumArtist = parsed.albumArtist
                    if (parsed.genre.isNotBlank()) genre = parsed.genre
                    if (parsed.year.isNotBlank()) year = parsed.year
                    if (parsed.trackNumber.isNotBlank()) trackNumber = parsed.trackNumber
                    if (parsed.composer.isNotBlank()) composer = parsed.composer
                    if (parsed.comment.isNotBlank()) comment = parsed.comment
                    if (parsed.coverArtBytes != null && parsed.coverArtBytes.isNotEmpty()) {
                        coverBytes = parsed.coverArtBytes
                        coverMime = parsed.coverMimeType
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error in manual ID3v2 extraction: ${e.message}")
            }
        }

        // 2. Read standard metadata & fallback via MediaMetadataRetriever
        val retriever = MediaMetadataRetriever()
        try {
            if (file != null && file.exists()) {
                retriever.setDataSource(file.absolutePath)
            } else if (uri != null) {
                retriever.setDataSource(context, uri)
            }

            if (title.isBlank()) {
                title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                    ?: fileName.substringBeforeLast(".")
            }
            if (artist.isBlank()) {
                artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                    ?: "Artis Tidak Diketahui"
            }
            if (album.isBlank()) {
                album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM) ?: ""
            }
            if (albumArtist.isBlank()) {
                albumArtist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST) ?: ""
            }
            if (genre.isBlank()) {
                genre = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_GENRE) ?: ""
            }
            if (year.isBlank()) {
                year = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_YEAR)
                    ?: retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DATE) ?: ""
            }
            if (composer.isBlank()) {
                composer = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_COMPOSER) ?: ""
            }

            val durStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            if (durStr != null) {
                durationMs = durStr.toLongOrNull() ?: 0L
            }
            val bitStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)
            if (bitStr != null) {
                bitrate = (bitStr.toIntOrNull() ?: 0) / 1000
            }
            val mime = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE) ?: ""
            format = when {
                mime.contains("mpeg", ignoreCase = true) || fileName.endsWith(".mp3", true) -> "MP3"
                mime.contains("mp4", ignoreCase = true) || fileName.endsWith(".m4a", true) -> "M4A"
                mime.contains("wav", ignoreCase = true) || fileName.endsWith(".wav", true) -> "WAV"
                mime.contains("flac", ignoreCase = true) || fileName.endsWith(".flac", true) -> "FLAC"
                else -> "MP3"
            }

            // Fallback cover art from retriever only if not WAV and container indicates presence
            if ((coverBytes == null || coverBytes.isEmpty()) && format != "WAV" && !fileName.endsWith(".wav", true)) {
                try {
                    val hasImage = retriever.extractMetadata(32) // METADATA_KEY_HAS_IMAGE (API 28+)
                    if (hasImage == "yes") {
                        val pic = retriever.embeddedPicture
                        if (pic != null && pic.isNotEmpty()) {
                            coverBytes = pic
                            coverMime = detectMimeType(pic)
                        }
                    }
                } catch (e: Exception) {
                    // ignore
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "MediaMetadataRetriever error: ${e.message}")
        } finally {
            try {
                retriever.release()
            } catch (e: Exception) {
                // ignore
            }
        }

        SongMetadata(
            title = title,
            artist = artist,
            album = album,
            albumArtist = albumArtist,
            genre = genre,
            year = year,
            trackNumber = trackNumber,
            composer = composer,
            comment = comment,
            coverArtBytes = coverBytes,
            coverMimeType = coverMime,
            durationMs = durationMs,
            bitrateKbps = bitrate,
            sampleRate = sampleRate,
            format = format,
            originalFileName = fileName,
            originalFilePath = file?.absolutePath,
            originalUri = uri
        )
    }

    fun isMp3File(file: File): Boolean {
        if (!file.exists() || file.length() < 10) return false
        if (file.name.endsWith(".mp3", ignoreCase = true)) return true
        try {
            RandomAccessFile(file, "r").use { raf ->
                val header = ByteArray(3)
                raf.readFully(header)
                if (header[0] == 'I'.code.toByte() && header[1] == 'D'.code.toByte() && header[2] == '3'.code.toByte()) {
                    return true
                }
                // Check sync byte
                raf.seek(0)
                val b1 = raf.read()
                val b2 = raf.read()
                if (b1 == 0xFF && (b2 and 0xE0) == 0xE0) {
                    return true
                }
            }
        } catch (e: Exception) {
            // ignore
        }
        return false
    }

    fun detectMimeType(bytes: ByteArray): String {
        if (bytes.size >= 8) {
            if (bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() && bytes[2] == 0x4E.toByte() && bytes[3] == 0x47.toByte()) {
                return "image/png"
            }
            if (bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte()) {
                return "image/jpeg"
            }
        }
        return "image/jpeg"
    }

    /**
     * Lossless MP3 ID3v2.3 tag writer conforming to the exact specification
     * demonstrated by reference MP3 (Lish_-_Cantik_db52f43d.mp3).
     */
    suspend fun writeMp3Tags(
        sourceFile: File,
        outputFile: File,
        metadata: SongMetadata
    ): Boolean = withContext(Dispatchers.IO) {
        var tempFile: File? = null
        try {
            if (!sourceFile.exists()) {
                Log.e(TAG, "Source file does not exist: ${sourceFile.absolutePath}")
                return@withContext false
            }

            // Determine MPEG audio start and end offsets
            var audioStart = 0L
            var audioEnd = sourceFile.length()

            RandomAccessFile(sourceFile, "r").use { raf ->
                val magic = ByteArray(3)
                raf.readFully(magic)
                if (magic[0] == 'I'.code.toByte() && magic[1] == 'D'.code.toByte() && magic[2] == '3'.code.toByte()) {
                    val version = raf.read()
                    val revision = raf.read()
                    val flags = raf.read()
                    val sizeBytes = ByteArray(4)
                    raf.readFully(sizeBytes)
                    val tagSize = parseSynchsafe(sizeBytes, 0)
                    audioStart = 10L + tagSize
                    if ((flags and 0x10) != 0) { // footer present
                        audioStart += 10L
                    }
                }

                // Check for ID3v1 tag at the end (128 bytes)
                if (sourceFile.length() - audioStart > 128) {
                    raf.seek(sourceFile.length() - 128)
                    val id3v1Tag = ByteArray(3)
                    raf.readFully(id3v1Tag)
                    if (id3v1Tag[0] == 'T'.code.toByte() && id3v1Tag[1] == 'A'.code.toByte() && id3v1Tag[2] == 'G'.code.toByte()) {
                        audioEnd = sourceFile.length() - 128
                    }
                }
            }

            if (audioStart >= audioEnd || audioStart < 0) {
                audioStart = 0L
                audioEnd = sourceFile.length()
            }

            // Build ID3v2.3 frames
            val framesStream = ByteArrayOutputStream()

            // 1. Text frames
            if (metadata.title.isNotBlank()) {
                framesStream.write(buildTextFrame("TIT2", metadata.title))
            }
            if (metadata.artist.isNotBlank()) {
                framesStream.write(buildTextFrame("TPE1", metadata.artist))
            }
            if (metadata.album.isNotBlank()) {
                framesStream.write(buildTextFrame("TALB", metadata.album))
            }
            if (metadata.albumArtist.isNotBlank()) {
                framesStream.write(buildTextFrame("TPE2", metadata.albumArtist))
            }
            if (metadata.genre.isNotBlank()) {
                framesStream.write(buildTextFrame("TCON", metadata.genre))
            }
            if (metadata.year.isNotBlank()) {
                framesStream.write(buildTextFrame("TYER", metadata.year))
            }
            if (metadata.trackNumber.isNotBlank()) {
                framesStream.write(buildTextFrame("TRCK", metadata.trackNumber))
            }
            if (metadata.composer.isNotBlank()) {
                framesStream.write(buildTextFrame("TCOM", metadata.composer))
            }
            if (metadata.comment.isNotBlank()) {
                framesStream.write(buildCommentFrame(metadata.comment))
            }

            // Software / encoder frame (TSSE)
            framesStream.write(buildTextFrame("TSSE", "Lish Studio Record"))

            // 2. APIC picture frame (Album artwork)
            val coverBytes = metadata.coverArtBytes
            if (coverBytes != null && coverBytes.isNotEmpty()) {
                val picFrame = buildPictureFrame(coverBytes, metadata.coverMimeType)
                framesStream.write(picFrame)
            }

            val framesData = framesStream.toByteArray()

            // Padding: 1024 bytes of nulls (standard in MP3 tag editors, enables instant re-tagging)
            val paddingSize = 1024
            val paddingBytes = ByteArray(paddingSize)

            val totalTagPayloadSize = framesData.size + paddingSize
            val synchsafeSize = toSynchsafe(totalTagPayloadSize)

            // ID3v2.3 10-byte header: "ID3" + version 3 + revision 0 + flags 0 + 4-byte synchsafe size
            val id3Header = byteArrayOf(
                'I'.code.toByte(), 'D'.code.toByte(), '3'.code.toByte(),
                0x03.toByte(), // ID3v2.3
                0x00.toByte(), // revision
                0x00.toByte(), // flags
                synchsafeSize[0], synchsafeSize[1], synchsafeSize[2], synchsafeSize[3]
            )

            // Write to a temporary file first
            val parentDir = outputFile.parentFile ?: sourceFile.parentFile ?: File("/tmp")
            if (!parentDir.exists()) {
                parentDir.mkdirs()
            }
            tempFile = File(parentDir, "tmp_lish_tag_${System.currentTimeMillis()}_${Random.nextInt(1000, 9999)}.mp3")

            FileOutputStream(tempFile).use { fos ->
                fos.write(id3Header)
                fos.write(framesData)
                fos.write(paddingBytes)

                // Copy audio stream from source
                RandomAccessFile(sourceFile, "r").use { raf ->
                    raf.seek(audioStart)
                    var remaining = audioEnd - audioStart
                    val buffer = ByteArray(65536)
                    while (remaining > 0) {
                        val toRead = min(remaining, buffer.size.toLong()).toInt()
                        val read = raf.read(buffer, 0, toRead)
                        if (read <= 0) break
                        fos.write(buffer, 0, read)
                        remaining -= read
                    }
                }
                fos.flush()
            }

            // Replace target file
            if (outputFile.exists()) {
                outputFile.delete()
            }
            val renamed = tempFile.renameTo(outputFile)
            if (!renamed) {
                // Fallback copy
                tempFile.copyTo(outputFile, overwrite = true)
                tempFile.delete()
            }
            Log.d(TAG, "Successfully wrote ID3v2.3 tags to ${outputFile.absolutePath} (size: ${outputFile.length()} bytes)")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed writing MP3 tags: ${e.message}", e)
            tempFile?.delete()
            false
        }
    }

    /**
     * Builds ID3v2.3 text frame:
     * - 4 bytes ID
     * - 4 bytes size (32-bit big-endian)
     * - 2 bytes flags (00 00)
     * - payload: encoding byte + text bytes (with ISO-8859-1 for Latin/ASCII with null terminator,
     *   or UTF-16 with BOM if non-ASCII)
     */
    fun buildTextFrame(frameId: String, text: String): ByteArray {
        if (text.isBlank()) return ByteArray(0)

        val bos = ByteArrayOutputStream()
        val isAsciiOrLatin = text.all { it.code in 0..255 }

        if (isAsciiOrLatin) {
            // Encoding 0 = ISO-8859-1
            bos.write(0x00)
            bos.write(text.toByteArray(StandardCharsets.ISO_8859_1))
            bos.write(0x00) // Null terminator
        } else {
            // Encoding 1 = UTF-16 with BOM
            bos.write(0x01)
            bos.write(0xFF)
            bos.write(0xFE)
            bos.write(text.toByteArray(StandardCharsets.UTF_16LE))
            bos.write(0x00)
            bos.write(0x00)
        }

        val payload = bos.toByteArray()
        val header = ByteArrayOutputStream()
        header.write(frameId.toByteArray(StandardCharsets.US_ASCII))
        header.write(toInt32Bytes(payload.size))
        header.write(byteArrayOf(0x00, 0x00)) // flags
        header.write(payload)
        return header.toByteArray()
    }

    /**
     * Builds ID3v2.3 APIC picture frame:
     * Exact binary match to reference MP3:
     * - Frame ID: "APIC" (4 bytes)
     * - Frame size: 32-bit big-endian integer (normal non-synchsafe in ID3v2.3)
     * - Flags: 00 00 (2 bytes)
     * - Payload:
     *     - Byte 0: encoding 0x00 (ISO-8859-1)
     *     - MIME string with null terminator: "image/jpeg\x00" (or "image/png\x00")
     *     - Picture type: 0x03 (Cover front)
     *     - Description with null terminator: "Cover\x00"
     *     - Raw JPEG / PNG image bytes
     */
    fun buildPictureFrame(imageBytes: ByteArray, mimeType: String): ByteArray {
        if (imageBytes.isEmpty()) return ByteArray(0)

        val normalizedMime = if (mimeType.contains("png", ignoreCase = true)) "image/png" else "image/jpeg"

        val payloadStream = ByteArrayOutputStream()
        // 1. Text encoding byte: 0x00 (ISO-8859-1)
        payloadStream.write(0x00)

        // 2. MIME type string + null terminator
        payloadStream.write(normalizedMime.toByteArray(StandardCharsets.US_ASCII))
        payloadStream.write(0x00)

        // 3. Picture type: 0x03 (Cover / front)
        payloadStream.write(0x03)

        // 4. Description: "Cover" + null terminator (6 bytes: 'C','o','v','e','r',0x00)
        payloadStream.write("Cover".toByteArray(StandardCharsets.ISO_8859_1))
        payloadStream.write(0x00)

        // 5. Binary image bytes
        payloadStream.write(imageBytes)

        val payload = payloadStream.toByteArray()

        val frameHeader = ByteArrayOutputStream()
        // Frame ID
        frameHeader.write("APIC".toByteArray(StandardCharsets.US_ASCII))
        // 4-byte size (32-bit big-endian integer)
        frameHeader.write(toInt32Bytes(payload.size))
        // 2-byte flags
        frameHeader.write(byteArrayOf(0x00, 0x00))
        // Payload
        frameHeader.write(payload)

        return frameHeader.toByteArray()
    }

    /**
     * Builds ID3v2.3 COMM comment frame.
     */
    fun buildCommentFrame(comment: String): ByteArray {
        if (comment.isBlank()) return ByteArray(0)

        val payloadStream = ByteArrayOutputStream()
        val isAsciiOrLatin = comment.all { it.code in 0..255 }

        if (isAsciiOrLatin) {
            payloadStream.write(0x00) // ISO-8859-1
            payloadStream.write("eng".toByteArray(StandardCharsets.US_ASCII))
            payloadStream.write(0x00) // Empty short description
            payloadStream.write(comment.toByteArray(StandardCharsets.ISO_8859_1))
            payloadStream.write(0x00)
        } else {
            payloadStream.write(0x01) // UTF-16
            payloadStream.write("eng".toByteArray(StandardCharsets.US_ASCII))
            payloadStream.write(0xFF)
            payloadStream.write(0xFE)
            payloadStream.write(0x00)
            payloadStream.write(0x00)
            payloadStream.write(0xFF)
            payloadStream.write(0xFE)
            payloadStream.write(comment.toByteArray(StandardCharsets.UTF_16LE))
            payloadStream.write(0x00)
            payloadStream.write(0x00)
        }

        val payload = payloadStream.toByteArray()
        val frameHeader = ByteArrayOutputStream()
        frameHeader.write("COMM".toByteArray(StandardCharsets.US_ASCII))
        frameHeader.write(toInt32Bytes(payload.size))
        frameHeader.write(byteArrayOf(0x00, 0x00))
        frameHeader.write(payload)
        return frameHeader.toByteArray()
    }

    private fun toInt32Bytes(value: Int): ByteArray {
        return byteArrayOf(
            ((value shr 24) and 0xFF).toByte(),
            ((value shr 16) and 0xFF).toByte(),
            ((value shr 8) and 0xFF).toByte(),
            (value and 0xFF).toByte()
        )
    }

    private fun toSynchsafe(size: Int): ByteArray {
        return byteArrayOf(
            ((size shr 21) and 0x7F).toByte(),
            ((size shr 14) and 0x7F).toByte(),
            ((size shr 7) and 0x7F).toByte(),
            (size and 0x7F).toByte()
        )
    }

    private fun parseSynchsafe(b: ByteArray, offset: Int): Int {
        return ((b[offset].toInt() and 0x7F) shl 21) or
                ((b[offset + 1].toInt() and 0x7F) shl 14) or
                ((b[offset + 2].toInt() and 0x7F) shl 7) or
                (b[offset + 3].toInt() and 0x7F)
    }

    private fun parseNormalInt32(b: ByteArray, offset: Int): Int {
        return ((b[offset].toInt() and 0xFF) shl 24) or
                ((b[offset + 1].toInt() and 0xFF) shl 16) or
                ((b[offset + 2].toInt() and 0xFF) shl 8) or
                (b[offset + 3].toInt() and 0xFF)
    }

    fun extractCoverFromFile(file: File): ByteArray? {
        if (!file.exists() || !isMp3File(file)) return null
        return extractId3v2Tags(file)?.coverArtBytes
    }

    /**
     * Extracts ID3v2.3/ID3v2.4 tags and APIC cover directly from the MP3 file header.
     */
    fun extractId3v2Tags(file: File): SongMetadata? {
        if (!file.exists() || file.length() < 10) return null

        RandomAccessFile(file, "r").use { raf ->
            val header = ByteArray(10)
            raf.readFully(header)
            if (header[0] != 'I'.code.toByte() || header[1] != 'D'.code.toByte() || header[2] != '3'.code.toByte()) {
                return null
            }
            val majorVersion = header[3].toInt()
            val totalTagSize = parseSynchsafe(header, 6)
            if (totalTagSize <= 0 || totalTagSize > 30 * 1024 * 1024) return null

            val tagBytes = ByteArray(totalTagSize)
            raf.readFully(tagBytes)

            var offset = 0
            var title = ""
            var artist = ""
            var album = ""
            var albumArtist = ""
            var genre = ""
            var year = ""
            var trackNumber = ""
            var composer = ""
            var comment = ""
            var coverBytes: ByteArray? = null
            var coverMime = "image/jpeg"

            while (offset + 10 <= totalTagSize) {
                // If we encounter padding null bytes
                if (tagBytes[offset] == 0.toByte()) break

                val frameId = String(tagBytes, offset, 4, StandardCharsets.US_ASCII)
                val frameSize = if (majorVersion == 4) {
                    parseSynchsafe(tagBytes, offset + 4)
                } else {
                    parseNormalInt32(tagBytes, offset + 4)
                }

                offset += 10
                if (frameSize <= 0 || offset + frameSize > totalTagSize) {
                    break
                }

                val frameData = ByteArray(frameSize)
                System.arraycopy(tagBytes, offset, frameData, 0, frameSize)
                offset += frameSize

                when (frameId) {
                    "TIT2" -> title = decodeTextFrame(frameData)
                    "TPE1" -> artist = decodeTextFrame(frameData)
                    "TALB" -> album = decodeTextFrame(frameData)
                    "TPE2" -> albumArtist = decodeTextFrame(frameData)
                    "TCON" -> genre = decodeTextFrame(frameData)
                    "TYER", "TDRC" -> year = decodeTextFrame(frameData)
                    "TRCK" -> trackNumber = decodeTextFrame(frameData)
                    "TCOM" -> composer = decodeTextFrame(frameData)
                    "COMM" -> comment = decodeCommentFrame(frameData)
                    "APIC" -> {
                        val parsedCover = parseApicFrame(frameData)
                        if (parsedCover != null) {
                            coverBytes = parsedCover.first
                            coverMime = parsedCover.second
                        }
                    }
                }
            }

            return SongMetadata(
                title = title,
                artist = artist,
                album = album,
                albumArtist = albumArtist,
                genre = genre,
                year = year,
                trackNumber = trackNumber,
                composer = composer,
                comment = comment,
                coverArtBytes = coverBytes,
                coverMimeType = coverMime
            )
        }
    }

    private fun decodeTextFrame(data: ByteArray): String {
        if (data.isEmpty()) return ""
        val encoding = data[0].toInt()
        val textBytes = if (data.size > 1) data.copyOfRange(1, data.size) else ByteArray(0)
        return try {
            when (encoding) {
                0 -> String(textBytes, StandardCharsets.ISO_8859_1).trimEnd('\u0000', ' ')
                1 -> {
                    // UTF-16 with BOM
                    String(textBytes, StandardCharsets.UTF_16).trimEnd('\u0000', ' ')
                }
                2 -> String(textBytes, StandardCharsets.UTF_16BE).trimEnd('\u0000', ' ')
                3 -> String(textBytes, StandardCharsets.UTF_8).trimEnd('\u0000', ' ')
                else -> String(textBytes, StandardCharsets.ISO_8859_1).trimEnd('\u0000', ' ')
            }
        } catch (e: Exception) {
            ""
        }
    }

    private fun decodeCommentFrame(data: ByteArray): String {
        if (data.size < 5) return ""
        val encoding = data[0].toInt()
        // skip 3-byte language
        var textOffset = 4
        if (encoding == 0 || encoding == 3) {
            while (textOffset < data.size && data[textOffset] != 0.toByte()) {
                textOffset++
            }
            textOffset++ // skip null
        } else {
            while (textOffset + 1 < data.size && !(data[textOffset] == 0.toByte() && data[textOffset + 1] == 0.toByte())) {
                textOffset += 2
            }
            textOffset += 2
        }

        if (textOffset >= data.size) return ""
        val commentBytes = data.copyOfRange(textOffset, data.size)
        return try {
            when (encoding) {
                0 -> String(commentBytes, StandardCharsets.ISO_8859_1).trimEnd('\u0000', ' ')
                1 -> String(commentBytes, StandardCharsets.UTF_16).trimEnd('\u0000', ' ')
                2 -> String(commentBytes, StandardCharsets.UTF_16BE).trimEnd('\u0000', ' ')
                3 -> String(commentBytes, StandardCharsets.UTF_8).trimEnd('\u0000', ' ')
                else -> String(commentBytes, StandardCharsets.ISO_8859_1).trimEnd('\u0000', ' ')
            }
        } catch (e: Exception) {
            ""
        }
    }

    private fun parseApicFrame(data: ByteArray): Pair<ByteArray, String>? {
        if (data.size < 5) return null
        var ptr = 0
        val encoding = data[ptr++].toInt()

        // 1. Read MIME type
        val mimeStart = ptr
        while (ptr < data.size && data[ptr] != 0.toByte()) {
            ptr++
        }
        val mime = if (ptr > mimeStart) {
            String(data, mimeStart, ptr - mimeStart, StandardCharsets.US_ASCII)
        } else {
            "image/jpeg"
        }
        ptr++ // skip null terminator

        if (ptr >= data.size) return null

        // 2. Picture type (1 byte)
        val picType = data[ptr++].toInt()

        // 3. Description string (terminated by 0x00 or 0x00 0x00)
        if (encoding == 0 || encoding == 3) {
            while (ptr < data.size && data[ptr] != 0.toByte()) {
                ptr++
            }
            ptr++
        } else {
            while (ptr + 1 < data.size && !(data[ptr] == 0.toByte() && data[ptr + 1] == 0.toByte())) {
                ptr += 2
            }
            ptr += 2
        }

        if (ptr >= data.size) return null

        // 4. Remaining bytes are the raw image bytes
        val imageBytes = data.copyOfRange(ptr, data.size)
        val detectedMime = if (mime.isNotBlank() && mime.contains("/")) mime else detectMimeType(imageBytes)
        return Pair(imageBytes, detectedMime)
    }

    /**
     * Loads a downsampled Bitmap from URI without OutOfMemory.
     */
    suspend fun loadBitmapFromUri(
        context: Context,
        uri: Uri,
        maxDimension: Int = 1000
    ): Bitmap? = withContext(Dispatchers.IO) {
        try {
            var input = context.contentResolver.openInputStream(uri) ?: return@withContext null
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeStream(input, null, options)
            input.close()

            var inSampleSize = 1
            var w = options.outWidth
            var h = options.outHeight
            while (w > maxDimension || h > maxDimension) {
                inSampleSize *= 2
                w /= 2
                h /= 2
            }

            input = context.contentResolver.openInputStream(uri) ?: return@withContext null
            val decodeOptions = BitmapFactory.Options().apply {
                this.inSampleSize = inSampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val bmp = BitmapFactory.decodeStream(input, null, decodeOptions)
            input.close()
            bmp
        } catch (e: Exception) {
            Log.e(TAG, "Failed loading bitmap from uri: ${e.message}")
            null
        }
    }

    fun loadBitmapFromBytes(bytes: ByteArray, maxDimension: Int = 1000): Bitmap? {
        try {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)

            var inSampleSize = 1
            var w = options.outWidth
            var h = options.outHeight
            while (w > maxDimension || h > maxDimension) {
                inSampleSize *= 2
                w /= 2
                h /= 2
            }

            val decodeOptions = BitmapFactory.Options().apply {
                this.inSampleSize = inSampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOptions)
        } catch (e: Exception) {
            return null
        }
    }

    /**
     * Crops, fits, rotates, and scales a Bitmap into a perfect square album artwork.
     */
    fun createCoverBitmap(
        source: Bitmap,
        mode: CoverFitMode = CoverFitMode.FILL,
        zoom: Float = 1.0f,
        panX: Float = 0f,
        panY: Float = 0f,
        rotationDegrees: Float = 0f,
        viewportSizePx: Float = 300f,
        outputDimension: Int = 600
    ): Bitmap {
        val output = Bitmap.createBitmap(outputDimension, outputDimension, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        canvas.drawColor(Color.BLACK)

        val srcW = source.width.toFloat()
        val srcH = source.height.toFloat()

        when (mode) {
            CoverFitMode.FILL -> {
                val scale = max(outputDimension / srcW, outputDimension / srcH)
                val scaledW = srcW * scale
                val scaledH = srcH * scale
                val dx = (outputDimension - scaledW) / 2f
                val dy = (outputDimension - scaledH) / 2f

                val matrix = Matrix().apply {
                    postScale(scale, scale)
                    postTranslate(dx, dy)
                }
                val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
                canvas.drawBitmap(source, matrix, paint)
            }
            CoverFitMode.FIT -> {
                val scale = min(outputDimension / srcW, outputDimension / srcH)
                val scaledW = srcW * scale
                val scaledH = srcH * scale
                val dx = (outputDimension - scaledW) / 2f
                val dy = (outputDimension - scaledH) / 2f

                val matrix = Matrix().apply {
                    postScale(scale, scale)
                    postTranslate(dx, dy)
                }
                val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
                canvas.drawBitmap(source, matrix, paint)
            }
            CoverFitMode.CUSTOM -> {
                val baseScale = max(outputDimension / srcW, outputDimension / srcH)
                val effectiveScale = baseScale * zoom
                val scaledW = srcW * effectiveScale
                val scaledH = srcH * effectiveScale

                val ratio = outputDimension / viewportSizePx
                val transX = ((outputDimension - scaledW) / 2f) + (panX * ratio)
                val transY = ((outputDimension - scaledH) / 2f) + (panY * ratio)

                val matrix = Matrix().apply {
                    postScale(effectiveScale, effectiveScale)
                    postRotate(rotationDegrees, scaledW / 2f, scaledH / 2f)
                    postTranslate(transX, transY)
                }
                val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
                canvas.drawBitmap(source, matrix, paint)
            }
        }

        return output
    }

    /**
     * Encodes bitmap into high quality JPEG byte array.
     */
    fun bitmapToCoverBytes(bitmap: Bitmap, quality: Int = 90): Pair<ByteArray, String> {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, stream)
        return Pair(stream.toByteArray(), "image/jpeg")
    }

    /**
     * Generates modern studio preset artwork.
     */
    fun generatePresetCover(presetIndex: Int, dimension: Int = 600): Pair<ByteArray, String> {
        val bmp = Bitmap.createBitmap(dimension, dimension, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val rect = RectF(0f, 0f, dimension.toFloat(), dimension.toFloat())

        val (c1, c2, c3, title, subtitle) = when (presetIndex) {
            0 -> listOf(Color.parseColor("#FF6F00"), Color.parseColor("#D84315"), Color.parseColor("#212121"), "SUNSET MELODY", "Lish Studio Record")
            1 -> listOf(Color.parseColor("#7C4DFF"), Color.parseColor("#00E5FF"), Color.parseColor("#121212"), "NEON CYBER", "High-Definition Vocal")
            2 -> listOf(Color.parseColor("#00796B"), Color.parseColor("#43A047"), Color.parseColor("#1B2A1E"), "ACOUSTIC NATURE", "Pure Studio Master")
            else -> listOf(Color.parseColor("#FFD700"), Color.parseColor("#FF8F00"), Color.parseColor("#1A1A1A"), "GOLDEN STUDIO", "Masterpiece Edition")
        }

        // Gradient background
        val grad = LinearGradient(0f, 0f, dimension.toFloat(), dimension.toFloat(), intArrayOf(c1 as Int, c2 as Int, c3 as Int), null, Shader.TileMode.CLAMP)
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = grad }
        canvas.drawRect(rect, bgPaint)

        // Vinyl disk illustration
        val center = dimension / 2f
        val discPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(190, 20, 20, 20)
            style = Paint.Style.FILL
        }
        canvas.drawCircle(center, center, dimension * 0.35f, discPaint)

        // Grooves
        val groovePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(60, 255, 255, 255)
            style = Paint.Style.STROKE
            strokeWidth = 2f
        }
        for (r in listOf(0.32f, 0.28f, 0.24f, 0.20f)) {
            canvas.drawCircle(center, center, dimension * r, groovePaint)
        }

        // Center label
        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = c1 as Int
            style = Paint.Style.FILL
        }
        canvas.drawCircle(center, center, dimension * 0.12f, labelPaint)
        val holePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            style = Paint.Style.FILL
        }
        canvas.drawCircle(center, center, dimension * 0.03f, holePaint)

        // Title and Subtitle
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = dimension * 0.065f
            textAlign = Paint.Align.CENTER
            isFakeBoldText = true
            setShadowLayer(8f, 0f, 4f, Color.argb(180, 0, 0, 0))
        }
        canvas.drawText(title as String, center, dimension * 0.82f, textPaint)

        val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(220, 255, 255, 255)
            textSize = dimension * 0.038f
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(subtitle as String, center, dimension * 0.88f, subPaint)

        return bitmapToCoverBytes(bmp, 92)
    }
}

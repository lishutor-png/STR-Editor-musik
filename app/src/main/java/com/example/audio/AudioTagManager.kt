package com.example.audio

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream

object AudioTagManager {
    private const val TAG = "AudioTagManager"

    /**
     * Extracts rich metadata including embedded cover art from an audio file or URI.
     */
    suspend fun readMetadata(context: Context, uri: Uri?, file: File?): SongMetadata = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        var title = ""
        var artist = ""
        var album = ""
        var albumArtist = ""
        var genre = ""
        var year = ""
        var trackNumber = ""
        var composer = ""
        var durationMs = 0L
        var bitrate = 0
        var sampleRate = 44100
        var coverBytes: ByteArray? = null
        var coverMime = "image/jpeg"
        var format = "MP3"
        var originalFileName = ""

        try {
            if (file != null) {
                retriever.setDataSource(file.absolutePath)
                format = file.extension.uppercase()
                originalFileName = file.name
                title = file.nameWithoutExtension
            } else if (uri != null) {
                retriever.setDataSource(context, uri)
                originalFileName = uri.lastPathSegment?.substringAfterLast('/') ?: "audio_track"
                title = originalFileName.substringBeforeLast('.')
                val ext = originalFileName.substringAfterLast('.', "")
                if (ext.isNotBlank()) format = ext.uppercase()
            }

            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)?.let {
                if (it.isNotBlank()) title = it.trim()
            }
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)?.let {
                if (it.isNotBlank()) artist = it.trim()
            }
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)?.let {
                if (it.isNotBlank()) album = it.trim()
            }
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST)?.let {
                if (it.isNotBlank()) albumArtist = it.trim()
            }
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_GENRE)?.let {
                if (it.isNotBlank()) genre = it.trim()
            }
            val rawYear = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_YEAR)
                ?: retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DATE)
            rawYear?.let {
                val yearMatch = Regex("""\b(19\d\d|20\d\d)\b""").find(it)
                year = yearMatch?.value ?: it.take(4)
            }
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER)?.let {
                trackNumber = it.substringBefore('/')
            }
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_COMPOSER)?.let {
                composer = it.trim()
            }
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.let {
                durationMs = it.toLongOrNull() ?: 0L
            }
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.let {
                bitrate = (it.toIntOrNull() ?: 0) / 1000
            }
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_SAMPLERATE)?.let {
                sampleRate = it.toIntOrNull() ?: 44100
            }

            val picture = retriever.embeddedPicture
            if (picture != null && picture.isNotEmpty()) {
                coverBytes = picture
                coverMime = if (picture.size >= 8 &&
                    picture[0] == 0x89.toByte() && picture[1] == 0x50.toByte() &&
                    picture[2] == 0x4E.toByte() && picture[3] == 0x47.toByte()
                ) {
                    "image/png"
                } else {
                    "image/jpeg"
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error extracting metadata: ${e.message}")
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
            coverArtBytes = coverBytes,
            coverMimeType = coverMime,
            durationMs = durationMs,
            bitrateKbps = bitrate,
            sampleRate = sampleRate,
            format = format,
            originalFileName = originalFileName,
            originalFilePath = file?.absolutePath,
            originalUri = uri
        )
    }

    /**
     * Resizes and compresses an image from URI for use as embedded album cover art.
     */
    suspend fun processImageForCover(
        context: Context,
        imageUri: Uri,
        maxDimension: Int = 800
    ): Pair<ByteArray, String>? = withContext(Dispatchers.IO) {
        try {
            // First pass: decode bounds
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(imageUri)?.use { input ->
                BitmapFactory.decodeStream(input, null, options)
            } ?: return@withContext null

            val width = options.outWidth
            val height = options.outHeight
            if (width <= 0 || height <= 0) return@withContext null

            var inSampleSize = 1
            while (width / (inSampleSize * 2) >= maxDimension && height / (inSampleSize * 2) >= maxDimension) {
                inSampleSize *= 2
            }

            // Second pass: decode actual bitmap
            val decodeOptions = BitmapFactory.Options().apply {
                this.inSampleSize = inSampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val bitmap = context.contentResolver.openInputStream(imageUri)?.use { input ->
                BitmapFactory.decodeStream(input, null, decodeOptions)
            } ?: return@withContext null

            // Scale if still larger than maxDimension
            val scaledBitmap = if (bitmap.width > maxDimension || bitmap.height > maxDimension) {
                val ratio = bitmap.width.toFloat() / bitmap.height.toFloat()
                val targetW: Int
                val targetH: Int
                if (ratio > 1f) {
                    targetW = maxDimension
                    targetH = (maxDimension / ratio).toInt().coerceAtLeast(1)
                } else {
                    targetH = maxDimension
                    targetW = (maxDimension * ratio).toInt().coerceAtLeast(1)
                }
                val resized = Bitmap.createScaledBitmap(bitmap, targetW, targetH, true)
                if (resized != bitmap) bitmap.recycle()
                resized
            } else {
                bitmap
            }

            val bos = ByteArrayOutputStream()
            scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 85, bos)
            scaledBitmap.recycle()
            val bytes = bos.toByteArray()
            return@withContext Pair(bytes, "image/jpeg")
        } catch (e: Exception) {
            Log.e(TAG, "Error processing cover image: ${e.message}", e)
            null
        }
    }

    /**
     * Injects or replaces ID3v2.3 tags and embedded cover artwork in an MP3 file.
     * This is a lossless operation: audio frames are copied directly without re-encoding.
     */
    suspend fun writeMp3Tags(
        sourceFile: File,
        outputFile: File,
        metadata: SongMetadata
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            var audioStart = 0L
            var audioEnd = sourceFile.length()

            // 1. Detect existing ID3v2 header at the start
            FileInputStream(sourceFile).use { fis ->
                val header = ByteArray(10)
                val read = fis.read(header)
                if (read == 10 &&
                    header[0] == 'I'.code.toByte() &&
                    header[1] == 'D'.code.toByte() &&
                    header[2] == '3'.code.toByte()
                ) {
                    val flags = header[5].toInt()
                    val tagSize = parseSynchsafe(header, 6)
                    audioStart = 10L + tagSize
                    // ID3v2.4 footer flag check
                    if ((flags and 0x10) != 0) {
                        audioStart += 10L
                    }
                }
            }

            // 2. Detect existing ID3v1 tag at the end (128 bytes)
            if (audioEnd - audioStart > 128) {
                FileInputStream(sourceFile).use { fis ->
                    fis.skip(audioEnd - 128)
                    val tagBytes = ByteArray(3)
                    val read = fis.read(tagBytes)
                    if (read == 3 &&
                        tagBytes[0] == 'T'.code.toByte() &&
                        tagBytes[1] == 'A'.code.toByte() &&
                        tagBytes[2] == 'G'.code.toByte()
                    ) {
                        audioEnd -= 128
                    }
                }
            }

            if (audioStart >= audioEnd) {
                audioStart = 0L
                audioEnd = sourceFile.length()
            }

            // 3. Assemble all ID3v2.3 frames
            val framesStream = ByteArrayOutputStream()
            framesStream.write(buildTextFrame("TIT2", metadata.title))
            framesStream.write(buildTextFrame("TPE1", metadata.artist))
            framesStream.write(buildTextFrame("TALB", metadata.album))
            framesStream.write(buildTextFrame("TPE2", metadata.albumArtist))
            framesStream.write(buildTextFrame("TCON", metadata.genre))
            framesStream.write(buildTextFrame("TYER", metadata.year))
            framesStream.write(buildTextFrame("TRCK", metadata.trackNumber))
            framesStream.write(buildTextFrame("TCOM", metadata.composer))
            if (metadata.comment.isNotBlank()) {
                framesStream.write(buildCommentFrame(metadata.comment))
            }
            metadata.coverArtBytes?.let { imageBytes ->
                if (imageBytes.isNotEmpty()) {
                    framesStream.write(buildPictureFrame(imageBytes, metadata.coverMimeType))
                }
            }

            val framesData = framesStream.toByteArray()
            val tagSize = framesData.size

            // 4. Construct ID3v2.3 10-byte header
            val id3Header = ByteArray(10)
            id3Header[0] = 'I'.code.toByte()
            id3Header[1] = 'D'.code.toByte()
            id3Header[2] = '3'.code.toByte()
            id3Header[3] = 0x03 // version 2.3
            id3Header[4] = 0x00 // revision
            id3Header[5] = 0x00 // flags
            val synchsafe = toSynchsafe(tagSize)
            System.arraycopy(synchsafe, 0, id3Header, 6, 4)

            // 5. Write to destination file
            val tempOutput = if (outputFile.absolutePath == sourceFile.absolutePath) {
                File(outputFile.parentFile, "tmp_tag_${System.currentTimeMillis()}.mp3")
            } else {
                outputFile
            }

            FileOutputStream(tempOutput).use { fos ->
                fos.write(id3Header)
                fos.write(framesData)

                // Copy audio payload
                FileInputStream(sourceFile).use { fis ->
                    if (audioStart > 0) {
                        fis.skip(audioStart)
                    }
                    var remaining = audioEnd - audioStart
                    val buffer = ByteArray(32768)
                    while (remaining > 0) {
                        val toRead = buffer.size.toLong().coerceAtMost(remaining).toInt()
                        val read = fis.read(buffer, 0, toRead)
                        if (read <= 0) break
                        fos.write(buffer, 0, read)
                        remaining -= read
                    }
                }
            }

            if (tempOutput != outputFile) {
                outputFile.delete()
                tempOutput.renameTo(outputFile)
            }

            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write MP3 ID3 tags: ${e.message}", e)
            false
        }
    }

    private fun buildTextFrame(frameId: String, text: String): ByteArray {
        if (text.isBlank()) return ByteArray(0)
        val bos = ByteArrayOutputStream()
        // Encoding byte: 0x01 (UTF-16 with BOM)
        bos.write(0x01)
        // UTF-16LE BOM
        bos.write(0xFF)
        bos.write(0xFE)
        val textBytes = text.toByteArray(Charsets.UTF_16LE)
        bos.write(textBytes)

        val payload = bos.toByteArray()
        val frameHeader = ByteArrayOutputStream()
        frameHeader.write(frameId.toByteArray(Charsets.US_ASCII))
        frameHeader.write(toInt32Bytes(payload.size))
        frameHeader.write(byteArrayOf(0x00, 0x00)) // Flags
        frameHeader.write(payload)
        return frameHeader.toByteArray()
    }

    private fun buildPictureFrame(imageBytes: ByteArray, mimeType: String): ByteArray {
        if (imageBytes.isEmpty()) return ByteArray(0)
        val bos = ByteArrayOutputStream()
        // Encoding: 0x00 (ISO-8859-1)
        bos.write(0x00)
        // MIME Type null-terminated
        bos.write(mimeType.toByteArray(Charsets.US_ASCII))
        bos.write(0x00)
        // Picture Type: 0x03 (Cover Front)
        bos.write(0x03)
        // Description: null-terminated empty string
        bos.write(0x00)
        // Binary picture data
        bos.write(imageBytes)

        val payload = bos.toByteArray()
        val frameHeader = ByteArrayOutputStream()
        frameHeader.write("APIC".toByteArray(Charsets.US_ASCII))
        frameHeader.write(toInt32Bytes(payload.size))
        frameHeader.write(byteArrayOf(0x00, 0x00)) // Flags
        frameHeader.write(payload)
        return frameHeader.toByteArray()
    }

    private fun buildCommentFrame(comment: String): ByteArray {
        if (comment.isBlank()) return ByteArray(0)
        val bos = ByteArrayOutputStream()
        // Encoding: 0x01 (UTF-16 with BOM)
        bos.write(0x01)
        // Language: "eng"
        bos.write("eng".toByteArray(Charsets.US_ASCII))
        // Short description: empty string in UTF-16LE with BOM and null terminator
        bos.write(byteArrayOf(0xFF.toByte(), 0xFE.toByte(), 0x00, 0x00))
        // Actual comment text
        bos.write(byteArrayOf(0xFF.toByte(), 0xFE.toByte()))
        bos.write(comment.toByteArray(Charsets.UTF_16LE))

        val payload = bos.toByteArray()
        val frameHeader = ByteArrayOutputStream()
        frameHeader.write("COMM".toByteArray(Charsets.US_ASCII))
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
}

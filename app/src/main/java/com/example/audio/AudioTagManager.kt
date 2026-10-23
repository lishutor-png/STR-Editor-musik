package com.example.audio

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.media.ExifInterface
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
import java.io.RandomAccessFile

object AudioTagManager {
    private const val TAG = "AudioTagManager"

    enum class CoverFitMode {
        FILL,   // Potong tengah 1:1 agar memenuhi bingkai tanpa sisa tepi
        FIT,    // Muat pas seluruh foto ke dalam bingkai 1:1 dengan latar belakang elegan
        CUSTOM  // Pengaturan bebas dengan zoom, geser (pan), dan putar
    }

    data class PresetCoverInfo(
        val id: Int,
        val name: String,
        val subtitle: String,
        val primaryColorHex: Long
    )

    val PRESET_COVERS = listOf(
        PresetCoverInfo(0, "Sunset Melodi", "Gradien Hangat & Piringan Hitam", 0xFFFF5722),
        PresetCoverInfo(1, "Neon Cyber", "Gelombang Vokal & Spektrum Ungu", 0xFF7C4DFF),
        PresetCoverInfo(2, "Akustik Alami", "Nuansa Hijau Botani & Akustik", 0xFF009688),
        PresetCoverInfo(3, "Golden Studio", "Kemewahan Hitam & Emas Studio", 0xFFFFB300)
    )

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
        var comment = ""
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
                coverMime = detectMimeType(picture)
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

        // Direct ID3v2 APIC fallback if MediaMetadataRetriever didn't return cover
        if (coverBytes == null && file != null && file.exists()) {
            val fallbackPic = extractId3v2Picture(file)
            if (fallbackPic != null && fallbackPic.isNotEmpty()) {
                coverBytes = fallbackPic
                coverMime = detectMimeType(fallbackPic)
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
            originalFileName = originalFileName,
            originalFilePath = file?.absolutePath,
            originalUri = uri
        )
    }

    private fun detectMimeType(bytes: ByteArray): String {
        return if (bytes.size >= 8 &&
            bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() &&
            bytes[2] == 0x4E.toByte() && bytes[3] == 0x47.toByte()
        ) {
            "image/png"
        } else if (bytes.size >= 3 &&
            bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() && bytes[2] == 0xFF.toByte()
        ) {
            "image/jpeg"
        } else if (bytes.size >= 4 &&
            bytes[0] == 'R'.code.toByte() && bytes[1] == 'I'.code.toByte() &&
            bytes[2] == 'F'.code.toByte() && bytes[3] == 'F'.code.toByte()
        ) {
            "image/webp"
        } else {
            "image/jpeg"
        }
    }

    /**
     * Memory-safe bitmap loading from content URI.
     * Uses two-pass stream decoding with inSampleSize and handles EXIF rotation.
     */
    suspend fun loadBitmapFromUri(context: Context, uri: Uri, maxDimension: Int = 1200): Bitmap? = withContext(Dispatchers.IO) {
        try {
            // Check EXIF rotation first
            val exifRotation = try {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    val exif = ExifInterface(input)
                    when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                        ExifInterface.ORIENTATION_ROTATE_90 -> 90
                        ExifInterface.ORIENTATION_ROTATE_180 -> 180
                        ExifInterface.ORIENTATION_ROTATE_270 -> 270
                        else -> 0
                    }
                } ?: 0
            } catch (t: Throwable) {
                0
            }

            // Pass 1: Decode image bounds without loading pixels
            val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { input ->
                BitmapFactory.decodeStream(input, null, boundsOptions)
            }
            if (boundsOptions.outWidth <= 0 || boundsOptions.outHeight <= 0) return@withContext null

            // Calculate sample size
            var sampleSize = 1
            val maxSide = maxOf(boundsOptions.outWidth, boundsOptions.outHeight)
            while (maxSide / (sampleSize * 2) >= maxDimension) {
                sampleSize *= 2
            }

            // Pass 2: Decode sampled bitmap
            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val sampledBitmap = context.contentResolver.openInputStream(uri)?.use { input ->
                BitmapFactory.decodeStream(input, null, decodeOptions)
            } ?: return@withContext null

            // Pass 3: Apply EXIF rotation if needed
            val rotatedBitmap = if (exifRotation != 0) {
                val matrix = Matrix().apply { postRotate(exifRotation.toFloat()) }
                val rot = Bitmap.createBitmap(sampledBitmap, 0, 0, sampledBitmap.width, sampledBitmap.height, matrix, true)
                if (rot != sampledBitmap) sampledBitmap.recycle()
                rot
            } else {
                sampledBitmap
            }

            // Pass 4: Resize if still exceeds maxDimension
            if (rotatedBitmap.width > maxDimension || rotatedBitmap.height > maxDimension) {
                val scale = maxDimension.toFloat() / maxOf(rotatedBitmap.width, rotatedBitmap.height)
                val targetW = (rotatedBitmap.width * scale).toInt().coerceAtLeast(1)
                val targetH = (rotatedBitmap.height * scale).toInt().coerceAtLeast(1)
                val scaled = Bitmap.createScaledBitmap(rotatedBitmap, targetW, targetH, true)
                if (scaled != rotatedBitmap) rotatedBitmap.recycle()
                scaled
            } else {
                rotatedBitmap
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Error loading bitmap from URI: ${t.message}", t)
            null
        }
    }

    /**
     * Memory-safe bitmap loading from byte array.
     */
    fun loadBitmapFromBytes(bytes: ByteArray, maxDimension: Int = 1200): Bitmap? {
        try {
            if (bytes.isEmpty()) return null
            val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, boundsOpts)
            if (boundsOpts.outWidth <= 0 || boundsOpts.outHeight <= 0) return null

            var sampleSize = 1
            val maxSide = maxOf(boundsOpts.outWidth, boundsOpts.outHeight)
            while (maxSide / (sampleSize * 2) >= maxDimension) {
                sampleSize *= 2
            }

            val decodeOpts = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOpts)
        } catch (t: Throwable) {
            Log.e(TAG, "Error decoding bitmap from bytes: ${t.message}", t)
            return null
        }
    }

    /**
     * Rebuilt, 100% WYSIWYG 1:1 square cover generator.
     * Produces pixel-perfect output matching the preview canvas.
     */
    fun createCoverBitmap(
        source: Bitmap,
        mode: CoverFitMode,
        zoom: Float = 1.0f,
        panX: Float = 0f,
        panY: Float = 0f,
        rotationDegrees: Int = 0,
        viewportSizePx: Float = 800f,
        outputDimension: Int = 800
    ): Bitmap {
        // Step 1: Rotate bitmap if specified
        val rotated = if (rotationDegrees != 0) {
            val rotMatrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
            Bitmap.createBitmap(source, 0, 0, source.width, source.height, rotMatrix, true)
        } else {
            source
        }

        val output = Bitmap.createBitmap(outputDimension, outputDimension, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)

        val srcW = rotated.width.toFloat()
        val srcH = rotated.height.toFloat()

        when (mode) {
            CoverFitMode.FILL -> {
                // Auto center-crop filling the 1:1 square completely
                val scale = maxOf(outputDimension / srcW, outputDimension / srcH)
                val scaledW = srcW * scale
                val scaledH = srcH * scale
                val left = (outputDimension - scaledW) / 2f
                val top = (outputDimension - scaledH) / 2f
                val destRect = RectF(left, top, left + scaledW, top + scaledH)
                val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
                canvas.drawBitmap(rotated, null, destRect, paint)
            }
            CoverFitMode.FIT -> {
                // Fit entire image inside 1:1 box with deep gradient background
                val bgPaint = Paint().apply {
                    shader = LinearGradient(
                        0f, 0f, outputDimension.toFloat(), outputDimension.toFloat(),
                        Color.rgb(26, 26, 36), Color.rgb(10, 10, 15),
                        Shader.TileMode.CLAMP
                    )
                }
                canvas.drawRect(0f, 0f, outputDimension.toFloat(), outputDimension.toFloat(), bgPaint)

                val scale = minOf(outputDimension / srcW, outputDimension / srcH)
                val scaledW = srcW * scale
                val scaledH = srcH * scale
                val left = (outputDimension - scaledW) / 2f
                val top = (outputDimension - scaledH) / 2f
                val destRect = RectF(left, top, left + scaledW, top + scaledH)
                val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
                canvas.drawBitmap(rotated, null, destRect, paint)
            }
            CoverFitMode.CUSTOM -> {
                // Dark background
                canvas.drawColor(Color.rgb(15, 15, 20))

                val ratio = outputDimension.toFloat() / viewportSizePx.coerceAtLeast(1f)
                val baseScale = maxOf(viewportSizePx / srcW, viewportSizePx / srcH)
                val currentScale = baseScale * zoom * ratio

                val scaledW = srcW * currentScale
                val scaledH = srcH * currentScale

                val centerX = outputDimension / 2f + (panX * ratio)
                val centerY = outputDimension / 2f + (panY * ratio)

                val left = centerX - (scaledW / 2f)
                val top = centerY - (scaledH / 2f)

                val destRect = RectF(left, top, left + scaledW, top + scaledH)
                val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
                canvas.drawBitmap(rotated, null, destRect, paint)
            }
        }

        if (rotated != source) {
            rotated.recycle()
        }
        return output
    }

    /**
     * Compresses a Bitmap into high-quality JPEG bytes suitable for ID3 embedding.
     */
    fun bitmapToCoverBytes(bitmap: Bitmap, quality: Int = 90): Pair<ByteArray, String> {
        val bos = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, bos)
        return Pair(bos.toByteArray(), "image/jpeg")
    }

    /**
     * Generates a preset album artwork programmatically.
     * Useful for immediate testing and stylish covers without needing local files.
     */
    fun generatePresetCover(presetIndex: Int, dimension: Int = 800): Pair<ByteArray, String> {
        val bitmap = Bitmap.createBitmap(dimension, dimension, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textAlign = Paint.Align.CENTER
            textSize = dimension * 0.075f
            isFakeBoldText = true
        }
        val subTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(220, 220, 230)
            textAlign = Paint.Align.CENTER
            textSize = dimension * 0.040f
        }

        when (presetIndex % 4) {
            0 -> {
                // Sunset Melodi
                paint.shader = LinearGradient(
                    0f, 0f, dimension.toFloat(), dimension.toFloat(),
                    Color.rgb(255, 87, 34), Color.rgb(136, 14, 79),
                    Shader.TileMode.CLAMP
                )
                canvas.drawRect(0f, 0f, dimension.toFloat(), dimension.toFloat(), paint)

                // Vinyl Disc concentric rings
                paint.shader = null
                paint.style = Paint.Style.STROKE
                paint.color = Color.argb(40, 255, 255, 255)
                val center = dimension / 2f
                for (r in listOf(0.18f, 0.25f, 0.32f, 0.38f)) {
                    paint.strokeWidth = 3f
                    canvas.drawCircle(center, center - 40f, dimension * r, paint)
                }

                // Center Icon Circle
                paint.style = Paint.Style.FILL
                paint.color = Color.rgb(255, 171, 0)
                canvas.drawCircle(center, center - 40f, dimension * 0.12f, paint)

                canvas.drawText("SUNSET MELODI", center, dimension * 0.78f, textPaint)
                canvas.drawText("Studio Album Edition", center, dimension * 0.85f, subTextPaint)
            }
            1 -> {
                // Neon Cyber
                paint.shader = LinearGradient(
                    0f, 0f, dimension.toFloat(), dimension.toFloat(),
                    Color.rgb(18, 18, 30), Color.rgb(94, 53, 177),
                    Shader.TileMode.CLAMP
                )
                canvas.drawRect(0f, 0f, dimension.toFloat(), dimension.toFloat(), paint)

                // Equalizer wave bars
                paint.shader = null
                paint.style = Paint.Style.FILL
                val barCount = 11
                val barW = dimension * 0.035f
                val spacing = dimension * 0.025f
                val startX = (dimension - (barCount * barW + (barCount - 1) * spacing)) / 2f
                val heights = listOf(0.15f, 0.25f, 0.38f, 0.50f, 0.35f, 0.58f, 0.42f, 0.30f, 0.45f, 0.22f, 0.12f)
                for (i in 0 until barCount) {
                    val x = startX + i * (barW + spacing)
                    val h = dimension * heights[i]
                    val y = dimension * 0.45f - h / 2f
                    paint.color = if (i % 2 == 0) Color.rgb(0, 229, 255) else Color.rgb(255, 64, 129)
                    canvas.drawRoundRect(RectF(x, y, x + barW, y + h), 8f, 8f, paint)
                }

                canvas.drawText("NEON SOUNDS", dimension / 2f, dimension * 0.78f, textPaint)
                canvas.drawText("Cyber Electronic Beats", dimension / 2f, dimension * 0.85f, subTextPaint)
            }
            2 -> {
                // Akustik Alami
                paint.shader = LinearGradient(
                    0f, 0f, 0f, dimension.toFloat(),
                    Color.rgb(0, 77, 64), Color.rgb(18, 30, 24),
                    Shader.TileMode.CLAMP
                )
                canvas.drawRect(0f, 0f, dimension.toFloat(), dimension.toFloat(), paint)

                paint.shader = null
                paint.style = Paint.Style.STROKE
                paint.color = Color.rgb(77, 182, 172)
                paint.strokeWidth = 5f
                canvas.drawCircle(dimension / 2f, dimension * 0.40f, dimension * 0.22f, paint)
                paint.strokeWidth = 2f
                canvas.drawCircle(dimension / 2f, dimension * 0.40f, dimension * 0.28f, paint)

                canvas.drawText("AKUSTIK SENJA", dimension / 2f, dimension * 0.78f, textPaint)
                canvas.drawText("Original Acoustic Tracks", dimension / 2f, dimension * 0.85f, subTextPaint)
            }
            3 -> {
                // Golden Studio
                paint.shader = LinearGradient(
                    0f, 0f, dimension.toFloat(), dimension.toFloat(),
                    Color.rgb(20, 20, 22), Color.rgb(44, 44, 48),
                    Shader.TileMode.CLAMP
                )
                canvas.drawRect(0f, 0f, dimension.toFloat(), dimension.toFloat(), paint)

                // Gold Border
                paint.shader = null
                paint.style = Paint.Style.STROKE
                paint.color = Color.rgb(255, 193, 7)
                paint.strokeWidth = 8f
                canvas.drawRect(dimension * 0.08f, dimension * 0.08f, dimension * 0.92f, dimension * 0.92f, paint)

                paint.strokeWidth = 2f
                canvas.drawRect(dimension * 0.11f, dimension * 0.11f, dimension * 0.89f, dimension * 0.89f, paint)

                textPaint.color = Color.rgb(255, 215, 64)
                canvas.drawText("GOLDEN HITS", dimension / 2f, dimension * 0.50f, textPaint)
                canvas.drawText("Master Recording", dimension / 2f, dimension * 0.60f, subTextPaint)
            }
        }

        val result = bitmapToCoverBytes(bitmap, 90)
        bitmap.recycle()
        return result
    }

    /**
     * Fallback ID3v2 APIC parser to extract embedded artwork if standard retriever misses it.
     */
    private fun extractId3v2Picture(file: File): ByteArray? {
        try {
            RandomAccessFile(file, "r").use { raf ->
                if (raf.length() < 10) return null
                val header = ByteArray(10)
                raf.readFully(header)
                if (header[0] != 'I'.code.toByte() || header[1] != 'D'.code.toByte() || header[2] != '3'.code.toByte()) {
                    return null
                }
                val tagSize = parseSynchsafe(header, 6)
                if (tagSize <= 0 || tagSize > 15 * 1024 * 1024) return null // Guard against corrupted size

                val tagBuffer = ByteArray(tagSize)
                raf.readFully(tagBuffer)

                // Search for "APIC" frame
                var offset = 0
                while (offset + 10 <= tagSize) {
                    val frameId = String(tagBuffer, offset, 4, Charsets.US_ASCII)
                    val frameSize = ((tagBuffer[offset + 4].toInt() and 0xFF) shl 24) or
                            ((tagBuffer[offset + 5].toInt() and 0xFF) shl 16) or
                            ((tagBuffer[offset + 6].toInt() and 0xFF) shl 8) or
                            (tagBuffer[offset + 7].toInt() and 0xFF)

                    if (frameId.isBlank() || frameSize <= 0) break
                    if (offset + 10 + frameSize > tagSize) break

                    if (frameId == "APIC") {
                        val payloadStart = offset + 10
                        // APIC payload format:
                        // 1 byte text encoding
                        // MIME type string (null terminated)
                        // 1 byte picture type
                        // description string (null terminated)
                        // picture data
                        var p = payloadStart + 1
                        while (p < payloadStart + frameSize && tagBuffer[p] != 0.toByte()) {
                            p++
                        }
                        p++ // skip null after MIME
                        p++ // skip picture type byte
                        // skip description
                        while (p < payloadStart + frameSize && tagBuffer[p] != 0.toByte()) {
                            p++
                        }
                        p++ // skip null after description

                        val picDataLen = (payloadStart + frameSize) - p
                        if (picDataLen > 0) {
                            val pic = ByteArray(picDataLen)
                            System.arraycopy(tagBuffer, p, pic, 0, picDataLen)
                            return pic
                        }
                    }
                    offset += 10 + frameSize
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error in fallback APIC reader: ${e.message}")
        }
        return null
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

            // 1. Detect existing ID3v2 header and ID3v1 footer safely using RandomAccessFile
            RandomAccessFile(sourceFile, "r").use { raf ->
                val len = raf.length()
                if (len >= 10) {
                    val header = ByteArray(10)
                    raf.readFully(header)
                    if (header[0] == 'I'.code.toByte() &&
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

                if (len - audioStart > 128) {
                    raf.seek(len - 128)
                    val tagBytes = ByteArray(3)
                    raf.readFully(tagBytes)
                    if (tagBytes[0] == 'T'.code.toByte() &&
                        tagBytes[1] == 'A'.code.toByte() &&
                        tagBytes[2] == 'G'.code.toByte()
                    ) {
                        audioEnd = len - 128
                    }
                }
            }

            if (audioStart >= audioEnd || audioStart < 0) {
                audioStart = 0L
                audioEnd = sourceFile.length()
            }

            // 2. Assemble all ID3v2.3 frames
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

            // 3. Construct ID3v2.3 10-byte header
            val id3Header = ByteArray(10)
            id3Header[0] = 'I'.code.toByte()
            id3Header[1] = 'D'.code.toByte()
            id3Header[2] = '3'.code.toByte()
            id3Header[3] = 0x03 // version 2.3
            id3Header[4] = 0x00 // revision
            id3Header[5] = 0x00 // flags
            val synchsafe = toSynchsafe(tagSize)
            System.arraycopy(synchsafe, 0, id3Header, 6, 4)

            // 4. Write to destination file
            val tempOutput = if (outputFile.absolutePath == sourceFile.absolutePath) {
                File(outputFile.parentFile, "tmp_tag_${System.currentTimeMillis()}.mp3")
            } else {
                outputFile
            }

            FileOutputStream(tempOutput).use { fos ->
                fos.write(id3Header)
                fos.write(framesData)

                // Copy audio payload with exact RandomAccessFile seek
                RandomAccessFile(sourceFile, "r").use { raf ->
                    raf.seek(audioStart)
                    var remaining = audioEnd - audioStart
                    val buffer = ByteArray(65536)
                    while (remaining > 0) {
                        val toRead = buffer.size.toLong().coerceAtMost(remaining).toInt()
                        val read = raf.read(buffer, 0, toRead)
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

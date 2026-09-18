package com.example.audio

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

object StorageExporter {
    private const val TAG = "StorageExporter"

    /**
     * Saves an audio file directly into the phone's public Music storage
     * (Music/LishStudioRecord/) so it is accessible in file managers and media players.
     */
    suspend fun saveToPhoneMemory(
        context: Context,
        sourceFile: File,
        displayName: String,
        mimeType: String
    ): Uri? = withContext(Dispatchers.IO) {
        try {
            val contentValues = ContentValues().apply {
                put(MediaStore.Audio.Media.DISPLAY_NAME, displayName)
                put(MediaStore.Audio.Media.TITLE, displayName.substringBeforeLast("."))
                put(MediaStore.Audio.Media.MIME_TYPE, mimeType)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Audio.Media.RELATIVE_PATH, Environment.DIRECTORY_MUSIC + "/LishStudioRecord")
                    put(MediaStore.Audio.Media.IS_PENDING, 1)
                }
            }

            val resolver = context.contentResolver
            val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            } else {
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            }

            val itemUri = resolver.insert(collection, contentValues) ?: return@withContext null

            resolver.openOutputStream(itemUri)?.use { outputStream ->
                FileInputStream(sourceFile).use { inputStream ->
                    inputStream.copyTo(outputStream)
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.clear()
                contentValues.put(MediaStore.Audio.Media.IS_PENDING, 0)
                resolver.update(itemUri, contentValues, null, null)
            }

            Log.d(TAG, "Audio successfully saved to MediaStore: $itemUri")
            itemUri
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save to phone memory: ${e.message}", e)
            null
        }
    }

    /**
     * "Save As": Writes the audio file to a custom user-selected Uri from SAF CreateDocument.
     */
    suspend fun saveAsToUri(
        context: Context,
        sourceFile: File,
        targetUri: Uri
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openOutputStream(targetUri)?.use { outputStream ->
                FileInputStream(sourceFile).use { inputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Save As failed: ${e.message}", e)
            false
        }
    }
}

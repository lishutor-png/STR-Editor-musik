package com.example.audio

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import kotlin.coroutines.resume

object StorageExporter {
    private const val TAG = "StorageExporter"

    /**
     * Saves audio file directly to public phone storage:
     * Directory: Music/LishStudioRecord/
     * Registers in MediaStore and runs MediaScannerConnection so the album cover
     * and metadata immediately show up in system players and file managers.
     */
    suspend fun saveToPhoneMemory(
        context: Context,
        sourceFile: File,
        displayName: String,
        mimeType: String
    ): Uri? = withContext(Dispatchers.IO) {
        try {
            if (!sourceFile.exists()) {
                Log.e(TAG, "Source file does not exist: ${sourceFile.absolutePath}")
                return@withContext null
            }

            val musicDir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC),
                "LishStudioRecord"
            )
            if (!musicDir.exists()) {
                musicDir.mkdirs()
            }

            // Direct public file write
            val destFile = File(musicDir, displayName)
            sourceFile.copyTo(destFile, overwrite = true)
            Log.d(TAG, "Directly copied audio to public storage: ${destFile.absolutePath}")

            // MediaStore insert or update
            var resultUri: Uri? = null
            val resolver = context.contentResolver

            val values = ContentValues().apply {
                put(MediaStore.Audio.Media.DISPLAY_NAME, displayName)
                put(MediaStore.Audio.Media.TITLE, displayName.substringBeforeLast("."))
                put(MediaStore.Audio.Media.MIME_TYPE, mimeType)
                put(MediaStore.Audio.Media.IS_MUSIC, 1)

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Audio.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MUSIC}/LishStudioRecord")
                    put(MediaStore.Audio.Media.IS_PENDING, 1)
                } else {
                    put(MediaStore.Audio.Media.DATA, destFile.absolutePath)
                }
            }

            val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            } else {
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            }

            // Check if item already exists in collection
            try {
                val projection = arrayOf(MediaStore.Audio.Media._ID)
                val selection = "${MediaStore.Audio.Media.DISPLAY_NAME} = ?"
                val selectionArgs = arrayOf(displayName)
                resolver.query(collection, projection, selection, selectionArgs, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID))
                        resultUri = Uri.withAppendedPath(collection, id.toString())
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Query existing media failed: ${e.message}")
            }

            if (resultUri == null) {
                resultUri = resolver.insert(collection, values)
            }

            if (resultUri != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                try {
                    resolver.openOutputStream(resultUri!!, "rwt")?.use { out ->
                        FileInputStream(sourceFile).use { input ->
                            input.copyTo(out)
                        }
                        out.flush()
                    }
                    val updateValues = ContentValues().apply {
                        put(MediaStore.Audio.Media.IS_PENDING, 0)
                    }
                    resolver.update(resultUri!!, updateValues, null, null)
                } catch (e: Exception) {
                    Log.w(TAG, "Write via MediaStore output stream error (destFile already written): ${e.message}")
                }
            }

            // Trigger MediaScannerConnection.scanFile so the system extracts APIC cover art
            scanMediaFile(context, destFile.absolutePath, mimeType)

            // Broadcast fallback for older receivers
            try {
                val scanIntent = Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE).apply {
                    data = Uri.fromFile(destFile)
                }
                context.sendBroadcast(scanIntent)
            } catch (e: Exception) {
                // ignore
            }

            resultUri ?: Uri.fromFile(destFile)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save audio to phone memory: ${e.message}", e)
            null
        }
    }

    /**
     * Saves audio file directly to a picked SAF Document Uri (Storage Access Framework).
     * Opens with "rwt" and truncates to prevent trailing garbage bytes.
     */
    suspend fun saveAsToUri(
        context: Context,
        sourceFile: File,
        targetUri: Uri
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val resolver: ContentResolver = context.contentResolver

            // Truncate existing content if possible
            try {
                resolver.openFileDescriptor(targetUri, "rwt")?.use { pfd ->
                    android.system.Os.ftruncate(pfd.fileDescriptor, 0)
                }
            } catch (e: Exception) {
                Log.w(TAG, "ftruncate not supported or failed: ${e.message}")
            }

            resolver.openOutputStream(targetUri, "wt")?.use { out ->
                FileInputStream(sourceFile).use { input ->
                    input.copyTo(out)
                }
                out.flush()
            } ?: run {
                // Fallback to standard openOutputStream
                resolver.openOutputStream(targetUri)?.use { out ->
                    FileInputStream(sourceFile).use { input ->
                        input.copyTo(out)
                    }
                    out.flush()
                }
            }

            Log.d(TAG, "Audio successfully written to Uri: $targetUri")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Save As failed: ${e.message}", e)
            false
        }
    }

    private suspend fun scanMediaFile(context: Context, path: String, mimeType: String): Uri? =
        suspendCancellableCoroutine { cont ->
            MediaScannerConnection.scanFile(
                context,
                arrayOf(path),
                arrayOf(mimeType)
            ) { scannedPath, uri ->
                Log.d(TAG, "Scanned $scannedPath -> $uri")
                if (cont.isActive) {
                    cont.resume(uri)
                }
            }
        }
}

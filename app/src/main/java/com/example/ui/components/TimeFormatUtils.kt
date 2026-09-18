package com.example.ui.components

import java.util.Locale

object TimeFormatUtils {
    fun formatMs(ms: Long): String {
        val totalSeconds = ms / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        val hundredths = (ms % 1000) / 10
        return String.format(Locale.getDefault(), "%02d:%02d.%02d", minutes, seconds, hundredths)
    }

    fun formatDuration(ms: Long): String {
        val totalSeconds = ms / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
    }

    fun formatFileSize(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        return if (mb >= 1.0) {
            String.format(Locale.getDefault(), "%.1f MB", mb)
        } else {
            String.format(Locale.getDefault(), "%.0f KB", kb)
        }
    }
}

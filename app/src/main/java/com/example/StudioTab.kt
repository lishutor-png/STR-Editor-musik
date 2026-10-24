package com.example

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallMerge
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.FolderSpecial
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.ui.graphics.vector.ImageVector

enum class StudioTab(val title: String, val icon: ImageVector) {
    TRIM("Potong", Icons.Default.ContentCut),
    MERGE("Gabung", Icons.AutoMirrored.Filled.CallMerge),
    TAGS("Info Lagu", Icons.Default.EditNote),
    CONVERT("Format", Icons.Default.SwapHoriz),
    LIBRARY("Koleksi", Icons.Default.FolderSpecial)
}

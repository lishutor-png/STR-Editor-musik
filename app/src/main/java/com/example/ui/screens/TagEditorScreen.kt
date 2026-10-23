package com.example.ui.screens

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.net.Uri
import java.io.File
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Comment
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FilterCenterFocus
import androidx.compose.material.icons.filled.FitScreen
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Numbers
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.SaveAs
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.audio.AudioTagManager
import com.example.audio.AudioViewModel
import com.example.audio.SongMetadata
import com.example.ui.components.TimeFormatUtils
import kotlinx.coroutines.launch

private val QUICK_GENRES = listOf(
    "Pop", "Dangdut", "Rock", "Jazz", "Akustik",
    "EDM", "Religi", "Klasik", "Hip-Hop", "R&B", "Indie"
)

@Composable
fun TagEditorScreen(
    viewModel: AudioViewModel,
    onOpenFilePicker: () -> Unit,
    onNavigateToLibrary: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val tagMetadata by viewModel.tagMetadata.collectAsState()

    var showSourceChooser by remember { mutableStateOf(false) }
    var adjustingSourceBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var showAdjustDialog by remember { mutableStateOf(false) }
    var isProcessingCover by remember { mutableStateOf(false) }
    var showSaveSuccessDialog by remember { mutableStateOf<File?>(null) }

    // Direct helper to process and apply chosen image URI
    val processAndApplyUri: (Uri) -> Unit = { uri ->
        coroutineScope.launch {
            isProcessingCover = true
            val bitmap = AudioTagManager.loadBitmapFromUri(context, uri)
            isProcessingCover = false
            if (bitmap != null) {
                // 1. Immediately apply clean 1:1 square cover (auto center crop)
                val squareCover = AudioTagManager.createCoverBitmap(
                    source = bitmap,
                    mode = AudioTagManager.CoverFitMode.FILL
                )
                val (bytes, mime) = AudioTagManager.bitmapToCoverBytes(squareCover)
                viewModel.updateCoverArt(bytes, mime)

                // 2. Open adjustment dialog so user can fine-tune if they want
                adjustingSourceBitmap = bitmap
                showAdjustDialog = true
            }
        }
    }

    // Gallery Picker launcher (GetContent image/* - supported on ALL Android versions & emulators)
    val galleryImagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { processAndApplyUri(it) }
    }

    // File Manager image picker (OpenDocument image/*)
    val fileImagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { processAndApplyUri(it) }
    }

    // Save As launcher
    val saveAsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("audio/mpeg")
    ) { destinationUri ->
        if (destinationUri != null) {
            viewModel.executeSaveAs(destinationUri)
        } else {
            viewModel.clearPendingSaveAs()
        }
    }

    val metadata = tagMetadata

    if (metadata == null) {
        // Empty State: Prompt user to pick an audio file
        EmptyTagEditorView(
            onOpenFilePicker = onOpenFilePicker,
            modifier = modifier
        )
    } else {
        // Tag Editor Form with Artwork & Metadata
        Column(
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header: Cover Art & Quick Actions
            CoverArtSection(
                metadata = metadata,
                isLoading = isProcessingCover,
                onOpenCoverChooser = { showSourceChooser = true },
                onAdjustCover = {
                    metadata.coverArtBytes?.let { bytes ->
                        val bitmap = AudioTagManager.loadBitmapFromBytes(bytes)
                        if (bitmap != null) {
                            adjustingSourceBitmap = bitmap
                            showAdjustDialog = true
                        }
                    }
                },
                onRemoveCover = { viewModel.removeCoverArt() },
                onChangeAudioFile = onOpenFilePicker
            )

            // Form: Song Information Fields
            MetadataFormCard(
                metadata = metadata,
                onMetadataChanged = { viewModel.updateSongMetadata(it) }
            )

            // Audio Technical Information
            AudioInfoCard(metadata = metadata)

            // Action Buttons: Save to Library and Save As
            ActionButtonsSection(
                metadata = metadata,
                onSave = {
                    viewModel.saveSongMetadata { savedFile ->
                        showSaveSuccessDialog = savedFile
                    }
                },
                onSaveAs = {
                    viewModel.saveSongMetadata { savedFile ->
                        viewModel.prepareSaveAs(savedFile)
                        saveAsLauncher.launch(savedFile.name)
                    }
                }
            )

            Spacer(Modifier.height(16.dp))
        }
    }

    // Dialog: Choose Cover Source (Gallery, File, or Preset)
    if (showSourceChooser) {
        CoverSourceChooserDialog(
            onDismiss = { showSourceChooser = false },
            onPickGallery = {
                showSourceChooser = false
                galleryImagePickerLauncher.launch("image/*")
            },
            onPickFile = {
                showSourceChooser = false
                fileImagePickerLauncher.launch(arrayOf("image/*"))
            },
            onSelectPreset = { presetIndex ->
                showSourceChooser = false
                viewModel.applyPresetCover(presetIndex)
            }
        )
    }

    // Dialog: 1:1 Cover Adjustment & Crop Tool
    val currentAdjusting = adjustingSourceBitmap
    if (showAdjustDialog && currentAdjusting != null) {
        CoverAdjustDialog(
            sourceBitmap = currentAdjusting,
            onDismiss = {
                showAdjustDialog = false
                adjustingSourceBitmap = null
            },
            onApply = { mode, zoom, panX, panY, rotation, viewportPx ->
                coroutineScope.launch {
                    isProcessingCover = true
                    val outputBitmap = AudioTagManager.createCoverBitmap(
                        source = currentAdjusting,
                        mode = mode,
                        zoom = zoom,
                        panX = panX,
                        panY = panY,
                        rotationDegrees = rotation,
                        viewportSizePx = viewportPx
                    )
                    val (croppedBytes, mime) = AudioTagManager.bitmapToCoverBytes(outputBitmap)
                    viewModel.updateCoverArt(croppedBytes, mime)
                    isProcessingCover = false
                    showAdjustDialog = false
                    adjustingSourceBitmap = null
                }
            }
        )
    }

    // Dialog: Confirmation of Successful Save with Cover Art
    val savedTargetFile = showSaveSuccessDialog
    if (savedTargetFile != null) {
        val successCoverBitmap = remember(tagMetadata?.coverArtBytes) {
            tagMetadata?.coverArtBytes?.let { bytes ->
                try {
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
                } catch (_: Throwable) {
                    null
                }
            }
        }

        AlertDialog(
            onDismissRequest = { showSaveSuccessDialog = null },
            shape = RoundedCornerShape(22.dp),
            containerColor = MaterialTheme.colorScheme.surface,
            icon = {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                }
            },
            title = {
                Text(
                    text = "Cover & Info Berhasil Disimpan!",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (successCoverBitmap != null) {
                        Image(
                            bitmap = successCoverBitmap,
                            contentDescription = "Cover Tersimpan",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(110.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(14.dp))
                        )
                    }

                    Text(
                        text = tagMetadata?.title?.ifBlank { savedTargetFile.nameWithoutExtension } ?: savedTargetFile.nameWithoutExtension,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )

                    if (!tagMetadata?.artist.isNullOrBlank()) {
                        Text(
                            text = tagMetadata?.artist.orEmpty(),
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }

                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "Lokasi Penyimpanan:",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "• Koleksi Musik Aplikasi",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "• Memori HP: Music/LishStudioRecord/${savedTargetFile.name}",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showSaveSuccessDialog = null
                        onNavigateToLibrary()
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    modifier = Modifier.testTag("btn_view_in_library")
                ) {
                    Icon(Icons.Default.Album, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Lihat di Koleksi", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showSaveSuccessDialog = null },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Tutup")
                }
            }
        )
    }
}

@Composable
private fun EmptyTagEditorView(
    onOpenFilePicker: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(20.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Album,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(38.dp)
                    )
                }

                Text(
                    text = "Edit Info & Gambar Lagu",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = "Ubah informasi lagu lengkap seperti judul, nama penyanyi, nama album, tahun rilis, genre, dan tambahkan gambar cover album pada file musik Anda.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 19.sp
                )

                Spacer(Modifier.height(4.dp))

                Button(
                    onClick = onOpenFilePicker,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("btn_pick_tag_audio")
                ) {
                    Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Pilih File Audio Dari HP",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}

/**
 * Rebuilt Cover Art Section with 1:1 Album Frame, Badges, and Touch Targets.
 */
@Composable
private fun CoverArtSection(
    metadata: SongMetadata,
    isLoading: Boolean,
    onOpenCoverChooser: () -> Unit,
    onAdjustCover: () -> Unit,
    onRemoveCover: () -> Unit,
    onChangeAudioFile: () -> Unit
) {
    val coverImageBitmap = remember(metadata.coverArtBytes) {
        metadata.coverArtBytes?.let { bytes ->
            try {
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
            } catch (e: Throwable) {
                null
            }
        }
    }

    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Default.Album,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Cover / Gambar Lagu",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }

                OutlinedButton(
                    onClick = onChangeAudioFile,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.height(36.dp).testTag("btn_change_audio_file")
                ) {
                    Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Ganti Lagu", fontSize = 12.sp)
                }
            }

            // 1:1 Square Album Artwork Frame
            Box(
                modifier = Modifier
                    .size(200.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .border(
                        width = 2.dp,
                        color = if (coverImageBitmap != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                        shape = RoundedCornerShape(20.dp)
                    )
                    .background(Color.Black.copy(alpha = 0.85f))
                    .clickable {
                        if (coverImageBitmap != null) {
                            onAdjustCover()
                        } else {
                            onOpenCoverChooser()
                        }
                    }
                    .testTag("cover_image_preview"),
                contentAlignment = Alignment.Center
            ) {
                if (isLoading) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(40.dp)
                        )
                        Text(
                            text = "Memproses gambar...",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                } else if (coverImageBitmap != null) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        Image(
                            bitmap = coverImageBitmap,
                            contentDescription = "Cover Album",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )

                        // Bottom Overlay Badge: "Ketuk untuk sesuaikan / crop"
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .background(Color.Black.copy(alpha = 0.65f))
                                .padding(vertical = 5.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Icon(
                                    Icons.Default.Crop,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(13.dp)
                                )
                                Text(
                                    text = "Ketuk untuk atur crop & posisi",
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                } else {
                    // Empty Artwork State
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.AddPhotoAlternate,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                        Text(
                            text = "Pasang Cover Album",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Format 1:1 Persegi Standard",
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 11.sp
                        )
                    }
                }
            }

            // Cover metadata badge
            if (coverImageBitmap != null && metadata.coverArtBytes != null) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = "1:1 Persegi",
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    val kb = metadata.coverArtBytes.size / 1024
                    Text(
                        text = "Ukuran: $kb KB • JPEG",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp
                    )
                }
            }

            // Action Buttons
            if (coverImageBitmap != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = onOpenCoverChooser,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1.2f).height(46.dp).testTag("btn_change_cover_image")
                    ) {
                        Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(17.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Ganti Cover", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = onAdjustCover,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1.2f).height(46.dp).testTag("btn_adjust_cover_image")
                    ) {
                        Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(17.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Sesuaikan", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }

                    OutlinedButton(
                        onClick = onRemoveCover,
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(0.9f).height(46.dp).testTag("btn_remove_cover_image")
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(17.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Hapus", fontSize = 12.sp)
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = onOpenCoverChooser,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth().height(48.dp).testTag("btn_pick_cover_image")
                    ) {
                        Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Tambah Cover Album", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

/**
 * Dialog to choose image source: Gallery, File Storage, or Music Presets.
 */
@Composable
private fun CoverSourceChooserDialog(
    onDismiss: () -> Unit,
    onPickGallery: () -> Unit,
    onPickFile: () -> Unit,
    onSelectPreset: (Int) -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(26.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp,
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .padding(vertical = 20.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Pilih Cover Album",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Gunakan foto sendiri atau desain musik siap pakai",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(32.dp).testTag("btn_close_cover_chooser")
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Tutup")
                    }
                }

                // Option 1: Gallery
                Card(
                    onClick = onPickGallery,
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.fillMaxWidth().testTag("chooser_gallery_option")
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(46.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.PhotoLibrary,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Buka Galeri Foto HP",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Pilih foto dari galeri kamera atau tangkapan layar",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Option 2: File Storage
                Card(
                    onClick = onPickFile,
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.fillMaxWidth().testTag("chooser_file_option")
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(46.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.secondaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.FolderOpen,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Pilih Dari File Manager",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Jelajahi folder memori internal atau kartu SD",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Option 3: Presets Section
                Text(
                    text = "Atau Desain Musik Siap Pakai (1 Ketuk):",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AudioTagManager.PRESET_COVERS.forEach { preset ->
                        Card(
                            onClick = { onSelectPreset(preset.id) },
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = Color(preset.primaryColorHex).copy(alpha = 0.15f)
                            ),
                            modifier = Modifier.weight(1f).aspectRatio(0.85f).testTag("preset_card_${preset.id}")
                        ) {
                            Column(
                                modifier = Modifier.fillMaxSize().padding(8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clip(CircleShape)
                                        .background(Color(preset.primaryColorHex)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.AutoAwesome,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    text = preset.name,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Rebuilt 1:1 Square Cover Adjustment & Crop Tool with 100% WYSIWYG matching.
 */
@Composable
fun CoverAdjustDialog(
    sourceBitmap: Bitmap,
    onDismiss: () -> Unit,
    onApply: (
        mode: AudioTagManager.CoverFitMode,
        zoom: Float,
        panX: Float,
        panY: Float,
        rotationDegrees: Int,
        viewportSizePx: Float
    ) -> Unit
) {
    var mode by remember { mutableStateOf(AudioTagManager.CoverFitMode.FILL) }
    var zoom by remember { mutableFloatStateOf(1.0f) }
    var panX by remember { mutableFloatStateOf(0f) }
    var panY by remember { mutableFloatStateOf(0f) }
    var rotation by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current

    val boxSizeDp = 260.dp
    val boxSizePx = with(density) { boxSizeDp.toPx() }

    // Prepare rotated source bitmap for preview canvas
    val rotatedSource = remember(sourceBitmap, rotation) {
        if (rotation != 0) {
            val mat = android.graphics.Matrix().apply { postRotate(rotation.toFloat()) }
            Bitmap.createBitmap(sourceBitmap, 0, 0, sourceBitmap.width, sourceBitmap.height, mat, true)
        } else {
            sourceBitmap
        }
    }
    val rotatedImageBitmap = remember(rotatedSource) { rotatedSource.asImageBitmap() }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(26.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp,
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .padding(vertical = 14.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Sesuaikan Cover Album",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Bingkai 1:1 Persegi Standard Musik",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(32.dp).testTag("btn_close_cover_adjust")
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Tutup",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Mode Selector Chips
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = mode == AudioTagManager.CoverFitMode.FILL,
                        onClick = {
                            mode = AudioTagManager.CoverFitMode.FILL
                            zoom = 1.0f
                            panX = 0f
                            panY = 0f
                        },
                        label = { Text("Isi Penuh", fontSize = 12.sp) },
                        leadingIcon = if (mode == AudioTagManager.CoverFitMode.FILL) {
                            { Icon(Icons.Default.Crop, contentDescription = null, modifier = Modifier.size(14.dp)) }
                        } else null,
                        modifier = Modifier.weight(1f).testTag("chip_mode_fill")
                    )

                    FilterChip(
                        selected = mode == AudioTagManager.CoverFitMode.FIT,
                        onClick = {
                            mode = AudioTagManager.CoverFitMode.FIT
                            zoom = 1.0f
                            panX = 0f
                            panY = 0f
                        },
                        label = { Text("Muat Pas", fontSize = 12.sp) },
                        leadingIcon = if (mode == AudioTagManager.CoverFitMode.FIT) {
                            { Icon(Icons.Default.FitScreen, contentDescription = null, modifier = Modifier.size(14.dp)) }
                        } else null,
                        modifier = Modifier.weight(1f).testTag("chip_mode_fit")
                    )

                    FilterChip(
                        selected = mode == AudioTagManager.CoverFitMode.CUSTOM,
                        onClick = { mode = AudioTagManager.CoverFitMode.CUSTOM },
                        label = { Text("Kustom", fontSize = 12.sp) },
                        leadingIcon = if (mode == AudioTagManager.CoverFitMode.CUSTOM) {
                            { Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(14.dp)) }
                        } else null,
                        modifier = Modifier.weight(1f).testTag("chip_mode_custom")
                    )
                }

                // 1:1 Viewport Box (Interactive Pan & Zoom)
                Box(
                    modifier = Modifier
                        .size(boxSizeDp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color.Black)
                        .border(
                            width = 2.dp,
                            color = MaterialTheme.colorScheme.primary,
                            shape = RoundedCornerShape(18.dp)
                        )
                        .pointerInput(mode, rotatedSource) {
                            if (mode == AudioTagManager.CoverFitMode.CUSTOM) {
                                detectTransformGestures { _, pan, gestureZoom, _ ->
                                    zoom = (zoom * gestureZoom).coerceIn(0.6f, 4.0f)
                                    panX += pan.x
                                    panY += pan.y
                                }
                            }
                        }
                        .testTag("cover_adjust_canvas_box"),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .clipToBounds()
                    ) {
                        val canvasW = size.width
                        val canvasH = size.height
                        val srcW = rotatedSource.width.toFloat()
                        val srcH = rotatedSource.height.toFloat()

                        when (mode) {
                            AudioTagManager.CoverFitMode.FILL -> {
                                val scale = maxOf(canvasW / srcW, canvasH / srcH)
                                val scaledW = srcW * scale
                                val scaledH = srcH * scale
                                val left = (canvasW - scaledW) / 2f
                                val top = (canvasH - scaledH) / 2f

                                drawImage(
                                    image = rotatedImageBitmap,
                                    dstOffset = androidx.compose.ui.unit.IntOffset(left.toInt(), top.toInt()),
                                    dstSize = androidx.compose.ui.unit.IntSize(scaledW.toInt(), scaledH.toInt())
                                )
                            }
                            AudioTagManager.CoverFitMode.FIT -> {
                                // Background gradient
                                drawRect(
                                    brush = androidx.compose.ui.graphics.Brush.linearGradient(
                                        colors = listOf(Color(0xFF1A1A24), Color(0xFF0A0A0F))
                                    )
                                )
                                val scale = minOf(canvasW / srcW, canvasH / srcH)
                                val scaledW = srcW * scale
                                val scaledH = srcH * scale
                                val left = (canvasW - scaledW) / 2f
                                val top = (canvasH - scaledH) / 2f

                                drawImage(
                                    image = rotatedImageBitmap,
                                    dstOffset = androidx.compose.ui.unit.IntOffset(left.toInt(), top.toInt()),
                                    dstSize = androidx.compose.ui.unit.IntSize(scaledW.toInt(), scaledH.toInt())
                                )
                            }
                            AudioTagManager.CoverFitMode.CUSTOM -> {
                                drawRect(Color(0xFF0F0F14))

                                val baseScale = maxOf(canvasW / srcW, canvasH / srcH)
                                val currentScale = baseScale * zoom
                                val scaledW = srcW * currentScale
                                val scaledH = srcH * currentScale

                                val centerX = canvasW / 2f + panX
                                val centerY = canvasH / 2f + panY

                                val left = centerX - (scaledW / 2f)
                                val top = centerY - (scaledH / 2f)

                                drawImage(
                                    image = rotatedImageBitmap,
                                    dstOffset = androidx.compose.ui.unit.IntOffset(left.toInt(), top.toInt()),
                                    dstSize = androidx.compose.ui.unit.IntSize(scaledW.toInt(), scaledH.toInt())
                                )
                            }
                        }

                        // Composition Grid Lines (Rule of thirds)
                        val gridColor = Color.White.copy(alpha = 0.25f)
                        drawLine(gridColor, Offset(canvasW / 3f, 0f), Offset(canvasW / 3f, canvasH), strokeWidth = 1.dp.toPx())
                        drawLine(gridColor, Offset(canvasW * 2f / 3f, 0f), Offset(canvasW * 2f / 3f, canvasH), strokeWidth = 1.dp.toPx())
                        drawLine(gridColor, Offset(0f, canvasH / 3f), Offset(canvasW, canvasH / 3f), strokeWidth = 1.dp.toPx())
                        drawLine(gridColor, Offset(0f, canvasH * 2f / 3f), Offset(canvasW, canvasH * 2f / 3f), strokeWidth = 1.dp.toPx())
                    }
                }

                // Custom Mode Toolbar: Zoom, Rotate, Center, Reset
                if (mode == AudioTagManager.CoverFitMode.CUSTOM) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Ukuran / Zoom",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "${(zoom * 100).toInt()}%",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            IconButton(
                                onClick = { zoom = (zoom - 0.15f).coerceAtLeast(0.6f) },
                                modifier = Modifier.size(34.dp).testTag("btn_zoom_out_cover")
                            ) {
                                Icon(Icons.Default.ZoomOut, contentDescription = "Perkecil", modifier = Modifier.size(18.dp))
                            }

                            Slider(
                                value = zoom,
                                onValueChange = { zoom = it },
                                valueRange = 0.6f..3.5f,
                                modifier = Modifier.weight(1f).testTag("slider_cover_zoom")
                            )

                            IconButton(
                                onClick = { zoom = (zoom + 0.15f).coerceAtMost(3.5f) },
                                modifier = Modifier.size(34.dp).testTag("btn_zoom_in_cover")
                            ) {
                                Icon(Icons.Default.ZoomIn, contentDescription = "Perbesar", modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }

                // Common Tool Buttons: Rotate, Center, Reset
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { rotation = (rotation + 90) % 360 },
                        modifier = Modifier.weight(1f).height(38.dp).testTag("btn_rotate_cover"),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.RotateRight, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Putar", fontSize = 11.sp)
                    }

                    OutlinedButton(
                        onClick = {
                            panX = 0f
                            panY = 0f
                        },
                        modifier = Modifier.weight(1f).height(38.dp).testTag("btn_center_cover"),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.FilterCenterFocus, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Tengah", fontSize = 11.sp)
                    }

                    OutlinedButton(
                        onClick = {
                            mode = AudioTagManager.CoverFitMode.FILL
                            zoom = 1.0f
                            panX = 0f
                            panY = 0f
                            rotation = 0
                        },
                        modifier = Modifier.weight(1f).height(38.dp).testTag("btn_reset_cover"),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.RestartAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Reset", fontSize = 11.sp)
                    }
                }

                // Action buttons: Cancel & Apply
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f).height(48.dp).testTag("btn_cancel_cover_adjust"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Batal", fontWeight = FontWeight.SemiBold)
                    }

                    Button(
                        onClick = {
                            onApply(mode, zoom, panX, panY, rotation, boxSizePx)
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        modifier = Modifier.weight(1.3f).height(48.dp).testTag("btn_apply_cover_adjust"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Terapkan Cover", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun MetadataFormCard(
    metadata: SongMetadata,
    onMetadataChanged: (SongMetadata) -> Unit
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Informasi Lagu",
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )

            // Judul Lagu
            OutlinedTextField(
                value = metadata.title,
                onValueChange = { onMetadataChanged(metadata.copy(title = it)) },
                label = { Text("Judul Lagu") },
                placeholder = { Text("Masukkan judul lagu") },
                leadingIcon = {
                    Icon(Icons.Default.MusicNote, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("input_song_title"),
                shape = RoundedCornerShape(12.dp)
            )

            // Penyanyi / Artis
            OutlinedTextField(
                value = metadata.artist,
                onValueChange = { onMetadataChanged(metadata.copy(artist = it)) },
                label = { Text("Penyanyi / Artis") },
                placeholder = { Text("Contoh: Tulus, Sheila On 7, Denny Caknan") },
                leadingIcon = {
                    Icon(Icons.Default.Person, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("input_song_artist"),
                shape = RoundedCornerShape(12.dp)
            )

            // Album
            OutlinedTextField(
                value = metadata.album,
                onValueChange = { onMetadataChanged(metadata.copy(album = it)) },
                label = { Text("Nama Album") },
                placeholder = { Text("Masukkan nama album") },
                leadingIcon = {
                    Icon(Icons.Default.Album, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("input_song_album"),
                shape = RoundedCornerShape(12.dp)
            )

            // Artis Album (Opsional)
            OutlinedTextField(
                value = metadata.albumArtist,
                onValueChange = { onMetadataChanged(metadata.copy(albumArtist = it)) },
                label = { Text("Artis Album (Opsional)") },
                placeholder = { Text("Artis album (jika kompilasi / band)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("input_song_album_artist"),
                shape = RoundedCornerShape(12.dp)
            )

            // Row: Tahun & Nomor Trek
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = metadata.year,
                    onValueChange = { onMetadataChanged(metadata.copy(year = it.take(4))) },
                    label = { Text("Tahun") },
                    placeholder = { Text("2024") },
                    leadingIcon = {
                        Icon(Icons.Default.CalendarToday, contentDescription = null, modifier = Modifier.size(18.dp))
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.weight(1f).testTag("input_song_year"),
                    shape = RoundedCornerShape(12.dp)
                )

                OutlinedTextField(
                    value = metadata.trackNumber,
                    onValueChange = { onMetadataChanged(metadata.copy(trackNumber = it.take(3))) },
                    label = { Text("No. Trek") },
                    placeholder = { Text("1") },
                    leadingIcon = {
                        Icon(Icons.Default.Numbers, contentDescription = null, modifier = Modifier.size(18.dp))
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.weight(1f).testTag("input_song_track_number"),
                    shape = RoundedCornerShape(12.dp)
                )
            }

            // Genre
            OutlinedTextField(
                value = metadata.genre,
                onValueChange = { onMetadataChanged(metadata.copy(genre = it)) },
                label = { Text("Genre Musik") },
                placeholder = { Text("Pilih atau ketik genre") },
                leadingIcon = {
                    Icon(Icons.Default.Category, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("input_song_genre"),
                shape = RoundedCornerShape(12.dp)
            )

            // Quick Genre Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                QUICK_GENRES.forEach { genreName ->
                    val isSelected = metadata.genre.equals(genreName, ignoreCase = true)
                    FilterChip(
                        selected = isSelected,
                        onClick = { onMetadataChanged(metadata.copy(genre = genreName)) },
                        label = { Text(genreName, fontSize = 12.sp) },
                        leadingIcon = if (isSelected) {
                            { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp)) }
                        } else null,
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    )
                }
            }

            // Komposer / Pencipta Lagu
            OutlinedTextField(
                value = metadata.composer,
                onValueChange = { onMetadataChanged(metadata.copy(composer = it)) },
                label = { Text("Pencipta / Komposer (Opsional)") },
                placeholder = { Text("Nama pencipta lagu") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("input_song_composer"),
                shape = RoundedCornerShape(12.dp)
            )

            // Komentar / Catatan
            OutlinedTextField(
                value = metadata.comment,
                onValueChange = { onMetadataChanged(metadata.copy(comment = it)) },
                label = { Text("Komentar / Catatan (Opsional)") },
                placeholder = { Text("Catatan pribadi atau sumber lagu") },
                leadingIcon = {
                    Icon(Icons.Default.Comment, contentDescription = null)
                },
                maxLines = 2,
                modifier = Modifier.fillMaxWidth().testTag("input_song_comment"),
                shape = RoundedCornerShape(12.dp)
            )
        }
    }
}

@Composable
private fun AudioInfoCard(metadata: SongMetadata) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Informasi File Audio",
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "Format Asli",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp
                    )
                    Text(
                        text = metadata.format,
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Column {
                    Text(
                        text = "Durasi",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp
                    )
                    Text(
                        text = TimeFormatUtils.formatDuration(metadata.durationMs),
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Column {
                    Text(
                        text = "Sample Rate",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp
                    )
                    Text(
                        text = "${metadata.sampleRate / 1000} kHz",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 13.sp
                    )
                }

                if (metadata.bitrateKbps > 0) {
                    Column {
                        Text(
                            text = "Bitrate",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp
                        )
                        Text(
                            text = "${metadata.bitrateKbps} kbps",
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ActionButtonsSection(
    metadata: SongMetadata,
    onSave: () -> Unit,
    onSaveAs: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Button(
            onClick = onSave,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .testTag("btn_save_song_tags")
        ) {
            Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                text = "Simpan Info & Cover Lagu",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
        }

        OutlinedButton(
            onClick = onSaveAs,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("btn_save_as_tags")
        ) {
            Icon(Icons.Default.SaveAs, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                text = "Simpan Ke Memori HP (Pilih Lokasi)",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

package com.example.ui.screens

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
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
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Comment
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FilterCenterFocus
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Info
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
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.graphics.drawscope.withTransform
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
import coil.compose.AsyncImage
import coil.request.ImageRequest
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
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val tagMetadata by viewModel.tagMetadata.collectAsState()
    val selectedFile by viewModel.selectedTagFile.collectAsState()

    var adjustingBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isProcessingCover by remember { mutableStateOf(false) }

    // Photo picker for album artwork (Google Play policy compliant Android Photo Picker)
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { imageUri: Uri? ->
        if (imageUri != null) {
            coroutineScope.launch {
                isProcessingCover = true
                val bitmap = AudioTagManager.loadBitmapFromUri(context, imageUri)
                isProcessingCover = false
                if (bitmap != null) {
                    adjustingBitmap = bitmap
                }
            }
        }
    }

    // Fallback file image picker (GetContent) for maximum device compatibility
    val fileImagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { imageUri: Uri? ->
        if (imageUri != null) {
            coroutineScope.launch {
                isProcessingCover = true
                val bitmap = AudioTagManager.loadBitmapFromUri(context, imageUri)
                isProcessingCover = false
                if (bitmap != null) {
                    adjustingBitmap = bitmap
                }
            }
        }
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
                onChangeCover = {
                    photoPickerLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                onPickFileCover = {
                    fileImagePickerLauncher.launch("image/*")
                },
                onAdjustCover = {
                    metadata.coverArtBytes?.let { bytes ->
                        val bitmap = AudioTagManager.loadBitmapFromBytes(bytes)
                        if (bitmap != null) {
                            adjustingBitmap = bitmap
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
                    viewModel.saveSongMetadata()
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

    // Cover Adjustment Dialog (Size, Crop, Pan, Rotate)
    val currentAdjusting = adjustingBitmap
    if (currentAdjusting != null) {
        CoverAdjustDialog(
            sourceBitmap = currentAdjusting,
            onDismiss = { adjustingBitmap = null },
            onApply = { scale, panX, panY, rotation, previewBoxSize ->
                coroutineScope.launch {
                    isProcessingCover = true
                    val (croppedBytes, mime) = AudioTagManager.cropSquareCover(
                        source = currentAdjusting,
                        scale = scale,
                        panX = panX,
                        panY = panY,
                        rotationDegrees = rotation,
                        previewBoxSize = previewBoxSize
                    )
                    viewModel.updateCoverArt(croppedBytes, mime)
                    isProcessingCover = false
                    adjustingBitmap = null
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

@Composable
private fun CoverArtSection(
    metadata: SongMetadata,
    isLoading: Boolean,
    onChangeCover: () -> Unit,
    onPickFileCover: () -> Unit,
    onAdjustCover: () -> Unit,
    onRemoveCover: () -> Unit,
    onChangeAudioFile: () -> Unit
) {
    val coverImageBitmap = remember(metadata.coverArtBytes) {
        metadata.coverArtBytes?.let { bytes ->
            try {
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
            } catch (e: Exception) {
                null
            }
        }
    }

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Cover / Gambar Lagu",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )

                OutlinedButton(
                    onClick = onChangeAudioFile,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.height(34.dp).testTag("btn_change_audio_file")
                ) {
                    Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Ganti File", fontSize = 12.sp)
                }
            }

            // Cover Image Box
            Box(
                modifier = Modifier
                    .size(180.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .border(
                        width = 1.5.dp,
                        color = if (coverImageBitmap != null) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outlineVariant,
                        shape = RoundedCornerShape(18.dp)
                    )
                    .background(MaterialTheme.colorScheme.surface)
                    .clickable {
                        if (coverImageBitmap != null) {
                            onAdjustCover()
                        } else {
                            onChangeCover()
                        }
                    }
                    .testTag("cover_image_preview"),
                contentAlignment = Alignment.Center
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(36.dp)
                    )
                } else if (coverImageBitmap != null) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        Image(
                            bitmap = coverImageBitmap,
                            contentDescription = "Cover Album",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )

                        // Overlay hint badge at bottom
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .background(Color.Black.copy(alpha = 0.6f))
                                .padding(vertical = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    Icons.Default.Crop,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(12.dp)
                                )
                                Text(
                                    text = "Sentuh untuk atur posisi",
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                } else {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AddPhotoAlternate,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(44.dp)
                        )
                        Text(
                            text = "Sentuh untuk pasang cover gambar",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            // Cover Action Buttons
            if (coverImageBitmap != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = onChangeCover,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f).height(40.dp).testTag("btn_change_cover_image")
                    ) {
                        Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Ganti", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }

                    OutlinedButton(
                        onClick = onAdjustCover,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1.1f).height(40.dp).testTag("btn_adjust_cover_image")
                    ) {
                        Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Atur Posisi", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }

                    OutlinedButton(
                        onClick = onRemoveCover,
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(0.9f).height(40.dp).testTag("btn_remove_cover_image")
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
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
                        onClick = onChangeCover,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1.3f).height(42.dp).testTag("btn_pick_cover_image")
                    ) {
                        Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Tambah Gambar", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = onPickFileCover,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f).height(42.dp).testTag("btn_pick_file_cover")
                    ) {
                        Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Pilih File", fontSize = 12.sp)
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
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "Informasi Teknis File",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            }

            Text(
                text = "Nama Asli: ${metadata.originalFileName}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Format: ${metadata.format}",
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp
                )
                if (metadata.durationMs > 0) {
                    Text("•", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                    Text(
                        text = "Durasi: ${TimeFormatUtils.formatDuration(metadata.durationMs)}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp
                    )
                }
                if (metadata.bitrateKbps > 0) {
                    Text("•", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                    Text(
                        text = "${metadata.bitrateKbps} kbps",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp
                    )
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

@Composable
fun CoverAdjustDialog(
    sourceBitmap: Bitmap,
    onDismiss: () -> Unit,
    onApply: (scale: Float, panX: Float, panY: Float, rotationDegrees: Int, previewBoxSize: Float) -> Unit
) {
    var zoom by remember { mutableStateOf(1.0f) }
    var panX by remember { mutableStateOf(0f) }
    var panY by remember { mutableStateOf(0f) }
    var rotation by remember { mutableStateOf(0) }
    val density = LocalDensity.current

    val boxSizeDp = 260.dp
    val boxSizePx = with(density) { boxSizeDp.toPx() }

    val imageBitmap = remember(sourceBitmap) { sourceBitmap.asImageBitmap() }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .padding(vertical = 16.dp)
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
                            text = "Sesuaikan Cover Lagu",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Geser, perbesar & putar untuk bingkai 1:1",
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

                // Square Viewport (1:1 aspect ratio)
                Box(
                    modifier = Modifier
                        .size(boxSizeDp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.Black)
                        .border(
                            width = 2.dp,
                            color = MaterialTheme.colorScheme.primary,
                            shape = RoundedCornerShape(16.dp)
                        )
                        .pointerInput(sourceBitmap) {
                            detectTransformGestures { _, pan, gestureZoom, _ ->
                                zoom = (zoom * gestureZoom).coerceIn(0.8f, 4.5f)
                                panX += pan.x
                                panY += pan.y
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
                        val effectiveW = if (rotation % 180 == 0) sourceBitmap.width.toFloat() else sourceBitmap.height.toFloat()
                        val effectiveH = if (rotation % 180 == 0) sourceBitmap.height.toFloat() else sourceBitmap.width.toFloat()
                        val baseScale = (size.width / effectiveW.coerceAtLeast(1f)).coerceAtLeast(size.height / effectiveH.coerceAtLeast(1f))
                        val currentScale = baseScale * zoom

                        withTransform({
                            translate(size.width / 2f + panX, size.height / 2f + panY)
                            scale(currentScale, currentScale)
                            rotate(rotation.toFloat())
                            translate(-sourceBitmap.width / 2f, -sourceBitmap.height / 2f)
                        }) {
                            drawImage(imageBitmap)
                        }

                        // Subtle grid framing lines (rule of thirds)
                        val gridColor = Color.White.copy(alpha = 0.30f)
                        drawLine(gridColor, Offset(size.width / 3f, 0f), Offset(size.width / 3f, size.height), strokeWidth = 1.dp.toPx())
                        drawLine(gridColor, Offset(size.width * 2f / 3f, 0f), Offset(size.width * 2f / 3f, size.height), strokeWidth = 1.dp.toPx())
                        drawLine(gridColor, Offset(0f, size.height / 3f), Offset(size.width, size.height / 3f), strokeWidth = 1.dp.toPx())
                        drawLine(gridColor, Offset(0f, size.height * 2f / 3f), Offset(size.width, size.height * 2f / 3f), strokeWidth = 1.dp.toPx())
                    }
                }

                // Controls: Zoom Slider and Buttons
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
                            onClick = { zoom = (zoom - 0.15f).coerceAtLeast(0.8f) },
                            modifier = Modifier.size(34.dp).testTag("btn_zoom_out_cover")
                        ) {
                            Icon(Icons.Default.ZoomOut, contentDescription = "Perkecil", modifier = Modifier.size(18.dp))
                        }

                        Slider(
                            value = zoom,
                            onValueChange = { zoom = it },
                            valueRange = 0.8f..4.0f,
                            modifier = Modifier.weight(1f).testTag("slider_cover_zoom")
                        )

                        IconButton(
                            onClick = { zoom = (zoom + 0.15f).coerceAtMost(4.0f) },
                            modifier = Modifier.size(34.dp).testTag("btn_zoom_in_cover")
                        ) {
                            Icon(Icons.Default.ZoomIn, contentDescription = "Perbesar", modifier = Modifier.size(18.dp))
                        }
                    }

                    // Toolbar buttons: Rotate, Center, Reset
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
                }

                // Action buttons: Cancel & Apply
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f).height(46.dp).testTag("btn_cancel_cover_adjust"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Batal", fontWeight = FontWeight.SemiBold)
                    }

                    Button(
                        onClick = {
                            onApply(zoom, panX, panY, rotation, boxSizePx)
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        modifier = Modifier.weight(1.3f).height(46.dp).testTag("btn_apply_cover_adjust"),
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

package com.example.ui.screens

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Comment
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Numbers
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.SaveAs
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

    // Photo picker for album artwork (Google Play policy compliant Android Photo Picker)
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { imageUri: Uri? ->
        if (imageUri != null) {
            coroutineScope.launch {
                val processed = AudioTagManager.processImageForCover(context, imageUri)
                if (processed != null) {
                    viewModel.updateCoverArt(processed.first, processed.second)
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
                onChangeCover = {
                    photoPickerLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
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
    onChangeCover: () -> Unit,
    onRemoveCover: () -> Unit,
    onChangeAudioFile: () -> Unit
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
                    .size(170.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant,
                        shape = RoundedCornerShape(16.dp)
                    )
                    .background(MaterialTheme.colorScheme.surface)
                    .clickable { onChangeCover() }
                    .testTag("cover_image_preview"),
                contentAlignment = Alignment.Center
            ) {
                if (metadata.coverArtBytes != null) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(metadata.coverArtBytes)
                            .crossfade(true)
                            .build(),
                        contentDescription = "Cover Album",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
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
            Row(
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
                    modifier = Modifier.height(40.dp).testTag("btn_pick_cover_image")
                ) {
                    Icon(
                        imageVector = if (metadata.coverArtBytes != null) Icons.Default.Edit else Icons.Default.AddPhotoAlternate,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = if (metadata.coverArtBytes != null) "Ganti Gambar" else "Tambah Gambar",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                if (metadata.coverArtBytes != null) {
                    OutlinedButton(
                        onClick = onRemoveCover,
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.height(40.dp).testTag("btn_remove_cover_image")
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Hapus", fontSize = 13.sp)
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

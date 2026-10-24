package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.audio.AudioTagManager
import com.example.audio.AudioViewModel
import com.example.ui.components.TimeFormatUtils
import androidx.compose.ui.graphics.asImageBitmap

@Composable
fun LibraryScreen(
    viewModel: AudioViewModel,
    onNavigateToTagEditor: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val exportedFiles by viewModel.exportedFiles.collectAsState()
    val playingPath by viewModel.libraryPlayingPath.collectAsState()

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Koleksi Musik Studio",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Lokasi: Penyimpanan Internal > Music > LishStudioRecord",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        IconButton(
                            onClick = { viewModel.refreshExportedLibrary() },
                            modifier = Modifier.testTag("refresh_library_button")
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = "Segarkan Koleksi")
                        }
                    }
                }
            }
        }

        if (exportedFiles.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.FolderOpen,
                            contentDescription = null,
                            modifier = Modifier.size(56.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Belum Ada File yang Diekspor",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "Potong lagu, edit tag & cover, atau gabungkan musik untuk melihat hasilnya di sini.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(exportedFiles) { audio ->
                val isPlayingThis = playingPath == audio.file.absolutePath
                val coverArt = remember(audio.coverArtBytes) {
                    audio.coverArtBytes?.let { AudioTagManager.loadBitmapFromBytes(it, 300) }
                }

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("library_item_${audio.file.name}"),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isPlayingThis) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        }
                    )
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Cover Thumbnail or Placeholder
                            Box(
                                modifier = Modifier
                                    .size(54.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.surface),
                                contentAlignment = Alignment.Center
                            ) {
                                if (coverArt != null) {
                                    Image(
                                        bitmap = coverArt.asImageBitmap(),
                                        contentDescription = "Cover Album",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.MusicNote,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = audio.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "${TimeFormatUtils.formatDuration(audio.durationMs)} • ${audio.format.displayName} • ${TimeFormatUtils.formatFileSize(audio.sizeBytes)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            // Inline Play/Pause
                            IconButton(
                                onClick = { viewModel.toggleLibraryPlay(audio) },
                                modifier = Modifier.testTag("play_library_${audio.file.name}")
                            ) {
                                Icon(
                                    imageVector = if (isPlayingThis) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = if (isPlayingThis) "Jeda" else "Putar",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Actions Row: Open in Tag Editor, Share, Delete
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            FilledTonalButton(
                                onClick = {
                                    viewModel.loadTagFromFile(audio.file)
                                    onNavigateToTagEditor()
                                },
                                modifier = Modifier.testTag("edit_tags_${audio.file.name}")
                            ) {
                                Icon(Icons.Default.EditNote, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Edit Tag & Cover")
                            }

                            Spacer(modifier = Modifier.width(6.dp))

                            IconButton(
                                onClick = { viewModel.shareExportedFile(context, audio) },
                                modifier = Modifier.testTag("share_library_${audio.file.name}")
                            ) {
                                Icon(Icons.Default.Share, contentDescription = "Bagikan")
                            }

                            IconButton(
                                onClick = { viewModel.deleteExportedFile(audio) },
                                modifier = Modifier.testTag("delete_library_${audio.file.name}")
                            ) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "Hapus",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

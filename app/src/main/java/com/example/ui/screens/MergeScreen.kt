package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallMerge
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.audio.AudioDecoder
import com.example.audio.AudioFormat
import com.example.audio.AudioViewModel
import com.example.ui.components.TimeFormatUtils

@Composable
fun MergeScreen(
    viewModel: AudioViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val mergeTracks by viewModel.mergeTracks.collectAsState()
    val mergeGapMs by viewModel.mergeGapMs.collectAsState()
    val sampleTracks by viewModel.sampleTracks.collectAsState()

    var outputFileName by remember { mutableStateOf("Gabungan_Lagu_Studio") }

    val multiPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        uris.forEach { uri ->
            val track = AudioDecoder.extractMetadata(context, uri = uri)
            viewModel.addTrackToMerge(track)
        }
    }

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
                    Text(
                        text = "Penggabung Lagu (Audio Merger)",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "Gabungkan beberapa lagu atau rekaman menjadi satu trek utuh secara mulus.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { multiPicker.launch("audio/*") },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("add_tracks_button")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Pilih dari HP")
                        }

                        if (sampleTracks.isNotEmpty()) {
                            OutlinedButton(
                                onClick = {
                                    sampleTracks.take(2).forEach { viewModel.addTrackToMerge(it) }
                                },
                                modifier = Modifier.testTag("add_sample_tracks_button")
                            ) {
                                Text("+ Trek Contoh")
                            }
                        }
                    }
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "Jeda Antar Lagu (Gap)")
                        Text(text = "${mergeGapMs / 1000f} detik")
                    }
                    Slider(
                        value = mergeGapMs.toFloat(),
                        onValueChange = { viewModel.setMergeGapMs(it.toLong()) },
                        valueRange = 0f..3000f,
                        steps = 6,
                        modifier = Modifier.testTag("gap_slider")
                    )
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Daftar Trek (${mergeTracks.size})",
                    style = MaterialTheme.typography.titleSmall
                )
                if (mergeTracks.isNotEmpty()) {
                    OutlinedButton(
                        onClick = { viewModel.clearMergeTracks() },
                        modifier = Modifier.testTag("clear_merge_list")
                    ) {
                        Text("Kosongkan")
                    }
                }
            }
        }

        if (mergeTracks.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Belum ada trek yang dipilih",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            itemsIndexed(mergeTracks) { index, track ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${index + 1}.",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.width(28.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = track.title, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                text = "${TimeFormatUtils.formatDuration(track.durationMs)} • ${track.format.displayName}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(
                            onClick = { viewModel.moveTrackMergeUp(index) },
                            enabled = index > 0
                        ) {
                            Icon(Icons.Default.ArrowUpward, contentDescription = "Pindah ke Atas")
                        }
                        IconButton(
                            onClick = { viewModel.moveTrackMergeDown(index) },
                            enabled = index < mergeTracks.size - 1
                        ) {
                            Icon(Icons.Default.ArrowDownward, contentDescription = "Pindah ke Bawah")
                        }
                        IconButton(onClick = { viewModel.removeTrackFromMerge(index) }) {
                            Icon(Icons.Default.Delete, contentDescription = "Hapus dari Daftar")
                        }
                    }
                }
            }

            item {
                Button(
                    onClick = {
                        viewModel.executeMerge(outputFileName, AudioFormat.MP3)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .testTag("execute_merge_button")
                ) {
                    Icon(Icons.AutoMirrored.Filled.CallMerge, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Gabungkan Sekarang (Format MP3)", style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

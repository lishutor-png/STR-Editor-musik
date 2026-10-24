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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.Loop
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.audio.AudioViewModel
import com.example.ui.components.EqualizerSection
import com.example.ui.components.ExportDialog
import com.example.ui.components.TimeFormatUtils
import com.example.ui.components.WaveformView
import java.util.Locale

@Composable
fun TrimScreen(
    viewModel: AudioViewModel,
    modifier: Modifier = Modifier
) {
    val activeTrack by viewModel.activeTrack.collectAsState()
    val activePcm by viewModel.activePcm.collectAsState()
    val waveformBars by viewModel.waveformBars.collectAsState()
    val startMs by viewModel.startMs.collectAsState()
    val endMs by viewModel.endMs.collectAsState()
    val volumeGain by viewModel.volumeGain.collectAsState()
    val fadeInMs by viewModel.fadeInMs.collectAsState()
    val fadeOutMs by viewModel.fadeOutMs.collectAsState()
    val equalizerSettings by viewModel.equalizerSettings.collectAsState()
    val isPlaying by viewModel.player.isPlaying.collectAsState()
    val currentPositionMs by viewModel.player.currentPositionMs.collectAsState()
    val isLooping by viewModel.player.isLooping.collectAsState()

    var showExportDialog by remember { mutableStateOf(false) }

    val audioPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.loadTrackFromUri(it) }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Track Header Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("track_header_card"),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = activeTrack?.title ?: "Belum ada trek yang dibuka",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (activePcm != null) {
                                "${TimeFormatUtils.formatDuration(activePcm!!.durationMs)} • ${activeTrack?.format?.displayName ?: "Audio"} • ${activeTrack?.sampleRate ?: 44100} Hz"
                            } else "Pilih file musik untuk mulai memotong",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Button(
                        onClick = { audioPicker.launch("audio/*") },
                        modifier = Modifier.testTag("open_audio_button")
                    ) {
                        Icon(imageVector = Icons.Default.FileOpen, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Buka File")
                    }
                }
            }
        }

        // Waveform Visualizer
        if (activePcm != null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Area Potongan: ${TimeFormatUtils.formatMs(startMs)} - ${TimeFormatUtils.formatMs(endMs)}",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Durasi: ${TimeFormatUtils.formatMs(endMs - startMs)}",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    WaveformView(
                        waveform = waveformBars,
                        durationMs = activePcm!!.durationMs,
                        startMs = startMs,
                        endMs = endMs,
                        currentPlayheadMs = currentPositionMs,
                        fadeInMs = fadeInMs,
                        fadeOutMs = fadeOutMs,
                        onRangeChange = { s, e -> viewModel.updateTrimRange(s, e) },
                        onSeek = { pos -> viewModel.player.seekTo(pos) }
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Player Controls Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { viewModel.player.seekTo(startMs) },
                            modifier = Modifier.testTag("restart_button")
                        ) {
                            Icon(Icons.Default.Replay, contentDescription = "Ulangi dari Awal")
                        }

                        Button(
                            onClick = { viewModel.player.togglePlayPause() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            ),
                            modifier = Modifier.testTag("play_pause_button")
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (isPlaying) "Jeda" else "Putar"
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (isPlaying) "Jeda" else "Putar Potongan")
                        }

                        IconButton(
                            onClick = { viewModel.player.toggleLoop() },
                            modifier = Modifier.testTag("loop_button")
                        ) {
                            Icon(
                                Icons.Default.Loop,
                                contentDescription = "Ulang Otomatis",
                                tint = if (isLooping) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Fine Tuning Adjusters
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Pengaturan Presisi Potongan (Milidetik)",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "Titik Awal: ${TimeFormatUtils.formatMs(startMs)}")
                        Row {
                            FilledTonalButton(
                                onClick = { viewModel.adjustStartMs(-100L) },
                                modifier = Modifier.testTag("start_minus_100")
                            ) {
                                Icon(Icons.Default.Remove, contentDescription = null)
                                Text("0.1s")
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                            FilledTonalButton(
                                onClick = { viewModel.adjustStartMs(100L) },
                                modifier = Modifier.testTag("start_plus_100")
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null)
                                Text("0.1s")
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "Titik Akhir: ${TimeFormatUtils.formatMs(endMs)}")
                        Row {
                            FilledTonalButton(
                                onClick = { viewModel.adjustEndMs(-100L) },
                                modifier = Modifier.testTag("end_minus_100")
                            ) {
                                Icon(Icons.Default.Remove, contentDescription = null)
                                Text("0.1s")
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                            FilledTonalButton(
                                onClick = { viewModel.adjustEndMs(100L) },
                                modifier = Modifier.testTag("end_plus_100")
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null)
                                Text("0.1s")
                            }
                        }
                    }
                }
            }

            // Volume Gain & Fade Effects
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Efek Volume & Transisi Suara",
                        style = MaterialTheme.typography.titleSmall
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Volume Boost")
                        Text(String.format(Locale.getDefault(), "%.0f%%", volumeGain * 100f))
                    }
                    Slider(
                        value = volumeGain,
                        onValueChange = { viewModel.setVolumeGain(it) },
                        valueRange = 0.5f..2.5f,
                        modifier = Modifier.testTag("volume_slider")
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Fade In (Masuk Lembut)")
                        Text("${fadeInMs / 1000f} dtk")
                    }
                    Slider(
                        value = fadeInMs.toFloat(),
                        onValueChange = { viewModel.setFadeInMs(it.toLong()) },
                        valueRange = 0f..5000f,
                        modifier = Modifier.testTag("fade_in_slider")
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Fade Out (Keluar Lembut)")
                        Text("${fadeOutMs / 1000f} dtk")
                    }
                    Slider(
                        value = fadeOutMs.toFloat(),
                        onValueChange = { viewModel.setFadeOutMs(it.toLong()) },
                        valueRange = 0f..5000f,
                        modifier = Modifier.testTag("fade_out_slider")
                    )
                }
            }

            // Equalizer Section
            EqualizerSection(
                settings = equalizerSettings,
                onPresetSelect = { viewModel.setEqualizerPreset(it) },
                onBassChange = { viewModel.setBassGain(it) },
                onMidChange = { viewModel.setMidGain(it) },
                onTrebleChange = { viewModel.setTrebleGain(it) },
                onReset = { viewModel.resetEqualizer() }
            )

            // Export Action
            Button(
                onClick = { showExportDialog = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .testTag("export_trimmed_button"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(Icons.Default.IosShare, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Ekspor Audio Hasil Potongan", style = MaterialTheme.typography.titleMedium)
            }
        }

        if (showExportDialog) {
            val baseName = activeTrack?.title ?: "Audio_Potongan"
            ExportDialog(
                initialFileName = "${baseName}_potong",
                onDismiss = { showExportDialog = false },
                onConfirmExport = { options ->
                    showExportDialog = false
                    viewModel.exportCurrentTrack(options)
                }
            )
        }
    }
}

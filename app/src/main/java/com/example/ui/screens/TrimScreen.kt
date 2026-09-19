package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Loop
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.audio.AudioViewModel
import com.example.ui.components.EqualizerSection
import com.example.ui.components.ExportDialog
import com.example.ui.components.TimeFormatUtils
import com.example.ui.components.WaveformView
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TrimScreen(
    viewModel: AudioViewModel,
    onOpenFilePicker: () -> Unit,
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

    val durationMs = activePcm?.durationMs ?: 0L
    val selectedDurationMs = (endMs - startMs).coerceAtLeast(0L)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Track Header Card
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.GraphicEq,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = activeTrack?.title ?: "Belum ada lagu dipilih",
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    text = if (activeTrack != null) TimeFormatUtils.formatDuration(durationMs) else "Pilih file untuk diedit",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 12.sp
                                )
                                if (activeTrack != null) {
                                    Text("•", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f), fontSize = 12.sp)
                                    Text(
                                        text = activeTrack?.format ?: "AUDIO",
                                        color = MaterialTheme.colorScheme.primary,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }

                    Button(
                        onClick = onOpenFilePicker,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.testTag("btn_open_file")
                    ) {
                        Icon(
                            imageVector = Icons.Default.FolderOpen,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("Buka File", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        if (activeTrack == null) {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Audiotrack,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                    Text(
                        text = "Belum Ada Lagu Dipilih",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Silakan ketuk tombol 'Buka File' untuk memilih lagu (MP3, WAV, M4A, FLAC) dari penyimpanan HP Anda untuk mulai memotong.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        lineHeight = 18.sp
                    )
                    Button(
                        onClick = onOpenFilePicker,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Pilih Lagu Dari Penyimpanan", fontWeight = FontWeight.Bold)
                    }
                }
            }
        } else {

        // Interactive Waveform Visualizer
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Waveform & Seleksi Potong",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    androidx.compose.material3.TextButton(
                        onClick = { viewModel.resetTrimRange() },
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier.height(28.dp).testTag("btn_reset_selection")
                    ) {
                        Text(
                            text = "Pilih Semua (Reset)",
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                WaveformView(
                    waveform = waveformBars,
                    durationMs = durationMs,
                    startMs = startMs,
                    endMs = endMs,
                    currentPlayheadMs = currentPositionMs,
                    fadeInMs = fadeInMs,
                    fadeOutMs = fadeOutMs,
                    onRangeChange = { s, e -> viewModel.updateTrimRange(s, e) },
                    onSeek = { pos -> viewModel.player.seekTo(pos) }
                )

                // Selection Info Pill & Playhead Indicator
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.tertiary)
                        )
                        Text(
                            text = "Posisi: ${TimeFormatUtils.formatMs(currentPositionMs)}",
                            color = MaterialTheme.colorScheme.tertiary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "Terpilih: ${TimeFormatUtils.formatMs(selectedDurationMs)}",
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // Precision Trim Time Controls (Start & End Markers)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Start Time Box
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.weight(1f)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "Mulai (Start)",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = TimeFormatUtils.formatMs(startMs),
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        StepButton(text = "-1s", onClick = { viewModel.adjustStartMs(-1000L) }, modifier = Modifier.weight(1f))
                        StepButton(text = "-0.1s", onClick = { viewModel.adjustStartMs(-100L) }, modifier = Modifier.weight(1f))
                        StepButton(text = "+0.1s", onClick = { viewModel.adjustStartMs(100L) }, modifier = Modifier.weight(1f))
                        StepButton(text = "+1s", onClick = { viewModel.adjustStartMs(1000L) }, modifier = Modifier.weight(1f))
                    }
                }
            }

            // End Time Box
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.weight(1f)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "Selesai (End)",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = TimeFormatUtils.formatMs(endMs),
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        StepButton(text = "-1s", onClick = { viewModel.adjustEndMs(-1000L) }, modifier = Modifier.weight(1f))
                        StepButton(text = "-0.1s", onClick = { viewModel.adjustEndMs(-100L) }, modifier = Modifier.weight(1f))
                        StepButton(text = "+0.1s", onClick = { viewModel.adjustEndMs(100L) }, modifier = Modifier.weight(1f))
                        StepButton(text = "+1s", onClick = { viewModel.adjustEndMs(1000L) }, modifier = Modifier.weight(1f))
                    }
                }
            }
        }

        // Real-Time Playback Controls Bar
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp, horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                // Rewind to Start
                IconButton(
                    onClick = { viewModel.player.seekTo(startMs) },
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surface)
                        .testTag("btn_player_rewind")
                ) {
                    Icon(
                        imageVector = Icons.Default.Replay,
                        contentDescription = "Ulang dari Awal",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }

                // Big Play / Pause Preview Button
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                        .clickable { viewModel.player.togglePlayPause() }
                        .testTag("btn_player_toggle"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Jeda Pratinjau" else "Putar Pratinjau",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(36.dp)
                    )
                }

                // Toggle Loop
                IconButton(
                    onClick = { viewModel.player.toggleLoop() },
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(
                            if (isLooping) MaterialTheme.colorScheme.primary.copy(alpha = 0.20f)
                            else MaterialTheme.colorScheme.surface
                        )
                        .border(
                            width = if (isLooping) 1.5.dp else 0.dp,
                            color = if (isLooping) MaterialTheme.colorScheme.primary else Color.Transparent,
                            shape = CircleShape
                        )
                        .testTag("btn_player_loop")
                ) {
                    Icon(
                        imageVector = Icons.Default.Loop,
                        contentDescription = "Ulang Otomatis (Loop)",
                        tint = if (isLooping) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Quick Equalizer Section
        EqualizerSection(
            settings = equalizerSettings,
            onSelectPreset = { viewModel.setEqualizerPreset(it) },
            onBassChange = { viewModel.setBassGain(it) },
            onMidChange = { viewModel.setMidGain(it) },
            onTrebleChange = { viewModel.setTrebleGain(it) },
            onReset = { viewModel.resetEqualizer() }
        )

        // Sound Effects Studio: Volume Gain & Fade In/Out
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Efek Suara & Penyesuaian Volume",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Volume Gain Slider (0% to 250%)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Volume Suara", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                        val percent = (volumeGain * 100).toInt()
                        val badgeColor = when {
                            percent > 100 -> MaterialTheme.colorScheme.tertiary
                            percent == 100 -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(badgeColor.copy(alpha = 0.15f))
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = if (percent > 100) "$percent% (Boost)" else "$percent%",
                                color = badgeColor,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Slider(
                        value = volumeGain,
                        onValueChange = { viewModel.setVolumeGain(it) },
                        valueRange = 0.0f..2.5f,
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary,
                            inactiveTrackColor = MaterialTheme.colorScheme.surface
                        ),
                        modifier = Modifier.testTag("slider_volume")
                    )
                }

                // Fade In Slider (0 to 6 seconds)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Fade In (Awal Lagu)", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                        Text(
                            text = String.format(Locale.getDefault(), "%.1f detik", fadeInMs / 1000.0),
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Slider(
                        value = fadeInMs.toFloat(),
                        onValueChange = { viewModel.setFadeInMs(it.toLong()) },
                        valueRange = 0f..6000f,
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary,
                            inactiveTrackColor = MaterialTheme.colorScheme.surface
                        ),
                        modifier = Modifier.testTag("slider_fade_in")
                    )
                }

                // Fade Out Slider (0 to 6 seconds)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Fade Out (Akhir Lagu)", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                        Text(
                            text = String.format(Locale.getDefault(), "%.1f detik", fadeOutMs / 1000.0),
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Slider(
                        value = fadeOutMs.toFloat(),
                        onValueChange = { viewModel.setFadeOutMs(it.toLong()) },
                        valueRange = 0f..6000f,
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.error,
                            activeTrackColor = MaterialTheme.colorScheme.error,
                            inactiveTrackColor = MaterialTheme.colorScheme.surface
                        ),
                        modifier = Modifier.testTag("slider_fade_out")
                    )
                }
            }
        }

        // Primary Action: Export Button
        Button(
            onClick = { showExportDialog = true },
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .testTag("btn_export_dialog")
        ) {
            Icon(
                imageVector = Icons.Default.SaveAlt,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "Simpan & Ekspor Hasil Edit",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }
        } // end if (activeTrack != null)

        Spacer(Modifier.height(16.dp))
    }

    if (showExportDialog) {
        val initialName = activeTrack?.title?.replace(" ", "_") ?: "hasil_potong"
        ExportDialog(
            initialFileName = initialName,
            selectedDurationMs = selectedDurationMs,
            volumeGain = volumeGain,
            fadeInMs = fadeInMs,
            fadeOutMs = fadeOutMs,
            equalizerSettings = equalizerSettings,
            onDismiss = { showExportDialog = false },
            onConfirmExport = { options ->
                showExportDialog = false
                viewModel.exportCurrentTrack(options)
            }
        )
    }
}

@Composable
private fun StepButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .height(34.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

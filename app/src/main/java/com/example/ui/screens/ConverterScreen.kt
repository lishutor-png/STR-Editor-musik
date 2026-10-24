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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.example.audio.AudioDecoder
import com.example.audio.AudioFormat
import com.example.audio.AudioViewModel
import com.example.ui.components.TimeFormatUtils

@Composable
fun ConverterScreen(
    viewModel: AudioViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val converterTrack by viewModel.converterTrack.collectAsState()
    val converterFormat by viewModel.converterFormat.collectAsState()
    val converterBitrate by viewModel.converterBitrate.collectAsState()
    val sampleTracks by viewModel.sampleTracks.collectAsState()

    var outputFileName by remember { mutableStateOf("") }

    val audioPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            val track = AudioDecoder.extractMetadata(context, uri = it)
            viewModel.selectTrackForConverter(track)
            outputFileName = "${track.title}_converted"
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Konverter Format Audio",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "Ubah format berkas audio Anda ke MP3, AAC/M4A, WAV Lossless, atau FLAC.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { audioPicker.launch("audio/*") },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("select_convert_file_button")
                    ) {
                        Icon(Icons.Default.FileOpen, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Pilih Audio")
                    }

                    if (converterTrack == null && sampleTracks.isNotEmpty()) {
                        Button(
                            onClick = {
                                val s = sampleTracks.first()
                                viewModel.selectTrackForConverter(s)
                                outputFileName = "${s.title}_converted"
                            }
                        ) {
                            Text("Gunakan Contoh")
                        }
                    }
                }
            }
        }

        if (converterTrack != null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(text = "Audio Terpilih", style = MaterialTheme.typography.labelMedium)
                    Text(text = converterTrack!!.title, style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = "Format Asli: ${converterTrack!!.format.displayName} • Durasi: ${TimeFormatUtils.formatDuration(converterTrack!!.durationMs)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = outputFileName,
                        onValueChange = { outputFileName = it },
                        label = { Text("Nama File Output") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            }

            // Format Selection
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(text = "Pilih Format Tujuan", style = MaterialTheme.typography.titleSmall)
                    Spacer(modifier = Modifier.height(8.dp))

                    Column(modifier = Modifier.selectableGroup()) {
                        AudioFormat.entries.forEach { format ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(44.dp)
                                    .selectable(
                                        selected = converterFormat == format,
                                        onClick = { viewModel.setConverterFormat(format) },
                                        role = Role.RadioButton
                                    ),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(selected = converterFormat == format, onClick = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(text = format.displayName, style = MaterialTheme.typography.bodyMedium)
                                    Text(
                                        text = format.description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    if (converterFormat == AudioFormat.MP3 || converterFormat == AudioFormat.M4A) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(text = "Pilih Kualitas Bitrate", style = MaterialTheme.typography.labelMedium)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectableGroup(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            listOf(128, 192, 256, 320).forEach { br ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.selectable(
                                        selected = converterBitrate == br,
                                        onClick = { viewModel.setConverterBitrate(br) },
                                        role = Role.RadioButton
                                    )
                                ) {
                                    RadioButton(selected = converterBitrate == br, onClick = null)
                                    Text(text = "$br k")
                                }
                            }
                        }
                    }
                }
            }

            Button(
                onClick = {
                    val finalName = outputFileName.ifBlank { "audio_converted" }
                    viewModel.executeConvert(finalName)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .testTag("execute_convert_button")
            ) {
                Icon(Icons.Default.SwapHoriz, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Mulai Konversi Format", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

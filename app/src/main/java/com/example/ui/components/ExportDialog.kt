package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.example.audio.AudioFormat
import com.example.audio.ExportOptions

@Composable
fun ExportDialog(
    initialFileName: String,
    onDismiss: () -> Unit,
    onConfirmExport: (ExportOptions) -> Unit
) {
    var fileName by remember { mutableStateOf(initialFileName) }
    var selectedFormat by remember { mutableStateOf(AudioFormat.MP3) }
    var selectedBitrate by remember { mutableIntStateOf(192) }

    val bitrates = listOf(128, 192, 256, 320)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Ekspor Audio Studio",
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = fileName,
                    onValueChange = { fileName = it },
                    label = { Text("Nama File") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("export_file_name_input")
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Pilih Format Audio",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Column(modifier = Modifier.selectableGroup()) {
                    AudioFormat.entries.forEach { format ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp)
                                .selectable(
                                    selected = selectedFormat == format,
                                    onClick = { selectedFormat = format },
                                    role = Role.RadioButton
                                ),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedFormat == format,
                                onClick = null
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = format.displayName,
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                Text(
                                    text = format.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                if (selectedFormat == AudioFormat.MP3 || selectedFormat == AudioFormat.M4A) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Kualitas Bitrate",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectableGroup(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        bitrates.forEach { br ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.selectable(
                                    selected = selectedBitrate == br,
                                    onClick = { selectedBitrate = br },
                                    role = Role.RadioButton
                                )
                            ) {
                                RadioButton(
                                    selected = selectedBitrate == br,
                                    onClick = null
                                )
                                Text(text = "$br k")
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val finalName = fileName.ifBlank { "Lish_Export" }
                    onConfirmExport(
                        ExportOptions(
                            fileName = finalName,
                            format = selectedFormat,
                            bitrateKbps = selectedBitrate
                        )
                    )
                },
                modifier = Modifier.testTag("confirm_export_button")
            ) {
                Text("Ekspor Sekarang")
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("cancel_export_button")
            ) {
                Text("Batal")
            }
        }
    )
}

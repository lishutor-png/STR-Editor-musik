package com.example.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.audio.EqualizerPreset
import com.example.audio.EqualizerSettings
import java.util.Locale

@Composable
fun EqualizerSection(
    settings: EqualizerSettings,
    onPresetSelect: (EqualizerPreset) -> Unit,
    onBassChange: (Float) -> Unit,
    onMidChange: (Float) -> Unit,
    onTrebleChange: (Float) -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("equalizer_section"),
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
                Text(
                    text = "Equalizer Studio (3-Band EQ)",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                IconButton(
                    onClick = onReset,
                    modifier = Modifier.testTag("eq_reset_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.RestartAlt,
                        contentDescription = "Reset EQ",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Preset Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                EqualizerPreset.PRESETS.forEach { preset ->
                    val isSelected = settings.presetName == preset.name
                    FilterChip(
                        selected = isSelected,
                        onClick = { onPresetSelect(preset) },
                        label = { Text(preset.name) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ),
                        modifier = Modifier.testTag("preset_${preset.name.lowercase().replace(" ", "_")}")
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Bass Slider
            BandSlider(
                label = "Bass (Rendah)",
                value = settings.bassGain,
                onValueChange = onBassChange,
                testTagPrefix = "bass"
            )

            // Mid Slider
            BandSlider(
                label = "Vokal / Mid (Tengah)",
                value = settings.midGain,
                onValueChange = onMidChange,
                testTagPrefix = "mid"
            )

            // Treble Slider
            BandSlider(
                label = "Treble (Tinggi / Kejernihan)",
                value = settings.trebleGain,
                onValueChange = onTrebleChange,
                testTagPrefix = "treble"
            )
        }
    }
}

@Composable
private fun BandSlider(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    testTagPrefix: String
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = String.format(Locale.getDefault(), "%+.1f dB", value),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = -10f..10f,
            steps = 20,
            modifier = Modifier.testTag("${testTagPrefix}_slider")
        )
    }
}

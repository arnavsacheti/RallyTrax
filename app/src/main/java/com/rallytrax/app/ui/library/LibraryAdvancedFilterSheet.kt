package com.rallytrax.app.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.rallytrax.app.data.preferences.UnitSystem
import com.rallytrax.app.util.formatDistance
import com.rallytrax.app.util.formatElapsedTime
import com.rallytrax.app.util.formatElevation

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryAdvancedFilterSheet(
    distanceRange: ClosedFloatingPointRange<Float>?,
    elevationRange: ClosedFloatingPointRange<Float>?,
    durationRange: ClosedFloatingPointRange<Float>?,
    maxDistance: Float,
    maxElevation: Float,
    maxDuration: Float,
    unitSystem: UnitSystem,
    onDistanceRangeChange: (ClosedFloatingPointRange<Float>?) -> Unit,
    onElevationRangeChange: (ClosedFloatingPointRange<Float>?) -> Unit,
    onDurationRangeChange: (ClosedFloatingPointRange<Float>?) -> Unit,
    onDismiss: () -> Unit,
    onReset: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Local state for sliders during drag
    var localDistance by remember(distanceRange, maxDistance) {
        mutableStateOf(distanceRange ?: 0f..maxDistance)
    }
    var localElevation by remember(elevationRange, maxElevation) {
        mutableStateOf(elevationRange ?: 0f..maxElevation)
    }
    var localDuration by remember(durationRange, maxDuration) {
        mutableStateOf(durationRange ?: 0f..maxDuration)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "Filters",
                    style = MaterialTheme.typography.headlineSmall,
                )
                TextButton(onClick = {
                    onReset()
                    localDistance = 0f..maxDistance
                    localElevation = 0f..maxElevation
                    localDuration = 0f..maxDuration
                }) {
                    Text("Reset")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Distance slider
            if (maxDistance > 0f) {
                FilterRangeSection(
                    title = "Distance",
                    value = localDistance,
                    valueRange = 0f..maxDistance,
                    onValueChange = { localDistance = it },
                    formatLabel = { formatDistance(it.toDouble(), unitSystem) },
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Elevation slider
            if (maxElevation > 0f) {
                FilterRangeSection(
                    title = "Elevation Gain",
                    value = localElevation,
                    valueRange = 0f..maxElevation,
                    onValueChange = { localElevation = it },
                    formatLabel = { formatElevation(it.toDouble(), unitSystem) },
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Duration slider
            if (maxDuration > 0f) {
                FilterRangeSection(
                    title = "Duration",
                    value = localDuration,
                    valueRange = 0f..maxDuration,
                    onValueChange = { localDuration = it },
                    formatLabel = { formatElapsedTime(it.toLong()) },
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            if (maxDistance == 0f && maxElevation == 0f && maxDuration == 0f) {
                Text(
                    text = "No filter data available",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Apply button
            FilledTonalButton(
                onClick = {
                    // Only set range if it differs from the full range
                    val distRange = if (localDistance.start > 0f || localDistance.endInclusive < maxDistance) localDistance else null
                    val eleRange = if (localElevation.start > 0f || localElevation.endInclusive < maxElevation) localElevation else null
                    val durRange = if (localDuration.start > 0f || localDuration.endInclusive < maxDuration) localDuration else null
                    onDistanceRangeChange(distRange)
                    onElevationRangeChange(eleRange)
                    onDurationRangeChange(durRange)
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Apply Filters")
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun FilterRangeSection(
    title: String,
    value: ClosedFloatingPointRange<Float>,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (ClosedFloatingPointRange<Float>) -> Unit,
    formatLabel: (Float) -> String,
) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
    )
    Spacer(modifier = Modifier.height(4.dp))
    RangeSlider(
        value = value,
        onValueChange = onValueChange,
        valueRange = valueRange,
        modifier = Modifier.fillMaxWidth(),
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = formatLabel(value.start),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = formatLabel(value.endInclusive),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

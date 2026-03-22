package com.rallytrax.app.ui.maintenance

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rallytrax.app.data.local.entity.MaintenanceScheduleEntity
import com.rallytrax.app.data.maintenance.ServiceCategories

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddScheduleSheet(
    vehicleId: String,
    onSave: (MaintenanceScheduleEntity) -> Unit,
    onDismiss: () -> Unit,
) {
    var serviceType by remember { mutableStateOf("") }
    var intervalKmInput by remember { mutableStateOf("") }
    var intervalMonthsInput by remember { mutableStateOf("") }
    var notifyDaysInput by remember { mutableStateOf("30") }

    // Auto-fill from presets when service type changes
    LaunchedEffect(serviceType) {
        val preset = ServiceCategories.getPresetInterval(serviceType)
        if (preset != null) {
            intervalKmInput = preset.intervalKm?.toInt()?.toString() ?: ""
            intervalMonthsInput = preset.intervalMonths?.toString() ?: ""
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Schedule, null, modifier = Modifier.size(28.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Text("Add Schedule", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(16.dp))

            // Service type (free text or pick from presets)
            OutlinedTextField(
                value = serviceType, onValueChange = { serviceType = it },
                label = { Text("Service type") },
                placeholder = { Text("e.g., Oil change") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(4.dp))

            // Preset suggestions
            if (serviceType.isBlank()) {
                Text(
                    text = "Common: ${ServiceCategories.presetIntervals.joinToString(", ") { it.serviceType }}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(modifier = Modifier.height(8.dp))

            // Interval km
            OutlinedTextField(
                value = intervalKmInput, onValueChange = { intervalKmInput = it },
                label = { Text("Interval (km)") },
                placeholder = { Text("e.g., 8000") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(8.dp))

            // Interval months
            OutlinedTextField(
                value = intervalMonthsInput, onValueChange = { intervalMonthsInput = it },
                label = { Text("Interval (months)") },
                placeholder = { Text("e.g., 6") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Whichever trigger comes first",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(8.dp))

            // Notification lead days
            OutlinedTextField(
                value = notifyDaysInput, onValueChange = { notifyDaysInput = it },
                label = { Text("Notify days before") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(16.dp))

            // Save
            FilledTonalButton(
                onClick = {
                    val now = System.currentTimeMillis()
                    val intervalKm = intervalKmInput.toDoubleOrNull()
                    val intervalMonths = intervalMonthsInput.toIntOrNull()
                    val notifyDays = notifyDaysInput.toIntOrNull() ?: 30

                    // Compute next due date from now + interval
                    val nextDueDate = if (intervalMonths != null) {
                        now + intervalMonths.toLong() * 30L * 24 * 60 * 60 * 1000
                    } else null

                    val schedule = MaintenanceScheduleEntity(
                        vehicleId = vehicleId,
                        serviceType = serviceType,
                        intervalKm = intervalKm,
                        intervalMonths = intervalMonths,
                        lastServiceDate = now,
                        nextDueDate = nextDueDate,
                        nextDueOdometerKm = null, // Will be computed when odometer is known
                        notifyDaysBefore = notifyDays,
                    )
                    onSave(schedule)
                    onDismiss()
                },
                enabled = serviceType.isNotBlank() && (intervalKmInput.isNotBlank() || intervalMonthsInput.isNotBlank()),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.Check, null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Save Schedule")
            }
        }
    }
}

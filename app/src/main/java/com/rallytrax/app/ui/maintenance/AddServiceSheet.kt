package com.rallytrax.app.ui.maintenance

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rallytrax.app.data.local.entity.MaintenanceRecordEntity
import com.rallytrax.app.data.maintenance.ServiceCategories

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddServiceSheet(
    vehicleId: String,
    onSave: (MaintenanceRecordEntity) -> Unit,
    onDismiss: () -> Unit,
    vehicleType: String? = null,
) {
    var category by remember { mutableStateOf("") }
    var serviceType by remember { mutableStateOf("") }
    var odometerInput by remember { mutableStateOf("") }
    var costPartsInput by remember { mutableStateOf("") }
    var costLaborInput by remember { mutableStateOf("") }
    var provider by remember { mutableStateOf("") }
    var isDiy by remember { mutableStateOf(false) }
    var notes by remember { mutableStateOf("") }

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
                Icon(Icons.Filled.Build, null, modifier = Modifier.size(28.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Text("Add Service", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(16.dp))

            // Category dropdown
            var catExpanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(expanded = catExpanded, onExpandedChange = { catExpanded = it }) {
                OutlinedTextField(
                    value = category, onValueChange = {}, readOnly = true,
                    label = { Text("Category") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(catExpanded) },
                    modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                )
                ExposedDropdownMenu(expanded = catExpanded, onDismissRequest = { catExpanded = false }) {
                    ServiceCategories.categoriesForVehicleType(vehicleType).keys.forEach { cat ->
                        DropdownMenuItem(text = { Text(cat) }, onClick = {
                            category = cat; serviceType = ""; catExpanded = false
                        })
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))

            // Service type dropdown
            if (category.isNotBlank()) {
                var typeExpanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(expanded = typeExpanded, onExpandedChange = { typeExpanded = it }) {
                    OutlinedTextField(
                        value = serviceType, onValueChange = {}, readOnly = true,
                        label = { Text("Service Type") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(typeExpanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                    )
                    ExposedDropdownMenu(expanded = typeExpanded, onDismissRequest = { typeExpanded = false }) {
                        (ServiceCategories.categoriesForVehicleType(vehicleType)[category] ?: emptyList()).forEach { svc ->
                            DropdownMenuItem(text = { Text(svc) }, onClick = {
                                serviceType = svc; typeExpanded = false
                            })
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Odometer
            OutlinedTextField(
                value = odometerInput, onValueChange = { odometerInput = it },
                label = { Text("Odometer (km)") }, singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(8.dp))

            // Cost
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = costPartsInput, onValueChange = { costPartsInput = it },
                    label = { Text("Parts cost") }, singleLine = true, modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = costLaborInput, onValueChange = { costLaborInput = it },
                    label = { Text("Labor cost") }, singleLine = true, modifier = Modifier.weight(1f),
                )
            }
            Spacer(modifier = Modifier.height(8.dp))

            // Provider
            OutlinedTextField(
                value = provider, onValueChange = { provider = it },
                label = { Text("Provider / Shop") }, singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(8.dp))

            // DIY toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("DIY (did it yourself)?", style = MaterialTheme.typography.bodyMedium)
                Switch(checked = isDiy, onCheckedChange = { isDiy = it })
            }
            Spacer(modifier = Modifier.height(8.dp))

            // Notes
            OutlinedTextField(
                value = notes, onValueChange = { notes = it },
                label = { Text("Notes") }, modifier = Modifier.fillMaxWidth(), minLines = 2,
            )
            Spacer(modifier = Modifier.height(16.dp))

            // Save
            FilledTonalButton(
                onClick = {
                    val parts = costPartsInput.toDoubleOrNull()
                    val labor = costLaborInput.toDoubleOrNull()
                    val total = (parts ?: 0.0) + (labor ?: 0.0)
                    val record = MaintenanceRecordEntity(
                        vehicleId = vehicleId,
                        category = category,
                        serviceType = serviceType,
                        odometerKm = odometerInput.toDoubleOrNull(),
                        costParts = parts,
                        costLabor = labor,
                        costTotal = total,
                        provider = provider.takeIf { it.isNotBlank() },
                        isDiy = isDiy,
                        notes = notes.takeIf { it.isNotBlank() },
                    )
                    onSave(record)
                    onDismiss()
                },
                enabled = category.isNotBlank() && serviceType.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.Check, null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Save Service Record")
            }
        }
    }
}

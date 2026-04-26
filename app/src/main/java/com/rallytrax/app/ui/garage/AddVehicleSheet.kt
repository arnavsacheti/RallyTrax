package com.rallytrax.app.ui.garage

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.FilterChip
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rallytrax.app.data.local.entity.Ownership

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddVehicleSheet(
    onDismiss: () -> Unit,
    onVehicleSaved: () -> Unit,
    viewModel: AddVehicleViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var selectedMode by remember { mutableIntStateOf(0) } // 0 = Year/Make/Model, 1 = VIN
    var showVinScanner by remember { mutableStateOf(false) }

    // Reset state when sheet opens so multiple vehicles can be added in a row
    LaunchedEffect(Unit) {
        viewModel.reset()
    }

    // Auto-dismiss after save
    LaunchedEffect(uiState.saved) {
        if (uiState.saved) onVehicleSaved()
    }

    if (showVinScanner) {
        VinScannerScreen(
            onVinScanned = { vin ->
                showVinScanner = false
                viewModel.onVinScanned(vin)
            },
            onBack = { showVinScanner = false },
        )
        return
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
        ) {
            Text(
                text = "Add Vehicle",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 16.dp),
            )

            // Ownership selector — Borrowed / Rental skip the NHTSA pipeline
            // and use a slim form, since users adding a friend's car or a
            // rental don't need EPA MPG or VIN decoding for a one-off trip.
            Text("Ownership", style = MaterialTheme.typography.labelLarge)
            Spacer(modifier = Modifier.height(4.dp))
            val ownerships = listOf(Ownership.OWNED, Ownership.BORROWED, Ownership.RENTED)
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                ownerships.forEachIndexed { index, ownership ->
                    SegmentedButton(
                        selected = uiState.ownership == ownership,
                        onClick = { viewModel.updateOwnership(ownership) },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = ownerships.size),
                    ) {
                        Text(
                            ownership.name.lowercase().replaceFirstChar { it.uppercase() },
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Vehicle type selector
            Text("Vehicle Type", style = MaterialTheme.typography.labelLarge)
            Spacer(modifier = Modifier.height(4.dp))
            val vehicleTypes = listOf("CAR", "MOTORCYCLE", "TRUCK", "SUV", "OTHER")
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                vehicleTypes.forEachIndexed { index, type ->
                    SegmentedButton(
                        selected = uiState.vehicleType == type,
                        onClick = { viewModel.updateVehicleType(type) },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = vehicleTypes.size),
                        icon = { Icon(vehicleTypeIcon(type), null, Modifier.size(18.dp)) },
                    ) { Text(type.lowercase().replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.labelSmall) }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (uiState.ownership != Ownership.OWNED) {
                LoanerSlimForm(viewModel, uiState)
            } else {
                // Mode selector
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = selectedMode == 0,
                        onClick = { selectedMode = 0 },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                        icon = { Icon(Icons.Filled.Search, null, Modifier.size(18.dp)) },
                    ) { Text("Year/Make/Model") }
                    SegmentedButton(
                        selected = selectedMode == 1,
                        onClick = { selectedMode = 1 },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                        icon = { Icon(Icons.Filled.CameraAlt, null, Modifier.size(18.dp)) },
                    ) { Text("Scan or enter VIN") }
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (selectedMode == 0) {
                    YearMakeModelPicker(viewModel, uiState)
                } else {
                    VinEntrySection(viewModel, uiState, onScanClick = { showVinScanner = true })
                }
            }

            // Vehicle name field (visible once model is selected). The slim
            // loaner form already has its own name field, so skip when not OWNED.
            if (uiState.ownership == Ownership.OWNED && uiState.selectedModel != null) {
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = uiState.name,
                    onValueChange = { viewModel.updateName(it) },
                    label = { Text("Vehicle nickname") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            // Fuel type selector
            if (uiState.ownership == Ownership.OWNED && uiState.selectedModel != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Text("Fuel Type", style = MaterialTheme.typography.labelLarge)
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    val fuelTypes = listOf("Gasoline", "Diesel", "Electric", "Hybrid", "E85")
                    fuelTypes.forEach { type ->
                        FilterChip(
                            selected = uiState.fuelType == type,
                            onClick = { viewModel.updateFuelType(type) },
                            label = { Text(type, style = MaterialTheme.typography.labelSmall) },
                        )
                    }
                }
            }

            // EPA trim selector (if multiple trims available)
            if (uiState.ownership == Ownership.OWNED &&
                uiState.trims.size > 1 &&
                uiState.selectedTrim == null
            ) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Select trim for EPA data:",
                    style = MaterialTheme.typography.labelLarge,
                )
                Spacer(modifier = Modifier.height(4.dp))
                LazyColumn(
                    modifier = Modifier.heightIn(max = 200.dp),
                ) {
                    items(uiState.trims) { trim ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.selectTrim(trim) }
                                .padding(vertical = 8.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = trim.displayName,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        HorizontalDivider()
                    }
                }
            }

            // EPA info display
            if (uiState.ownership == Ownership.OWNED && uiState.epaCombinedMpg != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    uiState.epaCityMpg?.let {
                        Text("City: ${it.toInt()} MPG", style = MaterialTheme.typography.bodySmall)
                    }
                    uiState.epaHwyMpg?.let {
                        Text("Hwy: ${it.toInt()} MPG", style = MaterialTheme.typography.bodySmall)
                    }
                    uiState.epaCombinedMpg?.let {
                        Text(
                            "Combined: ${it.toInt()} MPG",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Save button
            FilledTonalButton(
                onClick = { viewModel.saveVehicle() },
                enabled = viewModel.canSave(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (uiState.isSaving) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Icon(Icons.Filled.Check, null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Save Vehicle")
            }

            uiState.saveError?.let { error ->
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun YearMakeModelPicker(viewModel: AddVehicleViewModel, state: AddVehicleUiState) {
    // Year dropdown
    var yearExpanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = yearExpanded, onExpandedChange = { yearExpanded = it }) {
        OutlinedTextField(
            value = state.selectedYear?.toString() ?: "",
            onValueChange = {},
            readOnly = true,
            label = { Text("Year") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = yearExpanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = yearExpanded, onDismissRequest = { yearExpanded = false }) {
            (viewModel.currentYear downTo 1981).forEach { year ->
                DropdownMenuItem(
                    text = { Text(year.toString()) },
                    onClick = {
                        viewModel.selectYear(year)
                        yearExpanded = false
                    },
                )
            }
        }
    }

    Spacer(modifier = Modifier.height(8.dp))

    // Make dropdown
    if (state.selectedYear != null) {
        var makeExpanded by remember { mutableStateOf(false) }
        ExposedDropdownMenuBox(expanded = makeExpanded, onExpandedChange = { if (state.makes.isNotEmpty()) makeExpanded = it }) {
            OutlinedTextField(
                value = state.selectedMake?.makeName ?: "",
                onValueChange = {},
                readOnly = true,
                label = { Text("Make") },
                trailingIcon = {
                    if (state.isLoadingMakes) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = makeExpanded)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
            )
            ExposedDropdownMenu(expanded = makeExpanded, onDismissRequest = { makeExpanded = false }) {
                state.makes.forEach { make ->
                    DropdownMenuItem(
                        text = { Text(make.makeName) },
                        onClick = {
                            viewModel.selectMake(make)
                            makeExpanded = false
                        },
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
    }

    // Model dropdown
    if (state.selectedMake != null) {
        var modelExpanded by remember { mutableStateOf(false) }
        ExposedDropdownMenuBox(expanded = modelExpanded, onExpandedChange = { if (state.models.isNotEmpty()) modelExpanded = it }) {
            OutlinedTextField(
                value = state.selectedModel?.modelName ?: "",
                onValueChange = {},
                readOnly = true,
                label = { Text("Model") },
                trailingIcon = {
                    if (state.isLoadingModels) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = modelExpanded)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
            )
            ExposedDropdownMenu(expanded = modelExpanded, onDismissRequest = { modelExpanded = false }) {
                state.models.forEach { model ->
                    DropdownMenuItem(
                        text = { Text(model.modelName) },
                        onClick = {
                            viewModel.selectModel(model)
                            modelExpanded = false
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun LoanerSlimForm(viewModel: AddVehicleViewModel, state: AddVehicleUiState) {
    // Single nickname is enough to find the entry later in Garage. Year /
    // make / model are optional free-text — entered once at the rental
    // counter, never updated, never aggregated.
    OutlinedTextField(
        value = state.name,
        onValueChange = { viewModel.updateName(it) },
        label = { Text("Nickname") },
        placeholder = { Text("e.g. Mom's Subaru, Hertz Camry") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(modifier = Modifier.height(8.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            value = state.slimYear,
            onValueChange = { viewModel.updateSlimYear(it) },
            label = { Text("Year") },
            singleLine = true,
            modifier = Modifier.width(96.dp),
        )
        OutlinedTextField(
            value = state.slimMake,
            onValueChange = { viewModel.updateSlimMake(it) },
            label = { Text("Make") },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
    }
    Spacer(modifier = Modifier.height(8.dp))
    OutlinedTextField(
        value = state.slimModel,
        onValueChange = { viewModel.updateSlimModel(it) },
        label = { Text("Model") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun VinEntrySection(
    viewModel: AddVehicleViewModel,
    state: AddVehicleUiState,
    onScanClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = state.vinInput,
            onValueChange = { viewModel.updateVinInput(it) },
            label = { Text("Enter VIN") },
            placeholder = { Text("17-character VIN") },
            singleLine = true,
            modifier = Modifier.weight(1f),
            isError = state.vinError != null,
            supportingText = state.vinError?.let { { Text(it) } },
        )
        Spacer(modifier = Modifier.width(8.dp))
        IconButton(onClick = onScanClick) {
            Icon(Icons.Filled.CameraAlt, contentDescription = "Scan VIN barcode")
        }
    }

    Spacer(modifier = Modifier.height(8.dp))

    OutlinedButton(
        onClick = { viewModel.decodeVin() },
        enabled = state.vinInput.length == 17 && !state.isDecodingVin,
        modifier = Modifier.fillMaxWidth(),
    ) {
        if (state.isDecodingVin) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text("Decode VIN")
    }

    // Show decoded specs summary
    if (state.selectedMake != null && state.selectedModel != null && state.selectedYear != null) {
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Decoded: ${state.selectedYear} ${state.selectedMake?.makeName} ${state.selectedModel?.modelName}",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

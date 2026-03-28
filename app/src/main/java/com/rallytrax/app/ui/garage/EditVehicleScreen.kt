package com.rallytrax.app.ui.garage

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EditVehicleScreen(
    onBack: () -> Unit = {},
    viewModel: VehicleDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val vehicle = uiState.vehicle

    var name by remember(vehicle?.id) { mutableStateOf(vehicle?.name ?: "") }
    var year by remember(vehicle?.id) { mutableStateOf(vehicle?.year?.toString() ?: "") }
    var make by remember(vehicle?.id) { mutableStateOf(vehicle?.make ?: "") }
    var model by remember(vehicle?.id) { mutableStateOf(vehicle?.model ?: "") }
    var trim by remember(vehicle?.id) { mutableStateOf(vehicle?.trim ?: "") }
    var engineDisplacementL by remember(vehicle?.id) { mutableStateOf(vehicle?.engineDisplacementL?.toString() ?: "") }
    var cylinders by remember(vehicle?.id) { mutableStateOf(vehicle?.cylinders?.toString() ?: "") }
    var horsePower by remember(vehicle?.id) { mutableStateOf(vehicle?.horsePower?.toString() ?: "") }
    var drivetrain by remember(vehicle?.id) { mutableStateOf(vehicle?.drivetrain ?: "") }
    var transmissionType by remember(vehicle?.id) { mutableStateOf(vehicle?.transmissionType ?: "") }
    var transmissionSpeeds by remember(vehicle?.id) { mutableStateOf(vehicle?.transmissionSpeeds?.toString() ?: "") }
    var fuelType by remember(vehicle?.id) { mutableStateOf(vehicle?.fuelType ?: "Gasoline") }
    var tankSizeGal by remember(vehicle?.id) { mutableStateOf(vehicle?.tankSizeGal?.toString() ?: "") }
    var epaCityMpg by remember(vehicle?.id) { mutableStateOf(vehicle?.epaCityMpg?.toString() ?: "") }
    var epaHwyMpg by remember(vehicle?.id) { mutableStateOf(vehicle?.epaHwyMpg?.toString() ?: "") }
    var epaCombinedMpg by remember(vehicle?.id) { mutableStateOf(vehicle?.epaCombinedMpg?.toString() ?: "") }
    var curbWeightKg by remember(vehicle?.id) { mutableStateOf(vehicle?.curbWeightKg?.toString() ?: "") }
    var tireSize by remember(vehicle?.id) { mutableStateOf(vehicle?.tireSize ?: "") }
    var vin by remember(vehicle?.id) { mutableStateOf(vehicle?.vin ?: "") }
    var odometerKm by remember(vehicle?.id) { mutableStateOf(vehicle?.odometerKm?.toString() ?: "0.0") }
    var modsList by remember(vehicle?.id) { mutableStateOf(vehicle?.modsList ?: "") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Edit Vehicle") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            vehicle?.let { v ->
                                viewModel.updateVehicle(
                                    v.copy(
                                        name = name,
                                        year = year.toIntOrNull() ?: v.year,
                                        make = make,
                                        model = model,
                                        trim = trim.ifBlank { null },
                                        engineDisplacementL = engineDisplacementL.toDoubleOrNull(),
                                        cylinders = cylinders.toIntOrNull(),
                                        horsePower = horsePower.toIntOrNull(),
                                        drivetrain = drivetrain.ifBlank { null },
                                        transmissionType = transmissionType.ifBlank { null },
                                        transmissionSpeeds = transmissionSpeeds.toIntOrNull(),
                                        fuelType = fuelType,
                                        tankSizeGal = tankSizeGal.toDoubleOrNull(),
                                        epaCityMpg = epaCityMpg.toDoubleOrNull(),
                                        epaHwyMpg = epaHwyMpg.toDoubleOrNull(),
                                        epaCombinedMpg = epaCombinedMpg.toDoubleOrNull(),
                                        curbWeightKg = curbWeightKg.toDoubleOrNull(),
                                        tireSize = tireSize.ifBlank { null },
                                        vin = vin.ifBlank { null },
                                        odometerKm = odometerKm.toDoubleOrNull() ?: v.odometerKm,
                                        modsList = modsList.ifBlank { null },
                                        updatedAt = System.currentTimeMillis(),
                                    ),
                                )
                                onBack()
                            }
                        },
                        enabled = vehicle != null && name.isNotBlank() && make.isNotBlank() && model.isNotBlank(),
                    ) {
                        Icon(Icons.Filled.Check, contentDescription = "Save")
                    }
                },
            )
        },
    ) { innerPadding ->
        if (uiState.isLoading || vehicle == null) {
            Box(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SectionCard("Basic Info") {
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text("Name") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = year, onValueChange = { year = it },
                    label = { Text("Year") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = make, onValueChange = { make = it },
                    label = { Text("Make") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = model, onValueChange = { model = it },
                    label = { Text("Model") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = trim, onValueChange = { trim = it },
                    label = { Text("Trim") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            SectionCard("Engine & Drivetrain") {
                OutlinedTextField(
                    value = engineDisplacementL, onValueChange = { engineDisplacementL = it },
                    label = { Text("Engine Displacement (L)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = cylinders, onValueChange = { cylinders = it },
                    label = { Text("Cylinders") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = horsePower, onValueChange = { horsePower = it },
                    label = { Text("Horsepower") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(8.dp))

                DropdownField(
                    value = drivetrain,
                    onValueChange = { drivetrain = it },
                    label = "Drivetrain",
                    options = listOf("AWD", "FWD", "RWD", "4WD"),
                )
                Spacer(modifier = Modifier.height(8.dp))
                DropdownField(
                    value = transmissionType,
                    onValueChange = { transmissionType = it },
                    label = "Transmission",
                    options = listOf("Automatic", "Manual", "CVT", "DCT"),
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = transmissionSpeeds, onValueChange = { transmissionSpeeds = it },
                    label = { Text("Transmission Speeds") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            SectionCard("Fuel & Economy") {
                Text("Fuel Type", style = MaterialTheme.typography.labelMedium)
                Spacer(modifier = Modifier.height(4.dp))
                val fuelTypes = listOf("Gasoline", "Diesel", "Electric", "Hybrid", "E85")
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    fuelTypes.forEach { type ->
                        FilterChip(
                            selected = fuelType == type,
                            onClick = { fuelType = type },
                            label = { Text(type) },
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = tankSizeGal, onValueChange = { tankSizeGal = it },
                    label = { Text("Tank Size (gal)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = epaCityMpg, onValueChange = { epaCityMpg = it },
                        label = { Text("EPA City") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = epaHwyMpg, onValueChange = { epaHwyMpg = it },
                        label = { Text("EPA Hwy") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = epaCombinedMpg, onValueChange = { epaCombinedMpg = it },
                    label = { Text("EPA Combined (mpg)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            SectionCard("Weight & Tires") {
                OutlinedTextField(
                    value = curbWeightKg, onValueChange = { curbWeightKg = it },
                    label = { Text("Curb Weight (kg)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = tireSize, onValueChange = { tireSize = it },
                    label = { Text("Tire Size") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            SectionCard("Identification") {
                OutlinedTextField(
                    value = vin, onValueChange = { if (it.length <= 17) vin = it },
                    label = { Text("VIN") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = odometerKm, onValueChange = { odometerKm = it },
                    label = { Text("Odometer (km)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            SectionCard("Modifications") {
                OutlinedTextField(
                    value = modsList, onValueChange = { modsList = it },
                    label = { Text("Modifications") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SectionCard(
    title: String,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(8.dp))
            content()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DropdownField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    options: List<String>,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = { onValueChange(option); expanded = false },
                )
            }
        }
    }
}

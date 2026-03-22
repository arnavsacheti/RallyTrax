package com.rallytrax.app.ui.garage

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rallytrax.app.data.local.entity.VehicleEntity
import com.rallytrax.app.data.preferences.UnitSystem
import com.rallytrax.app.util.formatDistance
import com.rallytrax.app.util.formatDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VehicleDetailScreen(
    onBack: () -> Unit = {},
    onTrackClick: (String) -> Unit = {},
    viewModel: VehicleDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val tracks by viewModel.tracks.collectAsStateWithLifecycle()
    val vehicle = uiState.vehicle

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(vehicle?.name ?: "Vehicle") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    if (vehicle != null) {
                        IconButton(onClick = { viewModel.setActive() }) {
                            Icon(
                                imageVector = if (vehicle.isActive) Icons.Filled.Star else Icons.Outlined.StarOutline,
                                contentDescription = if (vehicle.isActive) "Active vehicle" else "Set as active",
                                tint = if (vehicle.isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        if (uiState.isLoading) {
            Box(Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (vehicle == null) {
            Box(Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                Text("Vehicle not found")
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
            ) {
                // Vehicle hero
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                    ),
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.DirectionsCar,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "${vehicle.year} ${vehicle.make} ${vehicle.model}",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                        vehicle.trim?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Specs card — 2-column grid
                Text(
                    text = "Specifications",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.height(8.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    ),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        SpecsGrid(vehicle, uiState.totalDistanceM)
                    }
                }

                // Mods section
                vehicle.modsList?.takeIf { it.isNotBlank() }?.let { mods ->
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Modifications",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = mods,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                // Linked tracks
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Linked Tracks (${tracks.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.height(8.dp))

                if (tracks.isEmpty()) {
                    Text(
                        text = "No tracks linked to this vehicle yet",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer,
                        ),
                    ) {
                        tracks.forEachIndexed { index, track ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onTrackClick(track.id) }
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = track.name,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                        Text(
                                            text = formatDistance(track.distanceMeters, UnitSystem.METRIC),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        Text(
                                            text = formatDate(track.recordedAt),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                                Icon(
                                    imageVector = Icons.Filled.ChevronRight,
                                    contentDescription = "View",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (index < tracks.lastIndex) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                    color = MaterialTheme.colorScheme.outlineVariant,
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(80.dp))
            }
        }
    }
}

@Composable
private fun SpecsGrid(vehicle: VehicleEntity, totalDistanceM: Double) {
    val specs = buildList {
        add("Year" to vehicle.year.toString())
        add("Make" to vehicle.make)
        add("Model" to vehicle.model)
        vehicle.trim?.let { add("Trim" to it) }
        vehicle.engineDisplacementL?.let { add("Engine" to "${it}L") }
        vehicle.cylinders?.let { add("Cylinders" to it.toString()) }
        vehicle.horsePower?.let { add("Horsepower" to "${it} hp") }
        vehicle.drivetrain?.let { add("Drivetrain" to it) }
        vehicle.transmissionType?.let { add("Transmission" to it) }
        vehicle.transmissionSpeeds?.let { add("Gears" to it.toString()) }
        add("Fuel Type" to vehicle.fuelType)
        vehicle.tankSizeGal?.let { add("Tank" to "${it} gal") }
        vehicle.epaCityMpg?.let { add("EPA City" to "${it.toInt()} mpg") }
        vehicle.epaHwyMpg?.let { add("EPA Hwy" to "${it.toInt()} mpg") }
        vehicle.epaCombinedMpg?.let { add("EPA Combined" to "${it.toInt()} mpg") }
        vehicle.curbWeightKg?.let { add("Weight" to "${it.toInt()} kg") }
        vehicle.tireSize?.let { add("Tires" to it) }
        add("Odometer" to formatDistance(vehicle.odometerKm * 1000, UnitSystem.METRIC))
        if (totalDistanceM > 0) {
            add("RallyTrax Distance" to formatDistance(totalDistanceM, UnitSystem.METRIC))
        }
        vehicle.vin?.let { add("VIN" to it) }
    }

    // Render as 2-column grid
    specs.chunked(2).forEach { row ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
        ) {
            row.forEach { (label, value) ->
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = value,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            // Fill empty space if odd number of specs in the row
            if (row.size == 1) {
                Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}

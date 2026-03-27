package com.rallytrax.app.ui.recording

import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rallytrax.app.data.classification.RouteClassifier
import com.rallytrax.app.ui.components.AchievementPopup
import com.rallytrax.app.util.formatDistance
import com.rallytrax.app.util.formatElapsedTime
import com.rallytrax.app.util.formatSpeed
import com.rallytrax.app.util.speedUnit

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ActivitySummaryScreen(
    onNavigateToDetail: (String) -> Unit,
    onNavigateBack: () -> Unit,
    viewModel: ActivitySummaryViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val preferences by viewModel.preferences.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.navigateToDetail.collect { trackId ->
            onNavigateToDetail(trackId)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.navigateBack.collect {
            onNavigateBack()
        }
    }

    val track = state.track

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold { innerPadding ->
            if (track == null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("Loading...")
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                ) {
                    // Header
                    Text(
                        text = "Activity Complete",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    // Hero Stats Grid
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        HeroStatCard(
                            icon = Icons.Filled.Straighten,
                            label = "Distance",
                            value = formatDistance(track.distanceMeters, preferences.unitSystem),
                            modifier = Modifier.weight(1f),
                        )
                        HeroStatCard(
                            icon = Icons.Filled.Timer,
                            label = "Time",
                            value = formatElapsedTime(track.durationMs),
                            modifier = Modifier.weight(1f),
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        HeroStatCard(
                            icon = Icons.Filled.Speed,
                            label = "Avg Speed",
                            value = "${formatSpeed(track.avgSpeedMps, preferences.unitSystem)} ${speedUnit(preferences.unitSystem)}",
                            modifier = Modifier.weight(1f),
                        )
                        HeroStatCard(
                            icon = Icons.Filled.Speed,
                            label = "Max Speed",
                            value = "${formatSpeed(track.maxSpeedMps, preferences.unitSystem)} ${speedUnit(preferences.unitSystem)}",
                            modifier = Modifier.weight(1f),
                        )
                    }

                    if (track.elevationGainM > 0) {
                        Spacer(modifier = Modifier.height(12.dp))
                        HeroStatCard(
                            icon = Icons.Filled.Route,
                            label = "Elevation Gain",
                            value = "${track.elevationGainM.toInt()} m",
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    // Sensor stats (lateral G, vertical G, yaw rate)
                    if (state.sensorStats.hasSensorData) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            state.sensorStats.peakLateralG?.let { g ->
                                HeroStatCard(
                                    icon = Icons.Filled.Speed,
                                    label = "Peak Lateral G",
                                    value = "${"%.2f".format(g)}g",
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            state.sensorStats.peakVerticalG?.let { g ->
                                HeroStatCard(
                                    icon = Icons.Filled.Route,
                                    label = "Peak Vertical G",
                                    value = "${"%.2f".format(g)}g",
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                        val hasYawOrRoll = state.sensorStats.maxYawRateDegPerS != null ||
                            state.sensorStats.maxRollRateDegPerS != null
                        if (hasYawOrRoll) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                state.sensorStats.maxYawRateDegPerS?.let { yaw ->
                                    HeroStatCard(
                                        icon = Icons.Filled.Speed,
                                        label = "Max Yaw Rate",
                                        value = "${"%.1f".format(yaw)}\u00B0/s",
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                                state.sensorStats.maxRollRateDegPerS?.let { roll ->
                                    HeroStatCard(
                                        icon = Icons.Filled.Speed,
                                        label = "Max Roll Rate",
                                        value = "${"%.1f".format(roll)}\u00B0/s",
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(16.dp))

                    // Activity Name
                    Text(
                        text = "Name",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = state.editedName,
                        onValueChange = { viewModel.updateName(it) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { Text("Name your drive") },
                    )

                    // Classification section
                    if (state.classification != null) {
                        Spacer(modifier = Modifier.height(20.dp))
                        HorizontalDivider()
                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "Classification",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Curviness: ${state.classification!!.curvinessScore.toInt()} • Suggested: ${state.classification!!.suggestedRouteType}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // Route type
                        Text("Route Type", style = MaterialTheme.typography.labelLarge)
                        Spacer(modifier = Modifier.height(4.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            RouteClassifier.allRouteTypes.forEach { type ->
                                FilterChip(
                                    selected = state.selectedRouteType == type,
                                    onClick = { viewModel.updateRouteType(type) },
                                    label = { Text(type, style = MaterialTheme.typography.labelMedium) },
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Difficulty
                        Text("Difficulty", style = MaterialTheme.typography.labelLarge)
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            listOf(
                                RouteClassifier.DIFFICULTY_CASUAL,
                                RouteClassifier.DIFFICULTY_MODERATE,
                                RouteClassifier.DIFFICULTY_SPIRITED,
                                RouteClassifier.DIFFICULTY_EXPERT,
                            ).forEach { diff ->
                                FilterChip(
                                    selected = state.selectedDifficulty == diff,
                                    onClick = { viewModel.updateDifficulty(diff) },
                                    label = { Text(diff) },
                                    leadingIcon = if (state.selectedDifficulty == diff) {
                                        { Icon(Icons.Filled.Check, null, modifier = Modifier.size(16.dp)) }
                                    } else null,
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Activity tags
                        Text("Activity", style = MaterialTheme.typography.labelLarge)
                        Spacer(modifier = Modifier.height(4.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            RouteClassifier.allActivityTags.forEach { tag ->
                                FilterChip(
                                    selected = tag in state.selectedActivityTags,
                                    onClick = { viewModel.toggleActivityTag(tag) },
                                    label = { Text(tag, style = MaterialTheme.typography.labelMedium) },
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(16.dp))

                    // Action buttons
                    Button(
                        onClick = { viewModel.saveAndViewDetails() },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !state.isSaving,
                    ) {
                        Icon(Icons.Filled.Check, null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Save & View Details")
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedButton(
                        onClick = { viewModel.discardTrack() },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Filled.Delete, null, tint = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Discard", color = MaterialTheme.colorScheme.error)
                    }

                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
        }

        // Achievement celebration overlay
        if (state.newlyUnlockedAchievements.isNotEmpty()) {
            AchievementPopup(
                achievements = state.newlyUnlockedAchievements,
                onDismiss = { viewModel.dismissAchievements() },
            )
        }
    }
}

@Composable
private fun HeroStatCard(
    icon: ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

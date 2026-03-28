package com.rallytrax.app.ui.garage

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.rallytrax.app.data.fuel.MpgCalculator
import com.rallytrax.app.data.local.entity.FuelLogEntity
import com.rallytrax.app.ui.fuel.FillUpSheet
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rallytrax.app.data.local.entity.MaintenanceRecordEntity
import com.rallytrax.app.data.local.entity.MaintenanceScheduleEntity
import com.rallytrax.app.data.local.entity.TrackEntity
import com.rallytrax.app.data.local.entity.VehicleEntity
import com.rallytrax.app.data.preferences.UnitSystem
import com.rallytrax.app.ui.theme.DifficultyAmber
import com.rallytrax.app.ui.theme.DifficultyGreen
import com.rallytrax.app.ui.theme.DifficultyOrange
import com.rallytrax.app.ui.theme.DifficultyRed
import com.rallytrax.app.ui.theme.SurfaceCobblestone
import com.rallytrax.app.ui.theme.SurfaceDirt
import com.rallytrax.app.ui.theme.SurfaceGravel
import com.rallytrax.app.ui.theme.SurfacePaved
import com.rallytrax.app.ui.theme.SurfaceUnknown
import com.rallytrax.app.util.formatDistance
import com.rallytrax.app.util.formatDate
import com.rallytrax.app.util.formatElapsedTime
import com.rallytrax.app.util.formatSpeed
import com.rallytrax.app.util.speedUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VehicleDetailScreen(
    onBack: () -> Unit = {},
    onTrackClick: (String) -> Unit = {},
    onEdit: (String) -> Unit = {},
    viewModel: VehicleDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val preferences by viewModel.preferences.collectAsStateWithLifecycle()
    val tracks by viewModel.tracks.collectAsStateWithLifecycle()
    val fuelLogs by viewModel.fuelLogs.collectAsStateWithLifecycle()
    val maintenanceRecords by viewModel.maintenanceRecords.collectAsStateWithLifecycle()
    val maintenanceSchedules by viewModel.maintenanceSchedules.collectAsStateWithLifecycle()
    val vehicle = uiState.vehicle
    var selectedTab by remember { mutableIntStateOf(0) }
    var showFillUpSheet by remember { mutableStateOf(false) }
    var showAddServiceSheet by remember { mutableStateOf(false) }
    var showAddScheduleSheet by remember { mutableStateOf(false) }

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
                        IconButton(onClick = { onEdit(vehicle.id) }) {
                            Icon(
                                imageVector = Icons.Filled.Edit,
                                contentDescription = "Edit vehicle",
                            )
                        }
                        IconButton(onClick = { viewModel.toggleActive() }) {
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
                    .padding(innerPadding),
            ) {
                // Tab row – M3 secondary tabs
                val tabLabels = listOf("Overview", "Fuel", "Maintenance", "Analytics")
                PrimaryScrollableTabRow(
                    selectedTabIndex = selectedTab,
                    edgePadding = 16.dp,
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ) {
                    tabLabels.forEachIndexed { index, label ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            text = {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelLarge,
                                )
                            },
                            selectedContentColor = MaterialTheme.colorScheme.primary,
                            unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                when (selectedTab) {
                    0 -> OverviewTab(vehicle, uiState.totalDistanceM, tracks, onTrackClick, preferences.unitSystem)
                    1 -> FuelTab(vehicle, fuelLogs, uiState.lifetimeMpg, uiState.costPerMile) {
                        showFillUpSheet = true
                    }
                    2 -> MaintenanceTab(
                        records = maintenanceRecords,
                        schedules = maintenanceSchedules,
                        onAddService = { showAddServiceSheet = true },
                        onAddSchedule = { showAddScheduleSheet = true },
                        onCompleteSchedule = { viewModel.completeSchedule(it) },
                    )
                    3 -> AnalyticsTab(analytics = uiState.analytics, unitSystem = preferences.unitSystem)
                }
            }

            if (showFillUpSheet) {
                FillUpSheet(
                    onDismiss = {
                        showFillUpSheet = false
                        viewModel.refresh()
                    },
                )
            }
            if (showAddServiceSheet) {
                vehicle?.let { v ->
                    com.rallytrax.app.ui.maintenance.AddServiceSheet(
                        vehicleId = v.id,
                        onSave = { viewModel.addMaintenanceRecord(it) },
                        onDismiss = { showAddServiceSheet = false },
                    )
                }
            }
            if (showAddScheduleSheet) {
                vehicle?.let { v ->
                    com.rallytrax.app.ui.maintenance.AddScheduleSheet(
                        vehicleId = v.id,
                        onSave = { viewModel.addMaintenanceSchedule(it) },
                        onDismiss = { showAddScheduleSheet = false },
                    )
                }
            }
        }
    }
}

@Composable
private fun OverviewTab(
    vehicle: VehicleEntity,
    totalDistanceM: Double,
    tracks: List<TrackEntity>,
    onTrackClick: (String) -> Unit,
    unitSystem: UnitSystem = UnitSystem.METRIC,
) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
            ) {
                Spacer(modifier = Modifier.height(16.dp))

                // Vehicle hero
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                    ),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(
                            imageVector = vehicleTypeIcon(vehicle.vehicleType),
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
                        SpecsGrid(vehicle, totalDistanceM, unitSystem)
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
                                            text = formatDistance(track.distanceMeters, unitSystem),
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

@Composable
private fun FuelTab(
    vehicle: VehicleEntity,
    fuelLogs: List<FuelLogEntity>,
    lifetimeMpg: Double?,
    costPerMile: Double?,
    onLogFillUp: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        // MPG summary card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
            ),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = lifetimeMpg?.let { "%.1f".format(it) } ?: "--",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Text(
                        text = "Actual MPG",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                    )
                }
                vehicle.epaCombinedMpg?.let { epa ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "${epa.toInt()}",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                        Text(
                            text = "EPA Combined",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                        )
                    }
                }
                costPerMile?.let { cpm ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "$%.2f".format(cpm),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                        Text(
                            text = "Cost/Mile",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Fuel log list
        Text(
            text = "Fill-Up History (${fuelLogs.size})",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.height(8.dp))

        if (fuelLogs.isEmpty()) {
            Text(
                text = "No fill-ups logged yet",
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
                fuelLogs.forEachIndexed { index, log ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = formatDate(log.date),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text(
                                    text = "%.1f L".format(log.volumeL),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                log.stationName?.let {
                                    Text(
                                        text = it,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                        log.computedMpg?.let { mpg ->
                            Text(
                                text = "%.1f MPG".format(mpg),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = if (vehicle.epaCombinedMpg != null && mpg >= vehicle.epaCombinedMpg) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.error
                                },
                            )
                        }
                        log.totalCost?.let { cost ->
                            Text(
                                text = "$%.2f".format(cost),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    if (index < fuelLogs.lastIndex) {
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = MaterialTheme.colorScheme.outlineVariant,
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Log fill-up button
        androidx.compose.material3.FilledTonalButton(
            onClick = onLogFillUp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Log Fill-Up")
        }

        Spacer(modifier = Modifier.height(80.dp))
    }
}

@Composable
private fun MaintenanceTab(
    records: List<MaintenanceRecordEntity>,
    schedules: List<MaintenanceScheduleEntity>,
    onAddService: () -> Unit,
    onAddSchedule: () -> Unit,
    onCompleteSchedule: (MaintenanceScheduleEntity) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        // Upcoming maintenance card
        val activeSchedules = schedules.filter { it.status != MaintenanceScheduleEntity.STATUS_COMPLETED }
        if (activeSchedules.isNotEmpty()) {
            Text("Upcoming", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            ) {
                activeSchedules.forEach { schedule ->
                    val statusColor = when (schedule.status) {
                        MaintenanceScheduleEntity.STATUS_OVERDUE -> MaterialTheme.colorScheme.error
                        MaintenanceScheduleEntity.STATUS_DUE_SOON -> androidx.compose.ui.graphics.Color(0xFFFBBC04) // amber
                        else -> MaterialTheme.colorScheme.primary // green/upcoming
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onCompleteSchedule(schedule) }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .padding(end = 0.dp)
                                .then(
                                    Modifier
                                        .width(8.dp)
                                        .height(8.dp)
                                ),
                        ) {
                            androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                                drawCircle(color = statusColor)
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(schedule.serviceType, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                            val dueText = when {
                                schedule.nextDueDate != null -> {
                                    val daysUntil = java.util.concurrent.TimeUnit.MILLISECONDS.toDays(
                                        schedule.nextDueDate - System.currentTimeMillis()
                                    )
                                    when {
                                        daysUntil < 0 -> "${-daysUntil} days overdue"
                                        daysUntil == 0L -> "Due today"
                                        else -> "Due in $daysUntil days"
                                    }
                                }
                                schedule.nextDueOdometerKm != null -> "Due at ${schedule.nextDueOdometerKm.toInt()} km"
                                else -> ""
                            }
                            Text(dueText, style = MaterialTheme.typography.bodySmall, color = statusColor)
                        }
                        Text("Done", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Action buttons
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            androidx.compose.material3.FilledTonalButton(
                onClick = onAddService,
                modifier = Modifier.weight(1f),
            ) { Text("Add Service") }
            androidx.compose.material3.OutlinedButton(
                onClick = onAddSchedule,
                modifier = Modifier.weight(1f),
            ) { Text("Add Schedule") }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Service history
        Text(
            text = "Service History (${records.size})",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.height(8.dp))

        if (records.isEmpty()) {
            Text("No service records yet", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            ) {
                records.forEachIndexed { index, record ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(record.serviceType, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text(record.category, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(formatDate(record.date), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        if (record.costTotal > 0) {
                            Text("$${"%,.0f".format(record.costTotal)}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        }
                        if (record.isDiy) {
                            Text("DIY", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                    if (index < records.lastIndex) {
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(80.dp))
    }
}

@Composable
private fun AnalyticsTab(
    analytics: VehicleAnalytics,
    unitSystem: UnitSystem,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        // Summary stats
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = formatDistance(analytics.totalDistanceM, unitSystem),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                        Text("Total Distance", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f))
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        val hours = analytics.totalTimeMs / 3_600_000
                        val mins = (analytics.totalTimeMs % 3_600_000) / 60_000
                        Text(
                            text = "${hours}h ${mins}m",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                        Text("Total Time", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f))
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "${formatSpeed(analytics.avgSpeedMps, unitSystem)} ${speedUnit(unitSystem)}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                        Text("Avg Speed", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f))
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "${formatSpeed(analytics.topSpeedMps, unitSystem)} ${speedUnit(unitSystem)}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                        Text("Top Speed", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f))
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "${analytics.trackCount}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                        Text("Tracks", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f))
                    }
                }
            }
        }

        // Route type breakdown
        if (analytics.routeTypeBreakdown.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            Text("Route Types", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            BreakdownCard(analytics.routeTypeBreakdown)
        }

        // Surface breakdown
        if (analytics.surfaceBreakdown.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            Text("Surface Types", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            BreakdownCard(analytics.surfaceBreakdown)
        }

        // Difficulty distribution
        if (analytics.curvinessDistribution.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            Text("Difficulty Distribution", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            BreakdownCard(analytics.curvinessDistribution)
        }

        Spacer(modifier = Modifier.height(16.dp))
        FavoriteSegmentsCard(analytics.favoriteSegments, unitSystem)

        if (analytics.performanceByDifficulty.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            PerformanceByCategoryCard(
                title = "Performance by Difficulty",
                categories = analytics.performanceByDifficulty,
                colorForCategory = { category ->
                    when (category.lowercase()) {
                        "casual", "easy" -> DifficultyGreen
                        "moderate" -> DifficultyAmber
                        "spirited", "hard" -> DifficultyOrange
                        "expert" -> DifficultyRed
                        else -> DifficultyGreen
                    }
                },
                unitSystem = unitSystem,
            )
        }

        if (analytics.performanceBySurface.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            PerformanceByCategoryCard(
                title = "Performance by Surface",
                categories = analytics.performanceBySurface,
                colorForCategory = { category ->
                    when (category.lowercase()) {
                        "paved", "tarmac", "asphalt" -> SurfacePaved
                        "gravel" -> SurfaceGravel
                        "dirt" -> SurfaceDirt
                        "cobblestone" -> SurfaceCobblestone
                        else -> SurfaceUnknown
                    }
                },
                unitSystem = unitSystem,
            )
        }

        Spacer(modifier = Modifier.height(80.dp))
    }
}

@Composable
private fun BreakdownCard(data: Map<String, Int>) {
    val total = data.values.sum().toFloat()
    if (total == 0f) return

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            data.entries.sortedByDescending { it.value }.forEach { (label, count) ->
                val fraction = count / total
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.width(120.dp),
                    )
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(16.dp)
                            .background(
                                MaterialTheme.colorScheme.outlineVariant,
                                androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                            ),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(fraction)
                                .height(16.dp)
                                .background(
                                    MaterialTheme.colorScheme.primary,
                                    androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                                ),
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "${(fraction * 100).toInt()}%",
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.width(36.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun SpecsGrid(vehicle: VehicleEntity, totalDistanceM: Double, unitSystem: UnitSystem = UnitSystem.METRIC) {
    val specs = buildList {
        add("Year" to vehicle.year.toString())
        add("Make" to vehicle.make)
        add("Model" to vehicle.model)
        vehicle.trim?.let { add("Trim" to it) }
        if (vehicle.vehicleType != "CAR") add("Type" to vehicle.vehicleType.lowercase().replaceFirstChar { it.uppercase() })
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
        vehicle.oilType?.let { add("Oil" to it) }
        vehicle.engineConfiguration?.let { add("Engine Config" to it) }
        vehicle.wheelDiameter?.let { add("Wheels" to "${it}\"") }
        vehicle.tireSize?.let { add("Tires" to it) }
        add("Odometer" to formatDistance(vehicle.odometerKm * 1000, unitSystem))
        if (totalDistanceM > 0) {
            add("RallyTrax Distance" to formatDistance(totalDistanceM, unitSystem))
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

@Composable
private fun FavoriteSegmentsCard(
    segments: List<VehicleSegmentInfo>,
    unitSystem: UnitSystem,
) {
    Text(
        text = "Favorite Segments",
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
    )
    Spacer(modifier = Modifier.height(8.dp))

    if (segments.isEmpty()) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        ) {
            Text(
                text = "No favorite segments yet",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
        }
    } else {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                segments.forEachIndexed { index, info ->
                    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Text(
                            text = info.segment.name,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(
                                text = "${info.runCount} runs",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            info.bestTimeMs?.let { bestMs ->
                                Text(
                                    text = "Best: ${formatElapsedTime(bestMs)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Text(
                                text = "Avg: ${formatSpeed(info.avgSpeedMps, unitSystem)} ${speedUnit(unitSystem)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    if (index < segments.lastIndex) {
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 4.dp),
                            color = MaterialTheme.colorScheme.outlineVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PerformanceByCategoryCard(
    title: String,
    categories: List<PerformanceByCategory>,
    colorForCategory: (String) -> Color,
    unitSystem: UnitSystem,
) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
    )
    Spacer(modifier = Modifier.height(8.dp))

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header row
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            ) {
                Text(
                    text = "Category",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1.2f),
                )
                Text(
                    text = "Tracks",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.End,
                    modifier = Modifier.weight(0.6f),
                )
                Text(
                    text = "Avg",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.End,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "Top",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.End,
                    modifier = Modifier.weight(1f),
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            categories.forEach { category ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        modifier = Modifier.weight(1.2f),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(
                                    colorForCategory(category.category),
                                    androidx.compose.foundation.shape.CircleShape,
                                ),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = category.category,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Text(
                        text = "${category.trackCount}",
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.End,
                        modifier = Modifier.weight(0.6f),
                    )
                    Text(
                        text = "${formatSpeed(category.avgSpeedMps, unitSystem)} ${speedUnit(unitSystem)}",
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.End,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = "${formatSpeed(category.topSpeedMps, unitSystem)} ${speedUnit(unitSystem)}",
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.End,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

package com.rallytrax.app.ui.garage

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Terrain
import androidx.compose.material.icons.filled.TwoWheeler
import androidx.compose.material.icons.filled.Warning
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.material3.rememberTooltipState
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rallytrax.app.ui.components.EmptyStateView
import com.rallytrax.app.ui.components.RallyTraxTopAppBar
import com.rallytrax.app.ui.theme.RallyTraxTypeEmphasized
import com.rallytrax.app.ui.theme.ShapeLargeIncreased
import com.rallytrax.app.ui.theme.rallyTraxColors
import com.rallytrax.app.util.formatDistance
import com.rallytrax.app.data.preferences.UserPreferencesData

internal fun vehicleTypeIcon(vehicleType: String): ImageVector = when (vehicleType) {
    "MOTORCYCLE" -> Icons.Filled.TwoWheeler
    "TRUCK" -> Icons.Filled.LocalShipping
    "SUV" -> Icons.Filled.Terrain
    "OTHER" -> Icons.Filled.MoreHoriz
    else -> Icons.Filled.DirectionsCar
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun GarageScreen(
    onVehicleClick: (String) -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    onBack: (() -> Unit)? = null,
    isSignedIn: Boolean = false,
    userPhotoUrl: String? = null,
    onProfileClick: () -> Unit = {},
    viewModel: GarageViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val preferences by viewModel.preferences.collectAsStateWithLifecycle()
    val pendingArchive by viewModel.pendingArchive.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    var showAddVehicleSheet by remember { mutableStateOf(false) }

    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    // Undo archive snackbar
    val latestPending = pendingArchive
    LaunchedEffect(latestPending?.id) {
        latestPending?.let { vehicle ->
            snackbarHostState.currentSnackbarData?.dismiss()
            val result = snackbarHostState.showSnackbar(
                message = "${vehicle.name} archived",
                actionLabel = "Undo",
                duration = SnackbarDuration.Short,
            )
            when (result) {
                SnackbarResult.ActionPerformed -> viewModel.cancelArchive()
                SnackbarResult.Dismissed -> viewModel.confirmArchive()
            }
        }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            RallyTraxTopAppBar(
                title = "Garage",
                onSettingsClick = onNavigateToSettings,
                scrollBehavior = scrollBehavior,
                isSignedIn = isSignedIn,
                userPhotoUrl = userPhotoUrl,
                onProfileClick = onProfileClick,
                onBack = onBack,
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddVehicleSheet = true },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                elevation = FloatingActionButtonDefaults.elevation(
                    defaultElevation = 6.dp,
                    pressedElevation = 6.dp,
                ),
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = "Add vehicle",
                )
            }
        },
    ) { innerPadding ->
        if (uiState.vehicles.isEmpty() && !uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                EmptyStateView(
                    icon = Icons.Filled.DirectionsCar,
                    title = "No vehicles yet",
                    body = "Add your first car to start tracking per-vehicle stats.",
                    actionLabel = "Add vehicle",
                    onAction = { showAddVehicleSheet = true },
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                items(
                    items = uiState.vehicles,
                    key = { it.vehicle.id },
                ) { vehicleWithStats ->
                    val vehicle = vehicleWithStats.vehicle
                    val dismissState = rememberSwipeToDismissBoxState()

                    LaunchedEffect(dismissState.currentValue) {
                        if (dismissState.currentValue == SwipeToDismissBoxValue.EndToStart) {
                            viewModel.requestArchiveVehicle(vehicle)
                            dismissState.snapTo(SwipeToDismissBoxValue.Settled)
                        }
                    }

                    SwipeToDismissBox(
                        state = dismissState,
                        backgroundContent = {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 16.dp, vertical = 4.dp)
                                    .clip(ShapeLargeIncreased)
                                    .background(MaterialTheme.colorScheme.errorContainer)
                                    .padding(horizontal = 20.dp),
                                contentAlignment = Alignment.CenterEnd,
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Filled.Archive,
                                        contentDescription = "Archive",
                                        tint = MaterialTheme.colorScheme.onErrorContainer,
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Archive",
                                        style = RallyTraxTypeEmphasized.labelLarge,
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                    )
                                }
                            }
                        },
                        enableDismissFromStartToEnd = false,
                    ) {
                        VehicleCard(
                            vehicleWithStats = vehicleWithStats,
                            unitSystem = preferences.unitSystem,
                            onClick = { onVehicleClick(vehicle.id) },
                            onLongClick = { viewModel.toggleActiveVehicle(vehicle.id) },
                            modifier = Modifier.animateItem(),
                        )
                    }
                }

                item { Spacer(modifier = Modifier.height(80.dp)) }
            }
        }
    }

    if (showAddVehicleSheet) {
        AddVehicleSheet(
            onDismiss = { showAddVehicleSheet = false },
            onVehicleSaved = { showAddVehicleSheet = false },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun VehicleCard(
    vehicleWithStats: VehicleWithStats,
    unitSystem: com.rallytrax.app.data.preferences.UnitSystem,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val vehicle = vehicleWithStats.vehicle

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(ShapeLargeIncreased)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        shape = ShapeLargeIncreased,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Box {
            Column(
                modifier = Modifier.padding(16.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Leading tinted badge showing vehicle type
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .background(
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                shape = CircleShape,
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = vehicleTypeIcon(vehicle.vehicleType),
                            contentDescription = vehicle.vehicleType,
                            modifier = Modifier.size(22.dp),
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = vehicle.name,
                            style = RallyTraxTypeEmphasized.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = "${vehicle.year} ${vehicle.make} ${vehicle.model}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (vehicle.isActive) {
                        Icon(
                            imageVector = Icons.Filled.Star,
                            contentDescription = "Active vehicle",
                            modifier = Modifier.size(24.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Stats row
                Row(
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                ) {
                    HeroStatItem(
                        value = formatDistance(vehicleWithStats.totalDistanceM, unitSystem),
                        label = "distance",
                    )
                    StatItem(
                        value = "${vehicleWithStats.trackCount}",
                        label = "tracks",
                    )
                    vehicle.epaCombinedMpg?.let { mpg ->
                        StatItem(
                            value = "${mpg.toInt()}",
                            label = "EPA MPG",
                        )
                    }
                }
            }

            // Warning lights in bottom-right corner
            if (vehicleWithStats.warnings.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    vehicleWithStats.warnings.forEach { warning ->
                        WarningLight(warning = warning)
                    }
                }
            }
        }
    }
}

@Composable
private fun StatItem(value: String, label: String) {
    Column {
        Text(
            text = value,
            style = RallyTraxTypeEmphasized.bodyLarge,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun HeroStatItem(value: String, label: String) {
    Column {
        Text(
            text = value,
            style = RallyTraxTypeEmphasized.titleLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WarningLight(warning: VehicleWarning) {
    val rtColors = MaterialTheme.rallyTraxColors
    val (icon, color, description) = when (warning) {
        VehicleWarning.MISSING_VIN -> Triple(Icons.Filled.ErrorOutline, rtColors.maintenanceWarning, "No VIN")
        VehicleWarning.NO_TRACKS -> Triple(Icons.Filled.ErrorOutline, rtColors.maintenanceWarning, "No tracks")
        VehicleWarning.INCOMPLETE_SPECS -> Triple(Icons.Filled.Warning, rtColors.maintenanceWarning, "Incomplete specs")
        VehicleWarning.MAINTENANCE_DUE -> Triple(Icons.Filled.Build, rtColors.maintenanceDue, "Maintenance due")
    }

    val pulsesUrgently = warning == VehicleWarning.MAINTENANCE_DUE
    val alpha = if (pulsesUrgently) {
        val transition = rememberInfiniteTransition(label = "warning_pulse")
        val pulse by transition.animateFloat(
            initialValue = 0.35f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 900),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "warning_pulse_alpha",
        )
        pulse
    } else 1f

    @Suppress("DEPRECATION")
    val tooltipPosition = TooltipDefaults.rememberTooltipPositionProvider()
    TooltipBox(
        positionProvider = tooltipPosition,
        tooltip = { PlainTooltip { Text(description) } },
        state = rememberTooltipState(),
    ) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .background(color.copy(alpha = 0.15f * alpha + 0.12f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = description,
                modifier = Modifier.size(14.dp),
                tint = color.copy(alpha = alpha),
            )
        }
    }
}

package com.rallytrax.app.ui.home

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rallytrax.app.data.local.entity.AchievementEntity
import com.rallytrax.app.ui.auth.GoogleSignInCard
import com.rallytrax.app.ui.components.GoalRing
import com.rallytrax.app.ui.fuel.FillUpSheet
import com.rallytrax.app.ui.components.RallyTraxTopAppBar
import com.rallytrax.app.ui.theme.RallyTraxMotion
import com.rallytrax.app.ui.theme.rallyTraxColors
import com.rallytrax.app.util.formatDistance
import com.rallytrax.app.data.local.entity.MaintenanceScheduleEntity
import com.rallytrax.app.util.formatDate
import com.rallytrax.app.util.formatElapsedTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onStartRecording: () -> Unit = {},
    onTrackClick: (String) -> Unit = {},
    onReplayTrack: (String) -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    authState: com.rallytrax.app.data.auth.AuthState = com.rallytrax.app.data.auth.AuthState.SignedOut,
    isSignedIn: Boolean = false,
    userPhotoUrl: String? = null,
    onSignIn: () -> Unit = {},
    onProfileClick: () -> Unit = {},
    onVehicleClick: (String) -> Unit = {},
    onViewAllAchievements: () -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val feedState by viewModel.feedState.collectAsStateWithLifecycle()
    val friendActivities by viewModel.friendActivities.collectAsStateWithLifecycle()
    val preferences by viewModel.preferences.collectAsStateWithLifecycle()
    val duplicateState by viewModel.duplicateImportState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    var hasLocationPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION,
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { permissions ->
        hasLocationPermission = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
        if (hasLocationPermission) {
            onStartRecording()
        }
    }

    val gpxImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        uri?.let { viewModel.importGpx(context, it) }
    }

    var isFabMenuExpanded by remember { mutableStateOf(false) }
    var showReplaySheet by remember { mutableStateOf(false) }
    var showFillUpSheet by remember { mutableStateOf(false) }

    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    fun requestRecording() {
        if (hasLocationPermission) {
            onStartRecording()
        } else {
            val perms = mutableListOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                perms.add(Manifest.permission.POST_NOTIFICATIONS)
            }
            permissionLauncher.launch(perms.toTypedArray())
        }
    }

    LaunchedEffect(Unit) {
        viewModel.snackbarMessage.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            RallyTraxTopAppBar(
                title = "RallyTrax",
                onSettingsClick = onNavigateToSettings,
                scrollBehavior = scrollBehavior,
                isSignedIn = isSignedIn,
                userPhotoUrl = userPhotoUrl,
                onProfileClick = onProfileClick,
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            RallyTraxFabMenu(
                expanded = isFabMenuExpanded,
                onToggle = { isFabMenuExpanded = !isFabMenuExpanded },
                onRecord = {
                    isFabMenuExpanded = false
                    requestRecording()
                },
                onReplay = {
                    isFabMenuExpanded = false
                    showReplaySheet = true
                },
                onImportGpx = {
                    isFabMenuExpanded = false
                    gpxImportLauncher.launch(
                        arrayOf("application/gpx+xml", "application/xml", "text/xml", "*/*")
                    )
                },
                onLogFillUp = {
                    isFabMenuExpanded = false
                    showFillUpSheet = true
                },
            )
        },
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // Sign-in card
                if (!isSignedIn) {
                    item(key = "sign_in") {
                        Spacer(modifier = Modifier.height(4.dp))
                        GoogleSignInCard(authState = authState, onClick = onSignIn)
                    }
                }

                // Vehicle filter chip
                item(key = "vehicle_filter") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        androidx.compose.material3.FilterChip(
                            selected = feedState.showActiveVehicleOnly,
                            onClick = { viewModel.toggleVehicleFilter() },
                            label = {
                                Text(
                                    text = if (feedState.showActiveVehicleOnly) {
                                        feedState.activeVehicleName ?: "Active Vehicle"
                                    } else {
                                        "All Vehicles"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            },
                        )
                    }
                }

                // Weekly summary strip
                item(key = "weekly_summary") {
                    WeeklySummaryStrip(
                        summary = feedState.weeklySummary,
                        unitSystem = preferences.unitSystem,
                        weeklyGoalProgress = feedState.weeklyGoalProgress,
                    )
                }

                // Milestone celebration cards
                items(
                    items = feedState.recentAchievements,
                    key = { "milestone_${it.id}" },
                ) { achievement ->
                    MilestoneCard(
                        achievement = achievement,
                        onViewAll = onViewAllAchievements,
                    )
                }

                // Maintenance warning
                if (feedState.maintenanceDueItems.isNotEmpty()) {
                    item(key = "maintenance") {
                        MaintenanceWarningCard(
                            items = feedState.maintenanceDueItems,
                            onItemClick = onVehicleClick,
                        )
                    }
                }

                // Motivational card for new users
                if (feedState.totalStintCount < 3) {
                    item(key = "motivational") {
                        MotivationalCard(onRecord = { requestRecording() })
                    }
                }

                // Activity feed header
                if (feedState.feedItems.isNotEmpty() || feedState.totalStintCount >= 3) {
                    item(key = "feed_header") {
                        Text(
                            text = "Recent Drives",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }

                // Activity feed
                if (feedState.feedItems.isEmpty() && feedState.totalStintCount >= 3) {
                    item(key = "empty_feed") {
                        Text(
                            text = "No drives match the current filter",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp),
                        )
                    }
                }

                items(
                    items = feedState.feedItems,
                    key = { it.track.id },
                ) { feedItem ->
                    ActivityFeedCard(
                        feedItem = feedItem,
                        unitSystem = preferences.unitSystem,
                        onClick = { onTrackClick(feedItem.track.id) },
                        onVehicleClick = onVehicleClick,
                        modifier = Modifier.animateItem(),
                    )
                }

                // Friends activity feed
                if (friendActivities.isNotEmpty()) {
                    item(key = "friends_header") {
                        Text(
                            text = "Friends",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(vertical = 8.dp),
                        )
                    }
                    items(
                        items = friendActivities,
                        key = { "friend_${it.trackId}" },
                    ) { sharedTrack ->
                        FriendActivityCard(
                            sharedTrack = sharedTrack,
                            unitSystem = preferences.unitSystem,
                            modifier = Modifier.animateItem(),
                        )
                    }
                }

                // Bottom spacer for FAB
                item(key = "fab_spacer") {
                    Spacer(modifier = Modifier.height(72.dp))
                }
            }

            // Scrim when FAB menu is expanded
            if (isFabMenuExpanded) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f))
                        .clickable { isFabMenuExpanded = false },
                )
            }
        }
    }

    if (showReplaySheet) {
        ReplayTrackPickerSheet(
            onTrackSelected = { trackId ->
                showReplaySheet = false
                onReplayTrack(trackId)
            },
            onDismiss = { showReplaySheet = false },
        )
    }

    if (showFillUpSheet) {
        FillUpSheet(onDismiss = { showFillUpSheet = false })
    }

    // Duplicate import detection dialog
    duplicateState?.let { state ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissDuplicateDialog() },
            title = { Text("Route Already Exists") },
            text = {
                Column {
                    Text(
                        text = "\"${state.pendingImport.track.name}\" appears to match an existing route.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Existing: ${formatDistance(state.existingTrack.distanceMeters, preferences.unitSystem)}, ${formatElapsedTime(state.existingTrack.durationMs)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "New: ${formatDistance(state.pendingImport.track.distanceMeters, preferences.unitSystem)}, ${formatElapsedTime(state.pendingImport.track.durationMs)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.confirmReplaceExisting() }) {
                    Text("Replace Existing")
                }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { viewModel.dismissDuplicateDialog() }) {
                        Text("Cancel")
                    }
                    TextButton(onClick = { viewModel.confirmImportAsNew() }) {
                        Text("Import Anyway")
                    }
                }
            },
        )
    }
}

// ── Weekly Summary Strip ──────────────────────────────────────────────────────

@Composable
private fun WeeklySummaryStrip(
    summary: WeeklySummary,
    unitSystem: com.rallytrax.app.data.preferences.UnitSystem,
    weeklyGoalProgress: Float? = null,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
        shape = MaterialTheme.shapes.large,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 20.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (weeklyGoalProgress != null) {
                GoalRing(
                    progress = weeklyGoalProgress,
                    label = "Goal",
                    size = 60.dp,
                    strokeWidth = 6.dp,
                )
            } else {
                SummaryMetric(
                    label = "This Week",
                    value = formatDistance(summary.totalDistanceMeters, unitSystem),
                    modifier = Modifier.weight(1f),
                )
            }
            VerticalDivider(
                modifier = Modifier.height(36.dp),
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.15f),
            )
            SummaryMetric(
                label = "Drives",
                value = summary.driveCount.toString(),
                modifier = Modifier.weight(1f),
            )
            VerticalDivider(
                modifier = Modifier.height(36.dp),
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.15f),
            )
            SummaryMetric(
                label = "Time",
                value = formatElapsedTime(summary.totalDurationMs),
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun SummaryMetric(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
        )
    }
}

// ── Maintenance Warning Card ──────────────────────────────────────────────────

@Composable
private fun MaintenanceWarningCard(
    items: List<MaintenanceDueItem>,
    onItemClick: (String) -> Unit = {},
) {
    val maintenanceColor = MaterialTheme.rallyTraxColors.maintenanceDue
    val displayItems = items.take(3)
    val remainingCount = items.size - displayItems.size

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(
                            maintenanceColor.copy(alpha = 0.15f),
                            androidx.compose.foundation.shape.CircleShape,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Build,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = maintenanceColor,
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Maintenance Due",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Service items
            displayItems.forEachIndexed { index, item ->
                if (index > 0) {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 8.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                    )
                }
                MaintenanceServiceRow(
                    item = item,
                    onClick = { onItemClick(item.vehicleId) },
                )
            }

            // "+X more" indicator
            if (remainingCount > 0) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "+ $remainingCount more",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun MaintenanceServiceRow(
    item: MaintenanceDueItem,
    onClick: () -> Unit,
) {
    val isOverdue = item.status == MaintenanceScheduleEntity.STATUS_OVERDUE
    val statusColor = if (isOverdue) {
        MaterialTheme.rallyTraxColors.maintenanceDue
    } else {
        MaterialTheme.rallyTraxColors.maintenanceWarning
    }
    val statusText = if (isOverdue) "Overdue" else "Due Soon"

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.serviceType,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = item.vehicleName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (item.nextDueDate != null) {
                Text(
                    text = formatDate(item.nextDueDate),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = statusText,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = statusColor,
            modifier = Modifier
                .background(
                    statusColor.copy(alpha = 0.12f),
                    MaterialTheme.shapes.small,
                )
                .padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

// ── Motivational Card ─────────────────────────────────────────────────────────

@Composable
private fun MotivationalCard(onRecord: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = Icons.Filled.DirectionsCar,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onTertiaryContainer,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Record your first rally stage!",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Hit the road and start building your driving library",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f),
            )
            Spacer(modifier = Modifier.height(16.dp))
            FilledTonalButton(onClick = onRecord) {
                Icon(Icons.Filled.FiberManualRecord, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Start Recording")
            }
        }
    }
}

// ── Milestone Celebration Card ────────────────────────────────────────────────

@Composable
private fun MilestoneCard(
    achievement: AchievementEntity,
    onViewAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
        ),
        shape = MaterialTheme.shapes.medium,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.EmojiEvents,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = Color(0xFFFFD700),
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Achievement Unlocked!",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f),
                )
                Text(
                    text = achievement.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
                Text(
                    text = achievement.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f),
                )
            }
        }
    }
}

// ── FAB Menu ──────────────────────────────────────────────────────────────────

@Composable
private fun RallyTraxFabMenu(
    expanded: Boolean,
    onToggle: () -> Unit,
    onRecord: () -> Unit,
    onReplay: () -> Unit,
    onImportGpx: () -> Unit,
    onLogFillUp: () -> Unit,
) {
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 45f else 0f,
        animationSpec = RallyTraxMotion.fastSpatial(),
        label = "fab_rotation",
    )

    Column(
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn(RallyTraxMotion.fastEffects()) +
                slideInVertically(RallyTraxMotion.fastSpatial()) { it / 2 } +
                scaleIn(RallyTraxMotion.fastSpatial(), initialScale = 0.8f),
            exit = fadeOut(RallyTraxMotion.fastEffects()) +
                slideOutVertically(RallyTraxMotion.fastSpatial()) { it / 2 } +
                scaleOut(RallyTraxMotion.fastSpatial(), targetScale = 0.8f),
        ) {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                FabMenuItem(
                    icon = Icons.Filled.LocalGasStation,
                    label = "Log fill-up",
                    onClick = onLogFillUp,
                )
                FabMenuItem(
                    icon = Icons.Filled.FileOpen,
                    label = "Import GPX",
                    onClick = onImportGpx,
                )
                FabMenuItem(
                    icon = Icons.Filled.PlayArrow,
                    label = "Replay",
                    onClick = onReplay,
                )
                FabMenuItem(
                    icon = Icons.Filled.FiberManualRecord,
                    label = "Record",
                    onClick = onRecord,
                )
            }
        }

        FloatingActionButton(
            onClick = onToggle,
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            elevation = androidx.compose.material3.FloatingActionButtonDefaults.elevation(
                defaultElevation = 6.dp,
                pressedElevation = 6.dp,
            ),
        ) {
            Icon(
                imageVector = if (expanded) Icons.Filled.Close else Icons.Filled.Add,
                contentDescription = if (expanded) "Close menu" else "Open menu",
                modifier = Modifier.rotate(rotation),
            )
        }
    }
}

@Composable
private fun FabMenuItem(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
            ),
        ) {
            Text(
                text = label,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                style = MaterialTheme.typography.labelLarge,
            )
        }
        SmallFloatingActionButton(
            onClick = onClick,
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
            )
        }
    }
}

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
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
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
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rallytrax.app.data.local.entity.AchievementEntity
import com.rallytrax.app.ui.auth.GoogleSignInCard
import com.rallytrax.app.ui.components.GoalRing
import com.rallytrax.app.ui.fuel.FillUpSheet
import com.rallytrax.app.ui.components.RallyTraxTopAppBar
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.window.core.layout.WindowWidthSizeClass
import com.rallytrax.app.ui.theme.RallyTraxMotion
import com.rallytrax.app.ui.theme.RallyTraxTypeEmphasized
import com.rallytrax.app.ui.theme.ShapeExtraLargeIncreased
import com.rallytrax.app.ui.theme.ShapeFullRound
import com.rallytrax.app.ui.theme.ShapeLargeIncreased
import com.rallytrax.app.ui.theme.rallyTraxColors
import com.rallytrax.app.util.formatDistance
import com.rallytrax.app.data.local.entity.MaintenanceScheduleEntity
import com.rallytrax.app.ui.trips.TripSuggestionCard
import com.rallytrax.app.ui.trips.TripSuggestionViewModel
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
    onNavigateToGarage: () -> Unit = {},
    onViewAllAchievements: () -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel(),
    suggestionViewModel: TripSuggestionViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val feedState by viewModel.feedState.collectAsStateWithLifecycle()
    val friendActivities by viewModel.friendActivities.collectAsStateWithLifecycle()
    val suggestionState by suggestionViewModel.uiState.collectAsStateWithLifecycle()
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

    // On Medium+ screens the navigation rail/drawer already surfaces Record,
    // so the Home FAB menu (which duplicates Record and clutters larger
    // layouts) is only shown on Compact.
    val isCompactWidth = currentWindowAdaptiveInfo().windowSizeClass.windowWidthSizeClass ==
        WindowWidthSizeClass.COMPACT

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
            if (isCompactWidth) {
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
            }
        },
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize()) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 360.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // Sign-in card
                if (!isSignedIn) {
                    item(key = "sign_in", span = { GridItemSpan(maxLineSpan) }) {
                        Spacer(modifier = Modifier.height(4.dp))
                        GoogleSignInCard(authState = authState, onClick = onSignIn)
                    }
                }

                // Vehicle hero banner (active vehicle) — or a prompt if none.
                val activeVehicle = feedState.activeVehicle
                if (activeVehicle != null) {
                    item(key = "vehicle_hero", span = { GridItemSpan(maxLineSpan) }) {
                        val tracksForVehicle = remember(feedState.feedItems, activeVehicle.id) {
                            feedState.feedItems
                                .map { it.track }
                                .filter { it.vehicleId == activeVehicle.id }
                        }
                        com.rallytrax.app.ui.garage.VehicleHeroCard(
                            vehicle = activeVehicle,
                            tracks = tracksForVehicle,
                            unitSystem = preferences.unitSystem,
                            onClick = { onVehicleClick(activeVehicle.id) },
                        )
                    }
                } else {
                    item(key = "vehicle_prompt", span = { GridItemSpan(maxLineSpan) }) {
                        NoPrimaryVehicleCard(onClick = onNavigateToGarage)
                    }
                }

                // Weekly summary strip
                item(key = "weekly_summary", span = { GridItemSpan(maxLineSpan) }) {
                    WeeklySummaryStrip(
                        summary = feedState.weeklySummary,
                        unitSystem = preferences.unitSystem,
                        weeklyGoalProgress = feedState.weeklyGoalProgress,
                    )
                }

                // Quick actions row
                item(key = "quick_actions", span = { GridItemSpan(maxLineSpan) }) {
                    QuickActionsRow(
                        onRecord = { requestRecording() },
                        onReplay = { showReplaySheet = true },
                        onImportGpx = {
                            gpxImportLauncher.launch(
                                arrayOf("application/gpx+xml", "application/xml", "text/xml", "*/*")
                            )
                        },
                        onFillUp = { showFillUpSheet = true },
                    )
                }

                // Milestone celebration cards — wrap into multiple columns on wider screens
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
                    item(key = "maintenance", span = { GridItemSpan(maxLineSpan) }) {
                        MaintenanceWarningCard(
                            items = feedState.maintenanceDueItems,
                            onItemClick = onVehicleClick,
                        )
                    }
                }

                // Motivational card for new users
                if (feedState.totalStintCount < 3) {
                    item(key = "motivational", span = { GridItemSpan(maxLineSpan) }) {
                        MotivationalCard(onRecord = { requestRecording() })
                    }
                }

                // Trip suggestion cards (show max 2 on home screen) — wrap side-by-side
                if (suggestionState.suggestions.isNotEmpty()) {
                    items(
                        items = suggestionState.suggestions.take(2),
                        key = { "home_suggestion_${it.id}" },
                    ) { suggestion ->
                        TripSuggestionCard(
                            suggestion = suggestion,
                            onAccept = { suggestionViewModel.acceptSuggestion(it) },
                            onDismiss = { suggestionViewModel.dismissSuggestion(it) },
                            modifier = Modifier.padding(horizontal = 0.dp),
                            unitSystem = preferences.unitSystem,
                        )
                    }
                }

                // Activity feed header
                if (feedState.feedItems.isNotEmpty() || feedState.totalStintCount >= 3) {
                    item(key = "feed_header", span = { GridItemSpan(maxLineSpan) }) {
                        Text(
                            text = "Recent Drives",
                            style = RallyTraxTypeEmphasized.titleMedium,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }

                // Activity feed empty-state placeholder
                if (feedState.feedItems.isEmpty() && feedState.totalStintCount >= 3) {
                    item(key = "empty_feed", span = { GridItemSpan(maxLineSpan) }) {
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

                // Activity feed cards — multi-column on Medium+ widths
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
                    item(key = "friends_header", span = { GridItemSpan(maxLineSpan) }) {
                        Text(
                            text = "Friends",
                            style = RallyTraxTypeEmphasized.titleMedium,
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
                item(key = "fab_spacer", span = { GridItemSpan(maxLineSpan) }) {
                    Spacer(modifier = Modifier.height(72.dp))
                }
            }

            // Scrim when FAB menu is expanded
            if (isFabMenuExpanded) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.55f))
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

// ── No-primary-vehicle prompt ─────────────────────────────────────────────────

@Composable
private fun NoPrimaryVehicleCard(onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(
                        MaterialTheme.colorScheme.secondaryContainer,
                        androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.DirectionsCar,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(24.dp),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "No primary vehicle",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "Pick one in the Garage to see it here",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                imageVector = Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ── Quick Actions ────────────────────────────────────────────────────────────

@Composable
private fun QuickActionsRow(
    onRecord: () -> Unit,
    onReplay: () -> Unit,
    onImportGpx: () -> Unit,
    onFillUp: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        QuickActionTile(
            icon = Icons.Filled.FiberManualRecord,
            label = "Record",
            primary = true,
            onClick = onRecord,
            modifier = Modifier.weight(1f),
        )
        QuickActionTile(
            icon = Icons.Filled.PlayArrow,
            label = "Replay",
            onClick = onReplay,
            modifier = Modifier.weight(1f),
        )
        QuickActionTile(
            icon = Icons.Filled.FileOpen,
            label = "Import",
            onClick = onImportGpx,
            modifier = Modifier.weight(1f),
        )
        QuickActionTile(
            icon = Icons.Filled.LocalGasStation,
            label = "Fuel",
            onClick = onFillUp,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun QuickActionTile(
    icon: ImageVector,
    label: String,
    primary: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val container = if (primary) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.surfaceContainer
    val content = if (primary) MaterialTheme.colorScheme.onPrimary
        else MaterialTheme.colorScheme.onSurface
    val innerBg = if (primary) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.14f)
        else MaterialTheme.colorScheme.surfaceContainerHigh
    Card(
        modifier = modifier,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = container, contentColor = content),
        onClick = onClick,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(innerBg, androidx.compose.foundation.shape.RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(imageVector = icon, contentDescription = label, modifier = Modifier.size(20.dp))
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = if (primary) content else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ── Weekly Summary Strip ──────────────────────────────────────────────────────

@Composable
private fun WeeklySummaryStrip(
    summary: WeeklySummary,
    unitSystem: com.rallytrax.app.data.preferences.UnitSystem,
    weeklyGoalProgress: Float? = null,
) {
    val isMetric = unitSystem == com.rallytrax.app.data.preferences.UnitSystem.METRIC
    val heroValue: Int
    val heroUnit: String
    if (isMetric) {
        heroValue = kotlin.math.round(summary.totalDistanceMeters / 1000.0).toInt()
        heroUnit = "km"
    } else {
        heroValue = kotlin.math.round(summary.totalDistanceMeters / 1609.344).toInt()
        heroUnit = "mi"
    }
    val onContainer = MaterialTheme.colorScheme.onPrimaryContainer
    val gradient = androidx.compose.ui.graphics.Brush.linearGradient(
        colors = listOf(
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.tertiaryContainer,
        ),
    )

    Card(
        shape = ShapeExtraLargeIncreased,
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(brush = gradient, shape = ShapeExtraLargeIncreased)
                .padding(20.dp),
        ) {
            com.rallytrax.app.ui.components.OverlineLabel(
                text = "This week",
                color = onContainer.copy(alpha = 0.75f),
            )
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = heroValue.toString(),
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 64.sp,
                            lineHeight = 62.sp,
                            letterSpacing = (-3).sp,
                            color = onContainer,
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = heroUnit,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Medium,
                            color = onContainer.copy(alpha = 0.7f),
                            letterSpacing = (-0.5).sp,
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    val subhead = if (weeklyGoalProgress != null) {
                        val pct = (weeklyGoalProgress.coerceIn(0f, 1f) * 100).toInt()
                        val dow = java.time.LocalDate.now().dayOfWeek.value  // Mon=1..Sun=7
                        val daysLeft = (7 - dow).coerceAtLeast(0)
                        val daysLabel = when (daysLeft) {
                            0 -> "last day of the week"
                            1 -> "1 day left"
                            else -> "$daysLeft days left"
                        }
                        "$pct% of weekly goal · $daysLabel"
                    } else {
                        "${summary.driveCount} drives · ${formatElapsedTime(summary.totalDurationMs)} behind the wheel"
                    }
                    Text(
                        text = subhead,
                        style = MaterialTheme.typography.bodyMedium,
                        color = onContainer.copy(alpha = 0.8f),
                    )
                }
                if (weeklyGoalProgress != null) {
                    Spacer(Modifier.width(12.dp))
                    GoalRing(
                        progress = weeklyGoalProgress,
                        label = "goal",
                        size = 82.dp,
                        strokeWidth = 9.dp,
                    )
                }
            }
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
            style = RallyTraxTypeEmphasized.headlineMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            style = RallyTraxTypeEmphasized.labelSmall,
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
                style = RallyTraxTypeEmphasized.titleMedium,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Hit the road and start building your driving library",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f),
            )
            Spacer(modifier = Modifier.height(16.dp))
            FilledTonalButton(
                onClick = onRecord,
                shape = ShapeFullRound,
            ) {
                Icon(Icons.Filled.FiberManualRecord, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Start Recording", style = RallyTraxTypeEmphasized.labelLarge)
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
    val gradient = androidx.compose.ui.graphics.Brush.linearGradient(
        colors = listOf(
            MaterialTheme.colorScheme.tertiaryContainer,
            MaterialTheme.colorScheme.primaryContainer,
        ),
    )
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color.Transparent,
        ),
        shape = ShapeLargeIncreased,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(brush = gradient, shape = ShapeLargeIncreased)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(
                        color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.12f),
                        shape = androidx.compose.foundation.shape.CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.EmojiEvents,
                    contentDescription = null,
                    modifier = Modifier.size(28.dp),
                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Achievement Unlocked!",
                    style = RallyTraxTypeEmphasized.labelSmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f),
                )
                Text(
                    text = achievement.title,
                    style = RallyTraxTypeEmphasized.titleMedium,
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
        animationSpec = androidx.compose.animation.core.spring(
            dampingRatio = 0.7f, stiffness = 420f,
        ),
        label = "fab_rotation",
    )
    // Press state drives the squircle "blob" morph when not expanded.
    val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    // Corner radius morphs between: pressed→10dp (pinched), expanded→50% (circle), else 20dp (squircle).
    val cornerDp by animateFloatAsState(
        targetValue = when {
            expanded -> 28f
            pressed -> 10f
            else -> 20f
        },
        animationSpec = androidx.compose.animation.core.spring(
            dampingRatio = 0.55f, stiffness = 260f,
        ),
        label = "fab_corner",
    )

    val items = listOf(
        Triple(Icons.Filled.FiberManualRecord, "Record", onRecord),
        Triple(Icons.Filled.PlayArrow, "Replay", onReplay),
        Triple(Icons.Filled.FileOpen, "Import GPX", onImportGpx),
        Triple(Icons.Filled.LocalGasStation, "Log fill-up", onLogFillUp),
    )

    Column(
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Staggered per-item entrance: index 0 fires last (bottom of stack) so
        // it appears closest to the FAB first. Reversing visually stacks bottom-up.
        items.forEachIndexed { index, (icon, label, handler) ->
            val delayMs = (items.size - 1 - index) * 50
            FabMenuItem(
                icon = icon,
                label = label,
                visible = expanded,
                delayMs = delayMs,
                onClick = handler,
            )
        }

        FloatingActionButton(
            onClick = onToggle,
            shape = androidx.compose.foundation.shape.RoundedCornerShape(cornerDp.dp),
            containerColor = if (expanded) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.primaryContainer
            },
            contentColor = if (expanded) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onPrimaryContainer
            },
            interactionSource = interactionSource,
            elevation = androidx.compose.material3.FloatingActionButtonDefaults.elevation(
                defaultElevation = 8.dp,
                pressedElevation = 4.dp,
            ),
        ) {
            Icon(
                imageVector = Icons.Filled.Add,
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
    visible: Boolean,
    delayMs: Int,
    onClick: () -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(
            animationSpec = androidx.compose.animation.core.tween(
                durationMillis = 220, delayMillis = delayMs,
            ),
        ) + slideInVertically(
            animationSpec = androidx.compose.animation.core.spring(
                dampingRatio = 0.55f, stiffness = 320f,
            ),
            initialOffsetY = { it / 2 },
        ) + scaleIn(
            animationSpec = androidx.compose.animation.core.spring(
                dampingRatio = 0.55f, stiffness = 320f,
            ),
            initialScale = 0.7f,
        ),
        exit = fadeOut(RallyTraxMotion.fastEffects()) +
            scaleOut(RallyTraxMotion.fastEffects(), targetScale = 0.7f),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                ),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(999.dp),
            ) {
                Text(
                    text = label,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            SmallFloatingActionButton(
                onClick = onClick,
                shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ) {
                Icon(imageVector = icon, contentDescription = label)
            }
        }
    }
}

package com.rallytrax.app.ui.library

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberSwipeToDismissBoxState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun LibraryScreen(
    onTrackClick: (String) -> Unit = {},
    onReplayTrack: (String) -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    isSignedIn: Boolean = false,
    userPhotoUrl: String? = null,
    onProfileClick: () -> Unit = {},
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val preferences by viewModel.preferences.collectAsStateWithLifecycle()
    val pendingDeletes by viewModel.pendingDeletes.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    var showSortMenu by remember { mutableStateOf(false) }
    var showTagFilter by remember { mutableStateOf(false) }
    var showAdvancedFilters by remember { mutableStateOf(false) }

    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        uri?.let { viewModel.importGpx(context, it) }
    }

    // Snackbar messages
    LaunchedEffect(Unit) {
        viewModel.snackbarMessage.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    // Undo delete snackbar — item disappears immediately, deletion on snackbar expiry
    val latestPending = pendingDeletes.lastOrNull()
    LaunchedEffect(latestPending?.id) {
        latestPending?.let { track ->
            snackbarHostState.currentSnackbarData?.dismiss() // dismiss any prior snackbar
            val result = snackbarHostState.showSnackbar(
                message = "${track.name} deleted",
                actionLabel = "Undo",
                duration = SnackbarDuration.Short,
            )
            when (result) {
                SnackbarResult.ActionPerformed -> viewModel.cancelDeleteTrack()
                SnackbarResult.Dismissed -> viewModel.confirmDeleteTrack()
            }
        }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    if (uiState.isMultiSelectMode) {
                        Text("${uiState.selectedTrackIds.size} selected")
                    } else {
                        Text("Routes")
                    }
                },
                scrollBehavior = scrollBehavior,
                actions = {
                    if (uiState.isMultiSelectMode) {
                        IconButton(onClick = { viewModel.deleteSelectedTracks() }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete selected")
                        }
                        IconButton(onClick = { viewModel.exitMultiSelectMode() }) {
                            Icon(Icons.Filled.Close, contentDescription = "Cancel selection")
                        }
                    } else {
                        IconButton(onClick = {
                            importLauncher.launch(arrayOf("application/gpx+xml", "application/xml", "text/xml", "*/*"))
                        }) {
                            Icon(Icons.Filled.FileOpen, contentDescription = "Import GPX")
                        }

                        // Sort button
                        Box {
                            IconButton(onClick = { showSortMenu = true }) {
                                Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = "Sort")
                            }
                            DropdownMenu(
                                expanded = showSortMenu,
                                onDismissRequest = { showSortMenu = false },
                            ) {
                                SortOption.entries.forEach { option ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = option.label,
                                                fontWeight = if (option == uiState.sortOption) {
                                                    FontWeight.Bold
                                                } else {
                                                    FontWeight.Normal
                                                },
                                            )
                                        },
                                        onClick = {
                                            viewModel.updateSortOption(option)
                                            showSortMenu = false
                                        },
                                    )
                                }
                            }
                        }

                        // Filter button with badge
                        BadgedBox(
                            badge = {
                                if (uiState.activeFilterCount > 0) {
                                    Badge { Text("${uiState.activeFilterCount}") }
                                }
                            },
                        ) {
                            IconButton(onClick = { showTagFilter = !showTagFilter }) {
                                Icon(Icons.Filled.FilterList, contentDescription = "Filter")
                            }
                        }

                        // Profile avatar
                        if (isSignedIn) {
                            IconButton(onClick = onProfileClick) {
                                if (userPhotoUrl != null) {
                                    coil.compose.AsyncImage(
                                        model = userPhotoUrl,
                                        contentDescription = "Profile",
                                        modifier = Modifier
                                            .size(28.dp)
                                            .clip(androidx.compose.foundation.shape.CircleShape),
                                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Filled.AccountCircle,
                                        contentDescription = "Profile",
                                        modifier = Modifier.size(28.dp),
                                    )
                                }
                            }
                        }

                        // Settings gear icon
                        IconButton(onClick = onNavigateToSettings) {
                            Icon(Icons.Filled.Settings, contentDescription = "Settings")
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            // Search bar
            TextField(
                value = uiState.searchQuery,
                onValueChange = { viewModel.updateSearchQuery(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("Search routes...") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (uiState.searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.updateSearchQuery("") }) {
                            Icon(Icons.Filled.Close, contentDescription = "Clear")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(28.dp),
                colors = TextFieldDefaults.colors(
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
            )

            // Active filter indicator
            if (uiState.activeFilterCount > 0) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "${uiState.activeFilterCount} filter${if (uiState.activeFilterCount > 1) "s" else ""} active",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    TextButton(onClick = { viewModel.clearAllFilters() }) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Clear all")
                    }
                }
            }

            // Filter chip bar
            LibraryFilterChipBar(
                uiState = uiState,
                onToggleDifficulty = { viewModel.toggleDifficultyFilter(it) },
                onToggleSurface = { viewModel.toggleSurfaceFilter(it) },
                onToggleRouteType = { viewModel.toggleRouteTypeFilter(it) },
                onNearMeClick = { /* TODO: location permission + near me filter */ },
                onMoreFiltersClick = { showAdvancedFilters = true },
                onClearAllFilters = { viewModel.clearAllFilters() },
            )

            // Tag filter chips
            if (showTagFilter && uiState.availableTags.isNotEmpty()) {
                FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    uiState.availableTags.forEach { tag ->
                        FilterChip(
                            selected = tag in uiState.selectedTags,
                            onClick = { viewModel.toggleTag(tag) },
                            label = { Text(tag) },
                        )
                    }
                }
            }

            val visibleTracks = remember(uiState.tracks, pendingDeletes) {
                val pendingIds = pendingDeletes.map { it.id }.toSet()
                if (pendingIds.isNotEmpty()) uiState.tracks.filter { it.id !in pendingIds } else uiState.tracks
            }

            if (visibleTracks.isEmpty()) {
                // Empty state
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = if (uiState.searchQuery.isNotEmpty() || uiState.activeFilterCount > 0) "No routes found" else "No routes yet",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (uiState.searchQuery.isNotEmpty() || uiState.activeFilterCount > 0) {
                                "Try a different search or adjust filters"
                            } else {
                                "Import a GPX or KML file to add a route"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    items(
                        items = visibleTracks,
                        key = { it.id },
                    ) { track ->
                        val dismissState = rememberSwipeToDismissBoxState()

                        LaunchedEffect(dismissState.currentValue) {
                            when (dismissState.currentValue) {
                                SwipeToDismissBoxValue.EndToStart -> {
                                    viewModel.requestDeleteTrack(track)
                                    dismissState.snapTo(SwipeToDismissBoxValue.Settled)
                                }
                                SwipeToDismissBoxValue.StartToEnd -> {
                                    onReplayTrack(track.id)
                                    dismissState.snapTo(SwipeToDismissBoxValue.Settled)
                                }
                                else -> {}
                            }
                        }

                        if (!uiState.isMultiSelectMode) {
                            SwipeToDismissBox(
                                state = dismissState,
                                backgroundContent = {
                                    val bgColor by animateColorAsState(
                                        when (dismissState.targetValue) {
                                            SwipeToDismissBoxValue.EndToStart -> MaterialTheme.colorScheme.errorContainer
                                            SwipeToDismissBoxValue.StartToEnd -> MaterialTheme.colorScheme.primaryContainer
                                            else -> Color.Transparent
                                        },
                                        label = "swipe_bg",
                                    )
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(bgColor),
                                    ) {
                                        // Replay icon (swipe right)
                                        Icon(
                                            Icons.Filled.PlayArrow,
                                            contentDescription = "Replay",
                                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier
                                                .align(Alignment.CenterStart)
                                                .padding(start = 24.dp),
                                        )
                                        // Delete icon (swipe left)
                                        Icon(
                                            Icons.Filled.Delete,
                                            contentDescription = "Delete",
                                            tint = MaterialTheme.colorScheme.onErrorContainer,
                                            modifier = Modifier
                                                .align(Alignment.CenterEnd)
                                                .padding(end = 24.dp),
                                        )
                                    }
                                },
                                enableDismissFromStartToEnd = true,
                                modifier = Modifier.animateItem(),
                            ) {
                                EnhancedTrackListItem(
                                    track = track,
                                    isSelected = track.id in uiState.selectedTrackIds,
                                    isMultiSelectMode = false,
                                    unitSystem = preferences.unitSystem,
                                    onClick = { onTrackClick(track.id) },
                                    onLongClick = { viewModel.toggleMultiSelect(track.id) },
                                    attemptCount = uiState.attemptCounts[track.name] ?: 1,
                                )
                            }
                        } else {
                            EnhancedTrackListItem(
                                track = track,
                                isSelected = track.id in uiState.selectedTrackIds,
                                isMultiSelectMode = true,
                                unitSystem = preferences.unitSystem,
                                onClick = { viewModel.toggleMultiSelect(track.id) },
                                onLongClick = { viewModel.toggleMultiSelect(track.id) },
                                modifier = Modifier.animateItem(),
                                attemptCount = uiState.attemptCounts[track.name] ?: 1,
                            )
                        }
                    }
                }
            }
        }
    }

    // Advanced filter bottom sheet
    if (showAdvancedFilters) {
        LibraryAdvancedFilterSheet(
            distanceRange = uiState.distanceRange,
            elevationRange = uiState.elevationRange,
            durationRange = uiState.durationRange,
            maxDistance = uiState.maxDistance,
            maxElevation = uiState.maxElevation,
            maxDuration = uiState.maxDuration,
            unitSystem = preferences.unitSystem,
            onDistanceRangeChange = { viewModel.updateDistanceRange(it) },
            onElevationRangeChange = { viewModel.updateElevationRange(it) },
            onDurationRangeChange = { viewModel.updateDurationRange(it) },
            onDismiss = { showAdvancedFilters = false },
            onReset = {
                viewModel.updateDistanceRange(null)
                viewModel.updateElevationRange(null)
                viewModel.updateDurationRange(null)
            },
        )
    }
}

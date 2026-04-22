package com.rallytrax.app.ui.stints

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberSwipeToDismissBoxState
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
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rallytrax.app.data.local.dao.LatLonSpeedProjection
import com.rallytrax.app.data.local.entity.TrackEntity
import com.rallytrax.app.data.preferences.UnitSystem
import com.rallytrax.app.ui.components.ShimmerLoadingList
import com.rallytrax.app.ui.components.ShimmerPlaceholder
import com.rallytrax.app.ui.components.TrackThumbnail
import com.rallytrax.app.ui.library.SortOption
import com.rallytrax.app.ui.theme.RallyTraxTypeEmphasized
import com.rallytrax.app.ui.theme.ShapeFullRound
import com.rallytrax.app.ui.theme.ShapeLargeIncreased
import com.rallytrax.app.ui.trips.TripSuggestionCard
import com.rallytrax.app.ui.trips.TripSuggestionViewModel
import com.rallytrax.app.util.formatDate
import com.rallytrax.app.util.formatDistance
import com.rallytrax.app.util.formatElapsedTime
import com.rallytrax.app.util.formatSpeed
import com.rallytrax.app.util.speedUnit

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun StintsScreen(
    onTrackClick: (String) -> Unit = {},
    onReplayTrack: (String) -> Unit = {},
    onVehicleClick: (String) -> Unit = {},
    onBack: () -> Unit = {},
    viewModel: StintsViewModel = hiltViewModel(),
    suggestionViewModel: TripSuggestionViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val suggestionState by suggestionViewModel.uiState.collectAsStateWithLifecycle()
    val preferences by viewModel.preferences.collectAsStateWithLifecycle()
    val pendingDeletes by viewModel.pendingDeletes.collectAsStateWithLifecycle()
    val thumbnails by viewModel.thumbnails.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    var showSortMenu by remember { mutableStateOf(false) }
    var showTagFilter by remember { mutableStateOf(false) }

    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    LaunchedEffect(Unit) {
        viewModel.snackbarMessage.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    LaunchedEffect(Unit) {
        suggestionViewModel.snackbarMessage.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    val latestPending = pendingDeletes.lastOrNull()
    LaunchedEffect(latestPending?.id) {
        latestPending?.let { track ->
            snackbarHostState.currentSnackbarData?.dismiss()
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
                        Text("My Stints")
                    }
                },
                navigationIcon = {
                    if (!uiState.isMultiSelectMode) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
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

                        if (uiState.availableTags.isNotEmpty()) {
                            IconButton(onClick = { showTagFilter = !showTagFilter }) {
                                Icon(Icons.Filled.FilterList, contentDescription = "Filter by tag")
                            }
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
            TextField(
                value = uiState.searchQuery,
                onValueChange = { viewModel.updateSearchQuery(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("Search stints...") },
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

            if (uiState.isLoading) {
                ShimmerLoadingList(modifier = Modifier.fillMaxSize())
            } else {
                val visibleTracks = remember(uiState.tracks, pendingDeletes) {
                    val pendingIds = pendingDeletes.map { it.id }.toSet()
                    if (pendingIds.isNotEmpty()) uiState.tracks.filter { it.id !in pendingIds } else uiState.tracks
                }

                if (visibleTracks.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = if (uiState.searchQuery.isNotEmpty()) "No stints found" else "No stints yet",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = if (uiState.searchQuery.isNotEmpty()) {
                                    "Try a different search"
                                } else {
                                    "Record your first drive to create a stint"
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
                    // Trip suggestion cards
                    if (suggestionState.suggestions.isNotEmpty()) {
                        item(key = "suggestion_header") {
                            Text(
                                text = "Trip Suggestions",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            )
                        }
                        items(
                            items = suggestionState.suggestions,
                            key = { "suggestion_${it.id}" },
                        ) { suggestion ->
                            TripSuggestionCard(
                                suggestion = suggestion,
                                onAccept = { suggestionViewModel.acceptSuggestion(it) },
                                onDismiss = { suggestionViewModel.dismissSuggestion(it) },
                                unitSystem = preferences.unitSystem,
                            )
                        }
                        item(key = "suggestion_divider") {
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }

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
                                        Icon(
                                            Icons.Filled.PlayArrow,
                                            contentDescription = "Replay",
                                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier
                                                .align(Alignment.CenterStart)
                                                .padding(start = 24.dp),
                                        )
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
                                StintListItem(
                                    track = track,
                                    vehicleName = track.vehicleId?.let { uiState.vehicleNames[it] },
                                    isSelected = track.id in uiState.selectedTrackIds,
                                    isMultiSelectMode = false,
                                    thumbnailPoints = thumbnails[track.id],
                                    unitSystem = preferences.unitSystem,
                                    onClick = { onTrackClick(track.id) },
                                    onLongClick = { viewModel.toggleMultiSelect(track.id) },
                                    onVehicleClick = { track.vehicleId?.let { onVehicleClick(it) } },
                                )
                            }
                        } else {
                            StintListItem(
                                track = track,
                                vehicleName = track.vehicleId?.let { uiState.vehicleNames[it] },
                                isSelected = track.id in uiState.selectedTrackIds,
                                isMultiSelectMode = true,
                                thumbnailPoints = thumbnails[track.id],
                                unitSystem = preferences.unitSystem,
                                onClick = { viewModel.toggleMultiSelect(track.id) },
                                onLongClick = { viewModel.toggleMultiSelect(track.id) },
                                onVehicleClick = { track.vehicleId?.let { onVehicleClick(it) } },
                                modifier = Modifier.animateItem(),
                            )
                        }
                    }
                }
            }

            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun StintListItem(
    track: TrackEntity,
    vehicleName: String?,
    isSelected: Boolean,
    isMultiSelectMode: Boolean,
    thumbnailPoints: List<LatLonSpeedProjection>?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onVehicleClick: () -> Unit,
    modifier: Modifier = Modifier,
    unitSystem: UnitSystem = UnitSystem.METRIC,
) {
    val containerColor = if (isSelected) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
    } else {
        MaterialTheme.colorScheme.surface
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(ShapeLargeIncreased)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            ),
        shape = ShapeLargeIncreased,
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
        ) {
            when {
                thumbnailPoints == null -> ShimmerPlaceholder(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(96.dp),
                    shape = MaterialTheme.shapes.medium,
                )
                thumbnailPoints.size >= 2 -> TrackThumbnail(
                    track = track,
                    points = thumbnailPoints,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (thumbnailPoints == null || thumbnailPoints.size >= 2) {
                Spacer(modifier = Modifier.height(12.dp))
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Text(
                    text = track.name,
                    style = RallyTraxTypeEmphasized.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = formatDate(track.recordedAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Hero primary metric: distance
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = formatDistance(track.distanceMeters, unitSystem),
                    style = RallyTraxTypeEmphasized.headlineMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Secondary metrics
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                StintStatChip(
                    label = "Duration",
                    value = formatElapsedTime(track.durationMs),
                )
                if (track.avgSpeedMps > 0) {
                    StintStatChip(
                        label = "Avg speed",
                        value = "${formatSpeed(track.avgSpeedMps, unitSystem)} ${speedUnit(unitSystem)}",
                    )
                }
            }

            if (vehicleName != null) {
                Spacer(modifier = Modifier.height(8.dp))
                AssistChip(
                    onClick = onVehicleClick,
                    label = {
                        Text(
                            text = vehicleName,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    leadingIcon = {
                        Icon(
                            Icons.Filled.DirectionsCar,
                            contentDescription = null,
                            modifier = Modifier.height(18.dp),
                        )
                    },
                    colors = AssistChipDefaults.assistChipColors(
                        leadingIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                )
            }

            if (track.tags.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    track.tags.split(",").map { it.trim() }.filter { it.isNotBlank() }.forEach { tag ->
                        Text(
                            text = tag,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .background(
                                    MaterialTheme.colorScheme.primaryContainer,
                                    MaterialTheme.shapes.small,
                                )
                                .padding(horizontal = 8.dp, vertical = 2.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StintStatChip(label: String, value: String) {
    Column {
        Text(
            text = value,
            style = RallyTraxTypeEmphasized.bodyMedium,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

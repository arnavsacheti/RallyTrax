package com.rallytrax.app.ui.library

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.automirrored.filled.Sort
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rallytrax.app.data.local.entity.TrackEntity
import com.rallytrax.app.data.preferences.UnitSystem
import com.rallytrax.app.util.formatDate
import com.rallytrax.app.util.formatDistance
import com.rallytrax.app.util.formatElapsedTime
import com.rallytrax.app.util.formatSpeed
import com.rallytrax.app.util.speedUnit

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun LibraryScreen(
    onTrackClick: (String) -> Unit = {},
    onReplayTrack: (String) -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val preferences by viewModel.preferences.collectAsStateWithLifecycle()
    val pendingDelete by viewModel.pendingDelete.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    var showSortMenu by remember { mutableStateOf(false) }
    var showTagFilter by remember { mutableStateOf(false) }

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
    LaunchedEffect(pendingDelete) {
        pendingDelete?.let { track ->
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
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    if (uiState.isMultiSelectMode) {
                        Text("${uiState.selectedTrackIds.size} selected")
                    } else {
                        Text("Library")
                    }
                },
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

                        // Tag filter button
                        if (uiState.availableTags.isNotEmpty()) {
                            IconButton(onClick = { showTagFilter = !showTagFilter }) {
                                Icon(Icons.Filled.FilterList, contentDescription = "Filter by tag")
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
                placeholder = { Text("Search tracks...") },
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

            val visibleTracks = remember(uiState.tracks, pendingDelete) {
                val pendingId = pendingDelete?.id
                if (pendingId != null) uiState.tracks.filter { it.id != pendingId } else uiState.tracks
            }

            if (visibleTracks.isEmpty()) {
                // Empty state
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = if (uiState.searchQuery.isNotEmpty()) "No tracks found" else "No tracks yet",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (uiState.searchQuery.isNotEmpty()) {
                                "Try a different search"
                            } else {
                                "Record your first drive or import a GPX file"
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
                                TrackListItem(
                                    track = track,
                                    isSelected = track.id in uiState.selectedTrackIds,
                                    isMultiSelectMode = false,
                                    unitSystem = preferences.unitSystem,
                                    onClick = { onTrackClick(track.id) },
                                    onLongClick = { viewModel.toggleMultiSelect(track.id) },
                                )
                            }
                        } else {
                            TrackListItem(
                                track = track,
                                isSelected = track.id in uiState.selectedTrackIds,
                                isMultiSelectMode = true,
                                unitSystem = preferences.unitSystem,
                                onClick = { viewModel.toggleMultiSelect(track.id) },
                                onLongClick = { viewModel.toggleMultiSelect(track.id) },
                                modifier = Modifier.animateItem(),
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TrackListItem(
    track: TrackEntity,
    isSelected: Boolean,
    isMultiSelectMode: Boolean,
    unitSystem: UnitSystem = UnitSystem.METRIC,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
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
            .clip(RoundedCornerShape(16.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
        ) {
            // Name and date
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Text(
                    text = track.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
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

            Spacer(modifier = Modifier.height(8.dp))

            // Stats row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                TrackStatChip(
                    label = "Distance",
                    value = formatDistance(track.distanceMeters, unitSystem),
                )
                TrackStatChip(
                    label = "Duration",
                    value = formatElapsedTime(track.durationMs),
                )
                if (track.avgSpeedMps > 0) {
                    TrackStatChip(
                        label = "Avg",
                        value = "${formatSpeed(track.avgSpeedMps, unitSystem)} ${speedUnit(unitSystem)}",
                    )
                }
            }

            // Tags
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
                                    RoundedCornerShape(4.dp),
                                )
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TrackStatChip(label: String, value: String) {
    Column {
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


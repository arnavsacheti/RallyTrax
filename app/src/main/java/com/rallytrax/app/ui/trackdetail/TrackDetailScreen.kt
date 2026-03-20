package com.rallytrax.app.ui.trackdetail

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import com.rallytrax.app.data.local.entity.NoteType
import com.rallytrax.app.ui.map.MapProvider
import com.rallytrax.app.ui.map.OsmMapView
import com.rallytrax.app.ui.map.OsmMarkerData
import com.rallytrax.app.ui.map.OsmPolylineData
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import com.rallytrax.app.data.local.entity.PaceNoteEntity
import com.rallytrax.app.recording.LatLng
import com.rallytrax.app.util.formatDateTime
import com.rallytrax.app.data.preferences.UnitSystem
import com.rallytrax.app.util.formatDistance
import com.rallytrax.app.util.formatElapsedTime
import com.rallytrax.app.util.formatElevation
import com.rallytrax.app.util.formatSpeed
import com.rallytrax.app.util.speedUnit
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TrackDetailScreen(
    onBack: () -> Unit,
    onReplay: (String) -> Unit = {},
    viewModel: TrackDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val preferences by viewModel.preferences.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showAddTagDialog by remember { mutableStateOf(false) }

    val pagerState = rememberPagerState(initialPage = 0, pageCount = { 2 })
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        viewModel.snackbarMessage.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(uiState.track?.name ?: "Track Detail") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
                actions = {
                    if (uiState.paceNotes.isNotEmpty()) {
                        IconButton(
                            onClick = {
                                uiState.track?.let { onReplay(it.id) }
                            },
                        ) {
                            Icon(
                                Icons.Filled.PlayArrow,
                                contentDescription = "Replay",
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                    IconButton(onClick = { viewModel.exportGpx(context) }) {
                        Icon(Icons.Filled.Share, contentDescription = "Export & Share")
                    }
                    IconButton(onClick = { showDeleteDialog = true }) {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = "Delete",
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        if (uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
                // Map (always visible at top)
                val points = uiState.polylinePoints

                if (points.isNotEmpty()) {
                    if (MapProvider.useGoogleMaps) {
                        val cameraPositionState = rememberCameraPositionState()
                        val boundsBuilder = LatLngBounds.builder()
                        points.forEach { p ->
                            boundsBuilder.include(
                                com.google.android.gms.maps.model.LatLng(p.latitude, p.longitude)
                            )
                        }
                        val bounds = boundsBuilder.build()
                        cameraPositionState.position =
                            CameraPosition.fromLatLngZoom(bounds.center, 14f)

                        GoogleMap(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(300.dp),
                            cameraPositionState = cameraPositionState,
                            uiSettings = MapUiSettings(
                                zoomControlsEnabled = true,
                                scrollGesturesEnabled = true,
                                zoomGesturesEnabled = true,
                            ),
                        ) {
                            if (points.size >= 2) {
                                Polyline(
                                    points = points.map {
                                        com.google.android.gms.maps.model.LatLng(
                                            it.latitude,
                                            it.longitude,
                                        )
                                    },
                                    color = Color(0xFF1A73E8),
                                    width = 10f,
                                )
                            }

                            if (pagerState.currentPage == 1) {
                                uiState.paceNotes.forEach { note ->
                                    val noteLatLng = findPointForNote(note, uiState.polylinePoints, points)
                                    if (noteLatLng != null) {
                                        val hue = when (note.noteType) {
                                            NoteType.LEFT -> BitmapDescriptorFactory.HUE_BLUE
                                            NoteType.RIGHT -> BitmapDescriptorFactory.HUE_GREEN
                                            NoteType.HAIRPIN_LEFT, NoteType.HAIRPIN_RIGHT -> BitmapDescriptorFactory.HUE_RED
                                            NoteType.CREST -> BitmapDescriptorFactory.HUE_YELLOW
                                            NoteType.DIP -> BitmapDescriptorFactory.HUE_ORANGE
                                            NoteType.STRAIGHT -> BitmapDescriptorFactory.HUE_VIOLET
                                        }
                                        Marker(
                                            state = MarkerState(
                                                position = com.google.android.gms.maps.model.LatLng(
                                                    noteLatLng.latitude,
                                                    noteLatLng.longitude,
                                                )
                                            ),
                                            title = note.callText,
                                            icon = BitmapDescriptorFactory.defaultMarker(hue),
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        // OSM fallback
                        val lats = points.map { it.latitude }
                        val lngs = points.map { it.longitude }
                        val fitBounds = BoundingBox(
                            lats.max(), lngs.max(),
                            lats.min(), lngs.min(),
                        )

                        val osmPolylines = if (points.size >= 2) {
                            listOf(
                                OsmPolylineData(
                                    points = points.map { GeoPoint(it.latitude, it.longitude) },
                                    width = 10f,
                                ),
                            )
                        } else {
                            emptyList()
                        }

                        val osmMarkers = if (pagerState.currentPage == 1) {
                            uiState.paceNotes.mapNotNull { note ->
                                val noteLatLng = findPointForNote(note, uiState.polylinePoints, points)
                                noteLatLng?.let {
                                    OsmMarkerData(
                                        position = GeoPoint(it.latitude, it.longitude),
                                        title = note.callText,
                                    )
                                }
                            }
                        } else {
                            emptyList()
                        }

                        OsmMapView(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(300.dp),
                            fitBounds = fitBounds,
                            polylines = osmPolylines,
                            markers = osmMarkers,
                            zoomControlsEnabled = true,
                        )
                    }
                }

                // Tabs
                TabRow(
                    selectedTabIndex = pagerState.currentPage,
                ) {
                    Tab(
                        selected = pagerState.currentPage == 0,
                        onClick = { coroutineScope.launch { pagerState.animateScrollToPage(0) } },
                        text = { Text("Details") },
                    )
                    Tab(
                        selected = pagerState.currentPage == 1,
                        onClick = { coroutineScope.launch { pagerState.animateScrollToPage(1) } },
                        text = {
                            Text(
                                if (uiState.paceNotes.isNotEmpty()) {
                                    "Pace Notes (${uiState.paceNotes.size})"
                                } else {
                                    "Pace Notes"
                                }
                            )
                        },
                    )
                }

                // Tab content
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                ) { page ->
                    when (page) {
                        0 -> DetailsTab(
                            uiState = uiState,
                            viewModel = viewModel,
                            context = context,
                            unitSystem = preferences.unitSystem,
                            onShowAddTagDialog = { showAddTagDialog = true },
                        )
                        1 -> PaceNotesTab(
                            uiState = uiState,
                            viewModel = viewModel,
                            unitSystem = preferences.unitSystem,
                        )
                    }
                }
            }
        }
    }

    // Delete confirmation dialog
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Track?") },
            text = { Text("This will permanently delete this track and all its data.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        viewModel.deleteTrack { onBack() }
                    },
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            },
        )
    }

    // Add tag dialog
    if (showAddTagDialog) {
        var tagText by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddTagDialog = false },
            title = { Text("Add Tag") },
            text = {
                OutlinedTextField(
                    value = tagText,
                    onValueChange = { tagText = it },
                    label = { Text("Tag name") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            if (tagText.isNotBlank()) {
                                viewModel.addTag(tagText)
                                showAddTagDialog = false
                            }
                        },
                    ),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (tagText.isNotBlank()) {
                            viewModel.addTag(tagText)
                            showAddTagDialog = false
                        }
                    },
                ) {
                    Text("Add")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddTagDialog = false }) {
                    Text("Cancel")
                }
            },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DetailsTab(
    uiState: TrackDetailUiState,
    viewModel: TrackDetailViewModel,
    context: android.content.Context,
    unitSystem: UnitSystem = UnitSystem.METRIC,
    onShowAddTagDialog: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(top = 16.dp),
    ) {
        // Stats card
        uiState.track?.let { track ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Summary",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = formatDateTime(track.recordedAt),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        DetailStatItem("Distance", formatDistance(track.distanceMeters, unitSystem))
                        DetailStatItem("Duration", formatElapsedTime(track.durationMs))
                    }

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 12.dp),
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        DetailStatItem(
                            "Avg Speed",
                            "${formatSpeed(track.avgSpeedMps, unitSystem)} ${speedUnit(unitSystem)}",
                        )
                        DetailStatItem(
                            "Max Speed",
                            "${formatSpeed(track.maxSpeedMps, unitSystem)} ${speedUnit(unitSystem)}",
                        )
                    }

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 12.dp),
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        DetailStatItem(
                            "Elevation Gain",
                            formatElevation(track.elevationGainM, unitSystem),
                        )
                        DetailStatItem(
                            "Points",
                            "${uiState.polylinePoints.size}",
                        )
                    }
                }
            }
        }

        // Elevation profile chart
        if (uiState.elevationProfile.size >= 2) {
            Spacer(modifier = Modifier.height(16.dp))
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Elevation Profile",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    ElevationChart(
                        data = uiState.elevationProfile,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(150.dp),
                    )

                    // Axis labels
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = "0 km",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = formatDistance(uiState.elevationProfile.last().distanceFromStart, unitSystem),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        // Tags section
        Spacer(modifier = Modifier.height(16.dp))
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
            ),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Tags",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    AssistChip(
                        onClick = onShowAddTagDialog,
                        label = { Text("Add") },
                        leadingIcon = {
                            Icon(
                                Icons.Filled.Add,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                        },
                    )
                }

                if (uiState.tags.isEmpty()) {
                    Text(
                        text = "No tags yet",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                } else {
                    Spacer(modifier = Modifier.height(8.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        uiState.tags.forEach { tag ->
                            InputChip(
                                selected = false,
                                onClick = { },
                                label = { Text(tag) },
                                trailingIcon = {
                                    IconButton(
                                        onClick = { viewModel.removeTag(tag) },
                                        modifier = Modifier.size(16.dp),
                                    ) {
                                        Icon(
                                            Icons.Filled.Close,
                                            contentDescription = "Remove tag",
                                            modifier = Modifier.size(14.dp),
                                        )
                                    }
                                },
                                colors = InputChipDefaults.inputChipColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                ),
                            )
                        }
                    }
                }
            }
        }

        // Export button
        Spacer(modifier = Modifier.height(16.dp))
        FilledTonalButton(
            onClick = { viewModel.exportGpx(context) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        ) {
            Icon(Icons.Filled.Share, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Export GPX & Share")
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun PaceNotesTab(
    uiState: TrackDetailUiState,
    viewModel: TrackDetailViewModel,
    unitSystem: UnitSystem = UnitSystem.METRIC,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Sensitivity selector + regenerate
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
            ),
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = "Sensitivity",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val labels = listOf("Low", "Medium", "High")
                    labels.forEachIndexed { index, label ->
                        FilterChip(
                            selected = uiState.selectedSensitivity == index,
                            onClick = { viewModel.regeneratePaceNotes(index) },
                            label = { Text(label) },
                            enabled = !uiState.isGeneratingNotes,
                        )
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    IconButton(
                        onClick = { viewModel.regeneratePaceNotes(uiState.selectedSensitivity) },
                        enabled = !uiState.isGeneratingNotes,
                    ) {
                        if (uiState.isGeneratingNotes) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp))
                        } else {
                            Icon(
                                Icons.Filled.Refresh,
                                contentDescription = "Regenerate",
                            )
                        }
                    }
                }
            }
        }

        // Pace notes list
        if (uiState.paceNotes.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "No pace notes yet",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Tap a sensitivity level to generate pace notes",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
            ) {
                items(uiState.paceNotes) { note ->
                    PaceNoteItem(note = note, unitSystem = unitSystem)
                }
            }
        }
    }
}

@Composable
private fun PaceNoteItem(note: PaceNoteEntity, unitSystem: UnitSystem = UnitSystem.METRIC) {
    val iconColor = when (note.noteType) {
        NoteType.LEFT -> Color(0xFF1A73E8) // Blue
        NoteType.RIGHT -> Color(0xFF34A853) // Green
        NoteType.HAIRPIN_LEFT, NoteType.HAIRPIN_RIGHT -> Color(0xFFEA4335) // Red
        NoteType.CREST -> Color(0xFFFBBC04) // Yellow
        NoteType.DIP -> Color(0xFFFF6D00) // Orange
        NoteType.STRAIGHT -> Color(0xFF9C27B0) // Purple
    }

    val typeIcon = when (note.noteType) {
        NoteType.LEFT -> "L"
        NoteType.RIGHT -> "R"
        NoteType.HAIRPIN_LEFT -> "HL"
        NoteType.HAIRPIN_RIGHT -> "HR"
        NoteType.CREST -> "CR"
        NoteType.DIP -> "DP"
        NoteType.STRAIGHT -> "ST"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Type indicator
        Card(
            modifier = Modifier.size(42.dp),
            shape = RoundedCornerShape(8.dp),
            colors = CardDefaults.cardColors(containerColor = iconColor.copy(alpha = 0.15f)),
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = typeIcon,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = iconColor,
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = note.callText,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = formatDistance(note.distanceFromStart, unitSystem),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (note.severity in 1..6) {
            Card(
                shape = RoundedCornerShape(6.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                ),
            ) {
                Text(
                    text = "${note.severity}",
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
            }
        }
    }
}

/** Find the LatLng for a pace note from the polyline points using the point index */
private fun findPointForNote(
    note: PaceNoteEntity,
    polylinePoints: List<LatLng>,
    allPoints: List<LatLng>,
): LatLng? {
    val idx = note.pointIndex
    return if (idx in polylinePoints.indices) {
        polylinePoints[idx]
    } else if (polylinePoints.isNotEmpty()) {
        // Fallback: find closest by distance from start
        polylinePoints.lastOrNull()
    } else {
        null
    }
}

@Composable
private fun ElevationChart(
    data: List<ElevationPoint>,
    modifier: Modifier = Modifier,
) {
    val lineColor = Color(0xFF1A73E8)
    val fillColor = Color(0x331A73E8)

    Canvas(modifier = modifier) {
        if (data.size < 2) return@Canvas

        val maxDist = data.last().distanceFromStart
        val minEle = data.minOf { it.elevation }
        val maxEle = data.maxOf { it.elevation }
        val eleRange = (maxEle - minEle).coerceAtLeast(1.0)

        val paddingTop = 8f
        val chartHeight = size.height - paddingTop

        fun xFor(d: Double): Float = ((d / maxDist) * size.width).toFloat()
        fun yFor(e: Double): Float =
            (paddingTop + chartHeight - ((e - minEle) / eleRange) * chartHeight).toFloat()

        // Fill path
        val fillPath = Path().apply {
            moveTo(xFor(data.first().distanceFromStart), size.height)
            lineTo(xFor(data.first().distanceFromStart), yFor(data.first().elevation))
            for (point in data) {
                lineTo(xFor(point.distanceFromStart), yFor(point.elevation))
            }
            lineTo(xFor(data.last().distanceFromStart), size.height)
            close()
        }
        drawPath(fillPath, color = fillColor)

        // Line path
        val linePath = Path().apply {
            moveTo(xFor(data.first().distanceFromStart), yFor(data.first().elevation))
            for (point in data) {
                lineTo(xFor(point.distanceFromStart), yFor(point.elevation))
            }
        }
        drawPath(linePath, color = lineColor, style = Stroke(width = 3f))
    }
}

@Composable
private fun DetailStatItem(label: String, value: String) {
    Column {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

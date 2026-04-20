package com.rallytrax.app.ui.trackdetail

import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.Canvas
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
// ButtonDefaults removed — moved to EditTrackScreen
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
// InputChip removed — moved to EditTrackScreen
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.compose.Circle
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.rememberMarkerState
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import com.rallytrax.app.data.local.entity.NoteModifier
import com.rallytrax.app.ui.components.WeatherBadge
import com.rallytrax.app.ui.recording.SensorStats
import com.rallytrax.app.data.local.entity.NoteType
import com.rallytrax.app.data.local.entity.PaceNoteEntity
import com.rallytrax.app.data.local.entity.TrackPointEntity
import com.rallytrax.app.data.preferences.UnitSystem
import com.rallytrax.app.pacenotes.SegmentMatcher
import com.rallytrax.app.recording.LatLng
import com.rallytrax.app.ui.components.GoalRing
import com.rallytrax.app.ui.components.Sparkline
import com.rallytrax.app.ui.theme.rallyTraxColors
import com.rallytrax.app.ui.map.MapProvider
import com.rallytrax.app.ui.map.OsmMapView
import com.rallytrax.app.ui.map.OsmMarkerData
import com.rallytrax.app.ui.map.OsmPolylineData
import com.rallytrax.app.ui.map.PaceNoteIconRenderer
import com.rallytrax.app.ui.map.buildColoredSegments
import com.rallytrax.app.data.analytics.GripEventDetector
import com.rallytrax.app.ui.theme.DifficultyAmber
import com.rallytrax.app.ui.theme.DifficultyOrange
import com.rallytrax.app.ui.theme.DifficultyRed
import com.rallytrax.app.ui.theme.HeatmapCold
import com.rallytrax.app.ui.theme.LayerAccel
import com.rallytrax.app.ui.theme.LayerCallout
import com.rallytrax.app.ui.theme.LayerCurvature
import com.rallytrax.app.ui.theme.LayerDecel
import com.rallytrax.app.ui.theme.LayerElevation
import com.rallytrax.app.ui.theme.LayerSpeedHigh
import com.rallytrax.app.ui.theme.LayerSpeedLow
import com.rallytrax.app.ui.theme.LayerSpeedMid
import com.rallytrax.app.ui.theme.lerpColor
import com.rallytrax.app.ui.theme.rallyTraxColors
import com.rallytrax.app.ui.theme.speedColor
import com.rallytrax.app.util.formatDateTime
import com.rallytrax.app.util.formatDistance
import com.rallytrax.app.util.formatElapsedTime
import com.rallytrax.app.util.formatElevation
import com.rallytrax.app.util.formatSpeed
import com.rallytrax.app.util.speedUnit
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TrackDetailScreen(
    onBack: () -> Unit,
    onReplay: (String) -> Unit = {},
    onEdit: (String) -> Unit = {},
    onViewAllSegments: () -> Unit = {},
    onSegmentClick: (String) -> Unit = {},
    onVehicleClick: (String) -> Unit = {},
    onNavigateToTrip: (String) -> Unit = {},
    viewModel: TrackDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val preferences by viewModel.preferences.collectAsStateWithLifecycle()
    val allVehicles by viewModel.allVehicles.collectAsStateWithLifecycle()
    val allTrips by viewModel.allTrips.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var isMapFullscreen by remember { mutableStateOf(false) }
    var showOverflowMenu by remember { mutableStateOf(false) }
    var showTripPicker by remember { mutableStateOf(false) }
    var showShareSheet by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.snackbarMessage.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    uiState.track?.let { track ->
        if (showShareSheet) {
            val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
            com.rallytrax.app.ui.share.ShareSheet(
                track = track,
                unitSystem = preferences.unitSystem,
                onAction = { action, caption ->
                    when (action) {
                        com.rallytrax.app.ui.share.ShareAction.SystemShare -> viewModel.shareActivity(context)
                        com.rallytrax.app.ui.share.ShareAction.ExportGpx -> viewModel.exportGpx(context)
                        com.rallytrax.app.ui.share.ShareAction.CopySummary -> {
                            val distKm = track.distanceMeters / 1000.0
                            val summary = buildString {
                                if (caption.isNotBlank()) appendLine(caption)
                                append("${track.name} · ")
                                append("%.1f km".format(distKm))
                                append(" · ")
                                append(formatElapsedTime(track.durationMs))
                            }
                            clipboard.setText(androidx.compose.ui.text.AnnotatedString(summary))
                        }
                    }
                },
                onDismiss = { showShareSheet = false },
            )
        }
    }

    if (showTripPicker) {
        TripPickerDialog(
            trips = allTrips,
            currentTripId = uiState.track?.tripId,
            onDismiss = { showTripPicker = false },
            onSelect = { tripId ->
                viewModel.assignTrip(tripId)
                showTripPicker = false
            },
            onClear = {
                viewModel.clearTrip()
                showTripPicker = false
            },
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(uiState.track?.name ?: "Track Detail") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (uiState.paceNotes.isNotEmpty()) {
                        IconButton(onClick = { uiState.track?.let { onReplay(it.id) } }) {
                            Icon(Icons.Filled.PlayArrow, "Replay", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                    // Three-dot overflow menu
                    Box {
                        IconButton(onClick = { showOverflowMenu = true }) {
                            Icon(Icons.Filled.MoreVert, "More options")
                        }
                        androidx.compose.material3.DropdownMenu(
                            expanded = showOverflowMenu,
                            onDismissRequest = { showOverflowMenu = false },
                        ) {
                            androidx.compose.material3.DropdownMenuItem(
                                text = { Text("Edit") },
                                onClick = {
                                    showOverflowMenu = false
                                    uiState.track?.let { onEdit(it.id) }
                                },
                                leadingIcon = { Icon(Icons.Filled.Edit, null) },
                            )
                            androidx.compose.material3.DropdownMenuItem(
                                text = { Text("Share…") },
                                onClick = {
                                    showOverflowMenu = false
                                    showShareSheet = true
                                },
                                leadingIcon = { Icon(Icons.Filled.Share, null) },
                            )
                            androidx.compose.material3.DropdownMenuItem(
                                text = { Text(if (uiState.isFetchingElevation) "Fetching elevation…" else "Fetch Elevation") },
                                onClick = {
                                    showOverflowMenu = false
                                    viewModel.fetchElevation()
                                },
                                enabled = !uiState.isFetchingElevation,
                                leadingIcon = { Icon(Icons.Filled.Refresh, null) },
                            )
                            val noteLabel = if (uiState.paceNotes.isEmpty()) "Generate Pace Notes" else "Regenerate Pace Notes"
                            androidx.compose.material3.DropdownMenuItem(
                                text = { Text(if (uiState.isGeneratingNotes) "Generating…" else noteLabel) },
                                onClick = {
                                    showOverflowMenu = false
                                    viewModel.regeneratePaceNotes()
                                },
                                enabled = !uiState.isGeneratingNotes,
                                leadingIcon = { Icon(Icons.Default.Refresh, null) },
                            )
                            val tripLabel = if (uiState.track?.tripId != null) "Change Trip" else "Add to Trip"
                            androidx.compose.material3.DropdownMenuItem(
                                text = { Text(tripLabel) },
                                onClick = {
                                    showOverflowMenu = false
                                    showTripPicker = true
                                },
                                leadingIcon = { Icon(Icons.Filled.Add, null) },
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
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
                // ── Map with Layer Toolbar ────────────────────────────
                val points = uiState.polylinePoints
                // Compute highlighted segment points from suggestion index
                val highlightedSegmentPoints = remember(uiState.highlightedSuggestionIndex, uiState.suggestedSegments, points) {
                    val idx = uiState.highlightedSuggestionIndex ?: return@remember emptyList()
                    val candidate = uiState.suggestedSegments.getOrNull(idx) ?: return@remember emptyList()
                    val start = candidate.startIdxA.coerceIn(0, points.size - 1)
                    val end = candidate.endIdxA.coerceIn(0, points.size - 1)
                    if (end > start) points.subList(start, end + 1) else emptyList()
                }
                // Compute selected pace note segment points for map highlight
                val selectedPaceNotePoints = remember(uiState.selectedPaceNoteIndex, uiState.paceNotes, points) {
                    val idx = uiState.selectedPaceNoteIndex ?: return@remember emptyList<LatLng>()
                    val note = uiState.paceNotes.getOrNull(idx) ?: return@remember emptyList<LatLng>()
                    val segStart = note.segmentStartIndex
                    val segEnd = note.segmentEndIndex
                    val start: Int
                    val end: Int
                    if (segStart != null && segEnd != null) {
                        start = segStart.coerceIn(0, points.size - 1)
                        end = segEnd.coerceIn(0, points.size - 1)
                    } else {
                        // Fallback: use pointIndex ± 5
                        start = (note.pointIndex - 5).coerceIn(0, points.size - 1)
                        end = (note.pointIndex + 5).coerceIn(0, points.size - 1)
                    }
                    if (end > start) points.subList(start, end + 1) else emptyList()
                }
                if (points.isNotEmpty()) {
                    Box {
                        TrackMap(
                            points = points,
                            trackPoints = uiState.trackPoints,
                            paceNotes = uiState.paceNotes,
                            activeLayers = uiState.activeLayers,
                            useGoogleMaps = MapProvider.useGoogleMaps(preferences.mapProvider),
                            modifier = Modifier.fillMaxWidth().height(300.dp),
                            highlightedSegmentPoints = highlightedSegmentPoints,
                            gripEvents = uiState.gripEvents,
                            selectedGripEventIndex = uiState.selectedGripEventIndex,
                            selectedPaceNotePoints = selectedPaceNotePoints,
                        )

                        // Gradient legend
                        Column(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(8.dp),
                        ) {
                            if (MapLayer.SPEED in uiState.activeLayers) {
                                GradientLegend("Speed", LayerSpeedLow, LayerSpeedMid, LayerSpeedHigh,
                                    "0", formatSpeed(uiState.track?.maxSpeedMps ?: 0.0, preferences.unitSystem) + " " + speedUnit(preferences.unitSystem))
                            }
                            if (MapLayer.ELEVATION in uiState.activeLayers) {
                                val minEle = uiState.elevationProfile.minOfOrNull { it.elevation } ?: 0.0
                                val maxEle = uiState.elevationProfile.maxOfOrNull { it.elevation } ?: 0.0
                                GradientLegend("Elevation", Color(0xFFCE93D8), LayerElevation, Color(0xFF4A148C),
                                    formatElevation(minEle, preferences.unitSystem), formatElevation(maxEle, preferences.unitSystem))
                            }
                            if (MapLayer.CURVATURE in uiState.activeLayers) {
                                GradientLegend("Curvature", Color.Gray, Color(0xFFF48FB1), LayerCurvature,
                                    "Straight", "Tight")
                            }
                        }

                        // Top-right overlay controls: fullscreen + layers
                        Column(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            horizontalAlignment = Alignment.End,
                        ) {
                            IconButton(
                                onClick = { isMapFullscreen = true },
                                modifier = Modifier.background(
                                    MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                                    RoundedCornerShape(8.dp),
                                ),
                            ) {
                                Icon(
                                    Icons.Filled.Fullscreen,
                                    contentDescription = "Fullscreen map",
                                    tint = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                            LayersButton(
                                activeLayers = uiState.activeLayers,
                                onToggle = viewModel::toggleLayer,
                                isRoute = uiState.track?.trackCategory == "route",
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Compute vehicle name for display
                val vehicleName = remember(uiState.track?.vehicleId, allVehicles) {
                    uiState.track?.vehicleId?.let { vid -> allVehicles.find { it.id == vid } }
                        ?.let { "${it.year} ${it.make} ${it.model}" }
                }

                val tripName = remember(uiState.track?.tripId, allTrips) {
                    uiState.track?.tripId?.let { tid -> allTrips.find { it.id == tid } }?.name
                }

                uiState.track?.let { track ->
                    ViewTab(
                        track = track,
                        uiState = uiState,
                        unitSystem = preferences.unitSystem,
                        activeLayers = uiState.activeLayers,
                        vehicleName = vehicleName,
                        tripName = tripName,
                        onTripClick = { track.tripId?.let { onNavigateToTrip(it) } },
                        onDetectSegments = { viewModel.detectNewSegments() },
                        onSegmentClick = onSegmentClick,
                        onViewAllSegments = onViewAllSegments,
                        onRegeneratePaceNotes = { viewModel.regeneratePaceNotes() },
                        onSaveSegment = viewModel::saveSegmentSuggestion,
                        onDismissSuggestions = { viewModel.clearSuggestions() },
                        onToggleHighlightSuggestion = { viewModel.toggleHighlightedSuggestion(it) },
                        onGripEventClick = { viewModel.selectGripEvent(it) },
                        onPaceNoteClick = { viewModel.selectPaceNote(it) },
                        selectedPaceNoteIndex = uiState.selectedPaceNoteIndex,
                        onVehicleClick = onVehicleClick,
                    )
                }
            }
        }
    }

    // Fullscreen map overlay
    if (isMapFullscreen) {
        val points = uiState.polylinePoints
        if (points.isNotEmpty()) {
            // Recompute selected pace note points for fullscreen (same logic as above)
            val fullscreenPaceNotePoints = remember(uiState.selectedPaceNoteIndex, uiState.paceNotes, points) {
                val idx = uiState.selectedPaceNoteIndex ?: return@remember emptyList<LatLng>()
                val note = uiState.paceNotes.getOrNull(idx) ?: return@remember emptyList<LatLng>()
                val segStart = note.segmentStartIndex
                val segEnd = note.segmentEndIndex
                val start: Int
                val end: Int
                if (segStart != null && segEnd != null) {
                    start = segStart.coerceIn(0, points.size - 1)
                    end = segEnd.coerceIn(0, points.size - 1)
                } else {
                    start = (note.pointIndex - 5).coerceIn(0, points.size - 1)
                    end = (note.pointIndex + 5).coerceIn(0, points.size - 1)
                }
                if (end > start) points.subList(start, end + 1) else emptyList()
            }
            FullscreenMapDialog(
                points = points,
                trackPoints = uiState.trackPoints,
                paceNotes = uiState.paceNotes,
                activeLayers = uiState.activeLayers,
                useGoogleMaps = MapProvider.useGoogleMaps(preferences.mapProvider),
                isRoute = uiState.track?.trackCategory == "route",
                onToggleLayer = { viewModel.toggleLayer(it) },
                onDismiss = { isMapFullscreen = false },
                gripEvents = uiState.gripEvents,
                selectedGripEventIndex = uiState.selectedGripEventIndex,
                selectedPaceNotePoints = fullscreenPaceNotePoints,
            )
        }
    }
}

// ── Map ─────────────────────────────────────────────────────────────────────

@Composable
private fun TrackMap(
    points: List<LatLng>,
    trackPoints: List<TrackPointEntity>,
    paceNotes: List<PaceNoteEntity>,
    activeLayers: Set<MapLayer>,
    useGoogleMaps: Boolean,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(bottom = 100.dp),
    highlightedSegmentPoints: List<LatLng> = emptyList(),
    gripEvents: List<GripEventDetector.GripEvent> = emptyList(),
    selectedGripEventIndex: Int? = null,
    selectedPaceNotePoints: List<LatLng> = emptyList(),
) {
    if (useGoogleMaps) {
        GoogleTrackMap(points, trackPoints, paceNotes, activeLayers, modifier, contentPadding, highlightedSegmentPoints, gripEvents, selectedGripEventIndex, selectedPaceNotePoints)
    } else {
        OsmTrackMap(points, trackPoints, paceNotes, activeLayers, modifier, highlightedSegmentPoints, gripEvents, selectedGripEventIndex, selectedPaceNotePoints)
    }
}

@Composable
private fun GoogleTrackMap(
    points: List<LatLng>,
    trackPoints: List<TrackPointEntity>,
    paceNotes: List<PaceNoteEntity>,
    activeLayers: Set<MapLayer>,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(bottom = 100.dp),
    highlightedSegmentPoints: List<LatLng> = emptyList(),
    gripEvents: List<GripEventDetector.GripEvent> = emptyList(),
    selectedGripEventIndex: Int? = null,
    selectedPaceNotePoints: List<LatLng> = emptyList(),
) {
    val cameraPositionState = rememberCameraPositionState()
    val bounds = remember(points) {
        val boundsBuilder = LatLngBounds.builder()
        points.forEach { boundsBuilder.include(com.google.android.gms.maps.model.LatLng(it.latitude, it.longitude)) }
        boundsBuilder.build()
    }
    // Set initial position at center; LaunchedEffect will fit bounds once map is laid out
    LaunchedEffect(bounds) {
        cameraPositionState.move(CameraUpdateFactory.newLatLngBounds(bounds, 64))
    }
    // Animate camera to highlighted segment bounds
    LaunchedEffect(highlightedSegmentPoints) {
        if (highlightedSegmentPoints.size >= 2) {
            val segBoundsBuilder = LatLngBounds.builder()
            highlightedSegmentPoints.forEach { segBoundsBuilder.include(com.google.android.gms.maps.model.LatLng(it.latitude, it.longitude)) }
            cameraPositionState.animate(CameraUpdateFactory.newLatLngBounds(segBoundsBuilder.build(), 96))
        }
    }
    // Animate camera to selected grip event
    LaunchedEffect(selectedGripEventIndex) {
        val event = selectedGripEventIndex?.let { gripEvents.getOrNull(it) }
        if (event != null && event.pointIndex in points.indices) {
            val p = points[event.pointIndex]
            cameraPositionState.animate(
                CameraUpdateFactory.newLatLngZoom(
                    com.google.android.gms.maps.model.LatLng(p.latitude, p.longitude),
                    17f,
                ),
            )
        }
    }
    // Animate camera to selected pace note segment bounds
    LaunchedEffect(selectedPaceNotePoints) {
        if (selectedPaceNotePoints.size >= 2) {
            val segBoundsBuilder = LatLngBounds.builder()
            selectedPaceNotePoints.forEach { segBoundsBuilder.include(com.google.android.gms.maps.model.LatLng(it.latitude, it.longitude)) }
            cameraPositionState.animate(CameraUpdateFactory.newLatLngBounds(segBoundsBuilder.build(), 96))
        }
    }

    GoogleMap(
        modifier = modifier,
        cameraPositionState = cameraPositionState,
        uiSettings = MapUiSettings(zoomControlsEnabled = true, scrollGesturesEnabled = true, zoomGesturesEnabled = true),
        contentPadding = contentPadding,
    ) {
        // Base route polyline (dimmed when callouts layer active or segment highlighted)
        val hasHighlight = highlightedSegmentPoints.size >= 2
        if (points.size >= 2) {
            val baseColor = when {
                hasHighlight -> Color(0xFF1A73E8).copy(alpha = 0.15f)
                MapLayer.CALLOUTS in activeLayers -> Color(0xFF1A73E8).copy(alpha = 0.3f)
                else -> Color(0xFF1A73E8)
            }
            Polyline(
                points = points.map { com.google.android.gms.maps.model.LatLng(it.latitude, it.longitude) },
                color = baseColor, width = 8f,
            )
        }

        // Speed layer: colored segments
        if (MapLayer.SPEED in activeLayers && trackPoints.size >= 2) {
            val maxSpeed = trackPoints.maxOfOrNull { it.speed ?: 0.0 } ?: 1.0
            for (i in 0 until trackPoints.size - 1) {
                val speed = trackPoints[i].speed ?: 0.0
                val fraction = (speed / maxSpeed).coerceIn(0.0, 1.0)
                val color = speedColor(fraction.toFloat())
                Polyline(
                    points = listOf(
                        com.google.android.gms.maps.model.LatLng(trackPoints[i].lat, trackPoints[i].lon),
                        com.google.android.gms.maps.model.LatLng(trackPoints[i + 1].lat, trackPoints[i + 1].lon),
                    ),
                    color = color, width = 12f,
                )
            }
        }

        // Accel/Decel layer: colored polyline segments
        if (MapLayer.ACCEL in activeLayers && trackPoints.size >= 2) {
            for (i in 0 until trackPoints.size - 1) {
                val accel = trackPoints[i].accelMps2 ?: continue
                if (abs(accel) < 0.5) continue // noise floor
                val baseColor = if (accel > 0) LayerAccel else LayerDecel
                val alpha = (abs(accel) / 3.0).coerceIn(0.2, 1.0).toFloat()
                Polyline(
                    points = listOf(
                        com.google.android.gms.maps.model.LatLng(trackPoints[i].lat, trackPoints[i].lon),
                        com.google.android.gms.maps.model.LatLng(trackPoints[i + 1].lat, trackPoints[i + 1].lon),
                    ),
                    color = baseColor.copy(alpha = alpha), width = 12f,
                )
            }
        }

        // Elevation layer: colored segments from green (low) to brown/red (high)
        if (MapLayer.ELEVATION in activeLayers && trackPoints.size >= 2) {
            val elevations = trackPoints.mapNotNull { it.elevation }
            if (elevations.isNotEmpty()) {
                val minEle = elevations.min()
                val maxEle = elevations.max()
                val eleRange = (maxEle - minEle).coerceAtLeast(1.0)
                for (i in 0 until trackPoints.size - 1) {
                    val ele = trackPoints[i].elevation ?: continue
                    val fraction = ((ele - minEle) / eleRange).coerceIn(0.0, 1.0).toFloat()
                    // Green (low) -> Brown/Red (high)
                    val color = lerpColor(LayerSpeedLow, Color(0xFF8B4513), fraction)
                    Polyline(
                        points = listOf(
                            com.google.android.gms.maps.model.LatLng(trackPoints[i].lat, trackPoints[i].lon),
                            com.google.android.gms.maps.model.LatLng(trackPoints[i + 1].lat, trackPoints[i + 1].lon),
                        ),
                        color = color, width = 12f,
                    )
                }
            }
        }

        // Curvature layer: colored segments from green (straight) to pink (tight curves)
        // Uses absolute thresholds (0–10 deg/m) instead of per-track normalization
        if (MapLayer.CURVATURE in activeLayers && trackPoints.size >= 2) {
            for (i in 0 until trackPoints.size - 1) {
                val curv = trackPoints[i].curvatureDegPerM?.let { abs(it) } ?: continue
                val fraction = curvatureToFraction(curv)
                val color = lerpColor(LayerSpeedLow, LayerCurvature, fraction)
                Polyline(
                    points = listOf(
                        com.google.android.gms.maps.model.LatLng(trackPoints[i].lat, trackPoints[i].lon),
                        com.google.android.gms.maps.model.LatLng(trackPoints[i + 1].lat, trackPoints[i + 1].lon),
                    ),
                    color = color, width = 12f,
                )
            }
        }

        // Surface layer: colored segments by road surface type
        if (MapLayer.SURFACE in activeLayers && trackPoints.size >= 2) {
            for (i in 0 until trackPoints.size - 1) {
                val surface = trackPoints[i].surfaceType ?: continue
                val color = surfaceTypeColor(surface)
                Polyline(
                    points = listOf(
                        com.google.android.gms.maps.model.LatLng(trackPoints[i].lat, trackPoints[i].lon),
                        com.google.android.gms.maps.model.LatLng(trackPoints[i + 1].lat, trackPoints[i + 1].lon),
                    ),
                    color = color, width = 12f,
                )
            }
        }

        // Callouts layer: colored segments + rally icons
        if (MapLayer.CALLOUTS in activeLayers) {
            val allMapPoints = points.map { com.google.android.gms.maps.model.LatLng(it.latitude, it.longitude) }
            // Draw full-coverage colored segments via shared builder
            val coloredSegs = buildColoredSegments(allMapPoints.size, paceNotes)
            for (seg in coloredSegs) {
                val start = seg.startIndex.coerceIn(0, allMapPoints.size - 1)
                val end = seg.endIndex.coerceIn(0, allMapPoints.size - 1)
                if (end > start) {
                    Polyline(
                        points = allMapPoints.subList(start, end + 1),
                        color = seg.color,
                        width = if (seg.isFeature) 12f else 8f,
                    )
                }
            }
            // Rally-style icon markers (skip straights)
            val density = (LocalDensity.current.density * 160f).toInt()
            paceNotes.forEach { note ->
                if (note.noteType == NoteType.STRAIGHT) return@forEach
                val apexIdx = note.pointIndex
                if (apexIdx in points.indices) {
                    val p = points[apexIdx]
                    val bitmap = PaceNoteIconRenderer.createMarkerBitmap(
                        note.noteType, note.severity, note.modifier, density,
                    )
                    Marker(
                        state = rememberMarkerState(position = com.google.android.gms.maps.model.LatLng(p.latitude, p.longitude)),
                        title = note.callText,
                        snippet = buildString {
                            append("Grade ${note.severity}")
                            note.turnRadiusM?.let { append(" · R=%.0fm".format(it)) }
                            if (note.modifier != NoteModifier.NONE) append(" · ${note.modifier.name.lowercase()}")
                        },
                        icon = BitmapDescriptorFactory.fromBitmap(bitmap),
                        anchor = androidx.compose.ui.geometry.Offset(0.5f, 0.5f),
                    )
                }
            }
        }

        // Grip event markers
        if (MapLayer.GRIP in activeLayers && gripEvents.isNotEmpty()) {
            gripEvents.forEachIndexed { eventIdx, event ->
                if (event.pointIndex in points.indices) {
                    val p = points[event.pointIndex]
                    val isSelected = selectedGripEventIndex == eventIdx
                    val markerColor = gripEventMarkerColor(event.type)
                    Circle(
                        center = com.google.android.gms.maps.model.LatLng(p.latitude, p.longitude),
                        radius = if (isSelected) 12.0 else 8.0,
                        fillColor = markerColor.copy(alpha = if (isSelected) 1f else 0.8f),
                        strokeColor = if (isSelected) Color.White else markerColor,
                        strokeWidth = if (isSelected) 4f else 2f,
                    )
                }
            }
        }

        // Highlighted suggested segment — full opacity over dimmed base
        if (hasHighlight) {
            Polyline(
                points = highlightedSegmentPoints.map { com.google.android.gms.maps.model.LatLng(it.latitude, it.longitude) },
                color = Color(0xFF1A73E8), width = 10f,
            )
        }

        // Highlighted selected pace note segment — amber/orange to distinguish from blue suggestion highlight
        if (selectedPaceNotePoints.size >= 2) {
            Polyline(
                points = selectedPaceNotePoints.map { com.google.android.gms.maps.model.LatLng(it.latitude, it.longitude) },
                color = Color(0xFFFFA726), width = 12f,
            )
        }
    }
}

@Composable
private fun OsmTrackMap(
    points: List<LatLng>,
    trackPoints: List<TrackPointEntity>,
    paceNotes: List<PaceNoteEntity>,
    activeLayers: Set<MapLayer>,
    modifier: Modifier = Modifier,
    highlightedSegmentPoints: List<LatLng> = emptyList(),
    gripEvents: List<GripEventDetector.GripEvent> = emptyList(),
    selectedGripEventIndex: Int? = null,
    selectedPaceNotePoints: List<LatLng> = emptyList(),
) {
    val lats = points.map { it.latitude }
    val lngs = points.map { it.longitude }
    val fitBounds = BoundingBox(lats.max(), lngs.max(), lats.min(), lngs.min())

    val density = (LocalDensity.current.density * 160f).toInt()
    val calloutsActive = MapLayer.CALLOUTS in activeLayers

    val hasHighlight = highlightedSegmentPoints.size >= 2
    val polylines = mutableListOf<OsmPolylineData>()
    if (points.size >= 2) {
        val hasOverlay = setOf(MapLayer.SPEED, MapLayer.ACCEL, MapLayer.ELEVATION, MapLayer.CURVATURE, MapLayer.SURFACE)
            .any { it in activeLayers }
        val baseColor = when {
            hasHighlight -> Color(0xFF1A73E8).copy(alpha = 0.15f)
            calloutsActive || hasOverlay -> Color(0xFF1A73E8).copy(alpha = 0.3f)
            else -> Color(0xFF1A73E8)
        }
        polylines.add(OsmPolylineData(points = points.map { GeoPoint(it.latitude, it.longitude) }, color = baseColor, width = 8f))
    }

    // Speed layer: colored segments
    if (MapLayer.SPEED in activeLayers && trackPoints.size >= 2) {
        val maxSpeed = trackPoints.maxOfOrNull { it.speed ?: 0.0 } ?: 1.0
        for (i in 0 until trackPoints.size - 1) {
            val speed = trackPoints[i].speed ?: 0.0
            val fraction = (speed / maxSpeed).coerceIn(0.0, 1.0)
            polylines.add(OsmPolylineData(
                points = listOf(
                    GeoPoint(trackPoints[i].lat, trackPoints[i].lon),
                    GeoPoint(trackPoints[i + 1].lat, trackPoints[i + 1].lon),
                ),
                color = speedColor(fraction.toFloat()), width = 12f,
            ))
        }
    }

    // Accel/Decel layer
    if (MapLayer.ACCEL in activeLayers && trackPoints.size >= 2) {
        for (i in 0 until trackPoints.size - 1) {
            val accel = trackPoints[i].accelMps2 ?: continue
            if (abs(accel) < 0.5) continue
            val baseLayerColor = if (accel > 0) LayerAccel else LayerDecel
            val alpha = (abs(accel) / 3.0).coerceIn(0.2, 1.0).toFloat()
            polylines.add(OsmPolylineData(
                points = listOf(
                    GeoPoint(trackPoints[i].lat, trackPoints[i].lon),
                    GeoPoint(trackPoints[i + 1].lat, trackPoints[i + 1].lon),
                ),
                color = baseLayerColor.copy(alpha = alpha), width = 12f,
            ))
        }
    }

    // Elevation layer: green (low) to brown (high)
    if (MapLayer.ELEVATION in activeLayers && trackPoints.size >= 2) {
        val elevations = trackPoints.mapNotNull { it.elevation }
        if (elevations.isNotEmpty()) {
            val minEle = elevations.min()
            val maxEle = elevations.max()
            val eleRange = (maxEle - minEle).coerceAtLeast(1.0)
            for (i in 0 until trackPoints.size - 1) {
                val ele = trackPoints[i].elevation ?: continue
                val fraction = ((ele - minEle) / eleRange).coerceIn(0.0, 1.0).toFloat()
                polylines.add(OsmPolylineData(
                    points = listOf(
                        GeoPoint(trackPoints[i].lat, trackPoints[i].lon),
                        GeoPoint(trackPoints[i + 1].lat, trackPoints[i + 1].lon),
                    ),
                    color = lerpColor(LayerSpeedLow, Color(0xFF8B4513), fraction), width = 12f,
                ))
            }
        }
    }

    // Curvature layer: green (straight) to pink (tight)
    // Uses absolute thresholds (0–10 deg/m) instead of per-track normalization
    if (MapLayer.CURVATURE in activeLayers && trackPoints.size >= 2) {
        for (i in 0 until trackPoints.size - 1) {
            val curv = trackPoints[i].curvatureDegPerM?.let { abs(it) } ?: continue
            val fraction = curvatureToFraction(curv)
            polylines.add(OsmPolylineData(
                points = listOf(
                    GeoPoint(trackPoints[i].lat, trackPoints[i].lon),
                    GeoPoint(trackPoints[i + 1].lat, trackPoints[i + 1].lon),
                ),
                color = lerpColor(LayerSpeedLow, LayerCurvature, fraction), width = 12f,
            ))
        }
    }

    // Surface layer: colored by road surface type
    if (MapLayer.SURFACE in activeLayers && trackPoints.size >= 2) {
        for (i in 0 until trackPoints.size - 1) {
            val surface = trackPoints[i].surfaceType ?: continue
            polylines.add(OsmPolylineData(
                points = listOf(
                    GeoPoint(trackPoints[i].lat, trackPoints[i].lon),
                    GeoPoint(trackPoints[i + 1].lat, trackPoints[i + 1].lon),
                ),
                color = surfaceTypeColor(surface), width = 12f,
            ))
        }
    }

    // Full-coverage colored segments for callouts via shared builder
    if (calloutsActive && points.size >= 2) {
        val geoPoints = points.map { GeoPoint(it.latitude, it.longitude) }
        val coloredSegs = buildColoredSegments(geoPoints.size, paceNotes)
        for (seg in coloredSegs) {
            val start = seg.startIndex.coerceIn(0, geoPoints.size - 1)
            val end = seg.endIndex.coerceIn(0, geoPoints.size - 1)
            if (end > start) {
                polylines.add(OsmPolylineData(
                    points = geoPoints.subList(start, end + 1),
                    color = seg.color,
                    width = if (seg.isFeature) 12f else 8f,
                ))
            }
        }
    }

    val markers = mutableListOf<OsmMarkerData>()
    if (calloutsActive) {
        paceNotes.forEach { note ->
            if (note.noteType == NoteType.STRAIGHT) return@forEach
            val apexIdx = note.pointIndex
            if (apexIdx in points.indices) {
                val bitmap = PaceNoteIconRenderer.createMarkerBitmap(
                    note.noteType, note.severity, note.modifier, density,
                )
                val osmTitle = buildString {
                    append(note.callText)
                    note.turnRadiusM?.let { append(" (R=%.0fm)".format(it)) }
                }
                markers.add(OsmMarkerData(GeoPoint(points[apexIdx].latitude, points[apexIdx].longitude), osmTitle, icon = bitmap))
            }
        }
    }

    // Grip event markers (OSM uses hue-colored default markers)
    if (MapLayer.GRIP in activeLayers && gripEvents.isNotEmpty()) {
        gripEvents.forEach { event ->
            if (event.pointIndex in points.indices) {
                val p = points[event.pointIndex]
                val hue = gripEventMarkerHue(event.type)
                markers.add(OsmMarkerData(GeoPoint(p.latitude, p.longitude), gripEventLabel(event), hue = hue))
            }
        }
    }

    // Highlighted suggested segment — full opacity over dimmed base
    if (hasHighlight) {
        polylines.add(OsmPolylineData(
            points = highlightedSegmentPoints.map { GeoPoint(it.latitude, it.longitude) },
            color = Color(0xFF1A73E8), width = 10f,
        ))
    }

    // Highlighted selected pace note segment — amber/orange
    if (selectedPaceNotePoints.size >= 2) {
        polylines.add(OsmPolylineData(
            points = selectedPaceNotePoints.map { GeoPoint(it.latitude, it.longitude) },
            color = Color(0xFFFFA726), width = 12f,
        ))
    }

    // Focus on selected grip event, then pace note, then suggestion highlight
    val osmFitBounds = if (selectedGripEventIndex != null) {
        val event = gripEvents.getOrNull(selectedGripEventIndex)
        if (event != null && event.pointIndex in points.indices) {
            val p = points[event.pointIndex]
            // Small bounding box centered on the event point
            val d = 0.002 // ~200m
            BoundingBox(p.latitude + d, p.longitude + d, p.latitude - d, p.longitude - d)
        } else fitBounds
    } else if (selectedPaceNotePoints.size >= 2) {
        val pnLats = selectedPaceNotePoints.map { it.latitude }
        val pnLngs = selectedPaceNotePoints.map { it.longitude }
        BoundingBox(pnLats.max(), pnLngs.max(), pnLats.min(), pnLngs.min())
    } else if (highlightedSegmentPoints.size >= 2) {
        val hLats = highlightedSegmentPoints.map { it.latitude }
        val hLngs = highlightedSegmentPoints.map { it.longitude }
        BoundingBox(hLats.max(), hLngs.max(), hLats.min(), hLngs.min())
    } else {
        fitBounds
    }

    OsmMapView(
        modifier = modifier,
        fitBounds = osmFitBounds, polylines = polylines, markers = markers, zoomControlsEnabled = true,
    )
}

// ── Fullscreen Map Dialog ────────────────────────────────────────────────────

@Composable
private fun FullscreenMapDialog(
    points: List<LatLng>,
    trackPoints: List<TrackPointEntity>,
    paceNotes: List<PaceNoteEntity>,
    activeLayers: Set<MapLayer>,
    useGoogleMaps: Boolean,
    isRoute: Boolean = false,
    onToggleLayer: (MapLayer) -> Unit = {},
    onDismiss: () -> Unit,
    gripEvents: List<GripEventDetector.GripEvent> = emptyList(),
    selectedGripEventIndex: Int? = null,
    selectedPaceNotePoints: List<LatLng> = emptyList(),
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
        ) {
            TrackMap(
                points = points,
                trackPoints = trackPoints,
                paceNotes = paceNotes,
                activeLayers = activeLayers,
                useGoogleMaps = useGoogleMaps,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 56.dp),
                gripEvents = gripEvents,
                selectedGripEventIndex = selectedGripEventIndex,
                selectedPaceNotePoints = selectedPaceNotePoints,
            )

            // Top-right overlay controls: exit fullscreen + layers
            Column(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.End,
            ) {
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.background(
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                        RoundedCornerShape(8.dp),
                    ),
                ) {
                    Icon(
                        Icons.Filled.FullscreenExit,
                        contentDescription = "Exit fullscreen",
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
                LayersButton(
                    activeLayers = activeLayers,
                    onToggle = onToggleLayer,
                    isRoute = isRoute,
                )
            }
        }
    }
}

// ── Layer Selector ──────────────────────────────────────────────────────────

@Composable
private fun LayersButton(
    activeLayers: Set<MapLayer>,
    onToggle: (MapLayer) -> Unit,
    isRoute: Boolean = false,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        IconButton(
            onClick = { expanded = true },
            modifier = Modifier.background(
                MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                RoundedCornerShape(8.dp),
            ),
        ) {
            Icon(
                Icons.Filled.Layers,
                contentDescription = "Map layers",
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            if (!isRoute) {
                LayerMenuItem("Speed", MapLayer.SPEED, activeLayers, onToggle)
                LayerMenuItem("Accel", MapLayer.ACCEL, activeLayers, onToggle)
            }
            LayerMenuItem("Elevation", MapLayer.ELEVATION, activeLayers, onToggle)
            LayerMenuItem("Curvature", MapLayer.CURVATURE, activeLayers, onToggle)
            LayerMenuItem("Surface", MapLayer.SURFACE, activeLayers, onToggle)
            LayerMenuItem("Grip", MapLayer.GRIP, activeLayers, onToggle)
            LayerMenuItem("Pace notes", MapLayer.CALLOUTS, activeLayers, onToggle)
        }
    }
}

@Composable
private fun LayerMenuItem(
    label: String,
    layer: MapLayer,
    active: Set<MapLayer>,
    onToggle: (MapLayer) -> Unit,
) {
    val isActive = layer in active
    DropdownMenuItem(
        text = { Text(label) },
        onClick = { onToggle(layer) },
        trailingIcon = {
            if (isActive) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = "Active",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        },
    )
}

// ── Gradient Legend ──────────────────────────────────────────────────────────

@Composable
private fun GradientLegend(title: String, startColor: Color, midColor: Color, endColor: Color, minLabel: String, maxLabel: String) {
    Card(
        modifier = Modifier.padding(vertical = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)),
    ) {
        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
            Text(title, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(2.dp))
            Canvas(modifier = Modifier.width(120.dp).height(8.dp)) {
                val w = size.width
                for (x in 0..w.toInt()) {
                    val fraction = x / w
                    val color = if (fraction < 0.5f) {
                        lerpColor(startColor, midColor, fraction * 2f)
                    } else {
                        lerpColor(midColor, endColor, (fraction - 0.5f) * 2f)
                    }
                    drawLine(color, Offset(x.toFloat(), 0f), Offset(x.toFloat(), size.height))
                }
            }
            Row(modifier = Modifier.width(120.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(minLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(maxLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

// ── Summary Card ────────────────────────────────────────────────────────────

@Composable
private fun SummaryCard(track: com.rallytrax.app.data.local.entity.TrackEntity, uiState: TrackDetailUiState, unitSystem: UnitSystem) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Summary", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))
            Text(formatDateTime(track.recordedAt), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(12.dp))

            // 2-column grid of stats
            val isRoute = track.trackCategory == "route"
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                StatItem("Distance", formatDistance(track.distanceMeters, unitSystem))
                StatItem("Duration", formatElapsedTime(track.durationMs))
            }
            if (!isRoute) {
                HorizontalDivider(Modifier.padding(vertical = 10.dp), color = MaterialTheme.colorScheme.outlineVariant)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    StatItem("Avg Speed", "${formatSpeed(track.avgSpeedMps, unitSystem)} ${speedUnit(unitSystem)}")
                    StatItem("Max Speed", "${formatSpeed(track.maxSpeedMps, unitSystem)} ${speedUnit(unitSystem)}")
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 10.dp), color = MaterialTheme.colorScheme.outlineVariant)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                StatItem("Elevation Gain", formatElevation(track.elevationGainM, unitSystem))
                StatItem("Avg Curvature", formatCurvature(uiState.trackPoints))
            }
        }
    }
}

@Composable
private fun StatItem(label: String, value: String) {
    Column {
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ── Driving Insights Card ───────────────────────────────────────────────────

@Composable
private fun DrivingInsightsCard(
    uiState: TrackDetailUiState,
    unitSystem: UnitSystem,
    track: com.rallytrax.app.data.local.entity.TrackEntity,
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Driving Insights", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(12.dp))

            // GoalRing row: Smoothness + Braking Efficiency
            val hasRings = uiState.smoothnessScore != null || uiState.brakingEfficiencyScore != null
            if (hasRings) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    if (uiState.smoothnessScore != null) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            GoalRing(
                                progress = uiState.smoothnessScore / 100f,
                                label = "Smoothness",
                                progressColor = MaterialTheme.colorScheme.primary,
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = when {
                                    uiState.smoothnessScore >= 80 -> "Very smooth inputs"
                                    uiState.smoothnessScore >= 60 -> "Fairly smooth"
                                    uiState.smoothnessScore >= 40 -> "Some abrupt inputs"
                                    else -> "Jerky inputs"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    if (uiState.brakingEfficiencyScore != null) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            GoalRing(
                                progress = uiState.brakingEfficiencyScore / 100f,
                                label = "Braking",
                                progressColor = MaterialTheme.colorScheme.tertiary,
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = when {
                                    uiState.brakingEfficiencyScore >= 80 -> "Progressive trail braking"
                                    uiState.brakingEfficiencyScore >= 60 -> "Mostly progressive"
                                    uiState.brakingEfficiencyScore >= 40 -> "Some late braking"
                                    else -> "Abrupt braking"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            // Stat items: Cornering G, Roughness, Elevation-Adjusted Speed
            val hasStats = uiState.peakCorneringG != null || uiState.roadRoughnessIndex != null
                || uiState.elevationAdjustedAvgSpeedMps != null
            if (hasStats) {
                if (hasRings) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(modifier = Modifier.height(12.dp))
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    if (uiState.peakCorneringG != null) {
                        StatItem("Peak Cornering", "%.2f G".format(uiState.peakCorneringG))
                    }
                    if (uiState.roadRoughnessIndex != null) {
                        val roughnessLabel = when {
                            uiState.roadRoughnessIndex < 1.0 -> "Smooth"
                            uiState.roadRoughnessIndex <= 2.0 -> "Moderate"
                            else -> "Rough"
                        }
                        StatItem("Road Surface", roughnessLabel)
                    }
                }
            }
            if (uiState.elevationAdjustedAvgSpeedMps != null) {
                Spacer(modifier = Modifier.height(8.dp))
                val rawAvg = track.avgSpeedMps
                val adjusted = uiState.elevationAdjustedAvgSpeedMps
                val delta = adjusted - rawAvg
                val arrow = if (delta >= 0) "\u2191" else "\u2193"
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    StatItem(
                        "Elev-Adj Speed",
                        "${formatSpeed(adjusted, unitSystem)} ${speedUnit(unitSystem)}",
                    )
                    StatItem(
                        "vs Raw Avg",
                        "$arrow ${formatSpeed(abs(delta), unitSystem)} ${speedUnit(unitSystem)}",
                    )
                }
            }
        }
    }
}

// ── Speed Profile Card ──────────────────────────────────────────────────────

@Composable
private fun SpeedProfileCard(speedProfile: List<SpeedPoint>, unitSystem: UnitSystem, track: com.rallytrax.app.data.local.entity.TrackEntity? = null) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Speed", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(12.dp))
            SpeedChart(data = speedProfile, unitSystem = unitSystem, modifier = Modifier.fillMaxWidth().height(200.dp))
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("0", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(formatDistance(speedProfile.last().distanceFromStart, unitSystem), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            // Hero stats below chart (Strava-style)
            if (track != null) {
                HorizontalDivider(Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outlineVariant)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    StatItem("Avg Speed", "${formatSpeed(track.avgSpeedMps, unitSystem)} ${speedUnit(unitSystem)}")
                    StatItem("Max Speed", "${formatSpeed(track.maxSpeedMps, unitSystem)} ${speedUnit(unitSystem)}")
                }
            }
        }
    }
}

@Composable
private fun SpeedChart(data: List<SpeedPoint>, unitSystem: UnitSystem, modifier: Modifier = Modifier) {
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val labelTextSize = with(androidx.compose.ui.platform.LocalDensity.current) { 10.dp.toPx() }
    val leftPadding = with(androidx.compose.ui.platform.LocalDensity.current) { 48.dp.toPx() }
    Canvas(modifier = modifier) {
        if (data.size < 2) return@Canvas
        val maxDist = data.last().distanceFromStart
        val maxSpeedMps = data.maxOf { it.speedMps }.coerceAtLeast(1.0)
        val paddingTop = 4f
        val chartLeft = leftPadding
        val chartWidth = size.width - chartLeft
        val h = size.height - paddingTop

        fun xFor(d: Double) = (chartLeft + (d / maxDist) * chartWidth).toFloat()
        fun yFor(s: Double) = (paddingTop + h - (s / maxSpeedMps) * h).toFloat()

        // Y-axis labels (min, mid, max)
        val paint = android.graphics.Paint().apply {
            color = labelColor.toArgb()
            textSize = labelTextSize
            isAntiAlias = true
            textAlign = android.graphics.Paint.Align.RIGHT
        }
        val speedFactor = if (unitSystem == UnitSystem.METRIC) 3.6 else 2.23694
        val unit = if (unitSystem == UnitSystem.METRIC) "km/h" else "mph"
        val maxDisplay = maxSpeedMps * speedFactor
        val midDisplay = maxDisplay / 2.0
        val labels = listOf(
            "0 $unit" to yFor(0.0),
            "%.0f $unit".format(midDisplay) to yFor(maxSpeedMps / 2.0),
            "%.0f $unit".format(maxDisplay) to yFor(maxSpeedMps),
        )
        drawIntoCanvas { canvas ->
            for ((text, y) in labels) {
                canvas.nativeCanvas.drawText(text, chartLeft - 4f, y + labelTextSize / 3f, paint)
            }
        }

        // Draw colored segments
        for (i in 0 until data.size - 1) {
            val fraction = (data[i].speedMps / maxSpeedMps).toFloat()
            val color = speedColor(fraction)
            drawLine(
                color = color,
                start = Offset(xFor(data[i].distanceFromStart), yFor(data[i].speedMps)),
                end = Offset(xFor(data[i + 1].distanceFromStart), yFor(data[i + 1].speedMps)),
                strokeWidth = 3f,
            )
        }
    }
}

// ── Acceleration Profile Card ───────────────────────────────────────────────

@Composable
private fun AccelerationProfileCard(
    accelProfile: List<AccelPoint>,
    paceNotes: List<PaceNoteEntity>,
    unitSystem: UnitSystem,
) {
    val peakAccel = accelProfile.maxOfOrNull { it.accelMps2 }?.coerceAtLeast(0.0) ?: 0.0
    val peakBraking = accelProfile.minOfOrNull { it.accelMps2 }?.coerceAtMost(0.0)?.let { abs(it) } ?: 0.0
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Acceleration Profile", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(12.dp))
            AccelChart(
                data = accelProfile,
                paceNoteDistances = paceNotes.map { it.distanceFromStart },
                modifier = Modifier.fillMaxWidth().height(180.dp),
            )
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("0", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(formatDistance(accelProfile.last().distanceFromStart, unitSystem), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            HorizontalDivider(Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outlineVariant)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                StatItem("Peak Accel", "%.2f m/s\u00B2".format(peakAccel))
                StatItem("Peak Braking", "%.2f m/s\u00B2".format(peakBraking))
            }
        }
    }
}

@Composable
private fun AccelChart(
    data: List<AccelPoint>,
    paceNoteDistances: List<Double>,
    modifier: Modifier = Modifier,
) {
    val accelColor = LayerAccel
    val decelColor = LayerDecel
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val noteLineColor = LayerCallout
    val labelTextSize = with(LocalDensity.current) { 10.dp.toPx() }
    val leftPadding = with(LocalDensity.current) { 48.dp.toPx() }

    Canvas(modifier = modifier) {
        if (data.size < 2) return@Canvas
        val maxDist = data.last().distanceFromStart
        val maxAbsAccel = data.maxOf { abs(it.accelMps2) }.coerceAtLeast(0.1)
        val paddingTop = 4f
        val chartLeft = leftPadding
        val chartWidth = size.width - chartLeft
        val h = size.height - paddingTop

        fun xFor(d: Double) = (chartLeft + (d / maxDist) * chartWidth).toFloat()
        fun yFor(a: Double) = (paddingTop + h / 2f - (a / maxAbsAccel) * (h / 2f)).toFloat()

        // Y-axis labels
        val paint = android.graphics.Paint().apply {
            color = labelColor.toArgb()
            textSize = labelTextSize
            isAntiAlias = true
            textAlign = android.graphics.Paint.Align.RIGHT
        }
        val labels = listOf(
            "%.1f".format(maxAbsAccel) to yFor(maxAbsAccel),
            "0" to yFor(0.0),
            "-%.1f".format(maxAbsAccel) to yFor(-maxAbsAccel),
        )
        drawIntoCanvas { canvas ->
            for ((text, y) in labels) {
                canvas.nativeCanvas.drawText(text, chartLeft - 4f, y + labelTextSize / 3f, paint)
            }
        }

        // Zero line
        val zeroY = yFor(0.0)
        drawLine(
            color = labelColor.copy(alpha = 0.3f),
            start = Offset(chartLeft, zeroY),
            end = Offset(size.width, zeroY),
            strokeWidth = 1f,
        )

        // Filled areas and line segments
        for (i in 0 until data.size - 1) {
            val x1 = xFor(data[i].distanceFromStart)
            val x2 = xFor(data[i + 1].distanceFromStart)
            val y1 = yFor(data[i].accelMps2)
            val y2 = yFor(data[i + 1].accelMps2)
            val isAccel = data[i].accelMps2 >= 0
            val segColor = if (isAccel) accelColor else decelColor

            // Fill to zero line
            val fillPath = Path().apply {
                moveTo(x1, zeroY)
                lineTo(x1, y1)
                lineTo(x2, y2)
                lineTo(x2, zeroY)
                close()
            }
            drawPath(fillPath, color = segColor.copy(alpha = 0.2f))

            // Line segment
            drawLine(
                color = segColor,
                start = Offset(x1, y1),
                end = Offset(x2, y2),
                strokeWidth = 2f,
            )
        }

        // Pace note overlay — vertical dashed lines
        val dashLength = 6f
        val gapLength = 4f
        for (dist in paceNoteDistances) {
            if (dist < 0 || dist > maxDist) continue
            val x = xFor(dist)
            // Draw dashed vertical line
            var currentY = paddingTop
            while (currentY < size.height) {
                val endY = (currentY + dashLength).coerceAtMost(size.height)
                drawLine(
                    color = noteLineColor.copy(alpha = 0.4f),
                    start = Offset(x, currentY),
                    end = Offset(x, endY),
                    strokeWidth = 1f,
                )
                currentY += dashLength + gapLength
            }
        }
    }
}

// ── Elevation Profile Card ──────────────────────────────────────────────────

@Composable
private fun ElevationProfileCard(elevationProfile: List<ElevationPoint>, paceNotes: List<PaceNoteEntity>, unitSystem: UnitSystem, track: com.rallytrax.app.data.local.entity.TrackEntity? = null) {
    val minEle = elevationProfile.minOfOrNull { it.elevation } ?: 0.0
    val maxEle = elevationProfile.maxOfOrNull { it.elevation } ?: 0.0
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Elevation", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(12.dp))
            ElevationChart(data = elevationProfile, unitSystem = unitSystem, modifier = Modifier.fillMaxWidth().height(200.dp))
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("0 km", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(formatDistance(elevationProfile.last().distanceFromStart, unitSystem), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            GradeGradientLegend(modifier = Modifier.padding(top = 10.dp))
            // Hero stats below chart
            HorizontalDivider(Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outlineVariant)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                StatItem("Elevation Gain", formatElevation(track?.elevationGainM ?: 0.0, unitSystem))
                StatItem("Min", formatElevation(minEle, unitSystem))
                StatItem("Max", formatElevation(maxEle, unitSystem))
            }
        }
    }
}

@Composable
private fun ElevationChart(data: List<ElevationPoint>, unitSystem: UnitSystem, modifier: Modifier = Modifier) {
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val labelTextSize = with(androidx.compose.ui.platform.LocalDensity.current) { 10.dp.toPx() }
    val leftPadding = with(androidx.compose.ui.platform.LocalDensity.current) { 48.dp.toPx() }
    Canvas(modifier = modifier) {
        if (data.size < 2) return@Canvas
        val maxDist = data.last().distanceFromStart
        val minEle = data.minOf { it.elevation }
        val maxEle = data.maxOf { it.elevation }
        val eleRange = (maxEle - minEle).coerceAtLeast(1.0)
        val paddingTop = 8f
        val chartLeft = leftPadding
        val chartWidth = size.width - chartLeft
        val chartHeight = size.height - paddingTop

        fun xFor(d: Double) = (chartLeft + (d / maxDist) * chartWidth).toFloat()
        fun yFor(e: Double) = (paddingTop + chartHeight - ((e - minEle) / eleRange) * chartHeight).toFloat()

        // Y-axis labels (min, mid, max)
        val paint = android.graphics.Paint().apply {
            color = labelColor.toArgb()
            textSize = labelTextSize
            isAntiAlias = true
            textAlign = android.graphics.Paint.Align.RIGHT
        }
        val eleFactor = if (unitSystem == UnitSystem.METRIC) 1.0 else 3.28084
        val unit = if (unitSystem == UnitSystem.METRIC) "m" else "ft"
        val midEle = (minEle + maxEle) / 2.0
        val labels = listOf(
            "%.0f $unit".format(minEle * eleFactor) to yFor(minEle),
            "%.0f $unit".format(midEle * eleFactor) to yFor(midEle),
            "%.0f $unit".format(maxEle * eleFactor) to yFor(maxEle),
        )
        drawIntoCanvas { canvas ->
            for ((text, y) in labels) {
                canvas.nativeCanvas.drawText(text, chartLeft - 4f, y + labelTextSize / 3f, paint)
            }
        }

        // Per-segment grade-gradient fill: flat = green, steep = red, matching the
        // speed chart and list thumbnails' visual language.
        for (i in 0 until data.size - 1) {
            val a = data[i]
            val b = data[i + 1]
            val dDist = (b.distanceFromStart - a.distanceFromStart).coerceAtLeast(1.0)
            val grade = (b.elevation - a.elevation) / dDist
            val segColor = speedColor(gradeToFraction(grade)).copy(alpha = 0.35f)
            val xA = xFor(a.distanceFromStart)
            val xB = xFor(b.distanceFromStart)
            val yA = yFor(a.elevation)
            val yB = yFor(b.elevation)
            val segPath = Path().apply {
                moveTo(xA, yA)
                lineTo(xB, yB)
                lineTo(xB, size.height)
                lineTo(xA, size.height)
                close()
            }
            drawPath(segPath, color = segColor)
        }
        val linePath = Path().apply {
            moveTo(xFor(data.first().distanceFromStart), yFor(data.first().elevation))
            for (point in data) lineTo(xFor(point.distanceFromStart), yFor(point.elevation))
        }
        drawPath(linePath, color = LayerElevation, style = Stroke(width = 3f))
    }
}

@Composable
private fun GradeGradientLegend(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GradeLegendItem("Flat", LayerSpeedLow)
        GradeLegendItem("Moderate", LayerSpeedMid)
        GradeLegendItem("Steep", LayerSpeedHigh)
    }
}

@Composable
private fun GradeLegendItem(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .background(color.copy(alpha = 0.9f), RoundedCornerShape(3.dp)),
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PaceNoteItem(
    note: PaceNoteEntity,
    unitSystem: UnitSystem,
    isSelected: Boolean = false,
    onClick: () -> Unit = {},
) {
    var expanded by remember { mutableStateOf(false) }
    val backgroundColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.secondaryContainer
        else Color.Transparent,
        label = "paceNoteSelectionBg",
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(backgroundColor)
            .clickable { onClick(); expanded = !expanded }
            .padding(vertical = 4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            com.rallytrax.app.ui.pacenotes.PaceNoteGlyphWithLabel(
                noteType = note.noteType,
                severity = note.severity,
                sizeDp = 40.dp,
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(note.callText, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Text(formatDistance(note.distanceFromStart, unitSystem), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(),
            exit = shrinkVertically(),
        ) {
            Column(modifier = Modifier.padding(start = 46.dp, top = 4.dp)) {
                val details = buildList {
                    note.turnRadiusM?.let { add("R = %.1fm".format(it)) }
                    classificationExplanation(note)?.let { add(it) }
                }
                if (details.isNotEmpty()) {
                    Text(
                        details.joinToString("  ·  "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

private fun classificationExplanation(note: PaceNoteEntity): String? {
    val radius = note.turnRadiusM ?: return null
    val band = when (note.severity) {
        1 -> "0–7m (Hairpin, <30 km/h)"
        2 -> "7–12m (30–40 km/h)"
        3 -> "12–22m (40–50 km/h)"
        4 -> "22–43m (50–70 km/h)"
        5 -> "43–71m (70–90 km/h)"
        6 -> "71–148m (90–130 km/h)"
        else -> return null
    }
    return "Grade ${note.severity}: radius in $band"
}

// ── Curvature Distribution Card ─────────────────────────────────────────────

@Composable
private fun CurvatureDistributionCard(cd: CurvatureDistribution) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Curvature Distribution", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(12.dp))
            CurvatureBar("Straight", cd.straight, Color.Gray)
            CurvatureBar("Gentle", cd.gentle, Color(0xFFF8BBD0))
            CurvatureBar("Moderate", cd.moderate, Color(0xFFF48FB1))
            CurvatureBar("Tight", cd.tight, Color(0xFFEC407A))
            CurvatureBar("Hairpin", cd.hairpin, LayerCurvature)
        }
    }
}

@Composable
private fun CurvatureBar(label: String, fraction: Float, color: Color) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(64.dp))
        Box(
            modifier = Modifier.weight(1f).height(16.dp)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest, RoundedCornerShape(4.dp)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction.coerceAtLeast(0.01f))
                    .height(16.dp)
                    .background(color, RoundedCornerShape(4.dp)),
            )
        }
        Text("${(fraction * 100).toInt()}%", style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.width(36.dp).padding(start = 4.dp))
    }
}

// ── Surface Breakdown Card ──────────────────────────────────────────────────

@Composable
private fun SurfaceBreakdownCard(track: com.rallytrax.app.data.local.entity.TrackEntity) {
    val breakdown = track.surfaceBreakdown ?: return
    if (breakdown.isBlank()) return

    val segments = breakdown.split(",").mapNotNull { segment ->
        val parts = segment.trim().split(":")
        if (parts.size == 2) {
            val type = parts[0].trim()
            val pct = parts[1].trim().toDoubleOrNull() ?: return@mapNotNull null
            type to pct
        } else null
    }
    if (segments.isEmpty()) return

    val totalPct = segments.sumOf { it.second }.coerceAtLeast(1.0)

    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Surface", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))
            track.primarySurface?.let {
                Text("Primary: $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Stacked bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(24.dp)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest, RoundedCornerShape(12.dp)),
            ) {
                segments.forEach { (type, pct) ->
                    val color = surfaceTypeColor(type)
                    val fraction = (pct / totalPct).toFloat()
                    Box(
                        modifier = Modifier
                            .weight(fraction.coerceAtLeast(0.01f))
                            .height(24.dp)
                            .background(color, RoundedCornerShape(if (segments.indexOf(type to pct) == 0) 12.dp else 0.dp)),
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Legend with percentages
            segments.forEach { (type, pct) ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .background(surfaceTypeColor(type), RoundedCornerShape(3.dp)),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = type,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = "${pct.toInt()}%",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

private fun surfaceTypeColor(type: String): Color {
    return when (type.lowercase()) {
        "paved", "tarmac" -> Color(0xFF607D8B)
        "gravel" -> Color(0xFFD4A574)
        "dirt" -> Color(0xFF8B6914)
        "cobblestone" -> Color(0xFF9E9E9E)
        "mixed" -> Color(0xFF795548)
        else -> Color(0xFFBDBDBD)
    }
}

// ── View Tab ────────────────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ViewTab(
    track: com.rallytrax.app.data.local.entity.TrackEntity,
    uiState: TrackDetailUiState,
    unitSystem: UnitSystem,
    activeLayers: Set<MapLayer>,
    vehicleName: String?,
    tripName: String? = null,
    onTripClick: () -> Unit = {},
    onDetectSegments: () -> Unit = {},
    onSegmentClick: (String) -> Unit = {},
    onViewAllSegments: () -> Unit = {},
    onRegeneratePaceNotes: () -> Unit = {},
    onSaveSegment: (SegmentMatcher.OverlapCandidate, String) -> Unit = { _, _ -> },
    onDismissSuggestions: () -> Unit = {},
    onToggleHighlightSuggestion: (Int) -> Unit = {},
    onGripEventClick: (Int) -> Unit = {},
    onPaceNoteClick: (Int) -> Unit = {},
    selectedPaceNoteIndex: Int? = null,
    onVehicleClick: (String) -> Unit = {},
) {
    val visible = visibleCards(activeLayers)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        // Summary: key stats + info chips
        SummaryCard(track, uiState, unitSystem)

        Spacer(modifier = Modifier.height(12.dp))
        TrackInfoChips(track, vehicleName, onVehicleClick, tripName, onTripClick)

        // Weather context badge
        val weather = uiState.weatherCondition
        if (weather != null) {
            Spacer(modifier = Modifier.height(12.dp))
            Column {
                WeatherBadge(weather = weather.toWeatherCondition())
                if (weather.hasPerformanceImpact) {
                    Text(
                        text = "Weather may have affected performance",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp, top = 4.dp),
                    )
                }
            }
        }

        // Driving insights (cross-sensor analytics)
        val hasInsights = uiState.smoothnessScore != null || uiState.brakingEfficiencyScore != null
            || uiState.peakCorneringG != null || uiState.roadRoughnessIndex != null
            || uiState.elevationAdjustedAvgSpeedMps != null
        if (hasInsights) {
            Spacer(modifier = Modifier.height(16.dp))
            DrivingInsightsCard(uiState, unitSystem, track)
        }

        // Speed chart with inline hero stats (hidden for routes)
        if (CardType.SPEED_PROFILE in visible && uiState.speedProfile.size >= 2 && track.trackCategory != "route") {
            Spacer(modifier = Modifier.height(16.dp))
            SpeedProfileCard(uiState.speedProfile, unitSystem, track)
        }

        // Acceleration profile chart (hidden for routes)
        if (uiState.accelProfile.size >= 2 && track.trackCategory != "route") {
            Spacer(modifier = Modifier.height(16.dp))
            AccelerationProfileCard(uiState.accelProfile, uiState.paceNotes, unitSystem)
        }

        // Elevation chart with inline hero stats
        if (CardType.ELEVATION_PROFILE in visible && uiState.elevationProfile.size >= 2) {
            Spacer(modifier = Modifier.height(16.dp))
            ElevationProfileCard(uiState.elevationProfile, uiState.paceNotes, unitSystem, track)
        }

        // Surface breakdown
        if (track.surfaceBreakdown?.isNotBlank() == true) {
            Spacer(modifier = Modifier.height(16.dp))
            SurfaceBreakdownCard(track)
        }

        // Curvature distribution
        val cd = uiState.curvatureDistribution
        if (CardType.CURVATURE in visible && cd.straight + cd.gentle + cd.moderate + cd.tight + cd.hairpin > 0f) {
            Spacer(modifier = Modifier.height(16.dp))
            CurvatureDistributionCard(cd)
        }

        // Sensor stats (Lateral G, Vertical G, Yaw Rate, Roll Rate)
        if (uiState.sensorStats.hasSensorData) {
            Spacer(modifier = Modifier.height(16.dp))
            SensorStatsCard(uiState.sensorStats, uiState.lateralGProfile, uiState.yawRateProfile, uiState.rollRateProfile)
        }

        // Grip events
        if (uiState.gripEvents.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            GripEventsCard(
                gripEvents = uiState.gripEvents,
                selectedIndex = uiState.selectedGripEventIndex,
                onEventClick = onGripEventClick,
            )
        }

        // Corner analysis
        if (uiState.cornerAnalysis.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            CornerPerformanceCard(
                corners = uiState.cornerAnalysis,
                unitSystem = unitSystem,
            )
        }

        // Pace notes
        if (CardType.PACE_NOTES in visible && uiState.paceNotes.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            PaceNotesList(
                paceNotes = uiState.paceNotes,
                unitSystem = unitSystem,
                isStale = uiState.paceNotesStale,
                isRegenerating = uiState.isGeneratingNotes,
                onRegenerate = onRegeneratePaceNotes,
                onPaceNoteClick = onPaceNoteClick,
                selectedPaceNoteIndex = selectedPaceNoteIndex,
            )
        }

        // Segments
        Spacer(modifier = Modifier.height(16.dp))
        SegmentsCard(
            segments = uiState.segments,
            isDetecting = uiState.isDetectingSegments,
            suggestedSegments = uiState.suggestedSegments,
            highlightedSuggestionIndex = uiState.highlightedSuggestionIndex,
            onDetectSegments = onDetectSegments,
            onSegmentClick = onSegmentClick,
            onViewAllSegments = onViewAllSegments,
            onSaveSegment = onSaveSegment,
            onDismissSuggestions = onDismissSuggestions,
            onToggleHighlight = onToggleHighlightSuggestion,
            unitSystem = unitSystem,
        )

        // Split Time Comparison
        if (uiState.segments.size >= 2 && uiState.segments.any { it.bestTimeMs != null }) {
            Spacer(modifier = Modifier.height(16.dp))
            SplitComparisonCard(segments = uiState.segments)
        }

        // Route History / Previous Attempts
        if (uiState.routeCompletionCount >= 2) {
            Spacer(modifier = Modifier.height(16.dp))
            RouteHistoryCard(
                completionCount = uiState.routeCompletionCount,
                personalBestMs = uiState.personalBestMs,
                averageTimeMs = uiState.averageTimeMs,
                currentTimeMs = track.durationMs,
            )
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun SegmentsCard(
    segments: List<TrackSegmentUi>,
    isDetecting: Boolean,
    suggestedSegments: List<SegmentMatcher.OverlapCandidate>,
    highlightedSuggestionIndex: Int?,
    onDetectSegments: () -> Unit,
    onSegmentClick: (String) -> Unit,
    onViewAllSegments: () -> Unit,
    onSaveSegment: (SegmentMatcher.OverlapCandidate, String) -> Unit,
    onDismissSuggestions: () -> Unit,
    onToggleHighlight: (Int) -> Unit,
    unitSystem: UnitSystem,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
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
                    text = "Segments (${segments.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                if (segments.isNotEmpty()) {
                    TextButton(onClick = onViewAllSegments) {
                        Text("View All")
                    }
                }
            }

            if (segments.isEmpty() && !isDetecting && suggestedSegments.isEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "No segments detected yet. Detect shared road sections across your stints.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // Segment list (show first 5)
            for (segment in segments.take(5)) {
                Spacer(modifier = Modifier.height(8.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSegmentClick(segment.segmentId) }
                        .padding(vertical = 4.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (segment.isFavorite) {
                                    Text(
                                        text = "\u2605 ",
                                        color = MaterialTheme.colorScheme.primary,
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                }
                                Text(
                                    text = segment.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                            Text(
                                text = "${formatDistance(segment.distanceMeters, unitSystem)} \u2022 ${segment.runCount} run${if (segment.runCount != 1) "s" else ""}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = formatElapsedTime(segment.thisRunDurationMs),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            segment.bestTimeMs?.let { best ->
                                Text(
                                    text = "Best: ${formatElapsedTime(best)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (segment.thisRunDurationMs <= best) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                )
                            }
                        }
                    }

                    if (segment.recentRunTimesMs.size >= 2 || segment.consistencyScore != null || segment.latestDeltaFromBest != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (segment.recentRunTimesMs.size >= 2) {
                                Sparkline(
                                    data = segment.recentRunTimesMs.map { it.toFloat() },
                                    modifier = Modifier
                                        .width(60.dp)
                                        .height(24.dp),
                                    color = MaterialTheme.colorScheme.primary,
                                    fillAlpha = 0.15f,
                                )
                            }

                            segment.consistencyScore?.let { score ->
                                val badgeColor = when {
                                    score >= 80 -> MaterialTheme.rallyTraxColors.speedSafe
                                    score >= 55 -> MaterialTheme.rallyTraxColors.fuelWarning
                                    else -> MaterialTheme.rallyTraxColors.speedDanger
                                }
                                ColorBadge(text = "${score}%", color = badgeColor)
                            }

                            segment.latestDeltaFromBest?.let { delta ->
                                if (delta != 0L) {
                                    val isImprovement = delta < 0
                                    val deltaColor = if (isImprovement) {
                                        MaterialTheme.rallyTraxColors.speedSafe
                                    } else {
                                        MaterialTheme.rallyTraxColors.speedDanger
                                    }
                                    val sign = if (isImprovement) "-" else "+"
                                    val absDelta = abs(delta)
                                    val deltaSeconds = absDelta / 1000.0
                                    val deltaText = if (deltaSeconds < 60) {
                                        "${sign}${String.format(java.util.Locale.US, "%.1f", deltaSeconds)}s"
                                    } else {
                                        "${sign}${formatElapsedTime(absDelta)}"
                                    }
                                    ColorBadge(text = deltaText, color = deltaColor)
                                }
                            }
                        }
                    }
                }
            }
            if (segments.size > 5) {
                Text(
                    text = "+ ${segments.size - 5} more",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .padding(top = 4.dp)
                        .clickable { onViewAllSegments() },
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(
                    onClick = onDetectSegments,
                    enabled = !isDetecting,
                ) {
                    Text(if (isDetecting) "Detecting..." else "Detect Segments")
                }
                if (isDetecting) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp))
                }
            }

            // Suggested segments from detection
            if (suggestedSegments.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Suggestions (${suggestedSegments.size})",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    IconButton(onClick = onDismissSuggestions) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = "Dismiss suggestions",
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }

                suggestedSegments.forEachIndexed { index, candidate ->
                    var segmentName by remember { mutableStateOf("Segment ${index + 1}") }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            OutlinedTextField(
                                value = segmentName,
                                onValueChange = { segmentName = it },
                                label = { Text("Name") },
                                singleLine = true,
                                textStyle = MaterialTheme.typography.bodyMedium,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(
                                    onDone = { onSaveSegment(candidate, segmentName) },
                                ),
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = formatDistance(candidate.overlapDistanceM, unitSystem),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        val isHighlighted = highlightedSuggestionIndex == index
                        IconButton(
                            onClick = { onToggleHighlight(index) },
                        ) {
                            Icon(
                                if (isHighlighted) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                                contentDescription = if (isHighlighted) "Hide on map" else "Show on map",
                                modifier = Modifier.size(20.dp),
                                tint = if (isHighlighted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        FilledTonalButton(
                            onClick = { onSaveSegment(candidate, segmentName) },
                        ) {
                            Icon(
                                Icons.Filled.Check,
                                contentDescription = "Save",
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }

                    if (index < suggestedSegments.lastIndex) {
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun SplitComparisonCard(
    segments: List<TrackSegmentUi>,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Split Times",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Header row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Segment",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "Time",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(64.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.End,
                )
                Text(
                    text = "Best",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(64.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.End,
                )
                Text(
                    text = "Delta",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(56.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.End,
                )
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 8.dp),
                color = MaterialTheme.colorScheme.outlineVariant,
            )

            // Segment rows
            for (segment in segments) {
                SplitComparisonRow(segment = segment)
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun SplitComparisonRow(segment: TrackSegmentUi) {
    val bestTime = segment.bestTimeMs
    val deltaMs = segment.latestDeltaFromBest
    val ratio = if (bestTime != null && bestTime > 0 && deltaMs != null) {
        (segment.thisRunDurationMs.toFloat() / bestTime.toFloat()).coerceIn(0f, 2f)
    } else {
        null
    }
    val deltaColor = if (deltaMs != null && deltaMs <= 0) {
        MaterialTheme.rallyTraxColors.speedSafe
    } else {
        MaterialTheme.rallyTraxColors.speedDanger
    }

    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = segment.name,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
            Text(
                text = formatElapsedTime(segment.thisRunDurationMs),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.width(64.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.End,
            )
            Text(
                text = if (bestTime != null) formatElapsedTime(bestTime) else "--",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(64.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.End,
            )
            if (deltaMs != null) {
                Text(
                    text = String.format(java.util.Locale.US, "%+.1fs", deltaMs / 1000.0),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = deltaColor,
                    modifier = Modifier.width(56.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.End,
                )
            } else {
                Spacer(modifier = Modifier.width(56.dp))
            }
        }

        // Visual bar showing performance relative to PB
        if (ratio != null) {
            Spacer(modifier = Modifier.height(4.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction = (ratio / 2f).coerceIn(0.05f, 1f))
                    .height(4.dp)
                    .background(deltaColor, RoundedCornerShape(2.dp)),
            )
        }
    }
}

@Composable
private fun RouteHistoryCard(
    completionCount: Int,
    personalBestMs: Long?,
    averageTimeMs: Long?,
    currentTimeMs: Long,
) {
    androidx.compose.material3.Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Your History",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "$completionCount",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                    Text(
                        text = "Completions",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f),
                    )
                }
                if (personalBestMs != null) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = com.rallytrax.app.util.formatElapsedTime(personalBestMs),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                        )
                        Text(
                            text = "Personal Best",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f),
                        )
                    }
                }
                if (averageTimeMs != null) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = com.rallytrax.app.util.formatElapsedTime(averageTimeMs),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                        )
                        Text(
                            text = "Average",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f),
                        )
                    }
                }
            }
            if (personalBestMs != null && currentTimeMs > 0 && currentTimeMs <= personalBestMs) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "This is your personal best!",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
            } else if (personalBestMs != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Your best: ${com.rallytrax.app.util.formatElapsedTime(personalBestMs)} — beat your record?",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f),
                )
            }
        }
    }
}

// ── Track Info Chips ────────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TrackInfoChips(
    track: com.rallytrax.app.data.local.entity.TrackEntity,
    vehicleName: String?,
    onVehicleClick: (String) -> Unit = {},
    tripName: String? = null,
    onTripClick: () -> Unit = {},
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        vehicleName?.let {
            AssistChip(
                onClick = { track.vehicleId?.let { id -> onVehicleClick(id) } },
                label = { Text(it) },
                leadingIcon = { Icon(Icons.Default.DirectionsCar, contentDescription = null, modifier = Modifier.size(18.dp)) },
            )
        }
        tripName?.let {
            AssistChip(onClick = onTripClick, label = { Text("Trip: $it") })
        }
        track.routeType?.let {
            AssistChip(onClick = {}, label = { Text(it) })
        }
        track.difficultyRating?.let {
            AssistChip(onClick = {}, label = { Text(it) })
        }
        track.primarySurface?.let {
            AssistChip(onClick = {}, label = { Text(it) })
        }
    }
}

// ── Pace Notes List (read-only) ─────────────────────────────────────────────

@Composable
private fun PaceNotesList(
    paceNotes: List<PaceNoteEntity>,
    unitSystem: UnitSystem,
    isStale: Boolean = false,
    isRegenerating: Boolean = false,
    onRegenerate: () -> Unit = {},
    onPaceNoteClick: (Int) -> Unit = {},
    selectedPaceNoteIndex: Int? = null,
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Pace Notes (${paceNotes.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            if (isStale) {
                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    ),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Regenerate for map segment visualization",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        if (isRegenerating) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            FilledTonalButton(
                                onClick = onRegenerate,
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            ) {
                                Text("Update", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            paceNotes.take(20).forEachIndexed { index, note ->
                PaceNoteItem(
                    note = note,
                    unitSystem = unitSystem,
                    isSelected = index == selectedPaceNoteIndex,
                    onClick = { onPaceNoteClick(index) },
                )
            }
            if (paceNotes.size > 20) {
                Text(
                    "+ ${paceNotes.size - 20} more",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 54.dp, top = 4.dp),
                )
            }
        }
    }
}

// ── Card Type & Layer Filtering ─────────────────────────────────────────────

private enum class CardType { SUMMARY, SPEED_PROFILE, ELEVATION_PROFILE, CURVATURE, PACE_NOTES }

private fun visibleCards(activeLayers: Set<MapLayer>): Set<CardType> {
    val extra = activeLayers - MapLayer.ROUTE
    if (extra.isEmpty()) return CardType.entries.toSet()
    return buildSet {
        for (layer in extra) when (layer) {
            MapLayer.SPEED -> { add(CardType.SPEED_PROFILE); add(CardType.SUMMARY) }
            MapLayer.ACCEL -> { add(CardType.SPEED_PROFILE) }
            MapLayer.ELEVATION -> { add(CardType.ELEVATION_PROFILE); add(CardType.SUMMARY) }
            MapLayer.CURVATURE -> { add(CardType.CURVATURE) }
            MapLayer.CALLOUTS -> { add(CardType.PACE_NOTES) }
            MapLayer.SURFACE -> { add(CardType.SUMMARY) }
            else -> {}
        }
    }
}

// ── Utility Functions ───────────────────────────────────────────────────────

/** Maps absolute curvature (deg/m) to a 0–1 fraction for color interpolation.
 *  Aligns with CurvatureDistribution thresholds: 10 deg/m = hairpin = full pink. */
private fun curvatureToFraction(curvatureDegPerM: Double): Float {
    return (curvatureDegPerM / 10.0).coerceIn(0.0, 1.0).toFloat()
}

/** Maps absolute grade (rise/run) to a 0–1 fraction for color interpolation.
 *  ≥10% grade saturates to red; below 0.5% is treated as flat. */
private fun gradeToFraction(grade: Double): Float {
    val absGrade = abs(grade)
    if (absGrade < 0.005) return 0f
    return (absGrade / 0.10).coerceIn(0.0, 1.0).toFloat()
}

private fun formatCurvature(trackPoints: List<TrackPointEntity>): String {
    val withCurvature = trackPoints.mapNotNull { it.curvatureDegPerM }
    if (withCurvature.isEmpty()) return "N/A"
    val avg = withCurvature.map { abs(it) }.average()
    return "%.1f deg/m".format(avg)
}

// ── Corner Performance Card ────────────────────────────────────────────

@Composable
private fun CornerPerformanceCard(
    corners: List<CornerAnalysis>,
    unitSystem: UnitSystem,
) {
    var expanded by remember { mutableStateOf(false) }
    val displayLimit = 10
    val showExpandButton = corners.size > displayLimit
    val displayedCorners = if (expanded) corners else corners.take(displayLimit)

    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Corner Analysis",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(12.dp))

            displayedCorners.forEachIndexed { index, corner ->
                if (index > 0) {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 6.dp),
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )
                }
                CornerRow(corner, unitSystem)
            }

            if (showExpandButton) {
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(
                    onClick = { expanded = !expanded },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        if (expanded) "Show less"
                        else "Show all ${corners.size} corners",
                    )
                }
            }
        }
    }
}

@Composable
private fun CornerRow(corner: CornerAnalysis, unitSystem: UnitSystem) {
    val note = corner.note
    val speedSafe = MaterialTheme.rallyTraxColors.speedSafe
    val speedDanger = MaterialTheme.rallyTraxColors.speedDanger

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            com.rallytrax.app.ui.pacenotes.PaceNoteGlyphWithLabel(
                noteType = note.noteType,
                severity = note.severity,
                sizeDp = 32.dp,
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                note.callText,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            // Speed flow: entry -> min -> exit
            if (corner.entrySpeedMps != null || corner.minSpeedMps != null || corner.exitSpeedMps != null) {
                SpeedFlow(
                    entryMps = corner.entrySpeedMps,
                    minMps = corner.minSpeedMps,
                    exitMps = corner.exitSpeedMps,
                    unitSystem = unitSystem,
                    safeColor = speedSafe,
                    dangerColor = speedDanger,
                )
            }
        }

        // Peak lateral G badge
        corner.peakLateralG?.let { g ->
            Row(
                modifier = Modifier.padding(start = 36.dp, top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Card(
                    shape = RoundedCornerShape(6.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                ) {
                    Text(
                        "${"%.2f".format(g)}g",
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                }
            }
        }

        corner.tip?.let { tip ->
            Text(
                tip,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier
                    .padding(start = 36.dp, top = 4.dp)
                    .background(
                        MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f),
                        RoundedCornerShape(6.dp),
                    )
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
    }
}

@Composable
private fun SpeedFlow(
    entryMps: Double?,
    minMps: Double?,
    exitMps: Double?,
    unitSystem: UnitSystem,
    safeColor: Color,
    dangerColor: Color,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        entryMps?.let {
            Text(
                formatSpeed(it, unitSystem),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        if (entryMps != null && minMps != null) {
            val losing = minMps < entryMps
            Text(
                " \u2192 ",
                style = MaterialTheme.typography.labelSmall,
                color = if (losing) dangerColor else safeColor,
            )
        }
        minMps?.let {
            Text(
                formatSpeed(it, unitSystem),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = dangerColor,
            )
        }
        if (minMps != null && exitMps != null) {
            val gaining = exitMps > minMps
            Text(
                " \u2192 ",
                style = MaterialTheme.typography.labelSmall,
                color = if (gaining) safeColor else dangerColor,
            )
        }
        exitMps?.let {
            val gaining = minMps != null && it > minMps
            Text(
                formatSpeed(it, unitSystem),
                style = MaterialTheme.typography.labelSmall,
                color = if (gaining) safeColor else dangerColor,
            )
        }
    }
}

// ── Sensor Stats Card ──────────────────────────────────────────────────

@Composable
private fun SensorStatsCard(
    sensorStats: SensorStats,
    lateralGProfile: List<LateralGPoint>,
    yawRateProfile: List<YawRatePoint>,
    rollRateProfile: List<RollRatePoint>,
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Sensor Data", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

            // Lateral G chart
            if (lateralGProfile.size >= 2) {
                Spacer(modifier = Modifier.height(12.dp))
                Text("Lateral G-Force", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(4.dp))
                LateralGChart(data = lateralGProfile, modifier = Modifier.fillMaxWidth().height(150.dp))
            }

            // Roll rate chart
            if (rollRateProfile.size >= 2) {
                Spacer(modifier = Modifier.height(12.dp))
                Text("Roll Rate", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(4.dp))
                RollRateChart(data = rollRateProfile, modifier = Modifier.fillMaxWidth().height(150.dp))
            }

            // Hero stats
            HorizontalDivider(Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outlineVariant)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                sensorStats.peakLateralG?.let { g ->
                    StatItem("Peak Lateral G", "${"%.2f".format(g)}g")
                }
                sensorStats.peakVerticalG?.let { g ->
                    StatItem("Peak Vertical G", "${"%.2f".format(g)}g")
                }
            }
            val hasYawOrRoll = sensorStats.maxYawRateDegPerS != null || sensorStats.maxRollRateDegPerS != null
            if (hasYawOrRoll) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    sensorStats.maxYawRateDegPerS?.let { yaw ->
                        StatItem("Max Yaw Rate", "${"%.1f".format(yaw)}\u00B0/s")
                    }
                    sensorStats.maxRollRateDegPerS?.let { roll ->
                        StatItem("Max Roll Rate", "${"%.1f".format(roll)}\u00B0/s")
                    }
                }
            }
        }
    }
}

@Composable
private fun LateralGChart(data: List<LateralGPoint>, modifier: Modifier = Modifier) {
    val lineColor = MaterialTheme.colorScheme.tertiary
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val labelTextSize = with(androidx.compose.ui.platform.LocalDensity.current) { 10.dp.toPx() }
    val leftPadding = with(androidx.compose.ui.platform.LocalDensity.current) { 36.dp.toPx() }
    Canvas(modifier = modifier) {
        if (data.size < 2) return@Canvas
        val maxDist = data.last().distanceFromStart
        val maxG = data.maxOf { it.lateralG }.coerceAtLeast(0.1)
        val paddingTop = 4f
        val chartLeft = leftPadding
        val chartWidth = size.width - chartLeft
        val h = size.height - paddingTop

        fun xFor(d: Double) = (chartLeft + (d / maxDist) * chartWidth).toFloat()
        fun yFor(g: Double) = (paddingTop + h - (g / maxG) * h).toFloat()

        // Y-axis labels
        val paint = android.graphics.Paint().apply {
            color = labelColor.toArgb()
            textSize = labelTextSize
            isAntiAlias = true
            textAlign = android.graphics.Paint.Align.RIGHT
        }
        val labels = listOf(
            "0g" to yFor(0.0),
            "${"%.1f".format(maxG / 2)}g" to yFor(maxG / 2),
            "${"%.1f".format(maxG)}g" to yFor(maxG),
        )
        drawIntoCanvas { canvas ->
            for ((text, y) in labels) {
                canvas.nativeCanvas.drawText(text, chartLeft - 4f, y + labelTextSize / 3f, paint)
            }
        }

        // Draw line
        for (i in 0 until data.size - 1) {
            drawLine(
                color = lineColor,
                start = Offset(xFor(data[i].distanceFromStart), yFor(data[i].lateralG)),
                end = Offset(xFor(data[i + 1].distanceFromStart), yFor(data[i + 1].lateralG)),
                strokeWidth = 2f,
            )
        }
    }
}

@Composable
private fun RollRateChart(data: List<RollRatePoint>, modifier: Modifier = Modifier) {
    val lineColor = MaterialTheme.colorScheme.secondary
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val labelTextSize = with(androidx.compose.ui.platform.LocalDensity.current) { 10.dp.toPx() }
    val leftPadding = with(androidx.compose.ui.platform.LocalDensity.current) { 36.dp.toPx() }
    Canvas(modifier = modifier) {
        if (data.size < 2) return@Canvas
        val maxDist = data.last().distanceFromStart
        val maxRate = data.maxOf { it.rollRateDegPerS }.coerceAtLeast(1.0)
        val paddingTop = 4f
        val chartLeft = leftPadding
        val chartWidth = size.width - chartLeft
        val h = size.height - paddingTop

        fun xFor(d: Double) = (chartLeft + (d / maxDist) * chartWidth).toFloat()
        fun yFor(r: Double) = (paddingTop + h - (r / maxRate) * h).toFloat()

        // Y-axis labels
        val paint = android.graphics.Paint().apply {
            color = labelColor.toArgb()
            textSize = labelTextSize
            isAntiAlias = true
            textAlign = android.graphics.Paint.Align.RIGHT
        }
        val labels = listOf(
            "0\u00B0/s" to yFor(0.0),
            "${"%.0f".format(maxRate / 2)}\u00B0/s" to yFor(maxRate / 2),
            "${"%.0f".format(maxRate)}\u00B0/s" to yFor(maxRate),
        )
        drawIntoCanvas { canvas ->
            for ((text, y) in labels) {
                canvas.nativeCanvas.drawText(text, chartLeft - 4f, y + labelTextSize / 3f, paint)
            }
        }

        // Draw line
        for (i in 0 until data.size - 1) {
            drawLine(
                color = lineColor,
                start = Offset(xFor(data[i].distanceFromStart), yFor(data[i].rollRateDegPerS)),
                end = Offset(xFor(data[i + 1].distanceFromStart), yFor(data[i + 1].rollRateDegPerS)),
                strokeWidth = 2f,
            )
        }
    }
}

@Composable
private fun ColorBadge(text: String, color: Color) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = color,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .background(
                color = color.copy(alpha = 0.12f),
                shape = RoundedCornerShape(4.dp),
            )
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

// ── Grip Events Card ──────────────────────────────────────────────────

@Composable
private fun GripEventsCard(
    gripEvents: List<GripEventDetector.GripEvent>,
    selectedIndex: Int? = null,
    onEventClick: (Int) -> Unit = {},
) {
    var expanded by remember { mutableStateOf(false) }

    // Count by type using the enum values
    val typeCounts = GripEventDetector.GripEventType.entries.associateWith { type ->
        gripEvents.count { it.type == type }
    }.filter { it.value > 0 }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Title row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    imageVector = Icons.Filled.Warning,
                    contentDescription = null,
                    tint = DifficultyOrange,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Grip Events",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "${gripEvents.size}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Summary badges — one per type that has events, colored by type
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                typeCounts.forEach { (type, count) ->
                    ColorBadge("$count ${gripEventBadgeText(type)}", gripEventMarkerColor(type))
                }
            }

            // Expand/collapse toggle
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (expanded) "Hide details" else "Show details · tap to locate on map",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Icon(
                    imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
            }

            // Expandable event list
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(),
                exit = shrinkVertically(),
            ) {
                Column {
                    Spacer(modifier = Modifier.height(8.dp))
                    HorizontalDivider()
                    gripEvents.forEachIndexed { index, event ->
                        GripEventRow(
                            event = event,
                            isSelected = selectedIndex == index,
                            onClick = { onEventClick(index) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GripEventRow(
    event: GripEventDetector.GripEvent,
    isSelected: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val severityColor = when (event.severity) {
        GripEventDetector.Severity.MILD -> DifficultyAmber
        GripEventDetector.Severity.MODERATE -> DifficultyOrange
        GripEventDetector.Severity.SEVERE -> DifficultyRed
    }
    val typeColor = gripEventMarkerColor(event.type)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (isSelected) Modifier.background(
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                    RoundedCornerShape(8.dp),
                ) else Modifier,
            )
            .then(
                if (onClick != null) Modifier
                    .clickable(onClick = onClick)
                    .padding(vertical = 4.dp, horizontal = 4.dp)
                else Modifier.padding(vertical = 4.dp, horizontal = 4.dp),
            ),
    ) {
        // Type dot (colored by event type for map correspondence)
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(typeColor, RoundedCornerShape(5.dp)),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = event.description,
                style = MaterialTheme.typography.bodySmall,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "${String.format("%.0f", event.distanceFromStart)}m from start",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (onClick != null) {
                    Icon(
                        imageVector = Icons.Filled.MyLocation,
                        contentDescription = "Show on map",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = event.severity.name.lowercase()
                .replaceFirstChar { it.uppercase() },
            style = MaterialTheme.typography.labelSmall,
            color = severityColor,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

// ── Grip Event Helpers ──────────────────────────────────────────────────────

/** Returns a color for each grip event type — used for map markers and card dots. */
private fun gripEventMarkerColor(type: GripEventDetector.GripEventType): Color = when (type) {
    GripEventDetector.GripEventType.OVERSTEER -> Color(0xFFE53935)     // red
    GripEventDetector.GripEventType.UNDERSTEER -> Color(0xFFFB8C00)    // orange
    GripEventDetector.GripEventType.ABS_ACTIVATION -> Color(0xFF8E24AA) // purple
    GripEventDetector.GripEventType.TRACTION_LOSS -> Color(0xFFFFB300)  // amber
    GripEventDetector.GripEventType.WHEELSPIN -> Color(0xFF43A047)      // green
    GripEventDetector.GripEventType.CORNER_ENTRY_LOCK -> Color(0xFF1E88E5) // blue
}

/** Returns an OSM marker hue (0–360) for each grip event type. */
private fun gripEventMarkerHue(type: GripEventDetector.GripEventType): Float = when (type) {
    GripEventDetector.GripEventType.OVERSTEER -> 0f         // red
    GripEventDetector.GripEventType.UNDERSTEER -> 30f       // orange
    GripEventDetector.GripEventType.ABS_ACTIVATION -> 280f  // purple
    GripEventDetector.GripEventType.TRACTION_LOSS -> 45f    // amber
    GripEventDetector.GripEventType.WHEELSPIN -> 120f       // green
    GripEventDetector.GripEventType.CORNER_ENTRY_LOCK -> 210f // blue
}

/** Returns a short label for an event — used for OSM marker titles. */
private fun gripEventLabel(event: GripEventDetector.GripEvent): String = when (event.type) {
    GripEventDetector.GripEventType.OVERSTEER -> "Oversteer (${event.severity.name.lowercase()})"
    GripEventDetector.GripEventType.UNDERSTEER -> "Understeer (${event.severity.name.lowercase()})"
    GripEventDetector.GripEventType.ABS_ACTIVATION -> "ABS (${event.severity.name.lowercase()})"
    GripEventDetector.GripEventType.TRACTION_LOSS -> "Traction loss (${event.severity.name.lowercase()})"
    GripEventDetector.GripEventType.WHEELSPIN -> "Wheelspin (${event.severity.name.lowercase()})"
    GripEventDetector.GripEventType.CORNER_ENTRY_LOCK -> "Entry lock (${event.severity.name.lowercase()})"
}

/** Short badge text for each event type. */
private fun gripEventBadgeText(type: GripEventDetector.GripEventType): String = when (type) {
    GripEventDetector.GripEventType.OVERSTEER -> "oversteer"
    GripEventDetector.GripEventType.UNDERSTEER -> "understeer"
    GripEventDetector.GripEventType.ABS_ACTIVATION -> "ABS"
    GripEventDetector.GripEventType.TRACTION_LOSS -> "traction"
    GripEventDetector.GripEventType.WHEELSPIN -> "wheelspin"
    GripEventDetector.GripEventType.CORNER_ENTRY_LOCK -> "entry lock"
}

// ── Trip Picker Dialog ─────────────────────────────────────────────────────

@Composable
private fun TripPickerDialog(
    trips: List<com.rallytrax.app.data.local.entity.TripEntity>,
    currentTripId: String?,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
    onClear: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Trip") },
        text = {
            if (trips.isEmpty()) {
                Text("No trips available. Create a trip from the Profile screen first.")
            } else {
                Column {
                    trips.forEach { trip ->
                        Card(
                            onClick = { onSelect(trip.id) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (trip.id == currentTripId) {
                                    MaterialTheme.colorScheme.secondaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surface
                                },
                            ),
                        ) {
                            Text(
                                text = trip.name,
                                modifier = Modifier.padding(12.dp),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (currentTripId != null) {
                TextButton(onClick = onClear) {
                    Text("Remove from trip")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

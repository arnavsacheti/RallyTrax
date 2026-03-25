package com.rallytrax.app.ui.trackdetail

import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
// ButtonDefaults removed — moved to EditTrackScreen
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
// InputChip removed — moved to EditTrackScreen
import androidx.compose.material3.MaterialTheme
// OutlinedTextField/OutlinedButton removed — moved to EditTrackScreen
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.rememberMarkerState
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import com.rallytrax.app.data.local.entity.NoteType
import com.rallytrax.app.data.local.entity.PaceNoteEntity
import com.rallytrax.app.data.local.entity.TrackPointEntity
import com.rallytrax.app.data.preferences.UnitSystem
import com.rallytrax.app.recording.LatLng
import com.rallytrax.app.ui.map.MapProvider
import com.rallytrax.app.ui.map.OsmMapView
import com.rallytrax.app.ui.map.OsmMarkerData
import com.rallytrax.app.ui.map.OsmPolylineData
import com.rallytrax.app.ui.map.PaceNoteIconRenderer
import com.rallytrax.app.ui.theme.HeatmapCold
import com.rallytrax.app.ui.theme.LayerAccel
import com.rallytrax.app.ui.theme.LayerCallout
import com.rallytrax.app.ui.theme.LayerCurvature
import com.rallytrax.app.ui.theme.LayerDecel
import com.rallytrax.app.ui.theme.LayerElevation
import com.rallytrax.app.ui.theme.LayerSpeedHigh
import com.rallytrax.app.ui.theme.LayerSpeedLow
import com.rallytrax.app.ui.theme.LayerSpeedMid
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
    viewModel: TrackDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val preferences by viewModel.preferences.collectAsStateWithLifecycle()
    val allVehicles by viewModel.allVehicles.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var isMapFullscreen by remember { mutableStateOf(false) }
    var showOverflowMenu by remember { mutableStateOf(false) }

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
                                text = { Text("Export GPX") },
                                onClick = {
                                    showOverflowMenu = false
                                    viewModel.exportGpx(context)
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
                if (points.isNotEmpty()) {
                    Box {
                        TrackMap(
                            points = points,
                            trackPoints = uiState.trackPoints,
                            paceNotes = uiState.paceNotes,
                            activeLayers = uiState.activeLayers,
                            useGoogleMaps = MapProvider.useGoogleMaps(preferences.mapProvider),
                            modifier = Modifier.fillMaxWidth().height(300.dp),
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

                        // Fullscreen toggle button
                        IconButton(
                            onClick = { isMapFullscreen = true },
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(8.dp)
                                .background(
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
                    }

                    // Layer toggle chips
                    LayerToolbar(
                        activeLayers = uiState.activeLayers,
                        onToggle = viewModel::toggleLayer,
                        isRoute = uiState.track?.trackCategory == "route",
                    )

                }

                Spacer(modifier = Modifier.height(8.dp))

                // Compute vehicle name for display
                val vehicleName = remember(uiState.track?.vehicleId, allVehicles) {
                    uiState.track?.vehicleId?.let { vid -> allVehicles.find { it.id == vid } }
                        ?.let { "${it.year} ${it.make} ${it.model}" }
                }

                uiState.track?.let { track ->
                    ViewTab(
                        track = track,
                        uiState = uiState,
                        unitSystem = preferences.unitSystem,
                        activeLayers = uiState.activeLayers,
                        vehicleName = vehicleName,
                        onDetectSegments = { viewModel.detectNewSegments() },
                        onSegmentClick = onSegmentClick,
                        onViewAllSegments = onViewAllSegments,
                    )
                }
            }
        }
    }

    // Fullscreen map overlay
    if (isMapFullscreen) {
        val points = uiState.polylinePoints
        if (points.isNotEmpty()) {
            FullscreenMapDialog(
                points = points,
                trackPoints = uiState.trackPoints,
                paceNotes = uiState.paceNotes,
                activeLayers = uiState.activeLayers,
                useGoogleMaps = MapProvider.useGoogleMaps(preferences.mapProvider),
                isRoute = uiState.track?.trackCategory == "route",
                onToggleLayer = { viewModel.toggleLayer(it) },
                onDismiss = { isMapFullscreen = false },
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
) {
    if (useGoogleMaps) {
        GoogleTrackMap(points, trackPoints, paceNotes, activeLayers, modifier)
    } else {
        OsmTrackMap(points, trackPoints, paceNotes, activeLayers, modifier)
    }
}

@Composable
private fun GoogleTrackMap(
    points: List<LatLng>,
    trackPoints: List<TrackPointEntity>,
    paceNotes: List<PaceNoteEntity>,
    activeLayers: Set<MapLayer>,
    modifier: Modifier = Modifier,
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

    GoogleMap(
        modifier = modifier,
        cameraPositionState = cameraPositionState,
        uiSettings = MapUiSettings(zoomControlsEnabled = true, scrollGesturesEnabled = true, zoomGesturesEnabled = true),
    ) {
        // Base route polyline (dimmed when callouts layer active with colored segments)
        if (points.size >= 2) {
            val baseColor = if (MapLayer.CALLOUTS in activeLayers) Color(0xFF1A73E8).copy(alpha = 0.3f) else Color(0xFF1A73E8)
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

        // Curvature layer: colored segments from green (straight) to red (tight curves)
        if (MapLayer.CURVATURE in activeLayers && trackPoints.size >= 2) {
            val curvatures = trackPoints.mapNotNull { it.curvatureDegPerM?.let { c -> abs(c) } }
            if (curvatures.isNotEmpty()) {
                val maxCurv = curvatures.max().coerceAtLeast(1.0)
                for (i in 0 until trackPoints.size - 1) {
                    val curv = trackPoints[i].curvatureDegPerM?.let { abs(it) } ?: continue
                    val fraction = (curv / maxCurv).coerceIn(0.0, 1.0).toFloat()
                    // Green (straight) -> Red (tight)
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
            // Draw colored segments for features
            paceNotes.filter { it.noteType != NoteType.STRAIGHT && it.segmentStartIndex != null && it.segmentEndIndex != null }
                .forEach { note ->
                    val start = note.segmentStartIndex!!.coerceIn(0, allMapPoints.size - 1)
                    val end = note.segmentEndIndex!!.coerceIn(0, allMapPoints.size - 1)
                    if (end > start) {
                        Polyline(
                            points = allMapPoints.subList(start, end + 1),
                            color = PaceNoteIconRenderer.severityColor(note.noteType, note.severity),
                            width = 12f,
                        )
                    }
                }
            // Rally-style icon markers (skip straights)
            val context = LocalContext.current
            val density = context.resources.displayMetrics.densityDpi
            paceNotes.forEach { note ->
                if (note.noteType == NoteType.STRAIGHT) return@forEach
                val midIdx = if (note.segmentStartIndex != null && note.segmentEndIndex != null) {
                    (note.segmentStartIndex + note.segmentEndIndex) / 2
                } else {
                    note.pointIndex
                }
                if (midIdx in points.indices) {
                    val p = points[midIdx]
                    val bitmap = PaceNoteIconRenderer.createMarkerBitmap(
                        note.noteType, note.severity, note.modifier, density,
                    )
                    Marker(
                        state = rememberMarkerState(position = com.google.android.gms.maps.model.LatLng(p.latitude, p.longitude)),
                        title = note.callText,
                        snippet = "Severity ${note.severity}",
                        icon = BitmapDescriptorFactory.fromBitmap(bitmap),
                        anchor = androidx.compose.ui.geometry.Offset(0.5f, 0.5f),
                    )
                }
            }
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
) {
    val lats = points.map { it.latitude }
    val lngs = points.map { it.longitude }
    val fitBounds = BoundingBox(lats.max(), lngs.max(), lats.min(), lngs.min())

    val context = LocalContext.current
    val density = context.resources.displayMetrics.densityDpi
    val calloutsActive = MapLayer.CALLOUTS in activeLayers

    val polylines = mutableListOf<OsmPolylineData>()
    if (points.size >= 2) {
        val baseColor = if (calloutsActive) Color(0xFF1A73E8).copy(alpha = 0.3f) else Color(0xFF1A73E8)
        polylines.add(OsmPolylineData(points = points.map { GeoPoint(it.latitude, it.longitude) }, color = baseColor, width = 8f))
    }

    // Colored segments for callouts
    if (calloutsActive && points.size >= 2) {
        val geoPoints = points.map { GeoPoint(it.latitude, it.longitude) }
        paceNotes.filter { it.noteType != NoteType.STRAIGHT && it.segmentStartIndex != null && it.segmentEndIndex != null }
            .forEach { note ->
                val start = note.segmentStartIndex!!.coerceIn(0, geoPoints.size - 1)
                val end = note.segmentEndIndex!!.coerceIn(0, geoPoints.size - 1)
                if (end > start) {
                    polylines.add(OsmPolylineData(
                        points = geoPoints.subList(start, end + 1),
                        color = PaceNoteIconRenderer.severityColor(note.noteType, note.severity),
                        width = 12f,
                    ))
                }
            }
    }

    val markers = mutableListOf<OsmMarkerData>()
    if (calloutsActive) {
        paceNotes.forEach { note ->
            if (note.noteType == NoteType.STRAIGHT) return@forEach
            val midIdx = if (note.segmentStartIndex != null && note.segmentEndIndex != null) {
                (note.segmentStartIndex + note.segmentEndIndex) / 2
            } else {
                note.pointIndex
            }
            if (midIdx in points.indices) {
                val bitmap = PaceNoteIconRenderer.createMarkerBitmap(
                    note.noteType, note.severity, note.modifier, density,
                )
                markers.add(OsmMarkerData(GeoPoint(points[midIdx].latitude, points[midIdx].longitude), note.callText, icon = bitmap))
            }
        }
    }

    OsmMapView(
        modifier = modifier,
        fitBounds = fitBounds, polylines = polylines, markers = markers, zoomControlsEnabled = true,
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
            )

            // Exit fullscreen button
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
                    .background(
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

            // Layer toolbar at bottom
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .background(
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                        RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp),
                    ),
            ) {
                LayerToolbar(
                    activeLayers = activeLayers,
                    onToggle = onToggleLayer,
                    isRoute = isRoute,
                )
            }
        }
    }
}

// ── Layer Toolbar ───────────────────────────────────────────────────────────

@Composable
private fun LayerToolbar(activeLayers: Set<MapLayer>, onToggle: (MapLayer) -> Unit, isRoute: Boolean = false) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        LayerChip("Route", MapLayer.ROUTE, activeLayers, onToggle, enabled = false)
        if (!isRoute) {
            LayerChip("Speed", MapLayer.SPEED, activeLayers, onToggle)
            LayerChip("Accel", MapLayer.ACCEL, activeLayers, onToggle)
        }
        LayerChip("Elevation", MapLayer.ELEVATION, activeLayers, onToggle)
        LayerChip("Curve", MapLayer.CURVATURE, activeLayers, onToggle)
        LayerChip("Surface", MapLayer.SURFACE, activeLayers, onToggle)
        LayerChip("Callouts", MapLayer.CALLOUTS, activeLayers, onToggle)
    }
}

@Composable
private fun LayerChip(label: String, layer: MapLayer, active: Set<MapLayer>, onToggle: (MapLayer) -> Unit, enabled: Boolean = true) {
    FilterChip(
        selected = layer in active,
        onClick = { onToggle(layer) },
        label = { Text(label) },
        enabled = enabled,
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

        val fillPath = Path().apply {
            moveTo(xFor(data.first().distanceFromStart), size.height)
            for (point in data) lineTo(xFor(point.distanceFromStart), yFor(point.elevation))
            lineTo(xFor(data.last().distanceFromStart), size.height)
            close()
        }
        drawPath(fillPath, color = LayerElevation.copy(alpha = 0.2f))
        val linePath = Path().apply {
            moveTo(xFor(data.first().distanceFromStart), yFor(data.first().elevation))
            for (point in data) lineTo(xFor(point.distanceFromStart), yFor(point.elevation))
        }
        drawPath(linePath, color = LayerElevation, style = Stroke(width = 3f))
    }
}

@Composable
private fun PaceNoteItem(note: PaceNoteEntity, unitSystem: UnitSystem) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PaceNoteIconRenderer.PaceNoteIcon(
            noteType = note.noteType,
            severity = note.severity,
            modifier = note.modifier,
            sizeDp = 36.dp,
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(note.callText, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Text(formatDistance(note.distanceFromStart, unitSystem), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (note.severity in 1..6) {
            Card(
                shape = RoundedCornerShape(6.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
            ) {
                Text("${note.severity}", modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onTertiaryContainer)
            }
        }
    }
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
    onDetectSegments: () -> Unit = {},
    onSegmentClick: (String) -> Unit = {},
    onViewAllSegments: () -> Unit = {},
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
        TrackInfoChips(track, vehicleName)

        // Speed chart with inline hero stats (hidden for routes)
        if (CardType.SPEED_PROFILE in visible && uiState.speedProfile.size >= 2 && track.trackCategory != "route") {
            Spacer(modifier = Modifier.height(16.dp))
            SpeedProfileCard(uiState.speedProfile, unitSystem, track)
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

        // Pace notes
        if (CardType.PACE_NOTES in visible && uiState.paceNotes.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            PaceNotesList(uiState.paceNotes, unitSystem)
        }

        // Segments
        Spacer(modifier = Modifier.height(16.dp))
        SegmentsCard(
            segments = uiState.segments,
            isDetecting = uiState.isDetectingSegments,
            suggestedCount = uiState.suggestedSegments.size,
            onDetectSegments = onDetectSegments,
            onSegmentClick = onSegmentClick,
            onViewAllSegments = onViewAllSegments,
            unitSystem = unitSystem,
        )

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
    suggestedCount: Int,
    onDetectSegments: () -> Unit,
    onSegmentClick: (String) -> Unit,
    onViewAllSegments: () -> Unit,
    unitSystem: UnitSystem,
) {
    androidx.compose.material3.Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        colors = androidx.compose.material3.CardDefaults.cardColors(
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
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                )
                if (segments.isNotEmpty()) {
                    TextButton(onClick = onViewAllSegments) {
                        Text("View All")
                    }
                }
            }

            if (segments.isEmpty() && !isDetecting) {
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
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSegmentClick(segment.segmentId) }
                        .padding(vertical = 4.dp),
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
                                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
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
private fun TrackInfoChips(track: com.rallytrax.app.data.local.entity.TrackEntity, vehicleName: String?) {
    FlowRow(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        vehicleName?.let {
            AssistChip(onClick = {}, label = { Text(it) })
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
private fun PaceNotesList(paceNotes: List<PaceNoteEntity>, unitSystem: UnitSystem) {
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
            Spacer(modifier = Modifier.height(8.dp))
            paceNotes.take(20).forEach { note ->
                PaceNoteItem(note, unitSystem)
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

private fun speedColor(fraction: Float): Color {
    return if (fraction < 0.5f) {
        lerpColor(LayerSpeedLow, LayerSpeedMid, fraction * 2f)
    } else {
        lerpColor(LayerSpeedMid, LayerSpeedHigh, (fraction - 0.5f) * 2f)
    }
}

private fun lerpColor(start: Color, end: Color, fraction: Float): Color {
    val f = fraction.coerceIn(0f, 1f)
    return Color(
        red = start.red + (end.red - start.red) * f,
        green = start.green + (end.green - start.green) * f,
        blue = start.blue + (end.blue - start.blue) * f,
        alpha = start.alpha + (end.alpha - start.alpha) * f,
    )
}

private fun formatCurvature(trackPoints: List<TrackPointEntity>): String {
    val withCurvature = trackPoints.mapNotNull { it.curvatureDegPerM }
    if (withCurvature.isEmpty()) return "N/A"
    val avg = withCurvature.map { abs(it) }.average()
    return "%.1f deg/m".format(avg)
}

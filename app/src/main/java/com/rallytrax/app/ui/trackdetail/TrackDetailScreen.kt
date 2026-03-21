package com.rallytrax.app.ui.trackdetail

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
    viewModel: TrackDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val preferences by viewModel.preferences.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showAddTagDialog by remember { mutableStateOf(false) }

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
                    IconButton(onClick = { viewModel.exportGpx(context) }) {
                        Icon(Icons.Filled.Share, "Export & Share")
                    }
                    IconButton(onClick = { showDeleteDialog = true }) {
                        Icon(Icons.Filled.Delete, "Delete", tint = MaterialTheme.colorScheme.error)
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
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState()),
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
                    }

                    // Layer toggle chips
                    LayerToolbar(
                        activeLayers = uiState.activeLayers,
                        onToggle = viewModel::toggleLayer,
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // ── Summary Card (2-column grid) ──────────────────────
                uiState.track?.let { track ->
                    SummaryCard(track, uiState, preferences.unitSystem)
                }

                // ── Speed Profile Card ────────────────────────────────
                if (uiState.speedProfile.size >= 2) {
                    Spacer(modifier = Modifier.height(12.dp))
                    SpeedProfileCard(uiState.speedProfile, preferences.unitSystem)
                }

                // ── Elevation Profile Card ────────────────────────────
                if (uiState.elevationProfile.size >= 2) {
                    Spacer(modifier = Modifier.height(12.dp))
                    ElevationProfileCard(uiState.elevationProfile, uiState.paceNotes, preferences.unitSystem)
                }

                // ── Pace Notes Card ───────────────────────────────────
                Spacer(modifier = Modifier.height(12.dp))
                PaceNotesCard(uiState, viewModel, preferences.unitSystem)

                // ── Curvature Distribution Card ───────────────────────
                val cd = uiState.curvatureDistribution
                if (cd.straight + cd.gentle + cd.moderate + cd.tight + cd.hairpin > 0f) {
                    Spacer(modifier = Modifier.height(12.dp))
                    CurvatureDistributionCard(cd)
                }

                // ── Tags Card ─────────────────────────────────────────
                Spacer(modifier = Modifier.height(12.dp))
                TagsCard(uiState, viewModel, onShowAddTagDialog = { showAddTagDialog = true })

                // ── Export Button ─────────────────────────────────────
                Spacer(modifier = Modifier.height(12.dp))
                FilledTonalButton(
                    onClick = { viewModel.exportGpx(context) },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                ) {
                    Icon(Icons.Filled.Share, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Export GPX & Share")
                }

                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Track?") },
            text = { Text("This will permanently delete this track and all its data.") },
            confirmButton = {
                TextButton(onClick = { showDeleteDialog = false; viewModel.deleteTrack { onBack() } }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") } },
        )
    }

    if (showAddTagDialog) {
        var tagText by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddTagDialog = false },
            title = { Text("Add Tag") },
            text = {
                OutlinedTextField(
                    value = tagText, onValueChange = { tagText = it },
                    label = { Text("Tag name") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                        if (tagText.isNotBlank()) { viewModel.addTag(tagText); showAddTagDialog = false }
                    }),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (tagText.isNotBlank()) { viewModel.addTag(tagText); showAddTagDialog = false }
                }) { Text("Add") }
            },
            dismissButton = { TextButton(onClick = { showAddTagDialog = false }) { Text("Cancel") } },
        )
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
) {
    if (useGoogleMaps) {
        GoogleTrackMap(points, trackPoints, paceNotes, activeLayers)
    } else {
        OsmTrackMap(points, trackPoints, paceNotes, activeLayers)
    }
}

@Composable
private fun GoogleTrackMap(
    points: List<LatLng>,
    trackPoints: List<TrackPointEntity>,
    paceNotes: List<PaceNoteEntity>,
    activeLayers: Set<MapLayer>,
) {
    val cameraPositionState = rememberCameraPositionState()
    val boundsBuilder = LatLngBounds.builder()
    points.forEach { boundsBuilder.include(com.google.android.gms.maps.model.LatLng(it.latitude, it.longitude)) }
    cameraPositionState.position = CameraPosition.fromLatLngZoom(boundsBuilder.build().center, 14f)

    GoogleMap(
        modifier = Modifier.fillMaxWidth().height(300.dp),
        cameraPositionState = cameraPositionState,
        uiSettings = MapUiSettings(zoomControlsEnabled = true, scrollGesturesEnabled = true, zoomGesturesEnabled = true),
    ) {
        // Base route polyline (always visible)
        if (points.size >= 2) {
            Polyline(
                points = points.map { com.google.android.gms.maps.model.LatLng(it.latitude, it.longitude) },
                color = Color(0xFF1A73E8), width = 8f,
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

        // Accel/Decel layer: colored dots
        if (MapLayer.ACCEL in activeLayers) {
            for (pt in trackPoints) {
                val accel = pt.accelMps2 ?: continue
                if (abs(accel) < 0.5) continue // noise floor
                val color = if (accel > 0) LayerAccel else LayerDecel
                val alpha = (abs(accel) / 3.0).coerceIn(0.2, 1.0).toFloat()
                Marker(
                    state = rememberMarkerState(position = com.google.android.gms.maps.model.LatLng(pt.lat, pt.lon)),
                    alpha = alpha,
                    icon = BitmapDescriptorFactory.defaultMarker(
                        if (accel > 0) BitmapDescriptorFactory.HUE_AZURE else BitmapDescriptorFactory.HUE_ORANGE,
                    ),
                )
            }
        }

        // Callouts layer
        if (MapLayer.CALLOUTS in activeLayers) {
            paceNotes.forEach { note ->
                val idx = note.pointIndex
                if (idx in points.indices) {
                    val p = points[idx]
                    val hue = when (note.noteType) {
                        NoteType.LEFT -> BitmapDescriptorFactory.HUE_BLUE
                        NoteType.RIGHT -> BitmapDescriptorFactory.HUE_GREEN
                        NoteType.HAIRPIN_LEFT, NoteType.HAIRPIN_RIGHT -> BitmapDescriptorFactory.HUE_RED
                        NoteType.CREST -> BitmapDescriptorFactory.HUE_YELLOW
                        NoteType.DIP -> BitmapDescriptorFactory.HUE_ORANGE
                        NoteType.STRAIGHT -> BitmapDescriptorFactory.HUE_VIOLET
                    }
                    Marker(
                        state = rememberMarkerState(position = com.google.android.gms.maps.model.LatLng(p.latitude, p.longitude)),
                        title = note.callText,
                        snippet = "Severity ${note.severity}",
                        icon = BitmapDescriptorFactory.defaultMarker(hue),
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
) {
    val lats = points.map { it.latitude }
    val lngs = points.map { it.longitude }
    val fitBounds = BoundingBox(lats.max(), lngs.max(), lats.min(), lngs.min())

    val polylines = mutableListOf<OsmPolylineData>()
    if (points.size >= 2) {
        polylines.add(OsmPolylineData(points = points.map { GeoPoint(it.latitude, it.longitude) }, width = 8f))
    }

    val markers = mutableListOf<OsmMarkerData>()
    if (MapLayer.CALLOUTS in activeLayers) {
        paceNotes.forEach { note ->
            val idx = note.pointIndex
            if (idx in points.indices) {
                markers.add(OsmMarkerData(GeoPoint(points[idx].latitude, points[idx].longitude), note.callText))
            }
        }
    }

    OsmMapView(
        modifier = Modifier.fillMaxWidth().height(300.dp),
        fitBounds = fitBounds, polylines = polylines, markers = markers, zoomControlsEnabled = true,
    )
}

// ── Layer Toolbar ───────────────────────────────────────────────────────────

@Composable
private fun LayerToolbar(activeLayers: Set<MapLayer>, onToggle: (MapLayer) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        LayerChip("Route", MapLayer.ROUTE, activeLayers, onToggle, enabled = false)
        LayerChip("Speed", MapLayer.SPEED, activeLayers, onToggle)
        LayerChip("Accel", MapLayer.ACCEL, activeLayers, onToggle)
        LayerChip("Elevation", MapLayer.ELEVATION, activeLayers, onToggle)
        LayerChip("Curve", MapLayer.CURVATURE, activeLayers, onToggle)
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
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                StatItem("Distance", formatDistance(track.distanceMeters, unitSystem))
                StatItem("Duration", formatElapsedTime(track.durationMs))
            }
            HorizontalDivider(Modifier.padding(vertical = 10.dp), color = MaterialTheme.colorScheme.outlineVariant)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                StatItem("Avg Speed", "${formatSpeed(track.avgSpeedMps, unitSystem)} ${speedUnit(unitSystem)}")
                StatItem("Max Speed", "${formatSpeed(track.maxSpeedMps, unitSystem)} ${speedUnit(unitSystem)}")
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
private fun SpeedProfileCard(speedProfile: List<SpeedPoint>, unitSystem: UnitSystem) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Speed Profile", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(12.dp))
            SpeedChart(data = speedProfile, modifier = Modifier.fillMaxWidth().height(120.dp))
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("0", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(formatDistance(speedProfile.last().distanceFromStart, unitSystem), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun SpeedChart(data: List<SpeedPoint>, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        if (data.size < 2) return@Canvas
        val maxDist = data.last().distanceFromStart
        val maxSpeed = data.maxOf { it.speedMps }.coerceAtLeast(1.0)
        val paddingTop = 4f
        val h = size.height - paddingTop

        fun xFor(d: Double) = ((d / maxDist) * size.width).toFloat()
        fun yFor(s: Double) = (paddingTop + h - (s / maxSpeed) * h).toFloat()

        // Draw colored segments
        for (i in 0 until data.size - 1) {
            val fraction = (data[i].speedMps / maxSpeed).toFloat()
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
private fun ElevationProfileCard(elevationProfile: List<ElevationPoint>, paceNotes: List<PaceNoteEntity>, unitSystem: UnitSystem) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Elevation Profile", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(12.dp))
            ElevationChart(data = elevationProfile, modifier = Modifier.fillMaxWidth().height(150.dp))
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("0 km", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(formatDistance(elevationProfile.last().distanceFromStart, unitSystem), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun ElevationChart(data: List<ElevationPoint>, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        if (data.size < 2) return@Canvas
        val maxDist = data.last().distanceFromStart
        val minEle = data.minOf { it.elevation }
        val maxEle = data.maxOf { it.elevation }
        val eleRange = (maxEle - minEle).coerceAtLeast(1.0)
        val paddingTop = 8f
        val chartHeight = size.height - paddingTop

        fun xFor(d: Double) = ((d / maxDist) * size.width).toFloat()
        fun yFor(e: Double) = (paddingTop + chartHeight - ((e - minEle) / eleRange) * chartHeight).toFloat()

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

// ── Pace Notes Card ─────────────────────────────────────────────────────────

@Composable
private fun PaceNotesCard(uiState: TrackDetailUiState, viewModel: TrackDetailViewModel, unitSystem: UnitSystem) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Pace Notes${if (uiState.paceNotes.isNotEmpty()) " (${uiState.paceNotes.size})" else ""}",
                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                )
                IconButton(
                    onClick = { viewModel.regeneratePaceNotes(uiState.selectedSensitivity) },
                    enabled = !uiState.isGeneratingNotes,
                ) {
                    if (uiState.isGeneratingNotes) CircularProgressIndicator(Modifier.size(20.dp))
                    else Icon(Icons.Filled.Refresh, "Regenerate")
                }
            }

            // Sensitivity chips
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Low", "Medium", "High").forEachIndexed { i, label ->
                    FilterChip(
                        selected = uiState.selectedSensitivity == i,
                        onClick = { viewModel.regeneratePaceNotes(i) },
                        label = { Text(label) },
                        enabled = !uiState.isGeneratingNotes,
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (uiState.paceNotes.isEmpty()) {
                Text("No pace notes yet — select a sensitivity to generate",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                // Show up to 10 notes inline, scrollable
                uiState.paceNotes.take(20).forEach { note ->
                    PaceNoteItem(note, unitSystem)
                }
                if (uiState.paceNotes.size > 20) {
                    Text("+ ${uiState.paceNotes.size - 20} more",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 54.dp, top = 4.dp))
                }
            }
        }
    }
}

@Composable
private fun PaceNoteItem(note: PaceNoteEntity, unitSystem: UnitSystem) {
    val iconColor = when (note.noteType) {
        NoteType.LEFT -> Color(0xFF1A73E8)
        NoteType.RIGHT -> Color(0xFF34A853)
        NoteType.HAIRPIN_LEFT, NoteType.HAIRPIN_RIGHT -> Color(0xFFEA4335)
        NoteType.CREST -> Color(0xFFFBBC04)
        NoteType.DIP -> Color(0xFFFF6D00)
        NoteType.STRAIGHT -> Color(0xFF9C27B0)
    }
    val typeIcon = when (note.noteType) {
        NoteType.LEFT -> "L"; NoteType.RIGHT -> "R"
        NoteType.HAIRPIN_LEFT -> "HL"; NoteType.HAIRPIN_RIGHT -> "HR"
        NoteType.CREST -> "CR"; NoteType.DIP -> "DP"; NoteType.STRAIGHT -> "ST"
    }

    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Card(
            modifier = Modifier.size(36.dp), shape = RoundedCornerShape(8.dp),
            colors = CardDefaults.cardColors(containerColor = iconColor.copy(alpha = 0.15f)),
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(typeIcon, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = iconColor)
            }
        }
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

// ── Tags Card ───────────────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TagsCard(uiState: TrackDetailUiState, viewModel: TrackDetailViewModel, onShowAddTagDialog: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Tags", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                AssistChip(onClick = onShowAddTagDialog, label = { Text("Add") },
                    leadingIcon = { Icon(Icons.Filled.Add, null, Modifier.size(16.dp)) })
            }
            if (uiState.tags.isEmpty()) {
                Text("No tags yet", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
            } else {
                Spacer(modifier = Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    uiState.tags.forEach { tag ->
                        InputChip(
                            selected = false, onClick = { }, label = { Text(tag) },
                            trailingIcon = {
                                IconButton(onClick = { viewModel.removeTag(tag) }, modifier = Modifier.size(16.dp)) {
                                    Icon(Icons.Filled.Close, "Remove tag", Modifier.size(14.dp))
                                }
                            },
                            colors = InputChipDefaults.inputChipColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                        )
                    }
                }
            }
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

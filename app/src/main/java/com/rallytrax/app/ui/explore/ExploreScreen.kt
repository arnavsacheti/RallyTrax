package com.rallytrax.app.ui.explore

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.StrokeStyle
import com.google.android.gms.maps.model.StyleSpan
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import com.rallytrax.app.data.local.entity.TrackEntity
import com.rallytrax.app.recording.LatLng
import com.rallytrax.app.ui.components.RallyTraxTopAppBar
import com.rallytrax.app.ui.map.MapProvider
import com.rallytrax.app.ui.map.OsmMapView
import com.rallytrax.app.ui.map.OsmPolylineData
import com.rallytrax.app.ui.theme.HeatmapCold
import com.rallytrax.app.ui.theme.HeatmapHot
import com.rallytrax.app.ui.theme.LayerElevation
import com.rallytrax.app.ui.theme.LayerSpeedHigh
import com.rallytrax.app.ui.theme.LayerSpeedLow
import com.rallytrax.app.ui.theme.LayerSpeedMid
import com.rallytrax.app.util.formatDate
import com.rallytrax.app.util.formatDistance
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExploreScreen(
    focusLat: Double? = null,
    focusLng: Double? = null,
    onNavigateToSettings: () -> Unit = {},
    onViewDetail: (String) -> Unit = {},
    onReplayTrack: (String) -> Unit = {},
    isSignedIn: Boolean = false,
    userPhotoUrl: String? = null,
    onProfileClick: () -> Unit = {},
    viewModel: ExploreViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val preferences by viewModel.preferences.collectAsStateWithLifecycle()

    // Refresh data each time the screen is shown (e.g. after deleting tracks in Library)
    LaunchedEffect(Unit) {
        viewModel.refresh()
    }

    Scaffold(
        topBar = {
            RallyTraxTopAppBar(
                title = "Explore",
                onSettingsClick = onNavigateToSettings,
                isSignedIn = isSignedIn,
                userPhotoUrl = userPhotoUrl,
                onProfileClick = onProfileClick,
            )
        },
    ) { innerPadding ->
        if (uiState.isLoading) {
            Box(Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            Box(Modifier.fillMaxSize().padding(innerPadding)) {
                // Full-screen map
                if (MapProvider.useGoogleMaps(preferences.mapProvider)) {
                    GoogleExploreMap(uiState, viewModel, focusLat, focusLng)
                } else {
                    OsmExploreMap(uiState, focusLat, focusLng)
                }

                if (uiState.trackPolylines.isEmpty()) {
                    // Empty state overlay on top of map
                    Card(
                        modifier = Modifier.align(Alignment.Center).padding(32.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)),
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text("No tracks yet", style = MaterialTheme.typography.titleMedium)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Record a drive to see it here", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                } else {
                    // Track count chip (top-left)
                    Card(
                        modifier = Modifier.align(Alignment.TopStart).padding(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)),
                    ) {
                        Text(
                            "${uiState.trackCount} tracks",
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }

                    // Layer toolbar (bottom, above nav)
                    ExploreLayerToolbar(
                        activeLayer = uiState.activeLayer,
                        onLayerSelected = viewModel::setActiveLayer,
                        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp),
                    )
                }

                // Recenter FAB (bottom-right)
                SmallFloatingActionButton(
                    onClick = { /* Camera re-fit handled by recomposition */ },
                    modifier = Modifier.align(Alignment.BottomEnd).padding(end = 12.dp, bottom = 56.dp),
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Icon(Icons.Filled.MyLocation, "Recenter")
                }
            }
        }
    }

    // Track selection bottom sheet
    uiState.selectedTrack?.let { track ->
        TrackInfoSheet(
            track = track,
            unitSystem = preferences.unitSystem,
            onViewDetail = { onViewDetail(track.id) },
            onReplay = { onReplayTrack(track.id) },
            onDismiss = { viewModel.clearSelection() },
        )
    }
}

// ── Google Maps ─────────────────────────────────────────────────────────────

@Composable
private fun GoogleExploreMap(
    uiState: ExploreUiState,
    viewModel: ExploreViewModel,
    focusLat: Double? = null,
    focusLng: Double? = null,
) {
    val cameraPositionState = rememberCameraPositionState()

    // Set camera position only once on initial load — not on every recomposition.
    // This prevents fighting with user pan/zoom gestures.
    val polylines = uiState.trackPolylines
    LaunchedEffect(focusLat, focusLng, polylines) {
        if (focusLat != null && focusLng != null) {
            cameraPositionState.animate(
                CameraUpdateFactory.newLatLngZoom(
                    com.google.android.gms.maps.model.LatLng(focusLat, focusLng), 14f,
                ),
            )
        } else {
            val allPoints = polylines.flatMap { it.points }
            if (allPoints.isNotEmpty()) {
                val boundsBuilder = LatLngBounds.builder()
                allPoints.forEach { boundsBuilder.include(com.google.android.gms.maps.model.LatLng(it.latitude, it.longitude)) }
                cameraPositionState.animate(
                    CameraUpdateFactory.newLatLngBounds(boundsBuilder.build(), 64),
                )
            }
        }
    }

    // Pre-compute GMS points list per track — stable across layer switches
    val gmsPointsByTrack = remember(polylines) {
        polylines.map { track ->
            track.points.map { com.google.android.gms.maps.model.LatLng(it.latitude, it.longitude) }
        }
    }

    // Pre-compute global stats for layer coloring
    val globalMaxSpeed = remember(polylines) {
        polylines.flatMap { it.speeds }.maxOrNull() ?: 1.0
    }
    val oldestTime = remember(polylines) {
        polylines.minOfOrNull { it.recordedAt } ?: 0L
    }
    val newestTime = remember(polylines) {
        polylines.maxOfOrNull { it.recordedAt } ?: 1L
    }
    val timeRange = remember(oldestTime, newestTime) {
        (newestTime - oldestTime).coerceAtLeast(1L)
    }

    // Pre-compute StyleSpans for speed layer — one list of spans per track
    val speedSpans = remember(polylines, globalMaxSpeed) {
        polylines.map { track ->
            if (track.speeds.size >= 2) {
                val segmentCount = (track.points.size - 1).coerceAtMost(track.speeds.size - 1)
                (0 until segmentCount).map { i ->
                    val fraction = (track.speeds[i] / globalMaxSpeed).coerceIn(0.0, 1.0).toFloat()
                    StyleSpan(StrokeStyle.colorBuilder(speedColor(fraction).toArgb()).build())
                }
            } else {
                emptyList()
            }
        }
    }

    // Pre-compute StyleSpans for elevation layer
    val elevationSpans = remember(polylines) {
        polylines.map { track ->
            if (track.elevations.size >= 2) {
                val minEle = track.elevations.min()
                val maxEle = track.elevations.max()
                val range = (maxEle - minEle).coerceAtLeast(1.0)
                val segmentCount = (track.points.size - 1).coerceAtMost(track.elevations.size - 1)
                (0 until segmentCount).map { i ->
                    val fraction = ((track.elevations[i] - minEle) / range).coerceIn(0.0, 1.0).toFloat()
                    StyleSpan(StrokeStyle.colorBuilder(lerpColor(Color(0xFFCE93D8), Color(0xFF4A148C), fraction).toArgb()).build())
                }
            } else {
                emptyList()
            }
        }
    }

    val primaryColor = MaterialTheme.colorScheme.primary

    GoogleMap(
        modifier = Modifier.fillMaxSize(),
        cameraPositionState = cameraPositionState,
        uiSettings = MapUiSettings(zoomControlsEnabled = false, scrollGesturesEnabled = true, zoomGesturesEnabled = true),
    ) {
        polylines.forEachIndexed { index, track ->
            val gmsPoints = gmsPointsByTrack[index]

            when (uiState.activeLayer) {
                ExploreLayer.DEFAULT -> {
                    Polyline(
                        points = gmsPoints,
                        color = primaryColor.copy(alpha = 0.6f),
                        width = 6f,
                        clickable = true,
                        onClick = { viewModel.selectTrack(track.trackId) },
                    )
                }
                ExploreLayer.SPEED -> {
                    val spans = speedSpans[index]
                    if (spans.isNotEmpty()) {
                        Polyline(
                            points = gmsPoints,
                            spans = spans,
                            width = 10f,
                        )
                    }
                }
                ExploreLayer.ELEVATION -> {
                    val spans = elevationSpans[index]
                    if (spans.isNotEmpty()) {
                        Polyline(
                            points = gmsPoints,
                            spans = spans,
                            width = 10f,
                        )
                    }
                }
                ExploreLayer.RECENCY -> {
                    val recencyFraction = ((track.recordedAt - oldestTime).toFloat() / timeRange).coerceIn(0f, 1f)
                    Polyline(
                        points = gmsPoints,
                        color = lerpColor(Color.Gray, primaryColor, recencyFraction),
                        width = 8f,
                        clickable = true,
                        onClick = { viewModel.selectTrack(track.trackId) },
                    )
                }
                ExploreLayer.HEATMAP -> {
                    Polyline(
                        points = gmsPoints,
                        color = primaryColor.copy(alpha = 0.3f),
                        width = 8f,
                    )
                }
            }
        }
    }
}

// ── OSM Map ─────────────────────────────────────────────────────────────────

@Composable
private fun OsmExploreMap(
    uiState: ExploreUiState,
    focusLat: Double? = null,
    focusLng: Double? = null,
) {
    val polylines = uiState.trackPolylines

    val fitBounds = remember(focusLat, focusLng, polylines) {
        if (focusLat != null && focusLng != null) {
            val delta = 0.01
            BoundingBox(focusLat + delta, focusLng + delta, focusLat - delta, focusLng - delta)
        } else {
            val allPoints = polylines.flatMap { it.points }
            if (allPoints.isNotEmpty()) {
                val lats = allPoints.map { it.latitude }
                val lngs = allPoints.map { it.longitude }
                BoundingBox(lats.max(), lngs.max(), lats.min(), lngs.min())
            } else {
                null
            }
        }
    }

    val osmPolylines = remember(polylines) {
        polylines.map { track ->
            OsmPolylineData(
                points = track.points.map { GeoPoint(it.latitude, it.longitude) },
                width = 6f,
                color = Color(0xFF1A73E8).copy(alpha = 0.6f),
            )
        }
    }

    OsmMapView(
        modifier = Modifier.fillMaxSize(),
        fitBounds = fitBounds,
        polylines = osmPolylines,
        zoomControlsEnabled = false,
        scrollEnabled = true,
    )
}

// ── Layer Toolbar ───────────────────────────────────────────────────────────

@Composable
private fun ExploreLayerToolbar(
    activeLayer: ExploreLayer,
    onLayerSelected: (ExploreLayer) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.padding(horizontal = 12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)),
    ) {
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(selected = activeLayer == ExploreLayer.HEATMAP, onClick = { onLayerSelected(ExploreLayer.HEATMAP) }, label = { Text("Heatmap") })
            FilterChip(selected = activeLayer == ExploreLayer.SPEED, onClick = { onLayerSelected(ExploreLayer.SPEED) }, label = { Text("Speed") })
            FilterChip(selected = activeLayer == ExploreLayer.ELEVATION, onClick = { onLayerSelected(ExploreLayer.ELEVATION) }, label = { Text("Elevation") })
            FilterChip(selected = activeLayer == ExploreLayer.RECENCY, onClick = { onLayerSelected(ExploreLayer.RECENCY) }, label = { Text("Recency") })
        }
    }
}

// ── Track Info Bottom Sheet ─────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TrackInfoSheet(
    track: TrackEntity,
    unitSystem: com.rallytrax.app.data.preferences.UnitSystem,
    onViewDetail: () -> Unit,
    onReplay: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text(track.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(formatDistance(track.distanceMeters, unitSystem), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(formatDate(track.recordedAt), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(modifier = Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FilledTonalButton(onClick = { onDismiss(); onViewDetail() }, modifier = Modifier.weight(1f)) {
                    Text("View Detail")
                }
                FilledTonalButton(onClick = { onDismiss(); onReplay() }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.PlayArrow, null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Replay")
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

// ── Utility ─────────────────────────────────────────────────────────────────

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

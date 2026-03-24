package com.rallytrax.app.ui.replay

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberMarkerState
import com.rallytrax.app.data.local.entity.NoteType
import com.rallytrax.app.replay.ReplayViewModel
import com.rallytrax.app.ui.map.MapProvider
import com.rallytrax.app.ui.map.OsmMapView
import com.rallytrax.app.ui.map.OsmMarkerData
import com.rallytrax.app.ui.map.OsmPolylineData
import com.rallytrax.app.util.formatDistance
import com.rallytrax.app.util.formatSpeed
import com.rallytrax.app.util.speedUnit
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint

private const val DARK_MAP_STYLE = """[
  {"elementType":"geometry","stylers":[{"color":"#212121"}]},
  {"elementType":"labels.icon","stylers":[{"visibility":"off"}]},
  {"elementType":"labels.text.fill","stylers":[{"color":"#757575"}]},
  {"elementType":"labels.text.stroke","stylers":[{"color":"#212121"}]},
  {"featureType":"road","elementType":"geometry.fill","stylers":[{"color":"#2c2c2c"}]},
  {"featureType":"road","elementType":"labels.text.fill","stylers":[{"color":"#8a8a8a"}]},
  {"featureType":"road.highway","elementType":"geometry","stylers":[{"color":"#3c3c3c"}]},
  {"featureType":"water","elementType":"geometry","stylers":[{"color":"#000000"}]},
  {"featureType":"water","elementType":"labels.text.fill","stylers":[{"color":"#3d3d3d"}]}
]"""

@Composable
fun ReplayHudScreen(
    onExit: () -> Unit,
    viewModel: ReplayViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val preferences by viewModel.preferences.collectAsStateWithLifecycle()
    var showExitDialog by remember { mutableStateOf(false) }

    // Force dark status bar
    val view = LocalView.current
    val activity = LocalContext.current as? Activity
    DisposableEffect(Unit) {
        activity?.window?.let { window ->
            val insetsController = WindowCompat.getInsetsController(window, view)
            insetsController.isAppearanceLightStatusBars = false
            insetsController.isAppearanceLightNavigationBars = false
        }
        onDispose {
            activity?.window?.let { window ->
                val insetsController = WindowCompat.getInsetsController(window, view)
                insetsController.isAppearanceLightStatusBars = true
                insetsController.isAppearanceLightNavigationBars = true
            }
        }
    }

    // Keep screen on during replay when enabled
    DisposableEffect(preferences.keepScreenOn) {
        if (preferences.keepScreenOn) {
            activity?.window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            activity?.window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    DisposableEffect(Unit) { onDispose { viewModel.stopReplay() } }

    BackHandler {
        if (uiState.isActive) showExitDialog = true else onExit()
    }

    MaterialTheme(colorScheme = darkColorScheme()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
        ) {
            if (uiState.isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color.White)
                }
            } else if (uiState.error != null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(uiState.error ?: "Error", color = Color.White, style = MaterialTheme.typography.bodyLarge)
                        Spacer(modifier = Modifier.height(16.dp))
                        FilledTonalButton(onClick = onExit) { Text("Go Back") }
                    }
                }
            } else {
                // Full-screen map
                ReplayMap(uiState, viewModel, preferences.mapProvider)

                // ── Thin progress bar at very top ──
                LinearProgressIndicator(
                    progress = { uiState.progressFraction },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .align(Alignment.TopCenter),
                    color = Color(0xFFFF6D00),
                    trackColor = Color.Transparent,
                )

                // ── Top bar ──
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopStart)
                        .padding(top = 48.dp, start = 8.dp, end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { if (uiState.isActive) showExitDialog = true else onExit() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
                    }
                    Spacer(Modifier.weight(1f))
                    Text(uiState.track?.name ?: "", color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.weight(1f))
                    // Mute indicator in top bar
                    if (uiState.isMuted) {
                        Icon(Icons.AutoMirrored.Filled.VolumeOff, "Muted", tint = Color.Red, modifier = Modifier.padding(end = 8.dp))
                    }
                }

                // ── Off-route warning banner (animated from top) ──
                AnimatedVisibility(
                    visible = uiState.isOffRoute && uiState.isActive,
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 96.dp),
                    enter = slideInVertically { -it } + expandVertically(expandFrom = Alignment.Top) + fadeIn(),
                    exit = slideOutVertically { -it } + shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut(),
                ) {
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xCCFF6D00)),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            Icon(Icons.Filled.Warning, null, tint = Color.White)
                            Spacer(Modifier.width(12.dp))
                            Text("OFF ROUTE", color = Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // ── Finish overlay ──
                AnimatedVisibility(
                    visible = uiState.isFinished,
                    modifier = Modifier.align(Alignment.Center),
                    enter = fadeIn(), exit = fadeOut(),
                ) {
                    Card(
                        modifier = Modifier.padding(horizontal = 32.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xCC34A853)),
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("FINISH", color = Color.White, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(8.dp))
                            FilledTonalButton(onClick = onExit) { Text("Done") }
                        }
                    }
                }

                // ── Next-note card (severity-tinted) ──
                if (uiState.isActive && uiState.nextNote != null && !uiState.isFinished) {
                    val note = uiState.nextNote!!
                    val severityBg = when (note.severity) {
                        1, 2 -> Color(0xFF34A853).copy(alpha = 0.15f) // green
                        3, 4 -> Color(0xFFFBBC04).copy(alpha = 0.15f) // amber
                        5, 6 -> Color(0xFFEA4335).copy(alpha = 0.15f) // red
                        else -> Color(0xFF1E1E1E)
                    }

                    Card(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 16.dp, top = 100.dp),
                        colors = CardDefaults.cardColors(containerColor = severityBg),
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            val noteColor = noteTypeColor(note.noteType)
                            Box(
                                Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)).background(noteColor.copy(alpha = 0.25f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(noteTypeAbbrev(note.noteType), color = noteColor, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }
                            Spacer(Modifier.width(12.dp))
                            Text(
                                note.callText, color = Color.White,
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f),
                            )
                            if (uiState.distanceToNextNote < 10000) {
                                Text(
                                    formatDistance(uiState.distanceToNextNote, preferences.unitSystem),
                                    color = Color.White.copy(alpha = 0.7f),
                                    style = MaterialTheme.typography.titleMedium,
                                )
                            }
                        }
                    }
                }

                // ── Bottom floating toolbar ──
                Card(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(start = 12.dp, end = 12.dp, bottom = 24.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xE6121212)),
                    shape = RoundedCornerShape(24.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // Speed display
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                formatSpeed(uiState.currentSpeedMps, preferences.unitSystem),
                                color = Color.White, fontSize = 48.sp, fontWeight = FontWeight.Bold, lineHeight = 48.sp,
                            )
                            Text(speedUnit(preferences.unitSystem), color = Color.White.copy(alpha = 0.5f), style = MaterialTheme.typography.labelSmall)
                        }

                        Spacer(Modifier.weight(1f))

                        // Mute toggle
                        IconButton(onClick = { viewModel.toggleMute() }) {
                            Icon(
                                if (uiState.isMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                                if (uiState.isMuted) "Unmute" else "Mute",
                                tint = if (uiState.isMuted) Color.Red else Color.White,
                            )
                        }

                        // Volume slider (compact, only when unmuted)
                        if (!uiState.isMuted) {
                            Slider(
                                value = uiState.volume,
                                onValueChange = { viewModel.setVolume(it) },
                                modifier = Modifier.width(80.dp),
                                colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color(0xFF1A73E8), inactiveTrackColor = Color.DarkGray),
                            )
                        }

                        Spacer(Modifier.width(8.dp))

                        // End Replay chip
                        if (!uiState.isFinished) {
                            FilledTonalButton(
                                onClick = {
                                    if (uiState.isActive) viewModel.stopReplay() else viewModel.startReplay()
                                },
                                shape = RoundedCornerShape(20.dp),
                            ) {
                                Icon(
                                    if (uiState.isActive) Icons.Filled.Close else Icons.Filled.PlayArrow,
                                    if (uiState.isActive) "End" else "Start",
                                    modifier = Modifier.size(20.dp),
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(if (uiState.isActive) "End" else "Start")
                            }
                        }
                    }
                }
            }
        }
    }

    if (showExitDialog) {
        AlertDialog(
            onDismissRequest = { showExitDialog = false },
            title = { Text("End Replay?") },
            text = { Text("The current replay session will be stopped.") },
            confirmButton = {
                TextButton(onClick = { showExitDialog = false; viewModel.stopReplay(); onExit() }) {
                    Text("End", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { showExitDialog = false }) { Text("Continue") } },
        )
    }
}

// ── Map Composables ─────────────────────────────────────────────────────────

@Composable
private fun ReplayMap(
    uiState: com.rallytrax.app.replay.ReplayUiState,
    viewModel: ReplayViewModel,
    mapProvider: com.rallytrax.app.data.preferences.MapProviderPreference,
) {
    if (MapProvider.useGoogleMaps(mapProvider)) GoogleReplayMap(uiState) else OsmReplayMap(uiState)
}

@Composable
private fun GoogleReplayMap(uiState: com.rallytrax.app.replay.ReplayUiState) {
    val cameraPositionState = rememberCameraPositionState()
    val driverPos = uiState.driverPosition
    if (driverPos != null) {
        cameraPositionState.position = CameraPosition.Builder()
            .target(com.google.android.gms.maps.model.LatLng(driverPos.latitude, driverPos.longitude))
            .zoom(16f).build()
    } else if (uiState.polylinePoints.isNotEmpty()) {
        val b = LatLngBounds.builder()
        uiState.polylinePoints.forEach { b.include(com.google.android.gms.maps.model.LatLng(it.latitude, it.longitude)) }
        cameraPositionState.position = CameraPosition.fromLatLngZoom(b.build().center, 14f)
    }

    GoogleMap(
        modifier = Modifier.fillMaxSize(),
        cameraPositionState = cameraPositionState,
        properties = MapProperties(mapStyleOptions = try { MapStyleOptions(DARK_MAP_STYLE) } catch (_: Exception) { null }),
        uiSettings = MapUiSettings(zoomControlsEnabled = false, compassEnabled = false, mapToolbarEnabled = false,
            myLocationButtonEnabled = false, scrollGesturesEnabled = true, zoomGesturesEnabled = true, rotationGesturesEnabled = true),
    ) {
        if (uiState.polylinePoints.size >= 2) {
            Polyline(
                points = uiState.polylinePoints.map { com.google.android.gms.maps.model.LatLng(it.latitude, it.longitude) },
                color = Color(0xFF1A73E8), width = 12f,
            )
        }
        if (driverPos != null) {
            val driverMarkerState = rememberMarkerState(key = "driver",
                position = com.google.android.gms.maps.model.LatLng(driverPos.latitude, driverPos.longitude))
            driverMarkerState.position = com.google.android.gms.maps.model.LatLng(driverPos.latitude, driverPos.longitude)
            Marker(state = driverMarkerState, title = "You", icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_CYAN))
        }
        uiState.paceNotes.forEach { note ->
            val idx = note.pointIndex
            if (idx in uiState.polylinePoints.indices) {
                val pos = uiState.polylinePoints[idx]
                val hue = when (note.noteType) {
                    NoteType.LEFT -> BitmapDescriptorFactory.HUE_BLUE
                    NoteType.RIGHT -> BitmapDescriptorFactory.HUE_GREEN
                    NoteType.HAIRPIN_LEFT, NoteType.HAIRPIN_RIGHT -> BitmapDescriptorFactory.HUE_RED
                    NoteType.SQUARE_LEFT, NoteType.SQUARE_RIGHT -> BitmapDescriptorFactory.HUE_RED
                    NoteType.CREST, NoteType.SMALL_CREST, NoteType.BIG_CREST -> BitmapDescriptorFactory.HUE_YELLOW
                    NoteType.DIP, NoteType.SMALL_DIP, NoteType.BIG_DIP -> BitmapDescriptorFactory.HUE_ORANGE
                    NoteType.STRAIGHT -> BitmapDescriptorFactory.HUE_VIOLET
                }
                Marker(
                    state = rememberMarkerState(key = "note_${note.id}",
                        position = com.google.android.gms.maps.model.LatLng(pos.latitude, pos.longitude)),
                    title = note.callText,
                    icon = BitmapDescriptorFactory.defaultMarker(hue), alpha = 0.6f,
                )
            }
        }
    }
}

@Composable
private fun OsmReplayMap(uiState: com.rallytrax.app.replay.ReplayUiState) {
    val driverPos = uiState.driverPosition
    val osmPolylines = if (uiState.polylinePoints.size >= 2) {
        listOf(OsmPolylineData(points = uiState.polylinePoints.map { GeoPoint(it.latitude, it.longitude) }))
    } else emptyList()

    val osmMarkers = buildList {
        if (driverPos != null) add(OsmMarkerData(GeoPoint(driverPos.latitude, driverPos.longitude), "You", hue = 180f))
        uiState.paceNotes.forEach { note ->
            val idx = note.pointIndex
            if (idx in uiState.polylinePoints.indices) {
                val pos = uiState.polylinePoints[idx]
                add(OsmMarkerData(GeoPoint(pos.latitude, pos.longitude), note.callText, alpha = 0.6f))
            }
        }
    }

    val followPos = driverPos?.let { GeoPoint(it.latitude, it.longitude) }
    val fitBounds = if (followPos == null && uiState.polylinePoints.isNotEmpty()) {
        val lats = uiState.polylinePoints.map { it.latitude }
        val lngs = uiState.polylinePoints.map { it.longitude }
        BoundingBox(lats.max(), lngs.max(), lats.min(), lngs.min())
    } else null

    OsmMapView(Modifier.fillMaxSize(), polylines = osmPolylines, markers = osmMarkers, darkMode = true, followPosition = followPos, fitBounds = fitBounds)
}

// ── Utilities ───────────────────────────────────────────────────────────────

private fun noteTypeColor(type: NoteType): Color = when (type) {
    NoteType.LEFT -> Color(0xFF1A73E8)
    NoteType.RIGHT -> Color(0xFF34A853)
    NoteType.HAIRPIN_LEFT, NoteType.HAIRPIN_RIGHT -> Color(0xFFEA4335)
    NoteType.SQUARE_LEFT, NoteType.SQUARE_RIGHT -> Color(0xFFEA4335)
    NoteType.CREST, NoteType.SMALL_CREST, NoteType.BIG_CREST -> Color(0xFFFBBC04)
    NoteType.DIP, NoteType.SMALL_DIP, NoteType.BIG_DIP -> Color(0xFFFF6D00)
    NoteType.STRAIGHT -> Color(0xFF9C27B0)
}

private fun noteTypeAbbrev(type: NoteType): String = when (type) {
    NoteType.LEFT -> "L"; NoteType.RIGHT -> "R"
    NoteType.HAIRPIN_LEFT -> "HL"; NoteType.HAIRPIN_RIGHT -> "HR"
    NoteType.SQUARE_LEFT -> "SL"; NoteType.SQUARE_RIGHT -> "SR"
    NoteType.CREST -> "CR"; NoteType.SMALL_CREST -> "sC"; NoteType.BIG_CREST -> "BC"
    NoteType.DIP -> "DP"; NoteType.SMALL_DIP -> "sD"; NoteType.BIG_DIP -> "BD"
    NoteType.STRAIGHT -> "ST"
}

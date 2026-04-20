package com.rallytrax.app.ui.replay

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
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
import com.rallytrax.app.data.local.entity.PaceNoteEntity
import com.rallytrax.app.replay.ReplayViewModel
import com.rallytrax.app.ui.map.MapProvider
import com.rallytrax.app.ui.map.OsmMapView
import com.rallytrax.app.ui.map.OsmMarkerData
import com.rallytrax.app.ui.map.OsmPolylineData
import com.rallytrax.app.ui.map.ColoredSegment
import com.rallytrax.app.ui.map.PaceNoteIconRenderer
import com.rallytrax.app.ui.map.buildColoredSegments
import com.rallytrax.app.util.formatDistance
import com.rallytrax.app.util.formatSpeed
import com.rallytrax.app.util.speedUnit
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import java.util.Locale

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
    val context = LocalContext.current

    var hasLocationPermission by remember {
        mutableStateOf(
            androidx.core.content.ContextCompat.checkSelfPermission(
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
            viewModel.startReplay()
        }
    }

    // Force dark status bar
    val view = LocalView.current
    val activity = LocalActivity.current
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

                // ── Ghost delta timer ──
                if (uiState.hasGhost && uiState.isActive && !uiState.isFinished) {
                    val deltaText = formatDelta(uiState.ghostDeltaMs)
                    val deltaColor = if (uiState.ghostDeltaMs <= 0) Color(0xFF34A853) else Color(0xFFEA4335)
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 84.dp)
                            .background(
                                Color.Black.copy(alpha = 0.6f),
                                RoundedCornerShape(12.dp),
                            )
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                    ) {
                        Text(
                            deltaText,
                            color = deltaColor,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                        )
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
                    val severityBg = PaceNoteIconRenderer.severityColor(note.noteType, note.severity).copy(alpha = 0.15f)
                    val noteTopPadding = if (uiState.isOffRoute) 160.dp else 100.dp

                    Card(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 16.dp, top = noteTopPadding),
                        colors = CardDefaults.cardColors(containerColor = severityBg),
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            com.rallytrax.app.ui.pacenotes.PaceNoteGlyphWithLabel(
                                noteType = note.noteType,
                                severity = note.severity,
                                sizeDp = 44.dp,
                                color = Color.White,
                            )
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
                                    if (uiState.isActive) {
                                        viewModel.stopReplay()
                                    } else if (hasLocationPermission) {
                                        viewModel.startReplay()
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
        val allPoints = uiState.polylinePoints.map { com.google.android.gms.maps.model.LatLng(it.latitude, it.longitude) }
        if (allPoints.size >= 2) {
            // Draw colored segments for pace notes, dimmed base for gaps
            val segments = buildColoredSegments(uiState.polylinePoints.size, uiState.paceNotes)
            for (seg in segments) {
                val start = seg.startIndex.coerceIn(0, allPoints.size - 1)
                val end = seg.endIndex.coerceIn(0, allPoints.size - 1)
                if (end > start) {
                    Polyline(
                        points = allPoints.subList(start, end + 1),
                        color = seg.color,
                        width = if (seg.isFeature) 14f else 10f,
                    )
                }
            }
        }
        if (driverPos != null) {
            val driverMarkerState = rememberMarkerState(key = "driver",
                position = com.google.android.gms.maps.model.LatLng(driverPos.latitude, driverPos.longitude))
            driverMarkerState.position = com.google.android.gms.maps.model.LatLng(driverPos.latitude, driverPos.longitude)
            Marker(state = driverMarkerState, title = "You", icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_CYAN))
        }
        // Ghost marker (personal best position)
        val ghostPos = uiState.ghostPosition
        if (ghostPos != null && uiState.hasGhost) {
            val ghostMarkerState = rememberMarkerState(key = "ghost",
                position = com.google.android.gms.maps.model.LatLng(ghostPos.latitude, ghostPos.longitude))
            ghostMarkerState.position = com.google.android.gms.maps.model.LatLng(ghostPos.latitude, ghostPos.longitude)
            Marker(
                state = ghostMarkerState,
                title = "Ghost (PB)",
                icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE),
                alpha = 0.5f,
            )
        }
        // Rally-style icons for pace notes (skip straights)
        val density = (LocalDensity.current.density * 160f).toInt()
        uiState.paceNotes.forEach { note ->
            if (note.noteType == NoteType.STRAIGHT) return@forEach
            val midIdx = if (note.segmentStartIndex != null && note.segmentEndIndex != null) {
                (note.segmentStartIndex + note.segmentEndIndex) / 2
            } else {
                note.pointIndex
            }
            if (midIdx in uiState.polylinePoints.indices) {
                val pos = uiState.polylinePoints[midIdx]
                val bitmap = PaceNoteIconRenderer.createMarkerBitmap(
                    note.noteType, note.severity, note.modifier, density,
                )
                Marker(
                    state = rememberMarkerState(key = "note_${note.id}",
                        position = com.google.android.gms.maps.model.LatLng(pos.latitude, pos.longitude)),
                    title = note.callText,
                    icon = BitmapDescriptorFactory.fromBitmap(bitmap),
                    anchor = androidx.compose.ui.geometry.Offset(0.5f, 0.5f),
                )
            }
        }
    }
}

@Composable
private fun OsmReplayMap(uiState: com.rallytrax.app.replay.ReplayUiState) {
    val driverPos = uiState.driverPosition
    val density = (LocalDensity.current.density * 160f).toInt()

    // Build colored polyline segments
    val allGeoPoints = uiState.polylinePoints.map { GeoPoint(it.latitude, it.longitude) }
    val osmPolylines = if (allGeoPoints.size >= 2) {
        val segments = buildColoredSegments(uiState.polylinePoints.size, uiState.paceNotes)
        segments.mapNotNull { seg ->
            val start = seg.startIndex.coerceIn(0, allGeoPoints.size - 1)
            val end = seg.endIndex.coerceIn(0, allGeoPoints.size - 1)
            if (end > start) {
                OsmPolylineData(
                    points = allGeoPoints.subList(start, end + 1),
                    color = seg.color,
                    width = if (seg.isFeature) 14f else 10f,
                )
            } else null
        }
    } else emptyList()

    val osmMarkers = buildList {
        if (driverPos != null) add(OsmMarkerData(GeoPoint(driverPos.latitude, driverPos.longitude), "You", hue = 180f))
        val ghostPos = uiState.ghostPosition
        if (ghostPos != null && uiState.hasGhost) {
            add(OsmMarkerData(GeoPoint(ghostPos.latitude, ghostPos.longitude), "Ghost (PB)", hue = 210f))
        }
        uiState.paceNotes.forEach { note ->
            if (note.noteType == NoteType.STRAIGHT) return@forEach
            val midIdx = if (note.segmentStartIndex != null && note.segmentEndIndex != null) {
                (note.segmentStartIndex + note.segmentEndIndex) / 2
            } else {
                note.pointIndex
            }
            if (midIdx in uiState.polylinePoints.indices) {
                val pos = uiState.polylinePoints[midIdx]
                val bitmap = PaceNoteIconRenderer.createMarkerBitmap(
                    note.noteType, note.severity, note.modifier, density,
                )
                add(OsmMarkerData(GeoPoint(pos.latitude, pos.longitude), note.callText, icon = bitmap))
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

// Colored segment building is provided by the shared utility:
// com.rallytrax.app.ui.map.ColoredSegmentBuilder

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

private fun formatDelta(ms: Long): String = String.format(Locale.US, "%+.1fs", ms / 1000.0)

private fun noteTypeAbbrev(type: NoteType): String = when (type) {
    NoteType.LEFT -> "L"; NoteType.RIGHT -> "R"
    NoteType.HAIRPIN_LEFT -> "HL"; NoteType.HAIRPIN_RIGHT -> "HR"
    NoteType.SQUARE_LEFT -> "SL"; NoteType.SQUARE_RIGHT -> "SR"
    NoteType.CREST -> "CR"; NoteType.SMALL_CREST -> "sC"; NoteType.BIG_CREST -> "BC"
    NoteType.DIP -> "DP"; NoteType.SMALL_DIP -> "sD"; NoteType.BIG_DIP -> "BD"
    NoteType.STRAIGHT -> "ST"
}

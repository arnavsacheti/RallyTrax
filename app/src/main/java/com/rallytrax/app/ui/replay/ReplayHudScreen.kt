package com.rallytrax.app.ui.replay

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.maps.android.compose.CameraPositionState
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapType
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberMarkerState
import com.rallytrax.app.data.local.entity.NoteType
import com.rallytrax.app.ui.map.MapProvider
import com.rallytrax.app.ui.map.OsmMapView
import com.rallytrax.app.ui.map.OsmMarkerData
import com.rallytrax.app.ui.map.OsmPolylineData
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import com.rallytrax.app.replay.ReplayViewModel
import com.rallytrax.app.util.formatDistance
import com.rallytrax.app.util.formatSpeed
import com.rallytrax.app.util.speedUnit

// Dark map style JSON for forced-dark mode
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

    // Cleanup on dispose
    DisposableEffect(Unit) {
        onDispose {
            viewModel.stopReplay()
        }
    }

    BackHandler {
        if (uiState.isActive) {
            showExitDialog = true
        } else {
            onExit()
        }
    }

    // Forced dark theme wrapper
    MaterialTheme(colorScheme = darkColorScheme()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
        ) {
            if (uiState.isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = Color.White)
                }
            } else if (uiState.error != null) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = uiState.error ?: "Error",
                            color = Color.White,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        FilledTonalButton(onClick = onExit) {
                            Text("Go Back")
                        }
                    }
                }
            } else {
                // Full-screen map
                ReplayMap(uiState, viewModel)

                // HUD overlays
                Column(
                    modifier = Modifier.fillMaxSize(),
                ) {
                    // Top bar with back button
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 48.dp, start = 8.dp, end = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(
                            onClick = {
                                if (uiState.isActive) showExitDialog = true else onExit()
                            },
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = Color.White,
                            )
                        }
                        Spacer(modifier = Modifier.weight(1f))
                        Text(
                            text = uiState.track?.name ?: "",
                            color = Color.White.copy(alpha = 0.7f),
                            style = MaterialTheme.typography.labelMedium,
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        // Mute toggle
                        IconButton(onClick = { viewModel.toggleMute() }) {
                            Icon(
                                imageVector = if (uiState.isMuted) {
                                    Icons.AutoMirrored.Filled.VolumeOff
                                } else {
                                    Icons.AutoMirrored.Filled.VolumeUp
                                },
                                contentDescription = if (uiState.isMuted) "Unmute" else "Mute",
                                tint = if (uiState.isMuted) Color.Red else Color.White,
                            )
                        }
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    // Off-route warning
                    AnimatedVisibility(
                        visible = uiState.isOffRoute && uiState.isActive,
                        enter = fadeIn(),
                        exit = fadeOut(),
                    ) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 32.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = Color(0xCCFF6D00),
                            ),
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center,
                            ) {
                                Icon(
                                    Icons.Filled.Warning,
                                    contentDescription = null,
                                    tint = Color.White,
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = "OFF ROUTE",
                                    color = Color.White,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                    }

                    // Finish overlay
                    AnimatedVisibility(
                        visible = uiState.isFinished,
                        enter = fadeIn(),
                        exit = fadeOut(),
                    ) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 32.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = Color(0xCC34A853),
                            ),
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Text(
                                    text = "FINISH",
                                    color = Color.White,
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.Bold,
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                FilledTonalButton(onClick = onExit) {
                                    Text("Done")
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    // Bottom HUD panel
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.Black.copy(alpha = 0.85f))
                            .padding(bottom = 32.dp),
                    ) {
                        // Progress bar
                        LinearProgressIndicator(
                            progress = { uiState.progressFraction },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp),
                            color = Color(0xFF1A73E8),
                            trackColor = Color.DarkGray,
                        )

                        // Next note card
                        if (uiState.isActive && uiState.nextNote != null && !uiState.isFinished) {
                            val note = uiState.nextNote!!
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = Color(0xFF1E1E1E),
                                ),
                                shape = RoundedCornerShape(12.dp),
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    // Note type icon
                                    val noteColor = noteTypeColor(note.noteType)
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(noteColor.copy(alpha = 0.2f)),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(
                                            text = noteTypeAbbrev(note.noteType),
                                            color = noteColor,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = note.callText,
                                            color = Color.White,
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.SemiBold,
                                        )
                                    }
                                    if (uiState.distanceToNextNote < 10000) {
                                        Text(
                                            text = formatDistance(uiState.distanceToNextNote, preferences.unitSystem),
                                            color = Color.White.copy(alpha = 0.7f),
                                            style = MaterialTheme.typography.bodyMedium,
                                        )
                                    }
                                }
                            }
                        }

                        // Speed + controls row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            // Speed display
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Text(
                                    text = formatSpeed(uiState.currentSpeedMps, preferences.unitSystem),
                                    color = Color.White,
                                    fontSize = 56.sp,
                                    fontWeight = FontWeight.Bold,
                                    lineHeight = 56.sp,
                                )
                                Text(
                                    text = speedUnit(preferences.unitSystem),
                                    color = Color.White.copy(alpha = 0.6f),
                                    style = MaterialTheme.typography.labelMedium,
                                )
                            }

                            Spacer(modifier = Modifier.weight(1f))

                            // Volume slider (compact)
                            if (!uiState.isMuted) {
                                Slider(
                                    value = uiState.volume,
                                    onValueChange = { viewModel.setVolume(it) },
                                    modifier = Modifier.width(100.dp),
                                    colors = SliderDefaults.colors(
                                        thumbColor = Color.White,
                                        activeTrackColor = Color(0xFF1A73E8),
                                        inactiveTrackColor = Color.DarkGray,
                                    ),
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                            }

                            // Start / Stop button
                            if (!uiState.isFinished) {
                                FilledTonalButton(
                                    onClick = {
                                        if (uiState.isActive) {
                                            viewModel.stopReplay()
                                        } else {
                                            viewModel.startReplay()
                                        }
                                    },
                                    shape = CircleShape,
                                    modifier = Modifier.size(64.dp),
                                ) {
                                    Icon(
                                        imageVector = if (uiState.isActive) {
                                            Icons.Filled.Stop
                                        } else {
                                            Icons.Filled.PlayArrow
                                        },
                                        contentDescription = if (uiState.isActive) "Stop" else "Start",
                                        modifier = Modifier.size(32.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Exit confirmation dialog
    if (showExitDialog) {
        AlertDialog(
            onDismissRequest = { showExitDialog = false },
            title = { Text("End Replay?") },
            text = { Text("The current replay session will be stopped.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showExitDialog = false
                        viewModel.stopReplay()
                        onExit()
                    },
                ) {
                    Text("End", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showExitDialog = false }) {
                    Text("Continue")
                }
            },
        )
    }
}

@Composable
private fun ReplayMap(
    uiState: com.rallytrax.app.replay.ReplayUiState,
    viewModel: ReplayViewModel,
) {
    if (MapProvider.useGoogleMaps) {
        GoogleReplayMap(uiState)
    } else {
        OsmReplayMap(uiState)
    }
}

@Composable
private fun GoogleReplayMap(uiState: com.rallytrax.app.replay.ReplayUiState) {
    val cameraPositionState = rememberCameraPositionState()

    val driverPos = uiState.driverPosition
    if (driverPos != null) {
        cameraPositionState.position = CameraPosition.Builder()
            .target(com.google.android.gms.maps.model.LatLng(driverPos.latitude, driverPos.longitude))
            .zoom(16f)
            .build()
    } else if (uiState.polylinePoints.isNotEmpty()) {
        val boundsBuilder = LatLngBounds.builder()
        uiState.polylinePoints.forEach { p ->
            boundsBuilder.include(
                com.google.android.gms.maps.model.LatLng(p.latitude, p.longitude)
            )
        }
        val bounds = boundsBuilder.build()
        cameraPositionState.position = CameraPosition.fromLatLngZoom(bounds.center, 14f)
    }

    val mapProperties = MapProperties(
        mapStyleOptions = try {
            MapStyleOptions(DARK_MAP_STYLE)
        } catch (_: Exception) {
            null
        },
    )

    GoogleMap(
        modifier = Modifier.fillMaxSize(),
        cameraPositionState = cameraPositionState,
        properties = mapProperties,
        uiSettings = MapUiSettings(
            zoomControlsEnabled = false,
            compassEnabled = false,
            mapToolbarEnabled = false,
            myLocationButtonEnabled = false,
            scrollGesturesEnabled = true,
            zoomGesturesEnabled = true,
            rotationGesturesEnabled = true,
        ),
    ) {
        if (uiState.polylinePoints.size >= 2) {
            Polyline(
                points = uiState.polylinePoints.map {
                    com.google.android.gms.maps.model.LatLng(it.latitude, it.longitude)
                },
                color = Color(0xFF1A73E8),
                width = 12f,
            )
        }

        if (driverPos != null) {
            val driverMarkerState = rememberMarkerState(
                key = "driver",
                position = com.google.android.gms.maps.model.LatLng(
                    driverPos.latitude,
                    driverPos.longitude,
                ),
            )
            driverMarkerState.position = com.google.android.gms.maps.model.LatLng(
                driverPos.latitude,
                driverPos.longitude,
            )
            Marker(
                state = driverMarkerState,
                title = "You",
                icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_CYAN),
            )
        }

        uiState.paceNotes.forEach { note ->
            val pointIdx = note.pointIndex
            if (pointIdx in uiState.polylinePoints.indices) {
                val pos = uiState.polylinePoints[pointIdx]
                val hue = when (note.noteType) {
                    NoteType.LEFT -> BitmapDescriptorFactory.HUE_BLUE
                    NoteType.RIGHT -> BitmapDescriptorFactory.HUE_GREEN
                    NoteType.HAIRPIN_LEFT, NoteType.HAIRPIN_RIGHT -> BitmapDescriptorFactory.HUE_RED
                    NoteType.CREST -> BitmapDescriptorFactory.HUE_YELLOW
                    NoteType.DIP -> BitmapDescriptorFactory.HUE_ORANGE
                    NoteType.STRAIGHT -> BitmapDescriptorFactory.HUE_VIOLET
                }
                val noteMarkerState = rememberMarkerState(
                    key = "note_${note.id}",
                    position = com.google.android.gms.maps.model.LatLng(
                        pos.latitude,
                        pos.longitude,
                    ),
                )
                Marker(
                    state = noteMarkerState,
                    title = note.callText,
                    icon = BitmapDescriptorFactory.defaultMarker(hue),
                    alpha = 0.6f,
                )
            }
        }
    }
}

@Composable
private fun OsmReplayMap(uiState: com.rallytrax.app.replay.ReplayUiState) {
    val driverPos = uiState.driverPosition

    val osmPolylines = if (uiState.polylinePoints.size >= 2) {
        listOf(
            OsmPolylineData(
                points = uiState.polylinePoints.map { GeoPoint(it.latitude, it.longitude) },
            ),
        )
    } else {
        emptyList()
    }

    val osmMarkers = buildList {
        if (driverPos != null) {
            add(
                OsmMarkerData(
                    position = GeoPoint(driverPos.latitude, driverPos.longitude),
                    title = "You",
                    hue = 180f, // Cyan
                ),
            )
        }
        uiState.paceNotes.forEach { note ->
            val pointIdx = note.pointIndex
            if (pointIdx in uiState.polylinePoints.indices) {
                val pos = uiState.polylinePoints[pointIdx]
                add(
                    OsmMarkerData(
                        position = GeoPoint(pos.latitude, pos.longitude),
                        title = note.callText,
                        alpha = 0.6f,
                    ),
                )
            }
        }
    }

    val followPos = driverPos?.let { GeoPoint(it.latitude, it.longitude) }

    val fitBounds = if (followPos == null && uiState.polylinePoints.isNotEmpty()) {
        val lats = uiState.polylinePoints.map { it.latitude }
        val lngs = uiState.polylinePoints.map { it.longitude }
        BoundingBox(
            lats.max(), lngs.max(),
            lats.min(), lngs.min(),
        )
    } else {
        null
    }

    OsmMapView(
        modifier = Modifier.fillMaxSize(),
        polylines = osmPolylines,
        markers = osmMarkers,
        darkMode = true,
        followPosition = followPos,
        fitBounds = fitBounds,
    )
}

private fun noteTypeColor(type: NoteType): Color = when (type) {
    NoteType.LEFT -> Color(0xFF1A73E8)
    NoteType.RIGHT -> Color(0xFF34A853)
    NoteType.HAIRPIN_LEFT, NoteType.HAIRPIN_RIGHT -> Color(0xFFEA4335)
    NoteType.CREST -> Color(0xFFFBBC04)
    NoteType.DIP -> Color(0xFFFF6D00)
    NoteType.STRAIGHT -> Color(0xFF9C27B0)
}

private fun noteTypeAbbrev(type: NoteType): String = when (type) {
    NoteType.LEFT -> "L"
    NoteType.RIGHT -> "R"
    NoteType.HAIRPIN_LEFT -> "HL"
    NoteType.HAIRPIN_RIGHT -> "HR"
    NoteType.CREST -> "CR"
    NoteType.DIP -> "DP"
    NoteType.STRAIGHT -> "ST"
}

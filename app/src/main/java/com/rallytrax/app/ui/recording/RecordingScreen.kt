package com.rallytrax.app.ui.recording

import androidx.activity.compose.BackHandler
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
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
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import com.rallytrax.app.recording.RecordingStatus
import com.rallytrax.app.ui.map.MapProvider
import com.rallytrax.app.ui.map.OsmMapView
import com.rallytrax.app.ui.map.OsmPolylineData
import org.osmdroid.util.GeoPoint
import com.rallytrax.app.util.formatDistance
import com.rallytrax.app.util.formatElapsedTime
import com.rallytrax.app.util.formatSpeed
import com.rallytrax.app.util.speedUnit

@Composable
fun RecordingScreen(
    onTrackSaved: (String) -> Unit,
    viewModel: RecordingViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val status by viewModel.recordingStatus.collectAsStateWithLifecycle()
    val data by viewModel.recordingData.collectAsStateWithLifecycle()
    val preferences by viewModel.preferences.collectAsStateWithLifecycle()
    var showStopDialog by remember { mutableStateOf(false) }

    // Keep screen on during recording when enabled
    val activity = context as? android.app.Activity
    DisposableEffect(preferences.keepScreenOn) {
        if (preferences.keepScreenOn) {
            activity?.window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            activity?.window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    LaunchedEffect(Unit) {
        if (status == RecordingStatus.IDLE) {
            viewModel.startRecording(context)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.navigateToTrackDetail.collect { trackId ->
            onTrackSaved(trackId)
        }
    }

    // Post-recording classification sheet
    val classificationPending by viewModel.classificationPending.collectAsStateWithLifecycle()
    classificationPending?.let { pending ->
        PostRecordingSheet(
            suggestedRouteType = pending.result.suggestedRouteType,
            curvinessScore = pending.result.curvinessScore,
            difficultyRating = pending.result.difficultyRating,
            onAccept = { routeType, difficulty, tags ->
                viewModel.acceptClassification(routeType, difficulty, tags)
            },
            onDismiss = { viewModel.skipClassification() },
        )
    }

    BackHandler(enabled = status == RecordingStatus.RECORDING || status == RecordingStatus.PAUSED) {
        showStopDialog = true
    }

    val isRecording = status == RecordingStatus.RECORDING
    val isPaused = status == RecordingStatus.PAUSED
    val isWaitingForGps = isRecording && data.pointCount == 0 && data.currentLatLng == null

    Box(modifier = Modifier.fillMaxSize()) {
        // Map
        if (MapProvider.useGoogleMaps(preferences.mapProvider)) {
            val cameraPositionState = rememberCameraPositionState {
                position = CameraPosition.fromLatLngZoom(
                    com.google.android.gms.maps.model.LatLng(37.7749, -122.4194), 15f,
                )
            }
            LaunchedEffect(data.currentLatLng) {
                data.currentLatLng?.let { pos ->
                    cameraPositionState.animate(
                        CameraUpdateFactory.newLatLngZoom(
                            com.google.android.gms.maps.model.LatLng(pos.latitude, pos.longitude), 17f,
                        ), 500,
                    )
                }
            }
            GoogleMap(
                modifier = Modifier.fillMaxSize(),
                cameraPositionState = cameraPositionState,
                properties = MapProperties(isMyLocationEnabled = true),
                uiSettings = MapUiSettings(zoomControlsEnabled = false, myLocationButtonEnabled = false, compassEnabled = true),
            ) {
                data.pathSegments.forEach { segment ->
                    if (segment.size >= 2) {
                        Polyline(
                            points = segment.map { com.google.android.gms.maps.model.LatLng(it.latitude, it.longitude) },
                            color = Color(0xFF1A73E8), width = 12f,
                        )
                    }
                }
            }
        } else {
            val osmPolylines = data.pathSegments.mapNotNull { segment ->
                if (segment.size >= 2) OsmPolylineData(points = segment.map { GeoPoint(it.latitude, it.longitude) }) else null
            }
            val followPos = data.currentLatLng?.let { GeoPoint(it.latitude, it.longitude) }
            OsmMapView(
                modifier = Modifier.fillMaxSize(),
                centerLat = 37.7749, centerLng = -122.4194, zoom = 15.0,
                polylines = osmPolylines, followPosition = followPos,
            )
        }

        // Pulsing recording indicator (top-center)
        if (isRecording && !isPaused) {
            val infiniteTransition = rememberInfiniteTransition(label = "pulse")
            val pulseAlpha by infiniteTransition.animateFloat(
                initialValue = 1f, targetValue = 0.3f,
                animationSpec = infiniteRepeatable(tween(800, easing = LinearEasing), RepeatMode.Reverse),
                label = "pulse_alpha",
            )
            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 56.dp)
                    .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .alpha(pulseAlpha)
                        .background(Color.Red, CircleShape),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("REC", color = Color.White, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            }
        }

        // GPS lock loading indicator
        if (isWaitingForGps) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(24.dp))
                    .padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Acquiring GPS...", color = Color.White, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        // Translucent dark stats overlay at bottom
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.8f))
                .padding(top = 16.dp, bottom = 32.dp, start = 16.dp, end = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Speed (Display Large)
            Text(
                text = formatSpeed(data.currentSpeed, preferences.unitSystem),
                style = MaterialTheme.typography.displayLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
            Text(
                text = speedUnit(preferences.unitSystem),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.6f),
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Time and distance
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                RecStatItem("Time", formatElapsedTime(data.elapsedTimeMs))
                RecStatItem("Distance", formatDistance(data.distanceMeters, preferences.unitSystem))
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Controls with animated FAB
            Row(
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Pause/Resume
                FilledIconButton(
                    onClick = {
                        when (status) {
                            RecordingStatus.RECORDING -> viewModel.pauseRecording(context)
                            RecordingStatus.PAUSED -> viewModel.resumeRecording(context)
                            else -> {}
                        }
                    },
                    modifier = Modifier.size(56.dp),
                    shape = CircleShape,
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color(0xFF2C2C2C)),
                ) {
                    Icon(
                        imageVector = if (isPaused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                        contentDescription = if (isPaused) "Resume" else "Pause",
                        modifier = Modifier.size(28.dp),
                        tint = Color.White,
                    )
                }

                // Stop button — morphs from circle to rounded-square
                val stopCornerRadius by animateDpAsState(
                    targetValue = if (isRecording) 16.dp else 28.dp,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
                    label = "stop_corner",
                )
                val stopContainerColor by animateColorAsState(
                    targetValue = if (isRecording) Color(0xFFEA4335) else Color(0xFF2C2C2C),
                    label = "stop_color",
                )

                FilledIconButton(
                    onClick = { showStopDialog = true },
                    modifier = Modifier.size(64.dp),
                    shape = RoundedCornerShape(stopCornerRadius),
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = stopContainerColor),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Stop,
                        contentDescription = "Stop",
                        modifier = Modifier.size(32.dp),
                        tint = Color.White,
                    )
                }
            }

            // Paused indicator
            if (isPaused) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "PAUSED",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color(0xFFFBBC04),
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }

    if (showStopDialog) {
        AlertDialog(
            onDismissRequest = { showStopDialog = false },
            title = { Text("Stop Recording?") },
            text = { Text("Your track will be saved.") },
            confirmButton = {
                TextButton(onClick = { showStopDialog = false; viewModel.stopRecording(context) }) {
                    Text("Stop & Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showStopDialog = false }) { Text("Continue") }
            },
        )
    }
}

@Composable
private fun RecStatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, color = Color.White)
        Text(label, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.6f))
    }
}

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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
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
import com.rallytrax.app.util.formatSpeed
import com.rallytrax.app.util.speedUnit
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.GpsNotFixed
import androidx.compose.material.icons.filled.GpsOff
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Speed
import androidx.core.content.ContextCompat
import com.rallytrax.app.recording.SensorHudData
import com.rallytrax.app.ui.theme.RallyTraxMotion
import com.rallytrax.app.ui.theme.RallyTraxTypeEmphasized
import com.rallytrax.app.ui.theme.ShapeFullRound
import com.rallytrax.app.ui.theme.rallyTraxColors
import com.rallytrax.app.ui.util.FoldingPosture
import com.rallytrax.app.ui.util.rememberFoldingPosture
import androidx.compose.foundation.layout.fillMaxHeight

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

    BackHandler(enabled = status == RecordingStatus.RECORDING || status == RecordingStatus.PAUSED) {
        showStopDialog = true
    }

    val sensorData by viewModel.sensorHudData.collectAsStateWithLifecycle()
    var showSensorHud by remember { mutableStateOf(false) }

    val isRecordingVoice by viewModel.isRecordingVoiceNote.collectAsStateWithLifecycle()

    // Permission launcher for RECORD_AUDIO
    val audioPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            viewModel.toggleVoiceNote(context)
        }
    }

    val isRecording = status == RecordingStatus.RECORDING
    val isPaused = status == RecordingStatus.PAUSED
    val isWaitingForGps = isRecording && data.pointCount == 0 && data.currentLatLng == null

    // Foldable in table-top posture: phone in car cradle, horizontal hinge.
    // Map occupies the top half, stats overlay expands to fill the bottom half,
    // keeping every interactive element clear of the hinge.
    val posture by rememberFoldingPosture()
    val isTableTop = posture == FoldingPosture.TableTop

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
                modifier = if (isTableTop) {
                    Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .fillMaxHeight(0.5f)
                } else {
                    Modifier.fillMaxSize()
                },
                cameraPositionState = cameraPositionState,
                properties = MapProperties(isMyLocationEnabled = true),
                uiSettings = MapUiSettings(zoomControlsEnabled = false, myLocationButtonEnabled = false, compassEnabled = true),
            ) {
                data.pathSegments.forEach { segment ->
                    if (segment.size >= 2) {
                        Polyline(
                            points = segment.map { com.google.android.gms.maps.model.LatLng(it.latitude, it.longitude) },
                            color = MaterialTheme.colorScheme.primary, width = 12f,
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
                modifier = if (isTableTop) {
                    Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .fillMaxHeight(0.5f)
                } else {
                    Modifier.fillMaxSize()
                },
                centerLat = 37.7749, centerLng = -122.4194, zoom = 15.0,
                polylines = osmPolylines, followPosition = followPos,
            )
        }

        // Top bar: Recording indicator + GPS quality badge
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 56.dp)
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Pulsing recording indicator
            if (isRecording && !isPaused && !data.isAutoPaused) {
                val infiniteTransition = rememberInfiniteTransition(label = "pulse")
                val pulseAlpha by infiniteTransition.animateFloat(
                    initialValue = 1f, targetValue = 0.3f,
                    animationSpec = infiniteRepeatable(tween(800, easing = LinearEasing), RepeatMode.Reverse),
                    label = "pulse_alpha",
                )
                Row(
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = 0.6f), ShapeFullRound)
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .alpha(pulseAlpha)
                            .background(MaterialTheme.rallyTraxColors.recordingActive, CircleShape),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("REC", color = Color.White, style = RallyTraxTypeEmphasized.labelMedium)
                }
            } else if (data.isAutoPaused) {
                Row(
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = 0.6f), ShapeFullRound)
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(MaterialTheme.rallyTraxColors.fuelWarning, CircleShape),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("AUTO-PAUSED", color = MaterialTheme.rallyTraxColors.fuelWarning, style = RallyTraxTypeEmphasized.labelMedium)
                }
            } else {
                Spacer(modifier = Modifier.width(1.dp))
            }

            // GPS quality indicator
            GpsQualityBadge(accuracy = data.gpsAccuracy)
        }

        // Sensor HUD overlay
        if (isRecording && showSensorHud) {
            SensorHudOverlay(
                sensorData = sensorData,
                modifier = Modifier.align(Alignment.TopEnd).padding(top = 100.dp, end = 12.dp),
            )
        }

        // Sensor HUD toggle
        if (isRecording) {
            androidx.compose.material3.IconButton(
                onClick = { showSensorHud = !showSensorHud },
                modifier = Modifier.align(Alignment.TopStart).padding(top = 100.dp, start = 12.dp)
                    .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(8.dp)),
            ) {
                Icon(Icons.Filled.Speed, if (showSensorHud) "Hide sensors" else "Show sensors",
                    tint = if (showSensorHud) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.7f))
            }
        }

        // GPS lock loading indicator — stay clear of the hinge in table-top mode
        if (isWaitingForGps) {
            Box(
                modifier = if (isTableTop) {
                    Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 140.dp)
                        .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(24.dp))
                        .padding(24.dp)
                } else {
                    Modifier
                        .align(Alignment.Center)
                        .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(24.dp))
                        .padding(24.dp)
                },
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
                .then(if (isTableTop) Modifier.fillMaxHeight(0.5f) else Modifier)
                .background(Color.Black.copy(alpha = 0.85f))
                .padding(top = 16.dp, bottom = 32.dp, start = 16.dp, end = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = if (isTableTop) Arrangement.Center else Arrangement.Top,
        ) {
            // === SPEED (always visible, above pager) ===
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.Center) {
                com.rallytrax.app.ui.components.MonoText(
                    text = formatSpeed(data.currentSpeed, preferences.unitSystem),
                    fontSize = 92.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = speedUnit(preferences.unitSystem),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White.copy(alpha = 0.65f),
                    modifier = Modifier.padding(bottom = 18.dp),
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Pager pages driven by the active field preset (Settings → Data fields).
            val fieldPreset = remember(preferences.recordingFieldsPreset) {
                RecordingFieldPreset.fromName(preferences.recordingFieldsPreset)
            }
            val pageCount = fieldPreset.pages.size
            val pagerState = rememberPagerState(pageCount = { pageCount })
            if (pageCount > 1) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    repeat(pageCount) { page ->
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .background(
                                    if (page == pagerState.currentPage) Color.White else Color.White.copy(alpha = 0.3f),
                                    CircleShape,
                                ),
                        )
                        if (page < pageCount - 1) Spacer(modifier = Modifier.width(6.dp))
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Swipeable metric pages
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxWidth().height(60.dp),
            ) { page ->
                val fields = fieldPreset.pages.getOrNull(page) ?: return@HorizontalPager
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    fields.forEach { field ->
                        MetricColumn(
                            value = field.format(data, sensorData, preferences),
                            label = field.label,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Controls row
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Segment marker button
                FilledIconButton(
                    onClick = { viewModel.markSegment(context) },
                    modifier = Modifier.size(44.dp),
                    shape = CircleShape,
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Flag,
                        contentDescription = "Mark Segment",
                        modifier = Modifier.size(20.dp),
                        tint = Color.White,
                    )
                }

                // Voice note button
                FilledIconButton(
                    onClick = {
                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
                            == PackageManager.PERMISSION_GRANTED
                        ) {
                            viewModel.toggleVoiceNote(context)
                        } else {
                            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    },
                    modifier = Modifier.size(44.dp),
                    shape = CircleShape,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = if (isRecordingVoice) MaterialTheme.rallyTraxColors.recordingActive else MaterialTheme.colorScheme.surfaceContainerHighest,
                    ),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Mic,
                        contentDescription = "Voice note",
                        modifier = Modifier.size(20.dp),
                        tint = Color.White,
                    )
                }

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
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest),
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
                    animationSpec = RallyTraxMotion.fastSpatial(),
                    label = "stop_corner",
                )
                val stopContainerColor by animateColorAsState(
                    targetValue = if (isRecording) MaterialTheme.rallyTraxColors.fuelCritical else MaterialTheme.colorScheme.surfaceContainerHighest,
                    animationSpec = RallyTraxMotion.fastEffects(),
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
                    color = MaterialTheme.rallyTraxColors.fuelWarning,
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
private fun MetricColumn(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = RallyTraxTypeEmphasized.titleLarge, color = Color.White)
        Text(label, style = RallyTraxTypeEmphasized.labelSmall, color = Color.White.copy(alpha = 0.7f))
    }
}

@Composable
private fun GpsQualityBadge(accuracy: Float?) {
    val gpsColor = when {
        accuracy == null -> Color.Gray
        accuracy < 10f -> MaterialTheme.rallyTraxColors.speedSafe
        accuracy < 25f -> MaterialTheme.rallyTraxColors.fuelWarning
        else -> MaterialTheme.rallyTraxColors.fuelCritical
    }
    val gpsIcon = when {
        accuracy == null -> Icons.Filled.GpsOff
        accuracy < 25f -> Icons.Filled.GpsFixed
        else -> Icons.Filled.GpsNotFixed
    }
    val gpsLabel = when {
        accuracy == null -> "No GPS"
        else -> "${accuracy.toInt()}m"
    }
    // Signal-strength bars: 0 (no GPS), 1 (poor), 2 (fair), 3 (good)
    val bars = when {
        accuracy == null -> 0
        accuracy < 10f -> 3
        accuracy < 25f -> 2
        else -> 1
    }

    Row(
        modifier = Modifier
            .background(Color.Black.copy(alpha = 0.6f), ShapeFullRound)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            imageVector = gpsIcon,
            contentDescription = "GPS quality",
            modifier = Modifier.size(14.dp),
            tint = gpsColor,
        )
        // Signal-strength bars
        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(1.5.dp),
        ) {
            listOf(5.dp, 8.dp, 11.dp).forEachIndexed { index, barHeight ->
                val isActive = index < bars
                Box(
                    modifier = Modifier
                        .width(2.5.dp)
                        .height(barHeight)
                        .background(
                            color = if (isActive) gpsColor else Color.White.copy(alpha = 0.25f),
                            shape = RoundedCornerShape(1.dp),
                        ),
                )
            }
        }
        Text(
            text = gpsLabel,
            style = RallyTraxTypeEmphasized.labelSmall,
            color = Color.White,
        )
    }
}

@Composable
private fun SensorHudOverlay(sensorData: SensorHudData, modifier: Modifier = Modifier) {
    val hapticFeedback = LocalHapticFeedback.current
    val latG = sensorData.lateralG
    val longG = sensorData.longitudinalG

    LaunchedEffect(latG) {
        if (latG != null && latG > 0.7) {
            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }
    LaunchedEffect(longG) {
        if (longG != null && longG < -0.6) {
            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }

    val latGAlertActive = latG != null && latG > SensorHudData.LATERAL_G_ALERT_THRESHOLD
    val latGRowBg by animateColorAsState(
        targetValue = if (latGAlertActive) MaterialTheme.rallyTraxColors.fuelCritical.copy(alpha = 0.35f) else Color.Transparent,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "lat_g_alert_bg",
    )

    Column(
        modifier = modifier.background(Color.Black.copy(alpha = 0.75f), RoundedCornerShape(12.dp)).padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (sensorData.alertCount > 0) {
            Text(
                text = "\u26A0 ${sensorData.alertCount} alerts",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.rallyTraxColors.fuelWarning,
                modifier = Modifier.align(Alignment.End),
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(latGRowBg, RoundedCornerShape(4.dp)),
        ) {
            SensorRow("Lat G", latG?.let { String.format(java.util.Locale.US, "%.2f", it) } ?: "\u2014",
                when { latG == null -> Color.Gray; latG < 0.3 -> MaterialTheme.rallyTraxColors.speedSafe; latG < 0.5 -> MaterialTheme.rallyTraxColors.fuelWarning; else -> MaterialTheme.rallyTraxColors.fuelCritical })
        }

        SensorRow(if (longG != null && longG < -0.05) "Brake" else "Accel",
            longG?.let { String.format(java.util.Locale.US, "%.2f", kotlin.math.abs(it)) } ?: "\u2014",
            when { longG == null -> Color.Gray; longG > 0.05 -> MaterialTheme.rallyTraxColors.speedSafe; longG < -0.05 -> MaterialTheme.rallyTraxColors.fuelCritical; else -> Color.White.copy(alpha = 0.5f) })

        val brakingFraction = kotlin.math.abs(longG ?: 0.0).toFloat().coerceIn(0f, 1f)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Color.DarkGray),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(brakingFraction)
                    .height(4.dp)
                    .background(if (brakingFraction > 0.4f) MaterialTheme.rallyTraxColors.fuelCritical else MaterialTheme.rallyTraxColors.fuelWarning),
            )
        }

        val vertG = sensorData.verticalG
        SensorRow("Vrt G", vertG?.let { String.format(java.util.Locale.US, "%.2f", it) } ?: "\u2014", Color.White.copy(alpha = 0.7f))
    }
}

@Composable
private fun SensorRow(label: String, value: String, color: Color) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(6.dp).background(color, CircleShape))
        Text(value, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.width(40.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.5f))
    }
}

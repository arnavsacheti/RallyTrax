package com.rallytrax.app.ui.trackdetail

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rallytrax.app.ui.map.OsmMapView
import com.rallytrax.app.ui.map.OsmPolylineData
import com.rallytrax.app.util.formatDateTime
import com.rallytrax.app.util.formatDistance
import com.rallytrax.app.util.formatElapsedTime
import com.rallytrax.app.util.formatSpeed
import com.rallytrax.app.util.speedUnit
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CropStintScreen(
    onBack: () -> Unit,
    onCropComplete: () -> Unit,
    viewModel: CropStintViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val preferences by viewModel.preferences.collectAsStateWithLifecycle()
    var showConfirmDialog by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.cropComplete) {
        if (uiState.cropComplete) onCropComplete()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Crop Stint") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { innerPadding ->
        if (uiState.isLoading) {
            Box(Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        if (uiState.points.size < 2) {
            Box(Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                Text("Not enough track points to crop.", style = MaterialTheme.typography.bodyLarge)
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState()),
        ) {
            SpeedProfileChart(
                speeds = uiState.speedProfile,
                startFraction = uiState.startIndex.toFloat() / uiState.points.lastIndex,
                endFraction = uiState.endIndex.toFloat() / uiState.points.lastIndex,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .padding(horizontal = 16.dp),
            )

            Spacer(Modifier.height(8.dp))

            val lastIdx = uiState.points.lastIndex.toFloat()
            val rangeStart = uiState.startIndex / lastIdx
            val rangeEnd = uiState.endIndex / lastIdx

            RangeSlider(
                value = rangeStart..rangeEnd,
                onValueChange = { range ->
                    viewModel.updateRange(range.start, range.endInclusive)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = formatDateTime(uiState.startTimestamp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = formatDateTime(uiState.endTimestamp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(16.dp))

            CropMapPreview(
                points = uiState.points,
                startIndex = uiState.startIndex,
                endIndex = uiState.endIndex,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .padding(horizontal = 16.dp),
            )

            Spacer(Modifier.height(16.dp))

            CropStatsCard(
                distance = formatDistance(uiState.previewDistance, preferences.unitSystem),
                duration = formatElapsedTime(uiState.previewDuration),
                avgSpeed = "${formatSpeed(uiState.previewAvgSpeed, preferences.unitSystem)} ${speedUnit(preferences.unitSystem)}",
                maxSpeed = "${formatSpeed(uiState.previewMaxSpeed, preferences.unitSystem)} ${speedUnit(preferences.unitSystem)}",
                pointsKept = (uiState.endIndex - uiState.startIndex + 1),
                totalPoints = uiState.totalPoints,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            )

            Spacer(Modifier.height(24.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = onBack,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Cancel")
                }
                Button(
                    onClick = { showConfirmDialog = true },
                    modifier = Modifier.weight(1f),
                    enabled = !uiState.isCropping &&
                        (uiState.startIndex > 0 || uiState.endIndex < uiState.points.lastIndex),
                ) {
                    if (uiState.isCropping) {
                        CircularProgressIndicator(
                            modifier = Modifier.height(20.dp).width(20.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Text("Crop")
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }

    if (showConfirmDialog) {
        val removed = uiState.totalPoints - (uiState.endIndex - uiState.startIndex + 1)
        AlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            title = { Text("Crop this stint?") },
            text = {
                Text(
                    "This will permanently remove $removed GPS points and recalculate stats. " +
                        "Pace notes and segment runs will be cleared.\n\nThis cannot be undone.",
                )
            },
            confirmButton = {
                Button(onClick = {
                    showConfirmDialog = false
                    viewModel.cropStint()
                }) {
                    Text("Crop")
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDialog = false }) {
                    Text("Cancel")
                }
            },
        )
    }
}

@Composable
private fun SpeedProfileChart(
    speeds: List<Float>,
    startFraction: Float,
    endFraction: Float,
    modifier: Modifier = Modifier,
) {
    if (speeds.size < 2) return

    val selectedColor = MaterialTheme.colorScheme.primary
    val dimmedColor = MaterialTheme.colorScheme.outlineVariant
    val fillAlpha = 0.15f

    Canvas(modifier = modifier) {
        val maxVal = speeds.max().coerceAtLeast(1f)
        val stepX = size.width / (speeds.size - 1).toFloat()
        val paddingY = 2f

        fun yForValue(value: Float): Float {
            val normalized = value / maxVal
            return size.height - paddingY - normalized * (size.height - paddingY * 2)
        }

        val startX = startFraction * size.width
        val endX = endFraction * size.width

        val fillPath = Path().apply {
            moveTo(0f, size.height)
            speeds.forEachIndexed { i, value ->
                lineTo(i * stepX, yForValue(value))
            }
            lineTo((speeds.size - 1) * stepX, size.height)
            close()
        }
        drawPath(fillPath, dimmedColor.copy(alpha = fillAlpha), style = Fill)

        for (i in 0 until speeds.size - 1) {
            val x1 = i * stepX
            val x2 = (i + 1) * stepX
            val inRange = x1 >= startX && x2 <= endX
            drawLine(
                color = if (inRange) selectedColor else dimmedColor,
                start = Offset(x1, yForValue(speeds[i])),
                end = Offset(x2, yForValue(speeds[i + 1])),
                strokeWidth = if (inRange) 2.5f else 1.5f,
            )
        }
    }
}

@Composable
private fun CropMapPreview(
    points: List<com.rallytrax.app.data.local.entity.TrackPointEntity>,
    startIndex: Int,
    endIndex: Int,
    modifier: Modifier = Modifier,
) {
    val selectedPoints = remember(points, startIndex, endIndex) {
        if (points.isEmpty() || startIndex > endIndex) emptyList()
        else points.subList(startIndex, (endIndex + 1).coerceAtMost(points.size))
    }

    if (selectedPoints.size < 2) {
        Box(
            modifier = modifier.background(
                MaterialTheme.colorScheme.surfaceContainerLow,
                RoundedCornerShape(12.dp),
            ),
            contentAlignment = Alignment.Center,
        ) {
            Text("Select at least 2 points", style = MaterialTheme.typography.bodySmall)
        }
        return
    }

    val geoPoints = remember(selectedPoints) {
        selectedPoints.map { GeoPoint(it.lat, it.lon) }
    }

    val bounds = remember(geoPoints) {
        var north = -90.0
        var south = 90.0
        var east = -180.0
        var west = 180.0
        for (p in geoPoints) {
            if (p.latitude > north) north = p.latitude
            if (p.latitude < south) south = p.latitude
            if (p.longitude > east) east = p.longitude
            if (p.longitude < west) west = p.longitude
        }
        val latPad = (north - south).coerceAtLeast(0.001) * 0.15
        val lonPad = (east - west).coerceAtLeast(0.001) * 0.15
        BoundingBox(north + latPad, east + lonPad, south - latPad, west - lonPad)
    }

    val darkMode = isSystemInDarkTheme()

    OsmMapView(
        modifier = modifier,
        polylines = listOf(
            OsmPolylineData(points = geoPoints, color = MaterialTheme.colorScheme.primary),
        ),
        fitBounds = bounds,
        darkMode = darkMode,
        scrollEnabled = false,
        zoomControlsEnabled = false,
    )
}

@Composable
private fun CropStatsCard(
    distance: String,
    duration: String,
    avgSpeed: String,
    maxSpeed: String,
    pointsKept: Int,
    totalPoints: Int,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Cropped Stats Preview",
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                StatItem("Distance", distance, Modifier.weight(1f))
                StatItem("Duration", duration, Modifier.weight(1f))
            }
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                StatItem("Avg Speed", avgSpeed, Modifier.weight(1f))
                StatItem("Max Speed", maxSpeed, Modifier.weight(1f))
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "$pointsKept / $totalPoints points kept",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun StatItem(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

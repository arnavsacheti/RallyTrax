package com.rallytrax.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.rallytrax.app.data.local.dao.LatLonSpeedProjection
import com.rallytrax.app.data.local.entity.TrackEntity
import com.rallytrax.app.ui.theme.LayerSpeedMid
import com.rallytrax.app.ui.theme.speedColor
import kotlin.math.max

/**
 * Compact speed-colored visualization of a recorded GPS trace.
 *
 * Projects lat/lon into the canvas using the track's pre-computed bounding box
 * so every thumbnail lays out identically regardless of track orientation. Each
 * segment is stroked with a color interpolated from the point's speed fraction
 * against the track's maxSpeedMps.
 *
 * Falls back to a single [LayerSpeedMid] polyline when speed data is missing
 * (legacy tracks). Renders nothing when the track has fewer than 2 points.
 */
@Composable
fun TrackThumbnail(
    track: TrackEntity,
    points: List<LatLonSpeedProjection>,
    modifier: Modifier = Modifier,
    height: Dp = 96.dp,
    strokeWidthPx: Float = 4f,
) {
    Box(
        modifier = modifier
            .height(height)
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        if (points.size < 2) return@Box

        val north = track.boundingBoxNorthLat
        val south = track.boundingBoxSouthLat
        val east = track.boundingBoxEastLon
        val west = track.boundingBoxWestLon
        val latRange = (north - south).coerceAtLeast(1e-6)
        val lonRange = (east - west).coerceAtLeast(1e-6)
        val maxSpeed = track.maxSpeedMps.coerceAtLeast(0.1)
        val hasSpeed = remember(points) { points.any { it.speed != null } }

        Canvas(modifier = Modifier.fillMaxSize()) {
            val padding = 8f
            val contentWidth = (size.width - padding * 2).coerceAtLeast(1f)
            val contentHeight = (size.height - padding * 2).coerceAtLeast(1f)

            // Preserve aspect ratio of the track bounding box inside the content area.
            val trackAspect = lonRange / latRange
            val canvasAspect = contentWidth / contentHeight
            val drawWidth: Float
            val drawHeight: Float
            if (trackAspect > canvasAspect) {
                drawWidth = contentWidth
                drawHeight = contentWidth / trackAspect.toFloat()
            } else {
                drawHeight = contentHeight
                drawWidth = contentHeight * trackAspect.toFloat()
            }
            val offsetX = padding + (contentWidth - drawWidth) / 2f
            val offsetY = padding + (contentHeight - drawHeight) / 2f

            fun project(lat: Double, lon: Double): Offset {
                val nx = ((lon - west) / lonRange).toFloat().coerceIn(0f, 1f)
                val ny = ((north - lat) / latRange).toFloat().coerceIn(0f, 1f)
                return Offset(offsetX + nx * drawWidth, offsetY + ny * drawHeight)
            }

            for (i in 0 until points.size - 1) {
                val a = points[i]
                val b = points[i + 1]
                val segmentSpeed = a.speed ?: b.speed
                val color: Color = if (!hasSpeed || segmentSpeed == null) {
                    LayerSpeedMid
                } else {
                    val fraction = (segmentSpeed / maxSpeed).toFloat().coerceIn(0f, 1f)
                    speedColor(fraction)
                }
                drawLine(
                    color = color,
                    start = project(a.lat, a.lon),
                    end = project(b.lat, b.lon),
                    strokeWidth = max(strokeWidthPx, 1f),
                    cap = StrokeCap.Round,
                )
            }
        }
    }
}

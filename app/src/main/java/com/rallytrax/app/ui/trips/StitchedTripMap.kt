package com.rallytrax.app.ui.trips

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import com.rallytrax.app.data.local.entity.TrackEntity
import com.rallytrax.app.ui.theme.DifficultyAmber
import com.rallytrax.app.ui.theme.DifficultyGreen
import com.rallytrax.app.ui.theme.DifficultyOrange
import com.rallytrax.app.ui.theme.DifficultyRed
import kotlin.math.PI
import kotlin.math.sin

/**
 * Abstract "stitched journey" backdrop. Each recorded stint is drawn as a
 * squiggle along the x-axis; legs (same-day groupings) are separated by a
 * dashed day divider. The seed string (trip id) drives deterministic variation
 * so the same trip always renders the same pattern.
 *
 * Used as the hero background for trip cards and the trip detail header —
 * stands in for a real map render when a full tile stack isn't loaded.
 */
@Composable
fun StitchedTripMap(
    tripId: String,
    tracks: List<TrackEntity>,
    modifier: Modifier = Modifier,
    isDark: Boolean = false,
) = StitchedTripMapImpl(
    tripId = tripId,
    legs = tracks
        .sortedBy { it.recordedAt }
        .groupBy { it.recordedAt / (24L * 60L * 60L * 1000L) }
        .toSortedMap()
        .values
        .map { it.map(::stintColor) },
    modifier = modifier,
    isDark = isDark,
)

/**
 * Abstract overload: when the caller only has summary counts (no track list),
 * render a plausible stitched pattern with [stintCount] squiggles split across
 * [dayCount] legs. Used on the trip list where we don't pre-fetch tracks.
 */
@Composable
fun StitchedTripMap(
    tripId: String,
    stintCount: Int,
    dayCount: Int,
    modifier: Modifier = Modifier,
    isDark: Boolean = false,
) {
    val days = dayCount.coerceAtLeast(1)
    val stints = stintCount.coerceAtLeast(1)
    val per = (stints + days - 1) / days
    val remainder = stints - per * (days - 1)
    val seed = tripId.fold(7u) { a, c -> (a * 31u + c.code.toUInt()) }
    val palette = listOf(DifficultyGreen, DifficultyAmber, DifficultyOrange, DifficultyRed)
    val legs = (0 until days).map { li ->
        val count = if (li == days - 1) remainder.coerceAtLeast(1) else per
        List(count) { si -> palette[((seed.toInt() + li * 3 + si) and 0x7fffffff) % palette.size] }
    }
    StitchedTripMapImpl(tripId = tripId, legs = legs, modifier = modifier, isDark = isDark)
}

@Composable
private fun StitchedTripMapImpl(
    tripId: String,
    legs: List<List<Color>>,
    modifier: Modifier = Modifier,
    isDark: Boolean = false,
) {
    val cs = MaterialTheme.colorScheme
    val bgTop = if (isDark) Color(0xFF1A2033) else Color(0xFFE7EEFC)
    val bgBot = if (isDark) Color(0xFF0F1218) else Color(0xFFD6E2F7)
    val mountFar = if (isDark) Color(0xFF1B2233) else Color(0xFFC5D4EE)
    val mountNear = if (isDark) Color(0xFF222A3E) else Color(0xFFAEBFDF)
    val markerStroke = if (isDark) Color(0xFF0F1218) else Color.White
    val dayDivider = if (isDark) Color(0x33FFFFFF) else Color(0x3A000000)

    val seed = tripId.fold(7u) { a, c -> (a * 31u + c.code.toUInt()) }

    Box(modifier = modifier) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            // Sky gradient
            drawRect(
                brush = Brush.verticalGradient(listOf(bgTop, bgBot)),
                size = Size(w, h),
            )

            // Mountain silhouettes
            val far = Path().apply {
                moveTo(0f, h)
                lineTo(0f, h * 0.55f)
                lineTo(w * 0.15f, h * 0.35f)
                lineTo(w * 0.28f, h * 0.55f)
                lineTo(w * 0.42f, h * 0.30f)
                lineTo(w * 0.56f, h * 0.50f)
                lineTo(w * 0.70f, h * 0.28f)
                lineTo(w * 0.82f, h * 0.48f)
                lineTo(w, h * 0.40f)
                lineTo(w, h)
                close()
            }
            drawPath(far, mountFar.copy(alpha = 0.7f))

            val near = Path().apply {
                moveTo(0f, h)
                lineTo(0f, h * 0.70f)
                lineTo(w * 0.12f, h * 0.58f)
                lineTo(w * 0.24f, h * 0.70f)
                lineTo(w * 0.38f, h * 0.52f)
                lineTo(w * 0.50f, h * 0.68f)
                lineTo(w * 0.63f, h * 0.58f)
                lineTo(w * 0.78f, h * 0.72f)
                lineTo(w, h * 0.62f)
                lineTo(w, h)
                close()
            }
            drawPath(near, mountNear.copy(alpha = 0.8f))

            // Stitched path: each stint is a sinusoidal squiggle drawn along x
            if (legs.isEmpty()) return@Canvas

            fun rand(n: Int): Float {
                val x = sin((seed.toLong() + n).toDouble() * 9301.0) * 0.5
                return (x - kotlin.math.floor(x)).toFloat()
            }

            val startX = 28f
            val edgePad = 24f
            val available = (w - startX - edgePad).coerceAtLeast(40f)
            val totalStints = legs.sumOf { it.size }.coerceAtLeast(1)
            val perStintBudget = available / totalStints.toFloat()

            var x = startX
            var y = h * 0.78f
            legs.forEachIndexed { li, leg ->
                leg.forEachIndexed { si, color ->
                    val length = (perStintBudget * 0.92f).coerceAtLeast(30f)
                    val amp = 8f + rand(li * 10 + si) * 16f
                    val steps = 14
                    val path = Path()
                    var prevX = x
                    var prevY = y
                    path.moveTo(prevX, prevY)
                    for (k in 1..steps) {
                        val tx = x + (length * k / steps)
                        val phase = (k.toFloat() / steps) * PI.toFloat() * (1.4f + rand(k) * 0.8f)
                        val taper = if (k.toFloat() / steps > 0.2f) 1f else 0.3f
                        val ty = y + sin(phase) * amp * taper
                        path.lineTo(tx, ty)
                        prevX = tx
                        prevY = ty
                    }
                    drawPath(
                        path = path,
                        color = color,
                        style = Stroke(width = 2.8f, cap = StrokeCap.Round, join = StrokeJoin.Round),
                    )
                    // End marker
                    drawCircle(color = markerStroke, radius = 3.6f, center = Offset(prevX, prevY))
                    drawCircle(color = color, radius = 2.4f, center = Offset(prevX, prevY))

                    x = prevX + 4f
                    y = prevY
                }
                // Day divider between legs
                if (li < legs.lastIndex) {
                    val dx = x + 2f
                    drawLine(
                        color = dayDivider,
                        start = Offset(dx, y - 6f),
                        end = Offset(dx, y + 6f),
                        strokeWidth = 1f,
                    )
                    x += 10f
                }
            }

            // Start pin
            drawCircle(color = Color.White, radius = 4.5f, center = Offset(startX, h * 0.78f))
            drawCircle(color = cs.primary, radius = 2.2f, center = Offset(startX, h * 0.78f))
        }
    }
}

private fun stintColor(track: TrackEntity): Color = when (track.difficultyRating?.lowercase()) {
    "green", "easy" -> DifficultyGreen
    "amber", "moderate" -> DifficultyAmber
    "orange", "hard" -> DifficultyOrange
    "red", "expert" -> DifficultyRed
    else -> {
        // Derive from curviness/avgSpeed as a fallback bucket
        val c = track.curvinessScore
        when {
            c == null -> DifficultyAmber
            c < 0.25 -> DifficultyGreen
            c < 0.5 -> DifficultyAmber
            c < 0.75 -> DifficultyOrange
            else -> DifficultyRed
        }
    }
}

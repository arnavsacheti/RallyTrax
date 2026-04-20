package com.rallytrax.app.ui.pacenotes

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import com.rallytrax.app.data.local.entity.NoteType
import com.rallytrax.app.ui.theme.DifficultyAmber
import com.rallytrax.app.ui.theme.DifficultyGreen
import com.rallytrax.app.ui.theme.DifficultyRed
import com.rallytrax.app.ui.theme.LocalRallyTraxColors
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

// Driver-POV pace-note glyphs. The road starts at the bottom-center (the
// driver), curves in the turn direction, and exits with an arrowhead pointing
// forward. Severity 1..6 controls exit angle. Inspired by DiRT Rally 2.0.

private val SEV_EXIT_ANGLE = mapOf(1 to 22.0, 2 to 42.0, 3 to 62.0, 4 to 82.0, 5 to 105.0, 6 to 135.0)

private fun effectiveSeverity(noteType: NoteType, severity: Int): Int = when (noteType) {
    NoteType.HAIRPIN_LEFT, NoteType.HAIRPIN_RIGHT -> 6
    NoteType.SQUARE_LEFT, NoteType.SQUARE_RIGHT -> 5
    else -> severity.coerceIn(1, 6)
}

private fun isTurn(t: NoteType) = t == NoteType.LEFT || t == NoteType.RIGHT ||
    t == NoteType.HAIRPIN_LEFT || t == NoteType.HAIRPIN_RIGHT ||
    t == NoteType.SQUARE_LEFT || t == NoteType.SQUARE_RIGHT

private fun isLeft(t: NoteType) =
    t == NoteType.LEFT || t == NoteType.HAIRPIN_LEFT || t == NoteType.SQUARE_LEFT

private fun isCrest(t: NoteType) =
    t == NoteType.CREST || t == NoteType.SMALL_CREST || t == NoteType.BIG_CREST

private fun isDip(t: NoteType) =
    t == NoteType.DIP || t == NoteType.SMALL_DIP || t == NoteType.BIG_DIP

/**
 * Continuous severity color: sev 1 = fresh green, sev 6 = deep red, passing
 * through amber. Crests/dips use amber. Straights use onSurfaceVariant.
 */
@Composable
fun sevTone(noteType: NoteType, severity: Int): Color {
    val rt = LocalRallyTraxColors.current
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    return when {
        isCrest(noteType) || isDip(noteType) -> rt.sevMid
        noteType == NoteType.STRAIGHT -> onSurfaceVariant
        !isTurn(noteType) -> onSurfaceVariant
        else -> {
            val u = (effectiveSeverity(noteType, severity) - 1) / 5f
            if (u <= 0.5f) lerp(rt.sevLow, rt.sevMid, u * 2f)
            else lerp(rt.sevMid, rt.sevHigh, (u - 0.5f) * 2f)
        }
    }
}

/**
 * Driver-POV pace-note glyph drawn as strokes on a transparent background.
 */
@Composable
fun PaceNoteGlyph(
    noteType: NoteType,
    severity: Int,
    modifier: Modifier = Modifier,
    sizeDp: Dp = 44.dp,
    color: Color = sevTone(noteType, severity),
) {
    Canvas(modifier = modifier.size(sizeDp)) {
        drawGlyph(noteType, effectiveSeverity(noteType, severity), color)
    }
}

/**
 * Glyph plus a "R 4" / "L 6" label aligned to its baseline. For non-turn
 * glyphs only the glyph is shown.
 */
@Composable
fun PaceNoteGlyphWithLabel(
    noteType: NoteType,
    severity: Int,
    modifier: Modifier = Modifier,
    sizeDp: Dp = 44.dp,
    color: Color = sevTone(noteType, severity),
) {
    Row(modifier = modifier, verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        PaceNoteGlyph(noteType, severity, sizeDp = sizeDp, color = color)
        if (isTurn(noteType)) {
            val sev = effectiveSeverity(noteType, severity)
            val label = if (isLeft(noteType)) "L" else "R"
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy((sizeDp.value * 0.08f).dp)) {
                Text(
                    text = label,
                    color = color.copy(alpha = 0.6f),
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = (sizeDp.value * 0.38f).sp,
                )
                Text(
                    text = sev.toString(),
                    color = color,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = (sizeDp.value * 0.38f).sp,
                )
            }
        }
    }
}

// ─── Drawing ────────────────────────────────────────────────────────────────

private fun DrawScope.drawGlyph(noteType: NoteType, sev: Int, color: Color) {
    val s = size.minDimension
    val strokeW = max(2.5f, s * 0.11f)
    val stroke = Stroke(width = strokeW, cap = StrokeCap.Round, join = StrokeJoin.Round)

    when {
        isTurn(noteType) -> drawTrajectory(sev, s, isLeft(noteType), color, strokeW, stroke)
        noteType == NoteType.STRAIGHT -> drawStraight(s, color, stroke)
        isCrest(noteType) -> drawCrest(s, color, stroke)
        isDip(noteType) -> drawDip(s, color, stroke)
    }
}

private fun DrawScope.drawTrajectory(
    sev: Int,
    s: Float,
    leftHand: Boolean,
    color: Color,
    strokeW: Float,
    stroke: Stroke,
) {
    val cx = s / 2f
    val bottom = s * 0.92f
    val sign = if (leftHand) -1f else 1f

    val exitAngleDeg = SEV_EXIT_ANGLE[sev] ?: 90.0
    val theta = (exitAngleDeg * PI / 180.0).toFloat()

    val entryLen = s * (0.22f - min(sev, 6) * 0.02f)
    val entryY = bottom - entryLen

    val padTop = s * 0.12f
    val padSide = s * 0.14f
    val ah = s * 0.16f
    val aw = s * 0.11f
    val halo = strokeW / 2f

    val tx = sign * sin(theta)
    val ty = -cos(theta)
    val px = -ty
    val py = tx

    val thetaCapped = min(theta, (PI / 2).toFloat())

    fun fits(r: Float): Boolean {
        val tipX = cx + sign * r - sign * r * cos(theta)
        val tipY = entryY - r * sin(theta)
        val backX = tipX - ah * tx
        val backY = tipY - ah * ty
        val w1x = backX + aw * px; val w1y = backY + aw * py
        val w2x = backX - aw * px; val w2y = backY - aw * py

        val arcRightRel = r * (1f - cos(thetaCapped))
        val arcTopY = entryY - r * sin(thetaCapped)

        fun u(x: Float) = sign * (x - cx)
        val maxOutward = maxOf(arcRightRel, u(tipX), u(backX), u(w1x), u(w2x))
        val minOutward = minOf(0f, u(tipX), u(backX), u(w1x), u(w2x))
        val minY = minOf(arcTopY, tipY, backY, w1y, w2y)

        val outwardOK = maxOutward + halo <= s / 2f - padSide
        val inwardOK = -minOutward + halo <= s / 2f - padSide
        val topOK = minY - halo >= padTop
        return outwardOK && inwardOK && topOK
    }

    var lo = 3f; var hi = s * 2f
    repeat(24) {
        val mid = (lo + hi) / 2f
        if (fits(mid)) lo = mid else hi = mid
    }
    val r = max(lo, s * 0.14f)

    val centerX = cx + sign * r
    val centerY = entryY
    val tipX = centerX - sign * r * cos(theta)
    val tipY = centerY - r * sin(theta)

    // Path: entry line then circular arc to tip.
    val path = Path().apply {
        moveTo(cx, bottom)
        lineTo(cx, entryY)
        // Approximate circular arc with arcTo on a bounding rect.
        arcTo(
            rect = Rect(
                offset = Offset(centerX - r, centerY - r),
                size = Size(2f * r, 2f * r),
            ),
            startAngleDegrees = if (leftHand) 0f else 180f,
            sweepAngleDegrees = (if (leftHand) -1 else 1) * Math.toDegrees(theta.toDouble()).toFloat(),
            forceMoveTo = false,
        )
    }
    drawPath(path, color, style = stroke)

    // Arrow head: three points along tangent.
    val tlen = kotlin.math.hypot(tx, ty).coerceAtLeast(1e-3f)
    val utx = tx / tlen; val uty = ty / tlen
    val upx = -uty; val upy = utx
    val backX = tipX - utx * ah
    val backY = tipY - uty * ah
    val w1 = Offset(backX + upx * aw, backY + upy * aw)
    val w2 = Offset(backX - upx * aw, backY - upy * aw)
    val tip = Offset(tipX, tipY)
    val head = Path().apply {
        moveTo(w1.x, w1.y)
        lineTo(tip.x, tip.y)
        lineTo(w2.x, w2.y)
    }
    drawPath(head, color, style = stroke)
}

private fun DrawScope.drawStraight(s: Float, color: Color, stroke: Stroke) {
    val cx = s / 2f
    val bottom = s * 0.88f
    val top = s * 0.16f
    val ah = s * 0.16f
    val aw = s * 0.11f
    drawPath(
        Path().apply {
            moveTo(cx, bottom); lineTo(cx, top)
        },
        color, style = stroke,
    )
    drawPath(
        Path().apply {
            moveTo(cx - aw, top + ah); lineTo(cx, top); lineTo(cx + aw, top + ah)
        },
        color, style = stroke,
    )
}

private fun DrawScope.drawCrest(s: Float, color: Color, stroke: Stroke) {
    val path = Path().apply {
        moveTo(s * 0.15f, s * 0.82f)
        quadraticTo(s * 0.32f, s * 0.82f, s * 0.5f, s * 0.35f)
        // Smooth continuation (reflected control): T in SVG → reflect previous
        // control point. Approximate with another quad with symmetric control.
        quadraticTo(s * 0.68f, (-0.12f) * s + 2f * 0.35f * s, s * 0.85f, s * 0.35f)
    }
    drawPath(path, color, style = stroke)
}

private fun DrawScope.drawDip(s: Float, color: Color, stroke: Stroke) {
    val path = Path().apply {
        moveTo(s * 0.15f, s * 0.35f)
        quadraticTo(s * 0.32f, s * 0.35f, s * 0.5f, s * 0.78f)
        quadraticTo(s * 0.68f, 2f * 0.78f * s - 1.21f * s, s * 0.85f, s * 0.4f)
    }
    drawPath(path, color, style = stroke)
}

// ─── Preview ────────────────────────────────────────────────────────────────

@androidx.compose.ui.tooling.preview.Preview(showBackground = true, widthDp = 420, heightDp = 540)
@Composable
private fun PaceNoteGlyphGridPreview() {
    com.rallytrax.app.ui.theme.RallyTraxTheme {
        androidx.compose.foundation.layout.Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            listOf(
                listOf(NoteType.LEFT to 1, NoteType.LEFT to 2, NoteType.LEFT to 3,
                    NoteType.LEFT to 4, NoteType.LEFT to 5, NoteType.HAIRPIN_LEFT to 6),
                listOf(NoteType.RIGHT to 1, NoteType.RIGHT to 2, NoteType.RIGHT to 3,
                    NoteType.RIGHT to 4, NoteType.RIGHT to 5, NoteType.HAIRPIN_RIGHT to 6),
            ).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { (t, s) ->
                        PaceNoteGlyphWithLabel(noteType = t, severity = s, sizeDp = 56.dp)
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                listOf(NoteType.STRAIGHT, NoteType.CREST, NoteType.DIP, NoteType.BIG_CREST, NoteType.BIG_DIP)
                    .forEach { PaceNoteGlyph(noteType = it, severity = 3, sizeDp = 56.dp) }
            }
        }
    }
}

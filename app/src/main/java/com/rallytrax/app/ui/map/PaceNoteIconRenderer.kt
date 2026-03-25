package com.rallytrax.app.ui.map

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.core.graphics.createBitmap
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import androidx.compose.foundation.Canvas as ComposeCanvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.rallytrax.app.data.local.entity.NoteModifier
import com.rallytrax.app.data.local.entity.NoteType
import com.rallytrax.app.ui.theme.DifficultyAmber
import com.rallytrax.app.ui.theme.DifficultyGreen
import com.rallytrax.app.ui.theme.DifficultyOrange
import com.rallytrax.app.ui.theme.DifficultyRed
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Rally-style pace note icon renderer. Draws Dirt-Rally-inspired icons
 * programmatically via Canvas for use on maps and in the HUD.
 */
object PaceNoteIconRenderer {

    // ── Color Mapping ────────────────────────────────────────────────────

    private val CrestColor = Color(0xFFFBBC04)
    private val DipColor = Color(0xFFFF6D00)

    fun severityColor(noteType: NoteType, severity: Int): Color = when {
        noteType == NoteType.STRAIGHT -> Color(0xFF9C27B0)
        noteType.isCrest() -> CrestColor
        noteType.isDip() -> DipColor
        severity <= 1 -> DifficultyRed
        severity == 2 -> DifficultyOrange
        severity in 3..4 -> DifficultyAmber
        else -> DifficultyGreen
    }

    // ── Bitmap for Google Maps / OSM markers ─────────────────────────────

    private val bitmapCache = lruCache<IconKey, Bitmap>(32)

    fun createMarkerBitmap(
        noteType: NoteType,
        severity: Int,
        modifier: NoteModifier,
        densityDpi: Int,
        sizeDp: Int = 48,
    ): Bitmap {
        val sizePx = (sizeDp * densityDpi / 160f).toInt().coerceAtLeast(48)
        val key = IconKey(noteType, severity, modifier, sizePx)
        bitmapCache[key]?.let { return it }

        val bitmap = createBitmap(sizePx, sizePx)
        val canvas = Canvas(bitmap)
        drawFullIcon(canvas, noteType, severity, modifier, sizePx.toFloat())

        bitmapCache[key] = bitmap
        return bitmap
    }

    // ── Compose composable ───────────────────────────────────────────────

    @Composable
    fun PaceNoteIcon(
        noteType: NoteType,
        severity: Int,
        modifier: NoteModifier = NoteModifier.NONE,
        sizeDp: Dp = 40.dp,
    ) {
        ComposeCanvas(modifier = Modifier.size(sizeDp)) {
            drawContext.canvas.nativeCanvas.apply {
                drawFullIcon(this, noteType, severity, modifier, size.width)
            }
        }
    }

    // ── Full icon drawing (background + shape) ───────────────────────────

    private fun drawFullIcon(
        canvas: Canvas,
        noteType: NoteType,
        severity: Int,
        modifier: NoteModifier,
        size: Float,
    ) {
        val bgColor = severityColor(noteType, severity)

        // Rounded-rect background
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = bgColor.toArgb()
            style = Paint.Style.FILL
        }
        val cornerR = size * 0.18f
        canvas.drawRoundRect(RectF(0f, 0f, size, size), cornerR, cornerR, bgPaint)

        // White icon stroke
        val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = size * 0.1f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }

        val cx = size / 2f
        val cy = size / 2f
        val r = size * 0.3f // drawing radius

        when {
            noteType == NoteType.STRAIGHT -> drawStraightArrow(canvas, iconPaint, cx, cy, r)
            noteType.isCrest() -> drawCrest(canvas, iconPaint, cx, cy, r, noteType)
            noteType.isDip() -> drawDip(canvas, iconPaint, cx, cy, r, noteType)
            noteType == NoteType.SQUARE_LEFT -> drawSquareTurn(canvas, iconPaint, cx, cy, r, isLeft = true)
            noteType == NoteType.SQUARE_RIGHT -> drawSquareTurn(canvas, iconPaint, cx, cy, r, isLeft = false)
            else -> {
                val isLeft = noteType.isLeft()
                val sweepAngle = severityToSweepAngle(noteType, severity)
                drawCurvedArrow(canvas, iconPaint, cx, cy, r, isLeft, sweepAngle)
            }
        }
    }

    // ── Individual shape drawing ─────────────────────────────────────────

    private fun severityToSweepAngle(noteType: NoteType, severity: Int): Float = when {
        noteType == NoteType.HAIRPIN_LEFT || noteType == NoteType.HAIRPIN_RIGHT -> 170f
        severity <= 1 -> 160f
        severity == 2 -> 130f
        severity == 3 -> 100f
        severity == 4 -> 75f
        severity == 5 -> 50f
        else -> 30f
    }

    /**
     * Draws a curved arrow representing a turn.
     * The arrow enters from the bottom and curves left or right.
     * The sweep angle determines how tight the curve is.
     */
    private fun drawCurvedArrow(
        canvas: Canvas,
        paint: Paint,
        cx: Float,
        cy: Float,
        r: Float,
        isLeft: Boolean,
        sweepAngle: Float,
    ) {
        // Draw a path: straight entry from bottom, then arc curve
        val path = Path()

        // Arc center is offset so the arrow enters from below
        val arcCx = cx
        val arcCy = cy
        val arcRect = RectF(arcCx - r, arcCy - r, arcCx + r, arcCy + r)

        // For left turns: start at bottom (90°) and sweep counter-clockwise (negative)
        // For right turns: start at bottom (90°) and sweep clockwise (positive)
        val startAngle = 90f
        // Clockwise (positive) sweeps LEFT on screen; counter-clockwise sweeps RIGHT
        val sweep = if (isLeft) sweepAngle else -sweepAngle

        // Draw the straight entry segment from bottom of icon to arc start
        val arcStartX = arcCx + r * cos(Math.toRadians(startAngle.toDouble())).toFloat()
        val arcStartY = arcCy + r * sin(Math.toRadians(startAngle.toDouble())).toFloat()
        path.moveTo(arcStartX, cy + r * 1.2f) // below arc start
        path.lineTo(arcStartX, arcStartY)

        // Continue the path with the arc (arcTo keeps it connected)
        path.arcTo(arcRect, startAngle, sweep)
        canvas.drawPath(path, paint)

        // Arrowhead at the end of the arc
        val endAngleDeg = startAngle + sweep
        val endAngleRad = Math.toRadians(endAngleDeg.toDouble())
        val tipX = arcCx + r * cos(endAngleRad).toFloat()
        val tipY = arcCy + r * sin(endAngleRad).toFloat()

        // Tangent direction at the arc end
        val tangentAngle = if (isLeft) endAngleRad - PI / 2 else endAngleRad + PI / 2
        val arrowLen = r * 0.5f
        val arrowSpread = PI / 6 // 30 degrees

        val arrow1Angle = tangentAngle + PI + arrowSpread
        val arrow2Angle = tangentAngle + PI - arrowSpread

        val arrowPath = Path()
        arrowPath.moveTo(
            tipX + arrowLen * cos(arrow1Angle).toFloat(),
            tipY + arrowLen * sin(arrow1Angle).toFloat(),
        )
        arrowPath.lineTo(tipX, tipY)
        arrowPath.lineTo(
            tipX + arrowLen * cos(arrow2Angle).toFloat(),
            tipY + arrowLen * sin(arrow2Angle).toFloat(),
        )
        canvas.drawPath(arrowPath, paint)
    }

    private fun drawSquareTurn(
        canvas: Canvas,
        paint: Paint,
        cx: Float,
        cy: Float,
        r: Float,
        isLeft: Boolean,
    ) {
        val path = Path()
        val startX = if (isLeft) cx + r * 0.2f else cx - r * 0.2f
        val cornerY = cy - r * 0.2f
        val endX = if (isLeft) cx - r else cx + r

        // Vertical entry, then 90° turn
        path.moveTo(startX, cy + r * 1.2f)
        path.lineTo(startX, cornerY)
        path.lineTo(endX, cornerY)
        canvas.drawPath(path, paint)

        // Arrowhead
        val arrowLen = r * 0.45f
        val arrowPath = Path()
        arrowPath.moveTo(endX, cornerY)
        if (isLeft) {
            arrowPath.lineTo(endX + arrowLen * 0.7f, cornerY - arrowLen * 0.5f)
            arrowPath.moveTo(endX, cornerY)
            arrowPath.lineTo(endX + arrowLen * 0.7f, cornerY + arrowLen * 0.5f)
        } else {
            arrowPath.lineTo(endX - arrowLen * 0.7f, cornerY - arrowLen * 0.5f)
            arrowPath.moveTo(endX, cornerY)
            arrowPath.lineTo(endX - arrowLen * 0.7f, cornerY + arrowLen * 0.5f)
        }
        canvas.drawPath(arrowPath, paint)
    }

    private fun drawStraightArrow(canvas: Canvas, paint: Paint, cx: Float, cy: Float, r: Float) {
        val top = cy - r
        val bottom = cy + r * 1.2f

        // Vertical line
        canvas.drawLine(cx, bottom, cx, top, paint)

        // Arrowhead
        val arrowLen = r * 0.5f
        canvas.drawLine(cx, top, cx - arrowLen * 0.7f, top + arrowLen, paint)
        canvas.drawLine(cx, top, cx + arrowLen * 0.7f, top + arrowLen, paint)
    }

    private fun drawCrest(canvas: Canvas, paint: Paint, cx: Float, cy: Float, r: Float, type: NoteType) {
        val scale = when (type) {
            NoteType.SMALL_CREST -> 0.65f
            NoteType.BIG_CREST -> 1.0f
            else -> 0.82f
        }
        val h = r * scale
        val w = r * 1.0f
        val path = Path()
        path.moveTo(cx - w, cy + h * 0.5f)
        path.lineTo(cx, cy - h * 0.5f)
        path.lineTo(cx + w, cy + h * 0.5f)
        canvas.drawPath(path, paint)
    }

    private fun drawDip(canvas: Canvas, paint: Paint, cx: Float, cy: Float, r: Float, type: NoteType) {
        val scale = when (type) {
            NoteType.SMALL_DIP -> 0.65f
            NoteType.BIG_DIP -> 1.0f
            else -> 0.82f
        }
        val h = r * scale
        val w = r * 1.0f
        val path = Path()
        path.moveTo(cx - w, cy - h * 0.5f)
        path.lineTo(cx, cy + h * 0.5f)
        path.lineTo(cx + w, cy - h * 0.5f)
        canvas.drawPath(path, paint)
    }

    // ── Helper extensions ────────────────────────────────────────────────

    private fun NoteType.isLeft(): Boolean =
        this == NoteType.LEFT || this == NoteType.HAIRPIN_LEFT || this == NoteType.SQUARE_LEFT

    private fun NoteType.isCrest(): Boolean =
        this == NoteType.CREST || this == NoteType.SMALL_CREST || this == NoteType.BIG_CREST

    private fun NoteType.isDip(): Boolean =
        this == NoteType.DIP || this == NoteType.SMALL_DIP || this == NoteType.BIG_DIP

    private fun Color.toArgb(): Int = android.graphics.Color.argb(
        (alpha * 255).toInt(),
        (red * 255).toInt(),
        (green * 255).toInt(),
        (blue * 255).toInt(),
    )

    private data class IconKey(
        val noteType: NoteType,
        val severity: Int,
        val modifier: NoteModifier,
        val sizePx: Int,
    )

    private fun <K, V> lruCache(maxSize: Int): MutableMap<K, V> {
        return object : LinkedHashMap<K, V>(maxSize, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, V>?): Boolean = size > maxSize
        }
    }
}

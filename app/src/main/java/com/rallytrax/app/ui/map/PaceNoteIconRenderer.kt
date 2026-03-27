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
 * Rally-style pace note icon renderer. Draws Dirt-Rally-inspired filled
 * arrow shapes programmatically via Canvas for use on maps and in the HUD.
 *
 * Arrow style: a thick filled "road" enters from the bottom-center and
 * curves toward the turn direction. Tighter turns curve more. The arrow
 * tip is a pointed chevron integrated into the filled shape.
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

    private val bitmapCache = lruCache<IconKey, Bitmap>(64)

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

        // White fill paint for the arrow shape
        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            style = Paint.Style.FILL
        }

        val cx = size / 2f
        val cy = size / 2f

        when {
            noteType == NoteType.STRAIGHT -> drawStraightArrow(canvas, fillPaint, cx, cy, size)
            noteType.isCrest() -> drawCrest(canvas, fillPaint, cx, cy, size, noteType)
            noteType.isDip() -> drawDip(canvas, fillPaint, cx, cy, size, noteType)
            noteType == NoteType.SQUARE_LEFT -> drawSquareTurn(canvas, fillPaint, cx, cy, size, isLeft = true)
            noteType == NoteType.SQUARE_RIGHT -> drawSquareTurn(canvas, fillPaint, cx, cy, size, isLeft = false)
            else -> {
                val isLeft = noteType.isLeft()
                val sweepAngle = severityToSweepAngle(noteType, severity)
                drawCurvedArrow(canvas, fillPaint, cx, cy, size, isLeft, sweepAngle)
            }
        }
    }

    // ── Severity → sweep angle ───────────────────────────────────────────

    private fun severityToSweepAngle(noteType: NoteType, severity: Int): Float = when {
        noteType == NoteType.HAIRPIN_LEFT || noteType == NoteType.HAIRPIN_RIGHT -> 165f
        severity <= 1 -> 150f  // acute turn (almost hairpin)
        severity == 2 -> 120f
        severity == 3 -> 95f
        severity == 4 -> 70f
        severity == 5 -> 45f
        else -> 25f            // gentle bend (grade 6)
    }

    // ── Dirt Rally style filled curved arrow ─────────────────────────────

    /**
     * Draws a filled arrow shape: a thick "road" enters from the bottom
     * and curves left or right. The shape is a closed filled path with
     * inner and outer edges of the curved road, ending in a pointed tip.
     */
    private fun drawCurvedArrow(
        canvas: Canvas,
        paint: Paint,
        cx: Float,
        cy: Float,
        size: Float,
        isLeft: Boolean,
        sweepAngle: Float,
    ) {
        val halfW = size * 0.08f   // half-width of the arrow "road"
        val r = size * 0.28f       // arc radius
        val arcCx = cx
        val arcCy = cy * 0.88f     // shift arc center slightly up

        // In Android Canvas: positive sweep = clockwise
        // From 90° (bottom): clockwise → left, counter-clockwise → right
        val startAngle = 90f
        val sweep = if (isLeft) sweepAngle else -sweepAngle
        val sign = if (isLeft) 1f else -1f

        val startRad = Math.toRadians(startAngle.toDouble())
        val endRad = Math.toRadians((startAngle + sweep).toDouble())

        // Straight entry: bottom of icon to arc start (bottom of circle)
        val entryBottom = size * 0.92f
        val entryTop = arcCy + r  // where the arc starts at 90°

        // Tip point (end of arc)
        val tipX = arcCx + r * cos(endRad).toFloat()
        val tipY = arcCy + r * sin(endRad).toFloat()

        // Build the filled shape:
        // Right edge of entry → outer arc → tip → inner arc → left edge of entry
        val path = Path()

        // Entry: right edge going up (or left edge depending on perspective)
        path.moveTo(cx + halfW, entryBottom)
        path.lineTo(cx + halfW, entryTop)

        // Outer arc (radius + halfW)
        val outerR = r + halfW
        val outerRect = RectF(arcCx - outerR, arcCy - outerR, arcCx + outerR, arcCy + outerR)
        path.arcTo(outerRect, startAngle, sweep)

        // Pointed tip: extend beyond the arc end
        val tipExtend = size * 0.12f
        // Tangent direction at the end of the arc
        val tangent = if (isLeft) endRad + PI / 2 else endRad - PI / 2
        val pointX = tipX + tipExtend * cos(tangent).toFloat()
        val pointY = tipY + tipExtend * sin(tangent).toFloat()
        path.lineTo(pointX, pointY)

        // Inner arc back (radius - halfW), drawn in reverse
        val innerR = (r - halfW).coerceAtLeast(size * 0.04f)
        val innerRect = RectF(arcCx - innerR, arcCy - innerR, arcCx + innerR, arcCy + innerR)
        path.arcTo(innerRect, startAngle + sweep, -sweep)

        // Entry: left edge going down
        path.lineTo(cx - halfW, entryTop)
        path.lineTo(cx - halfW, entryBottom)
        path.close()

        canvas.drawPath(path, paint)
    }

    // ── Square (90°) turn ────────────────────────────────────────────────

    private fun drawSquareTurn(
        canvas: Canvas,
        paint: Paint,
        cx: Float,
        cy: Float,
        size: Float,
        isLeft: Boolean,
    ) {
        val halfW = size * 0.08f
        val entryBottom = size * 0.92f
        val cornerY = cy * 0.75f
        val dir = if (isLeft) -1f else 1f
        val endX = cx + dir * size * 0.35f
        val tipX = cx + dir * size * 0.42f

        val path = Path().apply {
            // Right edge of vertical entry
            moveTo(cx + halfW, entryBottom)
            lineTo(cx + halfW, cornerY - halfW)
            // Outer horizontal
            lineTo(endX, cornerY - halfW)
            // Pointed tip
            lineTo(tipX, cornerY)
            // Inner horizontal back
            lineTo(endX, cornerY + halfW)
            lineTo(cx - halfW, cornerY + halfW)
            // Left edge of vertical entry
            lineTo(cx - halfW, entryBottom)
            close()
        }
        canvas.drawPath(path, paint)
    }

    // ── Straight arrow ───────────────────────────────────────────────────

    private fun drawStraightArrow(
        canvas: Canvas,
        paint: Paint,
        cx: Float,
        cy: Float,
        size: Float,
    ) {
        val halfW = size * 0.08f
        val bottom = size * 0.92f
        val tipY = size * 0.10f
        val chevronBase = size * 0.32f  // where the chevron wings start

        val path = Path().apply {
            // Right edge of shaft
            moveTo(cx + halfW, bottom)
            lineTo(cx + halfW, chevronBase)
            // Right chevron wing
            lineTo(cx + size * 0.22f, chevronBase)
            // Pointed tip
            lineTo(cx, tipY)
            // Left chevron wing
            lineTo(cx - size * 0.22f, chevronBase)
            // Left edge of shaft
            lineTo(cx - halfW, chevronBase)
            lineTo(cx - halfW, bottom)
            close()
        }
        canvas.drawPath(path, paint)
    }

    // ── Crest / Dip ──────────────────────────────────────────────────────

    private fun drawCrest(
        canvas: Canvas,
        paint: Paint,
        cx: Float,
        cy: Float,
        size: Float,
        type: NoteType,
    ) {
        val scale = when (type) {
            NoteType.SMALL_CREST -> 0.6f
            NoteType.BIG_CREST -> 1.0f
            else -> 0.8f
        }
        val h = size * 0.18f * scale
        val w = size * 0.32f
        val strokeW = size * 0.07f

        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = paint.color
            style = Paint.Style.STROKE
            strokeWidth = strokeW
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        val path = Path().apply {
            moveTo(cx - w, cy + h)
            lineTo(cx, cy - h)
            lineTo(cx + w, cy + h)
        }
        canvas.drawPath(path, strokePaint)
    }

    private fun drawDip(
        canvas: Canvas,
        paint: Paint,
        cx: Float,
        cy: Float,
        size: Float,
        type: NoteType,
    ) {
        val scale = when (type) {
            NoteType.SMALL_DIP -> 0.6f
            NoteType.BIG_DIP -> 1.0f
            else -> 0.8f
        }
        val h = size * 0.18f * scale
        val w = size * 0.32f
        val strokeW = size * 0.07f

        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = paint.color
            style = Paint.Style.STROKE
            strokeWidth = strokeW
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        val path = Path().apply {
            moveTo(cx - w, cy - h)
            lineTo(cx, cy + h)
            lineTo(cx + w, cy - h)
        }
        canvas.drawPath(path, strokePaint)
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

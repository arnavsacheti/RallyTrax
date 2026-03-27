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
        val arcRect = RectF(cx - r, cy - r, cx + r, cy + r)

        // In Android Canvas (Y-down): positive sweep = clockwise
        // From 90° (bottom): clockwise goes toward 180° (left), counter-clockwise toward 0° (right)
        val startAngle = 90f
        val sweep = if (isLeft) sweepAngle else -sweepAngle

        // Straight entry from bottom edge up to arc start at 90° (bottom of circle)
        val arcStartX = cx + r * cos(Math.toRadians(startAngle.toDouble())).toFloat()
        val arcStartY = cy + r * sin(Math.toRadians(startAngle.toDouble())).toFloat()
        val arcPath = Path().apply {
            moveTo(arcStartX, cy + r * 1.2f)
            lineTo(arcStartX, arcStartY)
            arcTo(arcRect, startAngle, sweep)
        }
        canvas.drawPath(arcPath, paint)

        // Filled arrowhead at arc tip
        val endAngleRad = Math.toRadians((startAngle + sweep).toDouble())
        val tipX = cx + r * cos(endAngleRad).toFloat()
        val tipY = cy + r * sin(endAngleRad).toFloat()

        // Tangent = direction of travel at arc end
        // Clockwise (positive sweep): tangent = endAngle + π/2
        // Counter-clockwise (negative sweep): tangent = endAngle - π/2
        val tangentAngle = if (isLeft) endAngleRad + PI / 2 else endAngleRad - PI / 2
        val arrowLen = r * 0.55f
        val arrowSpread = PI / 5 // 36°

        val baseAngle = tangentAngle + PI // point backward from direction of travel
        val p1x = tipX + arrowLen * cos(baseAngle + arrowSpread).toFloat()
        val p1y = tipY + arrowLen * sin(baseAngle + arrowSpread).toFloat()
        val p2x = tipX + arrowLen * cos(baseAngle - arrowSpread).toFloat()
        val p2y = tipY + arrowLen * sin(baseAngle - arrowSpread).toFloat()

        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = paint.color
            style = Paint.Style.FILL
        }
        val arrowPath = Path().apply {
            moveTo(tipX, tipY)
            lineTo(p1x, p1y)
            lineTo(p2x, p2y)
            close()
        }
        canvas.drawPath(arrowPath, fillPaint)
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

        // Filled arrowhead
        val arrowLen = r * 0.5f
        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = paint.color
            style = Paint.Style.FILL
        }
        val arrowPath = Path().apply {
            moveTo(endX, cornerY)
            if (isLeft) {
                lineTo(endX + arrowLen * 0.7f, cornerY - arrowLen * 0.55f)
                lineTo(endX + arrowLen * 0.7f, cornerY + arrowLen * 0.55f)
            } else {
                lineTo(endX - arrowLen * 0.7f, cornerY - arrowLen * 0.55f)
                lineTo(endX - arrowLen * 0.7f, cornerY + arrowLen * 0.55f)
            }
            close()
        }
        canvas.drawPath(arrowPath, fillPaint)
    }

    private fun drawStraightArrow(canvas: Canvas, paint: Paint, cx: Float, cy: Float, r: Float) {
        val top = cy - r
        val bottom = cy + r * 1.2f

        // Vertical line
        canvas.drawLine(cx, bottom, cx, top, paint)

        // Filled arrowhead
        val arrowLen = r * 0.55f
        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = paint.color
            style = Paint.Style.FILL
        }
        val arrowPath = Path().apply {
            moveTo(cx, top)
            lineTo(cx - arrowLen * 0.65f, top + arrowLen)
            lineTo(cx + arrowLen * 0.65f, top + arrowLen)
            close()
        }
        canvas.drawPath(arrowPath, fillPaint)
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

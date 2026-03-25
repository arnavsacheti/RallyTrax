package com.rallytrax.app.ui.map

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import androidx.compose.foundation.Canvas as ComposeCanvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.withSave
import com.rallytrax.app.data.local.entity.NoteModifier
import com.rallytrax.app.data.local.entity.NoteType
import com.rallytrax.app.ui.theme.DifficultyAmber
import com.rallytrax.app.ui.theme.DifficultyGreen
import com.rallytrax.app.ui.theme.DifficultyOrange
import com.rallytrax.app.ui.theme.DifficultyRed
import kotlin.math.cos
import kotlin.math.sin

/**
 * Rally-style pace note icon renderer. Draws Dirt-Rally-inspired icons
 * programmatically via Canvas for use on maps and in the HUD.
 */
object PaceNoteIconRenderer {

    // ── Color Mapping ────────────────────────────────────────────────────

    private val CrestColor = Color(0xFFFBBC04) // Yellow
    private val DipColor = Color(0xFFFF6D00) // Orange

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
        sizeDp: Int = 32,
    ): Bitmap {
        val sizePx = (sizeDp * densityDpi / 160f).toInt()
        val key = IconKey(noteType, severity, modifier, sizePx)
        bitmapCache[key]?.let { return it }

        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val bgColor = severityColor(noteType, severity)

        // Draw rounded-rect background
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = bgColor.toArgb()
            style = Paint.Style.FILL
        }
        val cornerR = sizePx * 0.2f
        canvas.drawRoundRect(RectF(0f, 0f, sizePx.toFloat(), sizePx.toFloat()), cornerR, cornerR, bgPaint)

        // Draw icon in white
        val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = sizePx * 0.08f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }

        val cx = sizePx / 2f
        val cy = sizePx / 2f
        val r = sizePx * 0.32f // icon radius

        drawIconOnNativeCanvas(canvas, noteType, severity, modifier, iconPaint, cx, cy, r, sizePx.toFloat())

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
        val bgColor = severityColor(noteType, severity)
        ComposeCanvas(modifier = Modifier.size(sizeDp)) {
            val sizePx = size.width
            val cornerR = sizePx * 0.2f

            // Background
            drawRoundRect(
                color = bgColor,
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(cornerR, cornerR),
            )

            // Icon in white
            drawPaceNoteShape(noteType, severity, modifier, sizePx)
        }
    }

    // ── Native Canvas icon drawing (for Bitmap) ──────────────────────────

    private fun drawIconOnNativeCanvas(
        canvas: Canvas,
        noteType: NoteType,
        severity: Int,
        modifier: NoteModifier,
        paint: Paint,
        cx: Float,
        cy: Float,
        r: Float,
        size: Float,
    ) {
        when {
            noteType == NoteType.STRAIGHT -> drawStraightArrow(canvas, paint, cx, cy, r)
            noteType.isCrest() -> drawCrest(canvas, paint, cx, cy, r, noteType)
            noteType.isDip() -> drawDip(canvas, paint, cx, cy, r, noteType)
            noteType == NoteType.SQUARE_LEFT -> drawSquareTurn(canvas, paint, cx, cy, r, isLeft = true)
            noteType == NoteType.SQUARE_RIGHT -> drawSquareTurn(canvas, paint, cx, cy, r, isLeft = false)
            else -> {
                val isLeft = noteType.isLeft()
                val sweepAngle = severityToSweepAngle(noteType, severity)
                drawCurvedArrow(canvas, paint, cx, cy, r, isLeft, sweepAngle)
                if (modifier == NoteModifier.TIGHTENS) drawTightensIndicator(canvas, paint, cx, cy, r, isLeft)
                if (modifier == NoteModifier.OPENS) drawOpensIndicator(canvas, paint, cx, cy, r, isLeft)
            }
        }
    }

    private fun severityToSweepAngle(noteType: NoteType, severity: Int): Float = when {
        noteType == NoteType.HAIRPIN_LEFT || noteType == NoteType.HAIRPIN_RIGHT -> 170f
        severity <= 1 -> 160f
        severity == 2 -> 130f
        severity == 3 -> 100f
        severity == 4 -> 75f
        severity == 5 -> 50f
        else -> 30f
    }

    private fun drawCurvedArrow(
        canvas: Canvas,
        paint: Paint,
        cx: Float,
        cy: Float,
        r: Float,
        isLeft: Boolean,
        sweepAngle: Float,
    ) {
        val path = Path()
        val arcRect = RectF(cx - r, cy - r, cx + r, cy + r)

        // Start from bottom, sweep upward
        val startAngle = if (isLeft) 90f else 90f - sweepAngle
        val sweep = if (isLeft) -sweepAngle else sweepAngle

        path.addArc(arcRect, startAngle, sweep)
        canvas.drawPath(path, paint)

        // Arrowhead at the end of the arc
        val endAngleRad = Math.toRadians((startAngle + sweep).toDouble())
        val tipX = cx + r * cos(endAngleRad).toFloat()
        val tipY = cy + r * sin(endAngleRad).toFloat()

        val arrowSize = r * 0.4f
        val arrowAngle1 = endAngleRad + if (isLeft) Math.toRadians(150.0) else Math.toRadians(-150.0)
        val arrowAngle2 = endAngleRad + if (isLeft) Math.toRadians(210.0) else Math.toRadians(-210.0)

        val arrowPath = Path()
        arrowPath.moveTo(tipX, tipY)
        arrowPath.lineTo(
            tipX + arrowSize * cos(arrowAngle1).toFloat(),
            tipY + arrowSize * sin(arrowAngle1).toFloat(),
        )
        arrowPath.moveTo(tipX, tipY)
        arrowPath.lineTo(
            tipX + arrowSize * cos(arrowAngle2).toFloat(),
            tipY + arrowSize * sin(arrowAngle2).toFloat(),
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
        // Vertical line up, then horizontal turn
        val startX = if (isLeft) cx + r * 0.3f else cx - r * 0.3f
        val startY = cy + r
        val cornerY = cy - r * 0.3f
        val endX = if (isLeft) cx - r else cx + r

        path.moveTo(startX, startY)
        path.lineTo(startX, cornerY)
        path.lineTo(endX, cornerY)

        canvas.drawPath(path, paint)

        // Arrowhead
        val arrowSize = r * 0.35f
        val arrowPath = Path()
        arrowPath.moveTo(endX, cornerY)
        if (isLeft) {
            arrowPath.lineTo(endX + arrowSize, cornerY - arrowSize * 0.5f)
            arrowPath.moveTo(endX, cornerY)
            arrowPath.lineTo(endX + arrowSize, cornerY + arrowSize * 0.5f)
        } else {
            arrowPath.lineTo(endX - arrowSize, cornerY - arrowSize * 0.5f)
            arrowPath.moveTo(endX, cornerY)
            arrowPath.lineTo(endX - arrowSize, cornerY + arrowSize * 0.5f)
        }
        canvas.drawPath(arrowPath, paint)
    }

    private fun drawStraightArrow(canvas: Canvas, paint: Paint, cx: Float, cy: Float, r: Float) {
        // Vertical arrow pointing up
        canvas.drawLine(cx, cy + r, cx, cy - r, paint)
        val arrowSize = r * 0.4f
        canvas.drawLine(cx, cy - r, cx - arrowSize, cy - r + arrowSize, paint)
        canvas.drawLine(cx, cy - r, cx + arrowSize, cy - r + arrowSize, paint)
    }

    private fun drawCrest(canvas: Canvas, paint: Paint, cx: Float, cy: Float, r: Float, type: NoteType) {
        val scale = when (type) {
            NoteType.SMALL_CREST -> 0.6f
            NoteType.BIG_CREST -> 1.0f
            else -> 0.8f
        }
        val h = r * scale
        val w = r * 0.9f
        // Chevron up (^)
        val path = Path()
        path.moveTo(cx - w, cy + h * 0.4f)
        path.lineTo(cx, cy - h * 0.6f)
        path.lineTo(cx + w, cy + h * 0.4f)
        canvas.drawPath(path, paint)
    }

    private fun drawDip(canvas: Canvas, paint: Paint, cx: Float, cy: Float, r: Float, type: NoteType) {
        val scale = when (type) {
            NoteType.SMALL_DIP -> 0.6f
            NoteType.BIG_DIP -> 1.0f
            else -> 0.8f
        }
        val h = r * scale
        val w = r * 0.9f
        // Chevron down (v)
        val path = Path()
        path.moveTo(cx - w, cy - h * 0.4f)
        path.lineTo(cx, cy + h * 0.6f)
        path.lineTo(cx + w, cy - h * 0.4f)
        canvas.drawPath(path, paint)
    }

    private fun drawTightensIndicator(canvas: Canvas, paint: Paint, cx: Float, cy: Float, r: Float, isLeft: Boolean) {
        // Small narrowing wedge near the end of the arrow
        val indicatorPaint = Paint(paint).apply { strokeWidth = paint.strokeWidth * 0.6f }
        val x = if (isLeft) cx + r * 0.5f else cx - r * 0.5f
        canvas.drawLine(x, cy + r * 0.6f, x, cy + r * 0.3f, indicatorPaint)
    }

    private fun drawOpensIndicator(canvas: Canvas, paint: Paint, cx: Float, cy: Float, r: Float, isLeft: Boolean) {
        // Small widening wedge near the end of the arrow
        val indicatorPaint = Paint(paint).apply { strokeWidth = paint.strokeWidth * 0.6f }
        val x = if (isLeft) cx + r * 0.5f else cx - r * 0.5f
        canvas.drawLine(x, cy + r * 0.3f, x, cy + r * 0.6f, indicatorPaint)
        canvas.drawLine(x - r * 0.15f, cy + r * 0.6f, x + r * 0.15f, cy + r * 0.6f, indicatorPaint)
    }

    // ── Compose DrawScope icon drawing ───────────────────────────────────

    private fun DrawScope.drawPaceNoteShape(
        noteType: NoteType,
        severity: Int,
        modifier: NoteModifier,
        sizePx: Float,
    ) {
        val stroke = Stroke(
            width = sizePx * 0.08f,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round,
        )
        val cx = sizePx / 2f
        val cy = sizePx / 2f
        val r = sizePx * 0.32f
        val white = Color.White

        // Use native canvas for path-based drawing
        drawContext.canvas.nativeCanvas.apply {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.WHITE
                style = Paint.Style.STROKE
                strokeWidth = sizePx * 0.08f
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
            }
            drawIconOnNativeCanvas(this, noteType, severity, modifier, paint, cx, cy, r, sizePx)
        }
    }

    // ── Helper extensions ────────────────────────────────────────────────

    private fun NoteType.isLeft(): Boolean = this == NoteType.LEFT || this == NoteType.HAIRPIN_LEFT || this == NoteType.SQUARE_LEFT

    private fun NoteType.isCrest(): Boolean = this == NoteType.CREST || this == NoteType.SMALL_CREST || this == NoteType.BIG_CREST

    private fun NoteType.isDip(): Boolean = this == NoteType.DIP || this == NoteType.SMALL_DIP || this == NoteType.BIG_DIP

    private fun Color.toArgb(): Int {
        return android.graphics.Color.argb(
            (alpha * 255).toInt(),
            (red * 255).toInt(),
            (green * 255).toInt(),
            (blue * 255).toInt(),
        )
    }

    private data class IconKey(
        val noteType: NoteType,
        val severity: Int,
        val modifier: NoteModifier,
        val sizePx: Int,
    )

    private fun <K, V> lruCache(maxSize: Int): MutableMap<K, V> {
        return object : LinkedHashMap<K, V>(maxSize, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, V>?): Boolean {
                return size > maxSize
            }
        }
    }
}

package com.rallytrax.app.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.net.Uri
import androidx.core.content.FileProvider
import com.rallytrax.app.data.classification.RouteClassifier
import com.rallytrax.app.data.local.entity.TrackEntity
import com.rallytrax.app.data.preferences.UnitSystem
import com.rallytrax.app.util.formatDate
import com.rallytrax.app.util.formatDistance
import com.rallytrax.app.util.formatElapsedTime
import com.rallytrax.app.util.formatElevation
import com.rallytrax.app.util.formatSpeed
import com.rallytrax.app.util.speedUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * Generates a shareable activity card bitmap using Android Canvas drawing.
 * Returns a content URI suitable for sharing via Intent.ACTION_SEND.
 */
suspend fun generateShareBitmap(
    context: Context,
    track: TrackEntity,
    unitSystem: UnitSystem,
): Uri? = withContext(Dispatchers.IO) {
    val width = 1080
    val height = 1350
    val density = 2.7f // ~xxhdpi reference for consistent sizing
    val padding = (24 * density).toInt()
    val cornerRadius = (16 * density)

    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    // Colors — Rally Blue palette, light theme style
    val bgColor = 0xFFF2F2F7.toInt()
    val cardBgColor = 0xFFFFFFFF.toInt()
    val primaryColor = 0xFF1A73E8.toInt()
    val onSurfaceColor = 0xFF1C1B1F.toInt()
    val onSurfaceVariantColor = 0xFF49454F.toInt()
    val dividerColor = 0xFFCAC4D0.toInt()
    val difficultyColorMap = RouteClassifier.difficultyColors

    // Background
    val bgPaint = Paint().apply { color = bgColor; isAntiAlias = true }
    canvas.drawRoundRect(
        RectF(0f, 0f, width.toFloat(), height.toFloat()),
        cornerRadius, cornerRadius, bgPaint,
    )

    // Card background
    val cardPaint = Paint().apply { color = cardBgColor; isAntiAlias = true }
    val cardMargin = padding / 2f
    canvas.drawRoundRect(
        RectF(cardMargin, cardMargin, width - cardMargin, height - cardMargin),
        cornerRadius, cornerRadius, cardPaint,
    )

    val contentLeft = padding.toFloat()
    val contentRight = width - padding.toFloat()
    var y = padding * 1.5f

    // Branding
    val brandPaint = Paint().apply {
        color = primaryColor
        textSize = 14 * density
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        isAntiAlias = true
    }
    canvas.drawText("RallyTrax", contentLeft, y, brandPaint)
    y += 12 * density

    val accentPaint = Paint().apply { color = primaryColor; isAntiAlias = true }
    canvas.drawRoundRect(
        RectF(contentLeft, y, contentLeft + 40 * density, y + 3 * density),
        1.5f * density, 1.5f * density, accentPaint,
    )
    y += 24 * density

    // Track name
    val namePaint = Paint().apply {
        color = onSurfaceColor
        textSize = 28 * density
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        isAntiAlias = true
    }
    val nameText = ellipsize(track.name, namePaint, contentRight - contentLeft)
    canvas.drawText(nameText, contentLeft, y, namePaint)
    y += 12 * density

    // Date
    val datePaint = Paint().apply {
        color = onSurfaceVariantColor
        textSize = 13 * density
        typeface = Typeface.DEFAULT
        isAntiAlias = true
    }
    canvas.drawText(formatDate(track.recordedAt), contentLeft, y, datePaint)
    y += 20 * density

    // Difficulty badge (if set)
    track.difficultyRating?.let { difficulty ->
        val badgeColor = difficultyColorMap[difficulty] ?: onSurfaceVariantColor
        val badgePaint = Paint().apply {
            color = badgeColor
            isAntiAlias = true
        }
        val badgeTextPaint = Paint().apply {
            color = 0xFFFFFFFF.toInt()
            textSize = 12 * density
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        val badgeTextWidth = badgeTextPaint.measureText(difficulty)
        val badgePadH = 12 * density
        val badgePadV = 6 * density
        val badgeHeight = badgeTextPaint.textSize + badgePadV * 2
        canvas.drawRoundRect(
            RectF(contentLeft, y, contentLeft + badgeTextWidth + badgePadH * 2, y + badgeHeight),
            badgeHeight / 2, badgeHeight / 2, badgePaint,
        )
        canvas.drawText(
            difficulty,
            contentLeft + badgePadH,
            y + badgePadV + badgeTextPaint.textSize - badgeTextPaint.descent(),
            badgeTextPaint,
        )
        y += badgeHeight + 12 * density
    }

    y += 8 * density

    // Divider
    val dividerPaint = Paint().apply { color = dividerColor; isAntiAlias = true }
    canvas.drawRect(contentLeft, y, contentRight, y + 1 * density, dividerPaint)
    y += 24 * density

    // Stats grid
    data class StatItem(val label: String, val value: String)

    val stats = buildList {
        add(StatItem("Distance", formatDistance(track.distanceMeters, unitSystem)))
        add(StatItem("Duration", formatElapsedTime(track.durationMs)))
        add(
            StatItem(
                "Avg Speed",
                "${formatSpeed(track.avgSpeedMps, unitSystem)} ${speedUnit(unitSystem)}",
            ),
        )
        add(
            StatItem(
                "Max Speed",
                "${formatSpeed(track.maxSpeedMps, unitSystem)} ${speedUnit(unitSystem)}",
            ),
        )
        if (track.elevationGainM > 0) {
            add(StatItem("Elev. Gain", formatElevation(track.elevationGainM, unitSystem)))
        }
    }

    val statLabelPaint = Paint().apply {
        color = onSurfaceVariantColor
        textSize = 13 * density
        typeface = Typeface.DEFAULT
        isAntiAlias = true
    }
    val statValuePaint = Paint().apply {
        color = onSurfaceColor
        textSize = 22 * density
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        isAntiAlias = true
    }

    val columns = 2
    val columnWidth = (contentRight - contentLeft) / columns
    val rowSpacing = 20 * density

    stats.forEachIndexed { index, stat ->
        val col = index % columns
        val x = contentLeft + col * columnWidth

        canvas.drawText(stat.label, x, y, statLabelPaint)
        canvas.drawText(stat.value, x, y + statValuePaint.textSize + 4 * density, statValuePaint)

        if (col == columns - 1 || index == stats.lastIndex) {
            y += statValuePaint.textSize + statLabelPaint.textSize + rowSpacing + 16 * density
        }
    }

    // Footer divider and branding
    y = height - padding * 2.5f
    canvas.drawRect(contentLeft, y, contentRight, y + 1 * density, dividerPaint)
    y += 16 * density

    val footerPaint = Paint().apply {
        color = onSurfaceVariantColor
        textSize = 11 * density
        typeface = Typeface.DEFAULT
        isAntiAlias = true
    }
    canvas.drawText("Recorded with RallyTrax", contentLeft, y, footerPaint)

    // Save to cache
    val shareDir = File(context.cacheDir, "share")
    shareDir.mkdirs()
    val file = File(shareDir, "share_card.png")
    FileOutputStream(file).use { out ->
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
    }
    bitmap.recycle()

    FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file,
    )
}

private fun ellipsize(text: String, paint: Paint, maxWidth: Float): String {
    if (paint.measureText(text) <= maxWidth) return text
    val ellipsis = "\u2026"
    var end = text.length
    while (end > 0 && paint.measureText(text.substring(0, end) + ellipsis) > maxWidth) {
        end--
    }
    return text.substring(0, end) + ellipsis
}

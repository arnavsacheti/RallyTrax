package com.rallytrax.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import com.rallytrax.app.data.local.entity.TrackPointEntity
import java.io.File
import java.io.FileOutputStream

object ThumbnailGenerator {

    fun generate(
        points: List<TrackPointEntity>,
        width: Int = 400,
        height: Int = 300,
        strokeColor: Int = 0xFF1A73E8.toInt(),
        backgroundColor: Int = 0xFF1E1E1E.toInt(),
        context: Context,
    ): String? {
        if (points.size < 2) return null

        val trackId = points.first().trackId
        val lats = points.map { it.lat }
        val lons = points.map { it.lon }
        val minLat = lats.min()
        val maxLat = lats.max()
        val minLon = lons.min()
        val maxLon = lons.max()

        val padding = 20f
        val drawWidth = width - 2 * padding
        val drawHeight = height - 2 * padding
        val scaleX = drawWidth / (maxLon - minLon).coerceAtLeast(0.0001)
        val scaleY = drawHeight / (maxLat - minLat).coerceAtLeast(0.0001)
        val scale = minOf(scaleX, scaleY).toFloat()

        // Center the path within the bitmap
        val scaledWidth = ((maxLon - minLon) * scale).toFloat()
        val scaledHeight = ((maxLat - minLat) * scale).toFloat()
        val offsetX = padding + (drawWidth - scaledWidth) / 2f
        val offsetY = padding + (drawHeight - scaledHeight) / 2f

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Draw rounded rect background
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = backgroundColor
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(
            RectF(0f, 0f, width.toFloat(), height.toFloat()),
            12f,
            12f,
            bgPaint,
        )

        // Build polyline path
        val path = Path()
        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = strokeColor
            style = Paint.Style.STROKE
            strokeWidth = 3f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }

        points.forEachIndexed { index, point ->
            val x = offsetX + ((point.lon - minLon) * scale).toFloat()
            // Flip Y: latitude increases upward but pixel Y increases downward
            val y = offsetY + scaledHeight - ((point.lat - minLat) * scale).toFloat()
            if (index == 0) {
                path.moveTo(x, y)
            } else {
                path.lineTo(x, y)
            }
        }

        canvas.drawPath(path, linePaint)

        // Save to cache directory
        val thumbnailDir = File(context.cacheDir, "thumbnails")
        thumbnailDir.mkdirs()
        val file = File(thumbnailDir, "$trackId.png")

        return try {
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 90, out)
            }
            bitmap.recycle()
            file.absolutePath
        } catch (e: Exception) {
            bitmap.recycle()
            null
        }
    }
}

package com.rallytrax.app.car.map

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import androidx.core.graphics.toColorInt
import androidx.car.app.CarContext
import androidx.car.app.SurfaceCallback
import androidx.car.app.SurfaceContainer
import com.rallytrax.app.recording.LatLng
import com.rallytrax.app.recording.RecordingData

/**
 * Renders a map surface for the Android Auto car display.
 * Uses Google Maps tiles when available, falling back to a Canvas-drawn path view.
 *
 * For the initial implementation, this renders polylines on a dark background
 * using the Android Canvas API. A future iteration can integrate full Google Maps
 * tile rendering via the Maps SDK renderer.
 */
open class CarMapRenderer(
    protected val carContext: CarContext,
) : SurfaceCallback {

    protected var surface: android.view.Surface? = null
    protected var visibleArea: Rect? = null
    protected var stableArea: Rect? = null
    protected var surfaceWidth: Int = 0
    protected var surfaceHeight: Int = 0

    // Map state
    protected var zoomLevel: Float = 15f
    protected var centerLat: Double = 0.0
    protected var centerLon: Double = 0.0

    // Recording data for live path rendering
    private var pathSegments: List<List<LatLng>> = emptyList()
    private var currentPosition: LatLng? = null

    // Paints
    protected val pathPaint = Paint().apply {
        color = "#4285F4".toColorInt() // Rally Blue
        strokeWidth = 6f
        style = Paint.Style.STROKE
        isAntiAlias = true
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    protected val positionPaint = Paint().apply {
        color = "#1A73E8".toColorInt()
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    protected val positionBorderPaint = Paint().apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 3f
        isAntiAlias = true
    }

    protected val backgroundPaint = Paint().apply {
        color = "#111318".toColorInt() // Dark surface color
    }

    override fun onSurfaceAvailable(surfaceContainer: SurfaceContainer) {
        surface = surfaceContainer.surface
        surfaceWidth = surfaceContainer.width
        surfaceHeight = surfaceContainer.height
        render()
    }

    override fun onSurfaceDestroyed(surfaceContainer: SurfaceContainer) {
        surface = null
    }

    override fun onVisibleAreaChanged(visibleArea: Rect) {
        this.visibleArea = visibleArea
        render()
    }

    override fun onStableAreaChanged(stableArea: Rect) {
        this.stableArea = stableArea
    }

    override fun onScale(focusX: Float, focusY: Float, scaleFactor: Float) {
        zoomLevel = (zoomLevel * scaleFactor).coerceIn(5f, 20f)
        render()
    }

    override fun onScroll(distanceX: Float, distanceY: Float) {
        // Convert pixel scroll to lat/lon offset
        val metersPerPixel = 156543.03392 * Math.cos(Math.toRadians(centerLat)) / Math.pow(2.0, zoomLevel.toDouble())
        centerLat += distanceY * metersPerPixel / 111320.0
        centerLon -= distanceX * metersPerPixel / (111320.0 * Math.cos(Math.toRadians(centerLat)))
        render()
    }

    fun updateRecordingData(data: RecordingData) {
        pathSegments = data.pathSegments
        currentPosition = data.currentLatLng
        // Auto-center on current position
        data.currentLatLng?.let {
            centerLat = it.latitude
            centerLon = it.longitude
        }
        render()
    }

    fun zoomIn() {
        zoomLevel = (zoomLevel + 1f).coerceAtMost(20f)
        render()
    }

    fun zoomOut() {
        zoomLevel = (zoomLevel - 1f).coerceAtLeast(5f)
        render()
    }

    open fun centerOnPosition() {
        currentPosition?.let {
            centerLat = it.latitude
            centerLon = it.longitude
            render()
        }
    }

    /** Force a full redraw — used when the host configuration changes (day/night). */
    fun refresh() {
        render()
    }

    protected open fun render() {
        val s = surface ?: return
        if (surfaceWidth <= 0 || surfaceHeight <= 0) return

        try {
            val canvas = s.lockCanvas(null) ?: return
            try {
                drawBackground(canvas)
                drawPath(canvas, pathSegments)
                drawCurrentPosition(canvas)
            } finally {
                s.unlockCanvasAndPost(canvas)
            }
        } catch (_: Exception) {
            // Surface may become invalid between check and lock
        }
    }

    protected fun drawBackground(canvas: Canvas) {
        // Check if car is in dark mode
        val isDark = carContext.isDarkMode
        backgroundPaint.color = if (isDark) "#111318".toColorInt() else "#FAFBFF".toColorInt()
        canvas.drawRect(0f, 0f, surfaceWidth.toFloat(), surfaceHeight.toFloat(), backgroundPaint)
    }

    protected fun drawPath(canvas: Canvas, segments: List<List<LatLng>>) {
        if (segments.isEmpty()) return

        val isDark = carContext.isDarkMode
        pathPaint.color = if (isDark) "#A8C8FF".toColorInt() else "#1A73E8".toColorInt()

        for (segment in segments) {
            if (segment.size < 2) continue
            val path = Path()
            var first = true
            for (point in segment) {
                val (x, y) = latLonToScreen(point.latitude, point.longitude)
                if (first) {
                    path.moveTo(x, y)
                    first = false
                } else {
                    path.lineTo(x, y)
                }
            }
            canvas.drawPath(path, pathPaint)
        }
    }

    protected fun drawCurrentPosition(canvas: Canvas) {
        val pos = currentPosition ?: return
        val (x, y) = latLonToScreen(pos.latitude, pos.longitude)

        // Draw position dot with white border
        canvas.drawCircle(x, y, 12f, positionPaint)
        canvas.drawCircle(x, y, 12f, positionBorderPaint)
    }

    /**
     * Converts lat/lon to screen coordinates using a simple Mercator projection.
     */
    protected fun latLonToScreen(lat: Double, lon: Double): Pair<Float, Float> {
        val metersPerPixel = 156543.03392 * Math.cos(Math.toRadians(centerLat)) /
            Math.pow(2.0, zoomLevel.toDouble())

        val dx = (lon - centerLon) * 111320.0 * Math.cos(Math.toRadians(centerLat)) / metersPerPixel
        val dy = (centerLat - lat) * 111320.0 / metersPerPixel

        val x = (surfaceWidth / 2.0 + dx).toFloat()
        val y = (surfaceHeight / 2.0 + dy).toFloat()

        return Pair(x, y)
    }

    fun destroy() {
        surface = null
    }
}

package com.rallytrax.app.car.map

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import androidx.core.graphics.toColorInt
import androidx.car.app.CarContext
import com.rallytrax.app.car.replay.CarReplayState
import com.rallytrax.app.data.local.entity.NoteType
import com.rallytrax.app.data.local.entity.PaceNoteEntity
import com.rallytrax.app.recording.LatLng

/**
 * Extended map renderer for track replay.
 * Adds GPX track polyline, pace note markers, progress shading, and driver position.
 */
class ReplayMapRenderer(carContext: CarContext) : CarMapRenderer(carContext) {

    private var trackPolyline: List<LatLng> = emptyList()
    private var paceNotes: List<PaceNoteEntity> = emptyList()
    private var driverPosition: LatLng? = null
    private var progressFraction: Float = 0f

    // Paint for the full track (dimmed for passed portion)
    private val trackPassedPaint = Paint().apply {
        color = "#66A8C8FF".toColorInt() // Semi-transparent blue
        strokeWidth = 5f
        style = Paint.Style.STROKE
        isAntiAlias = true
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val trackUpcomingPaint = Paint().apply {
        color = "#FFA8C8FF".toColorInt() // Bright blue
        strokeWidth = 7f
        style = Paint.Style.STROKE
        isAntiAlias = true
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    // Pace note marker paints by severity
    private val notePaintMild = Paint().apply {
        color = "#34A853".toColorInt() // Green
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val notePaintModerate = Paint().apply {
        color = "#FBBC04".toColorInt() // Amber
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val notePaintSevere = Paint().apply {
        color = "#EA4335".toColorInt() // Red
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    fun updateReplayState(state: CarReplayState) {
        if (trackPolyline.isEmpty() && state.polylinePoints.isNotEmpty()) {
            trackPolyline = state.polylinePoints
            paceNotes = state.paceNotes
        }
        driverPosition = state.driverPosition
        progressFraction = state.progressFraction

        // Center on driver
        state.driverPosition?.let {
            centerLat = it.latitude
            centerLon = it.longitude
        }

        render()
    }

    override fun render() {
        val s = surface ?: return
        if (surfaceWidth <= 0 || surfaceHeight <= 0) return

        try {
            val canvas = s.lockCanvas(null) ?: return
            try {
                drawBackground(canvas)
                drawTrackPolyline(canvas)
                drawPaceNoteMarkers(canvas)
                drawDriverPosition(canvas)
            } finally {
                s.unlockCanvasAndPost(canvas)
            }
        } catch (_: Exception) {
            // Surface may become invalid
        }
    }

    private fun drawTrackPolyline(canvas: Canvas) {
        if (trackPolyline.size < 2) return

        val isDark = carContext.isDarkMode

        trackPassedPaint.color = if (isDark) {
            "#4DA8C8FF".toColorInt()
        } else {
            "#4D1A73E8".toColorInt()
        }
        trackUpcomingPaint.color = if (isDark) {
            "#FFA8C8FF".toColorInt()
        } else {
            "#FF1A73E8".toColorInt()
        }

        val splitIndex = (trackPolyline.size * progressFraction).toInt()
            .coerceIn(0, trackPolyline.size - 1)

        // Draw passed portion (dimmed)
        if (splitIndex > 0) {
            val passedPath = Path()
            var first = true
            for (i in 0..splitIndex) {
                val pt = trackPolyline[i]
                val (x, y) = latLonToScreen(pt.latitude, pt.longitude)
                if (first) {
                    passedPath.moveTo(x, y)
                    first = false
                } else {
                    passedPath.lineTo(x, y)
                }
            }
            canvas.drawPath(passedPath, trackPassedPaint)
        }

        // Draw upcoming portion (bright)
        if (splitIndex < trackPolyline.size - 1) {
            val upcomingPath = Path()
            var first = true
            for (i in splitIndex until trackPolyline.size) {
                val pt = trackPolyline[i]
                val (x, y) = latLonToScreen(pt.latitude, pt.longitude)
                if (first) {
                    upcomingPath.moveTo(x, y)
                    first = false
                } else {
                    upcomingPath.lineTo(x, y)
                }
            }
            canvas.drawPath(upcomingPath, trackUpcomingPaint)
        }
    }

    private fun drawPaceNoteMarkers(canvas: Canvas) {
        if (trackPolyline.isEmpty()) return

        for (note in paceNotes) {
            if (note.pointIndex >= trackPolyline.size) continue
            val pt = trackPolyline[note.pointIndex]
            val (x, y) = latLonToScreen(pt.latitude, pt.longitude)

            // Skip markers far off screen
            if (x < -50 || x > surfaceWidth + 50 || y < -50 || y > surfaceHeight + 50) continue

            val paint = when {
                note.severity <= 2 -> notePaintSevere   // Tight turns = red
                note.severity <= 4 -> notePaintModerate  // Moderate = amber
                else -> notePaintMild                      // Easy = green
            }

            // Straights and elevation events get smaller markers
            val radius = when (note.noteType) {
                NoteType.STRAIGHT -> 3f
                NoteType.CREST, NoteType.DIP, NoteType.SMALL_CREST, NoteType.SMALL_DIP,
                NoteType.BIG_CREST, NoteType.BIG_DIP -> 4f
                else -> 6f
            }

            canvas.drawCircle(x, y, radius, paint)
        }
    }

    private fun drawDriverPosition(canvas: Canvas) {
        val pos = driverPosition ?: return
        val (x, y) = latLonToScreen(pos.latitude, pos.longitude)

        // Larger driver marker for replay
        positionPaint.color = "#1A73E8".toColorInt()
        canvas.drawCircle(x, y, 14f, positionPaint)
        canvas.drawCircle(x, y, 14f, positionBorderPaint)

        // Inner dot
        val innerPaint = Paint().apply {
            color = Color.WHITE
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        canvas.drawCircle(x, y, 5f, innerPaint)
    }
}

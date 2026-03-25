package com.rallytrax.app.car.screen

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.Header
import androidx.car.app.model.Pane
import androidx.car.app.model.PaneTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import com.rallytrax.app.car.RallyTraxSession
import com.rallytrax.app.data.local.entity.TrackEntity
import com.rallytrax.app.data.preferences.UserPreferencesData
import com.rallytrax.app.util.formatDistance
import com.rallytrax.app.util.formatElapsedTime
import com.rallytrax.app.util.formatSpeed
import com.rallytrax.app.util.speedUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class RouteDetailScreen(
    carContext: CarContext,
    private val session: RallyTraxSession,
    private val trackId: String,
) : Screen(carContext) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var track: TrackEntity? = null
    private var prefs: UserPreferencesData = UserPreferencesData()
    private var noteCount: Int = 0
    private var loaded = false

    init {
        lifecycle.addObserver(object : androidx.lifecycle.DefaultLifecycleObserver {
            override fun onDestroy(owner: androidx.lifecycle.LifecycleOwner) {
                scope.cancel()
            }
        })
        scope.launch {
            prefs = session.preferencesRepository.preferences.first()
            track = session.trackDao.getTrackById(trackId)
            noteCount = session.paceNoteDao.getNoteCount(trackId)
            loaded = true
            invalidate()
        }
    }

    override fun onGetTemplate(): Template {
        val t = track
        val paneBuilder = Pane.Builder()

        if (!loaded) {
            paneBuilder.setLoading(true)
        } else if (t == null) {
            paneBuilder.addRow(
                Row.Builder()
                    .setTitle("Route not found")
                    .build()
            )
        } else {
            val unit = prefs.unitSystem

            // Key rally metrics: Distance, Difficulty, Surface, Pace Notes
            paneBuilder.addRow(
                Row.Builder()
                    .setTitle("Distance")
                    .addText(formatDistance(t.distanceMeters, unit))
                    .build()
            )

            val difficultyText = t.difficultyRating ?: "Unknown"
            val surfaceText = t.primarySurface ?: "Unknown"
            paneBuilder.addRow(
                Row.Builder()
                    .setTitle("Difficulty / Surface")
                    .addText("$difficultyText | $surfaceText")
                    .build()
            )

            paneBuilder.addRow(
                Row.Builder()
                    .setTitle("Pace Notes")
                    .addText(if (noteCount > 0) "$noteCount notes" else "No pace notes")
                    .build()
            )

            paneBuilder.addRow(
                Row.Builder()
                    .setTitle("Avg Speed / Duration")
                    .addText("${formatSpeed(t.avgSpeedMps, unit)} ${speedUnit(unit)} | ${formatElapsedTime(t.durationMs)}")
                    .build()
            )

            // Primary action: Start Replay
            paneBuilder.addAction(
                Action.Builder()
                    .setTitle("Start Replay")
                    .setOnClickListener {
                        screenManager.push(
                            ReplayNavigationScreen(carContext, session, trackId)
                        )
                    }
                    .build()
            )

            // Secondary action: Record on this route
            paneBuilder.addAction(
                Action.Builder()
                    .setTitle("Record Route")
                    .setOnClickListener {
                        screenManager.push(RecordingScreen(carContext, session))
                    }
                    .build()
            )
        }

        return PaneTemplate.Builder(paneBuilder.build())
            .setHeader(
                Header.Builder()
                    .setTitle(t?.name ?: "Route Detail")
                    .setStartHeaderAction(Action.BACK)
                    .build()
            )
            .build()
    }
}

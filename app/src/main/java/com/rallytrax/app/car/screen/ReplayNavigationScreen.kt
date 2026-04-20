package com.rallytrax.app.car.screen

import androidx.car.app.AppManager
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.CarColor
import androidx.car.app.model.Distance
import androidx.car.app.model.Template
import androidx.car.app.navigation.NavigationManager
import androidx.car.app.navigation.NavigationManagerCallback
import androidx.car.app.navigation.model.NavigationTemplate
import androidx.car.app.navigation.model.RoutingInfo
import androidx.car.app.navigation.model.Step
import androidx.car.app.navigation.model.TravelEstimate
import com.rallytrax.app.car.RallyTraxSession
import com.rallytrax.app.car.map.ReplayMapRenderer
import com.rallytrax.app.car.replay.CarReplayManager
import com.rallytrax.app.car.util.severityLabel
import com.rallytrax.app.car.util.toManeuver
import com.rallytrax.app.data.preferences.UserPreferencesData
import com.rallytrax.app.util.formatSpeed
import com.rallytrax.app.util.speedUnit
import java.time.Duration
import java.time.ZonedDateTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class ReplayNavigationScreen(
    carContext: CarContext,
    private val session: RallyTraxSession,
    private val trackId: String,
) : Screen(carContext) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val mapRenderer = ReplayMapRenderer(carContext)
    private val navigationManager = carContext.getCarService(NavigationManager::class.java)
    private val replayManager = CarReplayManager(
        context = carContext,
        trackPointDao = session.trackPointDao,
        paceNoteDao = session.paceNoteDao,
        scope = scope,
    )
    private var prefs: UserPreferencesData = UserPreferencesData()
    private var refreshJob: Job? = null

    /** Guards against multiple navigationEnded() calls per session. */
    private var navigationActive = false

    init {
        // Register map surface callback so the host delivers surface events
        carContext.getCarService(AppManager::class.java).setSurfaceCallback(mapRenderer)
        session.registerRenderer(mapRenderer)

        // Start navigation session (required before returning NavigationTemplate)
        navigationManager.setNavigationManagerCallback(object : NavigationManagerCallback {
            override fun onStopNavigation() {
                // Host requested navigation stop — stop replay without calling
                // navigationEnded() again (the host already considers it stopped).
                replayManager.stopReplay()
                screenManager.pop()
            }
            override fun onAutoDriveEnabled() { }
        })
        navigationManager.navigationStarted()
        navigationActive = true

        lifecycle.addObserver(object : androidx.lifecycle.DefaultLifecycleObserver {
            override fun onDestroy(owner: androidx.lifecycle.LifecycleOwner) {
                refreshJob?.cancel()
                endNavigationIfActive()
                navigationManager.clearNavigationManagerCallback()
                carContext.getCarService(AppManager::class.java).setSurfaceCallback(null)
                session.unregisterRenderer(mapRenderer)
                replayManager.destroy()
                scope.cancel()
                mapRenderer.destroy()
            }
        })
        scope.launch {
            prefs = session.preferencesRepository.preferences.first()
            replayManager.startReplay(trackId, prefs)
        }
        startRefreshLoop()
    }

    private fun startRefreshLoop() {
        refreshJob = scope.launch {
            while (true) {
                delay(1000)
                val state = replayManager.state.value
                if (state.isActive) {
                    mapRenderer.updateReplayState(state)
                    invalidate()
                }
                if (state.isFinished) {
                    invalidate()
                    break
                }
            }
        }
    }

    override fun onGetTemplate(): Template {
        val state = replayManager.state.value
        val unit = prefs.unitSystem
        val builder = NavigationTemplate.Builder()

        // Map action strip
        builder.setMapActionStrip(
            ActionStrip.Builder()
                .addAction(
                    Action.Builder()
                        .setTitle("+")
                        .setOnClickListener { mapRenderer.zoomIn() }
                        .build()
                )
                .addAction(
                    Action.Builder()
                        .setTitle("-")
                        .setOnClickListener { mapRenderer.zoomOut() }
                        .build()
                )
                .addAction(
                    Action.Builder()
                        .setTitle("Center")
                        .setOnClickListener { mapRenderer.centerOnPosition() }
                        .build()
                )
                .build()
        )

        // Navigation info with pace note routing card
        if (state.isActive && !state.isLoading) {
            val nextNote = state.nextNote
            if (nextNote != null) {
                // Build step text: include off-route warning if applicable
                val noteText = if (state.isOffRoute) {
                    "OFF ROUTE | ${nextNote.callText}"
                } else {
                    nextNote.callText
                }

                val maneuver = nextNote.toManeuver()
                val stepBuilder = Step.Builder(noteText)
                    .setManeuver(maneuver)

                // Add speed + severity as cue text
                val speedText = "${formatSpeed(state.currentSpeedMps, unit)} ${speedUnit(unit)}"
                val severity = severityLabel(nextNote.severity)
                if (severity.isNotEmpty()) {
                    stepBuilder.setCue("$speedText | $severity")
                } else {
                    stepBuilder.setCue(speedText)
                }

                val step = stepBuilder.build()

                val distance = Distance.create(
                    state.distanceToNextNote.coerceAtLeast(0.0),
                    Distance.UNIT_METERS,
                )

                builder.setNavigationInfo(
                    RoutingInfo.Builder()
                        .setCurrentStep(step, distance)
                        .setLoading(false)
                        .build()
                )
            } else {
                // No next note — show finish or following state
                val statusText = when {
                    state.isOffRoute -> "OFF ROUTE"
                    state.isFinished -> "Finish!"
                    else -> "Following route..."
                }
                val step = Step.Builder(statusText).build()
                builder.setNavigationInfo(
                    RoutingInfo.Builder()
                        .setCurrentStep(step, Distance.create(0.0, Distance.UNIT_METERS))
                        .setLoading(false)
                        .build()
                )
            }

            // Travel estimate: remaining distance
            builder.setDestinationTravelEstimate(
                TravelEstimate.Builder(
                    Distance.create(
                        state.distanceRemaining.coerceAtLeast(0.0),
                        Distance.UNIT_METERS,
                    ),
                    ZonedDateTime.now().plus(Duration.ZERO),
                ).build()
            )
        } else if (state.isLoading) {
            builder.setNavigationInfo(
                RoutingInfo.Builder()
                    .setLoading(true)
                    .build()
            )
        }

        // Action strip — always include Back so it's never empty (avoids crash)
        val actionStripBuilder = ActionStrip.Builder()

        actionStripBuilder.addAction(
            Action.Builder()
                .setTitle("Back")
                .setOnClickListener {
                    replayManager.stopReplay()
                    endNavigationIfActive()
                    screenManager.pop()
                }
                .build()
        )

        if (state.isActive) {
            actionStripBuilder.addAction(
                Action.Builder()
                    .setTitle(if (state.isMuted) "Unmute" else "Mute")
                    .setOnClickListener {
                        replayManager.toggleMute()
                        invalidate()
                    }
                    .build()
            )

            actionStripBuilder.addAction(
                Action.Builder()
                    .setTitle("Stop")
                    .setBackgroundColor(CarColor.RED)
                    .setOnClickListener {
                        replayManager.stopReplay()
                        endNavigationIfActive()
                        screenManager.pop()
                    }
                    .build()
            )
        } else if (state.isFinished) {
            actionStripBuilder.addAction(
                Action.Builder()
                    .setTitle("Done")
                    .setOnClickListener {
                        endNavigationIfActive()
                        screenManager.pop()
                    }
                    .build()
            )
        }

        builder.setActionStrip(actionStripBuilder.build())

        return builder.build()
    }

    /**
     * Ends navigation if still active. Safe to call multiple times —
     * only the first call reaches NavigationManager.
     */
    private fun endNavigationIfActive() {
        if (navigationActive) {
            navigationActive = false
            navigationManager.navigationEnded()
        }
    }
}

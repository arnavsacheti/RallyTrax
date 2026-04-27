package com.rallytrax.app.car.screen

import android.content.Intent
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
import com.rallytrax.app.car.map.CarMapRenderer
import com.rallytrax.app.data.preferences.UserPreferencesData
import com.rallytrax.app.recording.RecordingStatus
import com.rallytrax.app.recording.TrackingService
import com.rallytrax.app.util.formatDistance
import com.rallytrax.app.util.formatElapsedTime
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

class RecordingScreen(
    carContext: CarContext,
    private val session: RallyTraxSession,
) : Screen(carContext) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val mapRenderer = CarMapRenderer(carContext)
    private val navigationManager = carContext.getCarService(NavigationManager::class.java)
    private var prefs: UserPreferencesData = UserPreferencesData()
    private var refreshJob: Job? = null

    /** Guards against multiple navigationEnded() calls per session. */
    private var navigationActive = false

    init {
        // Register map surface callback so the host delivers surface events
        carContext.getCarService(AppManager::class.java).setSurfaceCallback(mapRenderer)
        // Let the session refresh this renderer when the host flips day/night
        session.registerRenderer(mapRenderer)

        // Start navigation session (required before returning NavigationTemplate)
        navigationManager.setNavigationManagerCallback(object : NavigationManagerCallback {
            override fun onStopNavigation() {
                // Host requested navigation stop — end recording without calling
                // navigationEnded() again (the host already considers it stopped).
                stopRecordingOnly()
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
                scope.cancel()
                mapRenderer.destroy()
            }
        })
        scope.launch {
            prefs = session.preferencesRepository.preferences.first()
        }
        startRefreshLoop()
    }

    private fun startRefreshLoop() {
        refreshJob = scope.launch {
            while (true) {
                delay(1000)
                val status = TrackingService.recordingStatus.value
                if (status == RecordingStatus.RECORDING || status == RecordingStatus.PAUSED) {
                    val data = TrackingService.recordingData.value
                    mapRenderer.updateRecordingData(data)
                    invalidate()
                }
            }
        }
    }

    override fun onGetTemplate(): Template {
        val status = TrackingService.recordingStatus.value
        val data = TrackingService.recordingData.value
        val unit = prefs.unitSystem

        val builder = NavigationTemplate.Builder()

        // Map action strip: zoom + center
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

        // Navigation info: speed + GPS quality in the routing card
        if (status == RecordingStatus.RECORDING || status == RecordingStatus.PAUSED) {
            val speedText = "${formatSpeed(data.currentSpeed, unit)} ${speedUnit(unit)}"
            val gpsLabel = gpsQualityLabel(data.gpsAccuracy)
            val stepText = "$speedText | GPS: $gpsLabel"

            val step = Step.Builder(stepText).build()
            val distance = Distance.create(data.distanceMeters, Distance.UNIT_METERS)

            builder.setNavigationInfo(
                RoutingInfo.Builder()
                    .setCurrentStep(step, distance)
                    .setLoading(false)
                    .build()
            )

            // Travel estimate: elapsed time as remaining duration display
            builder.setDestinationTravelEstimate(
                TravelEstimate.Builder(
                    Distance.create(data.distanceMeters, Distance.UNIT_METERS),
                    ZonedDateTime.now().plus(Duration.ZERO),
                ).build()
            )
        }

        // Action strip: recording controls
        val actionStripBuilder = ActionStrip.Builder()

        when (status) {
            RecordingStatus.IDLE, RecordingStatus.STOPPED -> {
                actionStripBuilder.addAction(
                    Action.Builder()
                        .setTitle("Start")
                        .setOnClickListener { startRecording() }
                        .build()
                )
            }
            RecordingStatus.RECORDING -> {
                // Keep the in-drive primary strip to two critical actions
                // (Pause + Stop). Adding Fuel Stop here pushed the total
                // simultaneous action count above what's safely glanceable
                // while driving and risked sub-76dp touch targets on smaller
                // heads-up displays. Fuel Stop is still markable from the
                // phone and from Settings; it doesn't need to be in the
                // primary in-drive strip.
                actionStripBuilder.addAction(
                    Action.Builder()
                        .setTitle("Pause")
                        .setOnClickListener { pauseRecording() }
                        .build()
                )
                actionStripBuilder.addAction(
                    Action.Builder()
                        .setTitle("Stop")
                        .setBackgroundColor(CarColor.RED)
                        .setOnClickListener { stopRecording() }
                        .build()
                )
            }
            RecordingStatus.PAUSED -> {
                actionStripBuilder.addAction(
                    Action.Builder()
                        .setTitle("Resume")
                        .setOnClickListener { resumeRecording() }
                        .build()
                )
                actionStripBuilder.addAction(
                    Action.Builder()
                        .setTitle("Stop")
                        .setBackgroundColor(CarColor.RED)
                        .setOnClickListener { stopRecording() }
                        .build()
                )
            }
        }

        builder.setActionStrip(actionStripBuilder.build())
        builder.setBackgroundColor(CarColor.DEFAULT)

        return builder.build()
    }

    private fun gpsQualityLabel(accuracy: Float?): String {
        if (accuracy == null) return "No Signal"
        return when {
            accuracy <= 5f -> "Excellent"
            accuracy <= 10f -> "Good"
            accuracy <= 20f -> "Fair"
            else -> "Poor"
        }
    }

    private fun startRecording() {
        val intent = Intent(carContext, TrackingService::class.java).apply {
            action = TrackingService.ACTION_START
        }
        carContext.startForegroundService(intent)
        invalidate()
    }

    private fun pauseRecording() {
        val intent = Intent(carContext, TrackingService::class.java).apply {
            action = TrackingService.ACTION_PAUSE
        }
        carContext.startService(intent)
        invalidate()
    }

    private fun resumeRecording() {
        val intent = Intent(carContext, TrackingService::class.java).apply {
            action = TrackingService.ACTION_RESUME
        }
        carContext.startService(intent)
        invalidate()
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

    /**
     * Full stop: stops the recording service AND ends the navigation session.
     * Called by user-initiated stop (button press).
     */
    private fun stopRecording() {
        stopRecordingOnly()
        endNavigationIfActive()
    }

    /**
     * Stops only the recording service and pops the screen.
     * Does NOT call navigationEnded() — used when the host already ended navigation
     * via [NavigationManagerCallback.onStopNavigation].
     */
    private fun stopRecordingOnly() {
        val intent = Intent(carContext, TrackingService::class.java).apply {
            action = TrackingService.ACTION_STOP
        }
        carContext.startService(intent)
        scope.launch {
            delay(500)
            screenManager.pop()
        }
    }

}

package com.rallytrax.app.car.screen

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.CarIcon
import androidx.car.app.model.Header
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Pane
import androidx.car.app.model.PaneTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Tab
import androidx.car.app.model.TabContents
import androidx.car.app.model.TabTemplate
import androidx.car.app.model.Template
import androidx.core.graphics.drawable.IconCompat
import com.rallytrax.app.R
import com.rallytrax.app.car.RallyTraxSession
import com.rallytrax.app.data.local.entity.TrackEntity
import com.rallytrax.app.data.local.entity.VehicleEntity
import com.rallytrax.app.data.preferences.UserPreferencesData
import com.rallytrax.app.recording.RecordingStatus
import com.rallytrax.app.recording.TrackingService
import com.rallytrax.app.util.formatDate
import com.rallytrax.app.util.formatDistance
import com.rallytrax.app.util.formatElapsedTime
import com.rallytrax.app.util.formatSpeed
import com.rallytrax.app.util.speedUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainTabScreen(
    carContext: CarContext,
    private val session: RallyTraxSession,
) : Screen(carContext) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    init {
        lifecycle.addObserver(object : androidx.lifecycle.DefaultLifecycleObserver {
            override fun onDestroy(owner: androidx.lifecycle.LifecycleOwner) {
                scope.cancel()
            }
        })
    }

    private var activeTab = TAB_DRIVE
    private var routes: List<TrackEntity> = emptyList()
    private var activeVehicle: VehicleEntity? = null
    private var prefs: UserPreferencesData = UserPreferencesData()
    private var dataLoaded = false
    private var collectJob: Job? = null

    companion object {
        const val TAB_DRIVE = "drive"
        const val TAB_ROUTES = "routes"
    }

    init {
        loadData()
    }

    private fun loadData() {
        collectJob?.cancel()
        collectJob = scope.launch {
            prefs = session.preferencesRepository.preferences.first()

            routes = session.trackDao.getAllTracksOnce()
                .filter { it.trackCategory == "route" }
                .take(100)

            val vehicles = session.vehicleDao.getAllVehicles().first()
            activeVehicle = vehicles.firstOrNull { it.isActive }

            dataLoaded = true
            invalidate()
        }
    }

    override fun onGetTemplate(): Template {
        val tabBuilder = TabTemplate.Builder(object : TabTemplate.TabCallback {
            override fun onTabSelected(tabContentId: String) {
                activeTab = tabContentId
                invalidate()
            }
        })

        // Drive tab
        tabBuilder.addTab(
            Tab.Builder()
                .setTitle("Drive")
                .setContentId(TAB_DRIVE)
                .setIcon(
                    CarIcon.Builder(
                        IconCompat.createWithResource(carContext, R.drawable.ic_car_nav)
                    ).build()
                )
                .build()
        )

        // Routes tab
        tabBuilder.addTab(
            Tab.Builder()
                .setTitle("Routes")
                .setContentId(TAB_ROUTES)
                .setIcon(
                    CarIcon.Builder(
                        IconCompat.createWithResource(carContext, R.drawable.ic_car_routes)
                    ).build()
                )
                .build()
        )

        tabBuilder.setActiveTabContentId(activeTab)
        tabBuilder.setHeaderAction(Action.APP_ICON)

        // Settings action in the header action strip
        tabBuilder.setTabContents(
            when (activeTab) {
                TAB_DRIVE -> buildDriveContent()
                TAB_ROUTES -> buildRoutesContent()
                else -> buildDriveContent()
            }
        )

        return tabBuilder.build()
    }

    private fun buildDriveContent(): TabContents {
        val recordingStatus = TrackingService.recordingStatus.value
        val paneBuilder = Pane.Builder()

        when (recordingStatus) {
            RecordingStatus.IDLE, RecordingStatus.STOPPED -> {
                // Show active vehicle
                val vehicleName = activeVehicle?.let { "${it.year} ${it.name}" } ?: "No vehicle selected"
                paneBuilder.addRow(
                    Row.Builder()
                        .setTitle("Ready to Drive")
                        .addText(vehicleName)
                        .build()
                )

                // Start Recording action
                paneBuilder.addAction(
                    Action.Builder()
                        .setTitle("Start Recording")
                        .setOnClickListener {
                            screenManager.push(RecordingScreen(carContext, session))
                        }
                        .build()
                )

                // Vehicle picker if needed
                if (activeVehicle == null) {
                    paneBuilder.addAction(
                        Action.Builder()
                            .setTitle("Select Vehicle")
                            .setOnClickListener {
                                screenManager.push(VehiclePickerScreen(carContext, session))
                            }
                            .build()
                    )
                }
            }
            RecordingStatus.RECORDING, RecordingStatus.PAUSED -> {
                val data = TrackingService.recordingData.value
                val statusText = if (recordingStatus == RecordingStatus.RECORDING) "Recording" else "Paused"
                val gpsQuality = gpsQualityLabel(data.gpsAccuracy)

                paneBuilder.addRow(
                    Row.Builder()
                        .setTitle(statusText)
                        .addText("${formatElapsedTime(data.elapsedTimeMs)} | ${formatDistance(data.distanceMeters, prefs.unitSystem)}")
                        .build()
                )
                paneBuilder.addRow(
                    Row.Builder()
                        .setTitle("Speed")
                        .addText("${formatSpeed(data.currentSpeed, prefs.unitSystem)} ${speedUnit(prefs.unitSystem)} | GPS: $gpsQuality")
                        .build()
                )
                paneBuilder.addAction(
                    Action.Builder()
                        .setTitle("View Recording")
                        .setOnClickListener {
                            screenManager.push(RecordingScreen(carContext, session))
                        }
                        .build()
                )
            }
        }

        val paneTemplate = PaneTemplate.Builder(paneBuilder.build())
            .setHeader(
                Header.Builder()
                    .setTitle("Drive")
                    .addEndHeaderAction(
                        Action.Builder()
                            .setTitle("Settings")
                            .setOnClickListener {
                                screenManager.push(SettingsScreen(carContext, session))
                            }
                            .build()
                    )
                    .build()
            )
            .build()

        return TabContents.Builder(paneTemplate).build()
    }

    private fun buildRoutesContent(): TabContents {
        val listBuilder = ItemList.Builder()

        if (!dataLoaded) {
            listBuilder.setNoItemsMessage("Loading routes...")
        } else if (routes.isEmpty()) {
            listBuilder.setNoItemsMessage("No routes yet. Record your first drive!")
        } else {
            for (route in routes) {
                val subtitle = buildString {
                    append(formatDistance(route.distanceMeters, prefs.unitSystem))
                    route.difficultyRating?.let { append(" | $it") }
                    route.primarySurface?.let { append(" | $it") }
                }

                listBuilder.addItem(
                    Row.Builder()
                        .setTitle(route.name)
                        .addText(subtitle)
                        .addText(formatDate(route.recordedAt))
                        .setOnClickListener {
                            screenManager.push(RouteDetailScreen(carContext, session, route.id))
                        }
                        .build()
                )
            }
        }

        val listTemplate = ListTemplate.Builder()
            .setSingleList(listBuilder.build())
            .setHeader(
                Header.Builder()
                    .setTitle("Routes")
                    .addEndHeaderAction(
                        Action.Builder()
                            .setTitle("Settings")
                            .setOnClickListener {
                                screenManager.push(SettingsScreen(carContext, session))
                            }
                            .build()
                    )
                    .build()
            )
            .build()

        return TabContents.Builder(listTemplate).build()
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
}

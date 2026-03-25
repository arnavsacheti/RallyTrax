package com.rallytrax.app.car.screen

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.Header
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.car.app.model.Toggle
import com.rallytrax.app.car.RallyTraxSession
import com.rallytrax.app.data.preferences.UnitSystem
import com.rallytrax.app.data.preferences.UserPreferencesData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class SettingsScreen(
    carContext: CarContext,
    private val session: RallyTraxSession,
) : Screen(carContext) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var prefs: UserPreferencesData = UserPreferencesData()
    private var activeVehicleName: String? = null
    private var loaded = false

    init {
        lifecycle.addObserver(object : androidx.lifecycle.DefaultLifecycleObserver {
            override fun onDestroy(owner: androidx.lifecycle.LifecycleOwner) {
                scope.cancel()
            }
        })
        scope.launch {
            prefs = session.preferencesRepository.preferences.first()
            val vehicles = session.vehicleDao.getAllVehicles().first()
            activeVehicleName = vehicles.firstOrNull { it.isActive }?.name
            loaded = true
            invalidate()
        }
    }

    override fun onGetTemplate(): Template {
        val listBuilder = ItemList.Builder()

        if (!loaded) {
            listBuilder.setNoItemsMessage("Loading...")
        } else {
            // Vehicle selection
            listBuilder.addItem(
                Row.Builder()
                    .setTitle("Vehicle")
                    .addText(activeVehicleName ?: "None selected")
                    .setOnClickListener {
                        screenManager.push(VehiclePickerScreen(carContext, session))
                    }
                    .build()
            )

            // Unit System toggle
            listBuilder.addItem(
                Row.Builder()
                    .setTitle("Unit System")
                    .addText(if (prefs.unitSystem == UnitSystem.METRIC) "Metric (km/h)" else "Imperial (mph)")
                    .setOnClickListener {
                        scope.launch {
                            val newUnit = if (prefs.unitSystem == UnitSystem.METRIC) {
                                UnitSystem.IMPERIAL
                            } else {
                                UnitSystem.METRIC
                            }
                            session.preferencesRepository.setUnitSystem(newUnit)
                            prefs = prefs.copy(unitSystem = newUnit)
                            invalidate()
                        }
                    }
                    .build()
            )

            // TTS toggle
            listBuilder.addItem(
                Row.Builder()
                    .setTitle("Pace Note Audio")
                    .setToggle(
                        Toggle.Builder { isChecked ->
                            scope.launch {
                                session.preferencesRepository.setTtsEnabled(isChecked)
                                prefs = prefs.copy(ttsEnabled = isChecked)
                            }
                        }
                            .setChecked(prefs.ttsEnabled)
                            .build()
                    )
                    .build()
            )

            // Auto-pause toggle
            listBuilder.addItem(
                Row.Builder()
                    .setTitle("Auto-Pause")
                    .addText("Pause recording when stationary")
                    .setToggle(
                        Toggle.Builder { isChecked ->
                            scope.launch {
                                session.preferencesRepository.setAutoPauseEnabled(isChecked)
                                prefs = prefs.copy(autoPauseEnabled = isChecked)
                            }
                        }
                            .setChecked(prefs.autoPauseEnabled)
                            .build()
                    )
                    .build()
            )
        }

        return ListTemplate.Builder()
            .setSingleList(listBuilder.build())
            .setHeader(
                Header.Builder()
                    .setTitle("Settings")
                    .setStartHeaderAction(Action.BACK)
                    .build()
            )
            .build()
    }
}

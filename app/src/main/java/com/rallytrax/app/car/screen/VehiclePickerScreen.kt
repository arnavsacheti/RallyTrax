package com.rallytrax.app.car.screen

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import com.rallytrax.app.car.RallyTraxSession
import com.rallytrax.app.data.local.entity.VehicleEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class VehiclePickerScreen(
    carContext: CarContext,
    private val session: RallyTraxSession,
) : Screen(carContext) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var vehicles: List<VehicleEntity> = emptyList()
    private var loaded = false

    init {
        lifecycle.addObserver(object : androidx.lifecycle.DefaultLifecycleObserver {
            override fun onDestroy(owner: androidx.lifecycle.LifecycleOwner) {
                scope.cancel()
            }
        })
        scope.launch {
            vehicles = session.vehicleDao.getAllVehicles().first()
            loaded = true
            invalidate()
        }
    }

    override fun onGetTemplate(): Template {
        val listBuilder = ItemList.Builder()

        if (!loaded) {
            listBuilder.setNoItemsMessage("Loading vehicles...")
        } else if (vehicles.isEmpty()) {
            listBuilder.setNoItemsMessage("No vehicles. Add one in the phone app.")
        } else {
            for (vehicle in vehicles) {
                val details = buildString {
                    append("${vehicle.year} ${vehicle.make} ${vehicle.model}")
                    if (vehicle.isActive) append(" \u2022 Active")
                }
                listBuilder.addItem(
                    Row.Builder()
                        .setTitle(vehicle.name)
                        .addText(details)
                        .setOnClickListener {
                            scope.launch {
                                session.vehicleDao.clearActiveFlag()
                                session.vehicleDao.updateVehicle(
                                    vehicle.copy(isActive = true, updatedAt = System.currentTimeMillis())
                                )
                                screenManager.pop()
                            }
                        }
                        .build()
                )
            }
        }

        return ListTemplate.Builder()
            .setSingleList(listBuilder.build())
            .setTitle("Select Vehicle")
            .setHeaderAction(Action.BACK)
            .build()
    }
}

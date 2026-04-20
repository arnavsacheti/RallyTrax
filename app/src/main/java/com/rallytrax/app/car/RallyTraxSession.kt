package com.rallytrax.app.car

import android.content.Intent
import android.content.res.Configuration
import androidx.car.app.Screen
import androidx.car.app.Session
import com.rallytrax.app.car.map.CarMapRenderer
import com.rallytrax.app.car.screen.MainTabScreen
import com.rallytrax.app.data.local.dao.PaceNoteDao
import com.rallytrax.app.data.local.dao.TrackDao
import com.rallytrax.app.data.local.dao.TrackPointDao
import com.rallytrax.app.data.local.dao.VehicleDao
import com.rallytrax.app.data.preferences.UserPreferencesRepository

class RallyTraxSession(
    val trackDao: TrackDao,
    val trackPointDao: TrackPointDao,
    val paceNoteDao: PaceNoteDao,
    val vehicleDao: VehicleDao,
    val preferencesRepository: UserPreferencesRepository,
) : Session() {

    // Screens register their map renderer so the session can force a redraw
    // when the car host flips day/night (CarContext.isDarkMode reads fresh on
    // each render() call; we just need to re-trigger the draw).
    private val activeRenderers = mutableSetOf<CarMapRenderer>()

    fun registerRenderer(renderer: CarMapRenderer) {
        activeRenderers.add(renderer)
    }

    fun unregisterRenderer(renderer: CarMapRenderer) {
        activeRenderers.remove(renderer)
    }

    override fun onCreateScreen(intent: Intent): Screen {
        return MainTabScreen(carContext, this)
    }

    override fun onCarConfigurationChanged(newConfiguration: Configuration) {
        super.onCarConfigurationChanged(newConfiguration)
        // Invalidate the top template so list/pane content re-picks day/night
        // tokens on the next onGetTemplate(), and manually refresh any surface
        // renderers (surfaces don't redraw on config change by themselves).
        carContext.getCarService(androidx.car.app.ScreenManager::class.java)
            .top.invalidate()
        activeRenderers.toList().forEach { it.refresh() }
    }
}

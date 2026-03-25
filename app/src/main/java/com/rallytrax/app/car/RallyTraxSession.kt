package com.rallytrax.app.car

import android.content.Intent
import androidx.car.app.Screen
import androidx.car.app.Session
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

    override fun onCreateScreen(intent: Intent): Screen {
        return MainTabScreen(carContext, this)
    }
}

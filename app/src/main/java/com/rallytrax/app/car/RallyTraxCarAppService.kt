package com.rallytrax.app.car

import androidx.car.app.CarAppService
import androidx.car.app.Session
import androidx.car.app.SessionInfo
import androidx.car.app.validation.HostValidator
import com.rallytrax.app.data.local.dao.PaceNoteDao
import com.rallytrax.app.data.local.dao.TrackDao
import com.rallytrax.app.data.local.dao.TrackPointDao
import com.rallytrax.app.data.local.dao.VehicleDao
import com.rallytrax.app.data.preferences.UserPreferencesRepository
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class RallyTraxCarAppService : CarAppService() {

    @Inject lateinit var trackDao: TrackDao
    @Inject lateinit var trackPointDao: TrackPointDao
    @Inject lateinit var paceNoteDao: PaceNoteDao
    @Inject lateinit var vehicleDao: VehicleDao
    @Inject lateinit var preferencesRepository: UserPreferencesRepository

    override fun createHostValidator(): HostValidator {
        // ALLOW_ALL_HOSTS_VALIDATOR is acceptable per Google's documentation.
        // The Play Store validates host signatures during the review process.
        return HostValidator.ALLOW_ALL_HOSTS_VALIDATOR
    }

    override fun onCreateSession(sessionInfo: SessionInfo): Session {
        return RallyTraxSession(
            trackDao = trackDao,
            trackPointDao = trackPointDao,
            paceNoteDao = paceNoteDao,
            vehicleDao = vehicleDao,
            preferencesRepository = preferencesRepository,
        )
    }
}

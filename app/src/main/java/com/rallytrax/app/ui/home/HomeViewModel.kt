package com.rallytrax.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rallytrax.app.data.local.dao.TrackDao
import com.rallytrax.app.data.local.entity.TrackEntity
import com.rallytrax.app.data.preferences.UserPreferencesData
import com.rallytrax.app.data.preferences.UserPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoField
import javax.inject.Inject

data class HomeDashboardState(
    val recentTracks: List<TrackEntity> = emptyList(),
    val weeklyDistanceMeters: Double = 0.0,
    val monthlyRecordingCount: Int = 0,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val trackDao: TrackDao,
    preferencesRepository: UserPreferencesRepository,
) : ViewModel() {

    val preferences: StateFlow<UserPreferencesData> = preferencesRepository.preferences
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UserPreferencesData())

    val dashboardState: StateFlow<HomeDashboardState> = trackDao.getAllTracks()
        .map { tracks ->
            val now = Instant.now()
            val zone = ZoneId.systemDefault()
            val today = now.atZone(zone).toLocalDate()

            // Start of current week (Monday)
            val startOfWeek = today.with(ChronoField.DAY_OF_WEEK, 1)
            val weekStartMs = startOfWeek.atStartOfDay(zone).toInstant().toEpochMilli()

            // Start of current month
            val startOfMonth = today.withDayOfMonth(1)
            val monthStartMs = startOfMonth.atStartOfDay(zone).toInstant().toEpochMilli()

            val weeklyDistance = tracks
                .filter { it.recordedAt >= weekStartMs }
                .sumOf { it.distanceMeters }

            val monthlyCount = tracks.count { it.recordedAt >= monthStartMs }

            HomeDashboardState(
                recentTracks = tracks.take(5),
                weeklyDistanceMeters = weeklyDistance,
                monthlyRecordingCount = monthlyCount,
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HomeDashboardState())
}

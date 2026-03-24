package com.rallytrax.app.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rallytrax.app.data.local.dao.TrackDao
import com.rallytrax.app.data.local.dao.VehicleDao
import com.rallytrax.app.data.preferences.UserPreferencesData
import com.rallytrax.app.data.preferences.UserPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import javax.inject.Inject

data class ProfileState(
    val currentStreak: Int = 0,
    val activeDaysThisMonth: Set<LocalDate> = emptySet(),
    // Lifetime stats
    val totalDrives: Int = 0,
    val totalDistanceMeters: Double = 0.0,
    val avgDriveLengthMeters: Double = 0.0,
    val longestDriveMeters: Double = 0.0,
    val vehicleCount: Int = 0,
    val stintCount: Int = 0,
    // Yearly stats
    val yearlyDrives: Int = 0,
    val yearlyDistanceMeters: Double = 0.0,
)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val trackDao: TrackDao,
    private val vehicleDao: VehicleDao,
    preferencesRepository: UserPreferencesRepository,
) : ViewModel() {

    val preferences: StateFlow<UserPreferencesData> = preferencesRepository.preferences
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UserPreferencesData())

    val profileState: StateFlow<ProfileState> = combine(
        trackDao.getStints(),
        vehicleDao.getAllVehicles(),
    ) { stints, allVehicles ->
        val zone = ZoneId.systemDefault()
        val today = Instant.now().atZone(zone).toLocalDate()
        val yearMonth = YearMonth.from(today)

        // Active days this month (stints only)
        val monthStart = yearMonth.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val monthEnd = yearMonth.atEndOfMonth().plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val activeDaysThisMonth = stints
            .filter { it.recordedAt in monthStart until monthEnd }
            .map { Instant.ofEpochMilli(it.recordedAt).atZone(zone).toLocalDate() }
            .toSet()

        // Streak calculation (stints only)
        val drivingDays = stints
            .map { Instant.ofEpochMilli(it.recordedAt).atZone(zone).toLocalDate() }
            .distinct()
            .sortedDescending()
        val currentStreak = if (drivingDays.isEmpty()) 0 else {
            val firstDay = drivingDays.first()
            if (ChronoUnit.DAYS.between(firstDay, today) > 1) 0
            else {
                var streak = 1
                for (i in 0 until drivingDays.size - 1) {
                    if (ChronoUnit.DAYS.between(drivingDays[i + 1], drivingDays[i]) == 1L) streak++
                    else break
                }
                streak
            }
        }

        // Lifetime stats (stints only)
        val totalDrives = stints.size
        val totalDistance = stints.sumOf { it.distanceMeters }
        val avgDriveLength = if (totalDrives > 0) totalDistance / totalDrives else 0.0
        val longestDrive = stints.maxOfOrNull { it.distanceMeters } ?: 0.0
        val vehicleCount = allVehicles.count { !it.isArchived }

        // Yearly stats (stints only)
        val yearStart = LocalDate.of(today.year, 1, 1).atStartOfDay(zone).toInstant().toEpochMilli()
        val yearlyTracks = stints.filter { it.recordedAt >= yearStart }

        ProfileState(
            currentStreak = currentStreak,
            activeDaysThisMonth = activeDaysThisMonth,
            totalDrives = totalDrives,
            totalDistanceMeters = totalDistance,
            avgDriveLengthMeters = avgDriveLength,
            longestDriveMeters = longestDrive,
            vehicleCount = vehicleCount,
            stintCount = stints.size,
            yearlyDrives = yearlyTracks.size,
            yearlyDistanceMeters = yearlyTracks.sumOf { it.distanceMeters },
        )
    }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ProfileState())
}

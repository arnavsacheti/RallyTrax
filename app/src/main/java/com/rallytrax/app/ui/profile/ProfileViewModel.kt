package com.rallytrax.app.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rallytrax.app.data.local.dao.DriverProfileDao
import com.rallytrax.app.data.local.dao.TrackDao
import com.rallytrax.app.data.local.dao.VehicleDao
import com.rallytrax.app.data.preferences.UserPreferencesData
import com.rallytrax.app.data.preferences.UserPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import javax.inject.Inject

data class VehicleStats(
    val name: String,
    val driveCount: Int,
    val totalDistanceMeters: Double,
    val avgSmoothness: Int?,
)

data class ProfileState(
    val currentStreak: Int = 0,
    val activeDaysThisMonth: Set<LocalDate> = emptySet(),
    val dailyDistances: Map<LocalDate, Double> = emptyMap(),
    // Lifetime stats
    val totalDrives: Int = 0,
    val totalDistanceMeters: Double = 0.0,
    val avgDriveLengthMeters: Double = 0.0,
    val longestDriveMeters: Double = 0.0,
    val vehicleCount: Int = 0,
    val stintCount: Int = 0,
    // Driving trends (last 20 stints with insight data)
    val smoothnessTrend: List<Float> = emptyList(),
    val brakingTrend: List<Float> = emptyList(),
    val corneringGTrend: List<Float> = emptyList(),
    val latestSmoothness: Int? = null,
    val latestBraking: Int? = null,
    val latestCorneringG: Double? = null,
    // Yearly stats
    val yearlyDrives: Int = 0,
    val yearlyDistanceMeters: Double = 0.0,
    // Vehicle comparison
    val vehicleComparison: List<VehicleStats> = emptyList(),
    val driverProfile: Map<Int, Double> = emptyMap(),
)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val trackDao: TrackDao,
    private val vehicleDao: VehicleDao,
    private val driverProfileDao: DriverProfileDao,
    preferencesRepository: UserPreferencesRepository,
) : ViewModel() {

    private val _driverProfile = MutableStateFlow<Map<Int, Double>>(emptyMap())

    init {
        viewModelScope.launch {
            val entries = driverProfileDao.getAll()
                .filter { it.sampleCount >= 3 }
            _driverProfile.value = entries.associate { it.radiusBucketM to it.avgSpeedMps }
        }
    }

    val preferences: StateFlow<UserPreferencesData> = preferencesRepository.preferences
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UserPreferencesData())

    val profileState: StateFlow<ProfileState> = combine(
        trackDao.getStints(),
        vehicleDao.getAllVehicles(),
        _driverProfile,
    ) { stints, allVehicles, driverProfile ->
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

        // Daily distances for heatmap
        val dailyDistances = stints.groupBy {
            Instant.ofEpochMilli(it.recordedAt).atZone(zone).toLocalDate()
        }.mapValues { (_, tracks) -> tracks.sumOf { it.distanceMeters } }

        // Driving trends (most recent 20 stints with insight data, reversed to oldest-first for sparkline)
        val smoothnessTrend = stints.filter { it.smoothnessScore != null }.take(20)
            .reversed().mapNotNull { it.smoothnessScore?.toFloat() }
        val brakingTrend = stints.filter { it.brakingEfficiencyScore != null }.take(20)
            .reversed().mapNotNull { it.brakingEfficiencyScore?.toFloat() }
        val corneringGTrend = stints.filter { it.avgCorneringG != null }.take(20)
            .reversed().mapNotNull { it.avgCorneringG?.toFloat() }
        val latestSmoothness = stints.firstOrNull { it.smoothnessScore != null }?.smoothnessScore
        val latestBraking = stints.firstOrNull { it.brakingEfficiencyScore != null }?.brakingEfficiencyScore
        val latestCorneringG = stints.firstOrNull { it.avgCorneringG != null }?.avgCorneringG

        // Yearly stats (stints only)
        val yearStart = LocalDate.of(today.year, 1, 1).atStartOfDay(zone).toInstant().toEpochMilli()
        val yearlyTracks = stints.filter { it.recordedAt >= yearStart }

        // Per-vehicle comparison
        val vehicleMap = allVehicles.associateBy { it.id }
        val vehicleComparison = stints
            .groupBy { it.vehicleId }
            .map { (vehicleId, vehicleStints) ->
                val name = if (vehicleId != null) {
                    vehicleMap[vehicleId]?.name ?: "Unknown"
                } else {
                    "Unassigned"
                }
                val smoothnessScores = vehicleStints.mapNotNull { it.smoothnessScore }
                VehicleStats(
                    name = name,
                    driveCount = vehicleStints.size,
                    totalDistanceMeters = vehicleStints.sumOf { it.distanceMeters },
                    avgSmoothness = if (smoothnessScores.isNotEmpty()) {
                        smoothnessScores.average().toInt()
                    } else null,
                )
            }
            .sortedByDescending { it.totalDistanceMeters }

        ProfileState(
            currentStreak = currentStreak,
            activeDaysThisMonth = activeDaysThisMonth,
            dailyDistances = dailyDistances,
            totalDrives = totalDrives,
            totalDistanceMeters = totalDistance,
            avgDriveLengthMeters = avgDriveLength,
            longestDriveMeters = longestDrive,
            vehicleCount = vehicleCount,
            stintCount = stints.size,
            smoothnessTrend = smoothnessTrend,
            brakingTrend = brakingTrend,
            corneringGTrend = corneringGTrend,
            latestSmoothness = latestSmoothness,
            latestBraking = latestBraking,
            latestCorneringG = latestCorneringG,
            yearlyDrives = yearlyTracks.size,
            yearlyDistanceMeters = yearlyTracks.sumOf { it.distanceMeters },
            vehicleComparison = vehicleComparison,
            driverProfile = driverProfile,
        )
    }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ProfileState())
}

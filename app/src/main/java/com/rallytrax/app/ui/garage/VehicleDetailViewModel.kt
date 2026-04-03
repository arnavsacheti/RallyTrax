package com.rallytrax.app.ui.garage

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rallytrax.app.data.fuel.MpgCalculator
import com.rallytrax.app.data.local.dao.FuelLogDao
import com.rallytrax.app.data.local.dao.MaintenanceDao
import com.rallytrax.app.data.local.dao.SegmentDao
import com.rallytrax.app.data.local.dao.VehiclePartDao
import com.rallytrax.app.data.local.entity.SegmentEntity
import com.rallytrax.app.data.local.entity.FuelLogEntity
import com.rallytrax.app.data.local.entity.VehiclePartEntity
import com.rallytrax.app.data.local.entity.MaintenanceRecordEntity
import com.rallytrax.app.data.local.entity.MaintenanceScheduleEntity
import com.rallytrax.app.data.local.entity.TrackEntity
import com.rallytrax.app.data.local.entity.VehicleEntity
import com.rallytrax.app.data.preferences.UserPreferencesData
import com.rallytrax.app.data.preferences.UserPreferencesRepository
import com.rallytrax.app.data.repository.VehicleRepository
import com.rallytrax.app.data.sync.SyncManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PerformanceByCategory(
    val category: String,
    val trackCount: Int,
    val avgSpeedMps: Double,
    val topSpeedMps: Double,
    val totalDistanceM: Double,
)

data class VehicleSegmentInfo(
    val segment: SegmentEntity,
    val runCount: Int,
    val bestTimeMs: Long?,
    val avgSpeedMps: Double,
)

data class VehicleAnalytics(
    val totalDistanceM: Double = 0.0,
    val totalTimeMs: Long = 0L,
    val avgSpeedMps: Double = 0.0,
    val topSpeedMps: Double = 0.0,
    val trackCount: Int = 0,
    val routeTypeBreakdown: Map<String, Int> = emptyMap(),
    val surfaceBreakdown: Map<String, Int> = emptyMap(),
    val curvinessDistribution: Map<String, Int> = emptyMap(), // Casual/Moderate/Spirited/Expert
    val monthlyDistances: Map<String, Double> = emptyMap(), // "2026-01" -> meters
    val favoriteSegments: List<VehicleSegmentInfo> = emptyList(),
    val performanceByDifficulty: List<PerformanceByCategory> = emptyList(),
    val performanceBySurface: List<PerformanceByCategory> = emptyList(),
)

data class VehicleDetailUiState(
    val vehicle: VehicleEntity? = null,
    val tracks: List<TrackEntity> = emptyList(),
    val totalDistanceM: Double = 0.0,
    val lifetimeMpg: Double? = null,
    val costPerMile: Double? = null,
    val analytics: VehicleAnalytics = VehicleAnalytics(),
    val isLoading: Boolean = true,
)

@HiltViewModel
class VehicleDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val vehicleRepository: VehicleRepository,
    private val fuelLogDao: FuelLogDao,
    private val maintenanceDao: MaintenanceDao,
    private val segmentDao: SegmentDao,
    private val vehiclePartDao: VehiclePartDao,
    private val syncManager: SyncManager,
    preferencesRepository: UserPreferencesRepository,
) : ViewModel() {

    private val vehicleId: String = savedStateHandle["vehicleId"]
        ?: throw IllegalArgumentException("vehicleId required")

    val preferences: StateFlow<UserPreferencesData> = preferencesRepository.preferences
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UserPreferencesData())

    private val _uiState = MutableStateFlow(VehicleDetailUiState())
    val uiState: StateFlow<VehicleDetailUiState> = _uiState.asStateFlow()

    val tracks: StateFlow<List<TrackEntity>> = vehicleRepository.getTracksForVehicle(vehicleId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val fuelLogs: StateFlow<List<FuelLogEntity>> = fuelLogDao.getLogsForVehicle(vehicleId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val maintenanceRecords: StateFlow<List<MaintenanceRecordEntity>> =
        maintenanceDao.getRecordsForVehicle(vehicleId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val maintenanceSchedules: StateFlow<List<MaintenanceScheduleEntity>> =
        maintenanceDao.getSchedulesForVehicle(vehicleId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val parts: StateFlow<List<VehiclePartEntity>> =
        vehiclePartDao.getActivePartsForVehicle(vehicleId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        loadVehicle()
    }

    private fun loadVehicle() {
        viewModelScope.launch {
            val vehicle = vehicleRepository.getVehicleById(vehicleId)
            val totalDistance = vehicleRepository.getTotalDistanceForVehicle(vehicleId)
            val logs = fuelLogDao.getLogsForVehicleOnce(vehicleId)
            val lifetimeMpg = MpgCalculator.lifetimeAverage(logs)
            val costPerMile = MpgCalculator.costPerMile(logs)
            val analytics = computeAnalytics()

            _uiState.value = VehicleDetailUiState(
                vehicle = vehicle,
                totalDistanceM = totalDistance,
                lifetimeMpg = lifetimeMpg,
                costPerMile = costPerMile,
                analytics = analytics,
                isLoading = false,
            )
        }
    }

    private suspend fun computeAnalytics(): VehicleAnalytics {
        val tracksList = tracks.value
        if (tracksList.isEmpty()) return VehicleAnalytics()

        val totalDistance = tracksList.sumOf { it.distanceMeters }
        val totalTime = tracksList.sumOf { it.durationMs }
        val avgSpeed = if (tracksList.isNotEmpty()) tracksList.map { it.avgSpeedMps }.average() else 0.0
        val topSpeed = tracksList.maxOfOrNull { it.maxSpeedMps } ?: 0.0

        // Route type breakdown
        val routeTypes = tracksList.mapNotNull { it.routeType }
            .groupingBy { it }.eachCount()

        // Surface breakdown
        val surfaces = tracksList.mapNotNull { it.primarySurface }
            .groupingBy { it }.eachCount()

        // Curviness → difficulty distribution
        val difficulties = tracksList.mapNotNull { it.difficultyRating }
            .groupingBy { it }.eachCount()

        // Monthly distances (last 12 months)
        val monthlyDistances = tracksList
            .groupBy {
                val date = java.time.Instant.ofEpochMilli(it.recordedAt)
                    .atZone(java.time.ZoneId.systemDefault())
                    .toLocalDate()
                "${date.year}-${"%02d".format(date.monthValue)}"
            }
            .mapValues { (_, tracks) -> tracks.sumOf { it.distanceMeters } }

        // Favorite segments for this vehicle
        val favoriteSegments = segmentDao.getFavoriteSegmentsForVehicle(vehicleId).map { segment ->
            val runs = segmentDao.getRunsForSegmentAndVehicle(segment.id, vehicleId)
            VehicleSegmentInfo(
                segment = segment,
                runCount = runs.size,
                bestTimeMs = runs.filter { it.durationMs > 0 }.minOfOrNull { it.durationMs },
                avgSpeedMps = if (runs.isNotEmpty()) runs.map { it.avgSpeedMps }.average() else 0.0,
            )
        }

        // Performance breakdowns by difficulty and surface
        val performanceByDifficulty = groupTracksByCategory(tracksList) { it.difficultyRating }
        val performanceBySurface = groupTracksByCategory(tracksList) { it.primarySurface }

        return VehicleAnalytics(
            totalDistanceM = totalDistance,
            totalTimeMs = totalTime,
            avgSpeedMps = avgSpeed,
            topSpeedMps = topSpeed,
            trackCount = tracksList.size,
            routeTypeBreakdown = routeTypes,
            surfaceBreakdown = surfaces,
            curvinessDistribution = difficulties,
            monthlyDistances = monthlyDistances,
            favoriteSegments = favoriteSegments,
            performanceByDifficulty = performanceByDifficulty,
            performanceBySurface = performanceBySurface,
        )
    }

    private fun groupTracksByCategory(
        tracksList: List<TrackEntity>,
        selector: (TrackEntity) -> String?,
    ): List<PerformanceByCategory> =
        tracksList
            .mapNotNull { track -> selector(track)?.let { it to track } }
            .groupBy({ it.first }, { it.second })
            .map { (category, group) ->
                PerformanceByCategory(
                    category = category,
                    trackCount = group.size,
                    avgSpeedMps = group.map { it.avgSpeedMps }.average(),
                    topSpeedMps = group.maxOf { it.maxSpeedMps },
                    totalDistanceM = group.sumOf { it.distanceMeters },
                )
            }
            .sortedByDescending { it.trackCount }

    fun toggleActive() {
        viewModelScope.launch {
            val current = vehicleRepository.getActiveVehicle()
            if (current?.id == vehicleId) {
                // Already active — deactivate it
                vehicleRepository.updateVehicle(current.copy(isActive = false))
            } else {
                vehicleRepository.setActiveVehicle(vehicleId)
            }
            loadVehicle()
        }
    }

    fun updateVehicle(updated: VehicleEntity) {
        viewModelScope.launch {
            vehicleRepository.updateVehicle(updated)
            loadVehicle()
        }
    }

    fun addMaintenanceRecord(record: MaintenanceRecordEntity) {
        viewModelScope.launch {
            maintenanceDao.insertRecord(record)
            syncManager.scheduleDebouncedSync()
        }
    }

    fun addMaintenanceSchedule(schedule: MaintenanceScheduleEntity) {
        viewModelScope.launch {
            maintenanceDao.insertSchedule(schedule)
            syncManager.scheduleDebouncedSync()
        }
    }

    fun completeSchedule(schedule: MaintenanceScheduleEntity) {
        viewModelScope.launch {
            // Advance the next-due date/odometer
            val now = System.currentTimeMillis()
            val nextDueDate = if (schedule.intervalMonths != null) {
                now + schedule.intervalMonths.toLong() * 30L * 24 * 60 * 60 * 1000
            } else null
            val nextDueOdometer = if (schedule.intervalKm != null) {
                val vehicle = vehicleRepository.getVehicleById(vehicleId)
                val currentOdometer = vehicle?.odometerKm ?: 0.0
                currentOdometer + schedule.intervalKm
            } else null

            maintenanceDao.updateSchedule(
                schedule.copy(
                    lastServiceDate = now,
                    lastServiceOdometerKm = vehicleRepository.getVehicleById(vehicleId)?.odometerKm,
                    nextDueDate = nextDueDate,
                    nextDueOdometerKm = nextDueOdometer,
                    status = MaintenanceScheduleEntity.STATUS_UPCOMING,
                )
            )

            // Also log it as a maintenance record
            maintenanceDao.insertRecord(
                MaintenanceRecordEntity(
                    vehicleId = vehicleId,
                    category = "Scheduled",
                    serviceType = schedule.serviceType,
                    odometerKm = vehicleRepository.getVehicleById(vehicleId)?.odometerKm,
                )
            )

            syncManager.scheduleDebouncedSync()
        }
    }

    fun addPart(part: VehiclePartEntity) {
        viewModelScope.launch {
            vehiclePartDao.insertPart(part)
            syncManager.scheduleDebouncedSync()
        }
    }

    fun retirePart(partId: String) {
        viewModelScope.launch {
            vehiclePartDao.retirePart(partId)
            syncManager.scheduleDebouncedSync()
        }
    }

    fun updatePart(part: VehiclePartEntity) {
        viewModelScope.launch {
            vehiclePartDao.updatePart(part.copy(updatedAt = System.currentTimeMillis()))
            syncManager.scheduleDebouncedSync()
        }
    }

    fun refresh() {
        loadVehicle()
    }
}

package com.rallytrax.app.ui.garage

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rallytrax.app.data.fuel.MpgCalculator
import com.rallytrax.app.data.local.dao.FuelLogDao
import com.rallytrax.app.data.local.dao.MaintenanceDao
import com.rallytrax.app.data.local.entity.FuelLogEntity
import com.rallytrax.app.data.local.entity.MaintenanceRecordEntity
import com.rallytrax.app.data.local.entity.MaintenanceScheduleEntity
import com.rallytrax.app.data.local.entity.TrackEntity
import com.rallytrax.app.data.local.entity.VehicleEntity
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

data class VehicleDetailUiState(
    val vehicle: VehicleEntity? = null,
    val tracks: List<TrackEntity> = emptyList(),
    val totalDistanceM: Double = 0.0,
    val lifetimeMpg: Double? = null,
    val costPerMile: Double? = null,
    val isLoading: Boolean = true,
)

@HiltViewModel
class VehicleDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val vehicleRepository: VehicleRepository,
    private val fuelLogDao: FuelLogDao,
    private val maintenanceDao: MaintenanceDao,
    private val syncManager: SyncManager,
) : ViewModel() {

    private val vehicleId: String = savedStateHandle["vehicleId"]
        ?: throw IllegalArgumentException("vehicleId required")

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

            _uiState.value = VehicleDetailUiState(
                vehicle = vehicle,
                totalDistanceM = totalDistance,
                lifetimeMpg = lifetimeMpg,
                costPerMile = costPerMile,
                isLoading = false,
            )
        }
    }

    fun setActive() {
        viewModelScope.launch {
            vehicleRepository.setActiveVehicle(vehicleId)
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

    fun refresh() {
        loadVehicle()
    }
}

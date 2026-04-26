package com.rallytrax.app.ui.garage

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rallytrax.app.data.local.dao.MaintenanceDao
import com.rallytrax.app.data.local.entity.Ownership
import com.rallytrax.app.data.local.entity.VehicleEntity
import com.rallytrax.app.data.preferences.UserPreferencesData
import com.rallytrax.app.data.preferences.UserPreferencesRepository
import com.rallytrax.app.data.repository.VehicleRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class VehicleWarning {
    MISSING_VIN,
    NO_TRACKS,
    INCOMPLETE_SPECS,
    MAINTENANCE_DUE,
}

data class VehicleWithStats(
    val vehicle: VehicleEntity,
    val trackCount: Int = 0,
    val totalDistanceM: Double = 0.0,
    val warnings: List<VehicleWarning> = emptyList(),
)

data class GarageUiState(
    /** Vehicles the user owns — the canonical Garage list. */
    val vehicles: List<VehicleWithStats> = emptyList(),
    /** Borrowed friend's-car / rental entries. Rendered in their own
     *  collapsible section so they don't clutter the Garage but the user
     *  can still find and edit them. */
    val loaners: List<VehicleWithStats> = emptyList(),
    val isLoading: Boolean = true,
)

@HiltViewModel
class GarageViewModel @Inject constructor(
    private val vehicleRepository: VehicleRepository,
    private val maintenanceDao: MaintenanceDao,
    preferencesRepository: UserPreferencesRepository,
) : ViewModel() {

    val preferences: StateFlow<UserPreferencesData> = preferencesRepository.preferences
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UserPreferencesData())

    val uiState: StateFlow<GarageUiState> = vehicleRepository.getAllVehicles()
        .map { vehicles ->
            val withStats = vehicles.map { vehicle ->
                val trackCount = vehicleRepository.getTrackCountForVehicle(vehicle.id)
                val totalDistance = vehicleRepository.getTotalDistanceForVehicle(vehicle.id)
                val warnings = computeWarnings(vehicle, trackCount)
                VehicleWithStats(vehicle, trackCount, totalDistance, warnings)
            }
            val (loaners, owned) = withStats.partition {
                Ownership.fromStorage(it.vehicle.ownership) != Ownership.OWNED
            }
            GarageUiState(vehicles = owned, loaners = loaners, isLoading = false)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), GarageUiState())

    // Pending archive with undo support
    private val _pendingArchive = MutableStateFlow<VehicleEntity?>(null)
    val pendingArchive: StateFlow<VehicleEntity?> = _pendingArchive.asStateFlow()

    fun requestArchiveVehicle(vehicle: VehicleEntity) {
        viewModelScope.launch {
            _pendingArchive.value = vehicle
            vehicleRepository.archiveVehicle(vehicle.id)
        }
    }

    fun confirmArchive() {
        _pendingArchive.value = null
    }

    fun cancelArchive() {
        val vehicle = _pendingArchive.value ?: return
        _pendingArchive.value = null
        viewModelScope.launch {
            vehicleRepository.unarchiveVehicle(vehicle.id)
        }
    }

    private suspend fun computeWarnings(vehicle: VehicleEntity, trackCount: Int): List<VehicleWarning> {
        // Spec/VIN/maintenance prompts are noise on a borrowed or rental car —
        // the user isn't filling in EPA MPG for a Hertz Corolla. Only surface
        // "no tracks" so a stale loaner can still be cleaned up.
        val isOwned = Ownership.fromStorage(vehicle.ownership) == Ownership.OWNED
        return buildList {
            if (isOwned && vehicle.vin.isNullOrBlank()) add(VehicleWarning.MISSING_VIN)
            if (trackCount == 0) add(VehicleWarning.NO_TRACKS)
            if (isOwned &&
                vehicle.horsePower == null &&
                vehicle.engineDisplacementL == null &&
                vehicle.curbWeightKg == null
            ) {
                add(VehicleWarning.INCOMPLETE_SPECS)
            }
            if (isOwned) {
                val dueSchedules = maintenanceDao.getDueSchedulesForVehicle(vehicle.id)
                if (dueSchedules.isNotEmpty()) add(VehicleWarning.MAINTENANCE_DUE)
            }
        }
    }

    fun toggleActiveVehicle(vehicleId: String) {
        viewModelScope.launch {
            val current = vehicleRepository.getActiveVehicle()
            if (current?.id == vehicleId) {
                // Already active — deactivate it
                vehicleRepository.updateVehicle(current.copy(isActive = false))
            } else {
                vehicleRepository.setActiveVehicle(vehicleId)
            }
        }
    }
}

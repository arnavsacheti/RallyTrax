package com.rallytrax.app.ui.garage

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rallytrax.app.data.local.entity.VehicleEntity
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

data class VehicleWithStats(
    val vehicle: VehicleEntity,
    val trackCount: Int = 0,
    val totalDistanceM: Double = 0.0,
)

data class GarageUiState(
    val vehicles: List<VehicleWithStats> = emptyList(),
    val isLoading: Boolean = true,
)

@HiltViewModel
class GarageViewModel @Inject constructor(
    private val vehicleRepository: VehicleRepository,
) : ViewModel() {

    val uiState: StateFlow<GarageUiState> = vehicleRepository.getAllVehicles()
        .map { vehicles ->
            val withStats = vehicles.map { vehicle ->
                val trackCount = vehicleRepository.getTrackCountForVehicle(vehicle.id)
                val totalDistance = vehicleRepository.getTotalDistanceForVehicle(vehicle.id)
                VehicleWithStats(vehicle, trackCount, totalDistance)
            }
            GarageUiState(vehicles = withStats, isLoading = false)
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

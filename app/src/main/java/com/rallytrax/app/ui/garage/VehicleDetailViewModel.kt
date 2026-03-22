package com.rallytrax.app.ui.garage

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rallytrax.app.data.local.entity.TrackEntity
import com.rallytrax.app.data.local.entity.VehicleEntity
import com.rallytrax.app.data.repository.VehicleRepository
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
    val isLoading: Boolean = true,
)

@HiltViewModel
class VehicleDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val vehicleRepository: VehicleRepository,
) : ViewModel() {

    private val vehicleId: String = savedStateHandle["vehicleId"]
        ?: throw IllegalArgumentException("vehicleId required")

    private val _uiState = MutableStateFlow(VehicleDetailUiState())
    val uiState: StateFlow<VehicleDetailUiState> = _uiState.asStateFlow()

    val tracks: StateFlow<List<TrackEntity>> = vehicleRepository.getTracksForVehicle(vehicleId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        loadVehicle()
    }

    private fun loadVehicle() {
        viewModelScope.launch {
            val vehicle = vehicleRepository.getVehicleById(vehicleId)
            val totalDistance = vehicleRepository.getTotalDistanceForVehicle(vehicleId)
            _uiState.value = VehicleDetailUiState(
                vehicle = vehicle,
                totalDistanceM = totalDistance,
                isLoading = false,
            )
        }
    }

    fun setActive() {
        viewModelScope.launch {
            vehicleRepository.setActiveVehicle(vehicleId)
            loadVehicle() // Refresh to update isActive state
        }
    }

    fun updateVehicle(updated: VehicleEntity) {
        viewModelScope.launch {
            vehicleRepository.updateVehicle(updated)
            loadVehicle()
        }
    }
}

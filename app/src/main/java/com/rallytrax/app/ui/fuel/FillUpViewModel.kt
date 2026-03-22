package com.rallytrax.app.ui.fuel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rallytrax.app.data.fuel.MpgCalculator
import com.rallytrax.app.data.local.dao.FuelLogDao
import com.rallytrax.app.data.local.dao.VehicleDao
import com.rallytrax.app.data.local.entity.FuelLogEntity
import com.rallytrax.app.data.local.entity.VehicleEntity
import com.rallytrax.app.data.sync.SyncManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class FillUpUiState(
    val vehicleId: String? = null,
    val vehicleName: String? = null,
    val odometerInput: String = "",
    val volumeInput: String = "",
    val isFullTank: Boolean = true,
    val pricePerUnitInput: String = "",
    val totalCostInput: String = "",
    val fuelGrade: String? = null,
    val stationName: String = "",
    val stationLat: Double? = null,
    val stationLon: Double? = null,
    val notes: String = "",
    val trackId: String? = null,
    // Validation
    val odometerError: String? = null,
    val volumeError: String? = null,
    // Save state
    val isSaving: Boolean = false,
    val saved: Boolean = false,
    val saveError: String? = null,
)

@HiltViewModel
class FillUpViewModel @Inject constructor(
    private val fuelLogDao: FuelLogDao,
    private val vehicleDao: VehicleDao,
    private val syncManager: SyncManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(FillUpUiState())
    val uiState: StateFlow<FillUpUiState> = _uiState.asStateFlow()

    val vehicles: StateFlow<List<VehicleEntity>> = vehicleDao.getAllVehicles()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        // Pre-fill with active vehicle
        viewModelScope.launch {
            val active = vehicleDao.getActiveVehicle()
            if (active != null) {
                _uiState.update {
                    it.copy(vehicleId = active.id, vehicleName = active.name)
                }
            }
        }
    }

    fun prefill(stationName: String?, stationLat: Double?, stationLon: Double?, trackId: String?) {
        _uiState.update {
            it.copy(
                stationName = stationName ?: "",
                stationLat = stationLat,
                stationLon = stationLon,
                trackId = trackId,
            )
        }
    }

    fun selectVehicle(vehicle: VehicleEntity) {
        _uiState.update { it.copy(vehicleId = vehicle.id, vehicleName = vehicle.name) }
    }

    fun updateOdometer(value: String) {
        _uiState.update { it.copy(odometerInput = value, odometerError = null) }
    }

    fun updateVolume(value: String) {
        _uiState.update { it.copy(volumeInput = value, volumeError = null) }
    }

    fun updateFullTank(isFullTank: Boolean) {
        _uiState.update { it.copy(isFullTank = isFullTank) }
    }

    fun updatePricePerUnit(value: String) {
        _uiState.update { it.copy(pricePerUnitInput = value) }
        // Auto-compute total cost
        val price = value.toDoubleOrNull()
        val volume = _uiState.value.volumeInput.toDoubleOrNull()
        if (price != null && volume != null) {
            _uiState.update { it.copy(totalCostInput = "%.2f".format(price * volume)) }
        }
    }

    fun updateTotalCost(value: String) {
        _uiState.update { it.copy(totalCostInput = value) }
    }

    fun updateFuelGrade(grade: String?) {
        _uiState.update { it.copy(fuelGrade = grade) }
    }

    fun updateStationName(name: String) {
        _uiState.update { it.copy(stationName = name) }
    }

    fun updateNotes(notes: String) {
        _uiState.update { it.copy(notes = notes) }
    }

    fun save() {
        val state = _uiState.value
        val vehicleId = state.vehicleId ?: return

        // Validate
        val odometer = state.odometerInput.toDoubleOrNull()
        if (odometer == null) {
            _uiState.update { it.copy(odometerError = "Enter a valid odometer reading") }
            return
        }

        val volume = state.volumeInput.toDoubleOrNull()
        if (volume == null || volume <= 0) {
            _uiState.update { it.copy(volumeError = "Enter a valid fuel volume") }
            return
        }

        _uiState.update { it.copy(isSaving = true) }

        viewModelScope.launch {
            try {
                // Validate odometer > previous
                val latestLog = fuelLogDao.getLatestLog(vehicleId)
                if (latestLog != null && odometer <= latestLog.odometerKm) {
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            odometerError = "Must be greater than previous reading (${latestLog.odometerKm.toInt()} km)",
                        )
                    }
                    return@launch
                }

                // Create fuel log
                val pricePerUnit = state.pricePerUnitInput.toDoubleOrNull()
                val totalCost = state.totalCostInput.toDoubleOrNull()
                val previousLogs = fuelLogDao.getLogsForVehicleOnce(vehicleId)

                val newLog = FuelLogEntity(
                    vehicleId = vehicleId,
                    trackId = state.trackId,
                    odometerKm = odometer,
                    volumeL = volume,
                    isFullTank = state.isFullTank,
                    pricePerUnit = pricePerUnit,
                    totalCost = totalCost,
                    fuelGrade = state.fuelGrade,
                    stationName = state.stationName.takeIf { it.isNotBlank() },
                    stationLat = state.stationLat,
                    stationLon = state.stationLon,
                    notes = state.notes.takeIf { it.isNotBlank() },
                )

                // Calculate MPG
                val mpg = MpgCalculator.computeMpgForNewEntry(newLog, previousLogs)
                val logWithMpg = newLog.copy(computedMpg = mpg)

                fuelLogDao.insertLog(logWithMpg)

                // Update vehicle odometer
                val vehicle = vehicleDao.getVehicleById(vehicleId)
                if (vehicle != null && odometer > vehicle.odometerKm) {
                    vehicleDao.updateVehicle(
                        vehicle.copy(
                            odometerKm = odometer,
                            updatedAt = System.currentTimeMillis(),
                        )
                    )
                }

                syncManager.scheduleDebouncedSync()
                _uiState.update { it.copy(isSaving = false, saved = true) }
            } catch (e: Exception) {
                Log.e("FillUpViewModel", "Save failed", e)
                _uiState.update { it.copy(isSaving = false, saveError = e.message) }
            }
        }
    }
}

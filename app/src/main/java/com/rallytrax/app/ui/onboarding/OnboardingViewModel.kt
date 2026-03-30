package com.rallytrax.app.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rallytrax.app.data.local.entity.VehicleEntity
import com.rallytrax.app.data.repository.VehicleRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class OnboardingVehicleState(
    val make: String = "",
    val model: String = "",
    val year: String = "",
    val isSaving: Boolean = false,
    val saved: Boolean = false,
)

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val vehicleRepository: VehicleRepository,
) : ViewModel() {

    private val _vehicleState = MutableStateFlow(OnboardingVehicleState())
    val vehicleState: StateFlow<OnboardingVehicleState> = _vehicleState.asStateFlow()

    fun updateMake(make: String) {
        _vehicleState.update { it.copy(make = make) }
    }

    fun updateModel(model: String) {
        _vehicleState.update { it.copy(model = model) }
    }

    fun updateYear(year: String) {
        _vehicleState.update { it.copy(year = year.filter { c -> c.isDigit() }.take(4)) }
    }

    fun canSave(): Boolean {
        val state = _vehicleState.value
        return state.make.isNotBlank() &&
            state.model.isNotBlank() &&
            state.year.length == 4 &&
            state.year.toIntOrNull() != null &&
            !state.isSaving
    }

    fun saveVehicle() {
        val state = _vehicleState.value
        val yearInt = state.year.toIntOrNull() ?: return
        if (!canSave()) return

        _vehicleState.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            try {
                val name = "${state.year} ${state.make} ${state.model}"
                val vehicle = VehicleEntity(
                    name = name,
                    year = yearInt,
                    make = state.make.trim(),
                    model = state.model.trim(),
                    isActive = true,
                )
                vehicleRepository.addVehicle(vehicle)
                _vehicleState.update { it.copy(isSaving = false, saved = true) }
            } catch (_: Exception) {
                _vehicleState.update { it.copy(isSaving = false) }
            }
        }
    }
}

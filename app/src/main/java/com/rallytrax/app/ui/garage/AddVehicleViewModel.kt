package com.rallytrax.app.ui.garage

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rallytrax.app.data.api.FuelEconomyTrim
import com.rallytrax.app.data.api.NhtsaMake
import com.rallytrax.app.data.api.NhtsaModel
import com.rallytrax.app.data.local.entity.VehicleEntity
import com.rallytrax.app.data.repository.VehicleRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

data class AddVehicleUiState(
    // Year/Make/Model picker
    val selectedYear: Int? = null,
    val makes: List<NhtsaMake> = emptyList(),
    val isLoadingMakes: Boolean = false,
    val selectedMake: NhtsaMake? = null,
    val models: List<NhtsaModel> = emptyList(),
    val isLoadingModels: Boolean = false,
    val selectedModel: NhtsaModel? = null,
    val trims: List<FuelEconomyTrim> = emptyList(),
    val isLoadingTrims: Boolean = false,
    val selectedTrim: FuelEconomyTrim? = null,

    // VIN entry
    val vinInput: String = "",
    val isDecodingVin: Boolean = false,
    val vinError: String? = null,

    // Vehicle type
    val vehicleType: String = "CAR",

    // Spec fields (populated from API or VIN decode)
    val name: String = "",
    val trim: String? = null,
    val engineDisplacementL: Double? = null,
    val cylinders: Int? = null,
    val horsePower: Int? = null,
    val drivetrain: String? = null,
    val transmissionType: String? = null,
    val transmissionSpeeds: Int? = null,
    val curbWeightKg: Double? = null,
    val fuelType: String = "Gasoline",
    val epaCityMpg: Double? = null,
    val epaHwyMpg: Double? = null,
    val epaCombinedMpg: Double? = null,

    // Save state
    val isSaving: Boolean = false,
    val saveError: String? = null,
    val saved: Boolean = false,
)

@HiltViewModel
class AddVehicleViewModel @Inject constructor(
    private val vehicleRepository: VehicleRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AddVehicleUiState())
    val uiState: StateFlow<AddVehicleUiState> = _uiState.asStateFlow()

    val currentYear = Calendar.getInstance().get(Calendar.YEAR)

    fun selectYear(year: Int) {
        _uiState.update {
            it.copy(
                selectedYear = year,
                selectedMake = null,
                makes = emptyList(),
                selectedModel = null,
                models = emptyList(),
                trims = emptyList(),
                selectedTrim = null,
                isLoadingMakes = true,
            )
        }
        viewModelScope.launch {
            try {
                val makes = vehicleRepository.fetchMakesByYear(year)
                _uiState.update { it.copy(makes = makes, isLoadingMakes = false) }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to fetch makes", e)
                _uiState.update { it.copy(isLoadingMakes = false) }
            }
        }
    }

    fun selectMake(make: NhtsaMake) {
        val year = _uiState.value.selectedYear ?: return
        _uiState.update {
            it.copy(
                selectedMake = make,
                selectedModel = null,
                models = emptyList(),
                trims = emptyList(),
                selectedTrim = null,
                isLoadingModels = true,
            )
        }
        viewModelScope.launch {
            try {
                val models = vehicleRepository.fetchModelsByMakeAndYear(make.makeName, year)
                _uiState.update { it.copy(models = models, isLoadingModels = false) }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to fetch models", e)
                _uiState.update { it.copy(isLoadingModels = false) }
            }
        }
    }

    fun selectModel(model: NhtsaModel) {
        val state = _uiState.value
        val year = state.selectedYear ?: return
        val make = state.selectedMake ?: return
        val vehicleName = "$year ${make.makeName} ${model.modelName}"
        _uiState.update {
            it.copy(
                selectedModel = model,
                name = vehicleName,
                trims = emptyList(),
                selectedTrim = null,
                isLoadingTrims = true,
            )
        }
        viewModelScope.launch {
            try {
                val trims = vehicleRepository.fetchEpaTrims(year, make.makeName, model.modelName)
                _uiState.update { it.copy(trims = trims, isLoadingTrims = false) }
                // If only one trim, auto-select it
                if (trims.size == 1) {
                    selectTrim(trims.first())
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to fetch trims", e)
                _uiState.update { it.copy(isLoadingTrims = false) }
            }
        }
    }

    fun selectTrim(trim: FuelEconomyTrim) {
        _uiState.update { it.copy(selectedTrim = trim) }
        viewModelScope.launch {
            try {
                val data = vehicleRepository.fetchEpaVehicleData(trim.vehicleId)
                if (data != null) {
                    _uiState.update {
                        it.copy(
                            epaCityMpg = data.cityMpg,
                            epaHwyMpg = data.hwyMpg,
                            epaCombinedMpg = data.combinedMpg,
                            cylinders = data.cylinders ?: it.cylinders,
                            engineDisplacementL = data.displacement ?: it.engineDisplacementL,
                            drivetrain = data.drive ?: it.drivetrain,
                            transmissionType = data.transmission ?: it.transmissionType,
                            fuelType = data.fuelType ?: it.fuelType,
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to fetch EPA data", e)
            }
        }
    }

    fun updateVehicleType(type: String) {
        _uiState.update { it.copy(vehicleType = type) }
    }

    fun updateName(name: String) {
        _uiState.update { it.copy(name = name) }
    }

    fun updateFuelType(fuelType: String) {
        _uiState.update { it.copy(fuelType = fuelType) }
    }

    fun updateVinInput(vin: String) {
        _uiState.update { it.copy(vinInput = vin.uppercase().filter { c -> c.isLetterOrDigit() }.take(17)) }
    }

    fun decodeVin() {
        val vin = _uiState.value.vinInput
        if (vin.length != 17) {
            _uiState.update { it.copy(vinError = "VIN must be exactly 17 characters") }
            return
        }
        _uiState.update { it.copy(isDecodingVin = true, vinError = null) }
        viewModelScope.launch {
            try {
                val result = vehicleRepository.decodeVin(vin)
                if (result.errorCode != null && result.errorCode != "0") {
                    _uiState.update { it.copy(isDecodingVin = false, vinError = result.errorText) }
                    return@launch
                }
                val year = result.year
                val make = result.make
                val model = result.model
                val vehicleName = listOfNotNull(year?.toString(), make, model).joinToString(" ")

                _uiState.update {
                    it.copy(
                        isDecodingVin = false,
                        selectedYear = year,
                        selectedMake = make?.let { m -> NhtsaMake(0, m) },
                        selectedModel = model?.let { m -> NhtsaModel(0, m) },
                        name = vehicleName.ifBlank { it.name },
                        trim = result.trim,
                        engineDisplacementL = result.engineDisplacementL,
                        cylinders = result.cylinders,
                        horsePower = result.horsePower,
                        drivetrain = result.drivetrain,
                        transmissionType = result.transmissionType,
                        transmissionSpeeds = result.transmissionSpeeds,
                        curbWeightKg = result.curbWeightKg,
                        fuelType = result.fuelType ?: "Gasoline",
                    )
                }

                // Also try to fetch EPA data
                if (year != null && make != null && model != null) {
                    val trims = vehicleRepository.fetchEpaTrims(year, make, model)
                    _uiState.update { it.copy(trims = trims) }
                    if (trims.size == 1) {
                        selectTrim(trims.first())
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "VIN decode failed", e)
                _uiState.update { it.copy(isDecodingVin = false, vinError = e.message) }
            }
        }
    }

    fun onVinScanned(vin: String) {
        _uiState.update { it.copy(vinInput = vin) }
        decodeVin()
    }

    fun saveVehicle() {
        val state = _uiState.value
        val year = state.selectedYear ?: return
        val make = state.selectedMake?.makeName ?: return
        val model = state.selectedModel?.modelName ?: return
        val name = state.name.ifBlank { "$year $make $model" }

        _uiState.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            try {
                val vehicle = VehicleEntity(
                    name = name,
                    year = year,
                    make = make,
                    model = model,
                    vehicleType = state.vehicleType,
                    trim = state.trim ?: state.selectedTrim?.displayName,
                    vin = state.vinInput.takeIf { it.length == 17 },
                    engineDisplacementL = state.engineDisplacementL,
                    cylinders = state.cylinders,
                    horsePower = state.horsePower,
                    drivetrain = state.drivetrain,
                    transmissionType = state.transmissionType,
                    transmissionSpeeds = state.transmissionSpeeds,
                    curbWeightKg = state.curbWeightKg,
                    fuelType = state.fuelType,
                    epaCityMpg = state.epaCityMpg,
                    epaHwyMpg = state.epaHwyMpg,
                    epaCombinedMpg = state.epaCombinedMpg,
                )
                vehicleRepository.addVehicle(vehicle)
                _uiState.update { it.copy(isSaving = false, saved = true) }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save vehicle", e)
                _uiState.update { it.copy(isSaving = false, saveError = e.message) }
            }
        }
    }

    fun canSave(): Boolean {
        val state = _uiState.value
        return state.selectedYear != null &&
            state.selectedMake != null &&
            state.selectedModel != null &&
            !state.isSaving
    }

    fun reset() {
        _uiState.value = AddVehicleUiState()
    }

    companion object {
        private const val TAG = "AddVehicleViewModel"
    }
}

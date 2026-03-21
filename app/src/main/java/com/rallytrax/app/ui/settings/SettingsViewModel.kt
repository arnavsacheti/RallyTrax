package com.rallytrax.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rallytrax.app.data.local.dao.PaceNoteDao
import com.rallytrax.app.data.local.dao.TrackDao
import com.rallytrax.app.data.local.dao.TrackPointDao
import com.rallytrax.app.data.preferences.GpsAccuracy
import com.rallytrax.app.data.preferences.MapProviderPreference
import com.rallytrax.app.data.preferences.ThemeMode
import com.rallytrax.app.data.preferences.UnitSystem
import com.rallytrax.app.data.preferences.UserPreferencesData
import com.rallytrax.app.data.preferences.UserPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val showDeleteConfirmation: Boolean = false,
    val showClearCacheConfirmation: Boolean = false,
    val trackCount: Int = 0,
    val isDeleting: Boolean = false,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferencesRepository: UserPreferencesRepository,
    private val trackDao: TrackDao,
    private val trackPointDao: TrackPointDao,
    private val paceNoteDao: PaceNoteDao,
) : ViewModel() {

    val preferences: StateFlow<UserPreferencesData> = preferencesRepository.preferences
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UserPreferencesData())

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(trackCount = trackDao.getTrackCount())
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { preferencesRepository.setThemeMode(mode) }
    }

    fun setUnitSystem(system: UnitSystem) {
        viewModelScope.launch { preferencesRepository.setUnitSystem(system) }
    }

    fun setGpsAccuracy(accuracy: GpsAccuracy) {
        viewModelScope.launch { preferencesRepository.setGpsAccuracy(accuracy) }
    }

    fun setMapProvider(provider: MapProviderPreference) {
        viewModelScope.launch { preferencesRepository.setMapProvider(provider) }
    }

    fun setTtsRate(rate: Float) {
        viewModelScope.launch { preferencesRepository.setTtsRate(rate) }
    }

    fun setTtsPitch(pitch: Float) {
        viewModelScope.launch { preferencesRepository.setTtsPitch(pitch) }
    }

    fun setTtsEnabled(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setTtsEnabled(enabled) }
    }

    fun setKeepScreenOn(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setKeepScreenOn(enabled) }
    }

    fun showDeleteConfirmation() {
        _uiState.value = _uiState.value.copy(showDeleteConfirmation = true)
    }

    fun dismissDeleteConfirmation() {
        _uiState.value = _uiState.value.copy(showDeleteConfirmation = false)
    }

    fun deleteAllTracks() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isDeleting = true, showDeleteConfirmation = false)
            try {
                // Get all track IDs then delete everything
                trackDao.getAllTracks().collect { tracks ->
                    val ids = tracks.map { it.id }
                    if (ids.isNotEmpty()) {
                        trackDao.deleteTracks(ids)
                    }
                    _uiState.value = _uiState.value.copy(
                        isDeleting = false,
                        trackCount = 0,
                    )
                    return@collect
                }
            } catch (_: Exception) {
                _uiState.value = _uiState.value.copy(isDeleting = false)
            }
        }
    }
}

package com.rallytrax.app.ui.trips

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rallytrax.app.data.local.entity.TripSuggestionEntity
import com.rallytrax.app.data.repository.TripSuggestionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TripSuggestionUiState(
    val suggestions: List<TripSuggestionEntity> = emptyList(),
    val isLoading: Boolean = true,
)

@HiltViewModel
class TripSuggestionViewModel @Inject constructor(
    private val tripSuggestionRepository: TripSuggestionRepository,
) : ViewModel() {

    private val _snackbarMessage = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val snackbarMessage = _snackbarMessage.asSharedFlow()

    val uiState: StateFlow<TripSuggestionUiState> = tripSuggestionRepository
        .getPendingSuggestions()
        .map { suggestions ->
            TripSuggestionUiState(
                suggestions = suggestions,
                isLoading = false,
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TripSuggestionUiState())

    fun acceptSuggestion(suggestionId: String, customName: String? = null) {
        viewModelScope.launch {
            tripSuggestionRepository.acceptSuggestion(suggestionId, customName)
            _snackbarMessage.tryEmit("Trip created!")
        }
    }

    fun dismissSuggestion(suggestionId: String) {
        viewModelScope.launch {
            tripSuggestionRepository.dismissSuggestion(suggestionId)
            _snackbarMessage.tryEmit("Suggestion dismissed")
        }
    }

    /**
     * Trigger on-demand detection (e.g. pull-to-refresh).
     */
    fun runDetection() {
        viewModelScope.launch {
            tripSuggestionRepository.detectAndSuggest()
            tripSuggestionRepository.enrichPendingSuggestionsWithAi()
        }
    }
}

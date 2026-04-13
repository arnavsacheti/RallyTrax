package com.rallytrax.app.ui.trips

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rallytrax.app.data.local.entity.TrackEntity
import com.rallytrax.app.data.local.entity.TripEntity
import com.rallytrax.app.data.repository.TripRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TripDetailUiState(
    val trip: TripEntity? = null,
    val tracks: List<TrackEntity> = emptyList(),
    val totalDistanceMeters: Double = 0.0,
    val totalDurationMs: Long = 0L,
    val trackCount: Int = 0,
)

@HiltViewModel
class TripDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val tripRepository: TripRepository,
) : ViewModel() {

    private val tripId: String = savedStateHandle["tripId"] ?: ""

    private val _snackbarMessage = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val snackbarMessage = _snackbarMessage.asSharedFlow()

    val uiState: StateFlow<TripDetailUiState> = combine(
        tripRepository.getTripById(tripId),
        tripRepository.getTracksForTrip(tripId),
        tripRepository.getTotalDistanceForTrip(tripId),
        tripRepository.getTotalDurationForTrip(tripId),
    ) { trip, tracks, distance, duration ->
        TripDetailUiState(
            trip = trip,
            tracks = tracks,
            totalDistanceMeters = distance ?: 0.0,
            totalDurationMs = duration ?: 0L,
            trackCount = tracks.size,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TripDetailUiState())

    fun updateTrip(name: String, description: String?) {
        val trip = uiState.value.trip ?: return
        viewModelScope.launch {
            tripRepository.updateTrip(trip.copy(name = name.trim(), description = description?.trim()?.ifBlank { null }))
        }
    }

    fun removeTrackFromTrip(trackId: String) {
        viewModelScope.launch {
            tripRepository.assignTrackToTrip(trackId, null)
            _snackbarMessage.tryEmit("Stint removed from trip")
        }
    }
}

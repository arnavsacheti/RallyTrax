package com.rallytrax.app.ui.trips

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rallytrax.app.data.local.entity.TripEntity
import com.rallytrax.app.data.repository.TripRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TripSummary(
    val trip: TripEntity,
    val trackCount: Int = 0,
    val totalDistanceMeters: Double = 0.0,
    val totalDurationMs: Long = 0L,
)

data class TripsUiState(
    val trips: List<TripSummary> = emptyList(),
    val isLoading: Boolean = true,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TripsViewModel @Inject constructor(
    private val tripRepository: TripRepository,
) : ViewModel() {

    private val _pendingDelete = MutableStateFlow<TripEntity?>(null)
    val pendingDelete: StateFlow<TripEntity?> = _pendingDelete.asStateFlow()

    val uiState: StateFlow<TripsUiState> = tripRepository.getAllTrips()
        .mapLatest { trips ->
            val summaries = trips.map { trip ->
                TripSummary(
                    trip = trip,
                    trackCount = tripRepository.getTrackCountForTripOnce(trip.id),
                    totalDistanceMeters = tripRepository.getTotalDistanceForTripOnce(trip.id),
                    totalDurationMs = tripRepository.getTotalDurationForTripOnce(trip.id),
                )
            }
            TripsUiState(trips = summaries, isLoading = false)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TripsUiState())

    fun createTrip(name: String, description: String?) {
        if (name.isBlank()) return
        viewModelScope.launch {
            tripRepository.createTrip(name.trim(), description?.trim()?.ifBlank { null })
        }
    }

    fun requestDeleteTrip(trip: TripEntity) {
        val current = _pendingDelete.value
        if (current != null) {
            viewModelScope.launch { tripRepository.deleteTrip(current) }
        }
        _pendingDelete.value = trip
    }

    fun confirmDeleteTrip() {
        val pending = _pendingDelete.value ?: return
        viewModelScope.launch { tripRepository.deleteTrip(pending) }
        _pendingDelete.value = null
    }

    fun cancelDeleteTrip() {
        _pendingDelete.value = null
    }
}

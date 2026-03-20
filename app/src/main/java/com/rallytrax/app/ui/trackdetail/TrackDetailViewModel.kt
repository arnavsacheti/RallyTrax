package com.rallytrax.app.ui.trackdetail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rallytrax.app.data.local.dao.TrackDao
import com.rallytrax.app.data.local.dao.TrackPointDao
import com.rallytrax.app.data.local.entity.TrackEntity
import com.rallytrax.app.recording.LatLng
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TrackDetailUiState(
    val track: TrackEntity? = null,
    val polylinePoints: List<LatLng> = emptyList(),
    val isLoading: Boolean = true,
)

@HiltViewModel
class TrackDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val trackDao: TrackDao,
    private val trackPointDao: TrackPointDao,
) : ViewModel() {

    private val trackId: String = checkNotNull(savedStateHandle["trackId"])

    private val _uiState = MutableStateFlow(TrackDetailUiState())
    val uiState: StateFlow<TrackDetailUiState> = _uiState.asStateFlow()

    init {
        loadTrack()
    }

    private fun loadTrack() {
        viewModelScope.launch {
            val track = trackDao.getTrackById(trackId)
            val points = trackPointDao.getPointsForTrack(trackId).first()
            val polyline = points.map { LatLng(it.lat, it.lon) }
            _uiState.value = TrackDetailUiState(
                track = track,
                polylinePoints = polyline,
                isLoading = false,
            )
        }
    }
}

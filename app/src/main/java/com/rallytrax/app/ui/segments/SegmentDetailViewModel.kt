package com.rallytrax.app.ui.segments

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rallytrax.app.data.local.dao.TrackDao
import com.rallytrax.app.data.local.dao.TrackPointDao
import com.rallytrax.app.data.local.entity.SegmentEntity
import com.rallytrax.app.data.local.entity.SegmentRunEntity
import com.rallytrax.app.data.repository.SegmentRepository
import com.rallytrax.app.recording.LatLng
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RunHistoryItem(
    val run: SegmentRunEntity,
    val trackName: String,
)

data class SegmentDetailUiState(
    val segment: SegmentEntity? = null,
    val polylinePoints: List<LatLng> = emptyList(),
    val runCount: Int = 0,
    val bestTimeMs: Long? = null,
    val averageTimeMs: Long? = null,
    val isFavorite: Boolean = false,
    val runHistory: List<RunHistoryItem> = emptyList(),
    val isLoading: Boolean = true,
)

@HiltViewModel
class SegmentDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val segmentRepository: SegmentRepository,
    private val trackDao: TrackDao,
    private val trackPointDao: TrackPointDao,
) : ViewModel() {

    private val segmentId: String = checkNotNull(savedStateHandle["segmentId"])

    private val _uiState = MutableStateFlow(SegmentDetailUiState())
    val uiState: StateFlow<SegmentDetailUiState> = _uiState.asStateFlow()

    private val _snackbarMessage = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val snackbarMessage = _snackbarMessage.asSharedFlow()

    init {
        loadSegment()
    }

    private fun loadSegment() {
        viewModelScope.launch {
            val segment = segmentRepository.getSegmentById(segmentId)
            if (segment == null) {
                _uiState.value = _uiState.value.copy(isLoading = false)
                return@launch
            }

            val stats = segmentRepository.getSegmentStats(segmentId)

            // Load polyline from first run's track points
            segmentRepository.getRunsForSegment(segmentId).collect { runs ->
                val polyline = if (runs.isNotEmpty()) {
                    val firstRun = runs.first()
                    val points = trackPointDao.getPointsForTrackOnce(firstRun.trackId)
                    points.filter { it.index in firstRun.startPointIndex..firstRun.endPointIndex }
                        .map { LatLng(it.lat, it.lon) }
                } else emptyList()

                // Build run history with track names
                val history = runs.map { run ->
                    val track = trackDao.getTrackById(run.trackId)
                    RunHistoryItem(
                        run = run,
                        trackName = track?.name ?: "Unknown",
                    )
                }

                _uiState.value = SegmentDetailUiState(
                    segment = segment,
                    polylinePoints = polyline,
                    runCount = stats.runCount,
                    bestTimeMs = stats.bestTimeMs,
                    averageTimeMs = stats.averageTimeMs,
                    isFavorite = stats.isFavorite,
                    runHistory = history,
                    isLoading = false,
                )
            }
        }
    }

    fun toggleFavorite() {
        viewModelScope.launch {
            segmentRepository.toggleFavorite(segmentId)
            _uiState.value = _uiState.value.copy(isFavorite = !_uiState.value.isFavorite)
        }
    }

    fun updateName(name: String) {
        val trimmed = name.trim()
        if (trimmed.isBlank()) return
        viewModelScope.launch {
            segmentRepository.updateSegmentName(segmentId, trimmed)
            _uiState.value = _uiState.value.copy(
                segment = _uiState.value.segment?.copy(name = trimmed),
            )
        }
    }

    fun deleteSegment() {
        viewModelScope.launch {
            segmentRepository.deleteSegment(segmentId)
            _snackbarMessage.tryEmit("Segment deleted")
        }
    }
}

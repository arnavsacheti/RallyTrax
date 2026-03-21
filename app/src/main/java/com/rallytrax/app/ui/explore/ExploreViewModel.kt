package com.rallytrax.app.ui.explore

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rallytrax.app.data.local.dao.GridCellDao
import com.rallytrax.app.data.local.dao.TrackDao
import com.rallytrax.app.data.local.dao.TrackPointDao
import com.rallytrax.app.data.local.entity.GridCellEntity
import com.rallytrax.app.data.local.entity.TrackEntity
import com.rallytrax.app.data.local.entity.TrackPointEntity
import com.rallytrax.app.data.preferences.UserPreferencesData
import com.rallytrax.app.data.preferences.UserPreferencesRepository
import com.rallytrax.app.recording.LatLng
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class ExploreLayer {
    DEFAULT, HEATMAP, SPEED, ELEVATION, RECENCY
}

data class TrackPolyline(
    val trackId: String,
    val trackName: String,
    val recordedAt: Long,
    val points: List<LatLng>,
    val speeds: List<Double>,
    val elevations: List<Double>,
)

data class ExploreUiState(
    val trackPolylines: List<TrackPolyline> = emptyList(),
    val gridCells: List<GridCellEntity> = emptyList(),
    val activeLayer: ExploreLayer = ExploreLayer.DEFAULT,
    val selectedTrack: TrackEntity? = null,
    val isLoading: Boolean = true,
    val trackCount: Int = 0,
)

@HiltViewModel
class ExploreViewModel @Inject constructor(
    private val trackDao: TrackDao,
    private val trackPointDao: TrackPointDao,
    private val gridCellDao: GridCellDao,
    preferencesRepository: UserPreferencesRepository,
) : ViewModel() {

    val preferences: StateFlow<UserPreferencesData> = preferencesRepository.preferences
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UserPreferencesData())

    private val _uiState = MutableStateFlow(ExploreUiState())
    val uiState: StateFlow<ExploreUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            val tracks = trackDao.getAllTracksOnce()
            val polylines = tracks.map { track ->
                val points = trackPointDao.getPointsForTrackOnce(track.id)
                TrackPolyline(
                    trackId = track.id,
                    trackName = track.name,
                    recordedAt = track.recordedAt,
                    points = points.map { LatLng(it.lat, it.lon) },
                    speeds = points.mapNotNull { it.speed },
                    elevations = points.mapNotNull { it.elevation },
                )
            }.filter { it.points.size >= 2 }

            val gridCells = gridCellDao.getAllCellsOnce()

            _uiState.value = ExploreUiState(
                trackPolylines = polylines,
                gridCells = gridCells,
                isLoading = false,
                trackCount = tracks.size,
            )
        }
    }

    fun setActiveLayer(layer: ExploreLayer) {
        val current = _uiState.value.activeLayer
        _uiState.value = _uiState.value.copy(
            activeLayer = if (current == layer) ExploreLayer.DEFAULT else layer,
        )
    }

    fun selectTrack(trackId: String) {
        viewModelScope.launch {
            val track = trackDao.getTrackById(trackId)
            _uiState.value = _uiState.value.copy(selectedTrack = track)
        }
    }

    fun clearSelection() {
        _uiState.value = _uiState.value.copy(selectedTrack = null)
    }
}

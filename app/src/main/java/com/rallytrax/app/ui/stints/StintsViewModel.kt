package com.rallytrax.app.ui.stints

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rallytrax.app.data.local.dao.PaceNoteDao
import com.rallytrax.app.data.local.dao.TrackDao
import com.rallytrax.app.data.local.dao.TrackPointDao
import com.rallytrax.app.data.local.entity.TrackEntity
import com.rallytrax.app.data.preferences.UserPreferencesData
import com.rallytrax.app.data.preferences.UserPreferencesRepository
import com.rallytrax.app.ui.library.SortOption
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class StintsUiState(
    val tracks: List<TrackEntity> = emptyList(),
    val searchQuery: String = "",
    val sortOption: SortOption = SortOption.DATE_NEWEST,
    val selectedTags: Set<String> = emptySet(),
    val availableTags: Set<String> = emptySet(),
    val selectedTrackIds: Set<String> = emptySet(),
    val isMultiSelectMode: Boolean = false,
    val isLoading: Boolean = true,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class StintsViewModel @Inject constructor(
    private val trackDao: TrackDao,
    private val trackPointDao: TrackPointDao,
    private val paceNoteDao: PaceNoteDao,
    private val gridCellDao: com.rallytrax.app.data.local.dao.GridCellDao,
    preferencesRepository: UserPreferencesRepository,
) : ViewModel() {

    val preferences: StateFlow<UserPreferencesData> = preferencesRepository.preferences
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UserPreferencesData())

    private val _searchQuery = MutableStateFlow("")
    private val _sortOption = MutableStateFlow(SortOption.DATE_NEWEST)
    private val _selectedTags = MutableStateFlow<Set<String>>(emptySet())
    private val _selectedTrackIds = MutableStateFlow<Set<String>>(emptySet())
    private val _isMultiSelectMode = MutableStateFlow(false)

    private val _snackbarMessage = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val snackbarMessage = _snackbarMessage.asSharedFlow()

    private val _pendingDeletes = MutableStateFlow<List<TrackEntity>>(emptyList())
    val pendingDeletes: StateFlow<List<TrackEntity>> = _pendingDeletes.asStateFlow()

    private val allStints = _searchQuery.flatMapLatest { query ->
        if (query.isBlank()) {
            trackDao.getStints()
        } else {
            trackDao.searchStints(query)
        }
    }

    val uiState: StateFlow<StintsUiState> = combine(
        allStints,
        _searchQuery,
        _sortOption,
        _selectedTags,
        _selectedTrackIds,
    ) { tracks, query, sort, tags, selectedIds ->
        val availableTags = tracks
            .flatMap { it.tags.split(",").map { t -> t.trim() } }
            .filter { it.isNotBlank() }
            .toSet()

        val filteredByTags = if (tags.isEmpty()) {
            tracks
        } else {
            tracks.filter { track ->
                val trackTags = track.tags.split(",").map { it.trim() }.toSet()
                tags.any { it in trackTags }
            }
        }

        val sorted = when (sort) {
            SortOption.DATE_NEWEST -> filteredByTags.sortedByDescending { it.recordedAt }
            SortOption.DATE_OLDEST -> filteredByTags.sortedBy { it.recordedAt }
            SortOption.DISTANCE_LONGEST -> filteredByTags.sortedByDescending { it.distanceMeters }
            SortOption.DISTANCE_SHORTEST -> filteredByTags.sortedBy { it.distanceMeters }
            SortOption.DURATION_LONGEST -> filteredByTags.sortedByDescending { it.durationMs }
            SortOption.DURATION_SHORTEST -> filteredByTags.sortedBy { it.durationMs }
            SortOption.ELEVATION_MOST -> filteredByTags.sortedByDescending { it.elevationGainM }
            SortOption.ELEVATION_LEAST -> filteredByTags.sortedBy { it.elevationGainM }
            SortOption.DIFFICULTY_HARDEST -> filteredByTags.sortedByDescending { com.rallytrax.app.data.classification.RouteClassifier.difficultyOrdinal(it.difficultyRating) }
            SortOption.DIFFICULTY_EASIEST -> filteredByTags.sortedBy { com.rallytrax.app.data.classification.RouteClassifier.difficultyOrdinal(it.difficultyRating) }
        }

        StintsUiState(
            tracks = sorted,
            searchQuery = query,
            sortOption = sort,
            selectedTags = tags,
            availableTags = availableTags,
            selectedTrackIds = selectedIds,
            isMultiSelectMode = _isMultiSelectMode.value,
            isLoading = false,
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        StintsUiState(),
    )

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun updateSortOption(option: SortOption) {
        _sortOption.value = option
    }

    fun toggleTag(tag: String) {
        _selectedTags.value = _selectedTags.value.let { current ->
            if (tag in current) current - tag else current + tag
        }
    }

    fun toggleMultiSelect(trackId: String) {
        _isMultiSelectMode.value = true
        _selectedTrackIds.value = _selectedTrackIds.value.let { current ->
            if (trackId in current) {
                val newSet = current - trackId
                if (newSet.isEmpty()) {
                    _isMultiSelectMode.value = false
                }
                newSet
            } else {
                current + trackId
            }
        }
    }

    fun exitMultiSelectMode() {
        _isMultiSelectMode.value = false
        _selectedTrackIds.value = emptySet()
    }

    fun deleteSelectedTracks() {
        val ids = _selectedTrackIds.value.toList()
        if (ids.isEmpty()) return
        viewModelScope.launch {
            trackDao.deleteTracks(ids)
            _snackbarMessage.tryEmit("${ids.size} stint(s) deleted")
            recomputeGridCells()
        }
        exitMultiSelectMode()
    }

    fun requestDeleteTrack(track: TrackEntity) {
        val current = _pendingDeletes.value
        if (current.isNotEmpty()) {
            viewModelScope.launch {
                current.forEach { trackDao.deleteTrack(it.id) }
            }
        }
        _pendingDeletes.value = listOf(track)
    }

    fun confirmDeleteTrack() {
        val pending = _pendingDeletes.value
        if (pending.isEmpty()) return
        viewModelScope.launch {
            pending.forEach { trackDao.deleteTrack(it.id) }
            recomputeGridCells()
        }
        _pendingDeletes.value = emptyList()
    }

    fun cancelDeleteTrack() {
        val pending = _pendingDeletes.value
        if (pending.isEmpty()) return
        val rest = pending.dropLast(1)
        if (rest.isNotEmpty()) {
            viewModelScope.launch {
                rest.forEach { trackDao.deleteTrack(it.id) }
            }
        }
        _pendingDeletes.value = emptyList()
    }

    private fun recomputeGridCells() {
        viewModelScope.launch {
            try {
                val allTracks = trackDao.getAllTracksOnce()
                val allPoints = allTracks.flatMap { trackPointDao.getPointsForTrackOnce(it.id) }
                com.rallytrax.app.data.local.GridCellComputer.fullRecompute(allPoints, gridCellDao)
            } catch (_: Exception) {
                // Non-critical
            }
        }
    }

}

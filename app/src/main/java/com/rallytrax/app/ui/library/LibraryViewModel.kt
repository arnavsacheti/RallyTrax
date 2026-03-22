package com.rallytrax.app.ui.library

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rallytrax.app.data.gpx.GpxParseException
import com.rallytrax.app.data.gpx.GpxParser
import com.rallytrax.app.data.local.dao.PaceNoteDao
import com.rallytrax.app.data.local.dao.TrackDao
import com.rallytrax.app.data.local.dao.TrackPointDao
import com.rallytrax.app.data.local.entity.TrackEntity
import com.rallytrax.app.data.preferences.UserPreferencesData
import com.rallytrax.app.data.preferences.UserPreferencesRepository
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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class SortOption(val label: String) {
    DATE_NEWEST("Newest first"),
    DATE_OLDEST("Oldest first"),
    DISTANCE_LONGEST("Longest distance"),
    DISTANCE_SHORTEST("Shortest distance"),
    DURATION_LONGEST("Longest duration"),
    DURATION_SHORTEST("Shortest duration"),
}

data class LibraryUiState(
    val tracks: List<TrackEntity> = emptyList(),
    val searchQuery: String = "",
    val sortOption: SortOption = SortOption.DATE_NEWEST,
    val selectedTags: Set<String> = emptySet(),
    val availableTags: Set<String> = emptySet(),
    val selectedTrackIds: Set<String> = emptySet(),
    val isMultiSelectMode: Boolean = false,
    // Route classification filters
    val selectedRouteTypes: Set<String> = emptySet(),
    val selectedDifficulties: Set<String> = emptySet(),
    val selectedSurfaces: Set<String> = emptySet(),
    val availableRouteTypes: Set<String> = emptySet(),
    val availableDifficulties: Set<String> = emptySet(),
    val availableSurfaces: Set<String> = emptySet(),
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class LibraryViewModel @Inject constructor(
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
    private val _selectedRouteTypes = MutableStateFlow<Set<String>>(emptySet())
    private val _selectedDifficulties = MutableStateFlow<Set<String>>(emptySet())
    private val _selectedSurfaces = MutableStateFlow<Set<String>>(emptySet())

    private val _snackbarMessage = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val snackbarMessage = _snackbarMessage.asSharedFlow()

    // Pending deletes for undo — most recent is the one shown in the snackbar
    private val _pendingDeletes = MutableStateFlow<List<TrackEntity>>(emptyList())
    val pendingDeletes: StateFlow<List<TrackEntity>> = _pendingDeletes.asStateFlow()

    private val allTracks = _searchQuery.flatMapLatest { query ->
        if (query.isBlank()) {
            trackDao.getAllTracks()
        } else {
            trackDao.searchTracks(query)
        }
    }

    val uiState: StateFlow<LibraryUiState> = combine(
        allTracks,
        _searchQuery,
        _sortOption,
        _selectedTags,
        _selectedTrackIds,
    ) { tracks, query, sort, tags, selectedIds ->
        // Collect all available tags
        val availableTags = tracks
            .flatMap { it.tags.split(",").map { t -> t.trim() } }
            .filter { it.isNotBlank() }
            .toSet()

        // Filter by selected tags
        val filteredByTags = if (tags.isEmpty()) {
            tracks
        } else {
            tracks.filter { track ->
                val trackTags = track.tags.split(",").map { it.trim() }.toSet()
                tags.any { it in trackTags }
            }
        }

        // Sort
        val sorted = when (sort) {
            SortOption.DATE_NEWEST -> filteredByTags.sortedByDescending { it.recordedAt }
            SortOption.DATE_OLDEST -> filteredByTags.sortedBy { it.recordedAt }
            SortOption.DISTANCE_LONGEST -> filteredByTags.sortedByDescending { it.distanceMeters }
            SortOption.DISTANCE_SHORTEST -> filteredByTags.sortedBy { it.distanceMeters }
            SortOption.DURATION_LONGEST -> filteredByTags.sortedByDescending { it.durationMs }
            SortOption.DURATION_SHORTEST -> filteredByTags.sortedBy { it.durationMs }
        }

        LibraryUiState(
            tracks = sorted,
            searchQuery = query,
            sortOption = sort,
            selectedTags = tags,
            availableTags = availableTags,
            selectedTrackIds = selectedIds,
            isMultiSelectMode = _isMultiSelectMode.value,
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        LibraryUiState(),
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

    fun clearTagFilter() {
        _selectedTags.value = emptySet()
    }

    fun toggleRouteTypeFilter(routeType: String) {
        _selectedRouteTypes.value = _selectedRouteTypes.value.let { current ->
            if (routeType in current) current - routeType else current + routeType
        }
    }

    fun toggleDifficultyFilter(difficulty: String) {
        _selectedDifficulties.value = _selectedDifficulties.value.let { current ->
            if (difficulty in current) current - difficulty else current + difficulty
        }
    }

    fun toggleSurfaceFilter(surface: String) {
        _selectedSurfaces.value = _selectedSurfaces.value.let { current ->
            if (surface in current) current - surface else current + surface
        }
    }

    // --- Multi-select ---

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
            _snackbarMessage.tryEmit("${ids.size} track(s) deleted")
            recomputeGridCells()
        }
        exitMultiSelectMode()
    }

    // --- Single track delete with undo ---

    fun requestDeleteTrack(track: TrackEntity) {
        // Auto-confirm any previously pending deletes
        val current = _pendingDeletes.value
        if (current.isNotEmpty()) {
            viewModelScope.launch {
                current.forEach { trackDao.deleteTrack(it.id) }
            }
        }
        // Set the new one as the only pending (gets the snackbar)
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
        // Undo only the most recent (last) pending delete
        val pending = _pendingDeletes.value
        if (pending.isEmpty()) return
        val restored = pending.last()
        val rest = pending.dropLast(1)
        // Confirm all prior ones, undo the last
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

    // --- GPX Import ---

    fun importGpx(context: Context, uri: Uri) {
        viewModelScope.launch {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                    ?: throw GpxParseException("Could not open file")
                val result = inputStream.use { com.rallytrax.app.data.gpx.TrackImporter.import(it) }
                trackDao.insertTrack(result.track)
                trackPointDao.insertPoints(result.points)
                if (result.paceNotes.isNotEmpty()) {
                    paceNoteDao.insertNotes(result.paceNotes)
                }
                _snackbarMessage.tryEmit("Imported: ${result.track.name}")
            } catch (e: GpxParseException) {
                _snackbarMessage.tryEmit("Import failed: ${e.message}")
            } catch (e: Exception) {
                _snackbarMessage.tryEmit("Import failed: ${e.message}")
            }
        }
    }
}

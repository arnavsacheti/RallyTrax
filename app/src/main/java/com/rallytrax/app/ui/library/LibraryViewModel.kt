package com.rallytrax.app.ui.library

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.location.LocationServices
import com.rallytrax.app.data.classification.RouteClassifier
import com.rallytrax.app.data.gpx.GpxParseException
import com.rallytrax.app.data.local.GridCellComputer
import com.rallytrax.app.data.local.dao.LatLonSpeedProjection
import com.rallytrax.app.data.local.dao.PaceNoteDao
import com.rallytrax.app.data.local.dao.TrackDao
import com.rallytrax.app.data.local.dao.TrackPointDao
import com.rallytrax.app.data.local.entity.TrackEntity
import com.rallytrax.app.data.preferences.UserPreferencesData
import com.rallytrax.app.data.preferences.UserPreferencesRepository
import com.rallytrax.app.di.IoDispatcher
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

enum class SortOption(val label: String) {
    DATE_NEWEST("Newest first"),
    DATE_OLDEST("Oldest first"),
    DISTANCE_LONGEST("Longest distance"),
    DISTANCE_SHORTEST("Shortest distance"),
    DURATION_LONGEST("Longest duration"),
    DURATION_SHORTEST("Shortest duration"),
    ELEVATION_MOST("Most elevation"),
    ELEVATION_LEAST("Least elevation"),
    DIFFICULTY_HARDEST("Hardest first"),
    DIFFICULTY_EASIEST("Easiest first"),
}

data class LibraryUiState(
    val tracks: List<TrackEntity> = emptyList(),
    val searchQuery: String = "",
    val sortOption: SortOption = SortOption.DATE_NEWEST,
    val selectedTags: Set<String> = emptySet(),
    val availableTags: Set<String> = emptySet(),
    val selectedTrackIds: Set<String> = emptySet(),
    val isMultiSelectMode: Boolean = false,
    // Category filters
    val selectedRouteTypes: Set<String> = emptySet(),
    val selectedDifficulties: Set<String> = emptySet(),
    val selectedSurfaces: Set<String> = emptySet(),
    val availableRouteTypes: Set<String> = emptySet(),
    val availableDifficulties: Set<String> = emptySet(),
    val availableSurfaces: Set<String> = emptySet(),
    // Range filters
    val distanceRange: ClosedFloatingPointRange<Float>? = null,
    val elevationRange: ClosedFloatingPointRange<Float>? = null,
    val durationRange: ClosedFloatingPointRange<Float>? = null,
    val maxDistance: Float = 0f,
    val maxElevation: Float = 0f,
    val maxDuration: Float = 0f,
    // Proximity
    val nearMeFilter: NearMeFilter? = null,
    // Attempt counts
    val attemptCounts: Map<String, Int> = emptyMap(),
    // Active filter summary
    val activeFilterCount: Int = 0,
    val isLoading: Boolean = true,
)

private data class CategoryFilters(
    val selectedTags: Set<String>,
    val selectedRouteTypes: Set<String>,
    val selectedDifficulties: Set<String>,
    val selectedSurfaces: Set<String>,
)

private data class RangeFilters(
    val distanceRange: ClosedFloatingPointRange<Float>?,
    val elevationRange: ClosedFloatingPointRange<Float>?,
    val durationRange: ClosedFloatingPointRange<Float>?,
    val nearMeFilter: NearMeFilter?,
)

private data class SelectionState(
    val selectedTrackIds: Set<String>,
    val isMultiSelectMode: Boolean,
)

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val trackDao: TrackDao,
    private val trackPointDao: TrackPointDao,
    private val paceNoteDao: PaceNoteDao,
    private val gridCellDao: com.rallytrax.app.data.local.dao.GridCellDao,
    preferencesRepository: UserPreferencesRepository,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {

    val preferences: StateFlow<UserPreferencesData> = preferencesRepository.preferences
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UserPreferencesData())

    init {
        backfillUnclassifiedRoutes()
    }

    private val _searchQuery = MutableStateFlow("")
    private val _sortOption = MutableStateFlow(SortOption.DATE_NEWEST)
    private val _selectedTags = MutableStateFlow<Set<String>>(emptySet())
    private val _selectedTrackIds = MutableStateFlow<Set<String>>(emptySet())
    private val _isMultiSelectMode = MutableStateFlow(false)
    private val _selectedRouteTypes = MutableStateFlow<Set<String>>(emptySet())
    private val _selectedDifficulties = MutableStateFlow<Set<String>>(emptySet())
    private val _selectedSurfaces = MutableStateFlow<Set<String>>(emptySet())
    private val _distanceRange = MutableStateFlow<ClosedFloatingPointRange<Float>?>(null)
    private val _elevationRange = MutableStateFlow<ClosedFloatingPointRange<Float>?>(null)
    private val _durationRange = MutableStateFlow<ClosedFloatingPointRange<Float>?>(null)
    private val _nearMeFilter = MutableStateFlow<NearMeFilter?>(null)

    private val _snackbarMessage = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val snackbarMessage = _snackbarMessage.asSharedFlow()

    // Pending deletes for undo — most recent is the one shown in the snackbar
    private val _pendingDeletes = MutableStateFlow<List<TrackEntity>>(emptyList())
    val pendingDeletes: StateFlow<List<TrackEntity>> = _pendingDeletes.asStateFlow()

    private val _thumbnails = MutableStateFlow<Map<String, List<LatLonSpeedProjection>>>(emptyMap())
    val thumbnails: StateFlow<Map<String, List<LatLonSpeedProjection>>> = _thumbnails.asStateFlow()

    private val thumbnailFetchMutex = Mutex()
    private val thumbnailsInFlight = mutableSetOf<String>()

    private val allTracks = _searchQuery
        // Avoid hammering DAO/search when the user is typing rapidly.
        .debounce(150)
        .flatMapLatest { query ->
            if (query.isBlank()) {
                trackDao.getRoutes()
            } else {
                trackDao.searchRoutes(query)
            }
        }
        // Don't kick off prefetch on identical re-emissions of the same id set.
        .distinctUntilChanged { old, new -> old.map { it.id } == new.map { it.id } }
        .onEach { tracks ->
            prefetchThumbnails(tracks.map { it.id })
        }

    private val categoryFilters = combine(
        _selectedTags,
        _selectedRouteTypes,
        _selectedDifficulties,
        _selectedSurfaces,
        ::CategoryFilters,
    )

    private val rangeFilters = combine(
        _distanceRange,
        _elevationRange,
        _durationRange,
        _nearMeFilter,
        ::RangeFilters,
    )

    private val selectionState = combine(
        _selectedTrackIds,
        _isMultiSelectMode,
        ::SelectionState,
    )

    val uiState: StateFlow<LibraryUiState> = combine(
        allTracks,
        _searchQuery,
        combine(_sortOption, selectionState, ::Pair),
        categoryFilters,
        rangeFilters,
    ) { tracks, query, (sort, selection), catFilters, rngFilters ->
        // Compute available values from ALL tracks (before filtering)
        val availableTags = tracks
            .flatMap { it.tags.split(",").map { t -> t.trim() } }
            .filter { it.isNotBlank() }
            .toSet()
        val availableRouteTypes = tracks.mapNotNull { it.routeType }.filter { it.isNotBlank() }.toSortedSet()
        val availableDifficulties = tracks.mapNotNull { it.difficultyRating }.filter { it.isNotBlank() }.toSortedSet()
        val availableSurfaces = tracks.mapNotNull { it.primarySurface }.filter { it.isNotBlank() }.toSortedSet()

        // Compute range bounds
        val maxDistance = tracks.maxOfOrNull { it.distanceMeters.toFloat() } ?: 0f
        val maxElevation = tracks.maxOfOrNull { it.elevationGainM.toFloat() } ?: 0f
        val maxDuration = tracks.maxOfOrNull { it.durationMs.toFloat() } ?: 0f

        // Compute attempt counts (group by name)
        val attemptCounts = tracks.groupingBy { it.name }.eachCount()

        // Sequential filter pipeline
        var filtered = tracks

        // Tag filter
        if (catFilters.selectedTags.isNotEmpty()) {
            filtered = filtered.filter { track ->
                val trackTags = track.tags.split(",").map { it.trim() }.toSet()
                catFilters.selectedTags.any { it in trackTags }
            }
        }

        // Route type filter
        if (catFilters.selectedRouteTypes.isNotEmpty()) {
            filtered = filtered.filter { it.routeType in catFilters.selectedRouteTypes }
        }

        // Difficulty filter
        if (catFilters.selectedDifficulties.isNotEmpty()) {
            filtered = filtered.filter { it.difficultyRating in catFilters.selectedDifficulties }
        }

        // Surface filter
        if (catFilters.selectedSurfaces.isNotEmpty()) {
            filtered = filtered.filter { it.primarySurface in catFilters.selectedSurfaces }
        }

        // Distance range filter
        rngFilters.distanceRange?.let { range ->
            filtered = filtered.filter { it.distanceMeters.toFloat() in range }
        }

        // Elevation range filter
        rngFilters.elevationRange?.let { range ->
            filtered = filtered.filter { it.elevationGainM.toFloat() in range }
        }

        // Duration range filter
        rngFilters.durationRange?.let { range ->
            filtered = filtered.filter { it.durationMs.toFloat() in range }
        }

        // Near Me proximity filter
        rngFilters.nearMeFilter?.let { nearMe ->
            val bbox = nearMe.toBoundingBox()
            filtered = filtered.filter { track ->
                bbox.overlaps(
                    track.boundingBoxNorthLat, track.boundingBoxSouthLat,
                    track.boundingBoxEastLon, track.boundingBoxWestLon,
                )
            }
        }

        // Sort
        val sorted = when (sort) {
            SortOption.DATE_NEWEST -> filtered.sortedByDescending { it.recordedAt }
            SortOption.DATE_OLDEST -> filtered.sortedBy { it.recordedAt }
            SortOption.DISTANCE_LONGEST -> filtered.sortedByDescending { it.distanceMeters }
            SortOption.DISTANCE_SHORTEST -> filtered.sortedBy { it.distanceMeters }
            SortOption.DURATION_LONGEST -> filtered.sortedByDescending { it.durationMs }
            SortOption.DURATION_SHORTEST -> filtered.sortedBy { it.durationMs }
            SortOption.ELEVATION_MOST -> filtered.sortedByDescending { it.elevationGainM }
            SortOption.ELEVATION_LEAST -> filtered.sortedBy { it.elevationGainM }
            SortOption.DIFFICULTY_HARDEST -> filtered.sortedByDescending { com.rallytrax.app.data.classification.RouteClassifier.difficultyOrdinal(it.difficultyRating) }
            SortOption.DIFFICULTY_EASIEST -> filtered.sortedBy { com.rallytrax.app.data.classification.RouteClassifier.difficultyOrdinal(it.difficultyRating) }
        }

        // Active filter count
        val activeFilterCount = listOf(
            catFilters.selectedTags.isNotEmpty(),
            catFilters.selectedRouteTypes.isNotEmpty(),
            catFilters.selectedDifficulties.isNotEmpty(),
            catFilters.selectedSurfaces.isNotEmpty(),
            rngFilters.distanceRange != null,
            rngFilters.elevationRange != null,
            rngFilters.durationRange != null,
            rngFilters.nearMeFilter != null,
        ).count { it }

        LibraryUiState(
            tracks = sorted,
            searchQuery = query,
            sortOption = sort,
            selectedTags = catFilters.selectedTags,
            availableTags = availableTags,
            selectedTrackIds = selection.selectedTrackIds,
            isMultiSelectMode = selection.isMultiSelectMode,
            selectedRouteTypes = catFilters.selectedRouteTypes,
            selectedDifficulties = catFilters.selectedDifficulties,
            selectedSurfaces = catFilters.selectedSurfaces,
            availableRouteTypes = availableRouteTypes,
            availableDifficulties = availableDifficulties,
            availableSurfaces = availableSurfaces,
            distanceRange = rngFilters.distanceRange,
            elevationRange = rngFilters.elevationRange,
            durationRange = rngFilters.durationRange,
            maxDistance = maxDistance,
            maxElevation = maxElevation,
            maxDuration = maxDuration,
            nearMeFilter = rngFilters.nearMeFilter,
            attemptCounts = attemptCounts,
            activeFilterCount = activeFilterCount,
            isLoading = false,
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

    fun updateDistanceRange(range: ClosedFloatingPointRange<Float>?) {
        _distanceRange.value = range
    }

    fun updateElevationRange(range: ClosedFloatingPointRange<Float>?) {
        _elevationRange.value = range
    }

    fun updateDurationRange(range: ClosedFloatingPointRange<Float>?) {
        _durationRange.value = range
    }

    fun updateNearMeFilter(filter: NearMeFilter?) {
        _nearMeFilter.value = filter
    }

    @SuppressLint("MissingPermission")
    fun enableNearMeFilter(context: Context) {
        viewModelScope.launch {
            try {
                val client = LocationServices.getFusedLocationProviderClient(context)
                val location = suspendCancellableCoroutine { cont ->
                    client.lastLocation
                        .addOnSuccessListener { loc -> cont.resumeWith(Result.success(loc)) }
                        .addOnFailureListener { cont.resumeWith(Result.success(null)) }
                }
                if (location != null) {
                    _nearMeFilter.value = NearMeFilter(
                        latitude = location.latitude,
                        longitude = location.longitude,
                        radiusKm = 25.0,
                    )
                } else {
                    _snackbarMessage.tryEmit("Could not determine location — try again")
                }
            } catch (_: Exception) {
                _snackbarMessage.tryEmit("Failed to get location")
            }
        }
    }

    fun clearNearMeFilter() {
        _nearMeFilter.value = null
    }

    /** Haversine distance in meters from a user location to the center of a track's bounding box. */
    fun distanceToUser(track: TrackEntity, userLat: Double, userLon: Double): Double {
        val centerLat = (track.boundingBoxNorthLat + track.boundingBoxSouthLat) / 2.0
        val centerLon = (track.boundingBoxEastLon + track.boundingBoxWestLon) / 2.0
        return haversine(userLat, userLon, centerLat, centerLon)
    }

    fun clearAllFilters() {
        _selectedTags.value = emptySet()
        _selectedRouteTypes.value = emptySet()
        _selectedDifficulties.value = emptySet()
        _selectedSurfaces.value = emptySet()
        _distanceRange.value = null
        _elevationRange.value = null
        _durationRange.value = null
        _nearMeFilter.value = null
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
            withContext(ioDispatcher) {
                trackDao.deleteTracks(ids)
            }
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
                withContext(ioDispatcher) {
                    current.forEach { trackDao.deleteTrack(it.id) }
                }
            }
        }
        // Set the new one as the only pending (gets the snackbar)
        _pendingDeletes.value = listOf(track)
    }

    fun confirmDeleteTrack() {
        val pending = _pendingDeletes.value
        if (pending.isEmpty()) return
        viewModelScope.launch {
            withContext(ioDispatcher) {
                pending.forEach { trackDao.deleteTrack(it.id) }
            }
            recomputeGridCells()
        }
        _pendingDeletes.value = emptyList()
    }

    fun cancelDeleteTrack() {
        // Undo only the most recent (last) pending delete
        val pending = _pendingDeletes.value
        if (pending.isEmpty()) return
        val rest = pending.dropLast(1)
        // Confirm all prior ones, undo the last
        if (rest.isNotEmpty()) {
            viewModelScope.launch {
                withContext(ioDispatcher) {
                    rest.forEach { trackDao.deleteTrack(it.id) }
                }
            }
        }
        _pendingDeletes.value = emptyList()
    }

    private fun prefetchThumbnails(trackIds: List<String>) {
        if (trackIds.isEmpty()) return
        viewModelScope.launch {
            val toFetch = thumbnailFetchMutex.withLock {
                val cached = _thumbnails.value.keys
                trackIds.filter { it !in cached && it !in thumbnailsInFlight }
                    .also { thumbnailsInFlight.addAll(it) }
            }
            if (toFetch.isEmpty()) return@launch
            withContext(ioDispatcher) {
                for (id in toFetch) {
                    val raw = try {
                        trackPointDao.getLatLonSpeedForTrack(id)
                    } catch (_: Exception) {
                        emptyList()
                    }
                    val sampled = subsample(raw, targetPoints = 150)
                    _thumbnails.value = _thumbnails.value + (id to sampled)
                }
            }
            thumbnailFetchMutex.withLock { thumbnailsInFlight.removeAll(toFetch.toSet()) }
        }
    }

    private fun subsample(
        points: List<LatLonSpeedProjection>,
        targetPoints: Int,
    ): List<LatLonSpeedProjection> {
        if (points.size <= targetPoints) return points
        val stride = points.size / targetPoints
        if (stride <= 1) return points
        val sampled = ArrayList<LatLonSpeedProjection>(targetPoints + 1)
        for (i in points.indices step stride) sampled.add(points[i])
        if (sampled.lastOrNull() !== points.last()) sampled.add(points.last())
        return sampled
    }

    private fun recomputeGridCells() {
        viewModelScope.launch {
            try {
                withContext(ioDispatcher) {
                    val allTracks = trackDao.getAllTracksOnce()
                    val allPoints = allTracks.flatMap { trackPointDao.getPointsForTrackOnce(it.id) }
                    GridCellComputer.fullRecompute(allPoints, gridCellDao)
                }
            } catch (_: Exception) {
                // Non-critical
            }
        }
    }

    // --- GPX Import ---

    fun importGpx(context: Context, uri: Uri) {
        viewModelScope.launch {
            try {
                val result = withContext(ioDispatcher) {
                    val inputStream = context.contentResolver.openInputStream(uri)
                        ?: throw GpxParseException("Could not open file")
                    inputStream.use { com.rallytrax.app.data.gpx.TrackImporter.import(it) }
                }
                withContext(ioDispatcher) {
                    trackDao.insertTrack(result.track.copy(trackCategory = "route"))
                    // Insert points in chunks to avoid large single transactions
                    result.points.chunked(1000).forEach { chunk ->
                        trackPointDao.insertPoints(chunk)
                    }
                    if (result.paceNotes.isNotEmpty()) {
                        paceNoteDao.insertNotes(result.paceNotes)
                    }
                }
                _snackbarMessage.tryEmit("Imported: ${result.track.name}")
                // Incremental grid cell update instead of full recompute
                updateGridCellsForTrack(result.track.id)
                // Auto-classify the imported route
                classifyTrack(result.track.id)
            } catch (e: GpxParseException) {
                _snackbarMessage.tryEmit("Import failed: ${e.message}")
            } catch (e: Exception) {
                _snackbarMessage.tryEmit("Import failed: ${e.message}")
            }
        }
    }

    private fun classifyTrack(trackId: String) {
        viewModelScope.launch {
            try {
                withContext(ioDispatcher) {
                    val points = trackPointDao.getPointsForTrackOnce(trackId)
                    if (points.size < 10) return@withContext
                    val classification = RouteClassifier.classify(points)
                    val track = trackDao.getTrackById(trackId) ?: return@withContext
                    trackDao.updateTrack(
                        track.copy(
                            routeType = classification.suggestedRouteType,
                            difficultyRating = classification.difficultyRating,
                            curvinessScore = classification.curvinessScore,
                        ),
                    )
                }
            } catch (_: Exception) {
                // Non-critical
            }
        }
    }

    private fun backfillUnclassifiedRoutes() {
        viewModelScope.launch {
            try {
                withContext(ioDispatcher) {
                    val routes = trackDao.getAllTracksOnce()
                        .filter { it.trackCategory == "route" && it.difficultyRating == null }
                    for (route in routes) {
                        val points = trackPointDao.getPointsForTrackOnce(route.id)
                        if (points.size < 10) continue
                        val classification = RouteClassifier.classify(points)
                        trackDao.updateTrack(
                            route.copy(
                                routeType = classification.suggestedRouteType,
                                difficultyRating = classification.difficultyRating,
                                curvinessScore = classification.curvinessScore,
                            ),
                        )
                    }
                }
            } catch (_: Exception) {
                // Non-critical
            }
        }
    }

    private fun updateGridCellsForTrack(trackId: String) {
        viewModelScope.launch {
            try {
                withContext(ioDispatcher) {
                    val points = trackPointDao.getLatLonForTrack(trackId)
                    GridCellComputer.updateForLatLon(points, gridCellDao)
                }
            } catch (_: Exception) {
                // Non-critical
            }
        }
    }

    companion object {
        private const val EARTH_RADIUS_METERS = 6_371_000.0

        /** Haversine distance in meters between two lat/lon points. */
        fun haversine(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
            val dLat = Math.toRadians(lat2 - lat1)
            val dLon = Math.toRadians(lon2 - lon1)
            val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2) * sin(dLon / 2)
            val c = 2 * atan2(sqrt(a), sqrt(1 - a))
            return EARTH_RADIUS_METERS * c
        }
    }

}

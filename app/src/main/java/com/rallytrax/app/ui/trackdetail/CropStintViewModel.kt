package com.rallytrax.app.ui.trackdetail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import com.rallytrax.app.data.local.RallyTraxDatabase
import com.rallytrax.app.data.local.dao.PaceNoteDao
import com.rallytrax.app.data.local.dao.SegmentDao
import com.rallytrax.app.data.local.dao.TrackDao
import com.rallytrax.app.data.local.dao.TrackPointDao
import com.rallytrax.app.data.local.entity.TrackEntity
import com.rallytrax.app.data.local.entity.TrackPointEntity
import com.rallytrax.app.data.preferences.UserPreferencesData
import com.rallytrax.app.data.preferences.UserPreferencesRepository
import com.rallytrax.app.di.DefaultDispatcher
import com.rallytrax.app.di.IoDispatcher
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class CropStintUiState(
    val track: TrackEntity? = null,
    val points: List<TrackPointEntity> = emptyList(),
    val speedProfile: List<Float> = emptyList(),
    val startIndex: Int = 0,
    val endIndex: Int = 0,
    val totalPoints: Int = 0,
    val previewDistance: Double = 0.0,
    val previewDuration: Long = 0L,
    val previewAvgSpeed: Double = 0.0,
    val previewMaxSpeed: Double = 0.0,
    val startTimestamp: Long = 0L,
    val endTimestamp: Long = 0L,
    val isLoading: Boolean = true,
    val isCropping: Boolean = false,
    val cropComplete: Boolean = false,
)

@HiltViewModel
class CropStintViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val trackDao: TrackDao,
    private val trackPointDao: TrackPointDao,
    private val paceNoteDao: PaceNoteDao,
    private val segmentDao: SegmentDao,
    private val database: RallyTraxDatabase,
    preferencesRepository: UserPreferencesRepository,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    @DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
) : ViewModel() {

    val preferences: StateFlow<UserPreferencesData> = preferencesRepository.preferences
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UserPreferencesData())

    private val trackId: String = checkNotNull(savedStateHandle["trackId"])

    private val _uiState = MutableStateFlow(CropStintUiState())
    val uiState: StateFlow<CropStintUiState> = _uiState.asStateFlow()

    init {
        loadTrackData()
    }

    private fun loadTrackData() {
        viewModelScope.launch {
            val track = withContext(ioDispatcher) { trackDao.getTrackById(trackId) } ?: return@launch
            val points = withContext(ioDispatcher) { trackPointDao.getPointsForTrackOnce(trackId) }
            if (points.size < 2) {
                _uiState.value = CropStintUiState(track = track, isLoading = false)
                return@launch
            }

            val speeds = withContext(defaultDispatcher) {
                points.map { ((it.speed ?: 0.0) * 3.6).toFloat() }
            }

            val stats = withContext(defaultDispatcher) { computeStats(points, 0, points.lastIndex) }

            _uiState.value = CropStintUiState(
                track = track,
                points = points,
                speedProfile = speeds,
                startIndex = 0,
                endIndex = points.lastIndex,
                totalPoints = points.size,
                previewDistance = stats.distance,
                previewDuration = stats.duration,
                previewAvgSpeed = stats.avgSpeed,
                previewMaxSpeed = stats.maxSpeed,
                startTimestamp = points.first().timestamp,
                endTimestamp = points.last().timestamp,
                isLoading = false,
            )
        }
    }

    fun updateRange(startFraction: Float, endFraction: Float) {
        val state = _uiState.value
        if (state.points.isEmpty()) return

        val lastIdx = state.points.lastIndex
        val newStart = (startFraction * lastIdx).toInt().coerceIn(0, lastIdx)
        val newEnd = (endFraction * lastIdx).toInt().coerceIn(newStart, lastIdx)

        if (newStart == state.startIndex && newEnd == state.endIndex) return

        viewModelScope.launch {
            val stats = withContext(defaultDispatcher) {
                computeStats(state.points, newStart, newEnd)
            }
            _uiState.value = state.copy(
                startIndex = newStart,
                endIndex = newEnd,
                previewDistance = stats.distance,
                previewDuration = stats.duration,
                previewAvgSpeed = stats.avgSpeed,
                previewMaxSpeed = stats.maxSpeed,
                startTimestamp = state.points[newStart].timestamp,
                endTimestamp = state.points[newEnd].timestamp,
            )
        }
    }

    fun cropStint() {
        val state = _uiState.value
        val track = state.track ?: return
        if (state.points.isEmpty() || state.isCropping) return
        if (state.startIndex == 0 && state.endIndex == state.points.lastIndex) return

        _uiState.value = state.copy(isCropping = true)

        viewModelScope.launch {
            database.withTransaction {
                executeCrop(track, state.points, state.startIndex, state.endIndex)
            }
            _uiState.value = _uiState.value.copy(isCropping = false, cropComplete = true)
        }
    }

    private suspend fun executeCrop(
        track: TrackEntity,
        allPoints: List<TrackPointEntity>,
        startIndex: Int,
        endIndex: Int,
    ) {
        val keptPoints = allPoints.subList(startIndex, endIndex + 1)
        val reindexed = keptPoints.mapIndexed { i, p -> p.copy(index = i) }

        trackPointDao.deletePointsForTrack(trackId)
        trackPointDao.insertPoints(reindexed)

        val stats = computeStats(allPoints, startIndex, endIndex)
        var northLat = -90.0
        var southLat = 90.0
        var eastLon = -180.0
        var westLon = 180.0
        var elevationGain = 0.0
        var prevElev: Double? = null

        for (p in keptPoints) {
            if (p.lat > northLat) northLat = p.lat
            if (p.lat < southLat) southLat = p.lat
            if (p.lon > eastLon) eastLon = p.lon
            if (p.lon < westLon) westLon = p.lon
            val elev = p.elevation
            val prev = prevElev
            if (elev != null && prev != null) {
                val delta = elev - prev
                if (delta > 0) elevationGain += delta
            }
            if (elev != null) prevElev = elev
        }

        val updatedTrack = track.copy(
            recordedAt = keptPoints.first().timestamp,
            durationMs = stats.duration,
            distanceMeters = stats.distance,
            avgSpeedMps = stats.avgSpeed,
            maxSpeedMps = stats.maxSpeed,
            elevationGainM = elevationGain,
            boundingBoxNorthLat = northLat,
            boundingBoxSouthLat = southLat,
            boundingBoxEastLon = eastLon,
            boundingBoxWestLon = westLon,
            peakCorneringG = null,
            avgCorneringG = null,
            smoothnessScore = null,
            roadRoughnessIndex = null,
            brakingEfficiencyScore = null,
            elevationAdjustedAvgSpeedMps = null,
            gripEventCount = null,
            gripEventSummary = null,
        )
        trackDao.updateTrack(updatedTrack)

        paceNoteDao.deleteNotesForTrack(trackId)
        segmentDao.deleteRunsForTrack(trackId)
    }

    private data class CropStats(
        val distance: Double,
        val duration: Long,
        val avgSpeed: Double,
        val maxSpeed: Double,
    )

    private fun computeStats(
        points: List<TrackPointEntity>,
        startIndex: Int,
        endIndex: Int,
    ): CropStats {
        if (startIndex >= endIndex || startIndex >= points.size) {
            return CropStats(0.0, 0L, 0.0, 0.0)
        }

        var distance = 0.0
        var maxSpeed = 0.0

        for (i in startIndex until endIndex) {
            val p1 = points[i]
            val p2 = points[i + 1]
            distance += haversine(p1.lat, p1.lon, p2.lat, p2.lon)
            val speed = p2.speed ?: 0.0
            if (speed > maxSpeed) maxSpeed = speed
        }
        val speed0 = points[startIndex].speed ?: 0.0
        if (speed0 > maxSpeed) maxSpeed = speed0

        val duration = points[endIndex].timestamp - points[startIndex].timestamp
        val avgSpeed = if (duration > 0) distance / (duration / 1000.0) else 0.0

        return CropStats(distance, duration, avgSpeed, maxSpeed)
    }

    private fun haversine(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = kotlin.math.sin(dLat / 2) * kotlin.math.sin(dLat / 2) +
            kotlin.math.cos(Math.toRadians(lat1)) * kotlin.math.cos(Math.toRadians(lat2)) *
            kotlin.math.sin(dLon / 2) * kotlin.math.sin(dLon / 2)
        val c = 2 * kotlin.math.atan2(kotlin.math.sqrt(a), kotlin.math.sqrt(1 - a))
        return r * c
    }
}

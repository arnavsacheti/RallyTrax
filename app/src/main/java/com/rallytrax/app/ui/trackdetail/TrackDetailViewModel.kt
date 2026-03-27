package com.rallytrax.app.ui.trackdetail

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Environment
import android.provider.MediaStore
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rallytrax.app.data.gpx.GpxExporter
import com.rallytrax.app.data.local.dao.PaceNoteDao
import com.rallytrax.app.data.local.dao.TrackDao
import com.rallytrax.app.data.local.dao.TrackPointDao
import com.rallytrax.app.data.local.entity.PaceNoteEntity
import com.rallytrax.app.data.local.entity.TrackEntity
import com.rallytrax.app.data.local.entity.TrackPointEntity
import com.rallytrax.app.data.classification.ValhallaRouteClient
import com.rallytrax.app.data.preferences.UserPreferencesData
import com.rallytrax.app.data.preferences.UserPreferencesRepository
import com.rallytrax.app.data.repository.SegmentRepository
import com.rallytrax.app.data.repository.TrackSegmentMatch
import com.rallytrax.app.di.DefaultDispatcher
import com.rallytrax.app.di.IoDispatcher
import com.rallytrax.app.pacenotes.PaceNoteGenerator
import com.rallytrax.app.pacenotes.SegmentMatcher
import com.rallytrax.app.recording.LatLng
import com.rallytrax.app.ui.recording.SensorStats
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import kotlin.math.abs

data class ElevationPoint(
    val distanceFromStart: Double,
    val elevation: Double,
)

data class SpeedPoint(
    val distanceFromStart: Double,
    val speedMps: Double,
)

data class LateralGPoint(
    val distanceFromStart: Double,
    val lateralG: Double,
)

data class YawRatePoint(
    val distanceFromStart: Double,
    val yawRateDegPerS: Double,
)

data class RollRatePoint(
    val distanceFromStart: Double,
    val rollRateDegPerS: Double,
)

data class CurvatureDistribution(
    val straight: Float = 0f,  // curvature < 0.5 deg/m
    val gentle: Float = 0f,    // 0.5 - 2.0
    val moderate: Float = 0f,  // 2.0 - 5.0
    val tight: Float = 0f,     // 5.0 - 10.0
    val hairpin: Float = 0f,   // > 10.0
)

data class TrackSegmentUi(
    val segmentId: String,
    val name: String,
    val distanceMeters: Double,
    val thisRunDurationMs: Long,
    val bestTimeMs: Long?,
    val runCount: Int,
    val isFavorite: Boolean,
)

data class TrackDetailUiState(
    val track: TrackEntity? = null,
    val trackPoints: List<TrackPointEntity> = emptyList(),
    val polylinePoints: List<LatLng> = emptyList(),
    val elevationProfile: List<ElevationPoint> = emptyList(),
    val speedProfile: List<SpeedPoint> = emptyList(),
    val curvatureDistribution: CurvatureDistribution = CurvatureDistribution(),
    val tags: List<String> = emptyList(),
    val paceNotes: List<PaceNoteEntity> = emptyList(),
    val isLoading: Boolean = true,
    val isGeneratingNotes: Boolean = false,
    val isFetchingElevation: Boolean = false,
    val activeLayers: Set<MapLayer> = setOf(MapLayer.ROUTE),
    // Route history (Phase 1: previous attempts)
    val routeCompletionCount: Int = 0,
    val personalBestMs: Long? = null,
    val averageTimeMs: Long? = null,
    // Segments
    val segments: List<TrackSegmentUi> = emptyList(),
    val isDetectingSegments: Boolean = false,
    val suggestedSegments: List<SegmentMatcher.OverlapCandidate> = emptyList(),
    val paceNotesStale: Boolean = false, // true when pace notes lack segment data and need regeneration
    // Sensor data
    val sensorStats: SensorStats = SensorStats(),
    val lateralGProfile: List<LateralGPoint> = emptyList(),
    val yawRateProfile: List<YawRatePoint> = emptyList(),
    val rollRateProfile: List<RollRatePoint> = emptyList(),
)

enum class MapLayer {
    ROUTE, SPEED, ACCEL, ELEVATION, CURVATURE, CALLOUTS, SURFACE
}

@HiltViewModel
class TrackDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val trackDao: TrackDao,
    private val trackPointDao: TrackPointDao,
    private val paceNoteDao: PaceNoteDao,
    private val vehicleDao: com.rallytrax.app.data.local.dao.VehicleDao,
    private val segmentRepository: SegmentRepository,
    private val valhallaRouteClient: ValhallaRouteClient,
    preferencesRepository: UserPreferencesRepository,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    @DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
) : ViewModel() {

    val preferences: StateFlow<UserPreferencesData> = preferencesRepository.preferences
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UserPreferencesData())

    private val trackId: String = checkNotNull(savedStateHandle["trackId"])

    private val _uiState = MutableStateFlow(TrackDetailUiState())
    val uiState: StateFlow<TrackDetailUiState> = _uiState.asStateFlow()

    private val _snackbarMessage = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val snackbarMessage = _snackbarMessage.asSharedFlow()

    private var cachedPoints: List<TrackPointEntity> = emptyList()

    init {
        loadTrack()
    }

    private fun loadTrack() {
        viewModelScope.launch {
            // Load core data from DB on IO dispatcher
            val track = withContext(ioDispatcher) { trackDao.getTrackById(trackId) }
            val points = withContext(ioDispatcher) { trackPointDao.getPointsForTrackOnce(trackId) }
            cachedPoints = points

            // Parallelize CPU-bound profile building on Default dispatcher
            val polylineDeferred = async(defaultDispatcher) { points.map { LatLng(it.lat, it.lon) } }
            val elevationDeferred = async(defaultDispatcher) { buildElevationProfile(points) }
            val speedDeferred = async(defaultDispatcher) { buildSpeedProfile(points) }
            val curvatureDeferred = async(defaultDispatcher) { buildCurvatureDistribution(points) }
            val sensorStatsDeferred = async(defaultDispatcher) { buildSensorStats(points) }
            val lateralGDeferred = async(defaultDispatcher) { buildLateralGProfile(points) }
            val yawRateDeferred = async(defaultDispatcher) { buildYawRateProfile(points) }
            val rollRateDeferred = async(defaultDispatcher) { buildRollRateProfile(points) }

            // Parallelize independent DB queries on IO dispatcher
            val paceNotesDeferred = async(ioDispatcher) { paceNoteDao.getNotesForTrackOnce(trackId) }
            val routeTracksDeferred = async(ioDispatcher) {
                if (track != null) trackDao.getTracksForRoute(track.name) else emptyList()
            }
            val segmentMatchesDeferred = async(ioDispatcher) {
                try { segmentRepository.detectSegmentsForTrack(trackId) }
                catch (_: Exception) { emptyList() }
            }

            val polyline = polylineDeferred.await()
            val elevationProfile = elevationDeferred.await()
            val speedProfile = speedDeferred.await()
            val curvatureDistribution = curvatureDeferred.await()
            val sensorStats = sensorStatsDeferred.await()
            val lateralGProfile = lateralGDeferred.await()
            val yawRateProfile = yawRateDeferred.await()
            val rollRateProfile = rollRateDeferred.await()

            val tags = track?.tags
                ?.split(",")
                ?.map { it.trim() }
                ?.filter { it.isNotBlank() }
                ?: emptyList()

            val paceNotes = paceNotesDeferred.await()
            val paceNotesStale = paceNotes.isNotEmpty() && paceNotes.any { it.segmentStartIndex == null }

            val routeTracks = routeTracksDeferred.await()
            val completionCount = routeTracks.size
            val personalBest = if (completionCount >= 2) {
                routeTracks.filter { it.durationMs > 0 }.minOfOrNull { it.durationMs }
            } else null
            val averageTime = if (completionCount >= 2) {
                val validTimes = routeTracks.filter { it.durationMs > 0 }.map { it.durationMs }
                if (validTimes.isNotEmpty()) validTimes.average().toLong() else null
            } else null

            val segmentMatches = segmentMatchesDeferred.await()
            val segmentUi = segmentMatches.map { match ->
                TrackSegmentUi(
                    segmentId = match.segment.id,
                    name = match.segment.name,
                    distanceMeters = match.segment.distanceMeters,
                    thisRunDurationMs = match.durationMs,
                    bestTimeMs = match.stats.bestTimeMs,
                    runCount = match.stats.runCount,
                    isFavorite = match.stats.isFavorite,
                )
            }

            _uiState.value = TrackDetailUiState(
                track = track,
                trackPoints = points,
                polylinePoints = polyline,
                elevationProfile = elevationProfile,
                speedProfile = speedProfile,
                curvatureDistribution = curvatureDistribution,
                tags = tags,
                paceNotes = paceNotes,
                paceNotesStale = paceNotesStale,
                isLoading = false,
                routeCompletionCount = completionCount,
                personalBestMs = personalBest,
                averageTimeMs = averageTime,
                segments = segmentUi,
                sensorStats = sensorStats,
                lateralGProfile = lateralGProfile,
                yawRateProfile = yawRateProfile,
                rollRateProfile = rollRateProfile,
            )
        }
    }

    fun detectNewSegments() {
        _uiState.value = _uiState.value.copy(isDetectingSegments = true)
        viewModelScope.launch {
            try {
                val candidates = withContext(ioDispatcher) {
                    segmentRepository.findNewSegmentCandidates(trackId)
                }
                _uiState.value = _uiState.value.copy(
                    isDetectingSegments = false,
                    suggestedSegments = candidates,
                )
                if (candidates.isEmpty()) {
                    _snackbarMessage.tryEmit("No new segments detected")
                } else {
                    _snackbarMessage.tryEmit("Found ${candidates.size} potential segment(s)")
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isDetectingSegments = false)
                _snackbarMessage.tryEmit("Segment detection failed: ${e.message}")
            }
        }
    }

    fun saveSegmentSuggestion(candidate: SegmentMatcher.OverlapCandidate, name: String) {
        viewModelScope.launch {
            try {
                withContext(ioDispatcher) { segmentRepository.saveOverlapAsSegment(name, candidate, trackId) }
                // Remove from suggestions and reload segments
                val remaining = _uiState.value.suggestedSegments - candidate
                _uiState.value = _uiState.value.copy(suggestedSegments = remaining)
                reloadSegments()
                _snackbarMessage.tryEmit("Segment '$name' saved")
            } catch (e: Exception) {
                _snackbarMessage.tryEmit("Failed to save segment: ${e.message}")
            }
        }
    }

    fun clearSuggestions() {
        _uiState.value = _uiState.value.copy(suggestedSegments = emptyList())
    }

    fun createUserSegment(name: String, startIndex: Int, endIndex: Int) {
        viewModelScope.launch {
            try {
                withContext(ioDispatcher) { segmentRepository.createUserSegment(name, trackId, startIndex, endIndex) }
                reloadSegments()
                _snackbarMessage.tryEmit("Segment '$name' created")
            } catch (e: Exception) {
                _snackbarMessage.tryEmit("Failed to create segment: ${e.message}")
            }
        }
    }

    private suspend fun reloadSegments() {
        val matches = try {
            withContext(ioDispatcher) { segmentRepository.detectSegmentsForTrack(trackId) }
        } catch (_: Exception) { emptyList() }

        _uiState.value = _uiState.value.copy(
            segments = matches.map { match ->
                TrackSegmentUi(
                    segmentId = match.segment.id,
                    name = match.segment.name,
                    distanceMeters = match.segment.distanceMeters,
                    thisRunDurationMs = match.durationMs,
                    bestTimeMs = match.stats.bestTimeMs,
                    runCount = match.stats.runCount,
                    isFavorite = match.stats.isFavorite,
                )
            },
        )
    }

    fun toggleLayer(layer: MapLayer) {
        val current = _uiState.value.activeLayers.toMutableSet()
        if (layer == MapLayer.ROUTE) return // Route is always on
        if (layer in current) current.remove(layer) else current.add(layer)
        _uiState.value = _uiState.value.copy(activeLayers = current)
    }

    private fun buildElevationProfile(points: List<TrackPointEntity>): List<ElevationPoint> {
        val result = mutableListOf<ElevationPoint>()
        var cumulativeDistance = 0.0
        var prevLat: Double? = null
        var prevLon: Double? = null

        for (point in points) {
            if (point.elevation == null) continue
            if (prevLat != null && prevLon != null) {
                cumulativeDistance += haversine(prevLat, prevLon, point.lat, point.lon)
            }
            prevLat = point.lat
            prevLon = point.lon
            result.add(ElevationPoint(cumulativeDistance, point.elevation))
        }
        return result
    }

    private fun buildSpeedProfile(points: List<TrackPointEntity>): List<SpeedPoint> {
        val result = mutableListOf<SpeedPoint>()
        var cumulativeDistance = 0.0
        var prevLat: Double? = null
        var prevLon: Double? = null

        for (point in points) {
            val speed = point.speed ?: continue
            if (prevLat != null && prevLon != null) {
                cumulativeDistance += haversine(prevLat, prevLon, point.lat, point.lon)
            }
            prevLat = point.lat
            prevLon = point.lon
            result.add(SpeedPoint(cumulativeDistance, speed))
        }
        return result
    }

    private fun buildCurvatureDistribution(points: List<TrackPointEntity>): CurvatureDistribution {
        val withCurvature = points.filter { it.curvatureDegPerM != null }
        if (withCurvature.isEmpty()) return CurvatureDistribution()
        val total = withCurvature.size.toFloat()

        var straight = 0; var gentle = 0; var moderate = 0; var tight = 0; var hairpin = 0
        for (pt in withCurvature) {
            val c = abs(pt.curvatureDegPerM!!)
            when {
                c < 0.5 -> straight++
                c < 2.0 -> gentle++
                c < 5.0 -> moderate++
                c < 10.0 -> tight++
                else -> hairpin++
            }
        }
        return CurvatureDistribution(
            straight = straight / total,
            gentle = gentle / total,
            moderate = moderate / total,
            tight = tight / total,
            hairpin = hairpin / total,
        )
    }

    private fun buildSensorStats(points: List<TrackPointEntity>): SensorStats {
        val lateralAccels = points.mapNotNull { it.lateralAccelMps2 }
        val verticalAccels = points.mapNotNull { it.verticalAccelMps2 }
        val yawRates = points.mapNotNull { it.yawRateDegPerS }
        val rollRates = points.mapNotNull { it.rollRateDegPerS }
        val hasSensorData = lateralAccels.isNotEmpty() || verticalAccels.isNotEmpty()

        return SensorStats(
            peakLateralG = if (lateralAccels.isNotEmpty()) {
                lateralAccels.maxOfOrNull { abs(it) }?.let { it / 9.81 }
            } else null,
            peakVerticalG = if (verticalAccels.isNotEmpty()) {
                verticalAccels.maxOfOrNull { abs(it) }?.let { it / 9.81 }
            } else null,
            maxYawRateDegPerS = if (yawRates.isNotEmpty()) {
                yawRates.maxOfOrNull { abs(it) }
            } else null,
            maxRollRateDegPerS = if (rollRates.isNotEmpty()) {
                rollRates.maxOfOrNull { abs(it) }
            } else null,
            hasSensorData = hasSensorData,
        )
    }

    private fun buildLateralGProfile(points: List<TrackPointEntity>): List<LateralGPoint> {
        val result = mutableListOf<LateralGPoint>()
        var cumulativeDistance = 0.0
        var prevLat: Double? = null
        var prevLon: Double? = null

        for (point in points) {
            val lateral = point.lateralAccelMps2 ?: continue
            if (prevLat != null && prevLon != null) {
                cumulativeDistance += haversine(prevLat, prevLon, point.lat, point.lon)
            }
            prevLat = point.lat
            prevLon = point.lon
            result.add(LateralGPoint(cumulativeDistance, abs(lateral) / 9.81))
        }
        return result
    }

    private fun buildYawRateProfile(points: List<TrackPointEntity>): List<YawRatePoint> {
        val result = mutableListOf<YawRatePoint>()
        var cumulativeDistance = 0.0
        var prevLat: Double? = null
        var prevLon: Double? = null

        for (point in points) {
            val yaw = point.yawRateDegPerS ?: continue
            if (prevLat != null && prevLon != null) {
                cumulativeDistance += haversine(prevLat, prevLon, point.lat, point.lon)
            }
            prevLat = point.lat
            prevLon = point.lon
            result.add(YawRatePoint(cumulativeDistance, abs(yaw)))
        }
        return result
    }

    private fun buildRollRateProfile(points: List<TrackPointEntity>): List<RollRatePoint> {
        val result = mutableListOf<RollRatePoint>()
        var cumulativeDistance = 0.0
        var prevLat: Double? = null
        var prevLon: Double? = null

        for (point in points) {
            val roll = point.rollRateDegPerS ?: continue
            if (prevLat != null && prevLon != null) {
                cumulativeDistance += haversine(prevLat, prevLon, point.lat, point.lon)
            }
            prevLat = point.lat
            prevLon = point.lon
            result.add(RollRatePoint(cumulativeDistance, abs(roll)))
        }
        return result
    }

    fun regeneratePaceNotes() {
        val points = cachedPoints
        if (points.isEmpty()) {
            _snackbarMessage.tryEmit("No track points available")
            return
        }

        _uiState.value = _uiState.value.copy(isGeneratingNotes = true)

        viewModelScope.launch {
            try {
                val prefs = preferences.value
                val hasSpeedData = points.any { (it.speed ?: 0.0) > 0.0 }

                val notes = withContext(defaultDispatcher) {
                    PaceNoteGenerator.generate(
                        trackId = trackId,
                        points = points,
                        halfStepEnabled = prefs.halfStepSeverityEnabled,
                        useSpeedCalibration = hasSpeedData,
                    )
                }

                withContext(ioDispatcher) {
                    paceNoteDao.deleteNotesForTrack(trackId)
                    if (notes.isNotEmpty()) {
                        paceNoteDao.insertNotes(notes)
                    }
                }

                _uiState.value = _uiState.value.copy(
                    paceNotes = notes,
                    isGeneratingNotes = false,
                    paceNotesStale = false,
                )

                _snackbarMessage.tryEmit("Generated ${notes.size} pace notes")
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isGeneratingNotes = false)
                _snackbarMessage.tryEmit("Failed to generate pace notes: ${e.message}")
            }
        }
    }

    fun updateTrackName(name: String) {
        val track = _uiState.value.track ?: return
        val trimmed = name.trim()
        if (trimmed.isBlank() || trimmed == track.name) return
        val updated = track.copy(name = trimmed)
        viewModelScope.launch {
            withContext(ioDispatcher) { trackDao.updateTrack(updated) }
            _uiState.value = _uiState.value.copy(track = updated)
        }
    }

    fun addTag(tag: String) {
        val cleanTag = tag.trim()
        if (cleanTag.isBlank()) return
        val track = _uiState.value.track ?: return
        val currentTags = _uiState.value.tags
        if (cleanTag in currentTags) return

        val newTags = currentTags + cleanTag
        val newTagString = newTags.joinToString(",")
        val updatedTrack = track.copy(tags = newTagString)

        viewModelScope.launch {
            withContext(ioDispatcher) { trackDao.updateTrack(updatedTrack) }
            _uiState.value = _uiState.value.copy(
                track = updatedTrack,
                tags = newTags,
            )
        }
    }

    fun removeTag(tag: String) {
        val track = _uiState.value.track ?: return
        val newTags = _uiState.value.tags - tag
        val newTagString = newTags.joinToString(",")
        val updatedTrack = track.copy(tags = newTagString)

        viewModelScope.launch {
            withContext(ioDispatcher) { trackDao.updateTrack(updatedTrack) }
            _uiState.value = _uiState.value.copy(
                track = updatedTrack,
                tags = newTags,
            )
        }
    }

    fun deleteTrack(onDeleted: () -> Unit) {
        viewModelScope.launch {
            withContext(ioDispatcher) { trackDao.deleteTrack(trackId) }
            onDeleted()
        }
    }

    fun exportGpx(context: Context) {
        val track = _uiState.value.track ?: return
        val points = cachedPoints
        if (points.isEmpty()) {
            _snackbarMessage.tryEmit("No track data to export")
            return
        }

        viewModelScope.launch {
            try {
                val fileName = "${track.name.replace(Regex("[^a-zA-Z0-9_ -]"), "_")}.gpx"
                val paceNotes = withContext(ioDispatcher) { paceNoteDao.getNotesForTrackOnce(trackId) }

                val contentValues = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                    put(MediaStore.Downloads.MIME_TYPE, "application/gpx+xml")
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }

                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                if (uri == null) {
                    _snackbarMessage.tryEmit("Failed to create file")
                    return@launch
                }

                withContext(ioDispatcher) {
                    resolver.openOutputStream(uri)?.use { outputStream ->
                        GpxExporter.export(track, points, outputStream, paceNotes)
                    }
                }

                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/gpx+xml"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                val chooser = Intent.createChooser(shareIntent, "Share GPX track")
                chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(chooser)

                _snackbarMessage.tryEmit("Exported to Downloads: $fileName")
            } catch (e: Exception) {
                _snackbarMessage.tryEmit("Export failed: ${e.message}")
            }
        }
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

    // ── Fetch elevation from Valhalla ─────────────────────────────────────

    fun fetchElevation() {
        val points = cachedPoints
        if (points.isEmpty()) {
            _snackbarMessage.tryEmit("No track points available")
            return
        }

        _uiState.value = _uiState.value.copy(isFetchingElevation = true)

        viewModelScope.launch {
            try {
                val latLngs = points.map { LatLng(it.lat, it.lon) }
                val withElevation = withContext(ioDispatcher) {
                    valhallaRouteClient.fetchHeight(latLngs)
                }

                // Check if we actually got elevation data
                val hasElevation = withElevation.any { it.elevation != null }
                if (!hasElevation) {
                    _uiState.value = _uiState.value.copy(isFetchingElevation = false)
                    _snackbarMessage.tryEmit("Could not fetch elevation data")
                    return@launch
                }

                // Update track points with elevation
                val updatedPoints = points.mapIndexed { i, pt ->
                    pt.copy(elevation = withElevation[i].elevation ?: pt.elevation)
                }
                withContext(ioDispatcher) {
                    updatedPoints.chunked(1000).forEach { chunk ->
                        trackPointDao.insertPoints(chunk)
                    }
                }
                cachedPoints = updatedPoints

                // Compute cumulative elevation gain
                var elevationGain = 0.0
                var prevEle: Double? = null
                for (pt in updatedPoints) {
                    val ele = pt.elevation ?: continue
                    prevEle?.let { prev ->
                        val delta = ele - prev
                        if (delta > 2.0) elevationGain += delta
                    }
                    prevEle = ele
                }

                // Update track entity
                val track = _uiState.value.track
                if (track != null) {
                    val updatedTrack = track.copy(elevationGainM = elevationGain)
                    withContext(ioDispatcher) { trackDao.updateTrack(updatedTrack) }
                    _uiState.value = _uiState.value.copy(
                        track = updatedTrack,
                        trackPoints = updatedPoints,
                        elevationProfile = buildElevationProfile(updatedPoints),
                        isFetchingElevation = false,
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        trackPoints = updatedPoints,
                        elevationProfile = buildElevationProfile(updatedPoints),
                        isFetchingElevation = false,
                    )
                }

                _snackbarMessage.tryEmit("Elevation updated (${elevationGain.toInt()}m gain)")
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isFetchingElevation = false)
                _snackbarMessage.tryEmit("Failed to fetch elevation: ${e.message}")
            }
        }
    }

    // ── Vehicle assignment ──────────────────────────────────────────────────

    val allVehicles = vehicleDao.getAllVehicles()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun assignVehicle(vehicleId: String) {
        val track = _uiState.value.track ?: return
        viewModelScope.launch {
            val updated = track.copy(vehicleId = vehicleId)
            withContext(ioDispatcher) { trackDao.updateTrack(updated) }
            _uiState.value = _uiState.value.copy(track = updated)
            _snackbarMessage.tryEmit("Vehicle assigned")
        }
    }

    fun clearVehicle() {
        val track = _uiState.value.track ?: return
        viewModelScope.launch {
            val updated = track.copy(vehicleId = null)
            withContext(ioDispatcher) { trackDao.updateTrack(updated) }
            _uiState.value = _uiState.value.copy(track = updated)
            _snackbarMessage.tryEmit("Vehicle removed")
        }
    }
}

package com.rallytrax.app.ui.trackdetail

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rallytrax.app.data.gpx.GpxExporter
import com.rallytrax.app.data.local.dao.PaceNoteDao
import com.rallytrax.app.data.local.dao.TrackDao
import com.rallytrax.app.data.local.dao.TrackPointDao
import com.rallytrax.app.data.local.dao.WeatherDao
import com.rallytrax.app.data.local.entity.NoteType
import com.rallytrax.app.data.local.entity.PaceNoteEntity
import com.rallytrax.app.data.local.entity.TrackEntity
import com.rallytrax.app.data.local.entity.TrackPointEntity
import com.rallytrax.app.data.local.entity.WeatherEntity
import com.rallytrax.app.data.analytics.ConsistencyAnalyzer
import com.rallytrax.app.data.analytics.CrossSensorAnalytics
import com.rallytrax.app.data.analytics.GripEventDetector
import com.rallytrax.app.data.classification.ValhallaRouteClient
import com.rallytrax.app.data.local.dao.SegmentDao
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
import com.rallytrax.app.util.formatDate
import com.rallytrax.app.util.formatDistance
import com.rallytrax.app.util.formatElapsedTime
import com.rallytrax.app.util.formatSpeed
import java.io.File
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

data class AccelPoint(
    val distanceFromStart: Double,
    val accelMps2: Double,
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
    val recentRunTimesMs: List<Long> = emptyList(),
    val consistencyScore: Int? = null,
    val latestDeltaFromBest: Long? = null,
)

data class CornerAnalysis(
    val note: PaceNoteEntity,
    val entrySpeedMps: Double?,
    val minSpeedMps: Double?,
    val exitSpeedMps: Double?,
    val peakLateralG: Double?,
    val entryAccelMps2: Double?,   // negative = braking
    val exitAccelMps2: Double?,    // positive = accelerating
    val tip: String?,              // actionable improvement suggestion
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
    // Highlighted suggested segment (eye toggle — only one at a time)
    val highlightedSuggestionIndex: Int? = null,
    // Weather context
    val weatherCondition: WeatherEntity? = null,
    // Sensor data
    val sensorStats: SensorStats = SensorStats(),
    val lateralGProfile: List<LateralGPoint> = emptyList(),
    val yawRateProfile: List<YawRatePoint> = emptyList(),
    val rollRateProfile: List<RollRatePoint> = emptyList(),
    // Driving insights (cross-sensor analytics)
    val smoothnessScore: Int? = null,
    val brakingEfficiencyScore: Int? = null,
    val peakCorneringG: Double? = null,
    val avgCorneringG: Double? = null,
    val roadRoughnessIndex: Double? = null,
    val elevationAdjustedAvgSpeedMps: Double? = null,
    // Corner analysis
    val cornerAnalysis: List<CornerAnalysis> = emptyList(),
    val accelProfile: List<AccelPoint> = emptyList(),
    // Grip event detection
    val gripEvents: List<GripEventDetector.GripEvent> = emptyList(),
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
    private val weatherDao: WeatherDao,
    private val vehicleDao: com.rallytrax.app.data.local.dao.VehicleDao,
    private val segmentRepository: SegmentRepository,
    private val segmentDao: SegmentDao,
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
            val accelDeferred = async(defaultDispatcher) { buildAccelProfile(points) }
            val gripEventsDeferred = async(defaultDispatcher) { GripEventDetector.detect(points) }

            // Parallelize independent DB queries on IO dispatcher
            val weatherDeferred = async(ioDispatcher) { weatherDao.getWeatherForTrack(trackId) }
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
            val accelProfile = accelDeferred.await()

            val tags = track?.tags
                ?.split(",")
                ?.map { it.trim() }
                ?.filter { it.isNotBlank() }
                ?: emptyList()

            val paceNotes = paceNotesDeferred.await()
            val paceNotesStale = paceNotes.isNotEmpty() && paceNotes.any { it.segmentStartIndex == null }

            val cornerAnalysisDeferred = async(defaultDispatcher) { buildCornerAnalysis(points, paceNotes) }

            val routeTracks = routeTracksDeferred.await()
            val completionCount = routeTracks.size
            val personalBest = if (completionCount >= 2) {
                routeTracks.filter { it.durationMs > 0 }.minOfOrNull { it.durationMs }
            } else null
            val averageTime = if (completionCount >= 2) {
                val validTimes = routeTracks.filter { it.durationMs > 0 }.map { it.durationMs }
                if (validTimes.isNotEmpty()) validTimes.average().toLong() else null
            } else null

            val cornerAnalysis = cornerAnalysisDeferred.await()
            val segmentMatches = segmentMatchesDeferred.await()
            val segmentUi = withContext(ioDispatcher) {
                segmentMatches.map { match -> enrichSegmentUi(match) }
            }

            // Driving insights: read from track entity, or compute if missing
            var smoothness = track?.smoothnessScore
            var brakingEff = track?.brakingEfficiencyScore
            var peakCornG = track?.peakCorneringG
            var avgCornG = track?.avgCorneringG
            var roughness = track?.roadRoughnessIndex
            var elevAdjSpeed = track?.elevationAdjustedAvgSpeedMps

            val allNull = smoothness == null && brakingEff == null && peakCornG == null
                && avgCornG == null && roughness == null && elevAdjSpeed == null
            if (allNull && sensorStats.hasSensorData && track != null) {
                val insights = withContext(defaultDispatcher) {
                    CrossSensorAnalytics.analyze(points)
                }
                smoothness = insights.smoothnessScore
                brakingEff = insights.brakingEfficiency?.score
                peakCornG = insights.corneringG?.peakLateralG
                avgCornG = insights.corneringG?.avgLateralG
                roughness = insights.roadRoughnessIndex
                elevAdjSpeed = insights.elevationAdjustedAvgSpeed
                withContext(ioDispatcher) {
                    trackDao.updateInsights(
                        trackId = trackId,
                        peakCorneringG = peakCornG,
                        avgCorneringG = avgCornG,
                        smoothnessScore = smoothness,
                        roadRoughnessIndex = roughness,
                        brakingEfficiencyScore = brakingEff,
                        elevationAdjustedAvgSpeedMps = elevAdjSpeed,
                    )
                }
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
                weatherCondition = weatherDeferred.await(),
                sensorStats = sensorStats,
                lateralGProfile = lateralGProfile,
                yawRateProfile = yawRateProfile,
                rollRateProfile = rollRateProfile,
                smoothnessScore = smoothness,
                brakingEfficiencyScore = brakingEff,
                peakCorneringG = peakCornG,
                avgCorneringG = avgCornG,
                roadRoughnessIndex = roughness,
                elevationAdjustedAvgSpeedMps = elevAdjSpeed,
                cornerAnalysis = cornerAnalysis,
                accelProfile = accelProfile,
                gripEvents = gripEventsDeferred.await(),
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
                _uiState.value = _uiState.value.copy(suggestedSegments = remaining, highlightedSuggestionIndex = null)
                reloadSegments()
                _snackbarMessage.tryEmit("Segment '$name' saved")
            } catch (e: Exception) {
                _snackbarMessage.tryEmit("Failed to save segment: ${e.message}")
            }
        }
    }

    fun clearSuggestions() {
        _uiState.value = _uiState.value.copy(suggestedSegments = emptyList(), highlightedSuggestionIndex = null)
    }

    fun toggleHighlightedSuggestion(index: Int) {
        val current = _uiState.value.highlightedSuggestionIndex
        _uiState.value = _uiState.value.copy(
            highlightedSuggestionIndex = if (current == index) null else index,
        )
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

        val enriched = withContext(ioDispatcher) {
            matches.map { match -> enrichSegmentUi(match) }
        }
        _uiState.value = _uiState.value.copy(segments = enriched)
    }

    private suspend fun enrichSegmentUi(match: TrackSegmentMatch): TrackSegmentUi {
        val runs = segmentDao.getRunsForSegmentOnce(match.segment.id)
        val chronologicalRuns = runs.sortedBy { it.timestamp }
        val recentRunTimesMs = chronologicalRuns.map { it.durationMs }
        val consistencyResult = ConsistencyAnalyzer.analyze(chronologicalRuns)
        val latestDelta = match.stats.bestTimeMs?.let { best ->
            match.durationMs - best
        }

        return TrackSegmentUi(
            segmentId = match.segment.id,
            name = match.segment.name,
            distanceMeters = match.segment.distanceMeters,
            thisRunDurationMs = match.durationMs,
            bestTimeMs = match.stats.bestTimeMs,
            runCount = match.stats.runCount,
            isFavorite = match.stats.isFavorite,
            recentRunTimesMs = recentRunTimesMs,
            consistencyScore = consistencyResult?.overallScore,
            latestDeltaFromBest = latestDelta,
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

    private fun buildCornerAnalysis(
        points: List<TrackPointEntity>,
        paceNotes: List<PaceNoteEntity>,
    ): List<CornerAnalysis> {
        if (points.isEmpty() || paceNotes.isEmpty()) return emptyList()

        return paceNotes
            .filter { it.noteType != NoteType.STRAIGHT && it.segmentStartIndex != null && it.segmentEndIndex != null }
            .mapNotNull { note ->
                val start = note.segmentStartIndex!!
                val end = note.segmentEndIndex!!
                if (start < 0 || end >= points.size || start >= end) return@mapNotNull null
                val segment = points.subList(start, end + 1)
                if (segment.size < 3) return@mapNotNull null

                val first3 = segment.take(3)
                val last3 = segment.takeLast(3)

                val entrySpeed = first3.averageOfOrNull { it.speed }
                val exitSpeed = last3.averageOfOrNull { it.speed }
                val minSpeed = segment.mapNotNull { it.speed }.minOrNull()

                val peakLateralG = segment.mapNotNull { it.lateralAccelMps2 }
                    .maxOfOrNull { abs(it) }
                    ?.let { it / 9.81 }

                val entryAccel = first3.averageOfOrNull { it.accelMps2 }
                val exitAccel = last3.averageOfOrNull { it.accelMps2 }

                val tip = generateCornerTip(entrySpeed, minSpeed, exitSpeed, peakLateralG, entryAccel, exitAccel)

                CornerAnalysis(
                    note = note,
                    entrySpeedMps = entrySpeed,
                    minSpeedMps = minSpeed,
                    exitSpeedMps = exitSpeed,
                    peakLateralG = peakLateralG,
                    entryAccelMps2 = entryAccel,
                    exitAccelMps2 = exitAccel,
                    tip = tip,
                )
            }
    }

    private fun generateCornerTip(
        entrySpeed: Double?,
        minSpeed: Double?,
        exitSpeed: Double?,
        peakLateralG: Double?,
        entryAccel: Double?,
        exitAccel: Double?,
    ): String? {
        if (entryAccel != null && entryAccel < -3.0) {
            return "Heavy braking on entry \u2014 try earlier, lighter braking"
        }
        if (entryAccel != null && entryAccel < -1.5 && exitAccel != null && exitAccel > 1.0) {
            return "Good trail braking technique"
        }
        if (minSpeed != null && entrySpeed != null && entrySpeed > 0 && minSpeed < entrySpeed * 0.3) {
            return "Large speed loss \u2014 carry more speed through"
        }
        if (exitAccel != null && exitAccel < 0.5 && exitSpeed != null && entrySpeed != null && entrySpeed > 0 && exitSpeed < entrySpeed * 0.8) {
            return "Slow exit \u2014 get on throttle earlier"
        }
        if (peakLateralG != null && peakLateralG > 0.5) {
            return "High cornering forces \u2014 smooth inputs recommended"
        }
        return null
    }

    /** Average of non-null values extracted by [selector], or null if none. */
    private inline fun <T> List<T>.averageOfOrNull(selector: (T) -> Double?): Double? {
        val values = mapNotNull(selector)
        return if (values.isNotEmpty()) values.average() else null
    }

    private fun buildAccelProfile(points: List<TrackPointEntity>): List<AccelPoint> {
        val result = mutableListOf<AccelPoint>()
        var cumulativeDistance = 0.0
        var prevLat: Double? = null
        var prevLon: Double? = null
        for (point in points) {
            val accel = point.accelMps2 ?: continue
            if (prevLat != null && prevLon != null) {
                cumulativeDistance += haversine(prevLat, prevLon, point.lat, point.lon)
            }
            prevLat = point.lat
            prevLon = point.lon
            result.add(AccelPoint(cumulativeDistance, accel))
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

    fun updateTrackDescription(description: String) {
        val track = _uiState.value.track ?: return
        val newDescription = description.trim().ifBlank { null }
        if (newDescription == track.description) return
        val updated = track.copy(description = newDescription)
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

    // ── Share Activity ─────────────────────────────────────────────────────

    fun shareActivity(context: Context) {
        val track = _uiState.value.track ?: return
        viewModelScope.launch {
            try {
                val unitSystem = preferences.value.unitSystem
                val bitmap = withContext(defaultDispatcher) {
                    generateShareBitmap(track, unitSystem)
                }
                val file = File(context.cacheDir, "share_activity.png")
                withContext(ioDispatcher) {
                    file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                }
                bitmap.recycle()
                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file,
                )
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "image/png"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(
                        Intent.EXTRA_TEXT,
                        "${track.name} — ${formatDistance(track.distanceMeters, unitSystem)} in ${formatElapsedTime(track.durationMs)}",
                    )
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                val chooser = Intent.createChooser(shareIntent, "Share Activity")
                chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(chooser)
            } catch (e: Exception) {
                _snackbarMessage.tryEmit("Share failed: ${e.message}")
            }
        }
    }

    private fun generateShareBitmap(
        track: TrackEntity,
        unitSystem: com.rallytrax.app.data.preferences.UnitSystem,
    ): Bitmap {
        val width = 1080
        val height = 1350
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Colors
        val bgColor = 0xFFF8F9FA.toInt()
        val cardColor = 0xFFFFFFFF.toInt()
        val primaryColor = 0xFF1A73E8.toInt()
        val textPrimary = 0xFF1F1F1F.toInt()
        val textSecondary = 0xFF5F6368.toInt()
        val dividerColor = 0xFFE0E0E0.toInt()

        // Background
        val bgPaint = Paint().apply { color = bgColor; style = Paint.Style.FILL }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

        // Card background with rounded corners
        val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = cardColor
            style = Paint.Style.FILL
            setShadowLayer(8f, 0f, 4f, 0x33000000)
        }
        val cardRect = RectF(48f, 48f, (width - 48).toFloat(), (height - 48).toFloat())
        canvas.drawRoundRect(cardRect, 32f, 32f, cardPaint)

        // Branding
        val brandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = primaryColor
            textSize = 36f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("RallyTrax", width / 2f, 130f, brandPaint)

        // Divider under branding
        val dividerPaint = Paint().apply { color = dividerColor; strokeWidth = 2f }
        canvas.drawLine(96f, 160f, (width - 96).toFloat(), 160f, dividerPaint)

        // Track name
        val namePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = textPrimary
            textSize = 56f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(track.name, width / 2f, 240f, namePaint)

        // Date
        val datePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = textSecondary
            textSize = 32f
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(formatDate(track.recordedAt), width / 2f, 290f, datePaint)

        // Difficulty badge
        var statsTop = 360f
        val difficulty = track.difficultyRating
        if (!difficulty.isNullOrBlank()) {
            val badgeColor = when (difficulty.lowercase()) {
                "easy" -> 0xFF34A853.toInt()
                "moderate" -> 0xFFFBBC04.toInt()
                "hard" -> 0xFFE8710A.toInt()
                "expert" -> 0xFFEA4335.toInt()
                else -> primaryColor
            }
            val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = badgeColor
                style = Paint.Style.FILL
            }
            val badgeText = difficulty.replaceFirstChar { it.uppercase() }
            val badgeTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFFFFFFFF.toInt()
                textSize = 28f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textAlign = Paint.Align.CENTER
            }
            val badgeWidth = badgeTextPaint.measureText(badgeText) + 48f
            val badgeRect = RectF(
                (width - badgeWidth) / 2f, 320f,
                (width + badgeWidth) / 2f, 358f,
            )
            canvas.drawRoundRect(badgeRect, 19f, 19f, badgePaint)
            canvas.drawText(badgeText, width / 2f, 348f, badgeTextPaint)
            statsTop = 400f
        }

        // Stats grid (2 columns, 3 rows)
        data class Stat(val label: String, val value: String)

        val stats = mutableListOf(
            Stat("Distance", formatDistance(track.distanceMeters, unitSystem)),
            Stat("Duration", formatElapsedTime(track.durationMs)),
            Stat("Avg Speed", formatSpeed(track.avgSpeedMps, unitSystem)),
            Stat("Max Speed", formatSpeed(track.maxSpeedMps, unitSystem)),
        )
        if (track.elevationGainM > 0) {
            stats.add(Stat("Elevation Gain", "${track.elevationGainM.toInt()}m"))
        }

        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = textSecondary
            textSize = 28f
            textAlign = Paint.Align.CENTER
        }
        val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = textPrimary
            textSize = 48f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }

        val colWidth = (width - 96) / 2f
        val rowHeight = 160f
        val statCardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFF1F3F4.toInt()
            style = Paint.Style.FILL
        }

        stats.forEachIndexed { index, stat ->
            val col = index % 2
            val row = index / 2
            val cx = 96f + colWidth * col + colWidth / 2f
            val cy = statsTop + rowHeight * row

            val statRect = RectF(
                cx - colWidth / 2f + 16f, cy,
                cx + colWidth / 2f - 16f, cy + rowHeight - 20f,
            )
            canvas.drawRoundRect(statRect, 16f, 16f, statCardPaint)

            canvas.drawText(stat.value, cx, cy + 70f, valuePaint)
            canvas.drawText(stat.label, cx, cy + 110f, labelPaint)
        }

        // Footer branding
        val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = textSecondary
            textSize = 24f
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("Recorded with RallyTrax", width / 2f, (height - 80).toFloat(), footerPaint)

        return bitmap
    }
}

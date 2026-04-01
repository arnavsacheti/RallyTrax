package com.rallytrax.app.ui.recording

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.rallytrax.app.data.analytics.GripEventDetector
import com.rallytrax.app.data.ThumbnailGenerator
import com.rallytrax.app.data.achievements.AchievementTracker
import com.rallytrax.app.data.classification.RouteClassifier
import com.rallytrax.app.data.classification.SurfaceFusion
import com.rallytrax.app.data.classification.ValhallaSurfaceClient
import com.rallytrax.app.data.local.dao.TrackDao
import com.rallytrax.app.data.local.dao.TrackPointDao
import com.rallytrax.app.data.local.dao.VehicleDao
import com.rallytrax.app.data.local.entity.AchievementEntity
import com.rallytrax.app.data.local.entity.TrackEntity
import com.rallytrax.app.data.local.entity.VehicleEntity
import com.rallytrax.app.data.preferences.UserPreferencesData
import com.rallytrax.app.data.preferences.UserPreferencesRepository
import com.rallytrax.app.data.social.SocialRepository
import com.rallytrax.app.data.social.toSharedTrack
import com.rallytrax.app.ui.components.generateShareBitmap
import com.rallytrax.app.util.formatDistance
import com.rallytrax.app.util.formatElapsedTime
import com.rallytrax.app.di.DefaultDispatcher
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
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

data class SensorStats(
    val peakLateralG: Double? = null, // peak lateral acceleration in G
    val peakVerticalG: Double? = null, // peak vertical acceleration in G
    val maxYawRateDegPerS: Double? = null, // max yaw rate
    val maxRollRateDegPerS: Double? = null, // max roll rate
    val hasSensorData: Boolean = false,
)

data class ActivitySummaryState(
    val track: TrackEntity? = null,
    val classification: RouteClassifier.ClassificationResult? = null,
    val activeVehicle: VehicleEntity? = null,
    val editedName: String = "",
    val editedDescription: String = "",
    val selectedRouteType: String = "",
    val selectedDifficulty: String = "",
    val selectedActivityTags: Set<String> = emptySet(),
    val selectedVehicleId: String? = null,
    val isSaving: Boolean = false,
    val newlyUnlockedAchievements: List<AchievementEntity> = emptyList(),
    val sensorStats: SensorStats = SensorStats(),
    val gripEventCount: Int = 0,
    val isPersonalRecord: Boolean = false,
    val prDeltaMs: Long? = null, // negative = improvement (faster)
)

@HiltViewModel
class ActivitySummaryViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val application: Application,
    private val trackDao: TrackDao,
    private val trackPointDao: TrackPointDao,
    private val vehicleDao: VehicleDao,
    private val achievementTracker: AchievementTracker,
    private val valhallaSurfaceClient: ValhallaSurfaceClient,
    private val socialRepository: SocialRepository,
    private val auth: FirebaseAuth,
    preferencesRepository: UserPreferencesRepository,
    @DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
) : ViewModel() {

    private val trackId: String = savedStateHandle["trackId"] ?: ""

    val preferences: StateFlow<UserPreferencesData> = preferencesRepository.preferences
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UserPreferencesData())

    private val _state = MutableStateFlow(ActivitySummaryState())
    val state: StateFlow<ActivitySummaryState> = _state.asStateFlow()

    private val _navigateToDetail = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val navigateToDetail = _navigateToDetail.asSharedFlow()

    private val _navigateBack = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val navigateBack = _navigateBack.asSharedFlow()

    init {
        viewModelScope.launch {
            loadTrackData()
        }
    }

    private suspend fun loadTrackData() {
        val track = trackDao.getTrackById(trackId) ?: return
        val activeVehicle = vehicleDao.getActiveVehicle()

        // Run classification
        val points = trackPointDao.getPointsForTrackOnce(trackId)
        val classification = if (points.size >= 10) {
            RouteClassifier.classify(points)
        } else null

        // Run Valhalla surface detection in background (non-blocking)
        if (points.size >= 10) {
            viewModelScope.launch {
                try {
                    val mapSurfaces = valhallaSurfaceClient.getTraceAttributes(points)
                    if (mapSurfaces.isNotEmpty()) {
                        val classifiedPoints = SurfaceFusion.applyClassifications(points, mapSurfaces)
                        trackPointDao.insertPoints(classifiedPoints)
                        val breakdown = SurfaceFusion.computeSurfaceBreakdown(classifiedPoints)
                        val primary = SurfaceFusion.primarySurface(classifiedPoints)
                        val currentTrack = trackDao.getTrackById(trackId)
                        if (currentTrack != null) {
                            trackDao.updateTrack(
                                currentTrack.copy(
                                    primarySurface = primary,
                                    surfaceBreakdown = breakdown,
                                )
                            )
                        }
                    }
                } catch (e: Exception) {
                    Log.w("ActivitySummaryVM", "Surface detection failed", e)
                }
            }
        }

        // Compute sensor stats
        val lateralAccels = points.mapNotNull { it.lateralAccelMps2 }
        val verticalAccels = points.mapNotNull { it.verticalAccelMps2 }
        val yawRates = points.mapNotNull { it.yawRateDegPerS }
        val rollRates = points.mapNotNull { it.rollRateDegPerS }
        val hasSensorData = lateralAccels.isNotEmpty() || verticalAccels.isNotEmpty()

        val sensorStats = SensorStats(
            peakLateralG = if (lateralAccels.isNotEmpty()) {
                lateralAccels.maxOfOrNull { kotlin.math.abs(it) }?.let { it / 9.81 }
            } else null,
            peakVerticalG = if (verticalAccels.isNotEmpty()) {
                verticalAccels.maxOfOrNull { kotlin.math.abs(it) }?.let { it / 9.81 }
            } else null,
            maxYawRateDegPerS = if (yawRates.isNotEmpty()) {
                yawRates.maxOfOrNull { kotlin.math.abs(it) }
            } else null,
            maxRollRateDegPerS = if (rollRates.isNotEmpty()) {
                rollRates.maxOfOrNull { kotlin.math.abs(it) }
            } else null,
            hasSensorData = hasSensorData,
        )

        // Grip event detection
        val gripEvents = withContext(defaultDispatcher) { GripEventDetector.detect(points) }
        val gripEventCount = gripEvents.size
        if (gripEvents.isNotEmpty()) {
            val summary = gripEvents.joinToString(";") { "${it.type}:${it.severity}:${it.pointIndex}" }
            trackDao.updateGripEvents(track.id, gripEventCount, summary)
        }

        // Check for personal record
        val otherTracks = trackDao.getTracksForRoute(track.name).filter { it.id != track.id }
        val previousBestMs = otherTracks
            .filter { it.durationMs > 0 }
            .minOfOrNull { it.durationMs }
        val isNewPR = previousBestMs != null && track.durationMs > 0 && track.durationMs < previousBestMs

        _state.value = ActivitySummaryState(
            track = track,
            classification = classification,
            activeVehicle = activeVehicle,
            editedName = track.name,
            editedDescription = track.description ?: "",
            selectedRouteType = classification?.suggestedRouteType ?: "",
            selectedDifficulty = classification?.difficultyRating ?: "",
            selectedVehicleId = track.vehicleId ?: activeVehicle?.id,
            sensorStats = sensorStats,
            gripEventCount = gripEventCount,
            isPersonalRecord = isNewPR,
            prDeltaMs = if (isNewPR) track.durationMs - previousBestMs!! else null,
        )
    }

    fun updateName(name: String) {
        _state.value = _state.value.copy(editedName = name)
    }

    fun updateDescription(desc: String) {
        _state.value = _state.value.copy(editedDescription = desc)
    }

    fun updateRouteType(routeType: String) {
        _state.value = _state.value.copy(selectedRouteType = routeType)
    }

    fun updateDifficulty(difficulty: String) {
        _state.value = _state.value.copy(selectedDifficulty = difficulty)
    }

    fun toggleActivityTag(tag: String) {
        val current = _state.value.selectedActivityTags
        _state.value = _state.value.copy(
            selectedActivityTags = if (tag in current) current - tag else current + tag,
        )
    }

    fun updateVehicle(vehicleId: String?) {
        _state.value = _state.value.copy(selectedVehicleId = vehicleId)
    }

    fun saveAndViewDetails() {
        val current = _state.value
        val track = current.track ?: return
        _state.value = current.copy(isSaving = true)

        viewModelScope.launch {
            val updated = track.copy(
                name = current.editedName.ifBlank { track.name },
                description = current.editedDescription.ifBlank { null },
                routeType = current.selectedRouteType.ifBlank { null },
                difficultyRating = current.selectedDifficulty.ifBlank { null },
                activityTags = current.selectedActivityTags.joinToString(","),
                curvinessScore = current.classification?.curvinessScore ?: track.curvinessScore,
                vehicleId = current.selectedVehicleId,
            )
            trackDao.updateTrack(updated)

            // Generate thumbnail
            val points = trackPointDao.getPointsForTrackOnce(trackId)
            val thumbnailPath = withContext(defaultDispatcher) {
                ThumbnailGenerator.generate(points, context = application)
            }
            if (thumbnailPath != null) {
                trackDao.updateTrack(updated.copy(thumbnailPath = thumbnailPath))
            }

            // Fire-and-forget publish to Firestore
            publishTrackToFirestore(updated)

            // Check achievements
            achievementTracker.seedAchievements()
            val newAchievements = achievementTracker.checkAndUpdate(updated)
            if (newAchievements.isNotEmpty()) {
                _state.value = _state.value.copy(
                    newlyUnlockedAchievements = newAchievements,
                    isSaving = false,
                )
            } else {
                _navigateToDetail.tryEmit(trackId)
            }
        }
    }

    fun dismissAchievements() {
        _navigateToDetail.tryEmit(trackId)
    }

    fun shareActivity(context: Context) {
        val track = _state.value.track ?: return
        val unitSystem = preferences.value.unitSystem
        viewModelScope.launch {
            val uri = generateShareBitmap(context, track, unitSystem) ?: return@launch
            val shareText =
                "${track.name} — ${formatDistance(track.distanceMeters, unitSystem)} in ${formatElapsedTime(track.durationMs)}"
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_TEXT, shareText)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(shareIntent, "Share Activity")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
        }
    }

    private fun publishTrackToFirestore(track: TrackEntity) {
        val user = auth.currentUser ?: return
        viewModelScope.launch {
            try {
                val sharedTrack = track.toSharedTrack(
                    uid = user.uid,
                    displayName = user.displayName,
                    photoUrl = user.photoUrl?.toString(),
                )
                socialRepository.publishTrack(sharedTrack)
            } catch (_: Exception) {
                // Non-critical: Firestore publish failure should not affect the save flow
            }
        }
    }

    fun discardTrack() {
        viewModelScope.launch {
            trackDao.deleteTrack(trackId)
            _navigateBack.tryEmit(Unit)
        }
    }
}

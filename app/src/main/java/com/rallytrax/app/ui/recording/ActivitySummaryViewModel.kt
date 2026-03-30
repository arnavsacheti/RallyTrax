package com.rallytrax.app.ui.recording

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.util.Log
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
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
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
)

@HiltViewModel
class ActivitySummaryViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val trackDao: TrackDao,
    private val trackPointDao: TrackPointDao,
    private val vehicleDao: VehicleDao,
    private val achievementTracker: AchievementTracker,
    private val valhallaSurfaceClient: ValhallaSurfaceClient,
    preferencesRepository: UserPreferencesRepository,
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

    fun discardTrack() {
        viewModelScope.launch {
            trackDao.deleteTrack(trackId)
            _navigateBack.tryEmit(Unit)
        }
    }
}

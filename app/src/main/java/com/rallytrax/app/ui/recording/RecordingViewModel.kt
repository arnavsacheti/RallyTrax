package com.rallytrax.app.ui.recording

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rallytrax.app.data.classification.RouteClassifier
import com.rallytrax.app.data.classification.SurfaceFusion
import com.rallytrax.app.data.classification.ValhallaSurfaceClient
import com.rallytrax.app.data.local.dao.TrackDao
import com.rallytrax.app.data.local.dao.TrackPointDao
import com.rallytrax.app.data.preferences.UserPreferencesData
import com.rallytrax.app.data.preferences.UserPreferencesRepository
import com.rallytrax.app.recording.RecordingData
import com.rallytrax.app.recording.RecordingStatus
import com.rallytrax.app.recording.TrackingService
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

data class ClassificationPending(
    val trackId: String,
    val result: RouteClassifier.ClassificationResult,
)

@HiltViewModel
class RecordingViewModel @Inject constructor(
    preferencesRepository: UserPreferencesRepository,
    private val trackDao: TrackDao,
    private val trackPointDao: TrackPointDao,
    private val valhallaSurfaceClient: ValhallaSurfaceClient,
) : ViewModel() {

    val preferences: StateFlow<UserPreferencesData> = preferencesRepository.preferences
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UserPreferencesData())

    val recordingStatus: StateFlow<RecordingStatus> = TrackingService.recordingStatus
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), RecordingStatus.IDLE)

    val recordingData: StateFlow<RecordingData> = TrackingService.recordingData
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), RecordingData.EMPTY)

    private val _navigateToTrackDetail = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val navigateToTrackDetail = _navigateToTrackDetail.asSharedFlow()

    private val _classificationPending = MutableStateFlow<ClassificationPending?>(null)
    val classificationPending: StateFlow<ClassificationPending?> = _classificationPending.asStateFlow()

    init {
        viewModelScope.launch {
            TrackingService.savedTrackId.collect { trackId ->
                // Run classification before navigating
                runClassification(trackId)
            }
        }
    }

    private suspend fun runClassification(trackId: String) {
        try {
            val points = trackPointDao.getPointsForTrackOnce(trackId)
            if (points.size < 10) {
                // Too short to classify, just navigate
                _navigateToTrackDetail.tryEmit(trackId)
                return
            }

            // 1. Run route classification
            val result = RouteClassifier.classify(points)

            // 2. Try Valhalla surface detection (async, non-blocking)
            try {
                val mapSurfaces = valhallaSurfaceClient.getTraceAttributes(points)
                if (mapSurfaces.isNotEmpty()) {
                    val classifiedPoints = SurfaceFusion.applyClassifications(points, mapSurfaces)
                    trackPointDao.insertPoints(classifiedPoints) // Upsert with surface data

                    val breakdown = SurfaceFusion.computeSurfaceBreakdown(classifiedPoints)
                    val primary = SurfaceFusion.primarySurface(classifiedPoints)
                    val track = trackDao.getTrackById(trackId)
                    if (track != null) {
                        trackDao.updateTrack(
                            track.copy(
                                primarySurface = primary,
                                surfaceBreakdown = breakdown,
                                curvinessScore = result.curvinessScore,
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Surface detection failed, continuing without it", e)
            }

            // Show classification sheet
            _classificationPending.value = ClassificationPending(trackId, result)
        } catch (e: Exception) {
            Log.e(TAG, "Classification failed", e)
            _navigateToTrackDetail.tryEmit(trackId)
        }
    }

    fun acceptClassification(routeType: String, difficulty: String, activityTags: List<String>) {
        val pending = _classificationPending.value ?: return
        viewModelScope.launch {
            val track = trackDao.getTrackById(pending.trackId)
            if (track != null) {
                trackDao.updateTrack(
                    track.copy(
                        routeType = routeType,
                        difficultyRating = difficulty,
                        activityTags = activityTags.joinToString(","),
                        curvinessScore = pending.result.curvinessScore,
                    )
                )
            }
            _classificationPending.value = null
            _navigateToTrackDetail.tryEmit(pending.trackId)
        }
    }

    fun skipClassification() {
        val pending = _classificationPending.value ?: return
        viewModelScope.launch {
            // Still save curviness score even if user skips
            val track = trackDao.getTrackById(pending.trackId)
            if (track != null) {
                trackDao.updateTrack(
                    track.copy(curvinessScore = pending.result.curvinessScore)
                )
            }
            _classificationPending.value = null
            _navigateToTrackDetail.tryEmit(pending.trackId)
        }
    }

    fun startRecording(context: Context) {
        val intent = Intent(context, TrackingService::class.java).apply {
            action = TrackingService.ACTION_START
        }
        context.startForegroundService(intent)
    }

    fun pauseRecording(context: Context) {
        val intent = Intent(context, TrackingService::class.java).apply {
            action = TrackingService.ACTION_PAUSE
        }
        context.startService(intent)
    }

    fun resumeRecording(context: Context) {
        val intent = Intent(context, TrackingService::class.java).apply {
            action = TrackingService.ACTION_RESUME
        }
        context.startService(intent)
    }

    fun stopRecording(context: Context) {
        val intent = Intent(context, TrackingService::class.java).apply {
            action = TrackingService.ACTION_STOP
        }
        context.startService(intent)
    }

    companion object {
        private const val TAG = "RecordingViewModel"
    }
}

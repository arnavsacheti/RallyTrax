package com.rallytrax.app.ui.recording

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rallytrax.app.data.preferences.UserPreferencesData
import com.rallytrax.app.data.preferences.UserPreferencesRepository
import com.rallytrax.app.recording.RecordingData
import com.rallytrax.app.recording.RecordingStatus
import com.rallytrax.app.recording.SensorHudData
import com.rallytrax.app.recording.TrackingService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RecordingViewModel @Inject constructor(
    preferencesRepository: UserPreferencesRepository,
) : ViewModel() {

    val preferences: StateFlow<UserPreferencesData> = preferencesRepository.preferences
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UserPreferencesData())

    val recordingStatus: StateFlow<RecordingStatus> = TrackingService.recordingStatus
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), RecordingStatus.IDLE)

    val recordingData: StateFlow<RecordingData> = TrackingService.recordingData
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), RecordingData.EMPTY)

    val sensorHudData: StateFlow<SensorHudData> = TrackingService.sensorHudData
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SensorHudData.EMPTY)

    val isRecordingVoiceNote: StateFlow<Boolean> = TrackingService.isRecordingVoiceNote
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val _navigateToTrackDetail = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val navigateToTrackDetail = _navigateToTrackDetail.asSharedFlow()

    init {
        viewModelScope.launch {
            TrackingService.savedTrackId.collect { trackId ->
                // Navigate directly to ActivitySummaryScreen — it handles
                // classification, naming, vehicle assignment, and achievements
                _navigateToTrackDetail.tryEmit(trackId)
            }
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

    fun markSegment(context: Context, segmentType: String = "break") {
        val intent = Intent(context, TrackingService::class.java).apply {
            action = TrackingService.ACTION_MARK_SEGMENT
            putExtra(TrackingService.EXTRA_SEGMENT_TYPE, segmentType)
        }
        context.startService(intent)
    }

    fun toggleVoiceNote(context: Context) {
        val intent = Intent(context, TrackingService::class.java).apply {
            action = TrackingService.ACTION_VOICE_NOTE
        }
        context.startService(intent)
    }
}

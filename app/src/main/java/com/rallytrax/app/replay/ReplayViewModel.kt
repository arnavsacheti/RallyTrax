package com.rallytrax.app.replay

import android.annotation.SuppressLint
import android.app.Application
import android.content.Intent
import android.location.Location
import android.os.Looper
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.rallytrax.app.data.local.dao.DriverProfileDao
import com.rallytrax.app.data.local.dao.PaceNoteDao
import com.rallytrax.app.pacenotes.DriverProfileUpdater
import com.rallytrax.app.pacenotes.PaceNoteGenerator
import com.rallytrax.app.data.local.dao.TrackDao
import com.rallytrax.app.data.local.dao.TrackPointDao
import com.rallytrax.app.data.local.entity.PaceNoteEntity
import com.rallytrax.app.data.local.entity.TrackEntity
import com.rallytrax.app.data.local.entity.TrackPointEntity
import com.rallytrax.app.data.preferences.GpsAccuracy
import com.rallytrax.app.data.preferences.GpsIntervalConfig
import com.rallytrax.app.data.preferences.UserPreferencesData
import com.rallytrax.app.data.preferences.UserPreferencesRepository
import com.rallytrax.app.di.DefaultDispatcher
import com.rallytrax.app.di.IoDispatcher
import com.rallytrax.app.recording.GpsKalmanFilter
import com.rallytrax.app.recording.LatLng
import com.rallytrax.app.recording.TrackingService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class ReplayUiState(
    val track: TrackEntity? = null,
    val polylinePoints: List<LatLng> = emptyList(),
    val paceNotes: List<PaceNoteEntity> = emptyList(),
    val isLoading: Boolean = true,
    val isActive: Boolean = false,
    val isFinished: Boolean = false,
    val isOffRoute: Boolean = false,

    // Live driver data
    val driverPosition: LatLng? = null,
    val currentSpeedMps: Double = 0.0,
    val progressFraction: Float = 0f,

    // Next note preview
    val nextNote: PaceNoteEntity? = null,
    val distanceToNextNote: Double = Double.MAX_VALUE,

    // Audio controls
    val isMuted: Boolean = false,
    val volume: Float = 0.8f,

    // Ghost replay (personal best comparison)
    val ghostPosition: LatLng? = null,
    val ghostDeltaMs: Long = 0L,
    val hasGhost: Boolean = false,

    // Error
    val error: String? = null,
)

@HiltViewModel
class ReplayViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val application: Application,
    private val trackDao: TrackDao,
    private val trackPointDao: TrackPointDao,
    private val paceNoteDao: PaceNoteDao,
    private val driverProfileDao: DriverProfileDao,
    private val preferencesRepository: UserPreferencesRepository,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    @DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
) : ViewModel() {

    private val trackId: String = checkNotNull(savedStateHandle["trackId"])

    val preferences: StateFlow<UserPreferencesData> = preferencesRepository.preferences
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UserPreferencesData())

    private val _uiState = MutableStateFlow(ReplayUiState())
    val uiState: StateFlow<ReplayUiState> = _uiState.asStateFlow()

    private var replayEngine: ReplayEngine? = null
    private var audioManager: ReplayAudioManager? = null
    private var fusedLocationClient: FusedLocationProviderClient? = null
    private var locationCallback: LocationCallback? = null
    private val replayKalmanFilter = GpsKalmanFilter()
    private var replayPredictionJob: Job? = null
    private var ghostPoints: List<TrackPointEntity> = emptyList()
    private var replayStartTimeMs: Long = 0L

    init {
        loadTrackData()
    }

    private fun loadTrackData() {
        viewModelScope.launch {
            try {
                val track = withContext(ioDispatcher) { trackDao.getTrackById(trackId) }
                val points = withContext(ioDispatcher) { trackPointDao.getPointsForTrackOnce(trackId) }
                var notes = withContext(ioDispatcher) { paceNoteDao.getNotesForTrackOnce(trackId) }
                val polyline = withContext(defaultDispatcher) { points.map { LatLng(it.lat, it.lon) } }

                // Auto-regenerate pace notes if missing segment indices (migration from v12)
                if (notes.isNotEmpty() && notes.any { it.segmentStartIndex == null } && points.isNotEmpty()) {
                    val generated = withContext(defaultDispatcher) {
                        PaceNoteGenerator.generate(trackId, points)
                    }
                    withContext(ioDispatcher) {
                        paceNoteDao.deleteNotesForTrack(trackId)
                        paceNoteDao.insertNotes(generated)
                    }
                    notes = generated
                }

                if (track == null || points.isEmpty()) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "Track data not found",
                    )
                    return@launch
                }

                val prefs = withContext(ioDispatcher) { preferencesRepository.preferences.first() }

                // Load driver profile for adaptive pace note call timing
                val driverProfile = withContext(ioDispatcher) {
                    DriverProfileUpdater.loadProfile(driverProfileDao)
                }.ifEmpty { null }

                replayEngine = ReplayEngine(
                    trackPoints = points,
                    paceNotes = notes,
                    lookaheadSeconds = prefs.callTimingSeconds.toDouble(),
                    driverProfile = driverProfile,
                )

                // Load ghost track (personal best for the same route)
                val hasGhostTrack = withContext(ioDispatcher) {
                    val routeTracks = trackDao.getTracksForRoute(track.name)
                    val pb = routeTracks
                        .filter { it.id != trackId && it.durationMs > 0L }
                        .minByOrNull { it.durationMs }
                    if (pb != null) {
                        ghostPoints = trackPointDao.getPointsForTrackOnce(pb.id)
                        ghostPoints.size >= 2
                    } else {
                        false
                    }
                }
                if (hasGhostTrack) {
                    withContext(defaultDispatcher) {
                        replayEngine?.setGhostTrack(ghostPoints)
                    }
                }

                _uiState.value = _uiState.value.copy(
                    track = track,
                    polylinePoints = polyline,
                    paceNotes = notes,
                    isLoading = false,
                    hasGhost = hasGhostTrack,
                    nextNote = notes.sortedBy { it.distanceFromStart }.firstOrNull(),
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Failed to load track: ${e.message}",
                )
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun startReplay() {
        val engine = replayEngine ?: return
        engine.reset()

        viewModelScope.launch {
            // Read preferences on IO instead of runBlocking on Main
            val prefs = withContext(ioDispatcher) {
                preferencesRepository.preferences.first()
            }
            audioManager = ReplayAudioManager(application).also {
                it.initialize(prefs.ttsRate, prefs.ttsPitch)
            }

            // Start GPS tracking
            fusedLocationClient = LocationServices.getFusedLocationProviderClient(application)

            val locationRequest = when (prefs.gpsAccuracy) {
                GpsAccuracy.HIGH -> LocationRequest.Builder(
                    Priority.PRIORITY_HIGH_ACCURACY,
                    GpsIntervalConfig.HIGH_INTERVAL_MS,
                )
                    .setMinUpdateIntervalMillis(GpsIntervalConfig.HIGH_MIN_INTERVAL_MS)
                    .setMinUpdateDistanceMeters(GpsIntervalConfig.HIGH_MIN_DISTANCE_M)
                    .build()

                GpsAccuracy.BATTERY_SAVER -> LocationRequest.Builder(
                    Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                    GpsIntervalConfig.SAVER_INTERVAL_MS,
                )
                    .setMinUpdateIntervalMillis(GpsIntervalConfig.SAVER_MIN_INTERVAL_MS)
                    .setMinUpdateDistanceMeters(GpsIntervalConfig.SAVER_MIN_DISTANCE_M)
                    .build()
            }

            locationCallback = object : LocationCallback() {
                override fun onLocationResult(result: LocationResult) {
                    result.lastLocation?.let { location ->
                        onLocationUpdate(location)
                    }
                }
            }

            fusedLocationClient?.requestLocationUpdates(
                locationRequest,
                locationCallback!!,
                Looper.getMainLooper(),
            )

            replayKalmanFilter.reset()
            startReplayPredictionJob()

            // Start TrackingService to record a new stint from the live GPS
            val startIntent = Intent(application, TrackingService::class.java).apply {
                action = TrackingService.ACTION_START
            }
            application.startForegroundService(startIntent)

            replayStartTimeMs = System.currentTimeMillis()

            _uiState.value = _uiState.value.copy(
                isActive = true,
                isFinished = false,
                isOffRoute = false,
                progressFraction = 0f,
            )
        }
    }

    private fun onLocationUpdate(location: Location) {
        val engine = replayEngine ?: return
        val audio = audioManager ?: return

        val now = System.currentTimeMillis()
        val accuracy = if (location.hasAccuracy()) location.accuracy else 10f
        val rawSpeed = if (location.hasSpeed()) location.speed.toDouble() else null
        val rawBearing = if (location.hasBearing()) location.bearing.toDouble() else null

        // Feed GPS into Kalman filter for smoothed replay position
        val filtered = replayKalmanFilter.update(
            lat = location.latitude,
            lon = location.longitude,
            accuracyM = accuracy,
            speedMps = rawSpeed,
            bearingDeg = rawBearing,
            timestampMs = now,
        )

        val driverPos = LatLng(filtered.lat, filtered.lon)
        val speedMps = filtered.speedMps

        val result = engine.update(filtered.lat, filtered.lon, speedMps)

        // Speak the note if we have one
        result.noteToSpeak?.let { note ->
            audio.speak(note.callText)
        }

        // Handle finish
        if (result.isFinished && !_uiState.value.isFinished) {
            audio.speakFinish()
            stopLocationUpdates()
            stopTrackingService()
        }

        // Compute ghost position if a personal best track is loaded
        val ghostState = if (ghostPoints.size >= 2) {
            val elapsedMs = System.currentTimeMillis() - replayStartTimeMs
            engine.getGhostPosition(engine.currentProgress, elapsedMs)
        } else null

        _uiState.value = _uiState.value.copy(
            driverPosition = driverPos,
            currentSpeedMps = speedMps,
            progressFraction = result.progressFraction,
            isOffRoute = result.isOffRoute,
            isFinished = result.isFinished,
            nextNote = result.nextNote,
            distanceToNextNote = result.distanceToNextNote,
            ghostPosition = ghostState?.position,
            ghostDeltaMs = ghostState?.deltaMs ?: 0L,
        )
    }

    /**
     * High-rate prediction for smooth driver marker movement during replay.
     * Runs at 20 Hz between GPS fixes so the map marker doesn't jump.
     */
    private fun startReplayPredictionJob() {
        replayPredictionJob?.cancel()
        replayPredictionJob = viewModelScope.launch {
            while (true) {
                delay(50) // 20 Hz
                if (!replayKalmanFilter.isInitialized) continue
                val predicted = replayKalmanFilter.predict(System.currentTimeMillis())
                _uiState.value = _uiState.value.copy(
                    driverPosition = LatLng(predicted.lat, predicted.lon),
                    currentSpeedMps = predicted.speedMps,
                )
            }
        }
    }

    fun stopReplay() {
        replayPredictionJob?.cancel()
        stopLocationUpdates()
        audioManager?.stop()
        audioManager?.shutdown()
        audioManager = null

        // Stop TrackingService to finalize the recorded stint
        stopTrackingService()

        _uiState.value = _uiState.value.copy(
            isActive = false,
        )
    }

    fun toggleMute() {
        val newMuted = !_uiState.value.isMuted
        _uiState.value = _uiState.value.copy(isMuted = newMuted)
        audioManager?.setMuted(newMuted)
    }

    fun setVolume(vol: Float) {
        _uiState.value = _uiState.value.copy(volume = vol)
        audioManager?.setVolume(vol)
    }

    /** Adjust call timing during active replay (per-session override, not persisted). */
    fun setCallTimingSeconds(seconds: Float) {
        replayEngine?.lookaheadSeconds = seconds.toDouble().coerceIn(3.0, 12.0)
    }

    private fun stopTrackingService() {
        val stopIntent = Intent(application, TrackingService::class.java).apply {
            action = TrackingService.ACTION_STOP
        }
        application.startService(stopIntent)
    }

    private fun stopLocationUpdates() {
        locationCallback?.let { callback ->
            fusedLocationClient?.removeLocationUpdates(callback)
        }
        locationCallback = null
    }

    override fun onCleared() {
        super.onCleared()
        replayPredictionJob?.cancel()
        stopLocationUpdates()
        audioManager?.shutdown()
    }
}

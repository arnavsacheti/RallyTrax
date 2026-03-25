package com.rallytrax.app.car.replay

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.location.Location
import android.os.Looper
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.rallytrax.app.data.local.dao.PaceNoteDao
import com.rallytrax.app.data.local.dao.TrackPointDao
import com.rallytrax.app.data.local.entity.PaceNoteEntity
import com.rallytrax.app.data.preferences.GpsAccuracy
import com.rallytrax.app.data.preferences.GpsIntervalConfig
import com.rallytrax.app.data.preferences.UserPreferencesData
import com.rallytrax.app.recording.GpsKalmanFilter
import com.rallytrax.app.recording.LatLng
import com.rallytrax.app.recording.TrackingService
import com.rallytrax.app.replay.ReplayAudioManager
import com.rallytrax.app.replay.ReplayEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class CarReplayState(
    val driverPosition: LatLng? = null,
    val currentSpeedMps: Double = 0.0,
    val progressFraction: Float = 0f,
    val currentNote: PaceNoteEntity? = null,
    val nextNote: PaceNoteEntity? = null,
    val distanceToNextNote: Double = Double.MAX_VALUE,
    val distanceCovered: Double = 0.0,
    val distanceRemaining: Double = 0.0,
    val totalDistance: Double = 0.0,
    val isOffRoute: Boolean = false,
    val isFinished: Boolean = false,
    val isMuted: Boolean = false,
    val isActive: Boolean = false,
    val isLoading: Boolean = true,
    val polylinePoints: List<LatLng> = emptyList(),
    val paceNotes: List<PaceNoteEntity> = emptyList(),
    val error: String? = null,
)

class CarReplayManager(
    private val context: Context,
    private val trackPointDao: TrackPointDao,
    private val paceNoteDao: PaceNoteDao,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow(CarReplayState())
    val state: StateFlow<CarReplayState> = _state.asStateFlow()

    private var replayEngine: ReplayEngine? = null
    private var audioManager: ReplayAudioManager? = null
    private var fusedLocationClient: FusedLocationProviderClient? = null
    private var locationCallback: LocationCallback? = null
    private val kalmanFilter = GpsKalmanFilter()
    private var predictionJob: Job? = null

    @SuppressLint("MissingPermission")
    fun startReplay(trackId: String, prefs: UserPreferencesData) {
        scope.launch {
            try {
                _state.value = _state.value.copy(isLoading = true)

                val points = trackPointDao.getPointsForTrackOnce(trackId)
                val notes = paceNoteDao.getNotesForTrackOnce(trackId)

                if (points.isEmpty()) {
                    _state.value = _state.value.copy(
                        isLoading = false,
                        error = "Track data not found",
                    )
                    return@launch
                }

                val polyline = points.map { LatLng(it.lat, it.lon) }

                replayEngine = ReplayEngine(
                    trackPoints = points,
                    paceNotes = notes,
                    lookaheadSeconds = prefs.callTimingSeconds.toDouble(),
                )

                audioManager = ReplayAudioManager(context).also {
                    if (prefs.ttsEnabled) {
                        it.initialize(prefs.ttsRate, prefs.ttsPitch)
                    }
                }

                _state.value = _state.value.copy(
                    isLoading = false,
                    isActive = true,
                    polylinePoints = polyline,
                    paceNotes = notes,
                    totalDistance = replayEngine?.totalDistance ?: 0.0,
                    nextNote = notes.sortedBy { it.distanceFromStart }.firstOrNull(),
                )

                // Start GPS tracking
                fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)

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
                        result.lastLocation?.let { onLocationUpdate(it) }
                    }
                }

                fusedLocationClient?.requestLocationUpdates(
                    locationRequest,
                    locationCallback!!,
                    Looper.getMainLooper(),
                )

                kalmanFilter.reset()
                startPredictionJob()

                // Start TrackingService to record a new stint during replay
                val startIntent = Intent(context, TrackingService::class.java).apply {
                    action = TrackingService.ACTION_START
                }
                context.startForegroundService(startIntent)

            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = "Failed to start replay: ${e.message}",
                )
            }
        }
    }

    private fun onLocationUpdate(location: Location) {
        val engine = replayEngine ?: return
        val audio = audioManager

        val now = System.currentTimeMillis()
        val accuracy = if (location.hasAccuracy()) location.accuracy else 10f
        val rawSpeed = if (location.hasSpeed()) location.speed.toDouble() else null
        val rawBearing = if (location.hasBearing()) location.bearing.toDouble() else null

        val filtered = kalmanFilter.update(
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

        // Speak pace note
        result.noteToSpeak?.let { note ->
            audio?.speak(note.callText)
        }

        // Handle finish
        if (result.isFinished && !_state.value.isFinished) {
            audio?.speakFinish()
            stopLocationUpdates()
            stopTrackingService()
        }

        val totalDist = engine.totalDistance
        val covered = totalDist * result.progressFraction

        _state.value = _state.value.copy(
            driverPosition = driverPos,
            currentSpeedMps = speedMps,
            progressFraction = result.progressFraction,
            isOffRoute = result.isOffRoute,
            isFinished = result.isFinished,
            currentNote = result.noteToSpeak ?: _state.value.currentNote,
            nextNote = result.nextNote,
            distanceToNextNote = result.distanceToNextNote,
            distanceCovered = covered,
            distanceRemaining = totalDist - covered,
        )
    }

    private fun startPredictionJob() {
        predictionJob?.cancel()
        predictionJob = scope.launch {
            while (true) {
                delay(50) // 20 Hz
                if (!kalmanFilter.isInitialized) continue
                val predicted = kalmanFilter.predict(System.currentTimeMillis())
                _state.value = _state.value.copy(
                    driverPosition = LatLng(predicted.lat, predicted.lon),
                    currentSpeedMps = predicted.speedMps,
                )
            }
        }
    }

    fun toggleMute() {
        val newMuted = !_state.value.isMuted
        _state.value = _state.value.copy(isMuted = newMuted)
        audioManager?.setMuted(newMuted)
    }

    fun stopReplay() {
        predictionJob?.cancel()
        stopLocationUpdates()
        audioManager?.stop()
        audioManager?.shutdown()
        audioManager = null
        stopTrackingService()

        _state.value = _state.value.copy(isActive = false)
    }

    private fun stopLocationUpdates() {
        locationCallback?.let { callback ->
            fusedLocationClient?.removeLocationUpdates(callback)
        }
        locationCallback = null
    }

    private fun stopTrackingService() {
        val stopIntent = Intent(context, TrackingService::class.java).apply {
            action = TrackingService.ACTION_STOP
        }
        context.startService(stopIntent)
    }

    fun destroy() {
        stopReplay()
    }
}

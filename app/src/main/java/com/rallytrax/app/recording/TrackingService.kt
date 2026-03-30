package com.rallytrax.app.recording

import android.annotation.SuppressLint
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Location
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.rallytrax.app.data.local.dao.PaceNoteDao
import com.rallytrax.app.data.local.dao.TrackDao
import com.rallytrax.app.data.local.dao.TrackPointDao
import com.rallytrax.app.data.local.entity.TrackEntity
import com.rallytrax.app.data.local.entity.TrackPointEntity
import com.rallytrax.app.pacenotes.PaceNoteGenerator
import com.rallytrax.app.util.formatDistance
import com.rallytrax.app.util.formatElapsedTime
import com.rallytrax.app.data.preferences.GpsAccuracy
import com.rallytrax.app.data.preferences.GpsIntervalConfig
import com.rallytrax.app.data.preferences.UnitSystem
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import kotlin.math.max

@AndroidEntryPoint
class TrackingService : LifecycleService() {

    @Inject lateinit var trackDao: TrackDao
    @Inject lateinit var trackPointDao: TrackPointDao
    @Inject lateinit var paceNoteDao: PaceNoteDao
    @Inject lateinit var gridCellDao: com.rallytrax.app.data.local.dao.GridCellDao
    @Inject lateinit var preferencesRepository: com.rallytrax.app.data.preferences.UserPreferencesRepository
    @Inject lateinit var vehicleDao: com.rallytrax.app.data.local.dao.VehicleDao
    @Inject lateinit var gasStationDetector: com.rallytrax.app.data.fuel.GasStationDetector
    @Inject lateinit var driverProfileDao: com.rallytrax.app.data.local.dao.DriverProfileDao

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var notificationManager: TrackingNotificationManager

    private var timerJob: Job? = null
    private var flushJob: Job? = null
    private var trackId: String = ""
    private var pointIndex: Int = 0
    private var accumulatedTimeMs: Long = 0L
    private var timerStartMs: Long = 0L
    private var lastLocation: Location? = null
    private var previousElevation: Double? = null
    private var totalDistance: Double = 0.0
    private var maxSpeed: Double = 0.0
    private var elevationGain: Double = 0.0
    private var speedSum: Double = 0.0
    private var speedCount: Int = 0
    private var minLat: Double = Double.MAX_VALUE
    private var maxLat: Double = -Double.MAX_VALUE
    private var minLon: Double = Double.MAX_VALUE
    private var maxLon: Double = -Double.MAX_VALUE
    private var isNewSegment: Boolean = true
    private var cachedUnitSystem: UnitSystem = UnitSystem.METRIC

    // Kalman filter for smooth, high-rate position tracking
    private val kalmanFilter = GpsKalmanFilter()
    private var predictionJob: Job? = null

    // Phone sensor collector for Jemba-style inertial data
    private var sensorCollector: SensorCollector? = null

    // Gas station pause detection
    private var pauseStartTime: Long? = null
    private var pauseLocation: Location? = null
    private var pauseAlerted: Boolean = false

    // Auto-pause state
    private var stationaryStartMs: Long? = null
    private var isAutoPaused: Boolean = false
    private var lastGpsAccuracy: Float? = null
    private var lastElevation: Double? = null

    private var pendingFlushJob: Job? = null
    private val pointBuffer = mutableListOf<TrackPointEntity>()
    private val pathSegments = mutableListOf<MutableList<LatLng>>()

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.lastLocation?.let { location ->
                onNewLocation(location)
            }
        }
    }

    companion object {
        // Gas station pause detection thresholds
        private const val PAUSE_SPEED_THRESHOLD_MPS = 0.5     // ~1.1 mph
        private const val RESUME_SPEED_THRESHOLD_MPS = 2.0    // ~4.5 mph
        private const val PAUSE_DURATION_THRESHOLD_MS = 120_000L // 2 minutes
        private const val PAUSE_RADIUS_M = 30f                 // 30 metre radius

        const val ACTION_START = "ACTION_START"
        const val ACTION_PAUSE = "ACTION_PAUSE"
        const val ACTION_RESUME = "ACTION_RESUME"
        const val ACTION_STOP = "ACTION_STOP"
        const val ACTION_MARK_SEGMENT = "ACTION_MARK_SEGMENT"
        const val EXTRA_SEGMENT_TYPE = "EXTRA_SEGMENT_TYPE"

        private val _recordingStatus = MutableStateFlow(RecordingStatus.IDLE)
        val recordingStatus = _recordingStatus.asStateFlow()

        private val _recordingData = MutableStateFlow(RecordingData.EMPTY)
        val recordingData = _recordingData.asStateFlow()

        private val _savedTrackId = MutableSharedFlow<String>(extraBufferCapacity = 1)
        val savedTrackId = _savedTrackId.asSharedFlow()

        private val _sensorHudData = MutableStateFlow(SensorHudData.EMPTY)
        val sensorHudData = _sensorHudData.asStateFlow()

        private val _gasStationDetected = MutableSharedFlow<GasStationPrompt>(extraBufferCapacity = 1)
        val gasStationDetected = _gasStationDetected.asSharedFlow()
    }

    data class GasStationPrompt(
        val stationName: String,
        val lat: Double,
        val lon: Double,
        val trackId: String,
    )

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        notificationManager = TrackingNotificationManager(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_START -> startRecording()
            ACTION_PAUSE -> pauseRecording()
            ACTION_RESUME -> resumeRecording()
            ACTION_STOP -> stopRecording()
            ACTION_MARK_SEGMENT -> markSegment(intent?.getStringExtra(EXTRA_SEGMENT_TYPE) ?: "break")
        }
        return START_STICKY
    }

    @SuppressLint("MissingPermission")
    private fun startRecording() {
        trackId = UUID.randomUUID().toString()
        pointIndex = 0
        accumulatedTimeMs = 0L
        totalDistance = 0.0
        maxSpeed = 0.0
        elevationGain = 0.0
        speedSum = 0.0
        speedCount = 0
        lastLocation = null
        previousElevation = null
        isNewSegment = true
        cachedUnitSystem = kotlinx.coroutines.runBlocking {
            preferencesRepository.preferences.first()
        }.unitSystem
        kalmanFilter.reset()
        minLat = Double.MAX_VALUE
        maxLat = -Double.MAX_VALUE
        minLon = Double.MAX_VALUE
        maxLon = -Double.MAX_VALUE
        pointBuffer.clear()
        pathSegments.clear()
        pathSegments.add(mutableListOf())

        // Start phone sensor collection (accelerometer, gyroscope, barometer)
        sensorCollector = SensorCollector(this).also { it.start() }

        // Start foreground immediately (doesn't need DB)
        val notification = notificationManager.createNotification("00:00", formatDistance(0.0, cachedUnitSystem))
        startForeground(
            TrackingNotificationManager.NOTIFICATION_ID,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION,
        )

        // Insert skeleton track, then start GPS after it's persisted
        lifecycleScope.launch {
            val now = System.currentTimeMillis()
            val name = generateTrackName(now)
            // Link to active vehicle if one exists
            val activeVehicleId = vehicleDao.getActiveVehicle()?.id
            trackDao.insertTrack(
                TrackEntity(
                    id = trackId,
                    name = name,
                    recordedAt = now,
                    vehicleId = activeVehicleId,
                )
            )

            // Start GPS only after skeleton track exists in DB
            val locationRequest = buildLocationRequest()
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                mainLooper,
            )

            startTimer()
            startFlushJob()
            startPredictionJob()
            _recordingStatus.value = RecordingStatus.RECORDING
        }
    }

    private fun pauseRecording() {
        fusedLocationClient.removeLocationUpdates(locationCallback)
        pauseTimer()
        predictionJob?.cancel()
        isNewSegment = true

        _recordingStatus.value = RecordingStatus.PAUSED
        updateNotification(isPaused = true)
    }

    @SuppressLint("MissingPermission")
    private fun resumeRecording() {
        // Start a new segment for the gap
        pathSegments.add(mutableListOf())
        lastLocation = null
        isNewSegment = true
        kalmanFilter.reset()

        val locationRequest = buildLocationRequest()

        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            locationCallback,
            mainLooper,
        )

        startTimer()
        startPredictionJob()
        _recordingStatus.value = RecordingStatus.RECORDING
        updateNotification(isPaused = false)
    }

    private fun buildLocationRequest(): LocationRequest {
        val prefs = kotlinx.coroutines.runBlocking {
            preferencesRepository.preferences.first()
        }
        return when (prefs.gpsAccuracy) {
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
    }

    private fun stopRecording() {
        fusedLocationClient.removeLocationUpdates(locationCallback)
        pauseTimer()
        predictionJob?.cancel()
        flushJob?.cancel()
        sensorCollector?.stop()
        sensorCollector = null

        _recordingStatus.value = RecordingStatus.STOPPED

        lifecycleScope.launch {
            // Await any in-flight flush from a previous flushBuffer() call
            pendingFlushJob?.join()

            // Flush remaining buffer
            if (pointBuffer.isNotEmpty()) {
                trackPointDao.insertPoints(pointBuffer.toList())
                pointBuffer.clear()
            }

            // Compute final stats
            val avgSpeed = if (speedCount > 0) speedSum / speedCount else 0.0
            val durationMs = accumulatedTimeMs

            // Read existing skeleton track to preserve vehicleId and other fields
            val existingTrack = trackDao.getTrackById(trackId)
            val finalTrack = (existingTrack ?: TrackEntity(id = trackId, name = generateTrackName(System.currentTimeMillis()), recordedAt = System.currentTimeMillis() - durationMs)).copy(
                durationMs = durationMs,
                distanceMeters = totalDistance,
                maxSpeedMps = maxSpeed,
                avgSpeedMps = avgSpeed,
                elevationGainM = elevationGain,
                boundingBoxNorthLat = if (maxLat != -Double.MAX_VALUE) maxLat else 0.0,
                boundingBoxSouthLat = if (minLat != Double.MAX_VALUE) minLat else 0.0,
                boundingBoxEastLon = if (maxLon != -Double.MAX_VALUE) maxLon else 0.0,
                boundingBoxWestLon = if (minLon != Double.MAX_VALUE) minLon else 0.0,
            )
            trackDao.updateTrack(finalTrack)

            // Compute acceleration and curvature for all points
            val savedId = trackId
            var allPoints = trackPointDao.getPointsForTrackOnce(savedId)
            try {
                val enrichedPoints = withContext(Dispatchers.Default) {
                    com.rallytrax.app.pacenotes.TrackPointComputer.computeFields(allPoints)
                }
                if (enrichedPoints.isNotEmpty()) {
                    enrichedPoints.chunked(1000).forEach { chunk ->
                        trackPointDao.insertPoints(chunk)
                    }
                    allPoints = enrichedPoints
                }
            } catch (_: Exception) {
                // Non-critical; continue with un-enriched points
            }

            // Generate pace notes with speed calibration from driver profile
            try {
                val driverProfile = com.rallytrax.app.pacenotes.DriverProfileUpdater.loadProfile(driverProfileDao)
                val hasSpeedData = allPoints.any { (it.speed ?: 0.0) > 0.0 }
                val paceNotes = withContext(Dispatchers.Default) {
                    PaceNoteGenerator.generate(
                        trackId = savedId,
                        points = allPoints,
                        useSpeedCalibration = hasSpeedData,
                        driverProfile = driverProfile.ifEmpty { null },
                    )
                }
                if (paceNotes.isNotEmpty()) {
                    paceNoteDao.deleteNotesForTrack(savedId)
                    paceNoteDao.insertNotes(paceNotes)

                    // Update persistent driver profile with this stint's speed data
                    if (hasSpeedData) {
                        com.rallytrax.app.pacenotes.DriverProfileUpdater.updateFromStint(
                            paceNotes, allPoints, driverProfileDao,
                        )
                    }
                }
            } catch (_: Exception) {
                // Pace note generation is non-critical; don't block save
            }

            // Update heatmap grid cells incrementally
            try {
                com.rallytrax.app.data.local.GridCellComputer.updateForTrack(allPoints, gridCellDao)
            } catch (_: Exception) {
                // Non-critical
            }

            _savedTrackId.tryEmit(savedId)
            _recordingStatus.value = RecordingStatus.IDLE
            _recordingData.value = RecordingData.EMPTY
            _sensorHudData.value = SensorHudData.EMPTY

            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun onNewLocation(location: Location) {
        val now = System.currentTimeMillis()
        val accuracy = if (location.hasAccuracy()) location.accuracy else 10f
        val rawSpeed = if (location.hasSpeed()) location.speed.toDouble() else null
        val rawBearing = if (location.hasBearing()) location.bearing.toDouble() else null

        // Feed raw GPS into Kalman filter
        val filtered = kalmanFilter.update(
            lat = location.latitude,
            lon = location.longitude,
            accuracyM = accuracy,
            speedMps = rawSpeed,
            bearingDeg = rawBearing,
            timestampMs = now,
        )

        // Use filtered position for path display and UI
        val filteredLatLng = LatLng(filtered.lat, filtered.lon)

        // Update bounding box (use raw for accurate bounds)
        minLat = minOf(minLat, location.latitude)
        maxLat = maxOf(maxLat, location.latitude)
        minLon = minOf(minLon, location.longitude)
        maxLon = maxOf(maxLon, location.longitude)

        // Distance from filtered positions (skip first point of new segment)
        if (!isNewSegment && lastLocation != null) {
            val delta = lastLocation!!.distanceTo(location)
            // Filter out GPS jumps (> 100 m/s which is 360 km/h)
            if (delta / 1.0 < 100.0) {
                totalDistance += delta
            }
        }

        // Speed — use Kalman-filtered speed for smoother stats
        val speed = if (rawSpeed != null && rawSpeed > 0) rawSpeed else filtered.speedMps
        if (speed > 0) {
            maxSpeed = max(maxSpeed, speed)
            speedSum += speed
            speedCount++
        }

        // Gas station pause detection (use raw for accuracy)
        detectGasStationPause(location, speed)

        // Track GPS accuracy for UI indicator
        lastGpsAccuracy = accuracy

        // Elevation
        if (location.hasAltitude()) {
            val elevation = location.altitude
            lastElevation = elevation
            previousElevation?.let { prev ->
                val delta = elevation - prev
                if (delta > 2.0) { // Threshold to filter GPS noise
                    elevationGain += delta
                }
            }
            previousElevation = elevation
        }

        // Auto-pause detection
        checkAutoPause(speed, now)

        // Add filtered position to the visible path segment
        pathSegments.lastOrNull()?.add(filteredLatLng)
        isNewSegment = false
        lastLocation = location

        // Snapshot phone sensor data alongside GPS fix
        val sensorSnapshot = sensorCollector?.snapshot()

        // Store raw GPS in database (preserves ground-truth data for post-processing)
        val point = TrackPointEntity(
            trackId = trackId,
            index = pointIndex++,
            lat = location.latitude,
            lon = location.longitude,
            elevation = if (location.hasAltitude()) location.altitude else null,
            timestamp = now,
            speed = rawSpeed,
            bearing = rawBearing,
            accuracy = if (location.hasAccuracy()) location.accuracy else null,
            lateralAccelMps2 = sensorSnapshot?.lateralAccelMps2,
            verticalAccelMps2 = sensorSnapshot?.verticalAccelMps2,
            yawRateDegPerS = sensorSnapshot?.yawRateDegPerS,
            rollRateDegPerS = sensorSnapshot?.rollRateDegPerS,
            barometerAltitudeM = sensorSnapshot?.barometerAltitudeM,
        )
        pointBuffer.add(point)

        // Check buffer size
        if (pointBuffer.size >= 50) {
            flushBuffer()
        }

        // Update UI state with filtered speed and position
        emitRecordingData(filtered.speedMps, filteredLatLng)
    }

    private fun emitRecordingData(speed: Double, latLng: LatLng) {
        val elapsed = accumulatedTimeMs + (System.currentTimeMillis() - timerStartMs)
        val avgSpeed = if (speedCount > 0) speedSum / speedCount else 0.0
        _recordingData.value = RecordingData(
            pathSegments = pathSegments.map { it.toList() },
            currentSpeed = speed,
            elapsedTimeMs = elapsed,
            distanceMeters = totalDistance,
            maxSpeedMps = maxSpeed,
            elevationGainM = elevationGain,
            currentLatLng = latLng,
            pointCount = pointIndex,
            gpsAccuracy = lastGpsAccuracy,
            avgSpeedMps = avgSpeed,
            currentElevation = lastElevation,
            isAutoPaused = isAutoPaused,
        )
    }

    private fun markSegment(segmentType: String) {
        val location = lastLocation ?: return
        val point = TrackPointEntity(
            trackId = trackId,
            index = pointIndex++,
            lat = location.latitude,
            lon = location.longitude,
            elevation = if (location.hasAltitude()) location.altitude else null,
            timestamp = System.currentTimeMillis(),
            speed = if (location.hasSpeed()) location.speed.toDouble() else null,
            bearing = if (location.hasBearing()) location.bearing.toDouble() else null,
            accuracy = if (location.hasAccuracy()) location.accuracy else null,
            segmentMarker = segmentType,
        )
        pointBuffer.add(point)
    }

    private fun checkAutoPause(speed: Double, now: Long) {
        // Read auto-pause preference (cached at start, check periodically)
        val prefs = kotlinx.coroutines.runBlocking {
            preferencesRepository.preferences.first()
        }
        if (!prefs.autoPauseEnabled) {
            stationaryStartMs = null
            return
        }

        val delayMs = prefs.autoPauseDelaySeconds * 1000L

        if (speed < PAUSE_SPEED_THRESHOLD_MPS) {
            if (stationaryStartMs == null) {
                stationaryStartMs = now
            } else if (!isAutoPaused && (now - stationaryStartMs!!) >= delayMs) {
                // Auto-pause: pause the timer but keep GPS running so we can detect resume
                isAutoPaused = true
                pauseTimer()
                updateNotification(isPaused = true)
            }
        } else if (speed > RESUME_SPEED_THRESHOLD_MPS && isAutoPaused) {
            // Auto-resume
            isAutoPaused = false
            stationaryStartMs = null
            pathSegments.add(mutableListOf())
            startTimer()
            updateNotification(isPaused = false)
        } else if (speed > RESUME_SPEED_THRESHOLD_MPS) {
            stationaryStartMs = null
        }
    }

    private fun detectGasStationPause(location: Location, speed: Double) {
        val now = System.currentTimeMillis()

        if (speed < PAUSE_SPEED_THRESHOLD_MPS) {
            if (pauseStartTime == null) {
                pauseStartTime = now
                pauseLocation = location
                pauseAlerted = false
            } else if (!pauseAlerted) {
                val pauseDurationMs = now - (pauseStartTime ?: now)
                val distFromPauseStart = pauseLocation?.distanceTo(location) ?: 0f

                if (pauseDurationMs >= PAUSE_DURATION_THRESHOLD_MS && distFromPauseStart <= PAUSE_RADIUS_M) {
                    pauseAlerted = true
                    lifecycleScope.launch {
                        try {
                            val station = gasStationDetector.findNearbyStation(
                                location.latitude, location.longitude,
                            )
                            if (station != null) {
                                _gasStationDetected.tryEmit(
                                    GasStationPrompt(
                                        stationName = station.name,
                                        lat = station.lat,
                                        lon = station.lon,
                                        trackId = trackId,
                                    )
                                )
                            }
                        } catch (e: Exception) {
                            // Silent — gas station detection is best-effort
                        }
                    }
                }
            }
        } else if (speed > RESUME_SPEED_THRESHOLD_MPS) {
            // Moving again — clear pause state
            pauseStartTime = null
            pauseLocation = null
            pauseAlerted = false
        }
    }

    private fun startTimer() {
        timerStartMs = System.currentTimeMillis()
        timerJob = lifecycleScope.launch {
            while (true) {
                delay(1000)
                val elapsed = accumulatedTimeMs + (System.currentTimeMillis() - timerStartMs)
                _recordingData.value = _recordingData.value.copy(elapsedTimeMs = elapsed)
                updateNotification(isPaused = false)
            }
        }
    }

    private fun pauseTimer() {
        timerJob?.cancel()
        accumulatedTimeMs += System.currentTimeMillis() - timerStartMs
    }

    /**
     * High-rate prediction loop: runs at ~20 Hz (every 50ms) to produce smooth
     * interpolated positions between GPS fixes. This gives the UI buttery-smooth
     * map tracking without waiting for the next GPS callback.
     */
    private fun startPredictionJob() {
        predictionJob?.cancel()
        predictionJob = lifecycleScope.launch {
            while (true) {
                delay(50) // 20 Hz prediction rate
                if (!kalmanFilter.isInitialized) continue
                val predicted = kalmanFilter.predict(System.currentTimeMillis())
                val predictedLatLng = LatLng(predicted.lat, predicted.lon)
                // Update only the current position and speed for smooth UI;
                // path segments are only appended on real GPS updates
                _recordingData.value = _recordingData.value.copy(
                    currentLatLng = predictedLatLng,
                    currentSpeed = predicted.speedMps,
                )
                // Emit live sensor readings for HUD overlay
                // SensorCollector maps: values[1] -> lateralAccel (y-axis),
                // values[2] -> verticalAccel (z-axis). For the HUD:
                // - lateral G = y-axis (side-to-side cornering force)
                // - longitudinal G = z-axis (accel/brake in device frame)
                // - vertical G = residual z-axis component (road roughness)
                sensorCollector?.snapshot()?.let { snap ->
                    _sensorHudData.value = SensorHudData(
                        lateralAccelMps2 = snap.lateralAccelMps2,
                        longitudinalAccelMps2 = snap.verticalAccelMps2,
                        verticalAccelMps2 = snap.verticalAccelMps2,
                    )
                }
            }
        }
    }

    private fun startFlushJob() {
        flushJob = lifecycleScope.launch {
            while (true) {
                delay(30_000)
                flushBuffer()
            }
        }
    }

    private fun flushBuffer() {
        if (pointBuffer.isEmpty()) return
        val points = pointBuffer.toList()
        pointBuffer.clear()
        pendingFlushJob = lifecycleScope.launch {
            trackPointDao.insertPoints(points)
        }
    }

    private fun updateNotification(isPaused: Boolean) {
        val elapsed = if (isPaused) {
            accumulatedTimeMs
        } else {
            accumulatedTimeMs + (System.currentTimeMillis() - timerStartMs)
        }
        val notification = notificationManager.createNotification(
            elapsedTime = formatElapsedTime(elapsed),
            distance = formatDistance(totalDistance, cachedUnitSystem),
            isPaused = isPaused,
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(TrackingNotificationManager.NOTIFICATION_ID, notification)
    }

    private fun generateTrackName(timeMs: Long): String {
        val dateTime = LocalDateTime.ofInstant(
            Instant.ofEpochMilli(timeMs),
            ZoneId.systemDefault(),
        )
        return dateTime.format(DateTimeFormatter.ofPattern("MMM d, yyyy 'at' h:mm a"))
    }

    override fun onDestroy() {
        super.onDestroy()
        timerJob?.cancel()
        flushJob?.cancel()
        predictionJob?.cancel()
        sensorCollector?.stop()
        sensorCollector = null
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }
}

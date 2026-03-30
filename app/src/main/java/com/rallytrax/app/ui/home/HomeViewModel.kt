package com.rallytrax.app.ui.home

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rallytrax.app.data.gpx.GpxImportResult
import com.rallytrax.app.data.gpx.GpxParseException
import com.rallytrax.app.data.local.dao.PaceNoteDao
import com.rallytrax.app.data.local.dao.TrackDao
import com.rallytrax.app.data.local.dao.TrackPointDao
import com.rallytrax.app.data.local.entity.TrackEntity
import com.rallytrax.app.data.preferences.UserPreferencesData
import com.rallytrax.app.data.preferences.UserPreferencesRepository
import com.rallytrax.app.di.IoDispatcher
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoField
import javax.inject.Inject

data class WeeklySummary(
    val totalDistanceMeters: Double = 0.0,
    val driveCount: Int = 0,
    val totalDurationMs: Long = 0L,
)

data class DuplicateImportState(
    val pendingImport: GpxImportResult,
    val existingTrack: TrackEntity,
)

data class HomeFeedState(
    val weeklySummary: WeeklySummary = WeeklySummary(),
    val feedItems: List<ActivityFeedItem> = emptyList(),
    val totalStintCount: Int = 0,
    val maintenanceDueCount: Int = 0,
    val showActiveVehicleOnly: Boolean = false,
    val activeVehicleName: String? = null,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val trackDao: TrackDao,
    private val trackPointDao: TrackPointDao,
    private val paceNoteDao: PaceNoteDao,
    private val maintenanceDao: com.rallytrax.app.data.local.dao.MaintenanceDao,
    private val vehicleDao: com.rallytrax.app.data.local.dao.VehicleDao,
    preferencesRepository: UserPreferencesRepository,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {

    val preferences: StateFlow<UserPreferencesData> = preferencesRepository.preferences
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UserPreferencesData())

    private val _snackbarMessage = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val snackbarMessage = _snackbarMessage.asSharedFlow()

    private val _showActiveVehicleOnly = MutableStateFlow(false)

    private val _duplicateImportState = MutableStateFlow<DuplicateImportState?>(null)
    val duplicateImportState: StateFlow<DuplicateImportState?> = _duplicateImportState.asStateFlow()

    val feedState: StateFlow<HomeFeedState> = combine(
        trackDao.getStints(),
        vehicleDao.getAllVehicles(),
        _showActiveVehicleOnly,
        vehicleDao.observeActiveVehicle(),
    ) { allStints, allVehicles, filterByActive, activeVehicle ->
        val stints = if (filterByActive && activeVehicle != null) {
            allStints.filter { it.vehicleId == activeVehicle.id }
        } else {
            allStints
        }

        // Vehicle name lookup
        val vehicleNameMap = allVehicles.associate { it.id to it.name }

        // Weekly summary
        val now = Instant.now()
        val zone = ZoneId.systemDefault()
        val today = now.atZone(zone).toLocalDate()
        val startOfWeek = today.with(ChronoField.DAY_OF_WEEK, 1)
        val weekStartMs = startOfWeek.atStartOfDay(zone).toInstant().toEpochMilli()

        val weeklyStints = stints.filter { it.recordedAt >= weekStartMs }
        val weeklySummary = WeeklySummary(
            totalDistanceMeters = weeklyStints.sumOf { it.distanceMeters },
            driveCount = weeklyStints.size,
            totalDurationMs = weeklyStints.sumOf { it.durationMs },
        )

        val dueCount = try { maintenanceDao.getDueSchedules().size } catch (_: Exception) { 0 }

        val feedItems = stints.map { track ->
            ActivityFeedItem(
                track = track,
                vehicleName = track.vehicleId?.let { vehicleNameMap[it] },
            )
        }

        HomeFeedState(
            weeklySummary = weeklySummary,
            feedItems = feedItems,
            totalStintCount = stints.size,
            maintenanceDueCount = dueCount,
            showActiveVehicleOnly = filterByActive,
            activeVehicleName = activeVehicle?.name,
        )
    }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HomeFeedState())

    fun toggleVehicleFilter() {
        _showActiveVehicleOnly.value = !_showActiveVehicleOnly.value
    }

    fun importGpx(context: Context, uri: Uri) {
        viewModelScope.launch {
            try {
                val result = withContext(ioDispatcher) {
                    val inputStream = context.contentResolver.openInputStream(uri)
                        ?: throw GpxParseException("Could not open file")
                    inputStream.use { com.rallytrax.app.data.gpx.TrackImporter.import(it) }
                }

                // Detect duplicates before inserting
                val duplicate = withContext(ioDispatcher) { findDuplicate(result) }
                if (duplicate != null) {
                    _duplicateImportState.value = DuplicateImportState(result, duplicate)
                } else {
                    insertImport(result)
                    _snackbarMessage.tryEmit("Imported: ${result.track.name}")
                }
            } catch (e: GpxParseException) {
                _snackbarMessage.tryEmit("Import failed: ${e.message}")
            } catch (e: Exception) {
                _snackbarMessage.tryEmit("Import failed: ${e.message}")
            }
        }
    }

    fun confirmImportAsNew() {
        val state = _duplicateImportState.value ?: return
        _duplicateImportState.value = null
        viewModelScope.launch {
            insertImport(state.pendingImport)
            _snackbarMessage.tryEmit("Imported: ${state.pendingImport.track.name}")
        }
    }

    fun confirmReplaceExisting() {
        val state = _duplicateImportState.value ?: return
        _duplicateImportState.value = null
        viewModelScope.launch {
            withContext(ioDispatcher) {
                trackDao.deleteTrack(state.existingTrack.id)
            }
            insertImport(state.pendingImport)
            _snackbarMessage.tryEmit("Replaced: ${state.pendingImport.track.name}")
        }
    }

    fun dismissDuplicateDialog() {
        _duplicateImportState.value = null
    }

    private suspend fun findDuplicate(result: GpxImportResult): TrackEntity? {
        val track = result.track
        // First check by name match
        val byName = trackDao.getTracksForRoute(track.name)
            .filter { it.trackCategory == "route" }
        if (byName.isNotEmpty()) return byName.first()

        // Then check by bounding box overlap + distance similarity
        if (track.boundingBoxNorthLat == 0.0 && track.boundingBoxSouthLat == 0.0) return null
        val overlapping = trackDao.getTracksOverlappingBounds(
            excludeTrackId = "",
            northLat = track.boundingBoxNorthLat,
            southLat = track.boundingBoxSouthLat,
            eastLon = track.boundingBoxEastLon,
            westLon = track.boundingBoxWestLon,
        ).filter { it.trackCategory == "route" }

        return overlapping.firstOrNull { existing ->
            val distanceDiff = abs(existing.distanceMeters - track.distanceMeters)
            val tolerance = track.distanceMeters * 0.1 // 10% tolerance
            distanceDiff <= tolerance
        }
    }

    private suspend fun insertImport(result: GpxImportResult) {
        withContext(ioDispatcher) {
            trackDao.insertTrack(result.track.copy(trackCategory = "route"))
            result.points.chunked(1000).forEach { chunk ->
                trackPointDao.insertPoints(chunk)
            }
            if (result.paceNotes.isNotEmpty()) {
                paceNoteDao.insertNotes(result.paceNotes)
            }
        }
    }
}

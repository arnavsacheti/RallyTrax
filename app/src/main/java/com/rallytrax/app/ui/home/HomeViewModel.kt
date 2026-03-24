package com.rallytrax.app.ui.home

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rallytrax.app.data.gpx.GpxParseException
import com.rallytrax.app.data.gpx.GpxParser
import com.rallytrax.app.data.local.dao.PaceNoteDao
import com.rallytrax.app.data.local.dao.TrackDao
import com.rallytrax.app.data.local.dao.TrackPointDao
import com.rallytrax.app.data.local.entity.TrackEntity
import com.rallytrax.app.data.preferences.UserPreferencesData
import com.rallytrax.app.data.preferences.UserPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoField
import javax.inject.Inject

data class DailyDistance(
    val dayLabel: String, // e.g. "Mon", "Tue"
    val distanceMeters: Double,
)

enum class DashboardPeriod { WEEK, MONTH, YEAR }

data class PeriodBar(
    val label: String,
    val value: Double,
)

data class HomeDashboardState(
    val recentTracks: List<TrackEntity> = emptyList(),
    val totalDistanceMeters: Double = 0.0,
    val tracksThisWeek: Int = 0,
    val longestRouteMeters: Double = 0.0,
    val weeklyDistanceMeters: Double = 0.0,
    val monthlyRecordingCount: Int = 0,
    val dailyDistances: List<DailyDistance> = emptyList(),
    val totalTrackCount: Int = 0,
    val maintenanceDueCount: Int = 0,
    val showActiveVehicleOnly: Boolean = false,
    val activeVehicleName: String? = null,
    // Phase 4: Historical trends
    val sparklineData: List<Float> = emptyList(),
    val weeklyGoalProgress: Float? = null,
    val periodBars: List<PeriodBar> = emptyList(),
    val selectedPeriod: DashboardPeriod = DashboardPeriod.WEEK,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val trackDao: TrackDao,
    private val trackPointDao: TrackPointDao,
    private val paceNoteDao: PaceNoteDao,
    private val maintenanceDao: com.rallytrax.app.data.local.dao.MaintenanceDao,
    private val vehicleDao: com.rallytrax.app.data.local.dao.VehicleDao,
    preferencesRepository: UserPreferencesRepository,
) : ViewModel() {

    val preferences: StateFlow<UserPreferencesData> = preferencesRepository.preferences
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UserPreferencesData())

    private val _snackbarMessage = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val snackbarMessage = _snackbarMessage.asSharedFlow()

    private val _showActiveVehicleOnly = MutableStateFlow(false)

    val dashboardState: StateFlow<HomeDashboardState> = combine(
        trackDao.getAllTracks(),
        _showActiveVehicleOnly,
        vehicleDao.observeActiveVehicle(),
    ) { allTracks, filterByActive, activeVehicle ->
        val tracks = if (filterByActive && activeVehicle != null) {
            allTracks.filter { it.vehicleId == activeVehicle.id }
        } else {
            allTracks
        }

        val now = Instant.now()
        val zone = ZoneId.systemDefault()
        val today = now.atZone(zone).toLocalDate()

        // Start of current week (Monday)
        val startOfWeek = today.with(ChronoField.DAY_OF_WEEK, 1)
        val weekStartMs = startOfWeek.atStartOfDay(zone).toInstant().toEpochMilli()

        // Start of current month
        val startOfMonth = today.withDayOfMonth(1)
        val monthStartMs = startOfMonth.atStartOfDay(zone).toInstant().toEpochMilli()

        val weeklyTracks = tracks.filter { it.recordedAt >= weekStartMs }
        val weeklyDistance = weeklyTracks.sumOf { it.distanceMeters }
        val monthlyCount = tracks.count { it.recordedAt >= monthStartMs }
        val totalDistance = tracks.sumOf { it.distanceMeters }
        val longestRoute = tracks.maxOfOrNull { it.distanceMeters } ?: 0.0

        // Daily distances for the past 7 days
        val dayFormatter = java.time.format.DateTimeFormatter.ofPattern("EEE")
        val dailyDistances = (6 downTo 0).map { daysAgo ->
            val day = today.minusDays(daysAgo.toLong())
            val dayStartMs = day.atStartOfDay(zone).toInstant().toEpochMilli()
            val dayEndMs = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
            val distance = tracks
                .filter { it.recordedAt in dayStartMs until dayEndMs }
                .sumOf { it.distanceMeters }
            DailyDistance(
                dayLabel = day.format(dayFormatter),
                distanceMeters = distance,
            )
        }

        val dueCount = try { maintenanceDao.getDueSchedules().size } catch (_: Exception) { 0 }

        // Sparkline: 7-day distance trend
        val sparklineData = (6 downTo 0).map { daysAgo ->
            val day = today.minusDays(daysAgo.toLong())
            val dayStartMs = day.atStartOfDay(zone).toInstant().toEpochMilli()
            val dayEndMs = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
            tracks.filter { it.recordedAt in dayStartMs until dayEndMs }
                .sumOf { it.distanceMeters }.toFloat()
        }

        HomeDashboardState(
            recentTracks = tracks.take(5),
            totalDistanceMeters = totalDistance,
            tracksThisWeek = weeklyTracks.size,
            longestRouteMeters = longestRoute,
            weeklyDistanceMeters = weeklyDistance,
            monthlyRecordingCount = monthlyCount,
            dailyDistances = dailyDistances,
            totalTrackCount = tracks.size,
            maintenanceDueCount = dueCount,
            showActiveVehicleOnly = filterByActive,
            activeVehicleName = activeVehicle?.name,
            sparklineData = sparklineData,
        )
    }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HomeDashboardState())

    fun toggleVehicleFilter() {
        _showActiveVehicleOnly.value = !_showActiveVehicleOnly.value
    }

    fun importGpx(context: Context, uri: Uri): String? {
        var importedTrackId: String? = null
        viewModelScope.launch {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                    ?: throw GpxParseException("Could not open file")
                val result = inputStream.use { com.rallytrax.app.data.gpx.TrackImporter.import(it) }
                trackDao.insertTrack(result.track.copy(trackCategory = "route"))
                trackPointDao.insertPoints(result.points)
                if (result.paceNotes.isNotEmpty()) {
                    paceNoteDao.insertNotes(result.paceNotes)
                }
                importedTrackId = result.track.id
                _snackbarMessage.tryEmit("Imported: ${result.track.name}")
            } catch (e: GpxParseException) {
                _snackbarMessage.tryEmit("Import failed: ${e.message}")
            } catch (e: Exception) {
                _snackbarMessage.tryEmit("Import failed: ${e.message}")
            }
        }
        return importedTrackId
    }
}

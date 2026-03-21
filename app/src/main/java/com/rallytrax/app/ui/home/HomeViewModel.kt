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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoField
import javax.inject.Inject

data class DailyDistance(
    val dayLabel: String, // e.g. "Mon", "Tue"
    val distanceMeters: Double,
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
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val trackDao: TrackDao,
    private val trackPointDao: TrackPointDao,
    private val paceNoteDao: PaceNoteDao,
    preferencesRepository: UserPreferencesRepository,
) : ViewModel() {

    val preferences: StateFlow<UserPreferencesData> = preferencesRepository.preferences
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UserPreferencesData())

    private val _snackbarMessage = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val snackbarMessage = _snackbarMessage.asSharedFlow()

    val dashboardState: StateFlow<HomeDashboardState> = trackDao.getAllTracks()
        .map { tracks ->
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

            HomeDashboardState(
                recentTracks = tracks.take(5),
                totalDistanceMeters = totalDistance,
                tracksThisWeek = weeklyTracks.size,
                longestRouteMeters = longestRoute,
                weeklyDistanceMeters = weeklyDistance,
                monthlyRecordingCount = monthlyCount,
                dailyDistances = dailyDistances,
                totalTrackCount = tracks.size,
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HomeDashboardState())

    fun importGpx(context: Context, uri: Uri): String? {
        var importedTrackId: String? = null
        viewModelScope.launch {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                    ?: throw GpxParseException("Could not open file")
                val result = inputStream.use { com.rallytrax.app.data.gpx.TrackImporter.import(it) }
                trackDao.insertTrack(result.track)
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

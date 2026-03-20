package com.rallytrax.app.ui.trackdetail

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Environment
import android.provider.MediaStore
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rallytrax.app.data.gpx.GpxExporter
import com.rallytrax.app.data.local.dao.PaceNoteDao
import com.rallytrax.app.data.local.dao.TrackDao
import com.rallytrax.app.data.local.dao.TrackPointDao
import com.rallytrax.app.data.local.entity.PaceNoteEntity
import com.rallytrax.app.data.local.entity.TrackEntity
import com.rallytrax.app.data.local.entity.TrackPointEntity
import com.rallytrax.app.pacenotes.PaceNoteGenerator
import com.rallytrax.app.recording.LatLng
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ElevationPoint(
    val distanceFromStart: Double,
    val elevation: Double,
)

data class TrackDetailUiState(
    val track: TrackEntity? = null,
    val polylinePoints: List<LatLng> = emptyList(),
    val elevationProfile: List<ElevationPoint> = emptyList(),
    val tags: List<String> = emptyList(),
    val paceNotes: List<PaceNoteEntity> = emptyList(),
    val isLoading: Boolean = true,
    val isGeneratingNotes: Boolean = false,
    val selectedSensitivity: Int = 1, // 0=LOW, 1=MEDIUM, 2=HIGH
)

@HiltViewModel
class TrackDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val trackDao: TrackDao,
    private val trackPointDao: TrackPointDao,
    private val paceNoteDao: PaceNoteDao,
) : ViewModel() {

    private val trackId: String = checkNotNull(savedStateHandle["trackId"])

    private val _uiState = MutableStateFlow(TrackDetailUiState())
    val uiState: StateFlow<TrackDetailUiState> = _uiState.asStateFlow()

    private val _snackbarMessage = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val snackbarMessage = _snackbarMessage.asSharedFlow()

    private var cachedPoints: List<TrackPointEntity> = emptyList()

    init {
        loadTrack()
    }

    private fun loadTrack() {
        viewModelScope.launch {
            val track = trackDao.getTrackById(trackId)
            val points = trackPointDao.getPointsForTrackOnce(trackId)
            cachedPoints = points
            val polyline = points.map { LatLng(it.lat, it.lon) }

            // Build elevation profile
            val elevationProfile = buildElevationProfile(points)

            // Parse tags
            val tags = track?.tags
                ?.split(",")
                ?.map { it.trim() }
                ?.filter { it.isNotBlank() }
                ?: emptyList()

            // Load pace notes
            val paceNotes = paceNoteDao.getNotesForTrackOnce(trackId)

            _uiState.value = TrackDetailUiState(
                track = track,
                polylinePoints = polyline,
                elevationProfile = elevationProfile,
                tags = tags,
                paceNotes = paceNotes,
                isLoading = false,
            )
        }
    }

    private fun buildElevationProfile(points: List<TrackPointEntity>): List<ElevationPoint> {
        val result = mutableListOf<ElevationPoint>()
        var cumulativeDistance = 0.0
        var prevLat: Double? = null
        var prevLon: Double? = null

        for (point in points) {
            if (point.elevation == null) continue

            if (prevLat != null && prevLon != null) {
                cumulativeDistance += haversine(prevLat, prevLon, point.lat, point.lon)
            }
            prevLat = point.lat
            prevLon = point.lon

            result.add(ElevationPoint(cumulativeDistance, point.elevation))
        }
        return result
    }

    fun regeneratePaceNotes(sensitivityIndex: Int) {
        val points = cachedPoints
        if (points.isEmpty()) {
            _snackbarMessage.tryEmit("No track points available")
            return
        }

        _uiState.value = _uiState.value.copy(
            isGeneratingNotes = true,
            selectedSensitivity = sensitivityIndex,
        )

        viewModelScope.launch {
            try {
                val sensitivity = when (sensitivityIndex) {
                    0 -> PaceNoteGenerator.Sensitivity.LOW
                    2 -> PaceNoteGenerator.Sensitivity.HIGH
                    else -> PaceNoteGenerator.Sensitivity.MEDIUM
                }

                val notes = PaceNoteGenerator.generate(trackId, points, sensitivity)

                // Persist
                paceNoteDao.deleteNotesForTrack(trackId)
                if (notes.isNotEmpty()) {
                    paceNoteDao.insertNotes(notes)
                }

                _uiState.value = _uiState.value.copy(
                    paceNotes = notes,
                    isGeneratingNotes = false,
                )

                _snackbarMessage.tryEmit("Generated ${notes.size} pace notes")
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isGeneratingNotes = false)
                _snackbarMessage.tryEmit("Failed to generate pace notes: ${e.message}")
            }
        }
    }

    fun addTag(tag: String) {
        val cleanTag = tag.trim()
        if (cleanTag.isBlank()) return
        val track = _uiState.value.track ?: return
        val currentTags = _uiState.value.tags
        if (cleanTag in currentTags) return

        val newTags = currentTags + cleanTag
        val newTagString = newTags.joinToString(",")
        val updatedTrack = track.copy(tags = newTagString)

        viewModelScope.launch {
            trackDao.updateTrack(updatedTrack)
            _uiState.value = _uiState.value.copy(
                track = updatedTrack,
                tags = newTags,
            )
        }
    }

    fun removeTag(tag: String) {
        val track = _uiState.value.track ?: return
        val newTags = _uiState.value.tags - tag
        val newTagString = newTags.joinToString(",")
        val updatedTrack = track.copy(tags = newTagString)

        viewModelScope.launch {
            trackDao.updateTrack(updatedTrack)
            _uiState.value = _uiState.value.copy(
                track = updatedTrack,
                tags = newTags,
            )
        }
    }

    fun deleteTrack(onDeleted: () -> Unit) {
        viewModelScope.launch {
            trackDao.deleteTrack(trackId)
            onDeleted()
        }
    }

    fun exportGpx(context: Context) {
        val track = _uiState.value.track ?: return
        val points = cachedPoints
        if (points.isEmpty()) {
            _snackbarMessage.tryEmit("No track data to export")
            return
        }

        viewModelScope.launch {
            try {
                val fileName = "${track.name.replace(Regex("[^a-zA-Z0-9_ -]"), "_")}.gpx"
                val paceNotes = paceNoteDao.getNotesForTrackOnce(trackId)

                // Save to Downloads via MediaStore
                val contentValues = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                    put(MediaStore.Downloads.MIME_TYPE, "application/gpx+xml")
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }

                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                if (uri == null) {
                    _snackbarMessage.tryEmit("Failed to create file")
                    return@launch
                }

                resolver.openOutputStream(uri)?.use { outputStream ->
                    GpxExporter.export(track, points, outputStream, paceNotes)
                }

                // Open sharesheet
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/gpx+xml"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                val chooser = Intent.createChooser(shareIntent, "Share GPX track")
                chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(chooser)

                _snackbarMessage.tryEmit("Exported to Downloads: $fileName")
            } catch (e: Exception) {
                _snackbarMessage.tryEmit("Export failed: ${e.message}")
            }
        }
    }

    private fun haversine(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = kotlin.math.sin(dLat / 2) * kotlin.math.sin(dLat / 2) +
            kotlin.math.cos(Math.toRadians(lat1)) * kotlin.math.cos(Math.toRadians(lat2)) *
            kotlin.math.sin(dLon / 2) * kotlin.math.sin(dLon / 2)
        val c = 2 * kotlin.math.atan2(kotlin.math.sqrt(a), kotlin.math.sqrt(1 - a))
        return r * c
    }
}

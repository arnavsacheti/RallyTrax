package com.rallytrax.app.ui.settings

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Environment
import android.provider.MediaStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rallytrax.app.data.auth.AuthRepository
import com.rallytrax.app.data.auth.AuthState
import com.rallytrax.app.data.export.CsvExporter
import com.rallytrax.app.data.local.dao.PaceNoteDao
import com.rallytrax.app.data.local.dao.TrackDao
import com.rallytrax.app.data.local.dao.TrackPointDao
import com.rallytrax.app.data.preferences.GpsAccuracy
import com.rallytrax.app.data.preferences.MapProviderPreference
import com.rallytrax.app.data.preferences.ThemeMode
import com.rallytrax.app.data.preferences.UnitSystem
import com.rallytrax.app.data.preferences.UserPreferencesData
import com.rallytrax.app.data.preferences.UserPreferencesRepository
import com.rallytrax.app.data.local.dao.VehicleDao
import com.rallytrax.app.data.local.entity.VehicleEntity
import com.rallytrax.app.data.sync.SyncManager
import com.rallytrax.app.data.sync.SyncStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class SettingsUiState(
    val showDeleteConfirmation: Boolean = false,
    val showClearCacheConfirmation: Boolean = false,
    val trackCount: Int = 0,
    val isDeleting: Boolean = false,
    val isExporting: Boolean = false,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferencesRepository: UserPreferencesRepository,
    private val trackDao: TrackDao,
    private val trackPointDao: TrackPointDao,
    private val paceNoteDao: PaceNoteDao,
    private val syncManager: SyncManager,
    private val authRepository: AuthRepository,
    private val vehicleDao: VehicleDao,
) : ViewModel() {

    val preferences: StateFlow<UserPreferencesData> = preferencesRepository.preferences
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UserPreferencesData())

    val syncStatus: StateFlow<SyncStatus> = syncManager.syncStatus

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private val _snackbarMessage = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val snackbarMessage: SharedFlow<String> = _snackbarMessage.asSharedFlow()

    private val _isRestoring = MutableStateFlow(false)
    val isRestoring: StateFlow<Boolean> = _isRestoring.asStateFlow()

    private val isSignedIn: Boolean
        get() = authRepository.authState.value is AuthState.SignedIn

    init {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(trackCount = trackDao.getTrackCount())
        }
    }

    private fun onPreferenceChanged() {
        if (isSignedIn) {
            syncManager.scheduleDebouncedSync()
        }
    }

    fun syncNow() {
        syncManager.scheduleImmediateSync()
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            preferencesRepository.setThemeMode(mode)
            onPreferenceChanged()
        }
    }

    fun setUnitSystem(system: UnitSystem) {
        viewModelScope.launch {
            preferencesRepository.setUnitSystem(system)
            onPreferenceChanged()
        }
    }

    fun setGpsAccuracy(accuracy: GpsAccuracy) {
        viewModelScope.launch {
            preferencesRepository.setGpsAccuracy(accuracy)
            onPreferenceChanged()
        }
    }

    fun setMapProvider(provider: MapProviderPreference) {
        viewModelScope.launch {
            preferencesRepository.setMapProvider(provider)
            onPreferenceChanged()
        }
    }

    fun setTtsRate(rate: Float) {
        viewModelScope.launch {
            preferencesRepository.setTtsRate(rate)
            onPreferenceChanged()
        }
    }

    fun setTtsPitch(pitch: Float) {
        viewModelScope.launch {
            preferencesRepository.setTtsPitch(pitch)
            onPreferenceChanged()
        }
    }

    fun setTtsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setTtsEnabled(enabled)
            onPreferenceChanged()
        }
    }

    fun setPaceNoteSensitivity(sensitivity: Float) {
        viewModelScope.launch {
            preferencesRepository.setPaceNoteSensitivity(sensitivity)
        }
    }

    fun setCallTimingSeconds(seconds: Float) {
        viewModelScope.launch {
            preferencesRepository.setCallTimingSeconds(seconds)
        }
    }

    fun setHalfStepSeverityEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setHalfStepSeverityEnabled(enabled)
        }
    }

    fun setBackupTracksEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setBackupTracksEnabled(enabled)
        }
    }

    fun clearSyncError() {
        syncManager.clearError()
    }

    fun setKeepScreenOn(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setKeepScreenOn(enabled) }
    }

    fun showDeleteConfirmation() {
        _uiState.value = _uiState.value.copy(showDeleteConfirmation = true)
    }

    fun dismissDeleteConfirmation() {
        _uiState.value = _uiState.value.copy(showDeleteConfirmation = false)
    }

    fun deleteAllTracks() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isDeleting = true, showDeleteConfirmation = false)
            try {
                // Get all track IDs then delete everything
                trackDao.getAllTracks().collect { tracks ->
                    val ids = tracks.map { it.id }
                    if (ids.isNotEmpty()) {
                        trackDao.deleteTracks(ids)
                    }
                    _uiState.value = _uiState.value.copy(
                        isDeleting = false,
                        trackCount = 0,
                    )
                    return@collect
                }
            } catch (_: Exception) {
                _uiState.value = _uiState.value.copy(isDeleting = false)
            }
        }
    }

    // ── CSV Export ──────────────────────────────────────────────────────────

    fun exportCsv(context: Context) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isExporting = true)
            try {
                val tracksDeferred = async(Dispatchers.IO) { trackDao.getStintsOnce() }
                val vehiclesDeferred = async(Dispatchers.IO) { vehicleDao.getAllVehicles().first() }
                val tracks = tracksDeferred.await()
                val vehicles = vehiclesDeferred.await()

                if (tracks.isEmpty()) {
                    _snackbarMessage.tryEmit("No drives to export")
                    return@launch
                }

                val fileName = "rallytrax_export_${System.currentTimeMillis()}.csv"

                val contentValues = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                    put(MediaStore.Downloads.MIME_TYPE, "text/csv")
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }

                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                if (uri == null) {
                    _snackbarMessage.tryEmit("Failed to create file")
                    return@launch
                }

                withContext(Dispatchers.IO) {
                    resolver.openOutputStream(uri)?.use { outputStream ->
                        CsvExporter.export(tracks, vehicles, outputStream)
                    }
                }

                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/csv"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                val chooser = Intent.createChooser(shareIntent, "Share CSV export")
                chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(chooser)

                _snackbarMessage.tryEmit("Exported ${tracks.size} drives to Downloads: $fileName")
            } catch (e: Exception) {
                _snackbarMessage.tryEmit("Export failed: ${e.message}")
            } finally {
                _uiState.value = _uiState.value.copy(isExporting = false)
            }
        }
    }

    // ── Cloud Restore ────────────────────────────────────────────────────────

    fun restoreFromCloud() {
        if (_isRestoring.value) return
        viewModelScope.launch {
            _isRestoring.value = true
            try {
                val email = authRepository.getCurrentUser()?.email
                if (email == null) {
                    _snackbarMessage.tryEmit("Sign in required to restore from cloud")
                    return@launch
                }

                val restoredCount = syncManager.restoreTracks()

                if (restoredCount > 0) {
                    _uiState.value = _uiState.value.copy(
                        trackCount = trackDao.getTrackCount(),
                    )
                    _snackbarMessage.tryEmit("Restored $restoredCount track${if (restoredCount != 1) "s" else ""} from cloud")
                } else {
                    _snackbarMessage.tryEmit("No new tracks to restore")
                }
            } catch (e: Exception) {
                _snackbarMessage.tryEmit("Restore failed: ${e.message}")
            } finally {
                _isRestoring.value = false
            }
        }
    }

    // ── Archived vehicles ───────────────────────────────────────────────────

    val archivedVehicles: StateFlow<List<VehicleEntity>> = vehicleDao.getArchivedVehicles()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun restoreVehicle(vehicleId: String) {
        viewModelScope.launch {
            vehicleDao.unarchiveVehicle(vehicleId)
        }
    }
}

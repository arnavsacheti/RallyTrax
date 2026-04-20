package com.rallytrax.app.ui.yearinreview

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rallytrax.app.data.local.dao.TrackDao
import com.rallytrax.app.data.preferences.UserPreferencesData
import com.rallytrax.app.data.preferences.UserPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

data class YearInReviewState(
    val isLoading: Boolean = true,
    val year: Int = LocalDate.now().year,
    val trackCount: Int = 0,
    val totalDistanceMeters: Double = 0.0,
    val totalDurationMs: Long = 0L,
    val maxSpeedMps: Double = 0.0,
    val totalElevationGainM: Double = 0.0,
    val activeDays: Int = 0,
    val longestTrackDistanceMeters: Double = 0.0,
    val longestTrackName: String? = null,
    val fastestTrackMaxSpeedMps: Double = 0.0,
    val fastestTrackName: String? = null,
    val preferences: UserPreferencesData = UserPreferencesData(),
)

@HiltViewModel
class YearInReviewViewModel @Inject constructor(
    private val trackDao: TrackDao,
    preferencesRepository: UserPreferencesRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(YearInReviewState())
    val state: StateFlow<YearInReviewState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            preferencesRepository.preferences.collect { prefs ->
                _state.value = _state.value.copy(preferences = prefs)
            }
        }
        viewModelScope.launch { load(LocalDate.now().year) }
    }

    private suspend fun load(year: Int) {
        val zone = ZoneId.systemDefault()
        val startMs = LocalDate.of(year, 1, 1).atStartOfDay(zone).toInstant().toEpochMilli()
        val endMs = LocalDate.of(year + 1, 1, 1).atStartOfDay(zone).toInstant().toEpochMilli() - 1

        val tracks = withContext(Dispatchers.IO) { trackDao.getTracksBetween(startMs, endMs) }

        if (tracks.isEmpty()) {
            _state.value = _state.value.copy(isLoading = false, year = year, trackCount = 0)
            return
        }

        val longest = tracks.maxByOrNull { it.distanceMeters }
        val fastest = tracks.maxByOrNull { it.maxSpeedMps }
        val activeDays = tracks.map {
            java.time.Instant.ofEpochMilli(it.recordedAt).atZone(zone).toLocalDate()
        }.distinct().size

        _state.value = _state.value.copy(
            isLoading = false,
            year = year,
            trackCount = tracks.size,
            totalDistanceMeters = tracks.sumOf { it.distanceMeters },
            totalDurationMs = tracks.sumOf { it.durationMs },
            maxSpeedMps = tracks.maxOf { it.maxSpeedMps },
            totalElevationGainM = tracks.sumOf { it.elevationGainM },
            activeDays = activeDays,
            longestTrackDistanceMeters = longest?.distanceMeters ?: 0.0,
            longestTrackName = longest?.name,
            fastestTrackMaxSpeedMps = fastest?.maxSpeedMps ?: 0.0,
            fastestTrackName = fastest?.name,
        )
    }
}

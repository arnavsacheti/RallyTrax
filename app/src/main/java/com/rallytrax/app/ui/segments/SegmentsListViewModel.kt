package com.rallytrax.app.ui.segments

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rallytrax.app.data.local.entity.SegmentEntity
import com.rallytrax.app.data.preferences.UserPreferencesData
import com.rallytrax.app.data.preferences.UserPreferencesRepository
import com.rallytrax.app.data.repository.SegmentRepository
import com.rallytrax.app.data.repository.SegmentStats
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SegmentListItem(
    val segment: SegmentEntity,
    val stats: SegmentStats,
)

data class SegmentsListUiState(
    val segments: List<SegmentListItem> = emptyList(),
    val isLoading: Boolean = true,
)

@HiltViewModel
class SegmentsListViewModel @Inject constructor(
    private val segmentRepository: SegmentRepository,
    preferencesRepository: UserPreferencesRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SegmentsListUiState())
    val uiState: StateFlow<SegmentsListUiState> = _uiState.asStateFlow()

    val preferences: StateFlow<UserPreferencesData> = preferencesRepository.preferences
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UserPreferencesData())

    init {
        loadSegments()
    }

    private fun loadSegments() {
        viewModelScope.launch {
            segmentRepository.getAllSegments().collect { segments ->
                val items = segments.map { segment ->
                    val stats = segmentRepository.getSegmentStats(segment.id)
                    SegmentListItem(segment, stats)
                }
                _uiState.value = SegmentsListUiState(
                    segments = items,
                    isLoading = false,
                )
            }
        }
    }

    fun toggleFavorite(segmentId: String) {
        viewModelScope.launch {
            segmentRepository.toggleFavorite(segmentId)
        }
    }

    fun deleteSegment(segmentId: String) {
        viewModelScope.launch {
            segmentRepository.deleteSegment(segmentId)
        }
    }
}

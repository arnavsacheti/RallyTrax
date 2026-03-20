package com.rallytrax.app.ui.replay

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rallytrax.app.data.local.dao.PaceNoteDao
import com.rallytrax.app.data.local.dao.TrackDao
import com.rallytrax.app.data.local.entity.TrackEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ReplayTrackListViewModel @Inject constructor(
    private val trackDao: TrackDao,
    private val paceNoteDao: PaceNoteDao,
) : ViewModel() {

    // Show all tracks that have pace notes
    val tracks: StateFlow<List<TrackEntity>> = trackDao.getAllTracks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // We filter in the UI — any track is replayable,
    // but ideally ones with pace notes are preferred
}

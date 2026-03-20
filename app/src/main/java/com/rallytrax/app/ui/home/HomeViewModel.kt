package com.rallytrax.app.ui.home

import androidx.lifecycle.ViewModel
import com.rallytrax.app.data.local.dao.TrackDao
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val trackDao: TrackDao,
) : ViewModel()

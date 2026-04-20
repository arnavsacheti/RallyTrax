package com.rallytrax.app.ui.trips

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rallytrax.app.data.local.entity.CommonRouteEntity
import com.rallytrax.app.data.repository.CommonRouteRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CommonRoutesUiState(
    val routes: List<CommonRouteEntity> = emptyList(),
    val isLoading: Boolean = true,
)

@HiltViewModel
class CommonRoutesViewModel @Inject constructor(
    private val commonRouteRepository: CommonRouteRepository,
) : ViewModel() {

    val uiState: StateFlow<CommonRoutesUiState> = commonRouteRepository
        .getAllCommonRoutes()
        .map { routes ->
            CommonRoutesUiState(
                routes = routes,
                isLoading = false,
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CommonRoutesUiState())

    fun refreshDetection() {
        viewModelScope.launch {
            commonRouteRepository.detectCommonRoutes()
        }
    }
}

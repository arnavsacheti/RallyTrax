package com.rallytrax.app.ui.achievements

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rallytrax.app.data.achievements.AchievementTracker
import com.rallytrax.app.data.local.dao.AchievementDao
import com.rallytrax.app.data.local.entity.AchievementEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AchievementsUiState(
    val achievements: List<AchievementEntity> = emptyList(),
    val unlockedCount: Int = 0,
    val totalCount: Int = 0,
    val isLoading: Boolean = true,
)

@HiltViewModel
class AchievementsViewModel @Inject constructor(
    private val achievementDao: AchievementDao,
    private val achievementTracker: AchievementTracker,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AchievementsUiState())
    val uiState = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            achievementTracker.seedAchievements()

            achievementDao.getAllAchievements().collect { achievements ->
                val unlocked = achievements.count { it.unlockedAt != null }
                _uiState.update {
                    it.copy(
                        achievements = achievements,
                        unlockedCount = unlocked,
                        totalCount = achievements.size,
                        isLoading = false,
                    )
                }
            }
        }
    }
}

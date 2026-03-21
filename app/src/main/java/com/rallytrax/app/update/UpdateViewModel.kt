package com.rallytrax.app.update

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rallytrax.app.BuildConfig
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class UpdateUiState(
    val isChecking: Boolean = false,
    val updateAvailable: Boolean = false,
    val releaseInfo: ReleaseInfo? = null,
    val currentVersion: String = BuildConfig.VERSION_NAME,
    val lastCheckError: String? = null,
    val dismissed: Boolean = false,
)

@HiltViewModel
class UpdateViewModel @Inject constructor(
    private val updateChecker: UpdateChecker,
    val updateManager: AppUpdateManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(UpdateUiState())
    val uiState: StateFlow<UpdateUiState> = _uiState.asStateFlow()

    val downloadState: StateFlow<DownloadState> = updateManager.state

    init {
        checkForUpdate()
    }

    fun checkForUpdate() {
        if (_uiState.value.isChecking) return

        _uiState.value = _uiState.value.copy(
            isChecking = true,
            lastCheckError = null,
        )

        viewModelScope.launch {
            val release = updateChecker.fetchLatestRelease()
            if (release == null) {
                _uiState.value = _uiState.value.copy(
                    isChecking = false,
                    lastCheckError = "Could not reach GitHub",
                )
                return@launch
            }

            val currentVersion = BuildConfig.VERSION_NAME
            val isNewer = updateChecker.isNewer(currentVersion, release.versionName)

            _uiState.value = _uiState.value.copy(
                isChecking = false,
                updateAvailable = isNewer,
                releaseInfo = release,
            )
        }
    }

    fun startDownload() {
        val release = _uiState.value.releaseInfo ?: return
        val url = release.apkDownloadUrl ?: return

        updateManager.startDownload(url, release.versionName)

        // Poll for progress in the background
        viewModelScope.launch {
            updateManager.pollProgress()
        }
    }

    fun installUpdate(context: Context) {
        updateManager.installUpdate(context)
    }

    fun resetDownload() {
        updateManager.reset()
    }

    fun dismissUpdate() {
        _uiState.value = _uiState.value.copy(dismissed = true)
    }
}

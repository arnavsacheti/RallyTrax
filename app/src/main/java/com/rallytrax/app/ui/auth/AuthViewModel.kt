package com.rallytrax.app.ui.auth

import android.app.Activity
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rallytrax.app.data.auth.AuthRepository
import com.rallytrax.app.data.auth.AuthState
import com.rallytrax.app.data.preferences.UserPreferencesRepository
import com.rallytrax.app.data.sync.SyncManager
import com.rallytrax.app.data.sync.SyncStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val syncManager: SyncManager,
    private val preferencesRepository: UserPreferencesRepository,
) : ViewModel() {

    val authState: StateFlow<AuthState> = authRepository.authState

    val syncStatus: StateFlow<SyncStatus> = syncManager.syncStatus

    private val _showFirstSignInSheet = MutableStateFlow(false)
    val showFirstSignInSheet: StateFlow<Boolean> = _showFirstSignInSheet.asStateFlow()

    fun signIn(activity: Activity) {
        viewModelScope.launch {
            val result = authRepository.signInWithGoogle(activity)
            result.onSuccess { user ->
                // Store email for background sync
                preferencesRepository.setSignedInEmail(user.email)

                // Perform initial sync via Firestore (no Drive authorization needed)
                try {
                    syncManager.performSync()
                } catch (e: Exception) {
                    Log.e(TAG, "Initial sync failed", e)
                }
                syncManager.schedulePeriodicSync()

                _showFirstSignInSheet.value = true
            }
        }
    }

    fun dismissFirstSignInSheet() {
        _showFirstSignInSheet.value = false
    }

    fun signOut() {
        viewModelScope.launch {
            authRepository.signOut()
            preferencesRepository.setSignedInEmail(null)
            syncManager.cancelPeriodicSync()
        }
    }

    companion object {
        private const val TAG = "AuthViewModel"
    }
}

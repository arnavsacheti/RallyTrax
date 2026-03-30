package com.rallytrax.app.ui.social

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rallytrax.app.data.social.SocialRepository
import com.rallytrax.app.data.social.UserProfile
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class FriendsUiState(
    val following: List<UserProfile> = emptyList(),
    val followers: List<UserProfile> = emptyList(),
    val searchResults: List<UserProfile> = emptyList(),
    val searchQuery: String = "",
    val isSearching: Boolean = false,
    val isLoading: Boolean = true,
    val error: String? = null,
)

@HiltViewModel
class FriendsViewModel @Inject constructor(
    private val socialRepository: SocialRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(FriendsUiState())
    val uiState: StateFlow<FriendsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            socialRepository.getFollowing().collect { list ->
                _uiState.update { it.copy(following = list, isLoading = false) }
            }
        }
        viewModelScope.launch {
            socialRepository.getFollowers().collect { list ->
                _uiState.update { it.copy(followers = list) }
            }
        }
    }

    fun onSearchQueryChanged(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun search(email: String) {
        if (email.isBlank()) {
            _uiState.update { it.copy(searchResults = emptyList(), isSearching = false) }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isSearching = true, error = null) }
            try {
                val results = socialRepository.searchUsersByEmail(email)
                _uiState.update { it.copy(searchResults = results, isSearching = false) }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        searchResults = emptyList(),
                        isSearching = false,
                        error = e.message,
                    )
                }
            }
        }
    }

    fun follow(uid: String) {
        viewModelScope.launch {
            try {
                socialRepository.follow(uid)
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    fun unfollow(uid: String) {
        viewModelScope.launch {
            try {
                socialRepository.unfollow(uid)
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }
}

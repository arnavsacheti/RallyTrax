package com.rallytrax.app.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.ui.graphics.vector.ImageVector
import kotlinx.serialization.Serializable

data class TopLevelRoute(
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val route: Any,
)

@Serializable data object HomeRoute
@Serializable data object LibraryRoute
@Serializable data object ReplayRoute
@Serializable data object SettingsRoute
@Serializable data object OnboardingRoute
@Serializable data object RecordingRoute
@Serializable data class TrackDetailRoute(val trackId: String)
@Serializable data class ReplayHudRoute(val trackId: String)

val topLevelRoutes = listOf(
    TopLevelRoute(
        label = "Home",
        selectedIcon = Icons.Filled.Home,
        unselectedIcon = Icons.Outlined.Home,
        route = HomeRoute,
    ),
    TopLevelRoute(
        label = "Library",
        selectedIcon = Icons.Filled.VideoLibrary,
        unselectedIcon = Icons.Outlined.VideoLibrary,
        route = LibraryRoute,
    ),
    TopLevelRoute(
        label = "Replay",
        selectedIcon = Icons.Filled.PlayArrow,
        unselectedIcon = Icons.Outlined.PlayArrow,
        route = ReplayRoute,
    ),
    TopLevelRoute(
        label = "Settings",
        selectedIcon = Icons.Filled.Settings,
        unselectedIcon = Icons.Outlined.Settings,
        route = SettingsRoute,
    ),
)

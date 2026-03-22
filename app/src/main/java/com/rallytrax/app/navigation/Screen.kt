package com.rallytrax.app.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.ui.graphics.vector.ImageVector
import kotlinx.serialization.Serializable

data class TopLevelRoute(
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val route: Any,
)

@Serializable data object HomeRoute
@Serializable data class ExploreRoute(
    val focusLat: Double? = null,
    val focusLng: Double? = null,
)
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
        label = "Explore",
        selectedIcon = Icons.Filled.Explore,
        unselectedIcon = Icons.Outlined.Explore,
        route = ExploreRoute(),
    ),
    TopLevelRoute(
        label = "Library",
        selectedIcon = Icons.Filled.Folder,
        unselectedIcon = Icons.Outlined.Folder,
        route = LibraryRoute,
    ),
)

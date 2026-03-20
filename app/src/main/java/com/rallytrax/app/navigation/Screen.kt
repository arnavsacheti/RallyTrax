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

sealed interface TopLevelRoute {
    val label: String
    val selectedIcon: ImageVector
    val unselectedIcon: ImageVector
    val route: Any
}

@Serializable data object HomeRoute
@Serializable data object LibraryRoute
@Serializable data object ReplayRoute
@Serializable data object SettingsRoute

val topLevelRoutes = listOf(
    object : TopLevelRoute {
        override val label = "Home"
        override val selectedIcon = Icons.Filled.Home
        override val unselectedIcon = Icons.Outlined.Home
        override val route: Any = HomeRoute
    },
    object : TopLevelRoute {
        override val label = "Library"
        override val selectedIcon = Icons.Filled.VideoLibrary
        override val unselectedIcon = Icons.Outlined.VideoLibrary
        override val route: Any = LibraryRoute
    },
    object : TopLevelRoute {
        override val label = "Replay"
        override val selectedIcon = Icons.Filled.PlayArrow
        override val unselectedIcon = Icons.Outlined.PlayArrow
        override val route: Any = ReplayRoute
    },
    object : TopLevelRoute {
        override val label = "Settings"
        override val selectedIcon = Icons.Filled.Settings
        override val unselectedIcon = Icons.Outlined.Settings
        override val route: Any = SettingsRoute
    },
)

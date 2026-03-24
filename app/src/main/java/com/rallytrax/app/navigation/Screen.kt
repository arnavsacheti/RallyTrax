package com.rallytrax.app.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Route
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
@Serializable data object GarageRoute
@Serializable data object ProfileRoute
@Serializable data object ReplayRoute
@Serializable data object SettingsRoute
@Serializable data object OnboardingRoute
@Serializable data object RecordingRoute
@Serializable data class TrackDetailRoute(val trackId: String)
@Serializable data class ReplayHudRoute(val trackId: String)
@Serializable data class VehicleDetailRoute(val vehicleId: String)
@Serializable data class ActivitySummaryRoute(val trackId: String)
@Serializable data class EditTrackRoute(val trackId: String)
@Serializable data object StintsRoute
@Serializable data object AchievementsRoute

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
        label = "Routes",
        selectedIcon = Icons.Filled.Route,
        unselectedIcon = Icons.Outlined.Route,
        route = LibraryRoute,
    ),
    TopLevelRoute(
        label = "Profile",
        selectedIcon = Icons.Filled.Person,
        unselectedIcon = Icons.Outlined.Person,
        route = ProfileRoute,
    ),
)

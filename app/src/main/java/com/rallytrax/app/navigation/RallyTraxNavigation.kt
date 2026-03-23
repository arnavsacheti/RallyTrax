package com.rallytrax.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.rallytrax.app.data.auth.AuthState
import com.rallytrax.app.ui.explore.ExploreScreen
import com.rallytrax.app.ui.garage.GarageScreen
import com.rallytrax.app.ui.garage.VehicleDetailScreen
import com.rallytrax.app.ui.home.HomeScreen
import com.rallytrax.app.ui.library.LibraryScreen
import com.rallytrax.app.ui.onboarding.OnboardingScreen
import com.rallytrax.app.ui.recording.ActivitySummaryScreen
import com.rallytrax.app.ui.recording.RecordingScreen
import com.rallytrax.app.ui.trackdetail.EditTrackScreen
import com.rallytrax.app.ui.replay.ReplayScreen
import com.rallytrax.app.ui.replay.ReplayHudScreen
import com.rallytrax.app.ui.settings.SettingsScreen
import com.rallytrax.app.ui.trackdetail.TrackDetailScreen

@Composable
fun RallyTraxNavHost(
    navController: NavHostController,
    startDestination: Any = HomeRoute,
    modifier: Modifier = Modifier,
    authState: AuthState = AuthState.SignedOut,
    isSignedIn: Boolean = false,
    userPhotoUrl: String? = null,
    onSignIn: () -> Unit = {},
    onProfileClick: () -> Unit = {},
    onSignOut: () -> Unit = {},
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier,
    ) {
        composable<OnboardingRoute> {
            OnboardingScreen(
                onComplete = {
                    navController.navigate(HomeRoute) {
                        popUpTo<OnboardingRoute> { inclusive = true }
                    }
                },
            )
        }
        composable<HomeRoute> {
            HomeScreen(
                onStartRecording = {
                    navController.navigate(RecordingRoute)
                },
                onTrackClick = { trackId ->
                    navController.navigate(TrackDetailRoute(trackId))
                },
                onReplayTrack = { trackId ->
                    navController.navigate(ReplayHudRoute(trackId))
                },
                onNavigateToSettings = {
                    navController.navigate(SettingsRoute)
                },
                authState = authState,
                isSignedIn = isSignedIn,
                userPhotoUrl = userPhotoUrl,
                onSignIn = onSignIn,
                onProfileClick = onProfileClick,
            )
        }
        composable<ExploreRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<ExploreRoute>()
            ExploreScreen(
                focusLat = route.focusLat,
                focusLng = route.focusLng,
                onNavigateToSettings = {
                    navController.navigate(SettingsRoute)
                },
                onViewDetail = { trackId ->
                    navController.navigate(TrackDetailRoute(trackId))
                },
                onReplayTrack = { trackId ->
                    navController.navigate(ReplayHudRoute(trackId))
                },
                isSignedIn = isSignedIn,
                userPhotoUrl = userPhotoUrl,
                onProfileClick = onProfileClick,
            )
        }
        composable<LibraryRoute> {
            LibraryScreen(
                onTrackClick = { trackId ->
                    navController.navigate(TrackDetailRoute(trackId))
                },
                onReplayTrack = { trackId ->
                    navController.navigate(ReplayHudRoute(trackId))
                },
                onNavigateToSettings = {
                    navController.navigate(SettingsRoute)
                },
                isSignedIn = isSignedIn,
                userPhotoUrl = userPhotoUrl,
                onProfileClick = onProfileClick,
            )
        }
        composable<GarageRoute> {
            GarageScreen(
                onVehicleClick = { vehicleId ->
                    navController.navigate(VehicleDetailRoute(vehicleId))
                },
                onNavigateToSettings = {
                    navController.navigate(SettingsRoute)
                },
                isSignedIn = isSignedIn,
                userPhotoUrl = userPhotoUrl,
                onProfileClick = onProfileClick,
            )
        }
        composable<VehicleDetailRoute> {
            VehicleDetailScreen(
                onBack = { navController.popBackStack() },
                onTrackClick = { trackId ->
                    navController.navigate(TrackDetailRoute(trackId))
                },
            )
        }
        composable<ReplayRoute> {
            ReplayScreen(
                onTrackSelected = { trackId ->
                    navController.navigate(ReplayHudRoute(trackId))
                },
            )
        }
        composable<ReplayHudRoute> {
            ReplayHudScreen(
                onExit = {
                    navController.popBackStack()
                },
            )
        }
        composable<SettingsRoute> {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                authState = authState,
                isSignedIn = isSignedIn,
                userPhotoUrl = userPhotoUrl,
                onSignIn = onSignIn,
                onSignOut = onSignOut,
            )
        }
        composable<RecordingRoute> {
            RecordingScreen(
                onTrackSaved = { trackId ->
                    navController.navigate(ActivitySummaryRoute(trackId)) {
                        popUpTo<RecordingRoute> { inclusive = true }
                    }
                },
            )
        }
        composable<ActivitySummaryRoute> {
            ActivitySummaryScreen(
                onNavigateToDetail = { trackId ->
                    navController.navigate(TrackDetailRoute(trackId)) {
                        popUpTo<ActivitySummaryRoute> { inclusive = true }
                    }
                },
                onNavigateBack = {
                    navController.navigate(HomeRoute) {
                        popUpTo<ActivitySummaryRoute> { inclusive = true }
                    }
                },
            )
        }
        composable<TrackDetailRoute> {
            TrackDetailScreen(
                onBack = {
                    navController.popBackStack()
                },
                onReplay = { trackId ->
                    navController.navigate(ReplayHudRoute(trackId))
                },
                onEdit = { trackId ->
                    navController.navigate(EditTrackRoute(trackId))
                },
            )
        }
        composable<EditTrackRoute> {
            EditTrackScreen(
                onBack = {
                    navController.popBackStack()
                },
                onDeleted = {
                    navController.navigate(HomeRoute) {
                        popUpTo<HomeRoute> { inclusive = false }
                    }
                },
            )
        }
    }
}

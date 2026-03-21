package com.rallytrax.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.rallytrax.app.ui.explore.ExploreScreen
import com.rallytrax.app.ui.home.HomeScreen
import com.rallytrax.app.ui.library.LibraryScreen
import com.rallytrax.app.ui.onboarding.OnboardingScreen
import com.rallytrax.app.ui.recording.RecordingScreen
import com.rallytrax.app.ui.replay.ReplayScreen
import com.rallytrax.app.ui.replay.ReplayHudScreen
import com.rallytrax.app.ui.settings.SettingsScreen
import com.rallytrax.app.ui.trackdetail.TrackDetailScreen

@Composable
fun RallyTraxNavHost(
    navController: NavHostController,
    startDestination: Any = HomeRoute,
    modifier: Modifier = Modifier,
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
            )
        }
        composable<ExploreRoute> {
            ExploreScreen(
                onNavigateToSettings = {
                    navController.navigate(SettingsRoute)
                },
            )
        }
        composable<LibraryRoute> {
            LibraryScreen(
                onTrackClick = { trackId ->
                    navController.navigate(TrackDetailRoute(trackId))
                },
                onNavigateToSettings = {
                    navController.navigate(SettingsRoute)
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
            )
        }
        composable<RecordingRoute> {
            RecordingScreen(
                onTrackSaved = { trackId ->
                    navController.navigate(TrackDetailRoute(trackId)) {
                        popUpTo<RecordingRoute> { inclusive = true }
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
            )
        }
    }
}

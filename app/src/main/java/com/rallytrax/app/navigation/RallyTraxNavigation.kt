package com.rallytrax.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.rallytrax.app.ui.home.HomeScreen
import com.rallytrax.app.ui.library.LibraryScreen
import com.rallytrax.app.ui.recording.RecordingScreen
import com.rallytrax.app.ui.replay.ReplayScreen
import com.rallytrax.app.ui.settings.SettingsScreen
import com.rallytrax.app.ui.trackdetail.TrackDetailScreen

@Composable
fun RallyTraxNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = HomeRoute,
        modifier = modifier,
    ) {
        composable<HomeRoute> {
            HomeScreen(
                onStartRecording = {
                    navController.navigate(RecordingRoute)
                },
            )
        }
        composable<LibraryRoute> {
            LibraryScreen()
        }
        composable<ReplayRoute> {
            ReplayScreen()
        }
        composable<SettingsRoute> {
            SettingsScreen()
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
                    navController.popBackStack(HomeRoute, inclusive = false)
                },
            )
        }
    }
}

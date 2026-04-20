package com.rallytrax.app.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.rallytrax.app.data.auth.AuthState
import com.rallytrax.app.ui.theme.RallyTraxMotion
import com.rallytrax.app.ui.achievements.AchievementsScreen
import com.rallytrax.app.ui.social.FriendsScreen
import com.rallytrax.app.ui.explore.ExploreScreen
import com.rallytrax.app.ui.garage.EditVehicleScreen
import com.rallytrax.app.ui.garage.GarageScreen
import com.rallytrax.app.ui.garage.VehicleDetailScreen
import com.rallytrax.app.ui.home.HomeScreen
import com.rallytrax.app.ui.library.LibraryScreen
import com.rallytrax.app.ui.onboarding.OnboardingScreen
import com.rallytrax.app.ui.profile.ProfileScreen
import com.rallytrax.app.ui.recording.ActivitySummaryScreen
import com.rallytrax.app.ui.recording.RecordingScreen
import com.rallytrax.app.ui.stints.StintsScreen
import com.rallytrax.app.ui.trips.CommonRoutesScreen
import com.rallytrax.app.ui.trips.TripDetailScreen
import com.rallytrax.app.ui.trips.TripsScreen
import com.rallytrax.app.ui.trackdetail.EditTrackScreen
import com.rallytrax.app.ui.replay.ReplayScreen
import com.rallytrax.app.ui.replay.ReplayHudScreen
import com.rallytrax.app.ui.settings.SettingsScreen
import com.rallytrax.app.ui.trackdetail.TrackDetailScreen

@Composable
fun RallyTraxNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
    startDestination: Any = HomeRoute,
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
        enterTransition = {
            fadeIn(animationSpec = RallyTraxMotion.slowEffects()) +
                slideIntoContainer(
                    AnimatedContentTransitionScope.SlideDirection.Start,
                    animationSpec = RallyTraxMotion.slowSpatial(),
                )
        },
        exitTransition = {
            fadeOut(animationSpec = RallyTraxMotion.slowEffects()) +
                slideOutOfContainer(
                    AnimatedContentTransitionScope.SlideDirection.Start,
                    animationSpec = RallyTraxMotion.slowSpatial(),
                )
        },
        popEnterTransition = {
            fadeIn(animationSpec = RallyTraxMotion.slowEffects()) +
                slideIntoContainer(
                    AnimatedContentTransitionScope.SlideDirection.End,
                    animationSpec = RallyTraxMotion.slowSpatial(),
                )
        },
        popExitTransition = {
            fadeOut(animationSpec = RallyTraxMotion.slowEffects()) +
                slideOutOfContainer(
                    AnimatedContentTransitionScope.SlideDirection.End,
                    animationSpec = RallyTraxMotion.slowSpatial(),
                )
        },
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
                onVehicleClick = { vehicleId ->
                    navController.navigate(VehicleDetailRoute(vehicleId))
                },
                onViewAllAchievements = {
                    navController.navigate(AchievementsRoute)
                },
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
        composable<ProfileRoute> {
            ProfileScreen(
                onNavigateToGarage = {
                    navController.navigate(GarageRoute)
                },
                onNavigateToStints = {
                    navController.navigate(StintsRoute)
                },
                onNavigateToTrips = {
                    navController.navigate(TripsRoute)
                },
                onNavigateToCommonRoutes = {
                    navController.navigate(CommonRoutesRoute)
                },
                onNavigateToAchievements = {
                    navController.navigate(AchievementsRoute)
                },
                onNavigateToFriends = {
                    navController.navigate(FriendsRoute)
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
        composable<AchievementsRoute> {
            AchievementsScreen(
                onBack = { navController.popBackStack() },
            )
        }
        composable<FriendsRoute> {
            FriendsScreen(
                onBack = { navController.popBackStack() },
            )
        }
        composable<StintsRoute> {
            StintsScreen(
                onTrackClick = { trackId ->
                    navController.navigate(TrackDetailRoute(trackId))
                },
                onReplayTrack = { trackId ->
                    navController.navigate(ReplayHudRoute(trackId))
                },
                onVehicleClick = { vehicleId ->
                    navController.navigate(VehicleDetailRoute(vehicleId))
                },
                onBack = { navController.popBackStack() },
            )
        }
        composable<TripsRoute> {
            TripsScreen(
                onTripClick = { tripId -> navController.navigate(TripDetailRoute(tripId)) },
                onBack = { navController.popBackStack() },
            )
        }
        composable<TripDetailRoute> {
            TripDetailScreen(
                onBack = { navController.popBackStack() },
                onTrackClick = { trackId -> navController.navigate(TrackDetailRoute(trackId)) },
            )
        }
        composable<CommonRoutesRoute> {
            CommonRoutesScreen(
                onBack = { navController.popBackStack() },
                onRouteClick = { routeId -> navController.navigate(CommonRouteDetailRoute(routeId)) },
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
                onBack = { navController.popBackStack() },
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
                onEdit = { vehicleId ->
                    navController.navigate(EditVehicleRoute(vehicleId))
                },
            )
        }
        composable<EditVehicleRoute> {
            EditVehicleScreen(
                onBack = { navController.popBackStack() },
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
                onViewAllSegments = {
                    navController.navigate(SegmentsListRoute)
                },
                onSegmentClick = { segmentId ->
                    navController.navigate(SegmentDetailRoute(segmentId))
                },
                onVehicleClick = { vehicleId ->
                    navController.navigate(VehicleDetailRoute(vehicleId))
                },
                onNavigateToTrip = { tripId ->
                    navController.navigate(TripDetailRoute(tripId))
                },
            )
        }
        composable<SegmentsListRoute> {
            com.rallytrax.app.ui.segments.SegmentsListScreen(
                onSegmentClick = { segmentId ->
                    navController.navigate(SegmentDetailRoute(segmentId))
                },
                onBack = { navController.popBackStack() },
            )
        }
        composable<SegmentDetailRoute> {
            com.rallytrax.app.ui.segments.SegmentDetailScreen(
                onBack = { navController.popBackStack() },
                onTrackClick = { trackId ->
                    navController.navigate(TrackDetailRoute(trackId))
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

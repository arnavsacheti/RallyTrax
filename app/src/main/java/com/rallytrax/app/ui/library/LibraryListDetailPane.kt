package com.rallytrax.app.ui.library

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Route
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.layout.AnimatedPane
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldRole
import androidx.compose.material3.adaptive.navigation.NavigableListDetailPaneScaffold
import androidx.compose.material3.adaptive.navigation.rememberListDetailPaneScaffoldNavigator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.rallytrax.app.navigation.TrackDetailRoute
import com.rallytrax.app.ui.trackdetail.TrackDetailScreen
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

@Serializable
private data object LibraryDetailEmpty

/**
 * List-detail adaptive wrapper for the Library route. On Compact windows it
 * behaves like the old single-pane experience — the list fills the screen and
 * tapping a track slides the detail pane over it. On Medium+ windows the list
 * and the detail pane sit side by side and tapping swaps the detail content
 * without leaving Library.
 *
 * The detail pane hosts a nested [NavHost] so [TrackDetailScreen]'s Hilt
 * ViewModel reads `trackId` from the route's `SavedStateHandle` — same wiring
 * the top-level `TrackDetailRoute` composable uses, just scoped to this pane.
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun LibraryListDetailPane(
    onReplayTrack: (String) -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToEditTrack: (String) -> Unit,
    onNavigateToSegmentsList: () -> Unit,
    onNavigateToSegmentDetail: (String) -> Unit,
    onNavigateToVehicleDetail: (String) -> Unit,
    onNavigateToTrip: (String) -> Unit,
    isSignedIn: Boolean,
    userPhotoUrl: String?,
    onProfileClick: () -> Unit,
) {
    val navigator = rememberListDetailPaneScaffoldNavigator<String>()
    val scope = rememberCoroutineScope()
    val detailNavController = rememberNavController()

    BackHandler(enabled = navigator.canNavigateBack()) {
        scope.launch { navigator.navigateBack() }
    }

    val selectedTrackId = navigator.currentDestination?.contentKey
    LaunchedEffect(selectedTrackId) {
        // Wait for the detail-pane NavHost to set its graph before issuing
        // any navigate command. On compact widths the detail pane lives in
        // an AnimatedPane that defers composition while the list is shown,
        // so detailNavController.graph isn't valid until the back-stack
        // entry first appears. Reading .graph before that throws
        // IllegalStateException ("graph is not set").
        snapshotFlow { detailNavController.currentBackStackEntry }
            .filterNotNull()
            .first()
        val target: Any = if (selectedTrackId != null) {
            TrackDetailRoute(selectedTrackId)
        } else {
            LibraryDetailEmpty
        }
        detailNavController.navigate(target) {
            popUpTo(detailNavController.graph.findStartDestination().id) { inclusive = true }
            launchSingleTop = true
        }
    }

    NavigableListDetailPaneScaffold(
        navigator = navigator,
        listPane = {
            AnimatedPane {
                LibraryScreen(
                    onTrackClick = { trackId ->
                        scope.launch {
                            navigator.navigateTo(ListDetailPaneScaffoldRole.Detail, trackId)
                        }
                    },
                    onReplayTrack = onReplayTrack,
                    onNavigateToSettings = onNavigateToSettings,
                    isSignedIn = isSignedIn,
                    userPhotoUrl = userPhotoUrl,
                    onProfileClick = onProfileClick,
                )
            }
        },
        detailPane = {
            AnimatedPane {
                NavHost(
                    navController = detailNavController,
                    startDestination = LibraryDetailEmpty,
                ) {
                    composable<LibraryDetailEmpty> { LibraryDetailEmptyState() }
                    composable<TrackDetailRoute> {
                        TrackDetailScreen(
                            onBack = { scope.launch { navigator.navigateBack() } },
                            onReplay = onReplayTrack,
                            onEdit = onNavigateToEditTrack,
                            onViewAllSegments = onNavigateToSegmentsList,
                            onSegmentClick = onNavigateToSegmentDetail,
                            onVehicleClick = onNavigateToVehicleDetail,
                            onNavigateToTrip = onNavigateToTrip,
                        )
                    }
                }
            }
        },
    )
}

@Composable
private fun LibraryDetailEmptyState() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Filled.Route,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(48.dp),
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Select a route",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Choose a route from the list to see its details, pace notes, and replay.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

package com.rallytrax.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.rallytrax.app.data.gpx.GpxParseException
import com.rallytrax.app.data.gpx.GpxParser
import com.rallytrax.app.data.local.dao.TrackDao
import com.rallytrax.app.data.local.dao.TrackPointDao
import com.rallytrax.app.navigation.LibraryRoute
import com.rallytrax.app.navigation.RallyTraxNavHost
import com.rallytrax.app.navigation.RecordingRoute
import com.rallytrax.app.navigation.TrackDetailRoute
import com.rallytrax.app.navigation.topLevelRoutes
import com.rallytrax.app.ui.theme.RallyTraxTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var trackDao: TrackDao
    @Inject lateinit var trackPointDao: TrackPointDao

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            RallyTraxTheme {
                val navController = rememberNavController()
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = navBackStackEntry?.destination
                val snackbarHostState = remember { SnackbarHostState() }

                // Hide bottom bar on recording and track detail screens
                val showBottomBar = currentDestination?.let { dest ->
                    !dest.hasRoute(RecordingRoute::class) &&
                        !dest.hasRoute(TrackDetailRoute::class)
                } ?: true

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    snackbarHost = { SnackbarHost(snackbarHostState) },
                    bottomBar = {
                        if (showBottomBar) {
                            NavigationBar {
                                topLevelRoutes.forEach { topLevelRoute ->
                                    val selected = currentDestination?.hierarchy?.any {
                                        it.hasRoute(topLevelRoute.route::class)
                                    } == true

                                    NavigationBarItem(
                                        icon = {
                                            Icon(
                                                imageVector = if (selected) {
                                                    topLevelRoute.selectedIcon
                                                } else {
                                                    topLevelRoute.unselectedIcon
                                                },
                                                contentDescription = topLevelRoute.label,
                                            )
                                        },
                                        label = { Text(topLevelRoute.label) },
                                        selected = selected,
                                        onClick = {
                                            navController.navigate(topLevelRoute.route) {
                                                popUpTo(navController.graph.findStartDestination().id) {
                                                    saveState = true
                                                }
                                                launchSingleTop = true
                                                restoreState = true
                                            }
                                        },
                                    )
                                }
                            }
                        }
                    },
                ) { innerPadding ->
                    RallyTraxNavHost(
                        navController = navController,
                        modifier = if (showBottomBar) {
                            Modifier.padding(innerPadding)
                        } else {
                            Modifier
                        },
                    )
                }

                // Handle incoming GPX intent
                val gpxUri = handleGpxIntent(intent)
                LaunchedEffect(gpxUri) {
                    if (gpxUri != null) {
                        importGpxFromIntent(gpxUri, snackbarHostState) { trackId ->
                            navController.navigate(TrackDetailRoute(trackId))
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    private fun handleGpxIntent(intent: Intent?): Uri? {
        if (intent == null) return null
        val action = intent.action
        if (action == Intent.ACTION_VIEW) {
            return intent.data
        }
        return null
    }

    private suspend fun importGpxFromIntent(
        uri: Uri,
        snackbarHostState: SnackbarHostState,
        navigateToTrack: (String) -> Unit,
    ) {
        try {
            val inputStream = contentResolver.openInputStream(uri)
                ?: throw GpxParseException("Could not open file")
            val result = inputStream.use { GpxParser.parse(it) }
            trackDao.insertTrack(result.track)
            trackPointDao.insertPoints(result.points)
            snackbarHostState.showSnackbar("Imported: ${result.track.name}")
            navigateToTrack(result.track.id)
        } catch (e: GpxParseException) {
            snackbarHostState.showSnackbar("Import failed: ${e.message}")
        } catch (e: Exception) {
            snackbarHostState.showSnackbar("Import failed: ${e.message}")
        }
    }
}

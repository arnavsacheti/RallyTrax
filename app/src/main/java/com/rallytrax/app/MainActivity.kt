package com.rallytrax.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.rallytrax.app.data.gpx.GpxParseException
import com.rallytrax.app.data.gpx.GpxParser
import com.rallytrax.app.data.local.dao.PaceNoteDao
import com.rallytrax.app.data.local.dao.TrackDao
import com.rallytrax.app.data.local.dao.TrackPointDao
import com.rallytrax.app.data.preferences.UserPreferencesRepository
import com.rallytrax.app.navigation.OnboardingRoute
import com.rallytrax.app.navigation.RallyTraxNavHost
import com.rallytrax.app.navigation.RecordingRoute
import com.rallytrax.app.navigation.ReplayHudRoute
import com.rallytrax.app.navigation.ReplayRoute
import com.rallytrax.app.navigation.SettingsRoute
import com.rallytrax.app.navigation.TrackDetailRoute
import com.rallytrax.app.navigation.topLevelRoutes
import com.rallytrax.app.ui.theme.RallyTraxTheme
import com.rallytrax.app.update.UpdateViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var trackDao: TrackDao
    @Inject lateinit var trackPointDao: TrackPointDao
    @Inject lateinit var paceNoteDao: PaceNoteDao
    @Inject lateinit var preferencesRepository: UserPreferencesRepository

    private val updateViewModel: UpdateViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Read onboarding state synchronously to determine start destination
        val initialPrefs = runBlocking { preferencesRepository.preferences.first() }
        val startDestination: Any = if (initialPrefs.onboardingCompleted) {
            com.rallytrax.app.navigation.HomeRoute
        } else {
            OnboardingRoute
        }

        setContent {
            val prefs by preferencesRepository.preferences.collectAsStateWithLifecycle(
                initialValue = initialPrefs,
            )

            RallyTraxTheme(themeMode = prefs.themeMode) {
                val navController = rememberNavController()
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = navBackStackEntry?.destination
                val snackbarHostState = remember { SnackbarHostState() }

                val updateState by updateViewModel.uiState.collectAsStateWithLifecycle()

                // Hide bottom bar on non-tab screens
                val showBottomBar = currentDestination?.let { dest ->
                    !dest.hasRoute(RecordingRoute::class) &&
                        !dest.hasRoute(TrackDetailRoute::class) &&
                        !dest.hasRoute(ReplayHudRoute::class) &&
                        !dest.hasRoute(OnboardingRoute::class) &&
                        !dest.hasRoute(SettingsRoute::class) &&
                        !dest.hasRoute(ReplayRoute::class)
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
                        startDestination = startDestination,
                        modifier = if (showBottomBar) {
                            Modifier.padding(innerPadding)
                        } else {
                            Modifier
                        },
                    )
                }

                // Mark onboarding as completed when navigating away from it
                LaunchedEffect(currentDestination) {
                    if (currentDestination?.hasRoute(OnboardingRoute::class) == false && !initialPrefs.onboardingCompleted) {
                        preferencesRepository.setOnboardingCompleted(true)
                    }
                }

                // Update available dialog
                if (updateState.updateAvailable && !updateState.dismissed && updateState.releaseInfo != null) {
                    val release = updateState.releaseInfo!!
                    AlertDialog(
                        onDismissRequest = { updateViewModel.dismissUpdate() },
                        title = { Text("Update Available") },
                        text = {
                            Text(
                                "Version ${release.versionName} is available. " +
                                    "You are currently on version ${BuildConfig.VERSION_NAME}."
                            )
                        },
                        confirmButton = {
                            FilledTonalButton(
                                onClick = {
                                    updateViewModel.dismissUpdate()
                                    val downloadUrl = release.apkDownloadUrl ?: release.htmlUrl
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(downloadUrl))
                                    startActivity(intent)
                                },
                            ) {
                                Icon(Icons.Filled.Download, contentDescription = null)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    if (release.apkDownloadUrl != null) "Download" else "View Release"
                                )
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { updateViewModel.dismissUpdate() }) {
                                Text("Later")
                            }
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
            if (result.paceNotes.isNotEmpty()) {
                paceNoteDao.insertNotes(result.paceNotes)
            }
            snackbarHostState.showSnackbar("Imported: ${result.track.name}")
            navigateToTrack(result.track.id)
        } catch (e: GpxParseException) {
            snackbarHostState.showSnackbar("Import failed: ${e.message}")
        } catch (e: Exception) {
            snackbarHostState.showSnackbar("Import failed: ${e.message}")
        }
    }
}

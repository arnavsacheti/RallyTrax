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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.rallytrax.app.data.auth.AuthState
import com.rallytrax.app.data.gpx.GpxParseException
import com.rallytrax.app.data.local.dao.PaceNoteDao
import com.rallytrax.app.data.local.dao.TrackDao
import com.rallytrax.app.data.local.dao.TrackPointDao
import com.rallytrax.app.data.preferences.UserPreferencesRepository
import com.rallytrax.app.navigation.ExploreRoute
import com.rallytrax.app.navigation.OnboardingRoute
import com.rallytrax.app.navigation.RallyTraxNavHost
import com.rallytrax.app.navigation.RecordingRoute
import com.rallytrax.app.navigation.ReplayHudRoute
import com.rallytrax.app.navigation.ReplayRoute
import com.rallytrax.app.navigation.SettingsRoute
import com.rallytrax.app.navigation.TrackDetailRoute
import com.rallytrax.app.navigation.topLevelRoutes
import com.rallytrax.app.ui.auth.AuthViewModel
import com.rallytrax.app.ui.auth.FirstSignInSheet
import com.rallytrax.app.ui.auth.ProfileSheet
import com.rallytrax.app.ui.theme.RallyTraxTheme
import com.rallytrax.app.update.UpdateViewModel
import com.rallytrax.app.util.GoogleMapsUrlParser
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var trackDao: TrackDao
    @Inject lateinit var trackPointDao: TrackPointDao
    @Inject lateinit var paceNoteDao: PaceNoteDao
    @Inject lateinit var preferencesRepository: UserPreferencesRepository

    private val updateViewModel: UpdateViewModel by viewModels()
    private val authViewModel: AuthViewModel by viewModels()

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

            val authState by authViewModel.authState.collectAsStateWithLifecycle()
            val syncStatus by authViewModel.syncStatus.collectAsStateWithLifecycle()
            val showFirstSignInSheet by authViewModel.showFirstSignInSheet.collectAsStateWithLifecycle()

            val isSignedIn = authState is AuthState.SignedIn
            val userPhotoUrl = (authState as? AuthState.SignedIn)?.user?.photoUrl
            val authUser = (authState as? AuthState.SignedIn)?.user

            var showProfileSheet by remember { mutableStateOf(false) }

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
                            // Only apply bottom padding for the nav bar; each screen's
                            // own Scaffold handles the top (status bar + app bar) insets.
                            Modifier.padding(bottom = innerPadding.calculateBottomPadding())
                        } else {
                            Modifier
                        },
                        isSignedIn = isSignedIn,
                        userPhotoUrl = userPhotoUrl,
                        onSignIn = { authViewModel.signIn(this@MainActivity) },
                        onProfileClick = { showProfileSheet = true },
                        onSignOut = { authViewModel.signOut() },
                    )
                }

                // Mark onboarding as completed when navigating away from it
                LaunchedEffect(currentDestination) {
                    if (currentDestination?.hasRoute(OnboardingRoute::class) == false && !initialPrefs.onboardingCompleted) {
                        preferencesRepository.setOnboardingCompleted(true)
                    }
                }

                // Update available dialog — uses in-app download (same as Settings)
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
                            if (release.apkDownloadUrl != null) {
                                FilledTonalButton(
                                    onClick = {
                                        updateViewModel.dismissUpdate()
                                        updateViewModel.startDownload()
                                    },
                                ) {
                                    Icon(Icons.Filled.Download, contentDescription = null)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Download")
                                }
                            } else {
                                FilledTonalButton(
                                    onClick = {
                                        updateViewModel.dismissUpdate()
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(release.htmlUrl))
                                        startActivity(intent)
                                    },
                                ) {
                                    Icon(Icons.Filled.Download, contentDescription = null)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("View Release")
                                }
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { updateViewModel.dismissUpdate() }) {
                                Text("Later")
                            }
                        },
                    )
                }

                // Auto-install after in-app download completes
                val downloadState by updateViewModel.downloadState.collectAsStateWithLifecycle()
                if (downloadState.status == com.rallytrax.app.update.DownloadStatus.DOWNLOADED) {
                    LaunchedEffect(Unit) {
                        updateViewModel.installUpdate(this@MainActivity)
                    }
                }

                // Handle incoming intents (Open with, Share)
                val intentResult = handleIncomingIntent(intent)
                LaunchedEffect(intentResult) {
                    when (intentResult) {
                        is IntentResult.FileUris -> {
                            for (uri in intentResult.uris) {
                                importTrackFromIntent(uri, snackbarHostState) { trackId ->
                                    navController.navigate(TrackDetailRoute(trackId))
                                }
                            }
                        }
                        is IntentResult.SharedText -> {
                            val coords = withContext(Dispatchers.IO) {
                                GoogleMapsUrlParser.parse(intentResult.text)
                            }
                            if (coords != null) {
                                navController.navigate(
                                    ExploreRoute(focusLat = coords.latitude, focusLng = coords.longitude),
                                )
                            } else {
                                snackbarHostState.showSnackbar("Could not extract location from shared link")
                            }
                        }
                        IntentResult.None -> { /* nothing */ }
                    }
                }

                // First sign-in bottom sheet
                if (showFirstSignInSheet) {
                    FirstSignInSheet(
                        onDismiss = { authViewModel.dismissFirstSignInSheet() },
                    )
                }

                // Profile sheet
                if (showProfileSheet && authUser != null) {
                    ProfileSheet(
                        user = authUser,
                        syncStatus = syncStatus,
                        onSignOut = {
                            authViewModel.signOut()
                            showProfileSheet = false
                        },
                        onDismiss = { showProfileSheet = false },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    private sealed interface IntentResult {
        data object None : IntentResult
        data class FileUris(val uris: List<Uri>) : IntentResult
        data class SharedText(val text: String) : IntentResult
    }

    @Suppress("DEPRECATION")
    private fun handleIncomingIntent(intent: Intent?): IntentResult {
        if (intent == null) return IntentResult.None
        return when (intent.action) {
            Intent.ACTION_VIEW -> {
                val uris = listOfNotNull(intent.data)
                if (uris.isNotEmpty()) IntentResult.FileUris(uris) else IntentResult.None
            }
            Intent.ACTION_SEND -> {
                // Check for file attachment first, then fall back to shared text (URLs)
                val uri = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    intent.getParcelableExtra(Intent.EXTRA_STREAM)
                }
                if (uri != null) {
                    IntentResult.FileUris(listOf(uri))
                } else {
                    val text = intent.getStringExtra(Intent.EXTRA_TEXT)
                    if (text != null) IntentResult.SharedText(text) else IntentResult.None
                }
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                val uris = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM)
                } ?: emptyList()
                if (uris.isNotEmpty()) IntentResult.FileUris(uris) else IntentResult.None
            }
            else -> IntentResult.None
        }
    }

    private suspend fun importTrackFromIntent(
        uri: Uri,
        snackbarHostState: SnackbarHostState,
        navigateToTrack: (String) -> Unit,
    ) {
        try {
            val inputStream = contentResolver.openInputStream(uri)
                ?: throw GpxParseException("Could not open file")
            val result = inputStream.use { com.rallytrax.app.data.gpx.TrackImporter.import(it) }
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

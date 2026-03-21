package com.rallytrax.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import android.speech.tts.TextToSpeech
import androidx.compose.runtime.DisposableEffect
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rallytrax.app.BuildConfig
import com.rallytrax.app.data.preferences.GpsAccuracy
import com.rallytrax.app.data.preferences.MapProviderPreference
import com.rallytrax.app.data.preferences.ThemeMode
import com.rallytrax.app.data.preferences.UnitSystem
import com.rallytrax.app.update.DownloadStatus
import com.rallytrax.app.update.UpdateViewModel
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    updateViewModel: UpdateViewModel = hiltViewModel(),
    settingsViewModel: SettingsViewModel = hiltViewModel(),
) {
    val updateState by updateViewModel.uiState.collectAsStateWithLifecycle()
    val downloadState by updateViewModel.downloadState.collectAsStateWithLifecycle()
    val preferences by settingsViewModel.preferences.collectAsStateWithLifecycle()
    val uiState by settingsViewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            // App Info card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "RallyTrax",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Theme selector
            SettingsSectionCard(title = "Appearance", icon = { Icon(Icons.Filled.Palette, contentDescription = null, modifier = Modifier.size(20.dp)) }) {
                Text(
                    text = "Theme",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(modifier = Modifier.height(8.dp))
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    ThemeMode.entries.forEachIndexed { index, mode ->
                        SegmentedButton(
                            selected = preferences.themeMode == mode,
                            onClick = { settingsViewModel.setThemeMode(mode) },
                            shape = SegmentedButtonDefaults.itemShape(
                                index = index,
                                count = ThemeMode.entries.size,
                            ),
                            modifier = Modifier.semantics {
                                contentDescription = "${mode.name.lowercase().replaceFirstChar { it.uppercase() }} theme"
                            },
                        ) {
                            Text(mode.name.lowercase().replaceFirstChar { it.uppercase() })
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Units
            SettingsSectionCard(title = "Units", icon = { Icon(Icons.Filled.Straighten, contentDescription = null, modifier = Modifier.size(20.dp)) }) {
                Text(
                    text = "Measurement System",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(modifier = Modifier.height(8.dp))
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    UnitSystem.entries.forEachIndexed { index, system ->
                        SegmentedButton(
                            selected = preferences.unitSystem == system,
                            onClick = { settingsViewModel.setUnitSystem(system) },
                            shape = SegmentedButtonDefaults.itemShape(
                                index = index,
                                count = UnitSystem.entries.size,
                            ),
                            modifier = Modifier.semantics {
                                contentDescription = "${system.name.lowercase().replaceFirstChar { it.uppercase() }} units"
                            },
                        ) {
                            Text(
                                when (system) {
                                    UnitSystem.METRIC -> "Metric (km)"
                                    UnitSystem.IMPERIAL -> "Imperial (mi)"
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // GPS
            SettingsSectionCard(title = "GPS", icon = { Icon(Icons.Filled.GpsFixed, contentDescription = null, modifier = Modifier.size(20.dp)) }) {
                Text(
                    text = "Accuracy Mode",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(modifier = Modifier.height(8.dp))
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    GpsAccuracy.entries.forEachIndexed { index, accuracy ->
                        SegmentedButton(
                            selected = preferences.gpsAccuracy == accuracy,
                            onClick = { settingsViewModel.setGpsAccuracy(accuracy) },
                            shape = SegmentedButtonDefaults.itemShape(
                                index = index,
                                count = GpsAccuracy.entries.size,
                            ),
                            modifier = Modifier.semantics {
                                contentDescription = when (accuracy) {
                                    GpsAccuracy.HIGH -> "High accuracy GPS"
                                    GpsAccuracy.BATTERY_SAVER -> "Battery saver GPS"
                                }
                            },
                        ) {
                            Text(
                                when (accuracy) {
                                    GpsAccuracy.HIGH -> "High Accuracy"
                                    GpsAccuracy.BATTERY_SAVER -> "Battery Saver"
                                }
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = when (preferences.gpsAccuracy) {
                        GpsAccuracy.HIGH -> "Best accuracy, higher battery usage"
                        GpsAccuracy.BATTERY_SAVER -> "Reduced accuracy, longer battery life"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Map Provider
            SettingsSectionCard(title = "Map Provider", icon = { Icon(Icons.Filled.Map, contentDescription = null, modifier = Modifier.size(20.dp)) }) {
                Text(
                    text = "Tile Source",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(modifier = Modifier.height(8.dp))
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    MapProviderPreference.entries.forEachIndexed { index, provider ->
                        SegmentedButton(
                            selected = preferences.mapProvider == provider,
                            onClick = { settingsViewModel.setMapProvider(provider) },
                            shape = SegmentedButtonDefaults.itemShape(
                                index = index,
                                count = MapProviderPreference.entries.size,
                            ),
                            modifier = Modifier.semantics {
                                contentDescription = when (provider) {
                                    MapProviderPreference.AUTO -> "Auto map provider"
                                    MapProviderPreference.GOOGLE_MAPS -> "Google Maps"
                                    MapProviderPreference.OPENSTREETMAP -> "OpenStreetMap"
                                }
                            },
                        ) {
                            Text(
                                when (provider) {
                                    MapProviderPreference.AUTO -> "Auto"
                                    MapProviderPreference.GOOGLE_MAPS -> "Google"
                                    MapProviderPreference.OPENSTREETMAP -> "OSM"
                                },
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = when (preferences.mapProvider) {
                        MapProviderPreference.AUTO -> "Uses Google Maps if API key is configured, otherwise OpenStreetMap"
                        MapProviderPreference.GOOGLE_MAPS -> "Google Maps (requires API key)"
                        MapProviderPreference.OPENSTREETMAP -> "OpenStreetMap (no API key required)"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // TTS
            SettingsSectionCard(title = "Co-Driver Voice", icon = { Icon(Icons.Filled.RecordVoiceOver, contentDescription = null, modifier = Modifier.size(20.dp)) }) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Voice Enabled",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Switch(
                        checked = preferences.ttsEnabled,
                        onCheckedChange = { settingsViewModel.setTtsEnabled(it) },
                        modifier = Modifier.semantics {
                            contentDescription = "Toggle co-driver voice"
                        },
                    )
                }

                if (preferences.ttsEnabled) {
                    // TTS engine for voice preview
                    var ttsReady by remember { mutableStateOf(false) }
                    val tts = remember {
                        var engine: TextToSpeech? = null
                        engine = TextToSpeech(context) { status ->
                            ttsReady = status == TextToSpeech.SUCCESS
                        }
                        engine
                    }

                    DisposableEffect(Unit) {
                        onDispose { tts?.shutdown() }
                    }

                    val previewPhrases = remember {
                        listOf(
                            "Left 3 into right 4",
                            "Hairpin left, tightens",
                            "Crest, keep in",
                            "Right 2 long over crest",
                            "Flat out through dip",
                        )
                    }

                    fun speakPreview(rate: Float, pitch: Float) {
                        if (!ttsReady || tts == null) return
                        tts.setSpeechRate(rate)
                        tts.setPitch(pitch)
                        val phrase = previewPhrases.random()
                        tts.speak(phrase, TextToSpeech.QUEUE_FLUSH, null, "settings_preview")
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(modifier = Modifier.height(12.dp))

                    // Speech rate
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Speed, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Speech Rate",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                        Text(
                            text = String.format(Locale.US, "%.2f", preferences.ttsRate),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    Slider(
                        value = preferences.ttsRate,
                        onValueChange = { settingsViewModel.setTtsRate(it) },
                        onValueChangeFinished = {
                            speakPreview(preferences.ttsRate, preferences.ttsPitch)
                        },
                        valueRange = 0.5f..2.0f,
                        steps = 5,
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics { contentDescription = "Speech rate slider" },
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Pitch
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = "Pitch",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            text = String.format(Locale.US, "%.2f", preferences.ttsPitch),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    Slider(
                        value = preferences.ttsPitch,
                        onValueChange = { settingsViewModel.setTtsPitch(it) },
                        onValueChangeFinished = {
                            speakPreview(preferences.ttsRate, preferences.ttsPitch)
                        },
                        valueRange = 0.5f..2.0f,
                        steps = 5,
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics { contentDescription = "Pitch slider" },
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Update card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (updateState.updateAvailable) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainer
                    },
                ),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (updateState.updateAvailable) {
                                Icons.Filled.NewReleases
                            } else {
                                Icons.Filled.CheckCircle
                            },
                            contentDescription = null,
                            tint = if (updateState.updateAvailable) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.size(24.dp),
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Software Update",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    if (updateState.isChecking) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "Checking for updates...",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    } else if (updateState.updateAvailable && updateState.releaseInfo != null) {
                        val release = updateState.releaseInfo!!
                        Text(
                            text = "Version ${release.versionName} is available",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        if (release.releaseName.isNotBlank() && release.releaseName != release.tagName) {
                            Text(
                                text = release.releaseName,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        if (release.body.isNotBlank()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = release.body.take(500),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        when (downloadState.status) {
                            DownloadStatus.IDLE -> {
                                if (release.apkDownloadUrl != null) {
                                    FilledTonalButton(
                                        onClick = { updateViewModel.startDownload() },
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Icon(Icons.Filled.Download, contentDescription = null)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Download Update")
                                    }
                                }
                            }
                            DownloadStatus.DOWNLOADING -> {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                    ) {
                                        Text(
                                            text = "Downloading...",
                                            style = MaterialTheme.typography.bodyMedium,
                                        )
                                        Text(
                                            text = "${downloadState.progress}%",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold,
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    LinearProgressIndicator(
                                        progress = { downloadState.progress / 100f },
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                }
                            }
                            DownloadStatus.DOWNLOADED -> {
                                FilledTonalButton(
                                    onClick = { updateViewModel.installUpdate(context) },
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Icon(Icons.Filled.Download, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Install Update")
                                }
                            }
                            DownloadStatus.ERROR -> {
                                Text(
                                    text = downloadState.errorMessage ?: "Download failed",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                FilledTonalButton(
                                    onClick = {
                                        updateViewModel.resetDownload()
                                        updateViewModel.startDownload()
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Icon(Icons.Filled.Refresh, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Retry Download")
                                }
                            }
                        }
                    } else if (updateState.lastCheckError != null) {
                        Text(
                            text = updateState.lastCheckError!!,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                    } else {
                        Text(
                            text = "You're up to date",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    if (!updateState.isChecking) {
                        Spacer(modifier = Modifier.height(8.dp))
                        FilledTonalButton(
                            onClick = { updateViewModel.checkForUpdate() },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Filled.Refresh, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Check for Updates")
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Data Management
            SettingsSectionCard(title = "Data Management", icon = { Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.error) }) {
                Text(
                    text = "${uiState.trackCount} track${if (uiState.trackCount != 1) "s" else ""} stored",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedButton(
                    onClick = { settingsViewModel.showDeleteConfirmation() },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = uiState.trackCount > 0 && !uiState.isDeleting,
                ) {
                    if (uiState.isDeleting) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Deleting...")
                    } else {
                        Icon(Icons.Filled.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Delete All Tracks", color = MaterialTheme.colorScheme.error)
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    // Delete confirmation dialog
    if (uiState.showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { settingsViewModel.dismissDeleteConfirmation() },
            title = { Text("Delete All Tracks?") },
            text = {
                Text(
                    "This will permanently delete all ${uiState.trackCount} tracks, " +
                        "including their pace notes and track points. This action cannot be undone."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { settingsViewModel.deleteAllTracks() },
                ) {
                    Text("Delete All", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { settingsViewModel.dismissDeleteConfirmation() }) {
                    Text("Cancel")
                }
            },
        )
    }
}

@Composable
private fun SettingsSectionCard(
    title: String,
    icon: @Composable () -> Unit = {},
    content: @Composable () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                icon()
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            content()
        }
    }
}

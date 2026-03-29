package com.rallytrax.app.ui.profile

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.rallytrax.app.data.auth.AuthState
import com.rallytrax.app.data.preferences.UnitSystem
import com.rallytrax.app.ui.auth.GoogleSignInCard
import com.rallytrax.app.ui.components.MonthCalendar
import com.rallytrax.app.ui.components.RallyTraxTopAppBar
import com.rallytrax.app.ui.theme.rallyTraxColors
import com.rallytrax.app.util.formatDistance
import com.rallytrax.app.util.formatSpeed
import com.rallytrax.app.util.speedUnit
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    onNavigateToGarage: () -> Unit = {},
    onNavigateToStints: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    authState: AuthState = AuthState.SignedOut,
    isSignedIn: Boolean = false,
    userPhotoUrl: String? = null,
    onSignIn: () -> Unit = {},
    onProfileClick: () -> Unit = {},
    viewModel: ProfileViewModel = hiltViewModel(),
) {
    val profile by viewModel.profileState.collectAsStateWithLifecycle()
    val preferences by viewModel.preferences.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    val authUser = (authState as? AuthState.SignedIn)?.user

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            RallyTraxTopAppBar(
                title = "Profile",
                onSettingsClick = onNavigateToSettings,
                scrollBehavior = scrollBehavior,
                isSignedIn = isSignedIn,
                userPhotoUrl = userPhotoUrl,
                onProfileClick = onProfileClick,
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // Sign in card or user header
            if (!isSignedIn) {
                GoogleSignInCard(authState = authState, onClick = onSignIn)
            } else if (authUser != null) {
                UserHeader(
                    displayName = authUser.displayName,
                    email = authUser.email,
                    photoUrl = authUser.photoUrl,
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Month Calendar
            OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    MonthCalendar(
                        activeDays = profile.activeDaysThisMonth,
                        currentStreak = profile.currentStreak,
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Hero Stats — Lifetime
            Text(
                text = "Lifetime Stats",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                HeroStatCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Filled.Route,
                    value = "${profile.totalDrives}",
                    label = "Total Drives",
                )
                HeroStatCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Filled.LocalFireDepartment,
                    value = "${profile.currentStreak}",
                    label = "Day Streak",
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                HeroStatCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Filled.Straighten,
                    value = formatDistance(profile.totalDistanceMeters, preferences.unitSystem),
                    label = "Total Distance",
                )
                HeroStatCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Filled.Speed,
                    value = formatDistance(profile.avgDriveLengthMeters, preferences.unitSystem),
                    label = "Avg Drive",
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                HeroStatCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Filled.DirectionsCar,
                    value = "${profile.vehicleCount}",
                    label = "Cars",
                )
                HeroStatCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Filled.Timer,
                    value = formatDistance(profile.longestDriveMeters, preferences.unitSystem),
                    label = "Longest Drive",
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Yearly Stats
            Text(
                text = "This Year",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                HeroStatCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Filled.Route,
                    value = "${profile.yearlyDrives}",
                    label = "Drives",
                )
                HeroStatCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Filled.Straighten,
                    value = formatDistance(profile.yearlyDistanceMeters, preferences.unitSystem),
                    label = "Distance",
                )
            }

            // Weather Impact Card
            if (profile.weatherBuckets.size >= 2) {
                Spacer(modifier = Modifier.height(16.dp))
                WeatherImpactCard(
                    buckets = profile.weatherBuckets,
                    unitSystem = preferences.unitSystem,
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // My Garage button
            Card(
                onClick = onNavigateToGarage,
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                ),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Filled.DirectionsCar,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "My Garage",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                        Text(
                            text = "${profile.vehicleCount} vehicle${if (profile.vehicleCount != 1) "s" else ""}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                        )
                    }
                    Icon(
                        imageVector = Icons.Outlined.ChevronRight,
                        contentDescription = "Go to Garage",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // My Stints button
            Card(
                onClick = onNavigateToStints,
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                ),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Route,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "My Stints",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                        Text(
                            text = "${profile.stintCount} stint${if (profile.stintCount != 1) "s" else ""}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                        )
                    }
                    Icon(
                        imageVector = Icons.Outlined.ChevronRight,
                        contentDescription = "Go to Stints",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun UserHeader(
    displayName: String?,
    email: String?,
    photoUrl: String?,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (photoUrl != null) {
            AsyncImage(
                model = photoUrl,
                contentDescription = "Profile photo",
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop,
            )
            Spacer(modifier = Modifier.width(16.dp))
        }
        Column {
            Text(
                text = displayName ?: "Driver",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (email != null) {
                Text(
                    text = email,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun HeroStatCard(
    icon: ImageVector,
    value: String,
    label: String,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private val BASELINE_CONDITION_GROUPS = setOf("Clear", "Clouds")
private val IMPACT_CONDITION_GROUPS = setOf("Rain", "Drizzle", "Thunderstorm", "Snow")

@Composable
private fun WeatherImpactCard(
    buckets: List<WeatherBucket>,
    unitSystem: UnitSystem,
    modifier: Modifier = Modifier,
) {
    val clearBucket = buckets.find { it.label in BASELINE_CONDITION_GROUPS }
    val impactBuckets = buckets.filter { it.label in IMPACT_CONDITION_GROUPS }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Weather Impact",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(12.dp))

            buckets.forEach { bucket ->
                WeatherBucketRow(bucket = bucket, unitSystem = unitSystem)
                Spacer(modifier = Modifier.height(8.dp))
            }

            if (clearBucket != null && impactBuckets.isNotEmpty()) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                Spacer(modifier = Modifier.height(4.dp))

                impactBuckets.forEach { impactBucket ->
                    if (clearBucket.avgSpeedMps > 0) {
                        ComparisonRow(
                            label = "${impactBucket.label} vs ${clearBucket.label}:",
                            diffPercent = percentChange(impactBucket.avgSpeedMps, clearBucket.avgSpeedMps),
                            positiveLabel = "faster",
                            negativeLabel = "slower",
                        )
                    }

                    val clearSmooth = clearBucket.avgSmoothness
                    val impactSmooth = impactBucket.avgSmoothness
                    if (clearSmooth != null && impactSmooth != null && clearSmooth > 0) {
                        ComparisonRow(
                            label = "${impactBucket.label} smoothness:",
                            diffPercent = percentChange(impactSmooth, clearSmooth),
                            positiveLabel = "smoother",
                            negativeLabel = "rougher",
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }
            }
        }
    }
}

private fun percentChange(value: Double, baseline: Double): Double =
    ((value - baseline) / baseline) * 100

@Composable
private fun ComparisonRow(
    label: String,
    diffPercent: Double,
    positiveLabel: String,
    negativeLabel: String,
) {
    val text = if (diffPercent < 0) {
        String.format(Locale.US, "%.0f%% %s", -diffPercent, negativeLabel)
    } else {
        String.format(Locale.US, "+%.0f%% %s", diffPercent, positiveLabel)
    }
    val color = if (diffPercent < 0) {
        MaterialTheme.rallyTraxColors.speedDanger
    } else {
        MaterialTheme.rallyTraxColors.speedSafe
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = color,
        )
    }
}

@Composable
private fun WeatherBucketRow(
    bucket: WeatherBucket,
    unitSystem: UnitSystem,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = bucket.label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = "${bucket.driveCount} drive${if (bucket.driveCount != 1) "s" else ""}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = "${formatSpeed(bucket.avgSpeedMps, unitSystem)} ${speedUnit(unitSystem)}",
                style = MaterialTheme.typography.bodyMedium,
            )
            if (bucket.avgSmoothness != null) {
                Text(
                    text = String.format(Locale.US, "%.0f smoothness", bucket.avgSmoothness),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

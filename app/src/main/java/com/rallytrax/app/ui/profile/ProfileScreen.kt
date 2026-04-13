package com.rallytrax.app.ui.profile

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.People
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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.rallytrax.app.data.auth.AuthState
import com.rallytrax.app.data.preferences.UnitSystem
import com.rallytrax.app.ui.auth.GoogleSignInCard
import com.rallytrax.app.ui.components.RallyTraxTopAppBar
import com.rallytrax.app.ui.components.Sparkline
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
    onNavigateToTrips: () -> Unit = {},
    onNavigateToAchievements: () -> Unit = {},
    onNavigateToFriends: () -> Unit = {},
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
                    value = "${profile.stintCount}",
                    label = "Stints",
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

            // Streak card (Strava-style)
            if (profile.currentStreak > 0) {
                Spacer(modifier = Modifier.height(16.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    ),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.LocalFireDepartment,
                            contentDescription = null,
                            modifier = Modifier.size(28.dp),
                            tint = MaterialTheme.colorScheme.onTertiaryContainer,
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "${profile.currentStreak} day streak!",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                            )
                            Text(
                                text = "Keep it going — drive again today",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f),
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Achievements Summary
            if (profile.achievementSummary.totalCount > 0) {
                AchievementsSummaryCard(summary = profile.achievementSummary)
                Spacer(modifier = Modifier.height(16.dp))
            }

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

            // Driving Trends Card
            DrivingTrendsCard(profile = profile)

            if (profile.vehicleComparison.size >= 2) {
                Spacer(modifier = Modifier.height(16.dp))
                VehicleComparisonCard(
                    vehicleStats = profile.vehicleComparison,
                    unitSystem = preferences.unitSystem,
                )
            }

            // Corner Speed Profile radar chart
            if (profile.driverProfile.size >= 3) {
                Spacer(modifier = Modifier.height(16.dp))
                DriverRadarCard(driverProfile = profile.driverProfile)
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

            Spacer(modifier = Modifier.height(12.dp))

            // My Trips button
            Card(
                onClick = onNavigateToTrips,
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
                            text = "My Trips",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                        Text(
                            text = "Group drives into trips",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                        )
                    }
                    Icon(
                        imageVector = Icons.Outlined.ChevronRight,
                        contentDescription = "Go to Trips",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Achievements button
            Card(
                onClick = onNavigateToAchievements,
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
                        imageVector = Icons.Filled.EmojiEvents,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Achievements",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                        Text(
                            text = "Track your milestones",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                        )
                    }
                    Icon(
                        imageVector = Icons.Outlined.ChevronRight,
                        contentDescription = "Go to Achievements",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Card(
                onClick = onNavigateToFriends,
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
                        imageVector = Icons.Filled.People,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Friends",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                        Text(
                            text = "Follow and connect with friends",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                        )
                    }
                    Icon(
                        imageVector = Icons.Outlined.ChevronRight,
                        contentDescription = "Go to Friends",
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
private fun DrivingTrendsCard(profile: ProfileState) {
    val hasTrends = profile.smoothnessTrend.size >= 2 ||
        profile.brakingTrend.size >= 2 ||
        profile.corneringGTrend.size >= 2

    if (!hasTrends) return

    Spacer(modifier = Modifier.height(16.dp))

    Text(
        text = "Driving Trends",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
    )
    Spacer(modifier = Modifier.height(8.dp))

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (profile.smoothnessTrend.size >= 2) {
                TrendRow(
                    label = "Smoothness",
                    latestValue = profile.latestSmoothness?.toString() ?: "--",
                    trendData = profile.smoothnessTrend,
                )
            }
            if (profile.brakingTrend.size >= 2) {
                TrendRow(
                    label = "Braking",
                    latestValue = profile.latestBraking?.toString() ?: "--",
                    trendData = profile.brakingTrend,
                )
            }
            if (profile.corneringGTrend.size >= 2) {
                TrendRow(
                    label = "Cornering G",
                    latestValue = profile.latestCorneringG?.let {
                        String.format("%.2f", it)
                    } ?: "--",
                    trendData = profile.corneringGTrend,
                )
            }
        }
    }
}

@Composable
private fun TrendRow(
    label: String,
    latestValue: String,
    trendData: List<Float>,
) {
    val improving = trendData.last() > trendData.first()
    val declining = trendData.last() < trendData.first()
    val trendColor = when {
        improving -> MaterialTheme.rallyTraxColors.speedSafe
        declining -> MaterialTheme.rallyTraxColors.speedDanger
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val trendArrow = when {
        improving -> " \u2191"
        declining -> " \u2193"
        else -> ""
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "$latestValue$trendArrow",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = trendColor,
        )
        Spacer(modifier = Modifier.width(12.dp))
        Sparkline(
            data = trendData,
            color = trendColor,
            modifier = Modifier
                .width(60.dp)
                .height(24.dp),
        )
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

@Composable
private fun AchievementsSummaryCard(
    summary: AchievementSummary,
    modifier: Modifier = Modifier,
) {
    val overallProgress = if (summary.totalCount > 0) {
        summary.unlockedCount.toFloat() / summary.totalCount.toFloat()
    } else 0f

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Filled.EmojiEvents,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Achievements",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "${summary.unlockedCount} / ${summary.totalCount}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            LinearProgressIndicator(
                progress = { overallProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            )

            if (summary.nextAchievement != null) {
                Spacer(modifier = Modifier.height(12.dp))

                val nextProgress = (summary.nextAchievement.progress * 100).toInt()
                Text(
                    text = "Next: ${summary.nextAchievement.title}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    LinearProgressIndicator(
                        progress = { summary.nextAchievement.progress.toFloat() },
                        modifier = Modifier
                            .weight(1f)
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = MaterialTheme.colorScheme.tertiary,
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "$nextProgress%",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun VehicleComparisonCard(
    vehicleStats: List<VehicleStats>,
    unitSystem: UnitSystem,
    modifier: Modifier = Modifier,
) {
    val maxDistance = vehicleStats.maxOfOrNull { it.totalDistanceMeters } ?: 1.0

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Vehicle Comparison",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(12.dp))

            vehicleStats.forEach { stats ->
                val fraction = if (maxDistance > 0) {
                    (stats.totalDistanceMeters / maxDistance).toFloat().coerceIn(0f, 1f)
                } else 0f

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stats.name,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "${stats.driveCount}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = formatDistance(stats.totalDistanceMeters, unitSystem),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction)
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer),
                )
            }
        }
    }
}

@Composable
private fun DriverRadarCard(
    driverProfile: Map<Int, Double>,
    modifier: Modifier = Modifier,
) {
    val sortedEntries = remember(driverProfile) {
        driverProfile.entries.sortedBy { it.key }
    }
    val axisCount = sortedEntries.size
    val maxSpeed = remember(sortedEntries) {
        sortedEntries.maxOf { it.value }.coerceAtLeast(1.0)
    }
    val strongest = remember(sortedEntries) { sortedEntries.maxByOrNull { it.value } }
    val weakest = remember(sortedEntries) { sortedEntries.minByOrNull { it.value } }

    val primaryColor = MaterialTheme.colorScheme.primary
    val fillColor = primaryColor.copy(alpha = 0.3f)
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val labelTextSizePx = with(LocalDensity.current) { 10.dp.toPx() }

    val labelPaint = remember(labelColor, labelTextSizePx) {
        android.graphics.Paint().apply {
            color = labelColor.toArgb()
            textSize = labelTextSizePx
            isAntiAlias = true
            textAlign = android.graphics.Paint.Align.CENTER
        }
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Corner Speed Profile",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Your average speed through turns of different radii",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(12.dp))

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
            ) {
                val centerX = size.width / 2f
                val centerY = size.height / 2f
                val chartRadius = minOf(centerX, centerY) - labelTextSizePx * 2.5f

                val angleStep = (2.0 * Math.PI / axisCount).toFloat()
                // Start from top (negative Y) per radar chart convention
                val startAngle = (-Math.PI / 2.0).toFloat()

                for (fraction in listOf(0.25f, 0.5f, 0.75f, 1.0f)) {
                    drawCircle(
                        color = gridColor.copy(alpha = 0.2f),
                        radius = chartRadius * fraction,
                        center = Offset(centerX, centerY),
                        style = Stroke(width = 1f),
                    )
                }

                for (i in 0 until axisCount) {
                    val angle = startAngle + i * angleStep
                    val endX = centerX + chartRadius * kotlin.math.cos(angle)
                    val endY = centerY + chartRadius * kotlin.math.sin(angle)
                    drawLine(
                        color = gridColor.copy(alpha = 0.2f),
                        start = Offset(centerX, centerY),
                        end = Offset(endX, endY),
                        strokeWidth = 1f,
                    )
                }

                val path = Path()
                for (i in sortedEntries.indices) {
                    val angle = startAngle + i * angleStep
                    val normalizedSpeed = (sortedEntries[i].value / maxSpeed).toFloat()
                    val dist = chartRadius * normalizedSpeed
                    val px = centerX + dist * kotlin.math.cos(angle)
                    val py = centerY + dist * kotlin.math.sin(angle)
                    if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
                }
                path.close()

                drawPath(path = path, color = fillColor)
                drawPath(path = path, color = primaryColor, style = Stroke(width = 2f))

                drawIntoCanvas { canvas ->
                    for (i in sortedEntries.indices) {
                        val angle = startAngle + i * angleStep
                        val labelDist = chartRadius + labelTextSizePx * 1.5f
                        val lx = centerX + labelDist * kotlin.math.cos(angle)
                        val ly = centerY + labelDist * kotlin.math.sin(angle) + labelTextSizePx / 3f
                        canvas.nativeCanvas.drawText(
                            "${sortedEntries[i].key}m",
                            lx,
                            ly,
                            labelPaint,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                if (strongest != null) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Strongest",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = "${strongest.key}m radius",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                if (weakest != null) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Weakest",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = "${weakest.key}m radius",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }
    }
}

// ── Weather Impact Card ───────────────────────────────────────────────────────

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
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
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

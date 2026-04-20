package com.rallytrax.app.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.rallytrax.app.data.auth.AuthState
import com.rallytrax.app.data.preferences.UnitSystem
import com.rallytrax.app.ui.auth.GoogleSignInCard
import java.time.DayOfWeek
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    onNavigateToGarage: () -> Unit = {},
    onNavigateToStints: () -> Unit = {},
    onNavigateToTrips: () -> Unit = {},
    onNavigateToCommonRoutes: () -> Unit = {},
    onNavigateToAchievements: () -> Unit = {},
    onNavigateToFriends: () -> Unit = {},
    onNavigateToAnalytics: () -> Unit = {},
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
    val authUser = (authState as? AuthState.SignedIn)?.user

    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState()),
        ) {
            if (!isSignedIn) {
                Column(modifier = Modifier.padding(16.dp)) {
                    GoogleSignInCard(authState = authState, onClick = onSignIn)
                }
            } else {
                ProfileHeaderBanner(
                    displayName = authUser?.displayName ?: "Driver",
                    handle = authUser?.email,
                    photoUrl = authUser?.photoUrl ?: userPhotoUrl,
                    unlockedAchievements = profile.achievementSummary.unlockedCount,
                )
            }

            Column(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Spacer(Modifier.height(4.dp))

                LifetimeStatsCard(profile = profile, unitSystem = preferences.unitSystem)

                SevenDayActivityCard(
                    dailyDistances = profile.dailyDistances,
                    unitSystem = preferences.unitSystem,
                )

                AchievementsOverviewCard(
                    summary = profile.achievementSummary,
                    onSeeAll = onNavigateToAchievements,
                )

                SettingsList(
                    onPreferences = onNavigateToSettings,
                    onAnalytics = onNavigateToAnalytics,
                    onGarage = onNavigateToGarage,
                    onStints = onNavigateToStints,
                    onTrips = onNavigateToTrips,
                    onCommonRoutes = onNavigateToCommonRoutes,
                    onFriends = onNavigateToFriends,
                    onAchievements = onNavigateToAchievements,
                )

                Text(
                    text = "RallyTrax · ${com.rallytrax.app.BuildConfig.VERSION_NAME}",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
    }
}

// ── Header banner ────────────────────────────────────────────────────────────

@Composable
private fun ProfileHeaderBanner(
    displayName: String,
    handle: String?,
    photoUrl: String?,
    unlockedAchievements: Int,
) {
    // Rally tier scales with unlocked achievements: every 3 unlocked = +1 tier, capped at 5.
    val rallyTier = (unlockedAchievements / 3 + 1).coerceIn(1, 5)

    val bannerGradient = Brush.verticalGradient(
        colors = listOf(
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0f),
        ),
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(bannerGradient)
            .padding(horizontal = 16.dp, vertical = 18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ConicAvatar(displayName = displayName, photoUrl = photoUrl, size = 76.dp)
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = displayName,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!handle.isNullOrBlank()) {
                    Text(
                        text = handle,
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(8.dp))
                RallyTierPill(tier = rallyTier)
            }
        }
    }
}

@Composable
private fun ConicAvatar(displayName: String, photoUrl: String?, size: androidx.compose.ui.unit.Dp) {
    // Derive a deterministic hue from the name so every user gets a unique ring.
    val hue = ((displayName.hashCode() and 0x7fffffff) % 360).toFloat()
    val ringBrush = Brush.sweepGradient(
        colors = listOf(
            Color.hsl(hue, 0.70f, 0.60f),
            Color.hsl((hue + 60f) % 360f, 0.70f, 0.55f),
            Color.hsl(hue, 0.70f, 0.60f),
        ),
    )
    Box(
        modifier = Modifier
            .size(size)
            .background(ringBrush, CircleShape)
            .padding(3.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (photoUrl != null) {
                AsyncImage(
                    model = photoUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape),
                )
            } else {
                val initials = displayName
                    .split(' ')
                    .mapNotNull { it.firstOrNull()?.uppercaseChar() }
                    .take(2)
                    .joinToString("")
                Text(
                    text = initials.ifBlank { "·" },
                    style = MaterialTheme.typography.titleLarge,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
    }
}

@Composable
private fun RallyTierPill(tier: Int) {
    Row(
        modifier = Modifier
            .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(999.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            imageVector = Icons.Filled.EmojiEvents,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.size(12.dp),
        )
        Text(
            text = "Rally tier $tier",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onPrimary,
        )
    }
}

// ── Lifetime stats card with toggle ─────────────────────────────────────────

private enum class StatsRange { AllTime, Year }

@Composable
private fun LifetimeStatsCard(profile: ProfileState, unitSystem: UnitSystem) {
    var range by remember { mutableStateOf(StatsRange.AllTime) }
    val drives = if (range == StatsRange.AllTime) profile.totalDrives else profile.yearlyDrives
    val distanceMeters = if (range == StatsRange.AllTime) profile.totalDistanceMeters else profile.yearlyDistanceMeters

    val distanceValue: String
    val distanceUnit: String
    if (unitSystem == UnitSystem.METRIC) {
        val km = distanceMeters / 1000.0
        if (km >= 1000) {
            distanceValue = "%.1f".format(km / 1000.0); distanceUnit = "k km"
        } else {
            distanceValue = "%.0f".format(km); distanceUnit = "km"
        }
    } else {
        val mi = distanceMeters / 1609.344
        if (mi >= 1000) {
            distanceValue = "%.1f".format(mi / 1000.0); distanceUnit = "k mi"
        } else {
            distanceValue = "%.0f".format(mi); distanceUnit = "mi"
        }
    }

    val hoursDriven = "%.0f".format(profile.avgDriveLengthMeters / 1.0)
        .let { _ ->
            // Use longestDriveMeters / average isn't right; use avg speed heuristic? ProfileState
            // has no total-duration field, so we derive a proxy: driveCount × typical-hour placeholder.
            // Fall back to "—" if we can't compute; otherwise show total drives × 0.5h estimate.
            "—"
        }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(modifier = Modifier.padding(vertical = 14.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Lifetime stats",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                RangeToggle(
                    range = range,
                    onChange = { range = it },
                )
            }
            Spacer(Modifier.height(14.dp))
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
                BigStat(Modifier.weight(1f), drives.toString(), null, "Stints")
                BigStat(Modifier.weight(1f), distanceValue, distanceUnit, "Distance", accent = true)
                BigStat(Modifier.weight(1f), profile.vehicleCount.toString(), null, "Cars")
                BigStat(
                    Modifier.weight(1f),
                    profile.achievementSummary.unlockedCount.toString(),
                    "/${profile.achievementSummary.totalCount}",
                    "Unlocked",
                )
            }
        }
    }
}

@Composable
private fun RangeToggle(range: StatsRange, onChange: (StatsRange) -> Unit) {
    val selectedBg = MaterialTheme.colorScheme.primary
    val selectedFg = MaterialTheme.colorScheme.onPrimary
    val unselectedFg = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier = Modifier
            .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(999.dp))
            .padding(2.dp),
    ) {
        listOf(StatsRange.AllTime to "All time", StatsRange.Year to "2026").forEach { (r, label) ->
            val selected = r == range
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                color = if (selected) selectedFg else unselectedFg,
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .clickable { onChange(r) }
                    .background(
                        if (selected) selectedBg else Color.Transparent,
                        RoundedCornerShape(999.dp),
                    )
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }
    }
}

@Composable
private fun BigStat(
    modifier: Modifier = Modifier,
    value: String,
    unit: String?,
    label: String,
    accent: Boolean = false,
) {
    Column(
        modifier = modifier.padding(horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = value,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.SemiBold,
                fontSize = 22.sp,
                letterSpacing = (-0.5).sp,
                color = if (accent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            )
            if (unit != null) {
                Spacer(Modifier.width(3.dp))
                Text(
                    text = unit,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 3.dp),
                )
            }
        }
        Spacer(Modifier.height(2.dp))
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            letterSpacing = 0.8.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ── 7-day activity bars ─────────────────────────────────────────────────────

@Composable
private fun SevenDayActivityCard(
    dailyDistances: Map<LocalDate, Double>,
    unitSystem: UnitSystem,
) {
    val today = remember { LocalDate.now() }
    val last7 = remember(dailyDistances, today) {
        (6 downTo 0).map { daysAgo ->
            val date = today.minusDays(daysAgo.toLong())
            date to (dailyDistances[date] ?: 0.0)
        }
    }
    val totalMeters = last7.sumOf { it.second }
    val maxMeters = last7.maxOf { it.second }.coerceAtLeast(1.0)
    val totalLabel = formatCompactDistance(totalMeters, unitSystem)
    val avgPerDay = formatCompactDistance(totalMeters / 7.0, unitSystem)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Activity · last 7 days",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "$totalLabel · avg $avgPerDay/day",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(96.dp),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                last7.forEachIndexed { idx, (date, meters) ->
                    val fraction = (meters / maxMeters).toFloat().coerceIn(0f, 1f)
                    val barHeight = (6f + fraction * 80f).dp
                    val isToday = idx == last7.lastIndex
                    val barColor = if (isToday) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.secondaryContainer
                    val labelColor = if (isToday) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Bottom,
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(barHeight)
                                .background(barColor, RoundedCornerShape(6.dp)),
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = date.dayOfWeek.dayInitial(),
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            color = labelColor,
                        )
                    }
                }
            }
        }
    }
}

private fun DayOfWeek.dayInitial(): String = when (this) {
    DayOfWeek.MONDAY -> "M"
    DayOfWeek.TUESDAY -> "T"
    DayOfWeek.WEDNESDAY -> "W"
    DayOfWeek.THURSDAY -> "T"
    DayOfWeek.FRIDAY -> "F"
    DayOfWeek.SATURDAY -> "S"
    DayOfWeek.SUNDAY -> "S"
}

private fun formatCompactDistance(meters: Double, unitSystem: UnitSystem): String {
    return if (unitSystem == UnitSystem.METRIC) {
        val km = meters / 1000.0
        if (km >= 100) "%.0f km".format(km) else "%.1f km".format(km)
    } else {
        val mi = meters / 1609.344
        if (mi >= 100) "%.0f mi".format(mi) else "%.1f mi".format(mi)
    }
}

// ── Achievements overview card ──────────────────────────────────────────────

@Composable
private fun AchievementsOverviewCard(
    summary: AchievementSummary,
    onSeeAll: () -> Unit,
) {
    val progress = if (summary.totalCount > 0) {
        summary.unlockedCount.toFloat() / summary.totalCount.toFloat()
    } else 0f

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
        shape = RoundedCornerShape(20.dp),
        onClick = onSeeAll,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.EmojiEvents,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Achievements",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "${summary.unlockedCount} / ${summary.totalCount}",
                    style = MaterialTheme.typography.labelLarge,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(10.dp))
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            )
            summary.nextAchievement?.let { next ->
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "Next: ${next.title}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LinearProgressIndicator(
                        progress = { next.progress.toFloat() },
                        modifier = Modifier
                            .weight(1f)
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = MaterialTheme.colorScheme.tertiary,
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "${(next.progress * 100).toInt()}%",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

// ── Settings list ───────────────────────────────────────────────────────────

@Composable
private fun SettingsList(
    onPreferences: () -> Unit,
    onAnalytics: () -> Unit,
    onGarage: () -> Unit,
    onStints: () -> Unit,
    onTrips: () -> Unit,
    onCommonRoutes: () -> Unit,
    onFriends: () -> Unit,
    onAchievements: () -> Unit,
) {
    val rows = listOf(
        Row4(Icons.Filled.DirectionsCar, "Garage", "Manage your vehicles", onGarage),
        Row4(Icons.Filled.Route, "Stints", "All recorded drives", onStints),
        Row4(Icons.Filled.Map, "Trips", "Group drives into trips", onTrips),
        Row4(Icons.Filled.TrendingUp, "Common routes", "Routes you drive often", onCommonRoutes),
        Row4(Icons.Filled.BarChart, "Driving analytics", "Trends, weather, vehicle mix", onAnalytics),
        Row4(Icons.Filled.EmojiEvents, "Achievements", "Track milestones", onAchievements),
        Row4(Icons.Filled.People, "Friends", "Follow and connect", onFriends),
        Row4(Icons.Filled.Settings, "Preferences", "Units, map style, voice", onPreferences),
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(modifier = Modifier.padding(vertical = 4.dp)) {
            rows.forEachIndexed { idx, row ->
                if (idx > 0) {
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 62.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                    )
                }
                SettingsRow(row)
            }
        }
    }
}

private data class Row4(
    val icon: ImageVector,
    val label: String,
    val hint: String,
    val onClick: () -> Unit,
)

@Composable
private fun SettingsRow(row: Row4) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = row.onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(
                    MaterialTheme.colorScheme.surfaceContainerHigh,
                    RoundedCornerShape(10.dp),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = row.icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = row.label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = row.hint,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            imageVector = Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

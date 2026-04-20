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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rallytrax.app.data.preferences.UnitSystem
import com.rallytrax.app.ui.components.Sparkline
import com.rallytrax.app.ui.theme.RallyTraxTypeEmphasized
import com.rallytrax.app.ui.theme.ShapeLargeIncreased
import com.rallytrax.app.ui.theme.rallyTraxColors
import com.rallytrax.app.util.formatDistance
import com.rallytrax.app.util.formatSpeed
import com.rallytrax.app.util.speedUnit
import java.util.Locale

/**
 * Driving analytics — extracted from ProfileScreen so the base Profile can
 * focus on identity/lifetime stats/activity/achievements. Hosts the richer
 * cards: driving trends, vehicle comparison, corner radar, weather impact.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalyticsScreen(
    onBack: () -> Unit = {},
    viewModel: ProfileViewModel = hiltViewModel(),
) {
    val profile by viewModel.profileState.collectAsStateWithLifecycle()
    val preferences by viewModel.preferences.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Driving analytics") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
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
            Spacer(Modifier.height(8.dp))

            if (profile.currentStreak > 0) {
                StreakCard(streak = profile.currentStreak)
                Spacer(Modifier.height(16.dp))
            }

            Text(
                text = "Lifetime",
                style = RallyTraxTypeEmphasized.titleMedium,
            )
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                HeroStatCard(Modifier.weight(1f), Icons.Filled.Route, "${profile.totalDrives}", "Total Drives")
                HeroStatCard(Modifier.weight(1f), Icons.Filled.LocalFireDepartment, "${profile.stintCount}", "Stints")
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                HeroStatCard(
                    Modifier.weight(1f), Icons.Filled.Straighten,
                    formatDistance(profile.totalDistanceMeters, preferences.unitSystem), "Total Distance",
                )
                HeroStatCard(
                    Modifier.weight(1f), Icons.Filled.Speed,
                    formatDistance(profile.avgDriveLengthMeters, preferences.unitSystem), "Avg Drive",
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                HeroStatCard(Modifier.weight(1f), Icons.Filled.DirectionsCar, "${profile.vehicleCount}", "Cars")
                HeroStatCard(
                    Modifier.weight(1f), Icons.Filled.Timer,
                    formatDistance(profile.longestDriveMeters, preferences.unitSystem), "Longest Drive",
                )
            }

            Spacer(Modifier.height(16.dp))
            Text(text = "This Year", style = RallyTraxTypeEmphasized.titleMedium)
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                HeroStatCard(Modifier.weight(1f), Icons.Filled.Route, "${profile.yearlyDrives}", "Drives")
                HeroStatCard(
                    Modifier.weight(1f), Icons.Filled.Straighten,
                    formatDistance(profile.yearlyDistanceMeters, preferences.unitSystem), "Distance",
                )
            }

            DrivingTrendsCard(profile = profile)

            if (profile.vehicleComparison.size >= 2) {
                Spacer(Modifier.height(16.dp))
                VehicleComparisonCard(profile.vehicleComparison, preferences.unitSystem)
            }

            if (profile.driverProfile.size >= 3) {
                Spacer(Modifier.height(16.dp))
                DriverRadarCard(profile.driverProfile)
            }

            if (profile.weatherBuckets.size >= 2) {
                Spacer(Modifier.height(16.dp))
                WeatherImpactCard(profile.weatherBuckets, preferences.unitSystem)
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

// ── Cards (copied from the pre-redesign ProfileScreen) ──────────────────────

@Composable
private fun StreakCard(streak: Int) {
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
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    text = "$streak day streak!",
                    style = RallyTraxTypeEmphasized.titleMedium,
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

@Composable
private fun HeroStatCard(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    value: String,
    label: String,
) {
    Card(
        modifier = modifier,
        shape = ShapeLargeIncreased,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = value,
                style = RallyTraxTypeEmphasized.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DrivingTrendsCard(profile: ProfileState) {
    val hasTrends = profile.smoothnessTrend.size >= 2 ||
        profile.brakingTrend.size >= 2 ||
        profile.corneringGTrend.size >= 2
    if (!hasTrends) return
    Spacer(Modifier.height(16.dp))
    Text(text = "Driving Trends", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(8.dp))
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (profile.smoothnessTrend.size >= 2) {
                TrendRow("Smoothness", profile.latestSmoothness?.toString() ?: "--", profile.smoothnessTrend)
            }
            if (profile.brakingTrend.size >= 2) {
                TrendRow("Braking", profile.latestBraking?.toString() ?: "--", profile.brakingTrend)
            }
            if (profile.corneringGTrend.size >= 2) {
                TrendRow(
                    "Cornering G",
                    profile.latestCorneringG?.let { String.format(Locale.US, "%.2f", it) } ?: "--",
                    profile.corneringGTrend,
                )
            }
        }
    }
}

@Composable
private fun TrendRow(label: String, latestValue: String, trendData: List<Float>) {
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
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text("$latestValue$trendArrow", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = trendColor)
        Spacer(Modifier.width(12.dp))
        Sparkline(data = trendData, color = trendColor, modifier = Modifier.width(60.dp).height(24.dp))
    }
}

@Composable
private fun VehicleComparisonCard(vehicleStats: List<VehicleStats>, unitSystem: UnitSystem) {
    val maxDistance = vehicleStats.maxOfOrNull { it.totalDistanceMeters } ?: 1.0
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Vehicle Comparison", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            vehicleStats.forEach { stats ->
                val fraction = if (maxDistance > 0) {
                    (stats.totalDistanceMeters / maxDistance).toFloat().coerceIn(0f, 1f)
                } else 0f
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stats.name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.width(8.dp))
                    Text("${stats.driveCount}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(8.dp))
                    Text(formatDistance(stats.totalDistanceMeters, unitSystem), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
private fun DriverRadarCard(driverProfile: Map<Int, Double>) {
    val sortedEntries = remember(driverProfile) { driverProfile.entries.sortedBy { it.key } }
    val axisCount = sortedEntries.size
    val maxSpeed = remember(sortedEntries) { sortedEntries.maxOf { it.value }.coerceAtLeast(1.0) }
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
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Corner Speed Profile", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(
                "Your average speed through turns of different radii",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))

            Canvas(modifier = Modifier.fillMaxWidth().height(200.dp)) {
                val centerX = size.width / 2f
                val centerY = size.height / 2f
                val chartRadius = minOf(centerX, centerY) - labelTextSizePx * 2.5f
                val angleStep = (2.0 * Math.PI / axisCount).toFloat()
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
                        canvas.nativeCanvas.drawText("${sortedEntries[i].key}m", lx, ly, labelPaint)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                if (strongest != null) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Strongest", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("${strongest.key}m radius", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                }
                if (weakest != null) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Weakest", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("${weakest.key}m radius", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

private val BASELINE_CONDITION_GROUPS = setOf("Clear", "Clouds")
private val IMPACT_CONDITION_GROUPS = setOf("Rain", "Drizzle", "Thunderstorm", "Snow")

@Composable
private fun WeatherImpactCard(buckets: List<WeatherBucket>, unitSystem: UnitSystem) {
    val clearBucket = buckets.find { it.label in BASELINE_CONDITION_GROUPS }
    val impactBuckets = buckets.filter { it.label in IMPACT_CONDITION_GROUPS }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Weather Impact", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            buckets.forEach { bucket ->
                WeatherBucketRow(bucket, unitSystem)
                Spacer(Modifier.height(8.dp))
            }
            if (clearBucket != null && impactBuckets.isNotEmpty()) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                Spacer(Modifier.height(4.dp))
                impactBuckets.forEach { impactBucket ->
                    if (clearBucket.avgSpeedMps > 0) {
                        ComparisonRow(
                            label = "${impactBucket.label} vs ${clearBucket.label}:",
                            diffPercent = ((impactBucket.avgSpeedMps - clearBucket.avgSpeedMps) / clearBucket.avgSpeedMps) * 100,
                            positiveLabel = "faster",
                            negativeLabel = "slower",
                        )
                    }
                    val clearSmooth = clearBucket.avgSmoothness
                    val impactSmooth = impactBucket.avgSmoothness
                    if (clearSmooth != null && impactSmooth != null && clearSmooth > 0) {
                        ComparisonRow(
                            label = "${impactBucket.label} smoothness:",
                            diffPercent = ((impactSmooth - clearSmooth) / clearSmooth) * 100,
                            positiveLabel = "smoother",
                            negativeLabel = "rougher",
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                }
            }
        }
    }
}

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
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(text, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = color)
    }
}

@Composable
private fun WeatherBucketRow(bucket: WeatherBucket, unitSystem: UnitSystem) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(bucket.label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(
                "${bucket.driveCount} drive${if (bucket.driveCount != 1) "s" else ""}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                "${formatSpeed(bucket.avgSpeedMps, unitSystem)} ${speedUnit(unitSystem)}",
                style = MaterialTheme.typography.bodyMedium,
            )
            if (bucket.avgSmoothness != null) {
                Text(
                    String.format(Locale.US, "%.0f smoothness", bucket.avgSmoothness),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

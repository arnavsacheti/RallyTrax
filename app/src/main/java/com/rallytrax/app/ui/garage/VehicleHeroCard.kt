package com.rallytrax.app.ui.garage

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rallytrax.app.data.local.entity.TrackEntity
import com.rallytrax.app.data.local.entity.VehicleEntity
import com.rallytrax.app.data.preferences.UnitSystem
import com.rallytrax.app.util.formatSpeed
import com.rallytrax.app.util.speedUnit

// Horizontal gradient hero inspired by the RallyTrax design handoff.
// Left: colored tile with vehicle-type icon, right: plate / nickname / make-model
// and badges. Bottom: 4-stat quick row divided by a subtle top border.
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun VehicleHeroCard(
    vehicle: VehicleEntity,
    tracks: List<TrackEntity>,
    unitSystem: UnitSystem,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val hue = ((vehicle.id.hashCode() and 0x7fffffff) % 360).toFloat()
    val isDark = isSystemInDarkTheme()
    val tint = Color.hsl(hue, 0.55f, if (isDark) 0.30f else 0.52f)
    val bgStart = Color.hsl(hue, 0.55f, if (isDark) 0.22f else 0.82f)
    val bgEnd = Color.hsl((hue + 30f) % 360f, 0.50f, if (isDark) 0.14f else 0.92f)
    val onBg = if (isDark) Color(0xFFFFFFFF) else Color(0xFF0B1220)
    val muted = onBg.copy(alpha = 0.70f)
    val chipBg = if (isDark) Color.White.copy(alpha = 0.12f)
    else Color.Black.copy(alpha = 0.08f)

    val cardModifier = modifier.fillMaxWidth()
    val content: @Composable () -> Unit = {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(brush = Brush.linearGradient(listOf(bgStart, bgEnd))),
        ) {
            Row(
                modifier = Modifier.padding(18.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .background(tint, RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = vehicleTypeIcon(vehicle.vehicleType),
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(40.dp),
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    val overline = vehicle.vin?.takeLast(6)?.uppercase()
                        ?: vehicle.trim?.uppercase()
                        ?: vehicle.year.toString()
                    Text(
                        text = overline,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.8.sp,
                        color = muted,
                    )
                    Text(
                        text = vehicle.name.ifBlank { "${vehicle.year} ${vehicle.make}" },
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = onBg,
                    )
                    Text(
                        text = "${vehicle.year} ${vehicle.make} ${vehicle.model}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = onBg.copy(alpha = 0.85f),
                    )
                    val badges = listOfNotNull(
                        vehicle.drivetrain?.takeIf { it.isNotBlank() },
                        vehicle.fuelType?.takeIf { it.isNotBlank() },
                        vehicle.transmissionType?.takeIf { it.isNotBlank() },
                    )
                    if (badges.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            badges.forEach { badge ->
                                Text(
                                    text = badge,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = onBg,
                                    modifier = Modifier
                                        .background(chipBg, RoundedCornerShape(999.dp))
                                        .padding(horizontal = 8.dp, vertical = 3.dp),
                                )
                            }
                        }
                    }
                }
            }

            HorizontalDivider(color = onBg.copy(alpha = 0.12f), thickness = 1.dp)

            val stintCount = tracks.size
            val totalHours = tracks.sumOf { it.durationMs } / 3_600_000.0
            val topSpeedMps = tracks.maxOfOrNull { it.maxSpeedMps } ?: 0.0
            val odometerValue: String
            val odometerUnit: String
            if (unitSystem == UnitSystem.METRIC) {
                odometerValue = "%.1f".format(vehicle.odometerKm / 1000.0)
                odometerUnit = "k km"
            } else {
                odometerValue = "%.1f".format(vehicle.odometerKm * 0.621371 / 1000.0)
                odometerUnit = "k mi"
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
            ) {
                HeroStat(
                    label = "Odometer",
                    value = odometerValue,
                    unit = odometerUnit,
                    color = onBg,
                    muted = muted,
                    modifier = Modifier.weight(1f),
                )
                HeroStat(
                    label = "Stints",
                    value = stintCount.toString(),
                    color = onBg,
                    muted = muted,
                    modifier = Modifier.weight(1f),
                )
                HeroStat(
                    label = "Driven",
                    value = "%.0f".format(totalHours),
                    unit = "h",
                    color = onBg,
                    muted = muted,
                    modifier = Modifier.weight(1f),
                )
                HeroStat(
                    label = "Top spd",
                    value = formatSpeed(topSpeedMps, unitSystem),
                    unit = speedUnit(unitSystem),
                    color = MaterialTheme.colorScheme.primary,
                    muted = muted,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }

    if (onClick != null) {
        Card(
            modifier = cardModifier,
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.Transparent),
            onClick = onClick,
        ) { content() }
    } else {
        Card(
            modifier = cardModifier,
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        ) { content() }
    }
}

@Composable
private fun HeroStat(
    label: String,
    value: String,
    color: Color,
    muted: Color,
    unit: String? = null,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.8.sp,
            color = muted,
        )
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = value,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.SemiBold,
                fontSize = 17.sp,
                letterSpacing = (-0.3).sp,
                color = color,
            )
            if (unit != null) {
                Spacer(Modifier.width(3.dp))
                Text(
                    text = unit,
                    style = MaterialTheme.typography.labelSmall,
                    color = muted,
                )
            }
        }
    }
}

package com.rallytrax.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Landscape
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.rallytrax.app.data.local.entity.TrackEntity
import com.rallytrax.app.data.preferences.UnitSystem
import com.rallytrax.app.ui.components.DifficultyChip
import com.rallytrax.app.ui.components.SurfaceBreakdownBar
import com.rallytrax.app.util.formatDistance
import com.rallytrax.app.util.formatElapsedTime
import com.rallytrax.app.util.formatElevation
import com.rallytrax.app.util.formatRelativeTime
import com.rallytrax.app.util.formatSpeed
import com.rallytrax.app.util.speedUnit

data class ActivityFeedItem(
    val track: TrackEntity,
    val vehicleName: String? = null,
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ActivityFeedCard(
    feedItem: ActivityFeedItem,
    unitSystem: UnitSystem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val track = feedItem.track
    val isRoute = track.trackCategory == "route"

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        onClick = onClick,
    ) {
        // ── Map poster section ────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(if (track.thumbnailPath != null) 200.dp else 0.dp),
        ) {
            track.thumbnailPath?.let { path ->
                AsyncImage(
                    model = path,
                    contentDescription = "Map of ${track.name}",
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)),
                    contentScale = ContentScale.Crop,
                )

                // Gradient scrim over bottom of image
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.65f),
                                ),
                            ),
                        ),
                )

                // Category badge (top-left)
                Box(
                    modifier = Modifier
                        .padding(12.dp)
                        .align(Alignment.TopStart)
                        .background(
                            Color.Black.copy(alpha = 0.5f),
                            RoundedCornerShape(16.dp),
                        )
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(
                            imageVector = if (isRoute) Icons.Filled.Route else Icons.Filled.Map,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = Color.White,
                        )
                        Text(
                            text = if (isRoute) "Route" else "Drive",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White,
                        )
                    }
                }

                // Title + timestamp overlaid on image bottom
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    Text(
                        text = track.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = formatRelativeTime(track.recordedAt),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.8f),
                    )
                }
            }
        }

        // ── No thumbnail fallback: plain title row ────────────────────
        if (track.thumbnailPath == null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(
                        imageVector = if (isRoute) Icons.Filled.Route else Icons.Filled.Map,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = track.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = formatRelativeTime(track.recordedAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // ── Hero metrics ──────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            HeroMetric(
                icon = Icons.Filled.Route,
                value = formatDistance(track.distanceMeters, unitSystem),
                label = "Distance",
                modifier = Modifier.weight(1f),
            )
            HeroMetric(
                icon = Icons.Filled.Timer,
                value = formatElapsedTime(track.durationMs),
                label = "Duration",
                modifier = Modifier.weight(1f),
            )
            if (track.maxSpeedMps > 0 && !isRoute) {
                HeroMetric(
                    icon = Icons.Filled.Speed,
                    value = "${formatSpeed(track.maxSpeedMps, unitSystem)} ${speedUnit(unitSystem)}",
                    label = "Top Speed",
                    modifier = Modifier.weight(1f),
                )
            } else if (track.elevationGainM > 0) {
                HeroMetric(
                    icon = Icons.Filled.Landscape,
                    value = formatElevation(track.elevationGainM, unitSystem),
                    label = "Elevation",
                    modifier = Modifier.weight(1f),
                )
            }
        }

        // ── Description ───────────────────────────────────────────────
        track.description?.let { desc ->
            if (desc.isNotBlank()) {
                Text(
                    text = desc,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
        }

        // ── Surface breakdown bar ─────────────────────────────────────
        track.surfaceBreakdown?.let { breakdown ->
            if (breakdown.isNotBlank()) {
                Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                    SurfaceBreakdownBar(breakdown)
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
        }

        // ── Footer: difficulty + vehicle + tags ───────────────────────
        val hasDifficulty = track.difficultyRating != null
        val hasVehicle = feedItem.vehicleName != null
        val hasTags = track.tags.isNotBlank()

        if (hasDifficulty || hasVehicle || hasTags) {
            FlowRow(
                modifier = Modifier.padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                track.difficultyRating?.let { DifficultyChip(it) }

                feedItem.vehicleName?.let { name ->
                    Row(
                        modifier = Modifier
                            .background(
                                MaterialTheme.colorScheme.secondaryContainer,
                                RoundedCornerShape(12.dp),
                            )
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.DirectionsCar,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                        Text(
                            text = name,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                if (hasTags) {
                    track.tags.split(",").map { it.trim() }.filter { it.isNotBlank() }.forEach { tag ->
                        Text(
                            text = tag,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .background(
                                    MaterialTheme.colorScheme.primaryContainer,
                                    RoundedCornerShape(12.dp),
                                )
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(14.dp))
        } else {
            Spacer(modifier = Modifier.height(4.dp))
        }
    }
}

// ── Hero Metric ──────────────────────────────────────────────────────────────

@Composable
private fun HeroMetric(
    icon: ImageVector,
    value: String,
    label: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

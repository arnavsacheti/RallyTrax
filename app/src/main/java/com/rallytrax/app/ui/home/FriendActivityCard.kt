package com.rallytrax.app.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Landscape
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.rallytrax.app.data.preferences.UnitSystem
import com.rallytrax.app.data.social.SharedTrack
import com.rallytrax.app.ui.components.DifficultyChip
import com.rallytrax.app.util.formatDistance
import com.rallytrax.app.util.formatElapsedTime
import com.rallytrax.app.util.formatRelativeTime
import com.rallytrax.app.util.formatSpeed
import com.rallytrax.app.util.speedUnit

@Composable
fun FriendActivityCard(
    sharedTrack: SharedTrack,
    unitSystem: UnitSystem,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // ── Author row ────────────────────────────────────────────
            Row(verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(
                    model = sharedTrack.ownerPhotoUrl,
                    contentDescription = "${sharedTrack.ownerDisplayName}'s profile photo",
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape),
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = sharedTrack.ownerDisplayName ?: "Unknown",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = formatRelativeTime(sharedTrack.recordedAt),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // ── Track title ───────────────────────────────────────────
            Text(
                text = sharedTrack.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            Spacer(modifier = Modifier.height(12.dp))

            // ── Hero metrics ──────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                FriendMetric(
                    icon = Icons.Filled.Route,
                    value = formatDistance(sharedTrack.distanceMeters, unitSystem),
                    label = "Distance",
                    modifier = Modifier.weight(1f),
                )
                FriendMetric(
                    icon = Icons.Filled.Timer,
                    value = formatElapsedTime(sharedTrack.durationMs),
                    label = "Duration",
                    modifier = Modifier.weight(1f),
                )
                if (sharedTrack.avgSpeedMps > 0) {
                    FriendMetric(
                        icon = Icons.Filled.Speed,
                        value = "${formatSpeed(sharedTrack.avgSpeedMps, unitSystem)} ${speedUnit(unitSystem)}",
                        label = "Avg Speed",
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            // ── Difficulty ────────────────────────────────────────────
            sharedTrack.difficultyRating?.let {
                Spacer(modifier = Modifier.height(12.dp))
                DifficultyChip(it)
            }
        }
    }
}

@Composable
private fun FriendMetric(
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
            maxLines = 1,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.rallytrax.app.data.preferences.UnitSystem
import com.rallytrax.app.data.social.SharedTrack
import com.rallytrax.app.ui.components.DifficultyChip
import com.rallytrax.app.ui.components.StatLabel
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
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(
                    model = sharedTrack.ownerPhotoUrl,
                    contentDescription = "${sharedTrack.ownerDisplayName}'s profile photo",
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = sharedTrack.ownerDisplayName ?: "Unknown",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = formatRelativeTime(sharedTrack.recordedAt),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = sharedTrack.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                StatLabel("Distance", formatDistance(sharedTrack.distanceMeters, unitSystem))
                StatLabel("Duration", formatElapsedTime(sharedTrack.durationMs))
                if (sharedTrack.avgSpeedMps > 0) {
                    StatLabel(
                        "Avg Speed",
                        "${formatSpeed(sharedTrack.avgSpeedMps, unitSystem)} ${speedUnit(unitSystem)}",
                    )
                }
            }

            sharedTrack.difficultyRating?.let {
                Spacer(modifier = Modifier.height(8.dp))
                DifficultyChip(it)
            }
        }
    }
}

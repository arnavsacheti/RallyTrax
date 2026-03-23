package com.rallytrax.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rallytrax.app.data.local.entity.TrackEntity
import com.rallytrax.app.data.preferences.UnitSystem
import com.rallytrax.app.util.formatDistance
import com.rallytrax.app.util.formatElapsedTime

@Composable
fun RoutePreviewCard(
    track: TrackEntity,
    unitSystem: UnitSystem,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
        onClick = { onClick?.invoke() },
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Name and difficulty
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = track.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                track.difficultyRating?.let { difficulty ->
                    DifficultyChip(difficulty)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Key stats row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                StatLabel("Distance", formatDistance(track.distanceMeters, unitSystem))
                StatLabel("Time", formatElapsedTime(track.durationMs))
                if (track.elevationGainM > 0) {
                    StatLabel("Elevation", "${track.elevationGainM.toInt()} m")
                }
                track.routeType?.let {
                    StatLabel("Type", it)
                }
            }

            // Surface breakdown bar
            track.surfaceBreakdown?.let { breakdown ->
                if (breakdown.isNotBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    SurfaceBreakdownBar(breakdown)
                }
            }
        }
    }
}

@Composable
private fun DifficultyChip(difficulty: String) {
    val color = when (difficulty) {
        "Casual" -> Color(0xFF34A853)
        "Moderate" -> Color(0xFFFBBC04)
        "Spirited" -> Color(0xFFE8710A)
        "Expert" -> Color(0xFFEA4335)
        else -> Color.Gray
    }
    Box(
        modifier = Modifier
            .background(color.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Text(
            text = difficulty,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = color,
        )
    }
}

@Composable
private fun StatLabel(label: String, value: String) {
    Column {
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SurfaceBreakdownBar(breakdown: String) {
    // Parse "Paved:72,Gravel:20,Dirt:8"
    val segments = breakdown.split(",").mapNotNull { segment ->
        val parts = segment.trim().split(":")
        if (parts.size == 2) {
            val type = parts[0].trim()
            val pct = parts[1].trim().toDoubleOrNull() ?: return@mapNotNull null
            type to pct
        } else null
    }
    if (segments.isEmpty()) return

    val totalPct = segments.sumOf { it.second }.coerceAtLeast(1.0)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(RoundedCornerShape(3.dp)),
    ) {
        segments.forEach { (type, pct) ->
            val color = when (type.lowercase()) {
                "paved", "tarmac" -> Color(0xFF607D8B)
                "gravel" -> Color(0xFFD4A574)
                "dirt" -> Color(0xFF8B6914)
                "cobblestone" -> Color(0xFF9E9E9E)
                else -> Color(0xFFBDBDBD)
            }
            val fraction = (pct / totalPct).toFloat()
            Box(
                modifier = Modifier
                    .weight(fraction)
                    .height(6.dp)
                    .background(color),
            )
        }
    }

    Spacer(modifier = Modifier.height(4.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        segments.forEach { (type, pct) ->
            Text(
                text = "${type} ${pct.toInt()}%",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

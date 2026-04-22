package com.rallytrax.app.ui.trips

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GroupWork
import androidx.compose.material.icons.filled.Route
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rallytrax.app.data.local.entity.TripSuggestionEntity
import com.rallytrax.app.data.preferences.UnitSystem
import com.rallytrax.app.util.formatDistance
import com.rallytrax.app.util.formatElapsedTime

/**
 * Card showing a trip grouping suggestion with accept/dismiss actions.
 * Uses M3 Expressive styling with secondary container for the suggestion surface.
 */
@Composable
fun TripSuggestionCard(
    suggestion: TripSuggestionEntity,
    onAccept: (String) -> Unit,
    onDismiss: (String) -> Unit,
    modifier: Modifier = Modifier,
    unitSystem: UnitSystem = UnitSystem.METRIC,
) {
    var visible by remember { mutableStateOf(true) }

    AnimatedVisibility(
        visible = visible,
        exit = shrinkVertically() + fadeOut(),
    ) {
        Card(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                // Header row: icon + title + dismiss
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Filled.AutoAwesome,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Trip Suggestion",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    IconButton(
                        onClick = {
                            visible = false
                            onDismiss(suggestion.id)
                        },
                        modifier = Modifier.size(24.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Dismiss suggestion",
                            tint = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.6f),
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Suggested name
                Text(
                    text = suggestion.aiGeneratedName ?: suggestion.suggestedName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Stats row
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    SuggestionStat(
                        icon = Icons.Filled.Route,
                        value = "${suggestion.stintCount} stints",
                    )
                    SuggestionStat(
                        icon = Icons.Filled.GroupWork,
                        value = formatDistance(suggestion.totalDistanceMeters, unitSystem),
                    )
                    if (suggestion.totalDurationMs > 0) {
                        Text(
                            text = formatElapsedTime(suggestion.totalDurationMs),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f),
                        )
                    }
                }

                // AI badge if name was AI-generated
                if (suggestion.aiGeneratedName != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.AutoAwesome,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(12.dp),
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Named by AI",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.tertiary,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(
                        onClick = {
                            visible = false
                            onDismiss(suggestion.id)
                        },
                    ) {
                        Text("Dismiss")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    FilledTonalButton(
                        onClick = { onAccept(suggestion.id) },
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                    ) {
                        Text("Create Trip")
                    }
                }
            }
        }
    }
}

@Composable
private fun SuggestionStat(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    value: String,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f),
            modifier = Modifier.size(14.dp),
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f),
        )
    }
}

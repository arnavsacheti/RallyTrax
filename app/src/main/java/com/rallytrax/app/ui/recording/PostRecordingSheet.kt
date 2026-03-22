package com.rallytrax.app.ui.recording

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Route
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rallytrax.app.data.classification.RouteClassifier

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PostRecordingSheet(
    suggestedRouteType: String,
    curvinessScore: Double,
    difficultyRating: String,
    onAccept: (routeType: String, difficulty: String, activityTags: List<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    var selectedRouteType by remember { mutableStateOf(suggestedRouteType) }
    var selectedDifficulty by remember { mutableStateOf(difficultyRating) }
    var selectedActivityTags by remember { mutableStateOf(setOf<String>()) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Route, null, modifier = Modifier.size(28.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Classify Your Drive",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Curviness: ${curvinessScore.toInt()} • Suggested: $suggestedRouteType",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Route type selection
            Text("Route Type", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(4.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                RouteClassifier.allRouteTypes.forEach { type ->
                    FilterChip(
                        selected = selectedRouteType == type,
                        onClick = { selectedRouteType = type },
                        label = { Text(type, style = MaterialTheme.typography.labelMedium) },
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Difficulty
            Text("Difficulty", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf(
                    RouteClassifier.DIFFICULTY_CASUAL,
                    RouteClassifier.DIFFICULTY_MODERATE,
                    RouteClassifier.DIFFICULTY_SPIRITED,
                    RouteClassifier.DIFFICULTY_EXPERT,
                ).forEach { diff ->
                    val color = when (diff) {
                        RouteClassifier.DIFFICULTY_CASUAL -> Color(0xFF34A853)
                        RouteClassifier.DIFFICULTY_MODERATE -> Color(0xFFFBBC04)
                        RouteClassifier.DIFFICULTY_SPIRITED -> Color(0xFFE8710A)
                        RouteClassifier.DIFFICULTY_EXPERT -> Color(0xFFEA4335)
                        else -> MaterialTheme.colorScheme.primary
                    }
                    FilterChip(
                        selected = selectedDifficulty == diff,
                        onClick = { selectedDifficulty = diff },
                        label = { Text(diff) },
                        leadingIcon = if (selectedDifficulty == diff) {
                            { Icon(Icons.Filled.Check, null, modifier = Modifier.size(16.dp)) }
                        } else null,
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Activity tags (stackable)
            Text("Activity", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(4.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                RouteClassifier.allActivityTags.forEach { tag ->
                    FilterChip(
                        selected = tag in selectedActivityTags,
                        onClick = {
                            selectedActivityTags = if (tag in selectedActivityTags) {
                                selectedActivityTags - tag
                            } else {
                                selectedActivityTags + tag
                            }
                        },
                        label = { Text(tag, style = MaterialTheme.typography.labelMedium) },
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Accept button
            FilledTonalButton(
                onClick = {
                    onAccept(selectedRouteType, selectedDifficulty, selectedActivityTags.toList())
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.Check, null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Save Classification")
            }
        }
    }
}

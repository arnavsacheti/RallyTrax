package com.rallytrax.app.ui.trackdetail

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
import androidx.compose.material.icons.automirrored.outlined.NavigateBefore
import androidx.compose.material.icons.automirrored.outlined.NavigateNext
import androidx.compose.material.icons.outlined.Height
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.automirrored.outlined.TrendingDown
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rallytrax.app.data.local.entity.PaceNoteEntity
import com.rallytrax.app.data.preferences.UnitSystem
import com.rallytrax.app.pacenotes.PaceNoteExplanation
import com.rallytrax.app.util.formatDistance

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaceNoteDetailSheet(
    note: PaceNoteEntity,
    explanation: PaceNoteExplanation,
    prevNote: PaceNoteEntity?,
    nextNote: PaceNoteEntity?,
    unitSystem: UnitSystem,
    onDismiss: () -> Unit,
    onNavigateToNote: (String) -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // ── Header ──────────────────────────────────────────────
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Severity badge
                Card {
                    Text(
                        text = note.severity.toString(),
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    )
                }
                Text(
                    text = note.callText,
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.weight(1f),
                )
            }

            HorizontalDivider()

            // ── Why This Call ────────────────────────────────────────
            Text(
                text = "Why This Call",
                style = MaterialTheme.typography.titleMedium,
            )

            explanation.gradeReason?.let { reason ->
                ExplanationRow(icon = Icons.Outlined.Tune, text = reason)
            }

            explanation.safeSpeedKmh?.let { speed ->
                val formatted = if (unitSystem == UnitSystem.IMPERIAL) {
                    "%.0f mph".format(speed * 0.621371)
                } else {
                    "%.0f km/h".format(speed)
                }
                ExplanationRow(icon = Icons.Outlined.Speed, text = "Recommended speed: $formatted")
            }

            explanation.modifierReason?.let { reason ->
                ExplanationRow(icon = Icons.AutoMirrored.Outlined.TrendingDown, text = reason)
            }

            explanation.elevationReason?.let { reason ->
                ExplanationRow(icon = Icons.Outlined.Height, text = reason)
            }

            explanation.conjunctionReason?.let { reason ->
                ExplanationRow(icon = Icons.Outlined.SwapHoriz, text = reason)
            }

            // ── Segment stats ───────────────────────────────────────
            val hasStats = explanation.segmentLengthM != null || explanation.bearingChangeDeg != null
            if (hasStats) {
                HorizontalDivider()
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    explanation.segmentLengthM?.let { length ->
                        StatItem(
                            label = "Segment",
                            value = formatDistance(length, unitSystem),
                        )
                    }
                    explanation.bearingChangeDeg?.let { change ->
                        StatItem(label = "Turn", value = "%.0f\u00b0".format(change))
                    }
                    note.callDistanceM.takeIf { it > 0 }?.let { dist ->
                        StatItem(
                            label = "To next",
                            value = formatDistance(dist, unitSystem),
                        )
                    }
                }
            }

            // ── Prev / Next navigation ──────────────────────────────
            if (prevNote != null || nextNote != null) {
                HorizontalDivider()
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    if (prevNote != null) {
                        AssistChip(
                            onClick = { onNavigateToNote(prevNote.id) },
                            label = {
                                Text(
                                    text = prevNote.callText,
                                    maxLines = 1,
                                    style = MaterialTheme.typography.labelMedium,
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    Icons.AutoMirrored.Outlined.NavigateBefore,
                                    contentDescription = "Previous",
                                    modifier = Modifier.size(18.dp),
                                )
                            },
                        )
                    } else {
                        Spacer(Modifier.width(1.dp))
                    }

                    if (nextNote != null) {
                        AssistChip(
                            onClick = { onNavigateToNote(nextNote.id) },
                            label = {
                                Text(
                                    text = nextNote.callText,
                                    maxLines = 1,
                                    style = MaterialTheme.typography.labelMedium,
                                )
                            },
                            trailingIcon = {
                                Icon(
                                    Icons.AutoMirrored.Outlined.NavigateNext,
                                    contentDescription = "Next",
                                    modifier = Modifier.size(18.dp),
                                )
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ExplanationRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
) {
    Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun StatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

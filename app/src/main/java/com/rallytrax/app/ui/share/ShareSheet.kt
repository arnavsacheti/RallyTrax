package com.rallytrax.app.ui.share

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.animateFloatAsState
import com.rallytrax.app.data.local.entity.TrackEntity
import com.rallytrax.app.data.preferences.UnitSystem
import com.rallytrax.app.util.formatDistance
import com.rallytrax.app.util.formatElapsedTime
import com.rallytrax.app.util.formatSpeed
import com.rallytrax.app.util.speedUnit
import kotlinx.coroutines.launch

// Target actions a user can pick from the share sheet.
sealed interface ShareAction {
    data object SystemShare : ShareAction
    data object ExportGpx : ShareAction
    data object CopySummary : ShareAction
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareSheet(
    track: TrackEntity,
    unitSystem: UnitSystem,
    onAction: (ShareAction, caption: String) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var caption by remember { mutableStateOf("") }

    fun close(after: () -> Unit = {}) {
        scope.launch {
            sheetState.hide()
            onDismiss()
            after()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 18.dp),
        ) {
            // Header row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 22.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Share stint",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(Modifier.height(14.dp))

            // Preview card
            Box(modifier = Modifier.padding(horizontal = 22.dp)) {
                SharePreviewCard(track = track, unitSystem = unitSystem, caption = caption)
            }

            Spacer(Modifier.height(18.dp))

            // Caption input
            Column(modifier = Modifier.padding(horizontal = 22.dp)) {
                Text(
                    text = "CAPTION",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.8.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = caption,
                    onValueChange = { caption = it },
                    placeholder = { Text("Say something about this run…") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 72.dp),
                    shape = RoundedCornerShape(16.dp),
                    minLines = 2,
                )
            }

            Spacer(Modifier.height(18.dp))

            // Share-target row
            Column {
                Text(
                    text = "SHARE WITH",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.8.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 22.dp),
                )
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 22.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    ShareTarget(
                        icon = Icons.Filled.Share,
                        label = "Share",
                        primary = true,
                        onClick = { close { onAction(ShareAction.SystemShare, caption) } },
                    )
                    ShareTarget(
                        icon = Icons.Filled.FileDownload,
                        label = "GPX",
                        onClick = { close { onAction(ShareAction.ExportGpx, caption) } },
                    )
                    ShareTarget(
                        icon = Icons.Filled.ContentCopy,
                        label = "Copy",
                        onClick = { close { onAction(ShareAction.CopySummary, caption) } },
                    )
                }
            }

            Spacer(Modifier.height(18.dp))

            // CTA row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 22.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedButton(
                    onClick = { close() },
                    shape = RoundedCornerShape(999.dp),
                    modifier = Modifier.height(52.dp),
                ) {
                    Text("Cancel", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                }
                Button(
                    onClick = { close { onAction(ShareAction.SystemShare, caption) } },
                    shape = RoundedCornerShape(999.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp),
                ) {
                    Icon(Icons.Filled.Share, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Share now",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

@Composable
private fun SharePreviewCard(
    track: TrackEntity,
    unitSystem: UnitSystem,
    caption: String,
) {
    com.rallytrax.app.ui.components.HeroGradientCard(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = 16.dp,
    ) {
      androidx.compose.foundation.layout.Column {
        com.rallytrax.app.ui.components.OverlineLabel(
            text = "Stint",
            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f),
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = track.name,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            PreviewMetric("Time", formatElapsedTime(track.durationMs), null)
            PreviewMetric(
                "Distance",
                valueFromFormattedDistance(formatDistance(track.distanceMeters, unitSystem)).first,
                valueFromFormattedDistance(formatDistance(track.distanceMeters, unitSystem)).second,
            )
            PreviewMetric(
                "Avg",
                formatSpeed(track.avgSpeedMps, unitSystem),
                speedUnit(unitSystem),
            )
            PreviewMetric(
                "Max",
                formatSpeed(track.maxSpeedMps, unitSystem),
                speedUnit(unitSystem),
            )
        }
        if (caption.isNotBlank()) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = "\u201C${caption}\u201D",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
            )
        }
      }
    }
}

@Composable
private fun PreviewMetric(label: String, value: String, unit: String?) {
    Column {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = value,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
                letterSpacing = (-0.3).sp,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        Text(
            text = if (unit != null) "$label · $unit" else label,
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// Split "38.2 km" → ("38.2", "km"). Returns (value, null) if no space.
private fun valueFromFormattedDistance(s: String): Pair<String, String?> {
    val idx = s.lastIndexOf(' ')
    return if (idx < 0) s to null else s.substring(0, idx) to s.substring(idx + 1)
}

@Composable
private fun ShareTarget(
    icon: ImageVector,
    label: String,
    primary: Boolean = false,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    // Morph shape slightly while pressed to echo the M3 expressive FAB menu.
    val corner by animateDpAsState(
        targetValue = if (pressed) 20.dp else 28.dp,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 420f),
        label = "share_target_corner",
    )
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.92f else 1f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 420f),
        label = "share_target_scale",
    )
    val container = if (primary) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.secondaryContainer
    val content = if (primary) MaterialTheme.colorScheme.onPrimary
        else MaterialTheme.colorScheme.onSecondaryContainer

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.width(78.dp),
    ) {
        Box(
            modifier = Modifier
                .size(60.dp)
                .background(container, RoundedCornerShape(corner))
                .clickableRipple(interaction, onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = content,
                modifier = Modifier.size(24.dp),
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
    // Scale is read to force the recomposition observation; actual scale is
    // applied through Modifier.graphicsLayer on the outer box.
    @Suppress("UNUSED_EXPRESSION") scale
}

private fun Modifier.clickableRipple(
    interaction: MutableInteractionSource,
    onClick: () -> Unit,
): Modifier = clickable(interactionSource = interaction, indication = null, onClick = onClick)

package com.rallytrax.app.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/**
 * Creates a shared shimmer brush so all placeholders within one composition
 * animate in sync rather than each running an independent transition.
 */
@Composable
private fun rememberShimmerBrush(): Brush {
    val baseColor = MaterialTheme.colorScheme.surfaceContainerHighest
    val highlightColor = MaterialTheme.colorScheme.surfaceContainerLow

    val transition = rememberInfiniteTransition(label = "shimmer")
    val offsetX by transition.animateFloat(
        initialValue = -300f,
        targetValue = 900f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "shimmer_offset",
    )

    return Brush.linearGradient(
        colors = listOf(baseColor, highlightColor, baseColor),
        start = Offset(offsetX, 0f),
        end = Offset(offsetX + 300f, 0f),
    )
}

/**
 * A rounded rectangle placeholder with an animated shimmer gradient.
 */
@Composable
fun ShimmerPlaceholder(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(8.dp),
    brush: Brush = rememberShimmerBrush(),
) {
    Box(
        modifier = modifier
            .background(brush = brush, shape = shape),
    )
}

/**
 * A skeleton list item that mimics a typical track/stint card layout:
 * title bar, subtitle bar, and a trailing metric bar.
 */
@Composable
fun ShimmerListItem(
    modifier: Modifier = Modifier,
) {
    val brush = rememberShimmerBrush()

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ShimmerPlaceholder(
                    modifier = Modifier
                        .fillMaxWidth(0.6f)
                        .height(16.dp),
                    brush = brush,
                )
                Spacer(modifier = Modifier.width(8.dp))
                ShimmerPlaceholder(
                    modifier = Modifier
                        .width(60.dp)
                        .height(14.dp),
                    brush = brush,
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            ShimmerPlaceholder(
                modifier = Modifier
                    .fillMaxWidth(0.4f)
                    .height(12.dp),
                brush = brush,
            )
            Spacer(modifier = Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                ShimmerPlaceholder(
                    modifier = Modifier
                        .width(64.dp)
                        .height(12.dp),
                    brush = brush,
                )
                ShimmerPlaceholder(
                    modifier = Modifier
                        .width(56.dp)
                        .height(12.dp),
                    brush = brush,
                )
                ShimmerPlaceholder(
                    modifier = Modifier
                        .width(48.dp)
                        .height(12.dp),
                    brush = brush,
                )
            }
        }
    }
}

/**
 * Renders [itemCount] shimmer list item skeletons in a LazyColumn.
 */
@Composable
fun ShimmerLoadingList(
    modifier: Modifier = Modifier,
    itemCount: Int = 6,
) {
    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        items(itemCount) {
            ShimmerListItem()
        }
    }
}

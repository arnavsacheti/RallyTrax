package com.rallytrax.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.rallytrax.app.ui.theme.ShapeHeroCard

/**
 * 24 dp radius gradient card: primaryContainer → tertiaryContainer at 135°.
 * Reused on Home weekly hero, Trips totals strip, Achievement card, Share preview.
 */
@Composable
fun HeroGradientCard(
    modifier: Modifier = Modifier,
    contentPadding: Dp = 20.dp,
    content: @Composable BoxScope.() -> Unit,
) {
    val brush = Brush.linearGradient(
        colors = listOf(
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.tertiaryContainer,
        ),
        start = Offset(0f, 0f),
        end = Offset.Infinite,  // approximates 135° on wide cards
    )
    Box(
        modifier = modifier
            .clip(ShapeHeroCard)
            .background(brush)
            .padding(contentPadding),
    ) {
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onPrimaryContainer) {
            content()
        }
    }
}

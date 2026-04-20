package com.rallytrax.app.ui.components

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Uppercase overline with 0.8 sp tracking — distinctly tighter & more editorial
 * than stock M3 labelSmall. Used as "kicker" above hero titles and as MetricTile labels.
 */
@Composable
fun OverlineLabel(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
) {
    Text(
        text = text.uppercase(),
        modifier = modifier,
        color = color.takeIf { it != Color.Unspecified }
            ?: LocalContentColor.current.copy(alpha = 0.75f),
        style = TextStyle(
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.8.sp,
        ),
    )
}

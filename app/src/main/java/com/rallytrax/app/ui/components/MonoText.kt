package com.rallytrax.app.ui.components

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/**
 * Monospace text used for every telemetry number in the app. Prevents jitter on
 * recording HUD and gives data-dense screens the "instrument readout" feel the
 * design prototype settled on. Letter-spacing tightens as size grows.
 */
@Composable
fun MonoText(
    text: String,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 16.sp,
    fontWeight: FontWeight = FontWeight.SemiBold,
    color: Color = Color.Unspecified,
    maxLines: Int = 1,
) {
    val sizeValue = fontSize.value
    // Negative tracking scales roughly linearly from −0.2 at 14sp to −3 at 64sp+.
    val letterSpacingEm = when {
        sizeValue >= 64f -> (-0.047f)
        sizeValue >= 40f -> (-0.035f)
        sizeValue >= 28f -> (-0.022f)
        sizeValue >= 20f -> (-0.018f)
        sizeValue >= 14f -> (-0.012f)
        else -> 0f
    }.em
    val lineHeight = if (sizeValue >= 48f) (sizeValue * 0.95f).sp else TextUnit.Unspecified
    Text(
        text = text,
        modifier = modifier,
        color = color.takeIf { it != Color.Unspecified } ?: LocalContentColor.current,
        style = TextStyle(
            fontFamily = FontFamily.Monospace,
            fontWeight = fontWeight,
            fontSize = fontSize,
            letterSpacing = letterSpacingEm,
            lineHeight = lineHeight,
        ),
        maxLines = maxLines,
    )
}

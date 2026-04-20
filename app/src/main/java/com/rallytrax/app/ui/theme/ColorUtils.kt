package com.rallytrax.app.ui.theme

import androidx.compose.ui.graphics.Color

fun speedColor(fraction: Float): Color {
    return if (fraction < 0.5f) {
        lerpColor(LayerSpeedLow, LayerSpeedMid, fraction * 2f)
    } else {
        lerpColor(LayerSpeedMid, LayerSpeedHigh, (fraction - 0.5f) * 2f)
    }
}

fun lerpColor(start: Color, end: Color, fraction: Float): Color {
    val f = fraction.coerceIn(0f, 1f)
    return Color(
        red = start.red + (end.red - start.red) * f,
        green = start.green + (end.green - start.green) * f,
        blue = start.blue + (end.blue - start.blue) * f,
        alpha = start.alpha + (end.alpha - start.alpha) * f,
    )
}

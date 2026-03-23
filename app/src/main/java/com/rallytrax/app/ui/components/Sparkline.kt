package com.rallytrax.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun Sparkline(
    data: List<Float>,
    modifier: Modifier = Modifier,
    color: Color = Color(0xFF1A73E8),
    fillAlpha: Float = 0.2f,
    height: Dp = 24.dp,
    strokeWidth: Float = 2f,
) {
    if (data.size < 2) return

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(height),
    ) {
        val maxVal = data.max().coerceAtLeast(0.001f)
        val minVal = data.min().coerceAtLeast(0f)
        val range = (maxVal - minVal).coerceAtLeast(0.001f)
        val stepX = size.width / (data.size - 1).toFloat()
        val paddingY = 2f

        fun yForValue(value: Float): Float {
            val normalized = (value - minVal) / range
            return size.height - paddingY - normalized * (size.height - paddingY * 2)
        }

        // Draw filled area
        val fillPath = Path().apply {
            moveTo(0f, size.height)
            data.forEachIndexed { i, value ->
                lineTo(i * stepX, yForValue(value))
            }
            lineTo((data.size - 1) * stepX, size.height)
            close()
        }
        drawPath(fillPath, color.copy(alpha = fillAlpha), style = Fill)

        // Draw line
        for (i in 0 until data.size - 1) {
            drawLine(
                color = color,
                start = Offset(i * stepX, yForValue(data[i])),
                end = Offset((i + 1) * stepX, yForValue(data[i + 1])),
                strokeWidth = strokeWidth,
            )
        }
    }
}

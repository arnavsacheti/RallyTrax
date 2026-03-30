package com.rallytrax.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import kotlin.random.Random

private val confettiColors = listOf(
    Color(0xFFFFD700), // Gold
    Color(0xFF1A73E8), // Primary blue
    Color(0xFF4CAF50), // Green
    Color(0xFFFF5722), // Deep orange
    Color(0xFFE91E63), // Pink
    Color(0xFF9C27B0), // Purple
)

private data class ConfettiParticle(
    val x: Float, // normalized 0..1
    val startY: Float, // normalized, starts negative (above screen)
    val color: Color,
    val sizeDp: Float,
    val speed: Float, // multiplier for fall speed
    val drift: Float, // horizontal drift per unit progress
)

@Composable
fun ConfettiOverlay(modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    val progress = remember { Animatable(0f) }

    val particles = remember {
        List(50) {
            ConfettiParticle(
                x = Random.nextFloat(),
                startY = Random.nextFloat() * -0.3f - 0.05f,
                color = confettiColors.random(),
                sizeDp = Random.nextFloat() * 4f + 4f,
                speed = Random.nextFloat() * 0.6f + 0.7f,
                drift = (Random.nextFloat() - 0.5f) * 0.15f,
            )
        }
    }

    LaunchedEffect(Unit) {
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 3000, easing = LinearEasing),
        )
    }

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val pxPerDp = density.density
        val t = progress.value

        particles.forEach { p ->
            val py = (p.startY + t * p.speed * 1.4f) * h
            val px = (p.x + t * p.drift) * w
            if (py in -20f..h) {
                val fadeOut = (1f - t).coerceIn(0f, 1f)
                val alpha = (fadeOut * 0.9f + 0.1f).coerceIn(0f, 1f)
                val sizePx = p.sizeDp * pxPerDp
                drawRect(
                    color = p.color.copy(alpha = alpha),
                    topLeft = Offset(px - sizePx / 2, py),
                    size = Size(sizePx, sizePx * 0.6f),
                )
            }
        }
    }
}

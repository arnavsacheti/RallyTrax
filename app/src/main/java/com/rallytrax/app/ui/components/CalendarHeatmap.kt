package com.rallytrax.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit

@Composable
fun CalendarHeatmap(
    dailyDistances: Map<LocalDate, Double>,
    currentStreak: Int,
    modifier: Modifier = Modifier,
    baseColor: Color = MaterialTheme.colorScheme.primary,
    emptyColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    onDayClick: ((LocalDate) -> Unit)? = null,
) {
    val today = remember { LocalDate.now() }
    // Show past 365 days
    val startDate = remember { today.minusDays(364) }
    // Adjust to start on a Monday
    val adjustedStart = remember {
        var d = startDate
        while (d.dayOfWeek != DayOfWeek.MONDAY) d = d.minusDays(1)
        d
    }

    val maxDistance = remember(dailyDistances) {
        dailyDistances.values.maxOrNull()?.coerceAtLeast(1.0) ?: 1.0
    }

    Column(modifier = modifier) {
        // Streak counter
        Row {
            Text(
                text = "Driving Streak: ",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "$currentStreak day${if (currentStreak != 1) "s" else ""}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Heatmap grid
        val cellSize = 12f
        val cellGap = 2f
        val totalCellSize = cellSize + cellGap
        val totalWeeks = ChronoUnit.WEEKS.between(adjustedStart, today).toInt() + 1

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(((7 * totalCellSize) + cellGap).dp)
                .pointerInput(onDayClick) {
                    if (onDayClick == null) return@pointerInput
                    detectTapGestures { offset ->
                        val col = (offset.x / (totalCellSize * density)).toInt()
                        val row = (offset.y / (totalCellSize * density)).toInt()
                        if (row in 0..6 && col in 0 until totalWeeks) {
                            val date = adjustedStart.plusWeeks(col.toLong()).plusDays(row.toLong())
                            if (!date.isAfter(today)) {
                                onDayClick(date)
                            }
                        }
                    }
                },
        ) {
            val dpCellSize = cellSize * density
            val dpGap = cellGap * density
            val dpTotalCell = dpCellSize + dpGap

            for (week in 0 until totalWeeks) {
                for (day in 0..6) {
                    val date = adjustedStart.plusWeeks(week.toLong()).plusDays(day.toLong())
                    if (date.isAfter(today)) continue

                    val distance = dailyDistances[date] ?: 0.0
                    val intensity = if (distance > 0) {
                        (distance / maxDistance).toFloat().coerceIn(0.15f, 1f)
                    } else {
                        0f
                    }

                    val color = if (intensity > 0f) {
                        baseColor.copy(alpha = intensity)
                    } else {
                        emptyColor
                    }

                    drawRoundRect(
                        color = color,
                        topLeft = Offset(week * dpTotalCell, day * dpTotalCell),
                        size = Size(dpCellSize, dpCellSize),
                        cornerRadius = CornerRadius(2f * density, 2f * density),
                    )
                }
            }
        }
    }
}

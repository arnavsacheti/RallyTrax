package com.rallytrax.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Air
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Thermostat
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.rallytrax.app.data.api.WeatherCondition

@Composable
fun WeatherBadge(
    weather: WeatherCondition,
    modifier: Modifier = Modifier,
) {
    val icon = when (weather.conditionGroup) {
        "Clear" -> Icons.Outlined.WbSunny
        "Rain", "Drizzle", "Thunderstorm" -> Icons.Outlined.WaterDrop
        else -> Icons.Outlined.Cloud
    }

    AssistChip(
        onClick = { },
        label = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "%.0f\u00b0C".format(weather.temperatureC),
                    style = MaterialTheme.typography.labelMedium,
                )
                Icon(
                    imageVector = Icons.Outlined.Air,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                )
                Text(
                    text = "%.0f km/h".format(weather.windSpeedMps * 3.6),
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        },
        leadingIcon = {
            Icon(
                imageVector = icon,
                contentDescription = weather.conditionGroup,
                modifier = Modifier.size(18.dp),
            )
        },
        modifier = modifier,
    )
}

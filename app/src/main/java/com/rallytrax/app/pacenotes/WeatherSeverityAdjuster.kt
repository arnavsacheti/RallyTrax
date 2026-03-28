package com.rallytrax.app.pacenotes

import com.rallytrax.app.data.api.WeatherCondition
import kotlin.math.abs

data class AdjustedSeverity(
    val originalSeverity: Int,
    val adjustedSeverity: Int,
    val adjustmentDelta: Int,
    val adjustmentReasons: List<String>,
    val cautionModifiers: List<String>,
)

/**
 * Adjusts pace note severity based on weather conditions.
 *
 * Lower severity number = tighter/slower (grade 1 = hairpin, grade 6 = flat-out kink).
 * Weather adjustments REDUCE the number, meaning "treat this corner as tighter, slow down more."
 */
object WeatherSeverityAdjuster {

    fun adjust(
        severity: Int,
        weather: WeatherCondition,
        trackBearingDeg: Double? = null,
    ): AdjustedSeverity {
        var delta = 0
        val reasons = mutableListOf<String>()
        val cautions = mutableListOf<String>()

        // ── Precipitation ───────────────────────────────────────────
        when (weather.conditionGroup) {
            "Drizzle" -> {
                delta -= 1
                reasons += "Light rain: reduced grip"
            }
            "Rain" -> {
                val intensity = weather.precipitationMmH ?: 0.0
                if (intensity > 4.0) {
                    delta -= 2
                    reasons += "Rain (%.1fmm/h): significant grip loss".format(intensity)
                } else {
                    delta -= 1
                    reasons += "Rain (%.1fmm/h): reduced grip".format(intensity)
                }
            }
            "Snow" -> {
                delta -= 2
                reasons += "Snow: severely reduced traction"
            }
            "Thunderstorm" -> {
                delta -= 2
                reasons += "Thunderstorm: heavy rain and poor visibility"
            }
        }

        // ── Temperature ─────────────────────────────────────────────
        if (weather.temperatureC <= 0.0) {
            delta -= 2
            reasons += "Sub-zero (%.1f\u00b0C): high ice risk".format(weather.temperatureC)
        } else if (weather.temperatureC <= 3.0) {
            delta -= 1
            reasons += "Near freezing (%.1f\u00b0C): potential ice".format(weather.temperatureC)
        }

        // ── Wind ────────────────────────────────────────────────────
        val windKmh = (weather.windSpeedMps * 3.6).toInt()
        if (weather.windSpeedMps > 15.0) {
            cautions += "Strong wind ($windKmh km/h)"

            if (trackBearingDeg != null) {
                val angleDiff = abs(weather.windDirectionDeg - trackBearingDeg) % 180
                if (angleDiff in 45.0..135.0) {
                    delta -= 1
                    reasons += "Strong crosswind ($windKmh km/h)"
                }
            }
        }

        // ── Visibility ──────────────────────────────────────────────
        if (weather.visibility < 500) {
            delta -= 1
            reasons += "Very low visibility (${weather.visibility}m)"
        } else if (weather.visibility < 1000) {
            cautions += "Reduced visibility (${weather.visibility}m)"
        }

        val adjusted = (severity + delta).coerceIn(1, 6)
        return AdjustedSeverity(
            originalSeverity = severity,
            adjustedSeverity = adjusted,
            adjustmentDelta = delta,
            adjustmentReasons = reasons,
            cautionModifiers = cautions,
        )
    }
}

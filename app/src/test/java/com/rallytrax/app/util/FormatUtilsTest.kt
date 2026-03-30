package com.rallytrax.app.util

import com.rallytrax.app.data.preferences.UnitSystem
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FormatUtilsTest {

    // --- formatElapsedTime ---

    @Test
    fun formatElapsedTime_zeroMs_returnsZero() {
        assertEquals("00:00", formatElapsedTime(0L))
    }

    @Test
    fun formatElapsedTime_secondsOnly() {
        assertEquals("00:45", formatElapsedTime(45_000L))
    }

    @Test
    fun formatElapsedTime_minutesAndSeconds() {
        assertEquals("05:30", formatElapsedTime(330_000L))
    }

    @Test
    fun formatElapsedTime_hoursMinutesSeconds() {
        assertEquals("1:02:03", formatElapsedTime(3_723_000L))
    }

    @Test
    fun formatElapsedTime_exactHour() {
        assertEquals("1:00:00", formatElapsedTime(3_600_000L))
    }

    @Test
    fun formatElapsedTime_subSecondTruncated() {
        assertEquals("00:01", formatElapsedTime(1_999L))
    }

    // --- formatDistance ---

    @Test
    fun formatDistance_metric_meters() {
        assertEquals("500 m", formatDistance(500.0, UnitSystem.METRIC))
    }

    @Test
    fun formatDistance_metric_zeroMeters() {
        assertEquals("0 m", formatDistance(0.0, UnitSystem.METRIC))
    }

    @Test
    fun formatDistance_metric_kilometers() {
        assertEquals("1.50 km", formatDistance(1500.0, UnitSystem.METRIC))
    }

    @Test
    fun formatDistance_metric_exactThreshold() {
        assertEquals("1.00 km", formatDistance(1000.0, UnitSystem.METRIC))
    }

    @Test
    fun formatDistance_metric_justBelowThreshold() {
        assertEquals("999 m", formatDistance(999.0, UnitSystem.METRIC))
    }

    @Test
    fun formatDistance_imperial_feet() {
        // 30 meters = 98.4252 ft; 30m / 1609.344 = 0.01864 mi < 0.1
        assertEquals("98 ft", formatDistance(30.0, UnitSystem.IMPERIAL))
    }

    @Test
    fun formatDistance_imperial_miles() {
        // 1609.344 m = 1 mile
        assertEquals("1.00 mi", formatDistance(1609.344, UnitSystem.IMPERIAL))
    }

    @Test
    fun formatDistance_imperial_belowMileThreshold() {
        // 100m = 328.084 ft; 100m / 1609.344 = 0.0621 mi < 0.1 -> feet
        assertEquals("328 ft", formatDistance(100.0, UnitSystem.IMPERIAL))
    }

    @Test
    fun formatDistance_imperial_aboveMileThreshold() {
        // 200m / 1609.344 = 0.1243 mi >= 0.1 -> miles
        assertEquals("0.12 mi", formatDistance(200.0, UnitSystem.IMPERIAL))
    }

    @Test
    fun formatDistance_defaultIsMetric() {
        assertEquals("500 m", formatDistance(500.0))
    }

    // --- formatSpeed ---

    @Test
    fun formatSpeed_metric_zero() {
        assertEquals("0", formatSpeed(0.0, UnitSystem.METRIC))
    }

    @Test
    fun formatSpeed_metric_conversion() {
        // 10 m/s * 3.6 = 36 km/h
        assertEquals("36", formatSpeed(10.0, UnitSystem.METRIC))
    }

    @Test
    fun formatSpeed_imperial_conversion() {
        // 10 m/s * 2.23694 = 22.3694 -> "22"
        assertEquals("22", formatSpeed(10.0, UnitSystem.IMPERIAL))
    }

    @Test
    fun formatSpeed_metric_rounding() {
        // 1.0 m/s * 3.6 = 3.6 -> "4"
        assertEquals("4", formatSpeed(1.0, UnitSystem.METRIC))
    }

    @Test
    fun formatSpeed_defaultIsMetric() {
        assertEquals("36", formatSpeed(10.0))
    }

    // --- speedUnit ---

    @Test
    fun speedUnit_metric() {
        assertEquals("km/h", speedUnit(UnitSystem.METRIC))
    }

    @Test
    fun speedUnit_imperial() {
        assertEquals("mph", speedUnit(UnitSystem.IMPERIAL))
    }

    // --- formatElevation ---

    @Test
    fun formatElevation_metric() {
        assertEquals("1500 m", formatElevation(1500.0, UnitSystem.METRIC))
    }

    @Test
    fun formatElevation_imperial() {
        // 100 m * 3.28084 = 328.084 -> "328 ft"
        assertEquals("328 ft", formatElevation(100.0, UnitSystem.IMPERIAL))
    }

    @Test
    fun formatElevation_zero() {
        assertEquals("0 m", formatElevation(0.0, UnitSystem.METRIC))
    }

    @Test
    fun formatElevation_negative() {
        assertEquals("-100 m", formatElevation(-100.0, UnitSystem.METRIC))
    }

    @Test
    fun formatElevation_defaultIsMetric() {
        assertEquals("500 m", formatElevation(500.0))
    }

    // --- formatDate ---

    @Test
    fun formatDate_knownEpoch() {
        val epochMs = ZonedDateTime.of(2024, 1, 15, 12, 0, 0, 0, ZoneId.systemDefault())
            .toInstant().toEpochMilli()
        assertEquals("Jan 15, 2024", formatDate(epochMs))
    }

    @Test
    fun formatDate_epochZero() {
        val expected = Instant.EPOCH.atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US))
        assertEquals(expected, formatDate(0L))
    }

    // --- formatDateTime ---

    @Test
    fun formatDateTime_knownEpoch() {
        val epochMs = ZonedDateTime.of(2024, 6, 20, 14, 30, 0, 0, ZoneId.systemDefault())
            .toInstant().toEpochMilli()
        assertEquals("Jun 20, 2024 at 2:30 PM", formatDateTime(epochMs))
    }

    // --- formatRelativeTime ---

    @Test
    fun formatRelativeTime_justNow() {
        val now = Instant.now().toEpochMilli()
        assertEquals("Just now", formatRelativeTime(now))
    }

    @Test
    fun formatRelativeTime_minutesAgo() {
        val fiveMinAgo = Instant.now().toEpochMilli() - 5 * 60_000
        assertEquals("5m ago", formatRelativeTime(fiveMinAgo))
    }

    @Test
    fun formatRelativeTime_hoursAgoToday() {
        // 2 hours ago should show "2h ago" if still today
        val twoHoursAgo = Instant.now().toEpochMilli() - 2 * 3_600_000
        val twoHoursAgoDate = Instant.ofEpochMilli(twoHoursAgo)
            .atZone(ZoneId.systemDefault()).toLocalDate()
        val today = Instant.now().atZone(ZoneId.systemDefault()).toLocalDate()
        if (twoHoursAgoDate == today) {
            assertEquals("2h ago", formatRelativeTime(twoHoursAgo))
        }
        // If it crossed midnight, the result would be "Yesterday" -- still valid
    }

    @Test
    fun formatRelativeTime_resultIsNonEmpty() {
        // Far past date should produce a non-empty formatted string
        val oldEpoch = ZonedDateTime.of(2020, 3, 15, 10, 0, 0, 0, ZoneId.systemDefault())
            .toInstant().toEpochMilli()
        val result = formatRelativeTime(oldEpoch)
        assertTrue("Expected non-empty result", result.isNotEmpty())
        // Should contain "Mar 15, 2020" since it's a different year
        assertTrue("Expected year-qualified date", result.contains("2020"))
    }
}

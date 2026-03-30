package com.rallytrax.app.data.sync

import org.junit.Assert.assertEquals
import org.junit.Test

class SyncableSettingsTest {

    private val baseLocal = SyncableSettings(
        themeMode = "DARK",
        themeModeModifiedAt = 100L,
        unitSystem = "IMPERIAL",
        unitSystemModifiedAt = 100L,
        gpsAccuracy = "LOW",
        gpsAccuracyModifiedAt = 100L,
        mapProvider = "GOOGLE",
        mapProviderModifiedAt = 100L,
        ttsRate = 1.0f,
        ttsRateModifiedAt = 100L,
        ttsPitch = 0.8f,
        ttsPitchModifiedAt = 100L,
        ttsEnabled = false,
        ttsEnabledModifiedAt = 100L,
    )

    private val baseRemote = SyncableSettings(
        themeMode = "LIGHT",
        themeModeModifiedAt = 200L,
        unitSystem = "METRIC",
        unitSystemModifiedAt = 200L,
        gpsAccuracy = "HIGH",
        gpsAccuracyModifiedAt = 200L,
        mapProvider = "OSM",
        mapProviderModifiedAt = 200L,
        ttsRate = 1.5f,
        ttsRateModifiedAt = 200L,
        ttsPitch = 1.2f,
        ttsPitchModifiedAt = 200L,
        ttsEnabled = true,
        ttsEnabledModifiedAt = 200L,
    )

    @Test
    fun `remote newer for all fields uses remote values`() {
        val result = baseLocal.mergeWith(baseRemote)

        assertEquals("LIGHT", result.themeMode)
        assertEquals(200L, result.themeModeModifiedAt)
        assertEquals("METRIC", result.unitSystem)
        assertEquals(200L, result.unitSystemModifiedAt)
        assertEquals("HIGH", result.gpsAccuracy)
        assertEquals(200L, result.gpsAccuracyModifiedAt)
        assertEquals("OSM", result.mapProvider)
        assertEquals(200L, result.mapProviderModifiedAt)
        assertEquals(1.5f, result.ttsRate)
        assertEquals(200L, result.ttsRateModifiedAt)
        assertEquals(1.2f, result.ttsPitch)
        assertEquals(200L, result.ttsPitchModifiedAt)
        assertEquals(true, result.ttsEnabled)
        assertEquals(200L, result.ttsEnabledModifiedAt)
    }

    @Test
    fun `local newer for all fields uses local values`() {
        val result = baseRemote.mergeWith(baseLocal)

        assertEquals("LIGHT", result.themeMode)
        assertEquals(200L, result.themeModeModifiedAt)
        assertEquals("METRIC", result.unitSystem)
        assertEquals(200L, result.unitSystemModifiedAt)
        assertEquals("HIGH", result.gpsAccuracy)
        assertEquals(200L, result.gpsAccuracyModifiedAt)
        assertEquals("OSM", result.mapProvider)
        assertEquals(200L, result.mapProviderModifiedAt)
        assertEquals(1.5f, result.ttsRate)
        assertEquals(200L, result.ttsRateModifiedAt)
        assertEquals(1.2f, result.ttsPitch)
        assertEquals(200L, result.ttsPitchModifiedAt)
        assertEquals(true, result.ttsEnabled)
        assertEquals(200L, result.ttsEnabledModifiedAt)
    }

    @Test
    fun `mixed timestamps picks newer value per field`() {
        val local = SyncableSettings(
            themeMode = "DARK",
            themeModeModifiedAt = 300L,
            unitSystem = "IMPERIAL",
            unitSystemModifiedAt = 50L,
            gpsAccuracy = "LOW",
            gpsAccuracyModifiedAt = 300L,
            mapProvider = "GOOGLE",
            mapProviderModifiedAt = 50L,
            ttsRate = 1.0f,
            ttsRateModifiedAt = 300L,
            ttsPitch = 0.8f,
            ttsPitchModifiedAt = 50L,
            ttsEnabled = false,
            ttsEnabledModifiedAt = 300L,
        )
        val remote = SyncableSettings(
            themeMode = "LIGHT",
            themeModeModifiedAt = 100L,
            unitSystem = "METRIC",
            unitSystemModifiedAt = 200L,
            gpsAccuracy = "HIGH",
            gpsAccuracyModifiedAt = 100L,
            mapProvider = "OSM",
            mapProviderModifiedAt = 200L,
            ttsRate = 1.5f,
            ttsRateModifiedAt = 100L,
            ttsPitch = 1.2f,
            ttsPitchModifiedAt = 200L,
            ttsEnabled = true,
            ttsEnabledModifiedAt = 100L,
        )

        val result = local.mergeWith(remote)

        // Local newer: themeMode, gpsAccuracy, ttsRate, ttsEnabled
        assertEquals("DARK", result.themeMode)
        assertEquals(300L, result.themeModeModifiedAt)
        assertEquals("LOW", result.gpsAccuracy)
        assertEquals(300L, result.gpsAccuracyModifiedAt)
        assertEquals(1.0f, result.ttsRate)
        assertEquals(300L, result.ttsRateModifiedAt)
        assertEquals(false, result.ttsEnabled)
        assertEquals(300L, result.ttsEnabledModifiedAt)

        // Remote newer: unitSystem, mapProvider, ttsPitch
        assertEquals("METRIC", result.unitSystem)
        assertEquals(200L, result.unitSystemModifiedAt)
        assertEquals("OSM", result.mapProvider)
        assertEquals(200L, result.mapProviderModifiedAt)
        assertEquals(1.2f, result.ttsPitch)
        assertEquals(200L, result.ttsPitchModifiedAt)
    }

    @Test
    fun `identical timestamps keeps local values`() {
        val local = baseLocal.copy()
        val remote = SyncableSettings(
            themeMode = "LIGHT",
            themeModeModifiedAt = 100L,
            unitSystem = "METRIC",
            unitSystemModifiedAt = 100L,
            gpsAccuracy = "HIGH",
            gpsAccuracyModifiedAt = 100L,
            mapProvider = "OSM",
            mapProviderModifiedAt = 100L,
            ttsRate = 1.5f,
            ttsRateModifiedAt = 100L,
            ttsPitch = 1.2f,
            ttsPitchModifiedAt = 100L,
            ttsEnabled = true,
            ttsEnabledModifiedAt = 100L,
        )

        val result = local.mergeWith(remote)

        // When timestamps are equal, local wins (not strictly greater)
        assertEquals("DARK", result.themeMode)
        assertEquals("IMPERIAL", result.unitSystem)
        assertEquals("LOW", result.gpsAccuracy)
        assertEquals("GOOGLE", result.mapProvider)
        assertEquals(1.0f, result.ttsRate)
        assertEquals(0.8f, result.ttsPitch)
        assertEquals(false, result.ttsEnabled)
        // Timestamps remain at the shared value
        assertEquals(100L, result.themeModeModifiedAt)
    }

    @Test
    fun `merge with default remote keeps local values`() {
        val result = baseLocal.mergeWith(SyncableSettings())

        // Default timestamps are 0, so local (100) always wins
        assertEquals("DARK", result.themeMode)
        assertEquals(100L, result.themeModeModifiedAt)
        assertEquals("IMPERIAL", result.unitSystem)
        assertEquals("LOW", result.gpsAccuracy)
        assertEquals("GOOGLE", result.mapProvider)
        assertEquals(1.0f, result.ttsRate)
        assertEquals(0.8f, result.ttsPitch)
        assertEquals(false, result.ttsEnabled)
    }

    @Test
    fun `merge default local with remote takes remote values`() {
        val result = SyncableSettings().mergeWith(baseRemote)

        assertEquals("LIGHT", result.themeMode)
        assertEquals(200L, result.themeModeModifiedAt)
        assertEquals("METRIC", result.unitSystem)
        assertEquals("HIGH", result.gpsAccuracy)
        assertEquals("OSM", result.mapProvider)
        assertEquals(1.5f, result.ttsRate)
        assertEquals(1.2f, result.ttsPitch)
        assertEquals(true, result.ttsEnabled)
    }

    @Test
    fun `merge two defaults returns defaults`() {
        val result = SyncableSettings().mergeWith(SyncableSettings())

        val expected = SyncableSettings()
        assertEquals(expected, result)
    }

    @Test
    fun `merge is idempotent with self`() {
        val result = baseLocal.mergeWith(baseLocal)

        assertEquals(baseLocal, result)
    }

    @Test
    fun `toJson and fromJson roundtrip preserves all fields`() {
        val json = baseLocal.toJson()
        val restored = SyncableSettings.fromJson(json)

        assertEquals(baseLocal, restored)
    }

    @Test
    fun `fromJson ignores unknown keys`() {
        val json = """{"themeMode":"DARK","themeModeModifiedAt":100,"unknownField":"value"}"""
        val result = SyncableSettings.fromJson(json)

        assertEquals("DARK", result.themeMode)
        assertEquals(100L, result.themeModeModifiedAt)
        // Other fields should be defaults
        assertEquals("METRIC", result.unitSystem)
        assertEquals(0L, result.unitSystemModifiedAt)
    }
}

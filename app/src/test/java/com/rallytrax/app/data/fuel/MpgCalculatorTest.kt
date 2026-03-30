package com.rallytrax.app.data.fuel

import com.rallytrax.app.data.local.entity.FuelLogEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MpgCalculatorTest {

    private val kmPerMile = 1.60934
    private val litresPerGallon = 3.78541
    private val delta = 0.01

    private fun fuelLog(
        odometerKm: Double,
        volumeL: Double,
        isFullTank: Boolean = true,
        isMissed: Boolean = false,
        totalCost: Double? = null,
        vehicleId: String = "v1",
    ) = FuelLogEntity(
        vehicleId = vehicleId,
        odometerKm = odometerKm,
        volumeL = volumeL,
        isFullTank = isFullTank,
        isMissed = isMissed,
        totalCost = totalCost,
    )

    // --- calculateFillToFill ---

    @Test
    fun `calculateFillToFill - empty list returns empty`() {
        assertEquals(emptyList<FuelLogEntity>(), MpgCalculator.calculateFillToFill(emptyList()))
    }

    @Test
    fun `calculateFillToFill - single entry returns null mpg`() {
        val logs = listOf(fuelLog(odometerKm = 100.0, volumeL = 20.0))
        val result = MpgCalculator.calculateFillToFill(logs)
        assertEquals(1, result.size)
        assertNull(result[0].computedMpg)
    }

    @Test
    fun `calculateFillToFill - two full fills computes correct MPG`() {
        // 300 miles driven, 10 gallons filled = 30 MPG
        val distanceKm = 300.0 * kmPerMile // 482.802
        val volumeL = 10.0 * litresPerGallon // 37.8541
        val logs = listOf(
            fuelLog(odometerKm = 1000.0, volumeL = 40.0),
            fuelLog(odometerKm = 1000.0 + distanceKm, volumeL = volumeL),
        )
        val result = MpgCalculator.calculateFillToFill(logs)
        assertNull(result[0].computedMpg) // baseline
        assertEquals(30.0, result[1].computedMpg!!, delta)
    }

    @Test
    fun `calculateFillToFill - three full fills computes each segment`() {
        val logs = listOf(
            fuelLog(odometerKm = 0.0, volumeL = 50.0),
            fuelLog(odometerKm = 482.802, volumeL = 37.8541), // 300mi / 10gal = 30mpg
            fuelLog(odometerKm = 482.802 + 321.868, volumeL = 37.8541), // 200mi / 10gal = 20mpg
        )
        val result = MpgCalculator.calculateFillToFill(logs)
        assertNull(result[0].computedMpg)
        assertEquals(30.0, result[1].computedMpg!!, delta)
        assertEquals(20.0, result[2].computedMpg!!, delta)
    }

    @Test
    fun `calculateFillToFill - partial fill accumulates gallons`() {
        // Baseline at 0km, partial at 200km (5L), full at 482.802km (32.8541L)
        // Total litres = 5 + 32.8541 = 37.8541 (10 gal), distance = 482.802km (300mi)
        // MPG = 300 / 10 = 30
        val logs = listOf(
            fuelLog(odometerKm = 0.0, volumeL = 50.0),
            fuelLog(odometerKm = 200.0, volumeL = 5.0, isFullTank = false),
            fuelLog(odometerKm = 482.802, volumeL = 32.8541),
        )
        val result = MpgCalculator.calculateFillToFill(logs)
        assertNull(result[0].computedMpg)
        assertNull(result[1].computedMpg) // partial fill, no MPG
        assertEquals(30.0, result[2].computedMpg!!, delta)
    }

    @Test
    fun `calculateFillToFill - missed fill breaks chain`() {
        val logs = listOf(
            fuelLog(odometerKm = 0.0, volumeL = 50.0),
            fuelLog(odometerKm = 200.0, volumeL = 20.0, isMissed = true),
            fuelLog(odometerKm = 400.0, volumeL = 30.0),
        )
        val result = MpgCalculator.calculateFillToFill(logs)
        assertNull(result[0].computedMpg) // baseline
        assertNull(result[1].computedMpg) // missed
        assertNull(result[2].computedMpg) // chain broken, this becomes new baseline
    }

    @Test
    fun `calculateFillToFill - chain resumes after missed fill`() {
        val distanceKm = 300.0 * kmPerMile
        val volumeL = 10.0 * litresPerGallon
        val logs = listOf(
            fuelLog(odometerKm = 0.0, volumeL = 50.0),
            fuelLog(odometerKm = 100.0, volumeL = 10.0, isMissed = true),
            fuelLog(odometerKm = 200.0, volumeL = 50.0), // new baseline
            fuelLog(odometerKm = 200.0 + distanceKm, volumeL = volumeL), // 30 MPG
        )
        val result = MpgCalculator.calculateFillToFill(logs)
        assertNull(result[2].computedMpg) // new baseline after missed
        assertEquals(30.0, result[3].computedMpg!!, delta)
    }

    @Test
    fun `calculateFillToFill - zero volume yields null mpg`() {
        val logs = listOf(
            fuelLog(odometerKm = 0.0, volumeL = 50.0),
            fuelLog(odometerKm = 500.0, volumeL = 0.0),
        )
        val result = MpgCalculator.calculateFillToFill(logs)
        assertNull(result[1].computedMpg)
    }

    // --- lifetimeAverage ---

    @Test
    fun `lifetimeAverage - empty list returns null`() {
        assertNull(MpgCalculator.lifetimeAverage(emptyList()))
    }

    @Test
    fun `lifetimeAverage - single entry returns null`() {
        assertNull(MpgCalculator.lifetimeAverage(listOf(fuelLog(100.0, 20.0))))
    }

    @Test
    fun `lifetimeAverage - two full fills computes average`() {
        val distanceKm = 300.0 * kmPerMile
        val volumeL = 10.0 * litresPerGallon
        val logs = listOf(
            fuelLog(odometerKm = 1000.0, volumeL = 40.0),
            fuelLog(odometerKm = 1000.0 + distanceKm, volumeL = volumeL),
        )
        assertEquals(30.0, MpgCalculator.lifetimeAverage(logs)!!, delta)
    }

    @Test
    fun `lifetimeAverage - multiple segments weighted correctly`() {
        // Segment 1: 300mi, 10gal. Segment 2: 200mi, 10gal.
        // Lifetime = 500mi / 20gal = 25 MPG
        val logs = listOf(
            fuelLog(odometerKm = 0.0, volumeL = 50.0),
            fuelLog(odometerKm = 482.802, volumeL = 37.8541),
            fuelLog(odometerKm = 482.802 + 321.868, volumeL = 37.8541),
        )
        assertEquals(25.0, MpgCalculator.lifetimeAverage(logs)!!, delta)
    }

    @Test
    fun `lifetimeAverage - missed fill excludes broken segment`() {
        val distanceKm = 300.0 * kmPerMile
        val volumeL = 10.0 * litresPerGallon
        val logs = listOf(
            fuelLog(odometerKm = 0.0, volumeL = 50.0),
            fuelLog(odometerKm = 200.0, volumeL = 20.0, isMissed = true),
            fuelLog(odometerKm = 1000.0, volumeL = 40.0), // new baseline
            fuelLog(odometerKm = 1000.0 + distanceKm, volumeL = volumeL),
        )
        // Only the segment after the missed fill counts: 30 MPG
        assertEquals(30.0, MpgCalculator.lifetimeAverage(logs)!!, delta)
    }

    @Test
    fun `lifetimeAverage - no calculable segments returns null`() {
        val logs = listOf(
            fuelLog(odometerKm = 0.0, volumeL = 50.0, isFullTank = false),
            fuelLog(odometerKm = 200.0, volumeL = 20.0, isFullTank = false),
        )
        assertNull(MpgCalculator.lifetimeAverage(logs))
    }

    @Test
    fun `lifetimeAverage - sorts unsorted input`() {
        val distanceKm = 300.0 * kmPerMile
        val volumeL = 10.0 * litresPerGallon
        val logs = listOf(
            fuelLog(odometerKm = 1000.0 + distanceKm, volumeL = volumeL),
            fuelLog(odometerKm = 1000.0, volumeL = 40.0),
        )
        assertEquals(30.0, MpgCalculator.lifetimeAverage(logs)!!, delta)
    }

    // --- costPerMile ---

    @Test
    fun `costPerMile - empty list returns null`() {
        assertNull(MpgCalculator.costPerMile(emptyList()))
    }

    @Test
    fun `costPerMile - single entry returns null`() {
        assertNull(MpgCalculator.costPerMile(listOf(fuelLog(100.0, 20.0, totalCost = 50.0))))
    }

    @Test
    fun `costPerMile - computes total cost over total miles`() {
        // 100km apart = 62.137 miles, total cost $50
        // cost per mile = 50 / 62.137 = ~0.805
        val logs = listOf(
            fuelLog(odometerKm = 0.0, volumeL = 20.0, totalCost = 20.0),
            fuelLog(odometerKm = 100.0, volumeL = 20.0, totalCost = 30.0),
        )
        val totalMiles = 100.0 / kmPerMile
        val expected = 50.0 / totalMiles
        assertEquals(expected, MpgCalculator.costPerMile(logs)!!, delta)
    }

    @Test
    fun `costPerMile - null totalCost entries excluded from cost sum`() {
        val logs = listOf(
            fuelLog(odometerKm = 0.0, volumeL = 20.0, totalCost = null),
            fuelLog(odometerKm = 100.0, volumeL = 20.0, totalCost = 30.0),
        )
        val totalMiles = 100.0 / kmPerMile
        assertEquals(30.0 / totalMiles, MpgCalculator.costPerMile(logs)!!, delta)
    }

    @Test
    fun `costPerMile - zero total cost returns null`() {
        val logs = listOf(
            fuelLog(odometerKm = 0.0, volumeL = 20.0, totalCost = null),
            fuelLog(odometerKm = 100.0, volumeL = 20.0, totalCost = null),
        )
        assertNull(MpgCalculator.costPerMile(logs))
    }

    @Test
    fun `costPerMile - zero distance returns null`() {
        val logs = listOf(
            fuelLog(odometerKm = 100.0, volumeL = 20.0, totalCost = 25.0),
            fuelLog(odometerKm = 100.0, volumeL = 20.0, totalCost = 25.0),
        )
        assertNull(MpgCalculator.costPerMile(logs))
    }

    // --- computeMpgForNewEntry ---

    @Test
    fun `computeMpgForNewEntry - partial fill returns null`() {
        val newLog = fuelLog(odometerKm = 500.0, volumeL = 20.0, isFullTank = false)
        assertNull(MpgCalculator.computeMpgForNewEntry(newLog, emptyList()))
    }

    @Test
    fun `computeMpgForNewEntry - no previous logs returns null`() {
        val newLog = fuelLog(odometerKm = 500.0, volumeL = 20.0)
        assertNull(MpgCalculator.computeMpgForNewEntry(newLog, emptyList()))
    }

    @Test
    fun `computeMpgForNewEntry - computes against last full fill`() {
        val distanceKm = 300.0 * kmPerMile
        val volumeL = 10.0 * litresPerGallon
        val previous = listOf(fuelLog(odometerKm = 1000.0, volumeL = 40.0))
        val newLog = fuelLog(odometerKm = 1000.0 + distanceKm, volumeL = volumeL)
        assertEquals(30.0, MpgCalculator.computeMpgForNewEntry(newLog, previous)!!, delta)
    }

    @Test
    fun `computeMpgForNewEntry - accumulates partial fills before baseline`() {
        // Previous: full at 0km, partial at 200km (5L)
        // New: full at 482.802km with 32.8541L
        // Total litres = 5 + 32.8541 = 37.8541 (10gal), distance = 482.802km (300mi)
        val previous = listOf(
            fuelLog(odometerKm = 0.0, volumeL = 50.0),
            fuelLog(odometerKm = 200.0, volumeL = 5.0, isFullTank = false),
        )
        val newLog = fuelLog(odometerKm = 482.802, volumeL = 32.8541)
        assertEquals(30.0, MpgCalculator.computeMpgForNewEntry(newLog, previous)!!, delta)
    }

    @Test
    fun `computeMpgForNewEntry - missed fill in chain returns null`() {
        val previous = listOf(
            fuelLog(odometerKm = 0.0, volumeL = 50.0),
            fuelLog(odometerKm = 200.0, volumeL = 10.0, isMissed = true),
        )
        val newLog = fuelLog(odometerKm = 500.0, volumeL = 30.0)
        assertNull(MpgCalculator.computeMpgForNewEntry(newLog, previous))
    }

    @Test
    fun `computeMpgForNewEntry - zero distance returns null`() {
        val previous = listOf(fuelLog(odometerKm = 500.0, volumeL = 40.0))
        val newLog = fuelLog(odometerKm = 500.0, volumeL = 20.0)
        assertNull(MpgCalculator.computeMpgForNewEntry(newLog, previous))
    }

    @Test
    fun `computeMpgForNewEntry - only partial fills in history returns null`() {
        val previous = listOf(
            fuelLog(odometerKm = 0.0, volumeL = 10.0, isFullTank = false),
            fuelLog(odometerKm = 100.0, volumeL = 10.0, isFullTank = false),
        )
        val newLog = fuelLog(odometerKm = 500.0, volumeL = 30.0)
        assertNull(MpgCalculator.computeMpgForNewEntry(newLog, previous))
    }
}

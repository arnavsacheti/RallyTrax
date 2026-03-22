package com.rallytrax.app.data.maintenance

/**
 * Maintenance service categories and their common service types,
 * plus preset intervals for common schedules.
 */
object ServiceCategories {

    val categories: Map<String, List<String>> = linkedMapOf(
        "Engine" to listOf("Oil change", "Spark plugs", "Timing belt/chain", "Air filter", "Fuel filter", "Serpentine belt"),
        "Transmission" to listOf("Fluid change", "Differential fluid", "Clutch replacement", "CV joint/boot"),
        "Cooling" to listOf("Coolant flush", "Radiator", "Thermostat", "Water pump", "Hose replacement"),
        "Brakes" to listOf("Pad replacement", "Rotor resurfacing/replacement", "Fluid flush", "Caliper service"),
        "Tires & Wheels" to listOf("Rotation", "Alignment", "Balancing", "New tires", "TPMS sensor"),
        "Suspension" to listOf("Shocks/struts", "Ball joints", "Tie rods", "Bushings", "Springs"),
        "Electrical" to listOf("Battery", "Alternator", "Starter", "Lights", "Fuses", "Wiring"),
        "Exhaust" to listOf("Catalytic converter", "O2 sensor", "Muffler", "Exhaust manifold"),
        "HVAC" to listOf("Cabin air filter", "A/C recharge", "Compressor", "Heater core"),
        "Body & Interior" to listOf("Wipers", "Windshield", "Paint repair", "Detailing", "Upholstery"),
        "Inspections" to listOf("Annual inspection", "Emissions test", "Pre-trip check"),
    )

    val allCategories: List<String> = categories.keys.toList()

    fun servicesForCategory(category: String): List<String> =
        categories[category] ?: emptyList()

    /**
     * Preset intervals for common maintenance schedules.
     * Returns (intervalKm, intervalMonths) pair, or null if no preset.
     */
    data class PresetInterval(
        val serviceType: String,
        val intervalKm: Double?,
        val intervalMonths: Int?,
    )

    val presetIntervals = listOf(
        PresetInterval("Oil change", 8_000.0, 6),
        PresetInterval("Tire rotation", 10_000.0, null),
        PresetInterval("Brake fluid", 40_000.0, 24),
        PresetInterval("Coolant flush", 50_000.0, 36),
        PresetInterval("Air filter", 20_000.0, 12),
        PresetInterval("Spark plugs", 50_000.0, null),
        PresetInterval("Transmission fluid", 60_000.0, null),
        PresetInterval("Cabin air filter", 20_000.0, 12),
    )

    fun getPresetInterval(serviceType: String): PresetInterval? =
        presetIntervals.find { it.serviceType.equals(serviceType, ignoreCase = true) }
}

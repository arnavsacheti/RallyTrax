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

    val motorcycleCategories: Map<String, List<String>> = linkedMapOf(
        "Engine" to listOf("Oil change", "Spark plugs", "Valve adjustment", "Air filter", "Fuel filter", "Carb sync/clean"),
        "Drive" to listOf("Chain adjustment", "Chain replacement", "Sprocket replacement", "Belt replacement", "Shaft drive fluid"),
        "Brakes" to listOf("Pad replacement", "Rotor replacement", "Fluid flush", "Caliper service", "Brake line"),
        "Tires & Wheels" to listOf("Front tire", "Rear tire", "Wheel bearing", "Spoke tensioning", "Balancing"),
        "Suspension" to listOf("Fork oil change", "Fork seal replacement", "Rear shock service", "Linkage bearings"),
        "Electrical" to listOf("Battery", "Stator/alternator", "Starter", "Lights", "Fuses", "Wiring"),
        "Cooling" to listOf("Coolant flush", "Radiator", "Thermostat", "Water pump", "Hose replacement"),
        "Exhaust" to listOf("Header/pipe", "Muffler", "O2 sensor", "Exhaust gasket"),
        "Controls & Cables" to listOf("Clutch cable", "Throttle cable", "Brake lever", "Clutch lever", "Grip replacement"),
        "Body & Cosmetic" to listOf("Fairing repair", "Paint", "Windscreen", "Seat", "Detailing"),
        "Inspections" to listOf("Annual inspection", "Pre-ride check", "Track day prep"),
    )

    val motorcyclePresetIntervals = listOf(
        PresetInterval("Oil change", 5_000.0, 6),
        PresetInterval("Chain adjustment", 1_000.0, null),
        PresetInterval("Air filter", 15_000.0, 12),
        PresetInterval("Valve adjustment", 25_000.0, null),
        PresetInterval("Brake fluid", 30_000.0, 24),
        PresetInterval("Coolant flush", 30_000.0, 24),
        PresetInterval("Fork oil change", 20_000.0, 24),
        PresetInterval("Spark plugs", 20_000.0, null),
    )

    fun categoriesForVehicleType(vehicleType: String?): Map<String, List<String>> {
        return when (vehicleType?.uppercase()) {
            "MOTORCYCLE" -> motorcycleCategories
            else -> categories
        }
    }

    fun presetsForVehicleType(vehicleType: String?): List<PresetInterval> {
        return when (vehicleType?.uppercase()) {
            "MOTORCYCLE" -> motorcyclePresetIntervals
            else -> presetIntervals
        }
    }
}

package org.theharmen.geenregenaub.domain

import java.time.Duration
import java.time.Instant

/** Where a forecast came from. Shown to the user for attribution. */
enum class ForecastSource(val attribution: String) {
    KNMI("Radardata: KNMI, CC BY 4.0"),
    OPEN_METEO("Weerdata: Open-Meteo.com, CC BY 4.0"),
}

/**
 * A short-term precipitation forecast for one point.
 *
 * @param issuedAt start time of the first step.
 * @param step length of each step (5 minutes for the KNMI nowcast).
 * @param intensities precipitation intensity per step, in mm/h.
 */
data class RainForecast(
    val issuedAt: Instant,
    val step: Duration,
    val intensities: List<Double>,
    val source: ForecastSource,
) {
    init {
        require(!step.isNegative && !step.isZero) { "step must be positive" }
        require(intensities.all { it >= 0.0 }) { "intensities must not be negative" }
    }

    fun timeOf(index: Int): Instant = issuedAt.plus(step.multipliedBy(index.toLong()))
}

/** Intensity bands used for wording and colours (mm/h). */
enum class Intensity(val minMmPerHour: Double) {
    LIGHT(0.1),
    MODERATE(2.5),
    HEAVY(10.0),
    ;

    companion object {
        fun of(mmPerHour: Double): Intensity? = entries.lastOrNull { mmPerHour >= it.minMmPerHour }
    }
}

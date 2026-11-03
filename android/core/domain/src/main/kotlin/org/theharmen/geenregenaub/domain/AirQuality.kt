package org.theharmen.geenregenaub.domain

import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * Air quality as the European Air Quality Index (0 is clean air, above 100 is extremely poor),
 * from the Copernicus CAMS forecast that Open-Meteo serves.
 */
data class AirQuality(
    val current: Int?,
    val hours: List<AirQualityHour>,
) {
    /** The hour that contains [now] and the [count] - 1 hours after it. */
    fun hoursFrom(now: Instant, count: Int): List<AirQualityHour> {
        val thisHour = now.truncatedTo(ChronoUnit.HOURS)
        return hours.asSequence().filter { !it.time.isBefore(thisHour) }.take(count).toList()
    }
}

data class AirQualityHour(val time: Instant, val index: Int)

/** The EEA's six bands, with the Dutch words the RIVM uses. */
enum class AirQualityLevel(val upTo: Int, val dutch: String, val advice: String) {
    GOOD(20, "Goed", "Prima lucht om buiten actief te zijn."),
    FAIR(40, "Redelijk", "Prima lucht om buiten actief te zijn."),
    MODERATE(60, "Matig", "Gevoelige mensen doen het buiten beter wat rustiger aan."),
    POOR(80, "Slecht", "Beperk zware inspanning buiten."),
    VERY_POOR(100, "Zeer slecht", "Vermijd zware inspanning buiten."),
    EXTREMELY_POOR(Int.MAX_VALUE, "Extreem slecht", "Blijf zoveel mogelijk binnen."),
    ;

    companion object {
        fun of(index: Int): AirQualityLevel = entries.first { index <= it.upTo }
    }
}

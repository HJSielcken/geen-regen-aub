package org.theharmen.geenregenaub.forecast

import org.theharmen.geenregenaub.domain.Wind
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/** Dutch labels shared by the weather screen and the widget. */
object WeatherFormat {
    private val dutch: Locale = Locale.forLanguageTag("nl-NL")
    private val clock = DateTimeFormatter.ofPattern("HH:mm", dutch)
    private val shortDay = DateTimeFormatter.ofPattern("EEE d MMM", dutch)
    private val weekday = DateTimeFormatter.ofPattern("EEE", dutch)

    fun time(instant: Instant, zone: ZoneId): String = clock.format(instant.atZone(zone))

    /** The bare hour for a narrow column, e.g. "14". */
    fun hour(instant: Instant, zone: ZoneId): String = instant.atZone(zone).hour.toString()

    /** "Vandaag", "Morgen", else e.g. "wo 5 nov". */
    fun day(date: LocalDate, today: LocalDate, todayLabel: String, tomorrowLabel: String): String = when (date) {
        today -> todayLabel
        today.plusDays(1) -> tomorrowLabel
        else -> shortDay.format(date).replace(".", "")
    }

    /** Two-letter weekday for the widget, e.g. "wo". */
    fun weekday(date: LocalDate): String = weekday.format(date).replace(".", "")

    /** "ZW 4" (direction and Beaufort), or just "4" without a direction. */
    fun wind(kmh: Double?, degrees: Double?): String? {
        kmh ?: return null
        val force = Wind.beaufort(kmh)
        return if (degrees != null) "${Wind.direction(degrees)} $force" else "$force"
    }

    /** "ZW 4 Bft · 22 km/u". */
    fun windLong(kmh: Double?, degrees: Double?): String? {
        val short = wind(kmh, degrees) ?: return null
        return "$short Bft · ${kmh!!.roundToInt()} km/u"
    }

    fun kmh(kmh: Double): String = "${kmh.roundToInt()} km/u"

    /** "0,4 mm". */
    fun mm(value: Double): String = String.format(dutch, "%.1f mm", value)

    fun percent(value: Int): String = "$value%"
}

package org.theharmen.geenregenaub.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt

/**
 * The general weather forecast (temperature, sky, wind) for one place: the weather right now,
 * hour by hour for about ten days, and day by day. Comes from Open-Meteo.
 */
data class WeatherForecast(
    val current: CurrentWeather?,
    val hours: List<HourWeather>,
    val days: List<DayWeather>,
) {
    /** The hour that contains [now] and the [count] - 1 hours after it. */
    fun hoursFrom(now: Instant, count: Int): List<HourWeather> {
        val thisHour = now.truncatedTo(ChronoUnit.HOURS)
        return hours.asSequence().filter { !it.time.isBefore(thisHour) }.take(count).toList()
    }

    /** All hours of [date] in [zone], for a day's detail view. */
    fun hoursOn(date: LocalDate, zone: ZoneId): List<HourWeather> =
        hours.filter { it.time.atZone(zone).toLocalDate() == date }

    /** Today and the days after it. */
    fun daysFrom(today: LocalDate): List<DayWeather> = days.filter { !it.date.isBefore(today) }

    fun day(date: LocalDate): DayWeather? = days.firstOrNull { it.date == date }
}

data class CurrentWeather(
    val time: Instant,
    val temperature: Double,
    val feelsLike: Double?,
    val condition: WeatherCondition,
    val isDay: Boolean,
    val windSpeedKmh: Double?,
    val windGustsKmh: Double?,
    val windDirectionDegrees: Double?,
    val humidityPercent: Int?,
)

data class HourWeather(
    val time: Instant,
    val temperature: Double,
    val condition: WeatherCondition,
    val isDay: Boolean,
    /** 0–100, or null when the model has no estimate for this hour. */
    val precipitationProbability: Int?,
    val precipitationMm: Double?,
    val windSpeedKmh: Double?,
    val windDirectionDegrees: Double?,
)

data class DayWeather(
    val date: LocalDate,
    val condition: WeatherCondition,
    val temperatureMax: Double,
    val temperatureMin: Double,
    val precipitationMm: Double?,
    val precipitationProbability: Int?,
    val windSpeedMaxKmh: Double?,
    val windGustsMaxKmh: Double?,
    val windDirectionDegrees: Double?,
    val sunrise: Instant?,
    val sunset: Instant?,
    val uvIndexMax: Double?,
)

/** The sky, grouped from the WMO weather codes Open-Meteo uses. */
enum class WeatherCondition {
    CLEAR,
    MAINLY_CLEAR,
    PARTLY_CLOUDY,
    OVERCAST,
    FOG,
    DRIZZLE,
    FREEZING_DRIZZLE,
    LIGHT_RAIN,
    RAIN,
    HEAVY_RAIN,
    FREEZING_RAIN,
    LIGHT_SNOW,
    SNOW,
    HEAVY_SNOW,
    SNOW_GRAINS,
    RAIN_SHOWERS,
    HEAVY_RAIN_SHOWERS,
    SNOW_SHOWERS,
    THUNDERSTORM,
    THUNDERSTORM_HAIL,
    UNKNOWN,
    ;

    /** Dutch description, e.g. "Half bewolkt". A clear night is "Helder" rather than "Zonnig". */
    fun dutch(isDay: Boolean = true): String = when (this) {
        CLEAR -> if (isDay) "Zonnig" else "Helder"
        MAINLY_CLEAR -> "Licht bewolkt"
        PARTLY_CLOUDY -> "Half bewolkt"
        OVERCAST -> "Bewolkt"
        FOG -> "Mist"
        DRIZZLE -> "Motregen"
        FREEZING_DRIZZLE -> "Onderkoelde motregen"
        LIGHT_RAIN -> "Lichte regen"
        RAIN -> "Regen"
        HEAVY_RAIN -> "Zware regen"
        FREEZING_RAIN -> "IJzel"
        LIGHT_SNOW -> "Lichte sneeuw"
        SNOW -> "Sneeuw"
        HEAVY_SNOW -> "Zware sneeuw"
        SNOW_GRAINS -> "Korrelsneeuw"
        RAIN_SHOWERS -> "Buien"
        HEAVY_RAIN_SHOWERS -> "Zware buien"
        SNOW_SHOWERS -> "Sneeuwbuien"
        THUNDERSTORM -> "Onweer"
        THUNDERSTORM_HAIL -> "Onweer met hagel"
        UNKNOWN -> "Onbekend"
    }

    companion object {
        /** https://open-meteo.com/en/docs#weathervariables (WMO 4677 subset). */
        fun fromWmo(code: Int?): WeatherCondition = when (code) {
            0 -> CLEAR
            1 -> MAINLY_CLEAR
            2 -> PARTLY_CLOUDY
            3 -> OVERCAST
            45, 48 -> FOG
            51, 53, 55 -> DRIZZLE
            56, 57 -> FREEZING_DRIZZLE
            61 -> LIGHT_RAIN
            63 -> RAIN
            65 -> HEAVY_RAIN
            66, 67 -> FREEZING_RAIN
            71 -> LIGHT_SNOW
            73 -> SNOW
            75 -> HEAVY_SNOW
            77 -> SNOW_GRAINS
            80, 81 -> RAIN_SHOWERS
            82 -> HEAVY_RAIN_SHOWERS
            85, 86 -> SNOW_SHOWERS
            95 -> THUNDERSTORM
            96, 99 -> THUNDERSTORM_HAIL
            else -> UNKNOWN
        }
    }
}

/** Wind as the Dutch say it: direction letters and Beaufort force. */
object Wind {
    private val directions = listOf("N", "NO", "O", "ZO", "Z", "ZW", "W", "NW")

    /** Where the wind comes from, e.g. 225° → "ZW". */
    fun direction(degrees: Double): String {
        val normalized = ((degrees % 360) + 360) % 360
        return directions[((normalized + 22.5) / 45).toInt() % 8]
    }

    /** Upper bounds (km/h, exclusive) of Beaufort 0–11; anything faster is 12. */
    private val beaufortLimits = doubleArrayOf(1.0, 6.0, 12.0, 20.0, 29.0, 39.0, 50.0, 62.0, 75.0, 89.0, 103.0, 118.0)

    fun beaufort(kmh: Double): Int {
        val index = beaufortLimits.indexOfFirst { kmh < it }
        return if (index == -1) 12 else index
    }
}

/** "12°", "-3°". */
fun formatTemperature(celsius: Double): String = "${celsius.roundToInt()}°"

package org.theharmen.geenregenaub.data.weather

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.theharmen.geenregenaub.domain.CurrentWeather
import org.theharmen.geenregenaub.domain.DayWeather
import org.theharmen.geenregenaub.domain.HourWeather
import org.theharmen.geenregenaub.domain.WeatherCondition
import org.theharmen.geenregenaub.domain.WeatherForecast
import java.time.Instant
import java.time.ZoneOffset
import kotlin.math.roundToInt

/**
 * Parses Open-Meteo's `/v1/forecast` answer, requested with `timeformat=unixtime` and the
 * variables in [OpenMeteoWeatherApi.QUERY]. Hours or days with missing core values are skipped.
 */
internal object OpenMeteoJson {
    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    private data class Body(
        @SerialName("utc_offset_seconds") val utcOffsetSeconds: Int = 0,
        val current: Current? = null,
        val hourly: Hourly? = null,
        val daily: Daily? = null,
    )

    @Serializable
    private data class Current(
        val time: Long,
        @SerialName("temperature_2m") val temperature: Double? = null,
        @SerialName("apparent_temperature") val apparentTemperature: Double? = null,
        @SerialName("weather_code") val weatherCode: Int? = null,
        @SerialName("is_day") val isDay: Int? = null,
        @SerialName("wind_speed_10m") val windSpeed: Double? = null,
        @SerialName("wind_gusts_10m") val windGusts: Double? = null,
        @SerialName("wind_direction_10m") val windDirection: Double? = null,
        @SerialName("relative_humidity_2m") val humidity: Double? = null,
    )

    @Serializable
    private data class Hourly(
        val time: List<Long> = emptyList(),
        @SerialName("temperature_2m") val temperature: List<Double?> = emptyList(),
        @SerialName("weather_code") val weatherCode: List<Int?> = emptyList(),
        @SerialName("is_day") val isDay: List<Int?> = emptyList(),
        @SerialName("precipitation_probability") val precipitationProbability: List<Double?> = emptyList(),
        val precipitation: List<Double?> = emptyList(),
        @SerialName("wind_speed_10m") val windSpeed: List<Double?> = emptyList(),
        @SerialName("wind_direction_10m") val windDirection: List<Double?> = emptyList(),
    )

    @Serializable
    private data class Daily(
        val time: List<Long> = emptyList(),
        @SerialName("weather_code") val weatherCode: List<Int?> = emptyList(),
        @SerialName("temperature_2m_max") val temperatureMax: List<Double?> = emptyList(),
        @SerialName("temperature_2m_min") val temperatureMin: List<Double?> = emptyList(),
        @SerialName("precipitation_sum") val precipitationSum: List<Double?> = emptyList(),
        @SerialName("precipitation_probability_max") val precipitationProbability: List<Double?> = emptyList(),
        @SerialName("wind_speed_10m_max") val windSpeedMax: List<Double?> = emptyList(),
        @SerialName("wind_gusts_10m_max") val windGustsMax: List<Double?> = emptyList(),
        @SerialName("wind_direction_10m_dominant") val windDirection: List<Double?> = emptyList(),
        val sunrise: List<Long?> = emptyList(),
        val sunset: List<Long?> = emptyList(),
        @SerialName("uv_index_max") val uvIndexMax: List<Double?> = emptyList(),
    )

    /** @throws kotlinx.serialization.SerializationException or IllegalArgumentException on a malformed body. */
    fun parse(body: String): WeatherForecast {
        val parsed = json.decodeFromString(Body.serializer(), body)
        return WeatherForecast(
            current = parsed.current?.toDomain(),
            hours = parsed.hourly?.toDomain().orEmpty(),
            days = parsed.daily?.toDomain(ZoneOffset.ofTotalSeconds(parsed.utcOffsetSeconds)).orEmpty(),
        )
    }

    private fun Current.toDomain(): CurrentWeather? {
        val temperature = temperature ?: return null
        return CurrentWeather(
            time = Instant.ofEpochSecond(time),
            temperature = temperature,
            feelsLike = apparentTemperature,
            condition = WeatherCondition.fromWmo(weatherCode),
            isDay = isDay != 0,
            windSpeedKmh = windSpeed,
            windGustsKmh = windGusts,
            windDirectionDegrees = windDirection,
            humidityPercent = humidity?.roundToInt(),
        )
    }

    private fun Hourly.toDomain(): List<HourWeather> = time.indices.mapNotNull { i ->
        val temperature = temperature.getOrNull(i) ?: return@mapNotNull null
        HourWeather(
            time = Instant.ofEpochSecond(time[i]),
            temperature = temperature,
            condition = WeatherCondition.fromWmo(weatherCode.getOrNull(i)),
            isDay = isDay.getOrNull(i) != 0,
            precipitationProbability = precipitationProbability.getOrNull(i)?.roundToInt(),
            precipitationMm = precipitation.getOrNull(i),
            windSpeedKmh = windSpeed.getOrNull(i),
            windDirectionDegrees = windDirection.getOrNull(i),
        )
    }

    /** Daily `time` is local midnight as epoch seconds; [offset] turns it back into the date. */
    private fun Daily.toDomain(offset: ZoneOffset): List<DayWeather> = time.indices.mapNotNull { i ->
        val max = temperatureMax.getOrNull(i) ?: return@mapNotNull null
        val min = temperatureMin.getOrNull(i) ?: return@mapNotNull null
        DayWeather(
            date = Instant.ofEpochSecond(time[i]).atOffset(offset).toLocalDate(),
            condition = WeatherCondition.fromWmo(weatherCode.getOrNull(i)),
            temperatureMax = max,
            temperatureMin = min,
            precipitationMm = precipitationSum.getOrNull(i),
            precipitationProbability = precipitationProbability.getOrNull(i)?.roundToInt(),
            windSpeedMaxKmh = windSpeedMax.getOrNull(i),
            windGustsMaxKmh = windGustsMax.getOrNull(i),
            windDirectionDegrees = windDirection.getOrNull(i),
            sunrise = sunrise.getOrNull(i)?.let(Instant::ofEpochSecond),
            sunset = sunset.getOrNull(i)?.let(Instant::ofEpochSecond),
            uvIndexMax = uvIndexMax.getOrNull(i),
        )
    }
}

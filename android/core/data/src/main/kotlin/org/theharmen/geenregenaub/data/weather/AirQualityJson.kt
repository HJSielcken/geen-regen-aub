package org.theharmen.geenregenaub.data.weather

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.theharmen.geenregenaub.domain.AirQuality
import org.theharmen.geenregenaub.domain.AirQualityHour
import java.time.Instant
import kotlin.math.roundToInt

/**
 * Parses Open-Meteo's `/v1/air-quality` answer, requested with `timeformat=unixtime` and the
 * variables in [OpenMeteoWeatherApi.AIR_QUERY]. Hours without an index are skipped.
 */
internal object AirQualityJson {
    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    private data class Body(
        val current: Current? = null,
        val hourly: Hourly? = null,
    )

    @Serializable
    private data class Current(
        @SerialName("european_aqi") val europeanAqi: Double? = null,
    )

    @Serializable
    private data class Hourly(
        val time: List<Long> = emptyList(),
        @SerialName("european_aqi") val europeanAqi: List<Double?> = emptyList(),
    )

    /** @throws kotlinx.serialization.SerializationException or IllegalArgumentException on a malformed body. */
    fun parse(body: String): AirQuality {
        val parsed = json.decodeFromString(Body.serializer(), body)
        val hourly = parsed.hourly
        val hours = hourly?.time?.indices?.mapNotNull { i ->
            val index = hourly.europeanAqi.getOrNull(i) ?: return@mapNotNull null
            AirQualityHour(Instant.ofEpochSecond(hourly.time[i]), index.roundToInt())
        }.orEmpty()
        return AirQuality(current = parsed.current?.europeanAqi?.roundToInt(), hours = hours)
    }
}

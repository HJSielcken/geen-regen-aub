package org.theharmen.geenregenaub.data.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.theharmen.geenregenaub.domain.ForecastSource
import org.theharmen.geenregenaub.domain.RainForecast
import java.time.Duration
import java.time.Instant

/** Wire format of `GET /v1/rain`, see docs/api.md. Unknown fields are ignored (v1 only adds fields). */
@Serializable
internal data class RainResponseJson(
    val source: String,
    @SerialName("issued_at") val issuedAt: String,
    @SerialName("interval_minutes") val intervalMinutes: Int = 5,
    val series: List<Entry>,
    val stale: Boolean = false,
    val attribution: String? = null,
) {
    @Serializable
    data class Entry(
        val time: String,
        @SerialName("mm_per_hour") val mmPerHour: Double,
    )

    fun toResponse(): RainApiResponse {
        val source = if (source == "knmi") ForecastSource.KNMI else ForecastSource.OPEN_METEO
        val sorted = series.sortedBy { Instant.parse(it.time) }
        val forecast = RainForecast(
            issuedAt = sorted.firstOrNull()?.let { Instant.parse(it.time) } ?: Instant.parse(issuedAt),
            step = Duration.ofMinutes(intervalMinutes.toLong().coerceAtLeast(1)),
            intensities = sorted.map { it.mmPerHour.coerceAtLeast(0.0) },
            source = source,
        )
        return RainApiResponse(forecast, stale, attribution ?: source.attribution)
    }

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun parse(body: String): RainApiResponse = json.decodeFromString(serializer(), body).toResponse()
    }
}

package org.theharmen.geenregenaub.data.store

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.theharmen.geenregenaub.data.CachedForecast
import org.theharmen.geenregenaub.data.Place
import org.theharmen.geenregenaub.domain.ForecastSource
import org.theharmen.geenregenaub.domain.RainForecast
import java.time.Duration
import java.time.Instant

/** JSON encoding of the cache. A value that no longer decodes is treated as absent. */
internal object StoredJson {
    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    private data class StoredPlace(val name: String, val lat: Double, val lon: Double, val current: Boolean)

    @Serializable
    private data class StoredForecast(
        val place: StoredPlace,
        val issuedAtEpochSeconds: Long,
        val stepSeconds: Long,
        val intensities: List<Double>,
        val source: String,
        val fetchedAtEpochMillis: Long,
        val stale: Boolean,
        val attribution: String,
        val demo: Boolean = false,
    )

    private fun Place.stored() = StoredPlace(name, lat, lon, isCurrentLocation)

    private fun StoredPlace.place() = Place(name, lat, lon, current)

    fun encodePlace(place: Place): String = json.encodeToString(StoredPlace.serializer(), place.stored())

    fun decodePlace(value: String): Place? =
        runCatching { json.decodeFromString(StoredPlace.serializer(), value).place() }.getOrNull()

    fun encodePlaces(places: List<Place>): String =
        json.encodeToString(ListSerializer(StoredPlace.serializer()), places.map { it.stored() })

    fun decodePlaces(value: String): List<Place> =
        runCatching { json.decodeFromString(ListSerializer(StoredPlace.serializer()), value).map { it.place() } }
            .getOrDefault(emptyList())

    fun encodeForecast(value: CachedForecast): String = json.encodeToString(
        StoredForecast.serializer(),
        StoredForecast(
            place = value.place.stored(),
            issuedAtEpochSeconds = value.forecast.issuedAt.epochSecond,
            stepSeconds = value.forecast.step.seconds,
            intensities = value.forecast.intensities,
            source = value.forecast.source.name,
            fetchedAtEpochMillis = value.fetchedAt.toEpochMilli(),
            stale = value.stale,
            attribution = value.attribution,
            demo = value.isDemo,
        ),
    )

    fun decodeForecast(value: String): CachedForecast? = runCatching {
        val s = json.decodeFromString(StoredForecast.serializer(), value)
        CachedForecast(
            forecast = RainForecast(
                issuedAt = Instant.ofEpochSecond(s.issuedAtEpochSeconds),
                step = Duration.ofSeconds(s.stepSeconds),
                intensities = s.intensities,
                source = ForecastSource.entries.firstOrNull { it.name == s.source } ?: ForecastSource.KNMI,
            ),
            place = s.place.place(),
            fetchedAt = Instant.ofEpochMilli(s.fetchedAtEpochMillis),
            stale = s.stale,
            attribution = s.attribution,
            isDemo = s.demo,
        )
    }.getOrNull()
}

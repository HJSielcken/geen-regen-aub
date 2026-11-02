package org.theharmen.geenregenaub.data.weather

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.theharmen.geenregenaub.data.Place
import java.time.Instant

private val Context.weatherDataStore: DataStore<Preferences> by preferencesDataStore(name = "weather")

/**
 * The last weather forecast, kept apart from the rain cache. Stores Open-Meteo's own JSON so the
 * format follows the parser; a value that no longer parses counts as absent.
 */
internal class WeatherStore(context: Context) {
    private val dataStore = context.weatherDataStore

    val cached: Flow<CachedWeather?> = dataStore.data
        .map { prefs -> prefs[KEY]?.let(::decode) }
        .distinctUntilChanged()

    suspend fun save(place: Place, fetchedAt: Instant, body: String) {
        val stored = Stored(place.name, place.lat, place.lon, place.isCurrentLocation, fetchedAt.toEpochMilli(), body)
        dataStore.edit { it[KEY] = json.encodeToString(Stored.serializer(), stored) }
    }

    @Serializable
    private data class Stored(
        val name: String,
        val lat: Double,
        val lon: Double,
        val current: Boolean,
        val fetchedAtEpochMillis: Long,
        val body: String,
    )

    private companion object {
        val KEY = stringPreferencesKey("weather_v1")
        val json = Json { ignoreUnknownKeys = true }

        fun decode(value: String): CachedWeather? = runCatching {
            val stored = json.decodeFromString(Stored.serializer(), value)
            CachedWeather(
                forecast = OpenMeteoJson.parse(stored.body),
                place = Place(stored.name, stored.lat, stored.lon, stored.current),
                fetchedAt = Instant.ofEpochMilli(stored.fetchedAtEpochMillis),
            )
        }.getOrNull()
    }
}

package org.theharmen.geenregenaub.data.store

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import org.theharmen.geenregenaub.data.CachedForecast
import org.theharmen.geenregenaub.data.LocationSettings
import org.theharmen.geenregenaub.data.Place

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "geenregen")

/** On-device cache and location settings, shared by the app, the widget and background work. */
internal class ForecastStore(context: Context) {
    private val dataStore = context.dataStore

    val cachedForecast: Flow<CachedForecast?> = dataStore.data
        .map { prefs -> prefs[FORECAST]?.let(StoredJson::decodeForecast) }
        .distinctUntilChanged()

    val locationSettings: Flow<LocationSettings> = dataStore.data
        .map { prefs ->
            LocationSettings(
                useCurrentLocation = prefs[USE_CURRENT] ?: true,
                chosenPlace = prefs[CHOSEN_PLACE]?.let(StoredJson::decodePlace),
            )
        }
        .distinctUntilChanged()

    val lastCurrentLocation: Flow<Place?> = dataStore.data
        .map { prefs -> prefs[LAST_CURRENT]?.let(StoredJson::decodePlace) }

    /** Saved places, in the order the user added them. */
    val favorites: Flow<List<Place>> = dataStore.data
        .map { prefs -> prefs[FAVORITES]?.let(StoredJson::decodePlaces).orEmpty() }
        .distinctUntilChanged()

    /** Applies [change] to the saved places in one atomic edit. */
    suspend fun updateFavorites(change: (List<Place>) -> List<Place>) {
        dataStore.edit { prefs ->
            val current = prefs[FAVORITES]?.let(StoredJson::decodePlaces).orEmpty()
            prefs[FAVORITES] = StoredJson.encodePlaces(change(current))
        }
    }

    suspend fun saveForecast(forecast: CachedForecast) {
        dataStore.edit { it[FORECAST] = StoredJson.encodeForecast(forecast) }
    }

    suspend fun saveLastCurrentLocation(place: Place) {
        dataStore.edit { it[LAST_CURRENT] = StoredJson.encodePlace(place) }
    }

    /** [chosen] null keeps the current chosen place. */
    suspend fun saveLocationSettings(useCurrent: Boolean, chosen: Place?) {
        dataStore.edit { prefs ->
            prefs[USE_CURRENT] = useCurrent
            if (chosen != null) prefs[CHOSEN_PLACE] = StoredJson.encodePlace(chosen)
        }
    }

    private companion object {
        val FORECAST = stringPreferencesKey("forecast_v1")
        val USE_CURRENT = booleanPreferencesKey("use_current_location")
        val CHOSEN_PLACE = stringPreferencesKey("chosen_place_v1")
        val LAST_CURRENT = stringPreferencesKey("last_current_location_v1")
        val FAVORITES = stringPreferencesKey("favorite_places_v1")
    }
}

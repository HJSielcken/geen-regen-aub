package org.theharmen.geenregenaub.data

import kotlinx.coroutines.flow.Flow

/**
 * The one shared source of rain forecasts for the home screen, the widget and the background job.
 * Get it with [GeenRegenData.forecastRepository].
 */
interface ForecastRepository {
    /** The cached forecast, updated after every successful [refresh]. Never hits the network. */
    val cachedForecast: Flow<CachedForecast?>

    /** The cached forecast right now, or null before the first successful fetch. Offline-safe. */
    suspend fun latest(): CachedForecast?

    /**
     * Resolves the location (current location if allowed and available, else the last known one,
     * else the chosen place), fetches the forecast from the relay and stores it in the cache.
     */
    suspend fun refresh(): RefreshResult
}

/** The user's location choice and place search. Get it with [GeenRegenData.placeRepository]. */
interface PlaceRepository {
    val settings: Flow<LocationSettings>

    /** Searches Dutch towns, postcodes and addresses (PDOK Locatieserver). */
    suspend fun search(query: String): List<Place>

    /** Use [place] for the forecast and stop following the phone's location. */
    suspend fun choose(place: Place)

    /** Follow the phone's location again (the chosen place stays as fallback). */
    suspend fun useCurrentLocation()
}

package org.theharmen.geenregenaub.data

import kotlinx.coroutines.flow.Flow
import java.time.Duration

/**
 * The one shared source of rain forecasts for the home screen, the widget and the background job.
 * Get it with [GeenRegenData.forecastRepository].
 */
interface ForecastRepository {
    /**
     * The cached forecast for the default place, updated after every successful [refresh]. Never
     * hits the network. The widgets and rain alerts use this one.
     */
    val cachedForecast: Flow<CachedForecast?>

    /**
     * The last forecast for a temporary place, kept apart so the widgets stay on the default.
     * May be for an older temporary place: pick with [LocationSettings.shown].
     */
    val temporaryForecast: Flow<CachedForecast?>

    /** The cached forecast right now, or null before the first successful fetch. Offline-safe. */
    suspend fun latest(): CachedForecast?

    /**
     * Resolves the location (current location if allowed and available, else the last known one,
     * else the chosen place), fetches the forecast from the relay and stores it in the cache.
     */
    suspend fun refresh(): RefreshResult

    /** Like [refresh], but for the place the app shows now: a temporary place while it lasts. */
    suspend fun refreshShown(): RefreshResult

    /**
     * The rain at any [place], e.g. a point held on the radar map. Leaves the cache and the
     * location settings alone; a failure carries no cached forecast.
     */
    suspend fun forecastFor(place: Place): RefreshResult
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

    /**
     * Makes [place] the default, the place the app always comes back to; null makes it the
     * phone's location. A temporary place keeps showing, unless it is this same place.
     */
    suspend fun setDefault(place: Place?)

    /**
     * Shows the forecast for [place] for a while (an hour by default) without changing the
     * default; after that, or after [clearTemporary], the default place is back. A place with
     * [Place.isCurrentLocation] means: follow the phone for that while.
     */
    suspend fun chooseTemporarily(place: Place, duration: Duration = TEMPORARY_DURATION)

    /** Back to the default place straight away. */
    suspend fun clearTemporary()

    /** The town at this point, e.g. "Leusden", or null when unknown, abroad or offline. */
    suspend fun nameAt(lat: Double, lon: Double): String?

    /** The user's saved places ("Thuis", "Werk", …), in the order they were added. */
    val favorites: Flow<List<Place>>

    /**
     * Saves [place] under [name] (blank keeps its own name). A place at the same spot is
     * replaced, so this also renames. If it is the chosen place, the chosen place is renamed too.
     */
    suspend fun saveFavorite(place: Place, name: String)

    /** Forgets the saved place at the same spot as [place]. */
    suspend fun removeFavorite(place: Place)

    companion object {
        /** How long a place picked on the map stays before the default comes back. */
        val TEMPORARY_DURATION: Duration = Duration.ofHours(1)
    }
}

/** True when both are the same point on the map, whatever they are called. */
fun Place.isSameSpot(other: Place): Boolean =
    kotlin.math.abs(lat - other.lat) < 1e-5 && kotlin.math.abs(lon - other.lon) < 1e-5

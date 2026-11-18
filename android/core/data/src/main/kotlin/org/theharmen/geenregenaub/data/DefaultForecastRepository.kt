package org.theharmen.geenregenaub.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.theharmen.geenregenaub.data.api.RainApi
import org.theharmen.geenregenaub.data.api.RainApiException
import org.theharmen.geenregenaub.data.location.LocationProvider
import org.theharmen.geenregenaub.data.places.PlaceSearch
import org.theharmen.geenregenaub.data.store.ForecastStore
import java.io.IOException
import java.time.Clock
import kotlin.math.abs

internal class DefaultForecastRepository(
    private val store: ForecastStore,
    private val api: RainApi,
    private val isDemo: Boolean,
    private val location: LocationProvider,
    private val placeSearch: PlaceSearch,
    private val clock: Clock = Clock.systemUTC(),
) : ForecastRepository {
    private val mutex = Mutex()

    override val cachedForecast: Flow<CachedForecast?> = store.cachedForecast

    override val temporaryForecast: Flow<CachedForecast?> = store.temporaryForecast

    override suspend fun latest(): CachedForecast? = store.cachedForecast.first()

    override suspend fun refresh(): RefreshResult = mutex.withLock {
        val settings = store.locationSettings.first()
        if (!settings.useCurrentLocation) {
            val chosen = settings.chosenPlace ?: return RefreshResult.Failure(FailureReason.NO_LOCATION, latest())
            return fetch(chosen, temporary = false)
        }
        refreshHere(settings.chosenPlace, temporary = false)
    }

    override suspend fun refreshShown(): RefreshResult {
        val settings = store.locationSettings.first()
        val temporary = settings.activeTemporary(clock.instant()) ?: return refresh()
        return mutex.withLock {
            // A temporary "Hier" follows the phone even when the default is a fixed place.
            if (temporary.isCurrentLocation) refreshHere(fallback = null, temporary = true) else fetch(temporary, temporary = true)
        }
    }

    /**
     * The forecast where the phone is, else at [fallback]. [temporary]: keep it apart from the
     * default forecast that the widgets and alerts use.
     */
    private suspend fun refreshHere(fallback: Place?, temporary: Boolean): RefreshResult {
        val latest = if (temporary) null else latest()

        // The phone saw itself recently: use that position, no need to wait for a new fix.
        val known = location.lastKnownLocation()
        if (known != null && known.ageMs < RECENT_FIX_MS) return fetchHere(known.lat, known.lon, temporary)

        // Otherwise ask for a fix (that can take seconds), and meanwhile show the forecast for
        // where the phone was last. Most of the time it has not moved, and then we are done.
        return coroutineScope {
            val fix = async { location.freshLocation() }
            val guess = store.lastCurrentLocation.first()
            val first = when {
                guess != null -> fetch(guess, temporary)
                known != null -> fetchHere(known.lat, known.lon, temporary)
                else -> null
            }
            val firstPlace = (first as? RefreshResult.Success)?.forecast?.place ?: guess
            val here = fix.await()
            when {
                here == null -> first ?: fallback?.let { fetch(it, temporary) }
                    ?: RefreshResult.Failure(FailureReason.NO_LOCATION, latest)
                first is RefreshResult.Success && firstPlace != null && isNearby(firstPlace, here.first, here.second) -> first
                else -> fetchHere(here.first, here.second, temporary)
            }
        }
    }

    override suspend fun forecastFor(place: Place): RefreshResult = try {
        val response = api.rain(place.lat, place.lon)
        RefreshResult.Success(
            CachedForecast(
                forecast = response.forecast,
                place = place,
                fetchedAt = clock.instant(),
                stale = response.stale,
                attribution = response.attribution,
                isDemo = isDemo,
            ),
        )
    } catch (e: RainApiException) {
        RefreshResult.Failure(e.reason, null)
    } catch (e: IOException) {
        RefreshResult.Failure(FailureReason.NETWORK, null)
    }

    /** Fetches the forecast at the phone's position, looking up the town name at the same time. */
    private suspend fun fetchHere(lat: Double, lon: Double, temporary: Boolean): RefreshResult = coroutineScope {
        val name = async { townName(lat, lon) }
        val result = fetch(Place(CURRENT_LOCATION_NAME, lat, lon, isCurrentLocation = true), temporary) { place ->
            // The name only labels the forecast; do not let a slow lookup hold it back for long.
            place.copy(name = withTimeoutOrNull(NAME_WAIT_MS) { name.await() } ?: nearbyName(lat, lon) ?: place.name)
        }
        name.cancel()
        if (result is RefreshResult.Failure) {
            store.saveLastCurrentLocation(Place(nearbyName(lat, lon) ?: CURRENT_LOCATION_NAME, lat, lon, isCurrentLocation = true))
        }
        result
    }

    /** Fetches and caches the forecast for [place]; [name] can still rename it once the rain is in. */
    private suspend fun fetch(place: Place, temporary: Boolean, name: suspend (Place) -> Place = { it }): RefreshResult = try {
        val response = api.rain(place.lat, place.lon)
        val named = name(place)
        if (named.isCurrentLocation) store.saveLastCurrentLocation(named)
        val cached = CachedForecast(
            forecast = response.forecast,
            place = named,
            fetchedAt = clock.instant(),
            stale = response.stale,
            attribution = response.attribution,
            isDemo = isDemo,
        )
        if (temporary) store.saveTemporaryForecast(cached) else store.saveForecast(cached)
        RefreshResult.Success(cached)
    } catch (e: RainApiException) {
        RefreshResult.Failure(e.reason, if (temporary) null else latest())
    } catch (e: IOException) {
        RefreshResult.Failure(FailureReason.NETWORK, if (temporary) null else latest())
    }

    /** The saved name for the phone's last position, when [lat], [lon] is about the same spot. */
    private suspend fun nearbyName(lat: Double, lon: Double): String? {
        val last = store.lastCurrentLocation.first() ?: return null
        return last.name.takeIf { it != CURRENT_LOCATION_NAME && isNearby(last, lat, lon) }
    }

    /**
     * The town name for the phone's position. Reuses the last name while the phone stays within
     * about a kilometre, so a lookup only happens after moving; falls back to "Huidige locatie".
     */
    private suspend fun townName(lat: Double, lon: Double): String {
        nearbyName(lat, lon)?.let { return it }
        val town = try {
            placeSearch.townAt(lat, lon)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
        return town ?: CURRENT_LOCATION_NAME
    }

    companion object {
        const val CURRENT_LOCATION_NAME = "Huidige locatie"

        /** A position the phone saw this recently is good enough; no new fix needed. */
        private const val RECENT_FIX_MS = 10 * 60 * 1000L

        /** How long the forecast may wait for the town name lookup once the rain is in. */
        private const val NAME_WAIT_MS = 2_000L

        /** Within roughly a kilometre (0.01° latitude ≈ 1.1 km, 0.015° longitude ≈ 1 km here). */
        internal fun isNearby(place: Place, lat: Double, lon: Double): Boolean =
            abs(place.lat - lat) < 0.01 && abs(place.lon - lon) < 0.015
    }
}

package org.theharmen.geenregenaub.data

import kotlinx.coroutines.CancellationException
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

    override suspend fun latest(): CachedForecast? = store.cachedForecast.first()

    override suspend fun refresh(): RefreshResult = mutex.withLock {
        val place = resolvePlace() ?: return RefreshResult.Failure(FailureReason.NO_LOCATION, latest())
        try {
            val response = api.rain(place.lat, place.lon)
            val cached = CachedForecast(
                forecast = response.forecast,
                place = place,
                fetchedAt = clock.instant(),
                stale = response.stale,
                attribution = response.attribution,
                isDemo = isDemo,
            )
            store.saveForecast(cached)
            RefreshResult.Success(cached)
        } catch (e: RainApiException) {
            RefreshResult.Failure(e.reason, latest())
        } catch (e: IOException) {
            RefreshResult.Failure(FailureReason.NETWORK, latest())
        }
    }

    private suspend fun resolvePlace(): Place? {
        val settings = store.locationSettings.first()
        if (settings.useCurrentLocation) {
            location.currentLocation()?.let { (lat, lon) ->
                val place = Place(townName(lat, lon), lat, lon, isCurrentLocation = true)
                store.saveLastCurrentLocation(place)
                return place
            }
            // In the background, or without a fix: the last place we saw the phone.
            store.lastCurrentLocation.first()?.let { return it }
        }
        return settings.chosenPlace
    }

    /**
     * The town name for the phone's position. Reuses the last name while the phone stays within
     * about a kilometre, so a lookup only happens after moving; falls back to "Huidige locatie".
     */
    private suspend fun townName(lat: Double, lon: Double): String {
        val last = store.lastCurrentLocation.first()
        if (last != null && last.name != CURRENT_LOCATION_NAME && isNearby(last, lat, lon)) return last.name
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

        /** Within roughly a kilometre (0.01° latitude ≈ 1.1 km, 0.015° longitude ≈ 1 km here). */
        internal fun isNearby(place: Place, lat: Double, lon: Double): Boolean =
            abs(place.lat - lat) < 0.01 && abs(place.lon - lon) < 0.015
    }
}

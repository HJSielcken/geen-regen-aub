package org.theharmen.geenregenaub.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.theharmen.geenregenaub.data.api.RainApi
import org.theharmen.geenregenaub.data.api.RainApiException
import org.theharmen.geenregenaub.data.location.LocationProvider
import org.theharmen.geenregenaub.data.store.ForecastStore
import java.io.IOException
import java.time.Clock

internal class DefaultForecastRepository(
    private val store: ForecastStore,
    private val api: RainApi,
    private val isDemo: Boolean,
    private val location: LocationProvider,
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
                val place = Place(CURRENT_LOCATION_NAME, lat, lon, isCurrentLocation = true)
                store.saveLastCurrentLocation(place)
                return place
            }
            // In the background, or without a fix: the last place we saw the phone.
            store.lastCurrentLocation.first()?.let { return it }
        }
        return settings.chosenPlace
    }

    companion object {
        const val CURRENT_LOCATION_NAME = "Huidige locatie"
    }
}

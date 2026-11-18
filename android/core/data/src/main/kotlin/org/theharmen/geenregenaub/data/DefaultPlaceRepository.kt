package org.theharmen.geenregenaub.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import org.theharmen.geenregenaub.data.places.PlaceSearch
import org.theharmen.geenregenaub.data.store.ForecastStore
import java.time.Clock
import java.time.Duration

internal class DefaultPlaceRepository(
    private val store: ForecastStore,
    private val placeSearch: PlaceSearch,
    private val clock: Clock = Clock.systemUTC(),
) : PlaceRepository {
    override val settings: Flow<LocationSettings> = store.locationSettings

    override suspend fun search(query: String): List<Place> =
        if (query.isBlank()) emptyList() else placeSearch.search(query.trim())

    override suspend fun choose(place: Place) =
        store.saveLocationSettings(useCurrent = false, chosen = place, clearTemporary = true)

    override suspend fun useCurrentLocation() =
        store.saveLocationSettings(useCurrent = true, chosen = null, clearTemporary = true)

    override suspend fun chooseTemporarily(place: Place, duration: Duration) =
        store.saveTemporaryPlace(place.copy(isCurrentLocation = false), until = clock.instant().plus(duration))

    override suspend fun clearTemporary() = store.clearTemporaryPlace()

    override suspend fun nameAt(lat: Double, lon: Double): String? = try {
        placeSearch.townAt(lat, lon)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }

    override val favorites: Flow<List<Place>> = store.favorites

    override suspend fun saveFavorite(place: Place, name: String) {
        val saved = place.copy(name = name.trim().ifEmpty { place.name }, isCurrentLocation = false)
        store.updateFavorites { current ->
            val index = current.indexOfFirst { it.isSameSpot(saved) }
            if (index >= 0) current.toMutableList().apply { set(index, saved) } else current + saved
        }
        val settings = store.locationSettings.first()
        if (settings.chosenPlace?.isSameSpot(saved) == true) {
            store.saveLocationSettings(useCurrent = settings.useCurrentLocation, chosen = saved)
        }
        // A temporary place that is now saved shows its new name too.
        if (settings.activeTemporary(clock.instant())?.isSameSpot(saved) == true) {
            store.saveTemporaryPlace(saved, until = null)
        }
    }

    override suspend fun removeFavorite(place: Place) {
        store.updateFavorites { current -> current.filterNot { it.isSameSpot(place) } }
    }
}

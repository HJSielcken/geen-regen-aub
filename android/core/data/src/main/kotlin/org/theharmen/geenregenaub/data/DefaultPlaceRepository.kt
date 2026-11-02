package org.theharmen.geenregenaub.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import org.theharmen.geenregenaub.data.places.PlaceSearch
import org.theharmen.geenregenaub.data.store.ForecastStore

internal class DefaultPlaceRepository(
    private val store: ForecastStore,
    private val placeSearch: PlaceSearch,
) : PlaceRepository {
    override val settings: Flow<LocationSettings> = store.locationSettings

    override suspend fun search(query: String): List<Place> =
        if (query.isBlank()) emptyList() else placeSearch.search(query.trim())

    override suspend fun choose(place: Place) = store.saveLocationSettings(useCurrent = false, chosen = place)

    override suspend fun useCurrentLocation() = store.saveLocationSettings(useCurrent = true, chosen = null)

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
    }

    override suspend fun removeFavorite(place: Place) {
        store.updateFavorites { current -> current.filterNot { it.isSameSpot(place) } }
    }
}

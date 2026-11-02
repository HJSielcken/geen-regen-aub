package org.theharmen.geenregenaub.data

import kotlinx.coroutines.flow.Flow
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
}

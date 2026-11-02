package org.theharmen.geenregenaub.data

import android.content.Context
import org.theharmen.geenregenaub.data.api.DemoRainApi
import org.theharmen.geenregenaub.data.api.RelayRainApi
import org.theharmen.geenregenaub.data.location.CoarseLocationProvider
import org.theharmen.geenregenaub.data.places.PdokPlaceSearch
import org.theharmen.geenregenaub.data.radar.RadarRepository
import org.theharmen.geenregenaub.data.radar.RelayRadarRepository
import org.theharmen.geenregenaub.data.store.ForecastStore

/** Process-wide entry point to the data layer. Every caller shares the same cache. */
object GeenRegenData {
    @Volatile private var store: ForecastStore? = null
    @Volatile private var forecasts: ForecastRepository? = null
    @Volatile private var places: PlaceRepository? = null
    private val placeSearch by lazy { PdokPlaceSearch() }

    fun forecastRepository(context: Context): ForecastRepository =
        forecasts ?: synchronized(this) {
            forecasts ?: DefaultForecastRepository(
                store = store(context),
                api = if (RelayConfig.isConfigured) RelayRainApi(RelayConfig.baseUrl, RelayConfig.secret) else DemoRainApi(),
                isDemo = !RelayConfig.isConfigured,
                location = CoarseLocationProvider(context.applicationContext),
                placeSearch = placeSearch,
            ).also { forecasts = it }
        }

    fun placeRepository(context: Context): PlaceRepository =
        places ?: synchronized(this) {
            places ?: DefaultPlaceRepository(store(context), placeSearch).also { places = it }
        }

    /** Frames for the animated radar map. */
    val radarRepository: RadarRepository by lazy { RelayRadarRepository(RelayConfig.baseUrl, RelayConfig.secret) }

    private fun store(context: Context): ForecastStore =
        store ?: synchronized(this) {
            store ?: ForecastStore(context.applicationContext).also { store = it }
        }
}

package org.theharmen.geenregenaub.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.theharmen.geenregenaub.data.CachedForecast
import org.theharmen.geenregenaub.data.FailureReason
import org.theharmen.geenregenaub.data.GeenRegenData
import org.theharmen.geenregenaub.data.LocationSettings
import org.theharmen.geenregenaub.data.Place
import org.theharmen.geenregenaub.data.RefreshResult
import org.theharmen.geenregenaub.data.isSameSpot
import java.time.Instant

data class HomeUiState(
    val now: Instant = Instant.now(),
    val forecast: CachedForecast? = null,
    val settings: LocationSettings = LocationSettings(),
    val isRefreshing: Boolean = false,
    /** A refresh the user asked for (pull, new place), so the pull-to-refresh spinner shows. */
    val showRefreshIndicator: Boolean = false,
    val failure: FailureReason? = null,
    val hasLoaded: Boolean = false,
    val favorites: List<Place> = emptyList(),
) {
    /** A place picked on the map, shown instead of the default for a while; null when none. */
    val temporaryPlace: Place? get() = settings.activeTemporary(now)
}

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class HomeViewModel(application: Application) : AndroidViewModel(application) {
    private val forecasts = GeenRegenData.forecastRepository(application)
    private val places = GeenRegenData.placeRepository(application)

    private val refreshing = MutableStateFlow(false)
    private val userRefreshing = MutableStateFlow(false)
    private val failure = MutableStateFlow<FailureReason?>(null)
    private val loaded = MutableStateFlow(false)

    /** Ticks so "over 12 min" counts down without a new fetch. */
    private val clock = flow {
        while (true) {
            emit(Instant.now())
            delay(15_000)
        }
    }

    val state: StateFlow<HomeUiState> = combine(
        clock,
        forecasts.cachedForecast,
        places.settings,
        combine(refreshing, userRefreshing, failure, loaded) { r, u, f, l -> RefreshState(r, u, f, l) },
        places.favorites,
    ) { now, forecast, settings, refresh, favorites ->
        HomeUiState(
            now = now,
            forecast = forecast,
            settings = settings,
            isRefreshing = refresh.running,
            showRefreshIndicator = refresh.byUser,
            failure = refresh.failure,
            hasLoaded = refresh.loaded || forecast != null,
            favorites = favorites,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    private val query = MutableStateFlow("")
    val searchQuery: StateFlow<String> = query.asStateFlow()

    val searchResults: StateFlow<SearchResults> = query
        .debounce(300)
        .distinctUntilChanged()
        .mapLatest { q ->
            if (q.isBlank()) {
                SearchResults.Idle
            } else {
                try {
                    SearchResults.Found(places.search(q))
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    SearchResults.Failed
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SearchResults.Idle)

    private var refreshJob: Job? = null

    /**
     * Fetches a new forecast. [byUser] shows the pull-to-refresh spinner; the automatic refreshes
     * run quietly behind the cached forecast, which is on screen straight away.
     */
    fun refresh(byUser: Boolean = true) {
        if (byUser) userRefreshing.value = true
        if (refreshJob?.isActive == true) return
        refreshJob = viewModelScope.launch {
            refreshing.value = true
            try {
                val result = forecasts.refresh()
                failure.value = (result as? RefreshResult.Failure)?.reason
                loaded.value = true
            } finally {
                refreshing.value = false
                userRefreshing.value = false
            }
        }
    }

    /** Refreshes now and then every 5 minutes, for as long as the caller's coroutine runs. */
    suspend fun keepFresh(): Unit = coroutineScope {
        // A temporary place that starts or runs out: fetch for the new place right away.
        launch {
            combine(clock, places.settings) { now, settings -> settings.activeTemporary(now) }
                .distinctUntilChanged()
                .drop(1)
                .collect {
                    refreshJob?.join()
                    refresh()
                }
        }
        while (true) {
            refresh(byUser = false)
            delay(AUTO_REFRESH_MS)
        }
    }

    fun onQueryChange(value: String) {
        query.value = value
    }

    /**
     * Shows [place] now: the default place itself, or else temporarily, so the default stays
     * what it is (the star in the place sheet changes that).
     */
    fun show(place: Place) {
        viewModelScope.launch {
            val settings = places.settings.first()
            if (!settings.useCurrentLocation && settings.chosenPlace?.isSameSpot(place) == true) {
                places.clearTemporary()
            } else {
                places.chooseTemporarily(place.copy(isCurrentLocation = false))
            }
            query.value = ""
            refreshJob?.join()
            refresh()
        }
    }

    /** Shows the forecast where the phone is: the default when that is "Hier", else temporarily. */
    fun showCurrentLocation() {
        viewModelScope.launch {
            if (places.settings.first().useCurrentLocation) {
                places.clearTemporary()
            } else {
                places.chooseTemporarily(Place(CURRENT_LOCATION_LABEL, 0.0, 0.0, isCurrentLocation = true))
            }
            refreshJob?.join()
            refresh()
        }
    }

    /** Makes [place] the default (null: the phone's location). */
    fun setDefault(place: Place?) {
        viewModelScope.launch {
            places.setDefault(place)
            refreshJob?.join()
            refresh()
        }
    }

    /** Ends a temporary place: back to the phone's location or the chosen default place. */
    fun returnToDefault() {
        viewModelScope.launch {
            places.clearTemporary()
            refreshJob?.join()
            refresh()
        }
    }

    /** Saves [place] as a favourite called [name] (or renames it); [show] also shows its forecast. */
    fun saveFavorite(place: Place, name: String, show: Boolean) {
        viewModelScope.launch {
            places.saveFavorite(place, name)
            if (show) show(place.copy(name = name.trim().ifEmpty { place.name }, isCurrentLocation = false))
        }
    }

    fun removeFavorite(place: Place) {
        viewModelScope.launch { places.removeFavorite(place) }
    }

    private companion object {
        /** Name of a temporary "follow the phone"; the forecast names the town once it is in. */
        const val CURRENT_LOCATION_LABEL = "Huidige locatie"

        const val AUTO_REFRESH_MS = 5 * 60 * 1000L
    }
}

private data class RefreshState(
    val running: Boolean,
    val byUser: Boolean,
    val failure: FailureReason?,
    val loaded: Boolean,
)

sealed interface SearchResults {
    data object Idle : SearchResults
    data object Failed : SearchResults
    data class Found(val places: List<Place>) : SearchResults
}

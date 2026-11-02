package org.theharmen.geenregenaub.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
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
import java.time.Instant

data class HomeUiState(
    val now: Instant = Instant.now(),
    val forecast: CachedForecast? = null,
    val settings: LocationSettings = LocationSettings(),
    val isRefreshing: Boolean = false,
    val failure: FailureReason? = null,
    val hasLoaded: Boolean = false,
    val favorites: List<Place> = emptyList(),
)

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class HomeViewModel(application: Application) : AndroidViewModel(application) {
    private val forecasts = GeenRegenData.forecastRepository(application)
    private val places = GeenRegenData.placeRepository(application)

    private val refreshing = MutableStateFlow(false)
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
        combine(refreshing, failure, loaded) { r, f, l -> Triple(r, f, l) },
        places.favorites,
    ) { now, forecast, settings, (isRefreshing, lastFailure, hasLoaded), favorites ->
        HomeUiState(now, forecast, settings, isRefreshing, lastFailure, hasLoaded || forecast != null, favorites)
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

    fun refresh() {
        if (refreshJob?.isActive == true) return
        refreshJob = viewModelScope.launch {
            refreshing.value = true
            val result = forecasts.refresh()
            failure.value = (result as? RefreshResult.Failure)?.reason
            refreshing.value = false
            loaded.value = true
        }
    }

    /** Refreshes now and then every 5 minutes, for as long as the caller's coroutine runs. */
    suspend fun keepFresh() {
        while (true) {
            refresh()
            delay(AUTO_REFRESH_MS)
        }
    }

    fun onQueryChange(value: String) {
        query.value = value
    }

    fun choose(place: Place) {
        viewModelScope.launch {
            places.choose(place)
            query.value = ""
            refreshJob?.join()
            refresh()
        }
    }

    fun useCurrentLocation() {
        viewModelScope.launch {
            places.useCurrentLocation()
            refreshJob?.join()
            refresh()
        }
    }

    /** Saves [place] as a favourite called [name] (or renames it), then shows its forecast. */
    fun saveFavorite(place: Place, name: String, choose: Boolean) {
        viewModelScope.launch {
            places.saveFavorite(place, name)
            if (choose) {
                val saved = place.copy(name = name.trim().ifEmpty { place.name }, isCurrentLocation = false)
                places.choose(saved)
                query.value = ""
                refreshJob?.join()
                refresh()
            }
        }
    }

    fun removeFavorite(place: Place) {
        viewModelScope.launch { places.removeFavorite(place) }
    }

    private companion object {
        const val AUTO_REFRESH_MS = 5 * 60 * 1000L
    }
}

sealed interface SearchResults {
    data object Idle : SearchResults
    data object Failed : SearchResults
    data class Found(val places: List<Place>) : SearchResults
}

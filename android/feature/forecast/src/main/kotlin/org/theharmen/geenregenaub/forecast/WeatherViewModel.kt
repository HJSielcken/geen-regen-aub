package org.theharmen.geenregenaub.forecast

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.theharmen.geenregenaub.data.FailureReason
import org.theharmen.geenregenaub.data.GeenRegenData
import org.theharmen.geenregenaub.data.weather.CachedWeather
import org.theharmen.geenregenaub.data.weather.WeatherData
import org.theharmen.geenregenaub.data.weather.WeatherRefreshResult
import org.theharmen.geenregenaub.data.weather.WeatherRepository
import java.time.Duration
import java.time.Instant

data class WeatherUiState(
    val now: Instant = Instant.now(),
    val weather: CachedWeather? = null,
    val isRefreshing: Boolean = false,
    val failure: FailureReason? = null,
    val hasLoaded: Boolean = false,
)

class WeatherViewModel(application: Application) : AndroidViewModel(application) {
    private val weather = WeatherData.repository(application)
    private val rain = GeenRegenData.forecastRepository(application)
    private val places = GeenRegenData.placeRepository(application)

    private val refreshing = MutableStateFlow(false)
    private val failure = MutableStateFlow<FailureReason?>(null)
    private val loaded = MutableStateFlow(false)
    private val clock = MutableStateFlow(Instant.now())

    val state: StateFlow<WeatherUiState> = combine(
        clock,
        weather.cached,
        refreshing,
        failure,
        loaded,
    ) { now, cached, isRefreshing, lastFailure, hasLoaded ->
        WeatherUiState(now, cached, isRefreshing, lastFailure, hasLoaded || cached != null)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WeatherUiState())

    /** Pull to refresh: always fetch. */
    fun refresh() {
        viewModelScope.launch { load(Duration.ZERO) }
    }

    /**
     * While the screen is visible: fetch when the cache is old or the place changes (the rain side
     * resolves and names the place), and re-check every 10 minutes.
     */
    suspend fun keepFresh() = coroutineScope {
        launch {
            combine(rain.cachedForecast.map { it?.place }, places.settings) { place, settings -> place to settings }
                .distinctUntilChanged()
                .collect { load(WeatherRepository.DEFAULT_MAX_AGE) }
        }
        while (true) {
            clock.value = Instant.now()
            delay(10 * 60_000L)
            load(WeatherRepository.DEFAULT_MAX_AGE)
        }
    }

    private suspend fun load(maxAge: Duration) {
        refreshing.value = true
        val result = weather.refresh(maxAge)
        failure.value = (result as? WeatherRefreshResult.Failure)?.reason
        refreshing.value = false
        loaded.value = true
        clock.value = Instant.now()
        if (result is WeatherRefreshResult.Success) WeatherHooks.onWeatherUpdated(getApplication())
    }
}

/**
 * Lets the app redraw the weather widget after the screen fetched a new forecast, without this
 * module depending on the widget. Set once in the Application.
 */
object WeatherHooks {
    @Volatile
    var onWeatherUpdated: (Context) -> Unit = {}
}

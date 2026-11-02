package org.theharmen.geenregenaub.data.weather

import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerializationException
import org.theharmen.geenregenaub.data.FailureReason
import org.theharmen.geenregenaub.data.ForecastRepository
import org.theharmen.geenregenaub.data.GeenRegenData
import org.theharmen.geenregenaub.data.Place
import org.theharmen.geenregenaub.data.PlaceRepository
import org.theharmen.geenregenaub.domain.WeatherForecast
import java.io.IOException
import java.time.Clock
import java.time.Duration
import java.time.Instant
import kotlin.math.abs

/** The last weather forecast the app fetched. */
data class CachedWeather(
    val forecast: WeatherForecast,
    val place: Place,
    val fetchedAt: Instant,
) {
    companion object {
        const val ATTRIBUTION = "Weerdata: Open-Meteo.com, CC BY 4.0"
    }
}

sealed interface WeatherRefreshResult {
    data class Success(val weather: CachedWeather) : WeatherRefreshResult

    /** [cached] is the last good forecast, if any. */
    data class Failure(val reason: FailureReason, val cached: CachedWeather?) : WeatherRefreshResult
}

/**
 * The general weather forecast (temperature, sky, wind) for the app's weather screen and widget.
 * It follows the same place as the rain forecast. Get it with [WeatherData.repository].
 */
interface WeatherRepository {
    /** The cached forecast; never hits the network. */
    val cached: Flow<CachedWeather?>

    suspend fun latest(): CachedWeather?

    /**
     * Fetches a new forecast, unless the cached one is for the same place and younger than
     * [maxAge]. Pass [Duration.ZERO] to always fetch (pull to refresh).
     */
    suspend fun refresh(maxAge: Duration = DEFAULT_MAX_AGE): WeatherRefreshResult

    companion object {
        val DEFAULT_MAX_AGE: Duration = Duration.ofMinutes(30)
    }
}

/** Process-wide entry point, like [GeenRegenData]; shares its place and rain repositories. */
object WeatherData {
    @Volatile private var repository: WeatherRepository? = null

    fun repository(context: Context): WeatherRepository =
        repository ?: synchronized(this) {
            repository ?: DefaultWeatherRepository(
                store = WeatherStore(context.applicationContext),
                api = OpenMeteoWeatherApi(),
                rain = GeenRegenData.forecastRepository(context),
                places = GeenRegenData.placeRepository(context),
            ).also { repository = it }
        }
}

internal class DefaultWeatherRepository(
    private val store: WeatherStore,
    private val api: WeatherApi,
    private val rain: ForecastRepository,
    private val places: PlaceRepository,
    private val clock: Clock = Clock.systemUTC(),
) : WeatherRepository {
    private val mutex = Mutex()

    override val cached: Flow<CachedWeather?> = store.cached

    override suspend fun latest(): CachedWeather? = store.cached.first()

    override suspend fun refresh(maxAge: Duration): WeatherRefreshResult = mutex.withLock {
        val cached = latest()
        val place = resolvePlace() ?: return WeatherRefreshResult.Failure(FailureReason.NO_LOCATION, cached)
        val now = clock.instant()
        if (cached != null && cached.place.sameAs(place) && Duration.between(cached.fetchedAt, now) < maxAge) {
            return WeatherRefreshResult.Success(cached)
        }
        try {
            val body = api.forecastJson(place.lat, place.lon)
            val weather = CachedWeather(OpenMeteoJson.parse(body), place, now)
            store.save(place, now, body)
            WeatherRefreshResult.Success(weather)
        } catch (e: IOException) {
            WeatherRefreshResult.Failure(FailureReason.NETWORK, cached)
        } catch (e: SerializationException) {
            WeatherRefreshResult.Failure(FailureReason.RELAY_UNAVAILABLE, cached)
        } catch (e: IllegalArgumentException) {
            WeatherRefreshResult.Failure(FailureReason.RELAY_UNAVAILABLE, cached)
        }
    }

    /**
     * The same place the rain forecast uses: a fixed chosen place, or else wherever the last rain
     * forecast was for (the phone's location, named by the rain side), or else the chosen place.
     */
    private suspend fun resolvePlace(): Place? {
        val settings = places.settings.first()
        if (!settings.useCurrentLocation) settings.chosenPlace?.let { return it }
        return rain.latest()?.place ?: settings.chosenPlace
    }

    /** Same name and within ~1 km: no need to fetch again for a jittery location fix. */
    private fun Place.sameAs(other: Place) =
        name == other.name && abs(lat - other.lat) < 0.01 && abs(lon - other.lon) < 0.01
}

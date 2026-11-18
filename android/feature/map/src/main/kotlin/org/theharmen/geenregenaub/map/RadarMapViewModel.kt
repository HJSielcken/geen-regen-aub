package org.theharmen.geenregenaub.map

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.theharmen.geenregenaub.data.CachedForecast
import org.theharmen.geenregenaub.data.FailureReason
import org.theharmen.geenregenaub.data.GeenRegenData
import org.theharmen.geenregenaub.data.Place
import org.theharmen.geenregenaub.data.RefreshResult
import org.theharmen.geenregenaub.data.radar.RadarFrames
import org.theharmen.geenregenaub.data.weather.WeatherData
import org.theharmen.geenregenaub.domain.RainForecast
import org.theharmen.geenregenaub.domain.WeatherForecast
import java.time.Duration
import java.time.Instant
import java.util.Locale
import kotlin.math.abs

data class RadarMapState(
    val frames: RadarFrames? = null,
    /** Decoded pictures by frame index; frames still downloading are missing. */
    val images: Map<Int, Bitmap> = emptyMap(),
    val index: Int = 0,
    val playing: Boolean = false,
    val loading: Boolean = true,
    val failed: Boolean = false,
    val unavailable: Boolean = false,
    /** The place the home screen shows, for the pin and the first camera position. */
    val place: Place? = null,
    /** The home screen's 2-hour forecast, shown as a graph under the map. */
    val forecast: CachedForecast? = null,
    /** The point held on the map (or under the centre pin while picking), with its rain and weather. */
    val point: MapPoint? = null,
    val favorites: List<Place> = emptyList(),
    /** Name of a place that was just saved, for a short confirmation; null when there is none. */
    val savedName: String? = null,
) {
    val isReady: Boolean get() = frames != null && images.isNotEmpty()
}

/** Any point on the map, looked up on its own: the town name, the rain and the weather there. */
data class MapPoint(
    val lat: Double,
    val lon: Double,
    /** The town, once known; null while looking it up, or abroad. */
    val name: String? = null,
    val nameLoading: Boolean = true,
    val rain: RainForecast? = null,
    val rainFailure: FailureReason? = null,
    val rainLoading: Boolean = true,
    val weather: WeatherForecast? = null,
    val weatherLoading: Boolean = true,
) {
    /** The point as a place to save or use: named after its town, else its coordinates. */
    val place: Place get() = Place(name ?: coordinates(lat, lon), lat, lon, isCurrentLocation = false)

    companion object {
        /** "52,13° N 5,43° O". */
        fun coordinates(lat: Double, lon: Double): String {
            val nl = Locale.forLanguageTag("nl-NL")
            val ns = if (lat >= 0) "N" else "Z"
            val eo = if (lon >= 0) "O" else "W"
            return String.format(nl, "%.2f° %s %.2f° %s", kotlin.math.abs(lat), ns, kotlin.math.abs(lon), eo)
        }
    }
}

class RadarMapViewModel(application: Application) : AndroidViewModel(application) {
    private val radar = GeenRegenData.radarRepository
    private val forecasts = GeenRegenData.forecastRepository(application)
    private val places = GeenRegenData.placeRepository(application)
    private val weather = WeatherData.repository(application)
    private var pointJob: Job? = null

    private val _state = MutableStateFlow(RadarMapState())
    val state: StateFlow<RadarMapState> = _state.asStateFlow()

    private var loadJob: Job? = null
    private var playJob: Job? = null
    private var resumeJob: Job? = null
    private var autoplayed = false

    init {
        viewModelScope.launch {
            forecasts.cachedForecast.collect { cached ->
                _state.update { it.copy(place = cached?.place, forecast = cached) }
            }
        }
        viewModelScope.launch {
            places.favorites.collect { favorites -> _state.update { it.copy(favorites = favorites) } }
        }
        load()
    }

    fun load() {
        if (loadJob?.isActive == true) return
        if (!radar.isAvailable) {
            _state.update { it.copy(loading = false, unavailable = true) }
            return
        }
        loadJob = viewModelScope.launch {
            _state.update { it.copy(loading = true, failed = false) }
            try {
                val frames = radar.frames()
                val now = frames.frames.indexOfLast { it.observed }.coerceAtLeast(0)
                _state.update { it.copy(frames = frames, images = emptyMap(), index = now) }
                // "Now" first, then outwards, so the map shows something right away.
                val order = frames.frames.indices.sortedBy { kotlin.math.abs(it - now) }
                val gate = Semaphore(PARALLEL_DOWNLOADS)
                order.map { i ->
                    async {
                        gate.withPermit {
                            val bytes = radar.image(frames.frames[i])
                            val bitmap = withContext(Dispatchers.Default) {
                                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                            }
                            if (bitmap != null) {
                                _state.update { it.copy(images = it.images + (i to bitmap)) }
                                // Play as soon as there is something to show.
                                autoplay()
                            }
                        }
                    }
                }.awaitAll()
                _state.update { it.copy(loading = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, failed = !it.isReady) }
            }
        }
    }

    /**
     * Jumps to the frame closest to [time], e.g. when sliding along the rain graph. The animation
     * holds there for a moment, then carries on by itself; `null` (the marker tapped off) resumes now.
     */
    fun selectTime(time: Instant?) {
        if (time == null) {
            resume()
            return
        }
        val frames = _state.value.frames?.frames ?: return
        val index = frames.indices.minByOrNull { kotlin.math.abs(Duration.between(frames[it].time, time).seconds) }
            ?: return
        pause()
        _state.update { it.copy(index = index) }
        resumeJob?.cancel()
        resumeJob = viewModelScope.launch {
            delay(RESUME_AFTER_MS)
            resume()
        }
    }

    /**
     * Looks up the point at [lat], [lon]: its town, rain and weather, each shown as it comes in.
     * A new point cancels the lookups for the previous one.
     */
    fun inspect(lat: Double, lon: Double) {
        val current = _state.value.point
        if (current != null && abs(current.lat - lat) < 1e-5 && abs(current.lon - lon) < 1e-5) return
        pointJob?.cancel()
        _state.update { it.copy(point = MapPoint(lat, lon)) }
        pointJob = viewModelScope.launch {
            launch {
                val name = places.nameAt(lat, lon)
                updatePoint(lat, lon) { it.copy(name = name, nameLoading = false) }
            }
            launch {
                val spot = Place(MapPoint.coordinates(lat, lon), lat, lon, isCurrentLocation = false)
                when (val result = forecasts.forecastFor(spot)) {
                    is RefreshResult.Success -> updatePoint(lat, lon) {
                        it.copy(rain = result.forecast.forecast, rainLoading = false)
                    }
                    is RefreshResult.Failure -> updatePoint(lat, lon) {
                        it.copy(rainFailure = result.reason, rainLoading = false)
                    }
                }
            }
            launch {
                val spot = Place(MapPoint.coordinates(lat, lon), lat, lon, isCurrentLocation = false)
                val forecast = weather.forecastFor(spot)
                updatePoint(lat, lon) { it.copy(weather = forecast, weatherLoading = false) }
            }
        }
    }

    /** Applies [change] only while the point at [lat], [lon] is still the one on screen. */
    private fun updatePoint(lat: Double, lon: Double, change: (MapPoint) -> MapPoint) {
        _state.update { s ->
            val point = s.point
            if (point != null && point.lat == lat && point.lon == lon) s.copy(point = change(point)) else s
        }
    }

    fun dismissPoint() {
        pointJob?.cancel()
        _state.update { it.copy(point = null) }
    }

    /** Shows the point on the home screen for a while, without changing the default; then [done]. */
    fun useTemporarily(done: () -> Unit) {
        val point = _state.value.point ?: return
        viewModelScope.launch {
            places.chooseTemporarily(point.place)
            done()
        }
    }

    /** Saves the point as [name]; [makeDefault] also makes it the place the app always shows. */
    fun save(name: String, makeDefault: Boolean) {
        val point = _state.value.point ?: return
        val place = point.place
        val savedName = name.trim().ifEmpty { place.name }
        viewModelScope.launch {
            places.saveFavorite(place, savedName)
            if (makeDefault) places.choose(place.copy(name = savedName))
            _state.update { it.copy(savedName = savedName) }
        }
    }

    fun savedShown() {
        _state.update { it.copy(savedName = null) }
    }

    /** Starts the animation once, when the first picture is in. */
    fun autoplay() {
        if (autoplayed || !_state.value.isReady) return
        autoplayed = true
        if (playJob?.isActive != true) play()
    }

    private fun resume() {
        resumeJob?.cancel()
        resumeJob = null
        if (playJob?.isActive != true && _state.value.isReady) play()
    }

    private fun play() {
        playJob = viewModelScope.launch {
            _state.update { it.copy(playing = true) }
            // Start from the beginning when already at the end.
            _state.update { s ->
                val last = (s.frames?.frames?.size ?: 1) - 1
                if (s.index >= last) s.copy(index = 0) else s
            }
            while (isActive) {
                val s = _state.value
                val count = s.frames?.frames?.size ?: break
                val next = (s.index + 1) % count
                delay(if (next == 0) END_PAUSE_MS else FRAME_MS)
                // Skip frames that haven't downloaded yet.
                val target = (0 until count).map { (next + it) % count }.firstOrNull { it in _state.value.images }
                    ?: continue
                _state.update { it.copy(index = target) }
            }
        }
    }

    private fun pause() {
        playJob?.cancel()
        playJob = null
        _state.update { it.copy(playing = false) }
    }

    private companion object {
        const val PARALLEL_DOWNLOADS = 4
        const val FRAME_MS = 450L
        const val END_PAUSE_MS = 1_200L
        const val RESUME_AFTER_MS = 3_000L
    }
}

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
import org.theharmen.geenregenaub.data.GeenRegenData
import org.theharmen.geenregenaub.data.Place
import org.theharmen.geenregenaub.data.radar.RadarFrames
import java.time.Duration
import java.time.Instant

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
) {
    val isReady: Boolean get() = frames != null && images.isNotEmpty()
}

class RadarMapViewModel(application: Application) : AndroidViewModel(application) {
    private val radar = GeenRegenData.radarRepository
    private val forecasts = GeenRegenData.forecastRepository(application)

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

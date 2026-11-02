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
import org.theharmen.geenregenaub.data.GeenRegenData
import org.theharmen.geenregenaub.data.Place
import org.theharmen.geenregenaub.data.radar.RadarFrames

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

    init {
        viewModelScope.launch {
            val place = runCatching { forecasts.latest()?.place }.getOrNull()
            _state.update { it.copy(place = place) }
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
                            if (bitmap != null) _state.update { it.copy(images = it.images + (i to bitmap)) }
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

    fun select(index: Int) {
        pause()
        _state.update { it.copy(index = index.coerceIn(0, (it.frames?.frames?.size ?: 1) - 1)) }
    }

    fun togglePlay() {
        if (playJob?.isActive == true) pause() else play()
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
    }
}

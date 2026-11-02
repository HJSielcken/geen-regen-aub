package org.theharmen.geenregenaub.map

import android.os.Bundle
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngQuad
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.RasterLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.android.style.sources.ImageSource
import org.maplibre.geojson.Point
import org.theharmen.geenregenaub.data.radar.RadarBounds
import org.theharmen.geenregenaub.domain.RainForecast
import org.theharmen.geenregenaub.domain.remainingAt
import org.theharmen.geenregenaub.home.RainGraph
import org.theharmen.geenregenaub.ui.theme.isAppInDarkTheme
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val clock = DateTimeFormatter.ofPattern("HH:mm")
private const val RADAR_SOURCE = "radar"
private const val RADAR_LAYER = "radar-layer"
private const val PIN_SOURCE = "pin"
private const val PIN_LAYER = "pin-layer"

/** Animated radar: the past hour as measured, then the 2-hour forecast, over a map of the Netherlands. */
@Composable
fun RadarMapScreen(onBack: () -> Unit, viewModel: RadarMapViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val dark = isAppInDarkTheme()

    Box(Modifier.fillMaxSize()) {
        RadarMap(state, dark, Modifier.fillMaxSize())

        FilledTonalIconButton(
            onClick = onBack,
            modifier = Modifier
                .statusBarsPadding()
                .padding(12.dp),
        ) {
            Icon(painterResource(R.drawable.ic_back), contentDescription = stringResource(R.string.map_back))
        }

        Controls(
            state = state,
            onSelect = viewModel::select,
            onSelectTime = viewModel::selectTime,
            onTogglePlay = viewModel::togglePlay,
            onRetry = viewModel::load,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(12.dp),
        )
    }
}

@Composable
internal fun RadarMap(state: RadarMapState, dark: Boolean, modifier: Modifier, interactive: Boolean = true) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var style by remember { mutableStateOf<Style?>(null) }
    val mapView = remember {
        MapLibre.getInstance(context)
        MapView(context).apply { onCreate(Bundle()) }
    }

    DisposableEffect(lifecycle, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            mapView.onDestroy()
        }
    }

    // Base map and pin layer; redone when the theme changes.
    LaunchedEffect(mapView, dark) {
        mapView.getMapAsync { map ->
            map.uiSettings.isRotateGesturesEnabled = false
            map.uiSettings.isTiltGesturesEnabled = false
            if (!interactive) map.uiSettings.setAllGesturesEnabled(false)
            map.setMinZoomPreference(5.0)
            map.setMaxZoomPreference(12.0)
            style = null
            map.setStyle(Style.Builder().fromJson(BaseMap.styleJson(dark))) { loaded ->
                loaded.addSource(GeoJsonSource(PIN_SOURCE))
                loaded.addLayer(
                    CircleLayer(PIN_LAYER, PIN_SOURCE).withProperties(
                        PropertyFactory.circleRadius(7f),
                        PropertyFactory.circleColor("#1F6FEB"),
                        PropertyFactory.circleStrokeColor("#FFFFFF"),
                        PropertyFactory.circleStrokeWidth(2.5f),
                    ),
                )
                style = loaded
            }
        }
    }

    // Camera: on the home screen's place once it is known, else the whole country.
    var centred by remember { mutableStateOf(false) }
    val place = state.place
    LaunchedEffect(mapView, place) {
        if (centred) return@LaunchedEffect
        mapView.getMapAsync { map ->
            val position = CameraPosition.Builder()
                .target(if (place != null) LatLng(place.lat, place.lon) else LatLng(52.2, 5.3))
                .zoom(if (place != null) (if (interactive) 8.0 else 7.4) else 6.4)
                .build()
            map.moveCamera(CameraUpdateFactory.newCameraPosition(position))
        }
        if (place != null) centred = true
    }

    // The pin on that place.
    LaunchedEffect(style, place) {
        val source = style?.getSourceAs<GeoJsonSource>(PIN_SOURCE) ?: return@LaunchedEffect
        if (place != null) source.setGeoJson(Point.fromLngLat(place.lon, place.lat))
    }

    // Radar picture: add the layer once frames are known, then swap the picture per frame.
    val frames = state.frames
    val image = state.images[state.index] ?: state.images.entries.minByOrNull { kotlin.math.abs(it.key - state.index) }?.value
    LaunchedEffect(style, frames?.bounds, image) {
        val s = style ?: return@LaunchedEffect
        if (frames == null || image == null) return@LaunchedEffect
        val source = s.getSourceAs<ImageSource>(RADAR_SOURCE)
        if (source == null) {
            s.addSource(ImageSource(RADAR_SOURCE, quad(frames.bounds), image))
            s.addLayerBelow(
                RasterLayer(RADAR_LAYER, RADAR_SOURCE).withProperties(
                    PropertyFactory.rasterOpacity(0.85f),
                    PropertyFactory.rasterFadeDuration(0f),
                    PropertyFactory.rasterResampling("nearest"),
                ),
                PIN_LAYER,
            )
        } else {
            source.setImage(image)
        }
    }

    AndroidView(factory = { mapView }, modifier = modifier)
}

private fun quad(b: RadarBounds) = LatLngQuad(
    LatLng(b.north, b.west),
    LatLng(b.north, b.east),
    LatLng(b.south, b.east),
    LatLng(b.south, b.west),
)

@Composable
private fun Controls(
    state: RadarMapState,
    onSelect: (Int) -> Unit,
    onSelectTime: (Instant) -> Unit,
    onTogglePlay: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.95f)),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            val frames = state.frames
            when {
                state.unavailable -> Message(stringResource(R.string.map_unavailable))
                state.failed -> {
                    Message(stringResource(R.string.map_error))
                    OutlinedButton(onClick = onRetry) { Text(stringResource(R.string.map_retry)) }
                }
                frames == null || !state.isReady -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(12.dp))
                    Text(stringResource(R.string.map_loading), style = MaterialTheme.typography.bodyLarge)
                }
                else -> {
                    val frame = frames.frames[state.index]
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        FilledIconButton(onClick = onTogglePlay) {
                            Icon(
                                painterResource(if (state.playing) R.drawable.ic_pause else R.drawable.ic_play),
                                contentDescription = stringResource(if (state.playing) R.string.map_pause else R.string.map_play),
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                clock.format(frame.time.atZone(ZoneId.systemDefault())),
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                frameLabel(frame.time, frame.observed, frames.issuedAt),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (state.loading) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    }
                    val sliderLabel = stringResource(R.string.map_time_slider)
                    Slider(
                        value = state.index.toFloat(),
                        onValueChange = { onSelect(it.toInt()) },
                        valueRange = 0f..(frames.frames.size - 1).coerceAtLeast(1).toFloat(),
                        steps = (frames.frames.size - 2).coerceAtLeast(0),
                        modifier = Modifier.semantics { contentDescription = sliderLabel },
                    )
                    Legend(frames.legend.map { Color(it.argb) })
                    state.forecast?.let { cached ->
                        SyncedGraph(cached.forecast, frame.time, onSelectTime)
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.map_attribution),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** The home screen's 2-hour graph, its marker following the map frame. Sliding it moves the map. */
@Composable
private fun SyncedGraph(forecast: RainForecast, frameTime: Instant, onSelectTime: (Instant) -> Unit) {
    val upcoming = forecast.remainingAt(Instant.now()) ?: return
    val stepSeconds = upcoming.step.seconds.coerceAtLeast(1)
    val offset = Duration.between(upcoming.issuedAt, frameTime).seconds
    val index = ((offset + stepSeconds / 2).floorDiv(stepSeconds)).toInt().takeIf { offset >= 0 && it in upcoming.intensities.indices }
    Spacer(Modifier.height(8.dp))
    RainGraph(
        forecast = upcoming,
        zone = ZoneId.systemDefault(),
        summary = stringResource(R.string.map_graph_description),
        graphHeight = 96.dp,
        selectedIndex = index,
        onSelect = { i -> if (i != null) onSelectTime(upcoming.timeOf(i)) },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
internal fun frameLabel(time: Instant, observed: Boolean, issuedAt: Instant): String {
    val minutes = Duration.between(issuedAt, time).toMinutes().toInt()
    return when {
        observed && minutes == 0 -> stringResource(R.string.map_now)
        observed -> stringResource(R.string.map_observed_ago, -minutes)
        else -> stringResource(R.string.map_forecast_in, minutes)
    }
}

@Composable
private fun Legend(colors: List<Color>) {
    if (colors.isEmpty()) return
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.map_legend_light), style = MaterialTheme.typography.labelSmall)
        Canvas(
            Modifier
                .weight(1f)
                .height(8.dp),
        ) {
            val w = size.width / colors.size
            colors.forEachIndexed { i, c ->
                drawRoundRect(
                    color = c.copy(alpha = 1f),
                    topLeft = androidx.compose.ui.geometry.Offset(i * w, 0f),
                    size = androidx.compose.ui.geometry.Size(w - 2f, size.height),
                    cornerRadius = CornerRadius(3f, 3f),
                )
            }
        }
        Text(stringResource(R.string.map_legend_heavy), style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun Message(text: String) {
    Text(text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(vertical = 4.dp))
}

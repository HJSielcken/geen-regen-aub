package org.theharmen.geenregenaub.map

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import org.theharmen.geenregenaub.data.FailureReason
import org.theharmen.geenregenaub.domain.Headline
import org.theharmen.geenregenaub.domain.Verdict
import org.theharmen.geenregenaub.domain.VerdictText
import org.theharmen.geenregenaub.domain.WeatherForecast
import org.theharmen.geenregenaub.domain.formatTemperature
import org.theharmen.geenregenaub.domain.remainingAt
import org.theharmen.geenregenaub.domain.verdict
import org.theharmen.geenregenaub.forecast.WeatherFormat
import org.theharmen.geenregenaub.forecast.icon
import org.theharmen.geenregenaub.home.RainGraph
import java.time.Instant
import java.time.ZoneId

/**
 * The rain and weather at a point on the map, with buttons to save it or show it on the home
 * screen. [picking]: the map is a place picker, so the main button says "Kies deze plek".
 */
@Composable
internal fun PointCard(
    point: MapPoint,
    saved: Boolean,
    picking: Boolean,
    onClose: () -> Unit,
    onSave: () -> Unit,
    onUse: () -> Unit,
) {
    val now = Instant.now()
    val zone = ZoneId.systemDefault()
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(
            Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = point.name ?: if (point.nameLoading) "…" else MapPoint.coordinates(point.lat, point.lon),
                        style = MaterialTheme.typography.titleLarge,
                    )
                    if (point.name != null) {
                        Text(
                            text = MapPoint.coordinates(point.lat, point.lon),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (!picking) {
                    IconButton(onClick = onClose) {
                        Icon(painterResource(R.drawable.ic_close), contentDescription = stringResource(R.string.map_point_close))
                    }
                }
            }

            Column(Modifier.padding(end = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Rain(point, now, zone)
                point.weather?.let { Weather(it, now, zone) }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onSave, enabled = !saved, modifier = Modifier.weight(1f)) {
                        Icon(
                            painterResource(if (saved) R.drawable.ic_star else R.drawable.ic_star_outline),
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            stringResource(if (saved) R.string.map_point_saved_label else R.string.map_point_save),
                            modifier = Modifier.padding(start = 8.dp),
                            maxLines = 1,
                        )
                    }
                    Button(onClick = onUse, modifier = Modifier.weight(1f)) {
                        Text(
                            stringResource(if (picking) R.string.map_point_choose else R.string.map_point_use),
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Rain(point: MapPoint, now: Instant, zone: ZoneId) {
    val rain = point.rain
    when {
        point.rainLoading -> Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            Text(
                stringResource(R.string.map_point_rain_loading),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(start = 12.dp),
            )
        }
        rain == null -> Text(
            stringResource(
                if (point.rainFailure == FailureReason.OUTSIDE_NL) R.string.map_point_outside else R.string.map_point_rain_failed,
            ),
            style = MaterialTheme.typography.bodyLarge,
        )
        else -> {
            val upcoming = rain.remainingAt(now) ?: return
            val verdict = upcoming.verdict()
            val headline = Headline.dutch(verdict, now)
            Column(Modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }) {
                Text(
                    text = headline,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = if (verdict is Verdict.Dry) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = VerdictText.dutch(verdict, zone),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            RainGraph(
                forecast = upcoming,
                zone = zone,
                summary = stringResource(R.string.map_point_graph),
                graphHeight = 72.dp,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun Weather(forecast: WeatherForecast, now: Instant, zone: ZoneId) {
    val current = forecast.current
    val nextHour = forecast.hoursFrom(now, 1).firstOrNull()
    val temperature = current?.temperature ?: nextHour?.temperature ?: return
    val condition = current?.condition ?: nextHour?.condition ?: return
    val isDay = current?.isDay ?: nextHour?.isDay ?: true
    val day = forecast.day(now.atZone(zone).toLocalDate())

    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Image(painterResource(condition.icon(isDay)), contentDescription = null, modifier = Modifier.size(48.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = "${formatTemperature(temperature)} ${condition.dutch(isDay)}",
                style = MaterialTheme.typography.titleMedium,
            )
            day?.let {
                Text(
                    text = "max ${formatTemperature(it.temperatureMax)} · min ${formatTemperature(it.temperatureMin)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            WeatherFormat.wind(current?.windSpeedKmh ?: nextHour?.windSpeedKmh, current?.windDirectionDegrees ?: nextHour?.windDirectionDegrees)
                ?.let { wind ->
                    Text(
                        stringResource(R.string.map_point_wind, "$wind Bft"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            nextHour?.precipitationProbability?.let { chance ->
                Text(
                    stringResource(R.string.map_point_rain_chance, chance),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** The pin in the middle of the map while picking; its tip marks the point. */
@Composable
internal fun CenterPin(modifier: Modifier = Modifier) {
    Icon(
        painterResource(R.drawable.ic_place),
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
        modifier = modifier
            .offset(y = (-22).dp)
            .size(48.dp),
    )
}

/** Back button and what to do, on top of the map while picking. */
@Composable
internal fun PickHeader(onBack: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(32.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        shadowElevation = 3.dp,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 4.dp, end = 16.dp, top = 6.dp, bottom = 6.dp),
        ) {
            FilledTonalIconButton(onClick = onBack) {
                Icon(painterResource(R.drawable.ic_back), contentDescription = stringResource(R.string.map_back))
            }
            Column(Modifier.padding(start = 8.dp)) {
                Text(stringResource(R.string.map_pick_title), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.map_pick_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Name the point before saving it. */
@Composable
internal fun SavePointDialog(initial: String, onConfirm: (name: String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.map_save_title)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(40) },
                label = { Text(stringResource(R.string.map_save_name)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { onConfirm(name) }),
            )
        },
        confirmButton = { TextButton(onClick = { onConfirm(name) }) { Text(stringResource(R.string.map_save_confirm)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.map_save_cancel)) } },
    )
}

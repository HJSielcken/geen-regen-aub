package org.theharmen.geenregenaub.forecast

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.theharmen.geenregenaub.domain.formatTemperature
import java.time.ZoneId

/**
 * A compact weather card for the bottom of the home screen: now, today's max/min and the next
 * hours. Tapping it opens [WeatherActivity]. Shows nothing until there is a forecast.
 */
@Composable
fun WeatherSummaryCard(
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: WeatherViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) { viewModel.keepFresh() }
    }
    val forecast = state.weather?.forecast ?: return
    val zone = ZoneId.systemDefault()
    val hours = forecast.hoursFrom(state.now, 6)
    val current = forecast.current
    val temperature = current?.temperature ?: hours.firstOrNull()?.temperature ?: return
    val condition = current?.condition ?: hours.first().condition
    val isDay = current?.isDay ?: hours.first().isDay
    val today = forecast.day(state.now.atZone(zone).toLocalDate())

    Card(
        onClick = onOpen,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(painterResource(condition.icon(isDay)), contentDescription = null, modifier = Modifier.size(56.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "${formatTemperature(temperature)} ${condition.dutch(isDay)}",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = today?.let {
                            stringResource(R.string.weather_max_min, formatTemperature(it.temperatureMax), formatTemperature(it.temperatureMin))
                        } ?: stringResource(R.string.weather_summary_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (hours.size > 1) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    hours.forEachIndexed { index, hour ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = if (index == 0) stringResource(R.string.weather_now) else WeatherFormat.hour(hour.time, zone),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Image(painterResource(hour.condition.icon(hour.isDay)), contentDescription = null, modifier = Modifier.size(32.dp))
                            Text(formatTemperature(hour.temperature), style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
            Text(
                text = stringResource(R.string.weather_open),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

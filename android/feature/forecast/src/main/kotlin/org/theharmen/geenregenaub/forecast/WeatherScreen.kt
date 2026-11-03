package org.theharmen.geenregenaub.forecast

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.theharmen.geenregenaub.data.FailureReason
import org.theharmen.geenregenaub.data.weather.CachedWeather
import org.theharmen.geenregenaub.domain.DayWeather
import org.theharmen.geenregenaub.domain.HourWeather
import org.theharmen.geenregenaub.domain.WeatherForecast
import org.theharmen.geenregenaub.domain.formatTemperature
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * The weather forecast as sections for the home screen, under the rain graph: now, the next 24
 * hours and the coming days (each day expandable to its hours), then air quality, sun, moon and
 * wind. Shows nothing while the first forecast is still loading.
 */
@Composable
fun WeatherSections(modifier: Modifier = Modifier, viewModel: WeatherViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) { viewModel.keepFresh() }
    }
    WeatherContent(state, onRefresh = viewModel::refresh, modifier = modifier)
}

@Composable
internal fun WeatherContent(
    state: WeatherUiState,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    zone: ZoneId = ZoneId.systemDefault(),
) {
    var showLicence by remember { mutableStateOf(false) }
    val weather = state.weather
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (weather != null) {
            val today = state.now.atZone(zone).toLocalDate()
            CurrentBlock(weather.forecast, state.now, today, zone)
            HoursCard(weather.forecast.hoursFrom(state.now, 24), zone)
            DaysCard(weather.forecast, today, zone)
            weather.airQuality?.let { air -> AirQualityCard(air, state.now, zone) }
            SunCard(weather.forecast, state.now, today, zone)
            MoonCard(state.now, today, zone)
            weather.forecast.current?.let { current -> WindCard(current) }
        }
        // The rain side has its own location errors; only say something when the weather alone failed.
        state.failure?.takeIf { it != FailureReason.NO_LOCATION }?.let { ErrorCard(onRefresh) }
        if (weather != null || state.failure != null) {
            Footer(weather, zone, onShowLicence = { showLicence = true })
        }
    }
    if (showLicence) LicenceDialog(onDismiss = { showLicence = false })
}

@Composable
private fun CurrentBlock(forecast: WeatherForecast, now: Instant, today: LocalDate, zone: ZoneId) {
    val current = forecast.current
    val nextHour = forecast.hoursFrom(now, 1).firstOrNull()
    val day = forecast.day(today)
    val temperature = current?.temperature ?: nextHour?.temperature ?: return
    val condition = current?.condition ?: nextHour!!.condition
    val isDay = current?.isDay ?: nextHour!!.isDay

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(
                painter = painterResource(condition.icon(isDay)),
                contentDescription = null,
                modifier = Modifier.size(112.dp),
            )
            Spacer(Modifier.width(8.dp))
            Column {
                Text(
                    text = formatTemperature(temperature),
                    style = MaterialTheme.typography.displayLarge,
                    fontWeight = FontWeight.Light,
                )
                Text(
                    text = condition.dutch(isDay),
                    style = MaterialTheme.typography.titleMedium,
                )
                val detail = buildList {
                    current?.feelsLike?.let { add(stringResource(R.string.weather_feels_like, formatTemperature(it))) }
                    day?.let {
                        add(stringResource(R.string.weather_max_min, formatTemperature(it.temperatureMax), formatTemperature(it.temperatureMin)))
                    }
                }
                if (detail.isNotEmpty()) {
                    Text(
                        text = detail.joinToString(" · "),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        // Wind and the sun have their own sections further down, so they are not repeated here.
        val facts = buildList {
            current?.humidityPercent?.let { add(Fact(R.drawable.ic_fact_humidity, stringResource(R.string.weather_humidity), WeatherFormat.percent(it))) }
            day?.uvIndexMax?.let { add(Fact(R.drawable.ic_fact_uv, stringResource(R.string.weather_uv), String.format(java.util.Locale.ROOT, "%.0f", it))) }
        }
        if (facts.isNotEmpty()) FactGrid(facts)
    }
}

/** A value behind its icon; the label is read out by TalkBack instead of shown. */
private data class Fact(@DrawableRes val icon: Int, val label: String, val value: String)

@Composable
private fun FactItem(fact: Fact, modifier: Modifier = Modifier, large: Boolean = false) {
    Row(
        modifier = modifier.semantics(mergeDescendants = true) { contentDescription = "${fact.label}: ${fact.value}" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(fact.icon),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(if (large) 22.dp else 18.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = fact.value,
            style = if (large) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium,
            maxLines = 1,
        )
    }
}

@Composable
private fun FactGrid(facts: List<Fact>) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            facts.chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEach { fact -> FactItem(fact, Modifier.weight(1f), large = true) }
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
internal fun SectionCard(title: String, content: @Composable () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(vertical = 16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .semantics { heading() },
            )
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
private fun HoursCard(hours: List<HourWeather>, zone: ZoneId) {
    if (hours.isEmpty()) return
    SectionCard(stringResource(R.string.weather_hours_title)) {
        HourStrip(hours, zone, firstIsNow = true)
    }
}

/** A sideways-scrolling row of hours: time, icon, temperature, chance of rain and wind. */
@Composable
private fun HourStrip(hours: List<HourWeather>, zone: ZoneId, firstIsNow: Boolean) {
    val nowLabel = stringResource(R.string.weather_now)
    LazyRow(
        contentPadding = PaddingValues(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items(hours, key = { it.time.epochSecond }) { hour ->
            val label = if (firstIsNow && hour == hours.first()) nowLabel else WeatherFormat.hour(hour.time, zone)
            HourCell(hour, label)
        }
    }
}

@Composable
private fun HourCell(hour: HourWeather, label: String) {
    val description = buildString {
        append("$label: ${formatTemperature(hour.temperature)}, ${hour.condition.dutch(hour.isDay)}")
        hour.precipitationProbability?.takeIf { it > 0 }?.let { append(", $it% kans op neerslag") }
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = Modifier
            .width(56.dp)
            .semantics(mergeDescendants = true) { contentDescription = description },
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Image(painterResource(hour.condition.icon(hour.isDay)), contentDescription = null, modifier = Modifier.size(40.dp))
        Text(formatTemperature(hour.temperature), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        RainChance(hour.precipitationProbability)
        Text(
            text = WeatherFormat.wind(hour.windSpeedKmh, hour.windDirectionDegrees) ?: "",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** "40%" in rain blue, or an empty line of the same height when rain is unlikely. */
@Composable
private fun RainChance(probability: Int?) {
    val shown = probability?.takeIf { it >= 10 }
    Text(
        text = shown?.let(WeatherFormat::percent) ?: " ",
        style = MaterialTheme.typography.labelSmall,
        color = rainColor(),
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
private fun rainColor(): Color = MaterialTheme.colorScheme.primary

@Composable
private fun DaysCard(forecast: WeatherForecast, today: LocalDate, zone: ZoneId) {
    val days = forecast.daysFrom(today)
    if (days.isEmpty()) return
    val low = days.minOf { it.temperatureMin }
    val high = days.maxOf { it.temperatureMax }
    var expanded by rememberSaveable { mutableStateOf<String?>(null) }
    val todayLabel = stringResource(R.string.weather_today)
    val tomorrowLabel = stringResource(R.string.weather_tomorrow)

    SectionCard(stringResource(R.string.weather_days_title)) {
        days.forEachIndexed { index, day ->
            if (index > 0) HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
            val label = WeatherFormat.day(day.date, today, todayLabel, tomorrowLabel)
            val isExpanded = expanded == day.date.toString()
            DayRow(
                day = day,
                label = label,
                low = low,
                high = high,
                onClick = { expanded = if (isExpanded) null else day.date.toString() },
            )
            AnimatedVisibility(visible = isExpanded) {
                DayDetail(day, forecast.hoursOn(day.date, zone), zone)
            }
        }
    }
}

@Composable
private fun DayRow(day: DayWeather, label: String, low: Double, high: Double, onClick: () -> Unit) {
    val expandLabel = stringResource(R.string.weather_expand_day, label)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = expandLabel, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
            maxLines = 1,
        )
        Image(
            painter = painterResource(day.condition.icon()),
            contentDescription = day.condition.dutch(),
            modifier = Modifier.size(36.dp),
        )
        Box(Modifier.width(44.dp), contentAlignment = Alignment.Center) {
            RainChance(day.precipitationProbability)
        }
        Text(
            text = formatTemperature(day.temperatureMin),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.End,
            modifier = Modifier.width(36.dp),
        )
        TemperatureBar(day.temperatureMin, day.temperatureMax, low, high, Modifier.width(72.dp).padding(horizontal = 8.dp))
        Text(
            text = formatTemperature(day.temperatureMax),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.width(36.dp),
        )
    }
}

/** The day's min–max span on a track that covers the whole week's range, cool to warm. */
@Composable
private fun TemperatureBar(min: Double, max: Double, low: Double, high: Double, modifier: Modifier = Modifier) {
    val track = MaterialTheme.colorScheme.surfaceVariant
    Canvas(modifier.height(6.dp)) {
        val radius = CornerRadius(size.height / 2, size.height / 2)
        drawRoundRect(color = track, cornerRadius = radius)
        val span = (high - low).takeIf { it > 0 } ?: 1.0
        val start = ((min - low) / span).toFloat().coerceIn(0f, 1f) * size.width
        val end = ((max - low) / span).toFloat().coerceIn(0f, 1f) * size.width
        drawRoundRect(
            brush = Brush.horizontalGradient(listOf(temperatureColor(min), temperatureColor(max)), startX = start, endX = end),
            topLeft = Offset(start, 0f),
            size = Size((end - start).coerceAtLeast(size.height), size.height),
            cornerRadius = radius,
        )
    }
}

/** Blue below freezing, through green and yellow, to orange-red at 30 °C. Also used by the widgets. */
fun temperatureColor(celsius: Double): Color {
    val stops = listOf(
        -5.0 to Color(0xFF5B8DEF),
        5.0 to Color(0xFF4FC3F7),
        12.0 to Color(0xFF81C784),
        20.0 to Color(0xFFFFD54F),
        30.0 to Color(0xFFFF7043),
    )
    if (celsius <= stops.first().first) return stops.first().second
    if (celsius >= stops.last().first) return stops.last().second
    val upper = stops.indexOfFirst { celsius < it.first }
    val (t0, c0) = stops[upper - 1]
    val (t1, c1) = stops[upper]
    val f = ((celsius - t0) / (t1 - t0)).toFloat()
    return Color(
        red = c0.red + (c1.red - c0.red) * f,
        green = c0.green + (c1.green - c0.green) * f,
        blue = c0.blue + (c1.blue - c0.blue) * f,
    )
}

@Composable
private fun DayDetail(day: DayWeather, hours: List<HourWeather>, zone: ZoneId) {
    Column(Modifier.padding(bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (hours.isNotEmpty()) HourStrip(hours, zone, firstIsNow = false)
        val facts = buildList {
            WeatherFormat.wind(day.windSpeedMaxKmh, day.windDirectionDegrees)?.let {
                add(Fact(R.drawable.ic_fact_wind, stringResource(R.string.weather_wind), "$it Bft"))
            }
            day.windGustsMaxKmh?.let { add(Fact(R.drawable.ic_fact_gust, stringResource(R.string.weather_gusts), WeatherFormat.kmh(it))) }
            day.precipitationMm?.let { add(Fact(R.drawable.ic_fact_rain, stringResource(R.string.weather_rain_amount), WeatherFormat.mm(it))) }
            day.precipitationProbability?.let { add(Fact(R.drawable.ic_fact_rain_chance, stringResource(R.string.weather_rain_chance), WeatherFormat.percent(it))) }
            val sunrise = day.sunrise
            val sunset = day.sunset
            if (sunrise != null && sunset != null) {
                add(Fact(R.drawable.ic_fact_sunrise, stringResource(R.string.weather_sunrise), WeatherFormat.time(sunrise, zone)))
                add(Fact(R.drawable.ic_fact_sunset, stringResource(R.string.weather_sunset), WeatherFormat.time(sunset, zone)))
            }
            day.uvIndexMax?.let { add(Fact(R.drawable.ic_fact_uv, stringResource(R.string.weather_uv), String.format(java.util.Locale.ROOT, "%.0f", it))) }
        }
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            facts.chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEach { fact -> FactItem(fact, Modifier.weight(1f)) }
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun ErrorCard(onRetry: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.weather_error_network), color = MaterialTheme.colorScheme.onErrorContainer)
            OutlinedButton(onClick = onRetry) { Text(stringResource(R.string.weather_retry)) }
        }
    }
}

@Composable
private fun Footer(weather: CachedWeather?, zone: ZoneId, onShowLicence: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(bottom = 8.dp)) {
        val small = MaterialTheme.typography.bodySmall
        val color = MaterialTheme.colorScheme.onSurfaceVariant
        if (weather != null) {
            Text(stringResource(R.string.weather_updated, WeatherFormat.time(weather.fetchedAt, zone)), style = small, color = color)
        }
        Text(CachedWeather.ATTRIBUTION, style = small, color = color)
        if (weather?.airQuality != null) Text(CachedWeather.AIR_ATTRIBUTION, style = small, color = color)
        Text(
            text = stringResource(R.string.weather_icons_credit),
            style = small,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.clickable(onClick = onShowLicence),
        )
    }
}

@Composable
private fun LicenceDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val text = remember {
        context.resources.openRawResource(R.raw.meteocons_license).bufferedReader().use { it.readText() }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.weather_close)) } },
        title = { Text(stringResource(R.string.weather_licence_title)) },
        text = {
            LazyColumn {
                item { Text("https://github.com/basmilius/weather-icons", style = MaterialTheme.typography.bodySmall) }
                item { Spacer(Modifier.height(8.dp)) }
                item { Text(text, style = MaterialTheme.typography.bodySmall, fontSize = 11.sp) }
            }
        },
    )
}

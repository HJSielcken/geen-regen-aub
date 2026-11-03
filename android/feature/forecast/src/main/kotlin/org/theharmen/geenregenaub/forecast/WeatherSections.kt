package org.theharmen.geenregenaub.forecast

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.foundation.background
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.theharmen.geenregenaub.domain.AirQuality
import org.theharmen.geenregenaub.domain.AirQualityLevel
import org.theharmen.geenregenaub.domain.CurrentWeather
import org.theharmen.geenregenaub.domain.Moon
import org.theharmen.geenregenaub.domain.MoonPhaseName
import org.theharmen.geenregenaub.domain.WeatherForecast
import org.theharmen.geenregenaub.domain.Wind
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

/*
 * The sections below the forecast: air quality, sun, moon and wind, like the cards in other
 * weather apps. Wind and the sun are shown here and no longer as loose facts at the top.
 */

/** EEA colours for the six air quality bands. */
private fun airColor(index: Int): Color = when (AirQualityLevel.of(index)) {
    AirQualityLevel.GOOD -> Color(0xFF50F0E6)
    AirQualityLevel.FAIR -> Color(0xFF50CCAA)
    AirQualityLevel.MODERATE -> Color(0xFFF0E641)
    AirQualityLevel.POOR -> Color(0xFFFF5050)
    AirQualityLevel.VERY_POOR -> Color(0xFF960032)
    AirQualityLevel.EXTREMELY_POOR -> Color(0xFF7D2181)
}

/** European AQI now with its band and advice, the next 24 hours as bars, and the scale. */
@Composable
internal fun AirQualityCard(air: AirQuality, now: Instant, zone: ZoneId) {
    val hours = air.hoursFrom(now, 24)
    val index = air.current ?: hours.firstOrNull()?.index ?: return
    val level = AirQualityLevel.of(index)
    val variant = MaterialTheme.colorScheme.onSurfaceVariant
    SectionCard(stringResource(R.string.weather_air_title)) {
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = index.toString(),
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.Light,
                )
                Spacer(Modifier.width(16.dp))
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(10.dp).clip(CircleShape).background(airColor(index)))
                        Spacer(Modifier.width(8.dp))
                        Text(level.dutch, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    }
                    Text(level.advice, style = MaterialTheme.typography.bodyMedium, color = variant)
                }
            }
            if (hours.size >= 2) {
                val values = hours.map { it.index }
                val top = max(50, values.max()).toFloat()
                val baseline = variant.copy(alpha = 0.4f)
                Canvas(
                    Modifier
                        .fillMaxWidth()
                        .height(96.dp)
                        .semantics { contentDescription = "Luchtkwaliteit komende 24 uur" },
                ) {
                    val slot = size.width / values.size
                    val gap = minOf(slot * 0.25f, 3.dp.toPx())
                    values.forEachIndexed { i, v ->
                        val h = max(2.dp.toPx(), v / top * size.height)
                        drawRoundRect(
                            color = airColor(v),
                            topLeft = Offset(i * slot + gap / 2, size.height - h),
                            size = Size(slot - gap, h),
                            cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx()),
                        )
                    }
                    drawLine(baseline, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 1.dp.toPx())
                }
                Row(Modifier.fillMaxWidth()) {
                    listOf(hours.first(), hours[hours.size / 2], hours.last()).forEachIndexed { i, hour ->
                        Text(
                            text = WeatherFormat.time(hour.time, zone),
                            style = MaterialTheme.typography.labelSmall,
                            color = variant,
                            modifier = Modifier.weight(1f),
                            textAlign = when (i) {
                                0 -> androidx.compose.ui.text.style.TextAlign.Start
                                1 -> androidx.compose.ui.text.style.TextAlign.Center
                                else -> androidx.compose.ui.text.style.TextAlign.End
                            },
                        )
                    }
                }
            }
            AirScale(index)
            Row(Modifier.fillMaxWidth()) {
                Text(AirQualityLevel.GOOD.dutch, style = MaterialTheme.typography.labelSmall, color = variant, modifier = Modifier.weight(1f))
                Text(AirQualityLevel.EXTREMELY_POOR.dutch, style = MaterialTheme.typography.labelSmall, color = variant)
            }
        }
    }
}

@Composable
private fun AirScale(index: Int) {
    val ring = MaterialTheme.colorScheme.surface
    val stops = listOf(0, 20, 40, 60, 80, 100)
    Canvas(Modifier.fillMaxWidth().height(14.dp)) {
        val r = size.height / 2
        val track = 4.dp.toPx()
        val left = r
        val right = size.width - r
        drawRoundRect(
            brush = Brush.horizontalGradient(
                *stops.map { it / 100f to airColor(max(it, 1)) }.toTypedArray(),
                startX = left,
                endX = right,
            ),
            topLeft = Offset(left, r - track / 2),
            size = Size(right - left, track),
            cornerRadius = CornerRadius(track / 2, track / 2),
        )
        val x = left + (right - left) * index.coerceIn(0, 100) / 100f
        drawCircle(ring, radius = r, center = Offset(x, r))
        drawCircle(airColor(index), radius = r - 2.dp.toPx(), center = Offset(x, r))
    }
}

/** Sunrise and sunset with the sun's path, the sun where it is now, and the day's length. */
@Composable
internal fun SunCard(forecast: WeatherForecast, now: Instant, today: LocalDate, zone: ZoneId) {
    val days = forecast.daysFrom(today).filter { it.sunrise != null && it.sunset != null }
    val todays = days.firstOrNull { it.date == today }
    // After sunset, tomorrow's sun is the one that matters.
    val day = todays?.takeIf { now.isBefore(it.sunset!!) } ?: days.firstOrNull { it.date.isAfter(today) } ?: todays ?: return
    val sunrise = day.sunrise ?: return
    val sunset = day.sunset ?: return
    val isToday = day.date == today
    val progress = if (isToday && !now.isBefore(sunrise)) {
        Duration.between(sunrise, now).seconds.toFloat() / Duration.between(sunrise, sunset).seconds.coerceAtLeast(1)
    } else {
        null
    }
    val variant = MaterialTheme.colorScheme.onSurfaceVariant
    val status = when {
        !isToday -> stringResource(R.string.weather_sun_tomorrow, WeatherFormat.time(sunrise, zone))
        progress == null -> stringResource(R.string.weather_sun_rises_in, duration(Duration.between(now, sunrise)))
        else -> stringResource(R.string.weather_sun_left, duration(Duration.between(now, sunset)))
    }
    SectionCard(stringResource(R.string.weather_sun_title)) {
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Canvas(
                Modifier
                    .fillMaxWidth()
                    .height(110.dp)
                    .semantics { contentDescription = status },
            ) {
                val sunR = 7.dp.toPx()
                val horizon = size.height - 4.dp.toPx()
                val left = sunR * 2
                val right = size.width - sunR * 2
                val rx = (right - left) / 2
                val ry = horizon - sunR * 2
                val oval = Rect(left, horizon - ry, right, horizon + ry)
                drawArc(
                    color = variant.copy(alpha = 0.6f),
                    startAngle = 180f,
                    sweepAngle = 180f,
                    useCenter = false,
                    topLeft = oval.topLeft,
                    size = oval.size,
                    style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx()))),
                )
                drawLine(variant.copy(alpha = 0.35f), Offset(0f, horizon), Offset(size.width, horizon), strokeWidth = 1.dp.toPx())
                drawCircle(SUN, radius = 3.5.dp.toPx(), center = Offset(left, horizon))
                drawCircle(SUN, radius = 3.5.dp.toPx(), center = Offset(right, horizon))
                if (progress != null) {
                    val p = progress.coerceIn(0f, 1f)
                    val angle = PI * (1 - p)
                    val x = (left + rx + rx * cos(angle)).toFloat()
                    val y = (horizon - ry * sin(angle)).toFloat()
                    val travelled = Path().apply {
                        arcTo(oval, 180f, 180f * p, forceMoveTo = true)
                        lineTo(x, horizon)
                        lineTo(left, horizon)
                        close()
                    }
                    drawPath(travelled, SUN.copy(alpha = 0.18f))
                    drawArc(
                        color = SUN,
                        startAngle = 180f,
                        sweepAngle = 180f * p,
                        useCenter = false,
                        topLeft = oval.topLeft,
                        size = oval.size,
                        style = Stroke(2.dp.toPx(), cap = StrokeCap.Round),
                    )
                    drawCircle(SUN.copy(alpha = 0.3f), radius = sunR * 1.7f, center = Offset(x, y))
                    drawCircle(SUN, radius = sunR, center = Offset(x, y))
                }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                SunTime(R.drawable.ic_fact_sunrise, stringResource(R.string.weather_sunrise), WeatherFormat.time(sunrise, zone))
                Text(
                    text = stringResource(R.string.weather_sun_day_length, duration(Duration.between(sunrise, sunset))),
                    style = MaterialTheme.typography.bodySmall,
                    color = variant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
                SunTime(R.drawable.ic_fact_sunset, stringResource(R.string.weather_sunset), WeatherFormat.time(sunset, zone))
            }
            Text(status, style = MaterialTheme.typography.bodySmall, color = variant)
        }
    }
}

@Composable
private fun SunTime(icon: Int, label: String, time: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = "$label $time" },
    ) {
        androidx.compose.material3.Icon(
            painter = androidx.compose.ui.res.painterResource(icon),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(time, style = MaterialTheme.typography.titleSmall)
    }
}

/** "3u 12m" or "40m". */
private fun duration(d: Duration): String {
    val minutes = d.toMinutes().coerceAtLeast(0)
    val h = minutes / 60
    val m = minutes % 60
    return if (h > 0) "${h}u ${m}m" else "${m}m"
}

/** Tonight's moon, then the next new and full moon, whichever comes first. Worked out on the phone. */
@Composable
internal fun MoonCard(now: Instant, today: LocalDate, zone: ZoneId) {
    val phase = Moon.phase(now)
    val todayLabel = stringResource(R.string.weather_today)
    val tomorrowLabel = stringResource(R.string.weather_tomorrow)
    fun date(time: Instant) = WeatherFormat.day(time.atZone(zone).toLocalDate(), today, todayLabel, tomorrowLabel)
    SectionCard(stringResource(R.string.weather_moon_title)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.Top) {
            MoonColumn(phase.age, todayLabel, phase.name.dutch, stringResource(R.string.weather_moon_lit, (phase.illumination * 100).roundToInt()))
            listOf(
                Triple(phase.nextNewMoon, 0.0, MoonPhaseName.NEW.dutch),
                Triple(phase.nextFullMoon, 0.5, MoonPhaseName.FULL.dutch),
            ).sortedBy { it.first }.forEach { (time, age, name) ->
                MoonColumn(age, date(time), name, null)
            }
        }
    }
}

@Composable
private fun RowScope.MoonColumn(age: Double, title: String, name: String, extra: String?) {
    val variant = MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = Modifier
            .weight(1f)
            .semantics(mergeDescendants = true) { contentDescription = listOfNotNull(title, name, extra).joinToString(", ") },
    ) {
        MoonDisc(age, 52.dp, dark = variant.copy(alpha = 0.22f))
        Spacer(Modifier.height(4.dp))
        Text(title, style = MaterialTheme.typography.labelLarge)
        Text(name, style = MaterialTheme.typography.bodySmall, color = variant)
        if (extra != null) Text(extra, style = MaterialTheme.typography.bodySmall, color = variant)
    }
}

/** The moon at [age] (0 new, 0.5 full), lit from the right while waxing, as seen from the north. */
@Composable
private fun MoonDisc(age: Double, diameter: Dp, dark: Color) {
    Canvas(Modifier.size(diameter)) {
        val r = size.minDimension / 2
        val c = center
        drawCircle(dark, radius = r, center = c)
        val phase = 2 * PI * age
        val illumination = (1 - cos(phase)) / 2
        when {
            illumination < 0.01 -> Unit
            illumination > 0.99 -> drawCircle(MOONLIGHT, radius = r, center = c)
            else -> {
                val rx = (r * abs(cos(phase))).toFloat()
                val lit = Path().apply {
                    arcTo(Rect(c.x - r, c.y - r, c.x + r, c.y + r), -90f, 180f, forceMoveTo = true)
                    arcTo(Rect(c.x - rx, c.y - r, c.x + rx, c.y + r), 90f, if (illumination < 0.5) -180f else 180f, forceMoveTo = false)
                    close()
                }
                scale(scaleX = if (age < 0.5) 1f else -1f, scaleY = 1f, pivot = c) {
                    drawPath(lit, MOONLIGHT)
                }
            }
        }
    }
}

/** Speed, direction and gusts now, with a compass arrow pointing where the wind blows to. */
@Composable
internal fun WindCard(current: CurrentWeather) {
    val speed = current.windSpeedKmh ?: return
    val from = current.windDirectionDegrees
    val variant = MaterialTheme.colorScheme.onSurfaceVariant
    val arrow = MaterialTheme.colorScheme.primary
    SectionCard(stringResource(R.string.weather_wind)) {
        Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(112.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.size(80.dp)) {
                    val r = size.minDimension / 2
                    drawCircle(variant.copy(alpha = 0.45f), radius = r, style = Stroke(1.5.dp.toPx()))
                    for (i in 0 until 8) {
                        val a = Math.toRadians(i * 45.0 - 90)
                        val inner = r - 4.dp.toPx()
                        drawLine(
                            variant.copy(alpha = 0.6f),
                            Offset(center.x + (inner * cos(a)).toFloat(), center.y + (inner * sin(a)).toFloat()),
                            Offset(center.x + (r * cos(a)).toFloat(), center.y + (r * sin(a)).toFloat()),
                            strokeWidth = 1.dp.toPx(),
                        )
                    }
                }
                if (from != null) {
                    // Drawn pointing up (north), turned to where the wind blows to.
                    Canvas(Modifier.size(64.dp).rotate((from + 180).toFloat())) {
                        val cx = center.x
                        val len = size.height / 2
                        val head = len * 0.55f
                        val shaft = len * 0.12f
                        val path = Path().apply {
                            moveTo(cx, 0f)
                            lineTo(cx + head * 0.6f, head)
                            lineTo(cx + shaft, head * 0.8f)
                            lineTo(cx + shaft, size.height)
                            lineTo(cx - shaft, size.height)
                            lineTo(cx - shaft, head * 0.8f)
                            lineTo(cx - head * 0.6f, head)
                            close()
                        }
                        drawPath(path, arrow)
                    }
                }
                CompassLetter("N", Alignment.TopCenter)
                CompassLetter("O", Alignment.CenterEnd)
                CompassLetter("Z", Alignment.BottomCenter)
                CompassLetter("W", Alignment.CenterStart)
            }
            Spacer(Modifier.width(16.dp))
            Column {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("${speed.roundToInt()}", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Light)
                    Text(" km/u", style = MaterialTheme.typography.titleMedium, color = variant, modifier = Modifier.padding(bottom = 4.dp))
                }
                Text(
                    text = listOfNotNull(from?.let { DIRECTION_NAMES[Wind.direction(it)] }, "${Wind.beaufort(speed)} Bft").joinToString(" · "),
                    style = MaterialTheme.typography.titleSmall,
                )
                current.windGustsKmh?.let {
                    Text(
                        text = stringResource(R.string.weather_gusts_value, WeatherFormat.kmh(it)),
                        style = MaterialTheme.typography.bodyMedium,
                        color = variant,
                    )
                }
            }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.BoxScope.CompassLetter(letter: String, alignment: Alignment) {
    Text(
        text = letter,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.align(alignment),
    )
}

private val DIRECTION_NAMES = mapOf(
    "N" to "Noord", "NO" to "Noordoost", "O" to "Oost", "ZO" to "Zuidoost",
    "Z" to "Zuid", "ZW" to "Zuidwest", "W" to "West", "NW" to "Noordwest",
)

private val SUN = Color(0xFFFFB74D)
private val MOONLIGHT = Color(0xFFF3E9B5)

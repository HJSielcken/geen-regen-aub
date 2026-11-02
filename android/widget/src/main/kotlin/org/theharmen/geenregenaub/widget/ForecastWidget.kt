package org.theharmen.geenregenaub.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.RowScope
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.theharmen.geenregenaub.data.weather.CachedWeather
import org.theharmen.geenregenaub.data.weather.WeatherData
import org.theharmen.geenregenaub.domain.DayWeather
import org.theharmen.geenregenaub.domain.HourWeather
import org.theharmen.geenregenaub.domain.WeatherCondition
import org.theharmen.geenregenaub.domain.formatTemperature
import org.theharmen.geenregenaub.forecast.WeatherActivity
import org.theharmen.geenregenaub.forecast.WeatherFormat
import org.theharmen.geenregenaub.forecast.icon
import java.time.Instant
import java.time.ZoneId

/**
 * Home-screen weather widget: the weather now, and as the widget grows, the next hours and the
 * coming days. Renders from the offline cache only; [WidgetRefreshWorker] fetches.
 */
class ForecastWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val weather = runCatching { WeatherData.repository(context).latest() }.getOrNull()
        val state = ForecastWidgetState.of(weather, Instant.now(), ZoneId.systemDefault())
        val open = WeatherActivity.intent(context).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        provideContent {
            GlanceTheme {
                Content(state, open)
            }
        }
    }

    @Composable
    private fun Content(state: ForecastWidgetState?, open: Intent) {
        val context = LocalContext.current
        val size = LocalSize.current
        val modifier = GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.widgetBackground)
            .cornerRadius(20.dp)
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .clickable(actionStartActivity(open))

        Column(modifier = modifier) {
            if (state == null) {
                Text(
                    text = context.getString(R.string.forecast_widget_no_data),
                    style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 15.sp, fontWeight = FontWeight.Bold),
                )
                Text(
                    text = context.getString(R.string.forecast_widget_no_data_detail),
                    style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 13.sp),
                )
                return@Column
            }

            val wide = size.width >= 200.dp
            Header(state, wide)

            // Rows only appear when there is room for them.
            val hoursHeight = 78.dp
            val dayHeight = 30.dp
            var room = size.height - 20.dp - 56.dp - 18.dp
            val showHours = room >= hoursHeight && state.hours.size >= 3
            if (showHours) {
                room -= hoursHeight + 6.dp
                val count = ((size.width - 28.dp).value / 52).toInt().coerceIn(3, minOf(8, state.hours.size))
                Spacer(GlanceModifier.height(6.dp))
                Row(modifier = GlanceModifier.fillMaxWidth()) {
                    state.hours.take(count).forEachIndexed { index, hour ->
                        HourCell(hour, if (index == 0) context.getString(R.string.forecast_widget_now) else WeatherFormat.hour(hour.time, state.zone))
                    }
                }
            }
            val dayCount = (room.value / dayHeight.value).toInt().coerceIn(0, state.days.size)
            if (dayCount > 0) {
                Spacer(GlanceModifier.height(4.dp))
                state.days.take(dayCount).forEach { day -> DayRow(day, state) }
            }

            Spacer(GlanceModifier.defaultWeight())
            Text(
                text = if (wide) state.footer else "${state.placeName} · ${state.footer}",
                maxLines = 1,
                style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 11.sp),
            )
        }
    }

    @Composable
    private fun Header(state: ForecastWidgetState, wide: Boolean) {
        Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Image(
                provider = ImageProvider(state.condition.icon(state.isDay)),
                contentDescription = state.condition.dutch(state.isDay),
                modifier = GlanceModifier.size(56.dp),
            )
            Spacer(GlanceModifier.width(6.dp))
            Column(modifier = GlanceModifier.defaultWeight()) {
                Text(
                    text = formatTemperature(state.temperature),
                    style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 30.sp, fontWeight = FontWeight.Medium),
                )
                Text(
                    text = state.condition.dutch(state.isDay),
                    maxLines = 1,
                    style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 13.sp),
                )
            }
            if (wide) {
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = state.placeName,
                        maxLines = 1,
                        style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 14.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.End),
                    )
                    state.today?.let { today ->
                        Text(
                            text = "↑${formatTemperature(today.temperatureMax)} ↓${formatTemperature(today.temperatureMin)}",
                            style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 13.sp, textAlign = TextAlign.End),
                        )
                    }
                }
            }
        }
    }

    @Composable
    private fun RowScope.HourCell(hour: HourWeather, label: String) {
        Column(modifier = GlanceModifier.defaultWeight(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp, textAlign = TextAlign.Center))
            Image(
                provider = ImageProvider(hour.condition.icon(hour.isDay)),
                contentDescription = null,
                modifier = GlanceModifier.size(32.dp),
            )
            Text(
                text = formatTemperature(hour.temperature),
                style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 14.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center),
            )
            Text(
                text = hour.precipitationProbability?.takeIf { it >= 10 }?.let(WeatherFormat::percent) ?: " ",
                style = TextStyle(color = GlanceTheme.colors.primary, fontSize = 11.sp, textAlign = TextAlign.Center),
            )
        }
    }

    @Composable
    private fun DayRow(day: DayWeather, state: ForecastWidgetState) {
        val context = LocalContext.current
        val label = WeatherFormat.day(
            day.date,
            state.todayDate,
            context.getString(R.string.forecast_widget_today),
            context.getString(R.string.forecast_widget_tomorrow),
        )
        Row(modifier = GlanceModifier.fillMaxWidth().height(30.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = label,
                maxLines = 1,
                modifier = GlanceModifier.defaultWeight(),
                style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 13.sp),
            )
            Text(
                text = day.precipitationProbability?.takeIf { it >= 10 }?.let(WeatherFormat::percent) ?: "",
                modifier = GlanceModifier.width(36.dp),
                style = TextStyle(color = GlanceTheme.colors.primary, fontSize = 11.sp, textAlign = TextAlign.End),
            )
            Image(
                provider = ImageProvider(day.condition.icon()),
                contentDescription = day.condition.dutch(),
                modifier = GlanceModifier.size(26.dp).padding(horizontal = 2.dp),
            )
            Text(
                text = formatTemperature(day.temperatureMin),
                modifier = GlanceModifier.width(34.dp),
                style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 13.sp, textAlign = TextAlign.End),
            )
            Text(
                text = formatTemperature(day.temperatureMax),
                modifier = GlanceModifier.width(36.dp),
                style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 13.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.End),
            )
        }
    }

    companion object {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        /** Redraw every weather widget, e.g. after the app fetched a new forecast. */
        fun updateAllAsync(context: Context) {
            val app = context.applicationContext
            scope.launch { runCatching { ForecastWidget().updateAll(app) } }
        }
    }
}

/** Everything the weather widget shows, worked out before composing. */
internal data class ForecastWidgetState(
    val temperature: Double,
    val condition: WeatherCondition,
    val isDay: Boolean,
    val placeName: String,
    val today: DayWeather?,
    val todayDate: java.time.LocalDate,
    val hours: List<HourWeather>,
    val days: List<DayWeather>,
    val footer: String,
    val zone: ZoneId,
) {
    companion object {
        /** Null when there is no usable forecast yet. */
        fun of(weather: CachedWeather?, now: Instant, zone: ZoneId): ForecastWidgetState? {
            weather ?: return null
            val forecast = weather.forecast
            val hours = forecast.hoursFrom(now, 8)
            val todayDate = now.atZone(zone).toLocalDate()
            // A cached "current" older than two hours is worse than the forecast for this hour.
            val current = forecast.current?.takeIf { now.epochSecond - it.time.epochSecond < 2 * 3600 }
            val first = hours.firstOrNull()
            val temperature = current?.temperature ?: first?.temperature ?: return null
            return ForecastWidgetState(
                temperature = temperature,
                condition = current?.condition ?: first!!.condition,
                isDay = current?.isDay ?: first!!.isDay,
                placeName = weather.place.name.substringBefore(',').trim(),
                today = forecast.day(todayDate),
                todayDate = todayDate,
                hours = hours,
                days = forecast.daysFrom(todayDate).drop(1),
                footer = "${WeatherFormat.time(weather.fetchedAt, zone)} · Open-Meteo",
                zone = zone,
            )
        }
    }
}

class ForecastWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ForecastWidget()

    /** First widget placed: fetch right away instead of waiting for the next 15-minute run. */
    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        WidgetRefreshWorker.runNow(context)
    }
}

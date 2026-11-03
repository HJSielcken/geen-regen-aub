package org.theharmen.geenregenaub.widget

import android.content.Context
import android.content.Intent
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.theharmen.geenregenaub.data.weather.CachedWeather
import org.theharmen.geenregenaub.data.weather.WeatherData
import org.theharmen.geenregenaub.domain.DayWeather
import org.theharmen.geenregenaub.domain.HourWeather
import org.theharmen.geenregenaub.domain.WeatherCondition
import org.theharmen.geenregenaub.forecast.WeatherActivity
import org.theharmen.geenregenaub.forecast.WeatherFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** The three weather widgets (now, per hour, per day), redrawn together. */
object WeatherWidgets {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    internal fun all(): List<GlanceAppWidget> = listOf(CurrentWeatherWidget(), HourlyWeatherWidget(), DailyWeatherWidget())

    /** Redraw every weather widget, e.g. after the app fetched a new forecast. */
    fun updateAllAsync(context: Context) {
        val app = context.applicationContext
        scope.launch { updateAll(app) }
    }

    suspend fun updateAll(context: Context) {
        all().forEach { widget -> runCatching { widget.updateAll(context) } }
    }

    internal suspend fun load(context: Context): WeatherWidgetState? {
        val weather = runCatching { WeatherData.repository(context).latest() }.getOrNull()
        return WeatherWidgetState.of(weather, Instant.now(), ZoneId.systemDefault())
    }

    internal fun openIntent(context: Context): Intent =
        WeatherActivity.intent(context).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}

/** Everything the weather widgets show, worked out before composing. */
internal data class WeatherWidgetState(
    val temperature: Double,
    val feelsLike: Double?,
    val condition: WeatherCondition,
    val isDay: Boolean,
    val windSpeedKmh: Double?,
    val windDirectionDegrees: Double?,
    val windGustsKmh: Double?,
    val humidityPercent: Int?,
    val placeName: String,
    val today: DayWeather?,
    val todayDate: LocalDate,
    /** From this hour on, up to a day ahead. */
    val hours: List<HourWeather>,
    /** Today first, then the days after it. */
    val days: List<DayWeather>,
    val updatedAt: String,
    val zone: ZoneId,
) {
    val footer: String get() = "$updatedAt · Open-Meteo"

    companion object {
        /** Null when there is no usable forecast yet. */
        fun of(weather: CachedWeather?, now: Instant, zone: ZoneId): WeatherWidgetState? {
            weather ?: return null
            val forecast = weather.forecast
            val hours = forecast.hoursFrom(now, 24)
            val todayDate = now.atZone(zone).toLocalDate()
            // A cached "current" older than two hours is worse than the forecast for this hour.
            val current = forecast.current?.takeIf { now.epochSecond - it.time.epochSecond < 2 * 3600 }
            val first = hours.firstOrNull()
            val temperature = current?.temperature ?: first?.temperature ?: return null
            return WeatherWidgetState(
                temperature = temperature,
                feelsLike = current?.feelsLike,
                condition = current?.condition ?: first!!.condition,
                isDay = current?.isDay ?: first!!.isDay,
                windSpeedKmh = current?.windSpeedKmh ?: first?.windSpeedKmh,
                windDirectionDegrees = current?.windDirectionDegrees ?: first?.windDirectionDegrees,
                windGustsKmh = current?.windGustsKmh,
                humidityPercent = current?.humidityPercent,
                placeName = weather.place.name.substringBefore(',').trim(),
                today = forecast.day(todayDate),
                todayDate = todayDate,
                hours = hours,
                days = forecast.daysFrom(todayDate),
                updatedAt = WeatherFormat.time(weather.fetchedAt, zone),
                zone = zone,
            )
        }
    }
}

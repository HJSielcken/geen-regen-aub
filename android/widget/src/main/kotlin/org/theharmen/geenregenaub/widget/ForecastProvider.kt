package org.theharmen.geenregenaub.widget

import android.content.Context
import org.theharmen.geenregenaub.alerts.AlertPreferences
import org.theharmen.geenregenaub.domain.ForecastSource
import org.theharmen.geenregenaub.domain.RainForecast
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.sin

/** A forecast as the widget and the alert check see it. */
data class WidgetForecast(
    val forecast: RainForecast,
    val placeName: String?,
    val fetchedAt: Instant,
    val attribution: String,
)

/**
 * The widget's view of the data layer. [latest] reads the offline cache only, [refresh]
 * may hit the network. The app plugs in the real repository from :core:data; until that
 * exists this falls back to nothing (or demo rain, when switched on in settings).
 */
interface ForecastProvider {
    suspend fun latest(context: Context): WidgetForecast?
    suspend fun refresh(context: Context): WidgetForecast?
}

object WidgetDependencies {
    @Volatile
    var forecastProvider: ForecastProvider = NoForecastProvider

    /** The provider to use right now: demo rain when the user switched it on. */
    fun provider(context: Context): ForecastProvider =
        if (AlertPreferences(context).demoData) DemoForecastProvider else forecastProvider
}

private object NoForecastProvider : ForecastProvider {
    override suspend fun latest(context: Context): WidgetForecast? = null
    override suspend fun refresh(context: Context): WidgetForecast? = null
}

/** Made-up rain: a shower starting about half an hour from now, peaking at moderate rain. */
internal object DemoForecastProvider : ForecastProvider {
    override suspend fun latest(context: Context): WidgetForecast = demo(Instant.now())
    override suspend fun refresh(context: Context): WidgetForecast = demo(Instant.now())

    fun demo(now: Instant): WidgetForecast {
        val step = Duration.ofMinutes(5)
        val start = now.truncatedTo(ChronoUnit.MINUTES).let { it.minusSeconds(it.epochSecond % 300) }
        val values = List(25) { i ->
            val t = i - 5 // rain from step 5 to step 17
            if (t < 0 || t > 12) 0.0 else max(0.0, 4.5 * sin(PI * t / 12.0))
        }
        return WidgetForecast(
            forecast = RainForecast(start, step, values, ForecastSource.KNMI),
            placeName = "Voorbeeld",
            fetchedAt = now,
            attribution = "Voorbeeldgegevens",
        )
    }
}

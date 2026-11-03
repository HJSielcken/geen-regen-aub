package org.theharmen.geenregenaub.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.RowScope
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import org.theharmen.geenregenaub.domain.formatTemperature
import org.theharmen.geenregenaub.forecast.WeatherFormat
import org.theharmen.geenregenaub.forecast.icon

/**
 * "Weer nu": the weather right now, like the top of the weather screen. Wider adds the place and
 * today's max/min, taller adds "voelt als", and taller still the wind, humidity and sun.
 *
 * Responsive sizes: the launcher picks the largest layout that fits, so nothing is ever drawn
 * for more room than the widget has (which is what clipped and flickered before).
 */
class CurrentWeatherWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(
        setOf(
            DpSize(110.dp, 48.dp), DpSize(200.dp, 48.dp), DpSize(280.dp, 48.dp),
            DpSize(110.dp, 100.dp), DpSize(200.dp, 100.dp), DpSize(280.dp, 100.dp),
            DpSize(200.dp, 160.dp), DpSize(280.dp, 160.dp),
        ),
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val look = WidgetLook.load(context)
        val state = WeatherWidgets.load(context)
        val open = WeatherWidgets.openIntent(context)
        provideContent {
            WidgetTheme(look) { Content(state, open) }
        }
    }

    @Composable
    private fun Content(state: WeatherWidgetState?, open: Intent) {
        val size = LocalSize.current
        val short = size.height < 100.dp
        val tall = size.height >= 160.dp
        WidgetSurface(
            GlanceModifier
                .padding(horizontal = 14.dp, vertical = if (short) 6.dp else 12.dp)
                .clickable(actionStartActivity(open)),
        ) {
            if (state == null) {
                WeatherNoData()
                return@WidgetSurface
            }
            Header(state, short, tall, wide = size.width >= 200.dp)
            if (!short) Detail(state)
            if (tall) {
                Spacer(GlanceModifier.height(10.dp))
                Facts(state)
            }
        }
    }

    @Composable
    private fun Header(state: WeatherWidgetState, short: Boolean, tall: Boolean, wide: Boolean) {
        val palette = LocalWidgetPalette.current
        val description = state.condition.dutch(state.isDay)
        Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Image(
                provider = ImageProvider(state.condition.icon(state.isDay)),
                contentDescription = description,
                modifier = GlanceModifier.size(if (short) 36.dp else if (tall) 64.dp else 52.dp),
            )
            Spacer(GlanceModifier.width(8.dp))
            Column(modifier = GlanceModifier.defaultWeight()) {
                Text(
                    text = formatTemperature(state.temperature),
                    maxLines = 1,
                    style = TextStyle(
                        color = palette.text,
                        fontSize = if (short) 24.sp else if (tall) 40.sp else 28.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                )
                if (!short) {
                    Text(description, maxLines = 1, style = TextStyle(color = palette.text, fontSize = 14.sp))
                }
            }
            if (wide) {
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = state.placeName,
                        maxLines = 1,
                        style = TextStyle(color = palette.text, fontSize = 13.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.End),
                    )
                    state.today?.let { today ->
                        Text(
                            text = "↑${formatTemperature(today.temperatureMax)} ↓${formatTemperature(today.temperatureMin)}",
                            maxLines = 1,
                            style = TextStyle(color = palette.textVariant, fontSize = 12.sp, textAlign = TextAlign.End),
                        )
                    }
                }
            }
        }
    }

    /** "Voelt als 7° · max 10° · min 4°", as under the temperature in the app. */
    @Composable
    private fun Detail(state: WeatherWidgetState) {
        val context = LocalContext.current
        val parts = buildList {
            state.feelsLike?.let { add(context.getString(R.string.forecast_widget_feels_like, formatTemperature(it))) }
            state.today?.let {
                add(context.getString(R.string.forecast_widget_max_min, formatTemperature(it.temperatureMax), formatTemperature(it.temperatureMin)))
            }
        }
        if (parts.isEmpty()) return
        Text(
            text = parts.joinToString(" · "),
            maxLines = 1,
            modifier = GlanceModifier.padding(top = 2.dp),
            style = TextStyle(color = LocalWidgetPalette.current.textVariant, fontSize = 12.sp),
        )
    }

    /** Wind, humidity and sunrise/sunset, like the facts card in the app. */
    @Composable
    private fun Facts(state: WeatherWidgetState) {
        val context = LocalContext.current
        val facts = buildList {
            WeatherFormat.wind(state.windSpeedKmh, state.windDirectionDegrees)?.let {
                add(context.getString(R.string.forecast_widget_wind) to "$it Bft")
            }
            state.humidityPercent?.let { add(context.getString(R.string.forecast_widget_humidity) to WeatherFormat.percent(it)) }
            val sunrise = state.today?.sunrise
            val sunset = state.today?.sunset
            if (sunrise != null && sunset != null) {
                add(
                    context.getString(R.string.forecast_widget_sun) to
                        "${WeatherFormat.time(sunrise, state.zone)} / ${WeatherFormat.time(sunset, state.zone)}",
                )
            }
        }
        if (facts.isEmpty()) return
        Row(modifier = GlanceModifier.fillMaxWidth()) {
            facts.forEach { (label, value) -> Fact(label, value) }
        }
    }

    @Composable
    private fun RowScope.Fact(label: String, value: String) {
        val palette = LocalWidgetPalette.current
        Column(modifier = GlanceModifier.defaultWeight()) {
            Text(label, maxLines = 1, style = TextStyle(color = palette.textVariant, fontSize = 11.sp))
            Text(value, maxLines = 1, style = TextStyle(color = palette.text, fontSize = 13.sp, fontWeight = FontWeight.Medium))
        }
    }
}

/**
 * The receiver keeps its old name, so a weather widget that is already on someone's home screen
 * stays there and becomes the "Weer nu" widget.
 */
class ForecastWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = CurrentWeatherWidget()

    /** First widget placed: fetch right away instead of waiting for the next 15-minute run. */
    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        WidgetRefreshWorker.runNow(context)
    }
}

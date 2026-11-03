package org.theharmen.geenregenaub.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
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
 * today's max/min, taller adds "voelt als", and taller still the humidity. Wind and the sun have
 * widgets of their own now, so they are not repeated here.
 *
 * Responsive sizes: the launcher picks the largest layout that fits, so nothing is ever drawn
 * for more room than the widget has (which is what clipped and flickered before).
 */
class CurrentWeatherWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(
        setOf(
            DpSize(110.dp, 48.dp), DpSize(110.dp, 64.dp), DpSize(110.dp, 82.dp),
        ) + listOf(200.dp, 280.dp).flatMap { w -> HEIGHTS.map { h -> DpSize(w, h) } },
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
        // Each step adds one line, at the height where it fits (see HEIGHTS).
        val h = size.height
        val short = h < HEIGHTS[1]
        val tall = h >= HEIGHTS[5]
        val showDetail = h >= HEIGHTS[2]
        val showFacts = h >= HEIGHTS[3]
        WidgetSurface(
            GlanceModifier
                .padding(horizontal = 14.dp, vertical = 6.dp)
                .clickable(actionStartActivity(open)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (state == null) {
                WeatherNoData()
                return@WidgetSurface
            }
            Header(state, short, tall, wide = size.width >= 200.dp)
            if (showDetail) Detail(state)
            if (showFacts) {
                Spacer(GlanceModifier.height(if (tall) 10.dp else 6.dp))
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
                modifier = GlanceModifier.size(if (short) 36.dp else if (tall) 64.dp else 48.dp),
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

    /** Humidity behind its icon; wind and sun live in their own widgets. */
    @Composable
    private fun Facts(state: WeatherWidgetState) {
        val context = LocalContext.current
        val row = buildList {
            state.humidityPercent?.let {
                add(Fact(org.theharmen.geenregenaub.forecast.R.drawable.ic_fact_humidity, context.getString(R.string.forecast_widget_humidity), WeatherFormat.percent(it)))
            }
        }
        if (row.isEmpty()) return
        Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            row.forEach { FactCell(it) }
            repeat(3 - row.size) { Spacer(GlanceModifier.defaultWeight()) }
        }
    }

    private data class Fact(val icon: Int, val label: String, val value: String)

    @Composable
    private fun RowScope.FactCell(fact: Fact) {
        val palette = LocalWidgetPalette.current
        Row(modifier = GlanceModifier.defaultWeight(), verticalAlignment = Alignment.CenterVertically) {
            Image(
                provider = ImageProvider(fact.icon),
                contentDescription = fact.label,
                colorFilter = ColorFilter.tint(palette.textVariant),
                modifier = GlanceModifier.size(16.dp),
            )
            Spacer(GlanceModifier.width(4.dp))
            Text(fact.value, maxLines = 1, style = TextStyle(color = palette.text, fontSize = 12.sp, fontWeight = FontWeight.Medium))
        }
    }
}

private val HEIGHTS = listOf(
    48.dp, // temperature only
    64.dp, // + condition
    82.dp, // + "voelt als · max · min"
    106.dp, // + humidity
    130.dp, // a little more room between the lines
    160.dp, // bigger icon and temperature
)

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

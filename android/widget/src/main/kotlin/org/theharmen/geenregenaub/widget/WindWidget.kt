package org.theharmen.geenregenaub.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.toArgb
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
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.size
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import org.theharmen.geenregenaub.domain.Wind
import org.theharmen.geenregenaub.forecast.WeatherFormat
import kotlin.math.roundToInt

/**
 * "Wind": speed, direction and gusts now. Taller adds a compass with an arrow pointing where the
 * wind blows to; taller and wider still, the wind for the coming hours.
 */
class WindWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(
        WIDTHS.flatMap { w -> HEIGHTS.map { h -> DpSize(w, h) } }.toSet(),
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
        val context = LocalContext.current
        val palette = LocalWidgetPalette.current
        val size = LocalSize.current
        val showCompass = size.height >= HEIGHTS[1] && size.width >= WIDTHS[1]
        val showHours = size.height >= HEIGHTS[2] && size.width >= WIDTHS[1]
        WidgetSurface(
            GlanceModifier.padding(horizontal = 14.dp, vertical = 8.dp).clickable(actionStartActivity(open)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val speed = state?.windSpeedKmh
            if (state == null || speed == null) {
                WeatherNoData()
                return@WidgetSurface
            }
            val from = state.windDirectionDegrees
            if (showHours) WidgetTitle(context.getString(R.string.wind_widget_name), state.placeName)
            Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (showCompass) {
                    val compass = (size.height - if (showHours) 110.dp else 24.dp).coerceIn(56.dp, 110.dp)
                    Image(
                        provider = ImageProvider(
                            SkyRenderer.compass(
                                fromDegrees = from,
                                sizePx = context.px(compass),
                                density = context.density,
                                ringColor = palette.textVariant.getColor(context).toArgb(),
                                arrowColor = palette.accent.getColor(context).toArgb(),
                                labelColor = palette.textVariant.getColor(context).toArgb(),
                            ),
                        ),
                        contentDescription = from?.let { Wind.direction(it) },
                        modifier = GlanceModifier.size(compass),
                    )
                    Spacer(GlanceModifier.width(12.dp))
                }
                Column(modifier = GlanceModifier.defaultWeight()) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = if (showCompass) "${speed.roundToInt()}" else windKmh(speed, from).substringBefore(" km/u"),
                            maxLines = 1,
                            style = TextStyle(color = palette.text, fontSize = if (showCompass) 34.sp else 26.sp, fontWeight = FontWeight.Medium),
                        )
                        Text(
                            text = " km/u",
                            maxLines = 1,
                            modifier = GlanceModifier.padding(bottom = if (showCompass) 6.dp else 4.dp),
                            style = TextStyle(color = palette.textVariant, fontSize = 13.sp),
                        )
                    }
                    val direction = from?.let { DIRECTION_NAMES[Wind.direction(it)] }
                    Text(
                        text = listOfNotNull(direction, "${Wind.beaufort(speed)} Bft").joinToString(" · "),
                        maxLines = 1,
                        style = TextStyle(color = palette.text, fontSize = 13.sp, fontWeight = FontWeight.Bold),
                    )
                    state.windGustsKmh?.takeIf { size.height >= HEIGHTS[1] || size.width >= WIDTHS[1] }?.let {
                        Text(
                            text = context.getString(R.string.wind_widget_gusts, WeatherFormat.kmh(it)),
                            maxLines = 1,
                            style = TextStyle(color = palette.textVariant, fontSize = 12.sp),
                        )
                    }
                }
            }
            if (showHours && state.hours.size > 1) {
                Spacer(GlanceModifier.height(8.dp))
                val count = ((size.width - 28.dp).value / 46f).toInt().coerceIn(3, 8)
                Row(modifier = GlanceModifier.fillMaxWidth()) {
                    state.hours.drop(1).take(count).forEach { hour ->
                        Column(modifier = GlanceModifier.defaultWeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = WeatherFormat.hour(hour.time, state.zone),
                                maxLines = 1,
                                style = TextStyle(color = palette.textVariant, fontSize = 11.sp, textAlign = TextAlign.Center),
                            )
                            Text(
                                text = windKmh(hour.windSpeedKmh, hour.windDirectionDegrees).replace(" km/u", ""),
                                maxLines = 1,
                                style = TextStyle(color = palette.text, fontSize = 13.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center),
                            )
                        }
                    }
                }
            }
        }
    }

    private companion object {
        val WIDTHS = listOf(110.dp, 200.dp, 300.dp)
        val HEIGHTS = listOf(48.dp, 100.dp, 170.dp)

        val DIRECTION_NAMES = mapOf(
            "N" to "Noord", "NO" to "Noordoost", "O" to "Oost", "ZO" to "Zuidoost",
            "Z" to "Zuid", "ZW" to "Zuidwest", "W" to "West", "NW" to "Noordwest",
        )
    }
}

class WindWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = WindWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        WidgetRefreshWorker.runNow(context)
    }
}

package org.theharmen.geenregenaub.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
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
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import org.theharmen.geenregenaub.domain.HourWeather
import org.theharmen.geenregenaub.domain.formatTemperature
import org.theharmen.geenregenaub.forecast.WeatherFormat
import org.theharmen.geenregenaub.forecast.icon

/**
 * "Weer per uur": the next hours side by side, like the hour strip in the app. Wider shows more
 * hours; taller adds the chance of rain and a title.
 */
class HourlyWeatherWidget : GlanceAppWidget() {
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
        val palette = LocalWidgetPalette.current
        val context = LocalContext.current
        val size = LocalSize.current
        val level = when {
            size.height >= HEIGHTS[3] -> Level.TITLED
            size.height >= HEIGHTS[2] -> Level.FULL
            size.height >= HEIGHTS[1] -> Level.WIND
            else -> Level.SMALL
        }
        WidgetSurface(
            GlanceModifier
                .padding(horizontal = 10.dp, vertical = if (level == Level.TITLED) 10.dp else 4.dp)
                .clickable(actionStartActivity(open)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (state == null || state.hours.isEmpty()) {
                WeatherNoData()
                return@WidgetSurface
            }
            if (level == Level.TITLED) {
                Row(
                    modifier = GlanceModifier.fillMaxWidth().padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = context.getString(R.string.hourly_widget_title),
                        maxLines = 1,
                        modifier = GlanceModifier.defaultWeight(),
                        style = TextStyle(color = palette.textVariant, fontSize = 13.sp, fontWeight = FontWeight.Medium),
                    )
                    Text(
                        text = state.placeName,
                        maxLines = 1,
                        style = TextStyle(color = palette.textVariant, fontSize = 12.sp, textAlign = TextAlign.End),
                    )
                }
                Spacer(GlanceModifier.height(4.dp))
            }
            val count = cellCount(size.width).coerceAtMost(state.hours.size)
            Row(modifier = GlanceModifier.fillMaxWidth()) {
                state.hours.take(count).forEachIndexed { index, hour ->
                    val label = if (index == 0) context.getString(R.string.forecast_widget_now) else WeatherFormat.hour(hour.time, state.zone)
                    HourCell(hour, label, level)
                }
            }
        }
    }

    @Composable
    private fun RowScope.HourCell(hour: HourWeather, label: String, level: Level) {
        val palette = LocalWidgetPalette.current
        val compact = level == Level.SMALL || level == Level.WIND
        Column(modifier = GlanceModifier.defaultWeight(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = label,
                maxLines = 1,
                style = TextStyle(color = palette.textVariant, fontSize = if (compact) 11.sp else 12.sp, textAlign = TextAlign.Center),
            )
            Image(
                provider = ImageProvider(hour.condition.icon(hour.isDay)),
                contentDescription = hour.condition.dutch(hour.isDay),
                modifier = GlanceModifier.size(
                    when (level) {
                        Level.SMALL, Level.WIND -> 24.dp
                        Level.FULL -> 28.dp
                        Level.TITLED -> 36.dp
                    },
                ),
            )
            Text(
                text = formatTemperature(hour.temperature),
                maxLines = 1,
                style = TextStyle(
                    color = palette.text,
                    fontSize = if (compact) 13.sp else 15.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                ),
            )
            if (level == Level.FULL || level == Level.TITLED) {
                Text(
                    text = rainChance(hour.precipitationProbability).ifEmpty { " " },
                    maxLines = 1,
                    style = TextStyle(color = palette.accent, fontSize = 11.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center),
                )
            }
            if (level != Level.SMALL) {
                // "↗15 km/u": the arrow points where the wind blows to.
                Text(
                    text = windKmh(hour.windSpeedKmh, hour.windDirectionDegrees),
                    maxLines = 1,
                    style = TextStyle(color = palette.textVariant, fontSize = 10.sp, textAlign = TextAlign.Center),
                )
            }
        }
    }

    /** What fits: SMALL has no extras, WIND adds the wind, FULL also the rain chance, TITLED a title. */
    private enum class Level { SMALL, WIND, FULL, TITLED }

    private companion object {
        // 4 × 4 sizes: Android allows at most 16 layouts per widget.
        val WIDTHS = listOf(150.dp, 230.dp, 320.dp, 410.dp)
        val HEIGHTS = listOf(64.dp, 84.dp, 104.dp, 150.dp)

        /** About 46 dp per hour, from 3 hours on a narrow widget to 9 on a wide one. */
        fun cellCount(width: Dp): Int = ((width - 20.dp).value / 46f).toInt().coerceIn(3, 9)
    }
}

class HourlyWeatherWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = HourlyWeatherWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        WidgetRefreshWorker.runNow(context)
    }
}

package org.theharmen.geenregenaub.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.toArgb
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
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
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
import org.theharmen.geenregenaub.forecast.WeatherFormat
import java.time.Duration
import java.time.Instant

/**
 * "Zon": sunrise and sunset. Taller draws the sun's path with the sun where it is now, and the
 * length of the day. After sunset it shows tomorrow.
 */
class SunWidget : GlanceAppWidget() {
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

    /** The day to show and where the sun is in it. */
    private class SunDay(val sunrise: Instant, val sunset: Instant, val tomorrow: Boolean, val progress: Float?)

    private fun sunDay(state: WeatherWidgetState): SunDay? {
        val days = state.days.mapNotNull { day ->
            val rise = day.sunrise ?: return@mapNotNull null
            val set = day.sunset ?: return@mapNotNull null
            Triple(day.date, rise, set)
        }
        val today = days.firstOrNull { it.first == state.todayDate }
        if (today != null && state.now.isBefore(today.third)) {
            val (_, rise, set) = today
            val progress = if (state.now.isBefore(rise)) {
                null
            } else {
                Duration.between(rise, state.now).seconds.toFloat() / Duration.between(rise, set).seconds.coerceAtLeast(1)
            }
            return SunDay(rise, set, tomorrow = false, progress = progress)
        }
        val next = days.firstOrNull { it.first.isAfter(state.todayDate) } ?: today ?: return null
        return SunDay(next.second, next.third, tomorrow = next != today, progress = null)
    }

    @Composable
    private fun Content(state: WeatherWidgetState?, open: Intent) {
        val context = LocalContext.current
        val palette = LocalWidgetPalette.current
        val size = LocalSize.current
        val showArc = size.height >= HEIGHTS[1]
        val showTitle = size.height >= HEIGHTS[2]
        WidgetSurface(
            GlanceModifier.padding(horizontal = 14.dp, vertical = 8.dp).clickable(actionStartActivity(open)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val day = state?.let(::sunDay)
            if (state == null || day == null) {
                WeatherNoData()
                return@WidgetSurface
            }
            val title = context.getString(if (day.tomorrow) R.string.sun_widget_tomorrow else R.string.sun_widget_today)
            if (showTitle) WidgetTitle(context.getString(R.string.sun_widget_name), state.placeName)
            if (showArc) {
                val arcWidth = size.width - 28.dp
                val reserved = if (showTitle) 92.dp else 66.dp
                val arcHeight = (size.height - reserved).coerceIn(24.dp, (arcWidth.value / 2).dp)
                Image(
                    provider = ImageProvider(
                        SkyRenderer.sunArc(
                            progress = day.progress,
                            widthPx = context.px(arcWidth),
                            heightPx = context.px(arcHeight),
                            density = context.density,
                            lineColor = palette.textVariant.getColor(context).toArgb(),
                            sunColor = SUN,
                        ),
                    ),
                    contentDescription = null,
                    contentScale = ContentScale.FillBounds,
                    modifier = GlanceModifier.fillMaxWidth().height(arcHeight),
                )
                Spacer(GlanceModifier.height(2.dp))
            }
            Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                SunTime(R.drawable.ic_widget_sunrise, R.string.forecast_widget_sunrise, WeatherFormat.time(day.sunrise, state.zone))
                if (!showArc || size.width >= WIDTHS[1]) {
                    Text(
                        text = if (showArc) dayLength(context, day) else title,
                        maxLines = 1,
                        modifier = GlanceModifier.defaultWeight(),
                        style = TextStyle(color = palette.textVariant, fontSize = 11.sp, textAlign = TextAlign.Center),
                    )
                } else {
                    Spacer(GlanceModifier.defaultWeight())
                }
                SunTime(R.drawable.ic_widget_sunset, R.string.forecast_widget_sunset, WeatherFormat.time(day.sunset, state.zone))
            }
            if (showArc) {
                Text(
                    text = status(context, state.now, day),
                    maxLines = 1,
                    modifier = GlanceModifier.fillMaxWidth().padding(top = 2.dp),
                    style = TextStyle(color = palette.textVariant, fontSize = 11.sp, textAlign = TextAlign.Center),
                )
            }
        }
    }

    @Composable
    private fun SunTime(icon: Int, label: Int, time: String) {
        val palette = LocalWidgetPalette.current
        val context = LocalContext.current
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(
                provider = ImageProvider(icon),
                contentDescription = context.getString(label),
                colorFilter = ColorFilter.tint(palette.textVariant),
                modifier = GlanceModifier.size(16.dp),
            )
            Spacer(GlanceModifier.width(4.dp))
            Text(time, maxLines = 1, style = TextStyle(color = palette.text, fontSize = 14.sp, fontWeight = FontWeight.Medium))
        }
    }

    private fun dayLength(context: Context, day: SunDay): String =
        context.getString(R.string.sun_widget_day_length, duration(Duration.between(day.sunrise, day.sunset)))

    /** "Nog 3u 12m daglicht", "Zon op over 40m" or "Morgen". */
    private fun status(context: Context, now: Instant, day: SunDay): String = when {
        day.tomorrow -> context.getString(R.string.sun_widget_tomorrow)
        now.isBefore(day.sunrise) -> context.getString(R.string.sun_widget_rises_in, duration(Duration.between(now, day.sunrise)))
        else -> context.getString(R.string.sun_widget_daylight_left, duration(Duration.between(now, day.sunset)))
    }

    private fun duration(d: Duration): String {
        val minutes = d.toMinutes().coerceAtLeast(0)
        val h = minutes / 60
        val m = minutes % 60
        return if (h > 0) "${h}u ${m}m" else "${m}m"
    }

    private companion object {
        val WIDTHS = listOf(110.dp, 200.dp, 300.dp)
        val HEIGHTS = listOf(48.dp, 110.dp, 160.dp, 220.dp)
        const val SUN = 0xFFFFB74D.toInt()
    }
}

class SunWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = SunWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        WidgetRefreshWorker.runNow(context)
    }
}

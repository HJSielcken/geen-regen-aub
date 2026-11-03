package org.theharmen.geenregenaub.widget

import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
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
import androidx.glance.appwidget.AndroidRemoteViews
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
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
import org.theharmen.geenregenaub.domain.formatTemperature
import org.theharmen.geenregenaub.forecast.icon

/**
 * "Klok": the time and date, and when wide enough the weather now beside it, split by a thin line.
 * The time is a TextClock in plain RemoteViews, so it keeps ticking without the app waking up.
 * Tapping the time opens the alarm clock; tapping the weather opens the weather screen.
 */
class ClockWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(
        WIDTHS.flatMap { w -> HEIGHTS.map { h -> DpSize(w, h) } }.toSet(),
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val look = WidgetLook.load(context)
        val state = WeatherWidgets.load(context)
        val weather = WeatherWidgets.openIntent(context)
        val alarms = Intent(AlarmClock.ACTION_SHOW_ALARMS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        provideContent {
            WidgetTheme(look) { Content(state, weather, alarms) }
        }
    }

    @Composable
    private fun Content(state: WeatherWidgetState?, weather: Intent, alarms: Intent) {
        val palette = LocalWidgetPalette.current
        val size = LocalSize.current
        val h = size.height
        val level = when {
            h >= HEIGHTS[3] -> 3
            h >= HEIGHTS[2] -> 2
            h >= HEIGHTS[1] -> 1
            else -> 0
        }
        val wide = size.width >= WIDTHS[1]
        WidgetSurface(
            GlanceModifier.padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = GlanceModifier.clickable(actionStartActivity(alarms)),
                ) {
                    Clock(level)
                    if (level >= 2 && state != null) {
                        Text(
                            text = state.placeName,
                            maxLines = 1,
                            style = TextStyle(color = palette.text, fontSize = 13.sp, textAlign = TextAlign.Center),
                        )
                    }
                }
                if (wide && state != null) {
                    Spacer(GlanceModifier.width(16.dp))
                    Spacer(
                        GlanceModifier
                            .width(1.dp)
                            .height(if (level >= 2) 72.dp else 40.dp)
                            .background(palette.textVariant),
                    )
                    Spacer(GlanceModifier.width(16.dp))
                    Weather(state, level, weather)
                }
            }
        }
    }

    /** Time and date as a TextClock, coloured and sized for this widget. */
    @Composable
    private fun Clock(level: Int) {
        val palette = LocalWidgetPalette.current
        val context = LocalContext.current
        val views = RemoteViews(context.packageName, R.layout.clock_widget_time).apply {
            val timeSize = when (level) {
                0 -> 36f
                1 -> 40f
                2 -> 52f
                else -> 64f
            }
            setTextViewTextSize(R.id.clock_time, TypedValue.COMPLEX_UNIT_SP, timeSize)
            setTextViewTextSize(R.id.clock_date, TypedValue.COMPLEX_UNIT_SP, if (level >= 2) 14f else 12f)
            setTextColor(R.id.clock_time, palette.text.getColor(context).toArgb())
            setTextColor(R.id.clock_date, palette.text.getColor(context).toArgb())
            setViewVisibility(R.id.clock_date, if (level >= 1) View.VISIBLE else View.GONE)
        }
        AndroidRemoteViews(views)
    }

    @Composable
    private fun Weather(state: WeatherWidgetState, level: Int, open: Intent) {
        val palette = LocalWidgetPalette.current
        val big = level >= 2
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = GlanceModifier.clickable(actionStartActivity(open)),
        ) {
            Image(
                provider = ImageProvider(state.condition.icon(state.isDay)),
                contentDescription = state.condition.dutch(state.isDay),
                modifier = GlanceModifier.size(if (big) 44.dp else 28.dp),
            )
            Text(
                text = formatTemperature(state.temperature),
                maxLines = 1,
                style = TextStyle(color = palette.text, fontSize = if (big) 26.sp else 18.sp, fontWeight = FontWeight.Medium),
            )
            if (big) {
                state.today?.let { today ->
                    Text(
                        text = "${formatTemperature(today.temperatureMax)}/${formatTemperature(today.temperatureMin)}",
                        maxLines = 1,
                        style = TextStyle(color = palette.textVariant, fontSize = 12.sp),
                    )
                }
            }
        }
    }

    private companion object {
        val WIDTHS = listOf(150.dp, 250.dp, 330.dp)

        /** 0: time only; 1: + date; 2: bigger, + place and max/min; 3: biggest. */
        val HEIGHTS = listOf(56.dp, 80.dp, 110.dp, 140.dp)
    }
}

class ClockWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ClockWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        WidgetRefreshWorker.runNow(context)
    }
}

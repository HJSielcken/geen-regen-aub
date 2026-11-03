package org.theharmen.geenregenaub.widget

import android.content.Context
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.AlarmClock
import android.util.TypedValue
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
 * "Klok": the time, date and place, with the weather now beside it behind a thin line. The
 * content is the same at every size; a bigger widget only makes everything bigger.
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
        val alarms = AlarmClockIntent.pendingIntent(context)
        provideContent {
            WidgetTheme(look) { Content(state, weather, alarms) }
        }
    }

    @Composable
    private fun Content(state: WeatherWidgetState?, weather: Intent, alarms: PendingIntent?) {
        val palette = LocalWidgetPalette.current
        val size = LocalSize.current
        // Designed for BASE; scaled to fit the room there is, never re-arranged.
        val scale = minOf(size.width / BASE.width, size.height / BASE.height).coerceIn(0.45f, 2f)
        fun Float.s() = (this * scale).sp
        fun Int.d() = (this * scale).dp
        WidgetSurface(
            GlanceModifier.padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Clock(scale, alarms)
                    if (state != null) {
                        Text(
                            text = state.placeName,
                            maxLines = 1,
                            style = TextStyle(color = palette.text, fontSize = 13f.s(), textAlign = TextAlign.Center),
                        )
                    }
                }
                if (state != null) {
                    Spacer(GlanceModifier.width(16.d()))
                    Spacer(GlanceModifier.width(1.dp).height(72.d()).background(palette.textVariant))
                    Spacer(GlanceModifier.width(16.d()))
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = GlanceModifier.clickable(actionStartActivity(weather)),
                    ) {
                        Image(
                            provider = ImageProvider(state.condition.icon(state.isDay)),
                            contentDescription = state.condition.dutch(state.isDay),
                            modifier = GlanceModifier.size(44.d()),
                        )
                        Text(
                            text = formatTemperature(state.temperature),
                            maxLines = 1,
                            style = TextStyle(color = palette.text, fontSize = 26f.s(), fontWeight = FontWeight.Medium),
                        )
                        state.today?.let { today ->
                            Text(
                                text = "${formatTemperature(today.temperatureMax)}/${formatTemperature(today.temperatureMin)}",
                                maxLines = 1,
                                style = TextStyle(color = palette.textVariant, fontSize = 12f.s()),
                            )
                        }
                    }
                }
            }
        }
    }

    /** Time and date as a TextClock, coloured and sized for this widget; tapping opens the alarms. */
    @Composable
    private fun Clock(scale: Float, alarms: PendingIntent?) {
        val palette = LocalWidgetPalette.current
        val context = LocalContext.current
        val colour = palette.text.getColor(context).toArgb()
        val views = RemoteViews(context.packageName, R.layout.clock_widget_time).apply {
            setTextViewTextSize(R.id.clock_time, TypedValue.COMPLEX_UNIT_SP, 52f * scale)
            setTextViewTextSize(R.id.clock_date, TypedValue.COMPLEX_UNIT_SP, 14f * scale)
            setTextColor(R.id.clock_time, colour)
            setTextColor(R.id.clock_date, colour)
            // On the RemoteViews themselves: a Glance click around them does not reach every launcher.
            if (alarms != null) setOnClickPendingIntent(R.id.clock_root, alarms)
        }
        AndroidRemoteViews(views)
    }

    private companion object {
        /** The size the layout is designed for; everything scales from here. */
        val BASE = DpSize(300.dp, 120.dp)
        val WIDTHS = listOf(160.dp, 220.dp, 280.dp, 340.dp)
        val HEIGHTS = listOf(56.dp, 90.dp, 120.dp, 160.dp)
    }
}

/** Opens the phone's alarm clock app, whichever app that is. */
internal object AlarmClockIntent {
    private val KNOWN_CLOCKS = listOf(
        "com.sec.android.app.clockpackage", // Samsung
        "com.google.android.deskclock", // Google
        "com.android.deskclock", // AOSP and many others
        "com.oneplus.deskclock",
        "com.coloros.alarmclock",
        "com.miui.clock",
    )

    fun pendingIntent(context: Context): PendingIntent? {
        val intent = resolve(context) ?: return null
        return PendingIntent.getActivity(
            context,
            REQUEST_CODE,
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    /** An explicit intent: implicit ones in a widget's PendingIntent are not handled everywhere. */
    private fun resolve(context: Context): Intent? {
        val pm = context.packageManager
        val show = Intent(AlarmClock.ACTION_SHOW_ALARMS)
        pm.resolveActivity(show, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.let { info ->
            if (info.packageName != "android") return show.setClassName(info.packageName, info.name)
        }
        return KNOWN_CLOCKS.firstNotNullOfOrNull { pm.getLaunchIntentForPackage(it) }
    }

    private const val REQUEST_CODE = 4217
}

class ClockWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ClockWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        WidgetRefreshWorker.runNow(context)
    }
}

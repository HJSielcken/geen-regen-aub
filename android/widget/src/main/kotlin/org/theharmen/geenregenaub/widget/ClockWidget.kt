package org.theharmen.geenregenaub.widget

import android.app.Activity
import android.app.PendingIntent
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.AlarmClock
import android.view.View
import android.widget.RemoteViews
import androidx.compose.ui.graphics.toArgb
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.appwidget.AndroidRemoteViews
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.fillMaxSize
import org.theharmen.geenregenaub.domain.formatTemperature
import org.theharmen.geenregenaub.forecast.icon

/**
 * "Klok": the time, date and place, with the weather now beside it behind a thin line.
 * The whole widget is one RemoteViews layout in which every text auto-sizes to a fixed share of
 * the widget, so the content is the same at every size and only grows or shrinks. That does not
 * depend on the size the launcher reports, which some launchers (Nova) get wrong.
 * Tapping the time opens the alarm clock; tapping the weather opens the weather screen.
 */
class ClockWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Single

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val look = WidgetLook.load(context)
        val state = WeatherWidgets.load(context)
        val weather = PendingIntent.getActivity(
            context,
            WEATHER_REQUEST_CODE,
            WeatherWidgets.openIntent(context),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val alarms = AlarmClockIntent.pendingIntent(context)
        provideContent {
            WidgetTheme(look) {
                val palette = LocalWidgetPalette.current
                val text = palette.text.getColor(context).toArgb()
                val variant = palette.textVariant.getColor(context).toArgb()
                WidgetSurface(GlanceModifier) {
                    AndroidRemoteViews(
                        views(context, state, text, variant, alarms, weather),
                        modifier = GlanceModifier.fillMaxSize(),
                    )
                }
            }
        }
    }

    private fun views(
        context: Context,
        state: WeatherWidgetState?,
        text: Int,
        variant: Int,
        alarms: PendingIntent,
        weather: PendingIntent,
    ) = RemoteViews(context.packageName, R.layout.clock_widget).apply {
        setTextColor(R.id.clock_time, text)
        setTextColor(R.id.clock_date, text)
        setTextColor(R.id.clock_place, text)
        setOnClickPendingIntent(R.id.clock_root, alarms)
        setOnClickPendingIntent(R.id.clock_time, alarms)
        setOnClickPendingIntent(R.id.clock_date, alarms)
        if (state == null) {
            setViewVisibility(R.id.clock_place, View.GONE)
            setViewVisibility(R.id.clock_divider, View.GONE)
            setViewVisibility(R.id.clock_weather, View.GONE)
            return@apply
        }
        setTextViewText(R.id.clock_place, state.placeName)
        setInt(R.id.clock_divider, "setBackgroundColor", variant)
        setImageViewResource(R.id.clock_icon, state.condition.icon(state.isDay))
        setContentDescription(R.id.clock_icon, state.condition.dutch(state.isDay))
        setTextViewText(R.id.clock_temperature, formatTemperature(state.temperature))
        setTextColor(R.id.clock_temperature, text)
        val today = state.today
        if (today != null) {
            setTextViewText(
                R.id.clock_range,
                "${formatTemperature(today.temperatureMax)}/${formatTemperature(today.temperatureMin)}",
            )
            setTextColor(R.id.clock_range, variant)
        } else {
            setViewVisibility(R.id.clock_range, View.GONE)
        }
        setOnClickPendingIntent(R.id.clock_weather, weather)
    }

    private companion object {
        const val WEATHER_REQUEST_CODE = 4218
    }
}

/**
 * Opens the phone's alarm clock app, whichever app that is.
 * The widget's tap always goes to [ClockLauncherActivity] in this app, which is certain to
 * start; that then tries the ways to open a clock one by one until one works. Working it out
 * at tap time, with a fallback for each failure, is what makes it work across phone makers.
 */
internal object AlarmClockIntent {
    private val KNOWN_CLOCKS = listOf(
        "com.sec.android.app.clockpackage", // Samsung
        "com.google.android.deskclock", // Google
        "com.android.deskclock", // AOSP and many others
        "com.oneplus.deskclock",
        "com.coloros.alarmclock", // Oppo, Realme
        "com.oplus.alarmclock",
        "com.miui.clock", // Xiaomi
        "com.huawei.deskclock",
        "com.hihonor.deskclock",
        "com.motorola.blur.alarmclock",
        "com.lge.clock",
        "com.sonyericsson.organizer",
        "com.asus.deskclock",
        "com.vivo.alarmclock",
        "com.bbk.alarmclock",
        "com.nothing.deskclock",
    )

    fun pendingIntent(context: Context): PendingIntent = PendingIntent.getActivity(
        context,
        REQUEST_CODE,
        Intent(context, ClockLauncherActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    /** Everything worth trying, best first: the alarms screen, then a clock app's start screen. */
    fun candidates(context: Context): List<Intent> {
        val pm = context.packageManager
        val list = mutableListOf<Intent>()
        val show = Intent(AlarmClock.ACTION_SHOW_ALARMS)
        pm.resolveActivity(show, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.let { info ->
            // "android" is the chooser, not a clock.
            if (info.packageName != "android") list += Intent(show).setClassName(info.packageName, info.name)
        }
        list += show
        KNOWN_CLOCKS.mapNotNullTo(list) { pm.getLaunchIntentForPackage(it) }
        // Any other installed app that calls itself a clock.
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        pm.queryIntentActivities(launcher, 0)
            .map { it.activityInfo }
            .filter { it.packageName != context.packageName }
            .filter { info -> CLOCK_WORDS.any { info.packageName.contains(it, ignoreCase = true) } }
            .mapTo(list) { info -> Intent(launcher).setClassName(info.packageName, info.name) }
        return list
    }

    private val CLOCK_WORDS = listOf("deskclock", "alarmclock", "clockpackage", ".clock")
    private const val REQUEST_CODE = 4217
}

/** Invisible stop between the widget and the clock app; see [AlarmClockIntent]. */
class ClockLauncherActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        for (intent in AlarmClockIntent.candidates(this)) {
            try {
                startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                break
            } catch (e: ActivityNotFoundException) {
                continue // not there after all: try the next one
            } catch (e: SecurityException) {
                continue // not exported to other apps: try the next one
            }
        }
        finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }
}

class ClockWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ClockWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        WidgetRefreshWorker.runNow(context)
    }
}

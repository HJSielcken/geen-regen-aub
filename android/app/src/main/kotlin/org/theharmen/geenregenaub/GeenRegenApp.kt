package org.theharmen.geenregenaub

import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.os.Build
import android.os.Process
import android.util.Log
import io.sentry.android.core.SentryAndroid
import org.theharmen.geenregenaub.alerts.AlertsHooks
import org.theharmen.geenregenaub.alerts.RainAlertNotifier
import org.theharmen.geenregenaub.forecast.WeatherHooks
import org.theharmen.geenregenaub.widget.ForecastWidget
import org.theharmen.geenregenaub.widget.RainWidget
import org.theharmen.geenregenaub.widget.WidgetRefreshWorker

class GeenRegenApp : Application() {
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        // As early as possible: this runs before the content providers and onCreate.
        CrashLog.install(this)
    }

    override fun onCreate() {
        super.onCreate()
        CrashLog.recordPreviousExit(this)
        // The crash report screen runs in its own process; keep that one bare, so it still opens
        // when something below is what crashes.
        if (isCrashReportProcess()) return
        startCrashReporting()
        RainAlertNotifier.createChannel(this)
        AlertsHooks.onSettingsChanged = { context -> WidgetRefreshWorker.runNow(context) }
        AlertsHooks.onWidgetLookChanged = { context ->
            RainWidget.updateAllAsync(context)
            ForecastWidget.updateAllAsync(context)
        }
        WeatherHooks.onWeatherUpdated = { context -> ForecastWidget.updateAllAsync(context) }
        WidgetRefreshWorker.schedule(this)
    }

    private fun isCrashReportProcess(): Boolean {
        val name = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            getProcessName()
        } else {
            getSystemService(ActivityManager::class.java)?.runningAppProcesses
                ?.firstOrNull { it.pid == Process.myPid() }?.processName
        }
        return name?.endsWith(":crashreport") == true
    }

    /** Sentry, only when the build has a DSN (see app/build.gradle.kts). No personal data is sent. */
    private fun startCrashReporting() {
        if (BuildConfig.SENTRY_DSN.isBlank()) return
        try {
            SentryAndroid.init(this) { options ->
                options.dsn = BuildConfig.SENTRY_DSN
                options.environment = if (BuildConfig.DEBUG) "debug" else "release"
                options.release = "${BuildConfig.APPLICATION_ID}@${BuildConfig.VERSION_NAME}+${BuildConfig.VERSION_CODE}"
                options.isSendDefaultPii = false
                // Native crashes (MapLibre draws the map in C++) never reach the Java crash handler.
                // Android keeps a tombstone of them; Sentry reads it on the next start and reports it,
                // including the ones from before this version was installed.
                options.isTombstoneEnabled = true
                options.isReportHistoricalTombstones = true
            }
        } catch (e: Throwable) {
            // Crash reporting must never be the reason the app does not start.
            Log.e("GeenRegenApp", "Sentry did not start", e)
        }
    }
}

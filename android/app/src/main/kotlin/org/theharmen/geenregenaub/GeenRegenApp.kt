package org.theharmen.geenregenaub

import android.app.Application
import io.sentry.android.core.SentryAndroid
import org.theharmen.geenregenaub.alerts.AlertsHooks
import org.theharmen.geenregenaub.alerts.RainAlertNotifier
import org.theharmen.geenregenaub.forecast.WeatherHooks
import org.theharmen.geenregenaub.widget.ForecastWidget
import org.theharmen.geenregenaub.widget.WidgetRefreshWorker

class GeenRegenApp : Application() {
    override fun onCreate() {
        super.onCreate()
        startCrashReporting()
        RainAlertNotifier.createChannel(this)
        AlertsHooks.onSettingsChanged = { context -> WidgetRefreshWorker.runNow(context) }
        WeatherHooks.onWeatherUpdated = { context -> ForecastWidget.updateAllAsync(context) }
        WidgetRefreshWorker.schedule(this)
    }

    /** Sentry, only when the build has a DSN (see app/build.gradle.kts). No personal data is sent. */
    private fun startCrashReporting() {
        if (BuildConfig.SENTRY_DSN.isBlank()) return
        SentryAndroid.init(this) { options ->
            options.dsn = BuildConfig.SENTRY_DSN
            options.environment = if (BuildConfig.DEBUG) "debug" else "release"
            options.release = "${BuildConfig.APPLICATION_ID}@${BuildConfig.VERSION_NAME}+${BuildConfig.VERSION_CODE}"
            options.isSendDefaultPii = false
        }
    }
}

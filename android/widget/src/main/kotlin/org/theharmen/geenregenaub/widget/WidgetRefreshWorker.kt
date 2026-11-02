package org.theharmen.geenregenaub.widget

import android.content.Context
import androidx.glance.appwidget.updateAll
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import org.theharmen.geenregenaub.alerts.AlertPreferences
import org.theharmen.geenregenaub.alerts.RainAlertNotifier
import org.theharmen.geenregenaub.data.CachedForecast
import org.theharmen.geenregenaub.data.GeenRegenData
import org.theharmen.geenregenaub.data.RefreshResult
import org.theharmen.geenregenaub.data.weather.WeatherData
import org.theharmen.geenregenaub.domain.AlertDecision
import org.theharmen.geenregenaub.domain.RainAlertPolicy
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/**
 * The 15-minute background job: fetch the forecast into the cache, redraw the widgets, then
 * decide whether to send a "rain coming" notification.
 */
class WidgetRefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val context = applicationContext
        val repository = GeenRegenData.forecastRepository(context)
        val data = when (val result = runCatching { repository.refresh() }.getOrNull()) {
            is RefreshResult.Success -> result.forecast
            is RefreshResult.Failure -> result.cached
            null -> runCatching { repository.latest() }.getOrNull()
        }

        RainWidget().updateAll(context)

        // The weather forecast follows the place the rain fetch just resolved; it only fetches
        // when its cache is older than half an hour, so most runs cost nothing.
        runCatching { WeatherData.repository(context).refresh() }
        runCatching { ForecastWidget().updateAll(context) }

        // Sample data (a build without a relay key) never triggers a real-looking alert.
        if (data != null && !data.isDemo) checkAlert(context, data)
        return Result.success()
    }

    private fun checkAlert(context: Context, data: CachedForecast) {
        val preferences = AlertPreferences(context)
        val decision = RainAlertPolicy.decide(
            forecast = data.forecast,
            settings = preferences.settings,
            lastAnnouncedStart = preferences.lastAnnouncedStart,
            now = Instant.now(),
            zone = ZoneId.systemDefault(),
        )
        when (decision) {
            is AlertDecision.Stay -> preferences.lastAnnouncedStart = decision.rememberedStart
            is AlertDecision.Notify -> {
                // Only remember the shower once the user could actually be told about it.
                if (RainAlertNotifier.canNotify(context)) {
                    RainAlertNotifier.notifyRain(context, decision.rain, decision.minutesUntil)
                    preferences.lastAnnouncedStart = decision.rememberedStart
                }
            }
        }
    }

    companion object {
        private const val UNIQUE_NAME = "rain-refresh"
        private const val UNIQUE_NOW_NAME = "rain-refresh-now"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<WidgetRefreshWorker>(15, TimeUnit.MINUTES)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build(),
                )
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(UNIQUE_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
        }

        /** Run once now, e.g. after a settings change or when a widget is added. */
        fun runNow(context: Context) {
            val request = OneTimeWorkRequestBuilder<WidgetRefreshWorker>().build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork(UNIQUE_NOW_NAME, ExistingWorkPolicy.REPLACE, request)
        }
    }
}

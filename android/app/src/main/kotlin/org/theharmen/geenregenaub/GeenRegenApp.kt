package org.theharmen.geenregenaub

import android.app.Application
import org.theharmen.geenregenaub.alerts.AlertsHooks
import org.theharmen.geenregenaub.alerts.RainAlertNotifier
import org.theharmen.geenregenaub.forecast.WeatherHooks
import org.theharmen.geenregenaub.widget.ForecastWidget
import org.theharmen.geenregenaub.widget.WidgetRefreshWorker

class GeenRegenApp : Application() {
    override fun onCreate() {
        super.onCreate()
        RainAlertNotifier.createChannel(this)
        AlertsHooks.onSettingsChanged = { context -> WidgetRefreshWorker.runNow(context) }
        WeatherHooks.onWeatherUpdated = { context -> ForecastWidget.updateAllAsync(context) }
        WidgetRefreshWorker.schedule(this)
    }
}

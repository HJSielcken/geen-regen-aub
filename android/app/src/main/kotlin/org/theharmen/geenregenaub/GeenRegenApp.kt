package org.theharmen.geenregenaub

import android.app.Application
import org.theharmen.geenregenaub.widget.WidgetRefreshWorker

class GeenRegenApp : Application() {
    override fun onCreate() {
        super.onCreate()
        WidgetRefreshWorker.schedule(this)
    }
}

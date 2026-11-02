package org.theharmen.geenregenaub.alerts

import android.content.Context
import androidx.core.content.edit
import org.theharmen.geenregenaub.domain.AlertSettings
import org.theharmen.geenregenaub.domain.Intensity
import java.time.Duration
import java.time.Instant

/**
 * Alert settings and the background check's memory, in plain SharedPreferences so the
 * worker, the widget and the settings screen all read the same values without extra plumbing.
 */
class AlertPreferences(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    var settings: AlertSettings
        get() {
            val defaults = AlertSettings()
            return AlertSettings(
                enabled = prefs.getBoolean(KEY_ENABLED, defaults.enabled),
                leadTime = Duration.ofMinutes(prefs.getLong(KEY_LEAD_MINUTES, defaults.leadTime.toMinutes())),
                minIntensity = prefs.getString(KEY_MIN_INTENSITY, null)
                    ?.let { name -> Intensity.entries.firstOrNull { it.name == name } }
                    ?: defaults.minIntensity,
                quietHours = prefs.getBoolean(KEY_QUIET_HOURS, defaults.quietHours),
            )
        }
        set(value) = prefs.edit {
            putBoolean(KEY_ENABLED, value.enabled)
            putLong(KEY_LEAD_MINUTES, value.leadTime.toMinutes())
            putString(KEY_MIN_INTENSITY, value.minIntensity.name)
            putBoolean(KEY_QUIET_HOURS, value.quietHours)
        }

    /** Start time of the last shower we announced, so it is not announced twice. */
    var lastAnnouncedStart: Instant?
        get() = prefs.getLong(KEY_LAST_START, 0L).takeIf { it > 0L }?.let(Instant::ofEpochMilli)
        set(value) = prefs.edit {
            if (value == null) remove(KEY_LAST_START) else putLong(KEY_LAST_START, value.toEpochMilli())
        }

    /** Show made-up rain in the widget, to try the layout before real data arrives. */
    var demoData: Boolean
        get() = prefs.getBoolean(KEY_DEMO, false)
        set(value) = prefs.edit { putBoolean(KEY_DEMO, value) }

    private companion object {
        const val FILE = "rain_alerts"
        const val KEY_ENABLED = "enabled"
        const val KEY_LEAD_MINUTES = "lead_minutes"
        const val KEY_MIN_INTENSITY = "min_intensity"
        const val KEY_QUIET_HOURS = "quiet_hours"
        const val KEY_LAST_START = "last_announced_start"
        const val KEY_DEMO = "demo_data"
    }
}

package org.theharmen.geenregenaub.alerts

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableIntStateOf
import org.theharmen.geenregenaub.ui.theme.GeenRegenTheme

/**
 * Settings for rain alerts and the widget. Reachable from the launcher shortcut, the gear in
 * Android's app info page, and from inside the app via [intent].
 */
class SettingsActivity : ComponentActivity() {
    private val resumeCount = mutableIntStateOf(0)
    private val preferences by lazy { AlertPreferences(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            GeenRegenTheme {
                SettingsScreen(
                    preferences = preferences,
                    resumeCount = resumeCount.intValue,
                    onChanged = { AlertsHooks.onSettingsChanged(applicationContext) },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        resumeCount.intValue++
    }

    companion object {
        fun intent(context: Context): Intent = Intent(context, SettingsActivity::class.java)
    }
}

/**
 * Lets the app react to settings changes (redraw the widget, re-run the check) without this
 * module depending on the widget. Set once in the Application.
 */
object AlertsHooks {
    @Volatile
    var onSettingsChanged: (Context) -> Unit = {}
}

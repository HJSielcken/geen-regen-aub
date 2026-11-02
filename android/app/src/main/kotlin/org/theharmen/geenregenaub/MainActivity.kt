package org.theharmen.geenregenaub

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import org.theharmen.geenregenaub.alerts.SettingsActivity
import org.theharmen.geenregenaub.home.HomeScreen
import org.theharmen.geenregenaub.ui.theme.GeenRegenTheme
import org.theharmen.geenregenaub.ui.theme.SystemBarsFollowTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SystemBarsFollowTheme(this)
            GeenRegenTheme {
                HomeScreen(onOpenSettings = { startActivity(SettingsActivity.intent(this)) })
            }
        }
    }
}

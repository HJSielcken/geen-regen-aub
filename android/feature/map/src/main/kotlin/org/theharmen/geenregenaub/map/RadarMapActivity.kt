package org.theharmen.geenregenaub.map

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import org.theharmen.geenregenaub.ui.theme.GeenRegenTheme
import org.theharmen.geenregenaub.ui.theme.SystemBarsFollowTheme

/** The animated radar map. Opened from the map button on the home screen. */
class RadarMapActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SystemBarsFollowTheme(this)
            GeenRegenTheme {
                RadarMapScreen(onBack = ::finish)
            }
        }
    }

    companion object {
        fun intent(context: Context): Intent = Intent(context, RadarMapActivity::class.java)
    }
}

package org.theharmen.geenregenaub

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import org.theharmen.geenregenaub.alerts.SettingsActivity
import androidx.lifecycle.viewmodel.compose.viewModel
import org.theharmen.geenregenaub.forecast.WeatherSections
import org.theharmen.geenregenaub.forecast.WeatherViewModel
import org.theharmen.geenregenaub.home.HomeScreen
import org.theharmen.geenregenaub.map.RadarMapActivity
import org.theharmen.geenregenaub.map.RadarPreview
import org.theharmen.geenregenaub.ui.theme.GeenRegenTheme
import org.theharmen.geenregenaub.ui.theme.SystemBarsFollowTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SystemBarsFollowTheme(this)
            GeenRegenTheme {
                // One scrolling home screen: rain, a radar preview, then the full weather forecast.
                val weather: WeatherViewModel = viewModel()
                HomeScreen(
                    onOpenSettings = { startActivity(SettingsActivity.intent(this)) },
                    onRefreshMore = weather::refresh,
                    bottomContent = { WeatherSections(viewModel = weather) },
                    onOpenMap = { startActivity(RadarMapActivity.intent(this)) },
                    radarContent = { RadarPreview(onOpen = { startActivity(RadarMapActivity.intent(this)) }) },
                )
            }
        }
    }
}

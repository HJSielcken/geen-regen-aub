package org.theharmen.geenregenaub.forecast

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import org.theharmen.geenregenaub.ui.theme.GeenRegenTheme

/** The full weather forecast. Opened from the home screen card and from the weather widget. */
class WeatherActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            GeenRegenTheme {
                WeatherScreen(onBack = ::finish)
            }
        }
    }

    companion object {
        fun intent(context: Context): Intent = Intent(context, WeatherActivity::class.java)
    }
}

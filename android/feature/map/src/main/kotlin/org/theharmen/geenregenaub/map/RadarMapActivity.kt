package org.theharmen.geenregenaub.map

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import org.theharmen.geenregenaub.ui.theme.GeenRegenTheme
import org.theharmen.geenregenaub.ui.theme.SystemBarsFollowTheme

/**
 * The animated radar map. Opened from the map button on the home screen, or from "Kies op de
 * kaart" in the place sheet to pick a place.
 */
class RadarMapActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SystemBarsFollowTheme(this)
            GeenRegenTheme {
                RadarMapScreen(
                    onBack = ::finish,
                    pickPlace = intent.getBooleanExtra(EXTRA_PICK_PLACE, false),
                    onPlaceChosen = ::finish,
                )
            }
        }
    }

    companion object {
        private const val EXTRA_PICK_PLACE = "pick_place"

        fun intent(context: Context, pickPlace: Boolean = false): Intent =
            Intent(context, RadarMapActivity::class.java).putExtra(EXTRA_PICK_PLACE, pickPlace)
    }
}

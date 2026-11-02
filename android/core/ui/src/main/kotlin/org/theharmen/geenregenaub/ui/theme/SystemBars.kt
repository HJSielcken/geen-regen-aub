package org.theharmen.geenregenaub.ui.theme

import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect

/**
 * Keeps the status and navigation bar icons readable when the app's own light/dark choice
 * differs from the phone's.
 */
@Composable
fun SystemBarsFollowTheme(activity: ComponentActivity) {
    val dark = isAppInDarkTheme()
    LaunchedEffect(dark) {
        activity.enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark },
            navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark },
        )
    }
}

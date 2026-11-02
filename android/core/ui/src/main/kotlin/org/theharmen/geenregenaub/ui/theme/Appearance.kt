package org.theharmen.geenregenaub.ui.theme

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

/** Licht, donker, or follow the phone. */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** How the app looks, chosen in Instellingen. */
data class Appearance(
    val mode: ThemeMode = ThemeMode.SYSTEM,
    /** Material You: take the colours from the wallpaper (Android 12+). Off uses the app's own blue. */
    val dynamicColor: Boolean = true,
)

/** Stores [Appearance] in SharedPreferences; every screen picks up a change right away. */
class AppearancePreferences(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    var appearance: Appearance
        get() = Appearance(
            mode = prefs.getString(KEY_MODE, null)
                ?.let { name -> ThemeMode.entries.firstOrNull { it.name == name } }
                ?: ThemeMode.SYSTEM,
            dynamicColor = prefs.getBoolean(KEY_DYNAMIC, true),
        )
        set(value) {
            prefs.edit()
                .putString(KEY_MODE, value.mode.name)
                .putBoolean(KEY_DYNAMIC, value.dynamicColor)
                .apply()
        }

    fun register(listener: SharedPreferences.OnSharedPreferenceChangeListener) =
        prefs.registerOnSharedPreferenceChangeListener(listener)

    fun unregister(listener: SharedPreferences.OnSharedPreferenceChangeListener) =
        prefs.unregisterOnSharedPreferenceChangeListener(listener)

    companion object {
        private const val FILE = "appearance"
        private const val KEY_MODE = "theme_mode"
        private const val KEY_DYNAMIC = "dynamic_color"

        /** Wallpaper colours exist from Android 12. */
        val supportsDynamicColor: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    }
}

/** The saved [Appearance], updated live while the screen is shown. */
@Composable
fun rememberAppearance(): Appearance {
    val context = LocalContext.current
    val prefs = remember(context) { AppearancePreferences(context) }
    var value by remember(prefs) { mutableStateOf(prefs.appearance) }
    DisposableEffect(prefs) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> value = prefs.appearance }
        prefs.register(listener)
        onDispose { prefs.unregister(listener) }
    }
    return value
}

/** True when the app should draw dark, after applying the user's choice. */
@Composable
fun isAppInDarkTheme(appearance: Appearance = rememberAppearance()): Boolean = when (appearance.mode) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

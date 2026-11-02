package org.theharmen.geenregenaub.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

/** Geen regen aub's own accent: a clear "dry sky" blue. Rain intensity scales from it. */
object GeenRegenColors {
    val Sky = Color(0xFF1F6FEB)
    val SkyLight = Color(0xFFA9C7FF)
    val Dusk = Color(0xFF0B1D3A)

    val RainLight = Color(0xFF8DB8F5)
    val RainModerate = Color(0xFF3D7FE0)
    val RainHeavy = Color(0xFF16408F)
}

private val LightColors = lightColorScheme(
    primary = GeenRegenColors.Sky,
    onPrimary = Color.White,
    secondary = GeenRegenColors.RainModerate,
)

private val DarkColors = darkColorScheme(
    primary = GeenRegenColors.SkyLight,
    onPrimary = GeenRegenColors.Dusk,
    secondary = GeenRegenColors.RainLight,
)

@Composable
fun GeenRegenTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colorScheme, content = content)
}

package org.theharmen.geenregenaub.widget

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.glance.GlanceTheme
import androidx.glance.unit.ColorProvider
import org.theharmen.geenregenaub.alerts.WidgetBackground

/**
 * The few colours the widgets use. With a see-through background the theme's text colours
 * (dark on light) would vanish on most wallpapers, so those modes use white text and a light
 * blue accent instead, like the launcher's own labels.
 */
internal class WidgetPalette(
    val background: ColorProvider,
    val text: ColorProvider,
    val textVariant: ColorProvider,
    val accent: ColorProvider,
)

internal val LocalWidgetPalette = staticCompositionLocalOf<WidgetPalette> {
    error("WidgetTheme missing")
}

@Composable
internal fun WidgetTheme(background: WidgetBackground, content: @Composable () -> Unit) {
    GlanceTheme {
        val palette = when (background) {
            WidgetBackground.SOLID -> WidgetPalette(
                background = GlanceTheme.colors.widgetBackground,
                text = GlanceTheme.colors.onSurface,
                textVariant = GlanceTheme.colors.onSurfaceVariant,
                accent = GlanceTheme.colors.primary,
            )
            WidgetBackground.TRANSLUCENT -> onWallpaper(Color(0x66000000))
            WidgetBackground.TRANSPARENT -> onWallpaper(Color.Transparent)
        }
        CompositionLocalProvider(LocalWidgetPalette provides palette) { content() }
    }
}

private fun onWallpaper(background: Color) = WidgetPalette(
    background = ColorProvider(background),
    text = ColorProvider(Color.White),
    textVariant = ColorProvider(Color(0xE6FFFFFF)),
    accent = ColorProvider(Color(0xFFA8D4FF)),
)

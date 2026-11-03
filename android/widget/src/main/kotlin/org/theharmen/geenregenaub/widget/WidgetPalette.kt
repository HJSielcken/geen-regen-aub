package org.theharmen.geenregenaub.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.glance.GlanceTheme
import androidx.glance.color.ColorProviders
import androidx.glance.material3.ColorProviders as MaterialColorProviders
import androidx.glance.unit.ColorProvider
import org.theharmen.geenregenaub.alerts.WidgetBackground
import org.theharmen.geenregenaub.alerts.WidgetPreferences
import org.theharmen.geenregenaub.alerts.WidgetTextColor
import org.theharmen.geenregenaub.ui.theme.AppearancePreferences
import org.theharmen.geenregenaub.ui.theme.GeenRegenColors
import org.theharmen.geenregenaub.ui.theme.ThemeMode
import org.theharmen.geenregenaub.ui.theme.geenRegenColorScheme

/**
 * The few colours the widgets use. With a see-through background the theme's text colours
 * (dark on light) would vanish on most wallpapers, so those modes default to white text and a
 * light blue accent, like the launcher's own labels. The text colour can also be set by hand.
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

/** Everything about the widgets' look, read once per redraw. */
internal class WidgetLook(
    val background: WidgetBackground,
    val textColor: WidgetTextColor,
    /** The app's theme: licht/donker/systeem and wallpaper colours, as chosen in Instellingen. */
    val theme: ColorProviders,
) {
    companion object {
        fun load(context: Context): WidgetLook {
            val prefs = WidgetPreferences(context)
            val appearance = AppearancePreferences(context).appearance
            val light = geenRegenColorScheme(context, dark = false, dynamicColor = appearance.dynamicColor)
            val dark = geenRegenColorScheme(context, dark = true, dynamicColor = appearance.dynamicColor)
            val theme = when (appearance.mode) {
                ThemeMode.LIGHT -> MaterialColorProviders(scheme = light)
                ThemeMode.DARK -> MaterialColorProviders(scheme = dark)
                ThemeMode.SYSTEM -> MaterialColorProviders(light = light, dark = dark)
            }
            return WidgetLook(prefs.background, prefs.textColor, theme)
        }
    }
}

@Composable
internal fun WidgetTheme(look: WidgetLook, content: @Composable () -> Unit) {
    GlanceTheme(colors = look.theme) {
        val colors = GlanceTheme.colors
        val background = when (look.background) {
            WidgetBackground.SOLID -> colors.widgetBackground
            WidgetBackground.TRANSLUCENT -> ColorProvider(Color(0x66000000))
            WidgetBackground.TRANSPARENT -> ColorProvider(Color.Transparent)
        }
        val textColor = when (look.textColor) {
            WidgetTextColor.AUTO -> if (look.background == WidgetBackground.SOLID) null else WidgetTextColor.WHITE
            else -> look.textColor
        }
        val palette = when (textColor) {
            WidgetTextColor.WHITE -> WidgetPalette(
                background = background,
                text = ColorProvider(Color.White),
                textVariant = ColorProvider(Color(0xE6FFFFFF)),
                accent = ColorProvider(GeenRegenColors.SkyLight),
            )
            WidgetTextColor.BLACK -> WidgetPalette(
                background = background,
                text = ColorProvider(Color(0xFF111111)),
                textVariant = ColorProvider(Color(0xCC111111)),
                accent = ColorProvider(GeenRegenColors.Sky),
            )
            else -> WidgetPalette(
                background = background,
                text = colors.onSurface,
                textVariant = colors.onSurfaceVariant,
                accent = colors.primary,
            )
        }
        CompositionLocalProvider(LocalWidgetPalette provides palette) { content() }
    }
}

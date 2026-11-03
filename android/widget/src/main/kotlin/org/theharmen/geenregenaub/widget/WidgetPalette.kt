package org.theharmen.geenregenaub.widget

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.glance.LocalContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.glance.GlanceTheme
import androidx.glance.color.ColorProviders
import androidx.glance.material3.ColorProviders as MaterialColorProviders
import androidx.glance.unit.ColorProvider
import org.theharmen.geenregenaub.alerts.WidgetBackground
import org.theharmen.geenregenaub.alerts.WidgetPreferences
import org.theharmen.geenregenaub.ui.theme.AppearancePreferences
import org.theharmen.geenregenaub.ui.theme.GeenRegenColors
import org.theharmen.geenregenaub.ui.theme.ThemeMode
import org.theharmen.geenregenaub.ui.theme.geenRegenColorScheme

/** The few colours the widgets use, worked out from the theme and the widget settings. */
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
    /** Chosen background colour; null follows the theme. */
    val backgroundColor: Color?,
    /** Chosen text colour; null picks one that reads well. */
    val textColor: Color?,
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
            return WidgetLook(
                background = prefs.background,
                backgroundColor = prefs.backgroundColor?.let(::Color),
                textColor = prefs.textColor?.let(::Color),
                theme = theme,
            )
        }
    }
}

/**
 * The look, kept up to date while the widget is shown. Glance keeps a widget's session alive
 * between updates and only recomposes it, so a look read once in provideGlance would stay stale
 * until the session ended; listening to the settings makes a change show right away.
 */
private fun lookUpdates(context: Context): Flow<WidgetLook> = callbackFlow {
    val widget = WidgetPreferences(context)
    val appearance = AppearancePreferences(context)
    val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        trySend(WidgetLook.load(context))
    }
    widget.register(listener)
    appearance.register(listener)
    awaitClose {
        widget.unregister(listener)
        appearance.unregister(listener)
    }
}

@Composable
internal fun WidgetTheme(initial: WidgetLook, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val updates = remember(context) { lookUpdates(context.applicationContext) }
    val look by updates.collectAsState(initial = initial)
    GlanceTheme(colors = look.theme) {
        val colors = GlanceTheme.colors
        val chosen = look.backgroundColor
        val background = when (look.background) {
            WidgetBackground.SOLID -> chosen?.let(::ColorProvider) ?: colors.widgetBackground
            WidgetBackground.TRANSLUCENT -> ColorProvider((chosen ?: Color.Black).copy(alpha = 0.4f))
            WidgetBackground.TRANSPARENT -> ColorProvider(Color.Transparent)
        }
        // The colour the text sits on, when we know it; null means the theme's own background.
        val behind: Color? = when (look.background) {
            WidgetBackground.SOLID -> chosen
            // See-through: assume a busy wallpaper; light text reads best unless the tint is light.
            WidgetBackground.TRANSLUCENT -> if ((chosen ?: Color.Black).luminance() > 0.5f) Color.White else Color.Black
            WidgetBackground.TRANSPARENT -> Color.Black
        }
        val text: Color? = look.textColor ?: behind?.let { if (it.luminance() > 0.5f) Ink else Color.White }

        val palette = if (text == null) {
            WidgetPalette(background, colors.onSurface, colors.onSurfaceVariant, colors.primary)
        } else {
            val lightText = text.luminance() > 0.5f
            WidgetPalette(
                background = background,
                text = ColorProvider(text),
                textVariant = ColorProvider(text.copy(alpha = 0.85f)),
                // The app blue, unless the background is a colour of its own: then the text colour,
                // so rain never disappears into a blue or red widget.
                accent = ColorProvider(
                    when {
                        chosen != null && look.background != WidgetBackground.TRANSPARENT -> text
                        lightText -> GeenRegenColors.SkyLight
                        else -> GeenRegenColors.Sky
                    },
                ),
            )
        }
        CompositionLocalProvider(LocalWidgetPalette provides palette) { content() }
    }
}

private val Ink = Color(0xFF111111)

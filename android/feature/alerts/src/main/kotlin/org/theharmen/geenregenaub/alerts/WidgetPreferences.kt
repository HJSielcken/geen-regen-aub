package org.theharmen.geenregenaub.alerts

import android.content.Context
import androidx.core.content.edit

/** How the home-screen widgets draw their background. */
enum class WidgetBackground(val key: String) {
    /** The theme's own widget colour, with theme-coloured text. */
    SOLID("solid"),

    /** A dark see-through layer with white text: the wallpaper shows, text stays readable. */
    TRANSLUCENT("translucent"),

    /** No background at all, white text straight on the wallpaper. */
    TRANSPARENT("transparent"),
    ;

    companion object {
        // Stored by a fixed key, not by name(), so R8 renaming or reordering can never change it.
        fun of(key: String?): WidgetBackground = entries.firstOrNull { it.key == key } ?: SOLID
    }
}

/** Widget look, in plain SharedPreferences so the widgets read it without extra plumbing. */
class WidgetPreferences(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    var background: WidgetBackground
        get() = WidgetBackground.of(prefs.getString(KEY_BACKGROUND, null))
        set(value) = prefs.edit { putString(KEY_BACKGROUND, value.key) }

    private companion object {
        const val FILE = "widget_look"
        const val KEY_BACKGROUND = "background"
    }
}

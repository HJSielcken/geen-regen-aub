package org.theharmen.geenregenaub.alerts

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

/** How the home-screen widgets draw their background. */
enum class WidgetBackground(val key: String) {
    /** A full background: the chosen colour, or the theme's. */
    SOLID("solid"),

    /** The chosen colour (dark by default) see-through, so the wallpaper shows. */
    TRANSLUCENT("translucent"),

    /** No background at all, text straight on the wallpaper. */
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

    /** How much background there is: full, see-through, or none. */
    var background: WidgetBackground
        get() = WidgetBackground.of(prefs.getString(KEY_BACKGROUND, null))
        set(value) = prefs.edit { putString(KEY_BACKGROUND, value.key) }

    /** Background colour as ARGB; null follows the app theme. */
    var backgroundColor: Int?
        get() = colour(KEY_BACKGROUND_COLOR)
        set(value) = setColour(KEY_BACKGROUND_COLOR, value)

    /** Text colour as ARGB; null picks one that reads well on the background. */
    var textColor: Int?
        get() = colour(KEY_TEXT_COLOR)
        set(value) = setColour(KEY_TEXT_COLOR, value)

    /** Dashed lines for licht, matig and zwaar in the rain widget's graph. On unless switched off. */
    var intensityLines: Boolean
        get() = prefs.getBoolean(KEY_INTENSITY_LINES, true)
        set(value) = prefs.edit { putBoolean(KEY_INTENSITY_LINES, value) }

    fun register(listener: SharedPreferences.OnSharedPreferenceChangeListener) =
        prefs.registerOnSharedPreferenceChangeListener(listener)

    fun unregister(listener: SharedPreferences.OnSharedPreferenceChangeListener) =
        prefs.unregisterOnSharedPreferenceChangeListener(listener)

    private fun colour(key: String): Int? = if (prefs.contains(key)) prefs.getInt(key, 0) else null

    private fun setColour(key: String, value: Int?) = prefs.edit {
        if (value == null) remove(key) else putInt(key, value)
    }

    companion object {
        private const val FILE = "widget_look"
        private const val KEY_BACKGROUND = "background"
        private const val KEY_BACKGROUND_COLOR = "background_color"
        private const val KEY_TEXT_COLOR = "text_color_argb"
        private const val KEY_INTENSITY_LINES = "intensity_lines"

        /** The colours offered in Instellingen, for both background and text. */
        val SWATCHES: List<Int> = listOf(
            0xFFFFFFFF, // wit
            0xFF000000, // zwart
            0xFF455A64, // leisteen
            0xFF1F6FEB, // blauw
            0xFF0B1D3A, // nachtblauw
            0xFF00897B, // groen-blauw
            0xFF2E7D32, // groen
            0xFFF9A825, // geel
            0xFFEF6C00, // oranje
            0xFFC62828, // rood
            0xFFAD1457, // roze
            0xFF6A1B9A, // paars
        ).map { it.toInt() }
    }
}

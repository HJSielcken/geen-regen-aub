package org.theharmen.geenregenaub.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.ColumnScope
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle

/** The card every weather widget sits in: the chosen background, rounded like the app's cards. */
internal fun widgetCard(): GlanceModifier = GlanceModifier.fillMaxSize().cornerRadius(20.dp)

@Composable
internal fun WidgetSurface(
    modifier: GlanceModifier,
    verticalAlignment: Alignment.Vertical = Alignment.Top,
    content: @Composable ColumnScope.() -> Unit,
) {
    val palette = LocalWidgetPalette.current
    Column(
        modifier = widgetCard().background(palette.background).then(modifier),
        verticalAlignment = verticalAlignment,
        content = content,
    )
}

/** Shown until the app has fetched a forecast. */
@Composable
internal fun WeatherNoData() {
    val palette = LocalWidgetPalette.current
    val context = LocalContext.current
    Text(
        text = context.getString(R.string.forecast_widget_no_data),
        maxLines = 1,
        style = TextStyle(color = palette.text, fontSize = 14.sp, fontWeight = FontWeight.Bold),
    )
    Text(
        text = context.getString(R.string.forecast_widget_no_data_detail),
        maxLines = 2,
        style = TextStyle(color = palette.textVariant, fontSize = 12.sp),
    )
}

/** "40%" in rain blue, or nothing when rain is unlikely; same rule as the app. */
internal fun rainChance(probability: Int?): String = probability?.takeIf { it >= 10 }?.let { "$it%" } ?: ""

private val ARROWS = listOf("↓", "↙", "←", "↖", "↑", "↗", "→", "↘")

/** "↗15 km/u": speed with an arrow pointing where the wind blows to; the forecast gives where it comes from. */
internal fun windKmh(kmh: Double?, fromDegrees: Double?): String {
    kmh ?: return " "
    val speed = "${kotlin.math.round(kmh).toInt()} km/u"
    fromDegrees ?: return speed
    val sector = Math.floorMod(kotlin.math.round(fromDegrees / 45.0).toInt(), 8)
    return "${ARROWS[sector]}$speed"
}

/** A small title on the left and the place on the right, as on the hourly and daily widgets. */
@Composable
internal fun WidgetTitle(title: String, trailing: String?) {
    val palette = LocalWidgetPalette.current
    Row(modifier = GlanceModifier.fillMaxWidth().padding(bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = title,
            maxLines = 1,
            modifier = GlanceModifier.defaultWeight(),
            style = TextStyle(color = palette.textVariant, fontSize = 13.sp, fontWeight = FontWeight.Medium),
        )
        if (trailing != null) {
            Text(
                text = trailing,
                maxLines = 1,
                style = TextStyle(color = palette.textVariant, fontSize = 12.sp, textAlign = TextAlign.End),
            )
        }
    }
}

/** A pixel size for a bitmap drawn at [dp]. */
internal fun Context.px(dp: Dp): Int = (dp.value * resources.displayMetrics.density).toInt().coerceAtLeast(1)

internal val Context.density: Float get() = resources.displayMetrics.density

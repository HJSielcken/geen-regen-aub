package org.theharmen.geenregenaub.widget

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.Column
import androidx.glance.layout.ColumnScope
import androidx.glance.layout.fillMaxSize
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle

/** The card every weather widget sits in: the chosen background, rounded like the app's cards. */
internal fun widgetCard(): GlanceModifier = GlanceModifier.fillMaxSize().cornerRadius(20.dp)

@Composable
internal fun WidgetSurface(modifier: GlanceModifier, content: @Composable ColumnScope.() -> Unit) {
    val palette = LocalWidgetPalette.current
    Column(modifier = widgetCard().background(palette.background).then(modifier), content = content)
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

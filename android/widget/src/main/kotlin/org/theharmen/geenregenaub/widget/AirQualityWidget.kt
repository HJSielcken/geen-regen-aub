package org.theharmen.geenregenaub.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import org.theharmen.geenregenaub.domain.AirQualityLevel
import org.theharmen.geenregenaub.forecast.WeatherFormat

/**
 * "Luchtkwaliteit": the European air quality index now, with its band. Taller adds the scale
 * from goed to extreem slecht, taller still the next 24 hours as bars. Copernicus CAMS data.
 */
class AirQualityWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(
        WIDTHS.flatMap { w -> HEIGHTS.map { h -> DpSize(w, h) } }.toSet(),
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val look = WidgetLook.load(context)
        val state = WeatherWidgets.load(context)
        val open = WeatherWidgets.openIntent(context)
        provideContent {
            WidgetTheme(look) { Content(state, open) }
        }
    }

    @Composable
    private fun Content(state: WeatherWidgetState?, open: Intent) {
        val context = LocalContext.current
        val palette = LocalWidgetPalette.current
        val size = LocalSize.current
        val hours = state?.let { s -> s.airQuality?.hoursFrom(s.now, 24) }.orEmpty()
        val index = state?.airQuality?.current ?: hours.firstOrNull()?.index
        val showScale = size.height >= HEIGHTS[1]
        val showBars = size.height >= HEIGHTS[2] && hours.size >= 2
        val wide = size.width >= WIDTHS[1]
        WidgetSurface(
            GlanceModifier.padding(horizontal = 14.dp, vertical = 8.dp).clickable(actionStartActivity(open)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (state == null) {
                WeatherNoData()
                return@WidgetSurface
            }
            if (index == null) {
                Text(
                    text = context.getString(R.string.air_widget_no_data),
                    maxLines = 2,
                    style = TextStyle(color = palette.text, fontSize = 13.sp, fontWeight = FontWeight.Medium),
                )
                return@WidgetSurface
            }
            val level = AirQualityLevel.of(index)
            if (showBars) WidgetTitle(context.getString(R.string.air_widget_name), state.placeName)
            Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = index.toString(),
                    maxLines = 1,
                    style = TextStyle(color = palette.text, fontSize = if (showScale) 36.sp else 28.sp, fontWeight = FontWeight.Medium),
                )
                Spacer(GlanceModifier.width(10.dp))
                Column(modifier = GlanceModifier.defaultWeight()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            provider = ImageProvider(SkyRenderer.dot(context.px(10.dp), SkyRenderer.airColor(index))),
                            contentDescription = null,
                            modifier = GlanceModifier.size(10.dp),
                        )
                        Spacer(GlanceModifier.width(6.dp))
                        Text(
                            text = level.dutch,
                            maxLines = 1,
                            style = TextStyle(color = palette.text, fontSize = 15.sp, fontWeight = FontWeight.Bold),
                        )
                    }
                    if (wide && showScale) {
                        Text(level.advice, maxLines = 2, style = TextStyle(color = palette.textVariant, fontSize = 12.sp))
                    } else if (!showScale) {
                        Text(context.getString(R.string.air_widget_unit), maxLines = 1, style = TextStyle(color = palette.textVariant, fontSize = 11.sp))
                    }
                }
            }
            if (showBars) {
                Spacer(GlanceModifier.height(6.dp))
                val chartWidth = size.width - 28.dp
                val chartHeight = (size.height - 150.dp).coerceIn(28.dp, 120.dp)
                val base = palette.textVariant.getColor(context).copy(alpha = 0.4f).toArgb()
                Image(
                    provider = ImageProvider(
                        SkyRenderer.airBars(hours.map { it.index }, context.px(chartWidth), context.px(chartHeight), context.density, base),
                    ),
                    contentDescription = null,
                    contentScale = ContentScale.FillBounds,
                    modifier = GlanceModifier.fillMaxWidth().height(chartHeight),
                )
                Row(modifier = GlanceModifier.fillMaxWidth().padding(top = 2.dp)) {
                    Text(
                        text = WeatherFormat.time(hours.first().time, state.zone),
                        maxLines = 1,
                        modifier = GlanceModifier.defaultWeight(),
                        style = TextStyle(color = palette.textVariant, fontSize = 10.sp),
                    )
                    Text(
                        text = WeatherFormat.time(hours.last().time, state.zone),
                        maxLines = 1,
                        style = TextStyle(color = palette.textVariant, fontSize = 10.sp),
                    )
                }
            }
            if (showScale) {
                Spacer(GlanceModifier.height(6.dp))
                val scaleWidth = size.width - 28.dp
                Image(
                    provider = ImageProvider(
                        SkyRenderer.airScale(
                            index = index,
                            widthPx = context.px(scaleWidth),
                            heightPx = context.px(14.dp),
                            density = context.density,
                            markerColor = SkyRenderer.airColor(index),
                            ringColor = palette.text.getColor(context).toArgb(),
                        ),
                    ),
                    contentDescription = null,
                    contentScale = ContentScale.FillBounds,
                    modifier = GlanceModifier.fillMaxWidth().height(14.dp),
                )
                Row(modifier = GlanceModifier.fillMaxWidth()) {
                    Text(
                        text = AirQualityLevel.GOOD.dutch,
                        maxLines = 1,
                        modifier = GlanceModifier.defaultWeight(),
                        style = TextStyle(color = palette.textVariant, fontSize = 10.sp),
                    )
                    Text(
                        text = AirQualityLevel.EXTREMELY_POOR.dutch,
                        maxLines = 1,
                        style = TextStyle(color = palette.textVariant, fontSize = 10.sp),
                    )
                }
            }
        }
    }

    private companion object {
        val WIDTHS = listOf(110.dp, 200.dp, 300.dp)
        val HEIGHTS = listOf(48.dp, 96.dp, 180.dp)
    }
}

class AirQualityWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = AirQualityWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        WidgetRefreshWorker.runNow(context)
    }
}

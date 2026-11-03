package org.theharmen.geenregenaub.widget

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.createBitmap
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
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.Alignment
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
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import org.theharmen.geenregenaub.domain.DayWeather
import org.theharmen.geenregenaub.domain.formatTemperature
import org.theharmen.geenregenaub.forecast.WeatherFormat
import org.theharmen.geenregenaub.forecast.icon
import org.theharmen.geenregenaub.forecast.temperatureColor
import kotlin.math.max

/**
 * "Weer per dag": today and the coming days, like the days card in the app. The days are a
 * scrolling list, so a low widget shows fewer rows instead of cutting them off.
 */
class DailyWeatherWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(
        setOf(
            DpSize(180.dp, 60.dp), DpSize(260.dp, 60.dp), DpSize(320.dp, 60.dp),
            DpSize(180.dp, 120.dp), DpSize(260.dp, 120.dp), DpSize(320.dp, 120.dp),
        ),
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
        val palette = LocalWidgetPalette.current
        val context = LocalContext.current
        val size = LocalSize.current
        WidgetSurface(GlanceModifier.padding(horizontal = 14.dp, vertical = 8.dp).clickable(actionStartActivity(open))) {
            if (state == null || state.days.isEmpty()) {
                WeatherNoData()
                return@WidgetSurface
            }
            if (size.height >= 120.dp) {
                Row(modifier = GlanceModifier.fillMaxWidth().padding(bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = context.getString(R.string.daily_widget_title),
                        maxLines = 1,
                        modifier = GlanceModifier.defaultWeight(),
                        style = TextStyle(color = palette.textVariant, fontSize = 13.sp, fontWeight = FontWeight.Medium),
                    )
                    Text(
                        text = state.placeName,
                        maxLines = 1,
                        style = TextStyle(color = palette.textVariant, fontSize = 12.sp, textAlign = TextAlign.End),
                    )
                }
            }
            val showChance = size.width >= 260.dp
            val showBar = size.width >= 320.dp
            val low = state.days.minOf { it.temperatureMin }
            val high = state.days.maxOf { it.temperatureMax }
            LazyColumn(modifier = GlanceModifier.fillMaxWidth().defaultWeight()) {
                items(state.days, itemId = { it.date.toEpochDay() }) { day ->
                    DayRow(day, state, open, showChance, if (showBar) low to high else null)
                }
            }
        }
    }

    @Composable
    private fun DayRow(
        day: DayWeather,
        state: WeatherWidgetState,
        open: Intent,
        showChance: Boolean,
        range: Pair<Double, Double>?,
    ) {
        val palette = LocalWidgetPalette.current
        val context = LocalContext.current
        val label = WeatherFormat.day(
            day.date,
            state.todayDate,
            context.getString(R.string.forecast_widget_today),
            context.getString(R.string.forecast_widget_tomorrow),
        )
        Row(
            modifier = GlanceModifier.fillMaxWidth().height(32.dp).clickable(actionStartActivity(open)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                maxLines = 1,
                modifier = GlanceModifier.defaultWeight(),
                style = TextStyle(color = palette.text, fontSize = 14.sp),
            )
            Image(
                provider = ImageProvider(day.condition.icon()),
                contentDescription = day.condition.dutch(),
                modifier = GlanceModifier.size(28.dp),
            )
            if (showChance) {
                Text(
                    text = rainChance(day.precipitationProbability),
                    maxLines = 1,
                    modifier = GlanceModifier.width(38.dp),
                    style = TextStyle(color = palette.accent, fontSize = 11.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center),
                )
            } else {
                Spacer(GlanceModifier.width(4.dp))
            }
            Text(
                text = formatTemperature(day.temperatureMin),
                maxLines = 1,
                modifier = GlanceModifier.width(32.dp),
                style = TextStyle(color = palette.textVariant, fontSize = 14.sp, textAlign = TextAlign.End),
            )
            if (range != null) {
                TemperatureBar(day, range.first, range.second)
            } else {
                Spacer(GlanceModifier.width(6.dp))
            }
            Text(
                text = formatTemperature(day.temperatureMax),
                maxLines = 1,
                modifier = GlanceModifier.width(32.dp),
                style = TextStyle(color = palette.text, fontSize = 14.sp, fontWeight = FontWeight.Bold),
            )
        }
    }

    /** The day's min–max on a track spanning the whole week, cool to warm, as in the app. */
    @Composable
    private fun TemperatureBar(day: DayWeather, low: Double, high: Double) {
        val palette = LocalWidgetPalette.current
        val context = LocalContext.current
        val density = context.resources.displayMetrics.density
        val track = palette.textVariant.getColor(context).copy(alpha = 0.25f).toArgb()
        val bitmap = renderTemperatureBar(
            dayMin = day.temperatureMin,
            dayMax = day.temperatureMax,
            low = low,
            high = high,
            widthPx = (BAR_WIDTH.value * density).toInt(),
            heightPx = (BAR_HEIGHT.value * density).toInt(),
            trackColor = track,
        )
        Image(
            provider = ImageProvider(bitmap),
            contentDescription = null,
            contentScale = ContentScale.FillBounds,
            modifier = GlanceModifier.padding(horizontal = 8.dp).width(BAR_WIDTH + 16.dp).height(BAR_HEIGHT),
        )
    }

    private companion object {
        val BAR_WIDTH = 52.dp
        val BAR_HEIGHT = 6.dp
    }
}

internal fun renderTemperatureBar(
    dayMin: Double,
    dayMax: Double,
    low: Double,
    high: Double,
    widthPx: Int,
    heightPx: Int,
    trackColor: Int,
): Bitmap {
    val w = max(1, widthPx)
    val h = max(1, heightPx)
    val bitmap = createBitmap(w, h)
    val canvas = Canvas(bitmap)
    val radius = h / 2f
    canvas.drawRoundRect(RectF(0f, 0f, w.toFloat(), h.toFloat()), radius, radius, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = trackColor })
    val span = (high - low).takeIf { it > 0 } ?: 1.0
    val start = ((dayMin - low) / span).toFloat().coerceIn(0f, 1f) * w
    val end = max(((dayMax - low) / span).toFloat().coerceIn(0f, 1f) * w, start + h)
    val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = LinearGradient(
            start, 0f, end, 0f,
            temperatureColor(dayMin).toArgb(), temperatureColor(dayMax).toArgb(),
            Shader.TileMode.CLAMP,
        )
    }
    canvas.drawRoundRect(RectF(start, 0f, end.coerceAtMost(w.toFloat()), h.toFloat()), radius, radius, fill)
    return bitmap
}

class DailyWeatherWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = DailyWeatherWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        WidgetRefreshWorker.runNow(context)
    }
}

package org.theharmen.geenregenaub.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
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
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import org.theharmen.geenregenaub.alerts.WidgetPreferences
import org.theharmen.geenregenaub.data.CachedForecast
import org.theharmen.geenregenaub.data.GeenRegenData
import org.theharmen.geenregenaub.domain.RainForecast
import org.theharmen.geenregenaub.domain.Verdict
import org.theharmen.geenregenaub.domain.VerdictText
import org.theharmen.geenregenaub.domain.remainingAt
import org.theharmen.geenregenaub.domain.isStale
import org.theharmen.geenregenaub.domain.verdict
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Home-screen widget: the verdict ("Droog de komende 2 uur") and, when there is room, the
 * 2-hour rain graph. It renders from the offline cache only; [WidgetRefreshWorker] fetches.
 */
class RainWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val look = WidgetPreferences(context).background
        val data = runCatching { GeenRegenData.forecastRepository(context).latest() }.getOrNull()
        val now = Instant.now()
        val state = WidgetState.of(data, now, ZoneId.systemDefault(), context.getString(R.string.widget_sample_data))
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)

        provideContent {
            WidgetTheme(look) {
                Content(state, launch)
            }
        }
    }

    @Composable
    private fun Content(state: WidgetState, launch: Intent?) {
        val palette = LocalWidgetPalette.current
        val context = LocalContext.current
        val size = LocalSize.current
        var modifier = GlanceModifier
            .fillMaxSize()
            .background(palette.background)
            .cornerRadius(20.dp)
            .padding(horizontal = 16.dp, vertical = 12.dp)
        if (launch != null) modifier = modifier.clickable(actionStartActivity(launch))

        val compact = size.height < 110.dp
        Column(modifier = modifier) {
            Text(
                text = state.headline(context),
                maxLines = 2,
                style = TextStyle(
                    color = if (state.isRain) palette.accent else palette.text,
                    fontSize = if (size.width < 180.dp) 15.sp else 18.sp,
                    fontWeight = FontWeight.Bold,
                ),
            )

            val graph = state.graph
            if (!compact && graph != null) {
                Spacer(GlanceModifier.height(8.dp))
                Graph(graph, state.headline(context), size.width - 32.dp, graphHeight(size.height))
                Row(modifier = GlanceModifier.fillMaxWidth().padding(top = 2.dp)) {
                    AxisLabel(context.getString(R.string.widget_axis_now), TextAlign.Start)
                    AxisLabel(context.getString(R.string.widget_axis_one_hour), TextAlign.Center)
                    AxisLabel(context.getString(R.string.widget_axis_two_hours), TextAlign.End)
                }
            } else if (!compact) {
                Spacer(GlanceModifier.height(4.dp))
                Text(
                    text = context.getString(R.string.widget_no_data_detail),
                    maxLines = 2,
                    style = TextStyle(color = palette.textVariant, fontSize = 13.sp),
                )
            }

            Spacer(GlanceModifier.defaultWeight())
            Text(
                text = state.footer(context),
                maxLines = 1,
                style = TextStyle(color = palette.textVariant, fontSize = 11.sp),
            )
        }
    }

    @Composable
    private fun Graph(forecast: RainForecast, description: String, width: Dp, height: Dp) {
        val palette = LocalWidgetPalette.current
        val context = LocalContext.current
        val density = context.resources.displayMetrics.density
        val rain = palette.accent.getColor(context).toArgb()
        val grid = palette.textVariant.getColor(context).copy(alpha = 0.3f).toArgb()
        val bitmap = RainGraphRenderer.render(
            forecast = forecast,
            widthPx = (width.value * density).toInt(),
            heightPx = (height.value * density).toInt(),
            density = density,
            rainColor = rain,
            gridColor = grid,
        )
        Image(
            provider = ImageProvider(bitmap),
            contentDescription = description,
            contentScale = ContentScale.FillBounds,
            modifier = GlanceModifier.fillMaxWidth().height(height),
        )
    }

    @Composable
    private fun androidx.glance.layout.RowScope.AxisLabel(text: String, align: TextAlign) {
        val palette = LocalWidgetPalette.current
        Text(
            text = text,
            modifier = GlanceModifier.defaultWeight(),
            style = TextStyle(color = palette.textVariant, fontSize = 11.sp, textAlign = align),
        )
    }

    /** Room left for the graph after the headline, axis labels, footer and padding. */
    private fun graphHeight(widgetHeight: Dp): Dp = (widgetHeight - 104.dp).coerceIn(32.dp, 180.dp)
}

/** Everything the widget shows, worked out before composing. */
internal data class WidgetState(
    val verdictText: String?,
    val isRain: Boolean,
    val graph: RainForecast?,
    val stale: Boolean,
    val updatedAt: String?,
    val placeName: String?,
    /** Only for sample data, so it is never mistaken for a real forecast. */
    val sampleLabel: String?,
) {
    fun headline(context: Context): String = when {
        verdictText == null && updatedAt != null -> context.getString(R.string.widget_stale)
        verdictText == null -> context.getString(R.string.widget_no_data)
        else -> verdictText
    }

    fun footer(context: Context): String {
        val parts = buildList {
            placeName?.let(::add)
            updatedAt?.let { add(context.getString(if (stale) R.string.widget_updated_stale else R.string.widget_updated, it)) }
            sampleLabel?.let(::add)
        }
        return parts.joinToString(" · ")
    }

    companion object {
        private val clock = DateTimeFormatter.ofPattern("HH:mm")

        fun of(data: CachedForecast?, now: Instant, zone: ZoneId, sampleLabel: String): WidgetState {
            if (data == null) return WidgetState(null, false, null, false, null, null, null)
            val updated = clock.format(data.fetchedAt.atZone(zone))
            val placeName = data.place.name.substringBefore(",").trim()
            // The widget names no data source; the app itself credits KNMI.
            val label = if (data.isDemo) sampleLabel else null
            val upcoming = data.forecast.remainingAt(now)
            val stale = data.forecast.isStale(now)
            if (upcoming == null || stale) {
                return WidgetState(null, false, null, true, updated, placeName, label)
            }
            val verdict = upcoming.verdict()
            return WidgetState(
                verdictText = VerdictText.dutch(verdict.roundedForDisplay(), zone),
                isRain = verdict !is Verdict.Dry,
                graph = upcoming,
                stale = false,
                updatedAt = updated,
                placeName = placeName,
                sampleLabel = label,
            )
        }
    }
}

/**
 * A cached forecast's dry window shrinks every few minutes ("Droog de komende 115 minuten").
 * Round it to the nearest half hour so the widget keeps saying "2 uur" or "90 minuten".
 */
private fun Verdict.roundedForDisplay(): Verdict =
    if (this is Verdict.Dry) {
        val halfHours = ((window.toMinutes() + 15) / 30).coerceAtLeast(1)
        Verdict.Dry(Duration.ofMinutes(halfHours * 30))
    } else {
        this
    }

class RainWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = RainWidget()

    /** First widget placed: fetch right away instead of waiting for the next 15-minute run. */
    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        WidgetRefreshWorker.runNow(context)
    }
}

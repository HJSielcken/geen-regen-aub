package org.theharmen.geenregenaub.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
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
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.ColumnScope
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
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
    // Responsive, not Exact: Nova reports exact sizes that are off, which made the widget think
    // it was too low for the graph. The graph fills whatever height there really is.
    override val sizeMode = SizeMode.Responsive(
        listOf(150.dp, 250.dp, 330.dp).flatMap { w -> listOf(56.dp, 100.dp, 140.dp, 180.dp).map { h -> DpSize(w, h) } }.toSet(),
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val look = WidgetLook.load(context)
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
            // Only vertical padding: the graph runs edge to edge, the texts get their own side padding.
            .padding(vertical = 8.dp)
        if (launch != null) modifier = modifier.clickable(actionStartActivity(launch))

        val withAxis = size.height >= 100.dp
        val graph = state.graph
        val sides = GlanceModifier.padding(horizontal = 16.dp)
        Column(modifier = modifier) {
            if (graph == null) {
                // No forecast to draw: say why instead.
                Text(
                    text = state.headline(context),
                    modifier = sides,
                    maxLines = 2,
                    style = TextStyle(color = palette.text, fontSize = 15.sp, fontWeight = FontWeight.Bold),
                )
                if (withAxis) {
                    Spacer(GlanceModifier.height(4.dp))
                    Text(
                        text = context.getString(R.string.widget_no_data_detail),
                        modifier = sides,
                        maxLines = 2,
                        style = TextStyle(color = palette.textVariant, fontSize = 13.sp),
                    )
                }
                Spacer(GlanceModifier.defaultWeight())
            } else {
                // Just the graph: no "Droog de komende 2 uur" above it.
                val axis = if (withAxis) 16.dp else 0.dp
                val estimate = (size.height - 24.dp - 16.dp - axis).coerceAtLeast(24.dp)
                Graph(graph, state.headline(context), size.width, estimate)
                if (withAxis) {
                    Row(modifier = GlanceModifier.fillMaxWidth().padding(start = 16.dp, top = 2.dp, end = 16.dp)) {
                        AxisLabel(context.getString(R.string.widget_axis_now), TextAlign.Start)
                        AxisLabel(context.getString(R.string.widget_axis_one_hour), TextAlign.Center)
                        AxisLabel(context.getString(R.string.widget_axis_two_hours), TextAlign.End)
                    }
                }
            }
            Text(
                text = state.footer(context),
                modifier = sides,
                maxLines = 1,
                style = TextStyle(color = palette.textVariant, fontSize = 11.sp),
            )
        }
    }

    @Composable
    private fun ColumnScope.Graph(forecast: RainForecast, description: String, width: Dp, height: Dp) {
        val palette = LocalWidgetPalette.current
        val context = LocalContext.current
        val density = context.resources.displayMetrics.density
        val rain = palette.accent.getColor(context).toArgb()
        val label = palette.textVariant.getColor(context)
        val grid = label.copy(alpha = 0.3f).toArgb()
        val guides = if (LocalWidgetLook.current.intensityLines) {
            RainGraphRenderer.Guides(
                lineColor = label.copy(alpha = 0.55f).toArgb(),
                labelColor = label.toArgb(),
                light = context.getString(R.string.widget_intensity_light),
                moderate = context.getString(R.string.widget_intensity_moderate),
                heavy = context.getString(R.string.widget_intensity_heavy),
            )
        } else {
            null
        }
        val bitmap = RainGraphRenderer.render(
            forecast = forecast,
            widthPx = (width.value * density).toInt(),
            heightPx = (height.value * density).toInt(),
            density = density,
            rainColor = rain,
            gridColor = grid,
            guides = guides,
        )
        Image(
            provider = ImageProvider(bitmap),
            contentDescription = description,
            contentScale = ContentScale.FillBounds,
            // Fills the height left over; the bitmap is drawn for the estimated size and stretched.
            modifier = GlanceModifier.fillMaxWidth().defaultWeight(),
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


    companion object {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        /** Redraw every rain widget, e.g. after a colour change in Instellingen. */
        fun updateAllAsync(context: Context) {
            val app = context.applicationContext
            scope.launch { runCatching { updateWidgets(app, RainWidget(), RainWidgetReceiver::class.java) } }
        }
    }
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

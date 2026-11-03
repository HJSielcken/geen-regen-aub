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
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.RowScope
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import org.theharmen.geenregenaub.domain.Moon
import org.theharmen.geenregenaub.domain.MoonPhase
import org.theharmen.geenregenaub.domain.MoonPhaseName
import org.theharmen.geenregenaub.forecast.WeatherFormat
import java.time.Instant
import java.time.ZoneId
import kotlin.math.roundToInt

/**
 * "Maan": tonight's moon and how much of it is lit. Wider puts the next new and full moon beside
 * it, as in the app's moon row. Worked out on the phone, so it never needs the network.
 */
class MoonWidget : GlanceAppWidget() {
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
        val size = LocalSize.current
        val now = state?.now ?: Instant.now()
        val zone = state?.zone ?: ZoneId.systemDefault()
        val phase = Moon.phase(now)
        val tall = size.height >= HEIGHTS[1]
        val columns = tall && size.width >= WIDTHS[1]
        WidgetSurface(
            GlanceModifier.padding(horizontal = 14.dp, vertical = 8.dp).clickable(actionStartActivity(open)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (size.height >= HEIGHTS[2]) WidgetTitle(context.getString(R.string.moon_widget_name), state?.placeName)
            when {
                columns -> Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                    val disc = if (size.height >= HEIGHTS[2]) 52.dp else 40.dp
                    val today = now.atZone(zone).toLocalDate()
                    MoonColumn(phase.age, disc, context.getString(R.string.forecast_widget_today), phase.name.dutch)
                    // Whichever comes first goes first, as the moon goes through its cycle.
                    val events = listOf(
                        Triple(phase.nextNewMoon, 0.0, MoonPhaseName.NEW.dutch),
                        Triple(phase.nextFullMoon, 0.5, MoonPhaseName.FULL.dutch),
                    ).sortedBy { it.first }
                    events.forEach { (time, age, name) ->
                        MoonColumn(age, disc, name, dateLabel(context, time, zone, today))
                    }
                }
                else -> Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    MoonDisc(phase.age, if (tall) 48.dp else 32.dp)
                    Spacer(GlanceModifier.width(10.dp))
                    Column(modifier = GlanceModifier.defaultWeight()) {
                        Summary(phase)
                        if (tall) {
                            val today = now.atZone(zone).toLocalDate()
                            val (time, label) = if (phase.waxing) {
                                phase.nextFullMoon to MoonPhaseName.FULL.dutch
                            } else {
                                phase.nextNewMoon to MoonPhaseName.NEW.dutch
                            }
                            Text(
                                text = "$label ${dateLabel(context, time, zone, today).lowercase()}",
                                maxLines = 1,
                                style = TextStyle(color = LocalWidgetPalette.current.textVariant, fontSize = 11.sp),
                            )
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun Summary(phase: MoonPhase) {
        val palette = LocalWidgetPalette.current
        val context = LocalContext.current
        Text(phase.name.dutch, maxLines = 1, style = TextStyle(color = palette.text, fontSize = 14.sp, fontWeight = FontWeight.Bold))
        Text(
            text = context.getString(R.string.moon_widget_lit, (phase.illumination * 100).roundToInt()),
            maxLines = 1,
            style = TextStyle(color = palette.textVariant, fontSize = 12.sp),
        )
    }

    @Composable
    private fun RowScope.MoonColumn(age: Double, disc: Dp, title: String, subtitle: String) {
        val palette = LocalWidgetPalette.current
        Column(modifier = GlanceModifier.defaultWeight(), horizontalAlignment = Alignment.CenterHorizontally) {
            MoonDisc(age, disc)
            Text(
                text = title,
                maxLines = 1,
                modifier = GlanceModifier.padding(top = 4.dp),
                style = TextStyle(color = palette.text, fontSize = 12.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center),
            )
            Text(subtitle, maxLines = 1, style = TextStyle(color = palette.textVariant, fontSize = 11.sp, textAlign = TextAlign.Center))
        }
    }

    @Composable
    private fun MoonDisc(age: Double, diameter: Dp) {
        val context = LocalContext.current
        val dark = LocalWidgetPalette.current.textVariant.getColor(context).copy(alpha = 0.22f).toArgb()
        Image(
            provider = ImageProvider(SkyRenderer.moon(age, context.px(diameter), MOONLIGHT, dark)),
            contentDescription = null,
            modifier = GlanceModifier.size(diameter),
        )
    }

    /** "Vandaag", "Morgen" or "zo 9 nov". */
    private fun dateLabel(context: Context, time: Instant, zone: ZoneId, today: java.time.LocalDate): String = WeatherFormat.day(
        time.atZone(zone).toLocalDate(),
        today,
        context.getString(R.string.forecast_widget_today),
        context.getString(R.string.forecast_widget_tomorrow),
    )

    private companion object {
        val WIDTHS = listOf(110.dp, 200.dp, 300.dp)
        val HEIGHTS = listOf(48.dp, 90.dp, 140.dp)
        const val MOONLIGHT = 0xFFF3E9B5.toInt()
    }
}

class MoonWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = MoonWidget()
}

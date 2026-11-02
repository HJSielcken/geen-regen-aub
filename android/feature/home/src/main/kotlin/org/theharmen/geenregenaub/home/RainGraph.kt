package org.theharmen.geenregenaub.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.theharmen.geenregenaub.domain.Intensity
import org.theharmen.geenregenaub.domain.RainForecast
import org.theharmen.geenregenaub.domain.SmoothCurve
import org.theharmen.geenregenaub.ui.theme.GeenRegenColors
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.ln
import kotlin.math.max

private val clock = DateTimeFormatter.ofPattern("HH:mm")

/** Dutch number with one decimal: 1,8. */
internal fun mmPerHour(value: Double): String = String.format(Locale.forLanguageTag("nl-NL"), "%.1f mm/u", value)

/**
 * Two-hour rain graph: a smooth area through one point per 5 minutes, on a logarithmic scale so
 * drizzle stays visible next to a downpour. Dashed lines mark the moderate and heavy bands. Tap the
 * graph, or slide a finger along it, to read the time and intensity at that moment.
 */
@Composable
fun RainGraph(
    forecast: RainForecast,
    zone: ZoneId,
    summary: String,
    modifier: Modifier = Modifier,
) {
    var selected by remember(forecast) { mutableStateOf<Int?>(null) }
    val values = forecast.intensities
    val scaleTop = max(SCALE_MIN_TOP, values.maxOrNull() ?: 0.0)
    val guide = MaterialTheme.colorScheme.outlineVariant
    val baseline = MaterialTheme.colorScheme.outline
    val selectedColor = MaterialTheme.colorScheme.primary
    val emptyText = MaterialTheme.colorScheme.onSurfaceVariant
    val surface = MaterialTheme.colorScheme.surfaceContainerLow

    Column(modifier) {
        val sel = selected
        Text(
            text = if (sel != null && sel in values.indices) {
                "${clock.format(forecast.timeOf(sel).atZone(zone))} · ${
                    if (values[sel] < Intensity.LIGHT.minMmPerHour) "droog" else mmPerHour(values[sel])
                }"
            } else {
                " "
            },
            style = MaterialTheme.typography.labelLarge,
            color = selectedColor,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(4.dp))
        Row {
            Canvas(
                modifier = Modifier
                    .weight(1f)
                    .height(180.dp)
                    .semantics { contentDescription = summary }
                    .pointerInput(forecast) {
                        detectTapGestures { offset ->
                            val index = SmoothCurve.nearestIndex(offset.x, size.width.toFloat(), values.size)
                            selected = if (selected == index) null else index
                        }
                    }
                    .pointerInput(forecast) {
                        // Slide a finger along the graph to read every 5 minutes.
                        detectHorizontalDragGestures(
                            onDragStart = { offset ->
                                selected = SmoothCurve.nearestIndex(offset.x, size.width.toFloat(), values.size)
                            },
                        ) { change, _ ->
                            change.consume()
                            selected = SmoothCurve.nearestIndex(change.position.x, size.width.toFloat(), values.size)
                        }
                    },
            ) {
                fun yOf(v: Double) = size.height * (1f - (ln(1 + v) / ln(1 + scaleTop)).toFloat().coerceIn(0f, 1f))

                for (band in Intensity.entries.drop(1)) {
                    val y = yOf(band.minMmPerHour)
                    drawLine(
                        color = guide,
                        start = Offset(0f, y),
                        end = Offset(size.width, y),
                        strokeWidth = 1.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)),
                    )
                }

                val step = if (values.size > 1) size.width / (values.size - 1) else size.width
                val xs = FloatArray(values.size) { it * step }
                // Below the drizzle threshold counts as dry, so dry spells sit flat on the baseline;
                // the steps are softened so a shower fades in and out instead of jumping.
                val heights = FloatArray(values.size) { i ->
                    val v = values[i]
                    if (v < Intensity.LIGHT.minMmPerHour) 0f else size.height - yOf(v)
                }
                val soft = SmoothCurve.soften(heights)
                val ys = FloatArray(values.size) { i -> size.height - soft[i] }
                if (values.isNotEmpty() && ys.any { it < size.height }) {
                    val line = Path().apply {
                        moveTo(xs[0], ys[0])
                        for (seg in SmoothCurve.segments(xs, ys)) {
                            cubicTo(seg.c1x, seg.c1y, seg.c2x, seg.c2y, seg.x, seg.y)
                        }
                    }
                    val area = Path().apply {
                        addPath(line)
                        lineTo(xs.last(), size.height)
                        lineTo(xs.first(), size.height)
                        close()
                    }
                    // Colour follows height: light rain at the bottom, heavy rain at the top.
                    val brush = Brush.verticalGradient(
                        0f to GeenRegenColors.RainHeavy,
                        (yOf(Intensity.HEAVY.minMmPerHour) / size.height) to GeenRegenColors.RainHeavy,
                        (yOf(Intensity.MODERATE.minMmPerHour) / size.height) to GeenRegenColors.RainModerate,
                        1f to GeenRegenColors.RainLight,
                        startY = 0f,
                        endY = size.height,
                    )
                    drawPath(area, brush, alpha = 0.55f)
                    drawPath(
                        line,
                        brush,
                        style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
                    )
                }
                selected?.takeIf { it in values.indices }?.let { i ->
                    val x = xs[i]
                    drawLine(selectedColor.copy(alpha = 0.5f), Offset(x, 0f), Offset(x, size.height), 1.5.dp.toPx())
                    drawCircle(selectedColor, radius = 5.dp.toPx(), center = Offset(x, ys[i]))
                    drawCircle(surface, radius = 2.5.dp.toPx(), center = Offset(x, ys[i]))
                }
                drawLine(baseline, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx())
            }
            Column(
                modifier = Modifier
                    .height(180.dp)
                    .padding(start = 6.dp),
            ) {
                // Band labels, roughly aligned with the dashed lines.
                BandLabels(scaleTop, emptyText)
            }
        }
        Spacer(Modifier.height(6.dp))
        TimeAxis(forecast, zone)
    }
}

@Composable
private fun ColumnScope.BandLabels(scaleTop: Double, color: Color) {
    val heightDp = 180f
    var used = 0f
    for (band in Intensity.entries.drop(1).reversed()) {
        val frac = 1f - (ln(1 + band.minMmPerHour) / ln(1 + scaleTop)).toFloat()
        val target = (heightDp * frac - 8f).coerceAtLeast(used)
        Spacer(Modifier.height((target - used).dp))
        Text(
            text = if (band == Intensity.HEAVY) "zwaar" else "matig",
            style = MaterialTheme.typography.labelSmall,
            color = color,
        )
        used = target + 16f
    }
    Spacer(Modifier.weight(1f))
    Text("licht", style = MaterialTheme.typography.labelSmall, color = color)
}

@Composable
private fun TimeAxis(forecast: RainForecast, zone: ZoneId) {
    val steps = forecast.intensities.size
    Row(Modifier.fillMaxWidth().padding(end = 40.dp)) {
        val marks = listOf(0, steps / 4, steps / 2, steps * 3 / 4, steps - 1)
        marks.forEachIndexed { n, index ->
            Text(
                text = if (n == 0) "Nu" else clock.format(forecast.timeOf(index).atZone(zone)),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = when (n) {
                    0 -> TextAlign.Start
                    marks.lastIndex -> TextAlign.End
                    else -> TextAlign.Center
                },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

private const val SCALE_MIN_TOP = 20.0

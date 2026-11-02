package org.theharmen.geenregenaub.home

import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.theharmen.geenregenaub.domain.Intensity
import org.theharmen.geenregenaub.domain.RainForecast
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
 * Two-hour rain graph: one bar per 5 minutes on a logarithmic scale, so drizzle stays visible next
 * to a downpour. Dashed lines mark the light, moderate and heavy bands. Tap a bar for its time and
 * intensity.
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
                            val index = (offset.x / size.width * values.size).toInt().coerceIn(values.indices)
                            selected = if (selected == index) null else index
                        }
                    },
            ) {
                val slot = size.width / values.size
                val barWidth = slot * 0.7f
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
                values.forEachIndexed { i, v ->
                    if (v < Intensity.LIGHT.minMmPerHour) return@forEachIndexed
                    val top = yOf(v).coerceAtMost(size.height - 3.dp.toPx())
                    val color = if (selected == i) selectedColor else colorFor(v)
                    drawRoundRect(
                        color = color,
                        topLeft = Offset(i * slot + (slot - barWidth) / 2, top),
                        size = Size(barWidth, size.height - top),
                        cornerRadius = CornerRadius(barWidth / 3, barWidth / 3),
                    )
                }
                selected?.let { i ->
                    val x = i * slot + slot / 2
                    drawLine(selectedColor.copy(alpha = 0.4f), Offset(x, 0f), Offset(x, size.height), 1.dp.toPx())
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

internal fun colorFor(mmPerHour: Double): Color = when (Intensity.of(mmPerHour)) {
    Intensity.HEAVY -> GeenRegenColors.RainHeavy
    Intensity.MODERATE -> GeenRegenColors.RainModerate
    else -> GeenRegenColors.RainLight
}

private const val SCALE_MIN_TOP = 20.0

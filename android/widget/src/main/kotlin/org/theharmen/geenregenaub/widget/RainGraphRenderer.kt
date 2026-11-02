package org.theharmen.geenregenaub.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.LinearGradient
import android.graphics.Path
import android.graphics.Shader
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.createBitmap
import org.theharmen.geenregenaub.domain.Intensity
import org.theharmen.geenregenaub.domain.RainForecast
import org.theharmen.geenregenaub.domain.RainScale
import org.theharmen.geenregenaub.domain.SmoothCurve
import kotlin.math.max
import kotlin.math.min

/**
 * Draws the rain curve for the widget into a bitmap. Glance has no canvas, and a bitmap keeps
 * the graph sharp at any widget size. The shade follows intensity: light rain is a soft
 * tint of [rainColor], heavy rain is the full colour.
 */
internal object RainGraphRenderer {
    /** RemoteViews bitmaps travel over IPC; keep them well under the limit. */
    private const val MAX_WIDTH_PX = 1000
    private const val MAX_HEIGHT_PX = 400
    private const val POINTS = 25

    fun render(
        forecast: RainForecast,
        widthPx: Int,
        heightPx: Int,
        density: Float,
        rainColor: Int,
        gridColor: Int,
    ): Bitmap {
        val scale = min(1f, min(MAX_WIDTH_PX.toFloat() / max(widthPx, 1), MAX_HEIGHT_PX.toFloat() / max(heightPx, 1)))
        val w = max(1, (widthPx * scale).toInt())
        val h = max(1, (heightPx * scale).toInt())
        val d = density * scale
        val bitmap = createBitmap(w, h)
        val canvas = Canvas(bitmap)

        val baselineHeight = max(1f, 1.5f * d)
        val chartBottom = h - baselineHeight
        val grid = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = gridColor
            strokeWidth = max(1f, d)
            style = Paint.Style.STROKE
            pathEffect = android.graphics.DashPathEffect(floatArrayOf(3 * d, 3 * d), 0f)
        }
        // Guides at the moderate and heavy thresholds.
        for (band in listOf(Intensity.MODERATE, Intensity.HEAVY)) {
            val y = chartBottom - RainScale.fraction(band.minMmPerHour) * chartBottom
            canvas.drawLine(0f, y, w.toFloat(), y, grid)
        }

        // Smooth area through one point per 5 minutes; dry steps sit on the baseline.
        val values = forecast.intensities.take(POINTS)
        if (values.size >= 2 && values.any { Intensity.of(it) != null }) {
            val step = w.toFloat() / (values.size - 1)
            val xs = FloatArray(values.size) { it * step }
            val heights = FloatArray(values.size) { i ->
                val mm = values[i]
                if (Intensity.of(mm) == null) 0f else RainScale.fraction(mm) * chartBottom
            }
            val soft = SmoothCurve.soften(heights)
            val ys = FloatArray(values.size) { i -> chartBottom - soft[i] }
            val line = Path().apply {
                moveTo(xs[0], ys[0])
                for (seg in SmoothCurve.segments(xs, ys)) cubicTo(seg.c1x, seg.c1y, seg.c2x, seg.c2y, seg.x, seg.y)
            }
            val area = Path(line).apply {
                lineTo(xs.last(), chartBottom)
                lineTo(xs.first(), chartBottom)
                close()
            }
            // Shade follows height: a soft tint for drizzle, the full colour for a downpour.
            val heavyY = chartBottom - RainScale.fraction(Intensity.HEAVY.minMmPerHour) * chartBottom
            val moderateY = chartBottom - RainScale.fraction(Intensity.MODERATE.minMmPerHour) * chartBottom
            val shader = LinearGradient(
                0f, 0f, 0f, chartBottom,
                intArrayOf(
                    shade(rainColor, Intensity.HEAVY),
                    shade(rainColor, Intensity.HEAVY),
                    shade(rainColor, Intensity.MODERATE),
                    shade(rainColor, Intensity.LIGHT),
                ),
                floatArrayOf(0f, heavyY / chartBottom, moderateY / chartBottom, 1f),
                Shader.TileMode.CLAMP,
            )
            val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                this.shader = shader
                alpha = 170
            }
            val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = max(1.5f, 2f * d)
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
                this.shader = shader
            }
            canvas.drawPath(area, fill)
            canvas.drawPath(line, stroke)
        }

        val base = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = gridColor; style = Paint.Style.FILL }
        canvas.drawRect(0f, chartBottom, w.toFloat(), h.toFloat(), base)
        return bitmap
    }

    private fun shade(color: Int, intensity: Intensity): Int {
        val alpha = when (intensity) {
            Intensity.LIGHT -> 0.45f
            Intensity.MODERATE -> 0.75f
            Intensity.HEAVY -> 1f
        }
        return ColorUtils.setAlphaComponent(color, (alpha * 255).toInt())
    }
}

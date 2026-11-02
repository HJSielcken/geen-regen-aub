package org.theharmen.geenregenaub.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.createBitmap
import org.theharmen.geenregenaub.domain.Intensity
import org.theharmen.geenregenaub.domain.RainForecast
import org.theharmen.geenregenaub.domain.RainScale
import kotlin.math.max
import kotlin.math.min

/**
 * Draws the rain bars for the widget into a bitmap. Glance has no canvas, and a bitmap keeps
 * the graph smooth at any widget size. Bar shade follows intensity: light rain is a soft
 * tint of [rainColor], heavy rain is the full colour.
 */
internal object RainGraphRenderer {
    /** RemoteViews bitmaps travel over IPC; keep them well under the limit. */
    private const val MAX_WIDTH_PX = 1000
    private const val MAX_HEIGHT_PX = 400
    private const val BARS = 24

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

        val values = forecast.intensities.take(BARS)
        val slot = w.toFloat() / BARS
        val gap = min(slot * 0.25f, 3f * d)
        val radius = min((slot - gap) / 2f, 4f * d)
        val minBar = 3f * d
        val bar = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        val rect = RectF()
        values.forEachIndexed { i, mm ->
            val intensity = Intensity.of(mm) ?: return@forEachIndexed
            val fraction = RainScale.fraction(mm)
            val barHeight = max(minBar, fraction * chartBottom)
            bar.color = shade(rainColor, intensity)
            val left = i * slot + gap / 2f
            rect.set(left, chartBottom - barHeight, left + slot - gap, chartBottom + radius)
            canvas.save()
            canvas.clipRect(0f, 0f, w.toFloat(), chartBottom)
            canvas.drawRoundRect(rect, radius, radius, bar)
            canvas.restore()
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

package org.theharmen.geenregenaub.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.createBitmap
import org.theharmen.geenregenaub.domain.AirQualityLevel
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Bitmaps for the air quality, sun, moon and wind widgets. Glance has no canvas; drawing at the
 * widget's own size in pixels keeps them sharp. Each is capped so the RemoteViews stay small.
 */
internal object SkyRenderer {
    private const val MAX_SIDE_PX = 720

    /** A canvas of about [widthPx] × [heightPx], scaled down if needed; [d] is px per dp on it. */
    private class Sheet(widthPx: Int, heightPx: Int, density: Float) {
        val scale = min(1f, MAX_SIDE_PX.toFloat() / max(1, max(widthPx, heightPx)))
        val w = max(1, (widthPx * scale).toInt())
        val h = max(1, (heightPx * scale).toInt())
        val d = density * scale
        val bitmap = createBitmap(w, h)
        val canvas = Canvas(bitmap)
    }

    private fun paint(color: Int, fill: Boolean = true) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        style = if (fill) Paint.Style.FILL else Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    /** EEA colours for the six air quality bands. */
    fun airColor(index: Int): Int = when (AirQualityLevel.of(index)) {
        AirQualityLevel.GOOD -> 0xFF50F0E6.toInt()
        AirQualityLevel.FAIR -> 0xFF50CCAA.toInt()
        AirQualityLevel.MODERATE -> 0xFFF0E641.toInt()
        AirQualityLevel.POOR -> 0xFFFF5050.toInt()
        AirQualityLevel.VERY_POOR -> 0xFF960032.toInt()
        AirQualityLevel.EXTREMELY_POOR -> 0xFF7D2181.toInt()
    }

    fun dot(sizePx: Int, color: Int): Bitmap {
        val s = Sheet(sizePx, sizePx, 1f)
        s.canvas.drawCircle(s.w / 2f, s.h / 2f, min(s.w, s.h) / 2f, paint(color))
        return s.bitmap
    }

    /** One bar per hour, coloured by its band, on a baseline. */
    fun airBars(values: List<Int>, widthPx: Int, heightPx: Int, density: Float, baseColor: Int): Bitmap {
        val s = Sheet(widthPx, heightPx, density)
        val baseline = max(1f, s.d)
        val bottom = s.h - baseline
        s.canvas.drawRect(0f, bottom, s.w.toFloat(), s.h.toFloat(), paint(baseColor))
        if (values.isEmpty()) return s.bitmap
        // At least 50 on the scale, so a clean day does not look alarming.
        val top = max(50, values.max()).toFloat()
        val slot = s.w.toFloat() / values.size
        val gap = min(slot * 0.25f, 2 * s.d)
        values.forEachIndexed { i, v ->
            val barHeight = max(s.d, v / top * (bottom - s.d))
            val left = i * slot + gap / 2
            val right = (i + 1) * slot - gap / 2
            val radius = min((right - left) / 2, 2 * s.d)
            s.canvas.drawRoundRect(RectF(left, bottom - barHeight, right, bottom + radius), radius, radius, paint(airColor(v)))
        }
        // Clean up the rounding below the baseline.
        s.canvas.drawRect(0f, bottom, s.w.toFloat(), s.h.toFloat(), paint(baseColor))
        return s.bitmap
    }

    /** The 0–100+ scale from good to extremely poor, with a marker at [index]. */
    fun airScale(index: Int, widthPx: Int, heightPx: Int, density: Float, markerColor: Int, ringColor: Int): Bitmap {
        val s = Sheet(widthPx, heightPx, density)
        val track = min(4 * s.d, s.h / 2f)
        val r = min(s.h / 2f, 6 * s.d)
        val left = r
        val right = s.w - r
        val cy = s.h / 2f
        val stops = listOf(0, 20, 40, 60, 80, 100)
        val gradient = paint(0).apply {
            shader = LinearGradient(
                left, 0f, right, 0f,
                stops.map { airColor(max(it, 1)) }.toIntArray(),
                stops.map { it / 100f }.toFloatArray(),
                Shader.TileMode.CLAMP,
            )
        }
        s.canvas.drawRoundRect(RectF(left, cy - track / 2, right, cy + track / 2), track / 2, track / 2, gradient)
        val x = left + (right - left) * (index.coerceIn(0, 100) / 100f)
        s.canvas.drawCircle(x, cy, r, paint(ringColor))
        s.canvas.drawCircle(x, cy, r - 1.5f * s.d, paint(markerColor))
        return s.bitmap
    }

    /**
     * The sun's path as a dashed half-ellipse over a horizon, the part already travelled drawn
     * solid, and the sun where it is now. [progress] is 0 at sunrise and 1 at sunset; null is night.
     */
    fun sunArc(progress: Float?, widthPx: Int, heightPx: Int, density: Float, lineColor: Int, sunColor: Int): Bitmap {
        val s = Sheet(widthPx, heightPx, density)
        val sunR = 6 * s.d
        val horizon = s.h - 2 * s.d
        val left = sunR + 2 * s.d
        val right = s.w - sunR - 2 * s.d
        val rx = (right - left) / 2
        val ry = max(1f, min(horizon - sunR - 2 * s.d, rx))
        val cx = (left + right) / 2
        val oval = RectF(cx - rx, horizon - ry, cx + rx, horizon + ry)

        val dashed = paint(ColorUtils.setAlphaComponent(lineColor, 140), fill = false).apply {
            strokeWidth = max(1f, 1.5f * s.d)
            pathEffect = DashPathEffect(floatArrayOf(4 * s.d, 4 * s.d), 0f)
        }
        s.canvas.drawArc(oval, 180f, 180f, false, dashed)
        s.canvas.drawLine(0f, horizon, s.w.toFloat(), horizon, paint(ColorUtils.setAlphaComponent(lineColor, 90), fill = false).apply { strokeWidth = max(1f, s.d) })

        // The ends of the day, as in the screenshot: a small dot at sunrise and sunset.
        val endDot = paint(sunColor)
        s.canvas.drawCircle(left, horizon, 3 * s.d, endDot)
        s.canvas.drawCircle(right, horizon, 3 * s.d, endDot)

        if (progress != null) {
            val p = progress.coerceIn(0f, 1f)
            val sweep = 180f * p
            val travelled = Path().apply { arcTo(oval, 180f, sweep) }
            val area = Path(travelled).apply {
                val angle = PI * (1 - p)
                lineTo((cx + rx * cos(angle)).toFloat(), horizon)
                close()
            }
            s.canvas.drawPath(area, paint(ColorUtils.setAlphaComponent(sunColor, 50)))
            s.canvas.drawPath(travelled, paint(sunColor, fill = false).apply { strokeWidth = max(1.5f, 2 * s.d) })
            val angle = PI * (1 - p)
            val x = (cx + rx * cos(angle)).toFloat()
            val y = (horizon - ry * sin(angle)).toFloat()
            s.canvas.drawCircle(x, y, sunR * 1.6f, paint(ColorUtils.setAlphaComponent(sunColor, 70)))
            s.canvas.drawCircle(x, y, sunR, paint(sunColor))
        }
        return s.bitmap
    }

    /**
     * The moon at [age] (0 new, 0.5 full): the dark disc with the lit part on top, lit from the
     * right while waxing, as seen from the northern hemisphere.
     */
    fun moon(age: Double, sizePx: Int, litColor: Int, darkColor: Int): Bitmap {
        val s = Sheet(sizePx, sizePx, 1f)
        val r = min(s.w, s.h) / 2f - 1f
        val cx = s.w / 2f
        val cy = s.h / 2f
        s.canvas.drawCircle(cx, cy, r, paint(darkColor))
        val phase = 2 * PI * age
        val illumination = (1 - cos(phase)) / 2
        if (illumination < 0.01) return s.bitmap
        if (illumination > 0.99) {
            s.canvas.drawCircle(cx, cy, r, paint(litColor))
            return s.bitmap
        }
        val waxing = age < 0.5
        val rx = (r * abs(cos(phase))).toFloat()
        val lit = Path().apply {
            // The lit limb: top → right → bottom.
            arcTo(RectF(cx - r, cy - r, cx + r, cy + r), -90f, 180f)
            // Back up along the terminator: bulging right for a crescent, left for a gibbous moon.
            arcTo(RectF(cx - rx, cy - r, cx + rx, cy + r), 90f, if (illumination < 0.5) -180f else 180f)
            close()
        }
        s.canvas.save()
        if (!waxing) s.canvas.scale(-1f, 1f, cx, cy)
        s.canvas.drawPath(lit, paint(litColor))
        s.canvas.restore()
        return s.bitmap
    }

    /**
     * A compass ring with N/O/Z/W and an arrow that points where the wind blows to. [fromDegrees]
     * is where it comes from, as the forecast gives it; null draws the ring alone.
     */
    fun compass(fromDegrees: Double?, sizePx: Int, density: Float, ringColor: Int, arrowColor: Int, labelColor: Int): Bitmap {
        val s = Sheet(sizePx, sizePx, density)
        val cx = s.w / 2f
        val cy = s.h / 2f
        val r = min(s.w, s.h) / 2f - 1f
        val label = paint(labelColor).apply {
            textSize = max(8f * s.d, r * 0.22f)
            textAlign = Paint.Align.CENTER
            isFakeBoldText = true
        }
        val textR = r - label.textSize * 0.65f
        val ringR = textR - label.textSize * 0.9f
        s.canvas.drawCircle(cx, cy, ringR, paint(ColorUtils.setAlphaComponent(ringColor, 110), fill = false).apply { strokeWidth = max(1f, 1.5f * s.d) })
        // Ticks every 45°.
        val tick = paint(ColorUtils.setAlphaComponent(ringColor, 160), fill = false).apply { strokeWidth = max(1f, s.d) }
        for (i in 0 until 8) {
            val a = Math.toRadians(i * 45.0 - 90)
            val inner = ringR - 3 * s.d
            s.canvas.drawLine(
                (cx + inner * cos(a)).toFloat(), (cy + inner * sin(a)).toFloat(),
                (cx + ringR * cos(a)).toFloat(), (cy + ringR * sin(a)).toFloat(), tick,
            )
        }
        val baselineShift = -(label.fontMetrics.ascent + label.fontMetrics.descent) / 2
        listOf("N" to 0.0, "O" to 90.0, "Z" to 180.0, "W" to 270.0).forEach { (text, deg) ->
            val a = Math.toRadians(deg - 90)
            s.canvas.drawText(text, (cx + textR * cos(a)).toFloat(), (cy + textR * sin(a)).toFloat() + baselineShift, label)
        }
        if (fromDegrees != null) {
            s.canvas.save()
            // Blowing towards fromDegrees + 180; the arrow is drawn pointing up (north).
            s.canvas.rotate((fromDegrees + 180).toFloat(), cx, cy)
            val len = ringR * 0.8f
            val head = ringR * 0.38f
            val arrow = Path().apply {
                moveTo(cx, cy - len)
                lineTo(cx + head * 0.6f, cy - len + head)
                lineTo(cx + head * 0.18f, cy - len + head * 0.8f)
                lineTo(cx + head * 0.18f, cy + len)
                lineTo(cx - head * 0.18f, cy + len)
                lineTo(cx - head * 0.18f, cy - len + head * 0.8f)
                lineTo(cx - head * 0.6f, cy - len + head)
                close()
            }
            s.canvas.drawPath(arrow, paint(arrowColor))
            s.canvas.restore()
        }
        return s.bitmap
    }
}

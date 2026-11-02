package org.theharmen.geenregenaub.domain

import kotlin.math.sqrt

/** One cubic Bézier piece from the previous point to ([x], [y]) via two control points. */
data class CurveSegment(
    val c1x: Float,
    val c1y: Float,
    val c2x: Float,
    val c2y: Float,
    val x: Float,
    val y: Float,
)

/**
 * Smooth curve through points with a monotone cubic (Fritsch–Carlson), so the line never
 * overshoots: a dry stretch stays flat on zero and a peak never draws higher than its value.
 * Drawing code starts a path at the first point and adds one cubic per segment.
 */
object SmoothCurve {
    fun segments(xs: FloatArray, ys: FloatArray): List<CurveSegment> {
        require(xs.size == ys.size) { "xs and ys differ in size" }
        val n = xs.size
        if (n < 2) return emptyList()

        val slopes = FloatArray(n - 1) { i -> (ys[i + 1] - ys[i]) / (xs[i + 1] - xs[i]) }
        val tangents = FloatArray(n)
        tangents[0] = slopes[0]
        tangents[n - 1] = slopes[n - 2]
        for (i in 1 until n - 1) {
            tangents[i] = if (slopes[i - 1] * slopes[i] <= 0f) 0f else (slopes[i - 1] + slopes[i]) / 2f
        }
        for (i in 0 until n - 1) {
            if (slopes[i] == 0f) {
                tangents[i] = 0f
                tangents[i + 1] = 0f
                continue
            }
            val a = tangents[i] / slopes[i]
            val b = tangents[i + 1] / slopes[i]
            val s = a * a + b * b
            if (s > 9f) {
                val t = 3f / sqrt(s)
                tangents[i] = t * a * slopes[i]
                tangents[i + 1] = t * b * slopes[i]
            }
        }
        return List(n - 1) { i ->
            val h = (xs[i + 1] - xs[i]) / 3f
            CurveSegment(
                c1x = xs[i] + h,
                c1y = ys[i] + tangents[i] * h,
                c2x = xs[i + 1] - h,
                c2y = ys[i + 1] - tangents[i + 1] * h,
                x = xs[i + 1],
                y = ys[i + 1],
            )
        }
    }

    /** Index of the point nearest to [x] when [count] points are spread evenly over [width]. */
    fun nearestIndex(x: Float, width: Float, count: Int): Int {
        if (count <= 1 || width <= 0f) return 0
        val step = width / (count - 1)
        return (x / step + 0.5f).toInt().coerceIn(0, count - 1)
    }

}

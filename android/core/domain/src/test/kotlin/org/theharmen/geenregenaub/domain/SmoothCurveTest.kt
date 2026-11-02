package org.theharmen.geenregenaub.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SmoothCurveTest {
    private val xs = floatArrayOf(0f, 1f, 2f, 3f, 4f, 5f, 6f)
    private val ys = floatArrayOf(0f, 0f, 2f, 10f, 3f, 0f, 0f)

    @Test
    fun `passes through every point`() {
        val segments = SmoothCurve.segments(xs, ys)
        assertEquals(xs.size - 1, segments.size)
        segments.forEachIndexed { i, s ->
            assertEquals(xs[i + 1], s.x, 0f)
            assertEquals(ys[i + 1], s.y, 0f)
        }
    }

    @Test
    fun `never overshoots below zero or above the peak`() {
        for (s in SmoothCurve.segments(xs, ys)) {
            assertTrue(s.c1y >= 0f && s.c2y >= 0f)
            assertTrue(s.c1y <= 10f && s.c2y <= 10f)
        }
    }

    @Test
    fun `dry stretches stay flat`() {
        val first = SmoothCurve.segments(xs, ys).first()
        assertEquals(0f, first.c1y, 0f)
        assertEquals(0f, first.c2y, 0f)
    }

    @Test
    fun `nearest index snaps to the closest point`() {
        assertEquals(0, SmoothCurve.nearestIndex(-5f, 240f, 25))
        assertEquals(0, SmoothCurve.nearestIndex(4.9f, 240f, 25))
        assertEquals(1, SmoothCurve.nearestIndex(5.1f, 240f, 25))
        assertEquals(24, SmoothCurve.nearestIndex(500f, 240f, 25))
    }
}

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

    @Test
    fun `soften tapers a step but keeps the peak height`() {
        val step = FloatArray(25) { if (it in 7..18) 10f else 0f }
        val soft = SmoothCurve.soften(step)
        assertEquals(10f, soft.max(), 1e-4f)
        // Fades in over the steps around the edge instead of jumping from 0 to 10.
        assertTrue(soft[6] > 0f && soft[6] < 5f)
        assertTrue(soft[7] > soft[6] && soft[7] < 10f)
        // Far from the shower it stays dry.
        assertEquals(0f, soft[0], 0f)
        assertEquals(0f, soft[24], 0f)
    }

    @Test
    fun `soften leaves a dry row dry`() {
        val dry = FloatArray(25)
        assertTrue(SmoothCurve.soften(dry).all { it == 0f })
    }
}

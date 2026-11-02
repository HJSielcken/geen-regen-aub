package org.theharmen.geenregenaub.home

import org.junit.Assert.assertEquals
import org.junit.Test
import org.theharmen.geenregenaub.data.Place

class FormattingTest {
    @Test
    fun `short place names`() {
        assertEquals("Utrecht", Place("Utrecht, Utrecht, Utrecht", 52.1, 5.1, false).shortName())
        assertEquals("3511AA Utrecht", Place("3511AA Utrecht", 52.1, 5.1, false).shortName())
    }

    @Test
    fun `intensity uses a Dutch decimal comma`() {
        assertEquals("1,8 mm/u", mmPerHour(1.8))
    }
}

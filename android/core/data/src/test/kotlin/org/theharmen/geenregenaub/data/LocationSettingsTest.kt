package org.theharmen.geenregenaub.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class LocationSettingsTest {
    private val now = Instant.parse("2026-11-18T11:00:00Z")
    private val leusden = Place("Leusden", 52.13, 5.43, isCurrentLocation = false)

    @Test
    fun temporaryPlaceCountsUntilItEnds() {
        val settings = LocationSettings(temporaryPlace = leusden, temporaryUntil = now.plusSeconds(60))
        assertEquals(leusden, settings.activeTemporary(now))
        assertEquals(leusden, settings.activeTemporary(now.plusSeconds(59)))
    }

    @Test
    fun temporaryPlaceIsGoneAfterItsEnd() {
        val settings = LocationSettings(temporaryPlace = leusden, temporaryUntil = now.plusSeconds(60))
        assertNull(settings.activeTemporary(now.plusSeconds(60)))
        assertNull(settings.activeTemporary(now.plusSeconds(3600)))
    }

    @Test
    fun noEndTimeMeansNoTemporaryPlace() {
        assertNull(LocationSettings(temporaryPlace = leusden).activeTemporary(now))
        assertNull(LocationSettings().activeTemporary(now))
    }
}

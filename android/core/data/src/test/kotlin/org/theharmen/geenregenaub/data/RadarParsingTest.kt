package org.theharmen.geenregenaub.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.theharmen.geenregenaub.data.api.RainApiException
import org.theharmen.geenregenaub.data.radar.RelayRadarRepository
import java.time.Instant

class RadarParsingTest {
    @Test
    fun `parses the radar frames contract`() {
        val body = """
            {"issued_at":"2026-11-02T21:00:00Z",
             "bounds":{"north":55.97,"south":48.9,"east":10.86,"west":0.0},
             "width":700,"height":1010,"interval_minutes":5,
             "frames":[
               {"time":"2026-11-02T20:55:00Z","kind":"observed","url":"/v1/radar/202611022055-202611022055.png"},
               {"time":"2026-11-02T21:00:00Z","kind":"observed","url":"/v1/radar/202611022100-202611022100.png"},
               {"time":"2026-11-02T21:05:00Z","kind":"forecast","url":"/v1/radar/202611022100-202611022105.png"}
             ],
             "legend":[{"min_mm_per_hour":0.1,"color":"#8db8f596"}],
             "attribution":"Radardata: KNMI, CC BY 4.0","extra":1}
        """.trimIndent()
        val frames = RelayRadarRepository.parse(body)
        assertEquals(Instant.parse("2026-11-02T21:00:00Z"), frames.issuedAt)
        assertEquals(55.97, frames.bounds.north, 0.0)
        assertEquals(3, frames.frames.size)
        assertTrue(frames.frames[1].observed)
        assertFalse(frames.frames[2].observed)
        assertEquals("/v1/radar/202611022100-202611022105.png", frames.frames[2].path)
        assertEquals(0x968DB8F5.toInt(), frames.legend[0].argb)
    }

    @Test(expected = RainApiException::class)
    fun `rejects a broken body`() {
        RelayRadarRepository.parse("""{"frames":[]}""")
    }
}

package org.theharmen.geenregenaub.map

/**
 * The base map: PDOK's grey BRT Achtergrondkaart, the Dutch government's free, keyless map of the
 * Netherlands (Kadaster, CC BY 4.0). Dark mode inverts it so the radar colours still stand out.
 * https://www.pdok.nl/introductie/-/article/basisregistratie-topografie-achtergrondkaarten-brt-a-
 */
internal object BaseMap {
    private const val TILES = "https://service.pdok.nl/brt/achtergrondkaart/wmts/v2_0/grijs/EPSG:3857/{z}/{x}/{y}.png"

    fun styleJson(dark: Boolean): String {
        val background = if (dark) "#1b1d21" else "#eef0f2"
        val paint = if (dark) {
            """{"raster-brightness-min":1,"raster-brightness-max":0.12,"raster-saturation":-1,"raster-contrast":0.1}"""
        } else {
            """{"raster-opacity":0.9}"""
        }
        return """
            {
              "version": 8,
              "sources": {
                "pdok": {
                  "type": "raster",
                  "tiles": ["$TILES"],
                  "tileSize": 256,
                  "minzoom": 0,
                  "maxzoom": 19,
                  "attribution": "Kaart: Kadaster / PDOK"
                }
              },
              "layers": [
                {"id": "background", "type": "background", "paint": {"background-color": "$background"}},
                {"id": "pdok", "type": "raster", "source": "pdok", "paint": $paint}
              ]
            }
        """.trimIndent()
    }
}

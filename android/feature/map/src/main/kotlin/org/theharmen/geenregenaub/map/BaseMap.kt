package org.theharmen.geenregenaub.map

/**
 * The base map: OpenFreeMap's worldwide vector styles (OpenStreetMap data, ODbL; free, no key, no
 * usage limits), "liberty" in light mode and "dark" in dark mode. Tiles are fetched on demand, so
 * the APK does not grow. https://openfreemap.org/
 *
 * If that style cannot be loaded, [fallbackJson] gives PDOK's grey BRT Achtergrondkaart, the Dutch
 * government's free, keyless map of the Netherlands (Kadaster, CC BY 4.0).
 */
internal object BaseMap {
    fun styleUri(dark: Boolean): String =
        "https://tiles.openfreemap.org/styles/" + if (dark) "dark" else "liberty"

    private const val TILES = "https://service.pdok.nl/brt/achtergrondkaart/wmts/v2_0/grijs/EPSG:3857/{z}/{x}/{y}.png"

    fun fallbackJson(dark: Boolean): String {
        val background = if (dark) "#2b2e33" else "#eef0f2"
        val paint = if (dark) {
            """{"raster-brightness-min":1,"raster-brightness-max":0.28,"raster-saturation":-1,"raster-contrast":0.1}"""
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

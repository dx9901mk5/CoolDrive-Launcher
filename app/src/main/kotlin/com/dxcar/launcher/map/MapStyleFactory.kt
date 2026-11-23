package com.dxcar.launcher.map

import com.dxcar.launcher.data.MapEngineStyle

/**
 * Zero-API-Key map styles for MapLibre OpenGL:
 * 1. GOOGLE_TRAFFIC_TILES:
 *    - Authentic Google Maps HD (scale=2) Standard tiles (zh-TW), with toggleable Real-Time Traffic.
 *    - Night Mode applies GPU Hue-Preserving Night ColorMatrix on TextureView.
 * 2. GOOGLE_SATELLITE_HYBRID:
 *    - Authentic Google Maps Satellite Hybrid tiles (zh-TW), with toggleable Real-Time Traffic.
 * 3. OPENFREEMAP_VECTOR_3D:
 *    - Day: OpenFreeMap Liberty 3D Vector (3D buildings, crisp scaling at any car DPI)
 *    - Night: OpenFreeMap Dark Vector
 * 4. OPENFREEMAP_DARK:
 *    - Always-dark clean vector map (zero API key, zero watermarks — replaces CartoDB).
 */
object MapStyleFactory {

    const val OPENFREEMAP_DAY_URL = "https://tiles.openfreemap.org/styles/liberty"
    const val OPENFREEMAP_NIGHT_URL = "https://tiles.openfreemap.org/styles/dark"

    sealed interface ResolvedMapStyle {
        data class StyleUrl(val url: String) : ResolvedMapStyle
        data class StyleJson(val json: String) : ResolvedMapStyle
    }

    fun resolve(
        engineStyle: MapEngineStyle,
        isNight: Boolean,
        trafficEnabled: Boolean = true
    ): ResolvedMapStyle {
        return when (engineStyle) {
            MapEngineStyle.OPENFREEMAP_VECTOR_3D -> {
                ResolvedMapStyle.StyleUrl(
                    if (isNight) OPENFREEMAP_NIGHT_URL else OPENFREEMAP_DAY_URL
                )
            }
            MapEngineStyle.OPENFREEMAP_DARK -> {
                ResolvedMapStyle.StyleUrl(OPENFREEMAP_NIGHT_URL)
            }
            MapEngineStyle.GOOGLE_SATELLITE_HYBRID -> {
                val lyrs = if (trafficEnabled) "y,traffic" else "y"
                ResolvedMapStyle.StyleJson(buildGoogleTileJson("DX-Car Google Satellite Hybrid", lyrs))
            }
            MapEngineStyle.GOOGLE_TRAFFIC_TILES -> {
                val lyrs = if (trafficEnabled) "m,traffic" else "m"
                ResolvedMapStyle.StyleJson(buildGoogleTileJson("DX-Car Google Standard HD", lyrs))
            }
        }
    }

    private fun buildGoogleTileJson(name: String, lyrs: String): String = """
        {
          "version": 8,
          "name": "$name",
          "sources": {
            "gmaps-hd": {
              "type": "raster",
              "tiles": [
                "https://mt0.google.com/vt/lyrs=$lyrs&hl=zh-TW&gl=TW&scale=2&x={x}&y={y}&z={z}",
                "https://mt1.google.com/vt/lyrs=$lyrs&hl=zh-TW&gl=TW&scale=2&x={x}&y={y}&z={z}",
                "https://mt2.google.com/vt/lyrs=$lyrs&hl=zh-TW&gl=TW&scale=2&x={x}&y={y}&z={z}",
                "https://mt3.google.com/vt/lyrs=$lyrs&hl=zh-TW&gl=TW&scale=2&x={x}&y={y}&z={z}"
              ],
              "tileSize": 256
            }
          },
          "layers": [
            {
              "id": "gmaps-hd-layer",
              "type": "raster",
              "source": "gmaps-hd",
              "minzoom": 0,
              "maxzoom": 20
            }
          ]
        }
    """.trimIndent()
}

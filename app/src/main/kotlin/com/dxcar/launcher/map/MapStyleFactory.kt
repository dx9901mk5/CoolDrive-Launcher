package com.dxcar.launcher.map

import com.dxcar.launcher.data.MapEngineStyle

/**
 * Zero-API-Key map styles for MapLibre OpenGL:
 * 1. GOOGLE_TRAFFIC_TILES:
 *    - Uses 100% authentic Google Maps HD (scale=2) Standard + Real-Time Traffic tiles (zh-TW)
 *      with real Taiwan POIs, stores, stations, and live traffic!
 *    - In Night Mode, MapLibrePane applies a GPU Hue-Preserving Night ColorMatrix on TextureView
 *      so white roads/land become deep Google Maps Night Navy while parks stay green, water stays
 *      blue, traffic stays red/yellow/green, and all real Google Maps Chinese POIs remain crystal clear!
 * 2. OPENFREEMAP_VECTOR_3D:
 *    - Day: OpenFreeMap Liberty 3D Vector (3D buildings, crisp scaling at any car DPI)
 *    - Night: OpenFreeMap Dark Vector
 */
object MapStyleFactory {

    const val OPENFREEMAP_DAY_URL = "https://tiles.openfreemap.org/styles/liberty"
    const val OPENFREEMAP_NIGHT_URL = "https://tiles.openfreemap.org/styles/dark"

    sealed interface ResolvedMapStyle {
        data class StyleUrl(val url: String) : ResolvedMapStyle
        data class StyleJson(val json: String) : ResolvedMapStyle
    }

    fun resolve(engineStyle: MapEngineStyle, isNight: Boolean): ResolvedMapStyle {
        return when (engineStyle) {
            MapEngineStyle.OPENFREEMAP_VECTOR_3D -> {
                ResolvedMapStyle.StyleUrl(
                    if (isNight) OPENFREEMAP_NIGHT_URL else OPENFREEMAP_DAY_URL
                )
            }
            MapEngineStyle.GOOGLE_TRAFFIC_TILES -> {
                ResolvedMapStyle.StyleJson(GOOGLE_HD_TRAFFIC_JSON)
            }
        }
    }

    private val GOOGLE_HD_TRAFFIC_JSON = """
        {
          "version": 8,
          "name": "DX-Car Launcher Authentic Google Traffic HD",
          "sources": {
            "gmaps-hd": {
              "type": "raster",
              "tiles": [
                "https://mt0.google.com/vt/lyrs=m,traffic&hl=zh-TW&gl=TW&scale=2&x={x}&y={y}&z={z}",
                "https://mt1.google.com/vt/lyrs=m,traffic&hl=zh-TW&gl=TW&scale=2&x={x}&y={y}&z={z}",
                "https://mt2.google.com/vt/lyrs=m,traffic&hl=zh-TW&gl=TW&scale=2&x={x}&y={y}&z={z}",
                "https://mt3.google.com/vt/lyrs=m,traffic&hl=zh-TW&gl=TW&scale=2&x={x}&y={y}&z={z}"
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

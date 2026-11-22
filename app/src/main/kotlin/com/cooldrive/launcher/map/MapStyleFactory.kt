package com.cooldrive.launcher.map

import com.cooldrive.launcher.data.MapEngineStyle

/**
 * Zero-API-Key map styles for MapLibre OpenGL:
 * 1. GOOGLE_TRAFFIC_TILES:
 *    - Day: Google Maps Standard + Real-Time Traffic layer (Traditional Chinese zh-TW)
 *    - Night: Low-glare Dark Matter vector/raster navigation dark style (prevents night glare)
 *      with live Google Traffic overlay on top!
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
                if (isNight) {
                    ResolvedMapStyle.StyleJson(NIGHT_DARK_TRAFFIC_JSON)
                } else {
                    ResolvedMapStyle.StyleJson(DAY_GOOGLE_TRAFFIC_JSON)
                }
            }
        }
    }

    private val DAY_GOOGLE_TRAFFIC_JSON = """
        {
          "version": 8,
          "name": "CoolDrive Google Day Traffic",
          "sources": {
            "gmaps-day": {
              "type": "raster",
              "tiles": [
                "https://mt0.google.com/vt/lyrs=m,traffic&hl=zh-TW&gl=TW&x={x}&y={y}&z={z}",
                "https://mt1.google.com/vt/lyrs=m,traffic&hl=zh-TW&gl=TW&x={x}&y={y}&z={z}",
                "https://mt2.google.com/vt/lyrs=m,traffic&hl=zh-TW&gl=TW&x={x}&y={y}&z={z}",
                "https://mt3.google.com/vt/lyrs=m,traffic&hl=zh-TW&gl=TW&x={x}&y={y}&z={z}"
              ],
              "tileSize": 256
            }
          },
          "layers": [
            {
              "id": "gmaps-day-layer",
              "type": "raster",
              "source": "gmaps-day",
              "minzoom": 0,
              "maxzoom": 20
            }
          ]
        }
    """.trimIndent()

    // Anti-glare automotive dark base + translucent Google real-time traffic road layer
    private val NIGHT_DARK_TRAFFIC_JSON = """
        {
          "version": 8,
          "name": "CoolDrive Night Navigation",
          "sources": {
            "dark-base": {
              "type": "raster",
              "tiles": [
                "https://a.basemaps.cartocdn.com/dark_all/{z}/{x}/{y}@2x.png",
                "https://b.basemaps.cartocdn.com/dark_all/{z}/{x}/{y}@2x.png",
                "https://c.basemaps.cartocdn.com/dark_all/{z}/{x}/{y}@2x.png"
              ],
              "tileSize": 256
            },
            "gmaps-night-tint": {
              "type": "raster",
              "tiles": [
                "https://mt0.google.com/vt/lyrs=m,traffic&hl=zh-TW&gl=TW&x={x}&y={y}&z={z}",
                "https://mt1.google.com/vt/lyrs=m,traffic&hl=zh-TW&gl=TW&x={x}&y={y}&z={z}"
              ],
              "tileSize": 256
            }
          },
          "layers": [
            {
              "id": "dark-base-layer",
              "type": "raster",
              "source": "dark-base",
              "paint": {
                "raster-brightness-max": 0.92,
                "raster-contrast": 0.12
              }
            },
            {
              "id": "gmaps-night-overlay",
              "type": "raster",
              "source": "gmaps-night-tint",
              "paint": {
                "raster-opacity": 0.28,
                "raster-brightness-max": 0.65,
                "raster-saturation": -0.15
              }
            }
          ]
        }
    """.trimIndent()
}

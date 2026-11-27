package com.dxcar.launcher.map

import com.dxcar.launcher.data.MapEngineStyle

/**
 * Zero-API-Key map styles for MapLibre OpenGL:
 * - Always mounts canonical offline-cached base raster source (`lyrs=m` or `lyrs=y`) underneath so
 *   offline tiles in `mbgl-offline.db` render 100% when without internet.
 * - When `trafficEnabled == true`, mounts live composite Google Traffic tiles (`lyrs=m,traffic` or
 *   `lyrs=y,traffic`) right above it so live green/orange/red road traffic renders cleanly online!
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
            MapEngineStyle.GOOGLE_SATELLITE_HYBRID -> {
                ResolvedMapStyle.StyleJson(
                    buildGoogleTileJson(
                        name = "DX-Car Google Satellite Hybrid",
                        baseLyrs = "y",
                        includeTrafficOverlay = trafficEnabled
                    )
                )
            }
            MapEngineStyle.GOOGLE_TRAFFIC_TILES,
            MapEngineStyle.OPENFREEMAP_VECTOR_3D,
            MapEngineStyle.OPENFREEMAP_DARK -> {
                ResolvedMapStyle.StyleJson(
                    buildGoogleTileJson(
                        name = "DX-Car Google Standard HD",
                        baseLyrs = "m",
                        includeTrafficOverlay = trafficEnabled
                    )
                )
            }
        }
    }

    fun buildOfflineCompatibleGoogleStyleJson(baseLyrs: String): String {
        return buildGoogleTileJson(
            name = "DX-Car Offline Google ($baseLyrs)",
            baseLyrs = baseLyrs,
            includeTrafficOverlay = false
        )
    }

    private fun buildGoogleTileJson(
        name: String,
        baseLyrs: String,
        includeTrafficOverlay: Boolean
    ): String {
        val liveTrafficLyrs = "$baseLyrs,traffic"
        val trafficSourceBlock = if (includeTrafficOverlay) {
            """,
            "gmaps-traffic": {
              "type": "raster",
              "tiles": [
                "https://mt0.google.com/vt/lyrs=$liveTrafficLyrs&hl=zh-TW&gl=TW&scale=2&x={x}&y={y}&z={z}",
                "https://mt1.google.com/vt/lyrs=$liveTrafficLyrs&hl=zh-TW&gl=TW&scale=2&x={x}&y={y}&z={z}",
                "https://mt2.google.com/vt/lyrs=$liveTrafficLyrs&hl=zh-TW&gl=TW&scale=2&x={x}&y={y}&z={z}",
                "https://mt3.google.com/vt/lyrs=$liveTrafficLyrs&hl=zh-TW&gl=TW&scale=2&x={x}&y={y}&z={z}"
              ],
              "tileSize": 256
            }"""
        } else {
            ""
        }

        val trafficLayerBlock = if (includeTrafficOverlay) {
            """,
            {
              "id": "gmaps-traffic-layer",
              "type": "raster",
              "source": "gmaps-traffic",
              "minzoom": 0,
              "maxzoom": 20
            }"""
        } else {
            ""
        }

        return """
        {
          "version": 8,
          "name": "$name",
          "sources": {
            "gmaps-hd": {
              "type": "raster",
              "tiles": [
                "https://mt0.google.com/vt/lyrs=$baseLyrs&hl=zh-TW&gl=TW&scale=2&x={x}&y={y}&z={z}",
                "https://mt1.google.com/vt/lyrs=$baseLyrs&hl=zh-TW&gl=TW&scale=2&x={x}&y={y}&z={z}",
                "https://mt2.google.com/vt/lyrs=$baseLyrs&hl=zh-TW&gl=TW&scale=2&x={x}&y={y}&z={z}",
                "https://mt3.google.com/vt/lyrs=$baseLyrs&hl=zh-TW&gl=TW&scale=2&x={x}&y={y}&z={z}"
              ],
              "tileSize": 256
            }$trafficSourceBlock
          },
          "layers": [
            {
              "id": "gmaps-hd-layer",
              "type": "raster",
              "source": "gmaps-hd",
              "minzoom": 0,
              "maxzoom": 20
            }$trafficLayerBlock
          ]
        }
        """.trimIndent()
    }
}

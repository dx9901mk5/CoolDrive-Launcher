package com.dxcar.launcher.map

import com.dxcar.launcher.data.MapEngineStyle

/**
 * Zero-API-Key map styles for MapLibre OpenGL:
 * - Base raster tiles (lyrs=m or lyrs=y) are decoupled from the transparent traffic overlay (lyrs=traffic),
 *   guaranteeing 100% URL parity with OfflineMapManager's cached tiles in mbgl-offline.db!
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
                ResolvedMapStyle.StyleJson(
                    buildGoogleTileJson(
                        name = "DX-Car Google Satellite Hybrid",
                        baseLyrs = "y",
                        includeTrafficOverlay = trafficEnabled
                    )
                )
            }
            MapEngineStyle.GOOGLE_TRAFFIC_TILES -> {
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
        val trafficSourceBlock = if (includeTrafficOverlay) {
            """,
            "gmaps-traffic": {
              "type": "raster",
              "tiles": [
                "https://mt0.google.com/vt/lyrs=traffic&hl=zh-TW&gl=TW&scale=2&x={x}&y={y}&z={z}",
                "https://mt1.google.com/vt/lyrs=traffic&hl=zh-TW&gl=TW&scale=2&x={x}&y={y}&z={z}",
                "https://mt2.google.com/vt/lyrs=traffic&hl=zh-TW&gl=TW&scale=2&x={x}&y={y}&z={z}",
                "https://mt3.google.com/vt/lyrs=traffic&hl=zh-TW&gl=TW&scale=2&x={x}&y={y}&z={z}"
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
              "minzoom": 5,
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

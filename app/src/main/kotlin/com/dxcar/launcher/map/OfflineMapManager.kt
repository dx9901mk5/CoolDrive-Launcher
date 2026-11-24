package com.dxcar.launcher.map

import android.content.Context
import android.net.Uri
import com.dxcar.launcher.data.LauncherPreferences
import com.dxcar.launcher.data.MapEngineStyle
import com.dxcar.launcher.data.OfflineRegionRecord
import com.dxcar.launcher.data.OfflineUpdatePolicy
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.offline.OfflineManager
import org.maplibre.android.offline.OfflineRegion
import org.maplibre.android.offline.OfflineRegionError
import org.maplibre.android.offline.OfflineRegionStatus
import org.maplibre.android.offline.OfflineTilePyramidRegionDefinition
import java.io.File

data class OfflineRegionPreset(
    val id: String,
    val titleZh: String,
    val subtitleZh: String,
    val minLat: Double,
    val maxLat: Double,
    val minLon: Double,
    val maxLon: Double,
    val minZoom: Double = 9.0,
    val maxZoom: Double = 14.2,
    val isDynamicAroundGps: Boolean = false
)

data class OfflineUpdateSuggestion(
    val regionId: String,
    val regionTitleZh: String,
    val reasonZh: String
)

data class OfflineMapUiState(
    val downloadingRegionId: String? = null,
    val downloadingTitleZh: String = "",
    val progress: Float = 0f,
    val completedTiles: Long = 0L,
    val requiredTiles: Long = 0L,
    val statusMessage: String = "",
    val updateSuggestion: OfflineUpdateSuggestion? = null
)

/**
 * Plan A Offline Map Downloader & Smart Staleness / Region-Shift Detector:
 * - Downloads MapLibre vector/raster tile regions into persistent offline SQLite storage so Plan A
 *   continues rendering roads & landmarks in mountain / basement / poor-signal zones.
 * - Automatically detects when maps need updating based on:
 *     1) User-chosen update cycle (7 / 15 / 30 days, or Manual Only)
 *     2) Driving into a new geographic area outside previously downloaded regions
 */
class OfflineMapManager(
    private val context: Context,
    private val preferences: LauncherPreferences
) {

    private val _state = MutableStateFlow(OfflineMapUiState())
    val state: StateFlow<OfflineMapUiState> = _state.asStateFlow()

    private var activeDownloadingRegion: OfflineRegion? = null

    val presets: List<OfflineRegionPreset> = listOf(
        OfflineRegionPreset(
            id = "current_gps_20km",
            titleZh = "目前車機周邊 (20km)",
            subtitleZh = "依當前 GPS 自動框選最常用生活圈（推薦優先下載）",
            minLat = 24.90, maxLat = 25.16, minLon = 121.42, maxLon = 121.70,
            minZoom = 10.0, maxZoom = 15.2,
            isDynamicAroundGps = true
        ),
        OfflineRegionPreset(
            id = "tw_north",
            titleZh = "北北基桃地區",
            subtitleZh = "台北 · 新北 · 基隆 · 桃園主要路網",
            minLat = 24.78, maxLat = 25.22, minLon = 121.15, maxLon = 121.85,
            minZoom = 9.0, maxZoom = 13.8
        ),
        OfflineRegionPreset(
            id = "tw_hsinchu_miaoli",
            titleZh = "竹苗地區",
            subtitleZh = "新竹縣市 · 苗栗主要路網",
            minLat = 24.35, maxLat = 24.92, minLon = 120.65, maxLon = 121.25,
            minZoom = 9.0, maxZoom = 13.8
        ),
        OfflineRegionPreset(
            id = "tw_central",
            titleZh = "中彰投地區",
            subtitleZh = "台中 · 彰化 · 南投主要路網",
            minLat = 23.78, maxLat = 24.38, minLon = 120.32, maxLon = 121.02,
            minZoom = 9.0, maxZoom = 13.8
        ),
        OfflineRegionPreset(
            id = "tw_yunlin_chiayi_tainan",
            titleZh = "雲嘉南地區",
            subtitleZh = "雲林 · 嘉義 · 台南主要路網",
            minLat = 22.88, maxLat = 23.78, minLon = 120.02, maxLon = 120.62,
            minZoom = 9.0, maxZoom = 13.8
        ),
        OfflineRegionPreset(
            id = "tw_kaohsiung_pingtung",
            titleZh = "高屏地區",
            subtitleZh = "高雄 · 屏東主要路網",
            minLat = 22.35, maxLat = 22.92, minLon = 120.15, maxLon = 120.70,
            minZoom = 9.0, maxZoom = 14.0
        ),
        OfflineRegionPreset(
            id = "tw_east",
            titleZh = "宜花東地區",
            subtitleZh = "宜蘭 · 花蓮 · 台東山海線",
            minLat = 22.68, maxLat = 24.85, minLon = 121.05, maxLon = 121.68,
            minZoom = 8.5, maxZoom = 13.2
        )
    )

    fun evaluateUpdateNeeded(currentLat: Double, currentLon: Double) {
        val cfg = preferences.config.value
        val now = System.currentTimeMillis()
        // Respect user snooze for 12 hours
        if (now - cfg.dismissedOfflineUpdateAtMs < 12 * 3600_000L) {
            if (_state.value.updateSuggestion != null) {
                _state.value = _state.value.copy(updateSuggestion = null)
            }
            return
        }

        val records = cfg.offlineRegions
        val policy = cfg.offlineUpdatePolicy

        // 1. Check expiration by user's chosen OfflineUpdatePolicy (7 / 15 / 30 days)
        if (policy != OfflineUpdatePolicy.MANUAL_ONLY && records.isNotEmpty()) {
            val maxAgeMs = policy.days * 86_400_000L
            val expired = records.firstOrNull { (now - it.downloadedAtMs) >= maxAgeMs }
            if (expired != null) {
                val daysAgo = ((now - expired.downloadedAtMs) / 86_400_000L).coerceAtLeast(1L)
                _state.value = _state.value.copy(
                    updateSuggestion = OfflineUpdateSuggestion(
                        regionId = expired.regionId,
                        regionTitleZh = expired.titleZh,
                        reasonZh = "「${expired.titleZh}」離線地圖已下載 $daysAgo 天（超過設定的 ${policy.days} 天），建議連網時更新"
                    )
                )
                return
            }
        }

        // 2. Check if vehicle has moved outside all downloaded offline regions
        if (records.isNotEmpty() && currentLat != 0.0 && currentLon != 0.0) {
            val covered = records.any { r ->
                currentLat in (r.minLat - 0.04)..(r.maxLat + 0.04) &&
                    currentLon in (r.minLon - 0.04)..(r.maxLon + 0.04)
            }
            if (!covered) {
                _state.value = _state.value.copy(
                    updateSuggestion = OfflineUpdateSuggestion(
                        regionId = "current_gps_20km",
                        regionTitleZh = "目前車機周邊 (20km)",
                        reasonZh = "偵測到車輛位於尚未離線快取的新區域，可選擇下載「目前車機周邊」離線地圖"
                    )
                )
                return
            }
        }

        if (_state.value.updateSuggestion != null) {
            _state.value = _state.value.copy(updateSuggestion = null)
        }
    }

    fun dismissUpdateSuggestion() {
        preferences.update { it.copy(dismissedOfflineUpdateAtMs = System.currentTimeMillis()) }
        _state.value = _state.value.copy(updateSuggestion = null)
    }

    fun downloadOrUpdatePreset(
        presetId: String,
        currentLat: Double,
        currentLon: Double,
        style: MapEngineStyle
    ) {
        if (_state.value.downloadingRegionId != null) return
        val preset = presets.firstOrNull { it.id == presetId } ?: return

        val (minLat, maxLat, minLon, maxLon) = if (preset.isDynamicAroundGps && currentLat != 0.0 && currentLon != 0.0) {
            val dLat = 0.14
            val dLon = 0.15
            listOf(currentLat - dLat, currentLat + dLat, currentLon - dLon, currentLon + dLon)
        } else {
            listOf(preset.minLat, preset.maxLat, preset.minLon, preset.maxLon)
        }

        val cLat = (minLat + maxLat) / 2.0
        val cLon = (minLon + maxLon) / 2.0
        val styleUri = resolveOfflineCapableStyleUri(style)

        _state.value = _state.value.copy(
            downloadingRegionId = preset.id,
            downloadingTitleZh = preset.titleZh,
            progress = 0.02f,
            completedTiles = 0L,
            requiredTiles = 0L,
            statusMessage = "正在準備下載「${preset.titleZh}」離線圖磚…"
        )

        runCatching {
            val manager = OfflineManager.getInstance(context)
            manager.setOfflineMapboxTileCountLimit(30_000L)

            val bounds = LatLngBounds.Builder()
                .include(LatLng(minLat, minLon))
                .include(LatLng(maxLat, maxLon))
                .build()

            val definition = OfflineTilePyramidRegionDefinition(
                styleUri,
                bounds,
                preset.minZoom,
                preset.maxZoom,
                context.resources.displayMetrics.density.coerceIn(1f, 2f)
            )
            val metadataBytes = "${preset.id}|${preset.titleZh}".toByteArray(Charsets.UTF_8)

            manager.createOfflineRegion(
                definition,
                metadataBytes,
                object : OfflineManager.CreateOfflineRegionCallback {
                    override fun onCreate(offlineRegion: OfflineRegion) {
                        activeDownloadingRegion = offlineRegion
                        offlineRegion.setObserver(object : OfflineRegion.OfflineRegionObserver {
                            override fun onStatusChanged(status: OfflineRegionStatus) {
                                val req = status.requiredResourceCount.coerceAtLeast(1L)
                                val comp = status.completedResourceCount
                                val pct = (comp.toFloat() / req.toFloat()).coerceIn(0f, 1f)
                                val sizeMb = status.completedResourceSize / (1024f * 1024f)

                                if (status.isComplete) {
                                    offlineRegion.setDownloadState(OfflineRegion.STATE_INACTIVE)
                                    activeDownloadingRegion = null
                                    val rec = OfflineRegionRecord(
                                        regionId = preset.id,
                                        titleZh = preset.titleZh,
                                        centerLat = cLat,
                                        centerLon = cLon,
                                        minLat = minLat,
                                        maxLat = maxLat,
                                        minLon = minLon,
                                        maxLon = maxLon,
                                        downloadedAtMs = System.currentTimeMillis(),
                                        approxSizeMb = sizeMb.coerceAtLeast(0.1f),
                                        maplibreRegionId = offlineRegion.id
                                    )
                                    preferences.update { cur ->
                                        val nextList = cur.offlineRegions.filterNot { it.regionId == preset.id } + rec
                                        cur.copy(offlineRegions = nextList)
                                    }
                                    _state.value = OfflineMapUiState(
                                        statusMessage = "✓ 「${preset.titleZh}」離線地圖下載完成（約 %.1f MB）".format(sizeMb.coerceAtLeast(0.1f)),
                                        updateSuggestion = null
                                    )
                                } else {
                                    _state.value = _state.value.copy(
                                        progress = pct.coerceAtLeast(0.03f),
                                        completedTiles = comp,
                                        requiredTiles = req,
                                        statusMessage = "下載中 ${preset.titleZh}：${(pct * 100).toInt()}% ($comp / $req 圖磚 · %.1f MB)".format(sizeMb)
                                    )
                                }
                            }

                            override fun onError(error: OfflineRegionError) {
                                activeDownloadingRegion?.setDownloadState(OfflineRegion.STATE_INACTIVE)
                                activeDownloadingRegion = null
                                _state.value = _state.value.copy(
                                    downloadingRegionId = null,
                                    statusMessage = "下載中斷：${error.message}（請確認網路連線後重試）"
                                )
                            }

                            override fun mapboxTileCountLimitExceeded(limit: Long) {
                                activeDownloadingRegion?.setDownloadState(OfflineRegion.STATE_INACTIVE)
                                activeDownloadingRegion = null
                                _state.value = _state.value.copy(
                                    downloadingRegionId = null,
                                    statusMessage = "已達單區圖磚上限 ($limit)，已保存目前快取範圍"
                                )
                            }
                        })
                        offlineRegion.setDownloadState(OfflineRegion.STATE_ACTIVE)
                    }

                    override fun onError(error: String) {
                        _state.value = _state.value.copy(
                            downloadingRegionId = null,
                            statusMessage = "建立離線區域失敗：$error"
                        )
                    }
                }
            )
        }.onFailure { e ->
            _state.value = _state.value.copy(
                downloadingRegionId = null,
                statusMessage = "離線引擎尚未就緒：${e.localizedMessage ?: "請稍後再試"}"
            )
        }
    }

    fun cancelCurrentDownload() {
        runCatching {
            activeDownloadingRegion?.setDownloadState(OfflineRegion.STATE_INACTIVE)
        }
        activeDownloadingRegion = null
        _state.value = _state.value.copy(
            downloadingRegionId = null,
            statusMessage = "已取消離線地圖下載"
        )
    }

    fun deleteDownloadedRegion(regionId: String) {
        val target = preferences.config.value.offlineRegions.firstOrNull { it.regionId == regionId }
        preferences.update { cur ->
            cur.copy(offlineRegions = cur.offlineRegions.filterNot { it.regionId == regionId })
        }
        if (target != null && target.maplibreRegionId >= 0L) {
            runCatching {
                val manager = OfflineManager.getInstance(context)
                manager.listOfflineRegions(object : OfflineManager.ListOfflineRegionsCallback {
                    override fun onList(offlineRegions: Array<OfflineRegion>?) {
                        offlineRegions?.firstOrNull { it.id == target.maplibreRegionId }
                            ?.delete(object : OfflineRegion.OfflineRegionDeleteCallback {
                                override fun onDelete() = Unit
                                override fun onError(error: String) = Unit
                            })
                    }
                    override fun onError(error: String) = Unit
                })
            }
        }
        _state.value = _state.value.copy(statusMessage = "已移除「${target?.titleZh ?: regionId}」離線地圖")
    }

    private fun resolveOfflineCapableStyleUri(style: MapEngineStyle): String {
        return when (style) {
            MapEngineStyle.OPENFREEMAP_VECTOR_3D -> MapStyleFactory.OPENFREEMAP_DAY_URL
            MapEngineStyle.OPENFREEMAP_DARK -> MapStyleFactory.OPENFREEMAP_NIGHT_URL
            MapEngineStyle.GOOGLE_SATELLITE_HYBRID,
            MapEngineStyle.GOOGLE_TRAFFIC_TILES -> {
                // Persist clean offline base raster style (without ephemeral live traffic overlay) as file:// URI
                val lyrs = if (style == MapEngineStyle.GOOGLE_SATELLITE_HYBRID) "y" else "m"
                val styleJson = (MapStyleFactory.resolve(style, isNight = false, trafficEnabled = false)
                    as? MapStyleFactory.ResolvedMapStyle.StyleJson)?.json.orEmpty()
                val file = File(context.filesDir, "offline_style_$lyrs.json")
                runCatching { file.writeText(styleJson, Charsets.UTF_8) }
                Uri.fromFile(file).toString()
            }
        }
    }
}

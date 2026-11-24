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
    val maxZoom: Double = 14.0,
    val isDynamicAroundGps: Boolean = false
)

data class DetectedOfflineLocationInfo(
    val localRegionId: String,
    val localRegionTitleZh: String,
    val countryCode: String,
    val countryTitleZh: String,
    val totalCountryZones: Int
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
    val batchIndex: Int = 0,
    val batchTotal: Int = 0,
    val statusMessage: String = "",
    val updateSuggestion: OfflineUpdateSuggestion? = null
)

/**
 * Plan A Offline Map Downloader with Automatic Current-Region & Entire-Country Batch Download:
 * - Detects vehicle's current Local Region ("所在區域") and Current Country ("所在國家") from live GPS.
 * - Supports one-tap "下載目前所在區域" and one-tap "一鍵全下載所在國家（全區完整快取）" sequential queue.
 * - Automatically detects staleness (7 / 15 / 30 days / Manual) and region shifts.
 */
class OfflineMapManager(
    private val context: Context,
    private val preferences: LauncherPreferences
) {

    private val _state = MutableStateFlow(OfflineMapUiState())
    val state: StateFlow<OfflineMapUiState> = _state.asStateFlow()

    private var activeDownloadingRegion: OfflineRegion? = null
    private val pendingQueue = ArrayDeque<OfflineRegionPreset>()
    private var activeBatchTotal = 1
    private var activeBatchCompleted = 0
    private var lastBatchLat = 25.0330
    private var lastBatchLon = 121.5654
    private var lastBatchStyle = MapEngineStyle.GOOGLE_TRAFFIC_TILES

    val presets: List<OfflineRegionPreset> = listOf(
        OfflineRegionPreset(
            id = "current_gps_20km",
            titleZh = "目前車機周邊 (30km 高精細)",
            subtitleZh = "依當前 GPS 自動框選最常用街道生活圈（支援街巷級縮放）",
            minLat = 24.90, maxLat = 25.16, minLon = 121.42, maxLon = 121.70,
            minZoom = 10.0, maxZoom = 15.2,
            isDynamicAroundGps = true
        ),
        OfflineRegionPreset(
            id = "tw_north",
            titleZh = "北北基桃地區",
            subtitleZh = "台北 · 新北 · 基隆 · 桃園主要路網",
            minLat = 24.78, maxLat = 25.24, minLon = 121.12, maxLon = 121.92,
            minZoom = 8.5, maxZoom = 13.8
        ),
        OfflineRegionPreset(
            id = "tw_hsinchu_miaoli",
            titleZh = "竹苗地區",
            subtitleZh = "新竹縣市 · 苗栗主要路網",
            minLat = 24.35, maxLat = 24.92, minLon = 120.65, maxLon = 121.25,
            minZoom = 8.5, maxZoom = 13.8
        ),
        OfflineRegionPreset(
            id = "tw_central",
            titleZh = "中彰投地區",
            subtitleZh = "台中 · 彰化 · 南投主要路網",
            minLat = 23.78, maxLat = 24.38, minLon = 120.32, maxLon = 121.10,
            minZoom = 8.5, maxZoom = 13.8
        ),
        OfflineRegionPreset(
            id = "tw_yunlin_chiayi_tainan",
            titleZh = "雲嘉南地區",
            subtitleZh = "雲林 · 嘉義 · 台南主要路網",
            minLat = 22.88, maxLat = 23.78, minLon = 120.02, maxLon = 120.68,
            minZoom = 8.5, maxZoom = 13.8
        ),
        OfflineRegionPreset(
            id = "tw_kaohsiung_pingtung",
            titleZh = "高屏地區",
            subtitleZh = "高雄 · 屏東 · 墾丁主要路網",
            minLat = 21.88, maxLat = 22.95, minLon = 120.15, maxLon = 120.90,
            minZoom = 8.5, maxZoom = 13.8
        ),
        OfflineRegionPreset(
            id = "tw_east",
            titleZh = "宜花東地區",
            subtitleZh = "宜蘭 · 花蓮 · 台東山海線",
            minLat = 22.55, maxLat = 24.85, minLon = 120.95, maxLon = 121.95,
            minZoom = 8.0, maxZoom = 13.3
        ),
        OfflineRegionPreset(
            id = "tw_islands",
            titleZh = "澎金馬離島地區",
            subtitleZh = "澎湖 · 金門 · 馬祖主要路網",
            minLat = 23.15, maxLat = 26.35, minLon = 118.20, maxLon = 120.05,
            minZoom = 8.5, maxZoom = 13.2
        )
    )

    fun evaluateUpdateNeeded(currentLat: Double, currentLon: Double) {
        val cfg = preferences.config.value
        val now = System.currentTimeMillis()
        if (now - cfg.dismissedOfflineUpdateAtMs < 12 * 3600_000L) {
            if (_state.value.updateSuggestion != null) {
                _state.value = _state.value.copy(updateSuggestion = null)
            }
            return
        }

        val records = cfg.offlineRegions
        val policy = cfg.offlineUpdatePolicy

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

        if (records.isNotEmpty() && currentLat != 0.0 && currentLon != 0.0) {
            val covered = records.any { r ->
                currentLat in (r.minLat - 0.04)..(r.maxLat + 0.04) &&
                    currentLon in (r.minLon - 0.04)..(r.maxLon + 0.04)
            }
            if (!covered) {
                val detected = describeDetectedLocation(currentLat, currentLon)
                _state.value = _state.value.copy(
                    updateSuggestion = OfflineUpdateSuggestion(
                        regionId = TOKEN_AUTO_LOCAL_REGION,
                        regionTitleZh = detected.localRegionTitleZh,
                        reasonZh = "偵測到車輛進入「${detected.localRegionTitleZh}」尚未離線快取，可一鍵下載所在區域地圖"
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
        val effLat = if (currentLat != 0.0) currentLat else preferences.config.value.lastKnownLat
        val effLon = if (currentLon != 0.0) currentLon else preferences.config.value.lastKnownLon
        lastBatchLat = effLat
        lastBatchLon = effLon
        lastBatchStyle = style

        val batchList: List<OfflineRegionPreset> = when (presetId) {
            TOKEN_AUTO_LOCAL_REGION -> buildLocalRegionBatch(effLat, effLon)
            TOKEN_AUTO_ENTIRE_COUNTRY -> buildEntireCountryBatch(effLat, effLon)
            else -> listOfNotNull(presets.firstOrNull { it.id == presetId })
        }
        if (batchList.isEmpty()) return

        pendingQueue.clear()
        pendingQueue.addAll(batchList)
        activeBatchTotal = batchList.size
        activeBatchCompleted = 0
        startNextInQueue()
    }

    private fun buildLocalRegionBatch(lat: Double, lon: Double): List<OfflineRegionPreset> {
        val detected = describeDetectedLocation(lat, lon)
        val matchedTw = presets.firstOrNull { it.id == detected.localRegionId && it.id != "current_gps_20km" }
        val gpsAround = presets.first { it.id == "current_gps_20km" }
        return if (matchedTw != null) {
            listOf(matchedTw, gpsAround)
        } else {
            listOf(gpsAround)
        }
    }

    private fun buildEntireCountryBatch(lat: Double, lon: Double): List<OfflineRegionPreset> {
        val detected = describeDetectedLocation(lat, lon)
        return when (detected.countryCode) {
            "TW" -> presets.filterNot { it.id == "current_gps_20km" }
            "HK_MO" -> listOf(
                OfflineRegionPreset(
                    id = "country_hk_mo",
                    titleZh = "港澳全境地圖",
                    subtitleZh = "香港 · 九龍 · 新界 · 澳門完整路網",
                    minLat = 22.10, maxLat = 22.58, minLon = 113.52, maxLon = 114.45,
                    minZoom = 9.0, maxZoom = 14.5
                )
            )
            "JP" -> listOf(
                OfflineRegionPreset(
                    id = "country_jp_main",
                    titleZh = "日本全國主要幹道圖資",
                    subtitleZh = "日本全境高速與幹線路網",
                    minLat = 30.9, maxLat = 43.6, minLon = 129.5, maxLon = 142.2,
                    minZoom = 6.0, maxZoom = 10.5
                ),
                presets.first { it.id == "current_gps_20km" }
            )
            "KR" -> listOf(
                OfflineRegionPreset(
                    id = "country_kr_main",
                    titleZh = "韓國全國主要幹道圖資",
                    subtitleZh = "韓國全境高速與幹線路網",
                    minLat = 33.2, maxLat = 38.4, minLon = 125.8, maxLon = 129.7,
                    minZoom = 6.5, maxZoom = 11.2
                ),
                presets.first { it.id == "current_gps_20km" }
            )
            else -> listOf(
                OfflineRegionPreset(
                    id = "country_dynamic_wide",
                    titleZh = "目前所在國家／廣域路網",
                    subtitleZh = "依當前國家座標自動框選廣域幹道",
                    minLat = (lat - 2.2).coerceAtLeast(-80.0),
                    maxLat = (lat + 2.2).coerceAtMost(80.0),
                    minLon = (lon - 2.4).coerceAtLeast(-179.0),
                    maxLon = (lon + 2.4).coerceAtMost(179.0),
                    minZoom = 6.5,
                    maxZoom = 11.0
                ),
                presets.first { it.id == "current_gps_20km" }
            )
        }
    }

    private fun startNextInQueue() {
        val preset = pendingQueue.removeFirstOrNull()
        if (preset == null) {
            activeDownloadingRegion = null
            _state.value = OfflineMapUiState(
                statusMessage = if (activeBatchTotal > 1) {
                    "✓ 已完成全部 $activeBatchTotal 區離線地圖下載！"
                } else {
                    _state.value.statusMessage
                }
            )
            return
        }

        val stepIdx = activeBatchCompleted + 1
        val stepPrefix = if (activeBatchTotal > 1) "[全區下載 $stepIdx/$activeBatchTotal] " else ""

        val (minLat, maxLat, minLon, maxLon) = if (preset.isDynamicAroundGps && lastBatchLat != 0.0 && lastBatchLon != 0.0) {
            val dLat = 0.16
            val dLon = 0.18
            listOf(lastBatchLat - dLat, lastBatchLat + dLat, lastBatchLon - dLon, lastBatchLon + dLon)
        } else {
            listOf(preset.minLat, preset.maxLat, preset.minLon, preset.maxLon)
        }

        val cLat = (minLat + maxLat) / 2.0
        val cLon = (minLon + maxLon) / 2.0
        val styleUri = resolveOfflineCapableStyleUri(lastBatchStyle)

        _state.value = _state.value.copy(
            downloadingRegionId = preset.id,
            downloadingTitleZh = preset.titleZh,
            progress = 0.02f,
            completedTiles = 0L,
            requiredTiles = 0L,
            batchIndex = stepIdx,
            batchTotal = activeBatchTotal,
            statusMessage = "${stepPrefix}正在準備下載「${preset.titleZh}」離線圖磚…"
        )

        runCatching {
            val manager = OfflineManager.getInstance(context)
            manager.setOfflineMapboxTileCountLimit(250_000L)

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
                                    activeBatchCompleted++
                                    if (pendingQueue.isNotEmpty()) {
                                        startNextInQueue()
                                    } else {
                                        _state.value = OfflineMapUiState(
                                            statusMessage = if (activeBatchTotal > 1) {
                                                "✓ 所在國家／區域共 $activeBatchTotal 區離線地圖已全數下載完成！"
                                            } else {
                                                "✓ 「${preset.titleZh}」離線地圖下載完成（約 %.1f MB）".format(sizeMb.coerceAtLeast(0.1f))
                                            },
                                            updateSuggestion = null
                                        )
                                    }
                                } else {
                                    val overallPct = ((activeBatchCompleted + pct) / activeBatchTotal.toFloat()).coerceIn(0.02f, 1f)
                                    _state.value = _state.value.copy(
                                        progress = overallPct,
                                        completedTiles = comp,
                                        requiredTiles = req,
                                        statusMessage = "${stepPrefix}下載中 ${preset.titleZh}：${(pct * 100).toInt()}% ($comp / $req 圖磚 · %.1f MB)".format(sizeMb)
                                    )
                                }
                            }

                            override fun onError(error: OfflineRegionError) {
                                activeDownloadingRegion?.setDownloadState(OfflineRegion.STATE_INACTIVE)
                                activeDownloadingRegion = null
                                pendingQueue.clear()
                                _state.value = _state.value.copy(
                                    downloadingRegionId = null,
                                    statusMessage = "下載中斷：${error.message}（請確認網路連線後重試）"
                                )
                            }

                            override fun mapboxTileCountLimitExceeded(limit: Long) {
                                activeDownloadingRegion?.setDownloadState(OfflineRegion.STATE_INACTIVE)
                                activeDownloadingRegion = null
                                pendingQueue.clear()
                                _state.value = _state.value.copy(
                                    downloadingRegionId = null,
                                    statusMessage = "已達圖磚儲存上限 ($limit)，已保存目前已下載範圍"
                                )
                            }
                        })
                        offlineRegion.setDownloadState(OfflineRegion.STATE_ACTIVE)
                    }

                    override fun onError(error: String) {
                        pendingQueue.clear()
                        _state.value = _state.value.copy(
                            downloadingRegionId = null,
                            statusMessage = "建立離線區域失敗：$error"
                        )
                    }
                }
            )
        }.onFailure { e ->
            pendingQueue.clear()
            _state.value = _state.value.copy(
                downloadingRegionId = null,
                statusMessage = "離線引擎尚未就緒：${e.localizedMessage ?: "請稍後再試"}"
            )
        }
    }

    fun cancelCurrentDownload() {
        pendingQueue.clear()
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
                val lyrs = if (style == MapEngineStyle.GOOGLE_SATELLITE_HYBRID) "y" else "m"
                val styleJson = (MapStyleFactory.resolve(style, isNight = false, trafficEnabled = false)
                    as? MapStyleFactory.ResolvedMapStyle.StyleJson)?.json.orEmpty()
                val file = File(context.filesDir, "offline_style_$lyrs.json")
                runCatching { file.writeText(styleJson, Charsets.UTF_8) }
                Uri.fromFile(file).toString()
            }
        }
    }

    companion object {
        const val TOKEN_AUTO_LOCAL_REGION = "__auto_current_local_region__"
        const val TOKEN_AUTO_ENTIRE_COUNTRY = "__auto_entire_current_country__"

        fun describeDetectedLocation(lat: Double, lon: Double): DetectedOfflineLocationInfo {
            val effLat = if (lat != 0.0) lat else 25.0330
            val effLon = if (lon != 0.0) lon else 121.5654

            val inTaiwan = effLat in 21.6..26.5 && effLon in 118.0..122.4
            if (inTaiwan) {
                val (regId, regTitle) = when {
                    effLon < 119.95 -> "tw_islands" to "澎金馬離島地區"
                    effLon >= 121.32 && effLat < 24.82 -> "tw_east" to "宜花東地區"
                    effLat >= 24.78 -> "tw_north" to "北北基桃地區"
                    effLat >= 24.35 -> "tw_hsinchu_miaoli" to "竹苗地區"
                    effLat >= 23.78 -> "tw_central" to "中彰投地區"
                    effLat >= 22.88 -> "tw_yunlin_chiayi_tainan" to "雲嘉南地區"
                    else -> "tw_kaohsiung_pingtung" to "高屏地區"
                }
                return DetectedOfflineLocationInfo(
                    localRegionId = regId,
                    localRegionTitleZh = regTitle,
                    countryCode = "TW",
                    countryTitleZh = "台灣全境（本島＋離島全 7 區）",
                    totalCountryZones = 7
                )
            }

            if (effLat in 22.05..22.60 && effLon in 113.5..114.5) {
                return DetectedOfflineLocationInfo(
                    localRegionId = "current_gps_20km",
                    localRegionTitleZh = "港澳周邊生活圈",
                    countryCode = "HK_MO",
                    countryTitleZh = "香港／澳門全境",
                    totalCountryZones = 1
                )
            }

            if (effLat in 24.0..45.8 && effLon in 122.9..146.0) {
                return DetectedOfflineLocationInfo(
                    localRegionId = "current_gps_20km",
                    localRegionTitleZh = "日本當前周邊 (30km)",
                    countryCode = "JP",
                    countryTitleZh = "日本全國主要路網＋周邊",
                    totalCountryZones = 2
                )
            }

            if (effLat in 33.0..38.7 && effLon in 124.5..131.0) {
                return DetectedOfflineLocationInfo(
                    localRegionId = "current_gps_20km",
                    localRegionTitleZh = "韓國當前周邊 (30km)",
                    countryCode = "KR",
                    countryTitleZh = "韓國全國主要路網＋周邊",
                    totalCountryZones = 2
                )
            }

            return DetectedOfflineLocationInfo(
                localRegionId = "current_gps_20km",
                localRegionTitleZh = "目前所在區域 (周邊 30km)",
                countryCode = "GLOBAL",
                countryTitleZh = "目前所在國家／廣域全區",
                totalCountryZones = 2
            )
        }
    }
}

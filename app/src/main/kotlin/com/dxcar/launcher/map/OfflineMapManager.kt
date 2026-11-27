package com.dxcar.launcher.map

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.Composable
import com.dxcar.launcher.data.LauncherPreferences
import com.dxcar.launcher.data.MapEngineStyle
import com.dxcar.launcher.data.OfflineRegionRecord
import com.dxcar.launcher.data.OfflineUpdatePolicy
import com.dxcar.launcher.i18n.DxCarI18n
import com.dxcar.launcher.i18n.DxCarI18n.str
import com.dxcar.launcher.i18n.EffectiveLanguage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.maplibre.android.MapLibre
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.offline.OfflineManager
import org.maplibre.android.offline.OfflineRegion
import org.maplibre.android.offline.OfflineRegionError
import org.maplibre.android.offline.OfflineRegionStatus
import org.maplibre.android.offline.OfflineTilePyramidRegionDefinition
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

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
    val reasonZh: String,
    val daysAgo: Long = 0L,
    val policyDays: Int = 0,
    val isUncachedArea: Boolean = false
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
 * Plan A Offline Map Downloader with Local Loopback HTTP Style Server:
 * - MapLibre Native's C++ OfflineDownload uses HttpFileSource (OkHttp), which strictly requires
 *   http:// or https:// style URLs (passing file:// hangs forever at initial state).
 * - Serves canonical Google Standard (lyrs=m) & Satellite (lyrs=y) Style JSON over 127.0.0.1 loopback
 *   with pixelRatio = 1.0f (since tiles already carry &scale=2 retina HD), enabling fast 0% -> 100%
 *   downloads and 100% offline cache hits.
 */
class OfflineMapManager(
    private val context: Context,
    private val preferences: LauncherPreferences
) {

    private val _state = MutableStateFlow(OfflineMapUiState())
    val state: StateFlow<OfflineMapUiState> = _state.asStateFlow()

    private val mainHandler = Handler(Looper.getMainLooper())
    private var activeDownloadingRegion: OfflineRegion? = null
    private val pendingQueue = ArrayDeque<OfflineRegionPreset>()
    private val failedRegionTitles = mutableListOf<String>()
    private var currentPresetRetryCount = 0
    private var activeBatchTotal = 1
    private var activeBatchCompleted = 0
    private var lastBatchLat = 25.0330
    private var lastBatchLon = 121.5654
    private var lastBatchStyle = MapEngineStyle.GOOGLE_TRAFFIC_TILES

    @Volatile
    private var styleServerPort: Int = 0
    private val serverLock = Any()

    val presets: List<OfflineRegionPreset> = listOf(
        OfflineRegionPreset(
            id = "current_gps_20km",
            titleZh = "目前車機周邊 (30km 高精細)",
            subtitleZh = "依當前 GPS 自動框選最常用街道生活圈（支援街巷級縮放）",
            minLat = 24.90, maxLat = 25.16, minLon = 121.42, maxLon = 121.70,
            minZoom = 10.0, maxZoom = 15.0,
            isDynamicAroundGps = true
        ),
        OfflineRegionPreset(
            id = "tw_north",
            titleZh = "北北基桃地區",
            subtitleZh = "台北 · 新北 · 基隆 · 桃園主要路網",
            minLat = 24.78, maxLat = 25.24, minLon = 121.12, maxLon = 121.92,
            minZoom = 8.5, maxZoom = 13.5
        ),
        OfflineRegionPreset(
            id = "tw_hsinchu_miaoli",
            titleZh = "竹苗地區",
            subtitleZh = "新竹縣市 · 苗栗主要路網",
            minLat = 24.35, maxLat = 24.92, minLon = 120.65, maxLon = 121.25,
            minZoom = 8.5, maxZoom = 13.5
        ),
        OfflineRegionPreset(
            id = "tw_central",
            titleZh = "中彰投地區",
            subtitleZh = "台中 · 彰化 · 南投主要路網",
            minLat = 23.78, maxLat = 24.38, minLon = 120.32, maxLon = 121.10,
            minZoom = 8.5, maxZoom = 13.5
        ),
        OfflineRegionPreset(
            id = "tw_yunlin_chiayi_tainan",
            titleZh = "雲嘉南地區",
            subtitleZh = "雲林 · 嘉義 · 台南主要路網",
            minLat = 22.88, maxLat = 23.78, minLon = 120.02, maxLon = 120.68,
            minZoom = 8.5, maxZoom = 13.5
        ),
        OfflineRegionPreset(
            id = "tw_kaohsiung_pingtung",
            titleZh = "高屏地區",
            subtitleZh = "高雄 · 屏東 · 墾丁主要路網",
            minLat = 21.88, maxLat = 22.95, minLon = 120.15, maxLon = 120.90,
            minZoom = 8.5, maxZoom = 13.5
        ),
        OfflineRegionPreset(
            id = "tw_east",
            titleZh = "宜花東地區",
            subtitleZh = "宜蘭 · 花蓮 · 台東山海線",
            minLat = 22.55, maxLat = 24.85, minLon = 120.95, maxLon = 121.95,
            minZoom = 8.0, maxZoom = 13.0
        ),
        OfflineRegionPreset(
            id = "tw_islands",
            titleZh = "澎金馬離島地區",
            subtitleZh = "澎湖 · 金門 · 馬祖主要路網",
            minLat = 23.15, maxLat = 26.35, minLon = 118.20, maxLon = 120.05,
            minZoom = 8.5, maxZoom = 13.0
        )
    )

    private fun ensureLocalStyleServer(): Int {
        if (styleServerPort > 0) return styleServerPort
        synchronized(serverLock) {
            if (styleServerPort > 0) return styleServerPort
            val server = ServerSocket(0, 16, InetAddress.getByName("127.0.0.1"))
            styleServerPort = server.localPort
            thread(name = "DxCarOfflineStyleServer", isDaemon = true) {
                while (!server.isClosed) {
                    val client = runCatching { server.accept() }.getOrNull() ?: break
                    thread(name = "DxCarOfflineStyleReq", isDaemon = true) {
                        runCatching {
                            client.soTimeout = 3_000
                            val reader = BufferedReader(InputStreamReader(client.getInputStream(), Charsets.UTF_8))
                            val reqLine = reader.readLine().orEmpty()
                            val isSat = reqLine.contains("/style/y")
                            val bodyBytes = MapStyleFactory
                                .buildOfflineCompatibleGoogleStyleJson(if (isSat) "y" else "m")
                                .toByteArray(Charsets.UTF_8)
                            val header = buildString {
                                append("HTTP/1.1 200 OK\r\n")
                                append("Content-Type: application/json; charset=utf-8\r\n")
                                append("Content-Length: ${bodyBytes.size}\r\n")
                                append("Cache-Control: no-cache\r\n")
                                append("Connection: close\r\n\r\n")
                            }.toByteArray(Charsets.UTF_8)
                            val out = client.getOutputStream()
                            out.write(header)
                            out.write(bodyBytes)
                            out.flush()
                        }
                        runCatching { client.close() }
                    }
                }
            }
            return styleServerPort
        }
    }

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
                val locTitle = presetTitleTr(expired.regionId, expired.titleZh)
                _state.value = _state.value.copy(
                    updateSuggestion = OfflineUpdateSuggestion(
                        regionId = expired.regionId,
                        regionTitleZh = locTitle,
                        reasonZh = DxCarI18n.tr(
                            "「$locTitle」離線地圖已下載 $daysAgo 天（超過設定的 ${policy.days} 天），建議連網時更新",
                            "「$locTitle」离线地图已下载 $daysAgo 天（超过设置的 ${policy.days} 天），建议联网时更新",
                            "\"$locTitle\" offline map is $daysAgo days old (over ${policy.days}-day limit). Update recommended.",
                            "「$locTitle」オフライン地図は保存から $daysAgo 日経過（設定 ${policy.days} 日超過）。更新を推奨します"
                        ),
                        daysAgo = daysAgo,
                        policyDays = policy.days,
                        isUncachedArea = false
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
                        reasonZh = DxCarI18n.tr(
                            "偵測到車輛進入「${detected.localRegionTitleZh}」尚未離線快取，可一鍵下載所在區域地圖",
                            "检测到车辆进入「${detected.localRegionTitleZh}」尚未离线缓存，可一键下载所在区域地图",
                            "Entered \"${detected.localRegionTitleZh}\" without offline cache. Download local map in one tap.",
                            "オフライン未保存の「${detected.localRegionTitleZh}」に入りました。ワンタップで周辺地図を保存できます"
                        ),
                        isUncachedArea = true
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
        style: MapEngineStyle,
        forceRedownload: Boolean = false
    ) {
        if (_state.value.downloadingRegionId != null) return
        val effLat = if (currentLat != 0.0) currentLat else preferences.config.value.lastKnownLat
        val effLon = if (currentLon != 0.0) currentLon else preferences.config.value.lastKnownLon
        lastBatchLat = effLat
        lastBatchLon = effLon
        lastBatchStyle = style

        val rawBatchList: List<OfflineRegionPreset> = when (presetId) {
            TOKEN_AUTO_LOCAL_REGION -> buildLocalRegionBatch(effLat, effLon)
            TOKEN_AUTO_ENTIRE_COUNTRY -> buildEntireCountryBatch(effLat, effLon)
            else -> listOfNotNull(presets.firstOrNull { it.id == presetId })
        }
        if (rawBatchList.isEmpty()) return

        // Resume interrupted country batch by skipping regions already downloaded (unless forceRedownload)
        val batchList = if (presetId == TOKEN_AUTO_ENTIRE_COUNTRY && !forceRedownload) {
            val doneIds = preferences.config.value.offlineRegions.map { it.regionId }.toSet()
            val remaining = rawBatchList.filterNot { it.id in doneIds }
            remaining.ifEmpty { rawBatchList }
        } else {
            rawBatchList
        }

        pendingQueue.clear()
        failedRegionTitles.clear()
        currentPresetRetryCount = 0
        pendingQueue.addAll(batchList)
        activeBatchTotal = batchList.size.coerceAtLeast(1)
        activeBatchCompleted = 0
        mainHandler.post { startNextInQueue() }
    }

    fun clearAndRedownloadAll(
        currentLat: Double,
        currentLon: Double,
        style: MapEngineStyle
    ) {
        cancelCurrentDownload()
        deleteAllOfflineRegions {
            downloadOrUpdatePreset(
                presetId = TOKEN_AUTO_ENTIRE_COUNTRY,
                currentLat = currentLat,
                currentLon = currentLon,
                style = style,
                forceRedownload = true
            )
        }
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
            "TW" -> presets
                .filterNot { it.id == "current_gps_20km" }
                .map { it.copy(maxZoom = it.maxZoom.coerceAtMost(13.5)) }
            "HK_MO" -> listOf(
                OfflineRegionPreset(
                    id = "country_hk_mo",
                    titleZh = "港澳全境地圖",
                    subtitleZh = "香港 · 九龍 · 新界 · 澳門完整路網",
                    minLat = 22.10, maxLat = 22.58, minLon = 113.52, maxLon = 114.45,
                    minZoom = 9.0, maxZoom = 14.2
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
                progress = 1f,
                statusMessage = if (activeBatchTotal > 1) {
                    DxCarI18n.tr(
                        "✓ 已完成全部 $activeBatchTotal 區離線地圖下載！",
                        "✓ 已完成全部 $activeBatchTotal 区离线地图下载！",
                        "✓ Completed all $activeBatchTotal offline map regions!",
                        "✓ 全 $activeBatchTotal エリアのオフライン地図ダウンロードが完了しました！"
                    )
                } else {
                    _state.value.statusMessage
                }
            )
            return
        }

        val safeBatchTotal = activeBatchTotal.coerceAtLeast(1)
        val stepIdx = activeBatchCompleted + 1
        val stepPrefix = if (safeBatchTotal > 1) {
            DxCarI18n.tr(
                "[全區下載 $stepIdx/$safeBatchTotal] ",
                "[全区下载 $stepIdx/$safeBatchTotal] ",
                "[Batch $stepIdx/$safeBatchTotal] ",
                "[一括保存 $stepIdx/$safeBatchTotal] "
            )
        } else {
            ""
        }
        val pTitle = presetTitleTr(preset.id, preset.titleZh)

        val (minLat, maxLat, minLon, maxLon) = if (preset.isDynamicAroundGps && lastBatchLat != 0.0 && lastBatchLon != 0.0) {
            val dLat = 0.32
            val dLon = 0.34
            listOf(lastBatchLat - dLat, lastBatchLat + dLat, lastBatchLon - dLon, lastBatchLon + dLon)
        } else {
            listOf(preset.minLat, preset.maxLat, preset.minLon, preset.maxLon)
        }
        val effectiveMaxZoom = if (preset.isDynamicAroundGps) preset.maxZoom.coerceAtLeast(15.5) else preset.maxZoom

        val cLat = (minLat + maxLat) / 2.0
        val cLon = (minLon + maxLon) / 2.0
        val styleUri = resolveOfflineCapableStyleUri(lastBatchStyle)
        val initialOverallPct = (activeBatchCompleted.toFloat() / safeBatchTotal.toFloat()).coerceIn(0f, 0.99f)

        _state.value = _state.value.copy(
            downloadingRegionId = preset.id,
            downloadingTitleZh = pTitle,
            progress = initialOverallPct,
            completedTiles = 0L,
            requiredTiles = 0L,
            batchIndex = stepIdx,
            batchTotal = safeBatchTotal,
            statusMessage = DxCarI18n.tr(
                "${stepPrefix}正在連線圖磚伺服器「$pTitle」 (0%)…",
                "${stepPrefix}正在连接图砖服务器「$pTitle」 (0%)…",
                "${stepPrefix}Connecting to tile server for \"$pTitle\" (0%)…",
                "${stepPrefix}タイルサーバーに接続中「$pTitle」 (0%)…"
            )
        )

        runCatching {
            MapLibre.getInstance(context)
            val manager = OfflineManager.getInstance(context)
            manager.setOfflineMapboxTileCountLimit(300_000L)

            val bounds = LatLngBounds.Builder()
                .include(LatLng(minLat, minLon))
                .include(LatLng(maxLat, maxLon))
                .build()

            // pixelRatio = 1.0f because Google tiles already specify &scale=2 retina HD
            val definition = OfflineTilePyramidRegionDefinition(
                styleUri,
                bounds,
                preset.minZoom,
                effectiveMaxZoom,
                1.0f
            )
            val metadataBytes = "${preset.id}|${preset.titleZh}".toByteArray(Charsets.UTF_8)

            // Clean up any stale/partial regions for the same preset.id before creating a fresh one
            val startCreate: () -> Unit = {
                manager.createOfflineRegion(
                    definition,
                    metadataBytes,
                    object : OfflineManager.CreateOfflineRegionCallback {
                        override fun onCreate(offlineRegion: OfflineRegion) {
                            val completedOnce = AtomicBoolean(false)
                            activeDownloadingRegion = offlineRegion
                            offlineRegion.setObserver(object : OfflineRegion.OfflineRegionObserver {
                                override fun onStatusChanged(status: OfflineRegionStatus) {
                                    mainHandler.post {
                                        runCatching {
                                            val req = status.requiredResourceCount
                                            val comp = status.completedResourceCount
                                            val regionPct = if (req > 0L) {
                                                (comp.toFloat() / req.toFloat()).coerceIn(0f, 1f)
                                            } else {
                                                0f
                                            }
                                            val sizeMb = status.completedResourceSize / (1024f * 1024f)
                                            val liveTitle = presetTitleTr(preset.id, preset.titleZh)

                                            if (status.isComplete && req > 0L) {
                                                if (!completedOnce.compareAndSet(false, true)) return@post
                                                runCatching { offlineRegion.setObserver(null) }
                                                runCatching { offlineRegion.setDownloadState(OfflineRegion.STATE_INACTIVE) }
                                                activeDownloadingRegion = null
                                                currentPresetRetryCount = 0
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
                                                    val sizeStr = "%.1f".format(DxCarI18n.currentLocale(), sizeMb.coerceAtLeast(0.1f))
                                                    val failNote = if (failedRegionTitles.isNotEmpty()) {
                                                        DxCarI18n.tr(
                                                            "（部分區未完成：${failedRegionTitles.joinToString("、")}，可單獨重試）",
                                                            "（部分区未完成：${failedRegionTitles.joinToString("、")}，可单独重试）",
                                                            " (Incomplete: ${failedRegionTitles.joinToString(", ")}; retry individually)",
                                                            "（一部未完了：${failedRegionTitles.joinToString("・")}）"
                                                        )
                                                    } else ""
                                                    _state.value = OfflineMapUiState(
                                                        progress = 1f,
                                                        statusMessage = if (safeBatchTotal > 1) {
                                                            DxCarI18n.tr(
                                                                "✓ 所在國家／區域共 $safeBatchTotal 區離線地圖已處理完成！$failNote",
                                                                "✓ 所在国家／区域共 $safeBatchTotal 区离线地图已处理完成！$failNote",
                                                                "✓ Processed all $safeBatchTotal offline map regions!$failNote",
                                                                "✓ 全 $safeBatchTotal エリアのオフライン地図処理が完了しました！$failNote"
                                                            )
                                                        } else {
                                                            DxCarI18n.tr(
                                                                "✓ 「$liveTitle」離線地圖下載完成 100%（約 $sizeStr MB）",
                                                                "✓ 「$liveTitle」离线地图下载完成 100%（约 $sizeStr MB）",
                                                                "✓ \"$liveTitle\" offline map downloaded 100% (~$sizeStr MB)",
                                                                "✓ 「$liveTitle」オフライン地図ダウンロード完了 100%（約 $sizeStr MB）"
                                                            )
                                                        },
                                                        updateSuggestion = null
                                                    )
                                                }
                                            } else {
                                                val overallPct = ((activeBatchCompleted + regionPct) / safeBatchTotal.toFloat()).coerceIn(0f, 0.99f)
                                                val displayPctInt = (overallPct * 100f).toInt()
                                                val regionPctInt = (regionPct * 100f).toInt()
                                                val liveStepPrefix = if (safeBatchTotal > 1) {
                                                    DxCarI18n.tr(
                                                        "[全區下載 $stepIdx/$safeBatchTotal] ",
                                                        "[全区下载 $stepIdx/$safeBatchTotal] ",
                                                        "[Batch $stepIdx/$safeBatchTotal] ",
                                                        "[一括保存 $stepIdx/$safeBatchTotal] "
                                                    )
                                                } else {
                                                    ""
                                                }
                                                val sizeStr = "%.1f".format(DxCarI18n.currentLocale(), sizeMb)
                                                val detailText = if (req > 0L) {
                                                    DxCarI18n.tr(
                                                        "${liveStepPrefix}下載中 $liveTitle：$regionPctInt% ($comp / $req 圖磚 · $sizeStr MB)",
                                                        "${liveStepPrefix}下载中 $liveTitle：$regionPctInt% ($comp / $req 图砖 · $sizeStr MB)",
                                                        "${liveStepPrefix}Downloading $liveTitle: $regionPctInt% ($comp / $req tiles · $sizeStr MB)",
                                                        "${liveStepPrefix}ダウンロード中 $liveTitle：$regionPctInt% ($comp / $req タイル · $sizeStr MB)"
                                                    )
                                                } else {
                                                    DxCarI18n.tr(
                                                        "${liveStepPrefix}正在計算「$liveTitle」圖磚清單 ($displayPctInt%)…",
                                                        "${liveStepPrefix}正在计算「$liveTitle」图砖清单 ($displayPctInt%)…",
                                                        "${liveStepPrefix}Calculating tiles for \"$liveTitle\" ($displayPctInt%)…",
                                                        "${liveStepPrefix}「$liveTitle」のタイルを計算中 ($displayPctInt%)…"
                                                    )
                                                }
                                                _state.value = _state.value.copy(
                                                    progress = overallPct,
                                                    completedTiles = comp,
                                                    requiredTiles = req,
                                                    statusMessage = detailText
                                                )
                                            }
                                        }
                                    }
                                }

                                override fun onError(error: OfflineRegionError) {
                                    mainHandler.post {
                                        runCatching { offlineRegion.setObserver(null) }
                                        runCatching { activeDownloadingRegion?.setDownloadState(OfflineRegion.STATE_INACTIVE) }
                                        activeDownloadingRegion = null
                                        if (currentPresetRetryCount < 2) {
                                            currentPresetRetryCount++
                                            pendingQueue.addFirst(preset)
                                            _state.value = _state.value.copy(
                                                statusMessage = DxCarI18n.tr(
                                                    "${stepPrefix}「$pTitle」連線逾時，自動重試第 $currentPresetRetryCount 次…",
                                                    "${stepPrefix}「$pTitle」连接超时，自动重试第 $currentPresetRetryCount 次…",
                                                    "${stepPrefix}\"$pTitle\" timed out, retrying (#$currentPresetRetryCount)…",
                                                    "${stepPrefix}「$pTitle」タイムアウト、自動再試行 ($currentPresetRetryCount 回目)…"
                                                )
                                            )
                                            mainHandler.postDelayed({ startNextInQueue() }, 1500L)
                                        } else {
                                            currentPresetRetryCount = 0
                                            failedRegionTitles += pTitle
                                            activeBatchCompleted++
                                            if (pendingQueue.isNotEmpty()) {
                                                _state.value = _state.value.copy(
                                                    statusMessage = DxCarI18n.tr(
                                                        "「$pTitle」重試失敗，先繼續下載下一區…",
                                                        "「$pTitle」重试失败，先继续下载下一区…",
                                                        "\"$pTitle\" failed after retries, continuing to next region…",
                                                        "「$pTitle」再試行失敗、次のエリアへ進みます…"
                                                    )
                                                )
                                                mainHandler.postDelayed({ startNextInQueue() }, 800L)
                                            } else {
                                                _state.value = _state.value.copy(
                                                    downloadingRegionId = null,
                                                    statusMessage = DxCarI18n.tr(
                                                        "下載中斷（${failedRegionTitles.joinToString("、")}）：${error.message}，可點「重新下載」補齊",
                                                        "下载中断（${failedRegionTitles.joinToString("、")}）：${error.message}，可点「重新下载」补齐",
                                                        "Download interrupted (${failedRegionTitles.joinToString(", ")}): ${error.message}",
                                                        "ダウンロード中断（${failedRegionTitles.joinToString("・")}）：${error.message}"
                                                    )
                                                )
                                            }
                                        }
                                    }
                                }

                                override fun mapboxTileCountLimitExceeded(limit: Long) {
                                    mainHandler.post {
                                        runCatching { offlineRegion.setObserver(null) }
                                        runCatching { activeDownloadingRegion?.setDownloadState(OfflineRegion.STATE_INACTIVE) }
                                        activeDownloadingRegion = null
                                        pendingQueue.clear()
                                        _state.value = _state.value.copy(
                                            downloadingRegionId = null,
                                            statusMessage = DxCarI18n.tr(
                                                "已達圖磚儲存上限 ($limit)，已保存目前已下載範圍",
                                                "已达图砖存储上限 ($limit)，已保存当前已下载范围",
                                                "Tile limit reached ($limit). Downloaded area saved.",
                                                "タイル保存上限 ($limit) に達しました。保存済み範囲を維持します"
                                            )
                                        )
                                    }
                                }
                            })
                            offlineRegion.setDownloadState(OfflineRegion.STATE_ACTIVE)
                        }

                        override fun onError(error: String) {
                            mainHandler.post {
                                failedRegionTitles += pTitle
                                activeBatchCompleted++
                                if (pendingQueue.isNotEmpty()) {
                                    mainHandler.postDelayed({ startNextInQueue() }, 800L)
                                } else {
                                    _state.value = _state.value.copy(
                                        downloadingRegionId = null,
                                        statusMessage = DxCarI18n.tr(
                                            "建立離線區域失敗：$error",
                                            "创建离线区域失败：$error",
                                            "Failed to create offline region: $error",
                                            "オフライン領域の作成に失敗：$error"
                                        )
                                    )
                                }
                            }
                        }
                    }
                )
            }

            manager.listOfflineRegions(object : OfflineManager.ListOfflineRegionsCallback {
                override fun onList(offlineRegions: Array<OfflineRegion>?) {
                    val stale = offlineRegions?.filter { reg ->
                        val meta = runCatching { String(reg.metadata, Charsets.UTF_8) }.getOrDefault("")
                        meta.startsWith("${preset.id}|")
                    }.orEmpty()
                    if (stale.isEmpty()) {
                        startCreate()
                    } else {
                        var remaining = stale.size
                        stale.forEach { old ->
                            old.delete(object : OfflineRegion.OfflineRegionDeleteCallback {
                                override fun onDelete() {
                                    if (--remaining <= 0) startCreate()
                                }
                                override fun onError(error: String) {
                                    if (--remaining <= 0) startCreate()
                                }
                            })
                        }
                    }
                }
                override fun onError(error: String) {
                    startCreate()
                }
            })
        }.onFailure { e ->
            pendingQueue.clear()
            val errMsg = e.localizedMessage ?: DxCarI18n.tr(
                "請稍後再試",
                "请稍后再试",
                "Please try again later",
                "しばらくしてから再試行してください"
            )
            _state.value = _state.value.copy(
                downloadingRegionId = null,
                statusMessage = DxCarI18n.tr(
                    "離線引擎尚未就緒：$errMsg",
                    "离线引擎尚未就绪：$errMsg",
                    "Offline engine not ready: $errMsg",
                    "オフラインエンジン準備中：$errMsg"
                )
            )
        }
    }

    fun cancelCurrentDownload() {
        pendingQueue.clear()
        runCatching {
            activeDownloadingRegion?.setObserver(null)
            activeDownloadingRegion?.setDownloadState(OfflineRegion.STATE_INACTIVE)
        }
        activeDownloadingRegion = null
        _state.value = _state.value.copy(
            downloadingRegionId = null,
            progress = 0f,
            statusMessage = DxCarI18n.tr(
                "已取消離線地圖下載",
                "已取消离线地图下载",
                "Offline map download cancelled",
                "オフライン地図のダウンロードをキャンセルしました"
            )
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
        val removedTitle = presetTitleTr(regionId, target?.titleZh ?: regionId)
        _state.value = _state.value.copy(
            statusMessage = DxCarI18n.tr(
                "已移除「$removedTitle」離線地圖",
                "已移除「$removedTitle」离线地图",
                "Removed \"$removedTitle\" offline map",
                "「$removedTitle」オフライン地図を削除しました"
            )
        )
    }

    fun deleteAllOfflineRegions(onComplete: () -> Unit = {}) {
        cancelCurrentDownload()
        preferences.update { cur -> cur.copy(offlineRegions = emptyList()) }
        runCatching {
            MapLibre.getInstance(context)
            val manager = OfflineManager.getInstance(context)
            manager.listOfflineRegions(object : OfflineManager.ListOfflineRegionsCallback {
                override fun onList(offlineRegions: Array<OfflineRegion>?) {
                    val all = offlineRegions?.toList().orEmpty()
                    if (all.isEmpty()) {
                        mainHandler.post {
                            _state.value = _state.value.copy(
                                statusMessage = DxCarI18n.tr(
                                    "✓ 已清空所有離線圖資與殘留快取",
                                    "✓ 已清空所有离线图资与残留缓存",
                                    "✓ Cleared all offline maps & cached fragments",
                                    "✓ すべてのオフライン地図とキャッシュを削除しました"
                                )
                            )
                            onComplete()
                        }
                        return
                    }
                    var remaining = all.size
                    all.forEach { reg ->
                        reg.delete(object : OfflineRegion.OfflineRegionDeleteCallback {
                            override fun onDelete() {
                                if (--remaining <= 0) {
                                    mainHandler.post {
                                        _state.value = _state.value.copy(
                                            statusMessage = DxCarI18n.tr(
                                                "✓ 已清空全部 ${all.size} 組離線圖資與殘留區段",
                                                "✓ 已清空全部 ${all.size} 组离线图资与残留区段",
                                                "✓ Cleared all ${all.size} offline map regions",
                                                "✓ 全 ${all.size} 件のオフライン地図を削除しました"
                                            )
                                        )
                                        onComplete()
                                    }
                                }
                            }
                            override fun onError(error: String) {
                                if (--remaining <= 0) {
                                    mainHandler.post { onComplete() }
                                }
                            }
                        })
                    }
                }
                override fun onError(error: String) {
                    mainHandler.post { onComplete() }
                }
            })
        }.onFailure {
            mainHandler.post { onComplete() }
        }
    }

    private fun resolveOfflineCapableStyleUri(style: MapEngineStyle): String {
        return when (style) {
            MapEngineStyle.OPENFREEMAP_VECTOR_3D -> MapStyleFactory.OPENFREEMAP_DAY_URL
            MapEngineStyle.OPENFREEMAP_DARK -> MapStyleFactory.OPENFREEMAP_NIGHT_URL
            MapEngineStyle.GOOGLE_SATELLITE_HYBRID -> {
                val port = ensureLocalStyleServer()
                "http://127.0.0.1:$port/style/y.json"
            }
            MapEngineStyle.GOOGLE_TRAFFIC_TILES -> {
                val port = ensureLocalStyleServer()
                "http://127.0.0.1:$port/style/m.json"
            }
        }
    }

    companion object {
        const val TOKEN_AUTO_LOCAL_REGION = "__auto_current_local_region__"
        const val TOKEN_AUTO_ENTIRE_COUNTRY = "__auto_entire_current_country__"

        private fun trLang(
            lang: EffectiveLanguage,
            zhTw: String,
            zhCn: String,
            en: String,
            ja: String
        ): String = when (lang) {
            EffectiveLanguage.ZH_TW -> zhTw
            EffectiveLanguage.ZH_CN -> zhCn
            EffectiveLanguage.EN -> en
            EffectiveLanguage.JA -> ja
        }

        fun presetTitleTr(
            id: String,
            fallbackZh: String = "",
            lang: EffectiveLanguage = DxCarI18n.current()
        ): String = when (id) {
            "current_gps_20km" -> trLang(lang, "目前車機周邊 (30km 高精細)", "当前车机周边 (30km 高精细)", "Around Vehicle (30km HD)", "現在地周辺 (30km 高精細)")
            "tw_north" -> trLang(lang, "北北基桃地區", "北北基桃地区", "Northern Taiwan (Taipei/Taoyuan)", "台湾北部（台北・新北・基隆・桃園）")
            "tw_hsinchu_miaoli" -> trLang(lang, "竹苗地區", "竹苗地区", "Hsinchu & Miaoli", "新竹・苗栗エリア")
            "tw_central" -> trLang(lang, "中彰投地區", "中彰投地区", "Central Taiwan (Taichung)", "台湾中部（台中・彰化・南投）")
            "tw_yunlin_chiayi_tainan" -> trLang(lang, "雲嘉南地區", "云嘉南地区", "Yunlin, Chiayi & Tainan", "雲林・嘉義・台南エリア")
            "tw_kaohsiung_pingtung" -> trLang(lang, "高屏地區", "高屏地区", "Kaohsiung & Pingtung", "高雄・屏東エリア")
            "tw_east" -> trLang(lang, "宜花東地區", "宜花东地区", "Eastern Taiwan (Yilan/Hualien/Taitung)", "台湾東部（宜蘭・花蓮・台東）")
            "tw_islands" -> trLang(lang, "澎金馬離島地區", "澎金马离岛地区", "Outlying Islands (Penghu/Kinmen/Matsu)", "離島エリア（澎湖・金門・馬祖）")
            "country_hk_mo" -> trLang(lang, "港澳全境地圖", "港澳全境地图", "Hong Kong & Macau", "香港・マカオ全域地図")
            "country_jp_main" -> trLang(lang, "日本全國主要幹道圖資", "日本全国主要干道图资", "Japan Nationwide Trunk Roads", "日本全国の主要幹線道路")
            "country_kr_main" -> trLang(lang, "韓國全國主要幹道圖資", "韩国全国主要干道图资", "South Korea Nationwide Trunk Roads", "韓国全国の主要幹線道路")
            "country_dynamic_wide" -> trLang(lang, "目前所在國家／廣域路網", "当前所在国家／广域路网", "Current Country / Wide Region", "現在の国・広域道路網")
            else -> if (lang == EffectiveLanguage.ZH_CN) DxCarI18n.toSimplifiedChinese(fallbackZh) else fallbackZh
        }

        fun presetSubtitleTr(
            id: String,
            fallbackZh: String = "",
            lang: EffectiveLanguage = DxCarI18n.current()
        ): String = when (id) {
            "current_gps_20km" -> trLang(lang, "依當前 GPS 自動框選最常用街道生活圈（支援街巷級縮放）", "按当前 GPS 自动框选最常用街道生活圈（支持街巷级缩放）", "Auto-selects local streets around current GPS (street-level zoom)", "現在のGPS周辺エリアを自動選択（街路レベルのズーム対応）")
            "tw_north" -> trLang(lang, "台北 · 新北 · 基隆 · 桃園主要路網", "台北 · 新北 · 基隆 · 桃园主要路网", "Taipei · New Taipei · Keelung · Taoyuan road network", "台北・新北・基隆・桃園の主要道路網")
            "tw_hsinchu_miaoli" -> trLang(lang, "新竹縣市 · 苗栗主要路網", "新竹县市 · 苗栗主要路网", "Hsinchu City/County · Miaoli road network", "新竹市・新竹県・苗栗の主要道路網")
            "tw_central" -> trLang(lang, "台中 · 彰化 · 南投主要路網", "台中 · 彰化 · 南投主要路网", "Taichung · Changhua · Nantou road network", "台中・彰化・南投の主要道路網")
            "tw_yunlin_chiayi_tainan" -> trLang(lang, "雲林 · 嘉義 · 台南主要路網", "云林 · 嘉义 · 台南主要路网", "Yunlin · Chiayi · Tainan road network", "雲林・嘉義・台南の主要道路網")
            "tw_kaohsiung_pingtung" -> trLang(lang, "高雄 · 屏東 · 墾丁主要路網", "高雄 · 屏东 · 垦丁主要路网", "Kaohsiung · Pingtung · Kenting road network", "高雄・屏東・墾丁の主要道路網")
            "tw_east" -> trLang(lang, "宜蘭 · 花蓮 · 台東山海線", "宜兰 · 花莲 · 台东山海线", "Yilan · Hualien · Taitung coastal & mountain routes", "宜蘭・花蓮・台東の山海ルート")
            "tw_islands" -> trLang(lang, "澎湖 · 金門 · 馬祖主要路網", "澎湖 · 金门 · 马祖主要路网", "Penghu · Kinmen · Matsu road network", "澎湖・金門・馬祖の主要道路網")
            "country_hk_mo" -> trLang(lang, "香港 · 九龍 · 新界 · 澳門完整路網", "香港 · 九龙 · 新界 · 澳门完整路网", "Hong Kong · Kowloon · New Territories · Macau network", "香港・九龍・新界・マカオの全道路網")
            "country_jp_main" -> trLang(lang, "日本全境高速與幹線路網", "日本全境高速与干线路网", "Japan expressways & national trunk network", "日本全国の高速道路・主要国道網")
            "country_kr_main" -> trLang(lang, "韓國全境高速與幹線路網", "韩国全境高速与干线路网", "South Korea expressways & national trunk network", "韓国全国の高速道路・幹線道路網")
            "country_dynamic_wide" -> trLang(lang, "依當前國家座標自動框選廣域幹道", "按当前国家坐标自动框选广域干道", "Auto-selected wide-area trunk roads around current country", "現在地の座標から広域幹線網を自動選択")
            else -> if (lang == EffectiveLanguage.ZH_CN) DxCarI18n.toSimplifiedChinese(fallbackZh) else fallbackZh
        }

        @Composable
        fun presetTitleStr(id: String, fallbackZh: String = ""): String = when (id) {
            "current_gps_20km" -> str("目前車機周邊 (30km 高精細)", "当前车机周边 (30km 高精细)", "Around Vehicle (30km HD)", "現在地周辺 (30km 高精細)")
            "tw_north" -> str("北北基桃地區", "北北基桃地区", "Northern Taiwan (Taipei/Taoyuan)", "台湾北部（台北・新北・基隆・桃園）")
            "tw_hsinchu_miaoli" -> str("竹苗地區", "竹苗地区", "Hsinchu & Miaoli", "新竹・苗栗エリア")
            "tw_central" -> str("中彰投地區", "中彰投地区", "Central Taiwan (Taichung)", "台湾中部（台中・彰化・南投）")
            "tw_yunlin_chiayi_tainan" -> str("雲嘉南地區", "云嘉南地区", "Yunlin, Chiayi & Tainan", "雲林・嘉義・台南エリア")
            "tw_kaohsiung_pingtung" -> str("高屏地區", "高屏地区", "Kaohsiung & Pingtung", "高雄・屏東エリア")
            "tw_east" -> str("宜花東地區", "宜花东地区", "Eastern Taiwan (Yilan/Hualien/Taitung)", "台湾東部（宜蘭・花蓮・台東）")
            "tw_islands" -> str("澎金馬離島地區", "澎金马离岛地区", "Outlying Islands (Penghu/Kinmen/Matsu)", "離島エリア（澎湖・金門・馬祖）")
            "country_hk_mo" -> str("港澳全境地圖", "港澳全境地图", "Hong Kong & Macau", "香港・マカオ全域地図")
            "country_jp_main" -> str("日本全國主要幹道圖資", "日本全国主要干道图资", "Japan Nationwide Trunk Roads", "日本全国の主要幹線道路")
            "country_kr_main" -> str("韓國全國主要幹道圖資", "韩国全国主要干道图资", "South Korea Nationwide Trunk Roads", "韓国全国の主要幹線道路")
            "country_dynamic_wide" -> str("目前所在國家／廣域路網", "当前所在国家／广域路网", "Current Country / Wide Region", "現在の国・広域道路網")
            else -> str(fallbackZh, DxCarI18n.toSimplifiedChinese(fallbackZh), fallbackZh, fallbackZh)
        }

        @Composable
        fun presetSubtitleStr(id: String, fallbackZh: String = ""): String = when (id) {
            "current_gps_20km" -> str("依當前 GPS 自動框選最常用街道生活圈（支援街巷級縮放）", "按当前 GPS 自动框选最常用街道生活圈（支持街巷级缩放）", "Auto-selects local streets around current GPS (street-level zoom)", "現在のGPS周辺エリアを自動選択（街路レベルのズーム対応）")
            "tw_north" -> str("台北 · 新北 · 基隆 · 桃園主要路網", "台北 · 新北 · 基隆 · 桃园主要路网", "Taipei · New Taipei · Keelung · Taoyuan road network", "台北・新北・基隆・桃園の主要道路網")
            "tw_hsinchu_miaoli" -> str("新竹縣市 · 苗栗主要路網", "新竹县市 · 苗栗主要路网", "Hsinchu City/County · Miaoli road network", "新竹市・新竹県・苗栗の主要道路網")
            "tw_central" -> str("台中 · 彰化 · 南投主要路網", "台中 · 彰化 · 南投主要路网", "Taichung · Changhua · Nantou road network", "台中・彰化・南投の主要道路網")
            "tw_yunlin_chiayi_tainan" -> str("雲林 · 嘉義 · 台南主要路網", "云林 · 嘉义 · 台南主要路网", "Yunlin · Chiayi · Tainan road network", "雲林・嘉義・台南の主要道路網")
            "tw_kaohsiung_pingtung" -> str("高雄 · 屏東 · 墾丁主要路網", "高雄 · 屏东 · 垦丁主要路网", "Kaohsiung · Pingtung · Kenting road network", "高雄・屏東・墾丁の主要道路網")
            "tw_east" -> str("宜蘭 · 花蓮 · 台東山海線", "宜兰 · 花莲 · 台东山海线", "Yilan · Hualien · Taitung coastal & mountain routes", "宜蘭・花蓮・台東の山海ルート")
            "tw_islands" -> str("澎湖 · 金門 · 馬祖主要路網", "澎湖 · 金门 · 马祖主要路网", "Penghu · Kinmen · Matsu road network", "澎湖・金門・馬祖の主要道路網")
            "country_hk_mo" -> str("香港 · 九龍 · 新界 · 澳門完整路網", "香港 · 九龙 · 新界 · 澳门完整路网", "Hong Kong · Kowloon · New Territories · Macau network", "香港・九龍・新界・マカオの全道路網")
            "country_jp_main" -> str("日本全境高速與幹線路網", "日本全境高速与干线路网", "Japan expressways & national trunk network", "日本全国の高速道路・主要国道網")
            "country_kr_main" -> str("韓國全境高速與幹線路網", "韩国全境高速与干线路网", "South Korea expressways & national trunk network", "韓国全国の高速道路・幹線道路網")
            "country_dynamic_wide" -> str("依當前國家座標自動框選廣域幹道", "按当前国家坐标自动框选广域干道", "Auto-selected wide-area trunk roads around current country", "現在地の座標から広域幹線網を自動選択")
            else -> str(fallbackZh, DxCarI18n.toSimplifiedChinese(fallbackZh), fallbackZh, fallbackZh)
        }

        fun describeDetectedLocation(
            lat: Double,
            lon: Double,
            lang: EffectiveLanguage = DxCarI18n.current()
        ): DetectedOfflineLocationInfo {
            val effLat = if (lat != 0.0) lat else 25.0330
            val effLon = if (lon != 0.0) lon else 121.5654

            val inTaiwan = effLat in 21.6..26.5 && effLon in 118.0..122.4
            if (inTaiwan) {
                val regId = when {
                    effLon < 119.95 -> "tw_islands"
                    effLon >= 121.32 && effLat < 24.82 -> "tw_east"
                    effLat >= 24.78 -> "tw_north"
                    effLat >= 24.35 -> "tw_hsinchu_miaoli"
                    effLat >= 23.78 -> "tw_central"
                    effLat >= 22.88 -> "tw_yunlin_chiayi_tainan"
                    else -> "tw_kaohsiung_pingtung"
                }
                return DetectedOfflineLocationInfo(
                    localRegionId = regId,
                    localRegionTitleZh = presetTitleTr(regId, lang = lang),
                    countryCode = "TW",
                    countryTitleZh = trLang(
                        lang,
                        "台灣全境（本島＋離島全 7 區）",
                        "台湾全境（本岛＋离岛全 7 区）",
                        "All Taiwan (7 Regions + Islands)",
                        "台湾全域（本島＋離島 全7エリア）"
                    ),
                    totalCountryZones = 7
                )
            }

            if (effLat in 22.05..22.60 && effLon in 113.5..114.5) {
                return DetectedOfflineLocationInfo(
                    localRegionId = "current_gps_20km",
                    localRegionTitleZh = trLang(lang, "港澳周邊生活圈", "港澳周边生活圈", "HK & Macau Local Area", "香港・マカオ周辺エリア"),
                    countryCode = "HK_MO",
                    countryTitleZh = trLang(lang, "香港／澳門全境", "香港／澳门全境", "All Hong Kong & Macau", "香港・マカオ全域"),
                    totalCountryZones = 1
                )
            }

            if (effLat in 24.0..45.8 && effLon in 122.9..146.0) {
                return DetectedOfflineLocationInfo(
                    localRegionId = "current_gps_20km",
                    localRegionTitleZh = trLang(lang, "日本當前周邊 (30km)", "日本当前周边 (30km)", "Japan Local (30km)", "日本 現在地周辺 (30km)"),
                    countryCode = "JP",
                    countryTitleZh = trLang(lang, "日本全國主要路網＋周邊", "日本全国主要路网＋周边", "All Japan Trunk Roads + Local", "日本全国主要道路＋周辺"),
                    totalCountryZones = 2
                )
            }

            if (effLat in 33.0..38.7 && effLon in 124.5..131.0) {
                return DetectedOfflineLocationInfo(
                    localRegionId = "current_gps_20km",
                    localRegionTitleZh = trLang(lang, "韓國當前周邊 (30km)", "韩国当前周边 (30km)", "Korea Local (30km)", "韓国 現在地周辺 (30km)"),
                    countryCode = "KR",
                    countryTitleZh = trLang(lang, "韓國全國主要路網＋周邊", "韩国全国主要路网＋周边", "All Korea Trunk Roads + Local", "韓国全国主要道路＋周辺"),
                    totalCountryZones = 2
                )
            }

            return DetectedOfflineLocationInfo(
                localRegionId = "current_gps_20km",
                localRegionTitleZh = trLang(lang, "目前所在區域 (周邊 30km)", "当前所在区域 (周边 30km)", "Current Area (30km)", "現在地エリア (周辺 30km)"),
                countryCode = "GLOBAL",
                countryTitleZh = trLang(lang, "目前所在國家／廣域全區", "当前所在国家／广域全区", "Current Country / Wide Area", "現在の国・広域エリア"),
                totalCountryZones = 2
            )
        }
    }
}

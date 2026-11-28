package com.dxcar.launcher.map

import android.content.Context
import android.location.Geocoder
import android.location.Location
import android.os.Build
import com.dxcar.launcher.comms.TtsAnnouncer
import com.dxcar.launcher.i18n.DxCarI18n
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

data class NavSearchResult(
    val name: String,
    val subtitle: String,
    val latitude: Double,
    val longitude: Double
)

data class NavRoutePoint(
    val latitude: Double,
    val longitude: Double
)

data class NavLaneGuide(
    val indications: List<String>,
    val valid: Boolean
)

data class NavRouteStep(
    val instructionZh: String,
    val roadName: String,
    val maneuverLat: Double,
    val maneuverLon: Double,
    val distanceMeters: Double,
    val durationSeconds: Double = 0.0,
    val maneuverType: String,
    val modifier: String,
    val lanes: List<NavLaneGuide> = emptyList(),
    val destinations: String = "",
    val exits: String = "",
    val isHighwayManeuver: Boolean = false
)

data class BuiltInNavState(
    val isSearching: Boolean = false,
    val isRouting: Boolean = false,
    val errorMessage: String = "",
    val searchResults: List<NavSearchResult> = emptyList(),
    val isNavigating: Boolean = false,
    val destinationName: String = "",
    val destinationLat: Double = 0.0,
    val destinationLon: Double = 0.0,
    val routePoints: List<NavRoutePoint> = emptyList(),
    val steps: List<NavRouteStep> = emptyList(),
    val currentStepIndex: Int = 0,
    val currentInstructionZh: String = "",
    val nextStepInstructionZh: String = "",
    val upcomingLanes: List<NavLaneGuide> = emptyList(),
    val upcomingExitLabel: String = "",
    val isHighwayEarlyAlert: Boolean = false,
    val avoidEcoDetourFastest: Boolean = true,
    val preferShortestDistance: Boolean = false,
    val distanceToNextTurnMeters: Int = 0,
    val remainingDistanceMeters: Int = 0,
    val remainingDurationSeconds: Int = 0
)

/**
 * Zero-API-Key Built-In Navigation Engine:
 * - 4-Engine Address & POI Geocoding:
 *   1) Android system Geocoder (Google GMS rooftop precision for Taiwan street numbers)
 *   2) ArcGIS World Geocoding Service (exact Taiwan door-number & postal match, zero key)
 *   3) Nominatim (OSM) with smart Chinese address tokenization & street-level relaxation
 *   4) Komoot Photon fallback
 * - Turn-by-turn routing powered by public OSRM driving server
 * - Live GPS step tracking + Chinese TTS voice prompts via [TtsAnnouncer]
 */
class BuiltInNavEngine(
    private val ttsAnnouncer: TtsAnnouncer?,
    private val appContext: Context? = null
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .build()

    private val _state = MutableStateFlow(BuiltInNavState())
    val state: StateFlow<BuiltInNavState> = _state.asStateFlow()

    private var lastAnnouncedStepKey = ""
    private var lastRerouteTimeMs = 0L
    @Volatile
    private var cachedLocalAreaLat = 0.0
    @Volatile
    private var cachedLocalAreaLon = 0.0
    @Volatile
    private var cachedLocalCity = ""
    @Volatile
    private var cachedLocalDistrict = ""
    @Volatile
    private var cachedLocalAreaAtMs = 0L

    fun searchPlaces(query: String, biasLat: Double, biasLon: Double) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) {
            _state.value = _state.value.copy(searchResults = emptyList(), errorMessage = "")
            return
        }
        scope.launch {
            _state.value = _state.value.copy(isSearching = true, errorMessage = "")
            val result = withContext(Dispatchers.IO) {
                runCatching { queryPlacesDualEngine(trimmed, biasLat, biasLon) }
            }
            result.onSuccess { list ->
                _state.value = _state.value.copy(
                    isSearching = false,
                    searchResults = list,
                    errorMessage = if (list.isEmpty()) "找不到符合「$trimmed」的地點" else ""
                )
            }.onFailure { err ->
                _state.value = _state.value.copy(
                    isSearching = false,
                    errorMessage = "搜尋失敗：${err.message ?: "請檢查網路"}"
                )
            }
        }
    }

    /**
     * One-step Gemini Voice -> Built-in Plan A Map Navigation:
     * Resolves destination coordinates (using Gemini coordinates if available, or Nominatim zh-TW + Photon),
     * then draws the live blue OSRM route & turn-by-turn guidance directly on the Launcher's own Map!
     */
    fun navigateByQuery(
        query: String,
        startLat: Double,
        startLon: Double,
        explicitDestLat: Double? = null,
        explicitDestLon: Double? = null,
        onStatus: (String) -> Unit = {}
    ) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return
        val effStartLat = if (startLat != 0.0) startLat else 25.0330
        val effStartLon = if (startLon != 0.0) startLon else 121.5654

        if (explicitDestLat != null && explicitDestLon != null &&
            explicitDestLat != 0.0 && explicitDestLon != 0.0
        ) {
            onStatus("🗺️ 啟動器地圖正在規劃前往「$trimmed」的路線…")
            startNavigation(
                startLat = effStartLat,
                startLon = effStartLon,
                destName = trimmed,
                destLat = explicitDestLat,
                destLon = explicitDestLon
            )
            return
        }

        scope.launch {
            _state.value = _state.value.copy(isSearching = true, errorMessage = "")
            onStatus("🔍 啟動器內建地圖正在搜尋：「$trimmed」…")
            val foundResult = withContext(Dispatchers.IO) {
                runCatching { queryPlacesDualEngine(trimmed, effStartLat, effStartLon) }
            }
            foundResult.onSuccess { list ->
                if (list.isEmpty()) {
                    _state.value = _state.value.copy(
                        isSearching = false,
                        errorMessage = "內建地圖找不到「$trimmed」，請換個地標名稱再試一次"
                    )
                    ttsAnnouncer?.speak("抱歉，地圖找不到$trimmed")
                    onStatus("⚠️ 內建地圖找不到「$trimmed」")
                } else {
                    val best = list.first()
                    _state.value = _state.value.copy(
                        isSearching = false,
                        searchResults = list
                    )
                    onStatus("🗺️ 已在啟動器內建地圖規劃前往：${best.name}")
                    startNavigation(
                        startLat = effStartLat,
                        startLon = effStartLon,
                        destName = best.name,
                        destLat = best.latitude,
                        destLon = best.longitude
                    )
                }
            }.onFailure { err ->
                _state.value = _state.value.copy(
                    isSearching = false,
                    errorMessage = "搜尋失敗：${err.message ?: "請檢查網路"}"
                )
                onStatus("⚠️ 內建地圖搜尋失敗，請確認網路連線")
            }
        }
    }

    /**
     * Public suspend geocoder for interactive map pin detection & confirmation UI.
     */
    suspend fun resolveAddressCandidates(
        query: String,
        biasLat: Double,
        biasLon: Double
    ): List<NavSearchResult> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return@withContext emptyList()
        runCatching { queryPlacesDualEngine(trimmed, biasLat, biasLon) }.getOrDefault(emptyList())
    }

    private data class GeoRegionPolicy(
        val code: String,
        val isIsland: Boolean,
        val nominatimCc: String,
        val arcGisCc: String,
        val minLat: Double,
        val maxLat: Double,
        val minLon: Double,
        val maxLon: Double,
        val countryHint: String
    )

    private fun detectGeoRegion(biasLat: Double, biasLon: Double, query: String): GeoRegionPolicy? {
        val twHint = query.contains("台灣") || query.contains("臺灣") ||
            Regex("(台北|臺北|新北|桃園|台中|臺中|台南|臺南|高雄|基隆|新竹|苗栗|彰化|南投|雲林|嘉義|屏東|宜蘭|花蓮|台東|臺東|澎湖|金門|馬祖)").containsMatchIn(query)
        return when {
            (biasLat in 21.5..26.5 && biasLon in 118.0..122.6) || twHint ->
                GeoRegionPolicy("TW", true, "tw", "TWN", 21.8, 25.4, 119.2, 122.1, "台灣")
            biasLat in 24.0..46.0 && biasLon in 122.9..146.0 ->
                GeoRegionPolicy("JP", true, "jp", "JPN", 24.0, 46.0, 122.9, 146.0, "日本")
            biasLat in 4.5..21.2 && biasLon in 116.8..126.8 ->
                GeoRegionPolicy("PH", true, "ph", "PHL", 4.5, 21.2, 116.8, 126.8, "Philippines")
            biasLat in -44.0..-10.0 && biasLon in 112.0..154.0 ->
                GeoRegionPolicy("AU", true, "au", "AUS", -44.0, -10.0, 112.0, 154.0, "Australia")
            biasLat in -47.5..-34.0 && biasLon in 166.0..179.0 ->
                GeoRegionPolicy("NZ", true, "nz", "NZL", -47.5, -34.0, 166.0, 179.0, "New Zealand")
            biasLat in 1.15..1.48 && biasLon in 103.6..104.1 ->
                GeoRegionPolicy("SG", true, "sg", "SGP", 1.15, 1.48, 103.6, 104.1, "Singapore")
            biasLat in 49.8..60.9 && biasLon in -8.2..1.8 ->
                GeoRegionPolicy("GB", true, "gb", "GBR", 49.8, 60.9, -8.2, 1.8, "United Kingdom")
            else -> null
        }
    }

    private fun matchLocalRegisteredShortcuts(trimmed: String): List<NavSearchResult> {
        val cfg = com.dxcar.launcher.DxCarApp.instance?.preferences?.config?.value ?: return emptyList()
        val q = trimmed.lowercase(Locale.ROOT)
        val out = mutableListOf<NavSearchResult>()
        val homeLbl = cfg.homeShortcutLabel.ifBlank { "回家" }
        if (cfg.homeAddress.isNotBlank() && cfg.homeLat != 0.0 && cfg.homeLon != 0.0 &&
            (homeLbl.lowercase(Locale.ROOT).contains(q) || q.contains(homeLbl.lowercase(Locale.ROOT)) ||
                cfg.homeAddress.lowercase(Locale.ROOT).contains(q))
        ) {
            out += NavSearchResult("★ $homeLbl", cfg.homeAddress, cfg.homeLat, cfg.homeLon)
        }
        val workLbl = cfg.workShortcutLabel.ifBlank { "公司" }
        if (cfg.workAddress.isNotBlank() && cfg.workLat != 0.0 && cfg.workLon != 0.0 &&
            (workLbl.lowercase(Locale.ROOT).contains(q) || q.contains(workLbl.lowercase(Locale.ROOT)) ||
                cfg.workAddress.lowercase(Locale.ROOT).contains(q))
        ) {
            out += NavSearchResult("★ $workLbl", cfg.workAddress, cfg.workLat, cfg.workLon)
        }
        cfg.customNavShortcuts.forEach { sc ->
            if (sc.latitude != 0.0 && sc.longitude != 0.0 &&
                (sc.label.lowercase(Locale.ROOT).contains(q) || q.contains(sc.label.lowercase(Locale.ROOT)) ||
                    sc.address.lowercase(Locale.ROOT).contains(q))
            ) {
                out += NavSearchResult("★ ${sc.label}", sc.address, sc.latitude, sc.longitude)
            }
        }
        return out
    }

    private fun isWithinPolicy(item: NavSearchResult, region: GeoRegionPolicy?, biasLat: Double, biasLon: Double): Boolean {
        if (region != null && region.isIsland) {
            return item.latitude in region.minLat..region.maxLat &&
                item.longitude in region.minLon..region.maxLon
        }
        if (biasLat != 0.0 && biasLon != 0.0) {
            val distKm = distanceMeters(biasLat, biasLon, item.latitude, item.longitude) / 1000.0
            if (distKm > 2500.0) return false
        }
        return true
    }

    private fun queryPlacesDualEngine(trimmed: String, biasLat: Double, biasLon: Double): List<NavSearchResult> {
        val (effLat, effLon) = resolveFreshestStartCoords(biasLat, biasLon)
        val hasGps = effLat != 0.0 && effLon != 0.0
        val localMatches = matchLocalRegisteredShortcuts(trimmed)
        val region = detectGeoRegion(effLat, effLon, trimmed)
        val isLikelyTaiwan = region?.code == "TW"
        val userSpecifiedCity = containsExplicitCountyOrCity(trimmed)

        // Resolve user's current County/City (e.g. "高雄市") so nearby same-city matches ALWAYS win first!
        val (localCity, localDistrict) = if (hasGps && !userSpecifiedCity) {
            resolveCurrentLocalCityDistrict(effLat, effLon, isLikelyTaiwan)
        } else {
            "" to ""
        }

        val nearbyCandidates = mutableListOf<NavSearchResult>()

        // ==================== STAGE 1: STRICTLY NEARBY & SAME-COUNTY FIRST ====================
        // When user searches a keyword/road without naming another city, search tightly around
        // the user's live GPS position & current county first!
        if (hasGps && !userSpecifiedCity) {
            val localDeltaLat = 0.32 // ~35 km
            val localDeltaLon = 0.35 // ~35 km
            val minNearLat = effLat - localDeltaLat
            val maxNearLat = effLat + localDeltaLat
            val minNearLon = effLon - localDeltaLon
            val maxNearLon = effLon + localDeltaLon

            fun isTrulyNearbyOrSameCity(item: NavSearchResult): Boolean {
                if (!isWithinPolicy(item, region, effLat, effLon)) return false
                val distKm = distanceMeters(effLat, effLon, item.latitude, item.longitude) / 1000.0
                if (distKm <= 45.0) return true
                if (localCity.isNotBlank()) {
                    val normCity = normalizeCityAlias(localCity)
                    val combinedText = normalizeCityAlias("${item.name} ${item.subtitle}")
                    if (combinedText.contains(normCity)) return true
                }
                return false
            }

            // 1A. Android GMS Geocoder (strictly bounded to ~35km box around current GPS + localCity hint)
            val ctx = appContext
            if (ctx != null && runCatching { Geocoder.isPresent() }.getOrDefault(false)) {
                runCatching {
                    val locale = if (isLikelyTaiwan) Locale.TRADITIONAL_CHINESE else Locale.getDefault()
                    val geocoder = Geocoder(ctx, locale)
                    val nearQueries = buildList {
                        if (localDistrict.isNotBlank() && localCity.isNotBlank()) add("$localCity$localDistrict$trimmed")
                        if (localCity.isNotBlank()) add("$localCity$trimmed")
                        add(trimmed)
                    }.distinct()
                    for (gq in nearQueries) {
                        if (nearbyCandidates.size >= 5) break
                        @Suppress("DEPRECATION")
                        val addrs = geocoder.getFromLocationName(
                            gq, 6,
                            minNearLat, minNearLon, maxNearLat, maxNearLon
                        )
                        if (!addrs.isNullOrEmpty()) {
                            for (a in addrs) {
                                if (a.hasLatitude() && a.hasLongitude()) {
                                    val candidate = addressToNavResult(a, trimmed)
                                    if (isTrulyNearbyOrSameCity(candidate)) {
                                        nearbyCandidates += candidate
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 1B. ArcGIS World Geocoding (strictly constrained to local searchExtent + proximity bias)
            if (nearbyCandidates.size < 4) {
                runCatching {
                    val arcQueries = buildList {
                        if (localCity.isNotBlank()) add("$localCity $trimmed")
                        add(trimmed)
                    }.distinct()
                    val countryParam = if (region != null && region.arcGisCc.isNotBlank()) "&countryCode=${region.arcGisCc}" else ""
                    val extentParam = "&searchExtent=$minNearLon,$minNearLat,$maxNearLon,$maxNearLat&location=$effLon,$effLat"
                    for (aq in arcQueries) {
                        if (nearbyCandidates.size >= 5) break
                        val encoded = URLEncoder.encode(aq, "UTF-8")
                        val arcUrl =
                            "https://geocode.arcgis.com/arcgis/rest/services/World/GeocodeServer/findAddressCandidates" +
                                "?SingleLine=$encoded$countryParam$extentParam&maxLocations=6&outFields=Match_addr,Place_addr,Type&f=pjson"
                        val req = Request.Builder()
                            .url(arcUrl)
                            .header("User-Agent", "DX-Car-Launcher/1.9.3 (Android Car HeadUnit)")
                            .build()
                        httpClient.newCall(req).execute().use { resp ->
                            if (resp.isSuccessful) {
                                nearbyCandidates += parseArcGisResults(resp.body?.string().orEmpty(), trimmed)
                                    .filter { isTrulyNearbyOrSameCity(it) }
                            }
                        }
                    }
                }
            }

            // 1C. Nominatim strictly bounded to local viewbox (bounded=1)
            if (nearbyCandidates.size < 4) {
                val acceptLang = DxCarI18n.nominatimAcceptLanguage()
                val ccParam = if (region != null && region.nominatimCc.isNotBlank()) "&countrycodes=${region.nominatimCc}" else ""
                val localNomQueries = buildList {
                    add(trimmed)
                    if (localCity.isNotBlank()) add("$localCity $trimmed")
                    val spaced = tokenizeCjkAddress(trimmed)
                    if (spaced != trimmed) add(spaced)
                }.distinct()
                for (qCand in localNomQueries) {
                    if (nearbyCandidates.size >= 5) break
                    runCatching {
                        val enc = URLEncoder.encode(qCand, "UTF-8")
                        val nearUrl =
                            "https://nominatim.openstreetmap.org/search?q=$enc&format=jsonv2&accept-language=$acceptLang$ccParam&viewbox=$minNearLon,$maxNearLat,$maxNearLon,$minNearLat&bounded=1&limit=6"
                        val req = Request.Builder()
                            .url(nearUrl)
                            .header("User-Agent", "DX-Car-Launcher/1.9.3 (Android Car HeadUnit)")
                            .build()
                        httpClient.newCall(req).execute().use { resp ->
                            if (resp.isSuccessful) {
                                nearbyCandidates += parseNominatimResults(resp.body?.string().orEmpty())
                                    .filter { isTrulyNearbyOrSameCity(it) }
                            }
                        }
                    }
                }
            }

            // 1D. Photon tightly bounded to local bbox
            if (nearbyCandidates.isEmpty()) {
                runCatching {
                    val enc = URLEncoder.encode(trimmed, "UTF-8")
                    val photonUrl =
                        "https://photon.komoot.io/api/?q=$enc&lat=$effLat&lon=$effLon&bbox=$minNearLon,$minNearLat,$maxNearLon,$maxNearLat&limit=6"
                    val req = Request.Builder()
                        .url(photonUrl)
                        .header("User-Agent", "DX-Car-Launcher/1.9.3")
                        .build()
                    httpClient.newCall(req).execute().use { resp ->
                        if (resp.isSuccessful) {
                            nearbyCandidates += parsePhotonResults(resp.body?.string().orEmpty())
                                .filter { isTrulyNearbyOrSameCity(it) }
                        }
                    }
                }
            }

            // If Stage 1 found suitable nearby / same-county results, return ONLY those sorted by nearest distance!
            if (nearbyCandidates.isNotEmpty() || localMatches.isNotEmpty()) {
                return finalizeAndRankResults(localMatches, nearbyCandidates, effLat, effLon)
            }
        }

        // ==================== STAGE 2: OTHER COUNTIES / NATIONWIDE FALLBACK ====================
        // Runs ONLY when no suitable nearby match exists OR when user explicitly typed a city/county!
        val wideCandidates = mutableListOf<NavSearchResult>()

        // 2A. Android System Geocoder (full country / island bounds)
        val ctx = appContext
        if (ctx != null && runCatching { Geocoder.isPresent() }.getOrDefault(false)) {
            runCatching {
                val locale = if (isLikelyTaiwan) Locale.TRADITIONAL_CHINESE else Locale.getDefault()
                val geocoder = Geocoder(ctx, locale)
                val geoQueries = if (region != null && region.isIsland && !trimmed.contains(region.countryHint)) {
                    listOf(trimmed, "$trimmed ${region.countryHint}")
                } else {
                    listOf(trimmed)
                }
                for (gq in geoQueries) {
                    if (wideCandidates.size >= 4) break
                    @Suppress("DEPRECATION")
                    val addrs = if (region != null && region.isIsland) {
                        geocoder.getFromLocationName(
                            gq, 6,
                            region.minLat, region.minLon, region.maxLat, region.maxLon
                        )
                    } else {
                        geocoder.getFromLocationName(gq, 6)
                    }
                    if (!addrs.isNullOrEmpty()) {
                        for (a in addrs) {
                            if (a.hasLatitude() && a.hasLongitude()) {
                                val candidate = addressToNavResult(a, trimmed)
                                if (isWithinPolicy(candidate, region, effLat, effLon)) {
                                    wideCandidates += candidate
                                }
                            }
                        }
                    }
                }
            }
        }

        // 2B. ArcGIS World Geocoding Service (country-wide with proximity bias)
        if (wideCandidates.size < 3) {
            runCatching {
                val encoded = URLEncoder.encode(trimmed, "UTF-8")
                val countryParam = if (region != null && region.arcGisCc.isNotBlank()) "&countryCode=${region.arcGisCc}" else ""
                val locParam = if (hasGps) "&location=$effLon,$effLat" else ""
                val arcUrl =
                    "https://geocode.arcgis.com/arcgis/rest/services/World/GeocodeServer/findAddressCandidates" +
                        "?SingleLine=$encoded$countryParam$locParam&maxLocations=6&outFields=Match_addr,Place_addr,Type&f=pjson"
                val req = Request.Builder()
                    .url(arcUrl)
                    .header("User-Agent", "DX-Car-Launcher/1.9.3 (Android Car HeadUnit)")
                    .build()
                httpClient.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        wideCandidates += parseArcGisResults(resp.body?.string().orEmpty(), trimmed)
                            .filter { isWithinPolicy(it, region, effLat, effLon) }
                    }
                }
            }
        }

        // 2C. Nominatim nationwide
        if (wideCandidates.size < 3) {
            val queriesToTry = buildList {
                add(trimmed)
                val spaced = tokenizeCjkAddress(trimmed)
                if (spaced != trimmed) add(spaced)
                val streetOnly = stripDoorNumber(trimmed)
                if (streetOnly.isNotBlank() && streetOnly != trimmed) {
                    add(tokenizeCjkAddress(streetOnly))
                }
            }.distinct()

            val acceptLang = DxCarI18n.nominatimAcceptLanguage()
            val ccParam = if (region != null && region.nominatimCc.isNotBlank()) "&countrycodes=${region.nominatimCc}" else ""
            for (qCandidate in queriesToTry) {
                if (wideCandidates.size >= 4) break
                val enc = URLEncoder.encode(qCandidate, "UTF-8")
                runCatching {
                    val wideUrl =
                        "https://nominatim.openstreetmap.org/search?q=$enc&format=jsonv2&accept-language=$acceptLang$ccParam&limit=6"
                    val req = Request.Builder()
                        .url(wideUrl)
                        .header("User-Agent", "DX-Car-Launcher/1.9.3 (Android Car HeadUnit)")
                        .build()
                    httpClient.newCall(req).execute().use { resp ->
                        if (resp.isSuccessful) {
                            wideCandidates += parseNominatimResults(resp.body?.string().orEmpty())
                                .filter { isWithinPolicy(it, region, effLat, effLon) }
                        }
                    }
                }
            }
        }

        // 2D. Komoot Photon nationwide fallback
        if (wideCandidates.isEmpty()) {
            runCatching {
                val fallbackQ = tokenizeCjkAddress(stripDoorNumber(trimmed).ifBlank { trimmed })
                val enc = URLEncoder.encode(fallbackQ, "UTF-8")
                val bboxParam = if (region != null && region.isIsland) {
                    "&bbox=${region.minLon},${region.minLat},${region.maxLon},${region.maxLat}"
                } else {
                    ""
                }
                val photonUrl = "https://photon.komoot.io/api/?q=$enc&lat=$effLat&lon=$effLon$bboxParam&limit=6"
                val req = Request.Builder()
                    .url(photonUrl)
                    .header("User-Agent", "DX-Car-Launcher/1.9.3")
                    .build()
                httpClient.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        wideCandidates += parsePhotonResults(resp.body?.string().orEmpty())
                            .filter { isWithinPolicy(it, region, effLat, effLon) }
                    }
                }
            }
        }

        return finalizeAndRankResults(localMatches, wideCandidates, effLat, effLon)
    }

    private fun addressToNavResult(a: android.location.Address, fallbackQuery: String): NavSearchResult {
        val line = a.getAddressLine(0).orEmpty().trim()
        val title = a.featureName?.takeIf { it.isNotBlank() && !it.all(Char::isDigit) }
            ?: line.ifBlank { fallbackQuery }
        val sub = line.takeIf { it.isNotBlank() && it != title }
            ?: listOfNotNull(a.adminArea, a.subAdminArea, a.locality, a.thoroughfare)
                .distinct()
                .joinToString("")
                .ifBlank { "%.5f, %.5f".format(a.latitude, a.longitude) }
        return NavSearchResult(
            name = title,
            subtitle = sub,
            latitude = a.latitude,
            longitude = a.longitude
        )
    }

    private fun finalizeAndRankResults(
        localMatches: List<NavSearchResult>,
        external: List<NavSearchResult>,
        effLat: Double,
        effLon: Double
    ): List<NavSearchResult> {
        val hasGps = effLat != 0.0 && effLon != 0.0
        val sortedExternal = if (hasGps) {
            external.sortedBy { distanceMeters(effLat, effLon, it.latitude, it.longitude) }
        } else {
            external
        }
        val deduped = mutableListOf<NavSearchResult>()
        for (item in (localMatches + sortedExternal)) {
            val dup = deduped.any {
                kotlin.math.abs(it.latitude - item.latitude) < 0.0005 &&
                    kotlin.math.abs(it.longitude - item.longitude) < 0.0005
            }
            if (!dup) {
                val annotated = if (hasGps && !item.subtitle.contains("km ·") && !item.subtitle.contains("m ·")) {
                    val distM = distanceMeters(effLat, effLon, item.latitude, item.longitude).roundToInt()
                    val distTag = if (distM >= 1000) "%.1f km".format(distM / 1000f) else "$distM m"
                    item.copy(subtitle = "$distTag · ${item.subtitle}")
                } else {
                    item
                }
                deduped += annotated
            }
        }
        return deduped.take(7)
    }

    private fun containsExplicitCountyOrCity(query: String): Boolean {
        val cityPattern = Regex(
            "(台北|臺北|新北|基隆|桃園|新竹|苗栗|台中|臺中|彰化|南投|雲林|嘉義|台南|臺南|高雄|屏東|宜蘭|花蓮|台東|臺東|澎湖|金門|馬祖|連江|東京|大阪|京都|北海道|沖繩|福岡|名古屋|橫濱)"
        )
        return cityPattern.containsMatchIn(query)
    }

    private fun normalizeCityAlias(text: String): String {
        return text.replace("臺", "台").replace("市", "").replace("縣", "").trim()
    }

    @Suppress("DEPRECATION")
    private fun resolveCurrentLocalCityDistrict(lat: Double, lon: Double, isTaiwan: Boolean): Pair<String, String> {
        val now = System.currentTimeMillis()
        if (cachedLocalCity.isNotBlank() &&
            (now - cachedLocalAreaAtMs) < 300_000L &&
            distanceMeters(lat, lon, cachedLocalAreaLat, cachedLocalAreaLon) < 2500.0
        ) {
            return cachedLocalCity to cachedLocalDistrict
        }
        val ctx = appContext
        if (ctx != null && runCatching { Geocoder.isPresent() }.getOrDefault(false)) {
            val found = runCatching {
                val geocoder = Geocoder(ctx, if (isTaiwan) Locale.TRADITIONAL_CHINESE else Locale.getDefault())
                val addr = geocoder.getFromLocation(lat, lon, 1)?.firstOrNull()
                if (addr != null) {
                    val city = addr.adminArea?.trim().orEmpty()
                        .ifBlank { addr.locality?.trim().orEmpty() }
                        .ifBlank { addr.subAdminArea?.trim().orEmpty() }
                    val district = addr.subLocality?.trim().orEmpty()
                        .ifBlank { addr.subAdminArea?.trim().orEmpty().takeIf { it != city }.orEmpty() }
                        .ifBlank { addr.locality?.trim().orEmpty().takeIf { it != city }.orEmpty() }
                    city to district
                } else null
            }.getOrNull()
            if (found != null && found.first.isNotBlank()) {
                cachedLocalAreaLat = lat
                cachedLocalAreaLon = lon
                cachedLocalCity = found.first
                cachedLocalDistrict = found.second
                cachedLocalAreaAtMs = now
                return found
            }
        }
        return cachedLocalCity to cachedLocalDistrict
    }

    private fun tokenizeCjkAddress(raw: String): String {
        return raw
            .replace(Regex("(縣|市|區|鄉|鎮|村|里|路|街|大道|段)"), "$1 ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun stripDoorNumber(raw: String): String {
        return raw
            .replace(Regex("\\d+之\\d+號.*|\\d+[-－]\\d+號.*|\\d+巷.*|\\d+弄.*|\\d+號.*|\\d+樓.*"), "")
            .trim()
    }

    private fun parseArcGisResults(jsonStr: String, fallbackQuery: String): List<NavSearchResult> {
        if (jsonStr.isBlank()) return emptyList()
        val root = JSONObject(jsonStr)
        val arr = root.optJSONArray("candidates") ?: return emptyList()
        val out = mutableListOf<NavSearchResult>()
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val score = obj.optDouble("score", 0.0)
            if (score < 65.0) continue
            val loc = obj.optJSONObject("location") ?: continue
            val lon = loc.optDouble("x", Double.NaN)
            val lat = loc.optDouble("y", Double.NaN)
            if (lat.isNaN() || lon.isNaN()) continue
            val addr = obj.optString("address").trim().ifBlank { fallbackQuery }
            out += NavSearchResult(
                name = addr.substringBefore(",").trim().ifBlank { fallbackQuery },
                subtitle = addr,
                latitude = lat,
                longitude = lon
            )
        }
        return out
    }

    private fun parseNominatimResults(jsonStr: String): List<NavSearchResult> {
        if (jsonStr.isBlank()) return emptyList()
        val arr = JSONArray(jsonStr)
        val out = mutableListOf<NavSearchResult>()
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val lat = obj.optString("lat").toDoubleOrNull() ?: continue
            val lon = obj.optString("lon").toDoubleOrNull() ?: continue
            val display = obj.optString("display_name").orEmpty()
            val rawName = obj.optString("name").takeIf { it.isNotBlank() }
                ?: display.substringBefore(",").trim().ifBlank { continue }
            val sub = display.removePrefix(rawName).trim(',', ' ').take(60)
            out += NavSearchResult(
                name = rawName,
                subtitle = sub.ifBlank { "%.4f, %.4f".format(lat, lon) },
                latitude = lat,
                longitude = lon
            )
        }
        return out
    }

    @android.annotation.SuppressLint("MissingPermission")
    private fun resolveFreshestStartCoords(fallbackLat: Double, fallbackLon: Double): Pair<Double, Double> {
        // 1. Live LocationTracker real fix takes highest priority
        val live = com.dxcar.launcher.DxCarApp.instance?.locationTracker?.location?.value
        if (live != null && live.hasRealFix && live.latitude != 0.0 && live.longitude != 0.0) {
            return live.latitude to live.longitude
        }
        // 2. Direct Android LocationManager last known fix across GPS / Network / Passive
        val ctx = appContext ?: com.dxcar.launcher.DxCarApp.instance
        if (ctx != null) {
            val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as? android.location.LocationManager
            if (lm != null) {
                var best: Location? = null
                for (p in listOf(
                    android.location.LocationManager.GPS_PROVIDER,
                    android.location.LocationManager.NETWORK_PROVIDER,
                    android.location.LocationManager.PASSIVE_PROVIDER
                )) {
                    val loc = runCatching { lm.getLastKnownLocation(p) }.getOrNull()
                    if (loc != null && (best == null || loc.time > best.time)) {
                        best = loc
                    }
                }
                if (best != null && best.latitude != 0.0 && best.longitude != 0.0) {
                    return best.latitude to best.longitude
                }
            }
        }
        return fallbackLat to fallbackLon
    }

    fun toggleRoutePreferenceAndReroute(preferShortest: Boolean) {
        val cur = _state.value
        _state.value = cur.copy(
            preferShortestDistance = preferShortest,
            avoidEcoDetourFastest = !preferShortest
        )
        if (cur.isNavigating && cur.destinationLat != 0.0 && cur.destinationLon != 0.0) {
            val (lat, lon) = resolveFreshestStartCoords(25.0330, 121.5654)
            startNavigation(lat, lon, cur.destinationName, cur.destinationLat, cur.destinationLon)
        }
    }

    fun startNavigation(
        startLat: Double,
        startLon: Double,
        destName: String,
        destLat: Double,
        destLon: Double
    ) {
        scope.launch {
            val (effStartLat, effStartLon) = resolveFreshestStartCoords(startLat, startLon)
            val preferShortest = _state.value.preferShortestDistance
            _state.value = _state.value.copy(isRouting = true, errorMessage = "")
            val routeResult = withContext(Dispatchers.IO) {
                runCatching {
                    val url = "https://router.project-osrm.org/route/v1/driving/" +
                        "$effStartLon,$effStartLat;$destLon,$destLat" +
                        "?overview=full&geometries=geojson&steps=true&alternatives=true"
                    val req = Request.Builder()
                        .url(url)
                        .header("User-Agent", "DX-Car-Launcher/1.9")
                        .build()
                    httpClient.newCall(req).execute().use { resp ->
                        if (!resp.isSuccessful) error("HTTP ${resp.code}")
                        val body = resp.body?.string().orEmpty()
                        parseOsrmRoute(body, destName, preferShortest = preferShortest)
                    }
                }
            }

            routeResult.onSuccess { parsed ->
                lastAnnouncedStepKey = ""
                val firstStep = parsed.steps.firstOrNull()
                val upcomingStep = parsed.steps.getOrNull(1) ?: firstStep
                val afterStep = parsed.steps.getOrNull(2)
                val totalDist = parsed.totalDistanceMeters.roundToInt().coerceAtLeast(1)
                val totalDur = parsed.totalDurationSeconds.roundToInt().coerceAtLeast(30)
                val initDistToTurn = firstStep?.distanceMeters?.roundToInt() ?: totalDist
                val initExitLabel = buildExitLabel(upcomingStep)
                _state.value = _state.value.copy(
                    isRouting = false,
                    isNavigating = true,
                    errorMessage = "",
                    destinationName = destName,
                    destinationLat = destLat,
                    destinationLon = destLon,
                    routePoints = parsed.points,
                    steps = parsed.steps,
                    currentStepIndex = 0,
                    currentInstructionZh = upcomingStep?.instructionZh ?: "前往 $destName",
                    nextStepInstructionZh = afterStep?.instructionZh.orEmpty(),
                    upcomingLanes = upcomingStep?.lanes.orEmpty(),
                    upcomingExitLabel = initExitLabel,
                    isHighwayEarlyAlert = upcomingStep?.isHighwayManeuver == true && initDistToTurn <= 2200,
                    distanceToNextTurnMeters = initDistToTurn,
                    remainingDistanceMeters = totalDist,
                    remainingDurationSeconds = totalDur
                )
                val minText = (totalDur / 60).coerceAtLeast(1)
                val modeTag = if (preferShortest) "最短距離" else "最快路線"
                ttsAnnouncer?.speak("已為您規劃$modeTag 前往 $destName，全程約 ${formatDistanceSpeech(totalDist)}，預計 $minText 分鐘")
            }.onFailure { err ->
                _state.value = _state.value.copy(
                    isRouting = false,
                    errorMessage = "路線規劃失敗：${err.message ?: "網路不穩"}"
                )
            }
        }
    }

    fun stopNavigation() {
        lastAnnouncedStepKey = ""
        _state.value = BuiltInNavState(
            avoidEcoDetourFastest = _state.value.avoidEcoDetourFastest,
            preferShortestDistance = _state.value.preferShortestDistance
        )
    }

    fun clearSearchError() {
        _state.value = _state.value.copy(errorMessage = "")
    }

    fun onLocationUpdated(lat: Double, lon: Double) {
        val current = _state.value
        if (!current.isNavigating || current.steps.isEmpty()) return

        val destDist = distanceMeters(lat, lon, current.destinationLat, current.destinationLon)
        if (destDist < 38.0) {
            ttsAnnouncer?.speak("已抵達目的地，${current.destinationName}")
            stopNavigation()
            return
        }

        // Advance step index robustly (even if vehicle passed an intermediate turn slightly fast)
        var idx = current.currentStepIndex.coerceIn(0, current.steps.lastIndex)
        while (idx < current.steps.lastIndex) {
            val nextManeuver = current.steps[idx + 1]
            val dNext = distanceMeters(lat, lon, nextManeuver.maneuverLat, nextManeuver.maneuverLon)
            if (dNext < 35.0) {
                idx++
            } else if (idx + 2 <= current.steps.lastIndex) {
                val afterManeuver = current.steps[idx + 2]
                val dAfter = distanceMeters(lat, lon, afterManeuver.maneuverLat, afterManeuver.maneuverLon)
                if (dAfter < dNext && dAfter < 90.0) {
                    idx++
                } else {
                    break
                }
            } else {
                break
            }
        }

        val currentLegStep = current.steps[idx]
        val targetStep = if (idx + 1 <= current.steps.lastIndex) current.steps[idx + 1] else current.steps[idx]
        val afterTargetStep = if (idx + 2 <= current.steps.lastIndex) current.steps[idx + 2] else null
        val distToTurn = distanceMeters(lat, lon, targetStep.maneuverLat, targetStep.maneuverLon).roundToInt()

        // Deterministic distance & ETA computation directly from step durations (ZERO compounding drift!)
        val segTotalDist = currentLegStep.distanceMeters.coerceAtLeast(1.0)
        val segTotalDur = currentLegStep.durationSeconds.coerceAtLeast(1.0)
        val clampedCurDist = distToTurn.toDouble().coerceAtMost(segTotalDist * 1.15)
        val curSegRemainDur = (clampedCurDist / segTotalDist).coerceIn(0.0, 1.2) * segTotalDur

        var remainDistD = clampedCurDist
        var remainDurD = curSegRemainDur
        for (i in (idx + 1)..current.steps.lastIndex) {
            remainDistD += current.steps[i].distanceMeters
            remainDurD += current.steps[i].durationSeconds
        }
        val remainDist = remainDistD.roundToInt().coerceAtLeast(distToTurn)
        val remainSec = remainDurD.roundToInt().coerceAtLeast(20)

        val isHighwayTarget = targetStep.isHighwayManeuver || currentLegStep.isHighwayManeuver
        val showHighwayEarlyAlert = isHighwayTarget && distToTurn <= 2200

        // Early highway interchange / exit voice prompts (2km, 1km, 500m, 150m) + city prompts (300m, 60m)
        val voiceBand = if (isHighwayTarget) {
            when {
                distToTurn in 1800..2150 -> "2000"
                distToTurn in 880..1120 -> "1000"
                distToTurn in 420..580 -> "500"
                distToTurn in 80..180 -> "150"
                else -> ""
            }
        } else {
            when {
                distToTurn in 230..330 -> "300"
                distToTurn in 20..75 -> "60"
                else -> ""
            }
        }
        if (voiceBand.isNotEmpty()) {
            val key = "${idx}_$voiceBand"
            if (key != lastAnnouncedStepKey) {
                lastAnnouncedStepKey = key
                val spokenDist = when (voiceBand) {
                    "2000" -> "前方兩公里請留意車道"
                    "1000" -> "前方一公里請提前變換車道"
                    "500" -> "前方五百公尺準備"
                    "300" -> "前方三百公尺"
                    "150" -> "前方一百五十公尺"
                    else -> "前方五十公尺"
                }
                val laneHint = buildSpokenLaneHint(targetStep)
                ttsAnnouncer?.speak("$spokenDist，${targetStep.instructionZh}$laneHint")
            }
        }

        // Off-route check against nearest route point (> 110m off-route -> reroute from current GPS, throttled 15s)
        val minRouteDist = current.routePoints.minOfOrNull { pt ->
            distanceMeters(lat, lon, pt.latitude, pt.longitude)
        } ?: 0.0
        val now = System.currentTimeMillis()
        if (minRouteDist > 110.0 && now - lastRerouteTimeMs > 15_000L && !current.isRouting) {
            lastRerouteTimeMs = now
            ttsAnnouncer?.speak("已偏離路線，正在依目前位置重新規劃最短路線")
            startNavigation(lat, lon, current.destinationName, current.destinationLat, current.destinationLon)
            return
        }

        val showLanesNow = if (isHighwayTarget) distToTurn <= 2200 else distToTurn <= 650
        val effectiveLanes = if (showLanesNow) {
            targetStep.lanes.ifEmpty { synthesizeFallbackLanes(targetStep.maneuverType, targetStep.modifier) }
        } else {
            emptyList()
        }

        _state.value = current.copy(
            currentStepIndex = idx,
            currentInstructionZh = targetStep.instructionZh,
            nextStepInstructionZh = afterTargetStep?.instructionZh.orEmpty(),
            upcomingLanes = effectiveLanes,
            upcomingExitLabel = buildExitLabel(targetStep),
            isHighwayEarlyAlert = showHighwayEarlyAlert,
            distanceToNextTurnMeters = distToTurn,
            remainingDistanceMeters = remainDist,
            remainingDurationSeconds = remainSec
        )
    }

    private fun buildExitLabel(step: NavRouteStep?): String {
        if (step == null) return ""
        val parts = mutableListOf<String>()
        if (step.exits.isNotBlank()) parts += "出口 ${step.exits}"
        if (step.destinations.isNotBlank()) parts += step.destinations
        return parts.joinToString(" · ").take(36)
    }

    private fun buildSpokenLaneHint(step: NavRouteStep): String {
        val valid = step.lanes.filter { it.valid }
        if (valid.isEmpty()) return ""
        val dirs = valid.flatMap { it.indications }.distinct()
        return when {
            dirs.any { it.contains("right") } && step.isHighwayManeuver -> "，請靠右側車道行駛"
            dirs.any { it.contains("left") } && step.isHighwayManeuver -> "，請靠左側車道行駛"
            else -> ""
        }
    }

    private fun synthesizeFallbackLanes(maneuverType: String, modifier: String): List<NavLaneGuide> {
        val m = modifier.lowercase()
        val isExitOrTurn = maneuverType in setOf("off ramp", "fork", "merge", "on ramp", "turn", "end of road")
        if (!isExitOrTurn) return emptyList()
        return when {
            m.contains("right") -> listOf(
                NavLaneGuide(listOf("straight"), valid = false),
                NavLaneGuide(listOf("straight"), valid = false),
                NavLaneGuide(listOf(if (m.contains("slight")) "slight right" else "right"), valid = true)
            )
            m.contains("left") -> listOf(
                NavLaneGuide(listOf(if (m.contains("slight")) "slight left" else "left"), valid = true),
                NavLaneGuide(listOf("straight"), valid = false),
                NavLaneGuide(listOf("straight"), valid = false)
            )
            m == "uturn" -> listOf(
                NavLaneGuide(listOf("uturn"), valid = true),
                NavLaneGuide(listOf("straight"), valid = false)
            )
            else -> emptyList()
        }
    }

    private fun parsePhotonResults(jsonStr: String): List<NavSearchResult> {
        val root = JSONObject(jsonStr)
        val features = root.optJSONArray("features") ?: return emptyList()
        val results = mutableListOf<NavSearchResult>()
        for (i in 0 until features.length()) {
            val feat = features.optJSONObject(i) ?: continue
            val geom = feat.optJSONObject("geometry") ?: continue
            val coords = geom.optJSONArray("coordinates") ?: continue
            if (coords.length() < 2) continue
            val lon = coords.optDouble(0)
            val lat = coords.optDouble(1)
            val props = feat.optJSONObject("properties") ?: continue
            val name = props.optString("name").takeIf { it.isNotBlank() }
                ?: props.optString("street").takeIf { it.isNotBlank() }
                ?: continue
            val parts = listOfNotNull(
                props.optString("city").takeIf { it.isNotBlank() },
                props.optString("district").takeIf { it.isNotBlank() },
                props.optString("street").takeIf { it.isNotBlank() && it != name }
            )
            results += NavSearchResult(
                name = name,
                subtitle = parts.joinToString(" · ").ifBlank {
                    "%.4f, %.4f".format(lat, lon)
                },
                latitude = lat,
                longitude = lon
            )
        }
        return results
    }

    private data class ParsedOsrmRoute(
        val points: List<NavRoutePoint>,
        val steps: List<NavRouteStep>,
        val totalDistanceMeters: Double,
        val totalDurationSeconds: Double
    )

    private fun parseOsrmRoute(jsonStr: String, destName: String, preferShortest: Boolean = false): ParsedOsrmRoute {
        val root = JSONObject(jsonStr)
        val routes = root.optJSONArray("routes") ?: error("無可用路線")
        if (routes.length() == 0) error("無可用路線")

        // Compare all candidate routes from alternatives=true and select strictly fastest (avoid eco-detour) or shortest distance!
        var bestRoute = routes.getJSONObject(0)
        for (rIdx in 1 until routes.length()) {
            val cand = routes.getJSONObject(rIdx)
            val bestDist = bestRoute.optDouble("distance", Double.MAX_VALUE)
            val bestDur = bestRoute.optDouble("duration", Double.MAX_VALUE)
            val candDist = cand.optDouble("distance", Double.MAX_VALUE)
            val candDur = cand.optDouble("duration", Double.MAX_VALUE)
            val isBetter = if (preferShortest) {
                candDist < bestDist
            } else {
                candDur < bestDur * 0.98 || (candDur <= bestDur * 1.03 && candDist < bestDist)
            }
            if (isBetter) {
                bestRoute = cand
            }
        }

        val route = bestRoute
        val totalDist = route.optDouble("distance", 0.0)
        val totalDur = route.optDouble("duration", 0.0)

        val geom = route.getJSONObject("geometry")
        val coords = geom.getJSONArray("coordinates")
        val points = ArrayList<NavRoutePoint>(coords.length())
        for (i in 0 until coords.length()) {
            val pair = coords.getJSONArray(i)
            points += NavRoutePoint(latitude = pair.getDouble(1), longitude = pair.getDouble(0))
        }

        val steps = mutableListOf<NavRouteStep>()
        val legs = route.optJSONArray("legs")
        if (legs != null && legs.length() > 0) {
            val legSteps = legs.getJSONObject(0).optJSONArray("steps")
            if (legSteps != null) {
                for (i in 0 until legSteps.length()) {
                    val st = legSteps.getJSONObject(i)
                    val road = st.optString("name").orEmpty()
                    val ref = st.optString("ref").orEmpty()
                    val destinations = st.optString("destinations").orEmpty()
                    val exits = st.optString("exits").orEmpty()
                    val dist = st.optDouble("distance", 0.0)
                    val dur = st.optDouble("duration", 0.0)
                    val maneuver = st.optJSONObject("maneuver") ?: continue
                    val loc = maneuver.optJSONArray("location") ?: continue
                    val mLon = loc.optDouble(0)
                    val mLat = loc.optDouble(1)
                    val mType = maneuver.optString("type")
                    val modifier = maneuver.optString("modifier")

                    // Parse real OSRM lane guidance from intersections
                    val parsedLanes = mutableListOf<NavLaneGuide>()
                    val intersections = st.optJSONArray("intersections")
                    if (intersections != null) {
                        for (intIdx in 0 until intersections.length()) {
                            val inter = intersections.optJSONObject(intIdx) ?: continue
                            val lanesArr = inter.optJSONArray("lanes") ?: continue
                            if (lanesArr.length() > 0) {
                                parsedLanes.clear()
                                for (lIdx in 0 until lanesArr.length()) {
                                    val laneObj = lanesArr.optJSONObject(lIdx) ?: continue
                                    val valid = laneObj.optBoolean("valid", false)
                                    val indArr = laneObj.optJSONArray("indications")
                                    val inds = mutableListOf<String>()
                                    if (indArr != null) {
                                        for (k in 0 until indArr.length()) {
                                            indArr.optString(k)?.takeIf { it.isNotBlank() }?.let { inds += it }
                                        }
                                    }
                                    if (inds.isEmpty()) inds += "straight"
                                    parsedLanes += NavLaneGuide(inds, valid)
                                }
                                break
                            }
                        }
                    }

                    val roadWithRef = listOf(ref, road).filter { it.isNotBlank() }.distinct().joinToString(" ")
                    val highwayRegex = Regex("(國道|高速|快速|台61|台64|台66|台74|台86|台88|交流道|系統|高架|Expressway|Highway|Freeway|Motorway)", RegexOption.IGNORE_CASE)
                    val isHighway = mType in setOf("off ramp", "on ramp", "fork", "merge") ||
                        exits.isNotBlank() ||
                        destinations.isNotBlank() ||
                        highwayRegex.containsMatchIn(roadWithRef)

                    val finalLanes = if (parsedLanes.isNotEmpty()) parsedLanes else synthesizeFallbackLanes(mType, modifier)

                    steps += NavRouteStep(
                        instructionZh = translateManeuverZh(mType, modifier, roadWithRef.ifBlank { road }, destName, destinations, exits),
                        roadName = roadWithRef.ifBlank { road },
                        maneuverLat = mLat,
                        maneuverLon = mLon,
                        distanceMeters = dist,
                        durationSeconds = dur,
                        maneuverType = mType,
                        modifier = modifier,
                        lanes = finalLanes,
                        destinations = destinations,
                        exits = exits,
                        isHighwayManeuver = isHighway
                    )
                }
            }
        }
        return ParsedOsrmRoute(points, steps, totalDist, totalDur)
    }

    private fun translateManeuverZh(
        type: String,
        modifier: String,
        road: String,
        destName: String,
        destinations: String = "",
        exits: String = ""
    ): String {
        val targetTag = destinations.ifBlank { road }
        val exitPrefix = if (exits.isNotBlank()) "由 $exits 號出口" else ""
        val roadSuffix = if (targetTag.isNotBlank()) "進入 $targetTag" else ""
        val dir = when (modifier) {
            "left" -> "左轉"
            "sharp left" -> "左急轉"
            "slight left" -> "靠左微轉"
            "right" -> "右轉"
            "sharp right" -> "右急轉"
            "slight right" -> "靠右微轉"
            "uturn" -> "迴轉"
            else -> "直行"
        }
        return when (type) {
            "depart" -> if (road.isNotBlank()) "沿 $road 出發" else "沿道路出發"
            "arrive" -> "抵達目的地 $destName"
            "roundabout", "rotary" -> "進入圓環後駛出$roadSuffix"
            "merge" -> "匯入車道$roadSuffix"
            "off ramp" -> "${exitPrefix}靠${if (modifier.contains("left")) "左" else "右"}下交流道$roadSuffix"
            "on ramp" -> "$dir 上匝道$roadSuffix"
            "fork" -> "${if (modifier.contains("left")) "靠左" else "靠右"}行駛$roadSuffix"
            else -> "$dir$roadSuffix"
        }
    }

    private fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val res = FloatArray(1)
        Location.distanceBetween(lat1, lon1, lat2, lon2, res)
        return res[0].toDouble()
    }

    private fun formatDistanceSpeech(meters: Int): String {
        return if (meters >= 1000) {
            "%.1f 公里".format(meters / 1000f)
        } else {
            "$meters 公尺"
        }
    }
}

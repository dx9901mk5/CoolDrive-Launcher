package com.dxcar.launcher.map

import android.location.Location
import com.dxcar.launcher.comms.TtsAnnouncer
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
import org.json.JSONObject
import java.net.URLEncoder
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

data class NavRouteStep(
    val instructionZh: String,
    val roadName: String,
    val maneuverLat: Double,
    val maneuverLon: Double,
    val distanceMeters: Double,
    val maneuverType: String,
    val modifier: String
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
    val distanceToNextTurnMeters: Int = 0,
    val remainingDistanceMeters: Int = 0,
    val remainingDurationSeconds: Int = 0
)

/**
 * Plan B Zero-API-Key Built-In Navigation Engine:
 * - Place search powered by Komoot Photon (OSM, zh-TW bias near current car GPS)
 * - Turn-by-turn routing powered by public OSRM driving server
 * - Live GPS step tracking + Chinese TTS voice prompts via [TtsAnnouncer]
 */
class BuiltInNavEngine(private val ttsAnnouncer: TtsAnnouncer?) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .build()

    private val _state = MutableStateFlow(BuiltInNavState())
    val state: StateFlow<BuiltInNavState> = _state.asStateFlow()

    private var lastAnnouncedStepKey = ""
    private var lastRerouteTimeMs = 0L

    fun searchPlaces(query: String, biasLat: Double, biasLon: Double) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) {
            _state.value = _state.value.copy(searchResults = emptyList(), errorMessage = "")
            return
        }
        scope.launch {
            _state.value = _state.value.copy(isSearching = true, errorMessage = "")
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val encoded = URLEncoder.encode(trimmed, "UTF-8")
                    val url = "https://photon.komoot.io/api/?q=$encoded&lat=$biasLat&lon=$biasLon&limit=7"
                    val req = Request.Builder()
                        .url(url)
                        .header("User-Agent", "DX-Car-Launcher/1.4")
                        .build()
                    httpClient.newCall(req).execute().use { resp ->
                        if (!resp.isSuccessful) error("HTTP ${resp.code}")
                        val body = resp.body?.string().orEmpty()
                        parsePhotonResults(body)
                    }
                }
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

    fun startNavigation(
        startLat: Double,
        startLon: Double,
        destName: String,
        destLat: Double,
        destLon: Double
    ) {
        scope.launch {
            _state.value = _state.value.copy(isRouting = true, errorMessage = "")
            val routeResult = withContext(Dispatchers.IO) {
                runCatching {
                    val url = "https://router.project-osrm.org/route/v1/driving/" +
                        "$startLon,$startLat;$destLon,$destLat" +
                        "?overview=full&geometries=geojson&steps=true"
                    val req = Request.Builder()
                        .url(url)
                        .header("User-Agent", "DX-Car-Launcher/1.4")
                        .build()
                    httpClient.newCall(req).execute().use { resp ->
                        if (!resp.isSuccessful) error("HTTP ${resp.code}")
                        val body = resp.body?.string().orEmpty()
                        parseOsrmRoute(body, destName)
                    }
                }
            }

            routeResult.onSuccess { parsed ->
                lastAnnouncedStepKey = ""
                val firstStep = parsed.steps.firstOrNull()
                val totalDist = parsed.totalDistanceMeters.roundToInt()
                val totalDur = parsed.totalDurationSeconds.roundToInt()
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
                    currentInstructionZh = firstStep?.instructionZh ?: "前往 $destName",
                    distanceToNextTurnMeters = firstStep?.distanceMeters?.roundToInt() ?: totalDist,
                    remainingDistanceMeters = totalDist,
                    remainingDurationSeconds = totalDur
                )
                val minText = (totalDur / 60).coerceAtLeast(1)
                ttsAnnouncer?.speak("開始導航前往 $destName，全程約 ${formatDistanceSpeech(totalDist)}，預計 $minText 分鐘")
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
        _state.value = BuiltInNavState()
    }

    fun clearSearchError() {
        _state.value = _state.value.copy(errorMessage = "")
    }

    fun onLocationUpdated(lat: Double, lon: Double) {
        val current = _state.value
        if (!current.isNavigating || current.steps.isEmpty()) return

        val destDist = distanceMeters(lat, lon, current.destinationLat, current.destinationLon)
        if (destDist < 35.0) {
            ttsAnnouncer?.speak("已抵達目的地，${current.destinationName}")
            stopNavigation()
            return
        }

        // Advance step index when vehicle approaches the upcoming maneuver point
        var idx = current.currentStepIndex.coerceIn(0, current.steps.lastIndex)
        while (idx < current.steps.lastIndex) {
            val nextManeuver = current.steps[idx + 1]
            val dNext = distanceMeters(lat, lon, nextManeuver.maneuverLat, nextManeuver.maneuverLon)
            if (dNext < 28.0) {
                idx++
            } else {
                break
            }
        }

        val targetStep = if (idx + 1 <= current.steps.lastIndex) current.steps[idx + 1] else current.steps[idx]
        val distToTurn = distanceMeters(lat, lon, targetStep.maneuverLat, targetStep.maneuverLon).roundToInt()

        var remainDist = distToTurn
        for (i in (idx + 1)..current.steps.lastIndex) {
            remainDist += current.steps[i].distanceMeters.roundToInt()
        }
        val ratio = if (current.remainingDistanceMeters > 0) {
            (remainDist.toFloat() / current.remainingDistanceMeters.toFloat()).coerceIn(0.1f, 1.5f)
        } else 1f
        val remainSec = (current.remainingDurationSeconds * ratio).roundToInt().coerceAtLeast(30)

        // Voice announcements at 250m and 50m before next maneuver
        val voiceBand = when {
            distToTurn in 180..270 -> "250"
            distToTurn in 15..65 -> "50"
            else -> ""
        }
        if (voiceBand.isNotEmpty()) {
            val key = "${idx}_$voiceBand"
            if (key != lastAnnouncedStepKey) {
                lastAnnouncedStepKey = key
                val spokenDist = if (voiceBand == "250") "前方兩百公尺" else "前方五十公尺"
                ttsAnnouncer?.speak("$spokenDist，${targetStep.instructionZh}")
            }
        }

        // Off-route check against nearest route point (> 110m off-route -> reroute, throttled to 18s)
        val minRouteDist = current.routePoints.minOfOrNull { pt ->
            distanceMeters(lat, lon, pt.latitude, pt.longitude)
        } ?: 0.0
        val now = System.currentTimeMillis()
        if (minRouteDist > 110.0 && now - lastRerouteTimeMs > 18_000L && !current.isRouting) {
            lastRerouteTimeMs = now
            ttsAnnouncer?.speak("已偏離路線，正在重新規劃")
            startNavigation(lat, lon, current.destinationName, current.destinationLat, current.destinationLon)
            return
        }

        _state.value = current.copy(
            currentStepIndex = idx,
            currentInstructionZh = targetStep.instructionZh,
            distanceToNextTurnMeters = distToTurn,
            remainingDistanceMeters = remainDist,
            remainingDurationSeconds = remainSec
        )
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

    private fun parseOsrmRoute(jsonStr: String, destName: String): ParsedOsrmRoute {
        val root = JSONObject(jsonStr)
        val routes = root.optJSONArray("routes") ?: error("無可用路線")
        if (routes.length() == 0) error("無可用路線")
        val route = routes.getJSONObject(0)
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
                    val dist = st.optDouble("distance", 0.0)
                    val maneuver = st.optJSONObject("maneuver") ?: continue
                    val loc = maneuver.optJSONArray("location") ?: continue
                    val mLon = loc.optDouble(0)
                    val mLat = loc.optDouble(1)
                    val mType = maneuver.optString("type")
                    val modifier = maneuver.optString("modifier")
                    steps += NavRouteStep(
                        instructionZh = translateManeuverZh(mType, modifier, road, destName),
                        roadName = road,
                        maneuverLat = mLat,
                        maneuverLon = mLon,
                        distanceMeters = dist,
                        maneuverType = mType,
                        modifier = modifier
                    )
                }
            }
        }
        return ParsedOsrmRoute(points, steps, totalDist, totalDur)
    }

    private fun translateManeuverZh(type: String, modifier: String, road: String, destName: String): String {
        val roadSuffix = if (road.isNotBlank()) "進入 $road" else ""
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
            "on ramp", "off ramp" -> "$dir 上/下匝道$roadSuffix"
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

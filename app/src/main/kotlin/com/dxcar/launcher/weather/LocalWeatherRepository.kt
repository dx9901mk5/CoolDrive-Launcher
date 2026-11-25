package com.dxcar.launcher.weather

import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.location.Geocoder
import android.net.Uri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.roundToInt

data class LocalWeatherState(
    val hasData: Boolean = false,
    val temperatureText: String = "--°C",
    val conditionText: String = "讀取天氣中…",
    val locationName: String = "當地天氣",
    val detailText: String = "",
    val weatherCode: Int = 1,
    val fromGoogleApp: Boolean = false,
    val updatedAtMs: Long = 0L
)

/**
 * Unified Car Weather Provider integrated into the top strip of the Media Controller Widget:
 * 1. Primary source: Reads real-time weather notifications from Google App
 *    (`com.google.android.googlequicksearchbox` / Pixel Weather / OEM Weather apps).
 * 2. Automatic fallback: Queries live GPS weather + Taiwan district name (Geocoder / BigDataCloud + Open-Meteo)
 *    when Google App hasn't posted a notification yet, caching results offline.
 */
class LocalWeatherRepository(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val prefs = context.getSharedPreferences("dxcar_weather_cache", Context.MODE_PRIVATE)
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(7, TimeUnit.SECONDS)
        .readTimeout(7, TimeUnit.SECONDS)
        .build()

    private val _weather = MutableStateFlow(loadCached())
    val weather: StateFlow<LocalWeatherState> = _weather.asStateFlow()

    private var lastFetchLat = 0.0
    private var lastFetchLon = 0.0
    private var lastNetworkFetchMs = 0L

    fun start(initialLat: Double, initialLon: Double) {
        lastFetchLat = initialLat
        lastFetchLon = initialLon
        scope.launch {
            while (isActive) {
                val cur = _weather.value
                val now = System.currentTimeMillis()
                val googleFresh = cur.fromGoogleApp && (now - cur.updatedAtMs) < 45 * 60_000L
                if (!googleFresh && (now - lastNetworkFetchMs >= 15 * 60_000L || !cur.hasData)) {
                    fetchFallbackWeatherSync(lastFetchLat, lastFetchLon)
                }
                delay(60_000L)
            }
        }
    }

    fun onLocationUpdated(lat: Double, lon: Double) {
        if (lat == 0.0 && lon == 0.0) return
        val movedFar = abs(lat - lastFetchLat) > 0.04 || abs(lon - lastFetchLon) > 0.04
        lastFetchLat = lat
        lastFetchLon = lon
        val cur = _weather.value
        val googleFresh = cur.fromGoogleApp && (System.currentTimeMillis() - cur.updatedAtMs) < 45 * 60_000L
        if (!googleFresh && (movedFar || !cur.hasData)) {
            scope.launch { fetchFallbackWeatherSync(lat, lon) }
        }
    }

    fun forceRefresh() {
        scope.launch {
            fetchFallbackWeatherSync(lastFetchLat, lastFetchLon)
        }
    }

    /**
     * Parses live weather notifications from Google App (`com.google.android.googlequicksearchbox`)
     * or system weather providers. Returns true if recognized as a valid weather notification.
     */
    fun tryIngestNotification(pkg: String, title: String, text: String, subText: String): Boolean {
        val lowerPkg = pkg.lowercase()
        val isWeatherPkg = WEATHER_PACKAGES.any { lowerPkg == it || lowerPkg.contains("weather") }
        if (!isWeatherPkg) return false

        val combined = "$title · $text · $subText"
        val hasWeatherHint = WEATHER_KEYWORDS.any { combined.lowercase().contains(it) }
        val tempMatch = TEMP_REGEX.find(combined) ?: return false
        if (!hasWeatherHint && lowerPkg == GOOGLE_APP_PACKAGE) return false

        val tempVal = tempMatch.groupValues[1].toIntOrNull() ?: return false
        if (tempVal !in -35..55) return false

        val condition = extractConditionZh(combined)
        val locCandidate = extractLocationFromGoogleText(title, text, subText)
            .ifBlank { _weather.value.locationName.takeIf { it != "當地天氣" }.orEmpty() }
            .ifBlank { "目前位置" }

        val code = conditionToCode(condition)
        val isGoogle = lowerPkg == GOOGLE_APP_PACKAGE || lowerPkg == GOOGLE_WEATHER_PACKAGE
        val next = LocalWeatherState(
            hasData = true,
            temperatureText = "${tempVal}°C",
            conditionText = condition,
            locationName = locCandidate,
            detailText = if (isGoogle) "Google 天氣即時資訊" else "系統天氣同步",
            weatherCode = code,
            fromGoogleApp = isGoogle,
            updatedAtMs = System.currentTimeMillis()
        )
        _weather.value = next
        saveCache(next)
        return true
    }

    fun openGoogleWeatherApp(ctx: Context) {
        val candidates = listOf(
            // 1. Official Google App native Froggy Weather deep link -> launches WeatherActivity directly!
            Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/gaweather")).apply {
                addCategory(Intent.CATEGORY_DEFAULT)
                addCategory(Intent.CATEGORY_BROWSABLE)
                setPackage(GOOGLE_APP_PACKAGE)
            },
            // 2. Explicit DynamicActivityTrampoline with /gaweather URI for OEM / AI Box ROMs
            Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/gaweather")).apply {
                setClassName(
                    GOOGLE_APP_PACKAGE,
                    "com.google.android.apps.gsa.velour.DynamicActivityTrampoline"
                )
            },
            // 3. Pixel / Standalone Google Weather app (if installed)
            ctx.packageManager.getLaunchIntentForPackage(GOOGLE_WEATHER_PACKAGE),
            // 4. Google App Search Weather card fallback
            Intent(Intent.ACTION_WEB_SEARCH).apply {
                setPackage(GOOGLE_APP_PACKAGE)
                putExtra(SearchManager.QUERY, "weather")
            },
            // 5. Web fallback if Google App is not installed
            Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=weather"))
        )
        val launchBundle = runCatching {
            android.app.ActivityOptions.makeBasic()
                .setLaunchDisplayId(android.view.Display.DEFAULT_DISPLAY)
                .toBundle()
        }.getOrNull()

        for (intent in candidates) {
            if (intent == null) continue
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val resolved = ctx.packageManager.resolveActivity(intent, 0) != null
            if (!resolved && intent.`package` != null) continue
            val ok = runCatching {
                if (launchBundle != null) {
                    ctx.startActivity(intent, launchBundle)
                } else {
                    ctx.startActivity(intent)
                }
                true
            }.getOrDefault(false)
            if (ok) break
        }
        forceRefresh()
    }

    private fun fetchFallbackWeatherSync(lat: Double, lon: Double) {
        if (lat == 0.0 && lon == 0.0) return
        runCatching {
            val url = "https://api.open-meteo.com/v1/forecast" +
                "?latitude=$lat&longitude=$lon" +
                "&current=temperature_2m,weather_code,apparent_temperature" +
                "&daily=temperature_2m_max,temperature_2m_min,precipitation_probability_max" +
                "&forecast_days=1&timezone=auto"
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", "DX-Car-Launcher/1.5")
                .build()
            val body = httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return
                resp.body?.string().orEmpty()
            }
            if (body.isBlank()) return

            val root = JSONObject(body)
            val current = root.optJSONObject("current") ?: return
            val temp = current.optDouble("temperature_2m", Double.NaN)
            if (temp.isNaN()) return
            val code = current.optInt("weather_code", 1)

            var detail = ""
            root.optJSONObject("daily")?.let { daily ->
                val hi = daily.optJSONArray("temperature_2m_max")?.optDouble(0, Double.NaN) ?: Double.NaN
                val lo = daily.optJSONArray("temperature_2m_min")?.optDouble(0, Double.NaN) ?: Double.NaN
                val rainProb = daily.optJSONArray("precipitation_probability_max")?.optInt(0, -1) ?: -1
                val parts = mutableListOf<String>()
                if (!hi.isNaN() && !lo.isNaN()) {
                    parts += "${hi.roundToInt()}°/${lo.roundToInt()}°"
                }
                if (rainProb >= 0) {
                    parts += "降雨 $rainProb%"
                }
                detail = parts.joinToString(" · ")
            }

            val placeName = resolveDistrictName(lat, lon)
            lastNetworkFetchMs = System.currentTimeMillis()
            val next = LocalWeatherState(
                hasData = true,
                temperatureText = "${temp.roundToInt()}°C",
                conditionText = wmoCodeToZh(code),
                locationName = placeName,
                detailText = detail,
                weatherCode = code,
                fromGoogleApp = false,
                updatedAtMs = lastNetworkFetchMs
            )
            _weather.value = next
            saveCache(next)
        }
    }

    @Suppress("DEPRECATION")
    private fun resolveDistrictName(lat: Double, lon: Double): String {
        runCatching {
            if (Geocoder.isPresent()) {
                val list = Geocoder(context, Locale.TAIWAN).getFromLocation(lat, lon, 1)
                val addr = list?.firstOrNull()
                if (addr != null) {
                    val district = addr.subLocality ?: addr.locality ?: addr.subAdminArea ?: addr.adminArea
                    if (!district.isNullOrBlank()) return district
                }
            }
        }
        return runCatching {
            val geoUrl = "https://api.bigdatacloud.net/data/reverse-geocode-client" +
                "?latitude=$lat&longitude=$lon&localityLanguage=zh-TW"
            val req = Request.Builder().url(geoUrl).build()
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use ""
                val obj = JSONObject(resp.body?.string().orEmpty())
                obj.optString("locality")
                    .ifBlank { obj.optString("city") }
                    .ifBlank { obj.optString("principalSubdivision") }
            }
        }.getOrDefault("").ifBlank { "當地天氣" }
    }

    private fun extractConditionZh(text: String): String {
        val candidates = listOf(
            "雷陣雨", "大雷雨", "豪雨", "大雨", "陣雨", "毛毛雨", "小雨", "降雨", "下雨",
            "晴時多雲", "多雲時晴", "多雲時陰", "陰時多雲", "局部多雲", "多雲", "晴朗", "晴天", "晴", "陰天", "陰", "濃霧", "起霧"
        )
        candidates.firstOrNull { text.contains(it) }?.let { return it }
        val lower = text.lowercase()
        return when {
            lower.contains("thunder") || lower.contains("storm") -> "雷陣雨"
            lower.contains("rain") || lower.contains("shower") || lower.contains("drizzle") -> "陣雨"
            lower.contains("partly") -> "晴時多雲"
            lower.contains("cloud") || lower.contains("overcast") -> "多雲"
            lower.contains("clear") || lower.contains("sunny") -> "晴朗"
            else -> "即時天氣"
        }
    }

    private fun extractLocationFromGoogleText(title: String, text: String, subText: String): String {
        val twPlaceRegex = Regex("""([\u4e00-\u9fa5]{2,5}(?:區|市|鎮|鄉|縣))""")
        twPlaceRegex.find("$title $subText $text")?.groupValues?.getOrNull(1)?.let { return it }
        val cleaned = title
            .replace(TEMP_REGEX, "")
            .replace(Regex("""[·•|\-–:]"""), " ")
            .trim()
            .split(Regex("\\s+"))
            .firstOrNull { token ->
                token.length in 2..8 && WEATHER_KEYWORDS.none { token.lowercase().contains(it) }
            }
        return cleaned.orEmpty()
    }

    private fun conditionToCode(cond: String): Int = when {
        cond.contains("雷") -> 95
        cond.contains("雨") -> 61
        cond.contains("霧") -> 45
        cond.contains("晴時多雲") || cond.contains("多雲時晴") -> 2
        cond.contains("陰") -> 3
        cond.contains("雲") -> 2
        else -> 0
    }

    private fun wmoCodeToZh(code: Int): String = when (code) {
        0 -> "晴朗"
        1 -> "大致晴朗"
        2 -> "晴時多雲"
        3 -> "陰天"
        45, 48 -> "有霧"
        51, 53, 55, 56, 57 -> "毛毛雨"
        61, 63, 80, 81 -> "陣雨"
        65, 82 -> "大雨"
        71, 73, 75, 77 -> "降雪"
        95, 96, 99 -> "雷陣雨"
        else -> "多雲"
    }

    private fun loadCached(): LocalWeatherState {
        val has = prefs.getBoolean("has", false)
        if (!has) return LocalWeatherState()
        return LocalWeatherState(
            hasData = true,
            temperatureText = prefs.getString("temp", "--°C") ?: "--°C",
            conditionText = prefs.getString("cond", "多雲") ?: "多雲",
            locationName = prefs.getString("loc", "當地天氣") ?: "當地天氣",
            detailText = prefs.getString("detail", "") ?: "",
            weatherCode = prefs.getInt("code", 1),
            fromGoogleApp = prefs.getBoolean("google", false),
            updatedAtMs = prefs.getLong("ts", 0L)
        )
    }

    private fun saveCache(s: LocalWeatherState) {
        prefs.edit()
            .putBoolean("has", s.hasData)
            .putString("temp", s.temperatureText)
            .putString("cond", s.conditionText)
            .putString("loc", s.locationName)
            .putString("detail", s.detailText)
            .putInt("code", s.weatherCode)
            .putBoolean("google", s.fromGoogleApp)
            .putLong("ts", s.updatedAtMs)
            .apply()
    }

    companion object {
        const val GOOGLE_APP_PACKAGE = "com.google.android.googlequicksearchbox"
        private const val GOOGLE_WEATHER_PACKAGE = "com.google.android.apps.weather"

        private val WEATHER_PACKAGES = setOf(
            GOOGLE_APP_PACKAGE,
            GOOGLE_WEATHER_PACKAGE,
            "com.sec.android.daemonapp",
            "com.vivo.weather",
            "com.vivo.weather.provider",
            "com.coloros.weather",
            "com.coloros.weather2",
            "com.miui.weather2",
            "com.asus.weathertime",
            "com.huawei.android.totemweather"
        )

        private val TEMP_REGEX = Regex("""(-?\d{1,2})\s*°\s*[CFcf]?""")

        private val WEATHER_KEYWORDS = listOf(
            "晴", "雲", "陰", "雨", "雷", "霧", "雪", "天氣", "氣溫", "溫度", "體感", "降雨",
            "weather", "sunny", "cloud", "rain", "storm", "clear", "overcast", "humidity", "forecast"
        )
    }
}

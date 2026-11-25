package com.dxcar.launcher.weather

import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.location.Geocoder
import android.net.Uri
import androidx.compose.runtime.Composable
import com.dxcar.launcher.i18n.DxCarI18n
import com.dxcar.launcher.i18n.DxCarI18n.str
import com.dxcar.launcher.i18n.EffectiveLanguage
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
    val conditionText: String = DxCarI18n.tr("讀取天氣中…", "读取天气中…", "Loading weather…", "天気を読み込み中…"),
    val locationName: String = DxCarI18n.tr("當地天氣", "当地天气", "Local Weather", "現在地の天気"),
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
    private var languageObserverStarted = false

    fun start(initialLat: Double, initialLon: Double) {
        lastFetchLat = initialLat
        lastFetchLon = initialLon
        if (!languageObserverStarted) {
            languageObserverStarted = true
            scope.launch {
                DxCarI18n.effectiveLanguage.collect {
                    val cur = _weather.value
                    val nextLoc = when (cur.locationName) {
                        in DEFAULT_LOCAL_LABELS -> DxCarI18n.tr("當地天氣", "当地天气", "Local Weather", "現在地の天気")
                        in CURRENT_LOC_LABELS -> DxCarI18n.tr("目前位置", "当前位置", "Current Location", "現在地")
                        else -> cur.locationName
                    }
                    val nextCond = if (!cur.hasData) {
                        DxCarI18n.tr("讀取天氣中…", "读取天气中…", "Loading weather…", "天気を読み込み中…")
                    } else {
                        wmoCodeToLocalized(cur.weatherCode)
                    }
                    _weather.value = cur.copy(
                        conditionText = nextCond,
                        locationName = nextLoc
                    )
                    if (!cur.fromGoogleApp && (lastFetchLat != 0.0 || lastFetchLon != 0.0)) {
                        fetchFallbackWeatherSync(lastFetchLat, lastFetchLon)
                    }
                }
            }
        }
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

        val rawCondition = extractConditionZh(combined)
        val code = conditionToCode(rawCondition)
        val condition = wmoCodeToLocalized(code)
        val locCandidate = extractLocationFromGoogleText(title, text, subText)
            .ifBlank { _weather.value.locationName.takeIf { it !in DEFAULT_LOCAL_LABELS }.orEmpty() }
            .ifBlank { DxCarI18n.tr("目前位置", "当前位置", "Current Location", "現在地") }

        val isGoogle = lowerPkg == GOOGLE_APP_PACKAGE || lowerPkg == GOOGLE_WEATHER_PACKAGE
        val next = LocalWeatherState(
            hasData = true,
            temperatureText = "${tempVal}°C",
            conditionText = condition,
            locationName = locCandidate,
            detailText = if (isGoogle) {
                DxCarI18n.tr("Google 天氣即時資訊", "Google 天气实时信息", "Google Live Weather", "Google リアルタイム天気")
            } else {
                DxCarI18n.tr("系統天氣同步", "系统天气同步", "System Weather Sync", "システム天気同期")
            },
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
                    parts += DxCarI18n.tr("降雨 $rainProb%", "降雨 $rainProb%", "Rain $rainProb%", "降水 $rainProb%")
                }
                detail = parts.joinToString(" · ")
            }

            val placeName = resolveDistrictName(lat, lon)
            lastNetworkFetchMs = System.currentTimeMillis()
            val next = LocalWeatherState(
                hasData = true,
                temperatureText = "${temp.roundToInt()}°C",
                conditionText = wmoCodeToLocalized(code),
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
                val list = Geocoder(context, DxCarI18n.currentLocale()).getFromLocation(lat, lon, 1)
                val addr = list?.firstOrNull()
                if (addr != null) {
                    val district = addr.subLocality ?: addr.locality ?: addr.subAdminArea ?: addr.adminArea
                    if (!district.isNullOrBlank()) return district
                }
            }
        }
        val bdcLang = when (DxCarI18n.current()) {
            EffectiveLanguage.ZH_TW -> "zh-TW"
            EffectiveLanguage.ZH_CN -> "zh-CN"
            EffectiveLanguage.EN -> "en"
            EffectiveLanguage.JA -> "ja"
        }
        return runCatching {
            val geoUrl = "https://api.bigdatacloud.net/data/reverse-geocode-client" +
                "?latitude=$lat&longitude=$lon&localityLanguage=$bdcLang"
            val req = Request.Builder().url(geoUrl).build()
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use ""
                val obj = JSONObject(resp.body?.string().orEmpty())
                obj.optString("locality")
                    .ifBlank { obj.optString("city") }
                    .ifBlank { obj.optString("principalSubdivision") }
            }
        }.getOrDefault("").ifBlank {
            DxCarI18n.tr("當地天氣", "当地天气", "Local Weather", "現在地の天気")
        }
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

    private fun loadCached(): LocalWeatherState {
        val has = prefs.getBoolean("has", false)
        if (!has) return LocalWeatherState()
        val code = prefs.getInt("code", 1)
        val defaultLoc = DxCarI18n.tr("當地天氣", "当地天气", "Local Weather", "現在地の天気")
        val rawLoc = prefs.getString("loc", defaultLoc) ?: defaultLoc
        val loc = when (rawLoc) {
            in DEFAULT_LOCAL_LABELS -> defaultLoc
            in CURRENT_LOC_LABELS -> DxCarI18n.tr("目前位置", "当前位置", "Current Location", "現在地")
            else -> rawLoc
        }
        return LocalWeatherState(
            hasData = true,
            temperatureText = prefs.getString("temp", "--°C") ?: "--°C",
            conditionText = wmoCodeToLocalized(code),
            locationName = loc,
            detailText = prefs.getString("detail", "") ?: "",
            weatherCode = code,
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

        val DEFAULT_LOCAL_LABELS = setOf("當地天氣", "当地天气", "Local Weather", "現在地の天気")
        val CURRENT_LOC_LABELS = setOf("目前位置", "当前位置", "Current Location", "現在地")

        fun wmoCodeToLocalized(code: Int): String = when (code) {
            0 -> DxCarI18n.tr("晴朗", "晴朗", "Clear", "快晴")
            1 -> DxCarI18n.tr("大致晴朗", "大致晴朗", "Mostly Clear", "晴れ")
            2 -> DxCarI18n.tr("晴時多雲", "晴间多云", "Partly Cloudy", "晴れ時々曇り")
            3 -> DxCarI18n.tr("陰天", "阴天", "Overcast", "曇り")
            45, 48 -> DxCarI18n.tr("有霧", "有雾", "Foggy", "霧")
            51, 53, 55, 56, 57 -> DxCarI18n.tr("毛毛雨", "毛毛雨", "Drizzle", "霧雨")
            61, 63, 80, 81 -> DxCarI18n.tr("陣雨", "阵雨", "Showers", "にわか雨")
            65, 82 -> DxCarI18n.tr("大雨", "大雨", "Heavy Rain", "大雨")
            71, 73, 75, 77 -> DxCarI18n.tr("降雪", "降雪", "Snow", "雪")
            95, 96, 99 -> DxCarI18n.tr("雷陣雨", "雷阵雨", "Thunderstorm", "雷雨")
            else -> DxCarI18n.tr("多雲", "多云", "Cloudy", "曇り")
        }

        @Composable
        fun localizedConditionStr(code: Int, hasData: Boolean): String {
            if (!hasData) {
                return str("讀取天氣中…", "读取天气中…", "Loading weather…", "天気を読み込み中…")
            }
            return when (code) {
                0 -> str("晴朗", "晴朗", "Clear", "快晴")
                1 -> str("大致晴朗", "大致晴朗", "Mostly Clear", "晴れ")
                2 -> str("晴時多雲", "晴间多云", "Partly Cloudy", "晴れ時々曇り")
                3 -> str("陰天", "阴天", "Overcast", "曇り")
                45, 48 -> str("有霧", "有雾", "Foggy", "霧")
                51, 53, 55, 56, 57 -> str("毛毛雨", "毛毛雨", "Drizzle", "霧雨")
                61, 63, 80, 81 -> str("陣雨", "阵雨", "Showers", "にわか雨")
                65, 82 -> str("大雨", "大雨", "Heavy Rain", "大雨")
                71, 73, 75, 77 -> str("降雪", "降雪", "Snow", "雪")
                95, 96, 99 -> str("雷陣雨", "雷阵雨", "Thunderstorm", "雷雨")
                else -> str("多雲", "多云", "Cloudy", "曇り")
            }
        }

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

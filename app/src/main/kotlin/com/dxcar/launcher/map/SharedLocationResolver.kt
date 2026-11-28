package com.dxcar.launcher.map

import android.content.Context
import android.content.Intent
import android.location.Geocoder
import android.net.Uri
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder
import java.util.Locale

data class ResolvedSharedLocation(
    val label: String,
    val address: String,
    val latitude: Double,
    val longitude: Double
)

data class ExternalNavAppOption(
    val packageName: String,
    val displayName: String
)

/**
 * Resolves shared text, keywords, street addresses, or Google Maps share links
 * (e.g. `https://maps.app.goo.gl/...`) on the Phone into a clean real address + GPS coordinates,
 * and opens received locations on the Car in Built-In Map, Google Maps, or other installed nav apps.
 */
object SharedLocationResolver {

    private val URL_REGEX = Regex("""https?://[^\s<>"]+""", RegexOption.IGNORE_CASE)
    private val MAP_URL_REGEX = Regex(
        """https?://(?:maps\.app\.goo\.gl|goo\.gl/maps|g\.page|maps\.google\.[^\s/]+|(?:www\.)?google\.[^\s/]+/maps|maps\.apple\.com|(?:www\.)?waze\.com|map\.baidu\.com|uri\.amap\.com|surl\.amap\.com)[^\s<>"]*""",
        RegexOption.IGNORE_CASE
    )
    private val RAW_COORDS_REGEX = Regex("""-?\d{1,2}\.\d{3,}\s*[,，\s]\s*-?\d{1,3}\.\d{3,}""")
    private val ADDRESS_MARKER_REGEX = Regex("""[縣市區鄉鎮村里路街大道段巷弄號樓]|(?:\b(?:Rd|Road|St|Street|Ave|Avenue|Blvd|Ln|Lane|Way|Dr|Drive|Hwy|Highway)\b)""", RegexOption.IGNORE_CASE)
    private val AT_COORDS_REGEX = Regex("""@(-?\d+\.\d+),(-?\d+\.\d+)""")

    /**
     * Distinguishes a direct map link / street address / coordinates from arbitrary shared text
     * (social posts, chat paragraphs, articles, questions) that should be handed to Gemini first.
     */
    fun isDirectMapOrAddress(rawText: String): Boolean {
        val trimmed = rawText.trim()
        if (trimmed.isBlank()) return false
        if (trimmed.startsWith("geo:", ignoreCase = true)) return true
        if (MAP_URL_REGEX.containsMatchIn(trimmed)) return true
        if (RAW_COORDS_REGEX.containsMatchIn(trimmed)) return true
        // Non-map web links (Threads, FB, IG, YouTube, News, etc.) are NOT direct addresses -> let Gemini judge!
        if (URL_REGEX.containsMatchIn(trimmed)) return false
        // Very long prose / multi-sentence chat messages -> let Gemini judge!
        if (trimmed.length > 75 || trimmed.count { it == '\n' } >= 3) return false
        return ADDRESS_MARKER_REGEX.containsMatchIn(trimmed)
    }
    private val BANG_COORDS_REGEX = Regex("""!3d(-?\d+\.\d+)!4d(-?\d+\.\d+)""")
    private val Q_COORDS_REGEX = Regex("""[?&](?:q|query|ll|daddr)=(-?\d+\.\d+)[,%2C+]+(-?\d+\.\d+)""", RegexOption.IGNORE_CASE)
    private val PLACE_PATH_REGEX = Regex("""/maps/place/([^/?#]+)""")

    private val KNOWN_OTHER_NAV_APPS = listOf(
        "com.kingwaytek.naviking3d" to "導航王 3D",
        "com.kingwaytek.naviking" to "導航王",
        "com.kingwaytek.naviking.n5" to "導航王 N5",
        "com.papago.s1OBU" to "PAPAGO!",
        "com.papago.M11_Taiwan" to "PAPAGO!",
        "com.waze" to "Waze",
        "com.sygic.aura" to "Sygic",
        "com.here.app.maps" to "HERE WeGo",
        "net.osmand" to "OsmAnd",
        "net.osmand.plus" to "OsmAnd+",
        "com.autonavi.minimap" to "高德地圖",
        "com.baidu.BaiduMap" to "百度地圖",
        "com.polstar.polnav.mobile" to "Polnav"
    )

    suspend fun resolveSharedText(
        context: Context,
        navEngine: BuiltInNavEngine,
        rawText: String,
        biasLat: Double,
        biasLon: Double
    ): ResolvedSharedLocation = withContext(Dispatchers.IO) {
        val trimmed = rawText.trim()
        val urlMatch = URL_REGEX.find(trimmed)
        val rawUrl = urlMatch?.value.orEmpty()
        val nonUrlText = trimmed
            .replace(URL_REGEX, " ")
            .replace(Regex("""[\r\n]+"""), " ")
            .replace(Regex("""\s{2,}"""), " ")
            .trim()

        var lat = 0.0
        var lon = 0.0
        var extractedPlace = nonUrlText

        if (rawUrl.isNotBlank()) {
            val expandedUrl = followRedirects(rawUrl)
            val bang = BANG_COORDS_REGEX.find(expandedUrl)
            val at = AT_COORDS_REGEX.find(expandedUrl)
            val qCoord = Q_COORDS_REGEX.find(expandedUrl)
            when {
                bang != null -> {
                    lat = bang.groupValues[1].toDoubleOrNull() ?: 0.0
                    lon = bang.groupValues[2].toDoubleOrNull() ?: 0.0
                }
                qCoord != null -> {
                    lat = qCoord.groupValues[1].toDoubleOrNull() ?: 0.0
                    lon = qCoord.groupValues[2].toDoubleOrNull() ?: 0.0
                }
                at != null -> {
                    lat = at.groupValues[1].toDoubleOrNull() ?: 0.0
                    lon = at.groupValues[2].toDoubleOrNull() ?: 0.0
                }
            }
            val placeSeg = PLACE_PATH_REGEX.find(expandedUrl)?.groupValues?.getOrNull(1)
            if (!placeSeg.isNullOrBlank()) {
                val decoded = runCatching { URLDecoder.decode(placeSeg.replace("+", " "), "UTF-8") }
                    .getOrDefault(placeSeg)
                    .trim()
                if (decoded.isNotBlank() && extractedPlace.isBlank()) {
                    extractedPlace = decoded
                } else if (decoded.isNotBlank() && decoded.length > extractedPlace.length) {
                    extractedPlace = decoded
                }
            }
        }

        // If coordinates were extracted from a Maps URL, reverse-geocode to get the real street address!
        if (lat != 0.0 && lon != 0.0) {
            val revAddr = reverseGeocodeAddress(context, lat, lon)
            val finalLabel = extractedPlace.ifBlank { revAddr.ifBlank { "%.5f, %.5f".format(lat, lon) } }
            val finalAddr = revAddr.ifBlank { finalLabel }
            return@withContext ResolvedSharedLocation(
                label = finalLabel.take(64),
                address = finalAddr.take(140),
                latitude = lat,
                longitude = lon
            )
        }

        // Otherwise run our 4-engine door-number geocoder on the keyword / street address!
        val query = extractedPlace.ifBlank { trimmed }
        val candidates = navEngine.resolveAddressCandidates(query, biasLat, biasLon)
        val best = candidates.firstOrNull()
        if (best != null) {
            val bestLabel = if (extractedPlace.isNotBlank() && extractedPlace.length <= 28 &&
                !best.name.contains(extractedPlace, ignoreCase = true)
            ) {
                "$extractedPlace · ${best.name}"
            } else {
                best.name
            }
            val bestAddr = best.subtitle.ifBlank { best.name }
            return@withContext ResolvedSharedLocation(
                label = bestLabel.take(64),
                address = bestAddr.take(140),
                latitude = best.latitude,
                longitude = best.longitude
            )
        }

        ResolvedSharedLocation(
            label = query.take(64).ifBlank { "分享地點" },
            address = query.take(140),
            latitude = 0.0,
            longitude = 0.0
        )
    }

    private fun followRedirects(startUrl: String): String {
        var current = startUrl
        repeat(5) {
            val next = runCatching {
                val conn = (URL(current).openConnection() as HttpURLConnection).apply {
                    instanceFollowRedirects = false
                    connectTimeout = 4_500
                    readTimeout = 4_500
                    requestMethod = "GET"
                    setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36")
                }
                val code = conn.responseCode
                val loc = conn.getHeaderField("Location")
                conn.disconnect()
                if (code in 300..399 && !loc.isNullOrBlank()) {
                    if (loc.startsWith("http", ignoreCase = true)) loc
                    else URL(URL(current), loc).toString()
                } else {
                    null
                }
            }.getOrNull() ?: return current
            current = next
        }
        return current
    }

    @Suppress("DEPRECATION")
    private fun reverseGeocodeAddress(context: Context, lat: Double, lon: Double): String {
        return runCatching {
            if (!Geocoder.isPresent()) return@runCatching ""
            val geocoder = Geocoder(context, Locale.TRADITIONAL_CHINESE)
            val list = geocoder.getFromLocation(lat, lon, 1)
            list?.firstOrNull()?.getAddressLine(0)?.trim().orEmpty()
        }.getOrDefault("")
    }

    fun findInstalledOtherNavApps(context: Context): List<ExternalNavAppOption> {
        val pm = context.packageManager
        val out = mutableListOf<ExternalNavAppOption>()
        for ((pkg, defaultLabel) in KNOWN_OTHER_NAV_APPS) {
            val installed = runCatching { pm.getPackageInfo(pkg, 0); true }.getOrDefault(false)
            if (installed) {
                val label = runCatching {
                    pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
                }.getOrDefault(defaultLabel)
                out += ExternalNavAppOption(pkg, label)
            }
        }
        return out
    }

    fun openInGoogleMaps(context: Context, label: String, address: String, lat: Double, lon: Double): Boolean {
        val uri = when {
            lat != 0.0 && lon != 0.0 -> Uri.parse("google.navigation:q=$lat,$lon&mode=d")
            address.isNotBlank() -> Uri.parse("google.navigation:q=${Uri.encode(address)}&mode=d")
            else -> Uri.parse("google.navigation:q=${Uri.encode(label)}&mode=d")
        }
        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            setPackage("com.google.android.apps.maps")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return runCatching {
            context.startActivity(intent)
            true
        }.getOrElse {
            openInOtherMapChooser(context, null, label, address, lat, lon)
        }
    }

    fun openInOtherMapChooser(
        context: Context,
        specificPackage: String?,
        label: String,
        address: String,
        lat: Double,
        lon: Double
    ): Boolean {
        val qLabel = Uri.encode(address.ifBlank { label })
        val geoUri = if (lat != 0.0 && lon != 0.0) {
            Uri.parse("geo:$lat,$lon?q=$lat,$lon($qLabel)")
        } else {
            Uri.parse("geo:0,0?q=$qLabel")
        }
        if (!specificPackage.isNullOrBlank()) {
            val targeted = Intent(Intent.ACTION_VIEW, geoUri).apply {
                setPackage(specificPackage)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (runCatching { context.startActivity(targeted); true }.getOrDefault(false)) {
                return true
            }
            val launch = context.packageManager.getLaunchIntentForPackage(specificPackage)?.apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (launch != null && runCatching { context.startActivity(launch); true }.getOrDefault(false)) {
                return true
            }
        }
        val chooser = Intent.createChooser(
            Intent(Intent.ACTION_VIEW, geoUri),
            "選擇導航地圖開啟"
        ).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return runCatching {
            context.startActivity(chooser)
            true
        }.getOrDefault(false)
    }
}

package com.dxcar.launcher.voice

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.speech.RecognizerIntent
import com.dxcar.launcher.DxCarApp
import com.dxcar.launcher.data.GeminiVoiceMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

/**
 * Gemini-Powered Voice Navigation & Driving Assistant Controller:
 * Supports 3 seamless tiers so voice navigation works on ANY AI Box / Tablet:
 * 1. Direct Google Gemini App (`com.google.android.apps.bard`) Voice / Assist Handoff
 * 2. Android Speech Recognition (`RecognizerIntent`) -> Optional Gemini 2.5 Flash NLU API (`geminiApiKey`)
 *    -> Extracts exact destination & launches Google Maps Turn-by-Turn Navigation (`google.navigation:q=...`)
 * 3. Zero-Key Smart Mandarin Driving Intent Parser ("導航到台中高鐵站" / "回家" / "去公司" / "附近加油站")
 *    + Forwards general non-navigation Q&A straight to Gemini!
 */
object GeminiVoiceNavController {

    private const val GEMINI_PACKAGE = "com.google.android.apps.bard"
    private const val GOOGLE_QUICKSEARCH_PACKAGE = "com.google.android.googlequicksearchbox"

    private val scope by lazy { CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate) }
    private val json = Json { ignoreUnknownKeys = true }

    fun isGeminiAppInstalled(context: Context): Boolean {
        val pm = context.packageManager
        return runCatching { pm.getLaunchIntentForPackage(GEMINI_PACKAGE) != null }.getOrDefault(false)
    }

    /**
     * Builds the `RecognizerIntent` for MainActivity's `ActivityResultLauncher`.
     * When [forceBuiltInMap] is true (e.g. tapped directly on Plan A's built-in map), we ALWAYS capture speech
     * so the destination routes on the Launcher's own built-in Plan A map rather than jumping out to Google Maps!
     */
    fun buildSpeechRecognizerIntentOrOpenGemini(
        context: Context,
        mode: GeminiVoiceMode,
        forceBuiltInMap: Boolean = true,
        onStatusToast: (String) -> Unit
    ): Intent? {
        if (!forceBuiltInMap && mode == GeminiVoiceMode.GEMINI_APP_DIRECT) {
            val opened = launchGeminiVoiceAssistantDirect(context, query = null)
            onStatusToast(
                if (opened) "✨ 已喚起 Google Gemini 語音助理"
                else "⚠️ 未安裝 Google Gemini App，已改用內建語音辨識導航"
            )
            if (opened) return null
        }

        val speechIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.TRADITIONAL_CHINESE.toLanguageTag())
            putExtra(
                RecognizerIntent.EXTRA_PROMPT,
                "🎙️✨ Gemini 內建地圖語音導航：請說出目的地（如「導航到台北101」「附近加油站」「回家」）…"
            )
        }
        val canHandleSpeech = speechIntent.resolveActivity(context.packageManager) != null
        if (canHandleSpeech) {
            return speechIntent
        }

        onStatusToast("⚠️ 未偵測到系統語音辨識元件，請點擊地圖頂端搜尋框直接輸入目的地（或於商店安裝 Google 語音服務）")
        return null
    }

    /**
     * Directly opens the Google Gemini App (or system Assist voice overlay) optionally pre-filled with [query].
     */
    fun launchGeminiVoiceAssistantDirect(context: Context, query: String?): Boolean {
        val pm = context.packageManager

        // 1. If a text query is present and Gemini is installed, share/send prompt to Gemini
        if (!query.isNullOrBlank() && isGeminiAppInstalled(context)) {
            val sendToGemini = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                setPackage(GEMINI_PACKAGE)
                putExtra(Intent.EXTRA_TEXT, query)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (runCatching { context.startActivity(sendToGemini); true }.getOrDefault(false)) {
                return true
            }
        }

        // 2. Try Voice Command / Assist targeted at Gemini first, then system default assistant
        val candidates = buildList {
            add(Intent(Intent.ACTION_VOICE_COMMAND).setPackage(GEMINI_PACKAGE))
            add(Intent(Intent.ACTION_ASSIST).setPackage(GEMINI_PACKAGE))
            pm.getLaunchIntentForPackage(GEMINI_PACKAGE)?.let { add(it) }
            add(Intent(Intent.ACTION_VOICE_COMMAND))
            add(Intent(Intent.ACTION_ASSIST))
            pm.getLaunchIntentForPackage(GOOGLE_QUICKSEARCH_PACKAGE)?.let { add(it) }
        }
        for (intent in candidates) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (runCatching { context.startActivity(intent); true }.getOrDefault(false)) {
                return true
            }
        }
        return false
    }

    /**
     * Processes spoken transcript via Gemini 2.5 Flash NLU (if API key configured) + Smart Mandarin Parser,
     * and starts turn-by-turn navigation DIRECTLY ON THE LAUNCHER'S BUILT-IN PLAN A MAP (`BuiltInNavEngine`)!
     */
    fun handleSpokenNavigationTranscript(
        context: Context,
        rawTranscript: String,
        onStatusHud: (String) -> Unit
    ) {
        val spoken = rawTranscript.trim()
        if (spoken.isBlank()) return
        val app = DxCarApp.instance ?: return
        val cfg = app.preferences.config.value
        val curLoc = app.locationTracker.location.value
        val startLat = if (curLoc.latitude != 0.0) curLoc.latitude else cfg.lastKnownLat
        val startLon = if (curLoc.longitude != 0.0) curLoc.longitude else cfg.lastKnownLon

        // Quick local shortcuts for Home / Work -> Routed directly on Launcher's Built-In Map!
        if (spoken == "回家" || spoken.contains("導航回家") || spoken.contains("帶我回家")) {
            if (cfg.homeAddress.isNotBlank()) {
                app.builtInNavEngine.navigateByQuery(
                    query = cfg.homeAddress,
                    startLat = startLat,
                    startLon = startLon,
                    onStatus = onStatusHud
                )
                return
            }
        }
        if (spoken == "去公司" || spoken.contains("導航去公司") || spoken.contains("導航到公司") || spoken == "上班") {
            if (cfg.workAddress.isNotBlank()) {
                app.builtInNavEngine.navigateByQuery(
                    query = cfg.workAddress,
                    startLat = startLat,
                    startLon = startLon,
                    onStatus = onStatusHud
                )
                return
            }
        }

        scope.launch {
            val apiKey = cfg.geminiApiKey.trim()
            if (apiKey.isNotBlank()) {
                onStatusHud("✨ Gemini 正在理解語音並於啟動器地圖規劃：「$spoken」…")
                val parsed = withContext(Dispatchers.IO) {
                    parseDestinationWithGeminiCloud(apiKey, spoken, startLat, startLon, cfg.homeAddress, cfg.workAddress)
                }
                if (parsed != null) {
                    if (parsed.isNavigation && parsed.destination.isNotBlank()) {
                        if (parsed.spokenReply.isNotBlank()) {
                            app.ttsAnnouncer.speak(parsed.spokenReply)
                        }
                        app.builtInNavEngine.navigateByQuery(
                            query = parsed.destination,
                            startLat = startLat,
                            startLon = startLon,
                            explicitDestLat = parsed.lat,
                            explicitDestLon = parsed.lon,
                            onStatus = onStatusHud
                        )
                        return@launch
                    } else if (!parsed.isNavigation) {
                        if (parsed.spokenReply.isNotBlank()) {
                            app.ttsAnnouncer.speak(parsed.spokenReply)
                            onStatusHud("✨ Gemini：${parsed.spokenReply}")
                        } else {
                            launchGeminiVoiceAssistantDirect(context, query = spoken)
                        }
                        return@launch
                    }
                }
            }

            // Smart Zero-Key Mandarin Intent Classification -> Built-In Plan A Map Navigation!
            val lower = spoken.lowercase()
            val isExplicitGeminiChat = (lower.startsWith("問gemini") || lower.startsWith("問 gemini") ||
                spoken.contains("為什麼") || spoken.contains("笑話") || spoken.contains("新聞")) &&
                !spoken.contains("導航") && !spoken.contains("帶我去") && !spoken.contains("怎麼走")

            if (isExplicitGeminiChat && isGeminiAppInstalled(context)) {
                launchGeminiVoiceAssistantDirect(context, query = spoken)
                onStatusHud("✨ 已將問題交由 Gemini 回答：「$spoken」")
                return@launch
            }

            val cleanDest = extractDestinationFromMandarin(spoken)
            if (cleanDest.isNotBlank()) {
                app.builtInNavEngine.navigateByQuery(
                    query = cleanDest,
                    startLat = startLat,
                    startLon = startLon,
                    onStatus = onStatusHud
                )
            }
        }
    }

    internal fun extractDestinationFromMandarin(raw: String): String {
        var text = raw.trim()
        val prefixes = listOf(
            "hey gemini", "ok gemini", "gemini", "傑米尼",
            "請幫我導航到", "請幫我導航去", "幫我導航到", "幫我導航去",
            "請導航到", "請導航去", "導航前往", "導航到", "導航去", "導航至", "導航",
            "請帶我去", "帶我前往", "帶我去", "我要去", "我想去", "前往",
            "幫我找附近的", "搜尋附近的", "找附近的", "去附近的", "去"
        )
        for (p in prefixes) {
            if (text.lowercase().startsWith(p)) {
                text = text.substring(p.length).trim(' ', '，', ',', '。', '、', '：', ':')
            }
        }
        val suffixes = listOf("怎麼走", "的路線", "導航", "在哪裡")
        for (s in suffixes) {
            if (text.endsWith(s) && text.length > s.length + 1) {
                text = text.removeSuffix(s).trim()
            }
        }
        return text.ifBlank { raw.trim() }
    }

    private data class GeminiNavParsed(
        val isNavigation: Boolean,
        val destination: String,
        val lat: Double?,
        val lon: Double?,
        val spokenReply: String
    )

    private fun parseDestinationWithGeminiCloud(
        apiKey: String,
        userSpeech: String,
        curLat: Double,
        curLon: Double,
        homeAddr: String,
        workAddr: String
    ): GeminiNavParsed? {
        return runCatching {
            val endpoint =
                "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=${Uri.encode(apiKey)}"
            val prompt = """
                你是車機內建地圖導航語音助理。目前車輛座標 ($curLat, $curLon)，住家：「$homeAddr」，公司：「$workAddr」。
                請分析駕駛人語音：「$userSpeech」。
                若駕駛人想去某地點、找地標/餐廳/加油站/停車場或導航，請回傳純 JSON（若你能確定該台灣/當地知名地標經緯度請一併填入 lat 與 lon，不確定則填 0）：
                {"isNavigation":true,"destination":"地標或地址名稱","lat":0.0,"lon":0.0,"spokenReply":"簡短繁體中文語音確認"}
                若純粹是聊天問答，請回傳純 JSON：
                {"isNavigation":false,"destination":"","lat":0.0,"lon":0.0,"spokenReply":"50字內繁體中文簡短回答"}
                只輸出 JSON，不要加 markdown 語法。
            """.trimIndent()

            val bodyObj = buildJsonObject {
                put("contents", buildJsonArray {
                    add(buildJsonObject {
                        put("parts", buildJsonArray {
                            add(buildJsonObject { put("text", prompt) })
                        })
                    })
                })
            }

            val conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 5000
                readTimeout = 6500
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
            }
            conn.outputStream.use { it.write(bodyObj.toString().toByteArray(Charsets.UTF_8)) }
            if (conn.responseCode !in 200..299) return null
            val respText = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val root = json.parseToJsonElement(respText).jsonObject
            val candidateText = root["candidates"]?.jsonArray?.firstOrNull()
                ?.jsonObject?.get("content")
                ?.jsonObject?.get("parts")?.jsonArray?.firstOrNull()
                ?.jsonObject?.get("text")?.jsonPrimitive?.contentOrNull
                ?.trim()
                ?.removePrefix("```json")
                ?.removePrefix("```")
                ?.removeSuffix("```")
                ?.trim()
                ?: return null

            val parsedJson = json.parseToJsonElement(candidateText).jsonObject
            val pLat = parsedJson["lat"]?.jsonPrimitive?.contentOrNull?.toDoubleOrNull()?.takeIf { it != 0.0 }
            val pLon = parsedJson["lon"]?.jsonPrimitive?.contentOrNull?.toDoubleOrNull()?.takeIf { it != 0.0 }
            GeminiNavParsed(
                isNavigation = parsedJson["isNavigation"]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull() ?: true,
                destination = parsedJson["destination"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                lat = pLat,
                lon = pLon,
                spokenReply = parsedJson["spokenReply"]?.jsonPrimitive?.contentOrNull.orEmpty()
            )
        }.getOrNull()
    }
}

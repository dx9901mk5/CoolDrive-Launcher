package com.dxcar.launcher.comms

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.dxcar.launcher.i18n.DxCarI18n
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import java.util.UUID

class TtsAnnouncer(context: Context) {

    private val appContext = context.applicationContext
    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val mainHandler = Handler(Looper.getMainLooper())
    private var tts: TextToSpeech? = null
    private var ready = false
    private var currentFocusRequest: AudioFocusRequest? = null

    @Volatile
    private var activeUtteranceId: String? = null
    private var safetyClearRunnable: Runnable? = null

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private val _speakingLabel = MutableStateFlow("")
    val speakingLabel: StateFlow<String> = _speakingLabel.asStateFlow()

    init {
        tts = TextToSpeech(appContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val engine = tts ?: return@TextToSpeech
                val twResult = engine.setLanguage(Locale.TRADITIONAL_CHINESE)
                if (twResult == TextToSpeech.LANG_MISSING_DATA ||
                    twResult == TextToSpeech.LANG_NOT_SUPPORTED
                ) {
                    engine.setLanguage(Locale.CHINESE)
                }
                engine.setSpeechRate(1.05f)
                engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        if (utteranceId == null || utteranceId == activeUtteranceId) {
                            _isSpeaking.value = true
                        }
                    }

                    override fun onDone(utteranceId: String?) {
                        if (utteranceId == null || utteranceId == activeUtteranceId) {
                            clearSpeakingState()
                        }
                    }

                    override fun onStop(utteranceId: String?, interrupted: Boolean) {
                        if (utteranceId == null || utteranceId == activeUtteranceId) {
                            clearSpeakingState()
                        }
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        if (utteranceId == null || utteranceId == activeUtteranceId) {
                            clearSpeakingState()
                        }
                    }
                })
                ready = true
            }
        }
    }

    fun speakMessage(appName: String, sender: String, text: String) {
        val cleaned = text.take(180)
        val spoken = DxCarI18n.tr(
            "收到來自 $appName，$sender 的訊息：$cleaned",
            "收到来自 $appName，$sender 的消息：$cleaned",
            "New message from $sender on $appName: $cleaned",
            "$appName の $sender さんからのメッセージ：$cleaned"
        )
        val label = DxCarI18n.tr(
            "正在朗讀：$sender",
            "正在朗读：$sender",
            "Reading: $sender",
            "読み上げ中：$sender"
        )
        speak(spoken, label)
    }

    fun speak(text: String, label: String = "") {
        if (!ready || text.isBlank()) return
        runCatching {
            val targetLocale = DxCarI18n.currentLocale()
            val res = tts?.setLanguage(targetLocale)
            if (res == TextToSpeech.LANG_MISSING_DATA || res == TextToSpeech.LANG_NOT_SUPPORTED) {
                if (targetLocale.language == "zh") {
                    tts?.setLanguage(Locale.CHINESE)
                }
            }
        }
        val uid = UUID.randomUUID().toString()
        activeUtteranceId = uid
        _speakingLabel.value = label.ifBlank {
            DxCarI18n.tr("語音朗讀中", "语音朗读中", "Speaking", "音声読み上げ中")
        }
        _isSpeaking.value = true
        scheduleSafetyClear(uid, text.length)
        requestDuckFocus()
        val queued = runCatching {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, uid) == TextToSpeech.SUCCESS
        }.getOrDefault(false)
        if (!queued) {
            clearSpeakingState()
        }
    }

    fun stop() {
        runCatching { tts?.stop() }
        clearSpeakingState()
    }

    private fun scheduleSafetyClear(uid: String, charCount: Int) {
        safetyClearRunnable?.let { mainHandler.removeCallbacks(it) }
        val timeoutMs = (charCount * 280L + 2_500L).coerceIn(4_500L, 45_000L)
        val r = Runnable {
            if (activeUtteranceId == uid) {
                clearSpeakingState()
            }
        }
        safetyClearRunnable = r
        mainHandler.postDelayed(r, timeoutMs)
    }

    private fun clearSpeakingState() {
        safetyClearRunnable?.let { mainHandler.removeCallbacks(it) }
        safetyClearRunnable = null
        activeUtteranceId = null
        _isSpeaking.value = false
        _speakingLabel.value = ""
        abandonFocus()
    }

    private fun requestDuckFocus() {
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()
        val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(attrs)
            .build()
        currentFocusRequest = req
        runCatching { audioManager.requestAudioFocus(req) }
    }

    private fun abandonFocus() {
        currentFocusRequest?.let { req ->
            runCatching { audioManager.abandonAudioFocusRequest(req) }
        }
        currentFocusRequest = null
    }
}

package com.cooldrive.launcher.comms

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale
import java.util.UUID

class TtsAnnouncer(context: Context) {

    private val appContext = context.applicationContext
    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var tts: TextToSpeech? = null
    private var ready = false
    private var currentFocusRequest: AudioFocusRequest? = null

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
                    override fun onStart(utteranceId: String?) = Unit
                    override fun onDone(utteranceId: String?) = abandonFocus()
                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) = abandonFocus()
                })
                ready = true
            }
        }
    }

    fun speakMessage(appName: String, sender: String, text: String) {
        val cleaned = text.take(180)
        val spoken = "收到來自 $appName，$sender 的訊息：$cleaned"
        speak(spoken)
    }

    fun speak(text: String) {
        if (!ready || text.isBlank()) return
        requestDuckFocus()
        runCatching {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, UUID.randomUUID().toString())
        }
    }

    fun stop() {
        runCatching { tts?.stop() }
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

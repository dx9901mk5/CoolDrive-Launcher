package com.cooldrive.launcher.media

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.SystemClock
import android.view.KeyEvent
import com.cooldrive.launcher.comms.BoxNotificationListenerService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class MediaSessionSummary(
    val packageName: String,
    val appLabel: String,
    val isPlaying: Boolean
)

data class ActiveMediaState(
    val hasSession: Boolean = false,
    val packageName: String = "",
    val appName: String = "媒體播放器",
    val title: String = "目前沒有播放中的媒體",
    val artist: String = "點擊下方圖示開啟 YouTube、Spotify 或音樂 App",
    val album: String = "",
    val albumArt: Bitmap? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val availableSessions: List<MediaSessionSummary> = emptyList()
)

class MediaControlBridge(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val mediaSessionManager =
        context.getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager

    private val _mediaState = MutableStateFlow(ActiveMediaState())
    val mediaState: StateFlow<ActiveMediaState> = _mediaState.asStateFlow()

    private var activeControllers: List<MediaController> = emptyList()
    private var selectedController: MediaController? = null
    private var preferredPackage: String? = null
    private var listenerRegistered = false

    private val controllerCallback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) = publishSelectedState()
        override fun onMetadataChanged(metadata: MediaMetadata?) = publishSelectedState()
        override fun onSessionDestroyed() = refreshSessions()
    }

    private val sessionsChangedListener =
        MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
            updateControllers(controllers.orEmpty())
        }

    fun start() {
        refreshSessions()
        scope.launch {
            while (isActive) {
                if (_mediaState.value.isPlaying && _mediaState.value.durationMs > 0) {
                    val controller = selectedController
                    val pb = controller?.playbackState
                    if (pb != null) {
                        val elapsed = if (pb.state == PlaybackState.STATE_PLAYING) {
                            ((SystemClock.elapsedRealtime() - pb.lastPositionUpdateTime) * pb.playbackSpeed).toLong()
                        } else {
                            0L
                        }
                        val pos = (pb.position + elapsed).coerceIn(0L, _mediaState.value.durationMs)
                        _mediaState.value = _mediaState.value.copy(positionMs = pos)
                    }
                }
                delay(1_000L)
            }
        }
    }

    fun refreshSessions() {
        val manager = mediaSessionManager ?: return
        val component = ComponentName(context, BoxNotificationListenerService::class.java)
        runCatching {
            if (!listenerRegistered) {
                manager.addOnActiveSessionsChangedListener(sessionsChangedListener, component)
                listenerRegistered = true
            }
            val list = manager.getActiveSessions(component)
            updateControllers(list)
        }
    }

    private fun updateControllers(controllers: List<MediaController>) {
        selectedController?.unregisterCallback(controllerCallback)
        activeControllers = controllers

        val picked = when {
            preferredPackage != null ->
                controllers.firstOrNull { it.packageName == preferredPackage }
                    ?: pickBestController(controllers)
            else -> pickBestController(controllers)
        }

        selectedController = picked
        picked?.registerCallback(controllerCallback)
        publishSelectedState()
    }

    private fun pickBestController(controllers: List<MediaController>): MediaController? {
        if (controllers.isEmpty()) return null
        return controllers.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
            ?: controllers.firstOrNull { it.metadata != null }
            ?: controllers.first()
    }

    private fun publishSelectedState() {
        val controller = selectedController
        val summaries = activeControllers.map { c ->
            MediaSessionSummary(
                packageName = c.packageName,
                appLabel = resolveAppLabel(c.packageName),
                isPlaying = c.playbackState?.state == PlaybackState.STATE_PLAYING
            )
        }.distinctBy { it.packageName }

        if (controller == null) {
            _mediaState.value = ActiveMediaState(availableSessions = summaries)
            return
        }

        val meta = controller.metadata
        val pb = controller.playbackState
        val pkg = controller.packageName
        val appLabel = resolveAppLabel(pkg)

        val title = meta?.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)
            ?: meta?.getString(MediaMetadata.METADATA_KEY_TITLE)
            ?: appLabel
        val artist = meta?.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: meta?.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE)
            ?: meta?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
            ?: appLabel
        val album = meta?.getString(MediaMetadata.METADATA_KEY_ALBUM).orEmpty()
        val art = meta?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: meta?.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: meta?.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
        val duration = meta?.getLong(MediaMetadata.METADATA_KEY_DURATION)?.coerceAtLeast(0L) ?: 0L
        val position = pb?.position?.coerceAtLeast(0L) ?: 0L
        val playing = pb?.state == PlaybackState.STATE_PLAYING ||
            pb?.state == PlaybackState.STATE_BUFFERING

        _mediaState.value = ActiveMediaState(
            hasSession = true,
            packageName = pkg,
            appName = appLabel,
            title = title,
            artist = artist,
            album = album,
            albumArt = art,
            isPlaying = playing,
            positionMs = position,
            durationMs = duration,
            availableSessions = summaries
        )
    }

    fun selectSession(packageName: String) {
        preferredPackage = packageName
        updateControllers(activeControllers)
    }

    fun togglePlayPause() {
        val controller = selectedController
        if (controller != null) {
            val isPlaying = controller.playbackState?.state == PlaybackState.STATE_PLAYING
            if (isPlaying) {
                controller.transportControls.pause()
            } else {
                controller.transportControls.play()
            }
        } else {
            sendMediaKeyFallback(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
        }
    }

    fun skipNext() {
        val controller = selectedController
        if (controller != null) {
            controller.transportControls.skipToNext()
        } else {
            sendMediaKeyFallback(KeyEvent.KEYCODE_MEDIA_NEXT)
        }
    }

    fun skipPrevious() {
        val controller = selectedController
        if (controller != null) {
            controller.transportControls.skipToPrevious()
        } else {
            sendMediaKeyFallback(KeyEvent.KEYCODE_MEDIA_PREVIOUS)
        }
    }

    fun seekTo(positionMs: Long) {
        selectedController?.transportControls?.seekTo(positionMs)
    }

    private fun sendMediaKeyFallback(keyCode: Int) {
        val now = SystemClock.uptimeMillis()
        audioManager.dispatchMediaKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0))
        audioManager.dispatchMediaKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, keyCode, 0))
    }

    private fun resolveAppLabel(pkg: String): String {
        return runCatching {
            val appInfo = context.packageManager.getApplicationInfo(pkg, 0)
            context.packageManager.getApplicationLabel(appInfo).toString()
        }.getOrElse { pkg }
    }
}

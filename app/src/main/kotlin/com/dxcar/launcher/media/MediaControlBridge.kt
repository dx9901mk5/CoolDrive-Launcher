package com.dxcar.launcher.media

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.Rating
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.SystemClock
import android.view.KeyEvent
import com.dxcar.launcher.DxCarApp
import com.dxcar.launcher.comms.BoxNotificationListenerService
import com.dxcar.launcher.data.LauncherPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

data class MediaSessionSummary(
    val packageName: String,
    val appLabel: String,
    val appIcon: Bitmap?,
    val isPlaying: Boolean
)

data class ActiveMediaState(
    val hasSession: Boolean = false,
    val isRememberedLastApp: Boolean = false,
    val packageName: String = "",
    val appName: String = "媒體播放器",
    val appIcon: Bitmap? = null,
    val title: String = "目前沒有播放中的媒體",
    val artist: String = "開啟音樂 App 後自動記憶，下次可直接點擊 ▶ 繼續播放",
    val album: String = "",
    val albumArt: Bitmap? = null,
    val isPlaying: Boolean = false,
    val canFavorite: Boolean = false,
    val isFavorited: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val availableSessions: List<MediaSessionSummary> = emptyList()
)

class MediaControlBridge(
    private val context: Context,
    private val preferences: LauncherPreferences
) {

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
    private val appIconCache = ConcurrentHashMap<String, Bitmap>()

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
        publishSelectedState()
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

        val rememberedPkg = preferredPackage ?: preferences.config.value.lastMediaPackage.takeIf { it.isNotBlank() }
        val picked = controllers.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
            ?: rememberedPkg?.let { target -> controllers.firstOrNull { it.packageName == target } }
            ?: pickBestController(controllers)

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
                appIcon = resolveAppIcon(c.packageName),
                isPlaying = c.playbackState?.state == PlaybackState.STATE_PLAYING
            )
        }.distinctBy { it.packageName }

        if (controller == null) {
            val cfg = preferences.config.value
            val fallbackPkg = cfg.lastMediaPackage.takeIf { it.isNotBlank() && isPackageInstalled(it) }
                ?: findBestInstalledMusicApp()
            if (fallbackPkg != null) {
                val label = cfg.lastMediaAppName.takeIf { it.isNotBlank() && fallbackPkg == cfg.lastMediaPackage }
                    ?: resolveAppLabel(fallbackPkg)
                val icon = resolveAppIcon(fallbackPkg)
                val savedTitle = if (fallbackPkg == cfg.lastMediaPackage && cfg.lastMediaTitle.isNotBlank()) {
                    cfg.lastMediaTitle
                } else {
                    "點擊 ▶ 繼續播放 $label"
                }
                val savedArtist = if (fallbackPkg == cfg.lastMediaPackage && cfg.lastMediaArtist.isNotBlank()) {
                    "${cfg.lastMediaArtist} · 上次使用 $label"
                } else {
                    "上次使用的音樂 App：$label（點 ▶ 直接喚醒續播）"
                }
                _mediaState.value = ActiveMediaState(
                    hasSession = false,
                    isRememberedLastApp = true,
                    packageName = fallbackPkg,
                    appName = label,
                    appIcon = icon,
                    title = savedTitle,
                    artist = savedArtist,
                    availableSessions = summaries
                )
            } else {
                _mediaState.value = ActiveMediaState(availableSessions = summaries)
            }
            return
        }

        val meta = controller.metadata
        val pb = controller.playbackState
        val pkg = controller.packageName
        val appLabel = resolveAppLabel(pkg)
        val appIcon = resolveAppIcon(pkg)

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
        val (canFav, isFav) = detectFavoriteCapability(controller, pkg, meta, pb)

        // Persist the latest active music app & track so cold-boot resume works seamlessly!
        preferences.recordLastMediaSession(
            packageName = pkg,
            appName = appLabel,
            title = if (title == appLabel) "" else title,
            artist = if (artist == appLabel) "" else artist
        )

        _mediaState.value = ActiveMediaState(
            hasSession = true,
            isRememberedLastApp = false,
            packageName = pkg,
            appName = appLabel,
            appIcon = appIcon,
            title = title,
            artist = artist,
            album = album,
            albumArt = art,
            isPlaying = playing,
            canFavorite = canFav,
            isFavorited = isFav,
            positionMs = position,
            durationMs = duration,
            availableSessions = summaries
        )
    }

    private fun findBestInstalledMusicApp(): String? {
        val candidates = listOf(
            "com.netease.cloudmusic",
            "com.spotify.music",
            "com.google.android.apps.youtube.music",
            "com.skysoft.kkbox.android",
            "com.apple.android.music",
            "org.videolan.vlc",
            "com.google.android.youtube"
        )
        return candidates.firstOrNull { isPackageInstalled(it) }
    }

    private fun isPackageInstalled(pkg: String): Boolean {
        return runCatching {
            context.packageManager.getLaunchIntentForPackage(pkg) != null
        }.getOrDefault(false)
    }

    private fun findFavoriteCustomAction(pb: PlaybackState?): PlaybackState.CustomAction? {
        val keywords = listOf(
            "like", "fav", "heart", "love", "thumb_up", "thumbs_up",
            "喜歡", "紅心", "愛心", "收藏", "讚"
        )
        return pb?.customActions?.firstOrNull { act ->
            val key = "${act.action} ${act.name}".lowercase()
            keywords.any { key.contains(it) } && !key.contains("thumb_down") && !key.contains("dislike")
        }
    }

    private fun detectFavoriteCapability(
        controller: MediaController,
        pkg: String,
        meta: MediaMetadata?,
        pb: PlaybackState?
    ): Pair<Boolean, Boolean> {
        val customAct = findFavoriteCustomAction(pb)
        if (customAct != null) {
            val label = "${customAct.action} ${customAct.name}".lowercase()
            val liked = label.contains("unlike") || label.contains("unfav") ||
                label.contains("remove") || label.contains("已收藏") || label.contains("取消")
            return true to liked
        }

        val listener = BoxNotificationListenerService.instance
        if (listener != null && listener.hasFavoriteNotificationAction(pkg)) {
            return true to listener.isFavoriteMarkedInNotification(pkg)
        }

        val ratingType = controller.ratingType
        if (ratingType == Rating.RATING_HEART || ratingType == Rating.RATING_THUMB_UP_DOWN) {
            val userRating = meta?.getRating(MediaMetadata.METADATA_KEY_USER_RATING)
            val liked = when (ratingType) {
                Rating.RATING_HEART -> userRating?.hasHeart() == true
                Rating.RATING_THUMB_UP_DOWN -> userRating?.isThumbUp == true
                else -> false
            }
            return true to liked
        }

        return false to false
    }

    fun toggleFavoriteOrLike(): Pair<Boolean, String> {
        val controller = selectedController ?: return false to "請先點擊 ▶ 開始播放音樂後再收藏"
        val pkg = controller.packageName
        val appLabel = resolveAppLabel(pkg)
        val pb = controller.playbackState
        val meta = controller.metadata

        val customAct = findFavoriteCustomAction(pb)
        if (customAct != null) {
            val ok = runCatching {
                controller.transportControls.sendCustomAction(customAct.action, customAct.extras)
                true
            }.getOrDefault(false)
            if (ok) {
                val curFav = _mediaState.value.isFavorited
                _mediaState.value = _mediaState.value.copy(isFavorited = !curFav)
                return true to if (!curFav) "♥ 已收藏／點讚 ($appLabel)" else "♡ 已取消收藏 ($appLabel)"
            }
        }

        val listener = BoxNotificationListenerService.instance
        if (listener != null && listener.executeFavoriteNotificationAction(pkg)) {
            val curFav = _mediaState.value.isFavorited
            _mediaState.value = _mediaState.value.copy(isFavorited = !curFav)
            return true to "♥ 已觸發 $appLabel 紅心／喜歡"
        }

        val ratingType = controller.ratingType
        if (ratingType == Rating.RATING_HEART || ratingType == Rating.RATING_THUMB_UP_DOWN) {
            val userRating = meta?.getRating(MediaMetadata.METADATA_KEY_USER_RATING)
            val nextLiked = when (ratingType) {
                Rating.RATING_HEART -> userRating?.hasHeart() != true
                Rating.RATING_THUMB_UP_DOWN -> userRating?.isThumbUp != true
                else -> true
            }
            val newRating = if (ratingType == Rating.RATING_HEART) {
                Rating.newHeartRating(nextLiked)
            } else {
                Rating.newThumbRating(nextLiked)
            }
            val ok = runCatching {
                controller.transportControls.setRating(newRating)
                true
            }.getOrDefault(false)
            if (ok) {
                _mediaState.value = _mediaState.value.copy(isFavorited = nextLiked)
                return true to if (nextLiked) "♥ 已點讚 ($appLabel)" else "♡ 已取消讚 ($appLabel)"
            }
        }

        return false to "$appLabel 目前未開放背景紅心接口，可點上方開啟 App 收藏"
    }

    fun selectSession(packageName: String) {
        preferredPackage = packageName
        updateControllers(activeControllers)
    }

    /**
     * Plays/Pauses active media OR wakes up the last used music app automatically when no session is running!
     */
    fun togglePlayPause() {
        val controller = selectedController
        if (controller != null) {
            val isPlaying = controller.playbackState?.state == PlaybackState.STATE_PLAYING
            if (isPlaying) {
                controller.transportControls.pause()
            } else {
                controller.transportControls.play()
            }
            return
        }

        val targetPkg = _mediaState.value.packageName.takeIf { it.isNotBlank() }
            ?: preferences.config.value.lastMediaPackage.takeIf { it.isNotBlank() }
            ?: findBestInstalledMusicApp()

        if (targetPkg != null) {
            wakeAndPlayRememberedApp(targetPkg)
        } else {
            sendMediaKeyFallback(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
        }
    }

    private fun wakeAndPlayRememberedApp(targetPkg: String) {
        scope.launch {
            // 1. Dispatch targeted MediaButton PLAY broadcast directly to the remembered music app
            dispatchTargetedMediaButton(targetPkg, KeyEvent.KEYCODE_MEDIA_PLAY)
            sendMediaKeyFallback(KeyEvent.KEYCODE_MEDIA_PLAY)

            delay(650L)
            refreshSessions()
            val wokenController = selectedController
            if (wokenController != null) {
                if (wokenController.playbackState?.state != PlaybackState.STATE_PLAYING) {
                    wokenController.transportControls.play()
                }
                return@launch
            }

            // 2. Cold-start the remembered music app & trigger play once its session binds!
            DxCarApp.instance?.installedAppsRepository?.launchAppFullscreen(targetPkg)
            delay(1_300L)
            refreshSessions()
            val afterLaunch = selectedController
            if (afterLaunch != null) {
                afterLaunch.transportControls.play()
            } else {
                dispatchTargetedMediaButton(targetPkg, KeyEvent.KEYCODE_MEDIA_PLAY)
                sendMediaKeyFallback(KeyEvent.KEYCODE_MEDIA_PLAY)
            }
        }
    }

    private fun dispatchTargetedMediaButton(pkg: String, keyCode: Int) {
        runCatching {
            val now = SystemClock.uptimeMillis()
            val down = Intent(Intent.ACTION_MEDIA_BUTTON).apply {
                setPackage(pkg)
                putExtra(Intent.EXTRA_KEY_EVENT, KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0))
            }
            val up = Intent(Intent.ACTION_MEDIA_BUTTON).apply {
                setPackage(pkg)
                putExtra(Intent.EXTRA_KEY_EVENT, KeyEvent(now, now, KeyEvent.ACTION_UP, keyCode, 0))
            }
            context.sendBroadcast(down)
            context.sendBroadcast(up)
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

    private fun resolveAppIcon(pkg: String): Bitmap? {
        appIconCache[pkg]?.let { return it }
        val bmp = runCatching {
            val drawable = context.packageManager.getApplicationIcon(pkg)
            drawableToBitmap(drawable, 96)
        }.getOrNull()
        if (bmp != null) appIconCache[pkg] = bmp
        return bmp
    }

    private fun drawableToBitmap(drawable: Drawable, sizePx: Int): Bitmap {
        if (drawable is BitmapDrawable && drawable.bitmap != null) {
            return Bitmap.createScaledBitmap(drawable.bitmap, sizePx, sizePx, true)
        }
        val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bmp
    }
}

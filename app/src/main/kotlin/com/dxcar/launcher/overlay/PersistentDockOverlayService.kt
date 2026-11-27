package com.dxcar.launcher.overlay

import androidx.compose.animation.core.animateFloat

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.media.MediaMetadata
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.dxcar.launcher.DxCarApp
import com.dxcar.launcher.MainActivity
import com.dxcar.launcher.R
import com.dxcar.launcher.accessibility.DxCarAccessibilityService
import com.dxcar.launcher.comms.CallActionCommand
import com.dxcar.launcher.comms.CallStatus
import com.dxcar.launcher.comms.RemoteTouchpadController
import com.dxcar.launcher.data.DeviceFormFactor
import com.dxcar.launcher.data.DeviceRole
import com.dxcar.launcher.data.HomeExperimentMode
import com.dxcar.launcher.layout.CoolwalkLayoutEngine
import com.dxcar.launcher.layout.ResolvedDockPlacement
import com.dxcar.launcher.layout.ScreenMetrics
import com.dxcar.launcher.system.SystemStatusMonitor
import com.dxcar.launcher.theme.DayNightEngine
import com.dxcar.launcher.theme.DxCarTheme
import com.dxcar.launcher.theme.LocalDxCarColors
import com.dxcar.launcher.ui.CoolwalkDock
import com.dxcar.launcher.ui.IncomingCallBanner
import com.dxcar.launcher.ui.IncomingMessageBanner
import com.dxcar.launcher.ui.MediaControlCard
import com.dxcar.launcher.ui.bouncyClickable
import com.dxcar.launcher.ui.buildRecentDockSlots
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * System-wide TYPE_APPLICATION_OVERLAY Service powering:
 * 1. Persistent AA Dock (Left / Right / Top / Bottom) over fullscreen apps AND other car launchers
 *    (explicitly blocked on smartphone screens per user preference).
 * 2. Interactive 3D Mouse-Like Pointer Overlay (single-touch drag FX + 2s hold -> zoom / 3D tilt).
 * 3. Car-Side Remote Mouse Pointer controlled live from the Smartphone Companion Touchpad.
 * 4. Global AA Incoming Call & Message Banners + Plan C Music Side-Panel.
 */
class PersistentDockOverlayService :
    LifecycleService(),
    SavedStateRegistryOwner,
    ViewModelStoreOwner {

    private val savedStateController = SavedStateRegistryController.create(this)
    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateController.savedStateRegistry
    override val viewModelStore: ViewModelStore = ViewModelStore()

    private lateinit var windowManager: WindowManager
    private var dockView: View? = null
    private var bannerView: ComposeView? = null
    private var planCPanelView: ComposeView? = null
    private var cursor3DView: View? = null
    private var remoteMouseView: ComposeView? = null
    private var ytHandoffView: ComposeView? = null
    private var planBSeamView: ComposeView? = null

    private var dockParams: WindowManager.LayoutParams? = null
    private var bannerParams: WindowManager.LayoutParams? = null
    private var planCPanelParams: WindowManager.LayoutParams? = null
    private var cursor3DParams: WindowManager.LayoutParams? = null
    private var remoteMouseParams: WindowManager.LayoutParams? = null
    private var ytHandoffParams: WindowManager.LayoutParams? = null
    private var planBSeamParams: WindowManager.LayoutParams? = null

    private val isCollapsed = MutableStateFlow(true)
    private val isPinnedOpen = MutableStateFlow(false)
    private val planCPanelMinimized = MutableStateFlow(false)
    private val interactionTick = MutableStateFlow(0L)
    private val metricsFlow = MutableStateFlow(ScreenMetrics(1280, 720, 240))
    private var lastHomeForeground = true

    @Volatile
    private var lastRawX = 0f
    @Volatile
    private var lastRawY = 0f

    @Volatile
    private var cursor3DRawX = 0f
    @Volatile
    private var cursor3DRawY = 0f
    private var cursor3DDragAnchorSet = false
    private var cursor3DDragStartRawX = 0f
    private var cursor3DDragStartRawY = 0f
    private var cursor3DDragStartWinX = 0
    private var cursor3DDragStartWinY = 0
    private var cursor3DPosInitialized = false
    private var cursor3DPosX = 0
    private var cursor3DPosY = 0

    override fun onCreate() {
        super.onCreate()
        savedStateController.performAttach()
        savedStateController.performRestore(null)

        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        updateMetrics()

        ensureChannel()
        startForegroundSafe()

        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return
        }

        val app = DxCarApp.instance ?: return
        if (app.preferences.config.value.deviceRole == DeviceRole.PHONE_COMPANION ||
            DeviceFormFactor.isCompactPhoneScreen(resources.configuration)
        ) {
            stopSelf()
            return
        }

        setupDockWindow(app)
        setupBannerWindow(app)
        setupPlanCPanelWindow(app)
        setupCursor3DWindow(app)
        setupRemoteMouseWindow(app)
        setupYoutubeHandoffWindow(app)
        setupPlanBSeamWindow(app)
        observeVisibilityAndLayout(app)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_STOP -> {
                planCPanelRequested.value = false
                Cursor3DStateHolder.setActive(false)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_SHOW_PLAN_C_PANEL -> {
                planCPanelMinimized.value = false
                planCPanelRequested.value = true
            }
            ACTION_HIDE_PLAN_C_PANEL -> {
                planCPanelRequested.value = false
            }
            ACTION_TOGGLE_3D_CURSOR -> {
                Cursor3DStateHolder.toggle()
            }
        }
        updateMetrics()
        return START_STICKY
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        updateMetrics()
    }

    override fun onDestroy() {
        super.onDestroy()
        removeOverlaySafe(dockView)
        removeOverlaySafe(bannerView)
        removeOverlaySafe(planCPanelView)
        removeOverlaySafe(cursor3DView)
        removeOverlaySafe(remoteMouseView)
        removeOverlaySafe(ytHandoffView)
        removeOverlaySafe(planBSeamView)
        dockView = null
        bannerView = null
        planCPanelView = null
        cursor3DView = null
        remoteMouseView = null
        ytHandoffView = null
        planBSeamView = null
        _is3DWindowAttached.value = false
        viewModelStore.clear()
    }

    private fun updateMetrics() {
        val dm = resources.displayMetrics
        metricsFlow.value = ScreenMetrics(
            widthPx = dm.widthPixels,
            heightPx = dm.heightPixels,
            densityDpi = dm.densityDpi
        )
    }

    private fun setupDockWindow(app: DxCarApp) {
        val composeView = ComposeView(this).apply {
            setContent {
                val config by app.preferences.config.collectAsState()
                val sysStatus by app.systemStatusMonitor.status.collectAsState()
                val apps by app.installedAppsRepository.apps.collectAsState()
                val liveRecent by app.installedAppsRepository.recentPackages.collectAsState()
                val activePkg by DxCarAccessibilityService.foregroundPackage.collectAsState()
                val messages by app.notificationCenter.messages.collectAsState()
                val collapsed by isCollapsed.collectAsState()
                val pinnedOpen by isPinnedOpen.collectAsState()
                val cursor3DOn by Cursor3DStateHolder.active.collectAsState()
                val tick by interactionTick.collectAsState()
                val metrics by metricsFlow.collectAsState()

                LaunchedEffect(collapsed, pinnedOpen, tick, config.overlayAutoCollapseEnabled, config.overlayAutoRetractMs) {
                    if (!collapsed &&
                        config.overlayAutoCollapseEnabled &&
                        !pinnedOpen &&
                        config.overlayAutoRetractMs > 0L
                    ) {
                        delay(config.overlayAutoRetractMs)
                        isCollapsed.value = true
                    }
                }

                val isNight = DayNightEngine.isNightNow(
                    config = config,
                    now = sysStatus.now,
                    systemIsDark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
                )
                val spec = CoolwalkLayoutEngine.compute(
                    metrics = metrics,
                    dockMode = config.dockPositionMode,
                    rightHandDrive = config.rightHandDrive,
                    customPrimaryRatio = config.primarySplitRatio
                )
                val slots = remember(liveRecent, config.recentDockPackages, config.pinnedDockPackages, apps, activePkg) {
                    buildRecentDockSlots(
                        recentPackages = (liveRecent + config.recentDockPackages).distinct(),
                        fallbackPackages = config.pinnedDockPackages,
                        installedApps = apps,
                        activePackage = activePkg
                    )
                }
                val unreadCount = remember(messages) { messages.count { !it.isRead } }

                DxCarTheme(
                    isNight = isNight,
                    accentIndex = config.accentColorIndex,
                    stylePreset = config.stylePreset
                ) {
                    val palette = LocalDxCarColors.current
                    if (collapsed) {
                        val handleThicknessDp = config.overlayHandleThicknessDp.coerceIn(16, 48)
                        val handleLengthDp = 120
                        val handleShape = when (spec.dockPlacement) {
                            ResolvedDockPlacement.LEFT -> RoundedCornerShape(topEnd = 14.dp, bottomEnd = 14.dp)
                            ResolvedDockPlacement.RIGHT -> RoundedCornerShape(topStart = 14.dp, bottomStart = 14.dp)
                            ResolvedDockPlacement.TOP -> RoundedCornerShape(bottomStart = 14.dp, bottomEnd = 14.dp)
                            ResolvedDockPlacement.BOTTOM -> RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp)
                        }
                        val isHorizontalEdge = spec.dockPlacement.isHorizontalBar
                        val haptic = LocalHapticFeedback.current
                        var isDraggingPosition by remember { mutableStateOf(false) }

                        val handleGestureModifier = Modifier.pointerInput(
                            spec.dockPlacement,
                            metrics,
                            config.overlayHandlePositionPercent
                        ) {
                            val handleLenPx = handleLengthDp * metrics.density
                            val maxTravelPx = ((if (isHorizontalEdge) metrics.widthPx else metrics.heightPx) - handleLenPx)
                                .coerceAtLeast(1f)
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                val downTime = System.currentTimeMillis()
                                val downRawX = lastRawX
                                val downRawY = lastRawY
                                val startPercent = config.overlayHandlePositionPercent.coerceIn(0.05f, 0.95f)
                                var draggingPos = false
                                var expandedBySwipe = false
                                var movedSignificant = false
                                var latestPercent = startPercent

                                while (true) {
                                    val event = awaitPointerEvent(PointerEventPass.Main)
                                    val change = event.changes.firstOrNull { it.id == down.id } ?: event.changes.firstOrNull()
                                    if (change == null) break

                                    val curRawX = lastRawX
                                    val curRawY = lastRawY
                                    val dx = curRawX - downRawX
                                    val dy = curRawY - downRawY
                                    val elapsed = System.currentTimeMillis() - downTime

                                    if (!draggingPos && !expandedBySwipe) {
                                        val inwardTrigger = when (spec.dockPlacement) {
                                            ResolvedDockPlacement.LEFT -> dx > 22f && abs(dx) > abs(dy)
                                            ResolvedDockPlacement.RIGHT -> dx < -22f && abs(dx) > abs(dy)
                                            ResolvedDockPlacement.TOP -> dy > 22f && abs(dy) > abs(dx)
                                            ResolvedDockPlacement.BOTTOM -> dy < -22f && abs(dy) > abs(dx)
                                        }
                                        if (inwardTrigger) {
                                            expandedBySwipe = true
                                            isCollapsed.value = false
                                            interactionTick.value++
                                            change.consume()
                                            break
                                        }
                                        if (elapsed >= 340L || abs(if (isHorizontalEdge) dx else dy) > 24f) {
                                            draggingPos = true
                                            isDraggingPosition = true
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        }
                                    }

                                    if (draggingPos) {
                                        movedSignificant = true
                                        val axisDelta = if (isHorizontalEdge) dx else dy
                                        latestPercent = (startPercent + axisDelta / maxTravelPx).coerceIn(0.05f, 0.95f)
                                        updateCollapsedHandlePosition(spec.dockPlacement, metrics, handleLengthDp, latestPercent)
                                        change.consume()
                                    }

                                    if (change.changedToUpIgnoreConsumed()) {
                                        change.consume()
                                        break
                                    }
                                }

                                isDraggingPosition = false
                                if (draggingPos && movedSignificant) {
                                    app.preferences.update { it.copy(overlayHandlePositionPercent = latestPercent) }
                                } else if (!expandedBySwipe && !draggingPos) {
                                    isCollapsed.value = false
                                    interactionTick.value++
                                }
                            }
                        }

                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .then(
                                    if (isHorizontalEdge) Modifier.width(handleLengthDp.dp).height(handleThicknessDp.dp)
                                    else Modifier.width(handleThicknessDp.dp).height(handleLengthDp.dp)
                                )
                                .clip(handleShape)
                                .background(
                                    if (isDraggingPosition) palette.accent.copy(alpha = 0.35f)
                                    else palette.glassOverlay.copy(alpha = 0.78f)
                                )
                                .border(
                                    width = if (isDraggingPosition) 2.dp else 1.2.dp,
                                    color = palette.accent.copy(alpha = if (isDraggingPosition) 0.95f else 0.65f),
                                    shape = handleShape
                                )
                                .then(handleGestureModifier)
                        ) {
                            Box(
                                modifier = Modifier
                                    .then(
                                        if (isHorizontalEdge) Modifier.width(48.dp).height((handleThicknessDp * 0.22f).coerceIn(4f, 8f).dp)
                                        else Modifier.width((handleThicknessDp * 0.22f).coerceIn(4f, 8f).dp).height(48.dp)
                                    )
                                    .clip(CircleShape)
                                    .background(palette.accent.copy(alpha = 0.92f))
                            )
                        }
                    } else {
                        Box(
                            modifier = Modifier.pointerInput(Unit) {
                                awaitPointerEventScope {
                                    while (true) {
                                        awaitPointerEvent(PointerEventPass.Initial)
                                        interactionTick.value++
                                    }
                                }
                            }
                        ) {
                            CoolwalkDock(
                                placement = spec.dockPlacement,
                                thicknessDp = spec.dockThicknessDp,
                                systemStatus = sysStatus,
                                is24HourClock = config.is24HourClock,
                                isAppDrawerOpen = false,
                                isDashboardActive = false,
                                unreadCount = unreadCount,
                                pinnedSlots = slots,
                                rightHandDrive = config.rightHandDrive,
                                isOverlayMode = true,
                                isOverlayPinnedOpen = pinnedOpen,
                                is3DCursorActive = cursor3DOn,
                                onToggleAppDrawer = { bringDxCarToFront(MainActivity.EXTRA_OPEN_DRAWER) },
                                onGoDashboard = {
                                    if (config.overlayAutoCollapseEnabled && !pinnedOpen) {
                                        isCollapsed.value = true
                                    }
                                    bringDxCarToFront(MainActivity.EXTRA_GO_DASHBOARD)
                                },
                                onSlotClick = { slot ->
                                    when {
                                        slot.packageName == "ACTION_DIAL" -> app.installedAppsRepository.launchDialer()
                                        !slot.packageName.isNullOrBlank() -> {
                                            val ok = app.installedAppsRepository.launchAppSmart(
                                                packageName = slot.packageName,
                                                avoidDockFreeform = config.launchAppsAvoidDockFreeform,
                                                metrics = metrics,
                                                spec = spec
                                            )
                                            if (!ok && slot.id == "youtube") {
                                                bringDxCarToFront(MainActivity.EXTRA_OPEN_YOUTUBE)
                                            }
                                        }
                                        slot.id == "youtube" -> {
                                            val yt = app.installedAppsRepository.findBestYoutubePackage()
                                            if (yt != null) {
                                                app.installedAppsRepository.launchAppSmart(
                                                    packageName = yt,
                                                    avoidDockFreeform = config.launchAppsAvoidDockFreeform,
                                                    metrics = metrics,
                                                    spec = spec
                                                )
                                            } else {
                                                bringDxCarToFront(MainActivity.EXTRA_OPEN_YOUTUBE)
                                            }
                                        }
                                        else -> bringDxCarToFront(MainActivity.EXTRA_OPEN_DRAWER)
                                    }
                                    if (config.overlayAutoCollapseEnabled && !pinnedOpen) {
                                        isCollapsed.value = true
                                    }
                                },
                                onWifiClick = {
                                    SystemStatusMonitor.openWifiSettings(this@PersistentDockOverlayService)
                                    if (config.overlayAutoCollapseEnabled && !pinnedOpen) isCollapsed.value = true
                                },
                                onWifiLongClick = {
                                    SystemStatusMonitor.openWifiSettings(this@PersistentDockOverlayService)
                                    if (config.overlayAutoCollapseEnabled && !pinnedOpen) isCollapsed.value = true
                                },
                                onBluetoothClick = {
                                    SystemStatusMonitor.openBluetoothSettings(this@PersistentDockOverlayService)
                                    if (config.overlayAutoCollapseEnabled && !pinnedOpen) isCollapsed.value = true
                                },
                                onClockClick = {
                                    DxCarAccessibilityService.instance?.openQuickSettings()
                                    if (config.overlayAutoCollapseEnabled && !pinnedOpen) isCollapsed.value = true
                                },
                                onNotificationsClick = { bringDxCarToFront(MainActivity.EXTRA_OPEN_NOTIFICATIONS) },
                                onBackClick = {
                                    interactionTick.value++
                                    val acc = DxCarAccessibilityService.instance
                                    if (acc != null) {
                                        acc.goBack()
                                    } else {
                                        android.widget.Toast.makeText(
                                            this@PersistentDockOverlayService,
                                            "請先至設定 → 權限需求開啟「DX-Car Launcher 無障礙服務」以使用系統返回",
                                            android.widget.Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                },
                                onRecentsClick = {
                                    interactionTick.value++
                                    val acc = DxCarAccessibilityService.instance
                                    if (acc != null) {
                                        acc.openRecents()
                                    } else {
                                        android.widget.Toast.makeText(
                                            this@PersistentDockOverlayService,
                                            "請先至設定 → 權限需求開啟「DX-Car Launcher 無障礙服務」以使用多工",
                                            android.widget.Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                },
                                onToggle3DCursor = {
                                    val nowOn = Cursor3DStateHolder.toggle()
                                    if (nowOn && config.overlayAutoCollapseEnabled && !pinnedOpen) {
                                        isCollapsed.value = true
                                    }
                                },
                                onVoiceNavClick = {
                                    if (config.overlayAutoCollapseEnabled && !pinnedOpen) {
                                        isCollapsed.value = true
                                    }
                                    bringDxCarToFront(MainActivity.EXTRA_START_VOICE_NAV)
                                },
                                onCollapseOverlay = {
                                    isPinnedOpen.value = false
                                    isCollapsed.value = true
                                },
                                onTogglePinOverlay = {
                                    isPinnedOpen.value = !isPinnedOpen.value
                                    interactionTick.value++
                                }
                            )
                        }
                    }
                }
            }
        }

        val container = object : FrameLayout(this) {
            override fun dispatchTouchEvent(ev: MotionEvent?): Boolean {
                if (ev != null) {
                    lastRawX = ev.rawX
                    lastRawY = ev.rawY
                }
                return super.dispatchTouchEvent(ev)
            }
        }.apply {
            setViewTreeLifecycleOwner(this@PersistentDockOverlayService)
            setViewTreeSavedStateRegistryOwner(this@PersistentDockOverlayService)
            setViewTreeViewModelStoreOwner(this@PersistentDockOverlayService)
            addView(composeView)
        }

        val params = createOverlayParams(
            width = WindowManager.LayoutParams.WRAP_CONTENT,
            height = WindowManager.LayoutParams.WRAP_CONTENT,
            gravity = Gravity.START or Gravity.TOP
        )
        dockView = container
        dockParams = params
        runCatching { windowManager.addView(container, params) }
    }

    private fun setupCursor3DWindow(app: DxCarApp) {
        val composeView = ComposeView(this).apply {
            setContent {
                val config by app.preferences.config.collectAsState()
                val sysStatus by app.systemStatusMonitor.status.collectAsState()
                val showTut by Cursor3DStateHolder.showTutorial.collectAsState()
                val isNight = DayNightEngine.isNightNow(
                    config = config,
                    now = sysStatus.now,
                    systemIsDark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
                )
                DxCarTheme(
                    isNight = isNight,
                    accentIndex = config.accentColorIndex,
                    stylePreset = config.stylePreset
                ) {
                    if (showTut) {
                        ExtraLargeTutorialModal(
                            title = com.dxcar.launcher.i18n.DxCarI18n.str(
                                "🧊 3D 雙指快捷懸浮條 · 操作教學",
                                "🧊 3D 双指快捷悬浮条 · 操作教程",
                                "🧊 Floating 3D Control Bar · Guide",
                                "🧊 3D 操作ツールバー · 操作ガイド"
                            ),
                            steps = tutorial3DControlSteps(),
                            highlightNote = com.dxcar.launcher.i18n.DxCarI18n.str(
                                "💡 內建地圖免無障礙即可直接調整 3D／縮放；若要在原版 Google 地圖等外部導航使用，請在設定最上方「權限需求」開啟【無障礙服務】。",
                                "💡 内置地图免无障碍即可直接调整 3D／缩放；若要在原版 Google 地图等外部导航使用，请在设置最上方「权限需求」开启【无障碍服务】。",
                                "💡 Works directly on the built-in map with no extra permission; enable [Accessibility Service] in Settings > Permissions to control external Google Maps.",
                                "💡 内蔵マップでは追加権限なしで即座に動作します。Google マップ等の外部ナビで使う場合は、設定上部の「権限設定」から【ユーザー補助サービス】を有効にしてください。"
                            ),
                            onClose = { Cursor3DStateHolder.closeTutorial() }
                        )
                    } else {
                        Floating3DControlBar(
                            onOpenTutorial = { Cursor3DStateHolder.openTutorial() },
                            onExit3DMode = { Cursor3DStateHolder.setActive(false) },
                            onDragDelta = { _, _ -> updateCursor3DDragPosition() },
                            resolveExternalGestureAnchor = { resolveSafeExternalGestureAnchor() }
                        )
                    }
                }
            }
        }

        val container = object : FrameLayout(this) {
            override fun dispatchTouchEvent(ev: MotionEvent?): Boolean {
                if (ev != null) {
                    cursor3DRawX = ev.rawX
                    cursor3DRawY = ev.rawY
                    when (ev.actionMasked) {
                        MotionEvent.ACTION_DOWN,
                        MotionEvent.ACTION_UP,
                        MotionEvent.ACTION_CANCEL -> {
                            cursor3DDragAnchorSet = false
                        }
                    }
                }
                return super.dispatchTouchEvent(ev)
            }
        }.apply {
            setViewTreeLifecycleOwner(this@PersistentDockOverlayService)
            setViewTreeSavedStateRegistryOwner(this@PersistentDockOverlayService)
            setViewTreeViewModelStoreOwner(this@PersistentDockOverlayService)
            addView(composeView)
        }

        val params = createOverlayParams(
            width = WindowManager.LayoutParams.WRAP_CONTENT,
            height = WindowManager.LayoutParams.WRAP_CONTENT,
            gravity = Gravity.TOP or Gravity.START
        )
        cursor3DView = container
        cursor3DParams = params
        container.visibility = View.GONE
        val added = runCatching { windowManager.addView(container, params) }.isSuccess
        _is3DWindowAttached.value = added
    }

    private fun updateCursor3DDragPosition() {
        val view = cursor3DView ?: return
        val params = cursor3DParams ?: return
        if (Cursor3DStateHolder.showTutorial.value) return
        val metrics = metricsFlow.value
        val estW = view.width.takeIf { it > 0 } ?: (290f * metrics.density).roundToInt()
        val estH = view.height.takeIf { it > 0 } ?: (104f * metrics.density).roundToInt()
        val maxX = (metrics.widthPx - estW).coerceAtLeast(0)
        val maxY = (metrics.heightPx - estH).coerceAtLeast(0)

        if (!cursor3DDragAnchorSet) {
            cursor3DDragStartRawX = cursor3DRawX
            cursor3DDragStartRawY = cursor3DRawY
            cursor3DDragStartWinX = params.x
            cursor3DDragStartWinY = params.y
            cursor3DDragAnchorSet = true
        }
        val nextX = (cursor3DDragStartWinX + (cursor3DRawX - cursor3DDragStartRawX)).roundToInt().coerceIn(0, maxX)
        val nextY = (cursor3DDragStartWinY + (cursor3DRawY - cursor3DDragStartRawY)).roundToInt().coerceIn(0, maxY)
        cursor3DPosInitialized = true
        cursor3DPosX = nextX
        cursor3DPosY = nextY
        params.x = nextX
        params.y = nextY
        runCatching { windowManager.updateViewLayout(view, params) }
    }

    private fun clampCursor3DPosition(metrics: ScreenMetrics): Pair<Int, Int> {
        val view = cursor3DView
        val estW = view?.width?.takeIf { it > 0 } ?: (290f * metrics.density).roundToInt()
        val estH = view?.height?.takeIf { it > 0 } ?: (104f * metrics.density).roundToInt()
        val maxX = (metrics.widthPx - estW).coerceAtLeast(0)
        val maxY = (metrics.heightPx - estH).coerceAtLeast(0)
        if (!cursor3DPosInitialized) {
            cursor3DPosX = ((metrics.widthPx - estW) / 2).coerceIn(0, maxX)
            cursor3DPosY = (20f * metrics.density).roundToInt().coerceIn(0, maxY)
            cursor3DPosInitialized = true
        } else {
            cursor3DPosX = cursor3DPosX.coerceIn(0, maxX)
            cursor3DPosY = cursor3DPosY.coerceIn(0, maxY)
        }
        return cursor3DPosX to cursor3DPosY
    }

    /**
     * Picks a screen-space target point for 2-finger Accessibility gestures that is GUARANTEED
     * to avoid the compact floating 3D bar's current window rectangle.
     */
    private fun resolveSafeExternalGestureAnchor(): Pair<Float, Float> {
        val metrics = metricsFlow.value
        val w = metrics.widthPx.toFloat().coerceAtLeast(400f)
        val h = metrics.heightPx.toFloat().coerceAtLeast(300f)
        val defaultX = w * 0.50f
        var targetY = h * 0.50f

        val view = cursor3DView
        val params = cursor3DParams
        if (view != null && params != null && view.visibility == View.VISIBLE) {
            val barTop = params.y.toFloat()
            val barBottom = barTop + (view.height.takeIf { it > 0 }?.toFloat() ?: (104f * metrics.density))
            val margin = 72f * metrics.density
            if (targetY in (barTop - margin)..(barBottom + margin)) {
                val barCenterY = (barTop + barBottom) * 0.5f
                targetY = if (barCenterY < h * 0.50f) {
                    (barBottom + margin).coerceAtLeast(h * 0.62f).coerceAtMost(h * 0.74f)
                } else {
                    (barTop - margin).coerceAtMost(h * 0.38f).coerceAtLeast(h * 0.28f)
                }
            }
        }
        return defaultX to targetY
    }

    private fun setupRemoteMouseWindow(app: DxCarApp) {
        val view = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@PersistentDockOverlayService)
            setViewTreeSavedStateRegistryOwner(this@PersistentDockOverlayService)
            setViewTreeViewModelStoreOwner(this@PersistentDockOverlayService)
            setContent {
                val config by app.preferences.config.collectAsState()
                val sysStatus by app.systemStatusMonitor.status.collectAsState()
                val cursor by RemoteTouchpadController.cursorState.collectAsState()
                val isNight = DayNightEngine.isNightNow(
                    config = config,
                    now = sysStatus.now,
                    systemIsDark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
                )
                DxCarTheme(
                    isNight = isNight,
                    accentIndex = config.accentColorIndex,
                    stylePreset = config.stylePreset
                ) {
                    RemoteCarMousePointerOverlay(cursorState = cursor)
                }
            }
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }
        remoteMouseView = view
        remoteMouseParams = params
        view.visibility = View.GONE
        runCatching { windowManager.addView(view, params) }
    }

    private fun updateCollapsedHandlePosition(
        placement: ResolvedDockPlacement,
        metrics: ScreenMetrics,
        handleLengthDp: Int,
        percent: Float
    ) {
        val view = dockView ?: return
        val params = dockParams ?: return
        val handleLenPx = (handleLengthDp * metrics.density).toInt()
        val p = percent.coerceIn(0.05f, 0.95f)
        when (placement) {
            ResolvedDockPlacement.TOP -> {
                val maxX = (metrics.widthPx - handleLenPx).coerceAtLeast(0)
                params.gravity = Gravity.TOP or Gravity.START
                params.x = (maxX * p).toInt()
                params.y = 0
            }
            ResolvedDockPlacement.BOTTOM -> {
                val maxX = (metrics.widthPx - handleLenPx).coerceAtLeast(0)
                params.gravity = Gravity.BOTTOM or Gravity.START
                params.x = (maxX * p).toInt()
                params.y = 0
            }
            ResolvedDockPlacement.RIGHT -> {
                val maxY = (metrics.heightPx - handleLenPx).coerceAtLeast(0)
                params.gravity = Gravity.END or Gravity.TOP
                params.x = 0
                params.y = (maxY * p).toInt()
            }
            ResolvedDockPlacement.LEFT -> {
                val maxY = (metrics.heightPx - handleLenPx).coerceAtLeast(0)
                params.gravity = Gravity.START or Gravity.TOP
                params.x = 0
                params.y = (maxY * p).toInt()
            }
        }
        runCatching { windowManager.updateViewLayout(view, params) }
    }

    private fun setupBannerWindow(app: DxCarApp) {
        val view = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@PersistentDockOverlayService)
            setViewTreeSavedStateRegistryOwner(this@PersistentDockOverlayService)
            setViewTreeViewModelStoreOwner(this@PersistentDockOverlayService)
            setContent {
                val config by app.preferences.config.collectAsState()
                val sysStatus by app.systemStatusMonitor.status.collectAsState()
                val callState by app.notificationCenter.activeCall.collectAsState()
                val popupMessage by app.notificationCenter.popupMessage.collectAsState()

                val isNight = DayNightEngine.isNightNow(
                    config = config,
                    now = sysStatus.now,
                    systemIsDark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
                )

                DxCarTheme(
                    isNight = isNight,
                    accentIndex = config.accentColorIndex,
                    stylePreset = config.stylePreset
                ) {
                    val collapsed by isCollapsed.collectAsState()
                    val metrics by metricsFlow.collectAsState()
                    val spec = remember(metrics, config.dockPositionMode, config.rightHandDrive) {
                        CoolwalkLayoutEngine.compute(
                            metrics = metrics,
                            dockMode = config.dockPositionMode,
                            rightHandDrive = config.rightHandDrive,
                            customPrimaryRatio = config.primarySplitRatio
                        )
                    }
                    val startPad = if (!collapsed && spec.dockPlacement == ResolvedDockPlacement.LEFT) {
                        (spec.dockThicknessDp + 12).dp
                    } else {
                        16.dp
                    }
                    val topPad = if (!collapsed && spec.dockPlacement == ResolvedDockPlacement.TOP) {
                        (spec.dockThicknessDp + 10).dp
                    } else {
                        12.dp
                    }
                    val useCarBoxAudio by app.notificationCenter.useCarBoxAudio.collectAsState()
                    Column(
                        horizontalAlignment = Alignment.Start,
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.padding(top = topPad, start = startPad, end = 16.dp)
                    ) {
                        IncomingCallBanner(
                            callState = callState,
                            scale = config.notificationBannerScale,
                            useCarBoxAudio = useCarBoxAudio,
                            onToggleAudioRoute = { app.notificationCenter.toggleCallAudioRoute(this@PersistentDockOverlayService) },
                            onAnswer = { app.notificationCenter.performCallAction(CallActionCommand.ANSWER) },
                            onDecline = { app.notificationCenter.performCallAction(CallActionCommand.DECLINE) }
                        )
                        IncomingMessageBanner(
                            message = popupMessage,
                            quickReplies = config.quickReplies,
                            scale = config.notificationBannerScale,
                            onExpandQuickReplies = { app.notificationCenter.pausePopupDismiss() },
                            onReadAloud = { app.notificationCenter.readMessageAloud(it) },
                            onQuickReply = { item, reply -> app.notificationCenter.sendQuickReply(item, reply) },
                            onMarkRead = { app.notificationCenter.markMessageRead(it) },
                            onDismiss = { app.notificationCenter.dismissPopupMessage() }
                        )
                    }
                }
            }
        }

        val params = createOverlayParams(
            width = WindowManager.LayoutParams.WRAP_CONTENT,
            height = WindowManager.LayoutParams.WRAP_CONTENT,
            gravity = Gravity.TOP or Gravity.START
        )
        bannerView = view
        bannerParams = params
        runCatching { windowManager.addView(view, params) }
    }

    private fun setupPlanCPanelWindow(app: DxCarApp) {
        val view = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@PersistentDockOverlayService)
            setViewTreeSavedStateRegistryOwner(this@PersistentDockOverlayService)
            setViewTreeViewModelStoreOwner(this@PersistentDockOverlayService)
            setContent {
                val config by app.preferences.config.collectAsState()
                val sysStatus by app.systemStatusMonitor.status.collectAsState()
                val media by app.mediaControlBridge.mediaState.collectAsState()
                val weather by app.weatherRepository.weather.collectAsState()
                val installedApps by app.installedAppsRepository.apps.collectAsState()
                val minimized by planCPanelMinimized.collectAsState()

                val isNight = DayNightEngine.isNightNow(
                    config = config,
                    now = sysStatus.now,
                    systemIsDark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
                )

                DxCarTheme(
                    isNight = isNight,
                    accentIndex = config.accentColorIndex,
                    stylePreset = config.stylePreset
                ) {
                    val palette = LocalDxCarColors.current
                    if (minimized) {
                        Surface(
                            color = palette.glassOverlay,
                            shape = CircleShape,
                            shadowElevation = 10.dp,
                            modifier = Modifier
                                .padding(10.dp)
                                .size(50.dp)
                                .border(1.5.dp, palette.accent, CircleShape)
                                .bouncyClickable { planCPanelMinimized.value = false }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Rounded.MusicNote, contentDescription = "展開音樂控盤", tint = palette.accent, modifier = Modifier.size(24.dp))
                            }
                        }
                    } else {
                        Surface(
                            color = palette.cardSurface.copy(alpha = 0.96f),
                            shape = RoundedCornerShape(22.dp),
                            shadowElevation = 14.dp,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(6.dp)
                                .border(1.2.dp, palette.cardBorder, RoundedCornerShape(22.dp))
                        ) {
                            Column(modifier = Modifier.fillMaxSize()) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(palette.cardSurfaceElevated)
                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        color = palette.accent,
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.bouncyClickable {
                                            planCPanelRequested.value = false
                                            bringDxCarToFront(null)
                                        }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(Icons.Rounded.Home, contentDescription = null, tint = palette.onAccent, modifier = Modifier.size(14.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text("回首頁", color = palette.onAccent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }

                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Surface(
                                            color = palette.cardSurface,
                                            shape = RoundedCornerShape(10.dp),
                                            modifier = Modifier.bouncyClickable { planCPanelMinimized.value = true }
                                        ) {
                                            Text(
                                                text = "縮小",
                                                color = palette.secondaryText,
                                                fontSize = 11.sp,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                            )
                                        }
                                        Surface(
                                            color = palette.cardSurface,
                                            shape = CircleShape,
                                            modifier = Modifier
                                                .size(24.dp)
                                                .bouncyClickable { planCPanelRequested.value = false }
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(Icons.Rounded.Close, contentDescription = "關閉", tint = palette.secondaryText, modifier = Modifier.size(14.dp))
                                            }
                                        }
                                    }
                                }

                                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                                    MediaControlCard(
                                        media = media,
                                        weather = weather,
                                        isPrimary = false,
                                        pureMusicMode = true,
                                        showSwapButton = false,
                                        showSixShortcuts = config.showSixShortcutsInMediaPanel,
                                        customShortcutSlots = config.customShortcutSlots,
                                        splitShortcuts = config.splitShortcuts,
                                        installedApps = installedApps,
                                        onWeatherClick = { app.weatherRepository.openGoogleWeatherApp(this@PersistentDockOverlayService, config.weatherAppPackage) },
                                        onPlayPause = { app.mediaControlBridge.togglePlayPause() },
                                        onNext = { app.mediaControlBridge.skipNext() },
                                        onPrevious = { app.mediaControlBridge.skipPrevious() },
                                        onToggleFavorite = { app.mediaControlBridge.toggleFavoriteOrLike() },
                                        onSelectSession = { app.mediaControlBridge.selectSession(it) },
                                        onOpenMediaApp = {
                                            if (media.packageName.isNotBlank()) {
                                                app.installedAppsRepository.launchAppFullscreen(media.packageName)
                                            }
                                        },
                                        onCustomSlotLaunch = { token ->
                                            if (!token.startsWith("__")) {
                                                app.installedAppsRepository.launchAppFullscreen(token)
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        val params = createOverlayParams(
            width = 360,
            height = WindowManager.LayoutParams.MATCH_PARENT,
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
        )
        planCPanelView = view
        planCPanelParams = params
        view.visibility = View.GONE
        runCatching { windowManager.addView(view, params) }
    }

    private fun setupYoutubeHandoffWindow(app: DxCarApp) {
        val density = resources.displayMetrics.density
        val initCfg = app.preferences.config.value
        val params = createOverlayParams(
            width = WindowManager.LayoutParams.WRAP_CONTENT,
            height = WindowManager.LayoutParams.WRAP_CONTENT,
            gravity = Gravity.TOP or Gravity.START
        ).apply {
            x = (initCfg.ytHandoffPosX * density).toInt()
            y = (initCfg.ytHandoffPosY * density).toInt()
        }
        val view = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@PersistentDockOverlayService)
            setViewTreeSavedStateRegistryOwner(this@PersistentDockOverlayService)
            setViewTreeViewModelStoreOwner(this@PersistentDockOverlayService)
            setContent {
                val haptic = LocalHapticFeedback.current
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(androidx.compose.ui.graphics.Color(0xE6FF0000))
                        .border(1.2.dp, androidx.compose.ui.graphics.Color.White.copy(alpha = 0.65f), RoundedCornerShape(10.dp))
                        .pointerInput(Unit) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    val dm = resources.displayMetrics
                                    val maxX = (dm.widthPixels - (44f * density)).toInt().coerceAtLeast(0)
                                    val maxY = (dm.heightPixels - (44f * density)).toInt().coerceAtLeast(0)
                                    params.x = (params.x + dragAmount.x.toInt()).coerceIn(0, maxX)
                                    params.y = (params.y + dragAmount.y.toInt()).coerceIn(0, maxY)
                                    ytHandoffView?.let { v ->
                                        runCatching { windowManager.updateViewLayout(v, params) }
                                    }
                                },
                                onDragEnd = {
                                    val savedX = (params.x / density).toInt().coerceAtLeast(0)
                                    val savedY = (params.y / density).toInt().coerceAtLeast(0)
                                    app.preferences.update { cur ->
                                        cur.copy(ytHandoffPosX = savedX, ytHandoffPosY = savedY)
                                    }
                                }
                            )
                        }
                        .bouncyClickable { handoffFromYoutubeAppToPage2() }
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PlayArrow,
                        contentDescription = "Hand off to Page 2 YouTube",
                        tint = androidx.compose.ui.graphics.Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
        ytHandoffView = view
        ytHandoffParams = params
        view.visibility = View.GONE
        runCatching { windowManager.addView(view, params) }
    }

    private fun dispatchPlanBPageFromOverlay(app: DxCarApp, targetPage: Int) {
        val coerced = targetPage.coerceIn(0, 2)
        planBActivePage.value = coerced
        onPlanBPageSyncToActivity?.invoke(coerced)
        val cfg = app.preferences.config.value
        val metrics = metricsFlow.value
        val spec = CoolwalkLayoutEngine.compute(
            metrics = metrics,
            dockMode = cfg.dockPositionMode,
            rightHandDrive = cfg.rightHandDrive,
            customPrimaryRatio = cfg.primarySplitRatio
        )
        val dockPx = (spec.dockThicknessDp * metrics.density).toInt().coerceAtLeast(0)
        val helperCfg = com.dxcar.launcher.system.RootHelperBridge.readPlanBConfig(this)
        val ui1Ratio = if (helperCfg.planBEnabled) helperCfg.ui1SplitRatio else cfg.primarySplitRatio
        val ui2Ratio = if (helperCfg.planBEnabled) helperCfg.ui2SplitRatio else cfg.page2SplitRatio
        if (coerced == 0) {
            bringDxCarToFront(null)
        }
        com.dxcar.launcher.system.RootHelperBridge.applyPlanBPage(
            context = this,
            page = coerced,
            screenWidth = metrics.widthPx,
            screenHeight = metrics.heightPx,
            dockOffsetLeft = if (spec.dockPlacement == ResolvedDockPlacement.LEFT) dockPx else 0,
            dockOffsetRight = if (spec.dockPlacement == ResolvedDockPlacement.RIGHT) dockPx else 0,
            dockOffsetTop = if (spec.dockPlacement == ResolvedDockPlacement.TOP) dockPx else 0,
            dockOffsetBottom = if (spec.dockPlacement == ResolvedDockPlacement.BOTTOM) dockPx else 0,
            ui1Ratio = ui1Ratio,
            ui1Swapped = cfg.page1Swapped,
            ui2Ratio = ui2Ratio,
            ui2Swapped = cfg.page2Swapped
        )
    }

    private fun setupPlanBSeamWindow(app: DxCarApp) {
        val params = createOverlayParams(
            width = WindowManager.LayoutParams.WRAP_CONTENT,
            height = WindowManager.LayoutParams.WRAP_CONTENT,
            gravity = Gravity.TOP or Gravity.START
        )
        val view = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@PersistentDockOverlayService)
            setViewTreeSavedStateRegistryOwner(this@PersistentDockOverlayService)
            setViewTreeViewModelStoreOwner(this@PersistentDockOverlayService)
            setContent {
                val config by app.preferences.config.collectAsState()
                val activePage by planBActivePage.collectAsState()
                val metrics by metricsFlow.collectAsState()
                val haptic = LocalHapticFeedback.current
                var isRatioDragging by remember { mutableStateOf(false) }
                var liveRatio by remember(activePage, config.primarySplitRatio, config.page2SplitRatio) {
                    mutableStateOf(if (activePage == 1) config.primarySplitRatio else config.page2SplitRatio)
                }

                val pulseTrans = androidx.compose.animation.core.rememberInfiniteTransition(label = "planBSeamPulse")
                val pulseAlpha by pulseTrans.animateFloat(
                    initialValue = 0.55f,
                    targetValue = 1.0f,
                    animationSpec = androidx.compose.animation.core.infiniteRepeatable(
                        animation = androidx.compose.animation.core.tween(420),
                        repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
                    ),
                    label = "pulseAlpha"
                )
                val pillWidth by androidx.compose.animation.core.animateDpAsState(
                    targetValue = if (isRatioDragging) 64.dp else 34.dp,
                    animationSpec = androidx.compose.animation.core.tween(180),
                    label = "planBPillW"
                )

                val labelText = when {
                    isRatioDragging -> {
                        val l = (liveRatio * 100).toInt()
                        "◀ $l:${100 - l} ▶"
                    }
                    activePage == 1 -> "1/3"
                    else -> "2/3"
                }

                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .width(pillWidth)
                        .height(118.dp)
                        .pointerInput(activePage, config.page1Swapped, config.page2Swapped) {
                            detectTapGestures(
                                onDoubleTap = {
                                    if (activePage == 1) {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        app.preferences.update { it.copy(page1Swapped = !it.page1Swapped) }
                                        dispatchPlanBPageFromOverlay(app, 1)
                                    } else if (activePage == 2) {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        app.preferences.update { it.copy(page2Swapped = !it.page2Swapped) }
                                        dispatchPlanBPageFromOverlay(app, 2)
                                    }
                                }
                            )
                        }
                        .pointerInput(activePage, metrics.widthPx) {
                            if (activePage == 1 || activePage == 2) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        val helperCfg = com.dxcar.launcher.system.RootHelperBridge.readPlanBConfig(this@PersistentDockOverlayService)
                                        liveRatio = if (activePage == 1) {
                                            if (helperCfg.planBEnabled) helperCfg.ui1SplitRatio else config.primarySplitRatio
                                        } else {
                                            if (helperCfg.planBEnabled) helperCfg.ui2SplitRatio else config.page2SplitRatio
                                        }
                                        isRatioDragging = true
                                    },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        val availW = (metrics.widthPx * 0.90f).coerceAtLeast(480f)
                                        liveRatio = (liveRatio + (dragAmount.x / availW)).coerceIn(0.25f, 0.75f)
                                    },
                                    onDragEnd = {
                                        isRatioDragging = false
                                        val finalR = liveRatio
                                        if (activePage == 1) {
                                            app.preferences.update { it.copy(primarySplitRatio = finalR) }
                                            com.dxcar.launcher.system.RootHelperBridge.updateUi1Ratio(this@PersistentDockOverlayService, finalR)
                                            dispatchPlanBPageFromOverlay(app, 1)
                                        } else {
                                            app.preferences.update { it.copy(page2SplitRatio = finalR) }
                                            com.dxcar.launcher.system.RootHelperBridge.updateUi2Ratio(this@PersistentDockOverlayService, finalR)
                                            dispatchPlanBPageFromOverlay(app, 2)
                                        }
                                    },
                                    onDragCancel = {
                                        isRatioDragging = false
                                    }
                                )
                            }
                        }
                        .pointerInput(activePage) {
                            var accumDx = 0f
                            detectHorizontalDragGestures(
                                onDragStart = { accumDx = 0f },
                                onHorizontalDrag = { change, dragAmount ->
                                    if (!isRatioDragging) {
                                        change.consume()
                                        accumDx += dragAmount
                                    }
                                },
                                onDragEnd = {
                                    if (!isRatioDragging) {
                                        val thresholdPx = 42f * density
                                        when {
                                            accumDx > thresholdPx -> {
                                                val prev = if (activePage == 2) 1 else 0
                                                dispatchPlanBPageFromOverlay(app, prev)
                                            }
                                            accumDx < -thresholdPx -> {
                                                val next = if (activePage == 1) 2 else 0
                                                dispatchPlanBPageFromOverlay(app, next)
                                            }
                                        }
                                    }
                                    accumDx = 0f
                                }
                            )
                        }
                ) {
                    val borderColor = if (isRatioDragging) {
                        androidx.compose.ui.graphics.Color(0xFF38BDF8).copy(alpha = pulseAlpha)
                    } else {
                        androidx.compose.ui.graphics.Color(0x9964B5F6)
                    }
                    val bgColor = if (isRatioDragging) {
                        androidx.compose.ui.graphics.Color(0xEE0F172A)
                    } else {
                        androidx.compose.ui.graphics.Color(0xCC101828)
                    }
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(bgColor)
                            .border(if (isRatioDragging) 2.dp else 1.2.dp, borderColor, RoundedCornerShape(14.dp))
                            .padding(horizontal = if (isRatioDragging) 6.dp else 5.dp, vertical = 10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .width(if (isRatioDragging) 6.dp else 4.dp)
                                .height(if (isRatioDragging) 34.dp else 26.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isRatioDragging) androidx.compose.ui.graphics.Color(0xFF38BDF8).copy(alpha = pulseAlpha)
                                    else androidx.compose.ui.graphics.Color.White.copy(alpha = 0.85f)
                                )
                        )
                        Spacer(Modifier.height(5.dp))
                        Text(
                            text = labelText,
                            color = if (isRatioDragging) androidx.compose.ui.graphics.Color(0xFF7DD3FC) else androidx.compose.ui.graphics.Color.White,
                            fontSize = if (isRatioDragging) 10.sp else 9.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            }
        }
        planBSeamView = view
        planBSeamParams = params
        view.visibility = View.GONE
        runCatching { windowManager.addView(view, params) }
    }

    private fun isYoutubeAppInForeground(): Boolean {
        val acc = DxCarAccessibilityService.instance
        if (acc?.isPackageInForegroundWindow("com.google.android.youtube") == true) {
            return true
        }
        val fgAcc = DxCarAccessibilityService.foregroundPackage.value
        if (fgAcc.startsWith("com.google.android.youtube")) {
            return true
        }
        // Fallback via UsageStatsManager when Accessibility Service is not active or missed an event
        return runCatching {
            val usm = getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager ?: return@runCatching false
            val end = System.currentTimeMillis()
            val begin = end - 30_000L
            val events = usm.queryEvents(begin, end) ?: return@runCatching false
            val evt = UsageEvents.Event()
            var latestPkg = ""
            var latestTs = 0L
            while (events.hasNextEvent()) {
                events.getNextEvent(evt)
                @Suppress("DEPRECATION")
                val isResumed = evt.eventType == UsageEvents.Event.ACTIVITY_RESUMED ||
                    evt.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND
                if (isResumed && evt.timeStamp >= latestTs && !evt.packageName.isNullOrBlank()) {
                    latestTs = evt.timeStamp
                    latestPkg = evt.packageName
                }
            }
            latestPkg.startsWith("com.google.android.youtube")
        }.getOrDefault(false)
    }

    private fun handoffFromYoutubeAppToPage2() {
        val msm = getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager
        val comp = ComponentName(this, com.dxcar.launcher.comms.BoxNotificationListenerService::class.java)
        val controller = runCatching {
            msm?.getActiveSessions(comp)?.firstOrNull { it.packageName == "com.google.android.youtube" }
        }.getOrNull()

        val pb = controller?.playbackState
        val posMs = if (pb != null) {
            val base = pb.position.coerceAtLeast(0L)
            if (pb.state == PlaybackState.STATE_PLAYING) {
                base + ((SystemClock.elapsedRealtime() - pb.lastPositionUpdateTime) * pb.playbackSpeed).toLong().coerceAtLeast(0L)
            } else base
        } else 0L
        var posSec = (posMs / 1000L).toInt().coerceAtLeast(0)

        val meta = controller?.metadata
        val mediaUri = meta?.getString(MediaMetadata.METADATA_KEY_MEDIA_URI).orEmpty()
        val mediaId = meta?.getString(MediaMetadata.METADATA_KEY_MEDIA_ID).orEmpty()
        var title = meta?.getString(MediaMetadata.METADATA_KEY_TITLE).orEmpty()
        var artist = meta?.getString(MediaMetadata.METADATA_KEY_ARTIST).orEmpty()

        val (accTitle, accChannel, accSec) = DxCarAccessibilityService.instance?.extractYoutubePlayingInfo()
            ?: Triple(null, null, 0)
        if (title.isBlank() && !accTitle.isNullOrBlank()) title = accTitle
        if (artist.isBlank() && !accChannel.isNullOrBlank()) artist = accChannel
        if (posSec <= 0 && accSec > 0) posSec = accSec

        val rawCandidate = mediaUri.ifBlank { mediaId }
        val videoId = when {
            rawCandidate.matches(Regex("^[A-Za-z0-9_-]{11}$")) -> rawCandidate
            rawCandidate.contains("v=") -> android.net.Uri.parse(rawCandidate).getQueryParameter("v")
            rawCandidate.contains("youtu.be/") -> android.net.Uri.parse(rawCandidate).lastPathSegment
            else -> null
        }

        val isSearchFallback = videoId.isNullOrBlank() && title.isNotBlank()
        val targetUrl = when {
            !videoId.isNullOrBlank() ->
                if (posSec > 0) "https://m.youtube.com/watch?v=$videoId&t=${posSec}s"
                else "https://m.youtube.com/watch?v=$videoId"
            title.isNotBlank() -> {
                val q = java.net.URLEncoder.encode(listOf(title, artist).filter { it.isNotBlank() }.joinToString(" "), "UTF-8")
                "https://m.youtube.com/results?search_query=$q"
            }
            else -> "https://m.youtube.com"
        }

        runCatching { controller?.transportControls?.pause() }
        com.dxcar.launcher.ui.YoutubeHandoffBridge.requestHandoff(
            url = targetUrl,
            positionSec = posSec,
            enterFullscreen = true,
            autoClickFirstResult = isSearchFallback
        )
        bringDxCarToFront(MainActivity.EXTRA_OPEN_YOUTUBE_HANDOFF)
    }

    private fun observeVisibilityAndLayout(app: DxCarApp) {
        lifecycleScope.launch {
            combine(
                app.preferences.config,
                app.isLauncherForeground,
                isCollapsed,
                metricsFlow,
                DxCarAccessibilityService.foregroundPackage
            ) { config, isHomeFg, collapsed, metrics, fgPkg ->
                FiveState(config, isHomeFg, collapsed, metrics, fgPkg)
            }.collect { (config, isHomeFg, collapsed, metrics, fgPkg) ->
                val isPlanBMode = config.homeExperimentMode == HomeExperimentMode.PLAN_D_ROOT_RESERVED
                if (isPlanBMode) {
                    isPinnedOpen.value = true
                    isCollapsed.value = false
                } else if (lastHomeForeground && !isHomeFg) {
                    if (config.overlayAutoCollapseEnabled && !isPinnedOpen.value) {
                        isCollapsed.value = true
                    } else if (!config.overlayAutoCollapseEnabled) {
                        isCollapsed.value = false
                    }
                }
                lastHomeForeground = isHomeFg

                val isSensitiveDialogFg = fgPkg == "com.topjohnwu.magisk" ||
                    fgPkg.contains("packageinstaller", ignoreCase = true) ||
                    fgPkg.contains("permissioncontroller", ignoreCase = true)

                val isPhoneScreen = DeviceFormFactor.isCompactPhoneScreen(resources.configuration)
                val otherLauncherPkgs = app.installedAppsRepository.homeLaunchers.value
                    .filterNot { it.isSelf }
                    .map { it.packageName }
                    .toSet()
                val onAnotherLauncher = fgPkg.isNotBlank() && otherLauncherPkgs.contains(fgPkg)

                val showDock = config.deviceRole == DeviceRole.AI_BOX_LAUNCHER &&
                    !isPhoneScreen &&
                    !isSensitiveDialogFg &&
                    config.persistentOverlayDockEnabled &&
                    !isHomeFg &&
                    (config.overlayDockOnOtherLaunchers || !onAnotherLauncher)

                val view = dockView
                val params = dockParams
                if (view != null && params != null) {
                    view.visibility = if (showDock) View.VISIBLE else View.GONE
                    if (showDock) {
                        val spec = CoolwalkLayoutEngine.compute(
                            metrics = metrics,
                            dockMode = config.dockPositionMode,
                            rightHandDrive = config.rightHandDrive,
                            customPrimaryRatio = config.primarySplitRatio
                        )
                        if (collapsed) {
                            updateCollapsedHandlePosition(
                                placement = spec.dockPlacement,
                                metrics = metrics,
                                handleLengthDp = 120,
                                percent = config.overlayHandlePositionPercent
                            )
                            params.width = WindowManager.LayoutParams.WRAP_CONTENT
                            params.height = WindowManager.LayoutParams.WRAP_CONTENT
                            runCatching { windowManager.updateViewLayout(view, params) }
                        } else {
                            when (spec.dockPlacement) {
                                ResolvedDockPlacement.TOP -> {
                                    params.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                                    params.x = 0
                                    params.y = 0
                                    params.width = WindowManager.LayoutParams.MATCH_PARENT
                                    params.height = WindowManager.LayoutParams.WRAP_CONTENT
                                }
                                ResolvedDockPlacement.BOTTOM -> {
                                    params.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                                    params.x = 0
                                    params.y = 0
                                    params.width = WindowManager.LayoutParams.MATCH_PARENT
                                    params.height = WindowManager.LayoutParams.WRAP_CONTENT
                                }
                                ResolvedDockPlacement.RIGHT -> {
                                    params.gravity = Gravity.END or Gravity.CENTER_VERTICAL
                                    params.x = 0
                                    params.y = 0
                                    params.width = WindowManager.LayoutParams.WRAP_CONTENT
                                    params.height = WindowManager.LayoutParams.MATCH_PARENT
                                }
                                ResolvedDockPlacement.LEFT -> {
                                    params.gravity = Gravity.START or Gravity.CENTER_VERTICAL
                                    params.x = 0
                                    params.y = 0
                                    params.width = WindowManager.LayoutParams.WRAP_CONTENT
                                    params.height = WindowManager.LayoutParams.MATCH_PARENT
                                }
                            }
                            runCatching { windowManager.updateViewLayout(view, params) }
                        }
                    }
                }
            }
        }

        // Observe Non-Blocking 3D Control Bar visibility & responsive WRAP_CONTENT / Tutorial sizing
        lifecycleScope.launch {
            combine(
                Cursor3DStateHolder.active,
                Cursor3DStateHolder.showTutorial,
                metricsFlow
            ) { active, showTut, metrics ->
                Triple(active, showTut, metrics)
            }.collect { (active, showTut, metrics) ->
                val view = cursor3DView ?: return@collect
                val params = cursor3DParams ?: return@collect
                if (!active) {
                    view.visibility = View.GONE
                    return@collect
                }
                view.visibility = View.VISIBLE
                if (showTut) {
                    params.width = WindowManager.LayoutParams.MATCH_PARENT
                    params.height = WindowManager.LayoutParams.MATCH_PARENT
                    params.x = 0
                    params.y = 0
                } else {
                    val (safeX, safeY) = clampCursor3DPosition(metrics)
                    params.width = WindowManager.LayoutParams.WRAP_CONTENT
                    params.height = WindowManager.LayoutParams.WRAP_CONTENT
                    params.x = safeX
                    params.y = safeY
                }
                runCatching { windowManager.updateViewLayout(view, params) }
            }
        }

        // Observe Car-Side Remote Mouse Pointer visibility
        lifecycleScope.launch {
            RemoteTouchpadController.cursorState.collect { cursor ->
                remoteMouseView?.visibility = if (cursor.visible) View.VISIBLE else View.GONE
            }
        }

        // Observe YouTube App Foreground -> keep tiny top-left YouTube handoff button reliably visible
        lifecycleScope.launch {
            var lastShown = false
            while (true) {
                val cfg = app.preferences.config.value
                val isHomeFg = app.isLauncherForeground.value
                val curFg = DxCarAccessibilityService.foregroundPackage.value
                val sensitiveFg = curFg == "com.topjohnwu.magisk" ||
                    curFg.contains("packageinstaller", ignoreCase = true) ||
                    curFg.contains("permissioncontroller", ignoreCase = true)
                val showYtHandoff = !isHomeFg &&
                    !sensitiveFg &&
                    cfg.deviceRole == DeviceRole.AI_BOX_LAUNCHER &&
                    cfg.homeExperimentMode != HomeExperimentMode.PLAN_D_ROOT_RESERVED &&
                    (cfg.ytHandoffAlwaysVisible || isYoutubeAppInForeground())
                val view = ytHandoffView
                if (view != null) {
                    val targetVis = if (showYtHandoff) View.VISIBLE else View.GONE
                    if (view.visibility != targetVis) {
                        view.visibility = targetVis
                    }
                    if (showYtHandoff && !lastShown) {
                        ytHandoffParams?.let { p ->
                            runCatching { windowManager.updateViewLayout(view, p) }
                        }
                    }
                    lastShown = showYtHandoff
                }
                delay(650L)
            }
        }

        lifecycleScope.launch {
            combine(
                app.preferences.config,
                app.isLauncherForeground,
                app.notificationCenter.activeCall,
                app.notificationCenter.popupMessage
            ) { config, isHomeFg, call, msg ->
                config.deviceRole == DeviceRole.AI_BOX_LAUNCHER &&
                    config.globalCallMessageBannerEnabled &&
                    !isHomeFg &&
                    (call.status != CallStatus.IDLE || msg != null)
            }.collect { showBanner ->
                bannerView?.visibility = if (showBanner) View.VISIBLE else View.GONE
            }
        }

        lifecycleScope.launch {
            combine(
                app.preferences.config,
                app.isLauncherForeground,
                planCPanelRequested,
                planCPanelMinimized,
                combine(metricsFlow, planBActivePage, DxCarAccessibilityService.foregroundPackage) { m, p, f -> Triple(m, p, f) }
            ) { config, isHomeFg, requested, minimized, mpf ->
                FiveState(config, isHomeFg, requested, minimized, mpf)
            }.collect { (config, isHomeFg, requested, minimized, mpf) ->
                val (metrics, activePlanBPage, fgPkg) = mpf
                val sensitiveFg = fgPkg == "com.topjohnwu.magisk" ||
                    fgPkg.contains("packageinstaller", ignoreCase = true) ||
                    fgPkg.contains("permissioncontroller", ignoreCase = true)
                val isPlanB = config.homeExperimentMode == HomeExperimentMode.PLAN_D_ROOT_RESERVED
                val showPlanCPanel = !isHomeFg &&
                    !sensitiveFg &&
                    config.deviceRole == DeviceRole.AI_BOX_LAUNCHER &&
                    requested &&
                    config.homeExperimentMode == HomeExperimentMode.PLAN_C_APP_AND_MEDIA &&
                    config.planCOverlayMusicPanelEnabled

                val spec = CoolwalkLayoutEngine.compute(
                    metrics = metrics,
                    dockMode = config.dockPositionMode,
                    rightHandDrive = config.rightHandDrive,
                    customPrimaryRatio = config.primarySplitRatio
                )
                val dockPx = if (!spec.dockPlacement.isHorizontalBar) {
                    (spec.dockThicknessDp * metrics.density).toInt()
                } else 0
                val availW = (metrics.widthPx - dockPx).coerceAtLeast(480)

                val view = planCPanelView
                val params = planCPanelParams
                if (view != null && params != null) {
                    view.visibility = if (showPlanCPanel) View.VISIBLE else View.GONE
                    if (showPlanCPanel) {
                        val panelW = (availW * (1f - spec.primaryWeight)).toInt().coerceIn(260, (availW * 0.45f).toInt())

                        params.gravity = if (config.rightHandDrive) {
                            Gravity.START or Gravity.CENTER_VERTICAL
                        } else {
                            Gravity.END or Gravity.CENTER_VERTICAL
                        }
                        if (minimized && !isPlanB) {
                            params.width = WindowManager.LayoutParams.WRAP_CONTENT
                            params.height = WindowManager.LayoutParams.WRAP_CONTENT
                        } else {
                            params.width = panelW
                            params.height = WindowManager.LayoutParams.MATCH_PARENT
                        }
                        runCatching { windowManager.updateViewLayout(view, params) }
                    }
                }

                // Position Plan B Seam Handle on UI 1 and UI 2
                val seamV = planBSeamView
                val seamP = planBSeamParams
                val showSeam = isPlanB && !isHomeFg && !sensitiveFg && (activePlanBPage == 1 || activePlanBPage == 2)
                if (seamV != null && seamP != null) {
                    seamV.visibility = if (showSeam) View.VISIBLE else View.GONE
                    if (showSeam) {
                        val helperCfg = com.dxcar.launcher.system.RootHelperBridge.readPlanBConfig(this@PersistentDockOverlayService)
                        val ratio = if (activePlanBPage == 1) {
                            if (helperCfg.planBEnabled) helperCfg.ui1SplitRatio else config.primarySplitRatio
                        } else {
                            if (helperCfg.planBEnabled) helperCfg.ui2SplitRatio else config.page2SplitRatio
                        }
                        val leftOffset = if (spec.dockPlacement == ResolvedDockPlacement.LEFT) dockPx else 0
                        val splitX = leftOffset + (availW * ratio).toInt()
                        seamP.gravity = Gravity.TOP or Gravity.START
                        seamP.x = (splitX - (17f * metrics.density).toInt()).coerceAtLeast(0)
                        seamP.y = ((metrics.heightPx - (110f * metrics.density).toInt()) / 2).coerceAtLeast(0)
                        runCatching { windowManager.updateViewLayout(seamV, seamP) }
                    }
                }
            }
        }
    }

    private fun bringDxCarToFront(extraAction: String?) {
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            if (extraAction != null) putExtra(MainActivity.EXTRA_ACTION, extraAction)
        }
        runCatching { startActivity(intent) }
    }

    private fun createOverlayParams(width: Int, height: Int, gravity: Int): WindowManager.LayoutParams {
        return WindowManager.LayoutParams(
            width,
            height,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            this.gravity = gravity
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
    }

    private fun removeOverlaySafe(view: View?) {
        if (view != null) {
            runCatching { windowManager.removeViewImmediate(view) }
        }
    }

    private fun ensureChannel() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "DX-Car Launcher 常駐懸浮 Dock",
                NotificationManager.IMPORTANCE_LOW
            )
        )
    }

    private fun startForegroundSafe() {
        val pi = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notif: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("DX-Car Launcher 常駐懸浮與遠端觸控運作中")
            .setContentText("支援跨啟動器常駐 Dock、3D 雙指懸浮條與來電訊息橫幅")
            .setContentIntent(pi)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            runCatching { startForeground(NOTIF_ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE) }
                .onFailure { startForeground(NOTIF_ID, notif) }
        } else {
            startForeground(NOTIF_ID, notif)
        }
    }

    private data class FiveState<A, B, C, D, E>(
        val first: A,
        val second: B,
        val third: C,
        val fourth: D,
        val fifth: E
    )

    companion object {
        private const val CHANNEL_ID = "dxcar_persistent_dock"
        private const val NOTIF_ID = 2001
        const val ACTION_STOP = "com.dxcar.launcher.overlay.STOP"
        const val ACTION_SHOW_PLAN_C_PANEL = "com.dxcar.launcher.overlay.SHOW_PLAN_C"
        const val ACTION_HIDE_PLAN_C_PANEL = "com.dxcar.launcher.overlay.HIDE_PLAN_C"
        const val ACTION_TOGGLE_3D_CURSOR = "com.dxcar.launcher.overlay.TOGGLE_3D_CURSOR"

        private val planCPanelRequested = MutableStateFlow(false)
        private val _is3DWindowAttached = MutableStateFlow(false)
        val is3DWindowAttached: kotlinx.coroutines.flow.StateFlow<Boolean> = _is3DWindowAttached
        val planBActivePage = MutableStateFlow(1)
        var onPlanBPageSyncToActivity: ((Int) -> Unit)? = null

        fun start(context: Context) {
            if (!Settings.canDrawOverlays(context)) return
            val intent = Intent(context, PersistentDockOverlayService::class.java)
            runCatching { ContextCompat.startForegroundService(context, intent) }
        }

        fun toggle3DCursor(context: Context): Boolean {
            if (!Settings.canDrawOverlays(context)) return false
            val intent = Intent(context, PersistentDockOverlayService::class.java).apply {
                action = ACTION_TOGGLE_3D_CURSOR
            }
            runCatching { ContextCompat.startForegroundService(context, intent) }
            return true
        }

        fun showPlanCMusicPanel(context: Context) {
            if (!Settings.canDrawOverlays(context)) return
            planCPanelRequested.value = true
            val intent = Intent(context, PersistentDockOverlayService::class.java).apply {
                action = ACTION_SHOW_PLAN_C_PANEL
            }
            runCatching { ContextCompat.startForegroundService(context, intent) }
        }

        fun hidePlanCMusicPanel() {
            planCPanelRequested.value = false
        }

        fun stop(context: Context) {
            planCPanelRequested.value = false
            Cursor3DStateHolder.setActive(false)
            runCatching { context.stopService(Intent(context, PersistentDockOverlayService::class.java)) }
        }
    }
}

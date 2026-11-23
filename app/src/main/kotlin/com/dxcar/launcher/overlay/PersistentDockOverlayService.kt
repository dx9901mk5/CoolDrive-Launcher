package com.dxcar.launcher.overlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.unit.dp
import kotlin.math.abs
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
import com.dxcar.launcher.data.DeviceRole
import com.dxcar.launcher.layout.CoolwalkLayoutEngine
import com.dxcar.launcher.layout.ResolvedDockPlacement
import com.dxcar.launcher.layout.ScreenMetrics
import com.dxcar.launcher.system.SystemStatusMonitor
import com.dxcar.launcher.theme.DxCarTheme
import com.dxcar.launcher.theme.DayNightEngine
import com.dxcar.launcher.theme.LocalDxCarColors
import com.dxcar.launcher.ui.CoolwalkDock
import com.dxcar.launcher.ui.IncomingCallBanner
import com.dxcar.launcher.ui.IncomingMessageBanner
import com.dxcar.launcher.ui.buildRecentDockSlots
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * System-wide TYPE_APPLICATION_OVERLAY Service keeping:
 * 1. Persistent AA Dock visible over ALL full-screen apps (YouTube, Google Maps, Spotify, etc.),
 *    auto-positioned Left / Right / Bottom by Google Coolwalk DPI rules, and automatically
 *    hidden whenever DX-Car Launcher Home itself is in the foreground.
 * 2. Global AA Incoming Call & Message Popup Banners visible over every app!
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
    private var dockParams: WindowManager.LayoutParams? = null
    private var bannerParams: WindowManager.LayoutParams? = null

    private val isCollapsed = MutableStateFlow(true)
    private val isPinnedOpen = MutableStateFlow(false)
    private val interactionTick = MutableStateFlow(0L)
    private val metricsFlow = MutableStateFlow(ScreenMetrics(1280, 720, 240))
    private var lastHomeForeground = true
    @Volatile
    private var lastRawX = 0f
    @Volatile
    private var lastRawY = 0f

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
        setupDockWindow(app)
        setupBannerWindow(app)
        observeVisibilityAndLayout(app)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
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
        dockView = null
        bannerView = null
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
                val tick by interactionTick.collectAsState()
                val metrics by metricsFlow.collectAsState()

                // Auto-retract back to edge handle after idle seconds (when auto-collapse is ON & not pinned)
                LaunchedEffect(collapsed, pinnedOpen, tick, config.overlayAutoCollapseEnabled, config.overlayAutoRetractSeconds) {
                    if (!collapsed &&
                        config.overlayAutoCollapseEnabled &&
                        !pinnedOpen &&
                        config.overlayAutoRetractSeconds > 0
                    ) {
                        delay(config.overlayAutoRetractSeconds * 1_000L)
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
                        // Adjustable thickness translucent edge handle (default 26dp, 16-48dp) + Long-press drag to reposition!
                        val handleThicknessDp = config.overlayHandleThicknessDp.coerceIn(16, 48)
                        val handleLengthDp = 120
                        val handleShape = when (spec.dockPlacement) {
                            ResolvedDockPlacement.LEFT -> RoundedCornerShape(topEnd = 14.dp, bottomEnd = 14.dp)
                            ResolvedDockPlacement.RIGHT -> RoundedCornerShape(topStart = 14.dp, bottomStart = 14.dp)
                            ResolvedDockPlacement.BOTTOM -> RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp)
                        }
                        val isBottom = spec.dockPlacement == ResolvedDockPlacement.BOTTOM
                        val haptic = LocalHapticFeedback.current
                        var isDraggingPosition by remember { mutableStateOf(false) }

                        val handleGestureModifier = Modifier.pointerInput(
                            spec.dockPlacement,
                            metrics,
                            config.overlayHandlePositionPercent
                        ) {
                            val handleLenPx = handleLengthDp * metrics.density
                            val maxTravelPx = ((if (isBottom) metrics.widthPx else metrics.heightPx) - handleLenPx)
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
                                            ResolvedDockPlacement.BOTTOM -> dy < -22f && abs(dy) > abs(dx)
                                        }
                                        if (inwardTrigger) {
                                            expandedBySwipe = true
                                            isCollapsed.value = false
                                            interactionTick.value++
                                            change.consume()
                                            break
                                        }
                                        if (elapsed >= 340L || abs(if (isBottom) dx else dy) > 24f) {
                                            draggingPos = true
                                            isDraggingPosition = true
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        }
                                    }

                                    if (draggingPos) {
                                        movedSignificant = true
                                        val axisDelta = if (isBottom) dx else dy
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
                                    if (isBottom) Modifier.width(handleLengthDp.dp).height(handleThicknessDp.dp)
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
                                        if (isBottom) Modifier.width(48.dp).height((handleThicknessDp * 0.22f).coerceIn(4f, 8f).dp)
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
                                onToggleAppDrawer = { bringDxCarToFront(MainActivity.EXTRA_OPEN_DRAWER) },
                                onGoDashboard = { bringDxCarToFront(null) },
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
                                onBluetoothClick = {
                                    SystemStatusMonitor.openBluetoothSettings(this@PersistentDockOverlayService)
                                    if (config.overlayAutoCollapseEnabled && !pinnedOpen) isCollapsed.value = true
                                },
                                onClockClick = { bringDxCarToFront(MainActivity.EXTRA_OPEN_QUICK_PANEL) },
                                onNotificationsClick = { bringDxCarToFront(MainActivity.EXTRA_OPEN_NOTIFICATIONS) },
                                onBackClick = {
                                    interactionTick.value++
                                    DxCarAccessibilityService.instance?.goBack()
                                },
                                onRecentsClick = {
                                    interactionTick.value++
                                    DxCarAccessibilityService.instance?.openRecents()
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
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(top = 12.dp, start = 16.dp, end = 16.dp)
                    ) {
                        IncomingCallBanner(
                            callState = callState,
                            onAnswer = { app.notificationCenter.performCallAction(CallActionCommand.ANSWER) },
                            onDecline = { app.notificationCenter.performCallAction(CallActionCommand.DECLINE) }
                        )
                        IncomingMessageBanner(
                            message = popupMessage,
                            quickReplies = config.quickReplies,
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
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        )
        bannerView = view
        bannerParams = params
        runCatching { windowManager.addView(view, params) }
    }

    private fun observeVisibilityAndLayout(app: DxCarApp) {
        lifecycleScope.launch {
            combine(
                app.preferences.config,
                app.isLauncherForeground,
                isCollapsed,
                metricsFlow
            ) { config, isHomeFg, collapsed, metrics ->
                QuadState(config, isHomeFg, collapsed, metrics)
            }.collect { (config, isHomeFg, collapsed, metrics) ->
                // Whenever transitioning from Home -> Third-party Fullscreen App:
                // auto-collapse to slim edge handle if Option 1 is enabled (and not pinned open)
                if (lastHomeForeground && !isHomeFg) {
                    if (config.overlayAutoCollapseEnabled && !isPinnedOpen.value) {
                        isCollapsed.value = true
                    } else if (!config.overlayAutoCollapseEnabled) {
                        isCollapsed.value = false
                    }
                }
                lastHomeForeground = isHomeFg

                val showDock = config.deviceRole == DeviceRole.AI_BOX_LAUNCHER &&
                    config.persistentOverlayDockEnabled &&
                    !isHomeFg

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
            .setContentTitle("DX-Car Launcher 常駐 Dock 運作中")
            .setContentText("開啟全螢幕 YouTube／地圖時保持 AA Dock 與來電橫幅常駐")
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

    private data class QuadState<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

    companion object {
        private const val CHANNEL_ID = "dxcar_persistent_dock"
        private const val NOTIF_ID = 2001
        const val ACTION_STOP = "com.dxcar.launcher.overlay.STOP"

        fun start(context: Context) {
            if (!Settings.canDrawOverlays(context)) return
            val intent = Intent(context, PersistentDockOverlayService::class.java)
            runCatching { ContextCompat.startForegroundService(context, intent) }
        }

        fun stop(context: Context) {
            runCatching { context.stopService(Intent(context, PersistentDockOverlayService::class.java)) }
        }
    }
}

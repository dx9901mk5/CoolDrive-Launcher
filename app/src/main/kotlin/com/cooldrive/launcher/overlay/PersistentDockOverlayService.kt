package com.cooldrive.launcher.overlay

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
import android.view.View
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
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
import com.cooldrive.launcher.CoolDriveApp
import com.cooldrive.launcher.MainActivity
import com.cooldrive.launcher.R
import com.cooldrive.launcher.accessibility.CoolDriveAccessibilityService
import com.cooldrive.launcher.comms.CallActionCommand
import com.cooldrive.launcher.comms.CallStatus
import com.cooldrive.launcher.data.DeviceRole
import com.cooldrive.launcher.layout.CoolwalkLayoutEngine
import com.cooldrive.launcher.layout.ResolvedDockPlacement
import com.cooldrive.launcher.layout.ScreenMetrics
import com.cooldrive.launcher.system.SystemStatusMonitor
import com.cooldrive.launcher.theme.CoolDriveTheme
import com.cooldrive.launcher.theme.DayNightEngine
import com.cooldrive.launcher.theme.LocalCoolDriveColors
import com.cooldrive.launcher.ui.CoolwalkDock
import com.cooldrive.launcher.ui.IncomingCallBanner
import com.cooldrive.launcher.ui.IncomingMessageBanner
import com.cooldrive.launcher.ui.buildRecentDockSlots
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * System-wide TYPE_APPLICATION_OVERLAY Service keeping:
 * 1. Persistent AA Dock visible over ALL full-screen apps (YouTube, Google Maps, Spotify, etc.),
 *    auto-positioned Left / Right / Bottom by Google Coolwalk DPI rules, and automatically
 *    hidden whenever CoolDrive Home itself is in the foreground.
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
    private var dockView: ComposeView? = null
    private var bannerView: ComposeView? = null
    private var dockParams: WindowManager.LayoutParams? = null
    private var bannerParams: WindowManager.LayoutParams? = null

    private val isCollapsed = MutableStateFlow(false)
    private val metricsFlow = MutableStateFlow(ScreenMetrics(1280, 720, 240))

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

        val app = CoolDriveApp.instance ?: return
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

    private fun setupDockWindow(app: CoolDriveApp) {
        val view = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@PersistentDockOverlayService)
            setViewTreeSavedStateRegistryOwner(this@PersistentDockOverlayService)
            setViewTreeViewModelStoreOwner(this@PersistentDockOverlayService)
            setContent {
                val config by app.preferences.config.collectAsState()
                val sysStatus by app.systemStatusMonitor.status.collectAsState()
                val apps by app.installedAppsRepository.apps.collectAsState()
                val liveRecent by app.installedAppsRepository.recentPackages.collectAsState()
                val activePkg by CoolDriveAccessibilityService.foregroundPackage.collectAsState()
                val messages by app.notificationCenter.messages.collectAsState()
                val collapsed by isCollapsed.collectAsState()
                val metrics by metricsFlow.collectAsState()

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

                CoolDriveTheme(
                    isNight = isNight,
                    accentIndex = config.accentColorIndex,
                    stylePreset = config.stylePreset
                ) {
                    val palette = LocalCoolDriveColors.current
                    if (collapsed) {
                        val isBottom = spec.dockPlacement == ResolvedDockPlacement.BOTTOM
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .padding(4.dp)
                                .then(
                                    if (isBottom) Modifier.width(68.dp).height(24.dp)
                                    else Modifier.width(24.dp).height(76.dp)
                                )
                                .clip(RoundedCornerShape(14.dp))
                                .background(palette.glassOverlay)
                                .border(1.dp, palette.accent.copy(alpha = 0.7f), RoundedCornerShape(14.dp))
                                .clickable { isCollapsed.value = false }
                        ) {
                            Icon(
                                imageVector = if (isBottom) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.ChevronRight,
                                contentDescription = "展開常駐 Dock",
                                tint = palette.accent,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    } else {
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
                            onToggleAppDrawer = { bringCoolDriveToFront(MainActivity.EXTRA_OPEN_DRAWER) },
                            onGoDashboard = { bringCoolDriveToFront(null) },
                            onSlotClick = { slot ->
                                when {
                                    slot.packageName == "ACTION_DIAL" -> app.installedAppsRepository.launchDialer()
                                    !slot.packageName.isNullOrBlank() -> {
                                        val ok = app.installedAppsRepository.launchAppFullscreen(slot.packageName)
                                        if (!ok && slot.id == "youtube") {
                                            bringCoolDriveToFront(MainActivity.EXTRA_OPEN_YOUTUBE)
                                        }
                                    }
                                    slot.id == "youtube" -> {
                                        val yt = app.installedAppsRepository.findBestYoutubePackage()
                                        if (yt != null) app.installedAppsRepository.launchAppFullscreen(yt)
                                        else bringCoolDriveToFront(MainActivity.EXTRA_OPEN_YOUTUBE)
                                    }
                                    else -> bringCoolDriveToFront(MainActivity.EXTRA_OPEN_DRAWER)
                                }
                            },
                            onWifiClick = { SystemStatusMonitor.openWifiSettings(this@PersistentDockOverlayService) },
                            onBluetoothClick = { SystemStatusMonitor.openBluetoothSettings(this@PersistentDockOverlayService) },
                            onClockClick = { bringCoolDriveToFront(MainActivity.EXTRA_OPEN_QUICK_PANEL) },
                            onNotificationsClick = { bringCoolDriveToFront(MainActivity.EXTRA_OPEN_NOTIFICATIONS) },
                            onCollapseOverlay = { isCollapsed.value = true }
                        )
                    }
                }
            }
        }

        val params = createOverlayParams(
            width = WindowManager.LayoutParams.WRAP_CONTENT,
            height = WindowManager.LayoutParams.MATCH_PARENT,
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
        )
        dockView = view
        dockParams = params
        runCatching { windowManager.addView(view, params) }
    }

    private fun setupBannerWindow(app: CoolDriveApp) {
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

                CoolDriveTheme(
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

    private fun observeVisibilityAndLayout(app: CoolDriveApp) {
        lifecycleScope.launch {
            combine(
                app.preferences.config,
                app.isLauncherForeground,
                isCollapsed,
                metricsFlow
            ) { config, isHomeFg, collapsed, metrics ->
                QuadState(config, isHomeFg, collapsed, metrics)
            }.collect { (config, isHomeFg, collapsed, metrics) ->
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
                        when (spec.dockPlacement) {
                            ResolvedDockPlacement.BOTTOM -> {
                                params.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                                params.width = if (collapsed) WindowManager.LayoutParams.WRAP_CONTENT else WindowManager.LayoutParams.MATCH_PARENT
                                params.height = WindowManager.LayoutParams.WRAP_CONTENT
                            }
                            ResolvedDockPlacement.RIGHT -> {
                                params.gravity = Gravity.END or Gravity.CENTER_VERTICAL
                                params.width = WindowManager.LayoutParams.WRAP_CONTENT
                                params.height = if (collapsed) WindowManager.LayoutParams.WRAP_CONTENT else WindowManager.LayoutParams.MATCH_PARENT
                            }
                            ResolvedDockPlacement.LEFT -> {
                                params.gravity = Gravity.START or Gravity.CENTER_VERTICAL
                                params.width = WindowManager.LayoutParams.WRAP_CONTENT
                                params.height = if (collapsed) WindowManager.LayoutParams.WRAP_CONTENT else WindowManager.LayoutParams.MATCH_PARENT
                            }
                        }
                        runCatching { windowManager.updateViewLayout(view, params) }
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

    private fun bringCoolDriveToFront(extraAction: String?) {
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
                "CoolDrive 常駐懸浮 Dock",
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
            .setContentTitle("CoolDrive 車機常駐 Dock 運作中")
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
        private const val CHANNEL_ID = "cooldrive_persistent_dock"
        private const val NOTIF_ID = 2001
        const val ACTION_STOP = "com.cooldrive.launcher.overlay.STOP"

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

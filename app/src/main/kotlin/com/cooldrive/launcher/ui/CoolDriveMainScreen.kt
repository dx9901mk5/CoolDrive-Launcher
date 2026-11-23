package com.cooldrive.launcher.ui

import android.content.res.Configuration
import android.graphics.Rect
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cooldrive.launcher.CoolDriveApp
import com.cooldrive.launcher.SplitSidePanelActivity
import com.cooldrive.launcher.comms.BluetoothCompanionService
import com.cooldrive.launcher.comms.CallActionCommand
import com.cooldrive.launcher.comms.CarMessageItem
import com.cooldrive.launcher.data.DeviceRole
import com.cooldrive.launcher.data.MapEngineStyle
import com.cooldrive.launcher.data.ThemeMode
import com.cooldrive.launcher.gestures.MultiFingerEvent
import com.cooldrive.launcher.gestures.coolDriveMultiFingerGestures
import com.cooldrive.launcher.layout.CoolwalkLayoutEngine
import com.cooldrive.launcher.layout.ResolvedDockPlacement
import com.cooldrive.launcher.layout.ScreenMetrics
import com.cooldrive.launcher.map.LiveMapCard
import com.cooldrive.launcher.overlay.PersistentDockOverlayService
import com.cooldrive.launcher.system.InstalledAppInfo
import com.cooldrive.launcher.system.PermissionHelper
import com.cooldrive.launcher.system.SystemStatusMonitor
import com.cooldrive.launcher.theme.CoolDriveTheme
import com.cooldrive.launcher.theme.DayNightEngine
import com.cooldrive.launcher.theme.LocalCoolDriveColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class LauncherDestination {
    DASHBOARD_SPLIT,
    FULLSCREEN_MAP,
    FULLSCREEN_YOUTUBE_WEB,
    APP_DRAWER,
    SETTINGS
}

@Composable
fun CoolDriveRootScreen(
    app: CoolDriveApp,
    externalNavCommand: String?,
    onExternalNavConsumed: () -> Unit,
    onRequestRuntimePermissions: () -> Unit,
    onRequestSetDefaultHome: () -> Unit,
    onStartVoiceReply: (CarMessageItem) -> Unit
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val scope = rememberCoroutineScope()

    val config by app.preferences.config.collectAsState()
    val sysStatus by app.systemStatusMonitor.status.collectAsState()
    val location by app.locationTracker.location.collectAsState()
    val media by app.mediaControlBridge.mediaState.collectAsState()
    val installedApps by app.installedAppsRepository.apps.collectAsState()
    val liveRecentPackages by app.installedAppsRepository.recentPackages.collectAsState()
    val homeLaunchers by app.installedAppsRepository.homeLaunchers.collectAsState()
    val activeCall by app.notificationCenter.activeCall.collectAsState()
    val popupMessage by app.notificationCenter.popupMessage.collectAsState()
    val messages by app.notificationCenter.messages.collectAsState()
    val activeNav by app.notificationCenter.activeNavigation.collectAsState()
    val bridgeStatus by BluetoothCompanionService.bridgeState.collectAsState()
    val updateState by app.updateManager.state.collectAsState()

    var destination by remember { mutableStateOf(LauncherDestination.DASHBOARD_SPLIT) }
    var isMapPrimary by remember { mutableStateOf(true) }
    var isDockTemporarilyHidden by remember { mutableStateOf(false) }
    var showQuickControlCenter by remember { mutableStateOf(false) }
    var showNotificationCenter by remember { mutableStateOf(false) }
    var pickingShortcutSlotIndex by remember { mutableStateOf<Int?>(null) }
    var gestureHudText by remember { mutableStateOf<String?>(null) }
    var permissionHealth by remember { mutableStateOf(PermissionHelper.check(context)) }
    var pairedBtDevices by remember { mutableStateOf(app.systemStatusMonitor.getPairedBluetoothDevices()) }

    // Refresh permission health & home launchers automatically when entering Settings or returning from RoleManager
    LaunchedEffect(destination, homeLaunchers) {
        if (destination == LauncherDestination.SETTINGS) {
            permissionHealth = PermissionHelper.check(context)
        }
    }

    // Clear Gesture HUD pill automatically after 1.6s
    LaunchedEffect(gestureHudText) {
        if (gestureHudText != null) {
            delay(1_600L)
            gestureHudText = null
        }
    }

    // Handle external actions triggered from Persistent Overlay Dock
    LaunchedEffect(externalNavCommand) {
        when (externalNavCommand) {
            com.cooldrive.launcher.MainActivity.EXTRA_OPEN_DRAWER -> {
                destination = LauncherDestination.APP_DRAWER
                onExternalNavConsumed()
            }
            com.cooldrive.launcher.MainActivity.EXTRA_OPEN_YOUTUBE -> {
                destination = LauncherDestination.FULLSCREEN_YOUTUBE_WEB
                onExternalNavConsumed()
            }
            com.cooldrive.launcher.MainActivity.EXTRA_OPEN_QUICK_PANEL -> {
                showQuickControlCenter = true
                onExternalNavConsumed()
            }
            com.cooldrive.launcher.MainActivity.EXTRA_OPEN_NOTIFICATIONS -> {
                showNotificationCenter = true
                onExternalNavConsumed()
            }
            com.cooldrive.launcher.MainActivity.EXTRA_OPEN_SETTINGS -> {
                permissionHealth = PermissionHelper.check(context)
                destination = LauncherDestination.SETTINGS
                onExternalNavConsumed()
            }
        }
    }

    // Keep Persistent Overlay Dock & Bluetooth Bridge synced with user config
    LaunchedEffect(config.deviceRole, config.persistentOverlayDockEnabled) {
        if (config.deviceRole == DeviceRole.AI_BOX_LAUNCHER && config.persistentOverlayDockEnabled) {
            PersistentDockOverlayService.start(context)
        } else {
            PersistentDockOverlayService.stop(context)
        }
    }

    LaunchedEffect(config.deviceRole, config.companionTargetDeviceAddress, config.companionAutoConnect) {
        if (config.companionAutoConnect) {
            BluetoothCompanionService.startOrReconnect(context)
        }
    }

    // Automatic GitHub Update Check & Auto-Download on Startup
    LaunchedEffect(config.autoCheckAndDownloadUpdate) {
        delay(2_500L)
        if (config.autoCheckAndDownloadUpdate) {
            app.updateManager.checkForUpdate(autoDownload = true)
        }
    }

    val dm = context.resources.displayMetrics
    val screenMetrics = remember(configuration.screenWidthDp, configuration.screenHeightDp, configuration.densityDpi, dm.widthPixels, dm.heightPixels) {
        ScreenMetrics(
            widthPx = dm.widthPixels,
            heightPx = dm.heightPixels,
            densityDpi = dm.densityDpi
        )
    }

    val layoutSpec = remember(screenMetrics, config.dockPositionMode, config.rightHandDrive, config.primarySplitRatio) {
        CoolwalkLayoutEngine.compute(
            metrics = screenMetrics,
            dockMode = config.dockPositionMode,
            rightHandDrive = config.rightHandDrive,
            customPrimaryRatio = config.primarySplitRatio
        )
    }

    val systemIsDark = (configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    val isNight = DayNightEngine.isNightNow(
        config = config,
        now = sysStatus.now,
        systemIsDark = systemIsDark
    )

    val unreadCount = remember(messages) { messages.count { !it.isRead } }
    val dockSlots = remember(liveRecentPackages, config.recentDockPackages, config.pinnedDockPackages, installedApps, destination) {
        val activePkg = when (destination) {
            LauncherDestination.FULLSCREEN_MAP -> "com.google.android.apps.maps"
            LauncherDestination.FULLSCREEN_YOUTUBE_WEB -> "com.google.android.youtube"
            else -> ""
        }
        buildRecentDockSlots(
            recentPackages = (liveRecentPackages + config.recentDockPackages).distinct(),
            fallbackPackages = config.pinnedDockPackages,
            installedApps = installedApps,
            activePackage = activePkg
        )
    }

    fun launchYoutubeBest() {
        val ytPkg = app.installedAppsRepository.findBestYoutubePackage()
        if (ytPkg != null && app.installedAppsRepository.launchAppFullscreen(ytPkg)) {
            return
        }
        destination = LauncherDestination.FULLSCREEN_YOUTUBE_WEB
    }

    fun launchRealMapsFreeformBox() {
        val dockPx = (layoutSpec.dockThicknessDp * dm.density).toInt()
        val padPx = (12 * dm.density).toInt()
        val availW = (dm.widthPixels - if (layoutSpec.dockPlacement != ResolvedDockPlacement.BOTTOM) dockPx else 0).coerceAtLeast(300)
        val availH = (dm.heightPixels - if (layoutSpec.dockPlacement == ResolvedDockPlacement.BOTTOM) dockPx else 0).coerceAtLeast(240)
        val primaryW = (availW * layoutSpec.primaryWeight).toInt()
        val leftX = when {
            config.rightHandDrive -> availW - primaryW + padPx
            layoutSpec.dockPlacement == ResolvedDockPlacement.LEFT -> dockPx + padPx
            else -> padPx
        }
        val bounds = Rect(leftX, padPx, (leftX + primaryW - padPx * 2).coerceAtLeast(leftX + 200), availH - padPx)
        app.installedAppsRepository.launchGoogleMapsFreeform(bounds)
        gestureHudText = "🗺 已嘗試以 Freeform 視窗開啟真實 Google 地圖"
    }

    fun handleCustomShortcutLaunch(token: String) {
        when (token) {
            BuiltInShortcutTokens.SETTINGS -> {
                permissionHealth = PermissionHelper.check(context)
                app.installedAppsRepository.refreshHomeLaunchers()
                destination = LauncherDestination.SETTINGS
            }
            BuiltInShortcutTokens.YOUTUBE -> launchYoutubeBest()
            BuiltInShortcutTokens.DIALER -> app.installedAppsRepository.launchDialer()
            BuiltInShortcutTokens.DAY_NIGHT -> {
                val nextMode = if (isNight) ThemeMode.FORCE_DAY else ThemeMode.FORCE_NIGHT
                app.preferences.update { it.copy(themeMode = nextMode) }
                gestureHudText = if (nextMode == ThemeMode.FORCE_NIGHT) "🌙 已切換為夜間模式" else "☀️ 已切換為白天模式"
            }
            BuiltInShortcutTokens.SPLIT_GMAPS -> {
                SplitSidePanelActivity.launchWithRealGoogleMaps(context)
            }
            else -> {
                if (!app.installedAppsRepository.launchAppFullscreen(token)) {
                    gestureHudText = "無法啟動該應用程式"
                }
            }
        }
    }

    fun handleToggleDayNight() {
        val nextMode = if (isNight) ThemeMode.FORCE_DAY else ThemeMode.FORCE_NIGHT
        app.preferences.update { it.copy(themeMode = nextMode) }
        gestureHudText = if (nextMode == ThemeMode.FORCE_NIGHT) "🌙 已切換為夜間模式" else "☀️ 已切換為白天模式"
    }

    fun handleMultiFingerEvent(event: MultiFingerEvent) {
        when (event) {
            is MultiFingerEvent.TwoFingerBrightnessDelta -> {
                val (pct, written) = app.systemStatusMonitor.adjustBrightnessByDelta(event.deltaPercent)
                gestureHudText = if (written) "☀️ 螢幕亮度 $pct%" else "☀️ 亮度調整需開啟「修改系統設定」權限"
            }
            is MultiFingerEvent.TwoFingerVolumeDelta -> {
                val pct = app.systemStatusMonitor.adjustMediaVolumeByDelta(event.deltaPercent)
                gestureHudText = "🔊 媒體音量 $pct%"
            }
            MultiFingerEvent.TwoFingerSwipeLeft -> {
                app.mediaControlBridge.skipNext()
                gestureHudText = "⏭ 下一首"
            }
            MultiFingerEvent.TwoFingerSwipeRight -> {
                app.mediaControlBridge.skipPrevious()
                gestureHudText = "⏮ 上一首"
            }
            MultiFingerEvent.ThreeFingerSwipeDown -> {
                destination = LauncherDestination.DASHBOARD_SPLIT
                showQuickControlCenter = false
                showNotificationCenter = false
                gestureHudText = "🏠 返回 AA 首頁"
            }
            MultiFingerEvent.ThreeFingerSwipeLeft,
            MultiFingerEvent.ThreeFingerSwipeRight -> {
                isMapPrimary = !isMapPrimary
                destination = LauncherDestination.DASHBOARD_SPLIT
                gestureHudText = "⇄ 左右分割視窗互換"
            }
            MultiFingerEvent.ThreeFingerTap -> {
                handleToggleDayNight()
            }
            MultiFingerEvent.FourFingerPinch -> {
                isDockTemporarilyHidden = true
                gestureHudText = "⛶ 隱藏 Dock（四指展開恢復）"
            }
            MultiFingerEvent.FourFingerSpread -> {
                isDockTemporarilyHidden = false
                gestureHudText = "▤ 顯示常駐 Dock"
            }
        }
    }

    CoolDriveTheme(
        isNight = isNight,
        accentIndex = config.accentColorIndex,
        stylePreset = config.stylePreset
    ) {
        val colors = LocalCoolDriveColors.current

        if (config.deviceRole == DeviceRole.PHONE_COMPANION) {
            PhoneCompanionScreen(
                config = config,
                pairedDevices = pairedBtDevices,
                bridgeStatus = bridgeStatus,
                permissionHealth = permissionHealth,
                onRefreshPairedDevices = {
                    pairedBtDevices = app.systemStatusMonitor.getPairedBluetoothDevices()
                    permissionHealth = PermissionHelper.check(context)
                },
                onSelectTargetDevice = { dev ->
                    app.preferences.update {
                        it.copy(
                            companionTargetDeviceAddress = dev.address,
                            companionTargetDeviceName = dev.name
                        )
                    }
                    BluetoothCompanionService.startOrReconnect(context)
                },
                onToggleAllowedPackage = { pkg ->
                    app.preferences.update { current ->
                        val next = current.companionAllowedPackages.toMutableList()
                        if (next.contains(pkg)) next.remove(pkg) else next.add(pkg)
                        current.copy(companionAllowedPackages = next)
                    }
                },
                onRequestRuntimePermissions = onRequestRuntimePermissions,
                onReconnectBridge = { BluetoothCompanionService.startOrReconnect(context) },
                onSwitchToAiBoxMode = {
                    app.preferences.update { it.copy(deviceRole = DeviceRole.AI_BOX_LAUNCHER) }
                    BluetoothCompanionService.startOrReconnect(context)
                }
            )
            return@CoolDriveTheme
        }

        // AI BOX CAR LAUNCHER ROOT CONTAINER
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.canvasBackground)
                .coolDriveMultiFingerGestures(
                    enabled = config.multiFingerGesturesEnabled,
                    onGesture = ::handleMultiFingerEvent
                )
        ) {
            val dockComposable: @Composable () -> Unit = {
                CoolwalkDock(
                    placement = layoutSpec.dockPlacement,
                    thicknessDp = layoutSpec.dockThicknessDp,
                    systemStatus = sysStatus,
                    is24HourClock = config.is24HourClock,
                    isAppDrawerOpen = destination == LauncherDestination.APP_DRAWER,
                    isDashboardActive = destination == LauncherDestination.DASHBOARD_SPLIT,
                    unreadCount = unreadCount,
                    pinnedSlots = dockSlots,
                    rightHandDrive = config.rightHandDrive,
                    onToggleAppDrawer = {
                        destination = if (destination == LauncherDestination.APP_DRAWER) {
                            LauncherDestination.DASHBOARD_SPLIT
                        } else {
                            LauncherDestination.APP_DRAWER
                        }
                    },
                    onGoDashboard = { destination = LauncherDestination.DASHBOARD_SPLIT },
                    onSlotClick = { slot ->
                        when {
                            slot.packageName == "com.google.android.apps.maps" && destination != LauncherDestination.FULLSCREEN_MAP -> {
                                destination = LauncherDestination.FULLSCREEN_MAP
                            }
                            slot.id == "youtube" -> launchYoutubeBest()
                            slot.packageName == "ACTION_DIAL" -> app.installedAppsRepository.launchDialer()
                            !slot.packageName.isNullOrBlank() -> {
                                app.installedAppsRepository.launchAppFullscreen(slot.packageName)
                            }
                            else -> destination = LauncherDestination.APP_DRAWER
                        }
                    },
                    onWifiClick = { SystemStatusMonitor.openWifiSettings(context) },
                    onBluetoothClick = { SystemStatusMonitor.openBluetoothSettings(context) },
                    onBluetoothLongClick = {
                        permissionHealth = PermissionHelper.check(context)
                        app.installedAppsRepository.refreshHomeLaunchers()
                        destination = LauncherDestination.SETTINGS
                    },
                    onClockClick = { showQuickControlCenter = !showQuickControlCenter },
                    onNotificationsClick = { showNotificationCenter = !showNotificationCenter }
                )
            }

            val stageContent: @Composable (Modifier) -> Unit = { stageMod ->
                AnimatedContent(
                    targetState = destination,
                    transitionSpec = {
                        when (targetState) {
                            LauncherDestination.APP_DRAWER ->
                                (slideInVertically(
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioLowBouncy,
                                        stiffness = Spring.StiffnessMediumLow
                                    ),
                                    initialOffsetY = { it / 6 }
                                ) + scaleIn(initialScale = 0.95f, animationSpec = tween(220)) + fadeIn(tween(200))) togetherWith
                                    (scaleOut(targetScale = 0.96f, animationSpec = tween(160)) + fadeOut(tween(150)))

                            LauncherDestination.SETTINGS ->
                                (scaleIn(initialScale = 0.94f, animationSpec = tween(240)) + fadeIn(tween(200))) togetherWith
                                    (scaleOut(targetScale = 0.96f, animationSpec = tween(160)) + fadeOut(tween(150)))

                            else ->
                                (scaleIn(initialScale = 0.97f, animationSpec = tween(220)) + fadeIn(tween(200))) togetherWith
                                    (scaleOut(targetScale = 0.97f, animationSpec = tween(160)) + fadeOut(tween(150)))
                        }
                    },
                    label = "stageTransition",
                    modifier = stageMod
                ) { targetDest ->
                    when (targetDest) {
                        LauncherDestination.DASHBOARD_SPLIT -> {
                            CoolwalkSplitDashboard(
                                isHorizontalSplit = layoutSpec.isHorizontalSplit,
                                supportsThirdSubCard = layoutSpec.supportsThirdSubCard,
                                rightHandDrive = config.rightHandDrive,
                                primaryRatio = layoutSpec.primaryWeight,
                                isMapPrimary = isMapPrimary,
                                location = location,
                                activeNav = activeNav,
                                media = media,
                                isNight = isNight,
                                mapEngineStyle = config.mapEngineStyle,
                                homeAddress = config.homeAddress,
                                workAddress = config.workAddress,
                                customShortcutSlots = config.customShortcutSlots,
                                installedApps = installedApps,
                                onToggleMapStyle = {
                                    val next = if (config.mapEngineStyle == MapEngineStyle.GOOGLE_TRAFFIC_TILES) {
                                        MapEngineStyle.OPENFREEMAP_VECTOR_3D
                                    } else {
                                        MapEngineStyle.GOOGLE_TRAFFIC_TILES
                                    }
                                    app.preferences.update { it.copy(mapEngineStyle = next) }
                                    gestureHudText = if (next == MapEngineStyle.OPENFREEMAP_VECTOR_3D) {
                                        "🗺 切換至 3D 向量地圖"
                                    } else {
                                        "🗺 切換至 Google 即時路況圖磚"
                                    }
                                },
                                onSwapPanes = { isMapPrimary = !isMapPrimary },
                                onOpenFullscreenMap = { destination = LauncherDestination.FULLSCREEN_MAP },
                                onLaunchRealGoogleMaps = { query ->
                                    app.installedAppsRepository.launchGoogleMapsNavigation(query)
                                },
                                onLaunchRealMapsSplit = {
                                    SplitSidePanelActivity.launchWithRealGoogleMaps(context)
                                },
                                onMediaPlayPause = { app.mediaControlBridge.togglePlayPause() },
                                onMediaNext = { app.mediaControlBridge.skipNext() },
                                onMediaPrev = { app.mediaControlBridge.skipPrevious() },
                                onSelectMediaSession = { app.mediaControlBridge.selectSession(it) },
                                onOpenMediaApp = {
                                    val pkg = media.packageName
                                    if (pkg.isNotBlank()) app.installedAppsRepository.launchAppFullscreen(pkg)
                                    else launchYoutubeBest()
                                },
                                onOpenYoutubeFullscreen = ::launchYoutubeBest,
                                onCustomSlotEmptyClick = { slotIdx -> pickingShortcutSlotIndex = slotIdx },
                                onCustomSlotLaunch = ::handleCustomShortcutLaunch,
                                onCustomSlotRemove = { slotIdx, label ->
                                    app.preferences.clearCustomShortcutSlot(slotIdx)
                                    gestureHudText = "🗑 已移除 $label（點擊 ＋ 可重新加入）"
                                }
                            )
                        }

                        LauncherDestination.FULLSCREEN_MAP -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(10.dp)
                                    .clip(RoundedCornerShape(colors.cardCornerDp.dp))
                                    .border(
                                        colors.cardBorderWidthDp.dp,
                                        colors.cardBorder,
                                        RoundedCornerShape(colors.cardCornerDp.dp)
                                    )
                            ) {
                                LiveMapCard(
                                    location = location,
                                    activeNav = activeNav,
                                    isNight = isNight,
                                    mapEngineStyle = config.mapEngineStyle,
                                    isPrimary = true,
                                    isFullscreen = true,
                                    homeAddress = config.homeAddress,
                                    workAddress = config.workAddress,
                                    onToggleStyle = {
                                        val next = if (config.mapEngineStyle == MapEngineStyle.GOOGLE_TRAFFIC_TILES) {
                                            MapEngineStyle.OPENFREEMAP_VECTOR_3D
                                        } else {
                                            MapEngineStyle.GOOGLE_TRAFFIC_TILES
                                        }
                                        app.preferences.update { it.copy(mapEngineStyle = next) }
                                    },
                                    onSwapRequested = { destination = LauncherDestination.DASHBOARD_SPLIT },
                                    onToggleFullscreen = { destination = LauncherDestination.DASHBOARD_SPLIT },
                                    onLaunchGoogleMapsApp = { q ->
                                        app.installedAppsRepository.launchGoogleMapsNavigation(q)
                                    },
                                    onLaunchRealMapsSplit = {
                                        SplitSidePanelActivity.launchWithRealGoogleMaps(context)
                                    }
                                )
                            }
                        }

                        LauncherDestination.FULLSCREEN_YOUTUBE_WEB -> {
                            BuiltInYoutubeFullscreenScreen(
                                onClose = { destination = LauncherDestination.DASHBOARD_SPLIT }
                            )
                        }

                        LauncherDestination.APP_DRAWER -> {
                            AppDrawerScreen(
                                apps = installedApps,
                                pinnedPackages = config.pinnedDockPackages,
                                hasPendingUpdate = updateState.hasUpdate || updateState.downloadedReady,
                                onLaunchApp = { pkg -> app.installedAppsRepository.launchAppFullscreen(pkg) },
                                onLaunchYoutube = ::launchYoutubeBest,
                                onTogglePin = { pkg ->
                                    app.preferences.togglePinnedPackage(pkg)
                                    gestureHudText = "📌 已更新備選 Dock 清單"
                                },
                                onOpenSettings = {
                                    permissionHealth = PermissionHelper.check(context)
                                    app.installedAppsRepository.refreshHomeLaunchers()
                                    destination = LauncherDestination.SETTINGS
                                },
                                onCloseDrawer = { destination = LauncherDestination.DASHBOARD_SPLIT }
                            )
                        }

                        LauncherDestination.SETTINGS -> {
                            SettingsScreen(
                                config = config,
                                layoutSpec = layoutSpec,
                                homeLaunchers = homeLaunchers,
                                permissionHealth = permissionHealth,
                                bridgeStatus = bridgeStatus,
                                updateState = updateState,
                                onUpdateConfig = { transform -> app.preferences.update(transform) },
                                onRequestSetDefaultHome = {
                                    onRequestSetDefaultHome()
                                    app.installedAppsRepository.refreshHomeLaunchers()
                                },
                                onSwitchToLauncher = { targetLauncher ->
                                    val launched = app.installedAppsRepository.launchHomeLauncher(targetLauncher)
                                    gestureHudText = if (launched) {
                                        "🏠 正在切換至 ${targetLauncher.label}"
                                    } else {
                                        "無法啟動 ${targetLauncher.label}"
                                    }
                                },
                                onRefreshHomeLaunchers = {
                                    app.installedAppsRepository.refreshHomeLaunchers()
                                    permissionHealth = PermissionHelper.check(context)
                                },
                                onLaunchRealMapsSplitScreen = {
                                    SplitSidePanelActivity.launchWithRealGoogleMaps(context)
                                },
                                onLaunchRealMapsFreeform = ::launchRealMapsFreeformBox,
                                onResetSettingsToDefaults = {
                                    app.preferences.resetToDefaults()
                                    gestureHudText = "↺ 已恢復所有啟動器設定為預設值"
                                },
                                onRefreshPermissions = {
                                    permissionHealth = PermissionHelper.check(context)
                                    app.installedAppsRepository.refreshHomeLaunchers()
                                    app.locationTracker.startIfPermitted()
                                    app.mediaControlBridge.refreshSessions()
                                },
                                onRequestRuntimePermissions = onRequestRuntimePermissions,
                                onSimulateCall = { app.notificationCenter.triggerSimulatedCall() },
                                onSimulateMessage = { app.notificationCenter.triggerSimulatedMessage() },
                                onCheckUpdateNow = {
                                    scope.launch {
                                        app.updateManager.checkForUpdate(autoDownload = config.autoCheckAndDownloadUpdate)
                                    }
                                },
                                onDownloadOrInstallUpdate = {
                                    if (updateState.downloadedReady) {
                                        app.updateManager.installDownloadedApk()
                                    } else {
                                        scope.launch { app.updateManager.downloadAndInstall(autoLaunchInstaller = true) }
                                    }
                                },
                                onSwitchToPhoneCompanion = {
                                    pairedBtDevices = app.systemStatusMonitor.getPairedBluetoothDevices()
                                    permissionHealth = PermissionHelper.check(context)
                                    app.preferences.update { it.copy(deviceRole = DeviceRole.PHONE_COMPANION) }
                                    BluetoothCompanionService.startOrReconnect(context)
                                },
                                onClose = { destination = LauncherDestination.APP_DRAWER }
                            )
                        }
                    }
                }
            }

            // MAIN DOCK + STAGE LAYOUT (Auto Left / Right / Bottom per Google Coolwalk DPI!)
            when (layoutSpec.dockPlacement) {
                ResolvedDockPlacement.LEFT -> {
                    Row(modifier = Modifier.fillMaxSize()) {
                        if (!isDockTemporarilyHidden) dockComposable()
                        stageContent(Modifier.weight(1f).fillMaxHeight())
                    }
                }
                ResolvedDockPlacement.RIGHT -> {
                    Row(modifier = Modifier.fillMaxSize()) {
                        stageContent(Modifier.weight(1f).fillMaxHeight())
                        if (!isDockTemporarilyHidden) dockComposable()
                    }
                }
                ResolvedDockPlacement.BOTTOM -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        stageContent(Modifier.weight(1f).fillMaxWidth())
                        if (!isDockTemporarilyHidden) dockComposable()
                    }
                }
            }

            // TOP-CENTER AUTO-UPDATE READY BANNER (when new release was auto-downloaded!)
            AnimatedVisibility(
                visible = updateState.downloadedReady && destination == LauncherDestination.DASHBOARD_SPLIT,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = if (layoutSpec.dockPlacement == ResolvedDockPlacement.BOTTOM) 84.dp else 16.dp)
            ) {
                Surface(
                    color = colors.accent,
                    shape = RoundedCornerShape(22.dp),
                    shadowElevation = 10.dp,
                    modifier = Modifier.bouncyClickable(pressedScale = 0.93f) {
                        app.updateManager.installDownloadedApk()
                    }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.CloudDownload, contentDescription = null, tint = colors.onAccent)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "新版本 v${updateState.latestVersion} 已自動下載完成 · 點擊立即安裝",
                            color = colors.onAccent,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // FLOATING AA INCOMING CALL & MESSAGE BANNERS (Top Center)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 14.dp, start = 20.dp, end = 20.dp)
            ) {
                IncomingCallBanner(
                    callState = activeCall,
                    onAnswer = { app.notificationCenter.performCallAction(CallActionCommand.ANSWER) },
                    onDecline = { app.notificationCenter.performCallAction(CallActionCommand.DECLINE) }
                )
                IncomingMessageBanner(
                    message = popupMessage,
                    quickReplies = config.quickReplies,
                    onReadAloud = { app.notificationCenter.readMessageAloud(it) },
                    onQuickReply = { item, reply ->
                        app.notificationCenter.sendQuickReply(item, reply)
                        gestureHudText = "💬 已傳送回覆：$reply"
                    },
                    onVoiceReply = { item ->
                        app.notificationCenter.pausePopupDismiss()
                        onStartVoiceReply(item)
                    },
                    onMarkRead = { app.notificationCenter.markMessageRead(it) },
                    onDismiss = { app.notificationCenter.dismissPopupMessage() }
                )
            }

            // MULTI-FINGER GESTURE FEEDBACK PILL
            GestureFeedbackPill(
                text = gestureHudText,
                modifier = Modifier.align(Alignment.Center)
            )

            // QUICK CONTROL CENTER OVERLAY (Clock tap)
            AnimatedVisibility(
                visible = showQuickControlCenter,
                enter = fadeIn(tween(180)) + scaleIn(initialScale = 0.92f, animationSpec = spring(stiffness = Spring.StiffnessMedium)),
                exit = fadeOut(tween(140)) + scaleOut(targetScale = 0.94f, animationSpec = tween(140))
            ) {
                QuickControlCenterOverlay(
                    systemStatus = sysStatus,
                    isNight = isNight,
                    onVolumeChange = { app.systemStatusMonitor.setMediaVolumePercent(it) },
                    onBrightnessChange = { app.systemStatusMonitor.setBrightnessPercent(it) },
                    onToggleDayNight = ::handleToggleDayNight,
                    onSwapSplit = { isMapPrimary = !isMapPrimary },
                    onOpenWifiSettings = { SystemStatusMonitor.openWifiSettings(context) },
                    onOpenBluetoothSettings = { SystemStatusMonitor.openBluetoothSettings(context) },
                    onOpenSettings = {
                        permissionHealth = PermissionHelper.check(context)
                        app.installedAppsRepository.refreshHomeLaunchers()
                        destination = LauncherDestination.SETTINGS
                    },
                    onDismiss = { showQuickControlCenter = false }
                )
            }

            // NOTIFICATION CENTER OVERLAY (Bell tap)
            AnimatedVisibility(
                visible = showNotificationCenter,
                enter = fadeIn(tween(180)) + scaleIn(initialScale = 0.92f, animationSpec = spring(stiffness = Spring.StiffnessMedium)),
                exit = fadeOut(tween(140)) + scaleOut(targetScale = 0.94f, animationSpec = tween(140))
            ) {
                NotificationCenterSheet(
                    messages = messages,
                    quickReplies = config.quickReplies,
                    onReadAloud = { app.notificationCenter.readMessageAloud(it) },
                    onQuickReply = { item, reply ->
                        app.notificationCenter.sendQuickReply(item, reply)
                        gestureHudText = "💬 已傳送回覆：$reply"
                    },
                    onMarkRead = { app.notificationCenter.markMessageRead(it) },
                    onClearAll = { app.notificationCenter.clearAllMessages() },
                    onClose = { showNotificationCenter = false }
                )
            }

            // APP PICKER DIALOG FOR THE 6 CUSTOM SHORTCUT SLOTS (Shows Real App Icons!)
            AnimatedVisibility(
                visible = pickingShortcutSlotIndex != null,
                enter = fadeIn(tween(180)) + scaleIn(initialScale = 0.90f, animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMedium)),
                exit = fadeOut(tween(140)) + scaleOut(targetScale = 0.92f, animationSpec = tween(140))
            ) {
                val slotIdx = pickingShortcutSlotIndex ?: return@AnimatedVisibility
                AppPickerDialog(
                    slotIndex = slotIdx,
                    apps = installedApps,
                    onSelectToken = { token, label ->
                        app.preferences.setCustomShortcutSlot(slotIdx, token)
                        pickingShortcutSlotIndex = null
                        gestureHudText = "✓ 已加入「$label」至第 ${slotIdx + 1} 格"
                    },
                    onDismiss = { pickingShortcutSlotIndex = null }
                )
            }
        }
    }
}

/**
 * Android Auto Coolwalk Split Dashboard:
 * - Animated Primary (default 63%) vs Secondary (default 37%) proportion!
 * - Supports Left-Hand Drive (default: Primary on Left, Secondary + 6 Custom Slots on Right)
 *   AND Right-Hand Drive (mirrors cleanly: Primary on Right near driver, Secondary + 6 Custom Slots on Left)!
 * - Clicking the secondary card smoothly SWAPS sides (just like AA Coolwalk!).
 */
@Composable
private fun CoolwalkSplitDashboard(
    isHorizontalSplit: Boolean,
    supportsThirdSubCard: Boolean,
    rightHandDrive: Boolean,
    primaryRatio: Float,
    isMapPrimary: Boolean,
    location: com.cooldrive.launcher.map.VehicleLocationState,
    activeNav: com.cooldrive.launcher.comms.ActiveNavBanner?,
    media: com.cooldrive.launcher.media.ActiveMediaState,
    isNight: Boolean,
    mapEngineStyle: MapEngineStyle,
    homeAddress: String,
    workAddress: String,
    customShortcutSlots: List<String>,
    installedApps: List<InstalledAppInfo>,
    onToggleMapStyle: () -> Unit,
    onSwapPanes: () -> Unit,
    onOpenFullscreenMap: () -> Unit,
    onLaunchRealGoogleMaps: (String?) -> Unit,
    onLaunchRealMapsSplit: () -> Unit,
    onMediaPlayPause: () -> Unit,
    onMediaNext: () -> Unit,
    onMediaPrev: () -> Unit,
    onSelectMediaSession: (String) -> Unit,
    onOpenMediaApp: () -> Unit,
    onOpenYoutubeFullscreen: () -> Unit,
    onCustomSlotEmptyClick: (Int) -> Unit,
    onCustomSlotLaunch: (String) -> Unit,
    onCustomSlotRemove: (Int, String) -> Unit
) {
    val colors = LocalCoolDriveColors.current
    val cardShape = RoundedCornerShape(colors.cardCornerDp.dp)

    val primaryWeight by animateFloatAsState(
        targetValue = primaryRatio,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "primaryWeight"
    )
    val secondaryWeight = (1f - primaryWeight).coerceAtLeast(0.26f)

    val mapCardComposable: @Composable (Boolean, Modifier) -> Unit = { primary, mod ->
        Box(
            modifier = mod
                .clip(cardShape)
                .border(colors.cardBorderWidthDp.dp, colors.cardBorder, cardShape)
        ) {
            LiveMapCard(
                location = location,
                activeNav = activeNav,
                isNight = isNight,
                mapEngineStyle = mapEngineStyle,
                isPrimary = primary,
                isFullscreen = false,
                homeAddress = homeAddress,
                workAddress = workAddress,
                onToggleStyle = onToggleMapStyle,
                onSwapRequested = onSwapPanes,
                onToggleFullscreen = onOpenFullscreenMap,
                onLaunchGoogleMapsApp = onLaunchRealGoogleMaps,
                onLaunchRealMapsSplit = onLaunchRealMapsSplit
            )
        }
    }

    val mediaCardComposable: @Composable (Boolean, Modifier) -> Unit = { primary, mod ->
        Box(
            modifier = mod
                .clip(cardShape)
                .border(colors.cardBorderWidthDp.dp, colors.cardBorder, cardShape)
        ) {
            MediaControlCard(
                media = media,
                isPrimary = primary,
                onPlayPause = onMediaPlayPause,
                onNext = onMediaNext,
                onPrevious = onMediaPrev,
                onSelectSession = onSelectMediaSession,
                onOpenMediaApp = onOpenMediaApp,
                onOpenYoutubeFullscreen = onOpenYoutubeFullscreen,
                onSwapRequested = onSwapPanes
            )
        }
    }

    val customSixCard: @Composable () -> Unit = {
        CustomSixAppBarCard(
            slots = customShortcutSlots,
            installedApps = installedApps,
            onSlotEmptyClick = onCustomSlotEmptyClick,
            onSlotLaunch = onCustomSlotLaunch,
            onSlotRemove = onCustomSlotRemove
        )
    }

    if (isHorizontalSplit) {
        val primaryPane: @Composable (Modifier) -> Unit = { mod ->
            if (isMapPrimary) mapCardComposable(true, mod) else mediaCardComposable(true, mod)
        }
        val secondaryColumn: @Composable (Modifier) -> Unit = { mod ->
            Column(
                modifier = mod,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (isMapPrimary) {
                    mediaCardComposable(false, Modifier.weight(1f).fillMaxWidth())
                } else {
                    mapCardComposable(false, Modifier.weight(1f).fillMaxWidth())
                }
                if (supportsThirdSubCard) {
                    customSixCard()
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (rightHandDrive) {
                // RHD: Secondary + 6-App Bar on Left, Primary (near driver) on Right!
                secondaryColumn(Modifier.weight(secondaryWeight).fillMaxHeight())
                primaryPane(Modifier.weight(primaryWeight).fillMaxHeight())
            } else {
                // LHD (Default): Primary (near driver) on Left, Secondary + 6-App Bar on Right!
                primaryPane(Modifier.weight(primaryWeight).fillMaxHeight())
                secondaryColumn(Modifier.weight(secondaryWeight).fillMaxHeight())
            }
        }
    } else {
        // Portrait / Square vertical split (with 6 Custom App Bar cleanly docked at bottom)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (isMapPrimary) {
                mapCardComposable(true, Modifier.weight(primaryWeight).fillMaxWidth())
                mediaCardComposable(false, Modifier.weight(secondaryWeight).fillMaxWidth())
            } else {
                mediaCardComposable(true, Modifier.weight(primaryWeight).fillMaxWidth())
                mapCardComposable(false, Modifier.weight(secondaryWeight).fillMaxWidth())
            }
            customSixCard()
        }
    }
}

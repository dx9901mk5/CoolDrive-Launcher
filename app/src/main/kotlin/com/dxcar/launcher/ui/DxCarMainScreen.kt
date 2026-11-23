package com.dxcar.launcher.ui

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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dxcar.launcher.DxCarApp
import com.dxcar.launcher.SplitSidePanelActivity
import com.dxcar.launcher.accessibility.DxCarAccessibilityService
import com.dxcar.launcher.comms.BluetoothCompanionService
import com.dxcar.launcher.comms.CallActionCommand
import com.dxcar.launcher.comms.CarMessageItem
import com.dxcar.launcher.data.DeviceRole
import com.dxcar.launcher.data.HomeExperimentMode
import com.dxcar.launcher.data.HomeMapDisplayMode
import com.dxcar.launcher.data.MapEngineStyle
import com.dxcar.launcher.data.SplitScreenShortcut
import com.dxcar.launcher.data.ThemeMode
import com.dxcar.launcher.gestures.MultiFingerEvent
import com.dxcar.launcher.gestures.dxCarMultiFingerGestures
import com.dxcar.launcher.layout.CoolwalkLayoutEngine
import com.dxcar.launcher.layout.ResolvedDockPlacement
import com.dxcar.launcher.layout.ScreenMetrics
import com.dxcar.launcher.map.BuiltInNavState
import com.dxcar.launcher.map.LiveMapCard
import com.dxcar.launcher.map.NavSearchResult
import com.dxcar.launcher.overlay.PersistentDockOverlayService
import com.dxcar.launcher.system.InstalledAppInfo
import com.dxcar.launcher.system.PermissionHelper
import com.dxcar.launcher.system.SystemStatusMonitor
import com.dxcar.launcher.theme.DxCarTheme
import com.dxcar.launcher.theme.DayNightEngine
import com.dxcar.launcher.theme.LocalDxCarColors
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
fun DxCarRootScreen(
    app: DxCarApp,
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
    val builtInNavState by app.builtInNavEngine.state.collectAsState()
    val bridgeStatus by BluetoothCompanionService.bridgeState.collectAsState()
    val updateState by app.updateManager.state.collectAsState()

    // Feed live GPS coordinates to Plan B OSRM navigation engine
    LaunchedEffect(location.latitude, location.longitude, config.homeExperimentMode) {
        if (config.homeExperimentMode == HomeExperimentMode.PLAN_B_BUILTIN_NAV && builtInNavState.isNavigating) {
            app.builtInNavEngine.onLocationUpdated(location.latitude, location.longitude)
        }
    }

    var destination by remember { mutableStateOf(LauncherDestination.DASHBOARD_SPLIT) }
    var isMapPrimary by remember { mutableStateOf(true) }
    var isDockTemporarilyHidden by remember { mutableStateOf(false) }
    var showQuickControlCenter by remember { mutableStateOf(false) }
    var showNotificationCenter by remember { mutableStateOf(false) }
    var showSplitCreator by remember { mutableStateOf(false) }
    var pickingShortcutSlotIndex by remember { mutableStateOf<Int?>(null) }
    var latestMapCardRect by remember { mutableStateOf<Rect?>(null) }
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
            com.dxcar.launcher.MainActivity.EXTRA_OPEN_DRAWER -> {
                destination = LauncherDestination.APP_DRAWER
                onExternalNavConsumed()
            }
            com.dxcar.launcher.MainActivity.EXTRA_OPEN_YOUTUBE -> {
                destination = LauncherDestination.FULLSCREEN_YOUTUBE_WEB
                onExternalNavConsumed()
            }
            com.dxcar.launcher.MainActivity.EXTRA_OPEN_QUICK_PANEL -> {
                showQuickControlCenter = true
                onExternalNavConsumed()
            }
            com.dxcar.launcher.MainActivity.EXTRA_OPEN_NOTIFICATIONS -> {
                showNotificationCenter = true
                onExternalNavConsumed()
            }
            com.dxcar.launcher.MainActivity.EXTRA_OPEN_SETTINGS -> {
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

    fun launchAppRespectingOptions(packageName: String): Boolean {
        return app.installedAppsRepository.launchAppSmart(
            packageName = packageName,
            avoidDockFreeform = config.launchAppsAvoidDockFreeform,
            metrics = screenMetrics,
            spec = layoutSpec
        )
    }

    fun launchYoutubeBest() {
        val ytPkg = app.installedAppsRepository.findBestYoutubePackage()
        if (ytPkg != null && launchAppRespectingOptions(ytPkg)) {
            return
        }
        destination = LauncherDestination.FULLSCREEN_YOUTUBE_WEB
    }

    fun launchFreeformPrimaryBox(targetPackage: String? = null) {
        val measured = latestMapCardRect
        val bounds = if (measured != null && measured.width() >= 200 && measured.height() >= 180) {
            measured
        } else {
            val dockPx = if (config.showHomeDock) (layoutSpec.dockThicknessDp * dm.density).toInt() else 0
            val padPx = (12 * dm.density).toInt()
            val availW = (dm.widthPixels - if (layoutSpec.dockPlacement != ResolvedDockPlacement.BOTTOM) dockPx else 0).coerceAtLeast(300)
            val availH = (dm.heightPixels - if (layoutSpec.dockPlacement == ResolvedDockPlacement.BOTTOM) dockPx else 0).coerceAtLeast(240)
            val primaryW = (availW * layoutSpec.primaryWeight).toInt()
            val leftX = when {
                config.rightHandDrive -> availW - primaryW + padPx
                layoutSpec.dockPlacement == ResolvedDockPlacement.LEFT -> dockPx + padPx
                else -> padPx
            }
            Rect(leftX, padPx, (leftX + primaryW - padPx * 2).coerceAtLeast(leftX + 200), availH - padPx)
        }
        val pct = (layoutSpec.primaryWeight * 100).toInt()
        if (targetPackage.isNullOrBlank()) {
            app.installedAppsRepository.launchGoogleMapsFreeform(bounds)
            gestureHudText = "🗺 已以 $pct% 比例貼合開啟真實 Google 地圖"
        } else {
            app.installedAppsRepository.launchPackageFreeform(targetPackage, bounds)
            val label = app.installedAppsRepository.findApp(targetPackage)?.label ?: "導航 App"
            gestureHudText = "🗺 已以 $pct% 比例貼合開啟 $label"
        }
    }

    fun handleCustomShortcutLaunch(token: String) {
        when {
            token == BuiltInShortcutTokens.SETTINGS -> {
                permissionHealth = PermissionHelper.check(context)
                app.installedAppsRepository.refreshHomeLaunchers()
                destination = LauncherDestination.SETTINGS
            }
            token == BuiltInShortcutTokens.YOUTUBE -> launchYoutubeBest()
            token == BuiltInShortcutTokens.DIALER -> app.installedAppsRepository.launchDialer()
            token == BuiltInShortcutTokens.DAY_NIGHT -> {
                val nextMode = if (isNight) ThemeMode.FORCE_DAY else ThemeMode.FORCE_NIGHT
                app.preferences.update { it.copy(themeMode = nextMode) }
                gestureHudText = if (nextMode == ThemeMode.FORCE_NIGHT) "🌙 已切換為夜間模式" else "☀️ 已切換為白天模式"
            }
            token == BuiltInShortcutTokens.SPLIT_GMAPS -> {
                SplitSidePanelActivity.launchWithRealGoogleMaps(context)
            }
            token.startsWith(BuiltInShortcutTokens.SPLIT_PREFIX) -> {
                val id = token.removePrefix(BuiltInShortcutTokens.SPLIT_PREFIX)
                val sc = config.splitShortcuts.firstOrNull { it.id == id }
                if (sc != null) {
                    app.installedAppsRepository.launchSplitShortcut(sc, dm.widthPixels, dm.heightPixels)
                    gestureHudText = "⊞ 正在開啟分屏：${sc.label} (${sc.ratio.label})"
                }
            }
            else -> {
                if (!launchAppRespectingOptions(token)) {
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

    DxCarTheme(
        isNight = isNight,
        accentIndex = config.accentColorIndex,
        stylePreset = config.stylePreset
    ) {
        val colors = LocalDxCarColors.current

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
            return@DxCarTheme
        }

        // AI BOX CAR LAUNCHER ROOT CONTAINER
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.canvasBackground)
                .dxCarMultiFingerGestures(
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
                            slot.id == "youtube" -> launchYoutubeBest()
                            slot.packageName == "ACTION_DIAL" -> app.installedAppsRepository.launchDialer()
                            !slot.packageName.isNullOrBlank() -> {
                                launchAppRespectingOptions(slot.packageName)
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
                    onNotificationsClick = { showNotificationCenter = !showNotificationCenter },
                    onBackClick = {
                        when {
                            showQuickControlCenter -> showQuickControlCenter = false
                            showNotificationCenter -> showNotificationCenter = false
                            showSplitCreator -> showSplitCreator = false
                            pickingShortcutSlotIndex != null -> pickingShortcutSlotIndex = null
                            destination != LauncherDestination.DASHBOARD_SPLIT -> {
                                destination = if (destination == LauncherDestination.SETTINGS) {
                                    LauncherDestination.APP_DRAWER
                                } else {
                                    LauncherDestination.DASHBOARD_SPLIT
                                }
                            }
                            else -> {
                                val acc = DxCarAccessibilityService.instance
                                if (acc != null) {
                                    acc.goBack()
                                } else {
                                    gestureHudText = "請至設定開啟「無障礙服務」以啟用全域返回鍵"
                                }
                            }
                        }
                    },
                    onRecentsClick = {
                        val acc = DxCarAccessibilityService.instance
                        if (acc != null) {
                            acc.openRecents()
                        } else {
                            gestureHudText = "請至設定開啟「無障礙服務」以進入多工視窗"
                        }
                    }
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
                                experimentMode = config.homeExperimentMode,
                                location = location,
                                activeNav = activeNav,
                                builtInNavState = builtInNavState,
                                media = media,
                                isNight = isNight,
                                mapEngineStyle = config.mapEngineStyle,
                                trafficEnabled = config.mapTrafficEnabled,
                                homeMapDisplayMode = config.homeMapDisplayMode,
                                planCNavPackage = config.planCNavPackage,
                                showSixShortcutsInMediaPanel = config.showSixShortcutsInMediaPanel,
                                homeAddress = config.homeAddress,
                                workAddress = config.workAddress,
                                customShortcutSlots = config.customShortcutSlots,
                                splitShortcuts = config.splitShortcuts,
                                installedApps = installedApps,
                                onMapCardBoundsMeasured = { rect -> latestMapCardRect = rect },
                                onPinRealMapsHere = { launchFreeformPrimaryBox(null) },
                                onSwitchToBuiltinMap = {
                                    app.preferences.update { it.copy(homeMapDisplayMode = HomeMapDisplayMode.BUILTIN_LIVE_MAP) }
                                },
                                onSelectMapStyle = { styleItem ->
                                    app.preferences.update { it.copy(mapEngineStyle = styleItem) }
                                    gestureHudText = "🗺 已切換圖層：${styleItem.labelZh}"
                                },
                                onToggleTraffic = {
                                    val nextTraffic = !config.mapTrafficEnabled
                                    app.preferences.update { it.copy(mapTrafficEnabled = nextTraffic) }
                                    gestureHudText = if (nextTraffic) "🚦 已開啟 Google 即時路況" else "🗺 已關閉即時路況圖層"
                                },
                                onToggleMapStyle = {
                                    val allStyles = MapEngineStyle.entries
                                    val nextIdx = (allStyles.indexOf(config.mapEngineStyle) + 1) % allStyles.size
                                    val next = allStyles[nextIdx]
                                    app.preferences.update { it.copy(mapEngineStyle = next) }
                                    gestureHudText = "🗺 切換至 ${next.labelZh}"
                                },
                                onSearchBuiltInPlaces = { q ->
                                    app.builtInNavEngine.searchPlaces(q, location.latitude, location.longitude)
                                },
                                onStartBuiltInNav = { place ->
                                    app.builtInNavEngine.startNavigation(
                                        startLat = location.latitude,
                                        startLon = location.longitude,
                                        destName = place.name,
                                        destLat = place.latitude,
                                        destLon = place.longitude
                                    )
                                },
                                onStopBuiltInNav = { app.builtInNavEngine.stopNavigation() },
                                onSelectPlanCNavPkg = { pkg ->
                                    app.preferences.update { it.copy(planCNavPackage = pkg) }
                                    val label = app.installedAppsRepository.findApp(pkg)?.label ?: pkg
                                    gestureHudText = "🧭 已指定左側導航：$label"
                                },
                                onLaunchPlanCFreeform = { pkg -> launchFreeformPrimaryBox(pkg) },
                                onLaunchPlanCSplit = { pkg -> SplitSidePanelActivity.launchWithRealGoogleMaps(context, pkg) },
                                onLaunchPlanCFullscreen = { pkg -> launchAppRespectingOptions(pkg) },
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
                                    trafficEnabled = config.mapTrafficEnabled,
                                    experimentMode = config.homeExperimentMode,
                                    builtInNavState = builtInNavState,
                                    isPrimary = true,
                                    isFullscreen = true,
                                    homeAddress = config.homeAddress,
                                    workAddress = config.workAddress,
                                    onSelectMapStyle = { styleItem ->
                                        app.preferences.update { it.copy(mapEngineStyle = styleItem) }
                                    },
                                    onToggleTraffic = {
                                        app.preferences.update { it.copy(mapTrafficEnabled = !it.mapTrafficEnabled) }
                                    },
                                    onToggleStyle = {
                                        val allStyles = MapEngineStyle.entries
                                        val next = allStyles[(allStyles.indexOf(config.mapEngineStyle) + 1) % allStyles.size]
                                        app.preferences.update { it.copy(mapEngineStyle = next) }
                                    },
                                    onSearchBuiltInPlaces = { q ->
                                        app.builtInNavEngine.searchPlaces(q, location.latitude, location.longitude)
                                    },
                                    onStartBuiltInNav = { place ->
                                        app.builtInNavEngine.startNavigation(
                                            startLat = location.latitude,
                                            startLon = location.longitude,
                                            destName = place.name,
                                            destLat = place.latitude,
                                            destLon = place.longitude
                                        )
                                    },
                                    onStopBuiltInNav = { app.builtInNavEngine.stopNavigation() },
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
                                splitShortcuts = config.splitShortcuts,
                                hasPendingUpdate = updateState.hasUpdate || updateState.downloadedReady,
                                onLaunchApp = { pkg -> launchAppRespectingOptions(pkg) },
                                onLaunchSplitShortcut = { sc ->
                                    app.installedAppsRepository.launchSplitShortcut(sc, dm.widthPixels, dm.heightPixels)
                                    gestureHudText = "⊞ 正在開啟分屏：${sc.label} (${sc.ratio.label})"
                                },
                                onRemoveSplitShortcut = { sc ->
                                    app.preferences.removeSplitShortcut(sc.id)
                                    gestureHudText = "🗑 已移除分屏捷徑：${sc.label}"
                                },
                                onUninstallApp = { targetApp ->
                                    app.installedAppsRepository.requestUninstallApp(targetApp.packageName)
                                },
                                onOpenSplitCreator = { showSplitCreator = true },
                                onLaunchYoutube = ::launchYoutubeBest,
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
                                installedApps = installedApps,
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
                                onLaunchRealMapsFreeform = { launchFreeformPrimaryBox(null) },
                                onOpenSplitShortcutCreator = { showSplitCreator = true },
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

            val showHomeDockNow = config.showHomeDock && !isDockTemporarilyHidden
            // MAIN DOCK + STAGE LAYOUT (Auto Left / Right / Bottom per Google Coolwalk DPI & User Toggle!)
            when (layoutSpec.dockPlacement) {
                ResolvedDockPlacement.LEFT -> {
                    Row(modifier = Modifier.fillMaxSize()) {
                        if (showHomeDockNow) dockComposable()
                        stageContent(Modifier.weight(1f).fillMaxHeight())
                    }
                }
                ResolvedDockPlacement.RIGHT -> {
                    Row(modifier = Modifier.fillMaxSize()) {
                        stageContent(Modifier.weight(1f).fillMaxHeight())
                        if (showHomeDockNow) dockComposable()
                    }
                }
                ResolvedDockPlacement.BOTTOM -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        stageContent(Modifier.weight(1f).fillMaxWidth())
                        if (showHomeDockNow) dockComposable()
                    }
                }
            }

            // When Home Dock is hidden by user (e.g. using stock AI Box left dock), keep a sleek corner 9-dot menu button
            if (!showHomeDockNow && destination == LauncherDestination.DASHBOARD_SPLIT) {
                Surface(
                    color = colors.glassOverlay,
                    shape = CircleShape,
                    shadowElevation = 8.dp,
                    modifier = Modifier
                        .align(if (config.rightHandDrive) Alignment.BottomEnd else Alignment.BottomStart)
                        .padding(18.dp)
                        .size(50.dp)
                        .border(1.2.dp, colors.accent.copy(alpha = 0.7f), CircleShape)
                        .bouncyClickable(pressedScale = 0.88f) {
                            destination = LauncherDestination.APP_DRAWER
                        }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.Apps,
                            contentDescription = "展開 App 選單",
                            tint = colors.primaryText,
                            modifier = Modifier.size(26.dp)
                        )
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

            // APP PICKER DIALOG FOR THE 6 CUSTOM SHORTCUT SLOTS (Shows Real App Icons + Split Shortcuts!)
            AnimatedVisibility(
                visible = pickingShortcutSlotIndex != null,
                enter = fadeIn(tween(180)) + scaleIn(initialScale = 0.90f, animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMedium)),
                exit = fadeOut(tween(140)) + scaleOut(targetScale = 0.92f, animationSpec = tween(140))
            ) {
                val slotIdx = pickingShortcutSlotIndex ?: return@AnimatedVisibility
                AppPickerDialog(
                    slotIndex = slotIdx,
                    apps = installedApps,
                    splitShortcuts = config.splitShortcuts,
                    onSelectToken = { token, label ->
                        app.preferences.setCustomShortcutSlot(slotIdx, token)
                        pickingShortcutSlotIndex = null
                        gestureHudText = "✓ 已加入「$label」至第 ${slotIdx + 1} 格"
                    },
                    onDismiss = { pickingShortcutSlotIndex = null }
                )
            }

            // SPLIT-SCREEN SHORTCUT CREATOR ("分屏設定" -> 3:7..7:3 -> Save as Dual-Icon Shortcut!)
            AnimatedVisibility(
                visible = showSplitCreator,
                enter = fadeIn(tween(180)) + scaleIn(initialScale = 0.92f, animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMedium)),
                exit = fadeOut(tween(140)) + scaleOut(targetScale = 0.94f, animationSpec = tween(140))
            ) {
                SplitShortcutCreatorDialog(
                    apps = installedApps,
                    onSaveShortcut = { shortcut ->
                        app.preferences.addSplitShortcut(shortcut)
                        showSplitCreator = false
                        gestureHudText = "✓ 創建分屏捷徑成功：${shortcut.label} (${shortcut.ratio.label})"
                    },
                    onDismiss = { showSplitCreator = false }
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
    experimentMode: HomeExperimentMode,
    location: com.dxcar.launcher.map.VehicleLocationState,
    activeNav: com.dxcar.launcher.comms.ActiveNavBanner?,
    builtInNavState: BuiltInNavState,
    media: com.dxcar.launcher.media.ActiveMediaState,
    isNight: Boolean,
    mapEngineStyle: MapEngineStyle,
    trafficEnabled: Boolean,
    homeMapDisplayMode: HomeMapDisplayMode,
    planCNavPackage: String,
    showSixShortcutsInMediaPanel: Boolean,
    homeAddress: String,
    workAddress: String,
    customShortcutSlots: List<String>,
    splitShortcuts: List<SplitScreenShortcut>,
    installedApps: List<InstalledAppInfo>,
    onMapCardBoundsMeasured: (Rect) -> Unit,
    onPinRealMapsHere: () -> Unit,
    onSwitchToBuiltinMap: () -> Unit,
    onSelectMapStyle: (MapEngineStyle) -> Unit,
    onToggleTraffic: () -> Unit,
    onToggleMapStyle: () -> Unit,
    onSearchBuiltInPlaces: (String) -> Unit,
    onStartBuiltInNav: (NavSearchResult) -> Unit,
    onStopBuiltInNav: () -> Unit,
    onSelectPlanCNavPkg: (String) -> Unit,
    onLaunchPlanCFreeform: (String) -> Unit,
    onLaunchPlanCSplit: (String) -> Unit,
    onLaunchPlanCFullscreen: (String) -> Unit,
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
    val colors = LocalDxCarColors.current
    val cardShape = RoundedCornerShape(colors.cardCornerDp.dp)

    val primaryWeight by animateFloatAsState(
        targetValue = primaryRatio,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "primaryWeight"
    )
    val secondaryWeight = (1f - primaryWeight).coerceAtLeast(0.24f)
    val isPlanC = experimentMode == HomeExperimentMode.PLAN_C_APP_AND_MEDIA

    val mapCardComposable: @Composable (Boolean, Modifier) -> Unit = { primary, mod ->
        Box(
            modifier = mod
                .clip(cardShape)
                .border(colors.cardBorderWidthDp.dp, colors.cardBorder, cardShape)
                .onGloballyPositioned { coords ->
                    val b = coords.boundsInWindow()
                    onMapCardBoundsMeasured(
                        Rect(b.left.toInt(), b.top.toInt(), b.right.toInt(), b.bottom.toInt())
                    )
                }
        ) {
            when {
                isPlanC -> {
                    PlanCNavAppCard(
                        selectedNavPackage = planCNavPackage,
                        installedApps = installedApps,
                        activeNav = activeNav,
                        primaryRatio = primaryRatio,
                        onSelectNavPackage = onSelectPlanCNavPkg,
                        onLaunchFreeformHere = onLaunchPlanCFreeform,
                        onLaunchSplitPanel = onLaunchPlanCSplit,
                        onLaunchFullscreen = onLaunchPlanCFullscreen
                    )
                }
                homeMapDisplayMode == HomeMapDisplayMode.REAL_GMAPS_FREEFORM_EMBED -> {
                    Surface(
                        color = colors.cardSurface,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Map,
                                contentDescription = null,
                                tint = colors.accent,
                                modifier = Modifier.size(54.dp)
                            )
                            Spacer(Modifier.height(10.dp))
                            Text(
                                text = "真實 Google 地圖本體貼合模式（${(primaryRatio * 100).toInt()}% 比例）",
                                color = colors.primaryText,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = "點擊下方按鈕即以 Freeform 精準貼合此卡片範圍啟動 Google 地圖本體",
                                color = colors.secondaryText,
                                fontSize = 12.sp
                            )
                            Spacer(Modifier.height(14.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Button(
                                    onClick = onPinRealMapsHere,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = colors.accent,
                                        contentColor = colors.onAccent
                                    ),
                                    shape = RoundedCornerShape(16.dp)
                                ) {
                                    Text("貼合開啟真實 Google 地圖")
                                }
                                OutlinedButton(
                                    onClick = onLaunchRealMapsSplit,
                                    shape = RoundedCornerShape(16.dp)
                                ) {
                                    Text("原生分割模式")
                                }
                                OutlinedButton(
                                    onClick = onSwitchToBuiltinMap,
                                    shape = RoundedCornerShape(16.dp)
                                ) {
                                    Text("切回內建即時地圖")
                                }
                            }
                        }
                    }
                }
                else -> {
                    LiveMapCard(
                        location = location,
                        activeNav = activeNav,
                        isNight = isNight,
                        mapEngineStyle = mapEngineStyle,
                        trafficEnabled = trafficEnabled,
                        experimentMode = experimentMode,
                        builtInNavState = builtInNavState,
                        isPrimary = primary,
                        isFullscreen = false,
                        homeAddress = homeAddress,
                        workAddress = workAddress,
                        onSelectMapStyle = onSelectMapStyle,
                        onToggleTraffic = onToggleTraffic,
                        onToggleStyle = onToggleMapStyle,
                        onSearchBuiltInPlaces = onSearchBuiltInPlaces,
                        onStartBuiltInNav = onStartBuiltInNav,
                        onStopBuiltInNav = onStopBuiltInNav,
                        onSwapRequested = onSwapPanes,
                        onToggleFullscreen = onOpenFullscreenMap,
                        onLaunchGoogleMapsApp = onLaunchRealGoogleMaps,
                        onLaunchRealMapsSplit = onLaunchRealMapsSplit
                    )
                }
            }
        }
    }

    val mediaCardComposable: @Composable (Boolean, Boolean, Modifier) -> Unit = { primary, integrateSix, mod ->
        Box(
            modifier = mod
                .clip(cardShape)
                .border(colors.cardBorderWidthDp.dp, colors.cardBorder, cardShape)
        ) {
            MediaControlCard(
                media = media,
                isPrimary = primary,
                pureMusicMode = isPlanC,
                showSwapButton = !isPlanC,
                showSixShortcuts = integrateSix && showSixShortcutsInMediaPanel,
                customShortcutSlots = customShortcutSlots,
                installedApps = installedApps,
                splitShortcuts = splitShortcuts,
                onPlayPause = onMediaPlayPause,
                onNext = onMediaNext,
                onPrevious = onMediaPrev,
                onSelectSession = onSelectMediaSession,
                onOpenMediaApp = onOpenMediaApp,
                onOpenYoutubeFullscreen = onOpenYoutubeFullscreen,
                onSwapRequested = onSwapPanes,
                onCustomSlotEmptyClick = onCustomSlotEmptyClick,
                onCustomSlotLaunch = onCustomSlotLaunch,
                onCustomSlotRemove = onCustomSlotRemove
            )
        }
    }

    val customSixCard: @Composable () -> Unit = {
        CustomSixAppBarCard(
            slots = customShortcutSlots,
            installedApps = installedApps,
            splitShortcuts = splitShortcuts,
            onSlotEmptyClick = onCustomSlotEmptyClick,
            onSlotLaunch = onCustomSlotLaunch,
            onSlotRemove = onCustomSlotRemove
        )
    }

    if (isHorizontalSplit) {
        val showMapLeft = isMapPrimary || isPlanC
        val primaryPane: @Composable (Modifier) -> Unit = { mod ->
            if (showMapLeft) mapCardComposable(true, mod) else mediaCardComposable(true, false, mod)
        }
        val secondaryColumn: @Composable (Modifier) -> Unit = { mod ->
            Column(
                modifier = mod,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (showMapLeft) {
                    // Integrated Media Controller + optional 6 shortcuts as one unified right-side card!
                    mediaCardComposable(false, supportsThirdSubCard, Modifier.weight(1f).fillMaxWidth())
                } else {
                    mapCardComposable(false, Modifier.weight(1f).fillMaxWidth())
                    if (supportsThirdSubCard && showSixShortcutsInMediaPanel) {
                        customSixCard()
                    }
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
                secondaryColumn(Modifier.weight(secondaryWeight).fillMaxHeight())
                primaryPane(Modifier.weight(primaryWeight).fillMaxHeight())
            } else {
                primaryPane(Modifier.weight(primaryWeight).fillMaxHeight())
                secondaryColumn(Modifier.weight(secondaryWeight).fillMaxHeight())
            }
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (isMapPrimary || isPlanC) {
                mapCardComposable(true, Modifier.weight(primaryWeight).fillMaxWidth())
                mediaCardComposable(false, showSixShortcutsInMediaPanel, Modifier.weight(secondaryWeight).fillMaxWidth())
            } else {
                mediaCardComposable(true, showSixShortcutsInMediaPanel, Modifier.weight(primaryWeight).fillMaxWidth())
                mapCardComposable(false, Modifier.weight(secondaryWeight).fillMaxWidth())
            }
        }
    }
}

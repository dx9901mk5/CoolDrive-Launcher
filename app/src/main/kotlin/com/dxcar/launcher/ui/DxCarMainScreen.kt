package com.dxcar.launcher.ui

import android.content.res.Configuration
import android.graphics.Rect
import android.provider.Settings
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin
import com.dxcar.launcher.DxCarApp
import com.dxcar.launcher.SplitSidePanelActivity
import com.dxcar.launcher.accessibility.DxCarAccessibilityService
import com.dxcar.launcher.comms.BluetoothCompanionService
import com.dxcar.launcher.comms.CallActionCommand
import com.dxcar.launcher.comms.CarMessageItem
import com.dxcar.launcher.comms.RemoteTouchpadController
import com.dxcar.launcher.data.DeviceFormFactor
import com.dxcar.launcher.data.DeviceRole
import com.dxcar.launcher.data.HomeExperimentMode
import com.dxcar.launcher.data.HomeMapDisplayMode
import com.dxcar.launcher.data.MapEngineStyle
import com.dxcar.launcher.data.OfflineRegionRecord
import com.dxcar.launcher.data.OfflineUpdatePolicy
import com.dxcar.launcher.data.SplitScreenShortcut
import com.dxcar.launcher.data.ThemeMode
import com.dxcar.launcher.layout.CoolwalkLayoutEngine
import com.dxcar.launcher.layout.ResolvedDockPlacement
import com.dxcar.launcher.layout.ScreenMetrics
import com.dxcar.launcher.map.BuiltInNavState
import com.dxcar.launcher.map.LiveMapCard
import com.dxcar.launcher.map.NavSearchResult
import com.dxcar.launcher.map.OfflineMapUiState
import com.dxcar.launcher.map.OfflineRegionPreset
import com.dxcar.launcher.overlay.Cursor3DStateHolder
import com.dxcar.launcher.overlay.Interactive3DCursorOverlay
import com.dxcar.launcher.overlay.PersistentDockOverlayService
import com.dxcar.launcher.overlay.RemoteCarMousePointerOverlay
import com.dxcar.launcher.system.InstalledAppInfo
import com.dxcar.launcher.system.PermissionHelper
import com.dxcar.launcher.system.SystemStatusMonitor
import com.dxcar.launcher.theme.DayNightEngine
import com.dxcar.launcher.theme.DxCarTheme
import com.dxcar.launcher.theme.LocalDxCarColors
import com.dxcar.launcher.weather.LocalWeatherState
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
    externalHudMessage: String? = null,
    onExternalNavConsumed: () -> Unit,
    onExternalHudConsumed: () -> Unit = {},
    onRequestRuntimePermissions: () -> Unit,
    onRequestSetDefaultHome: () -> Unit,
    onStartVoiceReply: (CarMessageItem) -> Unit,
    onStartGeminiVoiceNav: () -> Unit = {},
    onPickStockSystemShortcut: () -> Unit = {}
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val scope = rememberCoroutineScope()

    val config by app.preferences.config.collectAsState()
    val sysStatus by app.systemStatusMonitor.status.collectAsState()
    val location by app.locationTracker.location.collectAsState()
    val media by app.mediaControlBridge.mediaState.collectAsState()
    val weather by app.weatherRepository.weather.collectAsState()
    val offlineState by app.offlineMapManager.state.collectAsState()
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
    val cursor3DActive by Cursor3DStateHolder.active.collectAsState()
    val cursor3DTutorial by Cursor3DStateHolder.showTutorial.collectAsState()
    val remoteCursor by RemoteTouchpadController.cursorState.collectAsState()
    val remoteSwapSeq by RemoteTouchpadController.swapHomePanesSeq.collectAsState()

    // Auto-detect Smartphone vs AI Box Car Head Unit on first launch when not pinned by user
    LaunchedEffect(configuration.screenWidthDp, configuration.screenHeightDp, config.roleUserPinned) {
        if (config.deviceRole == DeviceRole.AI_BOX_LAUNCHER &&
            DeviceFormFactor.shouldAutoDefaultToPhone(context, configuration, config)
        ) {
            app.preferences.update {
                it.copy(deviceRole = DeviceRole.PHONE_COMPANION)
            }
        }
    }

    // Feed live GPS coordinates to LocalWeatherRepository, BuiltInNavEngine & OfflineMapManager
    LaunchedEffect(location.latitude, location.longitude, config.offlineUpdatePolicy, config.offlineRegions.size) {
        app.weatherRepository.onLocationUpdated(location.latitude, location.longitude)
        app.builtInNavEngine.onLocationUpdated(location.latitude, location.longitude)
        app.offlineMapManager.evaluateUpdateNeeded(location.latitude, location.longitude)
    }

    var destination by remember { mutableStateOf(LauncherDestination.DASHBOARD_SPLIT) }
    var currentDashboardPage by remember { mutableIntStateOf(0) } // 0 = 第一介面, 1 = 第二介面 (1:1 方案A地圖 + 內嵌YouTube)
    var isMapPrimary by remember { mutableStateOf(true) }
    var showQuickControlCenter by remember { mutableStateOf(false) }
    var showNotificationCenter by remember { mutableStateOf(false) }
    var showSplitCreator by remember { mutableStateOf(false) }
    var pickingShortcutSlotIndex by remember { mutableStateOf<Int?>(null) }
    var latestMapCardRect by remember { mutableStateOf<Rect?>(null) }
    var gestureHudText by remember { mutableStateOf<String?>(null) }
    var permissionHealth by remember { mutableStateOf(PermissionHelper.check(context)) }
    var pairedBtDevices by remember { mutableStateOf(app.systemStatusMonitor.getPairedBluetoothDevices()) }

    LaunchedEffect(destination, homeLaunchers) {
        if (destination == LauncherDestination.SETTINGS) {
            permissionHealth = PermissionHelper.check(context)
        }
    }

    LaunchedEffect(gestureHudText) {
        if (gestureHudText != null) {
            delay(2_400L)
            gestureHudText = null
        }
    }

    LaunchedEffect(externalHudMessage) {
        if (!externalHudMessage.isNullOrBlank()) {
            gestureHudText = externalHudMessage
            onExternalHudConsumed()
        }
    }

    LaunchedEffect(remoteSwapSeq) {
        if (remoteSwapSeq > 0L) {
            isMapPrimary = !isMapPrimary
            gestureHudText = "⇄ 已從手機多指觸控切換首頁分欄"
        }
    }

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

    // Persistent Overlay Dock runs ONLY on Car / Tablet mode (never on compact Phone)
    LaunchedEffect(config.deviceRole, config.persistentOverlayDockEnabled, configuration.screenWidthDp) {
        val isPhone = DeviceFormFactor.isCompactPhoneScreen(configuration)
        if (config.deviceRole == DeviceRole.AI_BOX_LAUNCHER && !isPhone && config.persistentOverlayDockEnabled) {
            PersistentDockOverlayService.start(context)
        } else {
            PersistentDockOverlayService.stop(context)
        }
    }

    LaunchedEffect(config.deviceRole, config.companionTargetDeviceAddress, config.companionAutoConnect) {
        if (config.companionAutoConnect) {
            if (BluetoothCompanionService.shouldPhoneBridgeRunNow(context)) {
                BluetoothCompanionService.startOrReconnect(context)
            } else {
                BluetoothCompanionService.markStandby(context)
            }
        }
    }

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

    fun toggle3DPointerGlobal() {
        if (Settings.canDrawOverlays(context)) {
            PersistentDockOverlayService.toggle3DCursor(context)
        } else {
            Cursor3DStateHolder.toggle()
        }
    }

    fun launchFreeformPrimaryBox(targetPackage: String? = null) {
        val measured = latestMapCardRect
        val bounds = if (measured != null && measured.width() >= 200 && measured.height() >= 180) {
            measured
        } else {
            val dockPx = if (config.showHomeDock) (layoutSpec.dockThicknessDp * dm.density).toInt() else 0
            val padPx = (12 * dm.density).toInt()
            val availW = (dm.widthPixels - if (!layoutSpec.dockPlacement.isHorizontalBar) dockPx else 0).coerceAtLeast(300)
            val topY = if (layoutSpec.dockPlacement == ResolvedDockPlacement.TOP) dockPx + padPx else padPx
            val bottomY = (dm.heightPixels - if (layoutSpec.dockPlacement == ResolvedDockPlacement.BOTTOM) dockPx + padPx else padPx).coerceAtLeast(topY + 200)
            val primaryW = (availW * layoutSpec.primaryWeight).toInt()
            val leftX = when {
                config.rightHandDrive -> availW - primaryW + padPx
                layoutSpec.dockPlacement == ResolvedDockPlacement.LEFT -> dockPx + padPx
                else -> padPx
            }
            Rect(leftX, topY, (leftX + primaryW - padPx * 2).coerceAtLeast(leftX + 200), bottomY)
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

    fun dispatchSplitShortcutWithDockOffsets(sc: SplitScreenShortcut) {
        val dockPx = if (config.showHomeDock) (layoutSpec.dockThicknessDp * dm.density).toInt() else 0
        app.installedAppsRepository.launchSplitShortcut(
            shortcut = sc,
            screenWidthPx = dm.widthPixels,
            screenHeightPx = dm.heightPixels,
            dockOffsetLeftPx = if (layoutSpec.dockPlacement == ResolvedDockPlacement.LEFT) dockPx else 0,
            dockOffsetRightPx = if (layoutSpec.dockPlacement == ResolvedDockPlacement.RIGHT) dockPx else 0,
            dockOffsetTopPx = if (layoutSpec.dockPlacement == ResolvedDockPlacement.TOP) dockPx else 0,
            dockOffsetBottomPx = if (layoutSpec.dockPlacement == ResolvedDockPlacement.BOTTOM) dockPx else 0
        )
        gestureHudText = "⊞ 正在開啟 AOSP 分屏：${sc.label} (${sc.ratio.label})"
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
                if (sc != null) dispatchSplitShortcutWithDockOffsets(sc)
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
                installedApps = installedApps,
                bridgeStatus = bridgeStatus,
                permissionHealth = permissionHealth,
                updateState = updateState,
                onCheckUpdate = {
                    scope.launch { app.updateManager.checkForUpdate(autoDownload = false) }
                },
                onDownloadOrInstallUpdate = {
                    if (updateState.downloadedReady) {
                        app.updateManager.installDownloadedApk()
                    } else {
                        scope.launch { app.updateManager.downloadAndInstall(autoLaunchInstaller = true) }
                    }
                },
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
                    BluetoothCompanionService.forceReconnect(context)
                },
                onToggleAllowedPackage = { pkg ->
                    app.preferences.update { current ->
                        val next = current.companionAllowedPackages.toMutableList()
                        if (next.contains(pkg)) next.remove(pkg) else next.add(pkg)
                        current.copy(companionAllowedPackages = next)
                    }
                },
                onSetAllowedPackages = { pkgList ->
                    app.preferences.update { it.copy(companionAllowedPackages = pkgList.distinct()) }
                },
                onToggleSyncAllApps = { checked ->
                    app.preferences.update { it.copy(companionSyncAllApps = checked) }
                },
                onChangeTouchpadSensitivity = { sens ->
                    app.preferences.update { it.copy(phoneTouchpadSensitivity = sens) }
                },
                onRequestRuntimePermissions = onRequestRuntimePermissions,
                onReconnectBridge = { BluetoothCompanionService.forceReconnect(context) },
                onSwitchToAiBoxMode = {
                    app.preferences.update {
                        it.copy(deviceRole = DeviceRole.AI_BOX_LAUNCHER, roleUserPinned = true)
                    }
                    BluetoothCompanionService.startOrReconnect(context)
                }
            )
            return@DxCarTheme
        }

        // AI BOX CAR LAUNCHER ROOT CONTAINER (Multi-finger gestures removed per single-touch car display spec)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.canvasBackground)
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
                    is3DCursorActive = cursor3DActive,
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
                            !slot.packageName.isNullOrBlank() -> launchAppRespectingOptions(slot.packageName)
                            else -> destination = LauncherDestination.APP_DRAWER
                        }
                    },
                    // Top-left Wi-Fi click expands Dropdown Quick Toggles Shade! Long-press opens System Wi-Fi!
                    onWifiClick = { showQuickControlCenter = !showQuickControlCenter },
                    onWifiLongClick = { SystemStatusMonitor.openWifiSettings(context) },
                    onBluetoothClick = { SystemStatusMonitor.openBluetoothSettings(context) },
                    onBluetoothLongClick = {
                        permissionHealth = PermissionHelper.check(context)
                        app.installedAppsRepository.refreshHomeLaunchers()
                        destination = LauncherDestination.SETTINGS
                    },
                    onClockClick = { showQuickControlCenter = !showQuickControlCenter },
                    onNotificationsClick = { showNotificationCenter = !showNotificationCenter },
                    onToggle3DCursor = ::toggle3DPointerGlobal,
                    onVoiceNavClick = {
                        destination = LauncherDestination.DASHBOARD_SPLIT
                        onStartGeminiVoiceNav()
                    },
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
                                    gestureHudText = "請點設定最上方「權限需求」開啟無障礙服務"
                                }
                            }
                        }
                    },
                    onRecentsClick = {
                        val acc = DxCarAccessibilityService.instance
                        if (acc != null) {
                            acc.openRecents()
                        } else {
                            gestureHudText = "請點設定最上方「權限需求」開啟無障礙服務"
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
                                    animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow),
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
                            val startLatFallback = if (location.latitude != 0.0) location.latitude else config.lastKnownLat
                            val startLonFallback = if (location.longitude != 0.0) location.longitude else config.lastKnownLon

                            InfiniteLoopTwoPageDashboard(
                                currentPage = currentDashboardPage,
                                onPageChanged = { nextPage ->
                                    currentDashboardPage = nextPage
                                    gestureHudText = if (nextPage == 0) {
                                        "① 第一介面 · 導航與音樂天氣"
                                    } else {
                                        "② 第二介面 · 1:1 方案A地圖 ＋ YouTube 視窗"
                                    }
                                },
                                pageOneContent = { onSeamDragDelta, onSeamDragEnd ->
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
                                        weather = weather,
                                        offlineState = offlineState,
                                        offlinePresets = app.offlineMapManager.presets,
                                        downloadedRegions = config.offlineRegions,
                                        offlineUpdatePolicy = config.offlineUpdatePolicy,
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
                                        onSeamDragDelta = onSeamDragDelta,
                                        onSeamDragEnd = onSeamDragEnd,
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
                                            val next = allStyles[(allStyles.indexOf(config.mapEngineStyle) + 1) % allStyles.size]
                                            app.preferences.update { it.copy(mapEngineStyle = next) }
                                            gestureHudText = "🗺 切換至 ${next.labelZh}"
                                        },
                                        onSelectOfflinePolicy = { pol ->
                                            app.preferences.update { it.copy(offlineUpdatePolicy = pol) }
                                        },
                                        onDownloadOfflineRegion = { presetId ->
                                            app.offlineMapManager.downloadOrUpdatePreset(
                                                presetId = presetId,
                                                currentLat = location.latitude,
                                                currentLon = location.longitude,
                                                style = config.mapEngineStyle
                                            )
                                        },
                                        onCancelOfflineDownload = { app.offlineMapManager.cancelCurrentDownload() },
                                        onDeleteOfflineRegion = { rid -> app.offlineMapManager.deleteDownloadedRegion(rid) },
                                        onDismissOfflineSuggestion = { app.offlineMapManager.dismissUpdateSuggestion() },
                                        onSelectPlanCNavPkg = { pkg ->
                                            app.preferences.update { it.copy(planCNavPackage = pkg) }
                                            val label = app.installedAppsRepository.findApp(pkg)?.label ?: pkg
                                            gestureHudText = "🧭 已指定左側導航：$label"
                                        },
                                        onLaunchPlanCFreeform = { pkg ->
                                            launchFreeformPrimaryBox(pkg)
                                            if (config.planCOverlayMusicPanelEnabled) {
                                                if (Settings.canDrawOverlays(context)) {
                                                    PersistentDockOverlayService.showPlanCMusicPanel(context)
                                                } else {
                                                    gestureHudText = "請點設定最上方「權限需求」授予懸浮權限以顯示右側音樂控盤"
                                                }
                                            }
                                        },
                                        onLaunchPlanCSplit = { pkg -> SplitSidePanelActivity.launchWithRealGoogleMaps(context, pkg) },
                                        onLaunchPlanCFullscreen = { pkg ->
                                            PersistentDockOverlayService.hidePlanCMusicPanel()
                                            launchAppRespectingOptions(pkg)
                                        },
                                        onSwapPanes = { isMapPrimary = !isMapPrimary },
                                        onOpenFullscreenMap = { destination = LauncherDestination.FULLSCREEN_MAP },
                                        onLaunchRealGoogleMaps = { query ->
                                            app.installedAppsRepository.launchGoogleMapsNavigation(query)
                                        },
                                        onLaunchRealMapsSplit = {
                                            SplitSidePanelActivity.launchWithRealGoogleMaps(context)
                                        },
                                        onVoiceNavClick = onStartGeminiVoiceNav,
                                        onSearchBuiltInPlace = { q ->
                                            app.builtInNavEngine.searchPlaces(q, startLatFallback, startLonFallback)
                                        },
                                        onStartBuiltInNavResult = { res ->
                                            app.builtInNavEngine.startNavigation(
                                                startLat = startLatFallback,
                                                startLon = startLonFallback,
                                                destName = res.name,
                                                destLat = res.latitude,
                                                destLon = res.longitude
                                            )
                                        },
                                        onQuickBuiltInNavigateQuery = { q ->
                                            app.builtInNavEngine.navigateByQuery(
                                                query = q,
                                                startLat = startLatFallback,
                                                startLon = startLonFallback,
                                                onStatus = { msg -> gestureHudText = msg }
                                            )
                                        },
                                        onStopBuiltInNav = { app.builtInNavEngine.stopNavigation() },
                                        onWeatherClick = { app.weatherRepository.openGoogleWeatherApp(context) },
                                        onMediaPlayPause = { app.mediaControlBridge.togglePlayPause() },
                                        onMediaNext = { app.mediaControlBridge.skipNext() },
                                        onMediaPrev = { app.mediaControlBridge.skipPrevious() },
                                        onMediaToggleFavorite = {
                                            val (_, msg) = app.mediaControlBridge.toggleFavoriteOrLike()
                                            gestureHudText = msg
                                        },
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
                                },
                                pageTwoContent = { onSeamDragDelta, onSeamDragEnd ->
                                    SecondEqualMapYoutubeDashboard(
                                        isHorizontalSplit = layoutSpec.isHorizontalSplit,
                                        location = location,
                                        activeNav = activeNav,
                                        builtInNavState = builtInNavState,
                                        offlineState = offlineState,
                                        offlinePresets = app.offlineMapManager.presets,
                                        downloadedRegions = config.offlineRegions,
                                        offlineUpdatePolicy = config.offlineUpdatePolicy,
                                        isNight = isNight,
                                        mapEngineStyle = config.mapEngineStyle,
                                        trafficEnabled = config.mapTrafficEnabled,
                                        homeAddress = config.homeAddress,
                                        workAddress = config.workAddress,
                                        onSeamDragDelta = onSeamDragDelta,
                                        onSeamDragEnd = onSeamDragEnd,
                                        onSelectMapStyle = { styleItem ->
                                            app.preferences.update { it.copy(mapEngineStyle = styleItem) }
                                        },
                                        onToggleTraffic = {
                                            app.preferences.update { it.copy(mapTrafficEnabled = !it.mapTrafficEnabled) }
                                        },
                                        onToggleMapStyle = {
                                            val allStyles = MapEngineStyle.entries
                                            val next = allStyles[(allStyles.indexOf(config.mapEngineStyle) + 1) % allStyles.size]
                                            app.preferences.update { it.copy(mapEngineStyle = next) }
                                        },
                                        onSelectOfflinePolicy = { pol ->
                                            app.preferences.update { it.copy(offlineUpdatePolicy = pol) }
                                        },
                                        onDownloadOfflineRegion = { presetId ->
                                            app.offlineMapManager.downloadOrUpdatePreset(presetId, location.latitude, location.longitude, config.mapEngineStyle)
                                        },
                                        onCancelOfflineDownload = { app.offlineMapManager.cancelCurrentDownload() },
                                        onDeleteOfflineRegion = { rid -> app.offlineMapManager.deleteDownloadedRegion(rid) },
                                        onDismissOfflineSuggestion = { app.offlineMapManager.dismissUpdateSuggestion() },
                                        onOpenFullscreenMap = { destination = LauncherDestination.FULLSCREEN_MAP },
                                        onLaunchRealGoogleMaps = { query ->
                                            app.installedAppsRepository.launchGoogleMapsNavigation(query)
                                        },
                                        onLaunchRealMapsSplit = {
                                            SplitSidePanelActivity.launchWithRealGoogleMaps(context)
                                        },
                                        onVoiceNavClick = onStartGeminiVoiceNav,
                                        onSearchBuiltInPlace = { q ->
                                            app.builtInNavEngine.searchPlaces(q, startLatFallback, startLonFallback)
                                        },
                                        onStartBuiltInNavResult = { res ->
                                            app.builtInNavEngine.startNavigation(
                                                startLat = startLatFallback,
                                                startLon = startLonFallback,
                                                destName = res.name,
                                                destLat = res.latitude,
                                                destLon = res.longitude
                                            )
                                        },
                                        onQuickBuiltInNavigateQuery = { q ->
                                            app.builtInNavEngine.navigateByQuery(
                                                query = q,
                                                startLat = startLatFallback,
                                                startLon = startLonFallback,
                                                onStatus = { msg -> gestureHudText = msg }
                                            )
                                        },
                                        onStopBuiltInNav = { app.builtInNavEngine.stopNavigation() }
                                    )
                                }
                            )
                        }

                        LauncherDestination.FULLSCREEN_MAP -> {
                            val startLatFallback = if (location.latitude != 0.0) location.latitude else config.lastKnownLat
                            val startLonFallback = if (location.longitude != 0.0) location.longitude else config.lastKnownLon
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(10.dp)
                                    .clip(RoundedCornerShape(colors.cardCornerDp.dp))
                                    .border(colors.cardBorderWidthDp.dp, colors.cardBorder, RoundedCornerShape(colors.cardCornerDp.dp))
                            ) {
                                LiveMapCard(
                                    location = location,
                                    activeNav = activeNav,
                                    builtInNavState = builtInNavState,
                                    isNight = isNight,
                                    mapEngineStyle = config.mapEngineStyle,
                                    trafficEnabled = config.mapTrafficEnabled,
                                    offlineState = offlineState,
                                    offlinePresets = app.offlineMapManager.presets,
                                    downloadedRegions = config.offlineRegions,
                                    offlineUpdatePolicy = config.offlineUpdatePolicy,
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
                                    onSelectOfflinePolicy = { pol ->
                                        app.preferences.update { it.copy(offlineUpdatePolicy = pol) }
                                    },
                                    onDownloadOfflineRegion = { presetId ->
                                        app.offlineMapManager.downloadOrUpdatePreset(presetId, location.latitude, location.longitude, config.mapEngineStyle)
                                    },
                                    onCancelOfflineDownload = { app.offlineMapManager.cancelCurrentDownload() },
                                    onDeleteOfflineRegion = { rid -> app.offlineMapManager.deleteDownloadedRegion(rid) },
                                    onDismissOfflineSuggestion = { app.offlineMapManager.dismissUpdateSuggestion() },
                                    onSwapRequested = { destination = LauncherDestination.DASHBOARD_SPLIT },
                                    onToggleFullscreen = { destination = LauncherDestination.DASHBOARD_SPLIT },
                                    onLaunchGoogleMapsApp = { q ->
                                        app.installedAppsRepository.launchGoogleMapsNavigation(q)
                                    },
                                    onLaunchRealMapsSplit = {
                                        SplitSidePanelActivity.launchWithRealGoogleMaps(context)
                                    },
                                    onVoiceNavClick = onStartGeminiVoiceNav,
                                    onSearchBuiltInPlace = { q ->
                                        app.builtInNavEngine.searchPlaces(q, startLatFallback, startLonFallback)
                                    },
                                    onStartBuiltInNavResult = { res ->
                                        app.builtInNavEngine.startNavigation(startLatFallback, startLonFallback, res.name, res.latitude, res.longitude)
                                    },
                                    onQuickBuiltInNavigateQuery = { q ->
                                        app.builtInNavEngine.navigateByQuery(q, startLatFallback, startLonFallback) { gestureHudText = it }
                                    },
                                    onStopBuiltInNav = { app.builtInNavEngine.stopNavigation() }
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
                                appFolders = config.appFolders,
                                hasPendingUpdate = updateState.hasUpdate || updateState.downloadedReady,
                                onLaunchApp = { pkg -> launchAppRespectingOptions(pkg) },
                                onLaunchSplitShortcut = ::dispatchSplitShortcutWithDockOffsets,
                                onRemoveSplitShortcut = { sc ->
                                    app.preferences.removeSplitShortcut(sc.id)
                                    gestureHudText = "🗑 已移除分屏捷徑：${sc.label}"
                                },
                                onUninstallApp = { targetApp ->
                                    val launched = app.installedAppsRepository.requestUninstallApp(targetApp.packageName)
                                    if (!launched) {
                                        app.installedAppsRepository.openPackageDetails(targetApp.packageName)
                                    }
                                },
                                onOpenAppInfo = { targetApp ->
                                    app.installedAppsRepository.openPackageDetails(targetApp.packageName)
                                },
                                onCreateFolder = { name, pkgs ->
                                    app.preferences.createAppFolder(name, pkgs)
                                    gestureHudText = "📁 已建立資料夾：$name"
                                },
                                onAddAppToFolder = { folderId, pkg ->
                                    app.preferences.addAppToFolder(folderId, pkg)
                                    gestureHudText = "📁 已加入資料夾"
                                },
                                onRenameFolder = { folderId, newName ->
                                    app.preferences.renameAppFolder(folderId, newName)
                                },
                                onRemoveAppFromFolder = { folderId, pkg ->
                                    app.preferences.removeAppFromFolder(folderId, pkg)
                                },
                                onDeleteFolder = { folderId ->
                                    app.preferences.deleteAppFolder(folderId)
                                    gestureHudText = "📁 已解散資料夾"
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
                                offlineState = offlineState,
                                offlinePresets = app.offlineMapManager.presets,
                                onDownloadOfflineRegion = { presetId ->
                                    app.offlineMapManager.downloadOrUpdatePreset(
                                        presetId = presetId,
                                        currentLat = location.latitude,
                                        currentLon = location.longitude,
                                        style = config.mapEngineStyle
                                    )
                                },
                                onCancelOfflineDownload = { app.offlineMapManager.cancelCurrentDownload() },
                                onStartVoiceNavigation = {
                                    destination = LauncherDestination.DASHBOARD_SPLIT
                                    onStartGeminiVoiceNav()
                                },
                                onUpdateConfig = { transform -> app.preferences.update(transform) },
                                onRequestSetDefaultHome = {
                                    onRequestSetDefaultHome()
                                    app.installedAppsRepository.refreshHomeLaunchers()
                                },
                                onSwitchToLauncher = { targetLauncher ->
                                    val launched = app.installedAppsRepository.launchHomeLauncher(targetLauncher)
                                    gestureHudText = if (launched) "🏠 正在切換至 ${targetLauncher.label}" else "無法啟動 ${targetLauncher.label}"
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
                                    app.preferences.update {
                                        it.copy(deviceRole = DeviceRole.PHONE_COMPANION, roleUserPinned = true)
                                    }
                                    if (BluetoothCompanionService.shouldPhoneBridgeRunNow(context)) {
                                        BluetoothCompanionService.startOrReconnect(context)
                                    } else {
                                        BluetoothCompanionService.markStandby(context)
                                    }
                                },
                                onClose = { destination = LauncherDestination.APP_DRAWER }
                            )
                        }
                    }
                }
            }

            val showHomeDockNow = config.showHomeDock
            // MAIN DOCK + STAGE LAYOUT (Left / Right / Top / Bottom!)
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
                ResolvedDockPlacement.TOP -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        if (showHomeDockNow) dockComposable()
                        stageContent(Modifier.weight(1f).fillMaxWidth())
                    }
                }
                ResolvedDockPlacement.BOTTOM -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        stageContent(Modifier.weight(1f).fillMaxWidth())
                        if (showHomeDockNow) dockComposable()
                    }
                }
            }

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
                        Icon(Icons.Rounded.Apps, contentDescription = "展開 App 選單", tint = colors.primaryText, modifier = Modifier.size(26.dp))
                    }
                }
            }

            // AUTO-UPDATE BANNER
            AnimatedVisibility(
                visible = (updateState.downloading || updateState.downloadedReady) && destination == LauncherDestination.DASHBOARD_SPLIT,
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
                        if (updateState.downloadedReady) {
                            app.updateManager.installDownloadedApk()
                        }
                    }
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.CloudDownload, contentDescription = null, tint = colors.onAccent)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = if (updateState.downloadedReady) {
                                    "新版本 v${updateState.latestVersion} 已下載完成 · 點擊立即安裝"
                                } else {
                                    updateState.statusMessage.ifBlank { "正在下載更新…" }
                                },
                                color = colors.onAccent,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        if (updateState.downloading) {
                            if (updateState.totalBytes > 0L) {
                                LinearProgressIndicator(
                                    progress = { updateState.progress },
                                    color = colors.onAccent,
                                    trackColor = colors.onAccent.copy(alpha = 0.28f),
                                    modifier = Modifier.width(240.dp).height(5.dp).clip(RoundedCornerShape(3.dp))
                                )
                            } else {
                                LinearProgressIndicator(
                                    color = colors.onAccent,
                                    trackColor = colors.onAccent.copy(alpha = 0.28f),
                                    modifier = Modifier.width(240.dp).height(5.dp).clip(RoundedCornerShape(3.dp))
                                )
                            }
                        }
                    }
                }
            }

            // INCOMING CALL & MESSAGE BANNERS (Top-Left Corner + 2X Extra-Large Font + Left/Right Touch Swipe Dismiss!)
            val bannerStartPad = if (showHomeDockNow && layoutSpec.dockPlacement == ResolvedDockPlacement.LEFT) {
                (layoutSpec.dockThicknessDp + 14).dp
            } else {
                16.dp
            }
            val bannerTopPad = if (showHomeDockNow && layoutSpec.dockPlacement == ResolvedDockPlacement.TOP) {
                (layoutSpec.dockThicknessDp + 12).dp
            } else {
                14.dp
            }
            Column(
                horizontalAlignment = Alignment.Start,
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(top = bannerTopPad, start = bannerStartPad, end = 20.dp)
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

            // HUD TOAST PILL
            GestureFeedbackPill(
                text = gestureHudText,
                modifier = Modifier.align(Alignment.Center)
            )

            // DROPDOWN QUICK TOGGLES SHADE (Opened by tapping Top-Left Wi-Fi icon OR Clock!)
            AnimatedVisibility(
                visible = showQuickControlCenter,
                enter = slideInVertically(initialOffsetY = { -it / 2 }, animationSpec = tween(190)) + fadeIn(tween(160)),
                exit = slideOutVertically(targetOffsetY = { -it / 2 }, animationSpec = tween(140)) + fadeOut(tween(130))
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
                    onOpenSystemQuickShade = {
                        val ok = DxCarAccessibilityService.instance?.openQuickSettings() == true
                        if (!ok) {
                            gestureHudText = "開啟無障礙服務即可拉下系統原生通知欄"
                        }
                    },
                    onToggle3DCursor = ::toggle3DPointerGlobal,
                    onOpenSettings = {
                        permissionHealth = PermissionHelper.check(context)
                        app.installedAppsRepository.refreshHomeLaunchers()
                        destination = LauncherDestination.SETTINGS
                    },
                    onDismiss = { showQuickControlCenter = false }
                )
            }

            // NOTIFICATION CENTER OVERLAY
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

            // 6 CUSTOM SHORTCUT SLOTS PICKER
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

            // SPLIT-SCREEN SHORTCUT CREATOR
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
                    onTestLaunchShortcut = ::dispatchSplitShortcutWithDockOffsets,
                    onPickSystemShortcut = onPickStockSystemShortcut,
                    onDismiss = { showSplitCreator = false }
                )
            }

            // Fallback in-app 3D Cursor & Remote Mouse Cursor when SYSTEM_ALERT_WINDOW isn't enabled yet
            if (!Settings.canDrawOverlays(context)) {
                if (cursor3DActive) {
                    Interactive3DCursorOverlay(
                        showTutorial = cursor3DTutorial,
                        onOpenTutorial = { Cursor3DStateHolder.openTutorial() },
                        onCloseTutorial = { Cursor3DStateHolder.closeTutorial() },
                        onExit3DMode = { Cursor3DStateHolder.setActive(false) },
                        onSetWindowPassThrough = {}
                    )
                }
                RemoteCarMousePointerOverlay(cursorState = remoteCursor)
            }
        }
    }
}

/**
 * Infinite-Loop Two-Interface Dashboard Container:
 * - Page 0 (第一介面): Coolwalk Split Dashboard (70/30 adjustable, Map + Weather/Media/6-Shortcuts)
 * - Page 1 (第二介面): 1:1 Equal Split Dashboard (Left = Plan A Built-in Map, Right = Inline Playable YouTube)
 * - Dragging the center divider line ("兩個視窗中間的線") rightward (or leftward) slides smoothly and wraps infinitely (0 -> 1 -> 0 -> 1)!
 */
@Composable
private fun InfiniteLoopTwoPageDashboard(
    currentPage: Int,
    onPageChanged: (Int) -> Unit,
    pageOneContent: @Composable (onSeamDragDelta: (Float) -> Unit, onSeamDragEnd: () -> Unit) -> Unit,
    pageTwoContent: @Composable (onSeamDragDelta: (Float) -> Unit, onSeamDragEnd: () -> Unit) -> Unit
) {
    val scope = rememberCoroutineScope()
    val dragPx = remember { Animatable(0f) }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val widthPx = constraints.maxWidth.toFloat().coerceAtLeast(320f)
        val thresholdPx = (widthPx * 0.10f).coerceIn(55f, 150f)

        val handleDragDelta: (Float) -> Unit = { delta ->
            scope.launch {
                val next = (dragPx.value + delta).coerceIn(-widthPx * 0.95f, widthPx * 0.95f)
                dragPx.snapTo(next)
            }
        }

        val handleDragEnd: () -> Unit = {
            val cur = dragPx.value
            scope.launch {
                if (abs(cur) >= thresholdPx) {
                    val target = if (cur >= 0f) widthPx else -widthPx
                    dragPx.animateTo(
                        targetValue = target,
                        animationSpec = tween(
                            durationMillis = 360,
                            easing = CubicBezierEasing(0.25f, 0.1f, 0.25f, 1.0f)
                        )
                    )
                    // Infinite cyclic wrap-around: 0 -> 1 -> 0 -> 1 ...
                    onPageChanged((currentPage + 1) % 2)
                    dragPx.snapTo(0f)
                } else {
                    dragPx.animateTo(0f, spring(stiffness = Spring.StiffnessMedium))
                }
            }
        }

        val curDx = dragPx.value
        val isDragging = abs(curDx) > 0.5f

        val page0OffsetX = when {
            currentPage == 0 -> curDx.roundToInt()
            !isDragging -> -constraints.maxWidth - 40
            curDx >= 0f -> (-widthPx + curDx).roundToInt()
            else -> (widthPx + curDx).roundToInt()
        }

        val page1OffsetX = when {
            currentPage == 1 -> curDx.roundToInt()
            !isDragging -> -constraints.maxWidth - 40
            curDx >= 0f -> (-widthPx + curDx).roundToInt()
            else -> (widthPx + curDx).roundToInt()
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .zIndex(if (currentPage == 0) 2f else 1f)
                .offset { IntOffset(page0OffsetX, 0) }
        ) {
            pageOneContent(handleDragDelta, handleDragEnd)
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .zIndex(if (currentPage == 1) 2f else 1f)
                .offset { IntOffset(page1OffsetX, 0) }
        ) {
            pageTwoContent(handleDragDelta, handleDragEnd)
        }
    }
}

/**
 * Interactive Center Seam Divider Handle between Left & Right windows:
 * Slide rightward (or leftward) to switch infinitely between 第一介面 and 第二介面!
 */
@Composable
private fun DashboardSeamSwipeHandle(
    pageIndicator: String,
    onDragDelta: (Float) -> Unit,
    onDragEnd: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalDxCarColors.current
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .pointerInput(pageIndicator) {
                detectHorizontalDragGestures(
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        onDragDelta(dragAmount)
                    },
                    onDragEnd = onDragEnd,
                    onDragCancel = onDragEnd
                )
            }
    ) {
        // Subtle vertical center seam line
        Box(
            modifier = Modifier
                .width(3.dp)
                .fillMaxHeight(0.76f)
                .clip(RoundedCornerShape(2.dp))
                .background(colors.accent.copy(alpha = 0.35f))
        )

        // Tactile Center Grip Pill ("往右滑動切換介面")
        Surface(
            color = colors.cardSurfaceElevated.copy(alpha = 0.94f),
            shape = RoundedCornerShape(12.dp),
            shadowElevation = 6.dp,
            modifier = Modifier
                .border(1.2.dp, colors.accent.copy(alpha = 0.85f), RoundedCornerShape(12.dp))
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(3.dp),
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .height(22.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(colors.accent)
                )
                Text(
                    text = pageIndicator,
                    color = colors.primaryText,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }
    }
}

/**
 * 第二介面 (Page 2): Exact 1:1 Ratio Split Dashboard
 * - Left 50%: Plan A Built-In Live Map (with Gemini Built-in Voice Navigation & Route Line)
 * - Center Seam: Interactive Seam Divider Handle (swipe right to loop back to 第一介面!)
 * - Right 50%: Inline Playable YouTube Video Card (strictly non-fullscreen!)
 */
@Composable
private fun SecondEqualMapYoutubeDashboard(
    isHorizontalSplit: Boolean,
    location: com.dxcar.launcher.map.VehicleLocationState,
    activeNav: com.dxcar.launcher.comms.ActiveNavBanner?,
    builtInNavState: BuiltInNavState,
    offlineState: OfflineMapUiState,
    offlinePresets: List<OfflineRegionPreset>,
    downloadedRegions: List<OfflineRegionRecord>,
    offlineUpdatePolicy: OfflineUpdatePolicy,
    isNight: Boolean,
    mapEngineStyle: MapEngineStyle,
    trafficEnabled: Boolean,
    homeAddress: String,
    workAddress: String,
    onSeamDragDelta: (Float) -> Unit,
    onSeamDragEnd: () -> Unit,
    onSelectMapStyle: (MapEngineStyle) -> Unit,
    onToggleTraffic: () -> Unit,
    onToggleMapStyle: () -> Unit,
    onSelectOfflinePolicy: (OfflineUpdatePolicy) -> Unit,
    onDownloadOfflineRegion: (String) -> Unit,
    onCancelOfflineDownload: () -> Unit,
    onDeleteOfflineRegion: (String) -> Unit,
    onDismissOfflineSuggestion: () -> Unit,
    onOpenFullscreenMap: () -> Unit,
    onLaunchRealGoogleMaps: (String?) -> Unit,
    onLaunchRealMapsSplit: () -> Unit,
    onVoiceNavClick: () -> Unit,
    onSearchBuiltInPlace: (String) -> Unit,
    onStartBuiltInNavResult: (NavSearchResult) -> Unit,
    onQuickBuiltInNavigateQuery: (String) -> Unit,
    onStopBuiltInNav: () -> Unit
) {
    val colors = LocalDxCarColors.current
    val cardShape = RoundedCornerShape(colors.cardCornerDp.dp)

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp)
    ) {
        val gap = 16.dp
        if (isHorizontalSplit) {
            val halfPaneW = ((maxWidth - gap) / 2f).coerceAtLeast(140.dp)
            val fullH = maxHeight

            // Left 50% (1:1): Plan A Built-in Map
            Box(
                modifier = Modifier
                    .zIndex(1f)
                    .offset(x = 0.dp, y = 0.dp)
                    .width(halfPaneW)
                    .height(fullH)
                    .clip(cardShape)
                    .border(colors.cardBorderWidthDp.dp, colors.cardBorder, cardShape)
            ) {
                LiveMapCard(
                    location = location,
                    activeNav = activeNav,
                    builtInNavState = builtInNavState,
                    isNight = isNight,
                    mapEngineStyle = mapEngineStyle,
                    trafficEnabled = trafficEnabled,
                    offlineState = offlineState,
                    offlinePresets = offlinePresets,
                    downloadedRegions = downloadedRegions,
                    offlineUpdatePolicy = offlineUpdatePolicy,
                    isPrimary = true,
                    isFullscreen = false,
                    showSwapButton = false,
                    homeAddress = homeAddress,
                    workAddress = workAddress,
                    onSelectMapStyle = onSelectMapStyle,
                    onToggleTraffic = onToggleTraffic,
                    onToggleStyle = onToggleMapStyle,
                    onSelectOfflinePolicy = onSelectOfflinePolicy,
                    onDownloadOfflineRegion = onDownloadOfflineRegion,
                    onCancelOfflineDownload = onCancelOfflineDownload,
                    onDeleteOfflineRegion = onDeleteOfflineRegion,
                    onDismissOfflineSuggestion = onDismissOfflineSuggestion,
                    onSwapRequested = {},
                    onToggleFullscreen = onOpenFullscreenMap,
                    onLaunchGoogleMapsApp = onLaunchRealGoogleMaps,
                    onLaunchRealMapsSplit = onLaunchRealMapsSplit,
                    onVoiceNavClick = onVoiceNavClick,
                    onSearchBuiltInPlace = onSearchBuiltInPlace,
                    onStartBuiltInNavResult = onStartBuiltInNavResult,
                    onQuickBuiltInNavigateQuery = onQuickBuiltInNavigateQuery,
                    onStopBuiltInNav = onStopBuiltInNav
                )
            }

            // Right 50% (1:1): Inline Playable YouTube Video Card (Non-Fullscreen!)
            Box(
                modifier = Modifier
                    .zIndex(1f)
                    .offset(x = halfPaneW + gap, y = 0.dp)
                    .width(halfPaneW)
                    .height(fullH)
                    .clip(cardShape)
                    .border(colors.cardBorderWidthDp.dp, colors.cardBorder, cardShape)
            ) {
                EmbeddedYoutubePlayerCard()
            }

            // Center 1:1 Seam Divider Line (Swipe Right/Left -> Infinite Loop back to 第一介面!)
            DashboardSeamSwipeHandle(
                pageIndicator = "2/2",
                onDragDelta = onSeamDragDelta,
                onDragEnd = onSeamDragEnd,
                modifier = Modifier
                    .zIndex(5f)
                    .offset(x = halfPaneW + (gap / 2f) - 16.dp, y = 0.dp)
                    .width(32.dp)
                    .height(fullH)
            )
        } else {
            val halfPaneH = ((maxHeight - gap) / 2f).coerceAtLeast(140.dp)
            val fullW = maxWidth

            Box(
                modifier = Modifier
                    .zIndex(1f)
                    .offset(x = 0.dp, y = 0.dp)
                    .width(fullW)
                    .height(halfPaneH)
                    .clip(cardShape)
                    .border(colors.cardBorderWidthDp.dp, colors.cardBorder, cardShape)
            ) {
                LiveMapCard(
                    location = location,
                    activeNav = activeNav,
                    builtInNavState = builtInNavState,
                    isNight = isNight,
                    mapEngineStyle = mapEngineStyle,
                    trafficEnabled = trafficEnabled,
                    offlineState = offlineState,
                    offlinePresets = offlinePresets,
                    downloadedRegions = downloadedRegions,
                    offlineUpdatePolicy = offlineUpdatePolicy,
                    isPrimary = true,
                    isFullscreen = false,
                    showSwapButton = false,
                    homeAddress = homeAddress,
                    workAddress = workAddress,
                    onSelectMapStyle = onSelectMapStyle,
                    onToggleTraffic = onToggleTraffic,
                    onToggleStyle = onToggleMapStyle,
                    onSelectOfflinePolicy = onSelectOfflinePolicy,
                    onDownloadOfflineRegion = onDownloadOfflineRegion,
                    onCancelOfflineDownload = onCancelOfflineDownload,
                    onDeleteOfflineRegion = onDeleteOfflineRegion,
                    onDismissOfflineSuggestion = onDismissOfflineSuggestion,
                    onSwapRequested = {},
                    onToggleFullscreen = onOpenFullscreenMap,
                    onLaunchGoogleMapsApp = onLaunchRealGoogleMaps,
                    onLaunchRealMapsSplit = onLaunchRealMapsSplit,
                    onVoiceNavClick = onVoiceNavClick,
                    onSearchBuiltInPlace = onSearchBuiltInPlace,
                    onStartBuiltInNavResult = onStartBuiltInNavResult,
                    onQuickBuiltInNavigateQuery = onQuickBuiltInNavigateQuery,
                    onStopBuiltInNav = onStopBuiltInNav
                )
            }

            Box(
                modifier = Modifier
                    .zIndex(1f)
                    .offset(x = 0.dp, y = halfPaneH + gap)
                    .width(fullW)
                    .height(halfPaneH)
                    .clip(cardShape)
                    .border(colors.cardBorderWidthDp.dp, colors.cardBorder, cardShape)
            ) {
                EmbeddedYoutubePlayerCard()
            }

            DashboardSeamSwipeHandle(
                pageIndicator = "2/2",
                onDragDelta = onSeamDragDelta,
                onDragEnd = onSeamDragEnd,
                modifier = Modifier
                    .zIndex(5f)
                    .offset(x = (fullW / 2f) - 45.dp, y = halfPaneH + (gap / 2f) - 16.dp)
                    .width(90.dp)
                    .height(32.dp)
            )
        }
    }
}

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
    builtInNavState: BuiltInNavState = BuiltInNavState(),
    media: com.dxcar.launcher.media.ActiveMediaState,
    weather: LocalWeatherState,
    offlineState: OfflineMapUiState,
    offlinePresets: List<OfflineRegionPreset>,
    downloadedRegions: List<OfflineRegionRecord>,
    offlineUpdatePolicy: OfflineUpdatePolicy,
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
    onSeamDragDelta: (Float) -> Unit = {},
    onSeamDragEnd: () -> Unit = {},
    onMapCardBoundsMeasured: (Rect) -> Unit,
    onPinRealMapsHere: () -> Unit,
    onSwitchToBuiltinMap: () -> Unit,
    onSelectMapStyle: (MapEngineStyle) -> Unit,
    onToggleTraffic: () -> Unit,
    onToggleMapStyle: () -> Unit,
    onSelectOfflinePolicy: (OfflineUpdatePolicy) -> Unit,
    onDownloadOfflineRegion: (String) -> Unit,
    onCancelOfflineDownload: () -> Unit,
    onDeleteOfflineRegion: (String) -> Unit,
    onDismissOfflineSuggestion: () -> Unit,
    onSelectPlanCNavPkg: (String) -> Unit,
    onLaunchPlanCFreeform: (String) -> Unit,
    onLaunchPlanCSplit: (String) -> Unit,
    onLaunchPlanCFullscreen: (String) -> Unit,
    onSwapPanes: () -> Unit,
    onOpenFullscreenMap: () -> Unit,
    onLaunchRealGoogleMaps: (String?) -> Unit,
    onLaunchRealMapsSplit: () -> Unit,
    onVoiceNavClick: () -> Unit = {},
    onSearchBuiltInPlace: (String) -> Unit = {},
    onStartBuiltInNavResult: (NavSearchResult) -> Unit = {},
    onQuickBuiltInNavigateQuery: (String) -> Unit = {},
    onStopBuiltInNav: () -> Unit = {},
    onWeatherClick: () -> Unit,
    onMediaPlayPause: () -> Unit,
    onMediaNext: () -> Unit,
    onMediaPrev: () -> Unit,
    onMediaToggleFavorite: () -> Unit,
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
                    Surface(color = colors.cardSurface, modifier = Modifier.fillMaxSize()) {
                        Column(
                            modifier = Modifier.fillMaxSize().padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(Icons.Rounded.Map, contentDescription = null, tint = colors.accent, modifier = Modifier.size(54.dp))
                            Spacer(Modifier.height(10.dp))
                            Text(
                                text = "真實 Google 地圖本體貼合模式（${(primaryRatio * 100).toInt()}% 比例）",
                                color = colors.primaryText,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = "點擊下方按鈕即以 Freeform 貼合開啟，或使用 AOSP 原生可拖動分割模式",
                                color = colors.secondaryText,
                                fontSize = 12.sp
                            )
                            Spacer(Modifier.height(14.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Button(
                                    onClick = onPinRealMapsHere,
                                    colors = ButtonDefaults.buttonColors(containerColor = colors.accent, contentColor = colors.onAccent),
                                    shape = RoundedCornerShape(16.dp)
                                ) {
                                    Text("貼合開啟真實 Google 地圖")
                                }
                                OutlinedButton(onClick = onLaunchRealMapsSplit, shape = RoundedCornerShape(16.dp)) {
                                    Text("AOSP 原生分割模式")
                                }
                                OutlinedButton(onClick = onSwitchToBuiltinMap, shape = RoundedCornerShape(16.dp)) {
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
                        builtInNavState = builtInNavState,
                        isNight = isNight,
                        mapEngineStyle = mapEngineStyle,
                        trafficEnabled = trafficEnabled,
                        offlineState = offlineState,
                        offlinePresets = offlinePresets,
                        downloadedRegions = downloadedRegions,
                        offlineUpdatePolicy = offlineUpdatePolicy,
                        isPrimary = primary,
                        isFullscreen = false,
                        homeAddress = homeAddress,
                        workAddress = workAddress,
                        onSelectMapStyle = onSelectMapStyle,
                        onToggleTraffic = onToggleTraffic,
                        onToggleStyle = onToggleMapStyle,
                        onSelectOfflinePolicy = onSelectOfflinePolicy,
                        onDownloadOfflineRegion = onDownloadOfflineRegion,
                        onCancelOfflineDownload = onCancelOfflineDownload,
                        onDeleteOfflineRegion = onDeleteOfflineRegion,
                        onDismissOfflineSuggestion = onDismissOfflineSuggestion,
                        onSwapRequested = onSwapPanes,
                        onToggleFullscreen = onOpenFullscreenMap,
                        onLaunchGoogleMapsApp = onLaunchRealGoogleMaps,
                        onLaunchRealMapsSplit = onLaunchRealMapsSplit,
                        onVoiceNavClick = onVoiceNavClick,
                        onSearchBuiltInPlace = onSearchBuiltInPlace,
                        onStartBuiltInNavResult = onStartBuiltInNavResult,
                        onQuickBuiltInNavigateQuery = onQuickBuiltInNavigateQuery,
                        onStopBuiltInNav = onStopBuiltInNav
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
                weather = weather,
                isPrimary = primary,
                pureMusicMode = isPlanC,
                showSwapButton = !isPlanC,
                showSixShortcuts = integrateSix && showSixShortcutsInMediaPanel,
                customShortcutSlots = customShortcutSlots,
                installedApps = installedApps,
                splitShortcuts = splitShortcuts,
                onWeatherClick = onWeatherClick,
                onPlayPause = onMediaPlayPause,
                onNext = onMediaNext,
                onPrevious = onMediaPrev,
                onToggleFavorite = onMediaToggleFavorite,
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

    // 1.5-second (1500ms) Inward Card-Exchange Animation ("卡片向內交換特效動畫"):
    // Both Map and Media cards stay continuously mounted (zero MapView reload!) while smoothly
    // drawing inward toward the center axis, tilting inward in 3D perspective, crossing over,
    // and expanding into each other's slot over 1500ms.
    val showMapPrimary = isMapPrimary || isPlanC
    val swapProgress = remember { Animatable(if (showMapPrimary) 0f else 1f) }
    LaunchedEffect(showMapPrimary) {
        val target = if (showMapPrimary) 0f else 1f
        if (abs(swapProgress.value - target) > 0.001f) {
            swapProgress.animateTo(
                targetValue = target,
                animationSpec = tween(
                    durationMillis = 1500,
                    easing = CubicBezierEasing(0.32f, 0.0f, 0.18f, 1.0f)
                )
            )
        } else {
            swapProgress.snapTo(target)
        }
    }

    val f = swapProgress.value.coerceIn(0f, 1f)
    val mid = sin(f * Math.PI).toFloat()
    val totalWeight = (primaryWeight + secondaryWeight).coerceAtLeast(0.01f)
    val normPrimary = primaryWeight / totalWeight
    val normSecondary = secondaryWeight / totalWeight
    val mapIsPrimaryRole = f < 0.5f
    val mediaIsPrimaryRole = f >= 0.5f

    val frontScale = 1f - (0.055f * mid)
    val backScale = 1f - (0.145f * mid)
    val mapScale = if (showMapPrimary) frontScale else backScale
    val mediaScale = if (showMapPrimary) backScale else frontScale
    val mapAlpha = if (showMapPrimary) 1f else (1f - 0.08f * mid)
    val mediaAlpha = if (showMapPrimary) (1f - 0.08f * mid) else 1f
    val mapZ = if (showMapPrimary) 2f else 1f
    val mediaZ = if (showMapPrimary) 1f else 2f

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp)
    ) {
        val gap = 16.dp
        if (isHorizontalSplit) {
            val availW = (maxWidth - gap).coerceAtLeast(160.dp)
            val fullH = maxHeight
            val primaryW = availW * normPrimary
            val secondaryW = availW * normSecondary
            val primaryX = if (rightHandDrive) secondaryW + gap else 0.dp
            val secondaryX = if (rightHandDrive) 0.dp else primaryW + gap

            val mapW = lerp(primaryW, secondaryW, f)
            val mediaW = lerp(secondaryW, primaryW, f)
            val baseMapX = lerp(primaryX, secondaryX, f)
            val baseMediaX = lerp(secondaryX, primaryX, f)

            val centerX = maxWidth / 2f
            val halfW = centerX.coerceAtLeast(1.dp)
            val mapCenter = baseMapX + (mapW / 2f)
            val mediaCenter = baseMediaX + (mediaW / 2f)

            // Symmetric inward pull toward dashboard center + inward 3D perspective tilt
            val mapX = baseMapX + (centerX - mapCenter) * (0.20f * mid)
            val mediaX = baseMediaX + (centerX - mediaCenter) * (0.20f * mid)
            val mapSideRatio = ((mapCenter - centerX) / halfW).coerceIn(-1f, 1f)
            val mediaSideRatio = ((mediaCenter - centerX) / halfW).coerceIn(-1f, 1f)
            val mapRotY = -mapSideRatio * 13f * mid
            val mediaRotY = -mediaSideRatio * 13f * mid

            mapCardComposable(
                mapIsPrimaryRole,
                Modifier
                    .zIndex(mapZ)
                    .offset(x = mapX, y = 0.dp)
                    .width(mapW)
                    .height(fullH)
                    .graphicsLayer {
                        scaleX = mapScale
                        scaleY = mapScale
                        rotationY = mapRotY
                        alpha = mapAlpha
                        cameraDistance = 16f * density
                    }
            )

            mediaCardComposable(
                mediaIsPrimaryRole,
                supportsThirdSubCard,
                Modifier
                    .zIndex(mediaZ)
                    .offset(x = mediaX, y = 0.dp)
                    .width(mediaW)
                    .height(fullH)
                    .graphicsLayer {
                        scaleX = mediaScale
                        scaleY = mediaScale
                        rotationY = mediaRotY
                        alpha = mediaAlpha
                        cameraDistance = 16f * density
                    }
            )

            // Center Seam Divider Line between the two windows ("滑動第一介面兩個視窗中間的線就可以往右滑動切換，無限迴圈")
            val seamCenterX = (if (rightHandDrive) secondaryW else primaryW) + (gap / 2f)
            DashboardSeamSwipeHandle(
                pageIndicator = "1/2",
                onDragDelta = onSeamDragDelta,
                onDragEnd = onSeamDragEnd,
                modifier = Modifier
                    .zIndex(5f)
                    .offset(x = seamCenterX - 16.dp, y = 0.dp)
                    .width(32.dp)
                    .height(fullH)
            )
        } else {
            val availH = (maxHeight - gap).coerceAtLeast(160.dp)
            val fullW = maxWidth
            val primaryH = availH * normPrimary
            val secondaryH = availH * normSecondary
            val primaryY = 0.dp
            val secondaryY = primaryH + gap

            val mapH = lerp(primaryH, secondaryH, f)
            val mediaH = lerp(secondaryH, primaryH, f)
            val baseMapY = lerp(primaryY, secondaryY, f)
            val baseMediaY = lerp(secondaryY, primaryY, f)

            val centerY = maxHeight / 2f
            val halfH = centerY.coerceAtLeast(1.dp)
            val mapCenterY = baseMapY + (mapH / 2f)
            val mediaCenterY = baseMediaY + (mediaH / 2f)

            val mapY = baseMapY + (centerY - mapCenterY) * (0.20f * mid)
            val mediaY = baseMediaY + (centerY - mediaCenterY) * (0.20f * mid)
            val mapSideY = ((mapCenterY - centerY) / halfH).coerceIn(-1f, 1f)
            val mediaSideY = ((mediaCenterY - centerY) / halfH).coerceIn(-1f, 1f)
            val mapRotX = mapSideY * 11f * mid
            val mediaRotX = mediaSideY * 11f * mid

            mapCardComposable(
                mapIsPrimaryRole,
                Modifier
                    .zIndex(mapZ)
                    .offset(x = 0.dp, y = mapY)
                    .width(fullW)
                    .height(mapH)
                    .graphicsLayer {
                        scaleX = mapScale
                        scaleY = mapScale
                        rotationX = mapRotX
                        alpha = mapAlpha
                        cameraDistance = 16f * density
                    }
            )

            mediaCardComposable(
                mediaIsPrimaryRole,
                showSixShortcutsInMediaPanel,
                Modifier
                    .zIndex(mediaZ)
                    .offset(x = 0.dp, y = mediaY)
                    .width(fullW)
                    .height(mediaH)
                    .graphicsLayer {
                        scaleX = mediaScale
                        scaleY = mediaScale
                        rotationX = mediaRotX
                        alpha = mediaAlpha
                        cameraDistance = 16f * density
                    }
            )

            DashboardSeamSwipeHandle(
                pageIndicator = "1/2",
                onDragDelta = onSeamDragDelta,
                onDragEnd = onSeamDragEnd,
                modifier = Modifier
                    .zIndex(5f)
                    .offset(x = (fullW / 2f) - 45.dp, y = primaryH + (gap / 2f) - 16.dp)
                    .width(90.dp)
                    .height(32.dp)
            )
        }
    }
}

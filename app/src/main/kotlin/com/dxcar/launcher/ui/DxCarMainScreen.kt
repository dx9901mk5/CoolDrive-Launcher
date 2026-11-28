package com.dxcar.launcher.ui

import androidx.compose.animation.core.animateFloat

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
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.ui.draw.clipToBounds
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
import com.dxcar.launcher.overlay.ExtraLargeTutorialModal
import com.dxcar.launcher.overlay.Floating3DControlBar
import com.dxcar.launcher.overlay.PersistentDockOverlayService
import com.dxcar.launcher.overlay.RemoteCarMousePointerOverlay
import com.dxcar.launcher.overlay.tutorial3DControlSteps
import com.dxcar.launcher.system.InstalledAppInfo
import com.dxcar.launcher.system.PermissionHelper
import com.dxcar.launcher.system.SystemStatusMonitor
import com.dxcar.launcher.theme.DayNightEngine
import com.dxcar.launcher.theme.DxCarTheme
import com.dxcar.launcher.theme.LocalDxCarColors
import com.dxcar.launcher.i18n.DxCarI18n
import com.dxcar.launcher.i18n.DxCarI18n.str
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

private fun mapEngineStyleLabelTr(style: MapEngineStyle): String = when (style) {
    MapEngineStyle.GOOGLE_TRAFFIC_TILES -> DxCarI18n.tr("Google 繁中地圖", "Google 中文地图", "Google Maps", "Google マップ")
    MapEngineStyle.GOOGLE_SATELLITE_HYBRID -> DxCarI18n.tr("Google 衛星混合", "Google 卫星混合", "Google Satellite", "Google 衛星写真")
    MapEngineStyle.OPENFREEMAP_VECTOR_3D -> DxCarI18n.tr("OpenFreeMap 3D 向量", "OpenFreeMap 3D 矢量", "OpenFreeMap 3D", "OpenFreeMap 3D")
    MapEngineStyle.OPENFREEMAP_DARK -> DxCarI18n.tr("深色極簡向量", "深色极简矢量", "Dark Vector", "ダークベクター")
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
    val sharedLocation by app.notificationCenter.sharedLocation.collectAsState()
    val messages by app.notificationCenter.messages.collectAsState()
    val activeNav by app.notificationCenter.activeNavigation.collectAsState()
    val builtInNavState by app.builtInNavEngine.state.collectAsState()
    val bridgeStatus by BluetoothCompanionService.bridgeState.collectAsState()
    val updateState by app.updateManager.state.collectAsState()
    val billingState by app.billingTrialManager.state.collectAsState()
    val cursor3DActive by Cursor3DStateHolder.active.collectAsState()
    val cursor3DTutorial by Cursor3DStateHolder.showTutorial.collectAsState()
    val isLauncherFg by app.isLauncherForeground.collectAsState()
    val remoteCursor by RemoteTouchpadController.cursorState.collectAsState()
    val remoteSwapSeq by RemoteTouchpadController.swapHomePanesSeq.collectAsState()
    val remoteSelectPlan by RemoteTouchpadController.remoteSelectPlanMode.collectAsState()

    // Auto-detect Smartphone vs AI Box Car Head Unit on first launch when not pinned by user
    LaunchedEffect(configuration.screenWidthDp, configuration.screenHeightDp, config.roleUserPinned) {
        if (config.deviceRole == DeviceRole.AI_BOX_LAUNCHER &&
            DeviceFormFactor.shouldAutoDefaultToPhone(context, configuration, config)
        ) {
            app.preferences.update {
                it.copy(
                    deviceRole = DeviceRole.PHONE_COMPANION,
                    themeMode = if (it.themeMode == ThemeMode.AUTO_TIME) ThemeMode.FOLLOW_SYSTEM else it.themeMode
                )
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
    var currentDashboardPage by remember { mutableIntStateOf(1) } // 0 = 第0介面(小工具), 1 = 第一介面(預設), 2 = 第二介面(1:1 方案A地圖 + YouTube)
    var isMapPrimary by remember { mutableStateOf(true) }
    var showQuickControlCenter by remember { mutableStateOf(false) }
    var showNotificationCenter by remember { mutableStateOf(false) }
    var showSplitCreator by remember { mutableStateOf(false) }
    var showSettingsPermissionsSheet by remember { mutableStateOf(false) }
    var activeSettingsTutorial by remember { mutableStateOf<CarSettingsTutorialTopic?>(null) }
    var pickingShortcutSlotIndex by remember { mutableStateOf<Int?>(null) }

    fun handleInAppBack(): Boolean {
        return when {
            cursor3DTutorial -> {
                Cursor3DStateHolder.closeTutorial()
                true
            }
            activeSettingsTutorial != null -> {
                activeSettingsTutorial = null
                true
            }
            showSettingsPermissionsSheet -> {
                showSettingsPermissionsSheet = false
                true
            }
            showQuickControlCenter -> {
                showQuickControlCenter = false
                true
            }
            showNotificationCenter -> {
                showNotificationCenter = false
                true
            }
            showSplitCreator -> {
                showSplitCreator = false
                true
            }
            pickingShortcutSlotIndex != null -> {
                pickingShortcutSlotIndex = null
                true
            }
            destination == LauncherDestination.DASHBOARD_SPLIT && currentDashboardPage == 2 &&
                EmbeddedYoutubeBridge.isFullscreen?.invoke() == true -> {
                EmbeddedYoutubeBridge.exitFullscreen?.invoke()
                true
            }
            destination == LauncherDestination.DASHBOARD_SPLIT && currentDashboardPage == 2 &&
                EmbeddedYoutubeBridge.canGoBack?.invoke() == true -> {
                EmbeddedYoutubeBridge.goBack?.invoke()
                true
            }
            destination != LauncherDestination.DASHBOARD_SPLIT -> {
                destination = if (destination == LauncherDestination.SETTINGS) {
                    LauncherDestination.APP_DRAWER
                } else {
                    LauncherDestination.DASHBOARD_SPLIT
                }
                true
            }
            else -> false
        }
    }

    androidx.activity.compose.BackHandler(
        enabled = cursor3DTutorial ||
            activeSettingsTutorial != null ||
            showSettingsPermissionsSheet ||
            showQuickControlCenter ||
            showNotificationCenter ||
            showSplitCreator ||
            pickingShortcutSlotIndex != null ||
            destination != LauncherDestination.DASHBOARD_SPLIT
    ) {
        handleInAppBack()
    }

    // Track whether DX-Car's built-in MapLibre map is currently visible so Floating3DControlBar
    // can directly animate MapLibre 3D tilt/zoom at 60fps without needing Accessibility permission.
    LaunchedEffect(isLauncherFg, destination, currentDashboardPage, config.homeMapDisplayMode, config.deviceRole) {
        val hasBuiltinMapVisible = isLauncherFg &&
            config.deviceRole == DeviceRole.AI_BOX_LAUNCHER &&
            (destination == LauncherDestination.FULLSCREEN_MAP ||
                (destination == LauncherDestination.DASHBOARD_SPLIT &&
                    (currentDashboardPage == 2 || (currentDashboardPage == 1 && config.homeMapDisplayMode == HomeMapDisplayMode.BUILTIN_LIVE_MAP))))
        Cursor3DStateHolder.setBuiltinMapActive(hasBuiltinMapVisible)
    }
    var latestMapCardRect by remember { mutableStateOf<Rect?>(null) }
    var gestureHudText by remember { mutableStateOf<String?>(null) }
    var isUiSwitchHud by remember { mutableStateOf(false) }
    var permissionHealth by remember { mutableStateOf(PermissionHelper.check(context)) }
    var pairedBtDevices by remember { mutableStateOf(app.systemStatusMonitor.getPairedBluetoothDevices()) }

    LaunchedEffect(destination, homeLaunchers) {
        if (destination == LauncherDestination.SETTINGS) {
            permissionHealth = PermissionHelper.check(context)
        }
    }

    LaunchedEffect(gestureHudText, isUiSwitchHud) {
        if (gestureHudText != null) {
            delay(if (isUiSwitchHud) 4_200L else 2_400L)
            gestureHudText = null
            isUiSwitchHud = false
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
            gestureHudText = DxCarI18n.tr(
                "⇄ 已從手機多指觸控切換首頁分欄",
                "⇄ 已从手机多指触控切换首页分栏",
                "⇄ Swapped home panes via phone touchpad",
                "⇄ スマホタッチパッドでホーム分割を入れ替えました"
            )
        }
    }

    LaunchedEffect(externalNavCommand) {
        when (externalNavCommand) {
            com.dxcar.launcher.MainActivity.EXTRA_GO_DASHBOARD -> {
                destination = LauncherDestination.DASHBOARD_SPLIT
                onExternalNavConsumed()
            }
            com.dxcar.launcher.MainActivity.EXTRA_OPEN_DRAWER -> {
                destination = LauncherDestination.APP_DRAWER
                onExternalNavConsumed()
            }
            com.dxcar.launcher.MainActivity.EXTRA_OPEN_YOUTUBE -> {
                destination = LauncherDestination.FULLSCREEN_YOUTUBE_WEB
                onExternalNavConsumed()
            }
            com.dxcar.launcher.MainActivity.EXTRA_OPEN_YOUTUBE_HANDOFF -> {
                destination = LauncherDestination.DASHBOARD_SPLIT
                currentDashboardPage = if (config.planAPage2Enabled || config.homeExperimentMode == HomeExperimentMode.PLAN_D_ROOT_RESERVED) 2 else 1
                onExternalNavConsumed()
            }
            com.dxcar.launcher.MainActivity.EXTRA_OPEN_QUICK_PANEL -> {
                DxCarAccessibilityService.instance?.openQuickSettings()
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

    LaunchedEffect(Unit) {
        app.billingTrialManager.refreshTrialClock()
    }

    LaunchedEffect(config.autoCheckAndDownloadUpdate) {
        if (com.dxcar.launcher.BuildConfig.ENABLE_GITHUB_AUTO_UPDATE && config.autoCheckAndDownloadUpdate) {
            delay(2_500L)
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

    LaunchedEffect(config.planAPage2Enabled, config.homeExperimentMode) {
        if (config.homeExperimentMode == HomeExperimentMode.PLAN_A_MULTI_LAYER &&
            !config.planAPage2Enabled &&
            currentDashboardPage == 2
        ) {
            currentDashboardPage = 1
        }
    }

    LaunchedEffect(remoteSelectPlan) {
        val pair = remoteSelectPlan ?: return@LaunchedEffect
        val targetPlan = pair.second
        if (targetPlan == "PLAN_A") {
            com.dxcar.launcher.system.RootHelperBridge.setDockPinnedRightShift(context, false)
            com.dxcar.launcher.system.RootHelperBridge.parkPlanBWindows(
                context = context,
                screenWidth = screenMetrics.widthPx,
                screenHeight = screenMetrics.heightPx
            )
            app.preferences.updateSync {
                it.copy(
                    homeExperimentMode = HomeExperimentMode.PLAN_A_MULTI_LAYER,
                    launchAppsAvoidDockFreeform = false,
                    overlayAutoCollapseEnabled = true
                )
            }
            com.dxcar.launcher.MainActivity.restartLauncherCleanly(context)
        } else if (targetPlan == "PLAN_B") {
            val hasHelper = com.dxcar.launcher.system.RootHelperBridge.isRootHelperInstalled(context)
            if (!hasHelper) {
                gestureHudText = DxCarI18n.tr(
                    "⚠️ 車機尚未安裝 DX-Car 擴充輔助 App，無法切換方案 B",
                    "⚠️ 车机尚未安装 DX-Car 扩展辅助 App，无法切换方案 B",
                    "⚠️ Helper APK not installed on Car; cannot switch to Plan B",
                    "⚠️ 車載機に拡張補助 APK が未インストールのためプラン B に切替できません"
                )
            } else {
                // Cancel permanent Dock shift on A/B switch; Helper can re-enable when Plan B starts
                com.dxcar.launcher.system.RootHelperBridge.setDockPinnedRightShift(context, false)
                com.dxcar.launcher.system.RootHelperBridge.parkPlanBWindows(
                    context = context,
                    screenWidth = screenMetrics.widthPx,
                    screenHeight = screenMetrics.heightPx
                )
                app.preferences.updateSync {
                    it.copy(
                        homeExperimentMode = HomeExperimentMode.PLAN_D_ROOT_RESERVED,
                        launchAppsAvoidDockFreeform = false,
                        overlayAutoCollapseEnabled = true
                    )
                }
                com.dxcar.launcher.MainActivity.restartLauncherCleanly(context)
            }
        }
    }

    androidx.compose.runtime.DisposableEffect(Unit) {
        PersistentDockOverlayService.onPlanBPageSyncToActivity = { p ->
            currentDashboardPage = p.coerceIn(0, 2)
        }
        onDispose {
            PersistentDockOverlayService.onPlanBPageSyncToActivity = null
        }
    }

    var lastPlanBDispatchMs by remember { mutableStateOf(0L) }
    var lastPlanBDispatchedPage by remember { mutableIntStateOf(-1) }

    fun dispatchPlanBWindowLayout(targetPage: Int, force: Boolean = false) {
        if (!com.dxcar.launcher.system.RootHelperBridge.isRootHelperInstalled(context)) return
        val coerced = targetPage.coerceIn(0, 2)
        val now = System.currentTimeMillis()
        if (!force && coerced == lastPlanBDispatchedPage && (now - lastPlanBDispatchMs) < 1_800L) {
            return
        }
        lastPlanBDispatchMs = now
        lastPlanBDispatchedPage = coerced
        PersistentDockOverlayService.planBInFullscreenApp.value = false
        PersistentDockOverlayService.planBActivePage.value = coerced
        PersistentDockOverlayService.start(context)
        val dockPx = (layoutSpec.dockThicknessDp * screenMetrics.density).toInt().coerceAtLeast(0)
        val helperCfg = com.dxcar.launcher.system.RootHelperBridge.readPlanBConfig(context)
        val effUi1Ratio = if (helperCfg.planBEnabled) helperCfg.ui1SplitRatio else config.primarySplitRatio
        val effUi2Ratio = if (helperCfg.planBEnabled) helperCfg.ui2SplitRatio else config.page2SplitRatio
        com.dxcar.launcher.system.RootHelperBridge.applyPlanBPage(
            context = context,
            page = coerced,
            screenWidth = screenMetrics.widthPx,
            screenHeight = screenMetrics.heightPx,
            dockOffsetLeft = if (layoutSpec.dockPlacement == ResolvedDockPlacement.LEFT) dockPx else 0,
            dockOffsetRight = if (layoutSpec.dockPlacement == ResolvedDockPlacement.RIGHT) dockPx else 0,
            dockOffsetTop = if (layoutSpec.dockPlacement == ResolvedDockPlacement.TOP) dockPx else 0,
            dockOffsetBottom = if (layoutSpec.dockPlacement == ResolvedDockPlacement.BOTTOM) dockPx else 0,
            ui1Ratio = effUi1Ratio,
            ui1Swapped = config.page1Swapped,
            ui2Ratio = effUi2Ratio,
            ui2Swapped = config.page2Swapped
        )
    }

    LaunchedEffect(isLauncherFg, destination, currentDashboardPage, config.homeExperimentMode, showNotificationCenter) {
        if (config.deviceRole == DeviceRole.AI_BOX_LAUNCHER &&
            config.homeExperimentMode == HomeExperimentMode.PLAN_D_ROOT_RESERVED &&
            com.dxcar.launcher.system.RootHelperBridge.isRootHelperInstalled(context)
        ) {
            if (destination != LauncherDestination.DASHBOARD_SPLIT || showNotificationCenter || currentDashboardPage == 0) {
                lastPlanBDispatchedPage = 0
                com.dxcar.launcher.system.RootHelperBridge.parkPlanBWindows(
                    context = context,
                    screenWidth = screenMetrics.widthPx,
                    screenHeight = screenMetrics.heightPx
                )
            } else if (isLauncherFg && (currentDashboardPage == 1 || currentDashboardPage == 2)) {
                PersistentDockOverlayService.planBActivePage.value = currentDashboardPage
                delay(320L)
                if (isLauncherFg &&
                    destination == LauncherDestination.DASHBOARD_SPLIT &&
                    !showNotificationCenter &&
                    config.homeExperimentMode == HomeExperimentMode.PLAN_D_ROOT_RESERVED
                ) {
                    dispatchPlanBWindowLayout(currentDashboardPage, force = false)
                }
            }
        }
    }

    val systemIsDark = isSystemInDarkTheme()
    val effectiveThemeConfig = if (
        config.deviceRole == DeviceRole.PHONE_COMPANION &&
        (config.themeMode == ThemeMode.AUTO_TIME || config.themeMode == ThemeMode.AUTO_SUNRISE_SUNSET)
    ) {
        config.copy(themeMode = ThemeMode.FOLLOW_SYSTEM)
    } else {
        config
    }
    val isNight = DayNightEngine.isNightNow(
        config = effectiveThemeConfig,
        now = sysStatus.now,
        systemIsDark = systemIsDark
    )

    LaunchedEffect(
        isNight,
        config.deviceRole,
        config.dayNightBrightnessEnabled,
        config.dayBrightnessPercent,
        config.nightBrightnessPercent
    ) {
        if (config.deviceRole == DeviceRole.AI_BOX_LAUNCHER && config.dayNightBrightnessEnabled) {
            val targetPct = if (isNight) config.nightBrightnessPercent else config.dayBrightnessPercent
            app.systemStatusMonitor.setBrightnessPercent(targetPct)
        }
    }

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

    fun launchAppRespectingOptions(packageName: String, preferPlanBRightPane: Boolean = false): Boolean {
        if (config.homeExperimentMode == HomeExperimentMode.PLAN_D_ROOT_RESERVED &&
            com.dxcar.launcher.system.RootHelperBridge.isRootHelperInstalled(context)
        ) {
            PersistentDockOverlayService.start(context)
            val dockPx = (layoutSpec.dockThicknessDp * screenMetrics.density).toInt().coerceAtLeast(0)
            val helperCfg = com.dxcar.launcher.system.RootHelperBridge.readPlanBConfig(context)
            val ok = if (preferPlanBRightPane) {
                PersistentDockOverlayService.planBInFullscreenApp.value = false
                com.dxcar.launcher.system.RootHelperBridge.launchPlanBRightPaneApp(
                    context = context,
                    packageName = packageName,
                    screenWidth = screenMetrics.widthPx,
                    screenHeight = screenMetrics.heightPx,
                    dockOffsetLeft = if (layoutSpec.dockPlacement == ResolvedDockPlacement.LEFT) dockPx else 0,
                    dockOffsetRight = if (layoutSpec.dockPlacement == ResolvedDockPlacement.RIGHT) dockPx else 0,
                    dockOffsetTop = if (layoutSpec.dockPlacement == ResolvedDockPlacement.TOP) dockPx else 0,
                    dockOffsetBottom = if (layoutSpec.dockPlacement == ResolvedDockPlacement.BOTTOM) dockPx else 0,
                    ui1Ratio = if (helperCfg.planBEnabled) helperCfg.ui1SplitRatio else config.primarySplitRatio,
                    ui1Swapped = config.page1Swapped
                )
            } else {
                PersistentDockOverlayService.planBInFullscreenApp.value = true
                com.dxcar.launcher.system.RootHelperBridge.launchFullscreenApp(
                    context = context,
                    packageName = packageName,
                    screenWidth = screenMetrics.widthPx,
                    screenHeight = screenMetrics.heightPx,
                    dockOffsetLeft = if (layoutSpec.dockPlacement == ResolvedDockPlacement.LEFT) dockPx else 0,
                    dockOffsetRight = if (layoutSpec.dockPlacement == ResolvedDockPlacement.RIGHT) dockPx else 0,
                    dockOffsetTop = if (layoutSpec.dockPlacement == ResolvedDockPlacement.TOP) dockPx else 0,
                    dockOffsetBottom = if (layoutSpec.dockPlacement == ResolvedDockPlacement.BOTTOM) dockPx else 0
                )
            }
            if (ok) {
                app.installedAppsRepository.recordRecent(packageName)
                return true
            }
        }
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
        if (Settings.canDrawOverlays(context) && !DeviceFormFactor.isCompactPhoneScreen(configuration)) {
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
            gestureHudText = DxCarI18n.tr(
                "🗺 已以 $pct% 比例貼合開啟真實 Google 地圖",
                "🗺 已以 $pct% 比例贴合开启真实 Google 地图",
                "🗺 Opened Google Maps fitted to $pct% pane",
                "🗺 Google マップを $pct% 枠にフィット起動しました"
            )
        } else {
            app.installedAppsRepository.launchPackageFreeform(targetPackage, bounds)
            val label = app.installedAppsRepository.findApp(targetPackage)?.label
                ?: DxCarI18n.tr("導航 App", "导航应用", "Nav App", "ナビアプリ")
            gestureHudText = DxCarI18n.tr(
                "🗺 已以 $pct% 比例貼合開啟 $label",
                "🗺 已以 $pct% 比例贴合开启 $label",
                "🗺 Opened $label fitted to $pct% pane",
                "🗺 $label を $pct% 枠にフィット起動しました"
            )
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
        gestureHudText = DxCarI18n.tr(
            "⊞ 正在開啟 AOSP 分屏：${sc.label} (${sc.ratio.label})",
            "⊞ 正在开启 AOSP 分屏：${sc.label} (${sc.ratio.label})",
            "⊞ Launching Split-Screen: ${sc.label} (${sc.ratio.label})",
            "⊞ AOSP 分割画面を起動中：${sc.label} (${sc.ratio.label})"
        )
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
                gestureHudText = if (nextMode == ThemeMode.FORCE_NIGHT) {
                    DxCarI18n.tr("🌙 已切換為夜間模式", "🌙 已切换为夜间模式", "🌙 Switched to Night Mode", "🌙 夜間モードに切り替えました")
                } else {
                    DxCarI18n.tr("☀️ 已切換為白天模式", "☀️ 已切换为白天模式", "☀️ Switched to Day Mode", "☀️ 昼間モードに切り替えました")
                }
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
                    gestureHudText = DxCarI18n.tr(
                        "無法啟動該應用程式",
                        "无法启动该应用",
                        "Unable to launch this app",
                        "このアプリを起動できません"
                    )
                }
            }
        }
    }

    fun handleToggleDayNight() {
        val nextMode = if (isNight) ThemeMode.FORCE_DAY else ThemeMode.FORCE_NIGHT
        app.preferences.update { it.copy(themeMode = nextMode) }
        gestureHudText = if (nextMode == ThemeMode.FORCE_NIGHT) {
            DxCarI18n.tr("🌙 已切換為夜間模式", "🌙 已切换为夜间模式", "🌙 Switched to Night Mode", "🌙 夜間モードに切り替えました")
        } else {
            DxCarI18n.tr("☀️ 已切換為白天模式", "☀️ 已切换为白天模式", "☀️ Switched to Day Mode", "☀️ 昼間モードに切り替えました")
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
                installedApps = installedApps,
                bridgeStatus = bridgeStatus,
                permissionHealth = permissionHealth,
                updateState = updateState,
                billingState = billingState,
                onCheckUpdate = {
                    if (com.dxcar.launcher.BuildConfig.ENABLE_GITHUB_AUTO_UPDATE) {
                        scope.launch { app.updateManager.checkForUpdate(autoDownload = false) }
                    }
                },
                onDownloadOrInstallUpdate = {
                    if (com.dxcar.launcher.BuildConfig.ENABLE_GITHUB_AUTO_UPDATE) {
                        if (updateState.downloadedReady) {
                            app.updateManager.installDownloadedApk()
                        } else {
                            scope.launch { app.updateManager.downloadAndInstall(autoLaunchInstaller = true) }
                        }
                    }
                },
                onPurchasePro = {
                    (context as? android.app.Activity)?.let { act ->
                        app.billingTrialManager.launchPurchaseFlow(act)
                    }
                },
                onRestorePurchases = { app.billingTrialManager.restorePurchases() },
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
                onSetTargetDeviceName = { customName ->
                    app.preferences.update {
                        it.copy(
                            companionTargetDeviceName = customName,
                            companionTargetDeviceAddress = if (customName.isBlank()) "" else it.companionTargetDeviceAddress
                        )
                    }
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
                onTogglePhoneCallCapture = { checked ->
                    app.preferences.update { it.copy(phoneCallCaptureEnabled = checked) }
                },
                onChangeTouchpadSensitivity = { sens ->
                    app.preferences.update { it.copy(phoneTouchpadSensitivity = sens) }
                },
                onSelectLanguage = { lang ->
                    app.preferences.update { it.copy(appLanguage = lang) }
                },
                isNight = isNight,
                onCycleThemeMode = {
                    app.preferences.update { cur ->
                        val nextMode = when (cur.themeMode) {
                            ThemeMode.FOLLOW_SYSTEM, ThemeMode.AUTO_TIME, ThemeMode.AUTO_SUNRISE_SUNSET -> ThemeMode.FORCE_NIGHT
                            ThemeMode.FORCE_NIGHT -> ThemeMode.FORCE_DAY
                            ThemeMode.FORCE_DAY -> ThemeMode.FOLLOW_SYSTEM
                        }
                        cur.copy(themeMode = nextMode)
                    }
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
                        val isPlanB = config.homeExperimentMode == HomeExperimentMode.PLAN_D_ROOT_RESERVED
                        when {
                            slot.id == "youtube" -> {
                                val yt = app.installedAppsRepository.findBestYoutubePackage()
                                if (yt != null) launchAppRespectingOptions(yt, preferPlanBRightPane = isPlanB)
                                else destination = LauncherDestination.FULLSCREEN_YOUTUBE_WEB
                            }
                            slot.packageName == "ACTION_DIAL" -> app.installedAppsRepository.launchDialer()
                            !slot.packageName.isNullOrBlank() -> launchAppRespectingOptions(slot.packageName, preferPlanBRightPane = isPlanB)
                            else -> destination = LauncherDestination.APP_DRAWER
                        }
                    },
                    // Direct System Menus: Wi-Fi opens System Wi-Fi, Bluetooth opens System Bluetooth, Clock pulls System Quick Settings!
                    onWifiClick = { SystemStatusMonitor.openWifiSettings(context) },
                    onWifiLongClick = { SystemStatusMonitor.openWifiSettings(context) },
                    onBluetoothClick = { SystemStatusMonitor.openBluetoothSettings(context) },
                    onBluetoothLongClick = { SystemStatusMonitor.openBluetoothSettings(context) },
                    onClockClick = {
                        val ok = DxCarAccessibilityService.instance?.openQuickSettings() == true
                        if (!ok) {
                            SystemStatusMonitor.openWifiSettings(context)
                        }
                    },
                    onNotificationsClick = { showNotificationCenter = !showNotificationCenter },
                    onToggle3DCursor = ::toggle3DPointerGlobal,
                    onVoiceNavClick = {
                        destination = LauncherDestination.DASHBOARD_SPLIT
                        onStartGeminiVoiceNav()
                    },
                    onBackClick = {
                        if (!handleInAppBack()) {
                            val acc = DxCarAccessibilityService.instance
                            if (acc != null) {
                                acc.goBack()
                            } else {
                                gestureHudText = DxCarI18n.tr(
                                    "請點設定最上方「權限需求」開啟無障礙服務",
                                    "请点设置最上方「权限需求」开启无障碍服务",
                                    "Enable Accessibility Service in Settings > Permissions first",
                                    "設定上部の「必要な権限」からユーザー補助サービスを有効にしてください"
                                )
                            }
                        }
                    },
                    onRecentsClick = {
                        val acc = DxCarAccessibilityService.instance
                        if (acc != null) {
                            acc.openRecents()
                        } else {
                            gestureHudText = DxCarI18n.tr(
                                "請點設定最上方「權限需求」開啟無障礙服務",
                                "请点设置最上方「权限需求」开启无障碍服务",
                                "Enable Accessibility Service in Settings > Permissions first",
                                "設定上部の「必要な権限」からユーザー補助サービスを有効にしてください"
                            )
                        }
                    }
                )
            }

            val stageContent: @Composable (Modifier) -> Unit = { stageMod ->
                Box(modifier = stageMod) {
                    // Keep DASHBOARD_SPLIT permanently mounted so Page 2 YouTube & MapView
                    // NEVER reload when opening App Drawer / Settings or pressing Dock Home!
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .zIndex(if (destination == LauncherDestination.DASHBOARD_SPLIT) 1f else 0f)
                    ) {
                        val startLatFallback = if (location.latitude != 0.0) location.latitude else config.lastKnownLat
                        val startLonFallback = if (location.longitude != 0.0) location.longitude else config.lastKnownLon

                        val allowPageTwo = config.homeExperimentMode == HomeExperimentMode.PLAN_D_ROOT_RESERVED ||
                            config.planAPage2Enabled

                        InfiniteLoopTwoPageDashboard(
                                currentPage = if (!allowPageTwo && currentDashboardPage == 2) 1 else currentDashboardPage,
                                allowPageTwo = allowPageTwo,
                                onPageChanged = { nextPage ->
                                    currentDashboardPage = nextPage
                                    if (config.showUiSwitchHint) {
                                        isUiSwitchHud = true
                                        gestureHudText = when (nextPage) {
                                            0 -> DxCarI18n.tr(
                                                "⓪ 第零介面 · 自訂小工具看板",
                                                "⓪ 第零界面 · 自定义小组件看板",
                                                "⓪ Page 0 · Custom Widgets Board",
                                                "⓪ ページ 0 · カスタムウィジェット"
                                            )
                                            1 -> DxCarI18n.tr(
                                                "① 第一介面 · 導航與音樂天氣",
                                                "① 第一界面 · 导航与音乐天气",
                                                "① Page 1 · Navigation, Media & Weather",
                                                "① ページ 1 · ナビ・音楽・天気"
                                            )
                                            else -> DxCarI18n.tr(
                                                "② 第二介面 · 1:1 方案A地圖 ＋ YouTube 視窗",
                                                "② 第二界面 · 1:1 方案A地图 ＋ YouTube 窗口",
                                                "② Page 2 · 1:1 Live Map + YouTube Window",
                                                "② ページ 2 · 1:1 マップ ＋ YouTube ウィンドウ"
                                            )
                                        }
                                    }
                                },
                                pageZeroContent = { onSeamDragDelta, onSeamDragEnd ->
                                    PageZeroWidgetBoard(
                                        config = config,
                                        sysStatus = sysStatus,
                                        location = location,
                                        weather = weather,
                                        isNight = isNight,
                                        onAddWidget = { item ->
                                            app.preferences.update { cur ->
                                                cur.copy(pageZeroWidgets = cur.pageZeroWidgets + item)
                                            }
                                        },
                                        onRemoveWidget = { item ->
                                            app.preferences.update { cur ->
                                                cur.copy(pageZeroWidgets = cur.pageZeroWidgets.filterNot { it.id == item.id })
                                            }
                                        },
                                        onUpdateWidget = { updated ->
                                            app.preferences.update { cur ->
                                                cur.copy(pageZeroWidgets = cur.pageZeroWidgets.map { if (it.id == updated.id) updated else it })
                                            }
                                        },
                                        onQuickNavigateQuery = { q ->
                                            currentDashboardPage = 1
                                            app.builtInNavEngine.navigateByQuery(
                                                query = q,
                                                startLat = startLatFallback,
                                                startLon = startLonFallback,
                                                onStatus = { msg -> gestureHudText = msg }
                                            )
                                        },
                                        onWeatherClick = {
                                            app.weatherRepository.openGoogleWeatherApp(context, config.weatherAppPackage)
                                        },
                                        onGoPageOne = { currentDashboardPage = 1 },
                                        seamHandle = {
                                            DashboardSeamSwipeHandle(
                                                pageIndicator = "0/3",
                                                onDragDelta = onSeamDragDelta,
                                                onDragEnd = onSeamDragEnd,
                                                modifier = Modifier
                                                    .width(32.dp)
                                                    .fillMaxHeight()
                                            )
                                        }
                                    )
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
                                        planCNavPackage = if (config.homeExperimentMode == HomeExperimentMode.PLAN_D_ROOT_RESERVED &&
                                            com.dxcar.launcher.system.RootHelperBridge.isRootHelperInstalled(context)
                                        ) {
                                            com.dxcar.launcher.system.RootHelperBridge.readPlanBConfig(context).ui1NavPackage
                                        } else {
                                            config.planCNavPackage
                                        },
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
                                            val styleName = mapEngineStyleLabelTr(styleItem)
                                            gestureHudText = DxCarI18n.tr(
                                                "🗺 已切換圖層：$styleName",
                                                "🗺 已切换图层：$styleName",
                                                "🗺 Map Layer: $styleName",
                                                "🗺 マップレイヤー切替：$styleName"
                                            )
                                        },
                                        onToggleTraffic = {
                                            val nextTraffic = !config.mapTrafficEnabled
                                            app.preferences.update { it.copy(mapTrafficEnabled = nextTraffic) }
                                            gestureHudText = if (nextTraffic) {
                                                DxCarI18n.tr(
                                                    "🚦 已開啟 Google 即時路況",
                                                    "🚦 已开启 Google 实时路况",
                                                    "🚦 Live Traffic Enabled",
                                                    "🚦 Google リアルタイム交通情報をオンにしました"
                                                )
                                            } else {
                                                DxCarI18n.tr(
                                                    "🗺 已關閉即時路況圖層",
                                                    "🗺 已关闭实时路况图层",
                                                    "🗺 Live Traffic Hidden",
                                                    "🗺 交通情報レイヤーをオフにしました"
                                                )
                                            }
                                        },
                                        onToggleMapStyle = {
                                            val allStyles = MapEngineStyle.visibleEntries
                                            val next = allStyles[(allStyles.indexOf(config.mapEngineStyle) + 1) % allStyles.size]
                                            app.preferences.update { it.copy(mapEngineStyle = next) }
                                            val nextName = mapEngineStyleLabelTr(next)
                                            gestureHudText = DxCarI18n.tr(
                                                "🗺 切換至 $nextName",
                                                "🗺 切换至 $nextName",
                                                "🗺 Switched to $nextName",
                                                "🗺 $nextName に切り替えました"
                                            )
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
                                            gestureHudText = DxCarI18n.tr(
                                                "🧭 已指定左側導航：$label",
                                                "🧭 已指定左侧导航：$label",
                                                "🧭 Primary Nav App: $label",
                                                "🧭 メインナビアプリを設定：$label"
                                            )
                                        },
                                        onLaunchPlanCFreeform = { pkg ->
                                            if (config.homeExperimentMode == HomeExperimentMode.PLAN_D_ROOT_RESERVED) {
                                                dispatchPlanBWindowLayout(1)
                                            } else {
                                                launchFreeformPrimaryBox(pkg)
                                                if (config.planCOverlayMusicPanelEnabled) {
                                                    if (Settings.canDrawOverlays(context)) {
                                                        PersistentDockOverlayService.showPlanCMusicPanel(context)
                                                    } else {
                                                        gestureHudText = DxCarI18n.tr(
                                                            "請點設定最上方「權限需求」授予懸浮權限以顯示右側音樂控盤",
                                                            "请点设置最上方「权限需求」授予悬浮权限以显示右侧音乐控盘",
                                                            "Grant Overlay Permission in Settings > Permissions to show floating media panel",
                                                            "右側の音楽パネルを表示するには設定上部の「必要な権限」でオーバーレイ権限を許可してください"
                                                        )
                                                    }
                                                }
                                            }
                                        },
                                        onLaunchPlanCSplit = { pkg -> SplitSidePanelActivity.launchWithRealGoogleMaps(context, pkg) },
                                        onLaunchPlanCFullscreen = { pkg ->
                                            PersistentDockOverlayService.hidePlanCMusicPanel()
                                            launchAppRespectingOptions(pkg)
                                        },
                                        onSwapPanes = {
                                            isMapPrimary = !isMapPrimary
                                            if (config.homeExperimentMode == HomeExperimentMode.PLAN_D_ROOT_RESERVED) {
                                                app.preferences.update { it.copy(page1Swapped = !it.page1Swapped) }
                                                dispatchPlanBWindowLayout(1)
                                            }
                                        },
                                        onOpenFullscreenMap = { destination = LauncherDestination.FULLSCREEN_MAP },
                                        onLaunchRealGoogleMaps = { query ->
                                            if (config.homeExperimentMode == HomeExperimentMode.PLAN_D_ROOT_RESERVED &&
                                                com.dxcar.launcher.system.RootHelperBridge.isRootHelperInstalled(context) &&
                                                query.isNullOrBlank()
                                            ) {
                                                launchAppRespectingOptions("com.google.android.apps.maps")
                                            } else {
                                                app.installedAppsRepository.launchGoogleMapsNavigation(query)
                                            }
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
                                        onWeatherClick = { app.weatherRepository.openGoogleWeatherApp(context, config.weatherAppPackage) },
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
                                            gestureHudText = DxCarI18n.tr(
                                                "🗑 已移除 $label（點擊 ＋ 可重新加入）",
                                                "🗑 已移除 $label（点击 ＋ 可重新加入）",
                                                "🗑 Removed $label (Tap + to re-add)",
                                                "🗑 $label を削除しました（＋タップで再追加）"
                                            )
                                        }
                                    )
                                },
                                pageTwoContent = { onSeamDragDelta, onSeamDragEnd ->
                                    val hasRootHelper = remember(isLauncherFg) {
                                        com.dxcar.launcher.system.RootHelperBridge.isRootHelperInstalled(context)
                                    }
                                    val planBConfig = remember(isLauncherFg, config.homeExperimentMode) {
                                        if (hasRootHelper && config.homeExperimentMode == HomeExperimentMode.PLAN_D_ROOT_RESERVED) {
                                            com.dxcar.launcher.system.RootHelperBridge.readPlanBConfig(context)
                                        } else {
                                            com.dxcar.launcher.system.PlanBHelperConfig(planBEnabled = false)
                                        }
                                    }
                                    SecondEqualMapYoutubeDashboard(
                                        isHorizontalSplit = layoutSpec.isHorizontalSplit,
                                        page2Swapped = config.page2Swapped,
                                        page2SplitRatio = if (planBConfig.planBEnabled) planBConfig.ui2SplitRatio else config.page2SplitRatio,
                                        onSwapPage2 = {
                                            app.preferences.update { it.copy(page2Swapped = !it.page2Swapped) }
                                            if (config.homeExperimentMode == HomeExperimentMode.PLAN_D_ROOT_RESERVED) {
                                                dispatchPlanBWindowLayout(2)
                                            }
                                        },
                                        onUpdatePage2Ratio = { nextRatio ->
                                            val r = nextRatio.coerceIn(0.25f, 0.75f)
                                            app.preferences.update { it.copy(page2SplitRatio = r) }
                                            com.dxcar.launcher.system.RootHelperBridge.updateUi2Ratio(context, r)
                                            if (config.homeExperimentMode == HomeExperimentMode.PLAN_D_ROOT_RESERVED) {
                                                dispatchPlanBWindowLayout(2)
                                            }
                                        },
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
                                            val allStyles = MapEngineStyle.visibleEntries
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

                    androidx.compose.animation.AnimatedVisibility(
                        visible = destination != LauncherDestination.DASHBOARD_SPLIT,
                        enter = scaleIn(initialScale = 0.96f, animationSpec = tween(220)) + fadeIn(tween(180)),
                        exit = scaleOut(targetScale = 0.97f, animationSpec = tween(160)) + fadeOut(tween(150)),
                        modifier = Modifier
                            .fillMaxSize()
                            .zIndex(2f)
                            .background(colors.canvasBackground)
                    ) {
                        AnimatedContent(
                            targetState = destination,
                            label = "overlayStageTransition",
                            modifier = Modifier.fillMaxSize()
                        ) { targetDest ->
                            when (targetDest) {
                                LauncherDestination.DASHBOARD_SPLIT -> {
                                    Box(Modifier.fillMaxSize())
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
                                        val allStyles = MapEngineStyle.visibleEntries
                                        val next = allStyles[(allStyles.indexOf(config.mapEngineStyle).coerceAtLeast(0) + 1) % allStyles.size]
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
                                    gestureHudText = DxCarI18n.tr(
                                        "🗑 已移除分屏捷徑：${sc.label}",
                                        "🗑 已移除分屏快捷方式：${sc.label}",
                                        "🗑 Removed split shortcut: ${sc.label}",
                                        "🗑 分割ショートカットを削除：${sc.label}"
                                    )
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
                                    gestureHudText = DxCarI18n.tr(
                                        "📁 已建立資料夾：$name",
                                        "📁 已新建文件夹：$name",
                                        "📁 Created folder: $name",
                                        "📁 フォルダを作成しました：$name"
                                    )
                                },
                                onAddAppToFolder = { folderId, pkg ->
                                    app.preferences.addAppToFolder(folderId, pkg)
                                    gestureHudText = DxCarI18n.tr(
                                        "📁 已加入資料夾",
                                        "📁 已加入文件夹",
                                        "📁 Added to folder",
                                        "📁 フォルダに追加しました"
                                    )
                                },
                                onRenameFolder = { folderId, newName ->
                                    app.preferences.renameAppFolder(folderId, newName)
                                },
                                onRemoveAppFromFolder = { folderId, pkg ->
                                    app.preferences.removeAppFromFolder(folderId, pkg)
                                },
                                onDeleteFolder = { folderId ->
                                    app.preferences.deleteAppFolder(folderId)
                                    gestureHudText = DxCarI18n.tr(
                                        "📁 已解散資料夾",
                                        "📁 已解散文件夹",
                                        "📁 Folder ungrouped",
                                        "📁 フォルダを解除しました"
                                    )
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
                                billingState = billingState,
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
                                    gestureHudText = if (launched) {
                                        DxCarI18n.tr(
                                            "🏠 正在切換至 ${targetLauncher.label}",
                                            "🏠 正在切换至 ${targetLauncher.label}",
                                            "🏠 Switching to ${targetLauncher.label}",
                                            "🏠 ${targetLauncher.label} に切り替え中"
                                        )
                                    } else {
                                        DxCarI18n.tr(
                                            "無法啟動 ${targetLauncher.label}",
                                            "无法启动 ${targetLauncher.label}",
                                            "Unable to launch ${targetLauncher.label}",
                                            "${targetLauncher.label} を起動できません"
                                        )
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
                                onExportConfigJson = { app.preferences.exportConfigJson() },
                                onImportConfigJson = { raw -> app.preferences.importConfigJson(raw) },
                                onResetSettingsToDefaults = {
                                    app.preferences.resetToDefaults()
                                    gestureHudText = DxCarI18n.tr(
                                        "↺ 已恢復所有啟動器設定為預設值",
                                        "↺ 已恢复所有启动器设置为默认值",
                                        "↺ All launcher settings reset to defaults",
                                        "↺ すべての設定を初期値に戻しました"
                                    )
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
                                    if (com.dxcar.launcher.BuildConfig.ENABLE_GITHUB_AUTO_UPDATE) {
                                        scope.launch {
                                            app.updateManager.checkForUpdate(autoDownload = config.autoCheckAndDownloadUpdate)
                                        }
                                    }
                                },
                                onDownloadOrInstallUpdate = {
                                    if (com.dxcar.launcher.BuildConfig.ENABLE_GITHUB_AUTO_UPDATE) {
                                        if (updateState.downloadedReady) {
                                            app.updateManager.installDownloadedApk()
                                        } else {
                                            scope.launch { app.updateManager.downloadAndInstall(autoLaunchInstaller = true) }
                                        }
                                    }
                                },
                                onPurchasePro = {
                                    (context as? android.app.Activity)?.let { act ->
                                        app.billingTrialManager.launchPurchaseFlow(act)
                                    }
                                },
                                onRestorePurchases = { app.billingTrialManager.restorePurchases() },
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
                                showPermissionsSheet = showSettingsPermissionsSheet,
                                onShowPermissionsSheetChange = { showSettingsPermissionsSheet = it },
                                activeTutorial = activeSettingsTutorial,
                                onActiveTutorialChange = { activeSettingsTutorial = it },
                                onClose = { destination = LauncherDestination.APP_DRAWER }
                            )
                        }
                    }
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
                        Icon(
                            Icons.Rounded.Apps,
                            contentDescription = str("展開 App 選單", "展开应用菜单", "Open App Drawer", "アプリ一覧を開く"),
                            tint = colors.primaryText,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }
            }

            // AUTO-UPDATE BANNER (GitHub direct flavor only)
            AnimatedVisibility(
                visible = com.dxcar.launcher.BuildConfig.ENABLE_GITHUB_AUTO_UPDATE &&
                    (updateState.downloading || updateState.downloadedReady) &&
                    destination == LauncherDestination.DASHBOARD_SPLIT,
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
                                    com.dxcar.launcher.i18n.DxCarI18n.str(
                                        "新版本 v${updateState.latestVersion} 已下載完成 · 點擊立即安裝",
                                        "新版本 v${updateState.latestVersion} 已下载完成 · 点击立即安装",
                                        "v${updateState.latestVersion} ready · Tap to install",
                                        "v${updateState.latestVersion} 準備完了 · タップして更新"
                                    )
                                } else {
                                    updateState.statusMessage.ifBlank {
                                        com.dxcar.launcher.i18n.DxCarI18n.str("正在下載更新…", "正在下载更新…", "Downloading update…", "更新をダウンロード中…")
                                    }
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

            // 7-DAY TRIAL EXPIRED PAYWALL OVERLAY (NT$500 Lifetime Unlock via Google Play Billing)
            if (!billingState.hasFullAccess) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxSize()
                        .zIndex(99f)
                        .background(Color.Black.copy(alpha = 0.88f))
                        .clickable(enabled = false) {}
                ) {
                    Surface(
                        color = colors.cardSurface,
                        shape = RoundedCornerShape(28.dp),
                        modifier = Modifier
                            .padding(24.dp)
                            .border(1.5.dp, colors.accent, RoundedCornerShape(28.dp))
                    ) {
                        Column(
                            modifier = Modifier.padding(28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Text(
                                text = com.dxcar.launcher.i18n.DxCarI18n.str(
                                    "🔒 七天全功能免費試用已結束",
                                    "🔒 七天全功能免费试用已结束",
                                    "🔒 7-Day Full-Feature Trial Ended",
                                    "🔒 7日間フル機能トライアル終了"
                                ),
                                color = colors.primaryText,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Text(
                                text = com.dxcar.launcher.i18n.DxCarI18n.str(
                                    "一次買斷 ${billingState.formattedPrice} 即可永久解鎖全部車機啟動器與手機遙控同步功能（無任何訂閱月費）",
                                    "一次买断 ${billingState.formattedPrice} 即可永久解锁全部车机启动器与手机遥控同步功能（无任何订阅月费）",
                                    "Unlock all Car Launcher & Phone Remote features forever for a one-time ${billingState.formattedPrice} payment.",
                                    "${billingState.formattedPrice} の買い切りで、車載ランチャー＆スマホ連携の全機能を永久にご利用いただけます。"
                                ),
                                color = colors.secondaryText,
                                fontSize = 14.sp
                            )
                            if (billingState.statusMessage.isNotBlank()) {
                                Text(billingState.statusMessage, color = colors.accent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Button(
                                    onClick = {
                                        (context as? android.app.Activity)?.let { act ->
                                            app.billingTrialManager.launchPurchaseFlow(act)
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = colors.accent, contentColor = colors.onAccent),
                                    shape = RoundedCornerShape(16.dp)
                                ) {
                                    Text(
                                        com.dxcar.launcher.i18n.DxCarI18n.str(
                                            "立即買斷 ${billingState.formattedPrice}",
                                            "立即买断 ${billingState.formattedPrice}",
                                            "Unlock ${billingState.formattedPrice}",
                                            "買い切り購入 ${billingState.formattedPrice}"
                                        ),
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                OutlinedButton(
                                    onClick = { app.billingTrialManager.restorePurchases() },
                                    shape = RoundedCornerShape(16.dp)
                                ) {
                                    Text(
                                        str("恢復購買", "恢复购买", "Restore Purchase", "購入を復元"),
                                        fontSize = 14.sp
                                    )
                                }
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
            val useCarBoxAudio by app.notificationCenter.useCarBoxAudio.collectAsState()
            Column(
                horizontalAlignment = Alignment.Start,
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(top = bannerTopPad, start = bannerStartPad, end = 20.dp)
            ) {
                IncomingCallBanner(
                    callState = activeCall,
                    scale = config.notificationBannerScale,
                    scaleFactorOverride = config.notificationBannerScaleFloat,
                    useCarBoxAudio = useCarBoxAudio,
                    onToggleAudioRoute = {
                        val isCar = app.notificationCenter.toggleCallAudioRoute(context)
                        gestureHudText = if (isCar) {
                            DxCarI18n.tr("🔊 通話音源：車機盒", "🔊 通话音源：车机盒", "🔊 Call Audio: Car Box", "🔊 通話音声：車載機")
                        } else {
                            DxCarI18n.tr("📱 通話音源：手機", "📱 通话音源：手机", "📱 Call Audio: Phone", "📱 通話音声：スマホ")
                        }
                    },
                    onAnswer = { app.notificationCenter.performCallAction(CallActionCommand.ANSWER) },
                    onDecline = { app.notificationCenter.performCallAction(CallActionCommand.DECLINE) }
                )
                IncomingMessageBanner(
                    message = popupMessage,
                    quickReplies = config.quickReplies,
                    scale = config.notificationBannerScale,
                    scaleFactorOverride = config.notificationBannerScaleFloat,
                    rightHandDrive = config.rightHandDrive,
                    onExpandQuickReplies = { app.notificationCenter.pausePopupDismiss() },
                    onReadAloud = { app.notificationCenter.readMessageAloud(it) },
                    onQuickReply = { item, reply ->
                        app.notificationCenter.sendQuickReply(item, reply)
                        gestureHudText = DxCarI18n.tr(
                            "💬 已傳送回覆：$reply",
                            "💬 已发送回复：$reply",
                            "💬 Sent reply: $reply",
                            "💬 返信を送信しました：$reply"
                        )
                    },
                    onVoiceReply = { item ->
                        app.notificationCenter.pausePopupDismiss()
                        onStartVoiceReply(item)
                    },
                    onMarkRead = { app.notificationCenter.markMessageRead(it) },
                    onDismiss = { app.notificationCenter.dismissPopupMessage() }
                )
                val otherNavApps = remember { com.dxcar.launcher.map.SharedLocationResolver.findInstalledOtherNavApps(context) }
                SharedLocationBanner(
                    locationItem = sharedLocation,
                    scale = config.notificationBannerScale,
                    scaleFactorOverride = config.notificationBannerScaleFloat,
                    otherNavApps = otherNavApps,
                    onOpenBuiltInMap = { loc ->
                        app.notificationCenter.dismissSharedLocation()
                        destination = LauncherDestination.DASHBOARD_SPLIT
                        currentDashboardPage = 1
                        val sLat = if (location.latitude != 0.0) location.latitude else config.lastKnownLat
                        val sLon = if (location.longitude != 0.0) location.longitude else config.lastKnownLon
                        app.builtInNavEngine.navigateByQuery(
                            query = loc.address.ifBlank { loc.label },
                            startLat = sLat,
                            startLon = sLon,
                            explicitDestLat = loc.latitude.takeIf { it != 0.0 },
                            explicitDestLon = loc.longitude.takeIf { it != 0.0 },
                            onStatus = { msg -> gestureHudText = msg }
                        )
                    },
                    onOpenGoogleMaps = { loc ->
                        app.notificationCenter.dismissSharedLocation()
                        com.dxcar.launcher.map.SharedLocationResolver.openInGoogleMaps(
                            context, loc.label, loc.address, loc.latitude, loc.longitude
                        )
                    },
                    onOpenOtherMap = { loc, pkg ->
                        app.notificationCenter.dismissSharedLocation()
                        com.dxcar.launcher.map.SharedLocationResolver.openInOtherMapChooser(
                            context, pkg, loc.label, loc.address, loc.latitude, loc.longitude
                        )
                    },
                    onDismiss = { app.notificationCenter.dismissSharedLocation() }
                )
            }

            // HUD TOAST PILL
            GestureFeedbackPill(
                text = gestureHudText,
                showDontShowAgain = isUiSwitchHud,
                onDontShowAgain = {
                    app.preferences.update { it.copy(showUiSwitchHint = false) }
                    gestureHudText = null
                    isUiSwitchHud = false
                },
                modifier = Modifier.align(Alignment.Center)
            )

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
                        gestureHudText = DxCarI18n.tr(
                            "💬 已傳送回覆：$reply",
                            "💬 已发送回复：$reply",
                            "💬 Sent reply: $reply",
                            "💬 返信を送信しました：$reply"
                        )
                    },
                    onMarkRead = { app.notificationCenter.markMessageRead(it) },
                    onRemoveMessage = { app.notificationCenter.removeMessage(it) },
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
                        gestureHudText = DxCarI18n.tr(
                            "✓ 已加入「$label」至第 ${slotIdx + 1} 格",
                            "✓ 已添加「$label」至第 ${slotIdx + 1} 格",
                            "✓ Added \"$label\" to Slot ${slotIdx + 1}",
                            "✓「$label」をスロット ${slotIdx + 1} に追加しました"
                        )
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
                        gestureHudText = DxCarI18n.tr(
                            "✓ 創建分屏捷徑成功：${shortcut.label} (${shortcut.ratio.label})",
                            "✓ 创建分屏快捷方式成功：${shortcut.label} (${shortcut.ratio.label})",
                            "✓ Saved split shortcut: ${shortcut.label} (${shortcut.ratio.label})",
                            "✓ 分割ショートカットを作成：${shortcut.label} (${shortcut.ratio.label})"
                        )
                    },
                    onTestLaunchShortcut = ::dispatchSplitShortcutWithDockOffsets,
                    onPickSystemShortcut = onPickStockSystemShortcut,
                    onDismiss = { showSplitCreator = false }
                )
            }

            // Fallback in-app Non-Blocking Floating 3D Control Bar when system overlay window isn't attached
            val hasSystem3DWindow by PersistentDockOverlayService.is3DWindowAttached.collectAsState()
            if (!hasSystem3DWindow && cursor3DActive) {
                var inAppBarOffsetX by remember { mutableStateOf(0f) }
                var inAppBarOffsetY by remember { mutableStateOf(0f) }
                val maxHalfW = (dm.widthPixels * 0.42f).coerceAtLeast(40f)
                val maxDownY = (dm.heightPixels * 0.72f).coerceAtLeast(80f)
                Floating3DControlBar(
                    onOpenTutorial = { Cursor3DStateHolder.openTutorial() },
                    onExit3DMode = { Cursor3DStateHolder.setActive(false) },
                    onDragDelta = { dx, dy ->
                        inAppBarOffsetX = (inAppBarOffsetX + dx).coerceIn(-maxHalfW, maxHalfW)
                        inAppBarOffsetY = (inAppBarOffsetY + dy).coerceIn(0f, maxDownY)
                    },
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 14.dp)
                        .offset { IntOffset(inAppBarOffsetX.roundToInt(), inAppBarOffsetY.roundToInt()) }
                        .zIndex(45f)
                )
                if (cursor3DTutorial) {
                    Box(modifier = Modifier.fillMaxSize().zIndex(50f)) {
                        ExtraLargeTutorialModal(
                            title = str(
                                "🧊 3D 雙指快捷懸浮條 · 操作教學",
                                "🧊 3D 双指快捷悬浮条 · 操作教程",
                                "🧊 Floating 3D Control Bar · Guide",
                                "🧊 3D 操作ツールバー · 操作ガイド"
                            ),
                            steps = tutorial3DControlSteps(),
                            highlightNote = str(
                                "💡 內建地圖免無障礙即可直接調整 3D／縮放；若要在原版 Google 地圖等外部導航使用，請在設定最上方「權限需求」開啟【顯示在其他應用程式上層】與【無障礙服務】。",
                                "💡 内置地图免无障碍即可直接调整 3D／缩放；若要在原版 Google 地图等外部导航使用，请在设置最上方「权限需求」开启【显示在其他应用上层】与【无障碍服务】。",
                                "💡 Works directly on the built-in map right away; enable [Display Over Other Apps] and [Accessibility Service] in Settings > Permissions to control external Google Maps.",
                                "💡 内蔵マップでは追加権限なしで動作します。Google マップ等の外部ナビで使う場合は、設定上部の「権限設定」から【重ねて表示】と【ユーザー補助】を有効にしてください。"
                            ),
                            onClose = { Cursor3DStateHolder.closeTutorial() }
                        )
                    }
                }
            }
            if (!Settings.canDrawOverlays(context)) {
                RemoteCarMousePointerOverlay(cursorState = remoteCursor)
            }
        }
    }
}

/**
 * Finite Two-Interface Dashboard Container (NO infinite wrap-around, strictly clipped so Page 2 never bleeds over Dock):
 * - Page 0 (第一介面): Coolwalk Split Dashboard (70/30 adjustable, Map + Weather/Media/6-Shortcuts)
 * - Page 1 (第二介面): 1:1 Equal Split Dashboard (Left = Plan A Built-in Map, Right = Inline Playable YouTube)
 * - Dragging the center divider line switches cleanly between Page 0 (left) and Page 1 (right).
 */
@Composable
private fun InfiniteLoopTwoPageDashboard(
    currentPage: Int,
    allowPageTwo: Boolean = true,
    onPageChanged: (Int) -> Unit,
    pageZeroContent: @Composable (onSeamDragDelta: (Float) -> Unit, onSeamDragEnd: () -> Unit) -> Unit,
    pageOneContent: @Composable (onSeamDragDelta: (Float) -> Unit, onSeamDragEnd: () -> Unit) -> Unit,
    pageTwoContent: @Composable (onSeamDragDelta: (Float) -> Unit, onSeamDragEnd: () -> Unit) -> Unit
) {
    val scope = rememberCoroutineScope()
    val dragPx = remember { Animatable(0f) }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .clipToBounds()
    ) {
        val widthPx = constraints.maxWidth.toFloat().coerceAtLeast(320f)
        val thresholdPx = (widthPx * 0.10f).coerceIn(55f, 150f)

        val handleDragDelta: (Float) -> Unit = { delta ->
            scope.launch {
                val next = when (currentPage) {
                    0 -> (dragPx.value + delta).coerceIn(-widthPx * 0.95f, 0f)
                    1 -> (dragPx.value + delta).coerceIn(if (allowPageTwo) -widthPx * 0.95f else 0f, widthPx * 0.95f)
                    else -> (dragPx.value + delta).coerceIn(0f, widthPx * 0.95f)
                }
                dragPx.snapTo(next)
            }
        }

        val handleDragEnd: () -> Unit = {
            val cur = dragPx.value
            scope.launch {
                when {
                    currentPage == 0 && cur <= -thresholdPx -> {
                        dragPx.animateTo(
                            targetValue = -widthPx,
                            animationSpec = tween(
                                durationMillis = 360,
                                easing = CubicBezierEasing(0.25f, 0.1f, 0.25f, 1.0f)
                            )
                        )
                        onPageChanged(1)
                        dragPx.snapTo(0f)
                    }
                    currentPage == 1 && cur >= thresholdPx -> {
                        dragPx.animateTo(
                            targetValue = widthPx,
                            animationSpec = tween(
                                durationMillis = 360,
                                easing = CubicBezierEasing(0.25f, 0.1f, 0.25f, 1.0f)
                            )
                        )
                        onPageChanged(0)
                        dragPx.snapTo(0f)
                    }
                    currentPage == 1 && allowPageTwo && cur <= -thresholdPx -> {
                        dragPx.animateTo(
                            targetValue = -widthPx,
                            animationSpec = tween(
                                durationMillis = 360,
                                easing = CubicBezierEasing(0.25f, 0.1f, 0.25f, 1.0f)
                            )
                        )
                        onPageChanged(2)
                        dragPx.snapTo(0f)
                    }
                    currentPage == 2 && cur >= thresholdPx -> {
                        dragPx.animateTo(
                            targetValue = widthPx,
                            animationSpec = tween(
                                durationMillis = 360,
                                easing = CubicBezierEasing(0.25f, 0.1f, 0.25f, 1.0f)
                            )
                        )
                        onPageChanged(1)
                        dragPx.snapTo(0f)
                    }
                    else -> {
                        dragPx.animateTo(0f, spring(stiffness = Spring.StiffnessMedium))
                    }
                }
            }
        }

        val curDx = dragPx.value
        val isDragging = abs(curDx) > 0.5f

        val page0OffsetX = when (currentPage) {
            0 -> curDx.roundToInt()
            1 -> (-widthPx + curDx).roundToInt()
            else -> (-2f * widthPx + curDx).roundToInt()
        }
        val page1OffsetX = when (currentPage) {
            1 -> curDx.roundToInt()
            0 -> (widthPx + curDx).roundToInt()
            else -> (-widthPx + curDx).roundToInt()
        }
        val page2OffsetX = when (currentPage) {
            2 -> curDx.roundToInt()
            1 -> (widthPx + curDx).roundToInt()
            else -> (2f * widthPx + curDx).roundToInt()
        }

        var keepPageTwoAlive by remember { mutableStateOf(false) }
        LaunchedEffect(currentPage, allowPageTwo) {
            if (!allowPageTwo) {
                keepPageTwoAlive = false
            } else if (currentPage == 2) {
                keepPageTwoAlive = true
            }
        }

        if (currentPage == 0 || (isDragging && currentPage == 1 && curDx > 0f)) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clipToBounds()
                    .zIndex(if (currentPage == 0) 2f else 1f)
                    .offset { IntOffset(page0OffsetX, 0) }
            ) {
                pageZeroContent(handleDragDelta, handleDragEnd)
            }
        }

        if (currentPage == 1 || isDragging) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clipToBounds()
                    .zIndex(if (currentPage == 1) 2f else 1f)
                    .offset { IntOffset(page1OffsetX, 0) }
            ) {
                pageOneContent(handleDragDelta, handleDragEnd)
            }
        }

        if (allowPageTwo && (currentPage == 2 || isDragging || keepPageTwoAlive)) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clipToBounds()
                    .zIndex(if (currentPage == 2) 2f else 0f)
                    .offset { IntOffset(page2OffsetX, 0) }
            ) {
                pageTwoContent(handleDragDelta, handleDragEnd)
            }
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
    activeRatioLabel: String? = null,
    onDragDelta: (Float) -> Unit,
    onDragEnd: () -> Unit,
    onLongPress: (() -> Unit)? = null,
    onDoubleTap: (() -> Unit)? = null,
    onLongPressDragPx: ((Float) -> Unit)? = null,
    onLongPressDragEnd: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val colors = LocalDxCarColors.current
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    var inRatioDrag by remember { mutableStateOf(false) }
    val pulseTrans = androidx.compose.animation.core.rememberInfiniteTransition(label = "seamPulse")
    val pulseAlpha by pulseTrans.animateFloat(
        initialValue = 0.55f,
        targetValue = 1.0f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = androidx.compose.animation.core.tween(420),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
        ),
        label = "seamPulseAlpha"
    )
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .pointerInput(pageIndicator, onLongPress, onDoubleTap, onLongPressDragPx) {
                if (onLongPress != null || onDoubleTap != null) {
                    detectTapGestures(
                        onDoubleTap = onDoubleTap?.let { cb ->
                            {
                                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                cb()
                            }
                        },
                        onLongPress = if (onLongPressDragPx == null) onLongPress?.let { cb ->
                            {
                                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                cb()
                            }
                        } else null
                    )
                }
            }
            .pointerInput(pageIndicator, onLongPressDragPx) {
                if (onLongPressDragPx != null) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = {
                            inRatioDrag = true
                            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            onLongPressDragPx(dragAmount.x)
                        },
                        onDragEnd = {
                            inRatioDrag = false
                            onLongPressDragEnd?.invoke()
                        },
                        onDragCancel = {
                            inRatioDrag = false
                        }
                    )
                }
            }
            .pointerInput(pageIndicator) {
                detectHorizontalDragGestures(
                    onHorizontalDrag = { change, dragAmount ->
                        if (!inRatioDrag) {
                            change.consume()
                            onDragDelta(dragAmount)
                        }
                    },
                    onDragEnd = {
                        if (!inRatioDrag) onDragEnd()
                    },
                    onDragCancel = {
                        if (!inRatioDrag) onDragEnd()
                    }
                )
            }
    ) {
        // Subtle vertical center seam line (glows & thickens when in ratio-adjust mode)
        Box(
            modifier = Modifier
                .width(if (inRatioDrag) 5.dp else 3.dp)
                .fillMaxHeight(if (inRatioDrag) 0.90f else 0.76f)
                .clip(RoundedCornerShape(3.dp))
                .background(
                    if (inRatioDrag) colors.accent.copy(alpha = pulseAlpha)
                    else colors.accent.copy(alpha = 0.35f)
                )
        )

        // Tactile Center Grip Pill (expands & pulses with ◀ ratio ▶ animation when ready to adjust)
        Surface(
            color = if (inRatioDrag) colors.cardSurface.copy(alpha = 0.98f) else colors.cardSurfaceElevated.copy(alpha = 0.94f),
            shape = RoundedCornerShape(14.dp),
            shadowElevation = if (inRatioDrag) 12.dp else 6.dp,
            modifier = Modifier
                .border(
                    width = if (inRatioDrag) 2.2.dp else 1.2.dp,
                    color = if (inRatioDrag) colors.accent.copy(alpha = pulseAlpha) else colors.accent.copy(alpha = 0.85f),
                    shape = RoundedCornerShape(14.dp)
                )
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(3.dp),
                modifier = Modifier.padding(
                    horizontal = if (inRatioDrag) 7.dp else 4.dp,
                    vertical = if (inRatioDrag) 12.dp else 10.dp
                )
            ) {
                Box(
                    modifier = Modifier
                        .width(if (inRatioDrag) 6.dp else 4.dp)
                        .height(if (inRatioDrag) 28.dp else 22.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(if (inRatioDrag) colors.accent.copy(alpha = pulseAlpha) else colors.accent)
                )
                Text(
                    text = if (inRatioDrag && !activeRatioLabel.isNullOrBlank()) activeRatioLabel else pageIndicator,
                    color = if (inRatioDrag) colors.accent else colors.primaryText,
                    fontSize = if (inRatioDrag) 10.sp else 9.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1
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
    page2Swapped: Boolean,
    page2SplitRatio: Float,
    onSwapPage2: () -> Unit,
    onUpdatePage2Ratio: (Float) -> Unit,
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
    val isMapFirst = !page2Swapped
    val swapAnim by animateFloatAsState(
        targetValue = if (isMapFirst) 0f else 1f,
        animationSpec = tween(durationMillis = 650, easing = CubicBezierEasing(0.32f, 0f, 0.18f, 1f)),
        label = "page2Swap"
    )

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp)
    ) {
        val gap = 16.dp
        if (isHorizontalSplit) {
            var liveRatio by remember(page2SplitRatio) { mutableStateOf(page2SplitRatio.coerceIn(0.25f, 0.75f)) }
            val ratio = liveRatio.coerceIn(0.25f, 0.75f)
            val totalAvailPx = with(androidx.compose.ui.platform.LocalDensity.current) { (maxWidth - gap).toPx().coerceAtLeast(300f) }
            val leftPaneW = ((maxWidth - gap) * ratio).coerceAtLeast(140.dp)
            val rightPaneW = ((maxWidth - gap) - leftPaneW).coerceAtLeast(140.dp)
            val fullH = maxHeight
            val mapW = if (isMapFirst) leftPaneW else rightPaneW
            val ytW = if (isMapFirst) rightPaneW else leftPaneW
            val mapX = lerp(0.dp, leftPaneW + gap, swapAnim)
            val ytX = lerp(leftPaneW + gap, 0.dp, swapAnim)

            // 1:1 Plan A Built-in Map (stays continuously mounted while swapping Left <-> Right)
            Box(
                modifier = Modifier
                    .zIndex(if (isMapFirst) 2f else 1f)
                    .offset(x = mapX, y = 0.dp)
                    .width(mapW)
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

            // 1:1 Inline Playable YouTube Video Card (Non-Fullscreen, swaps Left <-> Right smoothly)
            Box(
                modifier = Modifier
                    .zIndex(if (isMapFirst) 1f else 2f)
                    .offset(x = ytX, y = 0.dp)
                    .width(ytW)
                    .height(fullH)
                    .clip(cardShape)
                    .border(colors.cardBorderWidthDp.dp, colors.cardBorder, cardShape)
            ) {
                EmbeddedYoutubePlayerCard()
            }

            // Center Seam Divider Line (Double-tap to swap Left <-> Right; Long-press drag to resize ratio with glow animation; Swipe to Page 1)
            val pctL = (ratio * 100).toInt()
            DashboardSeamSwipeHandle(
                pageIndicator = if (kotlin.math.abs(ratio - page2SplitRatio) > 0.005f) "$pctL:${100 - pctL}" else "2/3",
                activeRatioLabel = "◀ $pctL:${100 - pctL} ▶",
                onDragDelta = onSeamDragDelta,
                onDragEnd = onSeamDragEnd,
                onDoubleTap = onSwapPage2,
                onLongPressDragPx = { dx ->
                    liveRatio = (liveRatio + (dx / totalAvailPx)).coerceIn(0.25f, 0.75f)
                },
                onLongPressDragEnd = {
                    onUpdatePage2Ratio(liveRatio)
                },
                modifier = Modifier
                    .zIndex(5f)
                    .offset(x = leftPaneW + (gap / 2f) - 28.dp, y = 0.dp)
                    .width(56.dp)
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
                pageIndicator = "2/3",
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
            // Directly render LiveMapCard without any initial "how to present map" chooser prompt!
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
            if (!primary) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .zIndex(30f)
                        .clickable { onSwapPanes() }
                )
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
                showSwapButton = true,
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
    val showMapPrimary = isMapPrimary
    val swapProgress = remember { Animatable(if (showMapPrimary) 0f else 1f) }
    LaunchedEffect(showMapPrimary) {
        val target = if (showMapPrimary) 0f else 1f
        if (abs(swapProgress.value - target) > 0.001f) {
            swapProgress.animateTo(
                targetValue = target,
                animationSpec = tween(
                    durationMillis = 680,
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
                pageIndicator = "1/3",
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
                pageIndicator = "1/3",
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

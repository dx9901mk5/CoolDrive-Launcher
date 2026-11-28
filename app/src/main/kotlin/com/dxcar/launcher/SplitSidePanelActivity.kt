package com.dxcar.launcher

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Splitscreen
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.dxcar.launcher.accessibility.DxCarAccessibilityService
import com.dxcar.launcher.comms.CallActionCommand
import com.dxcar.launcher.data.HomeExperimentMode
import com.dxcar.launcher.data.ThemeMode
import com.dxcar.launcher.layout.CoolwalkLayoutEngine
import com.dxcar.launcher.layout.ResolvedDockPlacement
import com.dxcar.launcher.layout.ScreenMetrics
import com.dxcar.launcher.map.SharedLocationResolver
import com.dxcar.launcher.overlay.PersistentDockOverlayService
import com.dxcar.launcher.system.PermissionHelper
import com.dxcar.launcher.system.RootHelperBridge
import com.dxcar.launcher.theme.DayNightEngine
import com.dxcar.launcher.theme.DxCarTheme
import com.dxcar.launcher.theme.LocalDxCarColors
import com.dxcar.launcher.ui.BuiltInShortcutTokens
import com.dxcar.launcher.ui.IncomingCallBanner
import com.dxcar.launcher.ui.IncomingMessageBanner
import com.dxcar.launcher.ui.MediaControlCard
import com.dxcar.launcher.ui.SharedLocationBanner
import com.dxcar.launcher.ui.bouncyClickable
import com.dxcar.launcher.ui.buildRecentDockSlots
import kotlinx.coroutines.delay

/**
 * Dedicated resizable non-Home Activity serving as:
 * 1) Plan B UI 1 Right 30% Media Split App (runs natively side-by-side with Left 70% Google Maps!
 *    Houses ALL Plan A right-side elements: Media Player + Album Cover + Weather + 6 Shortcuts + Integrated Bottom Dock,
 *    where clicking any Dock App launches directly into the Right 30% Split Window without disturbing Google Maps!)
 * 2) Plan A / Standard native AOSP Split-Screen side panel alongside Google Maps.
 */
class SplitSidePanelActivity : ComponentActivity() {

    private val isSplitMode = mutableStateOf(false)
    private val isPlanBSplitHost = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        hideSystemBars()
        isSplitMode.value = isInMultiWindowMode

        val app = application as DxCarApp
        val autoSplit = intent.getBooleanExtra(EXTRA_AUTO_LAUNCH_MAPS, false)
        val planBIntentFlag = intent.getBooleanExtra(EXTRA_PLAN_B_MODE, false) ||
            app.preferences.config.value.homeExperimentMode == HomeExperimentMode.PLAN_D_ROOT_RESERVED
        isPlanBSplitHost.value = planBIntentFlag
        val navPkg = intent.getStringExtra(EXTRA_NAV_PACKAGE)?.takeIf { it.isNotBlank() }

        setContent {
            val config by app.preferences.config.collectAsState()
            val sysStatus by app.systemStatusMonitor.status.collectAsState()
            val media by app.mediaControlBridge.mediaState.collectAsState()
            val weather by app.weatherRepository.weather.collectAsState()
            val installedApps by app.installedAppsRepository.apps.collectAsState()
            val liveRecentPackages by app.installedAppsRepository.recentPackages.collectAsState()
            val activeCall by app.notificationCenter.activeCall.collectAsState()
            val popupMessage by app.notificationCenter.popupMessage.collectAsState()
            val sharedLocation by app.notificationCenter.sharedLocation.collectAsState()
            val accConnected by DxCarAccessibilityService.isConnected.collectAsState()
            val inSplit by isSplitMode
            val isPlanB = isPlanBSplitHost.value || config.homeExperimentMode == HomeExperimentMode.PLAN_D_ROOT_RESERVED
            val targetNavPkg = navPkg ?: config.planCNavPackage.ifBlank { "com.google.android.apps.maps" }

            var splitTriggered by remember { mutableStateOf(false) }

            // Only run AOSP Accessibility auto-split when explicitly requested in non-Plan-B mode!
            LaunchedEffect(autoSplit, isPlanB, accConnected, targetNavPkg) {
                if (autoSplit && !isPlanB && !splitTriggered && !isInMultiWindowMode) {
                    splitTriggered = true
                    delay(320L)
                    val acc = DxCarAccessibilityService.instance
                    if (acc != null) {
                        app.installedAppsRepository.launchAppFullscreen(targetNavPkg, forceFullscreenWindowMode = false)
                        val deadline = System.currentTimeMillis() + 1_800L
                        while (System.currentTimeMillis() < deadline) {
                            if (DxCarAccessibilityService.foregroundPackage.value == targetNavPkg) break
                            delay(80L)
                        }
                        delay(260L)
                        acc.enterAospSplitScreenFromCurrentApp()
                        delay(380L)
                        startActivity(
                            Intent(this@SplitSidePanelActivity, SplitSidePanelActivity::class.java).apply {
                                putExtra(EXTRA_AUTO_LAUNCH_MAPS, false)
                                putExtra(EXTRA_NAV_PACKAGE, targetNavPkg)
                                addFlags(
                                    Intent.FLAG_ACTIVITY_NEW_TASK or
                                        Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT or
                                        Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                                )
                            }
                        )
                    }
                }
            }

            val dm = resources.displayMetrics
            val screenMetrics = remember(dm.widthPixels, dm.heightPixels, dm.densityDpi) {
                ScreenMetrics(dm.widthPixels, dm.heightPixels, dm.densityDpi)
            }
            val layoutSpec = remember(screenMetrics, config.dockPositionMode, config.rightHandDrive, config.primarySplitRatio) {
                CoolwalkLayoutEngine.compute(
                    metrics = screenMetrics,
                    dockMode = config.dockPositionMode,
                    rightHandDrive = config.rightHandDrive,
                    customPrimaryRatio = config.primarySplitRatio
                )
            }
            val dockSlots = remember(liveRecentPackages, config.recentDockPackages, config.pinnedDockPackages, installedApps) {
                buildRecentDockSlots(
                    recentPackages = (liveRecentPackages + config.recentDockPackages).distinct(),
                    fallbackPackages = config.pinnedDockPackages,
                    installedApps = installedApps,
                    activePackage = ""
                )
            }

            fun launchIntoRight30Split(targetPackage: String) {
                if (targetPackage.isBlank()) return
                val helperCfg = RootHelperBridge.readPlanBConfig(this@SplitSidePanelActivity)
                val dockPx = (layoutSpec.dockThicknessDp * screenMetrics.density).toInt().coerceAtLeast(0)
                val launchedViaHelper = if (isPlanB && RootHelperBridge.isRootHelperInstalled(this@SplitSidePanelActivity)) {
                    RootHelperBridge.launchPlanBRightPaneApp(
                        context = this@SplitSidePanelActivity,
                        packageName = targetPackage,
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
                    false
                }
                if (launchedViaHelper) {
                    app.installedAppsRepository.recordRecent(targetPackage)
                } else {
                    app.installedAppsRepository.launchAppFullscreen(targetPackage, forceFullscreenWindowMode = false)
                }
            }

            fun dispatchPlanBSwapOrPage(targetPage: Int) {
                val helperCfg = RootHelperBridge.readPlanBConfig(this@SplitSidePanelActivity)
                val dockPx = (layoutSpec.dockThicknessDp * screenMetrics.density).toInt().coerceAtLeast(0)
                PersistentDockOverlayService.planBActivePage.value = targetPage
                PersistentDockOverlayService.planBInFullscreenApp.value = false
                RootHelperBridge.applyPlanBPage(
                    context = this@SplitSidePanelActivity,
                    page = targetPage,
                    screenWidth = screenMetrics.widthPx,
                    screenHeight = screenMetrics.heightPx,
                    dockOffsetLeft = if (layoutSpec.dockPlacement == ResolvedDockPlacement.LEFT) dockPx else 0,
                    dockOffsetRight = if (layoutSpec.dockPlacement == ResolvedDockPlacement.RIGHT) dockPx else 0,
                    dockOffsetTop = if (layoutSpec.dockPlacement == ResolvedDockPlacement.TOP) dockPx else 0,
                    dockOffsetBottom = if (layoutSpec.dockPlacement == ResolvedDockPlacement.BOTTOM) dockPx else 0,
                    ui1Ratio = if (helperCfg.planBEnabled) helperCfg.ui1SplitRatio else config.primarySplitRatio,
                    ui1Swapped = app.preferences.config.value.page1Swapped,
                    ui2Ratio = if (helperCfg.planBEnabled) helperCfg.ui2SplitRatio else config.page2SplitRatio,
                    ui2Swapped = app.preferences.config.value.page2Swapped
                )
            }

            fun openMainLauncherWithAction(extraAction: String?) {
                if (isPlanB) {
                    RootHelperBridge.parkPlanBWindows(
                        this@SplitSidePanelActivity,
                        screenMetrics.widthPx,
                        screenMetrics.heightPx
                    )
                }
                startActivity(
                    Intent(this@SplitSidePanelActivity, MainActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                        if (extraAction != null) putExtra(MainActivity.EXTRA_ACTION, extraAction)
                    }
                )
            }

            val systemIsDark =
                (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
            val isNight = DayNightEngine.isNightNow(config, sysStatus.now, systemIsDark)

            DxCarTheme(
                isNight = isNight,
                accentIndex = config.accentColorIndex,
                stylePreset = config.stylePreset
            ) {
                val colors = LocalDxCarColors.current
                val cardShape = RoundedCornerShape(colors.cardCornerDp.dp)

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(colors.canvasBackground)
                        .padding(8.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (!isPlanB && !inSplit && !accConnected) {
                            Surface(
                                color = colors.cardSurfaceElevated,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.dp, colors.accent.copy(alpha = 0.6f), RoundedCornerShape(14.dp))
                                    .bouncyClickable {
                                        PermissionHelper.openAccessibilitySettings(this@SplitSidePanelActivity)
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Rounded.Splitscreen, contentDescription = null, tint = colors.accent)
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = "開啟無障礙服務即可自動分割；或用系統分割配對 Google 地圖",
                                        color = colors.primaryText,
                                        fontSize = 11.5.sp
                                    )
                                }
                            }
                        }

                        // FULL PLAN-A-STYLE MEDIA CONTROL CARD (Album Cover + Weather + 6 Shortcuts!)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .clip(cardShape)
                                .border(colors.cardBorderWidthDp.dp, colors.cardBorder, cardShape)
                        ) {
                            MediaControlCard(
                                media = media,
                                weather = weather,
                                isPrimary = false,
                                pureMusicMode = false,
                                showSwapButton = true,
                                showSixShortcuts = config.showSixShortcutsInMediaPanel,
                                customShortcutSlots = config.customShortcutSlots,
                                installedApps = installedApps,
                                splitShortcuts = config.splitShortcuts,
                                onWeatherClick = {
                                    app.weatherRepository.openGoogleWeatherApp(
                                        this@SplitSidePanelActivity,
                                        config.weatherAppPackage
                                    )
                                },
                                onPlayPause = { app.mediaControlBridge.togglePlayPause() },
                                onNext = { app.mediaControlBridge.skipNext() },
                                onPrevious = { app.mediaControlBridge.skipPrevious() },
                                onToggleFavorite = { app.mediaControlBridge.toggleFavoriteOrLike() },
                                onSelectSession = { app.mediaControlBridge.selectSession(it) },
                                onOpenMediaApp = {
                                    val pkg = media.packageName
                                    if (pkg.isNotBlank()) launchIntoRight30Split(pkg)
                                },
                                onOpenYoutubeFullscreen = {
                                    val yt = app.installedAppsRepository.findBestYoutubePackage()
                                    if (yt != null) launchIntoRight30Split(yt)
                                },
                                onSwapRequested = {
                                    if (isPlanB) {
                                        app.preferences.update { it.copy(page1Swapped = !it.page1Swapped) }
                                        dispatchPlanBSwapOrPage(1)
                                    } else {
                                        app.installedAppsRepository.launchPackageAdjacent(targetNavPkg)
                                    }
                                },
                                onCustomSlotEmptyClick = {
                                    openMainLauncherWithAction(MainActivity.EXTRA_GO_DASHBOARD)
                                },
                                onCustomSlotLaunch = { token ->
                                    when {
                                        token == BuiltInShortcutTokens.SETTINGS ->
                                            openMainLauncherWithAction(MainActivity.EXTRA_OPEN_SETTINGS)
                                        token == BuiltInShortcutTokens.DIALER ->
                                            app.installedAppsRepository.launchDialer()
                                        token == BuiltInShortcutTokens.DAY_NIGHT -> {
                                            val next = if (isNight) ThemeMode.FORCE_DAY else ThemeMode.FORCE_NIGHT
                                            app.preferences.update { it.copy(themeMode = next) }
                                        }
                                        token == BuiltInShortcutTokens.YOUTUBE -> {
                                            val yt = app.installedAppsRepository.findBestYoutubePackage()
                                            if (yt != null) launchIntoRight30Split(yt)
                                        }
                                        !token.startsWith("__") -> launchIntoRight30Split(token)
                                    }
                                }
                            )
                        }

                        // INTEGRATED PLAN B BOTTOM DOCK BAR (Clicking any Dock App launches directly in Right 30% Split Window!)
                        Surface(
                            color = colors.cardSurface,
                            shape = RoundedCornerShape(18.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(62.dp)
                                .border(colors.cardBorderWidthDp.dp, colors.cardBorder, RoundedCornerShape(18.dp))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                // App Drawer button
                                Surface(
                                    color = colors.accent.copy(alpha = 0.2f),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .size(44.dp)
                                        .bouncyClickable { openMainLauncherWithAction(MainActivity.EXTRA_OPEN_DRAWER) }
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(Icons.Rounded.Apps, contentDescription = "應用程式", tint = colors.accent, modifier = Modifier.size(22.dp))
                                    }
                                }

                                // Scrollable Dock App Icons -> Directly launch into Right 30% Split Window!
                                Row(
                                    modifier = Modifier
                                        .weight(1f)
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    dockSlots.forEach { slot ->
                                        val iconBmp = slot.installedApp?.icon
                                        Surface(
                                            color = colors.cardSurfaceElevated,
                                            shape = RoundedCornerShape(12.dp),
                                            modifier = Modifier
                                                .size(44.dp)
                                                .border(1.dp, colors.cardBorder, RoundedCornerShape(12.dp))
                                                .bouncyClickable {
                                                    when {
                                                        slot.packageName == "ACTION_DIAL" -> app.installedAppsRepository.launchDialer()
                                                        !slot.packageName.isNullOrBlank() -> launchIntoRight30Split(slot.packageName)
                                                        slot.id == "youtube" -> {
                                                            app.installedAppsRepository.findBestYoutubePackage()?.let { launchIntoRight30Split(it) }
                                                        }
                                                        else -> openMainLauncherWithAction(MainActivity.EXTRA_OPEN_DRAWER)
                                                    }
                                                }
                                        ) {
                                            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize().padding(5.dp)) {
                                                if (iconBmp != null) {
                                                    Image(
                                                        bitmap = iconBmp.asImageBitmap(),
                                                        contentDescription = slot.label,
                                                        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp))
                                                    )
                                                } else {
                                                    Icon(
                                                        imageVector = slot.fallbackIcon,
                                                        contentDescription = slot.label,
                                                        tint = slot.fallbackTint,
                                                        modifier = Modifier.size(22.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                // Quick Settings / Day-Night / Home
                                Surface(
                                    color = colors.cardSurfaceElevated,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .size(40.dp)
                                        .bouncyClickable {
                                            val next = if (isNight) ThemeMode.FORCE_DAY else ThemeMode.FORCE_NIGHT
                                            app.preferences.update { it.copy(themeMode = next) }
                                        }
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = if (isNight) Icons.Rounded.DarkMode else Icons.Rounded.LightMode,
                                            contentDescription = "日夜切換",
                                            tint = colors.primaryText,
                                            modifier = Modifier.size(19.dp)
                                        )
                                    }
                                }

                                Surface(
                                    color = colors.cardSurfaceElevated,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .size(40.dp)
                                        .bouncyClickable { openMainLauncherWithAction(MainActivity.EXTRA_OPEN_SETTINGS) }
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(Icons.Rounded.Settings, contentDescription = "設定", tint = colors.primaryText, modifier = Modifier.size(19.dp))
                                    }
                                }
                            }
                        }
                    }

                    // Floating Call / Message / Shared Location Banners inside split panel
                    val otherNavApps = remember { SharedLocationResolver.findInstalledOtherNavApps(this@SplitSidePanelActivity) }
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.align(Alignment.TopCenter)
                    ) {
                        IncomingCallBanner(
                            callState = activeCall,
                            scale = config.notificationBannerScale,
                            scaleFactorOverride = config.notificationBannerScaleFloat,
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
                            onQuickReply = { item, reply -> app.notificationCenter.sendQuickReply(item, reply) },
                            onMarkRead = { app.notificationCenter.markMessageRead(it) },
                            onDismiss = { app.notificationCenter.dismissPopupMessage() }
                        )
                        SharedLocationBanner(
                            locationItem = sharedLocation,
                            scale = config.notificationBannerScale,
                            scaleFactorOverride = config.notificationBannerScaleFloat,
                            otherNavApps = otherNavApps,
                            onOpenBuiltInMap = { loc ->
                                app.notificationCenter.dismissSharedLocation()
                                val curLoc = app.locationTracker.location.value
                                val sLat = if (curLoc.latitude != 0.0) curLoc.latitude else config.lastKnownLat
                                val sLon = if (curLoc.longitude != 0.0) curLoc.longitude else config.lastKnownLon
                                app.builtInNavEngine.navigateByQuery(
                                    query = loc.address.ifBlank { loc.label },
                                    startLat = sLat,
                                    startLon = sLon,
                                    explicitDestLat = loc.latitude.takeIf { it != 0.0 },
                                    explicitDestLon = loc.longitude.takeIf { it != 0.0 }
                                )
                                openMainLauncherWithAction(MainActivity.EXTRA_GO_DASHBOARD)
                            },
                            onOpenGoogleMaps = { loc ->
                                app.notificationCenter.dismissSharedLocation()
                                SharedLocationResolver.openInGoogleMaps(
                                    this@SplitSidePanelActivity,
                                    loc.label,
                                    loc.address,
                                    loc.latitude,
                                    loc.longitude
                                )
                            },
                            onOpenOtherMap = { loc, pkg ->
                                app.notificationCenter.dismissSharedLocation()
                                SharedLocationResolver.openInOtherMapChooser(
                                    this@SplitSidePanelActivity,
                                    pkg,
                                    loc.label,
                                    loc.address,
                                    loc.latitude,
                                    loc.longitude
                                )
                            },
                            onDismiss = { app.notificationCenter.dismissSharedLocation() }
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra(EXTRA_PLAN_B_MODE, false)) {
            isPlanBSplitHost.value = true
        }
    }

    override fun onResume() {
        super.onResume()
        hideSystemBars()
    }

    override fun onMultiWindowModeChanged(isInMultiWindowMode: Boolean, newConfig: Configuration) {
        super.onMultiWindowModeChanged(isInMultiWindowMode, newConfig)
        isSplitMode.value = isInMultiWindowMode
    }

    private fun hideSystemBars() {
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
    }

    companion object {
        const val EXTRA_AUTO_LAUNCH_MAPS = "extra_auto_launch_maps"
        const val EXTRA_NAV_PACKAGE = "extra_nav_package"
        const val EXTRA_PLAN_B_MODE = "extra_plan_b_mode"

        fun launchWithRealGoogleMaps(context: Context, navPackage: String? = null) {
            val intent = Intent(context, SplitSidePanelActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra(EXTRA_AUTO_LAUNCH_MAPS, true)
                putExtra(EXTRA_PLAN_B_MODE, false)
                if (!navPackage.isNullOrBlank()) {
                    putExtra(EXTRA_NAV_PACKAGE, navPackage)
                }
            }
            runCatching { context.startActivity(intent) }
        }
    }
}

package com.dxcar.launcher

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BluetoothConnected
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.Phone
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dxcar.launcher.accessibility.DxCarAccessibilityService
import com.dxcar.launcher.comms.CallActionCommand
import com.dxcar.launcher.data.HomeExperimentMode
import com.dxcar.launcher.data.ThemeMode
import com.dxcar.launcher.system.PermissionHelper
import com.dxcar.launcher.theme.DxCarTheme
import com.dxcar.launcher.theme.DayNightEngine
import com.dxcar.launcher.theme.LocalDxCarColors
import com.dxcar.launcher.ui.IncomingCallBanner
import com.dxcar.launcher.ui.IncomingMessageBanner
import com.dxcar.launcher.ui.MediaControlCard
import com.dxcar.launcher.ui.bouncyClickable
import kotlinx.coroutines.delay

/**
 * Dedicated resizable non-Home Activity built specifically to run side-by-side with
 * REAL native Google Maps (`com.google.android.apps.maps`) in Android Native Split-Screen!
 *
 * Why a separate Activity?
 * Android WindowManager blocks `CATEGORY_HOME` tasks from entering native Split-Screen on many
 * Android 10-14 AI Box ROMs. By hosting DX-Car Launcher's AA Media + Quick Status panel in this
 * dedicated resizable Activity, Android lets real Google Maps run on one side and DX-Car Launcher's
 * AA Media/Comms side-panel on the other!
 */
class SplitSidePanelActivity : ComponentActivity() {

    private val isSplitMode = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        isSplitMode.value = isInMultiWindowMode

        val autoSplit = intent.getBooleanExtra(EXTRA_AUTO_LAUNCH_MAPS, true)
        val navPkg = intent.getStringExtra(EXTRA_NAV_PACKAGE)?.takeIf { it.isNotBlank() }
        val app = application as DxCarApp

        setContent {
            val config by app.preferences.config.collectAsState()
            val sysStatus by app.systemStatusMonitor.status.collectAsState()
            val media by app.mediaControlBridge.mediaState.collectAsState()
            val activeCall by app.notificationCenter.activeCall.collectAsState()
            val popupMessage by app.notificationCenter.popupMessage.collectAsState()
            val messages by app.notificationCenter.messages.collectAsState()
            val accConnected by DxCarAccessibilityService.isConnected.collectAsState()
            val inSplit by isSplitMode
            val targetNavPkg = navPkg ?: config.planCNavPackage.ifBlank { "com.google.android.apps.maps" }

            var splitTriggered by remember { mutableStateOf(false) }

            // Automatically enter AOSP Draggable Split-Screen (Nav on Left, Media Side-Panel on Right!)
            LaunchedEffect(autoSplit, accConnected, targetNavPkg) {
                if (autoSplit && !splitTriggered && !isInMultiWindowMode) {
                    splitTriggered = true
                    delay(320L)
                    val acc = DxCarAccessibilityService.instance
                    if (acc != null) {
                        // Put Navigation on Left Primary Stage and SplitSidePanelActivity on Right Secondary Stage!
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

            val systemIsDark =
                (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
            val isNight = DayNightEngine.isNightNow(config, sysStatus.now, systemIsDark)
            val unreadCount = remember(messages) { messages.count { !it.isRead } }

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
                        .padding(10.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Top Compact Action Header
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                color = colors.cardSurface,
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .border(1.dp, colors.cardBorder, RoundedCornerShape(16.dp))
                                    .bouncyClickable {
                                        val acc = DxCarAccessibilityService.instance
                                        if (!isInMultiWindowMode && acc != null) {
                                            acc.toggleSplitScreen()
                                        }
                                        app.installedAppsRepository.launchPackageAdjacent(targetNavPkg)
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(Icons.Rounded.Map, contentDescription = null, tint = colors.accent, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = if (inSplit) "重開導航 App" else "啟動分割導航",
                                        color = colors.primaryText,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1
                                    )
                                }
                            }

                            Surface(
                                color = colors.accent,
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.bouncyClickable {
                                    startActivity(
                                        Intent(this@SplitSidePanelActivity, MainActivity::class.java)
                                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                                    )
                                    finish()
                                }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Rounded.Home, contentDescription = null, tint = colors.onAccent, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(5.dp))
                                    Text("回首頁", color = colors.onAccent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        // Hint banner if Accessibility isn't enabled yet & not in split-screen
                        if (!inSplit && !accConnected) {
                            Surface(
                                color = colors.cardSurfaceElevated,
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.dp, colors.accent.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
                                    .bouncyClickable {
                                        PermissionHelper.openAccessibilitySettings(this@SplitSidePanelActivity)
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Rounded.Splitscreen, contentDescription = null, tint = colors.accent)
                                    Spacer(Modifier.width(10.dp))
                                    Text(
                                        text = "點此開啟「DX-Car Launcher 無障礙服務」即可一鍵自動與真實 Google 地圖分割；也可直接用車機原生分割鈕配對 Google 地圖！",
                                        color = colors.primaryText,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }

                        // Full AA Media Control Card
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .clip(cardShape)
                                .border(colors.cardBorderWidthDp.dp, colors.cardBorder, cardShape)
                        ) {
                            MediaControlCard(
                                media = media,
                                isPrimary = true,
                                pureMusicMode = config.homeExperimentMode == HomeExperimentMode.PLAN_C_APP_AND_MEDIA,
                                showSwapButton = false,
                                onPlayPause = { app.mediaControlBridge.togglePlayPause() },
                                onNext = { app.mediaControlBridge.skipNext() },
                                onPrevious = { app.mediaControlBridge.skipPrevious() },
                                onToggleFavorite = { app.mediaControlBridge.toggleFavoriteOrLike() },
                                onSelectSession = { app.mediaControlBridge.selectSession(it) },
                                onOpenMediaApp = {
                                    val pkg = media.packageName
                                    if (pkg.isNotBlank()) app.installedAppsRepository.launchAppFullscreen(pkg)
                                },
                                onOpenYoutubeFullscreen = {
                                    val yt = app.installedAppsRepository.findBestYoutubePackage()
                                    if (yt != null) app.installedAppsRepository.launchAppFullscreen(yt)
                                },
                                onSwapRequested = {
                                    app.installedAppsRepository.launchPackageAdjacent(targetNavPkg)
                                }
                            )
                        }

                        // Compact Bottom Status Card
                        Surface(
                            color = colors.cardSurface,
                            shape = cardShape,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(72.dp)
                                .border(colors.cardBorderWidthDp.dp, colors.cardBorder, cardShape)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .weight(1f)
                                        .bouncyClickable { app.installedAppsRepository.launchDialer() }
                                ) {
                                    Icon(
                                        imageVector = if (sysStatus.bluetoothConnected) Icons.Rounded.BluetoothConnected else Icons.Rounded.Phone,
                                        contentDescription = null,
                                        tint = if (sysStatus.bluetoothConnected) colors.accent else colors.secondaryText,
                                        modifier = Modifier.size(22.dp)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            text = sysStatus.connectedBtName.ifBlank { "藍牙電話" },
                                            color = colors.primaryText,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "${sysStatus.time24h} · ${if (unreadCount > 0) "$unreadCount 則未讀" else "真實地圖分割中"}",
                                            color = colors.secondaryText,
                                            fontSize = 11.sp
                                        )
                                    }
                                }

                                Surface(
                                    color = colors.cardSurfaceElevated,
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier
                                        .size(42.dp)
                                        .bouncyClickable {
                                            val next = if (isNight) ThemeMode.FORCE_DAY else ThemeMode.FORCE_NIGHT
                                            app.preferences.update { it.copy(themeMode = next) }
                                        }
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = if (isNight) Icons.Rounded.DarkMode else Icons.Rounded.LightMode,
                                            contentDescription = "切換日夜",
                                            tint = colors.primaryText,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Floating Call / Message Banners inside split panel
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
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
    }

    override fun onMultiWindowModeChanged(isInMultiWindowMode: Boolean, newConfig: Configuration) {
        super.onMultiWindowModeChanged(isInMultiWindowMode, newConfig)
        isSplitMode.value = isInMultiWindowMode
    }

    companion object {
        private const val EXTRA_AUTO_LAUNCH_MAPS = "extra_auto_launch_maps"
        private const val EXTRA_NAV_PACKAGE = "extra_nav_package"

        fun launchWithRealGoogleMaps(context: Context, navPackage: String? = null) {
            val intent = Intent(context, SplitSidePanelActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra(EXTRA_AUTO_LAUNCH_MAPS, true)
                if (!navPackage.isNullOrBlank()) {
                    putExtra(EXTRA_NAV_PACKAGE, navPackage)
                }
            }
            runCatching { context.startActivity(intent) }
        }
    }
}

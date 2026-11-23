package com.dxcar.launcher.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.DisplaySettings
import androidx.compose.material.icons.rounded.Gesture
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Splitscreen
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dxcar.launcher.BuildConfig
import com.dxcar.launcher.comms.BridgeStatusState
import com.dxcar.launcher.data.DockPositionMode
import com.dxcar.launcher.data.HomeExperimentMode
import com.dxcar.launcher.data.HomeMapDisplayMode
import com.dxcar.launcher.data.LauncherConfig
import com.dxcar.launcher.data.LauncherStylePreset
import com.dxcar.launcher.data.MapEngineStyle
import com.dxcar.launcher.data.ThemeMode
import com.dxcar.launcher.layout.CoolwalkLayoutSpec
import com.dxcar.launcher.system.AppCategory
import com.dxcar.launcher.system.HomeLauncherInfo
import com.dxcar.launcher.system.InstalledAppInfo
import com.dxcar.launcher.system.PermissionHealth
import com.dxcar.launcher.system.PermissionHelper
import com.dxcar.launcher.system.SystemStatusMonitor
import com.dxcar.launcher.theme.DxCarAccents
import com.dxcar.launcher.theme.DayNightEngine
import com.dxcar.launcher.theme.LocalDxCarColors
import com.dxcar.launcher.update.UpdateManager
import com.dxcar.launcher.update.UpdateUiState
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Full DX-Car Launcher Settings Screen (accessible right from the App Drawer):
 * 1. 風格與外觀（Coolwalk 風格預設、白天/夜晚依時間/日出日落自動切換、強調色、地圖引擎、分割比例）
 * 2. 車機 DPI 與常駐 Dock（Google Coolwalk 自動模式、DPI 診斷資訊、常駐懸浮開關）
 * 3. 多指手勢操作設定與手勢一覽
 * 4. 來電與訊息同步（藍牙橋接狀態、TTS 語音播報、模擬測試按鈕、切換手機同步端）
 * 5. AI Box 權限一鍵健檢
 * 6. 自動檢測與自動下載 App 更新 + GitHub 開源儲存庫連結
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    config: LauncherConfig,
    layoutSpec: CoolwalkLayoutSpec,
    installedApps: List<InstalledAppInfo>,
    homeLaunchers: List<HomeLauncherInfo>,
    permissionHealth: PermissionHealth,
    bridgeStatus: BridgeStatusState,
    updateState: UpdateUiState,
    onUpdateConfig: ((LauncherConfig) -> LauncherConfig) -> Unit,
    onRequestSetDefaultHome: () -> Unit,
    onSwitchToLauncher: (HomeLauncherInfo) -> Unit,
    onRefreshHomeLaunchers: () -> Unit,
    onLaunchRealMapsSplitScreen: () -> Unit,
    onLaunchRealMapsFreeform: () -> Unit,
    onOpenSplitShortcutCreator: () -> Unit,
    onResetSettingsToDefaults: () -> Unit,
    onRefreshPermissions: () -> Unit,
    onRequestRuntimePermissions: () -> Unit,
    onSimulateCall: () -> Unit,
    onSimulateMessage: () -> Unit,
    onCheckUpdateNow: () -> Unit,
    onDownloadOrInstallUpdate: () -> Unit,
    onSwitchToPhoneCompanion: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalDxCarColors.current
    val context = LocalContext.current
    var confirmResetPending by remember { mutableStateOf(false) }

    val solarPreview = remember(config.lastKnownLat, config.lastKnownLon) {
        val zone = ZoneId.systemDefault()
        val now = java.time.LocalDateTime.now()
        DayNightEngine.calculateSolarTimes(
            date = LocalDate.now(),
            latitude = config.lastKnownLat,
            longitude = config.lastKnownLon,
            tzOffsetHours = zone.rules.getOffset(now).totalSeconds / 3600.0
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.canvasBackground)
            .padding(horizontal = 20.dp, vertical = 14.dp)
    ) {
        // TOP BAR (weight(1f) keeps Switch button always visible even on narrow phone screens!)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f).padding(end = 8.dp)
            ) {
                IconButton(onClick = onClose) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "返回",
                        tint = colors.primaryText
                    )
                }
                Spacer(Modifier.width(4.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "DX-Car Launcher 設定",
                        color = colors.primaryText,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "實驗切換 · 啟動器管理 · 風格日夜 · Dock 與更新",
                        color = colors.secondaryText,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            OutlinedButton(
                onClick = onSwitchToPhoneCompanion,
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Rounded.PhoneAndroid, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("切換手機同步端", maxLines = 1)
            }
        }

        Spacer(Modifier.height(12.dp))

        Column(
            modifier = Modifier
                .fillMaxSize()
                .springBounceOverscroll(maxOverscrollPx = 150f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // TOP SECTION: 首頁實驗方案切換開關（方案 A / B / C / D 保留）
            SettingsSectionCard(
                icon = Icons.Rounded.Science,
                title = "首頁方案實驗切換（方案 A / B / C / D）"
            ) {
                Text(
                    text = "可一鍵切換並實車比較不同首頁架構，預設分割比例為 70% / 30%。",
                    color = colors.secondaryText,
                    fontSize = 12.sp
                )
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    HomeExperimentMode.entries.forEach { mode ->
                        val selected = config.homeExperimentMode == mode
                        Surface(
                            color = when {
                                !mode.isAvailable -> colors.cardSurfaceElevated.copy(alpha = 0.5f)
                                selected -> colors.accent.copy(alpha = 0.18f)
                                else -> colors.cardSurfaceElevated
                            },
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(
                                    width = if (selected) 1.5.dp else 1.dp,
                                    color = if (selected) colors.accent else colors.cardBorder,
                                    shape = RoundedCornerShape(16.dp)
                                )
                                .clickable(enabled = mode.isAvailable) {
                                    onUpdateConfig { it.copy(homeExperimentMode = mode) }
                                }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = mode.titleZh,
                                            color = if (mode.isAvailable) colors.primaryText else colors.secondaryText,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        if (!mode.isAvailable) {
                                            Surface(
                                                color = colors.cardBorder.copy(alpha = 0.6f),
                                                shape = RoundedCornerShape(8.dp)
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Icon(Icons.Rounded.Lock, contentDescription = null, tint = colors.secondaryText, modifier = Modifier.size(11.dp))
                                                    Spacer(Modifier.width(3.dp))
                                                    Text("待 Root 保留", color = colors.secondaryText, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                                                }
                                            }
                                        }
                                    }
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        text = mode.descZh,
                                        color = colors.secondaryText,
                                        fontSize = 11.sp
                                    )
                                }
                                if (mode.isAvailable) {
                                    Spacer(Modifier.width(10.dp))
                                    Switch(
                                        checked = selected,
                                        onCheckedChange = { onUpdateConfig { it.copy(homeExperimentMode = mode) } },
                                        colors = SwitchDefaults.colors(
                                            checkedThumbColor = colors.onAccent,
                                            checkedTrackColor = colors.accent
                                        )
                                    )
                                }
                            }
                        }
                    }
                }

                // 方案 C 專屬：左側預設導航 App 選擇（顯示 App 圖示＋名稱）
                if (config.homeExperimentMode == HomeExperimentMode.PLAN_C_APP_AND_MEDIA) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "方案 C 左側預設導航 App（顯示圖示與名稱，點擊選定）",
                        color = colors.primaryText,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    val orderedNavApps = remember(installedApps) {
                        val navFirst = installedApps.filter { it.category == AppCategory.NAVIGATION }
                        val others = installedApps.filterNot { it.category == AppCategory.NAVIGATION }
                        navFirst + others.take(12)
                    }
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        orderedNavApps.forEach { appInfo ->
                            val isSelected = config.planCNavPackage == appInfo.packageName
                            val bmp = remember(appInfo.icon) { appInfo.icon.asImageBitmap() }
                            Surface(
                                color = if (isSelected) colors.accent else colors.cardSurfaceElevated,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .border(
                                        width = 1.dp,
                                        color = if (isSelected) colors.accent else colors.cardBorder,
                                        shape = RoundedCornerShape(14.dp)
                                    )
                                    .clickable {
                                        onUpdateConfig { it.copy(planCNavPackage = appInfo.packageName) }
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Image(
                                        bitmap = bmp,
                                        contentDescription = appInfo.label,
                                        modifier = Modifier
                                            .size(24.dp)
                                            .clip(RoundedCornerShape(6.dp))
                                    )
                                    Spacer(Modifier.width(7.dp))
                                    Text(
                                        text = appInfo.label,
                                        color = if (isSelected) colors.onAccent else colors.primaryText,
                                        fontSize = 12.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }

                if (config.homeExperimentMode == HomeExperimentMode.PLAN_C_APP_AND_MEDIA) {
                    SettingsToggleRow(
                        title = "方案 C 開啟左側導航時於右側 30% 顯示懸浮純音樂控盤",
                        subtitle = "確保在未開放系統原生分割的車機上，也能一邊看真實導航 App 一邊操作大封面音樂控制（可隨時縮小或回首頁）",
                        checked = config.planCOverlayMusicPanelEnabled,
                        onCheckedChange = { checked -> onUpdateConfig { it.copy(planCOverlayMusicPanelEnabled = checked) } }
                    )
                }

                SettingsToggleRow(
                    title = "媒體控制器下方整合顯示 6 格快捷 App",
                    subtitle = "開啟後於右側媒體控制器底部呈現 6 格自訂捷徑；關閉則將右側全部空間留給大專輯封面與純音樂操控",
                    checked = config.showSixShortcutsInMediaPanel,
                    onCheckedChange = { checked -> onUpdateConfig { it.copy(showSixShortcutsInMediaPanel = checked) } }
                )
            }

            // SECTION 0: 啟動器切換與設為系統預設主畫面（支援切換原廠 AI Box 啟動器／其他啟動器＋選成預設值）
            SettingsSectionCard(
                icon = Icons.Rounded.Home,
                title = "啟動器切換與預設主畫面設定"
            ) {
                val currentDefaultLauncher = homeLaunchers.firstOrNull { it.isDefault }
                Surface(
                    color = colors.cardSurfaceElevated,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = if (permissionHealth.isDefaultHome) Icons.Rounded.CheckCircle else Icons.Rounded.WarningAmber,
                                contentDescription = null,
                                tint = if (permissionHealth.isDefaultHome) colors.statusConnected else Color(0xFFF59E0B),
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = if (permissionHealth.isDefaultHome) {
                                        "目前預設啟動器：DX-Car Launcher（已設為預設）"
                                    } else {
                                        "目前預設啟動器：${currentDefaultLauncher?.label ?: "系統預設啟動器（非 DX-Car Launcher）"}"
                                    },
                                    color = colors.primaryText,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "可一鍵將 DX-Car Launcher 設為預設啟動器，或切換至車機原廠／其他已安裝的啟動器並變更系統預設值",
                                    color = colors.secondaryText,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = onRequestSetDefaultHome,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.accent,
                            contentColor = colors.onAccent
                        ),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Rounded.Home, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (permissionHealth.isDefaultHome) "重新選擇預設啟動器" else "設 DX-Car Launcher 為系統預設")
                    }

                    OutlinedButton(
                        onClick = { SystemStatusMonitor.openHomeLauncherSettings(context) },
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Rounded.SwapHoriz, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("變更系統預設啟動器（選擇其他預設）")
                    }

                    OutlinedButton(
                        onClick = onRefreshHomeLaunchers,
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("重新掃描")
                    }
                }

                if (homeLaunchers.isNotEmpty()) {
                    Text(
                        text = "已安裝的車機／系統啟動器（點擊即可立即切換）",
                        color = colors.primaryText,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        homeLaunchers.forEach { launcher ->
                            val iconBmp = remember(launcher.icon) { launcher.icon.asImageBitmap() }
                            Surface(
                                color = colors.cardSurfaceElevated,
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(
                                        width = if (launcher.isDefault) 1.5.dp else 1.dp,
                                        color = if (launcher.isDefault) colors.accent else colors.cardBorder,
                                        shape = RoundedCornerShape(16.dp)
                                    )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Image(
                                            bitmap = iconBmp,
                                            contentDescription = launcher.label,
                                            modifier = Modifier
                                                .size(40.dp)
                                                .clip(RoundedCornerShape(10.dp))
                                        )
                                        Spacer(Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Text(
                                                    text = launcher.label,
                                                    color = colors.primaryText,
                                                    fontSize = 14.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                if (launcher.isDefault) {
                                                    Surface(
                                                        color = colors.statusConnected.copy(alpha = 0.2f),
                                                        shape = RoundedCornerShape(8.dp)
                                                    ) {
                                                        Text(
                                                            text = "系統預設",
                                                            color = colors.statusConnected,
                                                            fontSize = 10.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                        )
                                                    }
                                                }
                                                if (launcher.isSelf) {
                                                    Surface(
                                                        color = colors.accent.copy(alpha = 0.2f),
                                                        shape = RoundedCornerShape(8.dp)
                                                    ) {
                                                        Text(
                                                            text = "目前使用",
                                                            color = colors.accent,
                                                            fontSize = 10.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                        )
                                                    }
                                                }
                                            }
                                            Text(
                                                text = launcher.packageName,
                                                color = colors.secondaryText,
                                                fontSize = 11.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }

                                    Spacer(Modifier.width(8.dp))

                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        if (!launcher.isSelf) {
                                            Button(
                                                onClick = { onSwitchToLauncher(launcher) },
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = colors.accent,
                                                    contentColor = colors.onAccent
                                                ),
                                                shape = RoundedCornerShape(12.dp)
                                            ) {
                                                Text("立即切換", fontSize = 12.sp)
                                            }
                                        }
                                        if (!launcher.isDefault) {
                                            OutlinedButton(
                                                onClick = {
                                                    if (launcher.isSelf) onRequestSetDefaultHome()
                                                    else SystemStatusMonitor.openHomeLauncherSettings(context)
                                                },
                                                shape = RoundedCornerShape(12.dp)
                                            ) {
                                                Text("選成預設", fontSize = 12.sp)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // SECTION 1: 風格與日夜模式（Style Presets + Day/Night Schedule + Accent + Map + Split Ratio）
            SettingsSectionCard(
                icon = Icons.Rounded.Palette,
                title = "介面風格、白天／夜晚與地圖外觀"
            ) {
                Text("介面風格預設", color = colors.primaryText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    LauncherStylePreset.entries.forEach { preset ->
                        val selected = config.stylePreset == preset
                        Surface(
                            color = if (selected) colors.accent else colors.cardSurfaceElevated,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .border(
                                    1.2.dp,
                                    if (selected) colors.accent else colors.cardBorder,
                                    RoundedCornerShape(16.dp)
                                )
                                .clickable { onUpdateConfig { it.copy(stylePreset = preset) } }
                        ) {
                            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                                Text(
                                    text = preset.labelZh,
                                    color = if (selected) colors.onAccent else colors.primaryText,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = preset.descZh,
                                    color = if (selected) colors.onAccent.copy(alpha = 0.85f) else colors.secondaryText,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(6.dp))
                Text("強調色主題", color = colors.primaryText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    DxCarAccents.forEachIndexed { idx, accent ->
                        val selected = config.accentColorIndex == idx
                        val swatch = if (colors.isNight) accent.nightColor else accent.dayColor
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.clickable { onUpdateConfig { it.copy(accentColorIndex = idx) } }
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(swatch)
                                    .border(
                                        width = if (selected) 3.dp else 1.dp,
                                        color = if (selected) colors.primaryText else colors.cardBorder,
                                        shape = CircleShape
                                    )
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = accent.nameZh,
                                color = if (selected) colors.primaryText else colors.secondaryText,
                                fontSize = 11.sp
                            )
                        }
                    }
                }

                Spacer(Modifier.height(6.dp))
                Text("白天／夜晚切換模式", color = colors.primaryText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val modes = listOf(
                        ThemeMode.AUTO_TIME to "依自訂時間自動",
                        ThemeMode.AUTO_SUNRISE_SUNSET to "依 GPS 日出日落",
                        ThemeMode.FOLLOW_SYSTEM to "跟隨車機系統",
                        ThemeMode.FORCE_DAY to "固定白天",
                        ThemeMode.FORCE_NIGHT to "固定夜晚"
                    )
                    modes.forEach { (mode, label) ->
                        SelectablePill(
                            text = label,
                            selected = config.themeMode == mode,
                            onClick = { onUpdateConfig { it.copy(themeMode = mode) } }
                        )
                    }
                }

                if (config.themeMode == ThemeMode.AUTO_TIME) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        TimeStepperCard(
                            label = "白天開始時間",
                            hour = config.dayStartHour,
                            minute = config.dayStartMinute,
                            onDeltaMinutes = { delta ->
                                val total = ((config.dayStartHour * 60 + config.dayStartMinute + delta) % 1440 + 1440) % 1440
                                onUpdateConfig { it.copy(dayStartHour = total / 60, dayStartMinute = total % 60) }
                            },
                            modifier = Modifier.weight(1f)
                        )
                        TimeStepperCard(
                            label = "夜晚開始時間",
                            hour = config.nightStartHour,
                            minute = config.nightStartMinute,
                            onDeltaMinutes = { delta ->
                                val total = ((config.nightStartHour * 60 + config.nightStartMinute + delta) % 1440 + 1440) % 1440
                                onUpdateConfig { it.copy(nightStartHour = total / 60, nightStartMinute = total % 60) }
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                } else if (config.themeMode == ThemeMode.AUTO_SUNRISE_SUNSET) {
                    Text(
                        text = "依據車機 GPS 座標計算今日日出 ${solarPreview.formatSunrise()}、日落 ${solarPreview.formatSunset()}（離線天文計算，免 API Key）",
                        color = colors.accent,
                        fontSize = 12.sp
                    )
                }

                Spacer(Modifier.height(6.dp))
                Text("首頁地圖模式（預設 70% / 30% 分割比例）", color = colors.primaryText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    HomeMapDisplayMode.entries.forEach { mode ->
                        SelectablePill(
                            text = mode.labelZh,
                            selected = config.homeMapDisplayMode == mode,
                            onClick = { onUpdateConfig { it.copy(homeMapDisplayMode = mode) } }
                        )
                    }
                }
                Text(
                    text = config.homeMapDisplayMode.descZh,
                    color = colors.secondaryText,
                    fontSize = 11.sp
                )

                Text("內建免 Key 地圖圖層風格", color = colors.primaryText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    MapEngineStyle.entries.forEach { styleItem ->
                        SelectablePill(
                            text = styleItem.labelZh,
                            selected = config.mapEngineStyle == styleItem,
                            onClick = { onUpdateConfig { it.copy(mapEngineStyle = styleItem) } }
                        )
                    }
                }

                SettingsToggleRow(
                    title = "疊加 Google 即時路況圖層",
                    subtitle = "於 Google 標準繁中與 Google 衛星混合底圖顯示即時紅黃綠車流",
                    checked = config.mapTrafficEnabled,
                    onCheckedChange = { checked -> onUpdateConfig { it.copy(mapTrafficEnabled = checked) } }
                )

                Surface(
                    color = colors.cardSurfaceElevated,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "真實 Google 地圖本體分割與多比例分屏捷徑",
                            color = colors.primaryText,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "原廠 AI Box 系統內建啟動器因具系統簽章可直接呼叫私有 ActivityView；一般安裝版啟動器可透過「真實 G 地圖貼合（70%）」或「原生分割／分屏設定」直接使用真正的 Google 地圖本體！",
                            color = colors.secondaryText,
                            fontSize = 11.sp
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = onLaunchRealMapsSplitScreen,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = colors.accent,
                                    contentColor = colors.onAccent
                                ),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Icon(Icons.Rounded.Splitscreen, contentDescription = null, modifier = Modifier.size(17.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("原生分割：真實 G 地圖 ＋ 媒體")
                            }
                            OutlinedButton(
                                onClick = onLaunchRealMapsFreeform,
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Text("立即以 Freeform 貼合 70%")
                            }
                            OutlinedButton(
                                onClick = onOpenSplitShortcutCreator,
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Text("開啟分屏設定 (3:7~7:3)")
                            }
                        }
                    }
                }

                // Split Proportion Slider
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "主／副分割比例：${(config.primarySplitRatio * 100).roundToInt()}% / ${((1f - config.primarySplitRatio) * 100).roundToInt()}%",
                        color = colors.primaryText,
                        fontSize = 13.sp,
                        modifier = Modifier.width(195.dp)
                    )
                    Slider(
                        value = config.primarySplitRatio,
                        onValueChange = { ratio -> onUpdateConfig { it.copy(primarySplitRatio = ratio) } },
                        valueRange = 0.55f..0.75f,
                        colors = SliderDefaults.colors(thumbColor = colors.accent, activeTrackColor = colors.accent),
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // SECTION 2: 駕駛座左右駕、車機 DPI 與常駐 Dock（Google 模式）
            SettingsSectionCard(
                icon = Icons.Rounded.DisplaySettings,
                title = "左右駕模式、車機 DPI 偵測與常駐 Dock"
            ) {
                Text("駕駛座位置（自動鏡像 Dock 靠駕駛側與首頁分割方向）", color = colors.primaryText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SelectablePill(
                        text = "左駕模式（預設 · 台灣／歐美）",
                        selected = !config.rightHandDrive,
                        onClick = { onUpdateConfig { it.copy(rightHandDrive = false) } }
                    )
                    SelectablePill(
                        text = "右駕模式（日本／香港／英國）",
                        selected = config.rightHandDrive,
                        onClick = { onUpdateConfig { it.copy(rightHandDrive = true) } }
                    )
                }

                Surface(
                    color = colors.cardSurfaceElevated,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(
                            text = "目前車機螢幕：${layoutSpec.metrics.widthPx} × ${layoutSpec.metrics.heightPx} px · ${layoutSpec.metrics.densityDpi} DPI",
                            color = colors.primaryText,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "換算工作區：${layoutSpec.metrics.widthDp.roundToInt()} × ${layoutSpec.metrics.heightDp.roundToInt()} dp · ${layoutSpec.widthCategory.labelZh}",
                            color = colors.secondaryText,
                            fontSize = 12.sp
                        )
                        Text(
                            text = "判定結果：${layoutSpec.dockPlacement.labelZh}（${layoutSpec.reasonZh}）",
                            color = colors.accent,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val dockModes = listOf(
                        DockPositionMode.AUTO_GOOGLE_DPI to "依 DPI 自動（Google 模式）",
                        DockPositionMode.FORCE_LEFT to "強制左側直立",
                        DockPositionMode.FORCE_BOTTOM to "強制底部橫向",
                        DockPositionMode.FORCE_RIGHT to "強制右側（右駕）"
                    )
                    dockModes.forEach { (mode, label) ->
                        SelectablePill(
                            text = label,
                            selected = config.dockPositionMode == mode,
                            onClick = { onUpdateConfig { it.copy(dockPositionMode = mode) } }
                        )
                    }
                }

                SettingsToggleRow(
                    title = "常駐懸浮 Dock（在其他全螢幕 App 上方常駐）",
                    subtitle = "開啟後在 YouTube／Google 地圖／導航王全螢幕時保留常駐列；若車機盒本身已有原廠左側列可關閉",
                    checked = config.persistentOverlayDockEnabled,
                    onCheckedChange = { checked -> onUpdateConfig { it.copy(persistentOverlayDockEnabled = checked) } }
                )

                if (config.persistentOverlayDockEnabled) {
                    SettingsToggleRow(
                        title = "全螢幕 App 自動收合為邊緣拉條（推薦）",
                        subtitle = "進入 Google 地圖、YouTube 或導航王時自動縮成透明邊緣拉條，從邊緣一滑或輕點即展開，長按可沿邊緣拖曳挪動位置",
                        checked = config.overlayAutoCollapseEnabled,
                        onCheckedChange = { checked -> onUpdateConfig { it.copy(overlayAutoCollapseEnabled = checked) } }
                    )

                    if (config.overlayAutoCollapseEnabled) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("展開後自動收回：", color = colors.secondaryText, fontSize = 12.sp)
                            listOf(3 to "3 秒", 4 to "4 秒", 7 to "7 秒", 0 to "手動收回").forEach { (sec, label) ->
                                SelectablePill(
                                    text = label,
                                    selected = config.overlayAutoRetractSeconds == sec,
                                    onClick = { onUpdateConfig { it.copy(overlayAutoRetractSeconds = sec) } }
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "收起拉條厚度：${config.overlayHandleThicknessDp} dp",
                                color = colors.primaryText,
                                fontSize = 13.sp,
                                modifier = Modifier.width(175.dp)
                            )
                            Slider(
                                value = config.overlayHandleThicknessDp.toFloat(),
                                onValueChange = { v ->
                                    onUpdateConfig { it.copy(overlayHandleThicknessDp = v.roundToInt().coerceIn(16, 48)) }
                                },
                                valueRange = 16f..48f,
                                colors = SliderDefaults.colors(thumbColor = colors.accent, activeTrackColor = colors.accent),
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "拉條位置偏移：${(config.overlayHandlePositionPercent * 100).roundToInt()}%",
                                color = colors.primaryText,
                                fontSize = 13.sp,
                                modifier = Modifier.width(175.dp)
                            )
                            Slider(
                                value = config.overlayHandlePositionPercent,
                                onValueChange = { v ->
                                    onUpdateConfig { it.copy(overlayHandlePositionPercent = v.coerceIn(0.05f, 0.95f)) }
                                },
                                valueRange = 0.05f..0.95f,
                                colors = SliderDefaults.colors(thumbColor = colors.accent, activeTrackColor = colors.accent),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                SettingsToggleRow(
                    title = "實驗性選配：啟動 App 時以 Freeform 避讓 Dock 區域",
                    subtitle = "嘗試讓開啟的 App 自動往右（或上方）避開 Dock 寬度；註：部分車機可能出現視窗標題列，或 YT／導航王全螢幕時會跳回滿版",
                    checked = config.launchAppsAvoidDockFreeform,
                    onCheckedChange = { checked -> onUpdateConfig { it.copy(launchAppsAvoidDockFreeform = checked) } }
                )

                SettingsToggleRow(
                    title = "首頁顯示 DX-Car Launcher 側邊／底部 Dock",
                    subtitle = "關閉後首頁將隱藏本啟動器 Dock（僅留左下角九宮格按鈕），適合已使用車機原廠左側列時搭配",
                    checked = config.showHomeDock,
                    onCheckedChange = { checked -> onUpdateConfig { it.copy(showHomeDock = checked) } }
                )

                SettingsToggleRow(
                    title = "24 小時制時鐘",
                    subtitle = "關閉則預設顯示 12 小時制時間（含 AM / PM，如 8:20 PM）",
                    checked = config.is24HourClock,
                    onCheckedChange = { checked -> onUpdateConfig { it.copy(is24HourClock = checked) } }
                )

                // 單指車機模擬雙指 3D 俯仰手勢使用教學
                Surface(
                    color = colors.accent.copy(alpha = 0.09f),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, colors.accent.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Text(
                            text = "🧊 單指車機如何切換 Google 地圖 3D 建築（模擬雙指俯仰教學）",
                            color = colors.primaryText,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "1. 先至下方「AI Box 權限健檢」開啟【DX-Car Launcher 無障礙服務】，並確認上方「常駐懸浮 Dock」已開啟。",
                            color = colors.secondaryText,
                            fontSize = 12.sp
                        )
                        Text(
                            text = "2. 開啟原版 Google 地圖後，從螢幕邊緣滑出（或輕點）常駐懸浮 Dock，找到立體方塊【3D 視角】小圖示。",
                            color = colors.secondaryText,
                            fontSize = 12.sp
                        )
                        Text(
                            text = "3. 點一下【3D 視角】即自動收起 Dock 並對地圖中央注入「雙指同時往上滑」，立即傾斜顯示 Google 地圖 3D 建築；長按【3D 視角】則注入「雙指同時往下滑」切回 2D 平面。",
                            color = colors.secondaryText,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            // SECTION 3: 多指操作手勢
            SettingsSectionCard(
                icon = Icons.Rounded.Gesture,
                title = "車載多指操作手勢"
            ) {
                SettingsToggleRow(
                    title = "啟用多指操作手勢",
                    subtitle = "支援首頁與選單全域多指快速操作，不影響單指地圖拖曳",
                    checked = config.multiFingerGesturesEnabled,
                    onCheckedChange = { checked -> onUpdateConfig { it.copy(multiFingerGesturesEnabled = checked) } }
                )
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    GestureLegendRow("雙指左側上下滑", "即時調整車機螢幕亮度")
                    GestureLegendRow("雙指右側上下滑", "即時調整媒體播放音量")
                    GestureLegendRow("雙指左／右滑", "切換下一首／上一首歌曲或影片")
                    GestureLegendRow("三指左／右滑", "首頁左右分割視窗立即互換")
                    GestureLegendRow("三指向下滑", "一鍵返回 AA 分割首頁")
                    GestureLegendRow("三指輕點", "立即切換白天／夜晚模式")
                    GestureLegendRow("四指捏合／展開", "隱藏或顯示 Dock 進入純淨全螢幕")
                }
            }

            // SECTION 4: 來電與訊息同步（藍牙 + 手機同步端）
            SettingsSectionCard(
                icon = Icons.Rounded.NotificationsActive,
                title = "來電與訊息顯示（手機配對藍牙同步）"
            ) {
                Surface(
                    color = colors.cardSurfaceElevated,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (bridgeStatus.transportLabel.isNotBlank()) {
                                    "通知同步狀態：${bridgeStatus.status.labelZh} (${bridgeStatus.transportLabel})"
                                } else {
                                    "通知同步狀態：${bridgeStatus.status.labelZh}"
                                },
                                color = colors.primaryText,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (bridgeStatus.peerName.isNotBlank()) {
                                    "目前連線手機：${bridgeStatus.peerName}"
                                } else {
                                    "支援藍牙 SPP ＋ 同 Wi-Fi 熱點自動直連；於手機安裝同一 APK 即同步 LINE／簡訊／來電"
                                },
                                color = colors.secondaryText,
                                fontSize = 12.sp
                            )
                        }
                        Icon(
                            imageVector = Icons.Rounded.Bluetooth,
                            contentDescription = null,
                            tint = colors.accent,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }

                SettingsToggleRow(
                    title = "全螢幕看 YouTube／導航時顯示頂部來電與訊息懸浮卡片",
                    subtitle = "仿 Android Auto 頂部懸浮橫幅，支援接聽、掛斷、快速回覆",
                    checked = config.globalCallMessageBannerEnabled,
                    onCheckedChange = { checked -> onUpdateConfig { it.copy(globalCallMessageBannerEnabled = checked) } }
                )

                SettingsToggleRow(
                    title = "收到新訊息時自動語音朗讀 (TTS)",
                    subtitle = "自動壓低音樂音量並用繁體中文語音播報寄件人與內容",
                    checked = config.autoReadMessagesTts,
                    onCheckedChange = { checked -> onUpdateConfig { it.copy(autoReadMessagesTts = checked) } }
                )

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = onSimulateCall, shape = RoundedCornerShape(14.dp)) {
                        Text("測試模擬來電卡片")
                    }
                    OutlinedButton(onClick = onSimulateMessage, shape = RoundedCornerShape(14.dp)) {
                        Text("測試模擬 LINE 訊息與朗讀")
                    }
                }

                // 車機端配對與抓通知設定提醒卡片
                Surface(
                    color = colors.accent.copy(alpha = 0.09f),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, colors.accent.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Text(
                            text = "📖 手機與車機「抓通知」快速設定提醒",
                            color = colors.primaryText,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "1. 車機端：下方「AI Box 權限健檢」請開啟【顯示在其他應用程式上層】與【通知存取權】。",
                            color = colors.secondaryText,
                            fontSize = 12.sp
                        )
                        Text(
                            text = "2. 手機端：安裝同一支 APK 開啟會自動進入手機同步端，請開啟【通知讀取權限】（若遇「受限制的設定」可點畫面提示到應用程式資訊右上角 ⋮ 解鎖）、【藍牙／通話權限】與【忽略電池最佳化】。",
                            color = colors.secondaryText,
                            fontSize = 12.sp
                        )
                        Text(
                            text = "3. 省電連線機制：手機端平常待機不搜尋不耗電，會在「手機連上車機藍牙」時自動喚醒同步；亦可開手機 Wi-Fi 熱點給車機連網作為雙備援。",
                            color = colors.secondaryText,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            // SECTION 5: AI Box 權限健檢
            SettingsSectionCard(
                icon = Icons.Rounded.Security,
                title = "AI Box 權限與系統設定健檢"
            ) {
                PermissionRow(
                    title = "設為預設車機啟動器 (Home)",
                    granted = permissionHealth.isDefaultHome,
                    onAction = { SystemStatusMonitor.openHomeLauncherSettings(context) }
                )
                PermissionRow(
                    title = "顯示在其他應用程式上層（常駐 Dock 與全螢幕來電訊息）",
                    granted = permissionHealth.hasOverlay,
                    onAction = { PermissionHelper.openOverlayPermission(context) }
                )
                PermissionRow(
                    title = "通知存取權（媒體播放控制、Google 導航提示、訊息攔截）",
                    granted = permissionHealth.hasNotificationListener,
                    onAction = { PermissionHelper.openNotificationListenerSettings(context) }
                )
                PermissionRow(
                    title = "無障礙輔助服務（追蹤全螢幕 YouTube 與全域返回首頁）",
                    granted = permissionHealth.hasAccessibility,
                    onAction = { PermissionHelper.openAccessibilitySettings(context) }
                )
                PermissionRow(
                    title = "定位、藍牙、電話與通知執行期權限",
                    granted = permissionHealth.hasLocation && permissionHealth.hasBluetooth && permissionHealth.hasPhoneState,
                    onAction = onRequestRuntimePermissions
                )
                PermissionRow(
                    title = "修改系統設定（雙指滑動調整亮度）",
                    granted = permissionHealth.hasWriteSettings,
                    onAction = { PermissionHelper.openWriteSettingsPermission(context) }
                )
                PermissionRow(
                    title = "忽略電池最佳化（防止車機背景砍掉常駐 Dock 與藍牙橋接）",
                    granted = permissionHealth.hasIgnoreBatteryOpt,
                    onAction = { PermissionHelper.requestIgnoreBatteryOptimizations(context) }
                )

                OutlinedButton(
                    onClick = onRefreshPermissions,
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("重新檢查權限狀態")
                }
            }

            // SECTION 6: 自動檢測 App 更新、自動下載與 GitHub 連結
            SettingsSectionCard(
                icon = Icons.Rounded.CloudDownload,
                title = "App 自動更新與 GitHub 開源專案"
            ) {
                SettingsToggleRow(
                    title = "啟動時自動檢測並自動下載新版 APK",
                    subtitle = "偵測到 GitHub Releases 有新版本時自動背景下載並提示一鍵安裝",
                    checked = config.autoCheckAndDownloadUpdate,
                    onCheckedChange = { checked -> onUpdateConfig { it.copy(autoCheckAndDownloadUpdate = checked) } }
                )

                Surface(
                    color = colors.cardSurfaceElevated,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "目前版本：v${BuildConfig.VERSION_NAME} (code ${BuildConfig.VERSION_CODE})",
                                    color = colors.primaryText,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = updateState.statusMessage.ifBlank { "對接 GitHub Releases 自動更新通道" },
                                    color = if (updateState.hasUpdate || updateState.downloadedReady) colors.accent else colors.secondaryText,
                                    fontSize = 12.sp
                                )
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = onCheckUpdateNow,
                                    enabled = !updateState.checking && !updateState.downloading,
                                    shape = RoundedCornerShape(14.dp)
                                ) {
                                    Text(if (updateState.checking) "檢查中…" else "檢查更新")
                                }

                                if (updateState.hasUpdate || updateState.downloadedReady) {
                                    Button(
                                        onClick = onDownloadOrInstallUpdate,
                                        enabled = !updateState.downloading,
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = colors.accent,
                                            contentColor = colors.onAccent
                                        ),
                                        shape = RoundedCornerShape(14.dp)
                                    ) {
                                        Text(
                                            when {
                                                updateState.downloadedReady -> "立即安裝 v${updateState.latestVersion}"
                                                updateState.downloading -> "下載中 ${(updateState.progress * 100).toInt()}%"
                                                else -> "下載更新"
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        if (updateState.downloading) {
                            Spacer(Modifier.height(4.dp))
                            if (updateState.totalBytes > 0L) {
                                LinearProgressIndicator(
                                    progress = { updateState.progress },
                                    color = colors.accent,
                                    trackColor = colors.cardBorder,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(8.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                )
                            } else {
                                LinearProgressIndicator(
                                    color = colors.accent,
                                    trackColor = colors.cardBorder,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(8.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                )
                            }
                        }
                    }
                }

                // Direct GitHub Repository Link Card
                Surface(
                    color = colors.cardSurfaceElevated,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, colors.accent.copy(alpha = 0.55f), RoundedCornerShape(16.dp))
                        .clickable {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(UpdateManager.GITHUB_URL))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            runCatching { context.startActivity(intent) }
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(colors.accent.copy(alpha = 0.16f))
                            ) {
                                Icon(Icons.Rounded.Code, contentDescription = null, tint = colors.accent)
                            }
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "GitHub 開源儲存庫",
                                    color = colors.primaryText,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = UpdateManager.GITHUB_URL,
                                    color = colors.accent,
                                    fontSize = 12.sp
                                )
                            }
                        }
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                            contentDescription = "開啟 GitHub",
                            tint = colors.accent
                        )
                    }
                }
                // Reset all DX-Car Launcher settings to factory defaults
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    OutlinedButton(
                        onClick = {
                            if (confirmResetPending) {
                                onResetSettingsToDefaults()
                                confirmResetPending = false
                            } else {
                                confirmResetPending = true
                            }
                        },
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Rounded.RestartAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            if (confirmResetPending) "再次點擊確認恢復所有設定為預設值"
                            else "恢復啟動器設定為預設值"
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SettingsSectionCard(
    icon: ImageVector,
    title: String,
    content: @Composable () -> Unit
) {
    val colors = LocalDxCarColors.current
    Surface(
        color = colors.cardSurface,
        shape = RoundedCornerShape(22.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, colors.cardBorder, RoundedCornerShape(22.dp))
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = colors.accent, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    text = title,
                    color = colors.primaryText,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            content()
        }
    }
}

@Composable
private fun SelectablePill(
    text: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val colors = LocalDxCarColors.current
    Surface(
        color = if (selected) colors.accent else colors.cardSurfaceElevated,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .border(
                1.dp,
                if (selected) colors.accent else colors.cardBorder,
                RoundedCornerShape(16.dp)
            )
            .bouncyClickable(pressedScale = 0.91f, onClick = onClick)
    ) {
        Text(
            text = text,
            color = if (selected) colors.onAccent else colors.primaryText,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp)
        )
    }
}

@Composable
private fun TimeStepperCard(
    label: String,
    hour: Int,
    minute: Int,
    onDeltaMinutes: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalDxCarColors.current
    Surface(
        color = colors.cardSurfaceElevated,
        shape = RoundedCornerShape(16.dp),
        modifier = modifier.border(1.dp, colors.cardBorder, RoundedCornerShape(16.dp))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(label, color = colors.secondaryText, fontSize = 11.sp)
                Text(
                    text = String.format(Locale.US, "%02d:%02d", hour, minute),
                    color = colors.primaryText,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Row {
                IconButton(onClick = { onDeltaMinutes(-15) }) {
                    Icon(Icons.Rounded.Remove, contentDescription = "減少15分", tint = colors.primaryText)
                }
                IconButton(onClick = { onDeltaMinutes(15) }) {
                    Icon(Icons.Rounded.Add, contentDescription = "增加15分", tint = colors.primaryText)
                }
            }
        }
    }
}

@Composable
private fun SettingsToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val colors = LocalDxCarColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, color = colors.primaryText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = colors.secondaryText, fontSize = 12.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = colors.onAccent,
                checkedTrackColor = colors.accent
            )
        )
    }
}

@Composable
private fun GestureLegendRow(gesture: String, action: String) {
    val colors = LocalDxCarColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(colors.cardSurfaceElevated)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(gesture, color = colors.accent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Text(action, color = colors.primaryText, fontSize = 13.sp)
    }
}

@Composable
private fun PermissionRow(
    title: String,
    granted: Boolean,
    onAction: () -> Unit
) {
    val colors = LocalDxCarColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(colors.cardSurfaceElevated)
            .clickable { onAction() }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Icon(
                imageVector = if (granted) Icons.Rounded.CheckCircle else Icons.Rounded.WarningAmber,
                contentDescription = null,
                tint = if (granted) colors.statusConnected else Color(0xFFF59E0B),
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(10.dp))
            Text(title, color = colors.primaryText, fontSize = 13.sp)
        }
        Text(
            text = if (granted) "已就緒" else "前往開啟",
            color = if (granted) colors.statusConnected else colors.accent,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

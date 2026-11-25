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
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.DisplaySettings
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import com.dxcar.launcher.billing.BillingTrialState
import com.dxcar.launcher.comms.BridgeStatusState
import com.dxcar.launcher.data.AppLanguage
import com.dxcar.launcher.data.DockPositionMode
import com.dxcar.launcher.data.GeminiVoiceMode
import com.dxcar.launcher.data.HomeExperimentMode
import com.dxcar.launcher.data.HomeMapDisplayMode
import com.dxcar.launcher.data.LauncherConfig
import com.dxcar.launcher.data.LauncherStylePreset
import com.dxcar.launcher.data.MapEngineStyle
import com.dxcar.launcher.data.OfflineUpdatePolicy
import com.dxcar.launcher.data.SplitLaunchEngine
import com.dxcar.launcher.data.ThemeMode
import com.dxcar.launcher.i18n.DxCarI18n
import com.dxcar.launcher.i18n.DxCarI18n.str
import com.dxcar.launcher.i18n.LocalAppLanguage
import com.dxcar.launcher.layout.CoolwalkLayoutSpec
import com.dxcar.launcher.map.OfflineMapManager
import com.dxcar.launcher.map.OfflineMapUiState
import com.dxcar.launcher.map.OfflineRegionPreset
import com.dxcar.launcher.overlay.ExtraLargeTutorialModal
import com.dxcar.launcher.overlay.TUTORIAL_3D_CURSOR_STEPS
import com.dxcar.launcher.system.AppCategory
import com.dxcar.launcher.system.HomeLauncherInfo
import com.dxcar.launcher.system.InstalledAppInfo
import com.dxcar.launcher.system.PermissionHealth
import com.dxcar.launcher.system.PermissionHelper
import com.dxcar.launcher.system.SystemStatusMonitor
import com.dxcar.launcher.theme.DayNightEngine
import com.dxcar.launcher.theme.DxCarAccents
import com.dxcar.launcher.theme.LocalDxCarColors
import com.dxcar.launcher.update.UpdateManager
import com.dxcar.launcher.update.UpdateUiState
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import kotlin.math.roundToInt

enum class CarSettingsTutorialTopic {
    CURSOR_3D,
    PHONE_PAIRING,
    STOCK_DOCK_CONFLICT
}

/**
 * Categorized 2-Column Adaptive Car / Tablet Settings Screen:
 * - Top bar houses a compact "🛡️ 權限設定 (X/7)" pill button -> opens the dedicated Permissions + 60fps sheet!
 * - Multi-finger gestures page removed; Plan B removed; Plan A Offline Map settings integrated.
 * - Dock supports Left (LHD) / Right (RHD) / Top / Bottom + Floating on Other Launchers + Stock Dock conflict reminder & jump button.
 * - All tutorials open via explicit "教學" buttons with extra-large 26sp/22sp typography.
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
    billingState: BillingTrialState = BillingTrialState(),
    offlineState: OfflineMapUiState = OfflineMapUiState(),
    offlinePresets: List<OfflineRegionPreset> = emptyList(),
    onDownloadOfflineRegion: (String) -> Unit = {},
    onCancelOfflineDownload: () -> Unit = {},
    onStartVoiceNavigation: () -> Unit = {},
    onTestWifiHotspotCycle: () -> Unit = {},
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
    onPurchasePro: () -> Unit = {},
    onRestorePurchases: () -> Unit = {},
    onSwitchToPhoneCompanion: () -> Unit,
    showPermissionsSheet: Boolean = false,
    onShowPermissionsSheetChange: (Boolean) -> Unit = {},
    activeTutorial: CarSettingsTutorialTopic? = null,
    onActiveTutorialChange: (CarSettingsTutorialTopic?) -> Unit = {},
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalDxCarColors.current
    val context = LocalContext.current
    var confirmResetPending by remember { mutableStateOf(false) }

    val (grantedPerms, totalPerms) = remember(permissionHealth) { permissionHealth.summaryForCar() }
    val allPermsOk = grantedPerms == totalPerms
    val isWideTwoColumn = layoutSpec.metrics.widthDp >= 600f

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

    Box(modifier = modifier.fillMaxSize().background(colors.canvasBackground)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            // TOP HEADER BAR + COMPACT PERMISSIONS BUTTON ("集中在設定最上方的一個小按鈕提示")
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
                            text = str("DX-Car 車機啟動器設定", "DX-Car 车机启动器设置", "DX-Car Launcher Settings", "DX-Car ランチャー設定"),
                            color = colors.primaryText,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = str(
                                "已依功能分類排列 · 點右上「權限需求」檢查授權與 60fps 開關",
                                "已按功能分类排列 · 点右上「权限需求」检查授权与 60fps 开关",
                                "Categorized layout · Tap Permissions for grants & 60fps toggle",
                                "機能別に分類 · 右上の「権限」から各種許可と 60fps を設定"
                            ),
                            color = colors.secondaryText,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Compact Top Permissions Pill Button (Car & Tablet!)
                    Surface(
                        color = if (allPermsOk) colors.statusConnected.copy(alpha = 0.16f) else Color(0xFFF59E0B).copy(alpha = 0.20f),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .border(
                                1.2.dp,
                                if (allPermsOk) colors.statusConnected else Color(0xFFF59E0B),
                                RoundedCornerShape(16.dp)
                            )
                            .bouncyClickable {
                                onRefreshPermissions()
                                onShowPermissionsSheetChange(true)
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (allPermsOk) Icons.Rounded.CheckCircle else Icons.Rounded.Security,
                                contentDescription = null,
                                tint = if (allPermsOk) colors.statusConnected else Color(0xFFF59E0B),
                                modifier = Modifier.size(17.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = if (allPermsOk) {
                                    str("權限已就緒 ($grantedPerms/$totalPerms)", "Permissions OK ($grantedPerms/$totalPerms)", "権限 OK ($grantedPerms/$totalPerms)")
                                } else {
                                    str("權限需求 ($grantedPerms/$totalPerms)", "Permissions ($grantedPerms/$totalPerms)", "権限設定 ($grantedPerms/$totalPerms)")
                                },
                                color = colors.primaryText,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    OutlinedButton(
                        onClick = onSwitchToPhoneCompanion,
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(Icons.Rounded.PhoneAndroid, contentDescription = null, modifier = Modifier.size(17.dp))
                        Spacer(Modifier.width(5.dp))
                        Text(str("手機同步模式", "Phone Mode", "スマホ連携モード"), fontSize = 13.sp, maxLines = 1)
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            // MAIN SCROLLABLE CATEGORIZED SETTINGS (2-Column on Wide Car/Tablet >= 600dp, 1-Column on Compact)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .springBounceOverscroll(maxOverscrollPx = 150f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // ==================== LANGUAGE SELECTOR (繁體中文 / 简体中文 / English / 日本語) ====================
                val currentUiLang = LocalAppLanguage.current
                Surface(
                    color = colors.cardSurface,
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, colors.accent.copy(alpha = 0.45f), RoundedCornerShape(20.dp))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.padding(end = 12.dp)) {
                            Text(
                                text = str("🌐 介面語言 / UI Language", "🌐 界面语言 / UI Language", "🌐 UI Language / 介面語言", "🌐 表示言語 / UI Language"),
                                color = colors.primaryText,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = str(
                                    "支援繁體中文、简体中文、English、日本語（即時切換免重啟）",
                                    "支持繁体中文、简体中文、English、日本語（实时切换无需重启）",
                                    "Traditional / Simplified Chinese, English & Japanese (Instant)",
                                    "繁體中文・簡体字・English・日本語に対応（即時切替）"
                                ),
                                color = colors.secondaryText,
                                fontSize = 11.sp
                            )
                        }
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            AppLanguage.entries.forEach { langOption ->
                                SelectablePill(
                                    text = DxCarI18n.appLanguageLabel(langOption, currentUiLang),
                                    selected = config.appLanguage == langOption,
                                    onClick = { onUpdateConfig { it.copy(appLanguage = langOption) } }
                                )
                            }
                        }
                    }
                }

                // ==================== CATEGORY 1: 首頁方案、離線地圖與視覺風格 ====================
                CategoryHeaderBanner(
                    categoryTitle = str(
                        "分類一 · 首頁方案、離線地圖與視覺風格",
                        "Category 1 · Home Plan, Offline Maps & Visual Style",
                        "カテゴリ 1 · ホームプラン・オフライン地図・外観"
                    ),
                    hint = str(
                        "選擇方案 A（含離線地圖下載與自動更新判斷）或方案 C（指定導航＋純音樂），並自訂日夜主題與分割比例",
                        "Choose Plan A (Offline Maps & Traffic) or Plan C (Custom Nav + Music), plus Theme & Split Ratio",
                        "プラン A（オフライン地図＆渋滞）またはプラン C（指定ナビ＋音楽）、テーマと分割比率を設定"
                    )
                )

                val cardPlanAndOffline: @Composable (Modifier) -> Unit = { mod ->
                    SettingsSectionCard(
                        icon = Icons.Rounded.Science,
                        title = str("首頁方案與離線地圖管理", "Home Plan & Offline Maps", "ホームプラン・オフライン地図"),
                        modifier = mod
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            HomeExperimentMode.visibleEntries.forEach { mode ->
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
                                            .padding(horizontal = 14.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Text(
                                                    text = DxCarI18n.experimentTitle(mode),
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
                                                            Text(str("待 Root", "Needs Root", "Root 待ち"), color = colors.secondaryText, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                                                        }
                                                    }
                                                }
                                            }
                                            Text(DxCarI18n.experimentDesc(mode), color = colors.secondaryText, fontSize = 11.sp)
                                        }
                                        if (mode.isAvailable) {
                                            Spacer(Modifier.width(8.dp))
                                            Switch(
                                                checked = selected,
                                                onCheckedChange = { onUpdateConfig { it.copy(homeExperimentMode = mode) } },
                                                colors = SwitchDefaults.colors(checkedThumbColor = colors.onAccent, checkedTrackColor = colors.accent)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Plan A Offline Map Download & Update Cycle Quick Controls ("依據所在區域，或是所在國家，可以全下載")
                        val detectedLoc = remember(config.lastKnownLat, config.lastKnownLon) {
                            OfflineMapManager.describeDetectedLocation(config.lastKnownLat, config.lastKnownLon)
                        }
                        Surface(
                            color = colors.cardSurfaceElevated,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    text = "方案 A 離線地圖 · 所在區域／所在國家全下載（已快取 ${config.offlineRegions.size} 區）",
                                    color = colors.primaryText,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    OfflineUpdatePolicy.entries.forEach { pol ->
                                        SelectablePill(
                                            text = pol.labelZh,
                                            selected = config.offlineUpdatePolicy == pol,
                                            onClick = { onUpdateConfig { it.copy(offlineUpdatePolicy = pol) } }
                                        )
                                    }
                                }
                                if (offlineState.statusMessage.isNotBlank()) {
                                    Text(offlineState.statusMessage, color = colors.accent, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                }
                                if (offlineState.downloadingRegionId != null) {
                                    val pctInt = (offlineState.progress * 100f).toInt().coerceIn(0, 100)
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Surface(
                                            color = colors.accent.copy(alpha = 0.2f),
                                            shape = RoundedCornerShape(10.dp)
                                        ) {
                                            Text(
                                                text = "$pctInt%",
                                                color = colors.accent,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                                            )
                                        }
                                        LinearProgressIndicator(
                                            progress = { offlineState.progress },
                                            color = colors.accent,
                                            trackColor = colors.cardBorder,
                                            modifier = Modifier.weight(1f).height(8.dp).clip(RoundedCornerShape(4.dp))
                                        )
                                        OutlinedButton(onClick = onCancelOfflineDownload, shape = RoundedCornerShape(10.dp)) {
                                            Text("取消", fontSize = 11.sp)
                                        }
                                    }
                                }
                                // Smart Current-Region & Entire-Country One-Tap Download Buttons!
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Button(
                                        onClick = { onDownloadOfflineRegion(OfflineMapManager.TOKEN_AUTO_LOCAL_REGION) },
                                        enabled = offlineState.downloadingRegionId == null,
                                        colors = ButtonDefaults.buttonColors(containerColor = colors.accent, contentColor = colors.onAccent),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Text("📍 下載所在區域（${detectedLoc.localRegionTitleZh}）", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                    Button(
                                        onClick = { onDownloadOfflineRegion(OfflineMapManager.TOKEN_AUTO_ENTIRE_COUNTRY) },
                                        enabled = offlineState.downloadingRegionId == null,
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF22C55E), contentColor = Color.White),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Text("🌏 全下載所在國家（${detectedLoc.countryTitleZh}）", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                                // Full Regional List (no .take(4) truncation!)
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    offlinePresets.forEach { preset ->
                                        val downloaded = config.offlineRegions.any { it.regionId == preset.id }
                                        OutlinedButton(
                                            onClick = { onDownloadOfflineRegion(preset.id) },
                                            enabled = offlineState.downloadingRegionId == null,
                                            shape = RoundedCornerShape(12.dp)
                                        ) {
                                            Text(
                                                text = if (downloaded) "✓ 更新 ${preset.titleZh}" else "⬇ 下載 ${preset.titleZh}",
                                                fontSize = 11.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        SettingsToggleRow(
                            title = "媒體控制器下方整合顯示 6 格快捷 App",
                            subtitle = "關閉則將右側空間全數留給上方當地天氣與超大專輯封面",
                            checked = config.showSixShortcutsInMediaPanel,
                            onCheckedChange = { checked -> onUpdateConfig { it.copy(showSixShortcutsInMediaPanel = checked) } }
                        )
                    }
                }

                val cardStyleAndMap: @Composable (Modifier) -> Unit = { mod ->
                    SettingsSectionCard(
                        icon = Icons.Rounded.Palette,
                        title = "介面風格、日夜排程與分割比例",
                        modifier = mod
                    ) {
                        Text("介面風格預設", color = colors.primaryText, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            LauncherStylePreset.entries.forEach { preset ->
                                SelectablePill(
                                    text = preset.labelZh,
                                    selected = config.stylePreset == preset,
                                    onClick = { onUpdateConfig { it.copy(stylePreset = preset) } }
                                )
                            }
                        }

                        Text("強調色主題", color = colors.primaryText, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            DxCarAccents.forEachIndexed { idx, accent ->
                                val selected = config.accentColorIndex == idx
                                val swatch = if (colors.isNight) accent.nightColor else accent.dayColor
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.clickable { onUpdateConfig { it.copy(accentColorIndex = idx) } }
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(34.dp)
                                            .clip(CircleShape)
                                            .background(swatch)
                                            .border(if (selected) 3.dp else 1.dp, if (selected) colors.primaryText else colors.cardBorder, CircleShape)
                                    )
                                    Text(DxCarI18n.accentName(idx, accent.nameZh), color = if (selected) colors.primaryText else colors.secondaryText, fontSize = 10.sp)
                                }
                            }
                        }

                        Text(str("白天／夜晚切換模式", "Day / Night Mode", "昼間 / 夜間モード"), color = colors.primaryText, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            ThemeMode.entries.forEach { mode ->
                                SelectablePill(
                                    text = DxCarI18n.themeModeLabel(mode),
                                    selected = config.themeMode == mode,
                                    onClick = { onUpdateConfig { it.copy(themeMode = mode) } }
                                )
                            }
                        }

                        if (config.themeMode == ThemeMode.AUTO_TIME) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                TimeStepperCard(
                                    label = "白天開始",
                                    hour = config.dayStartHour,
                                    minute = config.dayStartMinute,
                                    onDeltaMinutes = { delta ->
                                        val total = ((config.dayStartHour * 60 + config.dayStartMinute + delta) % 1440 + 1440) % 1440
                                        onUpdateConfig { it.copy(dayStartHour = total / 60, dayStartMinute = total % 60) }
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                                TimeStepperCard(
                                    label = "夜晚開始",
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
                                text = "今日 GPS 日出 ${solarPreview.formatSunrise()} · 日落 ${solarPreview.formatSunset()}",
                                color = colors.accent,
                                fontSize = 12.sp
                            )
                        }

                        Text(str("內建地圖圖層與路況", "Map Layers & Traffic", "地図レイヤー・交通状況"), color = colors.primaryText, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            MapEngineStyle.entries.forEach { styleItem ->
                                SelectablePill(
                                    text = DxCarI18n.mapEngineLabel(styleItem),
                                    selected = config.mapEngineStyle == styleItem,
                                    onClick = { onUpdateConfig { it.copy(mapEngineStyle = styleItem) } }
                                )
                            }
                        }
                        SettingsToggleRow(
                            title = str("疊加 Google 即時路況圖層", "Google Live Traffic Layer", "Google 交通状況レイヤー"),
                            subtitle = str(
                                "顯示紅黃綠即時車流；可搭配「開啟分屏設定」測試 AOSP 原生可拖動分屏",
                                "Shows live traffic flow; pair with Split Setup to test draggable AOSP split-screen",
                                "リアルタイム渋滞情報を表示（画面分割設定と併用可能）"
                            ),
                            checked = config.mapTrafficEnabled,
                            onCheckedChange = { checked -> onUpdateConfig { it.copy(mapTrafficEnabled = checked) } }
                        )
                        // ALWAYS-VISIBLE 60FPS / 60HZ SWITCH + LIVE HARDWARE CAPABILITY PROMPT!
                        val refreshCap = remember(config.prefer60FpsEnabled) {
                            SystemStatusMonitor.inspectRefreshRateCapability(context)
                        }
                        Surface(
                            color = colors.accent.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.2.dp, colors.accent.copy(alpha = 0.7f), RoundedCornerShape(16.dp))
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                        Icon(Icons.Rounded.Speed, contentDescription = null, tint = colors.accent, modifier = Modifier.size(20.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Column {
                                            Text(str("60fps / 60Hz 高流暢度顯示模式", "60fps / 60Hz High Refresh Mode", "60fps / 60Hz 高フレームレート表示"), color = colors.primaryText, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                            Text(
                                                text = if (refreshCap.supports60Hz) {
                                                    str("✓ 可用（支援 ${refreshCap.maxSupportedHz}Hz · 目前 ${refreshCap.currentHz}Hz）", "✓ Supported (${refreshCap.maxSupportedHz}Hz max · ${refreshCap.currentHz}Hz now)", "✓ 利用可能（最大 ${refreshCap.maxSupportedHz}Hz · 現在 ${refreshCap.currentHz}Hz）")
                                                } else {
                                                    str("⚠️ 目前 ${refreshCap.currentHz}Hz（依車機盒韌體）", "⚠️ Currently ${refreshCap.currentHz}Hz (OEM dependent)", "⚠️ 現在 ${refreshCap.currentHz}Hz（端末依存）")
                                                },
                                                color = if (refreshCap.supports60Hz) colors.statusConnected else Color(0xFFF59E0B),
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                    Switch(
                                        checked = config.prefer60FpsEnabled,
                                        onCheckedChange = { checked -> onUpdateConfig { it.copy(prefer60FpsEnabled = checked) } },
                                        colors = SwitchDefaults.colors(checkedThumbColor = colors.onAccent, checkedTrackColor = colors.accent)
                                    )
                                }
                                Text(
                                    text = if (refreshCap.supports60Hz) {
                                        str(
                                            "偵測到此裝置螢幕支援 ${refreshCap.maxSupportedHz}Hz，開啟即可請求 60fps 高流暢度渲染！",
                                            "Display supports ${refreshCap.maxSupportedHz}Hz; enable to request 60fps smooth rendering.",
                                            "${refreshCap.maxSupportedHz}Hz 対応ディスプレイを検出。ON で 60fps 描画を要求します。"
                                        )
                                    } else {
                                        str(
                                            "未偵測到原生 60Hz（系統回報 ${refreshCap.currentHz}Hz）；開關仍可開啟以請求最高幀率，亦可進原廠設定檢查。",
                                            "Native 60Hz not reported (${refreshCap.currentHz}Hz); toggle still requests max FPS or check OEM settings.",
                                            "ネイティブ 60Hz 未検出（${refreshCap.currentHz}Hz）。ON で最大 FPS を要求、または純正設定を確認できます。"
                                        )
                                    },
                                    color = colors.secondaryText,
                                    fontSize = 11.sp
                                )
                                OutlinedButton(
                                    onClick = { SystemStatusMonitor.openAiBoxVendorSettings(context, preferDisplayFor60Fps = true) },
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(Icons.Rounded.Settings, contentDescription = null, modifier = Modifier.size(15.dp))
                                    Spacer(Modifier.width(5.dp))
                                    Text(str("開啟車機顯示／原廠設定檢查 60fps 選項", "Open Car / OEM Display Settings", "車載機・純正ディスプレイ設定を開く"), fontSize = 11.sp)
                                }
                            }
                        }

                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedButton(onClick = onLaunchRealMapsSplitScreen, shape = RoundedCornerShape(12.dp)) {
                                Text(str("測試 AOSP 分割G地圖", "Test AOSP Split Maps", "AOSP 分割マップテスト"), fontSize = 12.sp)
                            }
                            Button(
                                onClick = onOpenSplitShortcutCreator,
                                colors = ButtonDefaults.buttonColors(containerColor = colors.accent, contentColor = colors.onAccent),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(str("分屏設定／讀取原車分屏", "Split Setup / Import Stock", "画面分割設定・純正取込"), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = str("首頁比例：", "Home Ratio: ", "ホーム比率：") +
                                    "${(config.primarySplitRatio * 100).roundToInt()}% / ${((1f - config.primarySplitRatio) * 100).roundToInt()}%",
                                color = colors.primaryText,
                                fontSize = 13.sp,
                                modifier = Modifier.width(155.dp)
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
                }

                AdaptiveTwoColumnRow(isWideTwoColumn, cardPlanAndOffline, cardStyleAndMap)

                // ==================== CATEGORY 2: 常駐 Dock（支援上方／跨啟動器）與啟動器管理 ====================
                CategoryHeaderBanner(
                    categoryTitle = "分類二 · 常駐懸浮 Dock（左／右／上方／跨啟動器）與預設桌面",
                    hint = "左駕預設左側、右駕預設右側，亦可改放「上方」；支援懸浮於其他啟動器上，並內建 3D 俯瞰類滑鼠指標"
                )

                val cardDockAnd3D: @Composable (Modifier) -> Unit = { mod ->
                    SettingsSectionCard(
                        icon = Icons.Rounded.DisplaySettings,
                        title = "Dock 位置（左／右／上方）與跨啟動器常駐",
                        headerTrailing = {
                            TutorialPillButton(onClick = { onActiveTutorialChange(CarSettingsTutorialTopic.CURSOR_3D) })
                        },
                        modifier = mod
                    ) {
                        // Default stock launcher Dock / Assistive Ball conflict reminder + direct jump to CarBox Settings!
                        if (config.persistentOverlayDockEnabled) {
                            Surface(
                                color = Color(0xFFF59E0B).copy(alpha = 0.15f),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.2.dp, Color(0xFFF59E0B), RoundedCornerShape(16.dp))
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Rounded.WarningAmber, contentDescription = null, tint = Color(0xFFF59E0B), modifier = Modifier.size(20.dp))
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            text = "永久懸浮提醒：請關閉原車啟動器自帶的 Dock 與懸浮按鈕",
                                            color = colors.primaryText,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    Text(
                                        text = "當啟用本啟動器 Dock 永久懸浮（或懸浮在其他啟動器上方）時，建議先進入車機盒設定關閉原車自帶的側邊欄與懸浮小白點，避免雙重遮擋。",
                                        color = colors.secondaryText,
                                        fontSize = 12.sp
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Button(
                                            onClick = { SystemStatusMonitor.openAiBoxVendorSettings(context, preferDisplayFor60Fps = false) },
                                            colors = ButtonDefaults.buttonColors(containerColor = colors.accent, contentColor = colors.onAccent),
                                            shape = RoundedCornerShape(12.dp)
                                        ) {
                                            Icon(Icons.Rounded.Settings, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(5.dp))
                                            Text("前往車機盒設定頁面", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        }
                                        TutorialPillButton(
                                            label = "教學",
                                            onClick = { onActiveTutorialChange(CarSettingsTutorialTopic.STOCK_DOCK_CONFLICT) }
                                        )
                                    }
                                }
                            }
                        }

                        Text(str("駕駛座位置（左駕預設靠左 · 右駕預設靠右）", "Driver Side (LHD Left · RHD Right)", "運転席側（左ハンドル・右ハンドル）"), color = colors.primaryText, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SelectablePill(
                                text = str("左駕模式（預設左側）", "LHD (Left Dock)", "左ハンドル（左ドック）"),
                                selected = !config.rightHandDrive,
                                onClick = { onUpdateConfig { it.copy(rightHandDrive = false) } }
                            )
                            SelectablePill(
                                text = str("右駕模式（預設右側）", "RHD (Right Dock)", "右ハンドル（右ドック）"),
                                selected = config.rightHandDrive,
                                onClick = { onUpdateConfig { it.copy(rightHandDrive = true) } }
                            )
                        }

                        Text(str("Dock 擺放位置選擇（支援放置於上方！）", "Dock Placement (Supports Top Bar!)", "ドック配置（上部バー対応！）"), color = colors.primaryText, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(
                                DockPositionMode.DRIVER_SIDE,
                                DockPositionMode.FORCE_TOP,
                                DockPositionMode.FORCE_LEFT,
                                DockPositionMode.FORCE_RIGHT,
                                DockPositionMode.FORCE_BOTTOM,
                                DockPositionMode.AUTO_GOOGLE_DPI
                            ).forEach { mode ->
                                SelectablePill(
                                    text = DxCarI18n.dockModeLabel(mode, config.rightHandDrive),
                                    selected = config.dockPositionMode == mode,
                                    onClick = { onUpdateConfig { it.copy(dockPositionMode = mode) } }
                                )
                            }
                        }

                        SettingsToggleRow(
                            title = "常駐懸浮 Dock（全螢幕 App 上方常駐 · 手機端預設停用）",
                            subtitle = "開啟後於 Google 地圖／YouTube 全螢幕時保留常駐列（含 3D 俯瞰類滑鼠指標與返回／多工鍵）",
                            checked = config.persistentOverlayDockEnabled,
                            onCheckedChange = { checked -> onUpdateConfig { it.copy(persistentOverlayDockEnabled = checked) } }
                        )

                        if (config.persistentOverlayDockEnabled) {
                            SettingsToggleRow(
                                title = "同時懸浮在「其他車機啟動器」桌面上",
                                subtitle = "切換回原廠啟動器或其他桌面時，依然保留 DX-Car 常駐懸浮 Dock",
                                checked = config.overlayDockOnOtherLaunchers,
                                onCheckedChange = { checked -> onUpdateConfig { it.copy(overlayDockOnOtherLaunchers = checked) } }
                            )

                            SettingsToggleRow(
                                title = "自動收合為透明邊緣拉條（關閉則永久展開懸浮）",
                                subtitle = "開啟時縮成邊緣拉條，輕點或往內滑展開；關閉則保持完整 Dock 永久懸浮",
                                checked = config.overlayAutoCollapseEnabled,
                                onCheckedChange = { checked -> onUpdateConfig { it.copy(overlayAutoCollapseEnabled = checked) } }
                            )

                            if (config.overlayAutoCollapseEnabled) {
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    listOf(
                                        1500L to "1.5 秒收回（預設）",
                                        3000L to "3 秒收回",
                                        5000L to "5 秒收回",
                                        0L to "手動收回"
                                    ).forEach { (ms, label) ->
                                        SelectablePill(
                                            text = label,
                                            selected = config.overlayAutoRetractMs == ms,
                                            onClick = { onUpdateConfig { it.copy(overlayAutoRetractMs = ms) } }
                                        )
                                    }
                                }
                                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Text("拉條厚度：${config.overlayHandleThicknessDp} dp", color = colors.primaryText, fontSize = 12.sp, modifier = Modifier.width(135.dp))
                                    Slider(
                                        value = config.overlayHandleThicknessDp.toFloat(),
                                        onValueChange = { v -> onUpdateConfig { it.copy(overlayHandleThicknessDp = v.roundToInt().coerceIn(16, 48)) } },
                                        valueRange = 16f..48f,
                                        colors = SliderDefaults.colors(thumbColor = colors.accent, activeTrackColor = colors.accent),
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }

                        SettingsToggleRow(
                            title = "首頁顯示本啟動器 Dock",
                            subtitle = "目前判定：${layoutSpec.dockPlacement.labelZh}（${layoutSpec.metrics.widthPx}×${layoutSpec.metrics.heightPx} · ${layoutSpec.metrics.densityDpi}dpi）",
                            checked = config.showHomeDock,
                            onCheckedChange = { checked -> onUpdateConfig { it.copy(showHomeDock = checked) } }
                        )

                        SettingsToggleRow(
                            title = "24 小時制時鐘",
                            subtitle = "關閉時顯示單行 12 小時制（如 8:20 PM）；點擊左上 Wi-Fi 圖示可展開下拉快捷鍵",
                            checked = config.is24HourClock,
                            onCheckedChange = { checked -> onUpdateConfig { it.copy(is24HourClock = checked) } }
                        )

                        // Prominent 3D Cursor Tutorial Row
                        Surface(
                            color = colors.cardSurfaceElevated,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                    Text("🧊 Dock「3D 俯瞰」類滑鼠指標操作說明", color = colors.primaryText, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                    Text("單指移動＝一般觸控 · 長壓 2 秒不放＝左右縮放／上下 3D 俯瞰", color = colors.secondaryText, fontSize = 11.sp)
                                }
                                TutorialPillButton(
                                    label = "教學",
                                    onClick = { onActiveTutorialChange(CarSettingsTutorialTopic.CURSOR_3D) }
                                )
                            }
                        }
                    }
                }

                val cardHomeLaunchers: @Composable (Modifier) -> Unit = { mod ->
                    SettingsSectionCard(
                        icon = Icons.Rounded.Home,
                        title = "啟動器切換與系統預設桌面",
                        modifier = mod
                    ) {
                        val currentDefaultLauncher = homeLaunchers.firstOrNull { it.isDefault }
                        Text(
                            text = if (permissionHealth.isDefaultHome) {
                                "✓ 目前預設啟動器：DX-Car Launcher"
                            } else {
                                "目前預設啟動器：${currentDefaultLauncher?.label ?: "其他啟動器"}"
                            },
                            color = if (permissionHealth.isDefaultHome) colors.statusConnected else colors.primaryText,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )

                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = onRequestSetDefaultHome,
                                colors = ButtonDefaults.buttonColors(containerColor = colors.accent, contentColor = colors.onAccent),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Icon(Icons.Rounded.Home, contentDescription = null, modifier = Modifier.size(17.dp))
                                Spacer(Modifier.width(5.dp))
                                Text(if (permissionHealth.isDefaultHome) "重選預設桌面" else "設為系統預設桌面", fontSize = 12.sp)
                            }
                            OutlinedButton(
                                onClick = { SystemStatusMonitor.openHomeLauncherSettings(context) },
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Icon(Icons.Rounded.SwapHoriz, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("系統桌面設定", fontSize = 12.sp)
                            }
                            OutlinedButton(onClick = onRefreshHomeLaunchers, shape = RoundedCornerShape(14.dp)) {
                                Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            }
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            homeLaunchers.forEach { launcher ->
                                val iconBmp = remember(launcher.icon) { launcher.icon.asImageBitmap() }
                                Surface(
                                    color = colors.cardSurfaceElevated,
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .border(1.dp, if (launcher.isDefault) colors.accent else colors.cardBorder, RoundedCornerShape(14.dp))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                            Image(bitmap = iconBmp, contentDescription = launcher.label, modifier = Modifier.size(34.dp).clip(RoundedCornerShape(8.dp)))
                                            Spacer(Modifier.width(10.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(launcher.label, color = colors.primaryText, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                Text(
                                                    text = if (launcher.isDefault) "系統預設桌面" else launcher.packageName,
                                                    color = if (launcher.isDefault) colors.statusConnected else colors.secondaryText,
                                                    fontSize = 11.sp,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                        if (!launcher.isSelf) {
                                            Button(
                                                onClick = { onSwitchToLauncher(launcher) },
                                                colors = ButtonDefaults.buttonColors(containerColor = colors.accent, contentColor = colors.onAccent),
                                                shape = RoundedCornerShape(12.dp)
                                            ) {
                                                Text("切換", fontSize = 12.sp)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                AdaptiveTwoColumnRow(isWideTwoColumn, cardDockAnd3D, cardHomeLaunchers)

                // ==================== CATEGORY 3: 手機連線同步與版本更新 ====================
                CategoryHeaderBanner(
                    categoryTitle = "分類三 · 手機雙通道配對同步、遠端滑鼠與系統更新",
                    hint = "手機連上車機藍牙免持或開啟 Wi-Fi 熱點即可自動穩定同步通知與遠端滑鼠觸控板；點擊「教學」查看特大字步驟"
                )

                val cardPhoneBridge: @Composable (Modifier) -> Unit = { mod ->
                    SettingsSectionCard(
                        icon = Icons.Rounded.NotificationsActive,
                        title = "手機來電／訊息同步與遠端觸控",
                        headerTrailing = {
                            TutorialPillButton(onClick = { onActiveTutorialChange(CarSettingsTutorialTopic.PHONE_PAIRING) })
                        },
                        modifier = mod
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
                                            "連線狀態：${bridgeStatus.status.labelZh} (${bridgeStatus.transportLabel})"
                                        } else {
                                            "連線狀態：${bridgeStatus.status.labelZh}"
                                        },
                                        color = colors.primaryText,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = if (bridgeStatus.peerName.isNotBlank()) {
                                            "已連接手機：${bridgeStatus.peerName}"
                                        } else {
                                            "支援藍牙 SPP ＋ 手機熱點閘道雙向自動直連（無須手動輸入 IP）"
                                        },
                                        color = colors.secondaryText,
                                        fontSize = 12.sp
                                    )
                                }
                                Icon(Icons.Rounded.Bluetooth, contentDescription = null, tint = colors.accent, modifier = Modifier.size(26.dp))
                            }
                        }

                        SettingsToggleRow(
                            title = str(
                                "全螢幕導航／看影片時彈出頂部來電與訊息橫幅",
                                "Show Top Call & Message Banner in Fullscreen Apps",
                                "フルスクリーン時に着信・通知バナーを上部に表示"
                            ),
                            subtitle = str(
                                "支援接聽、掛斷、快速回覆與語音回覆",
                                "Supports Answer, Decline, Quick Reply & Voice Reply",
                                "応答・切断・クイック返信・音声返信に対応"
                            ),
                            checked = config.globalCallMessageBannerEnabled,
                            onCheckedChange = { checked -> onUpdateConfig { it.copy(globalCallMessageBannerEnabled = checked) } }
                        )

                        SettingsToggleRow(
                            title = str(
                                "收到新訊息時自動語音朗讀 (TTS)",
                                "Auto-Read New Messages (TTS)",
                                "新着メッセージの自動音声読み上げ (TTS)"
                            ),
                            subtitle = str(
                                "自動壓低音樂音量播報寄件人與內容",
                                "Automatically ducks music volume to announce sender & message",
                                "音楽の音量を自動で下げて送信者と内容を読み上げます"
                            ),
                            checked = config.autoReadMessagesTts,
                            onCheckedChange = { checked -> onUpdateConfig { it.copy(autoReadMessagesTts = checked) } }
                        )

                        // Notification & Incoming Call Scale Selector (0.5X / 0.75X / 1X Default / 1.5X / 2X)
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = str(
                                    "左上通知與來電橫幅大小（預設 1X 精簡紅框尺寸，字體連動縮放）",
                                    "Notification & Call Banner Scale (1X Compact Default)",
                                    "左上通知・着信バナーサイズ（標準 1X コンパクト）"
                                ),
                                color = colors.primaryText,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                com.dxcar.launcher.data.NotificationBannerScale.entries.forEach { scaleOpt ->
                                    val labelText = if (scaleOpt == com.dxcar.launcher.data.NotificationBannerScale.SCALE_1_0X) {
                                        "${scaleOpt.label} (${str("預設", "Default", "標準")})"
                                    } else {
                                        scaleOpt.label
                                    }
                                    SelectablePill(
                                        text = labelText,
                                        selected = config.notificationBannerScale == scaleOpt,
                                        onClick = { onUpdateConfig { it.copy(notificationBannerScale = scaleOpt) } }
                                    )
                                }
                            }
                        }

                        // Custom Quick Replies Editor
                        var newQuickReplyInput by remember { mutableStateOf("") }
                        Surface(
                            color = colors.cardSurfaceElevated,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, colors.cardBorder, RoundedCornerShape(14.dp))
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = str("💬 自訂快速回覆選項（點擊通知「快速回覆」展開滑動選擇）", "💬 Custom Quick Replies", "💬 クイック返信のカスタマイズ"),
                                        color = colors.primaryText,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.weight(1f)
                                    )
                                    OutlinedButton(
                                        onClick = {
                                            onUpdateConfig { it.copy(quickReplies = LauncherConfig.DEFAULT_QUICK_REPLIES) }
                                        },
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Text(str("恢復預設", "Reset", "初期化"), fontSize = 11.sp)
                                    }
                                }
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    config.quickReplies.forEachIndexed { idx, replyItem ->
                                        Surface(
                                            color = colors.cardSurface,
                                            shape = RoundedCornerShape(10.dp),
                                            modifier = Modifier.border(1.dp, colors.cardBorder, RoundedCornerShape(10.dp))
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.padding(start = 10.dp, end = 4.dp, top = 4.dp, bottom = 4.dp)
                                            ) {
                                                Text(replyItem, color = colors.primaryText, fontSize = 12.sp)
                                                IconButton(
                                                    onClick = {
                                                        onUpdateConfig { cur ->
                                                            cur.copy(quickReplies = cur.quickReplies.filterIndexed { i, _ -> i != idx })
                                                        }
                                                    },
                                                    modifier = Modifier.size(22.dp)
                                                ) {
                                                    Icon(Icons.Rounded.Close, contentDescription = "刪除", tint = colors.secondaryText, modifier = Modifier.size(14.dp))
                                                }
                                            }
                                        }
                                    }
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedTextField(
                                        value = newQuickReplyInput,
                                        onValueChange = { newQuickReplyInput = it },
                                        singleLine = true,
                                        placeholder = { Text(str("輸入新增快速回覆詞（例如：正在停車中）…", "Add quick reply phrase…", "返信フレーズを追加…"), fontSize = 12.sp) },
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.weight(1f)
                                    )
                                    Button(
                                        onClick = {
                                            val trimmed = newQuickReplyInput.trim()
                                            if (trimmed.isNotEmpty()) {
                                                onUpdateConfig { cur ->
                                                    cur.copy(quickReplies = (cur.quickReplies + trimmed).distinct())
                                                }
                                                newQuickReplyInput = ""
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = colors.accent, contentColor = colors.onAccent),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Text(str("＋ 新增", "+ Add", "＋ 追加"), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }

                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = onSimulateCall, shape = RoundedCornerShape(12.dp)) {
                                Text(str("測試來電橫幅 (${config.notificationBannerScale.label})", "Test Call (${config.notificationBannerScale.label})", "着信テスト (${config.notificationBannerScale.label})"), fontSize = 12.sp)
                            }
                            OutlinedButton(onClick = onSimulateMessage, shape = RoundedCornerShape(12.dp)) {
                                Text(str("測試左上通知 (${config.notificationBannerScale.label})", "Test Alert (${config.notificationBannerScale.label})", "通知テスト (${config.notificationBannerScale.label})"), fontSize = 12.sp)
                            }
                            TutorialPillButton(
                                label = str("教學", "Guide", "ガイド"),
                                onClick = { onActiveTutorialChange(CarSettingsTutorialTopic.PHONE_PAIRING) }
                            )
                        }

                        // GEMINI VOICE NAVIGATION SETTINGS ("啟動器的按鈕導航可否接受語音輸入，走gemini?")
                        Surface(
                            color = colors.cardSurfaceElevated,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, Color(0xFFA78BFA).copy(alpha = 0.6f), RoundedCornerShape(16.dp))
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                        Icon(Icons.Rounded.Mic, contentDescription = null, tint = Color(0xFFA78BFA), modifier = Modifier.size(20.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("✨ Gemini 語音輸入導航（Dock 與地圖一鍵呼叫）", color = colors.primaryText, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                    }
                                    Button(
                                        onClick = onStartVoiceNavigation,
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C3AED), contentColor = Color.White),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Text("🎙️ 測試語音導航", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    GeminiVoiceMode.entries.forEach { gm ->
                                        SelectablePill(
                                            text = gm.labelZh,
                                            selected = config.geminiVoiceMode == gm,
                                            onClick = { onUpdateConfig { it.copy(geminiVoiceMode = gm) } }
                                        )
                                    }
                                }
                                OutlinedTextField(
                                    value = config.geminiApiKey,
                                    onValueChange = { key -> onUpdateConfig { it.copy(geminiApiKey = key.trim()) } },
                                    singleLine = true,
                                    label = { Text("選填 Gemini API Key（不填亦可免 Key 語音導航／喚起 Gemini App）", fontSize = 11.sp) },
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }

                val cardUpdatesAndGithub: @Composable (Modifier) -> Unit = { mod ->
                    SettingsSectionCard(
                        icon = Icons.Rounded.CloudDownload,
                        title = str("七天試用買斷、GitHub 主頁與恢復預設", "Trial, Pro Unlock, GitHub & Reset", "トライアル・買い切り・GitHub・初期化"),
                        modifier = mod
                    ) {
                        if (BuildConfig.ENABLE_GITHUB_AUTO_UPDATE) {
                            SettingsToggleRow(
                                title = str("啟動時自動檢測並下載新版 APK", "Auto-Check & Download Updates", "起動時に自動で更新を確認・取得"),
                                subtitle = str(
                                    "目前版本：v${BuildConfig.VERSION_NAME} (code ${BuildConfig.VERSION_CODE})",
                                    "Current: v${BuildConfig.VERSION_NAME} (code ${BuildConfig.VERSION_CODE})",
                                    "現在: v${BuildConfig.VERSION_NAME} (code ${BuildConfig.VERSION_CODE})"
                                ),
                                checked = config.autoCheckAndDownloadUpdate,
                                onCheckedChange = { checked -> onUpdateConfig { it.copy(autoCheckAndDownloadUpdate = checked) } }
                            )
                            if (updateState.statusMessage.isNotBlank()) {
                                Text(
                                    text = updateState.statusMessage,
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
                                    Text(
                                        if (updateState.checking) str("檢查中…", "Checking…", "確認中…")
                                        else str("檢查更新", "Check Updates", "更新を確認")
                                    )
                                }
                                if (updateState.hasUpdate || updateState.downloadedReady) {
                                    Button(
                                        onClick = onDownloadOrInstallUpdate,
                                        enabled = !updateState.downloading,
                                        colors = ButtonDefaults.buttonColors(containerColor = colors.accent, contentColor = colors.onAccent),
                                        shape = RoundedCornerShape(14.dp)
                                    ) {
                                        Text(
                                            when {
                                                updateState.downloadedReady -> str("立即安裝 v${updateState.latestVersion}", "Install v${updateState.latestVersion}", "今すぐインストール v${updateState.latestVersion}")
                                                updateState.downloading -> str("下載中 ${(updateState.progress * 100).toInt()}%", "Downloading ${(updateState.progress * 100).toInt()}%", "ダウンロード中 ${(updateState.progress * 100).toInt()}%")
                                                else -> str("下載更新", "Download Update", "更新をダウンロード")
                                            }
                                        )
                                    }
                                }
                            }
                            if (updateState.downloading) {
                                if (updateState.totalBytes > 0L) {
                                    LinearProgressIndicator(
                                        progress = { updateState.progress },
                                        color = colors.accent,
                                        trackColor = colors.cardBorder,
                                        modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp))
                                    )
                                } else {
                                    LinearProgressIndicator(
                                        color = colors.accent,
                                        trackColor = colors.cardBorder,
                                        modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp))
                                    )
                                }
                            }
                        }

                        Surface(
                            color = colors.cardSurfaceElevated,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.2.dp, colors.accent.copy(alpha = 0.65f), RoundedCornerShape(16.dp))
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = if (billingState.isProUnlocked) {
                                        str("👑 DX-Car Pro 永久買斷版（已解鎖）", "👑 DX-Car Pro 永久买断版（已解锁）", "👑 DX-Car Pro Lifetime (Unlocked)", "👑 DX-Car Pro 買い切り版（解除済み）")
                                    } else if (billingState.isInTrial) {
                                        str(
                                            "🎁 七天全功能免費試用中（剩餘 ${billingState.remainingDays} 天 ${billingState.remainingHoursRemainder} 小時）",
                                            "🎁 七天全功能免费试用中（剩余 ${billingState.remainingDays} 天 ${billingState.remainingHoursRemainder} 小时）",
                                            "🎁 7-Day Full Trial (${billingState.remainingDays}d ${billingState.remainingHoursRemainder}h left)",
                                            "🎁 7日間フル機能トライアル（残り ${billingState.remainingDays}日 ${billingState.remainingHoursRemainder}時間）"
                                        )
                                    } else {
                                        str("🔒 七天免費試用已結束，請一次買斷永久解鎖", "🔒 七天免费试用已结束，请一次买断永久解锁", "🔒 7-Day Trial Ended — Unlock Lifetime Pro", "🔒 7日間トライアル終了 — 買い切りで解除")
                                    },
                                    color = colors.primaryText,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = str(
                                        "目前版本：v${BuildConfig.VERSION_NAME} (code ${BuildConfig.VERSION_CODE}) · 一次買斷 ${billingState.formattedPrice} 永久使用全功能",
                                        "当前版本：v${BuildConfig.VERSION_NAME} (code ${BuildConfig.VERSION_CODE}) · 一次买断 ${billingState.formattedPrice} 永久使用全功能",
                                        "Version v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) · One-time ${billingState.formattedPrice} lifetime unlock",
                                        "バージョン v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) · ${billingState.formattedPrice} 買い切りで全機能開放"
                                    ),
                                    color = colors.secondaryText,
                                    fontSize = 11.sp
                                )
                                if (billingState.statusMessage.isNotBlank()) {
                                    Text(billingState.statusMessage, color = colors.accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                }
                                if (!billingState.isProUnlocked) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Button(
                                            onClick = onPurchasePro,
                                            colors = ButtonDefaults.buttonColors(containerColor = colors.accent, contentColor = colors.onAccent),
                                            shape = RoundedCornerShape(12.dp)
                                        ) {
                                            Text(
                                                str(
                                                    "立即買斷 ${billingState.formattedPrice}",
                                                    "立即买断 ${billingState.formattedPrice}",
                                                    "Unlock ${billingState.formattedPrice}",
                                                    "買い切り購入 ${billingState.formattedPrice}"
                                                ),
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                        OutlinedButton(onClick = onRestorePurchases, shape = RoundedCornerShape(12.dp)) {
                                            Text(str("恢復購買", "Restore", "購入を復元"), fontSize = 12.sp)
                                        }
                                    }
                                }
                            }
                        }

                        Surface(
                            color = colors.cardSurfaceElevated,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, colors.accent.copy(alpha = 0.55f), RoundedCornerShape(14.dp))
                                .clickable {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(UpdateManager.GITHUB_URL))
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    runCatching { context.startActivity(intent) }
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Rounded.Code, contentDescription = null, tint = colors.accent)
                                    Spacer(Modifier.width(10.dp))
                                    Column {
                                        Text("GitHub 開源儲存庫", color = colors.primaryText, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                        Text(UpdateManager.GITHUB_URL, color = colors.accent, fontSize = 11.sp)
                                    }
                                }
                                Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null, tint = colors.accent)
                            }
                        }

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
                            Icon(Icons.Rounded.RestartAlt, contentDescription = null, modifier = Modifier.size(17.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                if (confirmResetPending) str("再次點擊確認重置所有設定", "Tap again to confirm reset", "もう一度タップして初期化")
                                else str("恢復啟動器設定為預設值", "Reset Launcher Defaults", "ランチャー設定を初期化"),
                                fontSize = 12.sp
                            )
                        }
                    }
                }

                AdaptiveTwoColumnRow(isWideTwoColumn, cardPhoneBridge, cardUpdatesAndGithub)

                Spacer(Modifier.height(18.dp))
            }
        }

        // DEDICATED PERMISSIONS + 60FPS SHEET (Opened by clicking the Top Permissions Pill Button!)
        if (showPermissionsSheet) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.74f))
                    .clickable { onShowPermissionsSheetChange(false) },
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    color = colors.cardSurface,
                    shape = RoundedCornerShape(26.dp),
                    shadowElevation = 24.dp,
                    modifier = Modifier
                        .fillMaxWidth(0.84f)
                        .padding(vertical = 20.dp)
                        .border(1.5.dp, colors.accent, RoundedCornerShape(26.dp))
                        .clickable(enabled = false) {}
                ) {
                    Column(
                        modifier = Modifier
                            .padding(22.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.Security, contentDescription = null, tint = colors.accent, modifier = Modifier.size(26.dp))
                                Spacer(Modifier.width(10.dp))
                                Column {
                                    Text("車機／平板權限列表與 60fps 高流暢設定", color = colors.primaryText, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                                    Text("目前已授權 $grantedPerms / $totalPerms 項必要權限", color = colors.secondaryText, fontSize = 12.sp)
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = onRefreshPermissions, shape = RoundedCornerShape(12.dp)) {
                                    Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("重新檢查")
                                }
                                IconButton(onClick = { onShowPermissionsSheetChange(false) }) {
                                    Icon(Icons.Rounded.Close, contentDescription = "關閉", tint = colors.primaryText)
                                }
                            }
                        }

                        // 60FPS / 60Hz High Frame Rate Switch + Hardware Capability Status Badge!
                        val sheetRefreshCap = remember(config.prefer60FpsEnabled) {
                            SystemStatusMonitor.inspectRefreshRateCapability(context)
                        }
                        Surface(
                            color = colors.accent.copy(alpha = 0.14f),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.2.dp, colors.accent, RoundedCornerShape(16.dp))
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                        Icon(Icons.Rounded.Speed, contentDescription = null, tint = colors.accent, modifier = Modifier.size(22.dp))
                                        Spacer(Modifier.width(8.dp))
                                        Column {
                                            Text("開啟 60fps / 60Hz 畫面高幀率模式", color = colors.primaryText, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                            Text(
                                                text = sheetRefreshCap.statusBadgeZh,
                                                color = if (sheetRefreshCap.supports60Hz) colors.statusConnected else Color(0xFFF59E0B),
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(sheetRefreshCap.detailHintZh, color = colors.secondaryText, fontSize = 11.sp)
                                        }
                                    }
                                    Switch(
                                        checked = config.prefer60FpsEnabled,
                                        onCheckedChange = { checked ->
                                            onUpdateConfig { it.copy(prefer60FpsEnabled = checked) }
                                        },
                                        colors = SwitchDefaults.colors(checkedThumbColor = colors.onAccent, checkedTrackColor = colors.accent)
                                    )
                                }
                                Button(
                                    onClick = { SystemStatusMonitor.openAiBoxVendorSettings(context, preferDisplayFor60Fps = true) },
                                    colors = ButtonDefaults.buttonColors(containerColor = colors.accent, contentColor = colors.onAccent),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(Icons.Rounded.Settings, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("進入原廠車機設定開啟 60fps（若有此選項即可開啟）", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        PermissionRow(
                            title = "設為預設車機啟動器 (Home)",
                            granted = permissionHealth.isDefaultHome,
                            onAction = onRequestSetDefaultHome
                        )
                        PermissionRow(
                            title = "顯示在其他應用程式上層（常駐 Dock、3D 指標、遠端滑鼠與來電橫幅）",
                            granted = permissionHealth.hasOverlay,
                            onAction = { PermissionHelper.openOverlayPermission(context) }
                        )
                        PermissionRow(
                            title = "通知存取權（音樂封面／紅心控制、Google 天氣讀取、導航與訊息）",
                            granted = permissionHealth.hasNotificationListener,
                            onAction = { PermissionHelper.openNotificationListenerSettings(context) }
                        )
                        PermissionRow(
                            title = "無障礙輔助服務（AOSP 可拖動分屏、3D 類滑鼠指標與手機遠端滑鼠點擊）",
                            granted = permissionHealth.hasAccessibility,
                            onAction = { PermissionHelper.openAccessibilitySettings(context) }
                        )
                        PermissionRow(
                            title = "定位、藍牙、電話與通知執行期權限",
                            granted = permissionHealth.hasLocation && permissionHealth.hasBluetooth && permissionHealth.hasPhoneState,
                            onAction = onRequestRuntimePermissions
                        )
                        PermissionRow(
                            title = "修改系統設定（快捷面板螢幕亮度控制）",
                            granted = permissionHealth.hasWriteSettings,
                            onAction = { PermissionHelper.openWriteSettingsPermission(context) }
                        )
                        PermissionRow(
                            title = "忽略電池最佳化（防止背景關閉常駐 Dock 與雙通道連線）",
                            granted = permissionHealth.hasIgnoreBatteryOpt,
                            onAction = { PermissionHelper.requestIgnoreBatteryOptimizations(context) }
                        )
                    }
                }
            }
        }

        // EXTRA-LARGE FONT TUTORIAL MODALS (Triggered by clicking any "教學" button!)
        when (activeTutorial) {
            CarSettingsTutorialTopic.CURSOR_3D -> {
                ExtraLargeTutorialModal(
                    title = "🧊 Dock「3D 俯瞰」類滑鼠指標 · 操作教學",
                    steps = TUTORIAL_3D_CURSOR_STEPS,
                    highlightNote = "💡 提醒：請先點設定最上方「權限設定」確認【顯示在其他應用程式上層】與【無障礙輔助服務】皆已開啟。",
                    onClose = { onActiveTutorialChange(null) }
                )
            }

            CarSettingsTutorialTopic.PHONE_PAIRING -> {
                ExtraLargeTutorialModal(
                    title = "📱 車機與手機配對同步 · 完整教學",
                    steps = listOf(
                        "【車機端】點設定最上方「權限設定」，開啟【顯示在其他應用程式上層】、【通知存取權】與【無障礙輔助服務】（啟用遠端滑鼠點擊）。",
                        "【手機端】安裝同一支 APK 開啟會自動進入手機介面：點最上方「權限設定」開啟【通知讀取權限】（Android 13+ 若顯示受限制設定，先去應用程式資訊右上角 ⋮ 點「允許受限制的設定」）。",
                        "【自動連線方式】只要「手機連上車機盒藍牙（可接電話狀態）」或「開啟手機 Wi-Fi 熱點給車機連網」，雙端會自動透過藍牙 SPP 或熱點閘道穩定連線，不需手動輸入 IP！",
                        "【遠端滑鼠觸控板】連線後手機畫面可直接當作車機滑鼠觸控板使用；點手機上的「車機滑鼠靈敏度設定」，車機畫面就會自動跳出系統指標速度設定頁！"
                    ),
                    highlightNote = "✓ 已修復 Wi-Fi 熱點頻繁斷開問題：現在通知推播與「推播測試通知」皆走背景高速通道，連線持續穩定不中斷！",
                    onClose = { onActiveTutorialChange(null) }
                )
            }

            CarSettingsTutorialTopic.STOCK_DOCK_CONFLICT -> {
                ExtraLargeTutorialModal(
                    title = "⚙️ 關閉原車啟動器 Dock 與懸浮球 · 教學",
                    steps = listOf(
                        "當您開啟 DX-Car Launcher 的「常駐懸浮 Dock（永久懸浮或跨啟動器懸浮）」時，若車機盒原本自帶左側欄或白色懸浮球，兩個懸浮層可能會重疊。",
                        "請點擊下方「前往車機盒設定頁面」按鈕（或開啟車機盒內建的「車機設定／輔助觸控／懸浮球」App）。",
                        "將原車系統的「懸浮按鈕（Assistive Touch／小白點）」與「原廠導航側邊欄」關閉，即可享受乾淨無遮擋的 AA Coolwalk 常駐 Dock！"
                    ),
                    actionButtonLabel = "立即前往車機盒設定頁面",
                    onActionClick = {
                        SystemStatusMonitor.openAiBoxVendorSettings(context, preferDisplayFor60Fps = false)
                        onActiveTutorialChange(null)
                    },
                    onClose = { onActiveTutorialChange(null) }
                )
            }

            null -> Unit
        }
    }
}

@Composable
private fun AdaptiveTwoColumnRow(
    twoColumn: Boolean,
    leftCard: @Composable (Modifier) -> Unit,
    rightCard: @Composable (Modifier) -> Unit
) {
    if (twoColumn) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            leftCard(Modifier.weight(1f))
            rightCard(Modifier.weight(1f))
        }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            leftCard(Modifier.fillMaxWidth())
            rightCard(Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun CategoryHeaderBanner(
    categoryTitle: String,
    hint: String
) {
    val colors = LocalDxCarColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp, start = 4.dp, end = 4.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = categoryTitle,
            color = colors.accent,
            fontSize = 15.sp,
            fontWeight = FontWeight.ExtraBold
        )
        Text(
            text = hint,
            color = colors.secondaryText,
            fontSize = 12.sp
        )
    }
}

@Composable
fun TutorialPillButton(
    label: String = "教學",
    onClick: () -> Unit
) {
    val colors = LocalDxCarColors.current
    Surface(
        color = colors.accent,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.bouncyClickable(pressedScale = 0.90f, onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.AutoMirrored.Rounded.HelpOutline, contentDescription = null, tint = colors.onAccent, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text(label, color = colors.onAccent, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
        }
    }
}

@Composable
private fun SettingsSectionCard(
    icon: ImageVector,
    title: String,
    headerTrailing: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val colors = LocalDxCarColors.current
    Surface(
        color = colors.cardSurface,
        shape = RoundedCornerShape(22.dp),
        modifier = modifier
            .border(1.dp, colors.cardBorder, RoundedCornerShape(22.dp))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(11.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f).padding(end = 8.dp)
                ) {
                    Icon(icon, contentDescription = null, tint = colors.accent, modifier = Modifier.size(21.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = title,
                        color = colors.primaryText,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                headerTrailing?.invoke()
            }
            content()
        }
    }
}

@Composable
internal fun SelectablePill(
    text: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val colors = LocalDxCarColors.current
    Surface(
        color = if (selected) colors.accent else colors.cardSurfaceElevated,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .border(1.dp, if (selected) colors.accent else colors.cardBorder, RoundedCornerShape(14.dp))
            .bouncyClickable(pressedScale = 0.91f, onClick = onClick)
    ) {
        Text(
            text = text,
            color = if (selected) colors.onAccent else colors.primaryText,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
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
        shape = RoundedCornerShape(14.dp),
        modifier = modifier.border(1.dp, colors.cardBorder, RoundedCornerShape(14.dp))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(label, color = colors.secondaryText, fontSize = 10.sp)
                Text(
                    text = String.format(Locale.US, "%02d:%02d", hour, minute),
                    color = colors.primaryText,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Row {
                IconButton(onClick = { onDeltaMinutes(-15) }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Rounded.Remove, contentDescription = "減少15分", tint = colors.primaryText, modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = { onDeltaMinutes(15) }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Rounded.Add, contentDescription = "增加15分", tint = colors.primaryText, modifier = Modifier.size(18.dp))
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
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 10.dp)) {
            Text(title, color = colors.primaryText, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = colors.secondaryText, fontSize = 11.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(checkedThumbColor = colors.onAccent, checkedTrackColor = colors.accent)
        )
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
            modifier = Modifier.weight(1f).padding(end = 8.dp)
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

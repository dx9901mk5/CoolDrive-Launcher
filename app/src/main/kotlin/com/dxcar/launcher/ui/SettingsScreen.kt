package com.dxcar.launcher.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import java.io.File
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
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.DisplaySettings
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Save
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
import androidx.compose.material3.OutlinedTextField
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
import com.dxcar.launcher.overlay.tutorial3DControlSteps
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
    DUAL_UI_GUIDE,
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
    onUpdateConfig: ((LauncherConfig) -> LauncherConfig) -> Unit,
    onRequestSetDefaultHome: () -> Unit,
    onSwitchToLauncher: (HomeLauncherInfo) -> Unit,
    onRefreshHomeLaunchers: () -> Unit,
    onLaunchRealMapsSplitScreen: () -> Unit,
    onLaunchRealMapsFreeform: () -> Unit,
    onOpenSplitShortcutCreator: () -> Unit,
    onExportConfigJson: () -> String = { "{}" },
    onImportConfigJson: (String) -> Boolean = { false },
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
                            contentDescription = str("返回", "返回", "Back", "戻る"),
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
                                    str(
                                        "權限已就緒 ($grantedPerms/$totalPerms)",
                                        "权限已就绪 ($grantedPerms/$totalPerms)",
                                        "Permissions OK ($grantedPerms/$totalPerms)",
                                        "権限 OK ($grantedPerms/$totalPerms)"
                                    )
                                } else {
                                    str(
                                        "權限需求 ($grantedPerms/$totalPerms)",
                                        "权限需求 ($grantedPerms/$totalPerms)",
                                        "Permissions ($grantedPerms/$totalPerms)",
                                        "権限設定 ($grantedPerms/$totalPerms)"
                                    )
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
                        Text(str("手機同步模式", "手机同步模式", "Phone Mode", "スマホ連携モード"), fontSize = 13.sp, maxLines = 1)
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
                        "分类一 · 首页方案、离线地图与视觉风格",
                        "Category 1 · Home Plan, Offline Maps & Visual Style",
                        "カテゴリ 1 · ホームプラン・オフライン地図・外観"
                    ),
                    hint = str(
                        "選擇方案 A（含離線地圖下載與自動更新判斷）或方案 C（指定導航＋純音樂），並自訂日夜主題與分割比例",
                        "选择方案 A（含离线地图下载与自动更新判断）或方案 C（指定导航＋纯音乐），并自定义日夜主题与分屏比例",
                        "Choose Plan A (Offline Maps & Traffic) or Plan C (Custom Nav + Music), plus Theme & Split Ratio",
                        "プラン A（オフライン地図＆渋滞）またはプラン C（指定ナビ＋音楽）、テーマと分割比率を設定"
                    )
                )

                val cardPlanAndOffline: @Composable (Modifier) -> Unit = { mod ->
                    SettingsSectionCard(
                        icon = Icons.Rounded.Science,
                        title = str(
                            "首頁方案、第一／第二介面教學與離線地圖",
                            "首页方案、第一／第二界面教程与离线地图",
                            "Home Plan, UI 1 & 2 Guide & Offline Maps",
                            "ホームプラン・第1/第2画面ガイド・オフライン地図"
                        ),
                        headerTrailing = {
                            TutorialPillButton(
                                label = str("放大教學", "放大教程", "Full Guide", "拡大ガイド"),
                                onClick = { onActiveTutorialChange(CarSettingsTutorialTopic.DUAL_UI_GUIDE) }
                            )
                        },
                        modifier = mod
                    ) {
                        // Built-in First UI (1/2) & Second UI (2/2) Usage Guide Description Card
                        Surface(
                            color = colors.cardSurfaceElevated,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, colors.accent.copy(alpha = 0.55f), RoundedCornerShape(16.dp))
                        ) {
                            Column(
                                modifier = Modifier.padding(13.dp),
                                verticalArrangement = Arrangement.spacedBy(7.dp)
                            ) {
                                Text(
                                    text = str(
                                        "📖 方案 A · 第 0 / 第 1 / 第 2 介面操作說明",
                                        "📖 方案 A · 第 0 / 第 1 / 第 2 界面操作说明",
                                        "📖 Plan A · UI 0 / UI 1 / UI 2 Usage Guide",
                                        "📖 プラン A · 第0 / 第1 / 第2 画面操作ガイド"
                                    ),
                                    color = colors.accent,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                                Text(
                                    text = str(
                                        "• 切換介面：按住畫面中央分隔線握把（0/3、1/3、2/3）向左或向右滑動即可循環切換。",
                                        "• 切换界面：按住画面中央分隔线握把（0/3、1/3、2/3）向左或向右滑动即可循环切换。",
                                        "• Switch Pages: Drag the center divider handle (0/3, 1/3, 2/3) left or right.",
                                        "• 画面切替：中央の区切りバー（0/3・1/3・2/3）を左右にスライドして切替えます。"
                                    ),
                                    color = colors.primaryText,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = str(
                                        "⓪ 第 0 介面（自訂小工具看板）：自由放置時間、時速、天氣與導航捷徑卡片。\n" +
                                            "① 第 1 介面（仿 Android Auto 卡片左右切換）：\n" +
                                            "  - 卡片左右切換：點擊播放器「專輯封面」即可仿 Android Auto 流暢交換主／副卡片位置與大小。\n" +
                                            "  - 天氣與播放 App：點擊封面左上角「天氣地點膠囊」直開天氣；點擊點讚（♡）旁的「App 圖示」直入該音樂 App。\n" +
                                            "  - 導航方向與圖層：地圖右下角可一鍵切換「正北朝上 / 車頭朝上」與底圖圖層；搜尋欄「+ 新增」最多支援 8 組快捷導航並可左右滑動選擇。",
                                        "⓪ 第 0 界面（自定义小组件看板）：自由放置时间、时速、天气与导航快捷卡片。\n" +
                                            "① 第 1 界面（仿 Android Auto 卡片左右切换）：\n" +
                                            "  - 卡片左右切换：点击播放器「专辑封面」即可仿 Android Auto 流畅交换主／副卡片位置与大小。\n" +
                                            "  - 天气与播放 App：点击封面左上角「天气地点胶囊」直开天气；点击点赞（♡）旁的「App 图标」直入该音乐 App。\n" +
                                            "  - 导航方向与图层：地图右下角可一键切换「正北朝上 / 车头朝上」与底图图层；搜索栏「+ 新增」最多支持 8 组快捷导航并可左右滑动选择。",
                                        "⓪ UI 0 (Custom Widgets Board): Place clock, speedometer, weather & nav shortcut widgets.\n" +
                                            "① UI 1 (Android Auto-Style Left/Right Card Swap):\n" +
                                            "  - Swap Cards: Tap the Album Cover to smoothly swap primary/secondary card sizes just like Android Auto.\n" +
                                            "  - Weather & Player: Tap top-left weather pill for Weather; tap App Icon next to Like (♡) to open the player app.\n" +
                                            "  - Heading & Shortcuts: Toggle North-Up / Heading-Up at bottom-right; add up to 8 custom nav shortcuts with horizontal scroll.",
                                        "⓪ 第0画面（カスタムウィジェット）：時計・速度・天気・ナビショートカットを自由に配置。\n" +
                                            "① 第1画面（Android Auto 風カード左右切替）：\n" +
                                            "  - カード左右切替：「アルバムカバー」をタップで Android Auto のように左右カードを入替。\n" +
                                            "  - 天気＆アプリ：左上の天気ピルで天気表示、いいね（♡）横のアイコンで再生アプリを起動。\n" +
                                            "  - 方位切替＆登録：右下でノースアップ/ヘディングアップ切替、最大8件のナビ登録を横スクロール可能。"
                                    ),
                                    color = colors.secondaryText,
                                    fontSize = 11.5.sp,
                                    lineHeight = 17.sp
                                )
                                Text(
                                    text = str(
                                        "② 第 2 介面（內建地圖 ＋ YouTube 雙視窗 · 預設 720p）：\n" +
                                            "  - 長壓中線不放開調比例：長壓中央「2/3 分隔線」手指不放開，出現發光與「◀ 比例 ▶」動畫提示時左右拖曳即可自由調整視窗比例。\n" +
                                            "  - 點兩下左右交換：快速「點兩下」中央分隔線，立即平滑交換左右兩個視窗位置。\n" +
                                            "  - 右半邊全螢幕與快轉：於 YouTube 上半部上滑進入滿版全螢幕（下滑退出）；左/右 1/3 點兩下倒退/快轉 10 秒，按住可持續加速。",
                                        "② 第 2 界面（内置地图 ＋ YouTube 双窗口 · 默认 720p）：\n" +
                                            "  - 长按中线不放空调比例：长按中央「2/3 分隔线」手指不放开，出现发光与「◀ 比例 ▶」动画提示时左右拖拽即可自由调整窗口比例。\n" +
                                            "  - 双击左右交换：快速「双击」中央分隔线，立即平滑交换左右两个窗口位置。\n" +
                                            "  - 右半边全屏与快进：在 YouTube 上半部上滑进入满版全屏（下滑退出）；左/右 1/3 双击快退/快进 10 秒，按住可持续加速。",
                                        "② UI 2 (Built-in Map + YouTube Dual Window · Default 720p):\n" +
                                            "  - Long-Press & Hold Divider to Resize: Long-press the center 2/3 divider without lifting; when the glowing ◀ ratio ▶ animation appears, drag left/right to resize.\n" +
                                            "  - Double-Tap to Swap: Double-tap the center divider to swap left and right windows immediately.\n" +
                                            "  - Fullscreen & Seek: Swipe up on upper video for fullscreen (swipe down to exit); double-tap left/right 1/3 for -10s/+10s.",
                                        "② 第2画面（内蔵地図＋YouTube 2画面・標準 720p）：\n" +
                                            "  - 長押しホールドで比率調整：中央「2/3 区切り線」を指を離さず長押しし、発光＆「◀ 比率 ▶」アニメ表示中に左右ドラッグで比率変更。\n" +
                                            "  - ダブルタップで左右入替：中央の区切り線を2回タップすると左右ウィンドウが即座に入れ替わります。\n" +
                                            "  - 全画面＆シーク：動画上部を上スワイプで全画面（下スワイプで終了）、左右1/3ダブルタップで10秒戻し/送り。"
                                    ),
                                    color = colors.secondaryText,
                                    fontSize = 11.5.sp,
                                    lineHeight = 17.sp
                                )
                            }
                        }

                        val hasRootHelper = remember { com.dxcar.launcher.system.RootHelperBridge.isRootHelperInstalled(context) }
                        var planBConfig by remember(hasRootHelper, config.homeExperimentMode, config.launchAppsAvoidDockFreeform) {
                            mutableStateOf(
                                if (hasRootHelper) com.dxcar.launcher.system.RootHelperBridge.readPlanBConfig(context) else null
                            )
                        }
                        fun switchExperimentModeAndSyncDock(targetMode: HomeExperimentMode) {
                            if (config.homeExperimentMode == targetMode) return
                            val dm = context.resources.displayMetrics
                            // Always cancel permanent Dock shift on A/B switch (Helper can re-enable on Plan B launch)
                            if (hasRootHelper) {
                                com.dxcar.launcher.system.RootHelperBridge.setDockPinnedRightShift(context, false)
                                com.dxcar.launcher.system.RootHelperBridge.parkPlanBWindows(
                                    context = context,
                                    screenWidth = dm.widthPixels,
                                    screenHeight = dm.heightPixels
                                )
                                planBConfig = com.dxcar.launcher.system.RootHelperBridge.readPlanBConfig(context)
                            }
                            com.dxcar.launcher.DxCarApp.instance?.preferences?.updateSync {
                                it.copy(
                                    homeExperimentMode = targetMode,
                                    launchAppsAvoidDockFreeform = false,
                                    overlayAutoCollapseEnabled = true
                                )
                            } ?: onUpdateConfig {
                                it.copy(
                                    homeExperimentMode = targetMode,
                                    launchAppsAvoidDockFreeform = false,
                                    overlayAutoCollapseEnabled = true
                                )
                            }
                            com.dxcar.launcher.MainActivity.restartLauncherCleanly(context)
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            HomeExperimentMode.visibleEntries.forEach { mode ->
                                val selected = config.homeExperimentMode == mode
                                val effectiveAvailable = if (mode == HomeExperimentMode.PLAN_D_ROOT_RESERVED) hasRootHelper else mode.isAvailable
                                Surface(
                                    color = when {
                                        !effectiveAvailable -> colors.cardSurfaceElevated.copy(alpha = 0.5f)
                                        selected -> colors.accent.copy(alpha = 0.18f)
                                        else -> colors.cardSurfaceElevated
                                    },
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .border(
                                            width = if (selected && effectiveAvailable) 1.5.dp else 1.dp,
                                            color = if (selected && effectiveAvailable) colors.accent else colors.cardBorder,
                                            shape = RoundedCornerShape(16.dp)
                                        )
                                        .clickable(enabled = effectiveAvailable) {
                                            switchExperimentModeAndSyncDock(mode)
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
                                                    color = if (effectiveAvailable) colors.primaryText else colors.secondaryText,
                                                    fontSize = 14.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                if (mode == HomeExperimentMode.PLAN_D_ROOT_RESERVED) {
                                                    Surface(
                                                        color = if (hasRootHelper) colors.accent.copy(alpha = 0.22f) else colors.cardBorder.copy(alpha = 0.6f),
                                                        shape = RoundedCornerShape(8.dp)
                                                    ) {
                                                        Row(
                                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                            verticalAlignment = Alignment.CenterVertically
                                                        ) {
                                                            if (!hasRootHelper) {
                                                                Icon(Icons.Rounded.Lock, contentDescription = null, tint = colors.secondaryText, modifier = Modifier.size(11.dp))
                                                                Spacer(Modifier.width(3.dp))
                                                            }
                                                            Text(
                                                                text = if (hasRootHelper) str("已偵測擴充 App", "已检测扩展 App", "Helper Ready", "拡張検出済")
                                                                else str("需安裝擴充 APK", "需安装扩展 APK", "Needs Helper APK", "拡張APK必要"),
                                                                color = if (hasRootHelper) colors.accent else colors.secondaryText,
                                                                fontSize = 10.sp,
                                                                fontWeight = FontWeight.SemiBold
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                            Text(DxCarI18n.experimentDesc(mode), color = colors.secondaryText, fontSize = 11.sp)
                                            if (mode == HomeExperimentMode.PLAN_D_ROOT_RESERVED) {
                                                Spacer(Modifier.height(6.dp))
                                                OutlinedButton(
                                                    onClick = { com.dxcar.launcher.system.RootHelperBridge.openHelperOrGithub(context) },
                                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                                    modifier = Modifier.height(30.dp)
                                                ) {
                                                    Text(
                                                        text = if (hasRootHelper) {
                                                            val ratioPct = ((planBConfig?.ui2SplitRatio ?: 0.50f) * 100).roundToInt()
                                                            str(
                                                                "開啟擴充輔助 App 客製化方案 B (UI2 $ratioPct:${100 - ratioPct})",
                                                                "打开扩展辅助 App 定制方案 B (UI2 $ratioPct:${100 - ratioPct})",
                                                                "Open Helper App for Plan B (UI2 $ratioPct:${100 - ratioPct})",
                                                                "拡張アプリでプランB設定"
                                                            )
                                                        } else {
                                                            str("前往 GitHub 下載擴充輔助 APK", "前往 GitHub 下载扩展辅助 APK", "Download Helper APK on GitHub", "GitHubで拡張APK取得")
                                                        },
                                                        fontSize = 11.sp
                                                    )
                                                }
                                                if (hasRootHelper && selected) {
                                                    Spacer(Modifier.height(6.dp))
                                                    val permDockOn = (planBConfig?.dockPinnedRightShift == true) || config.launchAppsAvoidDockFreeform
                                                    SettingsToggleRow(
                                                        title = str(
                                                            "方案 B 永久顯示 Dock（所有 App 與全螢幕皆自動挪位讓給 Dock）",
                                                            "方案 B 永久显示 Dock（所有 App 与全屏皆自动挪位让给 Dock）",
                                                            "Plan B Permanent Dock (Shift All Split & Fullscreen Apps for Dock)",
                                                            "プランB 常時ドック表示（全アプリ＆全画面表示をドック分シフト）"
                                                        ),
                                                        subtitle = str(
                                                            "與擴充輔助 App 雙向同步；改選方案 A 時將自動預設取消",
                                                            "与扩展辅助 App 双向同步；改选方案 A 时将自动默认取消",
                                                            "Synced with Helper App; automatically turned off when switching back to Plan A",
                                                            "拡張アプリと双方向同期。プラン A 選択時は自動でオフに戻ります"
                                                        ),
                                                        checked = permDockOn,
                                                        onCheckedChange = { enabled ->
                                                            com.dxcar.launcher.system.RootHelperBridge.setDockPinnedRightShift(context, enabled)
                                                            planBConfig = com.dxcar.launcher.system.RootHelperBridge.readPlanBConfig(context)
                                                            onUpdateConfig {
                                                                it.copy(
                                                                    launchAppsAvoidDockFreeform = enabled,
                                                                    overlayAutoCollapseEnabled = !enabled
                                                                )
                                                            }
                                                        }
                                                    )
                                                }
                                            }
                                        }
                                        if (effectiveAvailable) {
                                            Spacer(Modifier.width(8.dp))
                                            Switch(
                                                checked = selected,
                                                onCheckedChange = { switchExperimentModeAndSyncDock(mode) },
                                                colors = SwitchDefaults.colors(checkedThumbColor = colors.onAccent, checkedTrackColor = colors.accent)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Plan A Second UI (Map + YouTube) Toggle & Fullscreen Nav Edge Media Handle Toggle
                        if (config.homeExperimentMode == HomeExperimentMode.PLAN_A_MULTI_LAYER) {
                            Surface(
                                color = colors.cardSurfaceElevated,
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.dp, colors.cardBorder, RoundedCornerShape(16.dp))
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    SettingsToggleRow(
                                        title = str(
                                            "啟用方案 A 第二介面（1:1 內建地圖 ＋ YouTube 視窗）",
                                            "启用方案 A 第二界面（1:1 内置地图 ＋ YouTube 窗口）",
                                            "Enable Plan A Page 2 (1:1 Map + YouTube Window)",
                                            "プラン A 第2画面（1:1 地図＋YouTube）を有効にする"
                                        ),
                                        subtitle = str(
                                            "關閉後，啟動器中的第二介面與內嵌 YouTube App 將完全隱藏，僅保留第 0 與第 1 介面",
                                            "关闭后，启动器中的第二界面与内嵌 YouTube App 将完全隐藏，仅保留第 0 与第 1 界面",
                                            "When turned OFF, Page 2 and embedded YouTube inside the launcher are completely hidden",
                                            "オフにすると、ランチャー内の第2画面と内蔵 YouTube は完全に非表示になります"
                                        ),
                                        checked = config.planAPage2Enabled,
                                        onCheckedChange = { enabled ->
                                            onUpdateConfig { it.copy(planAPage2Enabled = enabled) }
                                        }
                                    )
                                    SettingsToggleRow(
                                        title = str(
                                            "導航全螢幕時右側隱藏式音樂控盤（點開 1.5 秒自動收回）",
                                            "导航全屏时右侧隐藏式音乐控盘（点开 1.5 秒自动收回）",
                                            "Fullscreen Nav Edge Media Handle (Auto-retract 1.5s)",
                                            "全画面ナビ時の右端メディアタブ（1.5秒で自動収納）"
                                        ),
                                        subtitle = str(
                                            "於 Google 地圖或其他導航 App 全螢幕時，像 Dock 一樣收合在右側上方不擋視野；點開控制後 1.5 秒自動隱藏",
                                            "在 Google 地图或其他导航 App 全屏时，像 Dock 一样收合在右侧上方不挡视野；点开控制后 1.5 秒自动隐藏",
                                            "Hides neatly on the upper-right edge during fullscreen Google Maps / nav apps and auto-hides 1.5s after tapping",
                                            "Google マップ等の全画面ナビ中に右上端へ収納され、操作後1.5秒で自動的に閉じます"
                                        ),
                                        checked = config.navFullscreenMediaHandleEnabled,
                                        onCheckedChange = { enabled ->
                                            onUpdateConfig { it.copy(navFullscreenMediaHandleEnabled = enabled) }
                                        }
                                    )
                                }
                            }
                        }

                        // Car Trigger Mode for Locations Auto-Shared from Phone Google Maps
                        run {
                            val otherMaps = remember { com.dxcar.launcher.map.SharedLocationResolver.findInstalledOtherNavApps(context) }
                            Surface(
                                color = colors.cardSurfaceElevated,
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.dp, colors.cardBorder, RoundedCornerShape(16.dp))
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = str(
                                            "📍 手機 Google 地圖分享地址 → 車機觸發導航方式",
                                            "📍 手机 Google 地图分享地址 → 车机触发导航方式",
                                            "📍 Phone Shared Address -> Car Nav App Trigger",
                                            "📍 スマホ共有住所の車載ナビ起動方法"
                                        ),
                                        color = colors.primaryText,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = str(
                                            "手機從 Google 地圖點「分享 → DX-Car」會自動丟地址至車機；可選擇跳通知挑選地圖，或直接自動啟動指定導航 App",
                                            "手机从 Google 地图点「分享 → DX-Car」会自动发送地址至车机；可选择弹通知挑选地图，或直接自动启动指定导航 App",
                                            "Sharing from Google Maps auto-sends the address to Car; choose to ask via banner or auto-launch your preferred nav app",
                                            "スマホの Google マップから共有すると自動で車載機へ送信され、指定のナビアプリを起動します"
                                        ),
                                        color = colors.secondaryText,
                                        fontSize = 11.sp
                                    )
                                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        SelectablePill(
                                            text = str("每次跳通知選擇（預設）", "每次弹通知选择（默认）", "Ask via Banner", "毎回通知で選択"),
                                            selected = config.sharedLocationNavTrigger == "ASK",
                                            onClick = { onUpdateConfig { it.copy(sharedLocationNavTrigger = "ASK") } }
                                        )
                                        SelectablePill(
                                            text = str("自動開內建地圖", "自动开内置地图", "Auto: Built-in Map", "自動：内蔵マップ"),
                                            selected = config.sharedLocationNavTrigger == "BUILTIN",
                                            onClick = { onUpdateConfig { it.copy(sharedLocationNavTrigger = "BUILTIN") } }
                                        )
                                        SelectablePill(
                                            text = str("自動開 Google 地圖", "自动开 Google 地图", "Auto: Google Maps", "自動：Google マップ"),
                                            selected = config.sharedLocationNavTrigger == "GOOGLE_MAPS",
                                            onClick = { onUpdateConfig { it.copy(sharedLocationNavTrigger = "GOOGLE_MAPS") } }
                                        )
                                        otherMaps.forEach { m ->
                                            val key = "OTHER:${m.packageName}"
                                            SelectablePill(
                                                text = str("自動開 ${m.displayName}", "自动开 ${m.displayName}", "Auto: ${m.displayName}", "自動：${m.displayName}"),
                                                selected = config.sharedLocationNavTrigger == key,
                                                onClick = { onUpdateConfig { it.copy(sharedLocationNavTrigger = key) } }
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
                        val detectedLocalTitle = DxCarI18n.offlineDetectedLocalTitle(detectedLoc)
                        val detectedCountryTitle = DxCarI18n.offlineDetectedCountryTitle(detectedLoc)
                        Surface(
                            color = colors.cardSurfaceElevated,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    text = str(
                                        "方案 A 離線地圖 · 所在區域／所在國家全下載（已快取 ${config.offlineRegions.size} 區）",
                                        "方案 A 离线地图 · 所在区域／所在国家全下载（已缓存 ${config.offlineRegions.size} 区）",
                                        "Plan A Offline Maps · Local & Country Download (${config.offlineRegions.size} cached)",
                                        "プラン A オフライン地図 · 地域／国一括取得（${config.offlineRegions.size} 件保存済）"
                                    ),
                                    color = colors.primaryText,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    OfflineUpdatePolicy.entries.forEach { pol ->
                                        SelectablePill(
                                            text = DxCarI18n.offlinePolicyLabel(pol),
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
                                            Text(str("取消", "取消", "Cancel", "キャンセル"), fontSize = 11.sp)
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
                                        Text(
                                            str(
                                                "📍 下載所在區域（$detectedLocalTitle）",
                                                "📍 下载所在区域（$detectedLocalTitle）",
                                                "📍 Download Local Area ($detectedLocalTitle)",
                                                "📍 現在地エリアを取得（$detectedLocalTitle）"
                                            ),
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    Button(
                                        onClick = { onDownloadOfflineRegion(OfflineMapManager.TOKEN_AUTO_ENTIRE_COUNTRY) },
                                        enabled = offlineState.downloadingRegionId == null,
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF22C55E), contentColor = Color.White),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Text(
                                            str(
                                                "🌏 全下載所在國家（$detectedCountryTitle）",
                                                "🌏 全下载所在国家（$detectedCountryTitle）",
                                                "🌏 Download Entire Country ($detectedCountryTitle)",
                                                "🌏 現在の国を一括取得（$detectedCountryTitle）"
                                            ),
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                                // Full Regional List (no .take(4) truncation!)
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    offlinePresets.forEach { preset ->
                                        val downloaded = config.offlineRegions.any { it.regionId == preset.id }
                                        val presetTitle = DxCarI18n.offlinePresetTitle(preset.id, preset.titleZh)
                                        OutlinedButton(
                                            onClick = { onDownloadOfflineRegion(preset.id) },
                                            enabled = offlineState.downloadingRegionId == null,
                                            shape = RoundedCornerShape(12.dp)
                                        ) {
                                            Text(
                                                text = if (downloaded) {
                                                    str("✓ 更新 $presetTitle", "✓ 更新 $presetTitle", "✓ Update $presetTitle", "✓ 更新 $presetTitle")
                                                } else {
                                                    str("⬇ 下載 $presetTitle", "⬇ 下载 $presetTitle", "⬇ Download $presetTitle", "⬇ 取得 $presetTitle")
                                                },
                                                fontSize = 11.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        SettingsToggleRow(
                            title = str(
                                "媒體控制器下方整合顯示 6 格快捷 App",
                                "媒体控制器下方整合显示 6 格快捷 App",
                                "Show 6 Quick App Shortcuts Below Media Player",
                                "メディアプレーヤー下に 6 枠ショートカットを表示"
                            ),
                            subtitle = str(
                                "關閉則將右側空間全數留給上方當地天氣與超大專輯封面",
                                "关闭则将右侧空间全数留给上方当地天气与超大专辑封面",
                                "Turn off to dedicate right pane to local weather & large album art",
                                "OFF にすると右側を天気と大型アルバムアートに最大化します"
                            ),
                            checked = config.showSixShortcutsInMediaPanel,
                            onCheckedChange = { checked -> onUpdateConfig { it.copy(showSixShortcutsInMediaPanel = checked) } }
                        )
                    }
                }

                val cardStyleAndMap: @Composable (Modifier) -> Unit = { mod ->
                    SettingsSectionCard(
                        icon = Icons.Rounded.Palette,
                        title = str(
                            "介面風格、日夜排程與分割比例",
                            "界面风格、日夜排程与分屏比例",
                            "UI Style, Day/Night Schedule & Split Ratio",
                            "外観スタイル・昼夜スケジュール・分割比率"
                        ),
                        modifier = mod
                    ) {
                        Text(str("介面風格預設", "界面风格预设", "UI Style Preset", "外観スタイルプリセット"), color = colors.primaryText, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            LauncherStylePreset.entries.forEach { preset ->
                                SelectablePill(
                                    text = DxCarI18n.stylePresetLabel(preset),
                                    selected = config.stylePreset == preset,
                                    onClick = { onUpdateConfig { it.copy(stylePreset = preset) } }
                                )
                            }
                        }

                        Text(str("強調色主題", "强调色主题", "Accent Color Theme", "アクセントカラー"), color = colors.primaryText, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
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

                        Text(str("白天／夜晚切換模式", "白天／夜晚切换模式", "Day / Night Mode", "昼間 / 夜間モード"), color = colors.primaryText, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
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
                                    label = str("白天開始", "白天开始", "Day Starts", "昼間開始"),
                                    hour = config.dayStartHour,
                                    minute = config.dayStartMinute,
                                    onDeltaMinutes = { delta ->
                                        val total = ((config.dayStartHour * 60 + config.dayStartMinute + delta) % 1440 + 1440) % 1440
                                        onUpdateConfig { it.copy(dayStartHour = total / 60, dayStartMinute = total % 60) }
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                                TimeStepperCard(
                                    label = str("夜晚開始", "夜晚开始", "Night Starts", "夜間開始"),
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
                                text = str(
                                    "今日 GPS 日出 ${solarPreview.formatSunrise()} · 日落 ${solarPreview.formatSunset()}",
                                    "今日 GPS 日出 ${solarPreview.formatSunrise()} · 日落 ${solarPreview.formatSunset()}",
                                    "Today's GPS Sunrise ${solarPreview.formatSunrise()} · Sunset ${solarPreview.formatSunset()}",
                                    "本日の GPS 日の出 ${solarPreview.formatSunrise()} · 日の入り ${solarPreview.formatSunset()}"
                                ),
                                color = colors.accent,
                                fontSize = 12.sp
                            )
                        }

                        // PRESET CAR SCREEN BRIGHTNESS FOR DAY / NIGHT MODE
                        val canWriteBrightness = remember(permissionHealth) {
                            android.provider.Settings.System.canWrite(context)
                        }
                        Surface(
                            color = colors.cardSurfaceElevated,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, colors.cardBorder, RoundedCornerShape(14.dp))
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                SettingsToggleRow(
                                    title = str(
                                        "依日夜模式自動調整車機螢幕亮度",
                                        "依日夜模式自动调整车机屏幕亮度",
                                        "Auto-Apply Preset Screen Brightness",
                                        "昼夜モード連動で画面輝度を自動調整"
                                    ),
                                    subtitle = str(
                                        "切換白天／夜晚模式時，自動套用下方預設車機螢幕亮度",
                                        "切换白天／夜晚模式时，自动套用下方预设车机屏幕亮度",
                                        "Automatically applies your preset Day/Night screen brightness on mode switch",
                                        "昼／夜モード切り替え時に下の既定輝度を自動適用します"
                                    ),
                                    checked = config.dayNightBrightnessEnabled,
                                    onCheckedChange = { checked ->
                                        onUpdateConfig { it.copy(dayNightBrightnessEnabled = checked) }
                                    }
                                )
                                if (config.dayNightBrightnessEnabled && !canWriteBrightness) {
                                    OutlinedButton(
                                        onClick = {
                                            runCatching {
                                                context.startActivity(
                                                    Intent(
                                                        android.provider.Settings.ACTION_MANAGE_WRITE_SETTINGS,
                                                        Uri.parse("package:${context.packageName}")
                                                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                )
                                            }
                                        },
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Text(
                                            str(
                                                "點此授權「修改系統設定」以調整車機亮度",
                                                "点此授权「修改系统设置」以调整车机亮度",
                                                "Grant Write Settings to Control Brightness",
                                                "輝度変更のため「システム設定の変更」を許可"
                                            ),
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = str(
                                            "白天預設亮度：${config.dayBrightnessPercent}%",
                                            "白天默认亮度：${config.dayBrightnessPercent}%",
                                            "Day Brightness: ${config.dayBrightnessPercent}%",
                                            "昼の既定輝度：${config.dayBrightnessPercent}%"
                                        ),
                                        color = colors.primaryText,
                                        fontSize = 12.sp,
                                        modifier = Modifier.width(150.dp)
                                    )
                                    Slider(
                                        value = config.dayBrightnessPercent.toFloat(),
                                        onValueChange = { v ->
                                            val pct = v.roundToInt().coerceIn(10, 100)
                                            onUpdateConfig {
                                                it.copy(
                                                    dayNightBrightnessEnabled = true,
                                                    dayBrightnessPercent = pct
                                                )
                                            }
                                        },
                                        valueRange = 10f..100f,
                                        colors = SliderDefaults.colors(thumbColor = colors.accent, activeTrackColor = colors.accent),
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = str(
                                            "夜晚預設亮度：${config.nightBrightnessPercent}%",
                                            "夜晚默认亮度：${config.nightBrightnessPercent}%",
                                            "Night Brightness: ${config.nightBrightnessPercent}%",
                                            "夜の既定輝度：${config.nightBrightnessPercent}%"
                                        ),
                                        color = colors.primaryText,
                                        fontSize = 12.sp,
                                        modifier = Modifier.width(150.dp)
                                    )
                                    Slider(
                                        value = config.nightBrightnessPercent.toFloat(),
                                        onValueChange = { v ->
                                            val pct = v.roundToInt().coerceIn(10, 100)
                                            onUpdateConfig {
                                                it.copy(
                                                    dayNightBrightnessEnabled = true,
                                                    nightBrightnessPercent = pct
                                                )
                                            }
                                        },
                                        valueRange = 10f..100f,
                                        colors = SliderDefaults.colors(thumbColor = colors.accent, activeTrackColor = colors.accent),
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }

                        Text(str("內建地圖圖層與路況", "内置地图图层与路况", "Map Layers & Traffic", "地図レイヤー・交通状況"), color = colors.primaryText, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            MapEngineStyle.visibleEntries.forEach { styleItem ->
                                SelectablePill(
                                    text = DxCarI18n.mapEngineLabel(styleItem),
                                    selected = config.mapEngineStyle == styleItem,
                                    onClick = { onUpdateConfig { it.copy(mapEngineStyle = styleItem) } }
                                )
                            }
                        }
                        SettingsToggleRow(
                            title = str("疊加 Google 即時路況圖層", "叠加 Google 实时路况图层", "Google Live Traffic Layer", "Google 交通状況レイヤー"),
                            subtitle = str(
                                "顯示紅黃綠即時車流；可搭配「開啟分屏設定」測試 AOSP 原生可拖動分屏",
                                "显示红黄绿实时车流；可搭配「分屏设置」测试 AOSP 原生可拖动分屏",
                                "Shows live traffic flow; pair with Split Setup to test draggable AOSP split-screen",
                                "リアルタイム渋滞情報を表示（画面分割設定と併用可能）"
                            ),
                            checked = config.mapTrafficEnabled,
                            onCheckedChange = { checked -> onUpdateConfig { it.copy(mapTrafficEnabled = checked) } }
                        )
                        // CUSTOM DEFAULT WEATHER APP PICKER (Default: Google App Froggy Weather)
                        var showWeatherAppPicker by remember { mutableStateOf(false) }
                        val selectedWeatherLabel = remember(config.weatherAppPackage, installedApps) {
                            if (config.weatherAppPackage.isBlank() || config.weatherAppPackage == "com.google.android.googlequicksearchbox") {
                                DxCarI18n.tr(
                                    "Google App（預設青蛙天氣）",
                                    "Google App（默认青蛙天气）",
                                    "Google App (Default Froggy Weather)",
                                    "Google アプリ（標準カエル天気）"
                                )
                            } else {
                                installedApps.firstOrNull { it.packageName == config.weatherAppPackage }?.label
                                    ?: config.weatherAppPackage
                            }
                        }
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
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                        Text(
                                            text = str(
                                                "第一介面天氣預設開啟 App",
                                                "第一界面天气默认开启 App",
                                                "Page 1 Default Weather App",
                                                "ページ 1 天気タップ時の既定アプリ"
                                            ),
                                            color = colors.primaryText,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = str(
                                                "目前設定：$selectedWeatherLabel",
                                                "当前设置：$selectedWeatherLabel",
                                                "Current: $selectedWeatherLabel",
                                                "現在の設定：$selectedWeatherLabel"
                                            ),
                                            color = colors.accent,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        if (config.weatherAppPackage.isNotBlank()) {
                                            OutlinedButton(
                                                onClick = { onUpdateConfig { it.copy(weatherAppPackage = "") } },
                                                shape = RoundedCornerShape(12.dp)
                                            ) {
                                                Text(str("預設 Google", "默认 Google", "Default Google", "Googleに戻す"), fontSize = 11.sp)
                                            }
                                        }
                                        Button(
                                            onClick = { showWeatherAppPicker = !showWeatherAppPicker },
                                            colors = ButtonDefaults.buttonColors(containerColor = colors.accent, contentColor = colors.onAccent),
                                            shape = RoundedCornerShape(12.dp)
                                        ) {
                                            Text(
                                                if (showWeatherAppPicker) str("收合", "收起", "Close", "閉じる")
                                                else str("更改 App", "更改 App", "Change App", "変更"),
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                                if (showWeatherAppPicker) {
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        SelectablePill(
                                            text = str("Google App（預設）", "Google App（默认）", "Google App (Default)", "Google アプリ（既定）"),
                                            selected = config.weatherAppPackage.isBlank() || config.weatherAppPackage == "com.google.android.googlequicksearchbox",
                                            onClick = {
                                                onUpdateConfig { it.copy(weatherAppPackage = "") }
                                                showWeatherAppPicker = false
                                            }
                                        )
                                        installedApps
                                            .sortedByDescending {
                                                val low = "${it.label} ${it.packageName}".lowercase()
                                                low.contains("weather") || low.contains("天氣") || low.contains("天气") || low.contains("天気")
                                            }
                                            .take(24)
                                            .forEach { appItem ->
                                                SelectablePill(
                                                    text = appItem.label,
                                                    selected = config.weatherAppPackage == appItem.packageName,
                                                    onClick = {
                                                        onUpdateConfig { it.copy(weatherAppPackage = appItem.packageName) }
                                                        showWeatherAppPicker = false
                                                    }
                                                )
                                            }
                                    }
                                }
                            }
                        }
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
                                            Text(str("60fps / 60Hz 高流暢度顯示模式", "60fps / 60Hz 高流畅度显示模式", "60fps / 60Hz High Refresh Mode", "60fps / 60Hz 高フレームレート表示"), color = colors.primaryText, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                            Text(
                                                text = if (refreshCap.supports60Hz) {
                                                    str(
                                                        "✓ 可用（支援 ${refreshCap.maxSupportedHz}Hz · 目前 ${refreshCap.currentHz}Hz）",
                                                        "✓ 可用（支持 ${refreshCap.maxSupportedHz}Hz · 当前 ${refreshCap.currentHz}Hz）",
                                                        "✓ Supported (${refreshCap.maxSupportedHz}Hz max · ${refreshCap.currentHz}Hz now)",
                                                        "✓ 利用可能（最大 ${refreshCap.maxSupportedHz}Hz · 現在 ${refreshCap.currentHz}Hz）"
                                                    )
                                                } else {
                                                    str(
                                                        "⚠️ 目前 ${refreshCap.currentHz}Hz（依車機盒韌體）",
                                                        "⚠️ 当前 ${refreshCap.currentHz}Hz（依车机盒固件）",
                                                        "⚠️ Currently ${refreshCap.currentHz}Hz (OEM dependent)",
                                                        "⚠️ 現在 ${refreshCap.currentHz}Hz（端末依存）"
                                                    )
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
                                            "检测到此设备屏幕支持 ${refreshCap.maxSupportedHz}Hz，开启即可请求 60fps 高流畅度渲染！",
                                            "Display supports ${refreshCap.maxSupportedHz}Hz; enable to request 60fps smooth rendering.",
                                            "${refreshCap.maxSupportedHz}Hz 対応ディスプレイを検出。ON で 60fps 描画を要求します。"
                                        )
                                    } else {
                                        str(
                                            "未偵測到原生 60Hz（系統回報 ${refreshCap.currentHz}Hz）；開關仍可開啟以請求最高幀率，亦可進原廠設定檢查。",
                                            "未检测到原生 60Hz（系统回报 ${refreshCap.currentHz}Hz）；开关仍可开启以请求最高帧率，亦可进原厂设置检查。",
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
                                    Text(str("開啟車機顯示／原廠設定檢查 60fps 選項", "开启车机显示／原厂设置检查 60fps 选项", "Open Car / OEM Display Settings", "車載機・純正ディスプレイ設定を開く"), fontSize = 11.sp)
                                }
                            }
                        }

                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedButton(onClick = onLaunchRealMapsSplitScreen, shape = RoundedCornerShape(12.dp)) {
                                Text(str("測試 AOSP 分割G地圖", "测试 AOSP 分屏G地图", "Test AOSP Split Maps", "AOSP 分割マップテスト"), fontSize = 12.sp)
                            }
                            Button(
                                onClick = onOpenSplitShortcutCreator,
                                colors = ButtonDefaults.buttonColors(containerColor = colors.accent, contentColor = colors.onAccent),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(str("分屏設定／讀取原車分屏", "分屏设置／读取原车分屏", "Split Setup / Import Stock", "画面分割設定・純正取込"), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = str("首頁比例：", "首页比例：", "Home Ratio: ", "ホーム比率：") +
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
                    categoryTitle = str(
                        "分類二 · 常駐懸浮 Dock（左／右／上方／跨啟動器）與預設桌面",
                        "分类二 · 常驻悬浮 Dock（左／右／上方／跨启动器）与默认桌面",
                        "Category 2 · Persistent Floating Dock & Default Home",
                        "カテゴリ 2 · 常駐フローティングドック＆標準ホーム設定"
                    ),
                    hint = str(
                        "左駕預設左側、右駕預設右側，亦可改放「上方」；支援懸浮於其他啟動器上，並內建 3D 俯瞰類滑鼠指標",
                        "左舵默认左侧、右舵默认右侧，亦可改放「上方」；支持悬浮于其他启动器上，并内置 3D 俯瞰类鼠标指针",
                        "LHD defaults left, RHD right, or Top Bar; floats over other launchers with built-in 3D mouse pointer",
                        "左/右ハンドル連動や上部配置に対応。他ランチャー上の常駐表示や 3D マウス機能を搭載"
                    )
                )

                val cardDockAnd3D: @Composable (Modifier) -> Unit = { mod ->
                    SettingsSectionCard(
                        icon = Icons.Rounded.DisplaySettings,
                        title = str(
                            "Dock 位置（左／右／上方）與跨啟動器常駐",
                            "Dock 位置（左／右／上方）与跨启动器常驻",
                            "Dock Position (Left / Right / Top) & Persistent Overlay",
                            "ドック位置（左／右／上）＆他ランチャー常駐"
                        ),
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
                                            text = str(
                                                "永久懸浮提醒：請關閉原車啟動器自帶的 Dock 與懸浮按鈕",
                                                "永久悬浮提醒：请关闭原车启动器自带的 Dock 与悬浮按钮",
                                                "Overlay Tip: Disable Stock Launcher Dock & Assistive Ball",
                                                "常駐表示のヒント：純正ランチャーのドックやアシストボールをオフにしてください"
                                            ),
                                            color = colors.primaryText,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    Text(
                                        text = str(
                                            "當啟用本啟動器 Dock 永久懸浮（或懸浮在其他啟動器上方）時，建議先進入車機盒設定關閉原車自帶的側邊欄與懸浮小白點，避免雙重遮擋。",
                                            "当启用本启动器 Dock 永久悬浮（或悬浮在其他启动器上方）时，建议先进入车机盒设置关闭原车自带的侧边栏与悬浮小白点，避免双重遮挡。",
                                            "When Persistent Floating Dock is enabled across apps/launchers, disable the AI Box's stock sidebar and floating ball in vendor settings to avoid overlap.",
                                            "常駐ドック（他ランチャー上を含む）を有効にする場合、重なりを防ぐため車載ボックス設定で純正サイドバーやフローティングボールをオフにしてください。"
                                        ),
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
                                            Text(str("前往車機盒設定頁面", "前往车机盒设置页面", "Open AI Box Vendor Settings", "車載ボックス設定を開く"), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        }
                                        TutorialPillButton(
                                            label = str("教學", "教程", "Guide", "ガイド"),
                                            onClick = { onActiveTutorialChange(CarSettingsTutorialTopic.STOCK_DOCK_CONFLICT) }
                                        )
                                    }
                                }
                            }
                        }

                        Text(str("駕駛座位置（左駕預設靠左 · 右駕預設靠右）", "驾驶座位置（左舵默认靠左 · 右舵默认靠右）", "Driver Side (LHD Left · RHD Right)", "運転席側（左ハンドル・右ハンドル）"), color = colors.primaryText, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SelectablePill(
                                text = str("左駕模式（預設左側）", "左舵模式（默认左侧）", "LHD (Left Dock)", "左ハンドル（左ドック）"),
                                selected = !config.rightHandDrive,
                                onClick = { onUpdateConfig { it.copy(rightHandDrive = false) } }
                            )
                            SelectablePill(
                                text = str("右駕模式（預設右側）", "右舵模式（默认右侧）", "RHD (Right Dock)", "右ハンドル（右ドック）"),
                                selected = config.rightHandDrive,
                                onClick = { onUpdateConfig { it.copy(rightHandDrive = true) } }
                            )
                        }

                        Text(str("Dock 擺放位置選擇（支援放置於上方！）", "Dock 摆放位置选择（支持放置于上方！）", "Dock Placement (Supports Top Bar!)", "ドック配置（上部バー対応！）"), color = colors.primaryText, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
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
                            title = str(
                                "常駐懸浮 Dock（全螢幕 App 上方常駐 · 手機端預設停用）",
                                "常驻悬浮 Dock（全屏 App 上方常驻 · 手机端默认停用）",
                                "Persistent Floating Dock (Over Fullscreen Apps · Off on Phones)",
                                "常駐フローティングドック（全画面アプリ上に表示・スマホでは標準オフ）"
                            ),
                            subtitle = str(
                                "開啟後於 Google 地圖／YouTube 全螢幕時保留常駐列（含 3D 俯瞰類滑鼠指標與返回／多工鍵）",
                                "开启后在 Google 地图／YouTube 全屏时保留常驻栏（含 3D 俯瞰类鼠标指针与返回／多任务键）",
                                "Keeps Dock accessible over fullscreen Google Maps/YouTube (includes 3D pointer, Back & Recents)",
                                "Google マップや YouTube の全画面表示中もドック（3D ポインター・戻る・履歴）を維持します"
                            ),
                            checked = config.persistentOverlayDockEnabled,
                            onCheckedChange = { checked -> onUpdateConfig { it.copy(persistentOverlayDockEnabled = checked) } }
                        )

                        if (config.persistentOverlayDockEnabled) {
                            SettingsToggleRow(
                                title = str(
                                    "同時懸浮在「其他車機啟動器」桌面上",
                                    "同时悬浮在「其他车机启动器」桌面上",
                                    "Also Float Over Other Car Launchers",
                                    "他の車載ランチャーのホーム画面上にも常駐表示"
                                ),
                                subtitle = str(
                                    "切換回原廠啟動器或其他桌面時，依然保留 DX-Car 常駐懸浮 Dock",
                                    "切换回原厂启动器或其他桌面时，依然保留 DX-Car 常驻悬浮 Dock",
                                    "Retains DX-Car Floating Dock even when switching back to the stock launcher",
                                    "純正ランチャーや他のホーム画面に切り替えても DX-Car ドックを維持します"
                                ),
                                checked = config.overlayDockOnOtherLaunchers,
                                onCheckedChange = { checked -> onUpdateConfig { it.copy(overlayDockOnOtherLaunchers = checked) } }
                            )

                            SettingsToggleRow(
                                title = str(
                                    "自動收合為透明邊緣拉條（關閉則永久展開懸浮）",
                                    "自动收起为透明边缘拉条（关闭则永久展开悬浮）",
                                    "Auto-Collapse to Edge Handle (Turn Off to Keep Expanded)",
                                    "端の透明バーへ自動収納（OFF で常時展開）"
                                ),
                                subtitle = str(
                                    "開啟時縮成邊緣拉條，輕點或往內滑展開；關閉則保持完整 Dock 永久懸浮",
                                    "开启时缩成边缘拉条，轻点或向内滑展开；关闭则保持完整 Dock 永久悬浮",
                                    "Collapses into a slim edge handle (tap/swipe inward to expand); turn off to keep full Dock visible",
                                    "ON で画面端のバーに縮小（タップ/内側スワイプで展開）。OFF でフルドックを常時表示"
                                ),
                                checked = config.overlayAutoCollapseEnabled,
                                onCheckedChange = { checked -> onUpdateConfig { it.copy(overlayAutoCollapseEnabled = checked) } }
                            )

                            if (config.overlayAutoCollapseEnabled) {
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    listOf(
                                        1500L to str("1.5 秒收回（預設）", "1.5 秒收回（默认）", "1.5s Auto-Hide (Default)", "1.5秒で収納（標準）"),
                                        3000L to str("3 秒收回", "3 秒收回", "3s Auto-Hide", "3秒で収納"),
                                        5000L to str("5 秒收回", "5 秒收回", "5s Auto-Hide", "5秒で収納"),
                                        0L to str("手動收回", "手动收回", "Manual Hide", "手動収納")
                                    ).forEach { (ms, label) ->
                                        SelectablePill(
                                            text = label,
                                            selected = config.overlayAutoRetractMs == ms,
                                            onClick = { onUpdateConfig { it.copy(overlayAutoRetractMs = ms) } }
                                        )
                                    }
                                }
                                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        str(
                                            "拉條厚度：${config.overlayHandleThicknessDp} dp",
                                            "拉条厚度：${config.overlayHandleThicknessDp} dp",
                                            "Handle Width: ${config.overlayHandleThicknessDp} dp",
                                            "バーの太さ：${config.overlayHandleThicknessDp} dp"
                                        ),
                                        color = colors.primaryText,
                                        fontSize = 12.sp,
                                        modifier = Modifier.width(145.dp)
                                    )
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

                        val resolvedDockLabel = DxCarI18n.resolvedDockPlacementLabel(layoutSpec.dockPlacement)
                        SettingsToggleRow(
                            title = str("首頁顯示本啟動器 Dock", "首页显示本启动器 Dock", "Show Dock on Home Screen", "ホーム画面にドックを表示"),
                            subtitle = str(
                                "目前判定：$resolvedDockLabel（${layoutSpec.metrics.widthPx}×${layoutSpec.metrics.heightPx} · ${layoutSpec.metrics.densityDpi}dpi）",
                                "当前判定：$resolvedDockLabel（${layoutSpec.metrics.widthPx}×${layoutSpec.metrics.heightPx} · ${layoutSpec.metrics.densityDpi}dpi）",
                                "Detected: $resolvedDockLabel (${layoutSpec.metrics.widthPx}×${layoutSpec.metrics.heightPx} · ${layoutSpec.metrics.densityDpi}dpi)",
                                "現在の判定：$resolvedDockLabel（${layoutSpec.metrics.widthPx}×${layoutSpec.metrics.heightPx} · ${layoutSpec.metrics.densityDpi}dpi）"
                            ),
                            checked = config.showHomeDock,
                            onCheckedChange = { checked -> onUpdateConfig { it.copy(showHomeDock = checked) } }
                        )

                        SettingsToggleRow(
                            title = str(
                                "顯示切換介面提示（第一／第二介面）",
                                "显示切换界面提示（第一／第二界面）",
                                "Show Page-Switch Hint Pill",
                                "画面切り替えヒントを表示"
                            ),
                            subtitle = str(
                                "在第一／第二介面間滑動時顯示中央提示，點提示上的「不再顯示」可關閉，也可於此重新開啟",
                                "在第一／第二界面间滑动时显示中央提示，点提示上的「不再显示」可关闭，也可在此重新开启",
                                "Shows the center pill when switching Page 1 / Page 2; re-enable here if dismissed",
                                "ページ1／2切り替え時に中央ヒントを表示します（非表示にした場合はここで再有効化）"
                            ),
                            checked = config.showUiSwitchHint,
                            onCheckedChange = { checked -> onUpdateConfig { it.copy(showUiSwitchHint = checked) } }
                        )

                        SettingsToggleRow(
                            title = str(
                                "YouTube App 內常駐「回第二介面」浮動按鈕（長壓可拖曳定位）",
                                "YouTube App 内常驻「回第二界面」浮动按钮（长按可拖动定位）",
                                "Always Show 'Back to Page 2 YT' Button in YouTube App (Long-Press Drag)",
                                "YouTube アプリ内に「ページ 2 へ戻る」ボタンを常時表示（長押し移動可）"
                            ),
                            subtitle = str(
                                "解決 YouTube 全螢幕時按鈕時有時無問題；長壓按鈕即可自由拖曳位置，切回第二介面開始播放後自動關閉原 App／小視窗",
                                "解决 YouTube 全屏时按钮时有时无问题；长按按钮即可自由拖动位置，切回第二界面开始播放后自动关闭原 App／小窗口",
                                "Keeps the handoff button visible over fullscreen YouTube; long-press to drag, auto-closes YouTube App/PiP after handoff",
                                "全画面時もボタンを安定表示し、長押しで位置調整可能。引き継ぎ再生後に元の YouTube／PiP を自動で閉じます"
                            ),
                            checked = config.ytHandoffAlwaysVisible,
                            onCheckedChange = { checked -> onUpdateConfig { it.copy(ytHandoffAlwaysVisible = checked) } }
                        )

                        SettingsToggleRow(
                            title = str(
                                "電話通知擷取（預設關閉 · 關閉時走原車藍牙電話）",
                                "电话通知捕获（默认关闭 · 关闭时走原车蓝牙电话）",
                                "Phone Call Capture (Default Off · Uses Stock Car BT Dialer)",
                                "電話着信キャプチャ（既定オフ・純正 Bluetooth 電話を使用）"
                            ),
                            subtitle = str(
                                "開啟後將由本啟動器接管來電橫幅（附切換音源鈕）並攔截原車前台通知；關閉則維持原車電話介面",
                                "开启后将由本启动器接管来电横幅（附切换音源按钮）并拦截原车前台通知；关闭则维持原车电话界面",
                                "When ON, intercepts stock foreground notifications/calls and shows DX-Car Call Banner with Audio Route switch",
                                "ON にすると純正の着信・通知をブロックし、音声切替付きバナーで表示します"
                            ),
                            checked = config.phoneCallCaptureEnabled,
                            onCheckedChange = { checked -> onUpdateConfig { it.copy(phoneCallCaptureEnabled = checked) } }
                        )

                        SettingsToggleRow(
                            title = str("24 小時制時鐘", "24 小时制时钟", "24-Hour Clock", "24時間表示"),
                            subtitle = str(
                                "關閉時顯示單行 12 小時制（如 8:20 PM）；點擊左上 Wi-Fi 圖示可展開下拉快捷鍵",
                                "关闭时显示单行 12 小时制（如 8:20 PM）；点击左上 Wi-Fi 图标可展开下拉快捷键",
                                "Shows 12-hour format (e.g. 8:20 PM) when off; tap top-left Wi-Fi icon for Quick Panel",
                                "OFF 時は 12時間制（例 8:20 PM）。左上の Wi-Fi アイコンでクイックパネルを展開"
                            ),
                            checked = config.is24HourClock,
                            onCheckedChange = { checked -> onUpdateConfig { it.copy(is24HourClock = checked) } }
                        )

                        // Non-blocking 3D Control Bar Tutorial Row
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
                                    Text(
                                        str(
                                            "🧊 Dock「3D 雙指懸浮條」操作說明",
                                            "🧊 Dock「3D 双指悬浮条」操作说明",
                                            "🧊 Dock Floating 3D Control Bar Guide",
                                            "🧊 ドック「3D 操作ツールバー」ガイド"
                                        ),
                                        color = colors.primaryText,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        str(
                                            "不擋畫面 4 鍵浮動條（3D仰角／2D平視／＋／－），支援長按微調與任意拖曳，絕不卡住畫面",
                                            "不挡画面 4 键浮动条（3D仰角／2D平视／＋／－），支持长按微调与任意拖拽，绝不卡住画面",
                                            "Non-blocking 4-button floating bar (3D Tilt / 2D Flat / + / −) with hold-to-repeat & drag, never traps screen",
                                            "画面を塞がない 4 ボタン浮動バー（3D視点／2D平面／拡大／縮小）。長押し微調整・移動対応で安心操作"
                                        ),
                                        color = colors.secondaryText,
                                        fontSize = 11.sp
                                    )
                                }
                                TutorialPillButton(
                                    label = str("教學", "教程", "Guide", "ガイド"),
                                    onClick = { onActiveTutorialChange(CarSettingsTutorialTopic.CURSOR_3D) }
                                )
                            }
                        }
                    }
                }

                val cardHomeLaunchers: @Composable (Modifier) -> Unit = { mod ->
                    SettingsSectionCard(
                        icon = Icons.Rounded.Home,
                        title = str("啟動器切換與系統預設桌面", "启动器切换与系统默认桌面", "Launcher Switcher & Default Home", "ランチャー切替・標準ホーム設定"),
                        modifier = mod
                    ) {
                        val currentDefaultLauncher = homeLaunchers.firstOrNull { it.isDefault }
                        val fallbackOtherLabel = currentDefaultLauncher?.label ?: str("其他啟動器", "其他启动器", "Other Launcher", "他のランチャー")
                        Text(
                            text = if (permissionHealth.isDefaultHome) {
                                str(
                                    "✓ 目前預設啟動器：DX-Car Launcher",
                                    "✓ 当前默认启动器：DX-Car Launcher",
                                    "✓ Current Default Home: DX-Car Launcher",
                                    "✓ 現在の標準ホーム：DX-Car Launcher"
                                )
                            } else {
                                str(
                                    "目前預設啟動器：$fallbackOtherLabel",
                                    "当前默认启动器：$fallbackOtherLabel",
                                    "Current Default Home: $fallbackOtherLabel",
                                    "現在の標準ホーム：$fallbackOtherLabel"
                                )
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
                                Text(
                                    if (permissionHealth.isDefaultHome) {
                                        str("重選預設桌面", "重选默认桌面", "Change Default Home", "標準ホームを再選択")
                                    } else {
                                        str("設為系統預設桌面", "设为系统默认桌面", "Set as Default Home", "標準ホームに設定")
                                    },
                                    fontSize = 12.sp
                                )
                            }
                            OutlinedButton(
                                onClick = { SystemStatusMonitor.openHomeLauncherSettings(context) },
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Icon(Icons.Rounded.SwapHoriz, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(str("系統桌面設定", "系统桌面设置", "System Home Settings", "システムホーム設定"), fontSize = 12.sp)
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
                                                    text = if (launcher.isDefault) str("系統預設桌面", "系统默认桌面", "System Default Home", "システムの標準ホーム") else launcher.packageName,
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
                                                Text(str("切換", "切换", "Switch", "切替"), fontSize = 12.sp)
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
                    categoryTitle = str(
                        "分類三 · 手機雙通道配對同步、遠端滑鼠與系統更新",
                        "分类三 · 手机双通道配对同步、远程鼠标与系统更新",
                        "Category 3 · Phone Dual-Channel Sync, Remote Mouse & Updates",
                        "カテゴリ 3 · スマホ2チャンネル同期・リモートマウス・更新"
                    ),
                    hint = str(
                        "手機連上車機藍牙免持即可自動穩定同步通知與遠端滑鼠觸控板；點擊「教學」查看特大字步驟",
                        "手机连上车机蓝牙免提即可自动稳定同步通知与远程鼠标触控板；点击「教程」查看特大字步骤",
                        "Auto-syncs notifications & remote touchpad via Car Bluetooth; tap Guide for large-print steps",
                        "車載 Bluetooth 接続で通知とリモートタッチパッドを自動同期。「ガイド」で拡大手順を表示"
                    )
                )

                val cardPhoneBridge: @Composable (Modifier) -> Unit = { mod ->
                    SettingsSectionCard(
                        icon = Icons.Rounded.NotificationsActive,
                        title = str(
                            "手機來電／訊息同步與遠端觸控",
                            "手机来电／消息同步与远程触控",
                            "Phone Calls / Messages Sync & Remote Touch",
                            "スマホ着信・メッセージ同期＆リモート操作"
                        ),
                        headerTrailing = {
                            TutorialPillButton(onClick = { onActiveTutorialChange(CarSettingsTutorialTopic.PHONE_PAIRING) })
                        },
                        modifier = mod
                    ) {
                        val bridgeStatusText = DxCarI18n.bridgeStatusLabel(bridgeStatus.status)
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
                                            str(
                                                "連線狀態：$bridgeStatusText (${bridgeStatus.transportLabel})",
                                                "连接状态：$bridgeStatusText (${bridgeStatus.transportLabel})",
                                                "Status: $bridgeStatusText (${bridgeStatus.transportLabel})",
                                                "接続状態：$bridgeStatusText (${bridgeStatus.transportLabel})"
                                            )
                                        } else {
                                            str(
                                                "連線狀態：$bridgeStatusText",
                                                "连接状态：$bridgeStatusText",
                                                "Status: $bridgeStatusText",
                                                "接続状態：$bridgeStatusText"
                                            )
                                        },
                                        color = colors.primaryText,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = if (bridgeStatus.peerName.isNotBlank()) {
                                            str(
                                                "已連接手機：${bridgeStatus.peerName}",
                                                "已连接手机：${bridgeStatus.peerName}",
                                                "Connected Phone: ${bridgeStatus.peerName}",
                                                "接続中のスマホ：${bridgeStatus.peerName}"
                                            )
                                        } else {
                                            str(
                                                "支援車機藍牙 SPP 自動配對與雙向同步（無須手動輸入 IP）",
                                                "支持车机蓝牙 SPP 自动配对与双向同步（无需手动输入 IP）",
                                                "Auto bi-directional sync via Car Bluetooth SPP (No manual IP needed)",
                                                "車載 Bluetooth SPP 自動接続・双方向同期（IP手入力不要）"
                                            )
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
                                "全屏导航／看视频时弹出顶部来电与消息横幅",
                                "Show Top Call & Message Banner in Fullscreen Apps",
                                "フルスクリーン時に着信・通知バナーを上部に表示"
                            ),
                            subtitle = str(
                                "支援接聽、掛斷、快速回覆與語音回覆",
                                "支持接听、挂断、快速回复与语音回复",
                                "Supports Answer, Decline, Quick Reply & Voice Reply",
                                "応答・切断・クイック返信・音声返信に対応"
                            ),
                            checked = config.globalCallMessageBannerEnabled,
                            onCheckedChange = { checked -> onUpdateConfig { it.copy(globalCallMessageBannerEnabled = checked) } }
                        )

                        SettingsToggleRow(
                            title = str(
                                "收到新訊息時自動語音朗讀 (TTS)",
                                "收到新消息时自动语音朗读 (TTS)",
                                "Auto-Read New Messages (TTS)",
                                "新着メッセージの自動音声読み上げ (TTS)"
                            ),
                            subtitle = str(
                                "自動壓低音樂音量播報寄件人與內容",
                                "自动压低音乐音量播报发件人与内容",
                                "Automatically ducks music volume to announce sender & message",
                                "音楽の音量を自動で下げて送信者と内容を読み上げます"
                            ),
                            checked = config.autoReadMessagesTts,
                            onCheckedChange = { checked -> onUpdateConfig { it.copy(autoReadMessagesTts = checked) } }
                        )

                        // Notification & Incoming Call Scale Slider (0.6X .. 2.0X, triggers live demo banner while dragging!)
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            val liveScale = config.notificationBannerScaleFloat.coerceIn(0.6f, 2.0f)
                            val scalePctText = "%.2fx".format(java.util.Locale.US, liveScale)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = str(
                                        "左上通知與來電橫幅大小（訊息字體已放大 1.5X · 拖動即時跳出示範通知）：$scalePctText",
                                        "左上通知与来电横幅大小（消息字体已放大 1.5X · 拖动实时跳出示范通知）：$scalePctText",
                                        "Notification & Call Banner Size (1.5X Larger Font · Drag to Preview): $scalePctText",
                                        "左上通知・着信バナーサイズ（文字1.5倍・ドラッグでプレビュー表示）：$scalePctText"
                                    ),
                                    color = colors.primaryText,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedButton(
                                    onClick = {
                                        onUpdateConfig {
                                            it.copy(
                                                notificationBannerScaleFloat = 1.0f,
                                                notificationBannerScale = com.dxcar.launcher.data.NotificationBannerScale.SCALE_1_0X
                                            )
                                        }
                                        com.dxcar.launcher.DxCarApp.instance?.notificationCenter?.showLiveDemoBannerForScalePreview()
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    modifier = Modifier.height(30.dp)
                                ) {
                                    Text(str("預設 1.0x", "默认 1.0x", "Reset 1.0x", "標準 1.0x"), fontSize = 11.sp)
                                }
                            }
                            Slider(
                                value = liveScale,
                                onValueChange = { nextVal ->
                                    val snapped = ((nextVal * 20f).roundToInt() / 20f).coerceIn(0.6f, 2.0f)
                                    val mappedEnum = when {
                                        snapped <= 0.62f -> com.dxcar.launcher.data.NotificationBannerScale.SCALE_0_5X
                                        snapped <= 0.87f -> com.dxcar.launcher.data.NotificationBannerScale.SCALE_0_75X
                                        snapped <= 1.24f -> com.dxcar.launcher.data.NotificationBannerScale.SCALE_1_0X
                                        snapped <= 1.74f -> com.dxcar.launcher.data.NotificationBannerScale.SCALE_1_5X
                                        else -> com.dxcar.launcher.data.NotificationBannerScale.SCALE_2_0X
                                    }
                                    onUpdateConfig {
                                        it.copy(
                                            notificationBannerScaleFloat = snapped,
                                            notificationBannerScale = mappedEnum
                                        )
                                    }
                                    com.dxcar.launcher.DxCarApp.instance?.notificationCenter?.showLiveDemoBannerForScalePreview()
                                },
                                valueRange = 0.6f..2.0f,
                                colors = SliderDefaults.colors(
                                    thumbColor = colors.accent,
                                    activeTrackColor = colors.accent
                                )
                            )
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
                                        text = str(
                                            "💬 自訂快速回覆選項（點擊通知「快速回覆」展開滑動選擇）",
                                            "💬 自定义快速回复选项（点击通知「快速回复」展开滑动选择）",
                                            "💬 Custom Quick Replies",
                                            "💬 クイック返信のカスタマイズ"
                                        ),
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
                                        Text(str("恢復預設", "恢复默认", "Reset", "初期化"), fontSize = 11.sp)
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
                                                    Icon(Icons.Rounded.Close, contentDescription = str("刪除", "删除", "Delete", "削除"), tint = colors.secondaryText, modifier = Modifier.size(14.dp))
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
                                        placeholder = {
                                            Text(
                                                str(
                                                    "輸入新增快速回覆詞（例如：正在停車中）…",
                                                    "输入新增快速回复语（例如：正在停车中）…",
                                                    "Add quick reply phrase…",
                                                    "返信フレーズを追加…"
                                                ),
                                                fontSize = 12.sp
                                            )
                                        },
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
                                        Text(str("＋ 新增", "＋ 新增", "+ Add", "＋ 追加"), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }

                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = onSimulateCall, shape = RoundedCornerShape(12.dp)) {
                                Text(
                                    str(
                                        "測試來電橫幅 (${config.notificationBannerScale.label})",
                                        "测试来电横幅 (${config.notificationBannerScale.label})",
                                        "Test Call (${config.notificationBannerScale.label})",
                                        "着信テスト (${config.notificationBannerScale.label})"
                                    ),
                                    fontSize = 12.sp
                                )
                            }
                            OutlinedButton(onClick = onSimulateMessage, shape = RoundedCornerShape(12.dp)) {
                                Text(
                                    str(
                                        "測試左上通知 (${config.notificationBannerScale.label})",
                                        "测试左上通知 (${config.notificationBannerScale.label})",
                                        "Test Alert (${config.notificationBannerScale.label})",
                                        "通知テスト (${config.notificationBannerScale.label})"
                                    ),
                                    fontSize = 12.sp
                                )
                            }
                            TutorialPillButton(
                                label = str("教學", "教程", "Guide", "ガイド"),
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
                                        Text(
                                            str(
                                                "✨ Gemini 語音輸入導航（Dock 與地圖一鍵呼叫）",
                                                "✨ Gemini 语音输入导航（Dock 与地图一键呼叫）",
                                                "✨ Gemini Voice Navigation (1-Tap from Dock & Map)",
                                                "✨ Gemini 音声ナビ検索（ドック・地図からワンタップ起動）"
                                            ),
                                            color = colors.primaryText,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    Button(
                                        onClick = onStartVoiceNavigation,
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C3AED), contentColor = Color.White),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Text(str("🎙️ 測試語音導航", "🎙️ 测试语音导航", "🎙️ Test Voice Nav", "🎙️ 音声ナビをテスト"), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    GeminiVoiceMode.entries.forEach { gm ->
                                        SelectablePill(
                                            text = DxCarI18n.geminiVoiceModeLabel(gm),
                                            selected = config.geminiVoiceMode == gm,
                                            onClick = { onUpdateConfig { it.copy(geminiVoiceMode = gm) } }
                                        )
                                    }
                                }
                                OutlinedTextField(
                                    value = config.geminiApiKey,
                                    onValueChange = { key -> onUpdateConfig { it.copy(geminiApiKey = key.trim()) } },
                                    singleLine = true,
                                    label = {
                                        Text(
                                            str(
                                                "選填 Gemini API Key（不填亦可免 Key 語音導航／喚起 Gemini App）",
                                                "选填 Gemini API Key（不填亦可免 Key 语音导航／唤起 Gemini App）",
                                                "Optional Gemini API Key (Works key-free for speech nav / Gemini App)",
                                                "Gemini API キー（任意・未入力でも音声ナビ／Gemini アプリ起動可能）"
                                            ),
                                            fontSize = 11.sp
                                        )
                                    },
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
                        title = str(
                            "七天試用買斷、GitHub 主頁與恢復預設",
                            "七天试用买断、GitHub 主页与恢复默认",
                            "Trial, Pro Unlock, GitHub & Reset",
                            "トライアル・買い切り・GitHub・初期化"
                        ),
                        modifier = mod
                    ) {
                        if (BuildConfig.ENABLE_GITHUB_AUTO_UPDATE) {
                            SettingsToggleRow(
                                title = str(
                                    "啟動時自動檢測並下載新版 APK",
                                    "启动时自动检测并下载新版 APK",
                                    "Auto-Check & Download Updates",
                                    "起動時に自動で更新を確認・取得"
                                ),
                                subtitle = str(
                                    "目前版本：v${BuildConfig.VERSION_NAME} (code ${BuildConfig.VERSION_CODE})",
                                    "当前版本：v${BuildConfig.VERSION_NAME} (code ${BuildConfig.VERSION_CODE})",
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
                                        if (updateState.checking) str("檢查中…", "检查中…", "Checking…", "確認中…")
                                        else str("檢查更新", "检查更新", "Check Updates", "更新を確認")
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
                                                updateState.downloadedReady -> str(
                                                    "立即安裝 v${updateState.latestVersion}",
                                                    "立即安装 v${updateState.latestVersion}",
                                                    "Install v${updateState.latestVersion}",
                                                    "今すぐインストール v${updateState.latestVersion}"
                                                )
                                                updateState.downloading -> str(
                                                    "下載中 ${(updateState.progress * 100).toInt()}%",
                                                    "下载中 ${(updateState.progress * 100).toInt()}%",
                                                    "Downloading ${(updateState.progress * 100).toInt()}%",
                                                    "ダウンロード中 ${(updateState.progress * 100).toInt()}%"
                                                )
                                                else -> str("下載更新", "下载更新", "Download Update", "更新をダウンロード")
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
                                            Text(str("恢復購買", "恢复购买", "Restore", "購入を復元"), fontSize = 12.sp)
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
                                        Text(
                                            str("GitHub 開源儲存庫", "GitHub 开源仓库", "GitHub Open-Source Repository", "GitHub オープンソースリポジトリ"),
                                            color = colors.primaryText,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(UpdateManager.GITHUB_URL, color = colors.accent, fontSize = 11.sp)
                                    }
                                }
                                Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null, tint = colors.accent)
                            }
                        }

                        // BACKUP & RESTORE CARD (Local + Google Drive App)
                        var backupNameInput by remember(config.backupDefaultName) {
                            mutableStateOf(config.backupDefaultName)
                        }
                        var backupStatusMsg by remember { mutableStateOf<String?>(null) }

                        fun sanitizedBackupBaseName(): String {
                            val cleaned = backupNameInput.trim().replace(Regex("[\\\\/:*?\"<>|]"), "_")
                            return cleaned.ifBlank { "DXCar_Settings_Backup" }
                        }

                        val saveLocalLauncher = rememberLauncherForActivityResult(
                            contract = ActivityResultContracts.CreateDocument("application/json")
                        ) { uri ->
                            if (uri != null) {
                                val ok = runCatching {
                                    val jsonText = onExportConfigJson()
                                    context.contentResolver.openOutputStream(uri)?.use { out ->
                                        out.write(jsonText.toByteArray(Charsets.UTF_8))
                                    } ?: error("Cannot open output stream")
                                }.isSuccess
                                backupStatusMsg = if (ok) {
                                    DxCarI18n.tr(
                                        "✅ 已成功備份設定至本機檔案",
                                        "✅ 已成功备份设置至本地文件",
                                        "✅ Settings backed up to local file",
                                        "✅ 設定をローカルファイルにバックアップしました"
                                    )
                                } else {
                                    DxCarI18n.tr(
                                        "❌ 寫入本機備份檔失敗",
                                        "❌ 写入本地备份文件失败",
                                        "❌ Failed to write local backup file",
                                        "❌ ローカルバックアップの保存に失敗しました"
                                    )
                                }
                            }
                        }

                        val restoreLauncher = rememberLauncherForActivityResult(
                            contract = ActivityResultContracts.OpenDocument()
                        ) { uri ->
                            if (uri != null) {
                                val ok = runCatching {
                                    val raw = context.contentResolver.openInputStream(uri)?.use { input ->
                                        input.readBytes().toString(Charsets.UTF_8)
                                    } ?: return@runCatching false
                                    onImportConfigJson(raw)
                                }.getOrDefault(false)
                                backupStatusMsg = if (ok) {
                                    DxCarI18n.tr(
                                        "✅ 已成功還原所有啟動器設定",
                                        "✅ 已成功还原所有启动器设置",
                                        "✅ Launcher settings restored successfully",
                                        "✅ すべてのランチャー設定を復元しました"
                                    )
                                } else {
                                    DxCarI18n.tr(
                                        "❌ 還原失敗：檔案格式不是有效的 DX-Car 設定備份",
                                        "❌ 还原失败：文件格式不是有效的 DX-Car 设置备份",
                                        "❌ Restore failed: invalid DX-Car settings backup",
                                        "❌ 復元に失敗：有効な DX-Car 設定バックアップではありません"
                                    )
                                }
                            }
                        }

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
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Rounded.Backup, contentDescription = null, tint = colors.accent, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = str(
                                            "設定備份與還原（本機／Google 雲端硬碟）",
                                            "设置备份与还原（本地／Google 云端硬盘）",
                                            "Backup & Restore Settings (Local / Google Drive)",
                                            "設定のバックアップと復元（ローカル／Google ドライブ）"
                                        ),
                                        color = colors.primaryText,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                OutlinedTextField(
                                    value = backupNameInput,
                                    onValueChange = { next ->
                                        backupNameInput = next
                                        onUpdateConfig { it.copy(backupDefaultName = next) }
                                    },
                                    singleLine = true,
                                    label = {
                                        Text(
                                            str(
                                                "備份檔案預設名稱（不含 .json）",
                                                "备份文件默认名称（不含 .json）",
                                                "Default Backup Name (without .json)",
                                                "バックアップの既定ファイル名（.json 不要）"
                                            ),
                                            fontSize = 12.sp
                                        )
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                )
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Button(
                                        onClick = {
                                            saveLocalLauncher.launch("${sanitizedBackupBaseName()}.json")
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = colors.accent, contentColor = colors.onAccent),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Icon(Icons.Rounded.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text(str("備份至本機", "备份至本地", "Save Locally", "ローカルに保存"), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                    Button(
                                        onClick = {
                                            val fileName = "${sanitizedBackupBaseName()}.json"
                                            val shared = runCatching {
                                                val dir = File(context.cacheDir, "backups").apply { mkdirs() }
                                                val outFile = File(dir, fileName)
                                                outFile.writeText(onExportConfigJson(), Charsets.UTF_8)
                                                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", outFile)
                                                val driveIntent = Intent(Intent.ACTION_SEND).apply {
                                                    type = "application/json"
                                                    putExtra(Intent.EXTRA_STREAM, uri)
                                                    putExtra(Intent.EXTRA_SUBJECT, fileName)
                                                    setPackage("com.google.android.apps.docs")
                                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                                                }
                                                try {
                                                    context.startActivity(driveIntent)
                                                } catch (_: Throwable) {
                                                    val chooser = Intent.createChooser(
                                                        Intent(Intent.ACTION_SEND).apply {
                                                            type = "application/json"
                                                            putExtra(Intent.EXTRA_STREAM, uri)
                                                            putExtra(Intent.EXTRA_SUBJECT, fileName)
                                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                        },
                                                        DxCarI18n.tr(
                                                            "上傳設定備份至雲端硬碟",
                                                            "上传设置备份至云端硬盘",
                                                            "Upload Backup to Drive",
                                                            "バックアップをドライブにアップロード"
                                                        )
                                                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                    context.startActivity(chooser)
                                                }
                                            }.isSuccess
                                            backupStatusMsg = if (shared) {
                                                DxCarI18n.tr(
                                                    "☁️ 已產生 $fileName 並開啟 Google 雲端硬碟上傳",
                                                    "☁️ 已生成 $fileName 并打开 Google 云端硬盘上传",
                                                    "☁️ Created $fileName & opened Google Drive upload",
                                                    "☁️ $fileName を作成し Google ドライブを開きました"
                                                )
                                            } else {
                                                DxCarI18n.tr(
                                                    "❌ 無法開啟 Google 雲端硬碟 App，請改用「備份至本機」",
                                                    "❌ 无法打开 Google 云端硬盘 App，请改用「备份至本地」",
                                                    "❌ Could not open Google Drive App; use Save Locally instead",
                                                    "❌ Google ドライブを開けませんでした。「ローカルに保存」をお試しください"
                                                )
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = colors.cardSurface, contentColor = colors.accent),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Icon(Icons.Rounded.CloudUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text(str("開啟 Google 雲端硬碟上傳", "打开 Google 云端硬盘上传", "Upload via Google Drive", "Google ドライブでアップロード"), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            restoreLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
                                        },
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Icon(Icons.Rounded.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text(str("還原設定檔", "还原配置文件", "Restore Backup", "バックアップから復元"), fontSize = 12.sp)
                                    }
                                }
                                if (!backupStatusMsg.isNullOrBlank()) {
                                    Text(
                                        text = backupStatusMsg.orEmpty(),
                                        color = colors.accent,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
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
                                if (confirmResetPending) {
                                    str("再次點擊確認重置所有設定", "再次点击确认重置所有设置", "Tap again to confirm reset", "もう一度タップして初期化")
                                } else {
                                    str("恢復啟動器設定為預設值", "恢复启动器设置为默认值", "Reset Launcher Defaults", "ランチャー設定を初期化")
                                },
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
                                    Text(
                                        str(
                                            "車機／平板權限列表與 60fps 高流暢設定",
                                            "车机／平板权限列表与 60fps 高流畅设置",
                                            "Car / Tablet Permissions & 60fps Smooth Mode",
                                            "車載機／タブレット権限一覧 ＆ 60fps 設定"
                                        ),
                                        color = colors.primaryText,
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        str(
                                            "目前已授權 $grantedPerms / $totalPerms 項必要權限",
                                            "当前已授权 $grantedPerms / $totalPerms 项必要权限",
                                            "$grantedPerms / $totalPerms required permissions granted",
                                            "必須権限 $grantedPerms / $totalPerms 件が許可済み"
                                        ),
                                        color = colors.secondaryText,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = onRefreshPermissions, shape = RoundedCornerShape(12.dp)) {
                                    Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text(str("重新檢查", "重新检查", "Recheck", "再確認"))
                                }
                                IconButton(onClick = { onShowPermissionsSheetChange(false) }) {
                                    Icon(Icons.Rounded.Close, contentDescription = str("關閉", "关闭", "Close", "閉じる"), tint = colors.primaryText)
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
                                            Text(
                                                str(
                                                    "開啟 60fps / 60Hz 畫面高幀率模式",
                                                    "开启 60fps / 60Hz 画面高帧率模式",
                                                    "Enable 60fps / 60Hz High Frame Rate Mode",
                                                    "60fps / 60Hz 高フレームレートモードを有効化"
                                                ),
                                                color = colors.primaryText,
                                                fontSize = 15.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = when {
                                                    sheetRefreshCap.supports60Hz -> str(
                                                        "✓ 可用（支援 ${sheetRefreshCap.maxSupportedHz}Hz · 目前 ${sheetRefreshCap.currentHz}Hz）",
                                                        "✓ 可用（支持 ${sheetRefreshCap.maxSupportedHz}Hz · 当前 ${sheetRefreshCap.currentHz}Hz）",
                                                        "✓ Supported (${sheetRefreshCap.maxSupportedHz}Hz max · ${sheetRefreshCap.currentHz}Hz now)",
                                                        "✓ 利用可能（最大 ${sheetRefreshCap.maxSupportedHz}Hz · 現在 ${sheetRefreshCap.currentHz}Hz）"
                                                    )
                                                    sheetRefreshCap.hasVendorSettingsApp -> str(
                                                        "⚠️ 目前 ${sheetRefreshCap.currentHz}Hz · 可進原廠設定檢查高幀率",
                                                        "⚠️ 当前 ${sheetRefreshCap.currentHz}Hz · 可进原厂设置检查高帧率",
                                                        "⚠️ Currently ${sheetRefreshCap.currentHz}Hz · Check OEM settings for high FPS",
                                                        "⚠️ 現在 ${sheetRefreshCap.currentHz}Hz · 純正設定で高フレームレートを確認可能"
                                                    )
                                                    else -> str(
                                                        "⚠️ 未偵測到原生 60Hz（目前系統回報 ${sheetRefreshCap.currentHz}Hz）",
                                                        "⚠️ 未检测到原生 60Hz（当前系统回报 ${sheetRefreshCap.currentHz}Hz）",
                                                        "⚠️ Native 60Hz not detected (System reports ${sheetRefreshCap.currentHz}Hz)",
                                                        "⚠️ ネイティブ 60Hz 未検出（システム報告 ${sheetRefreshCap.currentHz}Hz）"
                                                    )
                                                },
                                                color = if (sheetRefreshCap.supports60Hz) colors.statusConnected else Color(0xFFF59E0B),
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = when {
                                                    sheetRefreshCap.supports60Hz -> str(
                                                        "偵測到此裝置螢幕支援 ${sheetRefreshCap.maxSupportedHz}Hz（目前實際更新率 ${sheetRefreshCap.currentHz}Hz），開啟開關即可強制以 60fps 高流暢度渲染！",
                                                        "检测到此设备屏幕支持 ${sheetRefreshCap.maxSupportedHz}Hz（当前实际刷新率 ${sheetRefreshCap.currentHz}Hz），开启开关即可强制以 60fps 高流畅度渲染！",
                                                        "Display supports ${sheetRefreshCap.maxSupportedHz}Hz (${sheetRefreshCap.currentHz}Hz active); enable toggle to force 60fps smooth rendering!",
                                                        "${sheetRefreshCap.maxSupportedHz}Hz 対応（現在 ${sheetRefreshCap.currentHz}Hz）。ON にすると 60fps 滑らか描画を強制要求します！"
                                                    )
                                                    sheetRefreshCap.hasVendorSettingsApp -> str(
                                                        "目前螢幕回報為 ${sheetRefreshCap.currentHz}Hz（可能受 CarPlay 通道預設幀率限制）；開啟開關會強制請求最高幀率，亦可點下方進入車機原廠設定開啟 60fps。",
                                                        "当前屏幕回报为 ${sheetRefreshCap.currentHz}Hz（可能受 CarPlay 通道默认帧率限制）；开启开关会强制请求最高帧率，亦可点下方进入车机原厂设置开启 60fps。",
                                                        "Screen currently reports ${sheetRefreshCap.currentHz}Hz (may be capped by CarPlay channel); enable toggle to request max FPS, or open OEM Car settings below.",
                                                        "現在 ${sheetRefreshCap.currentHz}Hz と報告されています（CarPlay 経路制限の可能性）。ON で最大 FPS を要求、または下の純正設定を確認してください。"
                                                    )
                                                    else -> str(
                                                        "未偵測到車機原廠獨立 60Hz 設定（系統回報固定 ${sheetRefreshCap.currentHz}Hz）；開關仍可開啟以強制請求 App 最高渲染幀率，實際上限依車機盒韌體而定。",
                                                        "未检测到车机原厂独立 60Hz 设置（系统回报固定 ${sheetRefreshCap.currentHz}Hz）；开关仍可开启以强制请求 App 最高渲染帧率，实际上限依车机盒固件而定。",
                                                        "No dedicated OEM 60Hz setting detected (fixed ${sheetRefreshCap.currentHz}Hz reported); toggle still requests max app FPS subject to AI Box firmware.",
                                                        "純正 60Hz 設定は未検出（固定 ${sheetRefreshCap.currentHz}Hz）。ON で最大描画 FPS を要求します（上限は車載機ファームウェア依存）。"
                                                    )
                                                },
                                                color = colors.secondaryText,
                                                fontSize = 11.sp
                                            )
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
                                    Text(
                                        str(
                                            "進入原廠車機設定開啟 60fps（若有此選項即可開啟）",
                                            "进入原厂车机设置开启 60fps（若有此选项即可开启）",
                                            "Open OEM Car Settings for 60fps Option (If Available)",
                                            "純正の車載機設定を開いて 60fps を確認（対応機種のみ）"
                                        ),
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        PermissionRow(
                            title = str(
                                "設為預設車機啟動器 (Home)",
                                "设为默认车机启动器 (Home)",
                                "Set as Default Car Home Launcher",
                                "標準の車載ホームランチャーに設定"
                            ),
                            granted = permissionHealth.isDefaultHome,
                            onAction = onRequestSetDefaultHome
                        )
                        PermissionRow(
                            title = str(
                                "顯示在其他應用程式上層（常駐 Dock、3D 指標、遠端滑鼠與來電橫幅）",
                                "显示在其他应用上层（常驻 Dock、3D 指针、远程鼠标与来电横幅）",
                                "Display Over Other Apps (Floating Dock, 3D Pointer, Remote Mouse & Banners)",
                                "他のアプリの上に重ねて表示（常駐ドック・3Dポインター・着信バナー）"
                            ),
                            granted = permissionHealth.hasOverlay,
                            onAction = { PermissionHelper.openOverlayPermission(context) }
                        )
                        PermissionRow(
                            title = str(
                                "通知存取權（音樂封面／紅心控制、Google 天氣讀取、導航與訊息）",
                                "通知访问权限（音乐封面／红心控制、Google 天气读取、导航与消息）",
                                "Notification Access (Album Art / Like Control, Weather, Nav & Messages)",
                                "通知へのアクセス（音楽ジャケット／いいね制御・天気・ナビ・通知）"
                            ),
                            granted = permissionHealth.hasNotificationListener,
                            onAction = { PermissionHelper.openNotificationListenerSettings(context) }
                        )
                        PermissionRow(
                            title = str(
                                "無障礙輔助服務（AOSP 可拖動分屏、3D 類滑鼠指標與手機遠端滑鼠點擊）",
                                "无障碍辅助服务（AOSP 可拖动分屏、3D 类鼠标指针与手机远程鼠标点击）",
                                "Accessibility Service (AOSP Split-Screen, 3D Pointer & Remote Mouse Clicks)",
                                "ユーザー補助サービス（AOSP 画面分割・3D ポインター・リモートマウス操作）"
                            ),
                            granted = permissionHealth.hasAccessibility,
                            onAction = { PermissionHelper.openAccessibilitySettings(context) }
                        )
                        PermissionRow(
                            title = str(
                                "定位、藍牙、電話與通知執行期權限",
                                "定位、蓝牙、电话与通知运行时权限",
                                "Location, Bluetooth, Phone & Notification Runtime Permissions",
                                "位置情報・Bluetooth・電話・通知の実行時権限"
                            ),
                            granted = permissionHealth.hasLocation && permissionHealth.hasBluetooth && permissionHealth.hasPhoneState,
                            onAction = onRequestRuntimePermissions
                        )
                        PermissionRow(
                            title = str(
                                "修改系統設定（快捷面板螢幕亮度控制）",
                                "修改系统设置（快捷面板屏幕亮度控制）",
                                "Modify System Settings (Quick Panel Screen Brightness Control)",
                                "システム設定の変更（クイックパネルからの画面輝度調整）"
                            ),
                            granted = permissionHealth.hasWriteSettings,
                            onAction = { PermissionHelper.openWriteSettingsPermission(context) }
                        )
                        PermissionRow(
                            title = str(
                                "忽略電池最佳化（防止背景關閉常駐 Dock 與雙通道連線）",
                                "忽略电池优化（防止后台关闭常驻 Dock 与双通道连接）",
                                "Ignore Battery Optimizations (Keep Floating Dock & Companion Link Alive)",
                                "バッテリー最適化を無視（常駐ドックとスマホ連携の切断を防止）"
                            ),
                            granted = permissionHealth.hasIgnoreBatteryOpt,
                            onAction = { PermissionHelper.requestIgnoreBatteryOptimizations(context) }
                        )
                    }
                }
            }
        }

        // EXTRA-LARGE FONT TUTORIAL MODALS (Triggered by clicking any "教學" button!)
        when (activeTutorial) {
            CarSettingsTutorialTopic.DUAL_UI_GUIDE -> {
                ExtraLargeTutorialModal(
                    title = str(
                        "📖 第一介面 (1/2) ＆ 第二介面 (2/2) · 完整操作教學",
                        "📖 第一界面 (1/2) ＆ 第二界面 (2/2) · 完整操作教程",
                        "📖 First UI (1/2) & Second UI (2/2) · Full Guide",
                        "📖 第1画面 (1/2) ＆ 第2画面 (2/2) · 完全ガイド"
                    ),
                    steps = listOf(
                        str(
                            "【切換第一／第二介面】按住畫面中央分隔線握把（1/2 或 2/2）向左或向右拖曳滑動，即可在「① 第一介面」與「② 第二介面」之間切換。",
                            "【切换第一／第二界面】按住画面中央分隔线握把（1/2 或 2/2）向左或向右拖拽滑动，即可在「① 第一界面」与「② 第二界面」之间切换。",
                            "[Switch UI 1 / UI 2] Drag the center divider handle (1/2 or 2/2) left or right to switch smoothly between UI 1 and UI 2.",
                            "【第1／第2画面の切替】画面中央の区切りバー（1/2 または 2/2）を左右にドラッグして「① 第1画面」と「② 第2画面」を切り替えます。"
                        ),
                        str(
                            "【① 第一介面：封面交換與快捷操作】點擊播放器「專輯封面」即可左右交換地圖與播放器大小；封面左上角「天氣地點膠囊」點擊直開 Google 天氣；下方點讚（♡）旁的「App 圖示」點擊直入該播放 App；地圖右下角「圖層圓鈕」整合切換標準／衛星／3D 底圖與即時路況。",
                            "【① 第一界面：封面交换与快捷操作】点击播放器「专辑封面」即可左右交换地图与播放器大小；封面左上角「天气地点胶囊」点击直开 Google 天气；下方点赞（♡）旁的「App 图标」点击直入该播放 App；地图右下角「图层圆钮」整合切换标准／卫星／3D 底图与实时路况。",
                            "[① First UI: Swap & Shortcuts] Tap the Album Cover to swap Map and Media pane sizes; tap the top-left Weather Pill for Google Weather; tap the App Icon next to Like (♡) to open the music app; tap the bottom-right Map Layers button for Standard/Satellite/3D & Live Traffic.",
                            "【① 第1画面：カバー入替とショートカット】「アルバムカバー」タップで地図とプレーヤーのサイズを入替。カバー左上の天気ピルで Google 天気を表示。いいね（♡）横のアイコンで音楽アプリを起動。地図右下のレイヤーボタンで標準／衛星／3D地図と渋滞情報を切替。"
                        ),
                        str(
                            "【② 第二介面：長壓互換與右半邊全螢幕】長壓中央「2/2 分隔線」可對調左右視窗位置；於 YouTube「上半部影片區向上滑動」（或點播放器右下角全螢幕鈕）即可進入右半邊滿版全螢幕，「向下滑動」或再點按鈕即可退出。",
                            "【② 第二界面：长按互换与右半边全屏】长按中央「2/2 分隔线」可对调左右窗口位置；在 YouTube「上半部视频区向上滑动」（或点播放器右下角全屏钮）即可进入右半边满版全屏，「向下滑动」或再点按钮即可退出。",
                            "[② Second UI: Swap & Half-Fullscreen] Long-press the center 2/2 divider to swap left/right sides; swipe UP on the upper YouTube video area (or tap the fullscreen button) to fill the right half; swipe DOWN or tap again to exit.",
                            "【② 第2画面：左右入替と右半分全画面】中央の「2/2 区切り線」長押しで左右を入替。YouTube 上部の動画エリアを上スワイプ（または全画面ボタン）で右半分フルスクリーン表示、下スワイプで終了します。"
                        ),
                        str(
                            "【② 第二介面：YouTube 全螢幕快轉手勢】全螢幕狀態下：左 1/3 點兩下倒退 10 秒、右 1/3 點兩下快轉 10 秒；左右 1/3「點兩下後按住不放」可持續加速倒退／快轉；左右水平滑動可直接拖曳時間軸（中央 1/3 維持 YouTube 原生點控）。",
                            "【② 第二界面：YouTube 全屏快进手势】全屏状态下：左 1/3 双击快退 10 秒、右 1/3 双击快进 10 秒；左右 1/3「双击后按住不放」可持续加速快退／快进；左右水平滑动可直接拖拽时间轴（中央 1/3 维持 YouTube 原生点控）。",
                            "[② Second UI: YouTube Fullscreen Gestures] In fullscreen: double-tap Left/Right 1/3 for -10s / +10s; double-tap & hold Left/Right 1/3 for accelerated rewind/fast-forward; swipe horizontally to scrub timeline (Center 1/3 keeps native YouTube controls).",
                            "【② 第2画面：YouTube 全画面ジェスチャー】全画面時：左/右 1/3 ダブルタップで 10秒戻し/送り、ダブルタップ長押しで連続高速シーク、左右スワイプでタイムライン移動（中央 1/3 は標準タップ操作）。"
                        ),
                        str(
                            "【② 第二介面：720p 預設與跨系統背景播放】預設以 720p 播放並支援方向盤媒體鍵；切去 Google 地圖、瀏覽器、系統設定或退回原車機盒桌面皆會持續背景播放聲音，僅當其他音樂 App 開始播放時才自動暫停。",
                            "【② 第二界面：720p 默认与跨系统后台播放】默认以 720p 播放并支持方向盘媒体键；切去 Google 地图、浏览器、系统设置或退回原车机盒桌面均会持续后台播放声音，仅当其他音乐 App 开始播放时才自动暂停。",
                            "[② Second UI: 720p & Background Play] Streams at 720p by default with steering-wheel media key support; keeps audio playing across Google Maps, Browser, Settings, or Stock Launcher, pausing only when another music app plays.",
                            "【② 第2画面：標準 720p ＆ バックグラウンド再生】標準 720p 再生とステアリングリモコンに対応。Google マップやブラウザ、純正ホームへ移動しても音声再生を継続し、他音楽アプリの再生時のみ自動一時停止します。"
                        ),
                        str(
                            "【媒體進度條滑動＆語音朗讀隨時取消】媒體播放器進度條可直接以手指「點擊或拖曳滑動」調整歌曲進度；當系統語音朗讀訊息時，畫面左上角會出現「🔊 正在朗讀 · 點此取消」脈動按鈕，手指輕點即可立刻停止發話！",
                            "【媒体进度条滑动＆语音朗读随时取消】媒体播放器进度条可直接以手指「点击或拖拽滑动」调整歌曲进度；当系统语音朗读消息时，画面左上角会出现「🔊 正在朗读 · 点此取消」脉动按钮，手指轻点即可立刻停止播报！",
                            "[Scrubbable Media Bar & Tap-to-Stop TTS] Drag or tap the media progress bar to seek playback position directly; while TTS is reading a message aloud, a pulsing top-left cancel pill lets you tap once to stop speech immediately!",
                            "【シークバー操作＆読み上げ即停止】プレーヤーのシークバーをタップ／ドラッグして再生位置を調整可能。音声読み上げ中は画面左上にキャンセルボタンが表示され、ワンタップで即停止できます！"
                        )
                    ),
                    highlightNote = str(
                        "💡 提示：點擊 Dock 左下角按鈕在任何非首頁畫面皆會直接帶您回到啟動器首頁；已在首頁時點擊則展開 App 清單！",
                        "💡 提示：点击 Dock 左下角按钮在任何非首页画面均会直接带您回到启动器首页；已在首页时点击则展开 App 列表！",
                        "💡 Tip: Tapping the Home/Drawer Dock button from any non-home screen returns to DX-Car Home first; tapping it on Home opens the App Drawer!",
                        "💡 ヒント：ホーム以外の画面でドックのホームボタンを押すとホーム画面へ戻り、ホーム画面で押すとアプリ一覧を開きます！"
                    ),
                    onClose = { onActiveTutorialChange(null) }
                )
            }

            CarSettingsTutorialTopic.CURSOR_3D -> {
                ExtraLargeTutorialModal(
                    title = str(
                        "🧊 Dock「3D 雙指懸浮條」· 操作教學",
                        "🧊 Dock「3D 双指悬浮条」· 操作教程",
                        "🧊 Dock Floating 3D Control Bar · Guide",
                        "🧊 ドック「3D 操作ツールバー」· 操作ガイド"
                    ),
                    steps = tutorial3DControlSteps(),
                    highlightNote = str(
                        "💡 內建地圖免無障礙即可直接調整 3D／縮放；若要在原版 Google 地圖等外部導航使用，請點設定最上方「權限需求」開啟【顯示在其他應用程式上層】與【無障礙輔助服務】。",
                        "💡 内置地图免无障碍即可直接调整 3D／缩放；若要在原版 Google 地图等外部导航使用，请点设置最上方「权限需求」开启【显示在其他应用上层】与【无障碍辅助服务】。",
                        "💡 Works directly on the built-in map right away; enable [Display Over Other Apps] and [Accessibility Service] in Permissions at the top of Settings to control external Google Maps.",
                        "💡 内蔵マップでは追加権限なしで動作します。Google マップ等の外部ナビで使う場合は、設定上部の「権限設定」から【重ねて表示】と【ユーザー補助サービス】を有効にしてください。"
                    ),
                    onClose = { onActiveTutorialChange(null) }
                )
            }

            CarSettingsTutorialTopic.PHONE_PAIRING -> {
                ExtraLargeTutorialModal(
                    title = str(
                        "📱 車機與手機配對同步 · 完整教學",
                        "📱 车机与手机配对同步 · 完整教程",
                        "📱 Car & Phone Companion Sync · Full Guide",
                        "📱 車載機＆スマホ連携同期 · 完全ガイド"
                    ),
                    steps = listOf(
                        str(
                            "【車機端】點設定最上方「權限設定」，開啟【顯示在其他應用程式上層】、【通知存取權】與【無障礙輔助服務】（啟用遠端滑鼠點擊）。",
                            "【车机端】点设置最上方「权限需求」，开启【显示在其他应用上层】、【通知访问权限】与【无障碍辅助服务】（启用远程鼠标点击）。",
                            "[On Car AI Box] Tap Permissions at the top of Settings and enable [Display Over Other Apps], [Notification Access], and [Accessibility Service] (for remote mouse clicks).",
                            "【車載機側】設定上部の「権限設定」から【他のアプリの上に表示】・【通知へのアクセス】・【ユーザー補助サービス】（リモートクリック用）を有効にします。"
                        ),
                        str(
                            "【手機端】安裝同一支 APK 開啟會自動進入手機介面：點最上方「權限設定」開啟【通知讀取權限】（Android 13+ 若顯示受限制設定，先去應用程式資訊右上角 ⋮ 點「允許受限制的設定」）。",
                            "【手机端】安装同一支 APK 开启会自动进入手机界面：点最上方「权限需求」开启【通知读取权限】（Android 13+ 若显示受限制设置，先去应用信息右上角 ⋮ 点「允许受限制的设置」）。",
                            "[On Smartphone] Install the same APK (auto-enters Phone Companion mode): tap Permissions and enable [Notification Access] (on Android 13+, if restricted, open App Info → top-right ⋮ → Allow restricted settings).",
                            "【スマホ側】同じ APK を入れて起動すると自動でスマホモードになります。上部の「権限設定」から【通知へのアクセス】を許可します（Android 13+ で制限される場合はアプリ情報 → 右上 ⋮ →「制限付き設定を許可」）。"
                        ),
                        str(
                            "【自動連線方式】只要「手機連上車機盒藍牙（可接電話狀態）」，雙端就會自動透過藍牙 SPP 穩定連線，不需手動輸入 IP！若斷線可在手機端點「再次觸發藍牙連線」或使用桌面捷徑／下拉快捷鍵。",
                            "【自动连接方式】只要「手机连上车机盒蓝牙（可接电话状态）」，双端就会自动通过蓝牙 SPP 稳定连接，无需手动输入 IP！若断开可在手机端点「再次触发蓝牙连接」或使用桌面快捷方式／下拉快捷键。",
                            "[Auto-Connection] Simply connect your phone to Car Bluetooth — both sides connect automatically via Bluetooth SPP with no manual IP! Use [Reconnect Bluetooth] or the Home Shortcut / Quick Tile anytime.",
                            "【自動接続】「スマホを車載 Bluetooth に接続」するだけで、Bluetooth SPP 経由で自動接続されます（IP 入力不要）。スマホ側の「Bluetooth 再接続」やホーム・クイック設定からも再接続できます！"
                        ),
                        str(
                            "【遠端滑鼠觸控板】連線後手機畫面可直接當作車機滑鼠觸控板使用；點手機上的「車機滑鼠靈敏度設定」，車機畫面就會自動跳出系統指標速度設定頁！",
                            "【远程鼠标触控板】连接后手机画面可直接当作车机鼠标触控板使用；点手机上的「车机鼠标灵敏度设置」，车机画面就会自动弹出系统指针速度设置页！",
                            "[Remote Mouse Touchpad] Once connected, use your phone screen as a multi-touch Car touchpad; tap [Car Pointer Speed Settings] on your phone to pop up system pointer speed settings on the Car screen!",
                            "【リモートタッチパッド】接続後はスマホ画面で車載機のマウス操作が可能。スマホ側の「車載マウス感度設定」を押すと車載画面にポインター速度設定が開きます！"
                        ),
                        str(
                            "【📞 車機呼叫電話 → 自動連動手機原始電話撥打】本 App 已內建車機端電話撥號能力！當您按下方向盤語音鍵或向車機 Gemini 說「打電話給某某某／09xx…」，系統會判定車機 App 可撥號並自動接手，即時傳遞給配對手機，由手機端呼叫原始系統電話 App 撥出（手機端模式會自動停用攔截，絕不影響日常通話）。",
                            "【📞 车机呼叫电话 → 自动联动手机原始电话拨打】本 App 已内置车机端电话拨号能力！当您按下方向盘语音键或向车机 Gemini 说「打电话给某某某／09xx…」，系统会判定车机 App 可拨号并自动接手，实时传递给配对手机，由手机端调用原始系统电话 App 拨出（手机端模式自动停用拦截，绝不影响日常通话）。",
                            "[📞 Car Voice Dialing -> Smartphone Native Phone App] DX-Car registers phone-call capability on the Car Box! When you press the steering-wheel voice button or ask Car Gemini to call someone, DX-Car intercepts the call seamlessly and forwards it to your paired Smartphone's original Phone App to dial (disabled automatically on phones so normal calls are never affected).",
                            "【📞 車載音声発信 → スマホ標準電話アプリで発信】車載側で通話ハンドラー機能を搭載！ステアリング音声ボタンや車載 Gemini で「〇〇に電話」と話すと、DX-Car が自動で受け取りペアリング済みスマホへ転送し、スマホの標準電話アプリから発信します。"
                        ),
                        str(
                            "【📍 手機分享地址 → 自動送車機啟動導航搜尋】在手機 Google 地圖、LINE 或任何 App 點「分享 → DX-Car」，若是地圖連結、座標或地址，手機端會直接解析並發送給車機啟動器立即啟動地圖搜尋（也可在橫幅一鍵改開 Google 地圖或其他導航）；若分享的不是地址（如貼文／聊天），則自動觸發 Gemini 判斷！",
                            "【📍 手机分享地址 → 自动送车机启动导航搜索】在手机 Google 地图、微信或任意 App 点「分享 → DX-Car」，若是地图链接、坐标或地址，手机端直接解析并发送给车机启动器立即启动地图搜索（也可在横幅一键改开 Google 地图或其他导航）；若分享的不是地址（如帖子／聊天），则自动触发 Gemini 判断！",
                            "[📍 Phone Share Address -> Auto Car Map Search] Tap Share → DX-Car from Google Maps, LINE, or any phone app: map links, coordinates, and street addresses are sent straight to the Car to launch map search immediately (with 1-tap switch to Google Maps/other nav apps). Non-address text automatically triggers Gemini to analyze!",
                            "【📍 スマホから住所共有 → 車載ナビ自動検索】Google マップや LINE 等から「共有 → DX-Car」を選ぶと、地図リンクや住所は即座に車載機へ送信され地図検索を開始します。住所以外のテキストは自動で Gemini 判定を起動します！"
                        ),
                        str(
                            "【🖼️ 手機分享圖片 → 自動傳遞至車機觸發 Gemini】在手機相簿、LINE、Instagram 或截圖畫面點「分享圖片 → DX-Car」，手機會自動壓縮並傳遞圖片給車機端，由車機自動喚起 Google Gemini 進行影像判讀（例如辨識截圖中的餐廳店名、地址或招牌資訊）！",
                            "【🖼️ 手机分享图片 → 自动传递至车机触发 Gemini】在手机相册、微信、小红书或截图界面点「分享图片 → DX-Car」，手机会自动压缩并传递图片给车机端，由车机自动唤起 Google Gemini 进行图像判读（例如识别截图中的餐厅店名、地址或招牌信息）！",
                            "[🖼️ Phone Share Image -> Auto Send to Car Gemini] Share any photo or screenshot (restaurant info, address card, street sign) to DX-Car on your phone — it automatically transfers the image to the Car Box and triggers Car Gemini to inspect and identify it!",
                            "【🖼️ スマホから画像共有 → 車載 Gemini 自動起動】スマホの写真やスクリーンショットを「共有 → DX-Car」で送ると、自動で車載機へ転送され車載 Google Gemini を起動して店名・住所などの画像解析を行います！"
                        )
                    ),
                    highlightNote = str(
                        "✓ 已強化藍牙自動重連與手機端桌面捷徑／下拉快捷鍵一鍵重連，通知推播與遠端控制持續穩定不中斷！",
                        "✓ 已强化蓝牙自动重连与手机端桌面快捷方式／下拉快捷键一键重连，通知推送与远程控制持续稳定不中断！",
                        "✓ Enhanced Bluetooth auto-reconnect + Phone Home Shortcut & Quick Settings Tile for uninterrupted syncing!",
                        "✓ Bluetooth 自動再接続とスマホのホームショートカット／クイック設定タイルを強化し、安定した同期を維持します！"
                    ),
                    onClose = { onActiveTutorialChange(null) }
                )
            }

            CarSettingsTutorialTopic.STOCK_DOCK_CONFLICT -> {
                ExtraLargeTutorialModal(
                    title = str(
                        "⚙️ 關閉原車啟動器 Dock 與懸浮球 · 教學",
                        "⚙️ 关闭原车启动器 Dock 与悬浮球 · 教程",
                        "⚙️ Disable Stock Launcher Dock & Floating Ball · Guide",
                        "⚙️ 純正ドック＆フローティングボールの無効化 · ガイド"
                    ),
                    steps = listOf(
                        str(
                            "當您開啟 DX-Car Launcher 的「常駐懸浮 Dock（永久懸浮或跨啟動器懸浮）」時，若車機盒原本自帶左側欄或白色懸浮球，兩個懸浮層可能會重疊。",
                            "当您开启 DX-Car Launcher 的「常驻悬浮 Dock（永久悬浮或跨启动器悬浮）」时，若车机盒原本自带左侧栏或白色悬浮球，两个悬浮层可能会重叠。",
                            "When DX-Car's Persistent Floating Dock is enabled across apps/launchers, the AI Box's built-in sidebar or white assistive ball may overlap with it.",
                            "DX-Car の「常駐フローティングドック」を有効にした際、車載ボックス標準の左サイドバーや白いアシストボールがあると重なる場合があります。"
                        ),
                        str(
                            "請點擊下方「前往車機盒設定頁面」按鈕（或開啟車機盒內建的「車機設定／輔助觸控／懸浮球」App）。",
                            "请点击下方「前往车机盒设置页面」按钮（或开启车机盒内置的「车机设置／辅助触控／悬浮球」App）。",
                            "Tap [Open AI Box Vendor Settings] below (or open your AI Box's built-in Car Settings / Assistive Touch / Float Ball app).",
                            "下の「車載ボックス設定を開く」ボタンを押すか、端末内蔵の「車載設定／Assistive Touch／フローティングボール」アプリを開いてください。"
                        ),
                        str(
                            "將原車系統的「懸浮按鈕（Assistive Touch／小白點）」與「原廠導航側邊欄」關閉，即可享受乾淨無遮擋的 AA Coolwalk 常駐 Dock！",
                            "将原车系统的「悬浮按钮（Assistive Touch／小白点）」与「原厂导航侧边栏」关闭，即可享受干净无遮挡的 AA Coolwalk 常驻 Dock！",
                            "Turn off the stock [Assistive Touch / Floating Ball] and [Stock Navigation Sidebar] for a clean, unobstructed AA Coolwalk Dock!",
                            "純正システムの「フローティングボタン（Assistive Touch）」と「純正ナビサイドバー」をオフにすると、すっきり快適に常駐ドックを使えます！"
                        )
                    ),
                    actionButtonLabel = str(
                        "立即前往車機盒設定頁面",
                        "立即前往车机盒设置页面",
                        "Open AI Box Vendor Settings Now",
                        "今すぐ車載ボックス設定を開く"
                    ),
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
    label: String = str("教學", "教程", "Guide", "ガイド"),
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
                    Icon(Icons.Rounded.Remove, contentDescription = str("減少15分", "减少15分", "-15 min", "-15分"), tint = colors.primaryText, modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = { onDeltaMinutes(15) }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Rounded.Add, contentDescription = str("增加15分", "增加15分", "+15 min", "+15分"), tint = colors.primaryText, modifier = Modifier.size(18.dp))
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
            text = if (granted) str("已就緒", "已就绪", "Granted", "許可済み") else str("前往開啟", "前往开启", "Enable", "設定を開く"),
            color = if (granted) colors.statusConnected else colors.accent,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

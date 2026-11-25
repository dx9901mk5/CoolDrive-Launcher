package com.dxcar.launcher.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Splitscreen
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dxcar.launcher.data.SplitLaunchEngine
import com.dxcar.launcher.data.SplitRatioPreset
import com.dxcar.launcher.data.SplitScreenShortcut
import com.dxcar.launcher.i18n.DxCarI18n
import com.dxcar.launcher.i18n.DxCarI18n.str
import com.dxcar.launcher.system.AppCategory
import com.dxcar.launcher.system.InstalledAppInfo
import com.dxcar.launcher.system.PermissionHelper
import com.dxcar.launcher.system.StockSplitScanResult
import com.dxcar.launcher.system.StockSplitScreenBridge
import com.dxcar.launcher.theme.LocalDxCarColors
import java.util.UUID

/**
 * Dual-icon badge representing a saved Split-Screen Shortcut (like [Google Maps | NetEase Music]),
 * used inside both the App Drawer and the 6 Custom Home Shortcut Slots!
 */
@Composable
fun DualSplitShortcutIcon(
    shortcut: SplitScreenShortcut,
    appsByPackage: Map<String, InstalledAppInfo>,
    sizeDp: Int = 54,
    modifier: Modifier = Modifier
) {
    val leftApp = appsByPackage[shortcut.leftPackage]
    val rightApp = appsByPackage[shortcut.rightPackage]
    val leftBmp = remember(leftApp?.icon) { leftApp?.icon?.asImageBitmap() }
    val rightBmp = remember(rightApp?.icon) { rightApp?.icon?.asImageBitmap() }
    val miniSize = (sizeDp * 0.42f).dp

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(sizeDp.dp)
            .clip(RoundedCornerShape((sizeDp * 0.26f).dp))
            .background(Color(0xFFF3F6FB))
            .border(1.dp, Color(0xFFD5DFEE), RoundedCornerShape((sizeDp * 0.26f).dp))
            .padding(3.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            if (leftBmp != null) {
                Image(
                    bitmap = leftBmp,
                    contentDescription = leftApp?.label,
                    modifier = Modifier
                        .size(miniSize)
                        .clip(RoundedCornerShape(6.dp))
                )
            } else {
                Icon(Icons.Rounded.Splitscreen, contentDescription = null, tint = Color(0xFF1A73E8), modifier = Modifier.size(miniSize))
            }
            if (rightBmp != null) {
                Image(
                    bitmap = rightBmp,
                    contentDescription = rightApp?.label,
                    modifier = Modifier
                        .size(miniSize)
                        .clip(RoundedCornerShape(6.dp))
                )
            } else {
                Icon(Icons.Rounded.Splitscreen, contentDescription = null, tint = Color(0xFF22C55E), modifier = Modifier.size(miniSize))
            }
        }
    }
}

/**
 * Responsive Split-Screen Shortcut Creator:
 * - Defaults to Freeform Exact-Ratio Dual Windows (respects 3:7..7:3 ratios & Dock margins)
 * - Displays Freeform system capability status + direct link to Developer Options if disabled
 * - Includes category filter tabs in App picker & instant "Test Launch" preview button!
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SplitShortcutCreatorDialog(
    apps: List<InstalledAppInfo>,
    onSaveShortcut: (SplitScreenShortcut) -> Unit,
    onTestLaunchShortcut: ((SplitScreenShortcut) -> Unit)? = null,
    onPickSystemShortcut: (() -> Unit)? = null,
    onDismiss: () -> Unit
) {
    val colors = LocalDxCarColors.current
    val context = LocalContext.current
    val freeformSupported = remember { PermissionHelper.isFreeformSupported(context) }

    var selectedRatio by remember { mutableStateOf(SplitRatioPreset.R_7_3) }
    var selectedEngine by remember { mutableStateOf(SplitLaunchEngine.NATIVE_SPLIT_SCREEN) }
    var leftApp by remember { mutableStateOf<InstalledAppInfo?>(null) }
    var rightApp by remember { mutableStateOf<InstalledAppInfo?>(null) }
    var pickingSide by remember { mutableStateOf<String?>(null) }
    var pickerCategory by remember { mutableStateOf(AppCategory.ALL) }
    var stockScanResult by remember { mutableStateOf<StockSplitScanResult?>(null) }

    val animatedLeftWeight by animateFloatAsState(
        targetValue = selectedRatio.leftWeight,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "splitCreatorLeftWeight"
    )
    val animatedRightWeight = (1f - animatedLeftWeight).coerceAtLeast(0.25f)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.74f))
            .clickable { onDismiss() },
        contentAlignment = Alignment.Center
    ) {
        Surface(
            color = colors.cardSurface,
            shape = RoundedCornerShape(26.dp),
            shadowElevation = 24.dp,
            modifier = Modifier
                .fillMaxWidth(0.90f)
                .fillMaxHeight(0.90f)
                .border(1.2.dp, colors.cardBorder, RoundedCornerShape(26.dp))
                .clickable(enabled = false) {}
        ) {
            AnimatedContent(
                targetState = pickingSide,
                transitionSpec = {
                    (scaleIn(initialScale = 0.95f, animationSpec = tween(200)) + fadeIn(tween(180))) togetherWith
                        (scaleOut(targetScale = 0.95f, animationSpec = tween(140)) + fadeOut(tween(130)))
                },
                label = "splitCreatorStep"
            ) { side ->
                if (side != null) {
                    val filteredApps = remember(apps, pickerCategory) {
                        if (pickerCategory == AppCategory.ALL) apps
                        else apps.filter { it.category == pickerCategory }
                    }
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(18.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (side == "LEFT") {
                                    str("選擇左側分割應用", "选择左侧分屏应用", "Select Left Split App", "左側の分割アプリを選択")
                                } else {
                                    str("選擇右側分割應用", "选择右侧分屏应用", "Select Right Split App", "右側の分割アプリを選択")
                                },
                                color = colors.primaryText,
                                fontSize = 19.sp,
                                fontWeight = FontWeight.Bold
                            )
                            IconButton(onClick = { pickingSide = null }) {
                                Icon(
                                    Icons.Rounded.Close,
                                    contentDescription = str("返回", "返回", "Back", "戻る"),
                                    tint = colors.primaryText
                                )
                            }
                        }

                        Spacer(Modifier.height(6.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            AppCategory.entries.forEach { cat ->
                                val selected = pickerCategory == cat
                                val catLabel = when (cat) {
                                    AppCategory.ALL -> str("全部", "全部", "All", "すべて")
                                    AppCategory.NAVIGATION -> str("導航", "导航", "Nav", "ナビ")
                                    AppCategory.MEDIA -> str("影音", "影音", "Media", "メディア")
                                    AppCategory.COMMUNICATION -> str("通訊", "通讯", "Comms", "通話・連絡")
                                    AppCategory.SYSTEM -> str("系統", "系统", "System", "システム")
                                }
                                Surface(
                                    color = if (selected) colors.accent else colors.cardSurfaceElevated,
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier
                                        .border(1.dp, if (selected) colors.accent else colors.cardBorder, RoundedCornerShape(14.dp))
                                        .bouncyClickable { pickerCategory = cat }
                                ) {
                                    Text(
                                        text = catLabel,
                                        color = if (selected) colors.onAccent else colors.secondaryText,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(8.dp))
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = 128.dp),
                            contentPadding = PaddingValues(top = 6.dp, bottom = 20.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                            modifier = Modifier
                                .fillMaxSize()
                                .springBounceOverscroll(maxOverscrollPx = 170f)
                        ) {
                            items(filteredApps, key = { it.packageName }) { app ->
                                val bmp = remember(app.icon) { app.icon.asImageBitmap() }
                                Surface(
                                    color = colors.cardSurfaceElevated,
                                    shape = RoundedCornerShape(18.dp),
                                    modifier = Modifier
                                        .border(1.dp, colors.cardBorder, RoundedCornerShape(18.dp))
                                        .clip(RoundedCornerShape(18.dp))
                                        .bouncyClickable(pressedScale = 0.89f) {
                                            if (side == "LEFT") leftApp = app else rightApp = app
                                            pickingSide = null
                                        }
                                ) {
                                    Column(
                                        modifier = Modifier.padding(12.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Image(
                                            bitmap = bmp,
                                            contentDescription = app.label,
                                            modifier = Modifier
                                                .size(56.dp)
                                                .clip(RoundedCornerShape(14.dp))
                                        )
                                        Spacer(Modifier.height(6.dp))
                                        Text(
                                            text = app.label,
                                            color = colors.primaryText,
                                            fontSize = 13.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 20.dp, vertical = 14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Top Header
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = str(
                                    "分屏設定 · 建立左右分割捷徑",
                                    "分屏设置 · 新建左右分屏快捷方式",
                                    "Split-Screen · Create App Pair Shortcut",
                                    "分割画面設定 · 左右分割ショートカット作成"
                                ),
                                color = colors.primaryText,
                                fontSize = 19.sp,
                                fontWeight = FontWeight.Bold
                            )
                            IconButton(onClick = onDismiss) {
                                Icon(
                                    Icons.Rounded.Close,
                                    contentDescription = str("關閉", "关闭", "Close", "閉じる"),
                                    tint = colors.primaryText
                                )
                            }
                        }

                        // STOCK LAUNCHER SPLIT-SCREEN SCANNER & IMPORTER ("讀取原車啟動器已創立好的分割視窗")
                        Surface(
                            color = colors.accent.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.2.dp, colors.accent.copy(alpha = 0.7f), RoundedCornerShape(16.dp))
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = str(
                                        "📥 讀取／匯入原車啟動器已創立好的分割視窗",
                                        "📥 读取／导入原车启动器已创建好的分屏窗口",
                                        "📥 Import Split-Screen Pairs from Stock Launcher",
                                        "📥 純正ランチャーの作成済み分割画面を読み込み・インポート"
                                    ),
                                    color = colors.primaryText,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                val scanRes = stockScanResult
                                val scanDescription = if (scanRes == null) {
                                    str(
                                        "可自動掃描原車桌面／系統已儲存的分屏組合（設為預設桌面後亦可讀取系統已釘選 App Pair），或直接挑選原車系統捷徑匯入！",
                                        "可自动扫描原车桌面／系统已保存的分屏组合（设为默认桌面后亦可读取系统已固定 App Pair），或直接挑选原车系统快捷方式导入！",
                                        "Scan your stock launcher or system for saved split-screen pairs (set as Default Home to also read pinned App Pairs), or pick a system shortcut directly.",
                                        "純正ホームやシステムに保存された分割ペアを自動スキャン（デフォルトホーム設定でピン留めペアも読込可）、またはシステムショートカットを直接選択してインポートできます。"
                                    )
                                } else if (scanRes.discoveredShortcuts.isNotEmpty()) {
                                    str(
                                        scanRes.detailReportZh,
                                        DxCarI18n.toSimplifiedChinese(scanRes.detailReportZh),
                                        "✓ Found ${scanRes.discoveredShortcuts.size} saved split-screen pair(s) from stock launcher / system settings!",
                                        "✓ 純正ランチャー／システム設定から ${scanRes.discoveredShortcuts.size} 件の分割ペアを検出しました！"
                                    )
                                } else if (scanRes.detailReportZh.contains("⚠️")) {
                                    str(
                                        scanRes.detailReportZh,
                                        DxCarI18n.toSimplifiedChinese(scanRes.detailReportZh),
                                        "⚠️ Your stock launcher stores split pairs in a private system sandbox (unreadable without Root). Tap 'Pick System Split Shortcut' or create a pair below using our AOSP split engine!",
                                        "⚠️ 純正ランチャーの分割データは非公開領域に保護されています。「システム分割ショートカットを選択」を使うか、下の AOSP 分割エンジンでペアを作成してください。"
                                    )
                                } else {
                                    str(
                                        scanRes.detailReportZh,
                                        DxCarI18n.toSimplifiedChinese(scanRes.detailReportZh),
                                        "ℹ️ No public split-screen pairs found in the stock launcher yet. Tap 'Pick System Split Shortcut' to import, or create a new pair below.",
                                        "ℹ️ 純正ランチャーの公開データに保存済み分割ペアは見つかりませんでした。「システム分割ショートカットを選択」でインポートするか、下で新しく作成してください。"
                                    )
                                }
                                Text(
                                    text = scanDescription,
                                    color = colors.secondaryText,
                                    fontSize = 12.sp
                                )
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Button(
                                        onClick = {
                                            stockScanResult = StockSplitScreenBridge.scanStockSplitShortcuts(context, apps)
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = colors.accent, contentColor = colors.onAccent),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Text(
                                            str("🔍 掃描原車已建立分屏", "🔍 扫描原车已建分屏", "🔍 Scan Stock Split Pairs", "🔍 純正の分割ペアをスキャン"),
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    if (onPickSystemShortcut != null) {
                                        OutlinedButton(
                                            onClick = onPickSystemShortcut,
                                            shape = RoundedCornerShape(12.dp)
                                        ) {
                                            Text(
                                                str("📂 挑選原車系統分屏捷徑", "📂 挑选原车系统分屏快捷方式", "📂 Pick System Split Shortcut", "📂 システム分割ショートカットを選択"),
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                                stockScanResult?.discoveredShortcuts?.forEach { sc ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(colors.cardSurfaceElevated)
                                            .padding(horizontal = 10.dp, vertical = 7.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "⊞ ${sc.label}",
                                            color = colors.primaryText,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            if (onTestLaunchShortcut != null) {
                                                OutlinedButton(
                                                    onClick = { onTestLaunchShortcut(sc) },
                                                    shape = RoundedCornerShape(10.dp)
                                                ) {
                                                    Text(str("直接開啟", "直接开启", "Launch", "今すぐ開く"), fontSize = 11.sp)
                                                }
                                            }
                                            Button(
                                                onClick = { onSaveShortcut(sc) },
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF22C55E), contentColor = Color.White),
                                                shape = RoundedCornerShape(10.dp)
                                            ) {
                                                Text(str("匯入儲存", "导入保存", "Import & Save", "インポート保存"), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // System Freeform Support Health Hint
                        if (!freeformSupported && selectedEngine == SplitLaunchEngine.FREEFORM_EXACT_RATIO) {
                            Surface(
                                color = Color(0xFFF59E0B).copy(alpha = 0.14f),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = str(
                                            "偵測到此車機系統可能未開啟「自由視窗 / 強制調整活動大小」，若測試無法左右並排請開啟開發者選項內該兩項設定",
                                            "检测到此车机系统可能未开启「自由窗口 / 强制将活动设为可调整大小」，若测试无法左右并排请开启开发者选项内该两项设置",
                                            "Freeform windows / Force activities to be resizable may be disabled on this device. Enable both in Developer Options if side-by-side launch fails.",
                                            "この端末で「フリーフォームウィンドウ／アクティビティをサイズ変更可能にする」が無効の可能性があります。並べて表示できない場合は開発者向けオプションで有効にしてください。"
                                        ),
                                        color = colors.primaryText,
                                        fontSize = 11.sp,
                                        modifier = Modifier.weight(1f).padding(end = 8.dp)
                                    )
                                    OutlinedButton(
                                        onClick = { PermissionHelper.openDeveloperSettings(context) },
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Icon(Icons.Rounded.Settings, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text(str("開發者設定", "开发者选项", "Developer Options", "開発者向けオプション"), fontSize = 11.sp)
                                    }
                                }
                            }
                        }

                        // Ratio Pills
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            SplitRatioPreset.entries.forEach { ratio ->
                                val selected = ratio == selectedRatio
                                Surface(
                                    color = if (selected) Color(0xFF22C55E) else colors.cardSurfaceElevated,
                                    shape = RoundedCornerShape(20.dp),
                                    modifier = Modifier
                                        .border(
                                            width = 1.2.dp,
                                            color = if (selected) Color(0xFF22C55E) else colors.cardBorder,
                                            shape = RoundedCornerShape(20.dp)
                                        )
                                        .bouncyClickable(pressedScale = 0.89f) { selectedRatio = ratio }
                                ) {
                                    Text(
                                        text = ratio.label,
                                        color = if (selected) Color.White else colors.primaryText,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp)
                                    )
                                }
                            }
                        }

                        // Live Animated Split Preview Boxes (Left + Right)
                        val leftPct = (selectedRatio.leftWeight * 100).toInt()
                        val rightPct = ((1f - selectedRatio.leftWeight) * 100).toInt()
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(165.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            SplitSlotBox(
                                sideLabel = str(
                                    "點擊選擇左側 ($leftPct%)",
                                    "点击选择左侧 ($leftPct%)",
                                    "Tap to Select Left ($leftPct%)",
                                    "左側アプリを選択 ($leftPct%)"
                                ),
                                app = leftApp,
                                onClick = { pickingSide = "LEFT" },
                                modifier = Modifier
                                    .weight(animatedLeftWeight)
                                    .fillMaxHeight()
                            )
                            SplitSlotBox(
                                sideLabel = str(
                                    "點擊選擇右側 ($rightPct%)",
                                    "点击选择右侧 ($rightPct%)",
                                    "Tap to Select Right ($rightPct%)",
                                    "右側アプリを選択 ($rightPct%)"
                                ),
                                app = rightApp,
                                onClick = { pickingSide = "RIGHT" },
                                modifier = Modifier
                                    .weight(animatedRightWeight)
                                    .fillMaxHeight()
                            )
                        }

                        // Engine Selection & Action Buttons (Never clipped on compact car heights!)
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                SplitLaunchEngine.entries.forEach { eng ->
                                    val selected = eng == selectedEngine
                                    val engLabel = when (eng) {
                                        SplitLaunchEngine.NATIVE_SPLIT_SCREEN -> str(
                                            "AOSP 系統原生可拖動分屏（推薦）",
                                            "AOSP 系统原生可拖动分屏（推荐）",
                                            "AOSP Native Draggable Split-Screen (Recommended)",
                                            "AOSP ネイティブ分割画面（推奨）"
                                        )
                                        SplitLaunchEngine.STOCK_LAUNCHER_SHORTCUT -> str(
                                            "原車啟動器已建立分屏捷徑",
                                            "原车启动器已建分屏快捷方式",
                                            "Stock Launcher Split Shortcut",
                                            "純正ランチャー分割ショートカット"
                                        )
                                        SplitLaunchEngine.FREEFORM_EXACT_RATIO -> str(
                                            "Freeform 自由雙視窗",
                                            "Freeform 自由双窗口",
                                            "Freeform Dual Windows",
                                            "Freeform デュアルウィンドウ"
                                        )
                                    }
                                    Surface(
                                        color = if (selected) colors.accent else colors.cardSurfaceElevated,
                                        shape = RoundedCornerShape(14.dp),
                                        modifier = Modifier
                                            .border(
                                                1.dp,
                                                if (selected) colors.accent else colors.cardBorder,
                                                RoundedCornerShape(14.dp)
                                            )
                                            .bouncyClickable(pressedScale = 0.91f) { selectedEngine = eng }
                                    ) {
                                        Text(
                                            text = engLabel,
                                            color = if (selected) colors.onAccent else colors.secondaryText,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                        )
                                    }
                                }
                            }

                            val canSave = leftApp != null && rightApp != null
                            val buildShortcut: () -> SplitScreenShortcut? = {
                                val l = leftApp
                                val r = rightApp
                                if (l == null || r == null) null
                                else SplitScreenShortcut(
                                    id = UUID.randomUUID().toString().take(8),
                                    leftPackage = l.packageName,
                                    rightPackage = r.packageName,
                                    ratio = selectedRatio,
                                    engine = selectedEngine,
                                    label = "${l.label}/${r.label}"
                                )
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (onTestLaunchShortcut != null) {
                                    OutlinedButton(
                                        enabled = canSave,
                                        onClick = { buildShortcut()?.let(onTestLaunchShortcut) },
                                        shape = RoundedCornerShape(16.dp)
                                    ) {
                                        Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text(str("測試分屏", "测试分屏", "Test Split", "テスト起動"))
                                    }
                                }

                                Button(
                                    onClick = { buildShortcut()?.let(onSaveShortcut) },
                                    enabled = canSave,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFF22C55E),
                                        contentColor = Color.White
                                    ),
                                    shape = RoundedCornerShape(16.dp)
                                ) {
                                    Text(
                                        text = str("儲存為捷徑", "保存为快捷方式", "Save Shortcut", "ショートカット保存"),
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SplitSlotBox(
    sideLabel: String,
    app: InstalledAppInfo?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalDxCarColors.current
    val bmp = remember(app?.icon) { app?.icon?.asImageBitmap() }

    Surface(
        color = colors.cardSurfaceElevated.copy(alpha = 0.85f),
        shape = RoundedCornerShape(22.dp),
        modifier = modifier
            .border(
                width = 1.5.dp,
                color = if (app != null) colors.accent else colors.cardBorder,
                shape = RoundedCornerShape(22.dp)
            )
            .clip(RoundedCornerShape(22.dp))
            .bouncyClickable(pressedScale = 0.96f, onClick = onClick)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            if (bmp != null && app != null) {
                Image(
                    bitmap = bmp,
                    contentDescription = app.label,
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(14.dp))
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = app.label,
                    color = colors.primaryText,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            } else {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(13.dp))
                        .background(Color(0xFF22C55E))
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = sideLabel,
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = sideLabel,
                color = colors.secondaryText,
                fontSize = 11.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}

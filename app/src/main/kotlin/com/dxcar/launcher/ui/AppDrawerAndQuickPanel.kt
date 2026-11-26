package com.dxcar.launcher.ui

import android.annotation.SuppressLint
import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.Display
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.Brightness6
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FullscreenExit
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Splitscreen
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.ViewInAr
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import com.dxcar.launcher.i18n.DxCarI18n
import com.dxcar.launcher.i18n.DxCarI18n.str
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.dxcar.launcher.data.AppFolderItem
import com.dxcar.launcher.data.SplitScreenShortcut
import com.dxcar.launcher.system.AppCategory
import com.dxcar.launcher.system.InstalledAppInfo
import com.dxcar.launcher.system.SystemStatus
import com.dxcar.launcher.theme.LocalDxCarColors

private sealed interface DragRemoveTarget {
    val label: String
    data class SplitShortcutItem(val shortcut: SplitScreenShortcut) : DragRemoveTarget {
        override val label: String
            get() = shortcut.label.ifBlank {
                DxCarI18n.tr("分屏捷徑", "分屏快捷方式", "Split Shortcut", "分割ショートカット")
            }
    }
    data class FolderTarget(val folder: AppFolderItem) : DragRemoveTarget {
        override val label: String get() = folder.name
    }
    data class InstalledAppTarget(val app: InstalledAppInfo) : DragRemoveTarget {
        override val label: String get() = app.label
    }
}

private data class PendingFolderCreation(
    val firstApp: InstalledAppInfo,
    val secondApp: InstalledAppInfo,
    val suggestedName: String
)

private fun suggestDefaultFolderName(a: InstalledAppInfo, b: InstalledAppInfo): String {
    val combined = "${a.packageName} ${a.label} ${b.packageName} ${b.label}".lowercase()
    val musicWords = listOf("music", "spotify", "kkbox", "netease", "cloudmusic", "音樂", "網易", "podcast", "radio")
    val videoWords = listOf("youtube", "netflix", "disney", "bilibili", "twitch", "vlc", "video", "影", "視訊")
    return when {
        a.category == AppCategory.NAVIGATION && b.category == AppCategory.NAVIGATION ->
            DxCarI18n.tr("導航地圖", "导航地图", "Navigation", "ナビ・地図")
        musicWords.any { combined.contains(it) } && a.category == AppCategory.MEDIA && b.category == AppCategory.MEDIA ->
            DxCarI18n.tr("音樂播放", "音乐播放", "Music", "音楽")
        videoWords.any { combined.contains(it) } && a.category == AppCategory.MEDIA && b.category == AppCategory.MEDIA ->
            DxCarI18n.tr("影音娛樂", "影音娱乐", "Video & Media", "動画・エンタメ")
        a.category == AppCategory.MEDIA && b.category == AppCategory.MEDIA ->
            DxCarI18n.tr("影音音樂", "影音音乐", "Media & Music", "メディア・音楽")
        a.category == AppCategory.COMMUNICATION && b.category == AppCategory.COMMUNICATION ->
            DxCarI18n.tr("通訊社群", "通讯社交", "Social & Comms", "通話・SNS")
        a.category == AppCategory.SYSTEM && b.category == AppCategory.SYSTEM ->
            DxCarI18n.tr("系統工具", "系统工具", "System Tools", "システムツール")
        a.category == AppCategory.NAVIGATION || b.category == AppCategory.NAVIGATION ->
            DxCarI18n.tr("行車常用", "行车常用", "Driving Apps", "ドライブ定番")
        else ->
            DxCarI18n.tr("常用工具", "常用工具", "Utilities", "便利ツール")
    }
}

/**
 * Full AA App Drawer launched from the 9-dot button:
 * - Long-press & drag upward to "✕ 移除" -> Always asks user confirmation before uninstalling App / removing shortcut!
 * - Long-press & drag an App onto another App -> Automatically forms a Folder with smart suggested name & naming dialog!
 * - Long-press & drag an App onto an existing Folder -> Adds App directly into that Folder!
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun AppDrawerScreen(
    apps: List<InstalledAppInfo>,
    splitShortcuts: List<SplitScreenShortcut>,
    appFolders: List<AppFolderItem> = emptyList(),
    hasPendingUpdate: Boolean,
    onLaunchApp: (String) -> Unit,
    onLaunchSplitShortcut: (SplitScreenShortcut) -> Unit,
    onRemoveSplitShortcut: (SplitScreenShortcut) -> Unit,
    onUninstallApp: (InstalledAppInfo) -> Unit,
    onOpenAppInfo: (InstalledAppInfo) -> Unit = {},
    onCreateFolder: (String, List<String>) -> Unit = { _, _ -> },
    onAddAppToFolder: (String, String) -> Unit = { _, _ -> },
    onRenameFolder: (String, String) -> Unit = { _, _ -> },
    onRemoveAppFromFolder: (String, String) -> Unit = { _, _ -> },
    onDeleteFolder: (String) -> Unit = {},
    onOpenSplitCreator: () -> Unit,
    onLaunchYoutube: () -> Unit,
    onOpenSettings: () -> Unit,
    onCloseDrawer: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalDxCarColors.current
    val density = LocalDensity.current
    var selectedCategory by remember { mutableStateOf(AppCategory.ALL) }

    val appsByPkg = remember(apps) { apps.associateBy { it.packageName } }
    val folderedPackages = remember(appFolders) {
        appFolders.flatMap { it.packages }.toSet()
    }
    val filtered = remember(apps, selectedCategory, folderedPackages) {
        if (selectedCategory == AppCategory.ALL) {
            apps.filterNot { folderedPackages.contains(it.packageName) }
        } else {
            apps.filter { it.category == selectedCategory }
        }
    }

    var rootPosition by remember { mutableStateOf(Offset.Zero) }
    var draggingTarget by remember { mutableStateOf<DragRemoveTarget?>(null) }
    var dragFingerInRoot by remember { mutableStateOf(Offset.Zero) }
    val tileBoundsInRoot = remember { mutableStateMapOf<String, Rect>() }

    var confirmRemoveTarget by remember { mutableStateOf<DragRemoveTarget?>(null) }
    var pendingFolderCreation by remember { mutableStateOf<PendingFolderCreation?>(null) }
    var openedFolderId by remember { mutableStateOf<String?>(null) }
    var renamingFolder by remember { mutableStateOf<AppFolderItem?>(null) }

    val dropThresholdPx = with(density) { 108.dp.toPx() }
    val isHoveringRemove = draggingTarget != null && (dragFingerInRoot.y - rootPosition.y) <= dropThresholdPx

    // Detect overlap with another App or Folder tile when dragging an InstalledApp
    val hoveredOverlapKey: String? = remember(draggingTarget, dragFingerInRoot, isHoveringRemove, tileBoundsInRoot.size) {
        val draggedApp = (draggingTarget as? DragRemoveTarget.InstalledAppTarget)?.app
        if (draggedApp == null || isHoveringRemove) {
            null
        } else {
            val selfKey = "app:${draggedApp.packageName}"
            tileBoundsInRoot.entries.firstOrNull { (key, rect) ->
                key != selfKey && rect.contains(dragFingerInRoot)
            }?.key
        }
    }

    fun finishAppDragDrop(draggedApp: InstalledAppInfo) {
        val dropInZone = (dragFingerInRoot.y - rootPosition.y) <= dropThresholdPx
        val overlapKey = hoveredOverlapKey
        draggingTarget = null
        when {
            dropInZone -> {
                confirmRemoveTarget = DragRemoveTarget.InstalledAppTarget(draggedApp)
            }
            overlapKey != null && overlapKey.startsWith("folder:") -> {
                val folderId = overlapKey.removePrefix("folder:")
                onAddAppToFolder(folderId, draggedApp.packageName)
            }
            overlapKey != null && overlapKey.startsWith("app:") -> {
                val otherPkg = overlapKey.removePrefix("app:")
                val otherApp = appsByPkg[otherPkg]
                if (otherApp != null && otherApp.packageName != draggedApp.packageName) {
                    pendingFolderCreation = PendingFolderCreation(
                        firstApp = otherApp,
                        secondApp = draggedApp,
                        suggestedName = suggestDefaultFolderName(otherApp, draggedApp)
                    )
                }
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.canvasBackground)
            .onGloballyPositioned { coords -> rootPosition = coords.positionInRoot() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 18.dp, vertical = 14.dp)
        ) {
            // HEADER
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = str("應用程式", "应用", "Apps", "アプリ"),
                        color = colors.primaryText,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.width(8.dp))
                    AppCategory.entries.forEach { cat ->
                        val selected = cat == selectedCategory
                        val catLabel = when (cat) {
                            AppCategory.ALL -> str("全部", "全部", "All", "すべて")
                            AppCategory.NAVIGATION -> str("導航", "导航", "Nav", "ナビ")
                            AppCategory.MEDIA -> str("影音", "影音", "Media", "メディア")
                            AppCategory.COMMUNICATION -> str("通訊", "通讯", "Comms", "通話・連絡")
                            AppCategory.SYSTEM -> str("系統", "系统", "System", "システム")
                        }
                        Surface(
                            color = if (selected) colors.accent else colors.cardSurface,
                            shape = RoundedCornerShape(18.dp),
                            modifier = Modifier
                                .border(
                                    1.dp,
                                    if (selected) colors.accent else colors.cardBorder,
                                    RoundedCornerShape(18.dp)
                                )
                                .bouncyClickable(pressedScale = 0.90f) { selectedCategory = cat }
                        ) {
                            Text(
                                text = catLabel,
                                color = if (selected) colors.onAccent else colors.secondaryText,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                            )
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        color = colors.cardSurfaceElevated,
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier
                            .border(1.2.dp, colors.accent.copy(alpha = 0.7f), RoundedCornerShape(20.dp))
                            .bouncyClickable(pressedScale = 0.91f, onClick = onOpenSettings)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box {
                                Icon(
                                    imageVector = Icons.Rounded.Settings,
                                    contentDescription = str(
                                        "DX-Car Launcher 設定",
                                        "DX-Car Launcher 设置",
                                        "DX-Car Launcher Settings",
                                        "DX-Car Launcher 設定"
                                    ),
                                    tint = colors.accent,
                                    modifier = Modifier.size(20.dp)
                                )
                                if (hasPendingUpdate) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFFEF4444))
                                    )
                                }
                            }
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = if (hasPendingUpdate) {
                                    str("設定 · 有更新", "设置 · 有更新", "Settings · Update", "設定 · 更新あり")
                                } else {
                                    str("啟動器設定", "启动器设置", "Settings", "ランチャー設定")
                                },
                                color = colors.primaryText,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    IconButton(onClick = onCloseDrawer) {
                        Icon(
                            Icons.Rounded.Close,
                            contentDescription = str("返回首頁", "返回首页", "Back to Home", "ホームへ戻る"),
                            tint = colors.secondaryText
                        )
                    }
                }
            }

            Spacer(Modifier.height(6.dp))
            Text(
                text = str(
                    "點擊啟動 · 長按 App 重疊到另一 App 可建立資料夾 · 往上拖曳至「✕ 移除」會詢問確認後解除安裝",
                    "点击启动 · 长按应用重叠到另一应用可新建文件夹 · 往上拖曳至「✕ 移除」会询问确认后卸载",
                    "Tap to open · Hold & drop onto another app to create a folder · Drag up to '✕ Remove' to uninstall",
                    "タップで起動 · 長押しで他のアプリに重ねるとフォルダ作成 · 上の「✕ 削除」へドラッグでアンインストール"
                ),
                color = colors.secondaryText,
                fontSize = 12.sp
            )
            Spacer(Modifier.height(12.dp))

            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 148.dp),
                contentPadding = PaddingValues(top = 8.dp, bottom = 28.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                verticalArrangement = Arrangement.spacedBy(22.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .springBounceOverscroll(maxOverscrollPx = 190f)
            ) {
                item(key = "__dxcar_split_creator__") {
                    BuiltInDrawerTile(
                        label = str("分屏設定", "分屏设置", "Split-Screen", "分割画面設定"),
                        subLabel = str("AOSP 可拖動分屏", "AOSP 可拖动分屏", "AOSP Draggable Split", "AOSP 可変分割画面"),
                        icon = Icons.Rounded.Splitscreen,
                        tint = Color(0xFF22C55E),
                        badgeAlert = false,
                        onClick = onOpenSplitCreator
                    )
                }

                item(key = "__dxcar_settings__") {
                    BuiltInDrawerTile(
                        label = str(
                            "DX-Car Launcher 設定",
                            "DX-Car Launcher 设置",
                            "DX-Car Launcher Settings",
                            "DX-Car Launcher 設定"
                        ),
                        subLabel = if (hasPendingUpdate) {
                            str("有新版本", "有新版本", "Update Available", "新バージョンあり")
                        } else {
                            str("權限 · 風格 · 更新", "权限 · 风格 · 更新", "Permissions · Style · Update", "権限 · スタイル · 更新")
                        },
                        icon = Icons.Rounded.Settings,
                        tint = colors.accent,
                        badgeAlert = hasPendingUpdate,
                        onClick = onOpenSettings
                    )
                }

                if (selectedCategory == AppCategory.ALL) {
                    // User-Created Split-Screen Shortcuts
                    items(splitShortcuts, key = { "split_${it.id}" }) { sc ->
                        var tileOrigin by remember { mutableStateOf(Offset.Zero) }
                        val isBeingDragged = (draggingTarget as? DragRemoveTarget.SplitShortcutItem)?.shortcut?.id == sc.id

                        Surface(
                            color = colors.cardSurface,
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier
                                .alpha(if (isBeingDragged) 0.3f else 1f)
                                .border(1.2.dp, Color(0xFF22C55E).copy(alpha = 0.6f), RoundedCornerShape(20.dp))
                                .clip(RoundedCornerShape(20.dp))
                                .onGloballyPositioned { tileOrigin = it.positionInRoot() }
                                .bouncyClickable(pressedScale = 0.90f) { onLaunchSplitShortcut(sc) }
                                .pointerInput(sc.id) {
                                    detectDragGesturesAfterLongPress(
                                        onDragStart = { localOffset ->
                                            draggingTarget = DragRemoveTarget.SplitShortcutItem(sc)
                                            dragFingerInRoot = tileOrigin + localOffset
                                        },
                                        onDrag = { change, dragAmount ->
                                            change.consume()
                                            dragFingerInRoot += dragAmount
                                        },
                                        onDragEnd = {
                                            val dropInZone = (dragFingerInRoot.y - rootPosition.y) <= dropThresholdPx
                                            draggingTarget = null
                                            if (dropInZone) {
                                                confirmRemoveTarget = DragRemoveTarget.SplitShortcutItem(sc)
                                            }
                                        },
                                        onDragCancel = { draggingTarget = null }
                                    )
                                }
                        ) {
                            Box(modifier = Modifier.padding(12.dp)) {
                                Surface(
                                    color = Color(0xFF22C55E).copy(alpha = 0.22f),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.align(Alignment.TopEnd)
                                ) {
                                    Text(
                                        text = sc.ratio.label,
                                        color = Color(0xFF22C55E),
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                    )
                                }
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    DualSplitShortcutIcon(
                                        shortcut = sc,
                                        appsByPackage = appsByPkg,
                                        sizeDp = 68
                                    )
                                    Spacer(Modifier.height(10.dp))
                                    Text(
                                        text = sc.label.ifBlank {
                                            str("分屏捷徑", "分屏快捷方式", "Split Shortcut", "分割ショートカット")
                                        },
                                        color = colors.primaryText,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    }

                    // User-Created App Folders
                    items(appFolders, key = { "folder_${it.id}" }) { folder ->
                        val folderKey = "folder:${folder.id}"
                        var tileOrigin by remember { mutableStateOf(Offset.Zero) }
                        val isHoverTarget = hoveredOverlapKey == folderKey
                        val isBeingDragged = (draggingTarget as? DragRemoveTarget.FolderTarget)?.folder?.id == folder.id
                        val scale by animateFloatAsState(
                            targetValue = if (isHoverTarget) 1.08f else 1f,
                            animationSpec = tween(140),
                            label = "folderHoverScale"
                        )

                        DisposableEffect(folderKey) {
                            onDispose { tileBoundsInRoot.remove(folderKey) }
                        }

                        Surface(
                            color = if (isHoverTarget) colors.accent.copy(alpha = 0.22f) else colors.cardSurface,
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier
                                .graphicsLayer {
                                    scaleX = scale
                                    scaleY = scale
                                }
                                .alpha(if (isBeingDragged) 0.3f else 1f)
                                .border(
                                    width = if (isHoverTarget) 2.2.dp else 1.2.dp,
                                    color = if (isHoverTarget) colors.accent else colors.accent.copy(alpha = 0.55f),
                                    shape = RoundedCornerShape(20.dp)
                                )
                                .clip(RoundedCornerShape(20.dp))
                                .onGloballyPositioned { coords ->
                                    tileOrigin = coords.positionInRoot()
                                    tileBoundsInRoot[folderKey] = coords.boundsInRoot()
                                }
                                .bouncyClickable(pressedScale = 0.90f) { openedFolderId = folder.id }
                                .pointerInput(folder.id) {
                                    detectDragGesturesAfterLongPress(
                                        onDragStart = { localOffset ->
                                            draggingTarget = DragRemoveTarget.FolderTarget(folder)
                                            dragFingerInRoot = tileOrigin + localOffset
                                        },
                                        onDrag = { change, dragAmount ->
                                            change.consume()
                                            dragFingerInRoot += dragAmount
                                        },
                                        onDragEnd = {
                                            val dropInZone = (dragFingerInRoot.y - rootPosition.y) <= dropThresholdPx
                                            draggingTarget = null
                                            if (dropInZone) {
                                                confirmRemoveTarget = DragRemoveTarget.FolderTarget(folder)
                                            }
                                        },
                                        onDragCancel = { draggingTarget = null }
                                    )
                                }
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 14.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                FolderMiniGridIcon(
                                    packages = folder.packages,
                                    appsByPackage = appsByPkg
                                )
                                Spacer(Modifier.height(10.dp))
                                Text(
                                    text = if (isHoverTarget) {
                                        str(
                                            "➕ 加入「${folder.name}」",
                                            "➕ 加入「${folder.name}」",
                                            "➕ Add to \"${folder.name}\"",
                                            "➕「${folder.name}」に追加"
                                        )
                                    } else {
                                        folder.name
                                    },
                                    color = if (isHoverTarget) colors.accent else colors.primaryText,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }

                // Installed Apps
                items(filtered, key = { it.packageName }) { app ->
                    val appKey = "app:${app.packageName}"
                    val iconBmp = remember(app.icon) { app.icon.asImageBitmap() }
                    var tileOrigin by remember { mutableStateOf(Offset.Zero) }
                    val isBeingDragged = (draggingTarget as? DragRemoveTarget.InstalledAppTarget)?.app?.packageName == app.packageName
                    val isHoverFolderTarget = hoveredOverlapKey == appKey
                    val scale by animateFloatAsState(
                        targetValue = if (isHoverFolderTarget) 1.09f else 1f,
                        animationSpec = tween(140),
                        label = "appHoverFolderScale"
                    )

                    DisposableEffect(appKey) {
                        onDispose { tileBoundsInRoot.remove(appKey) }
                    }

                    Surface(
                        color = if (isHoverFolderTarget) colors.accent.copy(alpha = 0.24f) else colors.cardSurface,
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                            }
                            .alpha(if (isBeingDragged) 0.3f else 1f)
                            .border(
                                width = if (isHoverFolderTarget) 2.2.dp else 1.dp,
                                color = if (isHoverFolderTarget) colors.accent else colors.cardBorder,
                                shape = RoundedCornerShape(20.dp)
                            )
                            .clip(RoundedCornerShape(20.dp))
                            .onGloballyPositioned { coords ->
                                tileOrigin = coords.positionInRoot()
                                tileBoundsInRoot[appKey] = coords.boundsInRoot()
                            }
                            .bouncyClickable(pressedScale = 0.90f) { onLaunchApp(app.packageName) }
                            .pointerInput(app.packageName) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = { localOffset ->
                                        draggingTarget = DragRemoveTarget.InstalledAppTarget(app)
                                        dragFingerInRoot = tileOrigin + localOffset
                                    },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        dragFingerInRoot += dragAmount
                                    },
                                    onDragEnd = { finishAppDragDrop(app) },
                                    onDragCancel = { draggingTarget = null }
                                )
                            }
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Image(
                                bitmap = iconBmp,
                                contentDescription = app.label,
                                modifier = Modifier
                                    .size(68.dp)
                                    .clip(RoundedCornerShape(18.dp))
                            )
                            Spacer(Modifier.height(10.dp))
                            Text(
                                text = if (isHoverFolderTarget) {
                                    str("➕ 放開建立資料夾", "➕ 松开新建文件夹", "➕ Drop to Create Folder", "➕ 離してフォルダ作成")
                                } else {
                                    app.label
                                },
                                color = if (isHoverFolderTarget) colors.accent else colors.primaryText,
                                fontSize = if (isHoverFolderTarget) 12.sp else 14.sp,
                                fontWeight = if (isHoverFolderTarget) FontWeight.Bold else FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }

        // FLOATING TOP "✕ 移除／解除安裝" DROP ZONE
        AnimatedVisibility(
            visible = draggingTarget != null,
            enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(tween(150)),
            exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(tween(150)),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 14.dp)
        ) {
            val pillColor by animateColorAsState(
                targetValue = if (isHoveringRemove) Color(0xFFDC2626) else Color(0xE61F2937),
                animationSpec = tween(160),
                label = "removeDropColor"
            )
            val pillScale by animateFloatAsState(
                targetValue = if (isHoveringRemove) 1.12f else 1f,
                animationSpec = tween(150),
                label = "removeDropScale"
            )
            Surface(
                color = pillColor,
                shape = RoundedCornerShape(26.dp),
                shadowElevation = 14.dp,
                modifier = Modifier
                    .graphicsLayer {
                        scaleX = pillScale
                        scaleY = pillScale
                    }
                    .border(
                        width = 1.5.dp,
                        color = if (isHoveringRemove) Color.White else Color(0x88FFFFFF),
                        shape = RoundedCornerShape(26.dp)
                    )
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.DeleteOutline,
                        contentDescription = str("移除", "移除", "Remove", "削除"),
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = when {
                            isHoveringRemove -> str(
                                "放開後確認移除／解除安裝",
                                "松开后确认移除／卸载",
                                "Release to Confirm Remove / Uninstall",
                                "離して削除／アンインストールを確認"
                            )
                            hoveredOverlapKey != null -> str(
                                "➕ 放開建立或加入資料夾",
                                "➕ 松开新建或加入文件夹",
                                "➕ Release to Create or Add to Folder",
                                "➕ 離してフォルダ作成・追加"
                            )
                            else -> str(
                                "✕ 往上拖曳至此移除／解除安裝",
                                "✕ 往上拖曳至此移除／卸载",
                                "✕ Drag Up Here to Remove / Uninstall",
                                "✕ ここまで上にドラッグして削除／アンインストール"
                            )
                        },
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // FLOATING DRAGGED GHOST ICON
        draggingTarget?.let { target ->
            val ghostSizePx = with(density) { 78.dp.toPx() }
            val gx = (dragFingerInRoot.x - rootPosition.x) - ghostSizePx / 2f
            val gy = (dragFingerInRoot.y - rootPosition.y) - ghostSizePx / 2f
            Surface(
                color = colors.cardSurfaceElevated.copy(alpha = 0.94f),
                shape = RoundedCornerShape(22.dp),
                shadowElevation = 18.dp,
                modifier = Modifier
                    .graphicsLayer {
                        translationX = gx
                        translationY = gy
                        scaleX = 1.12f
                        scaleY = 1.12f
                    }
                    .border(
                        width = 2.dp,
                        color = when {
                            isHoveringRemove -> Color(0xFFEF4444)
                            hoveredOverlapKey != null -> Color(0xFF22C55E)
                            else -> colors.accent
                        },
                        shape = RoundedCornerShape(22.dp)
                    )
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(78.dp)
                        .padding(10.dp)
                ) {
                    when (target) {
                        is DragRemoveTarget.SplitShortcutItem -> {
                            DualSplitShortcutIcon(
                                shortcut = target.shortcut,
                                appsByPackage = appsByPkg,
                                sizeDp = 56
                            )
                        }
                        is DragRemoveTarget.FolderTarget -> {
                            Icon(
                                imageVector = Icons.Rounded.Folder,
                                contentDescription = target.label,
                                tint = colors.accent,
                                modifier = Modifier.size(48.dp)
                            )
                        }
                        is DragRemoveTarget.InstalledAppTarget -> {
                            val bmp = remember(target.app.icon) { target.app.icon.asImageBitmap() }
                            Image(
                                bitmap = bmp,
                                contentDescription = target.label,
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(RoundedCornerShape(14.dp))
                            )
                        }
                    }
                }
            }
        }

        // 1. CONFIRMATION DIALOG FOR UNINSTALL / REMOVE
        confirmRemoveTarget?.let { target ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.68f))
                    .clickable { confirmRemoveTarget = null },
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    color = colors.cardSurface,
                    shape = RoundedCornerShape(26.dp),
                    shadowElevation = 24.dp,
                    modifier = Modifier
                        .fillMaxWidth(0.56f)
                        .border(1.2.dp, colors.cardBorder, RoundedCornerShape(26.dp))
                        .clickable(enabled = false) {}
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        when (target) {
                            is DragRemoveTarget.InstalledAppTarget -> {
                                Text(
                                    text = str(
                                        "確定要解除安裝「${target.app.label}」嗎？",
                                        "确定要卸载「${target.app.label}」吗？",
                                        "Uninstall \"${target.app.label}\"?",
                                        "「${target.app.label}」をアンインストールしますか？"
                                    ),
                                    color = colors.primaryText,
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = if (target.app.isSystemApp) {
                                        str(
                                            "此應用程式為車機系統預載軟體；若系統限制無法移除，可點擊「應用資訊」進行停用或強制停止。",
                                            "此应用为车机系统预装软件；若系统限制无法卸载，可点击「应用信息」进行停用或强行停止。",
                                            "This is a pre-installed system app. If uninstall is restricted, tap \"App Info\" to disable or force stop it.",
                                            "このアプリはシステムプリインストールアプリです。削除できない場合は「アプリ情報」から無効化または強制停止できます。"
                                        )
                                    } else {
                                        str(
                                            "點擊「確認解除安裝」後將開啟系統安裝管理員執行卸載。",
                                            "点击「确认卸载」后将开启系统安装管理器执行卸载。",
                                            "Tap \"Uninstall\" to open the system package installer and remove this app.",
                                            "「アンインストール」をタップするとシステムのインストーラーが開きます。"
                                        )
                                    },
                                    color = colors.secondaryText,
                                    fontSize = 15.sp
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    OutlinedButton(
                                        onClick = { confirmRemoveTarget = null },
                                        shape = RoundedCornerShape(14.dp)
                                    ) {
                                        Text(str("取消", "取消", "Cancel", "キャンセル"), fontSize = 15.sp)
                                    }
                                    Spacer(Modifier.width(10.dp))
                                    OutlinedButton(
                                        onClick = {
                                            val appInfo = target.app
                                            confirmRemoveTarget = null
                                            onOpenAppInfo(appInfo)
                                        },
                                        shape = RoundedCornerShape(14.dp)
                                    ) {
                                        Text(str("應用資訊", "应用信息", "App Info", "アプリ情報"), fontSize = 15.sp)
                                    }
                                    Spacer(Modifier.width(10.dp))
                                    Button(
                                        onClick = {
                                            val appInfo = target.app
                                            confirmRemoveTarget = null
                                            onUninstallApp(appInfo)
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Color(0xFFDC2626),
                                            contentColor = Color.White
                                        ),
                                        shape = RoundedCornerShape(14.dp)
                                    ) {
                                        Text(
                                            str("確認解除安裝", "确认卸载", "Uninstall", "アンインストール"),
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }

                            is DragRemoveTarget.SplitShortcutItem -> {
                                Text(
                                    text = str(
                                        "確定要移除分屏捷徑「${target.label}」嗎？",
                                        "确定要移除分屏快捷方式「${target.label}」吗？",
                                        "Remove split shortcut \"${target.label}\"?",
                                        "分割ショートカット「${target.label}」を削除しますか？"
                                    ),
                                    color = colors.primaryText,
                                    fontSize = 21.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = str(
                                        "僅移除此雙視窗捷徑，不會刪除原本的應用程式。",
                                        "仅移除此双窗口快捷方式，不会删除原本的应用。",
                                        "Only removes this split-screen shortcut; the apps themselves will not be uninstalled.",
                                        "この分割ショートカットのみを削除します（元のアプリは削除されません）。"
                                    ),
                                    color = colors.secondaryText,
                                    fontSize = 15.sp
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    OutlinedButton(onClick = { confirmRemoveTarget = null }, shape = RoundedCornerShape(14.dp)) {
                                        Text(str("取消", "取消", "Cancel", "キャンセル"), fontSize = 15.sp)
                                    }
                                    Spacer(Modifier.width(10.dp))
                                    Button(
                                        onClick = {
                                            val sc = target.shortcut
                                            confirmRemoveTarget = null
                                            onRemoveSplitShortcut(sc)
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626), contentColor = Color.White),
                                        shape = RoundedCornerShape(14.dp)
                                    ) {
                                        Text(str("確認移除", "确认移除", "Remove", "削除する"), fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }

                            is DragRemoveTarget.FolderTarget -> {
                                Text(
                                    text = str(
                                        "確定要解散資料夾「${target.folder.name}」嗎？",
                                        "确定要解散文件夹「${target.folder.name}」吗？",
                                        "Ungroup folder \"${target.folder.name}\"?",
                                        "フォルダ「${target.folder.name}」を解除しますか？"
                                    ),
                                    color = colors.primaryText,
                                    fontSize = 21.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = str(
                                        "解散後資料夾內的應用程式不會被刪除，將全數回到應用程式清單。",
                                        "解散后文件夹内的应用不会被删除，将全部回到应用列表。",
                                        "Apps inside this folder will not be uninstalled and will return to the app drawer.",
                                        "フォルダ内のアプリは削除されず、すべてアプリ一覧に戻ります。"
                                    ),
                                    color = colors.secondaryText,
                                    fontSize = 15.sp
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    OutlinedButton(onClick = { confirmRemoveTarget = null }, shape = RoundedCornerShape(14.dp)) {
                                        Text(str("取消", "取消", "Cancel", "キャンセル"), fontSize = 15.sp)
                                    }
                                    Spacer(Modifier.width(10.dp))
                                    Button(
                                        onClick = {
                                            val fid = target.folder.id
                                            confirmRemoveTarget = null
                                            onDeleteFolder(fid)
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626), contentColor = Color.White),
                                        shape = RoundedCornerShape(14.dp)
                                    ) {
                                        Text(str("確認解散", "确认解散", "Ungroup", "解除する"), fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 2. CREATE OR RENAME FOLDER DIALOG (Auto-detects default name & asks user!)
        val activeNamingState = pendingFolderCreation
        val activeRenamingFolder = renamingFolder
        if (activeNamingState != null || activeRenamingFolder != null) {
            val initialText = activeNamingState?.suggestedName ?: activeRenamingFolder?.name.orEmpty()
            var folderNameInput by remember(activeNamingState, activeRenamingFolder) { mutableStateOf(initialText) }
            val presetSuggestions = listOf(
                str("導航地圖", "导航地图", "Navigation", "ナビ・地図"),
                str("音樂播放", "音乐播放", "Music", "音楽"),
                str("影音娛樂", "影音娱乐", "Video & Media", "動画・エンタメ"),
                str("通訊社群", "通讯社交", "Social & Comms", "通話・SNS"),
                str("行車常用", "行车常用", "Driving Apps", "ドライブ定番"),
                str("常用工具", "常用工具", "Utilities", "便利ツール")
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.68f))
                    .clickable {
                        pendingFolderCreation = null
                        renamingFolder = null
                    },
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    color = colors.cardSurface,
                    shape = RoundedCornerShape(26.dp),
                    shadowElevation = 24.dp,
                    modifier = Modifier
                        .fillMaxWidth(0.58f)
                        .border(1.2.dp, colors.cardBorder, RoundedCornerShape(26.dp))
                        .clickable(enabled = false) {}
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text(
                            text = if (activeNamingState != null) {
                                str(
                                    "建立新資料夾（已自動判定預設名稱）",
                                    "新建文件夹（已自动判定默认名称）",
                                    "Create Folder (Smart Name Suggested)",
                                    "新しいフォルダを作成（おすすめ名を自動設定）"
                                )
                            } else {
                                str("重新命名資料夾", "重命名文件夹", "Rename Folder", "フォルダ名を変更")
                            },
                            color = colors.primaryText,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                        if (activeNamingState != null) {
                            Text(
                                text = str(
                                    "包含：${activeNamingState.firstApp.label} ＋ ${activeNamingState.secondApp.label}",
                                    "包含：${activeNamingState.firstApp.label} ＋ ${activeNamingState.secondApp.label}",
                                    "Apps: ${activeNamingState.firstApp.label} + ${activeNamingState.secondApp.label}",
                                    "対象：${activeNamingState.firstApp.label} ＋ ${activeNamingState.secondApp.label}"
                                ),
                                color = colors.accent,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        OutlinedTextField(
                            value = folderNameInput,
                            onValueChange = { folderNameInput = it },
                            label = { Text(str("資料夾名稱", "文件夹名称", "Folder Name", "フォルダ名")) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = colors.primaryText,
                                unfocusedTextColor = colors.primaryText,
                                focusedBorderColor = colors.accent,
                                unfocusedBorderColor = colors.cardBorder
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            presetSuggestions.forEach { preset ->
                                Surface(
                                    color = if (folderNameInput == preset) colors.accent else colors.cardSurfaceElevated,
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier
                                        .border(1.dp, if (folderNameInput == preset) colors.accent else colors.cardBorder, RoundedCornerShape(14.dp))
                                        .clickable { folderNameInput = preset }
                                ) {
                                    Text(
                                        text = preset,
                                        color = if (folderNameInput == preset) colors.onAccent else colors.primaryText,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                                    )
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            OutlinedButton(
                                onClick = {
                                    pendingFolderCreation = null
                                    renamingFolder = null
                                },
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Text(str("取消", "取消", "Cancel", "キャンセル"), fontSize = 15.sp)
                            }
                            Spacer(Modifier.width(10.dp))
                            Button(
                                onClick = {
                                    val finalName = folderNameInput.trim().ifBlank {
                                        initialText.ifBlank {
                                            DxCarI18n.tr("常用工具", "常用工具", "Utilities", "便利ツール")
                                        }
                                    }
                                    if (activeNamingState != null) {
                                        onCreateFolder(
                                            finalName,
                                            listOf(
                                                activeNamingState.firstApp.packageName,
                                                activeNamingState.secondApp.packageName
                                            )
                                        )
                                    } else if (activeRenamingFolder != null) {
                                        onRenameFolder(activeRenamingFolder.id, finalName)
                                    }
                                    pendingFolderCreation = null
                                    renamingFolder = null
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = colors.accent, contentColor = colors.onAccent),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Text(
                                    if (activeNamingState != null) {
                                        str("確認建立", "确认新建", "Create", "作成する")
                                    } else {
                                        str("儲存名稱", "保存名称", "Save", "保存する")
                                    },
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }

        // 3. OPENED FOLDER SHEET
        val activeFolder = appFolders.firstOrNull { it.id == openedFolderId }
        if (activeFolder != null) {
            val memberApps = remember(activeFolder.packages, appsByPkg) {
                activeFolder.packages.mapNotNull { appsByPkg[it] }
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.65f))
                    .clickable { openedFolderId = null },
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    color = colors.cardSurface,
                    shape = RoundedCornerShape(28.dp),
                    shadowElevation = 24.dp,
                    modifier = Modifier
                        .fillMaxWidth(0.72f)
                        .border(1.2.dp, colors.cardBorder, RoundedCornerShape(28.dp))
                        .clickable(enabled = false) {}
                ) {
                    Column(
                        modifier = Modifier.padding(22.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.Folder, contentDescription = null, tint = colors.accent, modifier = Modifier.size(28.dp))
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    text = activeFolder.name,
                                    color = colors.primaryText,
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(Modifier.width(8.dp))
                                IconButton(onClick = { renamingFolder = activeFolder }) {
                                    Icon(
                                        Icons.Rounded.Edit,
                                        contentDescription = str("修改名稱", "修改名称", "Rename", "名前を変更"),
                                        tint = colors.secondaryText,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                            IconButton(onClick = { openedFolderId = null }) {
                                Icon(
                                    Icons.Rounded.Close,
                                    contentDescription = str("關閉", "关闭", "Close", "閉じる"),
                                    tint = colors.primaryText
                                )
                            }
                        }

                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = 132.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                            modifier = Modifier.height(270.dp)
                        ) {
                            items(memberApps, key = { it.packageName }) { member ->
                                val bmp = remember(member.icon) { member.icon.asImageBitmap() }
                                Surface(
                                    color = colors.cardSurfaceElevated,
                                    shape = RoundedCornerShape(18.dp),
                                    modifier = Modifier
                                        .border(1.dp, colors.cardBorder, RoundedCornerShape(18.dp))
                                        .bouncyClickable {
                                            openedFolderId = null
                                            onLaunchApp(member.packageName)
                                        }
                                ) {
                                    Column(
                                        modifier = Modifier.padding(12.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Image(
                                            bitmap = bmp,
                                            contentDescription = member.label,
                                            modifier = Modifier
                                                .size(56.dp)
                                                .clip(RoundedCornerShape(14.dp))
                                        )
                                        Spacer(Modifier.height(8.dp))
                                        Text(
                                            text = member.label,
                                            color = colors.primaryText,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Medium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            text = str("移出資料夾", "移出文件夹", "Remove from Folder", "フォルダから出す"),
                                            color = colors.accent,
                                            fontSize = 11.sp,
                                            modifier = Modifier.clickable {
                                                onRemoveAppFromFolder(activeFolder.id, member.packageName)
                                            }
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
}

@Composable
private fun FolderMiniGridIcon(
    packages: List<String>,
    appsByPackage: Map<String, InstalledAppInfo>
) {
    val colors = LocalDxCarColors.current
    val previewApps = remember(packages, appsByPackage) {
        packages.mapNotNull { appsByPackage[it] }.take(4)
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(68.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(colors.cardSurfaceElevated)
            .border(1.dp, colors.accent.copy(alpha = 0.45f), RoundedCornerShape(18.dp))
            .padding(6.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            for (row in 0..1) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (col in 0..1) {
                        val idx = row * 2 + col
                        val item = previewApps.getOrNull(idx)
                        if (item != null) {
                            val bmp = remember(item.icon) { item.icon.asImageBitmap() }
                            Image(
                                bitmap = bmp,
                                contentDescription = item.label,
                                modifier = Modifier
                                    .size(25.dp)
                                    .clip(RoundedCornerShape(6.dp))
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(25.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(colors.cardBorder.copy(alpha = 0.35f))
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BuiltInDrawerTile(
    label: String,
    subLabel: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    badgeAlert: Boolean,
    onClick: () -> Unit
) {
    val colors = LocalDxCarColors.current
    Surface(
        color = colors.cardSurfaceElevated,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier
            .border(1.2.dp, tint.copy(alpha = 0.55f), RoundedCornerShape(20.dp))
            .clip(RoundedCornerShape(20.dp))
            .bouncyClickable(pressedScale = 0.90f, onClick = onClick)
    ) {
        Box(modifier = Modifier.padding(horizontal = 12.dp, vertical = 16.dp)) {
            if (badgeAlert) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFEF4444))
                )
            }
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(68.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(tint.copy(alpha = 0.18f))
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = label,
                        tint = tint,
                        modifier = Modifier.size(36.dp)
                    )
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    text = label,
                    color = colors.primaryText,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subLabel,
                    color = tint,
                    fontSize = 11.sp,
                    maxLines = 1
                )
            }
        }
    }
}

/**
 * Top-Left Dropdown Quick Toggles Shade (opened by tapping the Wi-Fi icon in the Dock OR Clock!).
 */
@Composable
fun QuickControlCenterOverlay(
    systemStatus: SystemStatus,
    isNight: Boolean,
    onVolumeChange: (Int) -> Unit,
    onBrightnessChange: (Int) -> Unit,
    onToggleDayNight: () -> Unit,
    onSwapSplit: () -> Unit,
    onOpenWifiSettings: () -> Unit,
    onOpenBluetoothSettings: () -> Unit,
    onOpenSystemQuickShade: (() -> Unit)? = null,
    onToggle3DCursor: (() -> Unit)? = null,
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit
) {
    val colors = LocalDxCarColors.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.45f))
            .clickable { onDismiss() },
        contentAlignment = Alignment.TopStart
    ) {
        Surface(
            color = colors.cardSurface,
            shape = RoundedCornerShape(bottomEnd = 28.dp, bottomStart = 20.dp, topEnd = 20.dp),
            shadowElevation = 22.dp,
            modifier = Modifier
                .padding(start = 16.dp, top = 12.dp, end = 16.dp)
                .fillMaxWidth(0.76f)
                .border(1.2.dp, colors.cardBorder, RoundedCornerShape(bottomEnd = 28.dp, bottomStart = 20.dp, topEnd = 20.dp))
                .clickable(enabled = false) {}
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = str(
                                "下拉快捷控制面板  ·  ${systemStatus.time12h}",
                                "下拉快捷控制面板  ·  ${systemStatus.time12h}",
                                "Quick Control Center  ·  ${systemStatus.time12h}",
                                "クイックコントロール  ·  ${systemStatus.time12h}"
                            ),
                            color = colors.primaryText,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                        val wifiPart = if (systemStatus.wifiConnected) {
                            val ssid = systemStatus.wifiSsid.ifBlank { str("已連線", "已连接", "Connected", "接続済み") }
                            str("Wi-Fi：$ssid", "Wi-Fi：$ssid", "Wi-Fi: $ssid", "Wi-Fi：$ssid")
                        } else {
                            str("Wi-Fi：未連線", "Wi-Fi：未连接", "Wi-Fi: Disconnected", "Wi-Fi：未接続")
                        }
                        val btPart = if (systemStatus.bluetoothConnected) {
                            val btName = systemStatus.connectedBtName.ifBlank { str("已配對連線", "已配对连接", "Connected", "接続済み") }
                            str("藍牙：$btName", "蓝牙：$btName", "Bluetooth: $btName", "Bluetooth：$btName")
                        } else {
                            str("藍牙：待連線", "蓝牙：待连接", "Bluetooth: Standby", "Bluetooth：未接続")
                        }
                        val netLabel = "$wifiPart  ·  $btPart"
                        Text(text = netLabel, color = colors.secondaryText, fontSize = 13.sp)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (onOpenSystemQuickShade != null) {
                            OutlinedButton(
                                onClick = {
                                    onDismiss()
                                    onOpenSystemQuickShade()
                                },
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Icon(Icons.Rounded.ExpandMore, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(str("系統通知欄", "系统通知栏", "System Shade", "システム通知"))
                            }
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(
                                Icons.Rounded.Close,
                                contentDescription = str("關閉", "关闭", "Close", "閉じる"),
                                tint = colors.primaryText
                            )
                        }
                    }
                }

                // Quick Action Tiles
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    QuickTile(
                        icon = Icons.Rounded.Wifi,
                        label = if (systemStatus.wifiConnected) {
                            str("Wi-Fi 設定", "Wi-Fi 设置", "Wi-Fi Settings", "Wi-Fi 設定")
                        } else {
                            str("開啟 Wi-Fi", "开启 Wi-Fi", "Turn On Wi-Fi", "Wi-Fi をオン")
                        },
                        active = systemStatus.wifiConnected,
                        onClick = { onDismiss(); onOpenWifiSettings() },
                        modifier = Modifier.weight(1f)
                    )
                    QuickTile(
                        icon = Icons.Rounded.Bluetooth,
                        label = if (systemStatus.bluetoothConnected) {
                            str("藍牙已連", "蓝牙已连", "BT Connected", "BT 接続中")
                        } else {
                            str("藍牙設定", "蓝牙设置", "Bluetooth", "Bluetooth 設定")
                        },
                        active = systemStatus.bluetoothConnected,
                        onClick = { onDismiss(); onOpenBluetoothSettings() },
                        modifier = Modifier.weight(1f)
                    )
                    QuickTile(
                        icon = if (isNight) Icons.Rounded.DarkMode else Icons.Rounded.LightMode,
                        label = if (isNight) {
                            str("夜間模式", "夜间模式", "Night Mode", "夜間モード")
                        } else {
                            str("白天模式", "白天模式", "Day Mode", "昼間モード")
                        },
                        active = isNight,
                        onClick = onToggleDayNight,
                        modifier = Modifier.weight(1f)
                    )
                    if (onToggle3DCursor != null) {
                        QuickTile(
                            icon = Icons.Rounded.ViewInAr,
                            label = str("3D 俯瞰游標", "3D 俯瞰光标", "3D Cursor", "3D カーソル"),
                            active = false,
                            onClick = { onDismiss(); onToggle3DCursor() },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    QuickTile(
                        icon = Icons.Rounded.SwapHoriz,
                        label = str("左右互換", "左右互换", "Swap Sides", "左右入替"),
                        active = false,
                        onClick = { onSwapSplit(); onDismiss() },
                        modifier = Modifier.weight(1f)
                    )
                    QuickTile(
                        icon = Icons.Rounded.Settings,
                        label = str("啟動器設定", "启动器设置", "Settings", "ランチャー設定"),
                        active = false,
                        onClick = { onDismiss(); onOpenSettings() },
                        modifier = Modifier.weight(1f)
                    )
                }

                // Brightness Slider
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Brightness6, contentDescription = null, tint = colors.accent)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        str("亮度", "亮度", "Brightness", "明るさ"),
                        color = colors.primaryText,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.width(76.dp)
                    )
                    Slider(
                        value = systemStatus.brightnessPercent / 100f,
                        onValueChange = { onBrightnessChange((it * 100).toInt()) },
                        colors = SliderDefaults.colors(thumbColor = colors.accent, activeTrackColor = colors.accent),
                        modifier = Modifier.weight(1f)
                    )
                    Text("${systemStatus.brightnessPercent}%", color = colors.secondaryText, fontSize = 13.sp, modifier = Modifier.width(42.dp))
                }

                // Media Volume Slider
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.AutoMirrored.Rounded.VolumeUp, contentDescription = null, tint = colors.accent)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        str("音量", "音量", "Volume", "音量"),
                        color = colors.primaryText,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.width(76.dp)
                    )
                    Slider(
                        value = systemStatus.mediaVolumePercent / 100f,
                        onValueChange = { onVolumeChange((it * 100).toInt()) },
                        colors = SliderDefaults.colors(thumbColor = colors.accent, activeTrackColor = colors.accent),
                        modifier = Modifier.weight(1f)
                    )
                    Text("${systemStatus.mediaVolumePercent}%", color = colors.secondaryText, fontSize = 13.sp, modifier = Modifier.width(42.dp))
                }
            }
        }
    }
}

@Composable
private fun QuickTile(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalDxCarColors.current
    Surface(
        color = if (active) colors.accent else colors.cardSurfaceElevated,
        shape = RoundedCornerShape(18.dp),
        modifier = modifier
            .height(72.dp)
            .bouncyClickable(pressedScale = 0.91f, onClick = onClick)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (active) colors.onAccent else colors.primaryText,
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = label,
                color = if (active) colors.onAccent else colors.primaryText,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
        }
    }
}

data class YoutubeHandoffRequest(
    val url: String,
    val positionSec: Int = 0,
    val enterFullscreen: Boolean = true,
    val autoClickFirstResult: Boolean = false,
    val seq: Long = System.currentTimeMillis()
)

object YoutubeHandoffBridge {
    private val _pendingHandoff = kotlinx.coroutines.flow.MutableStateFlow<YoutubeHandoffRequest?>(null)
    val pendingHandoff: kotlinx.coroutines.flow.StateFlow<YoutubeHandoffRequest?> = _pendingHandoff

    fun requestHandoff(
        url: String,
        positionSec: Int = 0,
        enterFullscreen: Boolean = true,
        autoClickFirstResult: Boolean = false
    ) {
        _pendingHandoff.value = YoutubeHandoffRequest(
            url = url,
            positionSec = positionSec,
            enterFullscreen = enterFullscreen,
            autoClickFirstResult = autoClickFirstResult,
            seq = System.currentTimeMillis()
        )
    }

    fun consume() {
        _pendingHandoff.value = null
    }
}

/**
 * Bridge connecting steering wheel / car media buttons (both KeyEvent and MediaSession)
 * directly to Page 2's EmbeddedYoutubePlayerCard while it is active.
 */
object EmbeddedYoutubeBridge {
    @Volatile
    var isActive: Boolean = false
    @Volatile
    var onTogglePlayPause: (() -> Unit)? = null
    @Volatile
    var onPlay: (() -> Unit)? = null
    @Volatile
    var onPause: (() -> Unit)? = null
    @Volatile
    var onNext: (() -> Unit)? = null
    @Volatile
    var onPrevious: (() -> Unit)? = null
    @Volatile
    var canGoBack: (() -> Boolean)? = null
    @Volatile
    var goBack: (() -> Unit)? = null
    @Volatile
    var isFullscreen: (() -> Boolean)? = null
    @Volatile
    var exitFullscreen: (() -> Unit)? = null

    fun handleMediaKeyEvent(event: KeyEvent): Boolean {
        if (!isActive || event.action != KeyEvent.ACTION_DOWN) return false
        return when (event.keyCode) {
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
            KeyEvent.KEYCODE_HEADSETHOOK -> {
                onTogglePlayPause?.invoke()
                true
            }
            KeyEvent.KEYCODE_MEDIA_PLAY -> {
                onPlay?.invoke()
                true
            }
            KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                onPause?.invoke()
                true
            }
            KeyEvent.KEYCODE_MEDIA_NEXT -> {
                onNext?.invoke()
                true
            }
            KeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
                onPrevious?.invoke()
                true
            }
            else -> false
        }
    }
}

/**
 * Custom WebView that keeps Chromium's media decoder alive when switching to external apps
 * (Google Maps, Browser, System Settings, or Stock AI Box Launcher), pausing ONLY when another
 * media app takes AudioFocus or the user explicitly pauses!
 */
private class BackgroundPlayableWebView(context: Context) : WebView(context) {
    @Volatile
    var keepBackgroundPlaying: Boolean = true

    override fun onWindowVisibilityChanged(visibility: Int) {
        if (visibility != View.VISIBLE && keepBackgroundPlaying) {
            super.onWindowVisibilityChanged(View.VISIBLE)
        } else {
            super.onWindowVisibilityChanged(visibility)
        }
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        if (visibility != View.VISIBLE && keepBackgroundPlaying) {
            super.onVisibilityChanged(changedView, View.VISIBLE)
        } else {
            super.onVisibilityChanged(changedView, visibility)
        }
    }
}

private enum class YtGestureHudZone { LEFT, CENTER, RIGHT, TOP }

private data class YtGestureHudState(
    val zone: YtGestureHudZone,
    val label: String,
    val seq: Int = 0
)

/**
 * Inline Playable YouTube Card for Page 2 (1:1 Split Right Pane):
 * - Defaults video resolution preference to 720p via yt-player-quality storage.
 * - Swipe UP in the upper video player zone (top 48%) OR tap YouTube's bottom-right fullscreen icon
 *   -> enters true full-bleed Right-Half Fullscreen (CSS + CustomView lock, left 1:1 map untouched).
 * - Fullscreen-only YouTube gestures (Center 1/3 keeps native YouTube tap controls untouched!):
 *   * Double-tap Left 1/3 / Right 1/3 -> Rewind -10s / Fast-forward +10s (accumulating)
 *   * Double-tap & HOLD Left 1/3 or Right 1/3 -> Continuous accelerated rewind / fast-forward
 *   * Horizontal swipe/drag -> Smooth timeline scrub with live timestamp preview
 *   * Vertical swipe DOWN (or tap bottom-right exit fullscreen button) -> Exit fullscreen
 * - True cross-system background audio playback via BackgroundPlayableWebView + Chromium's native
 *   AudioFocusDelegate (continues in Stock Box Launcher, Google Maps, Browser, System Settings;
 *   automatically pauses when another media app starts playing and takes AudioFocus).
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun EmbeddedYoutubePlayerCard(
    modifier: Modifier = Modifier
) {
    val colors = LocalDxCarColors.current
    val context = LocalContext.current

    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var isRightPaneFullscreen by remember { mutableStateOf(false) }
    var showFsHintBanner by remember { mutableStateOf(false) }
    var gestureHud by remember { mutableStateOf<YtGestureHudState?>(null) }
    var gestureSeq by remember { mutableIntStateOf(0) }
    var exitCustomViewTrigger by remember { mutableStateOf<(() -> Unit)?>(null) }

    // Plain thread-safe array [currentSec, durationSec] updated from JavaBridge without Compose Snapshot overhead
    val videoTimeHolder = remember { FloatArray(2) }
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    val pendingHandoff by YoutubeHandoffBridge.pendingHandoff.collectAsState()
    var pendingSeekSec by remember { mutableIntStateOf(0) }
    var pendingAutoClickFirstResult by remember { mutableStateOf(false) }
    var pendingCloseSourceYt by remember { mutableStateOf(false) }

    fun evalYoutubeJs(js: String) {
        mainHandler.post {
            webViewRef?.evaluateJavascript(js, null)
        }
    }

    fun flashGestureHud(zone: YtGestureHudZone, text: String) {
        mainHandler.post {
            gestureSeq++
            gestureHud = YtGestureHudState(zone = zone, label = text, seq = gestureSeq)
        }
    }

    LaunchedEffect(gestureHud?.seq) {
        val currentSeq = gestureHud?.seq ?: return@LaunchedEffect
        if (gestureHud?.zone != YtGestureHudZone.TOP) {
            kotlinx.coroutines.delay(850L)
            if (gestureHud?.seq == currentSeq) {
                gestureHud = null
            }
        }
    }

    val applyRightPaneFsCssJs = """
        (function() {
          if (!document.getElementById('dxcar-yt-fs-lock')) {
            var st = document.createElement('style');
            st.id = 'dxcar-yt-fs-lock';
            st.textContent = `
              html, body { overflow: hidden !important; background: #000 !important; margin: 0 !important; padding: 0 !important; }
              ytm-mobile-topbar-renderer, .mobile-topbar-header, header,
              ytm-watch-next-secondary-results-renderer,
              ytm-item-section-renderer, ytm-slim-video-metadata-section-renderer,
              ytm-engagement-panel-section-list-renderer, .related-chips-slot-wrapper,
              ytm-pivot-bar-renderer, .watch-below-the-player { display: none !important; }
              #player-container-id, .player-container, #player, #movie_player,
              .html5-video-player, .html5-video-container {
                position: fixed !important;
                inset: 0 !important;
                top: 0 !important; left: 0 !important; right: 0 !important; bottom: 0 !important;
                width: 100vw !important; height: 100vh !important;
                max-width: 100vw !important; max-height: 100vh !important;
                z-index: 2147483000 !important;
                background: #000 !important;
              }
              video.video-stream, video {
                position: fixed !important;
                top: 0 !important; left: 0 !important;
                width: 100vw !important; height: 100vh !important;
                max-width: 100vw !important; max-height: 100vh !important;
                object-fit: contain !important;
                background: #000 !important;
              }
            `;
            (document.head || document.documentElement).appendChild(st);
          }
          window.scrollTo(0, 0);
        })();
    """.trimIndent()

    val removeRightPaneFsCssJs = """
        (function() {
          var st = document.getElementById('dxcar-yt-fs-lock');
          if (st) st.remove();
          if (document.fullscreenElement && document.exitFullscreen) {
            document.exitFullscreen().catch(function(){});
          }
        })();
    """.trimIndent()

    fun enterRightPaneFullscreen() {
        isRightPaneFullscreen = true
        evalYoutubeJs(applyRightPaneFsCssJs)
    }

    LaunchedEffect(pendingHandoff, webViewRef) {
        val req = pendingHandoff ?: return@LaunchedEffect
        val wv = webViewRef ?: return@LaunchedEffect
        YoutubeHandoffBridge.consume()
        pendingSeekSec = req.positionSec.coerceAtLeast(0)
        pendingAutoClickFirstResult = req.autoClickFirstResult
        pendingCloseSourceYt = true
        wv.loadUrl(req.url)
        if (req.enterFullscreen) {
            enterRightPaneFullscreen()
        }
    }

    fun exitRightPaneFullscreen() {
        exitCustomViewTrigger?.invoke()
        evalYoutubeJs(removeRightPaneFsCssJs)
        isRightPaneFullscreen = false
    }

    DisposableEffect(Unit) {
        EmbeddedYoutubeBridge.canGoBack = { webViewRef?.canGoBack() == true }
        EmbeddedYoutubeBridge.goBack = { webViewRef?.goBack() }
        EmbeddedYoutubeBridge.isFullscreen = { isRightPaneFullscreen }
        EmbeddedYoutubeBridge.exitFullscreen = { exitRightPaneFullscreen() }
        onDispose {
            EmbeddedYoutubeBridge.canGoBack = null
            EmbeddedYoutubeBridge.goBack = null
            EmbeddedYoutubeBridge.isFullscreen = null
            EmbeddedYoutubeBridge.exitFullscreen = null
        }
    }

    fun launchYoutubeAppWithState(rawUrl: String, sec: Int) {
        val baseUrl = rawUrl.ifBlank { "https://www.youtube.com" }
        val targetUrl = runCatching {
            val uri = Uri.parse(baseUrl)
            val vParam = uri.getQueryParameter("v")
            val pathSegs = uri.pathSegments
            val videoId = when {
                !vParam.isNullOrBlank() -> vParam
                uri.host?.contains("youtu.be") == true && pathSegs.isNotEmpty() -> pathSegs.first()
                pathSegs.size >= 2 && (pathSegs[0] == "shorts" || pathSegs[0] == "live" || pathSegs[0] == "embed") -> pathSegs[1]
                else -> null
            }
            if (!videoId.isNullOrBlank()) {
                if (sec > 0) "https://www.youtube.com/watch?v=$videoId&t=${sec}s"
                else "https://www.youtube.com/watch?v=$videoId"
            } else {
                baseUrl
            }
        }.getOrDefault(baseUrl)

        val opts = ActivityOptions.makeBasic().apply {
            launchDisplayId = Display.DEFAULT_DISPLAY
        }.toBundle()
        val ytIntent = Intent(Intent.ACTION_VIEW, Uri.parse(targetUrl)).apply {
            setPackage("com.google.android.youtube")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(ytIntent, opts)
        } catch (_: Throwable) {
            val fallbackIntent = Intent(Intent.ACTION_VIEW, Uri.parse(targetUrl)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            runCatching { context.startActivity(fallbackIntent, opts) }
        }
    }

    fun openCurrentYoutubeInApp() {
        val wv = webViewRef
        if (wv == null) {
            launchYoutubeAppWithState("https://www.youtube.com", 0)
            return
        }
        val js = """
            (function() {
              var v = document.querySelector('video');
              var sec = v && !isNaN(v.currentTime) ? Math.floor(v.currentTime) : -1;
              if (v && !v.paused) { try { v.pause(); } catch(e) {} }
              return JSON.stringify({ url: window.location.href || '', sec: sec });
            })();
        """.trimIndent()
        wv.evaluateJavascript(js) { raw ->
            val fallbackUrl = wv.url ?: "https://www.youtube.com"
            val fallbackSec = videoTimeHolder[0].toInt().coerceAtLeast(0)
            var parsedUrl = fallbackUrl
            var parsedSec = fallbackSec
            if (!raw.isNullOrBlank() && raw != "null") {
                runCatching {
                    val obj = org.json.JSONObject(org.json.JSONTokener(raw).nextValue().toString())
                    val u = obj.optString("url", "")
                    if (u.isNotBlank()) parsedUrl = u
                    val s = obj.optInt("sec", -1)
                    if (s >= 0) parsedSec = s
                }
            }
            launchYoutubeAppWithState(parsedUrl, parsedSec)
        }
    }

    val togglePlayPauseJs = """
        (function() {
          var v = document.querySelector('video');
          if (v) {
            if (v.paused) { v.play(); return 'PLAY'; }
            else { v.pause(); return 'PAUSE'; }
          }
          var btn = document.querySelector('button[aria-label*="Play"], button[aria-label*="Pause"], button[aria-label*="播放"], button[aria-label*="暫停"], .ytp-play-button');
          if (btn) btn.click();
          return 'BTN';
        })();
    """.trimIndent()

    val playOnlyJs = """
        (function() {
          var v = document.querySelector('video');
          if (v && v.paused) { v.play(); return 'PLAY'; }
          return 'OK';
        })();
    """.trimIndent()

    val pauseOnlyJs = """
        (function() {
          var v = document.querySelector('video');
          if (v && !v.paused) { v.pause(); return 'PAUSE'; }
          return 'OK';
        })();
    """.trimIndent()

    fun seekRelativeSec(deltaSec: Float) {
        evalYoutubeJs(
            """
            (function() {
              var v = document.querySelector('video');
              if (v) {
                var dur = (v.duration && isFinite(v.duration)) ? v.duration : 999999;
                v.currentTime = Math.max(0, Math.min(dur, (v.currentTime || 0) + ($deltaSec)));
              }
            })();
            """.trimIndent()
        )
    }

    fun seekAbsoluteSec(targetSec: Float) {
        evalYoutubeJs(
            """
            (function() {
              var v = document.querySelector('video');
              if (v) {
                var dur = (v.duration && isFinite(v.duration)) ? v.duration : 999999;
                v.currentTime = Math.max(0, Math.min(dur, $targetSec));
              }
            })();
            """.trimIndent()
        )
    }

    fun setPlaybackRateJs(rate: Float) {
        evalYoutubeJs(
            """
            (function() {
              var v = document.querySelector('video');
              if (v) { v.playbackRate = $rate; }
            })();
            """.trimIndent()
        )
    }

    fun formatYtTime(sec: Float): String {
        val total = sec.toInt().coerceAtLeast(0)
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
    }

    LaunchedEffect(isRightPaneFullscreen) {
        if (isRightPaneFullscreen) {
            evalYoutubeJs(applyRightPaneFsCssJs)
            showFsHintBanner = true
            kotlinx.coroutines.delay(2200L)
            showFsHintBanner = false
        } else {
            showFsHintBanner = false
            gestureHud = null
        }
    }

    // Bind steering wheel MediaSession + KeyEvent bridge (NO custom AudioFocusRequest so Chromium's
    // built-in AudioFocusDelegate plays unimpeded and yields naturally to external music apps!)
    DisposableEffect(context) {
        val actions = PlaybackState.ACTION_PLAY_PAUSE or
            PlaybackState.ACTION_PLAY or
            PlaybackState.ACTION_PAUSE or
            PlaybackState.ACTION_SKIP_TO_NEXT or
            PlaybackState.ACTION_SKIP_TO_PREVIOUS
        val session = runCatching {
            MediaSession(context, "DxCarPage2YouTube").apply {
                setPlaybackState(
                    PlaybackState.Builder()
                        .setActions(actions)
                        .setState(PlaybackState.STATE_PLAYING, PlaybackState.PLAYBACK_POSITION_UNKNOWN, 1f)
                        .build()
                )
                setCallback(object : MediaSession.Callback() {
                    override fun onPlay() {
                        evalYoutubeJs(playOnlyJs)
                    }

                    override fun onPause() {
                        evalYoutubeJs(pauseOnlyJs)
                    }

                    override fun onSkipToNext() {
                        evalYoutubeJs("document.querySelector('button[aria-label*=\"Next\"], .ytp-next-button')?.click();")
                    }

                    override fun onSkipToPrevious() {
                        evalYoutubeJs("document.querySelector('button[aria-label*=\"Previous\"], .ytp-prev-button')?.click();")
                    }

                    override fun onMediaButtonEvent(mediaButtonIntent: android.content.Intent): Boolean {
                        val ev = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                            mediaButtonIntent.getParcelableExtra(android.content.Intent.EXTRA_KEY_EVENT, KeyEvent::class.java)
                        } else {
                            @Suppress("DEPRECATION")
                            mediaButtonIntent.getParcelableExtra(android.content.Intent.EXTRA_KEY_EVENT)
                        }
                        if (ev != null && EmbeddedYoutubeBridge.handleMediaKeyEvent(ev)) {
                            return true
                        }
                        return super.onMediaButtonEvent(mediaButtonIntent)
                    }
                })
                isActive = true
            }
        }.getOrNull()

        EmbeddedYoutubeBridge.isActive = true
        EmbeddedYoutubeBridge.onTogglePlayPause = { evalYoutubeJs(togglePlayPauseJs) }
        EmbeddedYoutubeBridge.onPlay = { evalYoutubeJs(playOnlyJs) }
        EmbeddedYoutubeBridge.onPause = { evalYoutubeJs(pauseOnlyJs) }
        EmbeddedYoutubeBridge.onNext = {
            evalYoutubeJs("document.querySelector('button[aria-label*=\"Next\"], .ytp-next-button')?.click();")
        }
        EmbeddedYoutubeBridge.onPrevious = {
            evalYoutubeJs("document.querySelector('button[aria-label*=\"Previous\"], .ytp-prev-button')?.click();")
        }

        onDispose {
            EmbeddedYoutubeBridge.isActive = false
            EmbeddedYoutubeBridge.onTogglePlayPause = null
            EmbeddedYoutubeBridge.onPlay = null
            EmbeddedYoutubeBridge.onPause = null
            EmbeddedYoutubeBridge.onNext = null
            EmbeddedYoutubeBridge.onPrevious = null
            runCatching {
                session?.isActive = false
                session?.release()
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Compact Inline YouTube Control Bar (hidden per user spec for clean full-height Page 2 YouTube)
            AnimatedVisibility(visible = false) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(colors.cardSurfaceElevated)
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(24.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFFFF0000))
                                .clickable { openCurrentYoutubeInApp() }
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.PlayArrow,
                                contentDescription = str(
                                    "於 YouTube App 接續播放",
                                    "在 YouTube App 中继续播放",
                                    "Continue in YouTube App",
                                    "YouTube アプリで続きから再生"
                                ),
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Spacer(Modifier.width(7.dp))
                        Text(
                            text = str(
                                "YouTube 視窗播放 · 720p",
                                "YouTube 窗口播放 · 720p",
                                "YouTube Window · 720p",
                                "YouTube ウィンドウ再生 · 720p"
                            ),
                            color = colors.primaryText,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = { if (webViewRef?.canGoBack() == true) webViewRef?.goBack() },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = str("上一頁", "上一页", "Back", "戻る"),
                                tint = colors.primaryText,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        IconButton(
                            onClick = { webViewRef?.loadUrl("https://m.youtube.com") },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Home,
                                contentDescription = str("YouTube 首頁", "YouTube 首页", "YouTube Home", "YouTube ホーム"),
                                tint = colors.primaryText,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        IconButton(
                            onClick = { webViewRef?.reload() },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Refresh,
                                contentDescription = str("重新整理", "刷新", "Reload", "再読み込み"),
                                tint = colors.primaryText,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            // Video WebView + Full-Bleed Right-Pane Fullscreen + Left/Right 1/3 YouTube Gestures
            AndroidView(
                factory = { ctx ->
                    var inlineCustomView: View? = null
                    var inlineCustomCallback: WebChromeClient.CustomViewCallback? = null

                    val density = ctx.resources.displayMetrics.density
                    var downX = 0f
                    var downY = 0f
                    var downTimeMs = 0L
                    var gestureHandled = false
                    var isHorizontalScrubbing = false
                    var scrubStartSec = 0f

                    // Double-tap & Double-tap-and-hold state (ONLY Left 1/3 & Right 1/3 in Fullscreen; Center 1/3 keeps native YouTube controls!)
                    var lastSideTapUpMs = 0L
                    var lastSideTapX = 0f
                    var lastSideTapY = 0f
                    var isSecondSideTapDown = false
                    var isDoubleTapHolding = false
                    var sideTapZone = 0 // -1 = left 1/3, +1 = right 1/3
                    var accumulatedSeekSec = 0
                    var lastAccumulatedMs = 0L
                    var holdRepeatRunnable: Runnable? = null

                    fun stopHoldAcceleration() {
                        holdRepeatRunnable?.let { mainHandler.removeCallbacks(it) }
                        holdRepeatRunnable = null
                        if (isDoubleTapHolding) {
                            isDoubleTapHolding = false
                            setPlaybackRateJs(1.0f)
                            mainHandler.post { gestureHud = null }
                        }
                    }

                    val cardVideoContainer = object : FrameLayout(ctx) {
                        override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
                            val w = width.toFloat().coerceAtLeast(240f)
                            val h = height.toFloat().coerceAtLeast(200f)

                            if (!isRightPaneFullscreen) {
                                // Normal Page 2 state: ONLY swipe UP inside the upper video circle zone (top 48%, Image 4) enters Fullscreen!
                                when (ev.actionMasked) {
                                    MotionEvent.ACTION_DOWN -> {
                                        downX = ev.x
                                        downY = ev.y
                                        gestureHandled = false
                                    }
                                    MotionEvent.ACTION_MOVE -> {
                                        if (!gestureHandled) {
                                            val dx = ev.x - downX
                                            val dy = ev.y - downY
                                            if (downY in (h * 0.03f)..(h * 0.48f) &&
                                                dy < -(62f * density.coerceAtLeast(1f) / 1.6f) &&
                                                kotlin.math.abs(dy) > kotlin.math.abs(dx) * 1.3f
                                            ) {
                                                gestureHandled = true
                                                enterRightPaneFullscreen()
                                                return true
                                            }
                                        }
                                    }
                                }
                                return super.dispatchTouchEvent(ev)
                            }

                            // === RIGHT-PANE FULLSCREEN GESTURES ===
                            // Let direct taps on top-right (CC / Gear settings) or bottom-right (native Fullscreen button, Image 6) pass straight through!
                            val inTopRightControls = ev.y < 60f * density && ev.x > w * 0.62f
                            val inBottomRightFsCorner = ev.y > h - 64f * density && ev.x > w - 76f * density
                            if (inTopRightControls || inBottomRightFsCorner) {
                                return super.dispatchTouchEvent(ev)
                            }

                            val currentSideZone = when {
                                ev.x < w / 3f -> -1
                                ev.x > w * 2f / 3f -> 1
                                else -> 0 // Center 1/3 -> preserve native YouTube player controls!
                            }

                            when (ev.actionMasked) {
                                MotionEvent.ACTION_DOWN -> {
                                    downX = ev.x
                                    downY = ev.y
                                    downTimeMs = System.currentTimeMillis()
                                    gestureHandled = false
                                    isHorizontalScrubbing = false
                                    isSecondSideTapDown = false
                                    stopHoldAcceleration()

                                    // Double-tap & Double-tap-hold ONLY on Left 1/3 (-1) and Right 1/3 (+1)!
                                    if (currentSideZone != 0) {
                                        val dt = downTimeMs - lastSideTapUpMs
                                        val dist = kotlin.math.hypot((ev.x - lastSideTapX).toDouble(), (ev.y - lastSideTapY).toDouble())
                                        if (dt in 25L..320L && dist < 76f * density) {
                                            isSecondSideTapDown = true
                                            gestureHandled = true
                                            sideTapZone = currentSideZone
                                            val holdStartMs = downTimeMs
                                            val runner = object : Runnable {
                                                override fun run() {
                                                    if (!isSecondSideTapDown) return
                                                    if (!isDoubleTapHolding) {
                                                        isDoubleTapHolding = true
                                                        if (sideTapZone > 0) {
                                                            setPlaybackRateJs(2.0f)
                                                            flashGestureHud(
                                                                YtGestureHudZone.TOP,
                                                                DxCarI18n.tr(
                                                                    "⏩ 持續加速快轉中…",
                                                                    "⏩ 持续加速快进中…",
                                                                    "⏩ 2x Fast-Forwarding…",
                                                                    "⏩ 倍速早送り中…"
                                                                )
                                                            )
                                                        } else {
                                                            flashGestureHud(
                                                                YtGestureHudZone.TOP,
                                                                DxCarI18n.tr(
                                                                    "⏪ 持續加速倒退中…",
                                                                    "⏪ 持续加速快退中…",
                                                                    "⏪ Rewinding…",
                                                                    "⏪ 早戻し中…"
                                                                )
                                                            )
                                                        }
                                                    }
                                                    val heldElapsed = System.currentTimeMillis() - holdStartMs
                                                    val stepSec = if (heldElapsed > 1600L) 4.5f else 2.5f
                                                    seekRelativeSec(if (sideTapZone > 0) stepSec else -stepSec)
                                                    mainHandler.postDelayed(this, 160L)
                                                }
                                            }
                                            holdRepeatRunnable = runner
                                            mainHandler.postDelayed(runner, 230L)
                                            return true
                                        }
                                    }
                                }

                                MotionEvent.ACTION_MOVE -> {
                                    val dx = ev.x - downX
                                    val dy = ev.y - downY
                                    if (isSecondSideTapDown) {
                                        return true
                                    }
                                    if (isHorizontalScrubbing) {
                                        val deltaSec = (dx / w) * 90f
                                        val totalDur = videoTimeHolder[1]
                                        val dur = totalDur.coerceAtLeast(1f)
                                        val target = if (totalDur > 1f) {
                                            (scrubStartSec + deltaSec).coerceIn(0f, dur)
                                        } else {
                                            (scrubStartSec + deltaSec).coerceAtLeast(0f)
                                        }
                                        seekAbsoluteSec(target)
                                        val sign = if (deltaSec >= 0f) "+" else ""
                                        val durPart = if (totalDur > 1f) " / ${formatYtTime(totalDur)}" else ""
                                        val secUnit = DxCarI18n.tr("秒", "秒", "s", "秒")
                                        flashGestureHud(
                                            YtGestureHudZone.CENTER,
                                            "${if (deltaSec >= 0f) "⏩" else "⏪"} ${formatYtTime(target)}$durPart ($sign${deltaSec.toInt()}$secUnit)"
                                        )
                                        return true
                                    }
                                    if (!gestureHandled) {
                                        // Swipe DOWN -> Exit Right-Pane Fullscreen
                                        if (dy > 75f && kotlin.math.abs(dy) > kotlin.math.abs(dx) * 1.3f) {
                                            gestureHandled = true
                                            val cancelEv = MotionEvent.obtain(ev).apply { action = MotionEvent.ACTION_CANCEL }
                                            super.dispatchTouchEvent(cancelEv)
                                            cancelEv.recycle()
                                            exitRightPaneFullscreen()
                                            return true
                                        }
                                        // Horizontal Swipe -> Scrub Video Timeline!
                                        if (kotlin.math.abs(dx) > 28f * density.coerceAtLeast(1f) / 1.5f &&
                                            kotlin.math.abs(dx) > kotlin.math.abs(dy) * 1.35f
                                        ) {
                                            gestureHandled = true
                                            isHorizontalScrubbing = true
                                            scrubStartSec = videoTimeHolder[0]
                                            val cancelEv = MotionEvent.obtain(ev).apply { action = MotionEvent.ACTION_CANCEL }
                                            super.dispatchTouchEvent(cancelEv)
                                            cancelEv.recycle()
                                            return true
                                        }
                                    }
                                }

                                MotionEvent.ACTION_UP,
                                MotionEvent.ACTION_CANCEL -> {
                                    val nowUp = System.currentTimeMillis()
                                    if (isSecondSideTapDown) {
                                        val wasHolding = isDoubleTapHolding
                                        stopHoldAcceleration()
                                        isSecondSideTapDown = false
                                        if (!wasHolding && ev.actionMasked == MotionEvent.ACTION_UP) {
                                            if (sideTapZone > 0) {
                                                accumulatedSeekSec = if (nowUp - lastAccumulatedMs < 900L && accumulatedSeekSec > 0) {
                                                    accumulatedSeekSec + 10
                                                } else {
                                                    10
                                                }
                                                lastAccumulatedMs = nowUp
                                                seekRelativeSec(10f)
                                                lastSideTapUpMs = nowUp
                                                lastSideTapX = ev.x
                                                lastSideTapY = ev.y
                                                flashGestureHud(
                                                    YtGestureHudZone.RIGHT,
                                                    DxCarI18n.tr(
                                                        "⏩ +${accumulatedSeekSec} 秒",
                                                        "⏩ +${accumulatedSeekSec} 秒",
                                                        "⏩ +${accumulatedSeekSec}s",
                                                        "⏩ +${accumulatedSeekSec}秒"
                                                    )
                                                )
                                            } else if (sideTapZone < 0) {
                                                accumulatedSeekSec = if (nowUp - lastAccumulatedMs < 900L && accumulatedSeekSec < 0) {
                                                    accumulatedSeekSec - 10
                                                } else {
                                                    -10
                                                }
                                                lastAccumulatedMs = nowUp
                                                seekRelativeSec(-10f)
                                                lastSideTapUpMs = nowUp
                                                lastSideTapX = ev.x
                                                lastSideTapY = ev.y
                                                flashGestureHud(
                                                    YtGestureHudZone.LEFT,
                                                    DxCarI18n.tr(
                                                        "⏪ ${accumulatedSeekSec} 秒",
                                                        "⏪ ${accumulatedSeekSec} 秒",
                                                        "⏪ ${accumulatedSeekSec}s",
                                                        "⏪ ${accumulatedSeekSec}秒"
                                                    )
                                                )
                                            }
                                        }
                                        return true
                                    }
                                    if (isHorizontalScrubbing) {
                                        isHorizontalScrubbing = false
                                        return true
                                    }
                                    if (!gestureHandled &&
                                        currentSideZone != 0 &&
                                        ev.actionMasked == MotionEvent.ACTION_UP &&
                                        (nowUp - downTimeMs) < 280L
                                    ) {
                                        lastSideTapUpMs = nowUp
                                        lastSideTapX = ev.x
                                        lastSideTapY = ev.y
                                    }
                                }
                            }
                            return super.dispatchTouchEvent(ev)
                        }
                    }.apply {
                        setBackgroundColor(android.graphics.Color.BLACK)
                    }

                    fun hideCustomInline() {
                        inlineCustomView?.let { cardVideoContainer.removeView(it) }
                        inlineCustomView = null
                        inlineCustomCallback?.onCustomViewHidden()
                        inlineCustomCallback = null
                    }
                    exitCustomViewTrigger = { hideCustomInline() }

                    val bootstrapYoutubeEnhancementsJs = """
                        (function() {
                          if (window.__dxcarYtBootstrapped) return;
                          window.__dxcarYtBootstrapped = true;

                          // 1. Spoof Page Visibility & Focus so background playback continues in Stock Launcher / Google Maps / Browser!
                          try {
                            Object.defineProperty(document, 'hidden', { get: function() { return false; }, configurable: true });
                            Object.defineProperty(document, 'webkitHidden', { get: function() { return false; }, configurable: true });
                            Object.defineProperty(document, 'visibilityState', { get: function() { return 'visible'; }, configurable: true });
                            document.hasFocus = function() { return true; };
                            var blockEv = function(e) { e.stopImmediatePropagation(); };
                            document.addEventListener('visibilitychange', blockEv, true);
                            document.addEventListener('webkitvisibilitychange', blockEv, true);
                            window.addEventListener('pagehide', blockEv, true);
                            window.addEventListener('freeze', blockEv, true);
                            window.addEventListener('blur', blockEv, true);
                          } catch (e) {}

                          // 2. Non-intrusively set default playback quality preference to 720p via storage
                          try {
                            var now = Date.now();
                            var qPayload = JSON.stringify({
                              data: JSON.stringify({ quality: 720, previousQuality: 720 }),
                              expiration: now + 31536000000,
                              creation: now
                            });
                            localStorage.setItem('yt-player-quality', qPayload);
                            sessionStorage.setItem('yt-player-quality', qPayload);
                          } catch (e) {}

                          // 3. Hook YouTube player's own bottom-right Fullscreen button (Image 6) so clicking it toggles Right-Pane Fullscreen!
                          document.addEventListener('click', function(e) {
                            try {
                              var t = e.target;
                              var btn = t && t.closest ? t.closest('button.fullscreen-icon, .fullscreen-icon, .ytp-fullscreen-button, button[aria-label*="Full screen"], button[aria-label*="Exit full screen"], button[aria-label*="全螢幕"], button[aria-label*="退出全螢幕"]') : null;
                              if (btn && window.DxCarYtBridge) {
                                e.preventDefault();
                                e.stopPropagation();
                                window.DxCarYtBridge.onPlayerFullscreenButtonClicked();
                              }
                            } catch (err) {}
                          }, true);

                          // 4. Lightweight playback progress mirror for horizontal timeline scrubbing
                          setInterval(function() {
                            try {
                              var v = document.querySelector('video');
                              if (v && window.DxCarYtBridge) {
                                window.DxCarYtBridge.onVideoProgress(
                                  v.currentTime || 0,
                                  (v.duration && isFinite(v.duration)) ? v.duration : 0
                                );
                              }
                            } catch (err) {}
                          }, 350);
                        })();
                    """.trimIndent()

                    val wv = BackgroundPlayableWebView(ctx).apply {
                        layoutParams = FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        CookieManager.getInstance().setAcceptCookie(true)
                        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                        settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            mediaPlaybackRequiresUserGesture = false
                            loadsImagesAutomatically = true
                            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                            cacheMode = WebSettings.LOAD_DEFAULT
                            userAgentString =
                                "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36"
                        }
                        addJavascriptInterface(
                            object {
                                @JavascriptInterface
                                fun onPlayerFullscreenButtonClicked() {
                                    mainHandler.post {
                                        if (isRightPaneFullscreen) {
                                            exitRightPaneFullscreen()
                                        } else {
                                            enterRightPaneFullscreen()
                                        }
                                    }
                                }

                                @JavascriptInterface
                                fun onVideoProgress(cur: Float, dur: Float) {
                                    videoTimeHolder[0] = cur
                                    videoTimeHolder[1] = dur
                                    if (pendingCloseSourceYt && cur > 0.2f) {
                                        pendingCloseSourceYt = false
                                        mainHandler.post {
                                            com.dxcar.launcher.accessibility.DxCarAccessibilityService.instance
                                                ?.closeYoutubePipOrApp(context)
                                        }
                                    }
                                }
                            },
                            "DxCarYtBridge"
                        )
                        webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                view?.evaluateJavascript(bootstrapYoutubeEnhancementsJs, null)
                                if (isRightPaneFullscreen) {
                                    view?.evaluateJavascript(applyRightPaneFsCssJs, null)
                                }
                                val currentUrl = url.orEmpty()
                                if (pendingAutoClickFirstResult && currentUrl.contains("/results")) {
                                    pendingAutoClickFirstResult = false
                                    view?.evaluateJavascript(
                                        """
                                        (function() {
                                          var tries = 0;
                                          var timer = setInterval(function() {
                                            tries++;
                                            var a = document.querySelector('ytm-video-with-context-renderer a[href*="/watch"], a[href*="/watch?v="]');
                                            if (a) {
                                              clearInterval(timer);
                                              a.click();
                                            } else if (tries > 20) {
                                              clearInterval(timer);
                                            }
                                          }, 350);
                                        })();
                                        """.trimIndent(),
                                        null
                                    )
                                } else if (currentUrl.contains("/watch") && (pendingSeekSec > 0 || pendingCloseSourceYt)) {
                                    val seekTarget = pendingSeekSec
                                    pendingSeekSec = 0
                                    view?.evaluateJavascript(
                                        """
                                        (function() {
                                          var target = $seekTarget;
                                          var tries = 0;
                                          var timer = setInterval(function() {
                                            tries++;
                                            var v = document.querySelector('video');
                                            if (v && v.readyState >= 1) {
                                              if (target > 0 && Math.abs((v.currentTime || 0) - target) > 2) {
                                                try { v.currentTime = target; } catch(e) {}
                                              }
                                              try { v.play(); } catch(e) {}
                                              clearInterval(timer);
                                            } else if (tries > 25) {
                                              clearInterval(timer);
                                            }
                                          }, 350);
                                        })();
                                        """.trimIndent(),
                                        null
                                    )
                                }
                            }
                        }
                        webChromeClient = object : WebChromeClient() {
                            override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                                if (view == null) return
                                inlineCustomView?.let { cardVideoContainer.removeView(it) }
                                inlineCustomView = view
                                inlineCustomCallback = callback
                                isRightPaneFullscreen = true
                                cardVideoContainer.addView(
                                    view,
                                    FrameLayout.LayoutParams(
                                        ViewGroup.LayoutParams.MATCH_PARENT,
                                        ViewGroup.LayoutParams.MATCH_PARENT
                                    )
                                )
                            }

                            override fun onHideCustomView() {
                                hideCustomInline()
                                evalYoutubeJs(removeRightPaneFsCssJs)
                                isRightPaneFullscreen = false
                            }
                        }
                        resumeTimers()
                        loadUrl("https://m.youtube.com")
                    }
                    webViewRef = wv
                    cardVideoContainer.addView(wv)
                    cardVideoContainer
                },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            )
        }

        // Fullscreen Gesture Feedback Overlays (Left -10s / Center Scrub / Right +10s / Top Hold Speed)
        val activeHud = gestureHud
        AnimatedVisibility(
            visible = isRightPaneFullscreen && activeHud != null,
            enter = fadeIn(tween(110)),
            exit = fadeOut(tween(180)),
            modifier = Modifier
                .align(
                    when (activeHud?.zone) {
                        YtGestureHudZone.LEFT -> Alignment.CenterStart
                        YtGestureHudZone.RIGHT -> Alignment.CenterEnd
                        YtGestureHudZone.TOP -> Alignment.TopCenter
                        else -> Alignment.Center
                    }
                )
                .padding(horizontal = 24.dp, vertical = 18.dp)
        ) {
            Surface(
                color = Color(0xCC0F172A),
                shape = RoundedCornerShape(22.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.25f))
            ) {
                Text(
                    text = activeHud?.label.orEmpty(),
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp)
                )
            }
        }

        // Pinned top-left YouTube App handoff button (always visible on Page 2 YouTube: windowed & fullscreen)
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 10.dp, top = 10.dp)
                .size(30.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xE6FF0000))
                .border(1.dp, Color.White.copy(alpha = 0.45f), RoundedCornerShape(8.dp))
                .clickable { openCurrentYoutubeInApp() }
        ) {
            Icon(
                imageVector = Icons.Rounded.PlayArrow,
                contentDescription = str(
                    "於 YouTube App 接續播放",
                    "在 YouTube App 中继续播放",
                    "Continue in YouTube App",
                    "YouTube アプリで続きから再生"
                ),
                tint = Color.White,
                modifier = Modifier.size(19.dp)
            )
        }

        // Brief 2.2s hint when entering right-half YouTube fullscreen
        AnimatedVisibility(
            visible = showFsHintBanner,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 12.dp)
        ) {
            Surface(
                color = Color(0xCC0F172A),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(
                    text = str(
                        "已進入右半邊全螢幕 · 下滑或點右下角按鈕可退出",
                        "已进入右半边全屏 · 下滑或点右下角按钮可退出",
                        "Right-pane fullscreen · Swipe down or tap bottom-right to exit",
                        "右半分フルスクリーン · 下スワイプまたは右下ボタンで終了"
                    ),
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun BuiltInYoutubeFullscreenScreen(
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalDxCarColors.current
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    CookieManager.getInstance().setAcceptCookie(true)
                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        mediaPlaybackRequiresUserGesture = false
                        cacheMode = WebSettings.LOAD_DEFAULT
                        userAgentString =
                            "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36"
                    }
                    webViewClient = WebViewClient()
                    webChromeClient = WebChromeClient()
                    loadUrl("https://m.youtube.com")
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        Surface(
            color = colors.glassOverlay,
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(14.dp)
                .border(1.dp, colors.cardBorder, RoundedCornerShape(20.dp))
                .bouncyClickable(pressedScale = 0.90f, onClick = onClose)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Rounded.Close,
                    contentDescription = str("返回首頁", "返回首页", "Back to Home", "ホームへ戻る"),
                    tint = colors.primaryText,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    str("返回分割首頁", "返回分屏首页", "Back to Split Home", "分割ホームへ戻る"),
                    color = colors.primaryText,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

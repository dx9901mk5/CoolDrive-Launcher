package com.dxcar.launcher.ui

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
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
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.Brightness6
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.PlayArrow
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
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
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
        override val label: String get() = shortcut.label.ifBlank { "分屏捷徑" }
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
        a.category == AppCategory.NAVIGATION && b.category == AppCategory.NAVIGATION -> "導航地圖"
        musicWords.any { combined.contains(it) } && a.category == AppCategory.MEDIA && b.category == AppCategory.MEDIA -> "音樂播放"
        videoWords.any { combined.contains(it) } && a.category == AppCategory.MEDIA && b.category == AppCategory.MEDIA -> "影音娛樂"
        a.category == AppCategory.MEDIA && b.category == AppCategory.MEDIA -> "影音音樂"
        a.category == AppCategory.COMMUNICATION && b.category == AppCategory.COMMUNICATION -> "通訊社群"
        a.category == AppCategory.SYSTEM && b.category == AppCategory.SYSTEM -> "系統工具"
        a.category == AppCategory.NAVIGATION || b.category == AppCategory.NAVIGATION -> "行車常用"
        else -> "常用工具"
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
                        text = "應用程式",
                        color = colors.primaryText,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.width(8.dp))
                    AppCategory.entries.forEach { cat ->
                        val selected = cat == selectedCategory
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
                                text = cat.labelZh,
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
                                    contentDescription = "DX-Car Launcher 設定",
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
                                text = if (hasPendingUpdate) "設定 · 有更新" else "啟動器設定",
                                color = colors.primaryText,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    IconButton(onClick = onCloseDrawer) {
                        Icon(Icons.Rounded.Close, contentDescription = "返回首頁", tint = colors.secondaryText)
                    }
                }
            }

            Spacer(Modifier.height(6.dp))
            Text(
                text = "點擊啟動 · 長按 App 重疊到另一 App 可建立資料夾 · 往上拖曳至「✕ 移除」會詢問確認後解除安裝",
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
                        label = "分屏設定",
                        subLabel = "AOSP 可拖動分屏",
                        icon = Icons.Rounded.Splitscreen,
                        tint = Color(0xFF22C55E),
                        badgeAlert = false,
                        onClick = onOpenSplitCreator
                    )
                }

                item(key = "__dxcar_settings__") {
                    BuiltInDrawerTile(
                        label = "DX-Car Launcher 設定",
                        subLabel = if (hasPendingUpdate) "有新版本" else "權限 · 風格 · 更新",
                        icon = Icons.Rounded.Settings,
                        tint = colors.accent,
                        badgeAlert = hasPendingUpdate,
                        onClick = onOpenSettings
                    )
                }

                item(key = "__dxcar_youtube__") {
                    BuiltInDrawerTile(
                        label = "YouTube 全螢幕",
                        subLabel = "影音播放",
                        icon = Icons.Rounded.PlayArrow,
                        tint = Color(0xFFEF4444),
                        badgeAlert = false,
                        onClick = onLaunchYoutube
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
                                        text = sc.label.ifBlank { "分屏捷徑" },
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
                                    text = if (isHoverTarget) "➕ 加入「${folder.name}」" else folder.name,
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
                                text = if (isHoverFolderTarget) "➕ 放開建立資料夾" else app.label,
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
                        contentDescription = "移除",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = when {
                            isHoveringRemove -> "放開後確認移除／解除安裝"
                            hoveredOverlapKey != null -> "➕ 放開建立或加入資料夾"
                            else -> "✕ 往上拖曳至此移除／解除安裝"
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
                                    text = "確定要解除安裝「${target.app.label}」嗎？",
                                    color = colors.primaryText,
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = if (target.app.isSystemApp) {
                                        "此應用程式為車機系統預載軟體；若系統限制無法移除，可點擊「應用資訊」進行停用或強制停止。"
                                    } else {
                                        "點擊「確認解除安裝」後將開啟系統安裝管理員執行卸載。"
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
                                        Text("取消", fontSize = 15.sp)
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
                                        Text("應用資訊", fontSize = 15.sp)
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
                                        Text("確認解除安裝", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }

                            is DragRemoveTarget.SplitShortcutItem -> {
                                Text(
                                    text = "確定要移除分屏捷徑「${target.label}」嗎？",
                                    color = colors.primaryText,
                                    fontSize = 21.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "僅移除此雙視窗捷徑，不會刪除原本的應用程式。",
                                    color = colors.secondaryText,
                                    fontSize = 15.sp
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    OutlinedButton(onClick = { confirmRemoveTarget = null }, shape = RoundedCornerShape(14.dp)) {
                                        Text("取消", fontSize = 15.sp)
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
                                        Text("確認移除", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }

                            is DragRemoveTarget.FolderTarget -> {
                                Text(
                                    text = "確定要解散資料夾「${target.folder.name}」嗎？",
                                    color = colors.primaryText,
                                    fontSize = 21.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "解散後資料夾內的應用程式不會被刪除，將全數回到應用程式清單。",
                                    color = colors.secondaryText,
                                    fontSize = 15.sp
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    OutlinedButton(onClick = { confirmRemoveTarget = null }, shape = RoundedCornerShape(14.dp)) {
                                        Text("取消", fontSize = 15.sp)
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
                                        Text("確認解散", fontSize = 15.sp, fontWeight = FontWeight.Bold)
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
            val presetSuggestions = listOf("導航地圖", "音樂播放", "影音娛樂", "通訊社群", "行車常用", "常用工具")

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
                                "建立新資料夾（已自動判定預設名稱）"
                            } else {
                                "重新命名資料夾"
                            },
                            color = colors.primaryText,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                        if (activeNamingState != null) {
                            Text(
                                text = "包含：${activeNamingState.firstApp.label} ＋ ${activeNamingState.secondApp.label}",
                                color = colors.accent,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        OutlinedTextField(
                            value = folderNameInput,
                            onValueChange = { folderNameInput = it },
                            label = { Text("資料夾名稱") },
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
                                Text("取消", fontSize = 15.sp)
                            }
                            Spacer(Modifier.width(10.dp))
                            Button(
                                onClick = {
                                    val finalName = folderNameInput.trim().ifBlank { initialText.ifBlank { "常用工具" } }
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
                                Text(if (activeNamingState != null) "確認建立" else "儲存名稱", fontSize = 15.sp, fontWeight = FontWeight.Bold)
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
                                    Icon(Icons.Rounded.Edit, contentDescription = "修改名稱", tint = colors.secondaryText, modifier = Modifier.size(20.dp))
                                }
                            }
                            IconButton(onClick = { openedFolderId = null }) {
                                Icon(Icons.Rounded.Close, contentDescription = "關閉", tint = colors.primaryText)
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
                                            text = "移出資料夾",
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
                            text = "下拉快捷控制面板  ·  ${systemStatus.time12h}",
                            color = colors.primaryText,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                        val netLabel = buildString {
                            append(if (systemStatus.wifiConnected) "Wi-Fi：${systemStatus.wifiSsid.ifBlank { "已連線" }}" else "Wi-Fi：未連線")
                            append("  ·  ")
                            append(if (systemStatus.bluetoothConnected) "藍牙：${systemStatus.connectedBtName.ifBlank { "已配對連線" }}" else "藍牙：待連線")
                        }
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
                                Text("系統通知欄")
                            }
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Rounded.Close, contentDescription = "關閉", tint = colors.primaryText)
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
                        label = if (systemStatus.wifiConnected) "Wi-Fi 設定" else "開啟 Wi-Fi",
                        active = systemStatus.wifiConnected,
                        onClick = { onDismiss(); onOpenWifiSettings() },
                        modifier = Modifier.weight(1f)
                    )
                    QuickTile(
                        icon = Icons.Rounded.Bluetooth,
                        label = if (systemStatus.bluetoothConnected) "藍牙已連" else "藍牙設定",
                        active = systemStatus.bluetoothConnected,
                        onClick = { onDismiss(); onOpenBluetoothSettings() },
                        modifier = Modifier.weight(1f)
                    )
                    QuickTile(
                        icon = if (isNight) Icons.Rounded.DarkMode else Icons.Rounded.LightMode,
                        label = if (isNight) "夜間模式" else "白天模式",
                        active = isNight,
                        onClick = onToggleDayNight,
                        modifier = Modifier.weight(1f)
                    )
                    if (onToggle3DCursor != null) {
                        QuickTile(
                            icon = Icons.Rounded.ViewInAr,
                            label = "3D 俯瞰游標",
                            active = false,
                            onClick = { onDismiss(); onToggle3DCursor() },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    QuickTile(
                        icon = Icons.Rounded.SwapHoriz,
                        label = "左右互換",
                        active = false,
                        onClick = { onSwapSplit(); onDismiss() },
                        modifier = Modifier.weight(1f)
                    )
                    QuickTile(
                        icon = Icons.Rounded.Settings,
                        label = "啟動器設定",
                        active = false,
                        onClick = { onDismiss(); onOpenSettings() },
                        modifier = Modifier.weight(1f)
                    )
                }

                // Brightness Slider
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Brightness6, contentDescription = null, tint = colors.accent)
                    Spacer(Modifier.width(12.dp))
                    Text("亮度", color = colors.primaryText, fontSize = 14.sp, modifier = Modifier.width(42.dp))
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
                    Text("音量", color = colors.primaryText, fontSize = 14.sp, modifier = Modifier.width(42.dp))
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
                Icon(Icons.Rounded.Close, contentDescription = "返回首頁", tint = colors.primaryText, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("返回分割首頁", color = colors.primaryText, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

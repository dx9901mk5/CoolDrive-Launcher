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
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Splitscreen
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
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
    data class InstalledAppTarget(val app: InstalledAppInfo) : DragRemoveTarget {
        override val label: String get() = app.label
    }
}

/**
 * Full AA App Drawer launched from the bottom-left 9-dot button:
 * - Includes "分屏設定" (Split-Screen Shortcut Creator), "DX-Car Launcher 設定", "YouTube 全螢幕"
 * - Renders saved Dual-Icon Split-Screen Shortcuts right in the grid
 * - Long-press & drag upward into the floating "✕ 移除" bar to remove shortcuts or uninstall apps!
 * - Rubber-band spring overscroll bounce at top & bottom of list!
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppDrawerScreen(
    apps: List<InstalledAppInfo>,
    splitShortcuts: List<SplitScreenShortcut>,
    hasPendingUpdate: Boolean,
    onLaunchApp: (String) -> Unit,
    onLaunchSplitShortcut: (SplitScreenShortcut) -> Unit,
    onRemoveSplitShortcut: (SplitScreenShortcut) -> Unit,
    onUninstallApp: (InstalledAppInfo) -> Unit,
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
    val filtered = remember(apps, selectedCategory) {
        if (selectedCategory == AppCategory.ALL) apps
        else apps.filter { it.category == selectedCategory }
    }

    var rootPosition by remember { mutableStateOf(Offset.Zero) }
    var draggingTarget by remember { mutableStateOf<DragRemoveTarget?>(null) }
    var dragFingerInRoot by remember { mutableStateOf(Offset.Zero) }

    val dropThresholdPx = with(density) { 108.dp.toPx() }
    val isHoveringRemove = draggingTarget != null && (dragFingerInRoot.y - rootPosition.y) <= dropThresholdPx

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
            // HEADER: Title + Category Filter + Prominent Settings Button
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
                text = "點擊啟動 · 點「分屏設定」自訂分割捷徑 · 長按並往上拖曳至「✕ 移除」可移除捷徑或解除安裝 App",
                color = colors.secondaryText,
                fontSize = 12.sp
            )
            Spacer(Modifier.height(12.dp))

            // APP GRID with spacious car-safe icons & spring rubber-band overscroll bounce!
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 148.dp),
                contentPadding = PaddingValues(top = 8.dp, bottom = 28.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                verticalArrangement = Arrangement.spacedBy(22.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .springBounceOverscroll(maxOverscrollPx = 190f)
            ) {
                // Built-in Split-Screen Shortcut Creator ("分屏設定")
                item(key = "__dxcar_split_creator__") {
                    BuiltInDrawerTile(
                        label = "分屏設定",
                        subLabel = "多比例分割捷徑",
                        icon = Icons.Rounded.Splitscreen,
                        tint = Color(0xFF22C55E),
                        badgeAlert = false,
                        onClick = onOpenSplitCreator
                    )
                }

                // Built-in Launcher Settings Tile (Style, GitHub, Auto-Update)
                item(key = "__dxcar_settings__") {
                    BuiltInDrawerTile(
                        label = "DX-Car Launcher 設定",
                        subLabel = if (hasPendingUpdate) "有新版本" else "風格與更新",
                        icon = Icons.Rounded.Settings,
                        tint = colors.accent,
                        badgeAlert = hasPendingUpdate,
                        onClick = onOpenSettings
                    )
                }

                // Built-in / Native YouTube Fullscreen Shortcut
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

                // User-Created Split-Screen Shortcuts (with Dual Icon + Long-Press Drag Up to Remove!)
                if (selectedCategory == AppCategory.ALL) {
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
                                            if (dropInZone) onRemoveSplitShortcut(sc)
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
                }

                // All Installed Apps (Long-Press & Drag Upward to "✕ 移除" to Uninstall!)
                items(filtered, key = { it.packageName }) { app ->
                    val iconBmp = remember(app.icon) { app.icon.asImageBitmap() }
                    var tileOrigin by remember { mutableStateOf(Offset.Zero) }
                    val isBeingDragged = (draggingTarget as? DragRemoveTarget.InstalledAppTarget)?.app?.packageName == app.packageName

                    Surface(
                        color = colors.cardSurface,
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier
                            .alpha(if (isBeingDragged) 0.3f else 1f)
                            .border(1.dp, colors.cardBorder, RoundedCornerShape(20.dp))
                            .clip(RoundedCornerShape(20.dp))
                            .onGloballyPositioned { tileOrigin = it.positionInRoot() }
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
                                    onDragEnd = {
                                        val dropInZone = (dragFingerInRoot.y - rootPosition.y) <= dropThresholdPx
                                        draggingTarget = null
                                        if (dropInZone) onUninstallApp(app)
                                    },
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
                                text = app.label,
                                color = colors.primaryText,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }

        // FLOATING TOP "✕ 移除" DROP ZONE (Video 1 Long-Press Drag-Up to Remove!)
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
                        text = if (isHoveringRemove) "放開即可移除" else "✕ 拖曳至此移除",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // FLOATING DRAGGED GHOST ICON FOLLOWING FINGER
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
                        color = if (isHoveringRemove) Color(0xFFEF4444) else colors.accent,
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
 * Quick Control Center Overlay (opened by tapping the Clock in the Dock).
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
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit
) {
    val colors = LocalDxCarColors.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
            .clickable { onDismiss() },
        contentAlignment = Alignment.Center
    ) {
        Surface(
            color = colors.cardSurface,
            shape = RoundedCornerShape(28.dp),
            shadowElevation = 20.dp,
            modifier = Modifier
                .fillMaxWidth(0.72f)
                .border(1.dp, colors.cardBorder, RoundedCornerShape(28.dp))
                .clickable(enabled = false) {}
        ) {
            Column(
                modifier = Modifier.padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "${systemStatus.time24h}  ${systemStatus.dateLabel}",
                            color = colors.primaryText,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold
                        )
                        val netLabel = buildString {
                            append(if (systemStatus.wifiConnected) "Wi-Fi：${systemStatus.wifiSsid.ifBlank { "已連線" }}" else "Wi-Fi：未連線")
                            append("  ·  ")
                            append(if (systemStatus.bluetoothConnected) "藍牙：${systemStatus.connectedBtName.ifBlank { "已配對連線" }}" else "藍牙：待連線")
                        }
                        Text(text = netLabel, color = colors.secondaryText, fontSize = 13.sp)
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Rounded.Close, contentDescription = "關閉", tint = colors.primaryText)
                    }
                }

                // Quick Action Tiles
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    QuickTile(
                        icon = if (isNight) Icons.Rounded.DarkMode else Icons.Rounded.LightMode,
                        label = if (isNight) "夜間模式" else "白天模式",
                        active = isNight,
                        onClick = onToggleDayNight,
                        modifier = Modifier.weight(1f)
                    )
                    QuickTile(
                        icon = Icons.Rounded.Wifi,
                        label = if (systemStatus.wifiConnected) "Wi-Fi 已連" else "Wi-Fi 設定",
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
                        icon = Icons.Rounded.SwapHoriz,
                        label = "左右互換",
                        active = false,
                        onClick = { onSwapSplit(); onDismiss() },
                        modifier = Modifier.weight(1f)
                    )
                    QuickTile(
                        icon = Icons.Rounded.Settings,
                        label = "詳細設定",
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
            .height(74.dp)
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

/**
 * Built-in Fullscreen YouTube WebView Fallback (when native YouTube app isn't installed on an AI Box).
 */
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

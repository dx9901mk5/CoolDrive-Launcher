package com.cooldrive.launcher.ui

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Settings
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.cooldrive.launcher.system.AppCategory
import com.cooldrive.launcher.system.InstalledAppInfo
import com.cooldrive.launcher.system.SystemStatus
import com.cooldrive.launcher.theme.LocalCoolDriveColors

/**
 * Full AA App Drawer launched from the bottom-left 9-dot button:
 * - Includes a prominent built-in "設定（風格 / 更新 / GitHub）" tile + top-right Settings button
 * - Supports launching any installed app full-screen (including YouTube)
 * - Long-press any app to Pin/Unpin to the Persistent Dock
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppDrawerScreen(
    apps: List<InstalledAppInfo>,
    pinnedPackages: List<String>,
    hasPendingUpdate: Boolean,
    onLaunchApp: (String) -> Unit,
    onLaunchYoutube: () -> Unit,
    onTogglePin: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onCloseDrawer: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalCoolDriveColors.current
    var selectedCategory by remember { mutableStateOf(AppCategory.ALL) }

    val filtered = remember(apps, selectedCategory) {
        if (selectedCategory == AppCategory.ALL) apps
        else apps.filter { it.category == selectedCategory }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.canvasBackground)
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
                // Dedicated Settings button inside App Menu (Style, GitHub, Auto-Update)
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
                                contentDescription = "CoolDrive 設定",
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
            text = "點擊全螢幕啟動 · 長按圖示可釘選／取消釘選至常駐 Dock",
            color = colors.secondaryText,
            fontSize = 12.sp
        )
        Spacer(Modifier.height(12.dp))

        // APP GRID with real spring rubber-band overscroll bounce at top & bottom!
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 112.dp),
            contentPadding = PaddingValues(top = 4.dp, bottom = 22.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier
                .fillMaxSize()
                .springBounceOverscroll(maxOverscrollPx = 190f)
        ) {
            // Built-in Launcher Settings Tile (Style, GitHub, Auto-Update)
            item(key = "__cooldrive_settings__") {
                BuiltInDrawerTile(
                    label = "CoolDrive 設定",
                    subLabel = if (hasPendingUpdate) "有新版本" else "風格與更新",
                    icon = Icons.Rounded.Settings,
                    tint = colors.accent,
                    badgeAlert = hasPendingUpdate,
                    onClick = onOpenSettings
                )
            }

            // Built-in / Native YouTube Fullscreen Shortcut
            item(key = "__cooldrive_youtube__") {
                BuiltInDrawerTile(
                    label = "YouTube 全螢幕",
                    subLabel = "影音播放",
                    icon = Icons.Rounded.PlayArrow,
                    tint = Color(0xFFEF4444),
                    badgeAlert = false,
                    onClick = onLaunchYoutube
                )
            }

            items(filtered, key = { it.packageName }) { app ->
                val isPinned = pinnedPackages.contains(app.packageName)
                val iconBmp = remember(app.icon) { app.icon.asImageBitmap() }

                Surface(
                    color = colors.cardSurface,
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier
                        .border(
                            width = if (isPinned) 1.5.dp else 1.dp,
                            color = if (isPinned) colors.accent else colors.cardBorder,
                            shape = RoundedCornerShape(20.dp)
                        )
                        .clip(RoundedCornerShape(20.dp))
                        .bouncyCombinedClickable(
                            pressedScale = 0.90f,
                            onClick = { onLaunchApp(app.packageName) },
                            onLongClick = { onTogglePin(app.packageName) }
                        )
                ) {
                    Box(modifier = Modifier.padding(12.dp)) {
                        if (isPinned) {
                            Icon(
                                imageVector = Icons.Rounded.PushPin,
                                contentDescription = "已釘選至 Dock",
                                tint = colors.accent,
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .size(14.dp)
                            )
                        }
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Image(
                                bitmap = iconBmp,
                                contentDescription = app.label,
                                modifier = Modifier
                                    .size(54.dp)
                                    .clip(RoundedCornerShape(14.dp))
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = app.label,
                                color = colors.primaryText,
                                fontSize = 13.sp,
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
    val colors = LocalCoolDriveColors.current
    Surface(
        color = colors.cardSurfaceElevated,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier
            .border(1.2.dp, tint.copy(alpha = 0.55f), RoundedCornerShape(20.dp))
            .clip(RoundedCornerShape(20.dp))
            .bouncyClickable(pressedScale = 0.90f, onClick = onClick)
    ) {
        Box(modifier = Modifier.padding(12.dp)) {
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
                        .size(54.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(tint.copy(alpha = 0.18f))
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = label,
                        tint = tint,
                        modifier = Modifier.size(30.dp)
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = label,
                    color = colors.primaryText,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subLabel,
                    color = tint,
                    fontSize = 10.sp,
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
    val colors = LocalCoolDriveColors.current
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
    val colors = LocalCoolDriveColors.current
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
    val colors = LocalCoolDriveColors.current
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

package com.cooldrive.launcher.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.BluetoothConnected
import androidx.compose.material.icons.rounded.BluetoothDisabled
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cooldrive.launcher.layout.ResolvedDockPlacement
import com.cooldrive.launcher.system.InstalledAppInfo
import com.cooldrive.launcher.system.SystemStatus
import com.cooldrive.launcher.theme.LocalCoolDriveColors

data class DockAppSlot(
    val id: String,
    val label: String,
    val packageName: String?,
    val fallbackIcon: ImageVector,
    val fallbackTint: Color,
    val installedApp: InstalledAppInfo? = null,
    val isActive: Boolean = false
)

/**
 * Authentic Android Auto Coolwalk Dock:
 * - Vertical Side Rail (Left / Right) OR Horizontal Bottom Bar based on DPI/Google rules.
 * - Bottom-left corner ALWAYS houses the 9-dot App Menu Launcher button ("左下角啟動器有app選單可以展開").
 * - Status Cluster displays live Time + Wi-Fi status + Bluetooth status in exact AA positions:
 *     * Tapping Wi-Fi opens System Wi-Fi Settings directly.
 *     * Tapping Bluetooth opens System Bluetooth Settings directly.
 *     * Tapping Time opens Quick Control Center (Brightness, Volume, Day/Night, etc.).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CoolwalkDock(
    placement: ResolvedDockPlacement,
    thicknessDp: Int,
    systemStatus: SystemStatus,
    is24HourClock: Boolean,
    isAppDrawerOpen: Boolean,
    isDashboardActive: Boolean,
    unreadCount: Int,
    pinnedSlots: List<DockAppSlot>,
    rightHandDrive: Boolean = false,
    isOverlayMode: Boolean = false,
    isOverlayPinnedOpen: Boolean = false,
    onToggleAppDrawer: () -> Unit,
    onGoDashboard: () -> Unit,
    onSlotClick: (DockAppSlot) -> Unit,
    onWifiClick: () -> Unit,
    onBluetoothClick: () -> Unit,
    onBluetoothLongClick: () -> Unit = onBluetoothClick,
    onClockClick: () -> Unit,
    onNotificationsClick: () -> Unit,
    onCollapseOverlay: (() -> Unit)? = null,
    onTogglePinOverlay: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val colors = LocalCoolDriveColors.current
    val timeText = if (is24HourClock) systemStatus.time24h else systemStatus.time12h

    if (placement == ResolvedDockPlacement.BOTTOM) {
        val launcherCluster: @Composable () -> Unit = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DockLauncherButton(
                    isAppDrawerOpen = isAppDrawerOpen,
                    isDashboardActive = isDashboardActive,
                    onClick = if (isAppDrawerOpen) onGoDashboard else onToggleAppDrawer,
                    onLongClick = onGoDashboard
                )
                if (isOverlayMode && onTogglePinOverlay != null) {
                    SmallDockUtilityIcon(
                        icon = Icons.Rounded.PushPin,
                        contentDescription = if (isOverlayPinnedOpen) "取消釘選常駐" else "暫時釘選展開",
                        tint = if (isOverlayPinnedOpen) colors.accent else colors.secondaryText,
                        onClick = onTogglePinOverlay
                    )
                }
                if (isOverlayMode && onCollapseOverlay != null) {
                    SmallDockUtilityIcon(
                        icon = Icons.Rounded.ChevronLeft,
                        contentDescription = "收合常駐列",
                        tint = colors.secondaryText,
                        onClick = onCollapseOverlay
                    )
                }
            }
        }
        val appsCluster: @Composable () -> Unit = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                pinnedSlots.take(5).forEach { slot ->
                    DockAppIconTile(
                        slot = slot,
                        sizeDp = (thicknessDp - 20).coerceIn(44, 54),
                        onClick = { onSlotClick(slot) }
                    )
                }
            }
        }
        val statusCluster: @Composable () -> Unit = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                NotificationBellButton(
                    unreadCount = unreadCount,
                    onClick = onNotificationsClick
                )
                WifiStatusChip(
                    connected = systemStatus.wifiConnected,
                    enabled = systemStatus.wifiEnabled,
                    onClick = onWifiClick
                )
                BluetoothStatusChip(
                    connected = systemStatus.bluetoothConnected,
                    enabled = systemStatus.bluetoothEnabled,
                    onClick = onBluetoothClick,
                    onLongClick = onBluetoothLongClick
                )
                ClockChip(
                    timeText = timeText,
                    compact = false,
                    onClick = onClockClick
                )
            }
        }

        // Horizontal Bottom Bar (mirrors seamlessly for LHD vs RHD!)
        Surface(
            color = colors.dockBackground,
            tonalElevation = 4.dp,
            modifier = modifier
                .fillMaxWidth()
                .height(thicknessDp.dp)
                .border(
                    width = 0.75.dp,
                    color = colors.cardBorder.copy(alpha = 0.6f)
                )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                if (rightHandDrive) {
                    statusCluster()
                    appsCluster()
                    launcherCluster()
                } else {
                    launcherCluster()
                    appsCluster()
                    statusCluster()
                }
            }
        }
    } else {
        // Vertical Side Rail (Top = Exact AA Status Cluster [Time + Wi-Fi + BT], Middle = Pinned Apps, Bottom-Left = 9-dot App Menu Launcher!)
        Surface(
            color = colors.dockBackground,
            tonalElevation = 4.dp,
            modifier = modifier
                .fillMaxHeight()
                .width(thicknessDp.dp)
                .border(
                    width = 0.75.dp,
                    color = colors.cardBorder.copy(alpha = 0.6f)
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .padding(horizontal = 6.dp, vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // TOP: Exact AA Coolwalk Vertical Rail Status Cluster (Time + Wi-Fi + Bluetooth)
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    ClockChip(
                        timeText = timeText,
                        compact = true,
                        onClick = onClockClick
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        WifiStatusChip(
                            connected = systemStatus.wifiConnected,
                            enabled = systemStatus.wifiEnabled,
                            compact = true,
                            onClick = onWifiClick
                        )
                        BluetoothStatusChip(
                            connected = systemStatus.bluetoothConnected,
                            enabled = systemStatus.bluetoothEnabled,
                            compact = true,
                            onClick = onBluetoothClick,
                            onLongClick = onBluetoothLongClick
                        )
                    }
                    if (isOverlayMode && (onCollapseOverlay != null || onTogglePinOverlay != null)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            if (onTogglePinOverlay != null) {
                                SmallDockUtilityIcon(
                                    icon = Icons.Rounded.PushPin,
                                    contentDescription = if (isOverlayPinnedOpen) "取消釘選常駐" else "暫時釘選展開",
                                    tint = if (isOverlayPinnedOpen) colors.accent else colors.secondaryText,
                                    onClick = onTogglePinOverlay
                                )
                            }
                            if (onCollapseOverlay != null) {
                                SmallDockUtilityIcon(
                                    icon = Icons.Rounded.ChevronLeft,
                                    contentDescription = "收合常駐列",
                                    tint = colors.secondaryText,
                                    onClick = onCollapseOverlay
                                )
                            }
                        }
                    }
                }

                // MIDDLE: Pinned / Recent Car Apps (Maps, YouTube, Music, Phone)
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    pinnedSlots.take(4).forEach { slot ->
                        DockAppIconTile(
                            slot = slot,
                            sizeDp = (thicknessDp - 24).coerceIn(46, 54),
                            onClick = { onSlotClick(slot) }
                        )
                    }
                }

                // BOTTOM-LEFT: Notification Bell + Classic AA 9-Dot App Menu Launcher Button
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    NotificationBellButton(
                        unreadCount = unreadCount,
                        onClick = onNotificationsClick
                    )
                    DockLauncherButton(
                        isAppDrawerOpen = isAppDrawerOpen,
                        isDashboardActive = isDashboardActive,
                        onClick = if (isAppDrawerOpen) onGoDashboard else onToggleAppDrawer,
                        onLongClick = onGoDashboard
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DockLauncherButton(
    isAppDrawerOpen: Boolean,
    isDashboardActive: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val colors = LocalCoolDriveColors.current
    val highlighted = isAppDrawerOpen
    val iconRotation by animateFloatAsState(
        targetValue = if (highlighted) 90f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "launcherRotation"
    )
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(50.dp)
            .clip(CircleShape)
            .background(if (highlighted) colors.accent else colors.dockIconContainer)
            .border(
                width = 1.5.dp,
                color = if (highlighted) colors.accent else colors.cardBorder,
                shape = CircleShape
            )
            .bouncyCombinedClickable(
                pressedScale = 0.88f,
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {
        Icon(
            imageVector = if (isAppDrawerOpen || !isDashboardActive) {
                Icons.Rounded.Dashboard
            } else {
                Icons.Rounded.Apps
            },
            contentDescription = if (isAppDrawerOpen) "回到分割首頁" else "展開 App 選單",
            tint = if (highlighted) colors.onAccent else colors.primaryText,
            modifier = Modifier
                .size(26.dp)
                .graphicsLayer { rotationZ = iconRotation }
        )
    }
}

@Composable
private fun DockAppIconTile(
    slot: DockAppSlot,
    sizeDp: Int,
    onClick: () -> Unit
) {
    val colors = LocalCoolDriveColors.current
    val bitmapImage = remember(slot.installedApp?.icon) {
        slot.installedApp?.icon?.asImageBitmap()
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(sizeDp.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (slot.isActive) colors.accent.copy(alpha = 0.22f)
                else colors.dockIconContainer
            )
            .border(
                width = if (slot.isActive) 2.dp else 1.dp,
                color = if (slot.isActive) colors.accent else colors.cardBorder.copy(alpha = 0.5f),
                shape = RoundedCornerShape(16.dp)
            )
            .bouncyClickable(pressedScale = 0.88f, onClick = onClick)
    ) {
        if (bitmapImage != null) {
            Image(
                bitmap = bitmapImage,
                contentDescription = slot.label,
                modifier = Modifier
                    .size((sizeDp - 12).dp)
                    .clip(RoundedCornerShape(10.dp))
            )
        } else {
            Icon(
                imageVector = slot.fallbackIcon,
                contentDescription = slot.label,
                tint = slot.fallbackTint,
                modifier = Modifier.size((sizeDp * 0.54f).dp)
            )
        }
    }
}

@Composable
private fun WifiStatusChip(
    connected: Boolean,
    enabled: Boolean,
    compact: Boolean = false,
    onClick: () -> Unit
) {
    val colors = LocalCoolDriveColors.current
    val tint by animateColorAsState(
        targetValue = when {
            connected -> colors.statusConnected
            enabled -> colors.secondaryText
            else -> colors.statusDisconnected
        },
        animationSpec = tween(300),
        label = "wifiTint"
    )
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(if (compact) 29.dp else 36.dp)
            .clip(CircleShape)
            .background(colors.dockIconContainer.copy(alpha = 0.85f))
            .bouncyClickable(pressedScale = 0.86f, onClick = onClick)
    ) {
        Icon(
            imageVector = if (connected) Icons.Rounded.Wifi else Icons.Rounded.WifiOff,
            contentDescription = "Wi-Fi 設定",
            tint = tint,
            modifier = Modifier.size(if (compact) 16.dp else 20.dp)
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BluetoothStatusChip(
    connected: Boolean,
    enabled: Boolean,
    compact: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val colors = LocalCoolDriveColors.current
    // Explicit unmistakable Bluetooth Blue when paired phone is connected!
    val btBlue = if (colors.isNight) Color(0xFF3B82F6) else Color(0xFF1D4ED8)
    val tint by animateColorAsState(
        targetValue = when {
            connected -> btBlue
            enabled -> colors.primaryText
            else -> colors.statusDisconnected
        },
        animationSpec = tween(300),
        label = "btTint"
    )
    val bgColor by animateColorAsState(
        targetValue = if (connected) {
            Color(0xFF2563EB).copy(alpha = if (colors.isNight) 0.24f else 0.16f)
        } else {
            colors.dockIconContainer.copy(alpha = 0.85f)
        },
        animationSpec = tween(300),
        label = "btBg"
    )
    val icon = when {
        connected -> Icons.Rounded.BluetoothConnected
        enabled -> Icons.Rounded.Bluetooth
        else -> Icons.Rounded.BluetoothDisabled
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(if (compact) 29.dp else 36.dp)
            .clip(CircleShape)
            .background(bgColor)
            .then(
                if (connected) Modifier.border(1.2.dp, btBlue.copy(alpha = 0.75f), CircleShape)
                else Modifier
            )
            .bouncyCombinedClickable(
                pressedScale = 0.86f,
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {
        Icon(
            imageVector = icon,
            contentDescription = if (connected) "藍牙已連線手機（點擊進入設定）" else "藍牙設定",
            tint = tint,
            modifier = Modifier.size(if (compact) 17.dp else 20.dp)
        )
    }
}

@Composable
private fun ClockChip(
    timeText: String,
    compact: Boolean,
    onClick: () -> Unit
) {
    val colors = LocalCoolDriveColors.current
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .bouncyClickable(pressedScale = 0.90f, onClick = onClick)
            .padding(horizontal = if (compact) 2.dp else 8.dp, vertical = 4.dp)
    ) {
        Text(
            text = timeText,
            color = colors.primaryText,
            fontSize = if (compact) 16.sp else 18.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun NotificationBellButton(
    unreadCount: Int,
    onClick: () -> Unit
) {
    val colors = LocalCoolDriveColors.current
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(colors.dockIconContainer)
            .bouncyClickable(pressedScale = 0.88f, onClick = onClick)
    ) {
        Icon(
            imageVector = Icons.Rounded.Notifications,
            contentDescription = "訊息通知中心",
            tint = if (unreadCount > 0) colors.accent else colors.secondaryText,
            modifier = Modifier.size(20.dp)
        )
        if (unreadCount > 0) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFEF4444))
            ) {
                Text(
                    text = if (unreadCount > 9) "9+" else "$unreadCount",
                    color = Color.White,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun SmallDockUtilityIcon(
    icon: ImageVector,
    contentDescription: String,
    tint: Color,
    onClick: () -> Unit
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(28.dp)
            .clip(CircleShape)
            .bouncyClickable(pressedScale = 0.86f, onClick = onClick)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(18.dp)
        )
    }
}

fun buildRecentDockSlots(
    recentPackages: List<String>,
    fallbackPackages: List<String>,
    installedApps: List<InstalledAppInfo>,
    activePackage: String
): List<DockAppSlot> {
    val byPkg = installedApps.associateBy { it.packageName }
    val orderedCandidates = (recentPackages + fallbackPackages).distinct()
    val slots = mutableListOf<DockAppSlot>()

    for (pkg in orderedCandidates) {
        if (slots.size >= 5) break
        val app = byPkg[pkg]
        if (app != null) {
            slots += DockAppSlot(
                id = pkg,
                label = app.label,
                packageName = pkg,
                fallbackIcon = iconForPackage(pkg),
                fallbackTint = tintForPackage(pkg),
                installedApp = app,
                isActive = activePackage == pkg
            )
        }
    }

    // Fallback slots if device has very few installed apps (e.g., emulator)
    if (slots.isEmpty()) {
        slots += DockAppSlot(
            id = "maps",
            label = "Google 地圖",
            packageName = "com.google.android.apps.maps",
            fallbackIcon = Icons.Rounded.Map,
            fallbackTint = Color(0xFF34A853),
            installedApp = byPkg["com.google.android.apps.maps"],
            isActive = activePackage == "com.google.android.apps.maps"
        )
        slots += DockAppSlot(
            id = "youtube",
            label = "YouTube",
            packageName = "com.google.android.youtube",
            fallbackIcon = Icons.Rounded.PlayArrow,
            fallbackTint = Color(0xFFFF0000),
            installedApp = byPkg["com.google.android.youtube"],
            isActive = activePackage == "com.google.android.youtube"
        )
    }

    // Fill up to 4 slots with installed apps if still short
    if (slots.size < 4) {
        val used = slots.mapNotNull { it.packageName }.toSet()
        for (app in installedApps) {
            if (slots.size >= 4) break
            if (app.packageName in used) continue
            slots += DockAppSlot(
                id = app.packageName,
                label = app.label,
                packageName = app.packageName,
                fallbackIcon = iconForPackage(app.packageName),
                fallbackTint = tintForPackage(app.packageName),
                installedApp = app,
                isActive = activePackage == app.packageName
            )
        }
    }

    return slots
}

private fun iconForPackage(pkg: String): ImageVector = when {
    pkg.contains("maps") || pkg.contains("waze") || pkg.contains("navi") -> Icons.Rounded.Map
    pkg.contains("youtube") && !pkg.contains("music") -> Icons.Rounded.PlayArrow
    pkg.contains("dialer") || pkg.contains("phone") -> Icons.Rounded.Phone
    else -> Icons.Rounded.MusicNote
}

private fun tintForPackage(pkg: String): Color = when {
    pkg.contains("maps") -> Color(0xFF34A853)
    pkg.contains("youtube") -> Color(0xFFFF0000)
    pkg.contains("spotify") -> Color(0xFF22C55E)
    pkg.contains("dialer") || pkg.contains("phone") -> Color(0xFF38BDF8)
    else -> Color(0xFF60A5FA)
}

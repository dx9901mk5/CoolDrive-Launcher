package com.dxcar.launcher.ui

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
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.BluetoothConnected
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.dxcar.launcher.comms.BluetoothCompanionService
import com.dxcar.launcher.comms.BoxNotificationListenerService
import com.dxcar.launcher.comms.BridgeConnectionStatus
import com.dxcar.launcher.comms.BridgePacket
import com.dxcar.launcher.comms.BridgeStatusState
import com.dxcar.launcher.comms.PacketType
import com.dxcar.launcher.data.DeviceFormFactor
import com.dxcar.launcher.data.LauncherConfig
import com.dxcar.launcher.system.PairedBtDevice
import com.dxcar.launcher.system.PermissionHealth
import com.dxcar.launcher.system.PermissionHelper
import com.dxcar.launcher.theme.LocalDxCarColors
import java.util.UUID

/**
 * Dedicated Smartphone Companion Screen:
 * - On phones / folded cover displays: focuses strictly on phone-relevant sync settings.
 * - On tablets / unfolded foldables (>= 580dp): allows switching back to Car Launcher mode.
 * - Streams notifications & calls via dual transport (Bluetooth SPP + Wi-Fi Hotspot TCP).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PhoneCompanionScreen(
    config: LauncherConfig,
    pairedDevices: List<PairedBtDevice>,
    bridgeStatus: BridgeStatusState,
    permissionHealth: PermissionHealth,
    onRefreshPairedDevices: () -> Unit,
    onSelectTargetDevice: (PairedBtDevice) -> Unit,
    onToggleAllowedPackage: (String) -> Unit,
    onToggleSyncAllApps: (Boolean) -> Unit,
    onRequestRuntimePermissions: () -> Unit,
    onReconnectBridge: () -> Unit,
    onSwitchToAiBoxMode: () -> Unit
) {
    val colors = LocalDxCarColors.current
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var testSentTip by remember { mutableStateOf("") }

    val allowModeSwitch = DeviceFormFactor.isTabletOrExpandedScreen(configuration)
    val listenerAlive = bridgeStatus.isListenerBound || BoxNotificationListenerService.instance != null

    // Automatically refresh permission health & rebind listener whenever user returns from System Settings!
    DisposableEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                PermissionHelper.requestNotificationListenerRebind(context)
                onRefreshPairedDevices()
            }
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }

    val knownApps = listOf(
        "jp.naver.line.android" to "LINE",
        "com.facebook.orca" to "Messenger",
        "com.whatsapp" to "WhatsApp",
        "org.telegram.messenger" to "Telegram",
        "com.google.android.apps.messaging" to "Google 簡訊",
        "com.samsung.android.messaging" to "三星簡訊",
        "com.android.mms" to "系統簡訊",
        "com.vivo.mms" to "vivo 簡訊",
        "com.tencent.mm" to "WeChat",
        "com.instagram.android" to "Instagram",
        "com.discord" to "Discord"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.canvasBackground)
            .verticalScroll(rememberScrollState())
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // HEADER (shows Role Switch ONLY on Tablet / Unfolded Foldable wide screen!)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                Text(
                    text = "DX-Car 手機通知同步端",
                    color = colors.primaryText,
                    fontSize = 21.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "藍牙連上車機時自動喚醒同步（平時待機省電不搜尋）＋ Wi-Fi 熱點雙備援",
                    color = colors.secondaryText,
                    fontSize = 12.sp
                )
            }
            if (allowModeSwitch) {
                OutlinedButton(
                    onClick = onSwitchToAiBoxMode,
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Rounded.DirectionsCar, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("切換車機模式")
                }
            }
        }

        // FIRST-TIME / ALWAYS-HANDY PAIRING & NOTIFICATION SETUP GUIDE
        var showGuideExpanded by remember(listenerAlive, bridgeStatus.status) {
            mutableStateOf(!listenerAlive || bridgeStatus.status != BridgeConnectionStatus.CONNECTED)
        }
        Surface(
            color = colors.accent.copy(alpha = 0.10f),
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, colors.accent.copy(alpha = 0.40f), RoundedCornerShape(18.dp))
                .clickable { showGuideExpanded = !showGuideExpanded }
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "📖 配對與抓通知設定步驟（點此${if (showGuideExpanded) "收合" else "展開"}）",
                        color = colors.primaryText,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                if (showGuideExpanded) {
                    Text(
                        text = "步驟 1｜開啟下方【通知讀取權限】（若顯示「受限制的設定」，點下方黃字前往應用程式資訊 → 右上角 ⋮ → 允許受限制的設定）、【藍牙權限】與【忽略電池最佳化】。",
                        color = colors.secondaryText,
                        fontSize = 12.sp
                    )
                    Text(
                        text = "步驟 2｜先於手機系統藍牙配對車機 AI Box，並在下方「2. 選擇配對的 AI Box」點選車機（或開手機 Wi-Fi 熱點給車機連網）。",
                        color = colors.secondaryText,
                        fontSize = 12.sp
                    )
                    Text(
                        text = "步驟 3｜開啟下方「3. 同步手機所有 App 通知（不限清單）」，即可抓取 LINE、簡訊與所有通知。",
                        color = colors.secondaryText,
                        fontSize = 12.sp
                    )
                    Text(
                        text = "步驟 4｜完成後手機平時會待機省電不搜尋藍牙，每次「連上車機藍牙」即自動觸發同步；上車前可點「推播測試通知」驗證。",
                        color = colors.accent,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // CONNECTION STATUS CARD (Dual Transport: Bluetooth + Wi-Fi Hotspot)
        val isConnected = bridgeStatus.status == BridgeConnectionStatus.CONNECTED
        Surface(
            color = colors.cardSurface,
            shape = RoundedCornerShape(22.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    1.5.dp,
                    if (isConnected) colors.statusConnected else colors.cardBorder,
                    RoundedCornerShape(22.dp)
                )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background((if (isConnected) colors.statusConnected else colors.accent).copy(alpha = 0.18f))
                    ) {
                        Icon(
                            imageVector = when {
                                isConnected && bridgeStatus.transportLabel.contains("Wi-Fi") -> Icons.Rounded.Wifi
                                isConnected -> Icons.Rounded.BluetoothConnected
                                else -> Icons.Rounded.Bluetooth
                            },
                            contentDescription = null,
                            tint = if (isConnected) colors.statusConnected else colors.accent,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isConnected) {
                                "已連線同步中 (${bridgeStatus.transportLabel})"
                            } else {
                                bridgeStatus.status.labelZh
                            },
                            color = colors.primaryText,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = when {
                                isConnected && bridgeStatus.peerName.isNotBlank() ->
                                    "已連接車機：${bridgeStatus.peerName}"
                                config.companionTargetDeviceName.isNotBlank() ->
                                    "藍牙目標：${config.companionTargetDeviceName}（若車機已連手機熱點亦會自動直連）"
                                else ->
                                    "請開啟手機熱點讓車機連上網，或於下方選擇已配對的 AI Box 藍牙"
                            },
                            color = colors.secondaryText,
                            fontSize = 12.sp
                        )
                    }
                }

                // Notification Listener Binding Health Pill
                Surface(
                    color = if (listenerAlive) colors.statusConnected.copy(alpha = 0.14f) else Color(0xFFF59E0B).copy(alpha = 0.15f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.NotificationsActive,
                            contentDescription = null,
                            tint = if (listenerAlive) colors.statusConnected else Color(0xFFF59E0B),
                            modifier = Modifier.size(17.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = if (listenerAlive) {
                                "通知擷取服務已啟動運作中"
                            } else if (permissionHealth.hasNotificationListener) {
                                "權限已開但系統尚未喚醒服務，請切換一次通知權限開關或重連"
                            } else {
                                "尚未開啟通知讀取權限，手機無法擷取 LINE／簡訊"
                            },
                            color = colors.primaryText,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onReconnectBridge,
                        colors = ButtonDefaults.buttonColors(containerColor = colors.accent, contentColor = colors.onAccent),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("立即搜尋重連")
                    }
                    OutlinedButton(
                        onClick = {
                            val ok = BluetoothCompanionService.sendPacketFromPhone(
                                BridgePacket(
                                    type = PacketType.MESSAGE,
                                    id = UUID.randomUUID().toString(),
                                    appPackage = "jp.naver.line.android",
                                    appName = "LINE",
                                    sender = "手機同步測試",
                                    text = "DX-Car Launcher 通知橋接測試成功！",
                                    canReply = false
                                )
                            )
                            testSentTip = if (ok) {
                                "✓ 已送出測試通知至車機（請查看車機上方彈窗或通知鈴鐺）"
                            } else {
                                "✕ 尚未連上車機：請確認車機已開啟本 App，且已透過藍牙配對或連上手機 Wi-Fi 熱點"
                            }
                        },
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("推播測試通知")
                    }
                }
                if (testSentTip.isNotBlank()) {
                    Text(
                        text = testSentTip,
                        color = if (testSentTip.startsWith("✓")) colors.statusConnected else Color(0xFFF59E0B),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // STEP 1: PHONE PERMISSIONS
        Surface(
            color = colors.cardSurface,
            shape = RoundedCornerShape(22.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, colors.cardBorder, RoundedCornerShape(22.dp))
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("1. 手機端必要權限", color = colors.primaryText, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                CompanionPermButton(
                    label = "通知讀取權限（攔截 LINE / Messenger / 簡訊並回傳回覆）",
                    ok = permissionHealth.hasNotificationListener,
                    onClick = { PermissionHelper.openNotificationListenerSettings(context) }
                )
                if (!permissionHealth.hasNotificationListener) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(colors.accent.copy(alpha = 0.12f))
                            .clickable { PermissionHelper.openAppDetailsSettings(context) }
                            .padding(horizontal = 12.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.Security, contentDescription = null, tint = colors.accent, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "若通知讀取顯示「受限制的設定」無法開啟：點此開啟應用程式資訊 → 右上角 ⋮ → 允許受限制的設定",
                            color = colors.primaryText,
                            fontSize = 11.sp,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                CompanionPermButton(
                    label = "藍牙、來電狀態與前景通知權限",
                    ok = permissionHealth.hasBluetooth && permissionHealth.hasPhoneState && permissionHealth.hasPostNotifications,
                    onClick = onRequestRuntimePermissions
                )
                CompanionPermButton(
                    label = "忽略電池最佳化（鎖屏後保持連線同步）",
                    ok = permissionHealth.hasIgnoreBatteryOpt,
                    onClick = { PermissionHelper.requestIgnoreBatteryOptimizations(context) }
                )
            }
        }

        // STEP 2: SELECT PAIRED AI BOX
        Surface(
            color = colors.cardSurface,
            shape = RoundedCornerShape(22.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, colors.cardBorder, RoundedCornerShape(22.dp))
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("2. 選擇配對的 AI Box（Wi-Fi 熱點連上亦自動通）", color = colors.primaryText, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    OutlinedButton(onClick = onRefreshPairedDevices, shape = RoundedCornerShape(12.dp)) {
                        Text("重新整理")
                    }
                }
                if (pairedDevices.isEmpty()) {
                    Text(
                        text = "未偵測到已配對藍牙裝置；您也可以直接開啟手機熱點讓車機連上 Wi-Fi，系統將自動以區域網路直連！",
                        color = colors.secondaryText,
                        fontSize = 12.sp
                    )
                } else {
                    pairedDevices.forEach { dev ->
                        val selected = dev.address == config.companionTargetDeviceAddress
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(if (selected) colors.accent.copy(alpha = 0.2f) else colors.cardSurfaceElevated)
                                .border(
                                    1.dp,
                                    if (selected) colors.accent else colors.cardBorder,
                                    RoundedCornerShape(14.dp)
                                )
                                .clickable { onSelectTargetDevice(dev) }
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(dev.name, color = colors.primaryText, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                                Text(
                                    text = "${dev.address}${if (dev.isConnected) " · 藍牙已連線" else ""}",
                                    color = colors.secondaryText,
                                    fontSize = 12.sp
                                )
                            }
                            if (selected) {
                                Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = colors.accent)
                            }
                        }
                    }
                }
            }
        }

        // STEP 3: ALLOWED APPS FILTER + SYNC ALL APPS TOGGLE
        Surface(
            color = colors.cardSurface,
            shape = RoundedCornerShape(22.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, colors.cardBorder, RoundedCornerShape(22.dp))
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("3. 選擇要同步到車機的通知", color = colors.primaryText, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(colors.cardSurfaceElevated)
                        .clickable { onToggleSyncAllApps(!config.companionSyncAllApps) }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                        Text("同步手機所有 App 通知（不限清單）", color = colors.primaryText, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Text("推薦開啟，可涵蓋雙開 LINE、各品牌原生簡訊與所有通知", color = colors.secondaryText, fontSize = 11.sp)
                    }
                    Switch(
                        checked = config.companionSyncAllApps,
                        onCheckedChange = onToggleSyncAllApps,
                        colors = SwitchDefaults.colors(checkedThumbColor = colors.onAccent, checkedTrackColor = colors.accent)
                    )
                }

                if (!config.companionSyncAllApps) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        knownApps.forEach { (pkg, label) ->
                            val enabled = config.companionAllowedPackages.contains(pkg)
                            Surface(
                                color = if (enabled) colors.accent else colors.cardSurfaceElevated,
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier
                                    .border(1.dp, if (enabled) colors.accent else colors.cardBorder, RoundedCornerShape(16.dp))
                                    .clickable { onToggleAllowedPackage(pkg) }
                            ) {
                                Text(
                                    text = if (enabled) "✓ $label" else label,
                                    color = if (enabled) colors.onAccent else colors.primaryText,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // LIVE SYNC LOG
        if (bridgeStatus.recentLogs.isNotEmpty()) {
            Surface(
                color = colors.cardSurface,
                shape = RoundedCornerShape(22.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, colors.cardBorder, RoundedCornerShape(22.dp))
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("即時同步與診斷紀錄", color = colors.primaryText, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    bridgeStatus.recentLogs.take(10).forEach { line ->
                        Text(line, color = colors.secondaryText, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun CompanionPermButton(
    label: String,
    ok: Boolean,
    onClick: () -> Unit
) {
    val colors = LocalDxCarColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(colors.cardSurfaceElevated)
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = colors.primaryText, fontSize = 13.sp, modifier = Modifier.weight(1f).padding(end = 8.dp))
        Text(
            text = if (ok) "已授權" else "開啟",
            color = if (ok) colors.statusConnected else colors.accent,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

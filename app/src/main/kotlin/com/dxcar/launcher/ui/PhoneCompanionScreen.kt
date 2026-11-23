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
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dxcar.launcher.comms.BluetoothCompanionService
import com.dxcar.launcher.comms.BridgeConnectionStatus
import com.dxcar.launcher.comms.BridgePacket
import com.dxcar.launcher.comms.BridgeStatusState
import com.dxcar.launcher.comms.PacketType
import com.dxcar.launcher.data.LauncherConfig
import com.dxcar.launcher.system.PairedBtDevice
import com.dxcar.launcher.system.PermissionHealth
import com.dxcar.launcher.system.PermissionHelper
import com.dxcar.launcher.theme.LocalDxCarColors
import java.util.UUID

/**
 * Dedicated Smartphone Companion Mode (same APK installed on the user's phone):
 * - Select your already-paired AI Box from Bluetooth bonded devices
 * - Streams LINE / Messenger / WhatsApp / Telegram / SMS / Calls over Bluetooth SPP
 * - Executes RemoteInput Quick Replies & Call Answer/Decline sent from the car screen!
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
    onRequestRuntimePermissions: () -> Unit,
    onReconnectBridge: () -> Unit,
    onSwitchToAiBoxMode: () -> Unit
) {
    val colors = LocalDxCarColors.current
    val context = LocalContext.current
    var testSentTip by remember { mutableStateOf("") }

    val knownApps = listOf(
        "jp.naver.line.android" to "LINE",
        "com.facebook.orca" to "Messenger",
        "com.whatsapp" to "WhatsApp",
        "org.telegram.messenger" to "Telegram",
        "com.google.android.apps.messaging" to "Google 簡訊",
        "com.samsung.android.messaging" to "三星簡訊",
        "com.tencent.mm" to "WeChat",
        "com.instagram.android" to "Instagram",
        "com.discord" to "Discord"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.canvasBackground)
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // HEADER
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "DX-Car Launcher 手機同步端",
                    color = colors.primaryText,
                    fontSize = 23.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "將手機來電、LINE、Messenger、簡訊透過藍牙同步至 AI Box 車機",
                    color = colors.secondaryText,
                    fontSize = 13.sp
                )
            }
            OutlinedButton(
                onClick = onSwitchToAiBoxMode,
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Rounded.DirectionsCar, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("切回車機模式")
            }
        }

        // CONNECTION STATUS CARD
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
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background((if (isConnected) colors.statusConnected else colors.accent).copy(alpha = 0.18f))
                    ) {
                        Icon(
                            imageVector = if (isConnected) Icons.Rounded.BluetoothConnected else Icons.Rounded.Bluetooth,
                            contentDescription = null,
                            tint = if (isConnected) colors.statusConnected else colors.accent,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = bridgeStatus.status.labelZh,
                            color = colors.primaryText,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (config.companionTargetDeviceName.isNotBlank()) {
                                "目標 AI Box：${config.companionTargetDeviceName} (${config.companionTargetDeviceAddress})"
                            } else {
                                "請在下方選擇已與手機配對的 AI Box 藍牙裝置"
                            },
                            color = colors.secondaryText,
                            fontSize = 12.sp
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = onReconnectBridge,
                        colors = ButtonDefaults.buttonColors(containerColor = colors.accent, contentColor = colors.onAccent),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("立即連線 / 重連")
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
                                    text = "DX-Car Launcher 藍牙推播連線成功！",
                                    canReply = false
                                )
                            )
                            testSentTip = if (ok) "已送出測試訊息至 AI Box！" else "尚未連線至 AI Box"
                        },
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("推播測試訊息")
                    }
                }
                if (testSentTip.isNotBlank()) {
                    Text(testSentTip, color = colors.accent, fontSize = 12.sp)
                }
            }
        }

        // STEP 1: PERMISSIONS
        Surface(
            color = colors.cardSurface,
            shape = RoundedCornerShape(22.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, colors.cardBorder, RoundedCornerShape(22.dp))
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("1. 手機端必要權限", color = colors.primaryText, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                CompanionPermButton(
                    label = "通知讀取權限（攔截 LINE / Messenger / 簡訊並支援快速回覆）",
                    ok = permissionHealth.hasNotificationListener,
                    onClick = { PermissionHelper.openNotificationListenerSettings(context) }
                )
                CompanionPermButton(
                    label = "藍牙、來電狀態與前景通知權限",
                    ok = permissionHealth.hasBluetooth && permissionHealth.hasPhoneState,
                    onClick = onRequestRuntimePermissions
                )
                CompanionPermButton(
                    label = "忽略電池最佳化（上車自動背景連上 AI Box）",
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
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("2. 選擇已配對的 AI Box 藍牙裝置", color = colors.primaryText, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    OutlinedButton(onClick = onRefreshPairedDevices, shape = RoundedCornerShape(12.dp)) {
                        Text("重新整理")
                    }
                }
                if (pairedDevices.isEmpty()) {
                    Text(
                        text = "未偵測到已配對藍牙裝置，請先至系統藍牙與 AI Box 完成配對並授予藍牙權限。",
                        color = colors.secondaryText,
                        fontSize = 13.sp
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
                            Column {
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

        // STEP 3: ALLOWED APPS FILTER
        Surface(
            color = colors.cardSurface,
            shape = RoundedCornerShape(22.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, colors.cardBorder, RoundedCornerShape(22.dp))
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("3. 選擇要同步到車機的通訊 App", color = colors.primaryText, fontSize = 16.sp, fontWeight = FontWeight.Bold)
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
                    Text("即時同步紀錄", color = colors.primaryText, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    bridgeStatus.recentLogs.take(8).forEach { line ->
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

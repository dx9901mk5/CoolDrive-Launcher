package com.dxcar.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.BluetoothConnected
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Mouse
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.dxcar.launcher.BuildConfig
import com.dxcar.launcher.comms.BluetoothCompanionService
import com.dxcar.launcher.comms.BoxNotificationListenerService
import com.dxcar.launcher.comms.BridgeConnectionStatus
import com.dxcar.launcher.comms.BridgePacket
import com.dxcar.launcher.comms.BridgeStatusState
import com.dxcar.launcher.comms.PacketType
import com.dxcar.launcher.comms.TouchpadAction
import com.dxcar.launcher.data.DeviceFormFactor
import com.dxcar.launcher.data.LauncherConfig
import com.dxcar.launcher.overlay.ExtraLargeTutorialModal
import com.dxcar.launcher.system.PairedBtDevice
import com.dxcar.launcher.system.PermissionHealth
import com.dxcar.launcher.system.PermissionHelper
import com.dxcar.launcher.theme.LocalDxCarColors
import com.dxcar.launcher.update.UpdateUiState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.UUID
import kotlin.math.hypot

/**
 * Categorized Smartphone Companion Screen:
 * - Top bar houses compact "🛡️ 權限設定 (X/3)" pill button -> opens Phone Permissions sheet!
 * - Prominent "📖 教學" button opens Extra-Large Font (26sp/22sp) setup & troubleshooting guide.
 * - Phone-Exclusive Feature ("限定給手機"): Live Car Mouse Touchpad + button that pops up the
 *   Pointer Speed / Mouse Sensitivity settings directly on the Car AI Box screen!
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PhoneCompanionScreen(
    config: LauncherConfig,
    pairedDevices: List<PairedBtDevice>,
    bridgeStatus: BridgeStatusState,
    permissionHealth: PermissionHealth,
    updateState: UpdateUiState,
    onCheckUpdate: () -> Unit,
    onDownloadOrInstallUpdate: () -> Unit,
    onRefreshPairedDevices: () -> Unit,
    onSelectTargetDevice: (PairedBtDevice) -> Unit,
    onToggleAllowedPackage: (String) -> Unit,
    onToggleSyncAllApps: (Boolean) -> Unit,
    onChangeTouchpadSensitivity: (Float) -> Unit = {},
    onRequestRuntimePermissions: () -> Unit,
    onReconnectBridge: () -> Unit,
    onSwitchToAiBoxMode: () -> Unit
) {
    val colors = LocalDxCarColors.current
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var testSentTip by remember { mutableStateOf("") }
    var touchpadTip by remember { mutableStateOf("") }
    var showPermissionsSheet by remember { mutableStateOf(false) }
    var showLargeTutorial by remember { mutableStateOf(false) }

    val allowModeSwitch = DeviceFormFactor.isTabletOrExpandedScreen(configuration)
    val isPhoneExclusive = DeviceFormFactor.isPhoneDevice(configuration)
    val listenerAlive = bridgeStatus.isListenerBound || BoxNotificationListenerService.instance != null
    val (grantedPerms, totalPerms) = remember(permissionHealth) { permissionHealth.summaryForPhone() }
    val allPermsOk = grantedPerms == totalPerms

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

    Box(modifier = Modifier.fillMaxSize().background(colors.canvasBackground)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // TOP HEADER + COMPACT PERMISSIONS PILL + [教學] BUTTON
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                        Text(
                            text = "DX-Car 手機同步與遙控端",
                            color = colors.primaryText,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "藍牙連上車機免持或開啟 Wi-Fi 熱點即自動連線（平時待機省電）",
                            color = colors.secondaryText,
                            fontSize = 12.sp
                        )
                    }
                    TutorialPillButton(
                        label = "教學",
                        onClick = { showLargeTutorial = true }
                    )
                }

                // Compact Top Bar: Permissions Pill Button + Optional Tablet/Foldable Mode Switch
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        color = if (allPermsOk) colors.statusConnected.copy(alpha = 0.16f) else Color(0xFFF59E0B).copy(alpha = 0.22f),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .border(
                                1.2.dp,
                                if (allPermsOk) colors.statusConnected else Color(0xFFF59E0B),
                                RoundedCornerShape(14.dp)
                            )
                            .clickable {
                                onRefreshPairedDevices()
                                showPermissionsSheet = true
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
                                text = if (allPermsOk) "權限已就緒 ($grantedPerms/$totalPerms)" else "權限需求 ($grantedPerms/$totalPerms · 點此開啟)",
                                color = colors.primaryText,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    if (allowModeSwitch) {
                        OutlinedButton(
                            onClick = onSwitchToAiBoxMode,
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(Icons.Rounded.DirectionsCar, contentDescription = null, modifier = Modifier.size(17.dp))
                            Spacer(Modifier.width(5.dp))
                            Text("切換車機模式", fontSize = 13.sp)
                        }
                    }
                }
            }

            // ==================== CATEGORY 1: 車機連線狀態與測試 ====================
            PhoneCategoryLabel(
                title = "分類一 · 車機雙通道連線狀態",
                hint = "連上車機藍牙或開啟手機熱點自動直連；無須手動輸入 IP"
            )

            val isConnected = bridgeStatus.status == BridgeConnectionStatus.CONNECTED
            Surface(
                color = colors.cardSurface,
                shape = RoundedCornerShape(22.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.5.dp, if (isConnected) colors.statusConnected else colors.cardBorder, RoundedCornerShape(22.dp))
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
                                        "藍牙目標：${config.companionTargetDeviceName}（連熱點亦自動雙向直連）"
                                    else ->
                                        "只要手機已連車機藍牙通話或開啟熱點，系統即自動偵測連入"
                                },
                                color = colors.secondaryText,
                                fontSize = 12.sp
                            )
                        }
                    }

                    Surface(
                        color = if (listenerAlive) colors.statusConnected.copy(alpha = 0.14f) else Color(0xFFF59E0B).copy(alpha = 0.15f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { if (!listenerAlive) showPermissionsSheet = true }
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
                                    "✓ 通知擷取服務已啟動運作中"
                                } else {
                                    "⚠️ 尚未啟用通知讀取服務，請點最上方「權限需求」開啟"
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
                            onClick = {
                                testSentTip = ""
                                onReconnectBridge()
                            },
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
                                        text = "DX-Car Launcher 雙通道通知測試成功！",
                                        canReply = false
                                    )
                                )
                                testSentTip = if (ok) {
                                    "✓ 已送出測試通知至車機！請看車機上方橫幅或鈴鐺"
                                } else {
                                    "⏳ 已排入傳送佇列並喚醒連線：請確認車機已安裝開啟 v1.5.0+ 且連上同一藍牙或熱點"
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
                            color = if (testSentTip.startsWith("✓")) colors.statusConnected else colors.accent,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            // ==================== CATEGORY 2: 手機限定 — 操控車機的滑鼠觸控板 ====================
            if (isPhoneExclusive) {
                PhoneCategoryLabel(
                    title = "分類二 · 車機遠端滑鼠觸控板（手機專屬）",
                    hint = "在下方觸控區滑動即可操控車機畫面滑鼠游標、輕點＝左鍵點擊；點「車機滑鼠靈敏度設定」會直接在車機跳出系統設定畫面"
                )

                PhoneRemoteCarTouchpadCard(
                    sensitivity = config.phoneTouchpadSensitivity,
                    touchpadTip = touchpadTip,
                    onSensitivityChange = onChangeTouchpadSensitivity,
                    onOpenCarPointerSpeedSettings = {
                        val ok = BluetoothCompanionService.sendPacketFromPhone(
                            BridgePacket(
                                type = PacketType.TOUCHPAD_EVENT,
                                id = UUID.randomUUID().toString(),
                                touchAction = TouchpadAction.OPEN_POINTER_SPEED_SETTINGS
                            )
                        )
                        touchpadTip = if (ok) {
                            "✓ 已傳送指令！車機螢幕已跳出「滑鼠／指標速度（靈敏度）」設定頁"
                        } else {
                            "⚠️ 尚未連上車機，請先確認上方顯示「已連線同步中」"
                        }
                    }
                )
            }

            // ==================== CATEGORY 3: 藍牙目標與通知過濾設定 ====================
            PhoneCategoryLabel(
                title = "分類三 · 配對車機選擇與通知過濾",
                hint = "可點選車機藍牙名稱加速指定配對，並選擇要推送至車機的 App"
            )

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
                        Text(
                            text = "選擇配對的車機 AI Box（熱點連上亦自動通）",
                            color = colors.primaryText,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedButton(onClick = onRefreshPairedDevices, shape = RoundedCornerShape(12.dp)) {
                            Text("重新整理", fontSize = 12.sp)
                        }
                    }
                    if (pairedDevices.isEmpty()) {
                        Text(
                            text = "未偵測到已配對藍牙裝置（請先確認已授予藍牙權限，或直接開啟手機 Wi-Fi 熱點給車機連網）。",
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
                                    .border(1.dp, if (selected) colors.accent else colors.cardBorder, RoundedCornerShape(14.dp))
                                    .clickable { onSelectTargetDevice(dev) }
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(dev.name, color = colors.primaryText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                    Text(
                                        text = "${dev.address}${if (dev.isConnected) " · 目前已連上藍牙" else ""}",
                                        color = if (dev.isConnected) colors.statusConnected else colors.secondaryText,
                                        fontSize = 11.sp
                                    )
                                }
                                if (selected) {
                                    Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = colors.accent)
                                }
                            }
                        }
                    }

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
                            Text("推薦開啟（已自動過濾系統與 Android Auto 常駐背景通知）", color = colors.secondaryText, fontSize = 11.sp)
                        }
                        Switch(
                            checked = config.companionSyncAllApps,
                            onCheckedChange = onToggleSyncAllApps,
                            colors = SwitchDefaults.colors(checkedThumbColor = colors.onAccent, checkedTrackColor = colors.accent)
                        )
                    }

                    if (!config.companionSyncAllApps) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ==================== CATEGORY 4: 診斷紀錄與版本更新 ====================
            PhoneCategoryLabel(
                title = "分類四 · 即時診斷紀錄與版本更新",
                hint = "手機與車機皆更新至同一最新版本可獲得最完整雙向通道支援"
            )

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
                        bridgeStatus.recentLogs.take(8).forEach { line ->
                            Text(line, color = colors.secondaryText, fontSize = 12.sp)
                        }
                    }
                }
            }

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
                    Text(
                        text = "App 版本與 GitHub 更新（目前 v${BuildConfig.VERSION_NAME}）",
                        color = colors.primaryText,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = updateState.statusMessage.ifBlank { "點擊檢查 GitHub Releases 最新版本" },
                        color = if (updateState.hasUpdate || updateState.downloadedReady) colors.accent else colors.secondaryText,
                        fontSize = 12.sp
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = onCheckUpdate,
                            enabled = !updateState.checking && !updateState.downloading,
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text(if (updateState.checking) "檢查中…" else "檢查更新")
                        }
                        if (updateState.hasUpdate || updateState.downloadedReady || updateState.downloading) {
                            Button(
                                onClick = onDownloadOrInstallUpdate,
                                enabled = !updateState.downloading,
                                colors = ButtonDefaults.buttonColors(containerColor = colors.accent, contentColor = colors.onAccent),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Text(
                                    when {
                                        updateState.downloadedReady -> "立即安裝 v${updateState.latestVersion}"
                                        updateState.downloading -> {
                                            if (updateState.totalBytes > 0L) "下載中 ${(updateState.progress * 100).toInt()}%"
                                            else "下載中…"
                                        }
                                        else -> "下載更新 v${updateState.latestVersion}"
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
            }
        }

        // DEDICATED PHONE PERMISSIONS SHEET (Opened via Top Compact Permissions Button!)
        if (showPermissionsSheet) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.76f))
                    .clickable { showPermissionsSheet = false },
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    color = colors.cardSurface,
                    shape = RoundedCornerShape(24.dp),
                    shadowElevation = 24.dp,
                    modifier = Modifier
                        .fillMaxWidth(0.92f)
                        .border(1.5.dp, colors.accent, RoundedCornerShape(24.dp))
                        .clickable(enabled = false) {}
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("手機端必要權限設定 ($grantedPerms/$totalPerms)", color = colors.primaryText, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            IconButton(onClick = { showPermissionsSheet = false }) {
                                Icon(Icons.Rounded.Close, contentDescription = "關閉", tint = colors.primaryText)
                            }
                        }

                        CompanionPermButton(
                            label = "1. 通知讀取權限（攔截 LINE / 簡訊並執行車機回覆）",
                            ok = permissionHealth.hasNotificationListener,
                            onClick = { PermissionHelper.openNotificationListenerSettings(context) }
                        )
                        if (!permissionHealth.hasNotificationListener) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(colors.accent.copy(alpha = 0.14f))
                                    .clickable { PermissionHelper.openAppDetailsSettings(context) }
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Rounded.Security, contentDescription = null, tint = colors.accent, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = "若顯示「受限制的設定」無法開啟：點此前往應用程式資訊 → 右上角 ⋮ → 允許受限制的設定",
                                    color = colors.primaryText,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                        CompanionPermButton(
                            label = "2. 藍牙、來電狀態與前景通知權限",
                            ok = permissionHealth.hasBluetooth && permissionHealth.hasPhoneState && permissionHealth.hasPostNotifications,
                            onClick = onRequestRuntimePermissions
                        )
                        CompanionPermButton(
                            label = "3. 忽略電池最佳化（鎖屏後保持連線不斷開）",
                            ok = permissionHealth.hasIgnoreBatteryOpt,
                            onClick = { PermissionHelper.requestIgnoreBatteryOptimizations(context) }
                        )
                    }
                }
            }
        }

        // EXTRA-LARGE FONT TUTORIAL MODAL ("教學說明也是做一個按鈕，內容文字要特大")
        if (showLargeTutorial) {
            ExtraLargeTutorialModal(
                title = "📖 手機與車機連線＆遠端滑鼠 · 教學",
                steps = listOf(
                    "【重要】請先將「車機盒」與「手機」都更新到最新同一版本 APK！",
                    "點擊手機最上方【權限需求】按鈕，開啟「通知讀取權限」（若 vivo／三星／Pixel 顯示受限制設定，先點黃色提示去應用程式資訊右上角 ⋮ 點「允許受限制的設定」）、藍牙與忽略電池最佳化。",
                    "車機端同樣點設定最上方【權限需求】，開啟「顯示在其他應用程式上層」、「通知存取權」與「無障礙輔助服務」。",
                    "連線方式超簡單：只要手機藍牙連上車機盒（能接電話的狀態），或手機開 Wi-Fi 熱點給車機連網，兩邊就會自動連上並顯示綠色「已連線同步中」！",
                    "連上後，在手機滑動「車機遠端滑鼠觸控板」即可操控車機游標；按「車機滑鼠靈敏度設定」，車機螢幕就會自動跳出系統滑鼠速度設定畫面！"
                ),
                highlightNote = "💡 省電設計：下車斷開藍牙與熱點後，手機會自動退回 0% 耗電待機模式；下次發動上車即自動喚醒連線！",
                onClose = { showLargeTutorial = false }
            )
        }
    }
}

@Composable
private fun PhoneCategoryLabel(title: String, hint: String) {
    val colors = LocalDxCarColors.current
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 2.dp, start = 2.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(title, color = colors.accent, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
        Text(hint, color = colors.secondaryText, fontSize = 11.sp)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PhoneRemoteCarTouchpadCard(
    sensitivity: Float,
    touchpadTip: String,
    onSensitivityChange: (Float) -> Unit,
    onOpenCarPointerSpeedSettings: () -> Unit
) {
    val colors = LocalDxCarColors.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    fun sendTouchAction(action: TouchpadAction, dx: Float = 0f, dy: Float = 0f) {
        BluetoothCompanionService.sendPacketFromPhone(
            BridgePacket(
                type = PacketType.TOUCHPAD_EVENT,
                id = if (action == TouchpadAction.MOVE) "" else UUID.randomUUID().toString(),
                touchAction = action,
                touchDx = dx,
                touchDy = dy
            )
        )
    }

    Surface(
        color = colors.cardSurface,
        shape = RoundedCornerShape(22.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.2.dp, colors.accent.copy(alpha = 0.6f), RoundedCornerShape(22.dp))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Mouse, contentDescription = null, tint = colors.accent, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("操控車機滑鼠觸控板", color = colors.primaryText, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }

            // Button on Phone that pops open the Car System Pointer Speed / Mouse Sensitivity Settings!
            Button(
                onClick = onOpenCarPointerSpeedSettings,
                colors = ButtonDefaults.buttonColors(containerColor = colors.accent, contentColor = colors.onAccent),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Rounded.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("開啟車機系統「滑鼠靈敏度」設定（車機跳出畫面）", fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }

            if (touchpadTip.isNotBlank()) {
                Text(touchpadTip, color = colors.accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }

            // Interactive Multi-Gesture Touchpad Surface
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(215.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(colors.cardSurfaceElevated)
                    .border(1.5.dp, colors.cardBorder, RoundedCornerShape(18.dp))
                    .pointerInput(sensitivity) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val downPos = down.position
                            val downTime = System.currentTimeMillis()
                            var lastPos = downPos
                            var movedAny = false
                            var longPressed = false
                            var lastSendMs = 0L
                            var accDx = 0f
                            var accDy = 0f

                            val longPressJob = scope.launch {
                                delay(580L)
                                if (!movedAny) {
                                    longPressed = true
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    sendTouchAction(TouchpadAction.LONG_CLICK)
                                }
                            }

                            while (true) {
                                val ev = awaitPointerEvent(PointerEventPass.Main)
                                val ch = ev.changes.firstOrNull { it.id == down.id } ?: ev.changes.firstOrNull()
                                if (ch == null) break

                                val cur = ch.position
                                if (!movedAny && hypot(cur.x - downPos.x, cur.y - downPos.y) > 10f) {
                                    movedAny = true
                                    longPressJob.cancel()
                                }

                                if (movedAny) {
                                    accDx += (cur.x - lastPos.x) * sensitivity * 1.45f
                                    accDy += (cur.y - lastPos.y) * sensitivity * 1.45f
                                    val now = System.currentTimeMillis()
                                    if (now - lastSendMs >= 20L && (kotlin.math.abs(accDx) >= 1f || kotlin.math.abs(accDy) >= 1f)) {
                                        sendTouchAction(TouchpadAction.MOVE, accDx, accDy)
                                        accDx = 0f
                                        accDy = 0f
                                        lastSendMs = now
                                    }
                                }
                                lastPos = cur

                                if (ch.changedToUpIgnoreConsumed()) {
                                    ch.consume()
                                    break
                                }
                                ch.consume()
                            }

                            longPressJob.cancel()
                            if (movedAny && (kotlin.math.abs(accDx) >= 0.5f || kotlin.math.abs(accDy) >= 0.5f)) {
                                sendTouchAction(TouchpadAction.MOVE, accDx, accDy)
                            } else if (!movedAny && !longPressed && (System.currentTimeMillis() - downTime) < 550L) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                sendTouchAction(TouchpadAction.CLICK)
                            }
                        }
                    }
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(
                        imageVector = Icons.Rounded.TouchApp,
                        contentDescription = null,
                        tint = colors.accent.copy(alpha = 0.65f),
                        modifier = Modifier.size(40.dp)
                    )
                    Text(
                        text = "在此滑動操控車機游標",
                        color = colors.primaryText.copy(alpha = 0.85f),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "輕點＝左鍵點擊 · 長按＝車機長按",
                        color = colors.secondaryText,
                        fontSize = 12.sp
                    )
                }
            }

            // Touchpad Hardware-Style Buttons (Left Click / Scroll Up / Scroll Down / Back / Home)
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        sendTouchAction(TouchpadAction.CLICK)
                    },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("左鍵點擊", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                OutlinedButton(
                    onClick = { sendTouchAction(TouchpadAction.SCROLL_UP) },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Rounded.KeyboardArrowUp, contentDescription = null, modifier = Modifier.size(16.dp))
                    Text("上捲", fontSize = 12.sp)
                }
                OutlinedButton(
                    onClick = { sendTouchAction(TouchpadAction.SCROLL_DOWN) },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = null, modifier = Modifier.size(16.dp))
                    Text("下捲", fontSize = 12.sp)
                }
                OutlinedButton(
                    onClick = { sendTouchAction(TouchpadAction.BACK) },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(3.dp))
                    Text("車機返回", fontSize = 12.sp)
                }
                OutlinedButton(
                    onClick = { sendTouchAction(TouchpadAction.HOME) },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Rounded.Home, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(3.dp))
                    Text("車機首頁", fontSize = 12.sp)
                }
            }

            // Local Phone Touchpad Speed Multiplier Slider
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "觸控板移速：${String.format(Locale.US, "%.1fx", sensitivity)}",
                    color = colors.primaryText,
                    fontSize = 12.sp,
                    modifier = Modifier.width(105.dp)
                )
                Slider(
                    value = sensitivity,
                    onValueChange = { onSensitivityChange(it.coerceIn(0.6f, 3.0f)) },
                    valueRange = 0.6f..3.0f,
                    colors = SliderDefaults.colors(thumbColor = colors.accent, activeTrackColor = colors.accent),
                    modifier = Modifier.weight(1f)
                )
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
            text = if (ok) "已授權" else "前往開啟",
            color = if (ok) colors.statusConnected else colors.accent,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

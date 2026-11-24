package com.dxcar.launcher.ui

import android.app.Activity
import android.content.Context
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Add
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
import androidx.compose.material.icons.rounded.OpenInFull
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
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
import com.dxcar.launcher.data.AppLanguage
import com.dxcar.launcher.data.DeviceFormFactor
import com.dxcar.launcher.data.LauncherConfig
import com.dxcar.launcher.i18n.DxCarI18n
import com.dxcar.launcher.i18n.DxCarI18n.str
import com.dxcar.launcher.i18n.LocalAppLanguage
import com.dxcar.launcher.overlay.ExtraLargeTutorialModal
import com.dxcar.launcher.system.InstalledAppInfo
import com.dxcar.launcher.system.PairedBtDevice
import com.dxcar.launcher.system.PermissionHealth
import com.dxcar.launcher.system.PermissionHelper
import com.dxcar.launcher.theme.LocalDxCarColors
import com.dxcar.launcher.update.UpdateUiState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.UUID
import kotlin.math.abs
import kotlin.math.hypot

/**
 * Fires a crisp, unmistakable hardware motor click vibration on the smartphone
 * (uses VibratorManager + USAGE_HARDWARE_FEEDBACK so it works reliably on vivo / Samsung / Pixel / Xiaomi
 * even when system touch-sound haptics are muted!).
 */
private fun triggerPhoneConfirmVibration(context: Context, view: View, heavy: Boolean = false) {
    runCatching {
        @Suppress("DEPRECATION")
        view.performHapticFeedback(
            if (heavy) HapticFeedbackConstants.LONG_PRESS else HapticFeedbackConstants.KEYBOARD_TAP,
            HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING
        )
    }
    runCatching {
        val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vm?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
        if (vibrator?.hasVibrator() == true) {
            val effect = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                VibrationEffect.createPredefined(
                    if (heavy) VibrationEffect.EFFECT_HEAVY_CLICK else VibrationEffect.EFFECT_CLICK
                )
            } else {
                VibrationEffect.createOneShot(if (heavy) 48L else 28L, if (heavy) 230 else 190)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val attrs = VibrationAttributes.createForUsage(VibrationAttributes.USAGE_HARDWARE_FEEDBACK)
                vibrator.vibrate(effect, attrs)
            } else {
                vibrator.vibrate(effect)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PhoneCompanionScreen(
    config: LauncherConfig,
    pairedDevices: List<PairedBtDevice>,
    installedApps: List<InstalledAppInfo> = emptyList(),
    bridgeStatus: BridgeStatusState,
    permissionHealth: PermissionHealth,
    updateState: UpdateUiState,
    onCheckUpdate: () -> Unit,
    onDownloadOrInstallUpdate: () -> Unit,
    onRefreshPairedDevices: () -> Unit,
    onSelectTargetDevice: (PairedBtDevice) -> Unit,
    onToggleAllowedPackage: (String) -> Unit,
    onSetAllowedPackages: (List<String>) -> Unit = {},
    onToggleSyncAllApps: (Boolean) -> Unit,
    onChangeTouchpadSensitivity: (Float) -> Unit = {},
    onSelectLanguage: (AppLanguage) -> Unit = {},
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
    var showFullscreenTouchpad by remember { mutableStateOf(false) }
    var showInstalledAppPicker by remember { mutableStateOf(false) }
    var appSearchQuery by remember { mutableStateOf("") }

    val allowModeSwitch = DeviceFormFactor.isTabletOrExpandedScreen(configuration)
    val isPhoneExclusive = DeviceFormFactor.isPhoneDevice(configuration)
    val listenerAlive = bridgeStatus.isListenerBound || BoxNotificationListenerService.instance != null
    val (grantedPerms, totalPerms) = remember(permissionHealth) { permissionHealth.summaryForPhone() }
    val allPermsOk = grantedPerms == totalPerms
    val isConnected = bridgeStatus.status == BridgeConnectionStatus.CONNECTED

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

    fun requestCarPointerSpeedSettings() {
        val ok = BluetoothCompanionService.sendPacketFromPhone(
            BridgePacket(
                type = PacketType.TOUCHPAD_EVENT,
                id = UUID.randomUUID().toString(),
                touchAction = TouchpadAction.OPEN_POINTER_SPEED_SETTINGS
            )
        )
        touchpadTip = if (ok) {
            "✓ 已送出指令！車機螢幕已跳出「滑鼠／指標速度（靈敏度）」設定頁"
        } else {
            "⚠️ 尚未連上車機，請確認上方顯示「已連線同步中」"
        }
    }

    val knownAppGroups: List<Pair<List<String>, String>> = listOf(
        listOf("jp.naver.line.android", "com.linecorp.linelite") to "LINE",
        listOf("com.facebook.orca") to "Messenger",
        listOf("com.whatsapp", "com.whatsapp.w4b") to "WhatsApp",
        listOf("org.telegram.messenger") to "Telegram",
        listOf("com.google.android.apps.messaging") to "Google 簡訊",
        listOf("com.samsung.android.messaging") to "三星簡訊",
        listOf("com.android.mms", "com.coloros.mms", "com.oplus.mms", "com.miui.mms") to "系統簡訊",
        listOf("com.vivo.mms", "com.sie.mms", "com.bbk.mms", "com.iqoo.mms") to "vivo 簡訊",
        listOf("com.tencent.mm") to "WeChat",
        listOf("com.instagram.android") to "Instagram",
        listOf("com.instagram.barcelona") to "Threads",
        listOf("com.discord") to "Discord",
        listOf("com.google.android.gm") to "Gmail"
    )

    fun togglePackageGroup(pkgs: List<String>) {
        if (config.companionSyncAllApps) {
            onToggleSyncAllApps(false)
        }
        val anyEnabled = pkgs.any { config.companionAllowedPackages.contains(it) }
        val next = if (anyEnabled) {
            config.companionAllowedPackages - pkgs.toSet()
        } else {
            (config.companionAllowedPackages + pkgs).distinct()
        }
        onSetAllowedPackages(next)
    }

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
                            text = str("DX-Car 手機同步與遙控端", "DX-Car 手机同步与遥控端", "DX-Car Phone Companion", "DX-Car スマホ連携・リモコン"),
                            color = colors.primaryText,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = str(
                                "藍牙連上車機免持或開啟 Wi-Fi 熱點即自動連線（平時待機省電）",
                                "蓝牙连上车机免提或开启 Wi-Fi 热点即自动连接（平时待机省电）",
                                "Auto-connects via Car Bluetooth or Wi-Fi Hotspot (Standby when idle)",
                                "車載機 Bluetooth またはテザリング接続で自動同期（待機時は省電力）"
                            ),
                            color = colors.secondaryText,
                            fontSize = 12.sp
                        )
                    }
                    TutorialPillButton(
                        label = str("教學", "Guide", "ガイド"),
                        onClick = { showLargeTutorial = true }
                    )
                }

                // Compact 4-Language Selector Strip for Smartphone
                val currentUiLang = LocalAppLanguage.current
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    AppLanguage.entries.forEach { langOpt ->
                        SelectablePill(
                            text = DxCarI18n.appLanguageLabel(langOpt, currentUiLang),
                            selected = config.appLanguage == langOpt,
                            onClick = { onSelectLanguage(langOpt) }
                        )
                    }
                }

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
                                text = if (allPermsOk) {
                                    str("權限已就緒 ($grantedPerms/$totalPerms)", "Permissions OK ($grantedPerms/$totalPerms)", "権限 OK ($grantedPerms/$totalPerms)")
                                } else {
                                    str("權限需求 ($grantedPerms/$totalPerms · 點此開啟)", "Permissions ($grantedPerms/$totalPerms · Tap)", "権限設定 ($grantedPerms/$totalPerms · 開く)")
                                },
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
                            Text(str("切換車機模式", "Switch to Car Mode", "車載モードへ切替"), fontSize = 13.sp)
                        }
                    }
                }
            }

            // ==================== CATEGORY 1: 車機連線狀態與測試 ====================
            PhoneCategoryLabel(
                title = "分類一 · 車機雙通道連線狀態",
                hint = "連上車機藍牙或開啟手機熱點自動直連；無須手動輸入 IP"
            )

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
                                    "⏳ 已排入傳送佇列並喚醒連線：請確認車機已安裝開啟新版且連上同一藍牙或熱點"
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

            // ==================== CATEGORY 2: 手機限定 — 全螢幕純黑 OLED 滑鼠觸控板啟動器 ====================
            if (isPhoneExclusive) {
                PhoneCategoryLabel(
                    title = "分類二 · 車機遠端滑鼠觸控板（手機專屬）",
                    hint = "點擊下方執行按鈕展開「全螢幕純黑 OLED 防鎖屏觸控板」（支援點擊清脆震動回饋與車機滑鼠靈敏度設定）"
                )

                PhoneTouchpadLauncherCard(
                    sensitivity = config.phoneTouchpadSensitivity,
                    touchpadTip = touchpadTip,
                    onLaunchFullscreenTouchpad = { showFullscreenTouchpad = true },
                    onSensitivityChange = onChangeTouchpadSensitivity,
                    onOpenCarPointerSpeedSettings = ::requestCarPointerSpeedSettings
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

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(colors.cardSurfaceElevated)
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                Text(
                                    text = "選擇要攔截推送到車機的 App（預設已幫您打勾 ✓）",
                                    color = colors.primaryText,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "點選下方任一項目即可勾選／取消；亦可從手機已安裝 App 挑選更多應用程式",
                                    color = colors.secondaryText,
                                    fontSize = 11.sp
                                )
                            }
                        }

                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            knownAppGroups.forEach { (pkgs, label) ->
                                val enabled = config.companionSyncAllApps || pkgs.any { config.companionAllowedPackages.contains(it) }
                                Surface(
                                    color = if (enabled) colors.accent else colors.cardSurface,
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier
                                        .border(1.dp, if (enabled) colors.accent else colors.cardBorder, RoundedCornerShape(16.dp))
                                        .clickable { togglePackageGroup(pkgs) }
                                ) {
                                    Text(
                                        text = if (enabled) "✓ $label" else "☐ $label",
                                        color = if (enabled) colors.onAccent else colors.primaryText,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                                    )
                                }
                            }
                        }

                        // Custom extra apps selected from installed apps beyond knownAppGroups
                        val knownFlat = remember { knownAppGroups.flattenPairs() }
                        val extraSelectedApps = remember(installedApps, config.companionAllowedPackages) {
                            installedApps.filter {
                                config.companionAllowedPackages.contains(it.packageName) && !knownFlat.contains(it.packageName)
                            }
                        }
                        if (extraSelectedApps.isNotEmpty()) {
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                extraSelectedApps.forEach { appInfo ->
                                    Surface(
                                        color = colors.accent,
                                        shape = RoundedCornerShape(16.dp),
                                        modifier = Modifier
                                            .border(1.dp, colors.accent, RoundedCornerShape(16.dp))
                                            .clickable {
                                                if (config.companionSyncAllApps) onToggleSyncAllApps(false)
                                                onToggleAllowedPackage(appInfo.packageName)
                                            }
                                    ) {
                                        Text(
                                            text = "✓ ${appInfo.label}",
                                            color = colors.onAccent,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                                        )
                                    }
                                }
                            }
                        }

                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = {
                                    onToggleSyncAllApps(false)
                                    onSetAllowedPackages(
                                        (config.companionAllowedPackages + LauncherConfig.DEFAULT_COMPANION_PACKAGES).distinct()
                                    )
                                },
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("✓ 恢復預設常用全打勾", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                            Button(
                                onClick = { showInstalledAppPicker = true },
                                colors = ButtonDefaults.buttonColors(containerColor = colors.accent, contentColor = colors.onAccent),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("勾選更多手機 App（共 ${installedApps.size} 款）", fontSize = 12.sp, fontWeight = FontWeight.Bold)
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
                            Text("無差別同步所有 App 通知（不限勾選清單）", color = colors.primaryText, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            Text("預設關閉（僅推送上方已打勾 App，避免相機／相簿備份通知干擾行車）", color = colors.secondaryText, fontSize = 11.sp)
                        }
                        Switch(
                            checked = config.companionSyncAllApps,
                            onCheckedChange = onToggleSyncAllApps,
                            colors = SwitchDefaults.colors(checkedThumbColor = colors.onAccent, checkedTrackColor = colors.accent)
                        )
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

        // DEDICATED PHONE PERMISSIONS SHEET
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

        // INSTALLED PHONE APPS CHECKLIST PICKER MODAL ("手機端可攔截通知要可選，預設值可以先幫用戶打勾")
        if (showInstalledAppPicker) {
            val filteredApps = remember(installedApps, appSearchQuery, config.companionAllowedPackages) {
                val q = appSearchQuery.trim().lowercase()
                installedApps
                    .filter { q.isBlank() || it.label.lowercase().contains(q) || it.packageName.lowercase().contains(q) }
                    .sortedWith(
                        compareByDescending<InstalledAppInfo> { config.companionAllowedPackages.contains(it.packageName) }
                            .thenBy { it.label.lowercase() }
                    )
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.78f))
                    .clickable { showInstalledAppPicker = false },
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    color = colors.cardSurface,
                    shape = RoundedCornerShape(24.dp),
                    shadowElevation = 24.dp,
                    modifier = Modifier
                        .fillMaxWidth(0.94f)
                        .heightIn(max = 620.dp)
                        .border(1.5.dp, colors.accent, RoundedCornerShape(24.dp))
                        .clickable(enabled = false) {}
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
                            Column(modifier = Modifier.weight(1f)) {
                                Text("勾選要推送到車機的手機 App", color = colors.primaryText, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                                Text("目前已勾選 ${config.companionAllowedPackages.size} 項（預設已勾選常用通訊與簡訊）", color = colors.secondaryText, fontSize = 12.sp)
                            }
                            IconButton(onClick = { showInstalledAppPicker = false }) {
                                Icon(Icons.Rounded.Close, contentDescription = "關閉", tint = colors.primaryText)
                            }
                        }

                        OutlinedTextField(
                            value = appSearchQuery,
                            onValueChange = { appSearchQuery = it },
                            singleLine = true,
                            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null, tint = colors.accent) },
                            placeholder = { Text("搜尋手機 App 名稱…", fontSize = 13.sp) },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = {
                                    onToggleSyncAllApps(false)
                                    val allPkgs = (installedApps.map { it.packageName } + LauncherConfig.DEFAULT_COMPANION_PACKAGES).distinct()
                                    onSetAllowedPackages(allPkgs)
                                },
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("全部打勾", fontSize = 12.sp)
                            }
                            OutlinedButton(
                                onClick = {
                                    onToggleSyncAllApps(false)
                                    onSetAllowedPackages(LauncherConfig.DEFAULT_COMPANION_PACKAGES)
                                },
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("只勾選預設常用", fontSize = 12.sp)
                            }
                        }

                        LazyColumn(
                            modifier = Modifier.weight(1f, fill = false).fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(filteredApps, key = { it.packageName }) { appInfo ->
                                val checked = config.companionAllowedPackages.contains(appInfo.packageName)
                                val bmp = remember(appInfo.icon) { appInfo.icon.asImageBitmap() }
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(if (checked) colors.accent.copy(alpha = 0.15f) else colors.cardSurfaceElevated)
                                        .clickable {
                                            if (config.companionSyncAllApps) onToggleSyncAllApps(false)
                                            onToggleAllowedPackage(appInfo.packageName)
                                        }
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                        Image(bitmap = bmp, contentDescription = appInfo.label, modifier = Modifier.size(34.dp).clip(RoundedCornerShape(8.dp)))
                                        Spacer(Modifier.width(10.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(appInfo.label, color = colors.primaryText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            Text(appInfo.packageName, color = colors.secondaryText, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        }
                                    }
                                    Checkbox(
                                        checked = checked,
                                        onCheckedChange = {
                                            if (config.companionSyncAllApps) onToggleSyncAllApps(false)
                                            onToggleAllowedPackage(appInfo.packageName)
                                        },
                                        colors = CheckboxDefaults.colors(checkedColor = colors.accent, checkmarkColor = colors.onAccent)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // EXTRA-LARGE FONT TUTORIAL MODAL
        if (showLargeTutorial) {
            ExtraLargeTutorialModal(
                title = "📖 手機與車機連線＆多指滑鼠 · 教學",
                steps = listOf(
                    "【重要】請先將「車機盒」與「手機」都更新到最新同一版本 APK！",
                    "點擊手機最上方【權限需求】按鈕，開啟「通知讀取權限」（若 vivo／三星／Pixel 顯示受限制設定，先點黃色提示去應用程式資訊右上角 ⋮ 點「允許受限制的設定」）、藍牙與忽略電池最佳化。",
                    "車機端同樣點設定最上方【權限需求】，開啟「顯示在其他應用程式上層」、「通知存取權」與「無障礙輔助服務」。",
                    "常用通訊與簡訊 App 預設已自動幫您打勾 ✓，您也可隨時點擊「勾選更多手機 App」自由增減要推送到車機的應用程式！",
                    "點擊【啟動全螢幕滑鼠觸控板】進入純黑 OLED 防鎖屏模式：支援「單指移動／輕點震動確定」、「雙指捏合張開＝車機地圖縮放」、「雙指上下滑＝車機 Google 地圖 3D 俯瞰」、「雙指輕點＝返回」、「三指上滑＝多工／下滑＝首頁」！"
                ),
                highlightNote = "💡 離開全螢幕觸控板後，手機即恢復正常休眠；下車斷開藍牙與熱點自動退回 0% 耗電待機模式！",
                onClose = { showLargeTutorial = false }
            )
        }

        // FULLSCREEN PURE-BLACK OLED KEEP-SCREEN-ON TOUCHPAD MODE!
        AnimatedVisibility(
            visible = showFullscreenTouchpad,
            enter = fadeIn(tween(180)),
            exit = fadeOut(tween(140))
        ) {
            FullscreenOledCarTouchpad(
                isConnected = isConnected,
                peerName = bridgeStatus.peerName,
                sensitivity = config.phoneTouchpadSensitivity,
                onSensitivityChange = onChangeTouchpadSensitivity,
                onOpenCarPointerSpeedSettings = ::requestCarPointerSpeedSettings,
                onClose = { showFullscreenTouchpad = false }
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

/**
 * Clean Launcher Card on the main Phone Companion scroll screen:
 * Houses the big execution button to open the Fullscreen Pure-Black OLED Touchpad + Car Pointer Speed Settings button.
 */
@Composable
private fun PhoneTouchpadLauncherCard(
    sensitivity: Float,
    touchpadTip: String,
    onLaunchFullscreenTouchpad: () -> Unit,
    onSensitivityChange: (Float) -> Unit,
    onOpenCarPointerSpeedSettings: () -> Unit
) {
    val colors = LocalDxCarColors.current
    val context = LocalContext.current
    val localView = LocalView.current

    Surface(
        color = colors.cardSurface,
        shape = RoundedCornerShape(22.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.4.dp, colors.accent.copy(alpha = 0.65f), RoundedCornerShape(22.dp))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Mouse, contentDescription = null, tint = colors.accent, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(8.dp))
                Column {
                    Text("操控車機滑鼠觸控板", color = colors.primaryText, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text("點擊下方執行鈕進入「全螢幕純黑 OLED 防鎖屏觸控模式」", color = colors.secondaryText, fontSize = 12.sp)
                }
            }

            // BIG EXECUTION BUTTON -> Opens Fullscreen Pure-Black OLED Keep-Screen-On Touchpad!
            Button(
                onClick = {
                    triggerPhoneConfirmVibration(context, localView, heavy = false)
                    onLaunchFullscreenTouchpad()
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Black,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(62.dp)
                    .border(1.8.dp, colors.accent, RoundedCornerShape(18.dp))
            ) {
                Icon(Icons.Rounded.OpenInFull, contentDescription = null, tint = colors.accent, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(10.dp))
                Text(
                    text = "啟動全螢幕滑鼠觸控板（OLED 純黑 · 防鎖屏）",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }

            OutlinedButton(
                onClick = {
                    triggerPhoneConfirmVibration(context, localView, heavy = false)
                    onOpenCarPointerSpeedSettings()
                },
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

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "移速倍率：${String.format(Locale.US, "%.1fx", sensitivity)}",
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

/**
 * Dedicated Fullscreen Pure-Black (#000000) OLED Car Mouse Touchpad:
 * - Keeps smartphone screen awake (FLAG_KEEP_SCREEN_ON + keepScreenOn = true) until closed.
 * - True #000000 pure black canvas for zero OLED power draw & zero night glare.
 * - Crisp hardware motor vibration on every tap-confirm, left-click button, long-press & car action!
 */
@Composable
private fun FullscreenOledCarTouchpad(
    isConnected: Boolean,
    peerName: String,
    sensitivity: Float,
    onSensitivityChange: (Float) -> Unit,
    onOpenCarPointerSpeedSettings: () -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val localView = LocalView.current
    val scope = rememberCoroutineScope()

    var activeTouchPoints by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var clickBurstSeq by remember { mutableLongStateOf(0L) }
    var clickBurstPos by remember { mutableStateOf(Offset.Zero) }
    var statusBanner by remember {
        mutableStateOf("單指＝游標／點擊震動 · 雙指捏合＝車機縮放 · 雙指上下＝3D俯瞰 · 三指＝多工／首頁")
    }

    // Intercept system Back gesture to exit fullscreen touchpad cleanly
    BackHandler(onBack = onClose)

    // Keep Screen Awake (Prevent Screen Lock!) + Immersive Pure Black Bars while Fullscreen Touchpad is open!
    DisposableEffect(localView) {
        val activity = context as? Activity
        val window = activity?.window
        val prevKeepOn = localView.keepScreenOn
        localView.keepScreenOn = true
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val insetsController = if (window != null) {
            WindowCompat.getInsetsController(window, window.decorView)
        } else null
        insetsController?.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        insetsController?.hide(WindowInsetsCompat.Type.systemBars())

        onDispose {
            localView.keepScreenOn = prevKeepOn
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            insetsController?.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    var showClickRing by remember { mutableStateOf(false) }
    LaunchedEffect(clickBurstSeq) {
        if (clickBurstSeq > 0L) {
            showClickRing = true
            delay(260L)
            showClickRing = false
        }
    }
    val ringScale by animateFloatAsState(
        targetValue = if (showClickRing) 1.9f else 0.5f,
        animationSpec = tween(250),
        label = "oledClickRing"
    )

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

    val oledDimBorder = Color(0xFF1E293B)
    val oledSubtleText = Color(0xFF94A3B8)
    val oledAccent = Color(0xFF38BDF8)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black) // 100% Pure OLED #000000 Black!
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(10.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // TOP MINIMAL OLED BAR: Connection Dot + Speed -/+ + Car Sensitivity Jump + Exit Button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f).padding(end = 8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(9.dp)
                            .clip(CircleShape)
                            .background(if (isConnected) Color(0xFF22C55E) else Color(0xFFF59E0B))
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = if (isConnected) {
                            "防鎖屏滑鼠已連線 ${peerName.ifBlank { "AI Box" }}"
                        } else {
                            "防鎖屏開啟 · 等待車機連線…"
                        },
                        color = oledSubtleText,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Speed Stepper (- / 1.5x / +)
                    Surface(
                        color = Color.Black,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.border(1.dp, oledDimBorder, RoundedCornerShape(12.dp))
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            IconButton(
                                onClick = {
                                    triggerPhoneConfirmVibration(context, localView, heavy = false)
                                    onSensitivityChange((sensitivity - 0.2f).coerceIn(0.6f, 3.0f))
                                },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(Icons.Rounded.Remove, contentDescription = "減速", tint = oledSubtleText, modifier = Modifier.size(16.dp))
                            }
                            Text(
                                text = String.format(Locale.US, "%.1fx", sensitivity),
                                color = oledAccent,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            IconButton(
                                onClick = {
                                    triggerPhoneConfirmVibration(context, localView, heavy = false)
                                    onSensitivityChange((sensitivity + 0.2f).coerceIn(0.6f, 3.0f))
                                },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(Icons.Rounded.Add, contentDescription = "加速", tint = oledSubtleText, modifier = Modifier.size(16.dp))
                            }
                        }
                    }

                    // Exit Fullscreen Touchpad Button
                    Surface(
                        color = Color.Black,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .border(1.dp, Color(0xFF334155), RoundedCornerShape(12.dp))
                            .clickable {
                                triggerPhoneConfirmVibration(context, localView, heavy = false)
                                onClose()
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Rounded.Close, contentDescription = "關閉", tint = Color(0xFFE2E8F0), modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("關閉", color = Color(0xFFE2E8F0), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // MAIN GIANT EDGE-TO-EDGE PURE-BLACK OLED TOUCHPAD + RIGHT-SIDE SCROLL STRIP
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Primary Pure-Black Mouse Pad Area (~88% width, full remaining height!)
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color.Black)
                        .border(1.2.dp, oledDimBorder, RoundedCornerShape(24.dp))
                        .pointerInput(sensitivity) {
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                val downPos = down.position
                                val downTime = System.currentTimeMillis()
                                activeTouchPoints = listOf(downPos)
                                var lastSinglePos = downPos
                                var movedAny = false
                                var longPressed = false
                                var maxFingers = 1
                                var lastSendMs = 0L
                                var accDx = 0f
                                var accDy = 0f

                                // 2-finger & 3-finger tracking anchors
                                var twoInit = false
                                var twoStepSpan = 0f
                                var twoStepCx = 0f
                                var twoStepCy = 0f
                                var multiActionTriggered = false
                                var threeStartCx = 0f
                                var threeStartCy = 0f
                                var threeInit = false

                                val longPressJob = scope.launch {
                                    delay(560L)
                                    if (!movedAny && maxFingers == 1) {
                                        longPressed = true
                                        triggerPhoneConfirmVibration(context, localView, heavy = true)
                                        clickBurstPos = downPos
                                        clickBurstSeq++
                                        statusBanner = "✓ 已觸發車機長按"
                                        sendTouchAction(TouchpadAction.LONG_CLICK)
                                    }
                                }

                                while (true) {
                                    val ev = awaitPointerEvent(PointerEventPass.Main)
                                    val pressed = ev.changes.filter { it.pressed }
                                    activeTouchPoints = pressed.map { it.position }

                                    if (pressed.size > maxFingers) {
                                        maxFingers = pressed.size
                                        longPressJob.cancel()
                                    }

                                    if (pressed.size >= 3) {
                                        val cx = pressed.map { it.position.x }.average().toFloat()
                                        val cy = pressed.map { it.position.y }.average().toFloat()
                                        if (!threeInit) {
                                            threeInit = true
                                            threeStartCx = cx
                                            threeStartCy = cy
                                        } else if (!multiActionTriggered) {
                                            val d3x = cx - threeStartCx
                                            val d3y = cy - threeStartCy
                                            if (abs(d3y) >= 85f && abs(d3y) > abs(d3x)) {
                                                multiActionTriggered = true
                                                triggerPhoneConfirmVibration(context, localView, heavy = true)
                                                if (d3y < 0f) {
                                                    statusBanner = "✓ 三指上滑 → 車機多工視窗"
                                                    sendTouchAction(TouchpadAction.RECENTS)
                                                } else {
                                                    statusBanner = "✓ 三指下滑 → 返回車機首頁"
                                                    sendTouchAction(TouchpadAction.HOME)
                                                }
                                            } else if (abs(d3x) >= 95f && abs(d3x) > abs(d3y)) {
                                                multiActionTriggered = true
                                                triggerPhoneConfirmVibration(context, localView, heavy = true)
                                                statusBanner = "✓ 三指橫滑 → 車機首頁地圖↔媒體交換"
                                                sendTouchAction(TouchpadAction.SWAP_HOME_PANES)
                                            }
                                        }
                                    } else if (pressed.size == 2 && maxFingers == 2) {
                                        val p1 = pressed[0].position
                                        val p2 = pressed[1].position
                                        val span = hypot(p1.x - p2.x, p1.y - p2.y)
                                        val cx = (p1.x + p2.x) * 0.5f
                                        val cy = (p1.y + p2.y) * 0.5f

                                        if (!twoInit) {
                                            twoInit = true
                                            twoStepSpan = span
                                            twoStepCx = cx
                                            twoStepCy = cy
                                        } else {
                                            val dSpan = span - twoStepSpan
                                            val dCx = cx - twoStepCx
                                            val dCy = cy - twoStepCy
                                            val now = System.currentTimeMillis()
                                            if (now - lastSendMs >= 320L) {
                                                when {
                                                    abs(dSpan) >= 58f && abs(dSpan) > abs(dCy) * 1.1f -> {
                                                        multiActionTriggered = true
                                                        twoStepSpan = span
                                                        twoStepCx = cx
                                                        twoStepCy = cy
                                                        lastSendMs = now
                                                        triggerPhoneConfirmVibration(context, localView, heavy = false)
                                                        if (dSpan > 0f) {
                                                            statusBanner = "🔍 雙指張開 → 車機地圖／畫面放大"
                                                            sendTouchAction(TouchpadAction.TWO_FINGER_SPREAD_OUT)
                                                        } else {
                                                            statusBanner = "🔍 雙指捏合 → 車機地圖／畫面縮小"
                                                            sendTouchAction(TouchpadAction.TWO_FINGER_PINCH_IN)
                                                        }
                                                    }
                                                    abs(dCy) >= 64f && abs(dCy) > abs(dCx) * 1.15f -> {
                                                        multiActionTriggered = true
                                                        twoStepSpan = span
                                                        twoStepCx = cx
                                                        twoStepCy = cy
                                                        lastSendMs = now
                                                        triggerPhoneConfirmVibration(context, localView, heavy = false)
                                                        if (dCy < 0f) {
                                                            statusBanner = "🧊 雙指上推 → 車機 Google 地圖 3D 俯瞰"
                                                            sendTouchAction(TouchpadAction.TWO_FINGER_DRAG_UP)
                                                        } else {
                                                            statusBanner = "🧊 雙指下拉 → 車機 Google 地圖回 2D／下拉"
                                                            sendTouchAction(TouchpadAction.TWO_FINGER_DRAG_DOWN)
                                                        }
                                                    }
                                                    abs(dCx) >= 72f && abs(dCx) > abs(dCy) * 1.15f -> {
                                                        multiActionTriggered = true
                                                        twoStepSpan = span
                                                        twoStepCx = cx
                                                        twoStepCy = cy
                                                        lastSendMs = now
                                                        triggerPhoneConfirmVibration(context, localView, heavy = false)
                                                        if (dCx < 0f) {
                                                            statusBanner = "⇄ 雙指左滑 → 車機雙指平移"
                                                            sendTouchAction(TouchpadAction.TWO_FINGER_DRAG_LEFT)
                                                        } else {
                                                            statusBanner = "⇄ 雙指右滑 → 車機雙指平移"
                                                            sendTouchAction(TouchpadAction.TWO_FINGER_DRAG_RIGHT)
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    } else if (pressed.size == 1 && maxFingers == 1) {
                                        val cur = pressed[0].position
                                        if (!movedAny && hypot(cur.x - downPos.x, cur.y - downPos.y) > 11f) {
                                            movedAny = true
                                            longPressJob.cancel()
                                        }
                                        if (movedAny) {
                                            accDx += (cur.x - lastSinglePos.x) * sensitivity * 1.55f
                                            accDy += (cur.y - lastSinglePos.y) * sensitivity * 1.55f
                                            val now = System.currentTimeMillis()
                                            if (now - lastSendMs >= 16L && (abs(accDx) >= 0.8f || abs(accDy) >= 0.8f)) {
                                                sendTouchAction(TouchpadAction.MOVE, accDx, accDy)
                                                accDx = 0f
                                                accDy = 0f
                                                lastSendMs = now
                                            }
                                        }
                                        lastSinglePos = cur
                                    }

                                    ev.changes.forEach { it.consume() }
                                    if (pressed.isEmpty()) break
                                }

                                longPressJob.cancel()
                                activeTouchPoints = emptyList()
                                val elapsed = System.currentTimeMillis() - downTime

                                if (maxFingers == 1) {
                                    if (movedAny && (abs(accDx) >= 0.5f || abs(accDy) >= 0.5f)) {
                                        sendTouchAction(TouchpadAction.MOVE, accDx, accDy)
                                    } else if (!movedAny && !longPressed && elapsed < 550L) {
                                        triggerPhoneConfirmVibration(context, localView, heavy = false)
                                        clickBurstPos = downPos
                                        clickBurstSeq++
                                        statusBanner = "✓ 已點擊確定"
                                        sendTouchAction(TouchpadAction.CLICK)
                                    }
                                } else if (maxFingers == 2 && !multiActionTriggered && elapsed < 420L) {
                                    triggerPhoneConfirmVibration(context, localView, heavy = false)
                                    statusBanner = "← 雙指輕點 → 車機返回"
                                    sendTouchAction(TouchpadAction.BACK)
                                }
                            }
                        }
                ) {
                    // Dim low-luminance OLED watermark in the center
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(horizontal = 16.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.TouchApp,
                            contentDescription = null,
                            tint = Color(0xFF1E293B),
                            modifier = Modifier.size(54.dp)
                        )
                        Text(
                            text = "OLED 全螢幕多指車機觸控板",
                            color = Color(0xFF334155),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = statusBanner,
                            color = Color(0xFF475569),
                            fontSize = 12.sp
                        )
                    }

                    // Transient single & multi-finger touch-follow glow & confirm-click ripple (zero static burn-in!)
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        if (activeTouchPoints.size >= 2) {
                            for (i in 0 until activeTouchPoints.size - 1) {
                                drawLine(
                                    color = Color(0xFFA78BFA).copy(alpha = 0.45f),
                                    start = activeTouchPoints[i],
                                    end = activeTouchPoints[i + 1],
                                    strokeWidth = 2.5.dp.toPx()
                                )
                            }
                        }
                        val multiColor = if (activeTouchPoints.size >= 2) Color(0xFFA78BFA) else oledAccent
                        activeTouchPoints.forEach { pt ->
                            drawCircle(
                                color = multiColor.copy(alpha = 0.20f),
                                radius = 32.dp.toPx(),
                                center = pt
                            )
                            drawCircle(
                                color = multiColor.copy(alpha = 0.60f),
                                radius = 12.dp.toPx(),
                                center = pt
                            )
                        }
                        if (showClickRing) {
                            drawCircle(
                                color = Color(0xFF22C55E).copy(alpha = 0.65f),
                                radius = 26.dp.toPx() * ringScale,
                                center = clickBurstPos,
                                style = Stroke(width = 3.dp.toPx())
                            )
                        }
                    }
                }

                // Right-Side Dedicated Vertical Scroll Strip (Slide finger up/down to scroll car lists!)
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .width(44.dp)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color.Black)
                        .border(1.dp, oledDimBorder, RoundedCornerShape(20.dp))
                        .pointerInput(Unit) {
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                var stepY = down.position.y
                                while (true) {
                                    val ev = awaitPointerEvent(PointerEventPass.Main)
                                    val ch = ev.changes.firstOrNull { it.id == down.id } ?: ev.changes.firstOrNull()
                                    if (ch == null) break
                                    val dy = ch.position.y - stepY
                                    if (abs(dy) >= 68f) {
                                        stepY = ch.position.y
                                        triggerPhoneConfirmVibration(context, localView, heavy = false)
                                        sendTouchAction(if (dy < 0f) TouchpadAction.SCROLL_DOWN else TouchpadAction.SCROLL_UP)
                                    }
                                    if (ch.changedToUpIgnoreConsumed()) {
                                        ch.consume()
                                        break
                                    }
                                    ch.consume()
                                }
                            }
                        }
                        .padding(vertical = 14.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.KeyboardArrowUp,
                        contentDescription = "上捲",
                        tint = Color(0xFF475569),
                        modifier = Modifier
                            .size(24.dp)
                            .clickable {
                                triggerPhoneConfirmVibration(context, localView, heavy = false)
                                sendTouchAction(TouchpadAction.SCROLL_UP)
                            }
                    )
                    Text(
                        text = "捲\n動\n條",
                        color = Color(0xFF334155),
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Icon(
                        imageVector = Icons.Rounded.KeyboardArrowDown,
                        contentDescription = "下捲",
                        tint = Color(0xFF475569),
                        modifier = Modifier
                            .size(24.dp)
                            .clickable {
                                triggerPhoneConfirmVibration(context, localView, heavy = false)
                                sendTouchAction(TouchpadAction.SCROLL_DOWN)
                            }
                    )
                }
            }

            // BOTTOM PURE-BLACK TACTILE HARDWARE BUTTONS (Every click vibrates crisp!)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(58.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Big Primary Left-Click Confirm Button
                Surface(
                    color = Color.Black,
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier
                        .weight(1.4f)
                        .fillMaxHeight()
                        .border(1.4.dp, oledAccent.copy(alpha = 0.7f), RoundedCornerShape(18.dp))
                        .clickable {
                            triggerPhoneConfirmVibration(context, localView, heavy = false)
                            statusBanner = "✓ 已點擊確定"
                            sendTouchAction(TouchpadAction.CLICK)
                        }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("確定點擊", color = oledAccent, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
                    }
                }

                // Car Back Button
                Surface(
                    color = Color.Black,
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .border(1.dp, oledDimBorder, RoundedCornerShape(18.dp))
                        .clickable {
                            triggerPhoneConfirmVibration(context, localView, heavy = false)
                            statusBanner = "← 車機返回"
                            sendTouchAction(TouchpadAction.BACK)
                        }
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null, tint = Color(0xFFCBD5E1), modifier = Modifier.size(17.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("返回", color = Color(0xFFCBD5E1), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                }

                // Car Home Button
                Surface(
                    color = Color.Black,
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .border(1.dp, oledDimBorder, RoundedCornerShape(18.dp))
                        .clickable {
                            triggerPhoneConfirmVibration(context, localView, heavy = false)
                            statusBanner = "⌂ 車機首頁"
                            sendTouchAction(TouchpadAction.HOME)
                        }
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.Home, contentDescription = null, tint = Color(0xFFCBD5E1), modifier = Modifier.size(17.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("首頁", color = Color(0xFFCBD5E1), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                }

                // Car System Mouse Sensitivity Popup Button
                Surface(
                    color = Color.Black,
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier
                        .weight(1.1f)
                        .fillMaxHeight()
                        .border(1.dp, oledDimBorder, RoundedCornerShape(18.dp))
                        .clickable {
                            triggerPhoneConfirmVibration(context, localView, heavy = false)
                            statusBanner = "⚙️ 已在車機開啟靈敏度設定"
                            onOpenCarPointerSpeedSettings()
                        }
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.Settings, contentDescription = null, tint = oledSubtleText, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("車機靈敏度", color = oledSubtleText, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
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
            text = if (ok) "已授權" else "前往開啟",
            color = if (ok) colors.statusConnected else colors.accent,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

private fun List<Pair<List<String>, String>>.flattenPairs(): Set<String> =
    flatMap { it.first }.toSet()

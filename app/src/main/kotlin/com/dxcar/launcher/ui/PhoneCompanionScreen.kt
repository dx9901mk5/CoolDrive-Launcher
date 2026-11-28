package com.dxcar.launcher.ui

import android.app.Activity
import android.app.StatusBarManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.WindowManager
import android.widget.Toast
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.material.icons.rounded.AddToHomeScreen
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.BluetoothConnected
import androidx.compose.material.icons.rounded.BrightnessAuto
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.LightMode
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
import com.dxcar.launcher.MainActivity
import com.dxcar.launcher.R
import com.dxcar.launcher.billing.BillingTrialState
import com.dxcar.launcher.comms.BluetoothCompanionService
import com.dxcar.launcher.comms.BoxNotificationListenerService
import com.dxcar.launcher.comms.BridgeConnectionStatus
import com.dxcar.launcher.comms.BridgePacket
import com.dxcar.launcher.comms.BridgeStatusState
import com.dxcar.launcher.comms.DxCarBluetoothTileService
import com.dxcar.launcher.comms.PacketType
import com.dxcar.launcher.comms.TouchpadAction
import com.dxcar.launcher.data.AppLanguage
import com.dxcar.launcher.data.DeviceFormFactor
import com.dxcar.launcher.data.LauncherConfig
import com.dxcar.launcher.data.ThemeMode
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
    billingState: BillingTrialState = BillingTrialState(),
    onCheckUpdate: () -> Unit,
    onDownloadOrInstallUpdate: () -> Unit,
    onPurchasePro: () -> Unit = {},
    onRestorePurchases: () -> Unit = {},
    onRefreshPairedDevices: () -> Unit,
    onSelectTargetDevice: (PairedBtDevice) -> Unit,
    onSetTargetDeviceName: (String) -> Unit = {},
    onToggleAllowedPackage: (String) -> Unit,
    onSetAllowedPackages: (List<String>) -> Unit = {},
    onToggleSyncAllApps: (Boolean) -> Unit,
    onTogglePhoneCallCapture: (Boolean) -> Unit = {},
    onChangeTouchpadSensitivity: (Float) -> Unit = {},
    onSelectLanguage: (AppLanguage) -> Unit = {},
    isNight: Boolean = false,
    onCycleThemeMode: () -> Unit = {},
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

    // Automatically prompt newly required Contacts / Call Log / Phone permissions once when Phone Companion opens!
    LaunchedEffect(Unit) {
        if (!permissionHealth.hasContacts || !permissionHealth.hasCallLog || !permissionHealth.hasPhoneState || !permissionHealth.hasBluetooth) {
            onRequestRuntimePermissions()
        }
    }

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
            DxCarI18n.tr(
                "✓ 已送出指令！車機螢幕已跳出「滑鼠／指標速度（靈敏度）」設定頁",
                "✓ 已发送指令！车机屏幕已弹出「鼠标／指针速度（灵敏度）」设置页",
                "✓ Sent! Pointer Speed / Mouse Sensitivity settings opened on Car screen",
                "✓ 送信完了！車載画面に「ポインター速度（マウス感度）」設定を表示しました"
            )
        } else {
            DxCarI18n.tr(
                "⚠️ 尚未連上車機，請確認上方顯示「已連線同步中」",
                "⚠️ 尚未连上车机，请确认上方显示「已连接同步中」",
                "⚠️ Not connected to Car yet — verify connection status above",
                "⚠️ 車載機に未接続です。上部の接続状態をご確認ください"
            )
        }
    }

    val knownAppGroups: List<Pair<List<String>, String>> = listOf(
        listOf("jp.naver.line.android", "com.linecorp.linelite") to "LINE",
        listOf("com.facebook.orca") to "Messenger",
        listOf("com.whatsapp", "com.whatsapp.w4b") to "WhatsApp",
        listOf("org.telegram.messenger") to "Telegram",
        listOf("com.google.android.apps.messaging") to str("Google 簡訊", "Google 短信", "Google Messages", "Google メッセージ"),
        listOf("com.samsung.android.messaging") to str("三星簡訊", "三星短信", "Samsung Messages", "Samsung メッセージ"),
        listOf("com.android.mms", "com.coloros.mms", "com.oplus.mms", "com.miui.mms") to str("系統簡訊", "系统短信", "System SMS", "標準 SMS／メッセージ"),
        listOf("com.vivo.mms", "com.sie.mms", "com.bbk.mms", "com.iqoo.mms") to str("vivo 簡訊", "vivo 短信", "vivo Messages", "vivo メッセージ"),
        listOf("com.tencent.mm") to str("WeChat", "微信 (WeChat)", "WeChat", "WeChat"),
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
                        label = str("教學", "教程", "Guide", "ガイド"),
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
                                    str(
                                        "權限已就緒 ($grantedPerms/$totalPerms)",
                                        "权限已就绪 ($grantedPerms/$totalPerms)",
                                        "Permissions OK ($grantedPerms/$totalPerms)",
                                        "権限 OK ($grantedPerms/$totalPerms)"
                                    )
                                } else {
                                    str(
                                        "權限需求 ($grantedPerms/$totalPerms · 點此開啟)",
                                        "权限需求 ($grantedPerms/$totalPerms · 点此开启)",
                                        "Permissions ($grantedPerms/$totalPerms · Tap)",
                                        "権限設定 ($grantedPerms/$totalPerms · 開く)"
                                    )
                                },
                                color = colors.primaryText,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    val themeLabel = when (config.themeMode) {
                        ThemeMode.FORCE_NIGHT -> str("主題：深色", "主题：深色", "Theme: Dark", "テーマ：ダーク")
                        ThemeMode.FORCE_DAY -> str("主題：淺色", "主题：浅色", "Theme: Light", "テーマ：ライト")
                        else -> str("主題：跟隨系統", "主题：跟随系统", "Theme: System", "テーマ：システム")
                    }
                    val themeIcon = when (config.themeMode) {
                        ThemeMode.FORCE_NIGHT -> Icons.Rounded.DarkMode
                        ThemeMode.FORCE_DAY -> Icons.Rounded.LightMode
                        else -> if (isNight) Icons.Rounded.DarkMode else Icons.Rounded.BrightnessAuto
                    }
                    Surface(
                        color = colors.cardSurfaceElevated,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .border(1.2.dp, colors.accent.copy(alpha = 0.65f), RoundedCornerShape(14.dp))
                            .clickable { onCycleThemeMode() }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = themeIcon,
                                contentDescription = themeLabel,
                                tint = colors.accent,
                                modifier = Modifier.size(17.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = themeLabel,
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
                            Text(str("切換車機模式", "切换车机模式", "Switch to Car Mode", "車載モードへ切替"), fontSize = 13.sp)
                        }
                    }
                }
            }

            // ==================== CATEGORY 1: 車機連線狀態與測試 ====================
            PhoneCategoryLabel(
                title = str(
                    "分類一 · 車機雙通道連線狀態",
                    "分类一 · 车机双通道连接状态",
                    "Category 1 · Dual-Channel Car Connection",
                    "カテゴリ 1 · 車載機デュアル接続状態"
                ),
                hint = str(
                    "連上車機藍牙免持／SPP 即可自動穩定同步；可自訂預設藍牙裝置並將重連鈕加入桌面或下拉快捷鍵",
                    "连上车机蓝牙免提／SPP 即可自动稳定同步；可自定义默认蓝牙设备并将重连按钮加入桌面或下拉快捷键",
                    "Auto-syncs via Car Bluetooth; customize default target BT device and add Quick Reconnect shortcut/tile",
                    "車載 Bluetooth で自動同期。既定の Bluetooth 機器設定やホーム画面・クイック設定タイル追加に対応"
                )
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
                                    str(
                                        "已連線同步中 (${bridgeStatus.transportLabel})",
                                        "已连接同步中 (${bridgeStatus.transportLabel})",
                                        "Connected & Syncing (${bridgeStatus.transportLabel})",
                                        "接続済み・同期中 (${bridgeStatus.transportLabel})"
                                    )
                                } else {
                                    DxCarI18n.bridgeStatusLabel(bridgeStatus.status)
                                },
                                color = colors.primaryText,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = when {
                                    isConnected && bridgeStatus.peerName.isNotBlank() ->
                                        str(
                                            "已連接車機：${bridgeStatus.peerName}",
                                            "已连接车机：${bridgeStatus.peerName}",
                                            "Connected Car: ${bridgeStatus.peerName}",
                                            "接続中の車載機：${bridgeStatus.peerName}"
                                        )
                                    config.companionTargetDeviceName.isNotBlank() ->
                                        str(
                                            "預設藍牙目標：${config.companionTargetDeviceName}",
                                            "默认蓝牙目标：${config.companionTargetDeviceName}",
                                            "Default BT Target: ${config.companionTargetDeviceName}",
                                            "既定の Bluetooth 対象：${config.companionTargetDeviceName}"
                                        )
                                    else ->
                                        str(
                                            "只要手機已連上車機藍牙免持，系統即自動偵測連入",
                                            "只要手机已连上车机蓝牙免提，系统即自动检测连入",
                                            "Auto-detects & connects whenever paired to Car Bluetooth",
                                            "車載 Bluetooth に接続すると自動で検出して接続します"
                                        )
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
                                    str(
                                        "✓ 通知擷取服務已啟動運作中",
                                        "✓ 通知捕获服务已启动运行中",
                                        "✓ Notification Listener Service Active",
                                        "✓ 通知読み取りサービス稼働中"
                                    )
                                } else {
                                    str(
                                        "⚠️ 尚未啟用通知讀取服務，請點最上方「權限需求」開啟",
                                        "⚠️ 尚未启用通知读取服务，请点最上方「权限需求」开启",
                                        "⚠️ Notification Access off — tap Permissions above to enable",
                                        "⚠️ 通知へのアクセスが未許可です。上部の「権限設定」から有効にしてください"
                                    )
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
                            Text(str("再次觸發藍牙連線", "再次触发蓝牙连接", "Reconnect Bluetooth", "Bluetooth 再接続"))
                        }
                        OutlinedButton(
                            onClick = {
                                val sm = context.getSystemService(ShortcutManager::class.java)
                                if (sm != null && sm.isRequestPinShortcutSupported) {
                                    val shortcutIntent = Intent(context, MainActivity::class.java).apply {
                                        action = MainActivity.ACTION_TRIGGER_BT_RECONNECT
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                                    }
                                    val label = DxCarI18n.tr("DX-Car 藍牙連線", "DX-Car 蓝牙连接", "DX-Car BT Connect", "DX-Car BT接続")
                                    val info = ShortcutInfo.Builder(context, "dxcar_bt_reconnect")
                                        .setShortLabel(label)
                                        .setLongLabel(label)
                                        .setIcon(Icon.createWithResource(context, R.mipmap.ic_launcher))
                                        .setIntent(shortcutIntent)
                                        .build()
                                    sm.requestPinShortcut(info, null)
                                } else {
                                    Toast.makeText(
                                        context,
                                        DxCarI18n.tr(
                                            "此裝置桌面不支援自動釘選捷徑，可改用「加入下拉快捷鍵」",
                                            "此设备桌面不支持自动固定快捷方式，可改用「加入下拉快捷键」",
                                            "Launcher does not support pinned shortcuts; try Add Quick Tile",
                                            "ホーム画面のピン留め非対応です。「クイック設定に追加」をご利用ください"
                                        ),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            },
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(Icons.Rounded.AddToHomeScreen, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(str("加入桌面捷徑", "加入桌面快捷方式", "Add Home Shortcut", "ホーム画面に追加"), fontSize = 12.sp)
                        }
                        OutlinedButton(
                            onClick = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    val sbm = context.getSystemService(StatusBarManager::class.java)
                                    val label = DxCarI18n.tr("DX-Car 藍牙連線", "DX-Car 蓝牙连接", "DX-Car BT Connect", "DX-Car BT接続")
                                    sbm?.requestAddTileService(
                                        ComponentName(context, DxCarBluetoothTileService::class.java),
                                        label,
                                        Icon.createWithResource(context, R.mipmap.ic_launcher),
                                        context.mainExecutor
                                    ) { resultCode ->
                                        val msg = if (resultCode == StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED ||
                                            resultCode == StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED
                                        ) {
                                            DxCarI18n.tr("✓ 已加入下拉快速設定磚", "✓ 已加入下拉快捷设置砖", "✓ Added to Quick Settings", "✓ クイック設定に追加しました")
                                        } else {
                                            DxCarI18n.tr("可從系統下拉快捷鍵編輯面板拖入「DX-Car 藍牙連線」", "可从系统下拉快捷键编辑面板拖入「DX-Car 蓝牙连接」", "You can drag 'DX-Car BT Connect' in Quick Settings edit", "クイック設定の編集から「DX-Car BT接続」を追加できます")
                                        }
                                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                    }
                                } else {
                                    Toast.makeText(
                                        context,
                                        DxCarI18n.tr(
                                            "請從螢幕頂部下拉快捷鍵 → 點擊編輯，拖入「DX-Car 藍牙連線」",
                                            "请从屏幕顶部下拉快捷键 → 点击编辑，拖入「DX-Car 蓝牙连接」",
                                            "Pull down Quick Settings → Edit → drag 'DX-Car BT Connect'",
                                            "クイック設定を開き「編集」から「DX-Car BT接続」を追加してください"
                                        ),
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            },
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(Icons.Rounded.Bluetooth, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(str("加入下拉快捷鍵", "加入下拉快捷键", "Add Quick Tile", "クイック設定に追加"), fontSize = 12.sp)
                        }
                        OutlinedButton(
                            onClick = {
                                val ok = BluetoothCompanionService.sendPacketFromPhone(
                                    BridgePacket(
                                        type = PacketType.MESSAGE,
                                        id = UUID.randomUUID().toString(),
                                        appPackage = "jp.naver.line.android",
                                        appName = "LINE",
                                        sender = DxCarI18n.tr(
                                            "手機同步測試",
                                            "手机同步测试",
                                            "Phone Sync Test",
                                            "スマホ同期テスト"
                                        ),
                                        text = DxCarI18n.tr(
                                            "DX-Car Launcher 雙通道通知測試成功！",
                                            "DX-Car Launcher 双通道通知测试成功！",
                                            "DX-Car Launcher dual-channel notification test succeeded!",
                                            "DX-Car Launcher デュアル通知テスト成功！"
                                        ),
                                        canReply = false
                                    )
                                )
                                testSentTip = if (ok) {
                                    DxCarI18n.tr(
                                        "✓ 已送出測試通知至車機！請看車機上方橫幅或鈴鐺",
                                        "✓ 已发送测试通知至车机！请看车机上方横幅或铃铛",
                                        "✓ Test notification sent! Check the banner or bell on your Car screen",
                                        "✓ テスト通知を送信しました！車載画面の上部バナーまたはベルをご確認ください"
                                    )
                                } else {
                                    DxCarI18n.tr(
                                        "⏳ 已排入傳送佇列並喚醒藍牙連線：請確認車機已開啟新版且與手機藍牙已配對",
                                        "⏳ 已排入发送队列并唤醒蓝牙连接：请确认车机已开启新版且与手机蓝牙已配对",
                                        "⏳ Queued & waking BT link: ensure Car has the app open and BT paired",
                                        "⏳ 送信キューに追加し BT 接続を起動中：車載機側も起動し Bluetooth ペアリング済みかご確認ください"
                                    )
                                }
                            },
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(str("推播測試通知", "推送测试通知", "Send Test Alert", "テスト通知を送信"))
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
                    title = str(
                        "分類二 · 車機遠端滑鼠觸控板（手機專屬）",
                        "分类二 · 车机远程鼠标触控板（手机专属）",
                        "Category 2 · Remote Car Mouse Touchpad (Phone Exclusive)",
                        "カテゴリ 2 · 車載リモートマウスタッチパッド（スマホ専用）"
                    ),
                    hint = str(
                        "點擊下方執行按鈕展開「全螢幕純黑 OLED 防鎖屏觸控板」（支援點擊清脆震動回饋與車機滑鼠靈敏度設定）",
                        "点击下方执行按钮展开「全屏纯黑 OLED 防锁屏触控板」（支持点击清脆震动反馈与车机鼠标灵敏度设置）",
                        "Tap below to launch the Fullscreen Pure-Black OLED Keep-Awake Touchpad with crisp haptic click & Car pointer speed control",
                        "下のボタンで「全画面ピュアブラック OLED スリープ防止タッチパッド」を起動（振動フィードバック＆車載マウス感度設定対応）"
                    )
                )

                PhoneTouchpadLauncherCard(
                    sensitivity = config.phoneTouchpadSensitivity,
                    touchpadTip = touchpadTip,
                    onLaunchFullscreenTouchpad = { showFullscreenTouchpad = true },
                    onSensitivityChange = onChangeTouchpadSensitivity,
                    onOpenCarPointerSpeedSettings = ::requestCarPointerSpeedSettings
                )
            }

            // ==================== SEND ADDRESS / LOCATION TO CAR CARD ====================
            run {
                var shareQuery by remember { mutableStateOf("") }
                var shareSending by remember { mutableStateOf(false) }
                var shareResultTip by remember { mutableStateOf("") }
                val shareScope = rememberCoroutineScope()

                Surface(
                    color = colors.cardSurface,
                    shape = RoundedCornerShape(22.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.2.dp, Color(0xFF38BDF8).copy(alpha = 0.65f), RoundedCornerShape(22.dp))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = str(
                                "📍 分享地址／關鍵字傳送到車機導航",
                                "📍 分享地址／关键词发送到车机导航",
                                "📍 Send Address or Place to Car Map",
                                "📍 住所・スポットを車載機へ送信"
                            ),
                            color = colors.primaryText,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = str(
                                "可在任何 App（LINE / Google 地圖 / 瀏覽器）點「分享 → DX-Car」，或直接在此輸入地址、店名或地圖連結；手機會自動解析實際地址並送到車機，讓您選擇用內建地圖、Google 地圖或其他地圖開啟。",
                                "可在任意 App（微信 / Google 地图 / 浏览器）点「分享 → DX-Car」，或直接在此输入地址、店名或地图链接；手机自动解析实际地址并发送到车机，供您选择打开的地图。",
                                "Share from any app (Google Maps / LINE / Browser -> DX-Car) or enter an address/keyword below. Phone resolves the real address and lets you pick which map opens it on the Car.",
                                "他アプリから「共有 → DX-Car」または下に住所・店名を入力すると、スマホが住所を解析して車載機へ送信します。"
                            ),
                            color = colors.secondaryText,
                            fontSize = 12.sp
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = shareQuery,
                                onValueChange = { shareQuery = it },
                                singleLine = true,
                                placeholder = {
                                    Text(
                                        str("輸入地址、地標關鍵字或貼上地圖連結…", "输入地址、地标关键词或粘贴地图链接…", "Address, place name, or Maps link…", "住所・店名・マップURLを入力…"),
                                        fontSize = 12.sp
                                    )
                                },
                                modifier = Modifier.weight(1f)
                            )
                            Button(
                                enabled = !shareSending && shareQuery.isNotBlank(),
                                onClick = {
                                    val q = shareQuery.trim()
                                    if (q.isBlank()) return@Button
                                    shareSending = true
                                    shareResultTip = DxCarI18n.tr("🔍 正在解析實際地址…", "🔍 正在解析实际地址…", "🔍 Resolving address…", "🔍 住所を解析中…")
                                    shareScope.launch {
                                        val app = com.dxcar.launcher.DxCarApp.instance
                                        val curLoc = app?.locationTracker?.location?.value
                                        val biasLat = if (curLoc != null && curLoc.latitude != 0.0) curLoc.latitude else config.lastKnownLat
                                        val biasLon = if (curLoc != null && curLoc.longitude != 0.0) curLoc.longitude else config.lastKnownLon
                                        val resolved = if (app != null) {
                                            com.dxcar.launcher.map.SharedLocationResolver.resolveSharedText(
                                                context = context,
                                                navEngine = app.builtInNavEngine,
                                                rawText = q,
                                                biasLat = biasLat,
                                                biasLon = biasLon
                                            )
                                        } else {
                                            com.dxcar.launcher.map.ResolvedSharedLocation(q, q, 0.0, 0.0)
                                        }
                                        val sent = BluetoothCompanionService.sendPacketFromPhone(
                                            BridgePacket(
                                                type = PacketType.SHARED_LOCATION,
                                                id = UUID.randomUUID().toString(),
                                                sender = resolved.label,
                                                text = resolved.address,
                                                latitude = resolved.latitude,
                                                longitude = resolved.longitude
                                            )
                                        )
                                        shareSending = false
                                        shareResultTip = if (sent) {
                                            "✓ 已傳送給車機：${resolved.label}（${resolved.address}）"
                                        } else {
                                            "⚠️ 已解析「${resolved.address}」並排入傳送，請確認已連上車機"
                                        }
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = colors.accent, contentColor = colors.onAccent),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(str("傳送", "发送", "Send", "送信"), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        if (shareResultTip.isNotBlank()) {
                            Text(shareResultTip, color = colors.accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            // ==================== CATEGORY 3: 藍牙目標與通知過濾設定 ====================
            PhoneCategoryLabel(
                title = str(
                    "分類三 · 配對車機選擇與通知過濾",
                    "分类三 · 配对车机选择与通知过滤",
                    "Category 3 · Paired Car Selection & Notification Filter",
                    "カテゴリ 3 · ペアリング車載機の選択＆通知フィルター"
                ),
                hint = str(
                    "可點選車機藍牙名稱加速指定配對，並選擇要推送至車機的 App",
                    "可点选车机蓝牙名称加速指定配对，并选择要推送至车机的 App",
                    "Select your Car Bluetooth device for faster pairing and choose which apps forward notifications",
                    "車載 Bluetooth 名を選択して素早く接続し、車載機へ転送するアプリを選択します"
                )
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
                            text = str(
                                "選擇配對的車機 AI Box（或輸入預設藍牙裝置名稱）",
                                "选择配对的车机 AI Box（或输入默认蓝牙设备名称）",
                                "Select Paired Car AI Box (or set default BT device name)",
                                "ペアリング済みの車載 AI Box を選択（または既定の機器名を入力）"
                            ),
                            color = colors.primaryText,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f).padding(end = 8.dp)
                        )
                        OutlinedButton(onClick = onRefreshPairedDevices, shape = RoundedCornerShape(12.dp)) {
                            Text(str("重新整理", "刷新", "Refresh", "更新"), fontSize = 12.sp)
                        }
                    }
                    OutlinedTextField(
                        value = config.companionTargetDeviceName,
                        onValueChange = { onSetTargetDeviceName(it) },
                        singleLine = true,
                        label = {
                            Text(
                                str(
                                    "預設觸發搜尋的藍牙裝置名稱",
                                    "默认触发搜索的蓝牙设备名称",
                                    "Default Target Bluetooth Device Name",
                                    "既定の検索対象 Bluetooth 機器名"
                                ),
                                fontSize = 12.sp
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (pairedDevices.isEmpty()) {
                        Text(
                            text = str(
                                "未偵測到已配對藍牙裝置（請先確認已授予藍牙權限並於系統藍牙配對車機）。",
                                "未检测到已配对蓝牙设备（请先确认已授予蓝牙权限并在系统蓝牙配对车机）。",
                                "No paired Bluetooth devices found (grant Bluetooth permission and pair Car BT in system settings).",
                                "ペアリング済みの Bluetooth 機器が見つかりません（Bluetooth 権限を許可し車載機とペアリングしてください）。"
                            ),
                            color = colors.secondaryText,
                            fontSize = 12.sp
                        )
                    } else {
                        val btConnectedSuffix = str("目前已連上藍牙", "当前已连上蓝牙", "Connected via BT", "Bluetooth 接続中")
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
                                        text = "${dev.address}${if (dev.isConnected) " · $btConnectedSuffix" else ""}",
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
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Text(
                                text = str(
                                    "電話通知擷取（預設開啟 · 同步來電至車機）",
                                    "电话通知捕获（默认开启 · 同步来电至车机）",
                                    "Phone Call Capture (Default On)",
                                    "電話着信キャプチャ（既定オン）"
                                ),
                                color = colors.primaryText,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = str(
                                    "即時轉傳聯絡人名稱與未知來電號碼至車機，通話結束自動消失；未勾選的系統 App 不轉傳",
                                    "实时转发联系人名称与未知来电号码至车机，通话结束自动消失；未勾选的系统 App 不转发",
                                    "Forwards caller name & unknown numbers to Car in real time and auto-clears when ended",
                                    "発信者名・番号を車載機へリアルタイム転送し、通話終了時に自動消去します"
                                ),
                                color = colors.secondaryText,
                                fontSize = 11.sp
                            )
                        }
                        Switch(
                            checked = config.phoneCallCaptureEnabled,
                            onCheckedChange = onTogglePhoneCallCapture
                        )
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
                                    text = str(
                                        "選擇要攔截推送到車機的 App（預設已幫您打勾 ✓）",
                                        "选择要拦截推送到车机的 App（默认已帮您打勾 ✓）",
                                        "Select Apps to Forward to Car (Common apps pre-checked ✓)",
                                        "車載機へ通知転送するアプリを選択（主要アプリは選択済 ✓）"
                                    ),
                                    color = colors.primaryText,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = str(
                                        "點選下方任一項目即可勾選／取消；亦可從手機已安裝 App 挑選更多應用程式",
                                        "点选下方任一项目即可勾选／取消；亦可从手机已安装 App 挑选更多应用",
                                        "Tap any chip below to toggle, or pick more from all installed phone apps",
                                        "下のチップをタップして切替、またはインストール済みアプリから追加できます"
                                    ),
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
                        val knownFlat = remember(knownAppGroups) { knownAppGroups.flattenPairs() }
                        val extraSelectedApps = remember(installedApps, config.companionAllowedPackages, knownFlat) {
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
                                Text(
                                    str(
                                        "✓ 恢復預設常用全打勾",
                                        "✓ 恢复默认常用全打勾",
                                        "✓ Reset Default Apps",
                                        "✓ 標準アプリを再選択"
                                    ),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            OutlinedButton(
                                onClick = {
                                    onToggleSyncAllApps(false)
                                    onSetAllowedPackages(emptyList())
                                },
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(
                                    str(
                                        "✕ 全不勾選",
                                        "✕ 全不勾选",
                                        "✕ Unselect All",
                                        "✕ すべて解除"
                                    ),
                                    color = Color(0xFFEF4444),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Button(
                                onClick = { showInstalledAppPicker = true },
                                colors = ButtonDefaults.buttonColors(containerColor = colors.accent, contentColor = colors.onAccent),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    str(
                                        "勾選更多手機 App（共 ${installedApps.size} 款）",
                                        "勾选更多手机 App（共 ${installedApps.size} 款）",
                                        "More Phone Apps (${installedApps.size})",
                                        "他のアプリを選択（全 ${installedApps.size} 件）"
                                    ),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
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
                            Text(
                                str(
                                    "無差別同步所有 App 通知（不限勾選清單）",
                                    "无差别同步所有 App 通知（不限勾选清单）",
                                    "Forward All App Notifications (Ignore Checklist)",
                                    "すべてのアプリ通知を転送（選択リスト無制限）"
                                ),
                                color = colors.primaryText,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                str(
                                    "預設關閉（僅推送上方已打勾 App，避免相機／相簿備份通知干擾行車）",
                                    "默认关闭（仅推送上方已打勾 App，避免相机／相册备份通知干扰行车）",
                                    "Off by default (only forwards checked apps to avoid backup/system clutter while driving)",
                                    "標準オフ（選択アプリのみ転送し、バックアップ等の不要な通知を防止）"
                                ),
                                color = colors.secondaryText,
                                fontSize = 11.sp
                            )
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
                title = str(
                    "分類四 · 即時診斷紀錄與版本更新",
                    "分类四 · 实时诊断记录与版本更新",
                    "Category 4 · Live Diagnostics & App Updates",
                    "カテゴリ 4 · 診断ログ＆バージョン更新"
                ),
                hint = str(
                    "手機與車機皆更新至同一最新版本可獲得最完整雙向通道支援",
                    "手机与车机皆更新至同一最新版本可获得最完整双向通道支持",
                    "Keep both Phone & Car updated to the same latest version for best dual-channel reliability",
                    "スマホと車載機の両方を同じ最新版に更新すると最も安定して連携できます"
                )
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
                        Text(
                            str(
                                "即時同步與診斷紀錄",
                                "实时同步与诊断记录",
                                "Live Sync & Diagnostic Logs",
                                "リアルタイム同期・診断ログ"
                            ),
                            color = colors.primaryText,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
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
                    .border(1.dp, colors.accent.copy(alpha = 0.55f), RoundedCornerShape(22.dp))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = if (billingState.isProUnlocked) {
                            str("👑 DX-Car Pro 永久買斷版（已啟用）", "👑 DX-Car Pro 永久买断版（已激活）", "👑 DX-Car Pro Lifetime (Unlocked)", "👑 DX-Car Pro 買い切り版（有効）")
                        } else if (billingState.isInTrial) {
                            str(
                                "🎁 七天全功能免費試用中（剩餘 ${billingState.remainingDays} 天 ${billingState.remainingHoursRemainder} 小時）",
                                "🎁 七天全功能免费试用中（剩余 ${billingState.remainingDays} 天 ${billingState.remainingHoursRemainder} 小时）",
                                "🎁 7-Day Full Trial (${billingState.remainingDays}d ${billingState.remainingHoursRemainder}h left)",
                                "🎁 7日間フル機能トライアル中（残り ${billingState.remainingDays}日 ${billingState.remainingHoursRemainder}時間）"
                            )
                        } else {
                            str("🔒 七天免費試用已結束", "🔒 七天免费试用已结束", "🔒 7-Day Free Trial Ended", "🔒 7日間無料トライアル終了")
                        },
                        color = colors.primaryText,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = str(
                            "v${BuildConfig.VERSION_NAME} · 一次買斷 ${billingState.formattedPrice} 永久解鎖雙端全功能（含 Google Play 授權同步）",
                            "v${BuildConfig.VERSION_NAME} · 一次买断 ${billingState.formattedPrice} 永久解锁双端全功能（含 Google Play 授权同步）",
                            "v${BuildConfig.VERSION_NAME} · One-time ${billingState.formattedPrice} lifetime unlock for Car & Phone",
                            "v${BuildConfig.VERSION_NAME} · ${billingState.formattedPrice} 買い切りで全機能を永久利用可能"
                        ),
                        color = colors.secondaryText,
                        fontSize = 12.sp
                    )
                    if (billingState.statusMessage.isNotBlank()) {
                        Text(billingState.statusMessage, color = colors.accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                    if (!billingState.isProUnlocked) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = onPurchasePro,
                                colors = ButtonDefaults.buttonColors(containerColor = colors.accent, contentColor = colors.onAccent),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Text(
                                    str(
                                        "立即買斷 ${billingState.formattedPrice}",
                                        "立即买断 ${billingState.formattedPrice}",
                                        "Unlock ${billingState.formattedPrice}",
                                        "買い切り購入 ${billingState.formattedPrice}"
                                    ),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            OutlinedButton(
                                onClick = onRestorePurchases,
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Text(str("恢復購買", "恢复购买", "Restore Purchase", "購入を復元"))
                            }
                        }
                    }
                    if (BuildConfig.ENABLE_GITHUB_AUTO_UPDATE) {
                        if (updateState.statusMessage.isNotBlank()) {
                            Text(
                                text = updateState.statusMessage,
                                color = if (updateState.hasUpdate || updateState.downloadedReady) colors.accent else colors.secondaryText,
                                fontSize = 12.sp
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = onCheckUpdate,
                                enabled = !updateState.checking && !updateState.downloading,
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Text(
                                    if (updateState.checking) {
                                        str("檢查中…", "检查中…", "Checking…", "確認中…")
                                    } else {
                                        str("檢查 GitHub 更新", "检查 GitHub 更新", "Check Updates", "更新を確認")
                                    }
                                )
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
                                            updateState.downloadedReady -> str(
                                                "立即安裝 v${updateState.latestVersion}",
                                                "立即安装 v${updateState.latestVersion}",
                                                "Install v${updateState.latestVersion}",
                                                "今すぐインストール v${updateState.latestVersion}"
                                            )
                                            updateState.downloading -> str(
                                                "下載中 ${(updateState.progress * 100).toInt()}%",
                                                "下载中 ${(updateState.progress * 100).toInt()}%",
                                                "Downloading ${(updateState.progress * 100).toInt()}%",
                                                "ダウンロード中 ${(updateState.progress * 100).toInt()}%"
                                            )
                                            else -> str(
                                                "下載更新 v${updateState.latestVersion}",
                                                "下载更新 v${updateState.latestVersion}",
                                                "Download v${updateState.latestVersion}",
                                                "更新 v${updateState.latestVersion}"
                                            )
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

                    OutlinedButton(
                        onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(com.dxcar.launcher.update.UpdateManager.GITHUB_URL))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            runCatching { context.startActivity(intent) }
                        },
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text(
                            str(
                                "GitHub 開源主頁：dx9901mk5/DX-Car-Launcher",
                                "GitHub 开源主页：dx9901mk5/DX-Car-Launcher",
                                "GitHub: dx9901mk5/DX-Car-Launcher",
                                "GitHub: dx9901mk5/DX-Car-Launcher"
                            ),
                            fontSize = 12.sp
                        )
                    }
                }
            }

            // ==================== BOTTOM REMOTE PLAN A / PLAN B SELECTOR CARD ====================
            val localView = LocalView.current
            var remotePlanTip by remember { mutableStateOf("") }
            Surface(
                color = colors.cardSurface,
                shape = RoundedCornerShape(22.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.4.dp, colors.accent.copy(alpha = 0.65f), RoundedCornerShape(22.dp))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.DirectionsCar,
                            contentDescription = null,
                            tint = colors.accent,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(
                                text = str(
                                    "遙控切換車機首頁方案（方案 A ／ 方案 B）",
                                    "遥控切换车机首页方案（方案 A ／ 方案 B）",
                                    "Remote Car Home Plan Switcher (Plan A / Plan B)",
                                    "車載機のホームプラン遠隔切替（プラン A ／ プラン B）"
                                ),
                                color = colors.primaryText,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = str(
                                    "一鍵遙控車機切換「方案 A（內建地圖）」或「方案 B（進階雙窗擴充模式）」",
                                    "一键遥控车机切换「方案 A（内置地图）」或「方案 B（进阶双窗扩展模式）」",
                                    "One-tap remote switch between Plan A (Built-in Map) and Plan B (Dual-Window Mode)",
                                    "ワンタップで車載機を「プラン A（内蔵地図）」または「プラン B（拡張2画面）」に切替"
                                ),
                                color = colors.secondaryText,
                                fontSize = 11.5.sp
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = {
                                triggerPhoneConfirmVibration(context, localView, heavy = false)
                                val ok = BluetoothCompanionService.sendPacketFromPhone(
                                    BridgePacket(
                                        type = PacketType.TOUCHPAD_EVENT,
                                        id = UUID.randomUUID().toString(),
                                        touchAction = TouchpadAction.SELECT_PLAN_A
                                    )
                                )
                                remotePlanTip = if (ok) {
                                    DxCarI18n.tr(
                                        "✓ 已傳送指令：車機已切換為「方案 A」",
                                        "✓ 已发送指令：车机已切换为「方案 A」",
                                        "✓ Sent: Car switched to Plan A",
                                        "✓ 送信完了：車載機をプラン A に切り替えました"
                                    )
                                } else {
                                    DxCarI18n.tr(
                                        "⚠️ 尚未連上車機，請先確認上方藍牙／Wi-Fi 已連線",
                                        "⚠️ 尚未连上车机，请先确认上方蓝牙／Wi-Fi 已连接",
                                        "⚠️ Not connected to Car yet — check connection above",
                                        "⚠️ 車載機に未接続です。上部の接続状態をご確認ください"
                                    )
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = colors.accent,
                                contentColor = colors.onAccent
                            ),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.weight(1f).height(48.dp)
                        ) {
                            Text(
                                text = str("遙控切換 方案 A", "遥控切换 方案 A", "Select Plan A", "プラン A へ切替"),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.ExtraBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        OutlinedButton(
                            onClick = {
                                triggerPhoneConfirmVibration(context, localView, heavy = false)
                                val ok = BluetoothCompanionService.sendPacketFromPhone(
                                    BridgePacket(
                                        type = PacketType.TOUCHPAD_EVENT,
                                        id = UUID.randomUUID().toString(),
                                        touchAction = TouchpadAction.SELECT_PLAN_B
                                    )
                                )
                                remotePlanTip = if (ok) {
                                    DxCarI18n.tr(
                                        "✓ 已傳送指令：車機已切換為「方案 B」",
                                        "✓ 已发送指令：车机已切换为「方案 B」",
                                        "✓ Sent: Car switched to Plan B",
                                        "✓ 送信完了：車載機をプラン B に切り替えました"
                                    )
                                } else {
                                    DxCarI18n.tr(
                                        "⚠️ 尚未連上車機，請先確認上方藍牙／Wi-Fi 已連線",
                                        "⚠️ 尚未连上车机，请先确认上方蓝牙／Wi-Fi 已连接",
                                        "⚠️ Not connected to Car yet — check connection above",
                                        "⚠️ 車載機に未接続です。上部の接続状態をご確認ください"
                                    )
                                }
                            },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.weight(1f).height(48.dp)
                        ) {
                            Text(
                                text = str("遙控切換 方案 B", "遥控切换 方案 B", "Select Plan B", "プラン B へ切替"),
                                color = colors.accent,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.ExtraBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    if (remotePlanTip.isNotBlank()) {
                        Text(
                            text = remotePlanTip,
                            color = colors.accent,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
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
                            Text(
                                str(
                                    "手機端必要權限設定 ($grantedPerms/$totalPerms)",
                                    "手机端必要权限设置 ($grantedPerms/$totalPerms)",
                                    "Required Phone Permissions ($grantedPerms/$totalPerms)",
                                    "スマホ側の必須権限 ($grantedPerms/$totalPerms)"
                                ),
                                color = colors.primaryText,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f).padding(end = 8.dp)
                            )
                            IconButton(onClick = { showPermissionsSheet = false }) {
                                Icon(Icons.Rounded.Close, contentDescription = str("關閉", "关闭", "Close", "閉じる"), tint = colors.primaryText)
                            }
                        }

                        CompanionPermButton(
                            label = str(
                                "1. 通知讀取權限（攔截 LINE / 簡訊並執行車機回覆）",
                                "1. 通知读取权限（拦截 LINE / 短信并执行车机回复）",
                                "1. Notification Access (Forward LINE / SMS & send replies from Car)",
                                "1. 通知へのアクセス（LINE・SMS の転送と車載機からの返信）"
                            ),
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
                                    text = str(
                                        "若顯示「受限制的設定」無法開啟：點此前往應用程式資訊 → 右上角 ⋮ → 允許受限制的設定",
                                        "若显示「受限制的设置」无法开启：点此前往应用信息 → 右上角 ⋮ → 允许受限制的设置",
                                        "If blocked by 'Restricted setting': tap here → App Info → top-right ⋮ → Allow restricted settings",
                                        "「制限付き設定」と表示される場合：ここをタップ → アプリ情報 → 右上 ⋮ →「制限付き設定を許可」"
                                    ),
                                    color = colors.primaryText,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                        CompanionPermButton(
                            label = str(
                                "2. 藍牙、來電狀態與前景通知權限",
                                "2. 蓝牙、来电状态与前台通知权限",
                                "2. Bluetooth, Phone Call State & Foreground Notification",
                                "2. Bluetooth・着信状態・通知の権限"
                            ),
                            ok = permissionHealth.hasBluetooth && permissionHealth.hasPhoneState && permissionHealth.hasPostNotifications,
                            onClick = onRequestRuntimePermissions
                        )
                        CompanionPermButton(
                            label = str(
                                "3. 手機聯絡人與來電號碼權限（顯示聯絡人姓名與陌生純號碼）",
                                "3. 手机联系人与来电号码权限（显示联系人姓名与陌生纯号码）",
                                "3. Contacts & Call Log (Show caller name & phone number on Car)",
                                "3. 連絡先＆通話履歴（車載画面に発信者名と電話番号を表示）"
                            ),
                            ok = permissionHealth.hasContacts && permissionHealth.hasCallLog,
                            onClick = onRequestRuntimePermissions
                        )
                        CompanionPermButton(
                            label = str(
                                "4. 忽略電池最佳化（鎖屏後保持連線不斷開）",
                                "4. 忽略电池优化（锁屏后保持连接不断开）",
                                "4. Ignore Battery Optimizations (Keep link alive while screen is off)",
                                "4. バッテリー最適化を無視（画面ロック後も接続を維持）"
                            ),
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
                            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                Text(
                                    str(
                                        "勾選要推送到車機的手機 App",
                                        "勾选要推送到车机的手机 App",
                                        "Select Phone Apps to Forward to Car",
                                        "車載機へ通知転送するアプリを選択"
                                    ),
                                    color = colors.primaryText,
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    str(
                                        "目前已勾選 ${config.companionAllowedPackages.size} 項（預設已勾選常用通訊與簡訊）",
                                        "当前已勾选 ${config.companionAllowedPackages.size} 项（默认已勾选常用通讯与短信）",
                                        "${config.companionAllowedPackages.size} selected (common messaging apps pre-selected)",
                                        "現在 ${config.companionAllowedPackages.size} 件選択中（主要メッセージアプリは標準選択済）"
                                    ),
                                    color = colors.secondaryText,
                                    fontSize = 12.sp
                                )
                            }
                            IconButton(onClick = { showInstalledAppPicker = false }) {
                                Icon(Icons.Rounded.Close, contentDescription = str("關閉", "关闭", "Close", "閉じる"), tint = colors.primaryText)
                            }
                        }

                        OutlinedTextField(
                            value = appSearchQuery,
                            onValueChange = { appSearchQuery = it },
                            singleLine = true,
                            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null, tint = colors.accent) },
                            placeholder = {
                                Text(
                                    str("搜尋手機 App 名稱…", "搜索手机 App 名称…", "Search phone apps…", "アプリ名を検索…"),
                                    fontSize = 13.sp
                                )
                            },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        )

                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedButton(
                                onClick = {
                                    onToggleSyncAllApps(false)
                                    val allPkgs = (installedApps.map { it.packageName } + LauncherConfig.DEFAULT_COMPANION_PACKAGES).distinct()
                                    onSetAllowedPackages(allPkgs)
                                },
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(str("全部打勾", "全部打勾", "Select All", "すべて選択"), fontSize = 12.sp)
                            }
                            OutlinedButton(
                                onClick = {
                                    onToggleSyncAllApps(false)
                                    onSetAllowedPackages(LauncherConfig.DEFAULT_COMPANION_PACKAGES)
                                },
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(str("只勾選預設常用", "只勾选默认常用", "Default Apps Only", "標準アプリのみ選択"), fontSize = 12.sp)
                            }
                            OutlinedButton(
                                onClick = {
                                    onToggleSyncAllApps(false)
                                    onSetAllowedPackages(emptyList())
                                },
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(
                                    str("✕ 全不勾選", "✕ 全不勾选", "✕ Unselect All", "✕ すべて解除"),
                                    color = Color(0xFFEF4444),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
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
                title = str(
                    "📖 手機與車機連線＆多指滑鼠 · 教學",
                    "📖 手机与车机连接＆多指鼠标 · 教程",
                    "📖 Phone–Car Sync & Multi-Touch Pad · Guide",
                    "📖 スマホ連携＆マルチタッチパッド · ガイド"
                ),
                steps = listOf(
                    str(
                        "【重要】請先將「車機盒」與「手機」都更新到最新同一版本 APK！",
                        "【重要】请先将「车机盒」与「手机」都更新到最新同一版本 APK！",
                        "[Important] Update both your Car AI Box and Smartphone to the same latest APK version first!",
                        "【重要】「車載ボックス」と「スマホ」の両方を同じ最新バージョンの APK に更新してください！"
                    ),
                    str(
                        "點擊手機最上方【權限需求】按鈕，開啟「通知讀取權限」（若 vivo／三星／Pixel 顯示受限制設定，先點黃色提示去應用程式資訊右上角 ⋮ 點「允許受限制的設定」）、藍牙與忽略電池最佳化。",
                        "点击手机最上方【权限需求】按钮，开启「通知读取权限」（若 vivo／三星／Pixel 显示受限制设置，先点提示去应用信息右上角 ⋮ 点「允许受限制的设置」）、蓝牙与忽略电池优化。",
                        "Tap [Permissions] at the top of your phone screen and grant Notification Access (if restricted on vivo/Samsung/Pixel, open App Info → top-right ⋮ → Allow restricted settings), Bluetooth, and Ignore Battery Optimizations.",
                        "スマホ上部の【権限設定】から「通知へのアクセス」（制限される場合はアプリ情報 → 右上 ⋮ →「制限付き設定を許可」）・Bluetooth・バッテリー最適化の無視を許可します。"
                    ),
                    str(
                        "車機端同樣點設定最上方【權限需求】，開啟「顯示在其他應用程式上層」、「通知存取權」與「無障礙輔助服務」。",
                        "车机端同样点设置最上方【权限需求】，开启「显示在其他应用上层」、「通知访问权限」与「无障碍辅助服务」。",
                        "On your Car AI Box, tap [Permissions] in Settings and enable Display Over Other Apps, Notification Access, and Accessibility Service.",
                        "車載機側でも設定上部の【権限設定】から「他のアプリの上に表示」「通知へのアクセス」「ユーザー補助サービス」を有効にします。"
                    ),
                    str(
                        "常用通訊與簡訊 App 預設已自動幫您打勾 ✓，您也可隨時點擊「勾選更多手機 App」自由增減要推送到車機的應用程式！",
                        "常用通讯与短信 App 默认已自动帮您打勾 ✓，您也可随时点击「勾选更多手机 App」自由增减要推送到车机的应用！",
                        "Common messaging & SMS apps are pre-checked ✓ by default; tap [More Phone Apps] anytime to customize which apps forward alerts to your Car!",
                        "主要なメッセージ・SMS アプリは標準で選択済み ✓ です。「他のアプリを選択」からいつでも追加・解除できます！"
                    ),
                    str(
                        "點擊【啟動全螢幕滑鼠觸控板】進入純黑 OLED 防鎖屏模式：支援「單指移動／輕點震動確定」、「雙指捏合張開＝車機地圖縮放」、「雙指上下滑＝車機 Google 地圖 3D 俯瞰」、「雙指輕點＝返回」、「三指上滑＝多工／下滑＝首頁」！",
                        "点击【启动全屏鼠标触控板】进入纯黑 OLED 防锁屏模式：支持「单指移动／轻点震动确定」、「双指捏合张开＝车机地图缩放」、「双指上下滑＝车机 Google 地图 3D 俯瞰」、「双指轻点＝返回」、「三指上滑＝多任务／下滑＝首页」！",
                        "Tap [Launch Fullscreen Touchpad] for Pure-Black OLED Keep-Awake mode: 1-finger move/tap with crisp haptic click, 2-finger pinch to zoom Car Maps, 2-finger vertical drag for 3D tilt, 2-finger tap for Back, and 3-finger swipe up/down for Recents/Home!",
                        "【全画面タッチパッドを起動】で純黒 OLED スリープ防止モードへ：1本指移動＆振動クリック、2本指ピンチで車載地図ズーム、2本指上下で 3D 視点切替、2本指タップで戻る、3本指上/下で履歴/ホームに対応！"
                    )
                ),
                highlightNote = str(
                    "💡 離開全螢幕觸控板後，手機即恢復正常休眠；下車斷開藍牙與熱點自動退回 0% 耗電待機模式！",
                    "💡 离开全屏触控板后，手机即恢复正常休眠；下车断开蓝牙与热点自动退回 0% 耗电待机模式！",
                    "💡 Exiting the fullscreen touchpad restores normal screen sleep; disconnecting Car BT & Hotspot returns to zero-drain standby automatically!",
                    "💡 全画面タッチパッドを閉じると通常スリープに戻り、降車して BT・テザリングが切れると自動で省電力待機に戻ります！"
                ),
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
                    Text(
                        str("操控車機滑鼠觸控板", "操控车机鼠标触控板", "Remote Car Mouse Touchpad", "車載マウスタッチパッド操作"),
                        color = colors.primaryText,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        str(
                            "點擊下方執行鈕進入「全螢幕純黑 OLED 防鎖屏觸控模式」",
                            "点击下方执行钮进入「全屏纯黑 OLED 防锁屏触控模式」",
                            "Tap below to enter Fullscreen Pure-Black OLED Keep-Awake Touchpad Mode",
                            "下のボタンで「全画面ピュアブラック OLED スリープ防止モード」を開始"
                        ),
                        color = colors.secondaryText,
                        fontSize = 12.sp
                    )
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
                    text = str(
                        "啟動全螢幕滑鼠觸控板（OLED 純黑 · 防鎖屏）",
                        "启动全屏鼠标触控板（OLED 纯黑 · 防锁屏）",
                        "Launch Fullscreen Touchpad (Pure OLED · Keep Awake)",
                        "全画面タッチパッドを起動（純黒 OLED・スリープ防止）"
                    ),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
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
                Text(
                    str(
                        "開啟車機系統「滑鼠靈敏度」設定（車機跳出畫面）",
                        "开启车机系统「鼠标灵敏度」设置（车机弹出画面）",
                        "Open Car Pointer Speed Settings (Pops up on Car)",
                        "車載システムの「マウス感度」設定を開く（車載画面に表示）"
                    ),
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            if (touchpadTip.isNotBlank()) {
                Text(touchpadTip, color = colors.accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }

            val formattedSpeed = String.format(Locale.US, "%.1fx", sensitivity)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = str(
                        "移速倍率：$formattedSpeed",
                        "移速倍率：$formattedSpeed",
                        "Speed: $formattedSpeed",
                        "移動速度：$formattedSpeed"
                    ),
                    color = colors.primaryText,
                    fontSize = 12.sp,
                    modifier = Modifier.width(115.dp)
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
    var statusBanner by remember { mutableStateOf("") }

    val defaultStatusBanner = str(
        "單指＝游標／點擊震動 · 雙指捏合＝車機縮放 · 雙指上下＝3D俯瞰 · 三指＝多工／首頁",
        "单指＝光标／点击震动 · 双指捏合＝车机缩放 · 双指上下＝3D俯瞰 · 三指＝多任务／首页",
        "1-Finger = Move/Tap · 2-Finger Pinch = Zoom · 2-Finger Up/Down = 3D Tilt · 3-Finger = Recents/Home",
        "1本指＝移動/タップ · 2本指ピンチ＝ズーム · 2本指上下＝3D視点 · 3本指＝履歴/ホーム"
    )

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
                    val targetName = peerName.ifBlank { "AI Box" }
                    Text(
                        text = if (isConnected) {
                            str(
                                "防鎖屏滑鼠已連線 $targetName",
                                "防锁屏鼠标已连接 $targetName",
                                "Keep-Awake Mouse · $targetName",
                                "スリープ防止マウス接続済 · $targetName"
                            )
                        } else {
                            str(
                                "防鎖屏開啟 · 等待車機連線…",
                                "防锁屏开启 · 等待车机连接…",
                                "Keep-Awake On · Waiting for Car…",
                                "スリープ防止 ON · 車載機待機中…"
                            )
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
                                Icon(Icons.Rounded.Remove, contentDescription = str("減速", "减速", "Slower", "遅く"), tint = oledSubtleText, modifier = Modifier.size(16.dp))
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
                                Icon(Icons.Rounded.Add, contentDescription = str("加速", "加速", "Faster", "速く"), tint = oledSubtleText, modifier = Modifier.size(16.dp))
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
                            Icon(Icons.Rounded.Close, contentDescription = str("關閉", "关闭", "Close", "閉じる"), tint = Color(0xFFE2E8F0), modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(str("關閉", "关闭", "Close", "閉じる"), color = Color(0xFFE2E8F0), fontSize = 12.sp, fontWeight = FontWeight.Bold)
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
                                        statusBanner = DxCarI18n.tr(
                                            "✓ 已觸發車機長按",
                                            "✓ 已触发车机长按",
                                            "✓ Car Long-Press Triggered",
                                            "✓ 車載機の長押しを実行"
                                        )
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
                                                    statusBanner = DxCarI18n.tr(
                                                        "✓ 三指上滑 → 車機多工視窗",
                                                        "✓ 三指上滑 → 车机多任务窗口",
                                                        "✓ 3-Finger Swipe Up → Car Recents",
                                                        "✓ 3本指上スワイプ → 車載機の履歴"
                                                    )
                                                    sendTouchAction(TouchpadAction.RECENTS)
                                                } else {
                                                    statusBanner = DxCarI18n.tr(
                                                        "✓ 三指下滑 → 返回車機首頁",
                                                        "✓ 三指下滑 → 返回车机首页",
                                                        "✓ 3-Finger Swipe Down → Car Home",
                                                        "✓ 3本指下スワイプ → 車載ホーム"
                                                    )
                                                    sendTouchAction(TouchpadAction.HOME)
                                                }
                                            } else if (abs(d3x) >= 95f && abs(d3x) > abs(d3y)) {
                                                multiActionTriggered = true
                                                triggerPhoneConfirmVibration(context, localView, heavy = true)
                                                statusBanner = DxCarI18n.tr(
                                                    "✓ 三指橫滑 → 車機首頁地圖↔媒體交換",
                                                    "✓ 三指横滑 → 车机首页地图↔媒体交换",
                                                    "✓ 3-Finger Swipe → Swap Map ↔ Media",
                                                    "✓ 3本指横スワイプ → 地図↔メディア入替"
                                                )
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
                                                            statusBanner = DxCarI18n.tr(
                                                                "🔍 雙指張開 → 車機地圖／畫面放大",
                                                                "🔍 双指张开 → 车机地图／画面放大",
                                                                "🔍 2-Finger Spread → Zoom In",
                                                                "🔍 2本指ピンチアウト → 拡大"
                                                            )
                                                            sendTouchAction(TouchpadAction.TWO_FINGER_SPREAD_OUT)
                                                        } else {
                                                            statusBanner = DxCarI18n.tr(
                                                                "🔍 雙指捏合 → 車機地圖／畫面縮小",
                                                                "🔍 双指捏合 → 车机地图／画面缩小",
                                                                "🔍 2-Finger Pinch → Zoom Out",
                                                                "🔍 2本指ピンチイン → 縮小"
                                                            )
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
                                                            statusBanner = DxCarI18n.tr(
                                                                "🧊 雙指上推 → 車機 Google 地圖 3D 俯瞰",
                                                                "🧊 双指上推 → 车机 Google 地图 3D 俯瞰",
                                                                "🧊 2-Finger Drag Up → 3D Map Tilt",
                                                                "🧊 2本指上スライド → 3D 俯瞰視点"
                                                            )
                                                            sendTouchAction(TouchpadAction.TWO_FINGER_DRAG_UP)
                                                        } else {
                                                            statusBanner = DxCarI18n.tr(
                                                                "🧊 雙指下拉 → 車機 Google 地圖回 2D／下拉",
                                                                "🧊 双指下拉 → 车机 Google 地图回 2D／下拉",
                                                                "🧊 2-Finger Drag Down → 2D View",
                                                                "🧊 2本指下スライド → 2D 視点復帰"
                                                            )
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
                                                            statusBanner = DxCarI18n.tr(
                                                                "⇄ 雙指左滑 → 車機雙指平移",
                                                                "⇄ 双指左滑 → 车机双指平移",
                                                                "⇄ 2-Finger Left → Pan Left",
                                                                "⇄ 2本指左スライド → 2本指移動"
                                                            )
                                                            sendTouchAction(TouchpadAction.TWO_FINGER_DRAG_LEFT)
                                                        } else {
                                                            statusBanner = DxCarI18n.tr(
                                                                "⇄ 雙指右滑 → 車機雙指平移",
                                                                "⇄ 双指右滑 → 车机双指平移",
                                                                "⇄ 2-Finger Right → Pan Right",
                                                                "⇄ 2本指右スライド → 2本指移動"
                                                            )
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
                                        statusBanner = DxCarI18n.tr(
                                            "✓ 已點擊確定",
                                            "✓ 已点击确定",
                                            "✓ Clicked",
                                            "✓ クリック送信"
                                        )
                                        sendTouchAction(TouchpadAction.CLICK)
                                    }
                                } else if (maxFingers == 2 && !multiActionTriggered && elapsed < 420L) {
                                    triggerPhoneConfirmVibration(context, localView, heavy = false)
                                    statusBanner = DxCarI18n.tr(
                                        "← 雙指輕點 → 車機返回",
                                        "← 双指轻点 → 车机返回",
                                        "← 2-Finger Tap → Car Back",
                                        "← 2本指タップ → 車載機の戻る"
                                    )
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
                            text = str(
                                "OLED 全螢幕多指車機觸控板",
                                "OLED 全屏多指车机触控板",
                                "OLED Fullscreen Multi-Touch Car Pad",
                                "OLED 全画面マルチタッチ車載パッド"
                            ),
                            color = Color(0xFF334155),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = statusBanner.ifBlank { defaultStatusBanner },
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
                        contentDescription = str("上捲", "上滚", "Scroll Up", "上スクロール"),
                        tint = Color(0xFF475569),
                        modifier = Modifier
                            .size(24.dp)
                            .clickable {
                                triggerPhoneConfirmVibration(context, localView, heavy = false)
                                sendTouchAction(TouchpadAction.SCROLL_UP)
                            }
                    )
                    Text(
                        text = str("捲\n動\n條", "滚\n动\n条", "S\nC\nR\nO\nL\nL", "ス\nク\nロ\n｜\nル"),
                        color = Color(0xFF334155),
                        fontSize = 11.sp,
                        lineHeight = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Icon(
                        imageVector = Icons.Rounded.KeyboardArrowDown,
                        contentDescription = str("下捲", "下滚", "Scroll Down", "下スクロール"),
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
                            statusBanner = DxCarI18n.tr(
                                "✓ 已點擊確定",
                                "✓ 已点击确定",
                                "✓ Clicked",
                                "✓ クリック送信"
                            )
                            sendTouchAction(TouchpadAction.CLICK)
                        }
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 4.dp)) {
                        Text(
                            str("確定點擊", "确定点击", "Tap / Click", "決定クリック"),
                            color = oledAccent,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
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
                            statusBanner = DxCarI18n.tr("← 車機返回", "← 车机返回", "← Car Back", "← 車載機の戻る")
                            sendTouchAction(TouchpadAction.BACK)
                        }
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null, tint = Color(0xFFCBD5E1), modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(3.dp))
                        Text(
                            str("返回", "返回", "Back", "戻る"),
                            color = Color(0xFFCBD5E1),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
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
                            statusBanner = DxCarI18n.tr("⌂ 車機首頁", "⌂ 车机首页", "⌂ Car Home", "⌂ 車載ホーム")
                            sendTouchAction(TouchpadAction.HOME)
                        }
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.Home, contentDescription = null, tint = Color(0xFFCBD5E1), modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(3.dp))
                        Text(
                            str("首頁", "首页", "Home", "ホーム"),
                            color = Color(0xFFCBD5E1),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
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
                            statusBanner = DxCarI18n.tr(
                                "⚙️ 已在車機開啟靈敏度設定",
                                "⚙️ 已在车机开启灵敏度设置",
                                "⚙️ Opened Car Pointer Speed Settings",
                                "⚙️ 車載画面に感度設定を表示"
                            )
                            onOpenCarPointerSpeedSettings()
                        }
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.Settings, contentDescription = null, tint = oledSubtleText, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(3.dp))
                        Text(
                            str("車機靈敏度", "车机灵敏度", "Car Speed", "車載感度"),
                            color = oledSubtleText,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
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
            text = if (ok) str("已授權", "已授权", "Granted", "許可済み") else str("前往開啟", "前往开启", "Enable", "設定を開く"),
            color = if (ok) colors.statusConnected else colors.accent,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

private fun List<Pair<List<String>, String>>.flattenPairs(): Set<String> =
    flatMap { it.first }.toSet()

package com.dxcar.launcher.system

import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.Toast
import com.dxcar.launcher.DxCarApp
import com.dxcar.launcher.accessibility.DxCarAccessibilityService
import com.dxcar.launcher.data.DeviceFormFactor
import com.dxcar.launcher.data.DeviceRole
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Forces the Car AI Box's system Wi-Fi OFF and back ON immediately upon detecting that the user's
 * smartphone has connected over Bluetooth, kicking Android's Wi-Fi scanner out of idle backoff so
 * it locks onto the phone's Wi-Fi hotspot in seconds!
 *
 * Uses a 4-stage cascade so it works across all Android 9–15 AI Boxes:
 * 1. Direct `WifiManager.setWifiEnabled(false -> true)` (Android 9 & OEM-patched AI Box ROMs)
 * 2. Privileged / Root shell (`cmd wifi set-wifi-enabled` / `su -c svc wifi`) for rooted AI Boxes
 * 3. Android 10+ `Settings.Panel.ACTION_WIFI` + `DxCarAccessibilityService` automatic Switch OFF -> ON -> Auto-Close
 * 4. Active `disconnect()` + `startScan()` + `reconnect()` + `reassociate()` kick fallback
 */
object WifiHotspotFastRestarter {

    private val scope by lazy { CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate) }
    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }
    private val isCycling = AtomicBoolean(false)

    @Volatile
    private var lastAutoTriggerAtMs: Long = 0L

    private val _statusEvent = MutableStateFlow<String?>(null)
    val statusEvent: StateFlow<String?> = _statusEvent.asStateFlow()

    fun consumeStatusEvent() {
        _statusEvent.value = null
    }

    /**
     * Called automatically whenever the AI Box detects a phone Bluetooth connection transition.
     */
    fun onPhoneBluetoothConnected(context: Context, peerName: String = "") {
        val app = DxCarApp.instance ?: return
        val cfg = app.preferences.config.value

        // Strictly run ONLY on the Car AI Box / Tablet Launcher role — NEVER on the user's Smartphone!
        if (cfg.deviceRole != DeviceRole.AI_BOX_LAUNCHER) return
        if (DeviceFormFactor.isCompactPhoneScreen(context.resources.configuration)) return
        if (!cfg.autoCycleWifiOnBtConnect) return

        val now = System.currentTimeMillis()
        // 28-second debounce so simultaneous ACL + HFP + A2DP broadcasts from the same phone connect only cycle once
        if (now - lastAutoTriggerAtMs < 28_000L) return
        lastAutoTriggerAtMs = now

        val label = peerName.trim().ifBlank { "手機" }
        forceCycleSystemWifi(
            context = context.applicationContext,
            triggerReason = "偵測到「$label」連上藍牙"
        )
    }

    /**
     * Executes the forced Wi-Fi OFF -> ON -> Active Scan & Reconnect sequence.
     */
    fun forceCycleSystemWifi(
        context: Context,
        triggerReason: String = "手動觸發"
    ) {
        if (!isCycling.compareAndSet(false, true)) return

        val appCtx = context.applicationContext
        val wifiManager = appCtx.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        if (wifiManager == null) {
            isCycling.set(false)
            return
        }

        scope.launch {
            try {
                val wasInitiallyEnabled = isWifiCurrentlyEnabled(wifiManager)
                if (!wasInitiallyEnabled) {
                    notifyStatus(appCtx, "📶 $triggerReason：偵測到 Wi-Fi 為關閉狀態，正在自動開啟並連入手機熱點…")
                    ensureWifiTurnedOnEveryWay(appCtx, wifiManager)
                    delay(500L)
                    kickHotspotScanAndReconnect(wifiManager)
                    notifyStatus(appCtx, "✓ 已自動開啟系統 Wi-Fi，正在搜尋並連入手機熱點！")
                    return@launch
                }

                notifyStatus(appCtx, "📶 $triggerReason：正在強制關閉再開啟 Wi-Fi 以加速連入手機熱點…")

                // ==================== STAGE 1: Direct WifiManager.setWifiEnabled ====================
                @Suppress("DEPRECATION")
                runCatching { wifiManager.setWifiEnabled(false) }
                val wifiNowOffStage1 = waitForWifiState(wifiManager, targetEnabled = false, timeoutMs = 950L)
                if (wifiNowOffStage1) {
                    try {
                        delay(1_200L)
                    } finally {
                        ensureWifiTurnedOnEveryWay(appCtx, wifiManager)
                    }
                    delay(500L)
                    kickHotspotScanAndReconnect(wifiManager)
                    notifyStatus(appCtx, "✓ 已重啟系統 Wi-Fi，正在高速搜尋並連入手機熱點！")
                    return@launch
                }

                // ==================== STAGE 2: AI Box Shell (cmd wifi / su svc wifi) ====================
                val shellInvoked = withContext(Dispatchers.IO) { tryShellSetWifi(enable = false) }
                val wifiNowOffStage2 = shellInvoked &&
                    waitForWifiState(wifiManager, targetEnabled = false, timeoutMs = 950L)
                if (wifiNowOffStage2) {
                    try {
                        delay(1_200L)
                    } finally {
                        withContext(Dispatchers.IO) { tryShellSetWifi(enable = true) }
                        ensureWifiTurnedOnEveryWay(appCtx, wifiManager)
                    }
                    delay(500L)
                    kickHotspotScanAndReconnect(wifiManager)
                    notifyStatus(appCtx, "✓ 已透過系統指令重啟 Wi-Fi，正在加速連入手機熱點！")
                    return@launch
                }

                // ==================== STAGE 3: Android 10+ Settings Panel + Accessibility Auto-Switch ====================
                val acc = DxCarAccessibilityService.instance
                if (acc != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val panelOpened = openWifiSlicePanel(appCtx)
                    if (panelOpened) {
                        delay(650L)
                        var toggledOff = false
                        try {
                            if (isWifiCurrentlyEnabled(wifiManager)) {
                                val clicked = acc.clickWifiMasterSwitchInSettings(expectCurrentlyChecked = true)
                                toggledOff = clicked &&
                                    waitForWifiState(wifiManager, targetEnabled = false, timeoutMs = 1_100L)
                            } else {
                                toggledOff = true
                            }
                            if (toggledOff) {
                                delay(1_250L)
                            }
                        } finally {
                            // Guarantee Wi-Fi is turned back ON before closing the panel!
                            repeat(3) {
                                if (!isWifiCurrentlyEnabled(wifiManager)) {
                                    acc.clickWifiMasterSwitchInSettings(expectCurrentlyChecked = false)
                                    delay(550L)
                                }
                            }
                            ensureWifiTurnedOnDirect(wifiManager)
                            delay(350L)
                            acc.goBack()
                        }

                        if (toggledOff) {
                            delay(500L)
                            kickHotspotScanAndReconnect(wifiManager)
                            notifyStatus(appCtx, "✓ 已自動關閉並重啟系統 Wi-Fi，正在加速連入手機熱點！")
                            return@launch
                        }
                    }
                }

                // ==================== STAGE 4: Disconnect + Active Scan + Reconnect Kick ====================
                kickHotspotScanAndReconnect(wifiManager, forceDisconnectFirst = true)
                val hint = if (acc == null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    "📶 已強制重連並掃描熱點（開啟「無障礙服務」可解鎖 Android 10+ 全自動關閉重開 Wi-Fi）"
                } else {
                    "📶 已強制重掃描並重連手機 Wi-Fi 熱點！"
                }
                notifyStatus(appCtx, hint)
            } finally {
                DxCarApp.instance?.systemStatusMonitor?.refreshNetworkState()
                isCycling.set(false)
            }
        }
    }

    private fun isWifiCurrentlyEnabled(wifiManager: WifiManager): Boolean {
        return runCatching { wifiManager.isWifiEnabled }.getOrDefault(true)
    }

    private suspend fun waitForWifiState(
        wifiManager: WifiManager,
        targetEnabled: Boolean,
        timeoutMs: Long
    ): Boolean {
        val stepMs = 220L
        var elapsed = 0L
        while (elapsed < timeoutMs) {
            if (isWifiCurrentlyEnabled(wifiManager) == targetEnabled) return true
            delay(stepMs)
            elapsed += stepMs
        }
        return isWifiCurrentlyEnabled(wifiManager) == targetEnabled
    }

    private suspend fun ensureWifiTurnedOnDirect(wifiManager: WifiManager) {
        repeat(4) {
            if (isWifiCurrentlyEnabled(wifiManager)) return
            @Suppress("DEPRECATION")
            runCatching { wifiManager.setWifiEnabled(true) }
            delay(350L)
        }
    }

    private suspend fun ensureWifiTurnedOnEveryWay(context: Context, wifiManager: WifiManager) {
        ensureWifiTurnedOnDirect(wifiManager)
        if (isWifiCurrentlyEnabled(wifiManager)) return

        withContext(Dispatchers.IO) { tryShellSetWifi(enable = true) }
        if (waitForWifiState(wifiManager, targetEnabled = true, timeoutMs = 800L)) return

        val acc = DxCarAccessibilityService.instance
        if (acc != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (openWifiSlicePanel(context)) {
                delay(650L)
                try {
                    repeat(3) {
                        if (!isWifiCurrentlyEnabled(wifiManager)) {
                            acc.clickWifiMasterSwitchInSettings(expectCurrentlyChecked = false)
                            delay(550L)
                        }
                    }
                } finally {
                    delay(300L)
                    acc.goBack()
                }
            }
        }
    }

    @Suppress("DEPRECATION")
    private suspend fun kickHotspotScanAndReconnect(
        wifiManager: WifiManager,
        forceDisconnectFirst: Boolean = false
    ) {
        if (forceDisconnectFirst) {
            runCatching { wifiManager.disconnect() }
            delay(420L)
        }
        runCatching { wifiManager.startScan() }
        delay(250L)
        runCatching { wifiManager.reconnect() }
        runCatching { wifiManager.reassociate() }
    }

    private fun openWifiSlicePanel(context: Context): Boolean {
        val candidates = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                add(Intent(Settings.Panel.ACTION_WIFI))
                add(Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY))
            }
            add(Intent(Settings.ACTION_WIFI_SETTINGS))
        }
        for (intent in candidates) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (runCatching { context.startActivity(intent); true }.getOrDefault(false)) {
                return true
            }
        }
        return false
    }

    private suspend fun tryShellSetWifi(enable: Boolean): Boolean {
        val stateWord = if (enable) "enabled" else "disabled"
        val svcWord = if (enable) "enable" else "disable"
        val commands = listOf(
            arrayOf("cmd", "wifi", "set-wifi-enabled", stateWord),
            arrayOf("svc", "wifi", svcWord),
            arrayOf("su", "-c", "svc wifi $svcWord")
        )
        for (cmd in commands) {
            val ok = withTimeoutOrNull(950L) {
                runCatching {
                    val proc = ProcessBuilder(*cmd)
                        .redirectErrorStream(true)
                        .start()
                    val finished = proc.waitFor(850L, TimeUnit.MILLISECONDS)
                    if (!finished) {
                        proc.destroyForcibly()
                        false
                    } else {
                        proc.exitValue() == 0
                    }
                }.getOrDefault(false)
            } ?: false
            if (ok) return true
        }
        return false
    }

    private fun notifyStatus(context: Context, message: String) {
        _statusEvent.value = message
        mainHandler.post {
            runCatching { Toast.makeText(context, message, Toast.LENGTH_SHORT).show() }
        }
    }
}

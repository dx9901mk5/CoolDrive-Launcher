package com.dxcar.launcher

import android.Manifest
import android.app.role.RoleManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.speech.RecognizerIntent
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.dxcar.launcher.comms.BluetoothCompanionService
import com.dxcar.launcher.comms.CarMessageItem
import com.dxcar.launcher.data.SplitLaunchEngine
import com.dxcar.launcher.data.SplitRatioPreset
import com.dxcar.launcher.data.SplitScreenShortcut
import com.dxcar.launcher.i18n.DxCarI18n
import com.dxcar.launcher.system.PermissionHelper
import com.dxcar.launcher.system.StockSplitScreenBridge
import com.dxcar.launcher.system.SystemStatusMonitor
import com.dxcar.launcher.ui.DxCarRootScreen
import com.dxcar.launcher.ui.EmbeddedYoutubeBridge
import com.dxcar.launcher.voice.GeminiVoiceNavController
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.UUID

class MainActivity : ComponentActivity() {

    private val externalNavCommand = mutableStateOf<String?>(null)
    private val externalHudToast = mutableStateOf<String?>(null)
    private var pendingVoiceReplyTarget: CarMessageItem? = null

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        val app = application as DxCarApp
        app.locationTracker.startIfPermitted()
        app.systemStatusMonitor.refreshAll()
        if (app.preferences.config.value.companionAutoConnect) {
            if (BluetoothCompanionService.shouldPhoneBridgeRunNow(this)) {
                BluetoothCompanionService.startOrReconnect(this)
            } else {
                BluetoothCompanionService.markStandby(this)
            }
        }
        PermissionHelper.requestNotificationListenerRebind(this)
    }

    private val homeRoleLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        (application as DxCarApp).installedAppsRepository.refreshHomeLaunchers()
    }

    private val voiceReplyLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val target = pendingVoiceReplyTarget
        pendingVoiceReplyTarget = null
        if (result.resultCode == RESULT_OK && target != null) {
            val spoken = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
                ?.trim()
            if (!spoken.isNullOrBlank()) {
                (application as DxCarApp).notificationCenter.sendQuickReply(target, spoken)
            }
        }
    }

    private val voiceNavLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val spoken = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
                ?.trim()
            if (!spoken.isNullOrBlank()) {
                GeminiVoiceNavController.handleSpokenNavigationTranscript(this, spoken) { msg ->
                    externalHudToast.value = msg
                }
            }
        }
    }

    @Suppress("DEPRECATION")
    private val stockShortcutConfigLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK && result.data != null) {
            val data = result.data!!
            val shortcutIntent = data.getParcelableExtra<Intent>(Intent.EXTRA_SHORTCUT_INTENT)
            val name = data.getStringExtra(Intent.EXTRA_SHORTCUT_NAME)?.trim().orEmpty()
                .ifBlank { "原車系統捷徑" }
            if (shortcutIntent != null) {
                val uriStr = shortcutIntent.toUri(Intent.URI_INTENT_SCHEME)
                val pkg = shortcutIntent.`package` ?: shortcutIntent.component?.packageName
                    ?: "com.google.android.apps.maps"
                val sc = SplitScreenShortcut(
                    id = UUID.randomUUID().toString().take(8),
                    leftPackage = pkg,
                    rightPackage = pkg,
                    ratio = SplitRatioPreset.R_5_5,
                    engine = SplitLaunchEngine.STOCK_LAUNCHER_SHORTCUT,
                    label = name,
                    stockIntentUri = uriStr
                )
                (application as DxCarApp).preferences.addSplitShortcut(sc)
                externalHudToast.value = "✓ 已成功匯入原車捷徑：$name"
            }
        }
    }

    private val stockShortcutPickLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK && result.data != null) {
            runCatching { stockShortcutConfigLauncher.launch(result.data!!) }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        hideSystemBarsForCar()

        // Home Launcher should never finish when Back is pressed
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    externalNavCommand.value = null
                }
            }
        )

        handleIncomingIntent(intent)

        val app = application as DxCarApp
        setContent {
            val liveCfg by app.preferences.config.collectAsState()
            LaunchedEffect(liveCfg.prefer60FpsEnabled) {
                applyPreferredRefreshRate(liveCfg.prefer60FpsEnabled)
            }
            LaunchedEffect(liveCfg.deviceRole) {
                com.dxcar.launcher.comms.CarDialerBridgeActivity.syncComponentEnabled(this@MainActivity, liveCfg.deviceRole)
            }
            DxCarRootScreen(
                app = app,
                externalNavCommand = externalNavCommand.value,
                externalHudMessage = externalHudToast.value,
                onExternalNavConsumed = { externalNavCommand.value = null },
                onExternalHudConsumed = { externalHudToast.value = null },
                onRequestRuntimePermissions = ::requestNeededRuntimePermissions,
                onRequestSetDefaultHome = ::requestSetDefaultHomeLauncher,
                onStartVoiceReply = ::launchVoiceReply,
                onStartGeminiVoiceNav = ::launchGeminiVoiceNavigation,
                onPickStockSystemShortcut = ::launchStockSystemShortcutPicker
            )
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (EmbeddedYoutubeBridge.handleMediaKeyEvent(event)) {
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        val app = application as DxCarApp
        app.setLauncherForeground(true)
        com.dxcar.launcher.comms.CarDialerBridgeActivity.syncComponentEnabled(this, app.preferences.config.value.deviceRole)
        app.systemStatusMonitor.refreshAll()
        app.locationTracker.startIfPermitted()
        app.mediaControlBridge.refreshSessions()
        app.installedAppsRepository.refreshHomeLaunchers()
        PermissionHelper.requestNotificationListenerRebind(this)
        applyPreferredRefreshRate(app.preferences.config.value.prefer60FpsEnabled)
        if (app.preferences.config.value.companionAutoConnect) {
            if (BluetoothCompanionService.shouldPhoneBridgeRunNow(this)) {
                BluetoothCompanionService.startOrReconnect(this)
            } else {
                BluetoothCompanionService.markStandby(this)
            }
        }
        hideSystemBarsForCar()
    }

    override fun onPause() {
        super.onPause()
        (application as DxCarApp).setLauncherForeground(false)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        val isShareText = intent?.action == Intent.ACTION_SEND && intent.type == "text/plain"
        val isGeoView = intent?.action == Intent.ACTION_VIEW && intent.data?.scheme.equals("geo", ignoreCase = true)
        if (isShareText || isGeoView) {
            val combined = if (isShareText) {
                val sharedText = intent?.getStringExtra(Intent.EXTRA_TEXT)?.trim().orEmpty()
                val sharedSubject = intent?.getStringExtra(Intent.EXTRA_SUBJECT)?.trim().orEmpty()
                listOf(sharedSubject, sharedText)
                    .filter { it.isNotBlank() }
                    .distinct()
                    .joinToString(" ")
                    .trim()
            } else {
                val geoData = intent?.data
                val qParam = geoData?.getQueryParameter("q")?.trim().orEmpty()
                qParam.ifBlank { geoData?.schemeSpecificPart?.trim().orEmpty() }
            }
            if (combined.isNotBlank()) {
                val app = application as DxCarApp
                val cfg = app.preferences.config.value
                val curLoc = app.locationTracker.location.value
                val biasLat = if (curLoc.latitude != 0.0) curLoc.latitude else cfg.lastKnownLat
                val biasLon = if (curLoc.longitude != 0.0) curLoc.longitude else cfg.lastKnownLon
                val isPhoneRole = cfg.deviceRole == com.dxcar.launcher.data.DeviceRole.PHONE_COMPANION
                val directAddress = isGeoView || com.dxcar.launcher.map.SharedLocationResolver.isDirectMapOrAddress(combined)

                if (isPhoneRole) {
                    BluetoothCompanionService.startOrReconnect(this)
                }

                if (directAddress) {
                    android.widget.Toast.makeText(
                        this,
                        "📍 已偵測地圖地址，直接發送車機啟動地圖搜尋…",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main.immediate).launch {
                        val resolved = com.dxcar.launcher.map.SharedLocationResolver.resolveSharedText(
                            context = this@MainActivity,
                            navEngine = app.builtInNavEngine,
                            rawText = combined,
                            biasLat = biasLat,
                            biasLon = biasLon
                        )
                        if (isPhoneRole) {
                            val sent = BluetoothCompanionService.sendPacketFromPhone(
                                com.dxcar.launcher.comms.BridgePacket(
                                    type = com.dxcar.launcher.comms.PacketType.SHARED_LOCATION,
                                    id = UUID.randomUUID().toString(),
                                    sender = resolved.label,
                                    text = resolved.address,
                                    latitude = resolved.latitude,
                                    longitude = resolved.longitude
                                )
                            )
                            val msg = if (sent) {
                                "✓ 已傳送至車機並啟動地圖搜尋：${resolved.label}"
                            } else {
                                "📍 已排入傳送（連上車機立即啟動搜尋）：${resolved.label}"
                            }
                            android.widget.Toast.makeText(this@MainActivity, msg, android.widget.Toast.LENGTH_LONG).show()
                            externalHudToast.value = msg
                            kotlinx.coroutines.delay(500L)
                            runCatching { moveTaskToBack(true) }
                        } else {
                            app.notificationCenter.postSharedLocation(
                                com.dxcar.launcher.comms.SharedLocationItem(
                                    id = UUID.randomUUID().toString(),
                                    label = resolved.label,
                                    address = resolved.address,
                                    latitude = resolved.latitude,
                                    longitude = resolved.longitude
                                )
                            )
                        }
                    }
                } else {
                    // NOT a direct map link / street address -> trigger Gemini to analyze!
                    android.widget.Toast.makeText(
                        this,
                        "✨ 非標準地址，已交由 Gemini 進行判斷…",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main.immediate).launch {
                        val apiKey = cfg.geminiApiKey.trim()
                        val geminiResult = if (apiKey.isNotBlank()) {
                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                GeminiVoiceNavController.analyzeSharedTextWithGemini(
                                    apiKey = apiKey,
                                    sharedText = combined,
                                    curLat = biasLat,
                                    curLon = biasLon
                                )
                            }
                        } else {
                            null
                        }

                        if (geminiResult != null && geminiResult.isPlaceOrAddress &&
                            (geminiResult.address.isNotBlank() || geminiResult.label.isNotBlank())
                        ) {
                            val queryForGeo = geminiResult.address.ifBlank { geminiResult.label }
                            val resolved = if (geminiResult.lat != 0.0 && geminiResult.lon != 0.0) {
                                com.dxcar.launcher.map.ResolvedSharedLocation(
                                    label = geminiResult.label.ifBlank { queryForGeo }.take(64),
                                    address = queryForGeo.take(140),
                                    latitude = geminiResult.lat,
                                    longitude = geminiResult.lon
                                )
                            } else {
                                com.dxcar.launcher.map.SharedLocationResolver.resolveSharedText(
                                    context = this@MainActivity,
                                    navEngine = app.builtInNavEngine,
                                    rawText = queryForGeo,
                                    biasLat = biasLat,
                                    biasLon = biasLon
                                )
                            }
                            if (isPhoneRole) {
                                val sent = BluetoothCompanionService.sendPacketFromPhone(
                                    com.dxcar.launcher.comms.BridgePacket(
                                        type = com.dxcar.launcher.comms.PacketType.SHARED_LOCATION,
                                        id = UUID.randomUUID().toString(),
                                        sender = resolved.label,
                                        text = resolved.address,
                                        latitude = resolved.latitude,
                                        longitude = resolved.longitude
                                    )
                                )
                                val msg = if (sent) {
                                    "✨ Gemini 已辨識地點並送往車機搜尋：${resolved.label}"
                                } else {
                                    "✨ Gemini 已辨識「${resolved.label}」（連上車機自動搜尋）"
                                }
                                android.widget.Toast.makeText(this@MainActivity, msg, android.widget.Toast.LENGTH_LONG).show()
                                externalHudToast.value = msg
                                kotlinx.coroutines.delay(500L)
                                runCatching { moveTaskToBack(true) }
                            } else {
                                app.notificationCenter.postSharedLocation(
                                    com.dxcar.launcher.comms.SharedLocationItem(
                                        id = UUID.randomUUID().toString(),
                                        label = resolved.label,
                                        address = resolved.address,
                                        latitude = resolved.latitude,
                                        longitude = resolved.longitude
                                    )
                                )
                            }
                        } else {
                            // Hand off directly to Google Gemini App to interpret non-address content!
                            val geminiPrompt = "請幫我判斷這段內容是否包含地點或地址；若是地點請整理出完整地址，若否請直接解析內容：\n$combined"
                            val launchedGemini = GeminiVoiceNavController.launchGeminiVoiceAssistantDirect(
                                context = this@MainActivity,
                                query = geminiPrompt
                            )
                            val msg = when {
                                launchedGemini -> "✨ 已交由 Google Gemini 進行判斷"
                                geminiResult != null && geminiResult.summary.isNotBlank() -> "✨ Gemini：${geminiResult.summary}"
                                else -> "⚠️ 未安裝 Google Gemini App，且未設定 Gemini API Key"
                            }
                            android.widget.Toast.makeText(this@MainActivity, msg, android.widget.Toast.LENGTH_LONG).show()
                            externalHudToast.value = msg
                        }
                    }
                }
            }
            return
        }
        if (intent?.action == ACTION_TRIGGER_BT_RECONNECT) {
            BluetoothCompanionService.forceReconnect(this)
            val targetName = (application as DxCarApp).preferences.config.value.companionTargetDeviceName
            val tip = if (targetName.isNotBlank()) {
                DxCarI18n.tr(
                    "🔄 正在重新觸發藍牙連線：$targetName",
                    "🔄 正在重新触发蓝牙连接：$targetName",
                    "🔄 Reconnecting Bluetooth: $targetName",
                    "🔄 Bluetooth 再接続中：$targetName"
                )
            } else {
                DxCarI18n.tr(
                    "🔄 正在重新觸發車機藍牙連線…",
                    "🔄 正在重新触发车机蓝牙连接…",
                    "🔄 Reconnecting Car Bluetooth…",
                    "🔄 車載 Bluetooth に再接続中…"
                )
            }
            android.widget.Toast.makeText(this, tip, android.widget.Toast.LENGTH_SHORT).show()
            externalHudToast.value = tip
            return
        }
        val action = intent?.getStringExtra(EXTRA_ACTION)
        if (!action.isNullOrBlank()) {
            if (action == EXTRA_START_VOICE_NAV) {
                launchGeminiVoiceNavigation()
            } else {
                externalNavCommand.value = action
            }
        }
    }

    private fun requestNeededRuntimePermissions() {
        val perms = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.ANSWER_PHONE_CALLS,
            Manifest.permission.CALL_PHONE,
            Manifest.permission.READ_CONTACTS
        )
        if (BuildConfig.ENABLE_GITHUB_AUTO_UPDATE) {
            // GitHub Direct flavor declares READ_CALL_LOG so Android 9+ delivers incoming phone numbers!
            perms += Manifest.permission.READ_CALL_LOG
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            perms += Manifest.permission.BLUETOOTH_CONNECT
            perms += Manifest.permission.BLUETOOTH_SCAN
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms += Manifest.permission.POST_NOTIFICATIONS
        }
        permissionLauncher.launch(perms.toTypedArray())
    }

    private fun requestSetDefaultHomeLauncher() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = getSystemService(RoleManager::class.java)
            if (roleManager != null &&
                roleManager.isRoleAvailable(RoleManager.ROLE_HOME) &&
                !roleManager.isRoleHeld(RoleManager.ROLE_HOME)
            ) {
                val roleIntent = roleManager.createRequestRoleIntent(RoleManager.ROLE_HOME)
                val launched = runCatching {
                    homeRoleLauncher.launch(roleIntent)
                    true
                }.getOrDefault(false)
                if (launched) return
            }
        }
        SystemStatusMonitor.openHomeLauncherSettings(this)
    }

    private fun launchVoiceReply(target: CarMessageItem) {
        pendingVoiceReplyTarget = target
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, DxCarI18n.speechLanguageTag())
            putExtra(
                RecognizerIntent.EXTRA_PROMPT,
                DxCarI18n.tr(
                    "請說出要回覆給 ${target.sender} 的內容…",
                    "请说出要回复给 ${target.sender} 的内容…",
                    "Speak your reply to ${target.sender}…",
                    "${target.sender} さんへの返信を話してください…"
                )
            )
        }
        runCatching { voiceReplyLauncher.launch(intent) }
    }

    private fun launchGeminiVoiceNavigation() {
        val app = application as DxCarApp
        val mode = app.preferences.config.value.geminiVoiceMode
        val speechIntent = GeminiVoiceNavController.buildSpeechRecognizerIntentOrOpenGemini(
            context = this,
            mode = mode,
            onStatusToast = { msg -> externalHudToast.value = msg }
        )
        if (speechIntent != null) {
            runCatching { voiceNavLauncher.launch(speechIntent) }
                .onFailure {
                    GeminiVoiceNavController.launchGeminiVoiceAssistantDirect(this, null)
                }
        }
    }

    private fun launchStockSystemShortcutPicker() {
        runCatching {
            stockShortcutPickLauncher.launch(StockSplitScreenBridge.buildSystemShortcutPickerIntent())
        }.onFailure {
            externalHudToast.value = DxCarI18n.tr(
                "⚠️ 此車機系統未提供外部捷徑挑選清單",
                "⚠️ 此车机系统未提供外部快捷方式选择列表",
                "⚠️ System shortcut picker is not available on this device",
                "⚠️ この端末ではシステムショートカット選択を利用できません"
            )
        }
    }

    private fun applyPreferredRefreshRate(prefer60Fps: Boolean) {
        runCatching {
            val lp = window.attributes
            if (prefer60Fps) {
                @Suppress("DEPRECATION")
                val modes = (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) display else windowManager.defaultDisplay)
                    ?.supportedModes.orEmpty()
                val best60Plus = modes.filter { it.refreshRate >= 59.5f }.maxByOrNull { it.refreshRate }
                if (best60Plus != null) {
                    lp.preferredDisplayModeId = best60Plus.modeId
                }
                lp.preferredRefreshRate = 60f
            } else {
                lp.preferredDisplayModeId = 0
                lp.preferredRefreshRate = 0f
            }
            window.attributes = lp
        }
    }

    private fun hideSystemBarsForCar() {
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
    }

    companion object {
        const val ACTION_TRIGGER_BT_RECONNECT = "com.dxcar.launcher.ACTION_TRIGGER_BT_RECONNECT"
        const val EXTRA_ACTION = "dxcar_extra_action"
        const val EXTRA_GO_DASHBOARD = "go_dashboard"
        const val EXTRA_OPEN_DRAWER = "open_drawer"
        const val EXTRA_OPEN_YOUTUBE = "open_youtube"
        const val EXTRA_OPEN_YOUTUBE_HANDOFF = "open_youtube_handoff"
        const val EXTRA_OPEN_QUICK_PANEL = "open_quick_panel"
        const val EXTRA_OPEN_NOTIFICATIONS = "open_notifications"
        const val EXTRA_OPEN_SETTINGS = "open_settings"
        const val EXTRA_START_VOICE_NAV = "start_voice_nav"

        /**
         * Cleanly restarts DX-Car Launcher after switching Plan A <-> Plan B so all overlay/split states
         * boot fresh with zero map flickering or leftover permanent Dock shift.
         */
        fun restartLauncherCleanly(context: android.content.Context) {
            val appCtx = context.applicationContext
            com.dxcar.launcher.overlay.PersistentDockOverlayService.stop(appCtx)
            val restartIntent = Intent.makeRestartActivityTask(
                android.content.ComponentName(appCtx, MainActivity::class.java)
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
            runCatching { appCtx.startActivity(restartIntent) }
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                android.os.Process.killProcess(android.os.Process.myPid())
            }, 220L)
        }
    }
}

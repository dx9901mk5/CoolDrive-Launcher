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
            Manifest.permission.READ_CONTACTS
        )
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
        const val EXTRA_ACTION = "dxcar_extra_action"
        const val EXTRA_GO_DASHBOARD = "go_dashboard"
        const val EXTRA_OPEN_DRAWER = "open_drawer"
        const val EXTRA_OPEN_YOUTUBE = "open_youtube"
        const val EXTRA_OPEN_QUICK_PANEL = "open_quick_panel"
        const val EXTRA_OPEN_NOTIFICATIONS = "open_notifications"
        const val EXTRA_OPEN_SETTINGS = "open_settings"
        const val EXTRA_START_VOICE_NAV = "start_voice_nav"
    }
}

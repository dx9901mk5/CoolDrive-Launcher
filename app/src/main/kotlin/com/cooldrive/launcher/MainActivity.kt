package com.cooldrive.launcher

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.speech.RecognizerIntent
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.mutableStateOf
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.cooldrive.launcher.comms.CarMessageItem
import com.cooldrive.launcher.ui.CoolDriveRootScreen
import java.util.Locale

class MainActivity : ComponentActivity() {

    private val externalNavCommand = mutableStateOf<String?>(null)
    private var pendingVoiceReplyTarget: CarMessageItem? = null

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        val app = application as CoolDriveApp
        app.locationTracker.startIfPermitted()
        app.systemStatusMonitor.refreshAll()
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
                (application as CoolDriveApp).notificationCenter.sendQuickReply(target, spoken)
            }
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

        val app = application as CoolDriveApp
        setContent {
            CoolDriveRootScreen(
                app = app,
                externalNavCommand = externalNavCommand.value,
                onExternalNavConsumed = { externalNavCommand.value = null },
                onRequestRuntimePermissions = ::requestNeededRuntimePermissions,
                onStartVoiceReply = ::launchVoiceReply
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        val app = application as CoolDriveApp
        app.setLauncherForeground(true)
        app.systemStatusMonitor.refreshAll()
        app.locationTracker.startIfPermitted()
        app.mediaControlBridge.refreshSessions()
        hideSystemBarsForCar()
    }

    override fun onPause() {
        super.onPause()
        (application as CoolDriveApp).setLauncherForeground(false)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        val action = intent?.getStringExtra(EXTRA_ACTION)
        if (!action.isNullOrBlank()) {
            externalNavCommand.value = action
        }
    }

    private fun requestNeededRuntimePermissions() {
        val perms = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.READ_PHONE_STATE
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

    private fun launchVoiceReply(target: CarMessageItem) {
        pendingVoiceReplyTarget = target
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.TRADITIONAL_CHINESE.toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_PROMPT, "請說出要回覆給 ${target.sender} 的內容…")
        }
        runCatching { voiceReplyLauncher.launch(intent) }
    }

    private fun hideSystemBarsForCar() {
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
    }

    companion object {
        const val EXTRA_ACTION = "cooldrive_extra_action"
        const val EXTRA_OPEN_DRAWER = "open_drawer"
        const val EXTRA_OPEN_YOUTUBE = "open_youtube"
        const val EXTRA_OPEN_QUICK_PANEL = "open_quick_panel"
        const val EXTRA_OPEN_NOTIFICATIONS = "open_notifications"
        const val EXTRA_OPEN_SETTINGS = "open_settings"
    }
}

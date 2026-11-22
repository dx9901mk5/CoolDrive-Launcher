package com.cooldrive.launcher.accessibility

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import com.cooldrive.launcher.CoolDriveApp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Lightweight AccessibilityService powering:
 * - Accurate tracking of foreground third-party apps (YouTube, Google Maps, Spotify, etc.)
 *   so the Persistent AA Dock highlights the active app and hides/shows cleanly.
 * - Global car-safe actions (Home, Back, Recents, Split-Screen toggle) from the Persistent Dock.
 */
class CoolDriveAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        _isConnected.value = true
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg.isBlank() || pkg == "com.android.systemui") return
        _foregroundPackage.value = pkg
        if (pkg != packageName) {
            CoolDriveApp.instance?.installedAppsRepository?.recordRecent(pkg)
        }
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        super.onDestroy()
        if (instance == this) instance = null
        _isConnected.value = false
    }

    fun goHome(): Boolean = performGlobalAction(GLOBAL_ACTION_HOME)

    fun goBack(): Boolean = performGlobalAction(GLOBAL_ACTION_BACK)

    fun openRecents(): Boolean = performGlobalAction(GLOBAL_ACTION_RECENTS)

    fun toggleSplitScreen(): Boolean = performGlobalAction(GLOBAL_ACTION_TOGGLE_SPLIT_SCREEN)

    companion object {
        @Volatile
        var instance: CoolDriveAccessibilityService? = null

        private val _isConnected = MutableStateFlow(false)
        val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

        private val _foregroundPackage = MutableStateFlow("")
        val foregroundPackage: StateFlow<String> = _foregroundPackage.asStateFlow()
    }
}

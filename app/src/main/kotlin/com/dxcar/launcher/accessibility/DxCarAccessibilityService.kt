package com.dxcar.launcher.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import com.dxcar.launcher.DxCarApp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Lightweight AccessibilityService powering:
 * - Accurate tracking of foreground third-party apps (YouTube, Google Maps, Spotify, etc.)
 *   so the Persistent AA Dock highlights the active app and hides/shows cleanly.
 * - Global car-safe actions (Home, Back, Recents, Split-Screen toggle) from the Persistent Dock.
 */
class DxCarAccessibilityService : AccessibilityService() {

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
            DxCarApp.instance?.installedAppsRepository?.recordRecent(pkg)
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

    /**
     * Simulates a true 2-finger parallel vertical swipe in the center of the screen
     * so single-touch AI Box screens can tilt Google Maps into 3D buildings view (or back to 2D).
     */
    fun dispatchTwoFingerTilt(tiltUpTo3D: Boolean, onDone: ((Boolean) -> Unit)? = null): Boolean {
        val dm = resources.displayMetrics
        val w = dm.widthPixels.toFloat().coerceAtLeast(400f)
        val h = dm.heightPixels.toFloat().coerceAtLeast(300f)

        val x1 = w * 0.44f
        val x2 = w * 0.56f
        val startY = if (tiltUpTo3D) h * 0.62f else h * 0.34f
        val endY = if (tiltUpTo3D) h * 0.30f else h * 0.64f

        val p1 = Path().apply {
            moveTo(x1, startY)
            lineTo(x1, endY)
        }
        val p2 = Path().apply {
            moveTo(x2, startY)
            lineTo(x2, endY)
        }

        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(p1, 0L, 420L))
            .addStroke(GestureDescription.StrokeDescription(p2, 0L, 420L))
            .build()

        return dispatchGesture(
            gesture,
            object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    onDone?.invoke(true)
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    onDone?.invoke(false)
                }
            },
            Handler(Looper.getMainLooper())
        )
    }

    /**
     * Simulates a 2-finger pinch zoom gesture in the center of the screen.
     */
    fun dispatchTwoFingerPinch(zoomIn: Boolean, onDone: ((Boolean) -> Unit)? = null): Boolean {
        val dm = resources.displayMetrics
        val w = dm.widthPixels.toFloat().coerceAtLeast(400f)
        val h = dm.heightPixels.toFloat().coerceAtLeast(300f)
        val cy = h * 0.50f

        val leftStart = if (zoomIn) w * 0.44f else w * 0.28f
        val leftEnd = if (zoomIn) w * 0.28f else w * 0.44f
        val rightStart = if (zoomIn) w * 0.56f else w * 0.72f
        val rightEnd = if (zoomIn) w * 0.72f else w * 0.56f

        val p1 = Path().apply {
            moveTo(leftStart, cy)
            lineTo(leftEnd, cy)
        }
        val p2 = Path().apply {
            moveTo(rightStart, cy)
            lineTo(rightEnd, cy)
        }

        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(p1, 0L, 380L))
            .addStroke(GestureDescription.StrokeDescription(p2, 0L, 380L))
            .build()

        return dispatchGesture(
            gesture,
            object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    onDone?.invoke(true)
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    onDone?.invoke(false)
                }
            },
            Handler(Looper.getMainLooper())
        )
    }

    companion object {
        @Volatile
        var instance: DxCarAccessibilityService? = null

        private val _isConnected = MutableStateFlow(false)
        val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

        private val _foregroundPackage = MutableStateFlow("")
        val foregroundPackage: StateFlow<String> = _foregroundPackage.asStateFlow()
    }
}

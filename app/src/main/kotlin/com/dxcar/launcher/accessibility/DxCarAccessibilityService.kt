package com.dxcar.launcher.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import com.dxcar.launcher.DxCarApp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * AccessibilityService powering:
 * - Foreground app tracking (for Persistent AA Dock & AOSP Split-Screen focus verification)
 * - Global actions: Home, Back, Recents, System Quick Settings Shade, and AOSP Split-Screen
 * - AOSP Recents Split-Screen node automation fallback (for Android 12L/13/14 WMShell StageCoordinator)
 * - Single-touch / Long-press / Drag / Scroll injection (for Phone Remote Touchpad & 3D Cursor)
 * - 2-finger parallel tilt (3D buildings) & 2-finger pinch zoom injection (for 3D Cursor Controller)
 */
class DxCarAccessibilityService : AccessibilityService() {

    private val mainHandler = Handler(Looper.getMainLooper())

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

    fun openQuickSettings(): Boolean = performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS)

    fun openNotificationsShade(): Boolean = performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)

    fun toggleSplitScreen(): Boolean = performGlobalAction(GLOBAL_ACTION_TOGGLE_SPLIT_SCREEN)

    /**
     * Inspects interactive system windows to verify whether AOSP draggable Split-Screen is genuinely active.
     */
    fun isSystemInSplitScreenNow(): Boolean {
        val winList = runCatching { windows }.getOrNull().orEmpty()
        if (winList.any { it.type == AccessibilityWindowInfo.TYPE_SPLIT_SCREEN_DIVIDER }) {
            return true
        }
        val dm = resources.displayMetrics
        val fullW = dm.widthPixels
        val fullH = dm.heightPixels
        val appWindows = winList.filter { win ->
            if (win.type != AccessibilityWindowInfo.TYPE_APPLICATION) return@filter false
            val r = Rect()
            win.getBoundsInScreen(r)
            r.width() in 120 until (fullW - 80) || r.height() in 120 until (fullH - 80)
        }
        return appWindows.isNotEmpty()
    }

    /**
     * Multi-stage AOSP Split-Screen trigger for the currently focused foreground app:
     * 1) Try standard GLOBAL_ACTION_TOGGLE_SPLIT_SCREEN and verify window split state.
     * 2) Fallback for Android 12L/13/14 WMShell Launcher3/QuickStep: open Recents Overview,
     *    locate "分割畫面 / 分屏 / Split" action (directly or via Task Header App Icon menu) and click it!
     */
    suspend fun enterAospSplitScreenFromCurrentApp(): Boolean {
        if (isSystemInSplitScreenNow()) return true

        val directOk = toggleSplitScreen()
        if (directOk) {
            delay(420L)
            if (isSystemInSplitScreenNow()) return true
        }

        // Stage B: AOSP Recents Overview Split-Screen button click
        if (!openRecents()) return directOk
        delay(520L)

        if (findAndClickSplitNode()) {
            delay(450L)
            return true
        }

        // In AOSP Launcher3 QuickStep, tapping the task's top app icon opens the popup menu containing "分割畫面 / Split top"
        if (findAndClickRecentsTaskHeaderIcon()) {
            delay(360L)
            if (findAndClickSplitNode()) {
                delay(450L)
                return true
            }
        }
        return isSystemInSplitScreenNow()
    }

    private fun collectNonSelfRoots(): List<AccessibilityNodeInfo> {
        val selfPkg = packageName
        return buildList {
            runCatching { windows }.getOrNull()?.forEach { w ->
                val r = w.root ?: return@forEach
                if (r.packageName?.toString() != selfPkg) {
                    add(r)
                }
            }
            rootInActiveWindow?.let { r ->
                if (r.packageName?.toString() != selfPkg) add(r)
            }
        }
    }

    private fun findAndClickSplitNode(): Boolean {
        val roots = collectNonSelfRoots()
        val splitKeywords = listOf(
            "分割畫面", "分割螢幕", "分屏", "左右分屏", "開啟分屏", "雙屏", "雙視窗",
            "split screen", "split top", "split left", "enter split", "multi window"
        )
        for (root in roots) {
            val found = searchNodeRecursive(root) { node ->
                val text = node.text?.toString()?.lowercase().orEmpty()
                val desc = node.contentDescription?.toString()?.lowercase().orEmpty()
                val id = node.viewIdResourceName?.lowercase().orEmpty()
                splitKeywords.any { kw -> text.contains(kw) || desc.contains(kw) } ||
                    id.contains("split") || id.contains("multi_window") || id.contains("dock_top")
            }
            if (found != null && performClickUpHierarchy(found)) {
                return true
            }
        }
        return false
    }

    private fun findAndClickRecentsTaskHeaderIcon(): Boolean {
        val dm = resources.displayMetrics
        val screenH = dm.heightPixels
        for (root in collectNonSelfRoots()) {
            val iconNode = searchNodeRecursive(root) { node ->
                val id = node.viewIdResourceName?.lowercase().orEmpty()
                val cls = node.className?.toString()?.lowercase().orEmpty()
                val r = Rect()
                node.getBoundsInScreen(r)
                val isSmallHeaderIcon = r.width() in 28..180 && r.height() in 28..180 &&
                    r.top in 20..(screenH * 0.45f).toInt()
                val matchesId = id.contains("icon") || id.contains("task_icon") ||
                    id.contains("app_icon") || id.contains("title_icon") || id.contains("menu")
                (matchesId && (node.isClickable || isSmallHeaderIcon)) ||
                    (isSmallHeaderIcon && node.isClickable && cls.contains("image"))
            }
            if (iconNode != null && performClickUpHierarchy(iconNode)) {
                return true
            }
        }
        return false
    }

    private fun searchNodeRecursive(
        node: AccessibilityNodeInfo?,
        predicate: (AccessibilityNodeInfo) -> Boolean
    ): AccessibilityNodeInfo? {
        if (node == null) return null
        if (predicate(node)) return node
        for (i in 0 until node.childCount) {
            val child = runCatching { node.getChild(i) }.getOrNull() ?: continue
            val match = searchNodeRecursive(child, predicate)
            if (match != null) return match
        }
        return null
    }

    private fun performClickUpHierarchy(node: AccessibilityNodeInfo): Boolean {
        var cur: AccessibilityNodeInfo? = node
        var depth = 0
        while (cur != null && depth < 5) {
            if (cur.isClickable && cur.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                return true
            }
            cur = cur.parent
            depth++
        }
        val r = Rect()
        node.getBoundsInScreen(r)
        if (!r.isEmpty) {
            return dispatchSingleTap(r.exactCenterX(), r.exactCenterY())
        }
        return false
    }

    /**
     * Finds and clicks the master Wi-Fi Switch inside `Settings.Panel.ACTION_WIFI` or `ACTION_WIFI_SETTINGS`
     * so Android 10–15 AI Boxes that block `WifiManager.setWifiEnabled` can still automatically cycle Wi-Fi OFF -> ON!
     */
    fun clickWifiMasterSwitchInSettings(expectCurrentlyChecked: Boolean): Boolean {
        val roots = collectNonSelfRoots().filter { r ->
            val pkg = r.packageName?.toString()?.lowercase().orEmpty()
            pkg.contains("settings") || pkg.contains("systemui") || pkg.contains("wifi") || pkg.contains("car")
        }.ifEmpty { collectNonSelfRoots() }

        val wifiWords = listOf("wi-fi", "wifi", "wlan", "無線網路", "使用 wi-fi", "開啟")

        for (root in roots) {
            // 1. Prefer an explicit checkable Switch matching expected state near a Wi-Fi label or main_switch_bar
            val matchedSwitch = searchNodeRecursive(root) { node ->
                val id = node.viewIdResourceName?.lowercase().orEmpty()
                val cls = node.className?.toString()?.lowercase().orEmpty()
                val isSwitchLike = node.isCheckable || cls.contains("switch") || cls.contains("toggle") ||
                    id.contains("switch_widget") || id.contains("switch_bar") || id.contains("main_switch")
                if (!isSwitchLike) return@searchNodeRecursive false
                if (node.isCheckable && node.isChecked != expectCurrentlyChecked) return@searchNodeRecursive false

                val selfText = "${node.text ?: ""} ${node.contentDescription ?: ""}".lowercase()
                val parentText = buildString {
                    val p = node.parent
                    if (p != null) {
                        append(p.text ?: "").append(' ').append(p.contentDescription ?: "")
                        for (i in 0 until p.childCount.coerceAtMost(6)) {
                            val c = runCatching { p.getChild(i) }.getOrNull() ?: continue
                            append(' ').append(c.text ?: "").append(' ').append(c.contentDescription ?: "")
                        }
                    }
                }.lowercase()

                id.contains("switch_bar") || id.contains("main_switch") ||
                    wifiWords.any { kw -> selfText.contains(kw) || parentText.contains(kw) }
            }
            if (matchedSwitch != null && performClickUpHierarchy(matchedSwitch)) {
                return true
            }

            // 2. Fallback: topmost checkable Switch inside the Wi-Fi Panel / Wi-Fi Settings window
            val topSwitch = searchNodeRecursive(root) { node ->
                val cls = node.className?.toString()?.lowercase().orEmpty()
                node.isCheckable && node.isChecked == expectCurrentlyChecked && cls.contains("switch")
            }
            if (topSwitch != null && performClickUpHierarchy(topSwitch)) {
                return true
            }
        }
        return false
    }

    // ==================== SINGLE-TOUCH / REMOTE MOUSE / 3D GESTURE INJECTION ====================

    fun dispatchSingleTap(x: Float, y: Float, onDone: ((Boolean) -> Unit)? = null): Boolean {
        val dm = resources.displayMetrics
        val sx = x.coerceIn(2f, (dm.widthPixels - 2).coerceAtLeast(10).toFloat())
        val sy = y.coerceIn(2f, (dm.heightPixels - 2).coerceAtLeast(10).toFloat())
        val path = Path().apply { moveTo(sx, sy) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0L, 55L))
            .build()
        return dispatchWithCallback(gesture, onDone)
    }

    fun dispatchLongPress(x: Float, y: Float, onDone: ((Boolean) -> Unit)? = null): Boolean {
        val dm = resources.displayMetrics
        val sx = x.coerceIn(2f, (dm.widthPixels - 2).coerceAtLeast(10).toFloat())
        val sy = y.coerceIn(2f, (dm.heightPixels - 2).coerceAtLeast(10).toFloat())
        val path = Path().apply { moveTo(sx, sy) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0L, 620L))
            .build()
        return dispatchWithCallback(gesture, onDone)
    }

    fun dispatchSinglePathGesture(
        path: Path,
        durationMs: Long,
        onDone: ((Boolean) -> Unit)? = null
    ): Boolean {
        val safeDur = durationMs.coerceIn(45L, 900L)
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0L, safeDur))
            .build()
        return dispatchWithCallback(gesture, onDone)
    }

    fun dispatchScrollSwipe(x: Float, y: Float, scrollUp: Boolean, onDone: ((Boolean) -> Unit)? = null): Boolean {
        val dm = resources.displayMetrics
        val w = dm.widthPixels.toFloat().coerceAtLeast(320f)
        val h = dm.heightPixels.toFloat().coerceAtLeast(240f)
        val sx = x.coerceIn(20f, w - 20f)
        val startY = y.coerceIn(h * 0.22f, h * 0.78f)
        val delta = (h * 0.26f).coerceIn(90f, 260f)
        val endY = (if (scrollUp) startY + delta else startY - delta).coerceIn(16f, h - 16f)
        val path = Path().apply {
            moveTo(sx, startY)
            lineTo(sx, endY)
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0L, 220L))
            .build()
        return dispatchWithCallback(gesture, onDone)
    }

    /**
     * Simulates a 2-finger parallel vertical swipe centered around [anchorX, anchorY] (or screen center)
     * so single-touch AI Box screens can smoothly tilt Google Maps into 3D buildings view or back to 2D.
     */
    fun dispatchTwoFingerTilt(
        tiltUpTo3D: Boolean,
        anchorX: Float? = null,
        anchorY: Float? = null,
        onDone: ((Boolean) -> Unit)? = null
    ): Boolean {
        val dm = resources.displayMetrics
        val w = dm.widthPixels.toFloat().coerceAtLeast(400f)
        val h = dm.heightPixels.toFloat().coerceAtLeast(300f)

        val cx = (anchorX ?: (w * 0.50f)).coerceIn(w * 0.20f, w * 0.80f)
        val cy = (anchorY ?: (h * 0.50f)).coerceIn(h * 0.28f, h * 0.72f)
        val halfSpan = (w * 0.055f).coerceIn(32f, 85f)
        val travel = (h * 0.26f).coerceIn(100f, 240f)

        val x1 = (cx - halfSpan).coerceAtLeast(16f)
        val x2 = (cx + halfSpan).coerceAtMost(w - 16f)
        val startY = if (tiltUpTo3D) (cy + travel * 0.5f).coerceAtMost(h - 20f) else (cy - travel * 0.5f).coerceAtLeast(20f)
        val endY = if (tiltUpTo3D) (cy - travel * 0.5f).coerceAtLeast(20f) else (cy + travel * 0.5f).coerceAtMost(h - 20f)

        val p1 = Path().apply {
            moveTo(x1, startY)
            lineTo(x1, endY)
        }
        val p2 = Path().apply {
            moveTo(x2, startY)
            lineTo(x2, endY)
        }

        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(p1, 0L, 340L))
            .addStroke(GestureDescription.StrokeDescription(p2, 0L, 340L))
            .build()

        return dispatchWithCallback(gesture, onDone)
    }

    /**
     * Simulates a 2-finger pinch-in (zoom out) or spread-out (zoom in) centered around [anchorX, anchorY].
     */
    fun dispatchTwoFingerPinch(
        zoomIn: Boolean,
        anchorX: Float? = null,
        anchorY: Float? = null,
        onDone: ((Boolean) -> Unit)? = null
    ): Boolean {
        val dm = resources.displayMetrics
        val w = dm.widthPixels.toFloat().coerceAtLeast(400f)
        val h = dm.heightPixels.toFloat().coerceAtLeast(300f)

        val cx = (anchorX ?: (w * 0.50f)).coerceIn(w * 0.24f, w * 0.76f)
        val cy = (anchorY ?: (h * 0.50f)).coerceIn(h * 0.20f, h * 0.80f)
        val innerOffset = (w * 0.045f).coerceIn(28f, 70f)
        val outerOffset = (w * 0.16f).coerceIn(95f, 220f)

        val leftStart = (cx - if (zoomIn) innerOffset else outerOffset).coerceAtLeast(14f)
        val leftEnd = (cx - if (zoomIn) outerOffset else innerOffset).coerceAtLeast(14f)
        val rightStart = (cx + if (zoomIn) innerOffset else outerOffset).coerceAtMost(w - 14f)
        val rightEnd = (cx + if (zoomIn) outerOffset else innerOffset).coerceAtMost(w - 14f)

        val p1 = Path().apply {
            moveTo(leftStart, cy)
            lineTo(leftEnd, cy)
        }
        val p2 = Path().apply {
            moveTo(rightStart, cy)
            lineTo(rightEnd, cy)
        }

        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(p1, 0L, 300L))
            .addStroke(GestureDescription.StrokeDescription(p2, 0L, 300L))
            .build()

        return dispatchWithCallback(gesture, onDone)
    }

    /**
     * Simulates a 2-finger parallel horizontal drag centered around [anchorX, anchorY]
     * (triggered by 2-finger horizontal drag on the Smartphone Remote Touchpad).
     */
    fun dispatchTwoFingerHorizontalDrag(
        dragLeft: Boolean,
        anchorX: Float? = null,
        anchorY: Float? = null,
        onDone: ((Boolean) -> Unit)? = null
    ): Boolean {
        val dm = resources.displayMetrics
        val w = dm.widthPixels.toFloat().coerceAtLeast(400f)
        val h = dm.heightPixels.toFloat().coerceAtLeast(300f)

        val cx = (anchorX ?: (w * 0.50f)).coerceIn(w * 0.25f, w * 0.75f)
        val cy = (anchorY ?: (h * 0.50f)).coerceIn(h * 0.25f, h * 0.75f)
        val halfSpanY = (h * 0.06f).coerceIn(28f, 75f)
        val travelX = (w * 0.22f).coerceIn(110f, 260f)

        val y1 = (cy - halfSpanY).coerceAtLeast(16f)
        val y2 = (cy + halfSpanY).coerceAtMost(h - 16f)
        val startX = if (dragLeft) (cx + travelX * 0.5f).coerceAtMost(w - 20f) else (cx - travelX * 0.5f).coerceAtLeast(20f)
        val endX = if (dragLeft) (cx - travelX * 0.5f).coerceAtLeast(20f) else (cx + travelX * 0.5f).coerceAtMost(w - 20f)

        val p1 = Path().apply {
            moveTo(startX, y1)
            lineTo(endX, y1)
        }
        val p2 = Path().apply {
            moveTo(startX, y2)
            lineTo(endX, y2)
        }

        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(p1, 0L, 300L))
            .addStroke(GestureDescription.StrokeDescription(p2, 0L, 300L))
            .build()

        return dispatchWithCallback(gesture, onDone)
    }

    private fun dispatchWithCallback(
        gesture: GestureDescription,
        onDone: ((Boolean) -> Unit)?
    ): Boolean {
        return runCatching {
            dispatchGesture(
                gesture,
                object : GestureResultCallback() {
                    override fun onCompleted(gestureDescription: GestureDescription?) {
                        onDone?.invoke(true)
                    }

                    override fun onCancelled(gestureDescription: GestureDescription?) {
                        onDone?.invoke(false)
                    }
                },
                mainHandler
            )
        }.getOrDefault(false)
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

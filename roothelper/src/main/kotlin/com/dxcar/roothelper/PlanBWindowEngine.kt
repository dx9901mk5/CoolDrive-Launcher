package com.dxcar.roothelper

import android.app.ActivityOptions
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.os.Bundle
import android.view.Display
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

object PlanBWindowEngine {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private const val MEDIA_SPLIT_COMPONENT = "com.dxcar.launcher/com.dxcar.launcher.SplitSidePanelActivity"

    @Volatile
    private var globalsInitialized = false

    @Volatile
    private var lastRightPanePkg: String = ""

    @Volatile
    private var cachedSafeTopBottom: Pair<Int, Int>? = null

    @Volatile
    private var cachedPhysicalDisplaySize: Pair<Int, Int>? = null

    /**
     * Resolves Display 0's true physical pixel dimensions via `wm size` so even if a caller
     * inside a split/freeform Activity passes its local pane bounds, Plan B ALWAYS lays out against
     * the full screen (e.g. 1920x720 on car head units, 1920x1080 on tablets).
     */
    private suspend fun resolvePhysicalDisplaySize(fallbackW: Int, fallbackH: Int): Pair<Int, Int> {
        cachedPhysicalDisplaySize?.let { (w, h) ->
            if (w >= 640 && h >= 400) return w to h
        }
        val res = RootShell.execSu("wm size", 3L)
        val overrideMatch = Regex("""Override size:\s*(\d+)x(\d+)""").find(res.stdout)
        val physicalMatch = Regex("""Physical size:\s*(\d+)x(\d+)""").find(res.stdout)
        val m = overrideMatch ?: physicalMatch
        if (m != null) {
            val w = m.groupValues[1].toIntOrNull() ?: fallbackW
            val h = m.groupValues[2].toIntOrNull() ?: fallbackH
            if (w >= 640 && h >= 400) {
                // Keep landscape orientation consistent if caller is landscape
                val pair = if (fallbackW > fallbackH && h > w) h to w else w to h
                cachedPhysicalDisplaySize = pair
                return pair
            }
        }
        return fallbackW.coerceAtLeast(800) to fallbackH.coerceAtLeast(480)
    }

    /**
     * Reads Display 0's real usable vertical bounds so Freeform windows NEVER clip off the bottom edge
     * on tablets with a persistent Taskbar, while NEVER accidentally clamping to secondary private displays
     * (e.g. Display 2 = 360x360 on Car AI Boxes, which would otherwise crush 1920x720 windows to half height!).
     */
    private suspend fun resolveSafeVerticalBounds(screenW: Int, screenH: Int): Pair<Int, Int> {
        cachedSafeTopBottom?.let { (t, b) ->
            if (b in (screenH * 0.80f).toInt()..screenH && t in 0 until (b - 240)) return t to b
        }
        val res = RootShell.execSu(
            "dumpsys window displays | awk '/Display: mDisplayId=0/,/Display: mDisplayId=[1-9]/' | grep -oE 'mAppBounds=Rect\\([0-9]+, [0-9]+ - [0-9]+, [0-9]+\\)' | head -n 4; " +
                "echo '===NAV==='; getprop persist.suding.hasnavibar",
            3L
        )
        var safeTop = 0
        var safeBottom = screenH
        val minValidW = (screenW * 0.85f).toInt()
        val minValidBottom = (screenH * 0.80f).toInt()

        val rectRegex = Regex("""mAppBounds=Rect\((\d+),\s*(\d+)\s*-\s*(\d+),\s*(\d+)\)""")
        for (m in rectRegex.findAll(res.stdout)) {
            val l = m.groupValues[1].toIntOrNull() ?: continue
            val t = m.groupValues[2].toIntOrNull() ?: continue
            val r = m.groupValues[3].toIntOrNull() ?: continue
            val b = m.groupValues[4].toIntOrNull() ?: continue
            val w = r - l
            // Strictly reject secondary/sub-container rects (like 360x360 Display 2!) whose width/height doesn't match Display 0
            if (w >= minValidW && b in minValidBottom..screenH) {
                safeBottom = b
                if (t in 0..120) safeTop = t
                break
            }
        }
        // On frameless car head units (persist.suding.hasnavibar=false or 1920x720 wide aspect), always use full 0..screenH!
        val noNaviBar = res.stdout.substringAfter("===NAV===", "").trim().equals("false", ignoreCase = true)
        if (noNaviBar || (screenW >= 1600 && screenH <= 800)) {
            safeTop = 0
            safeBottom = screenH
        }
        if (safeBottom - safeTop < (screenH * 0.78f).toInt()) {
            safeTop = 0
            safeBottom = screenH
        }
        val pair = safeTop to safeBottom
        cachedSafeTopBottom = pair
        return pair
    }

    fun dispatchFromProvider(context: Context, method: String, extras: Bundle?): Bundle {
        val out = Bundle()
        when (method) {
            "apply_plan_b_page" -> {
                val b = extras ?: return out.apply { putBoolean("ok", false) }
                val page = b.getInt("page", 1)
                val screenW = b.getInt("screenWidth", 1920)
                val screenH = b.getInt("screenHeight", 720)
                val dockLeft = b.getInt("dockOffsetLeft", 0)
                val dockRight = b.getInt("dockOffsetRight", 0)
                val dockTop = b.getInt("dockOffsetTop", 0)
                val dockBottom = b.getInt("dockOffsetBottom", 0)
                val ui1Ratio = b.getFloat("ui1Ratio", 0.70f).coerceIn(0.25f, 0.75f)
                val ui1Swapped = b.getBoolean("ui1Swapped", false)
                val ui2Ratio = b.getFloat("ui2Ratio", 0.50f).coerceIn(0.25f, 0.75f)
                val ui2Swapped = b.getBoolean("ui2Swapped", false)

                scope.launch {
                    applyPlanBPageInternal(
                        context = context.applicationContext,
                        page = page,
                        screenW = screenW,
                        screenH = screenH,
                        dockLeft = dockLeft,
                        dockRight = dockRight,
                        dockTop = dockTop,
                        dockBottom = dockBottom,
                        ui1Ratio = ui1Ratio,
                        ui1Swapped = ui1Swapped,
                        ui2Ratio = ui2Ratio,
                        ui2Swapped = ui2Swapped
                    )
                }
                out.putBoolean("ok", true)
            }
            "launch_plan_b_right_pane" -> {
                val b = extras ?: return out.apply { putBoolean("ok", false) }
                val targetPkg = b.getString("packageName").orEmpty()
                val screenW = b.getInt("screenWidth", 1920)
                val screenH = b.getInt("screenHeight", 720)
                val dockLeft = b.getInt("dockOffsetLeft", 0)
                val dockRight = b.getInt("dockOffsetRight", 0)
                val dockTop = b.getInt("dockOffsetTop", 0)
                val dockBottom = b.getInt("dockOffsetBottom", 0)
                val ui1Ratio = b.getFloat("ui1Ratio", 0.70f).coerceIn(0.25f, 0.75f)
                val ui1Swapped = b.getBoolean("ui1Swapped", false)
                if (targetPkg.isBlank()) {
                    out.putBoolean("ok", false)
                } else {
                    scope.launch {
                        launchPlanBRightPaneInternal(
                            context = context.applicationContext,
                            targetPkg = targetPkg,
                            screenW = screenW,
                            screenH = screenH,
                            dockLeft = dockLeft,
                            dockRight = dockRight,
                            dockTop = dockTop,
                            dockBottom = dockBottom,
                            ui1Ratio = ui1Ratio,
                            ui1Swapped = ui1Swapped
                        )
                    }
                    out.putBoolean("ok", true)
                }
            }
            "update_ui1_ratio" -> {
                val ratio = extras?.getFloat("ratio", 0.70f)?.coerceIn(0.25f, 0.75f) ?: 0.70f
                val cfg = RootHelperConfigStore.load(context)
                RootHelperConfigStore.save(context, cfg.copy(ui1SplitRatio = ratio))
                out.putBoolean("ok", true)
            }
            "update_ui2_ratio" -> {
                val ratio = extras?.getFloat("ratio", 0.50f)?.coerceIn(0.25f, 0.75f) ?: 0.50f
                val cfg = RootHelperConfigStore.load(context)
                RootHelperConfigStore.save(context, cfg.copy(ui2SplitRatio = ratio))
                out.putBoolean("ok", true)
            }
            "set_dock_pinned_right_shift" -> {
                val enabled = extras?.getBoolean("enabled", false) ?: false
                val cfg = RootHelperConfigStore.load(context)
                RootHelperConfigStore.save(context, cfg.copy(dockPinnedRightShift = enabled))
                out.putBoolean("ok", true)
            }
            "park_plan_b_windows" -> {
                val screenW = extras?.getInt("screenWidth", 1920) ?: 1920
                val screenH = extras?.getInt("screenHeight", 1080) ?: 1080
                scope.launch {
                    parkPlanBWindowsInternal(context.applicationContext, screenW, screenH)
                }
                out.putBoolean("ok", true)
            }
            "launch_fullscreen_app" -> {
                val targetPkg = extras?.getString("packageName").orEmpty()
                val screenW = extras?.getInt("screenWidth", 1920) ?: 1920
                val screenH = extras?.getInt("screenHeight", 1080) ?: 1080
                val dockLeft = extras?.getInt("dockOffsetLeft", 0) ?: 0
                val dockRight = extras?.getInt("dockOffsetRight", 0) ?: 0
                val dockTop = extras?.getInt("dockOffsetTop", 0) ?: 0
                val dockBottom = extras?.getInt("dockOffsetBottom", 0) ?: 0
                if (targetPkg.isBlank()) {
                    out.putBoolean("ok", false)
                } else {
                    scope.launch {
                        launchFullscreenAppInternal(
                            context = context.applicationContext,
                            targetPkg = targetPkg,
                            screenW = screenW,
                            screenH = screenH,
                            dockLeft = dockLeft,
                            dockRight = dockRight,
                            dockTop = dockTop,
                            dockBottom = dockBottom
                        )
                    }
                    out.putBoolean("ok", true)
                }
            }
            else -> out.putBoolean("ok", false)
        }
        return out
    }

    private suspend fun ensureMultiWindowGlobals() {
        if (globalsInitialized) return
        val res = RootShell.execSu(
            "settings put global enable_freeform_support 1; " +
                "settings put global force_resizable_activities 1; " +
                "settings put global enable_non_resizable_multi_window 1",
            4L
        )
        if (res.isSuccess) {
            globalsInitialized = true
        }
    }

    private suspend fun applyPlanBPageInternal(
        context: Context,
        page: Int,
        screenW: Int,
        screenH: Int,
        dockLeft: Int,
        dockRight: Int,
        dockTop: Int,
        dockBottom: Int,
        ui1Ratio: Float,
        ui1Swapped: Boolean,
        ui2Ratio: Float,
        ui2Swapped: Boolean
    ) {
        val cfg = RootHelperConfigStore.load(context)
        ensureMultiWindowGlobals()
        val (realW, realH) = resolvePhysicalDisplaySize(screenW, screenH)
        val (safeTop, safeBottom) = resolveSafeVerticalBounds(realW, realH)

        val effLeft = if (cfg.dockPinnedRightShift) dockLeft else 0
        val effRight = if (cfg.dockPinnedRightShift) dockRight else 0
        val effTop = maxOf(safeTop, if (cfg.dockPinnedRightShift) dockTop else 0)
        val effBottomEdge = minOf(safeBottom, realH - if (cfg.dockPinnedRightShift) dockBottom else 0)
        val availW = (realW - effLeft - effRight).coerceAtLeast(480)
        val availH = (effBottomEdge - effTop).coerceAtLeast(320)

        when (page) {
            0 -> {
                parkPlanBWindowsInternal(context, screenW, screenH)
            }
            1 -> {
                // UI 1 True Dual Split-App Architecture (ZERO Flicker!):
                // Left 70% = Real Google Maps (or configured navPkg)
                // Right 30% = DX-Car SplitSidePanelActivity (Media Player + Album Cover + Weather + 6 Shortcuts + Integrated Dock!)
                // NEVER bring fullscreen MainActivity to front here so Google Maps never flickers!
                val navPkg = cfg.ui1NavPackage.ifBlank { "com.google.android.apps.maps" }
                val strayPkgs = setOf(cfg.ui1RightPackage, cfg.ui2LeftPackage, cfg.ui2RightPackage, lastRightPanePkg)
                    .filter { it.isNotBlank() && it != navPkg && it != "com.dxcar.launcher" }
                if (strayPkgs.isNotEmpty()) {
                    demotePackagesFromFreeform(context, strayPkgs, screenW, screenH)
                }

                val navW = (availW * ui1Ratio).toInt().coerceIn(240, availW - 220)
                val navRect = if (!ui1Swapped) {
                    Rect(effLeft, effTop, effLeft + navW, effTop + availH)
                } else {
                    Rect(effLeft + (availW - navW), effTop, effLeft + availW, effTop + availH)
                }
                val mediaRect = if (!ui1Swapped) {
                    Rect(effLeft + navW, effTop, effLeft + availW, effTop + availH)
                } else {
                    Rect(effLeft, effTop, effLeft + (availW - navW), effTop + availH)
                }

                val okNav = launchAndResizeFreeform(context, navPkg, navRect, screenW)
                delay(180L)
                val okMedia = launchSplitMediaPanelFreeform(context, navPkg, mediaRect, screenW)
                lastRightPanePkg = "com.dxcar.launcher"

                if (!okNav || !okMedia) {
                    triggerOemOrAospSplit(
                        leftPkg = if (!ui1Swapped) navPkg else MEDIA_SPLIT_COMPONENT,
                        rightComponentOrPkg = if (!ui1Swapped) MEDIA_SPLIT_COMPONENT else navPkg,
                        ratio = if (!ui1Swapped) ui1Ratio else (1f - ui1Ratio)
                    )
                }
            }
            2 -> {
                val rawLeft = cfg.ui2LeftPackage.ifBlank { "com.google.android.apps.maps" }
                val rawRight = cfg.ui2RightPackage.ifBlank { "com.google.android.youtube" }
                val firstPkg = if (!ui2Swapped) rawLeft else rawRight
                val secondPkg = if (!ui2Swapped) rawRight else rawLeft

                val strayPkgs = setOf(cfg.ui1NavPackage, cfg.ui1RightPackage, lastRightPanePkg)
                    .filter { it.isNotBlank() && it != firstPkg && it != secondPkg }
                if (strayPkgs.isNotEmpty()) {
                    demotePackagesFromFreeform(context, strayPkgs, screenW, screenH)
                }

                val leftW = (availW * ui2Ratio).toInt().coerceIn(220, availW - 220)
                val splitX = effLeft + leftW
                val leftRect = Rect(effLeft, effTop, splitX, effTop + availH)
                val rightRect = Rect(splitX, effTop, effLeft + availW, effTop + availH)

                val okLeft = launchAndResizeFreeform(context, firstPkg, leftRect, screenW)
                delay(200L)
                val okRight = launchAndResizeFreeform(context, secondPkg, rightRect, screenW)
                lastRightPanePkg = secondPkg
                if (!okLeft || !okRight) {
                    triggerOemOrAospSplit(
                        leftPkg = firstPkg,
                        rightComponentOrPkg = secondPkg,
                        ratio = ui2Ratio
                    )
                }
            }
        }
    }

    /**
     * Plan B Dock Click Behavior:
     * Directly opens [targetPkg] inside the Right 30% Split Window while keeping Left 70% Google Maps intact!
     */
    private suspend fun launchPlanBRightPaneInternal(
        context: Context,
        targetPkg: String,
        screenW: Int,
        screenH: Int,
        dockLeft: Int,
        dockRight: Int,
        dockTop: Int,
        dockBottom: Int,
        ui1Ratio: Float,
        ui1Swapped: Boolean
    ) {
        val cfg = RootHelperConfigStore.load(context)
        ensureMultiWindowGlobals()
        val (realW, realH) = resolvePhysicalDisplaySize(screenW, screenH)
        val (safeTop, safeBottom) = resolveSafeVerticalBounds(realW, realH)

        val effLeft = if (cfg.dockPinnedRightShift) dockLeft else 0
        val effRight = if (cfg.dockPinnedRightShift) dockRight else 0
        val effTop = maxOf(safeTop, if (cfg.dockPinnedRightShift) dockTop else 0)
        val effBottomEdge = minOf(safeBottom, realH - if (cfg.dockPinnedRightShift) dockBottom else 0)
        val availW = (realW - effLeft - effRight).coerceAtLeast(480)
        val availH = (effBottomEdge - effTop).coerceAtLeast(320)

        val navPkg = cfg.ui1NavPackage.ifBlank { "com.google.android.apps.maps" }
        val navW = (availW * ui1Ratio).toInt().coerceIn(240, availW - 220)
        val rightRect = if (!ui1Swapped) {
            Rect(effLeft + navW, effTop, effLeft + availW, effTop + availH)
        } else {
            Rect(effLeft, effTop, effLeft + (availW - navW), effTop + availH)
        }
        val navRect = if (!ui1Swapped) {
            Rect(effLeft, effTop, effLeft + navW, effTop + availH)
        } else {
            Rect(effLeft + (availW - navW), effTop, effLeft + availW, effTop + availH)
        }

        // Keep Google Maps anchored in Left 70% if user clicked a different app for Right 30%
        if (targetPkg != navPkg) {
            val prevRight = lastRightPanePkg
            if (prevRight.isNotBlank() && prevRight != navPkg && prevRight != targetPkg && prevRight != "com.dxcar.launcher") {
                demotePackagesFromFreeform(context, listOf(prevRight), screenW, screenH)
            }
            launchAndResizeFreeform(context, navPkg, navRect, screenW)
            delay(120L)
        }

        val okRight = launchAndResizeFreeform(context, targetPkg, rightRect, screenW)
        lastRightPanePkg = targetPkg
        if (!okRight && targetPkg != navPkg) {
            triggerOemOrAospSplit(
                leftPkg = if (!ui1Swapped) navPkg else targetPkg,
                rightComponentOrPkg = if (!ui1Swapped) targetPkg else navPkg,
                ratio = if (!ui1Swapped) ui1Ratio else (1f - ui1Ratio)
            )
        }
    }

    private suspend fun launchSplitMediaPanelFreeform(
        context: Context,
        navPkg: String,
        rect: Rect,
        fullScreenW: Int
    ): Boolean {
        runCatching {
            val intent = Intent().apply {
                component = ComponentName("com.dxcar.launcher", "com.dxcar.launcher.SplitSidePanelActivity")
                putExtra("extra_plan_b_mode", true)
                putExtra("extra_auto_launch_maps", false)
                putExtra("extra_nav_package", navPkg)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            }
            val opts = ActivityOptions.makeBasic().apply {
                launchDisplayId = Display.DEFAULT_DISPLAY
                launchBounds = rect
                runCatching {
                    val m = ActivityOptions::class.java.getMethod("setLaunchWindowingMode", Int::class.javaPrimitiveType)
                    m.invoke(this, 5)
                }
            }
            context.startActivity(intent, opts.toBundle())
        }

        val script = """
            am start --user 0 --windowingMode 5 --activity-reorder-to-front -n '$MEDIA_SPLIT_COMPONENT' --ez extra_plan_b_mode true --ez extra_auto_launch_maps false --es extra_nav_package '$navPkg' >/dev/null 2>&1
            sleep 0.22
            TID=${'$'}(dumpsys activity activities | grep -B 3 "SplitSidePanelActivity" | grep -oE "t[0-9]+" | head -n 1 | tr -d 't')
            if [ -z "${'$'}TID" ]; then
              TID=${'$'}(dumpsys activity containers | grep -B 2 "SplitSidePanelActivity" | grep -oE "Task=[0-9]+" | tail -n 1 | cut -d= -f2)
            fi
            if [ -n "${'$'}TID" ]; then
              cmd activity task resizeable ${'$'}TID 2 >/dev/null 2>&1
              cmd activity task resize ${'$'}TID ${rect.left} ${rect.top} ${rect.right} ${rect.bottom} >/dev/null 2>&1
              am task resize ${'$'}TID ${rect.left} ${rect.top} ${rect.right} ${rect.bottom} >/dev/null 2>&1
              sleep 0.12
              am start --user 0 --windowingMode 5 --activity-reorder-to-front -n '$MEDIA_SPLIT_COMPONENT' --ez extra_plan_b_mode true --ez extra_auto_launch_maps false --es extra_nav_package '$navPkg' >/dev/null 2>&1
            fi
            dumpsys activity containers | grep -B 2 "SplitSidePanelActivity" | grep -oE "bounds=\[[0-9,]+\]\[[0-9,]+\]" | tail -n 1
        """.trimIndent()

        val res = RootShell.execSu(script, 6L)
        val boundsLine = res.stdout.lines().lastOrNull { it.startsWith("bounds=") }.orEmpty()
        if (boundsLine.isBlank()) return false
        val expectedMaxW = (fullScreenW * 0.92f).toInt()
        if (rect.width() < expectedMaxW && boundsLine.contains("[0,0][$fullScreenW,")) {
            return false
        }
        return true
    }

    private suspend fun demotePackagesFromFreeform(
        context: Context,
        packages: Collection<String>,
        screenW: Int,
        screenH: Int
    ) {
        val unique = packages.map { it.trim() }.filter { it.isNotBlank() }.distinct()
        if (unique.isEmpty()) return
        val script = buildString {
            for (pkg in unique) {
                val compStr = resolveLaunchComponent(context, pkg)?.flattenToShortString()
                appendLine("for TID in \$(dumpsys activity activities | grep -B 3 -E \"ActivityRecord\\{[^}]* $pkg/\" | grep -oE \"t[0-9]+\" | tr -d 't' | uniq); do")
                appendLine("  cmd activity task resize \$TID 0 0 $screenW $screenH >/dev/null 2>&1")
                appendLine("  am task resize \$TID 0 0 $screenW $screenH >/dev/null 2>&1")
                appendLine("done")
                if (!compStr.isNullOrBlank()) {
                    appendLine("am start --user 0 --windowingMode 1 --activity-single-top --activity-reorder-to-front -n '$compStr' >/dev/null 2>&1")
                }
            }
        }
        RootShell.execSu(script, 5L)
    }

    private suspend fun parkPlanBWindowsInternal(
        context: Context,
        screenW: Int,
        screenH: Int
    ) {
        val cfg = RootHelperConfigStore.load(context)
        val allPlanBPkgs = listOf(cfg.ui1NavPackage, cfg.ui1RightPackage, cfg.ui2LeftPackage, cfg.ui2RightPackage, lastRightPanePkg)
            .filter { it.isNotBlank() && it != "com.dxcar.launcher" }
        demotePackagesFromFreeform(context, allPlanBPkgs, screenW, screenH)
        // Also demote SplitSidePanelActivity task if present and bring MainActivity cleanly to front
        val script = """
            for TID in ${'$'}(dumpsys activity activities | grep -B 3 "SplitSidePanelActivity" | grep -oE "t[0-9]+" | tr -d 't' | uniq); do
              cmd activity task resize ${'$'}TID 0 0 $screenW $screenH >/dev/null 2>&1
            done
            am start --user 0 --windowingMode 1 --activity-single-top --activity-reorder-to-front -n com.dxcar.launcher/com.dxcar.launcher.MainActivity >/dev/null 2>&1
        """.trimIndent()
        RootShell.execSu(script, 4L)
    }

    private suspend fun launchFullscreenAppInternal(
        context: Context,
        targetPkg: String,
        screenW: Int,
        screenH: Int,
        dockLeft: Int = 0,
        dockRight: Int = 0,
        dockTop: Int = 0,
        dockBottom: Int = 0
    ) {
        val cfg = RootHelperConfigStore.load(context)
        ensureMultiWindowGlobals()
        val otherPlanBPkgs = listOf(cfg.ui1NavPackage, cfg.ui1RightPackage, cfg.ui2LeftPackage, cfg.ui2RightPackage, lastRightPanePkg)
            .filter { it.isNotBlank() && it != targetPkg && it != "com.dxcar.launcher" }
        demotePackagesFromFreeform(context, otherPlanBPkgs, screenW, screenH)

        val effLeft = if (cfg.dockPinnedRightShift) dockLeft else 0
        val effRight = if (cfg.dockPinnedRightShift) dockRight else 0
        val effTop = if (cfg.dockPinnedRightShift) dockTop else 0
        val effBottom = if (cfg.dockPinnedRightShift) dockBottom else 0
        val hasDockShift = cfg.dockPinnedRightShift && (effLeft > 0 || effRight > 0 || effTop > 0 || effBottom > 0)

        if (hasDockShift) {
            val targetRect = Rect(
                effLeft,
                effTop,
                (screenW - effRight).coerceAtLeast(effLeft + 320),
                (screenH - effBottom).coerceAtLeast(effTop + 240)
            )
            val ok = launchAndResizeFreeform(context, targetPkg, targetRect, screenW)
            if (ok) return
        }

        val comp = resolveLaunchComponent(context, targetPkg)
        val compStr = comp?.flattenToShortString()
        val launchCmd = if (!compStr.isNullOrBlank()) {
            "am start --user 0 --windowingMode 1 --activity-single-top --activity-reorder-to-front -n '$compStr' >/dev/null 2>&1"
        } else {
            "monkey -p '$targetPkg' -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1"
        }
        val script = """
            for TID in ${'$'}(dumpsys activity activities | grep -B 3 -E "ActivityRecord\{[^}]* $targetPkg/" | grep -oE "t[0-9]+" | tr -d 't' | uniq); do
              cmd activity task resize ${'$'}TID 0 0 $screenW $screenH >/dev/null 2>&1
              am task resize ${'$'}TID 0 0 $screenW $screenH >/dev/null 2>&1
            done
            $launchCmd
            sleep 0.12
            for TID in ${'$'}(dumpsys activity activities | grep -B 3 -E "ActivityRecord\{[^}]* $targetPkg/" | grep -oE "t[0-9]+" | tr -d 't' | uniq); do
              cmd activity task resize ${'$'}TID 0 0 $screenW $screenH >/dev/null 2>&1
            done
        """.trimIndent()
        RootShell.execSu(script, 5L)
    }

    private fun resolveLaunchComponent(context: Context, pkg: String): ComponentName? {
        return runCatching {
            val intent = context.packageManager.getLaunchIntentForPackage(pkg) ?: return@runCatching null
            intent.component ?: context.packageManager.resolveActivity(intent, 0)?.activityInfo?.let {
                ComponentName(it.packageName, it.name)
            }
        }.getOrNull()
    }

    private suspend fun launchAndResizeFreeform(
        context: Context,
        pkg: String,
        rect: Rect,
        fullScreenW: Int
    ): Boolean {
        val comp = resolveLaunchComponent(context, pkg)
        val compStr = comp?.flattenToShortString()

        runCatching {
            val baseIntent = if (comp != null) {
                Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_LAUNCHER)
                    component = comp
                }
            } else {
                context.packageManager.getLaunchIntentForPackage(pkg)
            }
            if (baseIntent != null) {
                baseIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                val opts = ActivityOptions.makeBasic().apply {
                    launchDisplayId = Display.DEFAULT_DISPLAY
                    launchBounds = rect
                    runCatching {
                        val m = ActivityOptions::class.java.getMethod(
                            "setLaunchWindowingMode",
                            Int::class.javaPrimitiveType
                        )
                        m.invoke(this, 5)
                    }
                }
                context.startActivity(baseIntent, opts.toBundle())
            }
        }

        val startCmd = if (!compStr.isNullOrBlank()) {
            "am start --user 0 --windowingMode 5 --activity-reorder-to-front -n '$compStr' >/dev/null 2>&1; "
        } else {
            "monkey -p '$pkg' -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1; "
        }
        val script = """
            $startCmd
            sleep 0.22
            TID=${'$'}(dumpsys activity activities | grep -B 3 -E "ActivityRecord\{[^}]* $pkg/" | grep -oE "t[0-9]+" | head -n 1 | tr -d 't')
            if [ -z "${'$'}TID" ]; then
              TID=${'$'}(dumpsys activity containers | grep -B 2 "$pkg/" | grep -oE "Task=[0-9]+" | tail -n 1 | cut -d= -f2)
            fi
            if [ -n "${'$'}TID" ]; then
              cmd activity task resizeable ${'$'}TID 2 >/dev/null 2>&1
              cmd activity task resize ${'$'}TID ${rect.left} ${rect.top} ${rect.right} ${rect.bottom} >/dev/null 2>&1
              am task resize ${'$'}TID ${rect.left} ${rect.top} ${rect.right} ${rect.bottom} >/dev/null 2>&1
              sleep 0.12
              $startCmd
            fi
            dumpsys activity containers | grep -B 2 "$pkg/" | grep -oE "bounds=\[[0-9,]+\]\[[0-9,]+\]" | tail -n 1
        """.trimIndent()

        val res = RootShell.execSu(script, 6L)
        val boundsLine = res.stdout.lines().lastOrNull { it.startsWith("bounds=") }.orEmpty()
        if (boundsLine.isBlank()) return false
        val expectedMaxW = (fullScreenW * 0.92f).toInt()
        if (rect.width() < expectedMaxW && boundsLine.contains("[0,0][$fullScreenW,")) {
            return false
        }
        return true
    }

    private suspend fun triggerOemOrAospSplit(
        leftPkg: String,
        rightComponentOrPkg: String,
        ratio: Float
    ) {
        val percentStr = when {
            ratio >= 0.66f -> "7:3"
            ratio >= 0.56f -> "6:4"
            ratio <= 0.34f -> "3:7"
            ratio <= 0.44f -> "4:6"
            else -> "1:1"
        }
        val rightPkgOnly = rightComponentOrPkg.substringBefore("/")
        val launchRightCmd = if (rightComponentOrPkg.contains("/")) {
            "am start --user 0 -n '$rightComponentOrPkg' --ez extra_plan_b_mode true --ez extra_auto_launch_maps false --es extra_nav_package '$leftPkg'"
        } else {
            "monkey -p '$rightPkgOnly' -c android.intent.category.LAUNCHER 1"
        }

        val cmd = """
            setprop persist.splitscreen.percent $percentStr
            $launchRightCmd >/dev/null 2>&1
            sleep 0.30
            monkey -p '$leftPkg' -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1
            sleep 0.30
            am start --user 0 -n com.android.launcher3/com.android.quickstep.RecentsActivity >/dev/null 2>&1
            sleep 0.35
            am broadcast -a com.android.intent.ext.main.split.two.screen --es setting_key_split_screen_l '$leftPkg' --es setting_key_split_screen_r '$rightPkgOnly' >/dev/null 2>&1
        """.trimIndent()
        RootShell.execSu(cmd, 6L)
    }
}

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

    @Volatile
    private var globalsInitialized = false

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
                val ui1Ratio = b.getFloat("ui1Ratio", 0.70f).coerceIn(0.45f, 0.80f)
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
                        ui2Ratio = ui2Ratio,
                        ui2Swapped = ui2Swapped
                    )
                }
                out.putBoolean("ok", true)
            }
            "update_ui2_ratio" -> {
                val ratio = extras?.getFloat("ratio", 0.50f)?.coerceIn(0.25f, 0.75f) ?: 0.50f
                val cfg = RootHelperConfigStore.load(context)
                RootHelperConfigStore.save(context, cfg.copy(ui2SplitRatio = ratio))
                out.putBoolean("ok", true)
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
        ui2Ratio: Float,
        ui2Swapped: Boolean
    ) {
        val cfg = RootHelperConfigStore.load(context)
        ensureMultiWindowGlobals()

        val effLeft = if (cfg.dockPinnedRightShift) dockLeft else 0
        val effRight = if (cfg.dockPinnedRightShift) dockRight else 0
        val effTop = if (cfg.dockPinnedRightShift) dockTop else 0
        val effBottom = if (cfg.dockPinnedRightShift) dockBottom else 0
        val availW = (screenW - effLeft - effRight).coerceAtLeast(480)
        val availH = (screenH - effTop - effBottom).coerceAtLeast(320)

        when (page) {
            0 -> {
                // Bring DX-Car Launcher MainActivity to front for Page 0 Widgets
                RootShell.execSu(
                    "am start --user 0 --windowingMode 1 -n com.dxcar.launcher/com.dxcar.launcher.MainActivity",
                    4L
                )
            }
            1 -> {
                val navPkg = cfg.ui1NavPackage.ifBlank { "com.google.android.apps.maps" }
                val navW = (availW * ui1Ratio).toInt().coerceIn(280, availW - 220)
                val navRect = Rect(effLeft, effTop, effLeft + navW, effTop + availH)
                val freeformOk = launchAndResizeFreeform(context, navPkg, navRect, screenW)
                if (!freeformOk) {
                    // Fallback to native split-screen: Left 70% Nav + Right 30% DX-Car SplitSidePanelActivity
                    triggerOemOrAospSplit(
                        leftPkg = navPkg,
                        rightComponentOrPkg = "com.dxcar.launcher/com.dxcar.launcher.SplitSidePanelActivity",
                        ratio = ui1Ratio
                    )
                }
            }
            2 -> {
                val rawLeft = cfg.ui2LeftPackage.ifBlank { "com.google.android.apps.maps" }
                val rawRight = cfg.ui2RightPackage.ifBlank { "com.google.android.youtube" }
                val firstPkg = if (!ui2Swapped) rawLeft else rawRight
                val secondPkg = if (!ui2Swapped) rawRight else rawLeft

                val leftW = (availW * ui2Ratio).toInt().coerceIn(220, availW - 220)
                val splitX = effLeft + leftW
                val leftRect = Rect(effLeft, effTop, splitX, effTop + availH)
                val rightRect = Rect(splitX, effTop, effLeft + availW, effTop + availH)

                val okLeft = launchAndResizeFreeform(context, firstPkg, leftRect, screenW)
                delay(220L)
                val okRight = launchAndResizeFreeform(context, secondPkg, rightRect, screenW)
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

        // 1. Try starting via ActivityOptions FREEFORM (mode 5) + exact launchBounds
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

        // 2. Enforce via Root `am start --windowingMode 5` + `cmd activity task resize`
        val startCmd = if (!compStr.isNullOrBlank()) {
            "am start --user 0 --windowingMode 5 -n '$compStr' >/dev/null 2>&1; "
        } else {
            "monkey -p '$pkg' -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1; "
        }
        val script = """
            $startCmd
            sleep 0.18
            TID=$(dumpsys activity activities | grep -B 3 -E "ActivityRecord\{[^}]* $pkg/" | grep -oE "t[0-9]+" | head -n 1 | tr -d 't')
            if [ -z "${'$'}TID" ]; then
              TID=$(dumpsys activity containers | grep -B 2 "$pkg/" | grep -oE "Task=[0-9]+" | tail -n 1 | cut -d= -f2)
            fi
            if [ -n "${'$'}TID" ]; then
              cmd activity task resize ${'$'}TID ${rect.left} ${rect.top} ${rect.right} ${rect.bottom} >/dev/null 2>&1
              am task resize ${'$'}TID ${rect.left} ${rect.top} ${rect.right} ${rect.bottom} >/dev/null 2>&1
            fi
            dumpsys activity containers | grep -B 2 "$pkg/" | grep -oE "bounds=\[[0-9,]+\]\[[0-9,]+\]" | tail -n 1
        """.trimIndent()

        val res = RootShell.execSu(script, 6L)
        val boundsLine = res.stdout.lines().lastOrNull { it.startsWith("bounds=") }.orEmpty()
        if (boundsLine.isBlank()) return false
        // If bounds are still full width [0,0][1920,...] when rect width < 92% of screen, Freeform was ignored by ROM
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
            "am start --user 0 -n '$rightComponentOrPkg' --ez extra_auto_launch_maps false --es extra_nav_package '$leftPkg'"
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

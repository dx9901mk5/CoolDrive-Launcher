package com.dxcar.launcher.system

import android.app.ActivityOptions
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.Display
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.dxcar.launcher.accessibility.DxCarAccessibilityService
import com.dxcar.launcher.data.SplitLaunchEngine
import com.dxcar.launcher.data.SplitScreenShortcut
import com.dxcar.launcher.layout.CoolwalkLayoutSpec
import com.dxcar.launcher.layout.ResolvedDockPlacement
import com.dxcar.launcher.layout.ScreenMetrics
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class AppCategory(val labelZh: String) {
    ALL("全部"),
    NAVIGATION("導航"),
    MEDIA("影音"),
    COMMUNICATION("通訊"),
    SYSTEM("系統")
}

data class InstalledAppInfo(
    val packageName: String,
    val activityName: String = "",
    val label: String,
    val icon: Bitmap,
    val category: AppCategory,
    val isSystemApp: Boolean = false
)

data class HomeLauncherInfo(
    val packageName: String,
    val activityName: String,
    val label: String,
    val icon: Bitmap,
    val isSelf: Boolean,
    val isDefault: Boolean
)

class InstalledAppsRepository(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val pm: PackageManager = context.packageManager

    private val _apps = MutableStateFlow<List<InstalledAppInfo>>(emptyList())
    val apps: StateFlow<List<InstalledAppInfo>> = _apps.asStateFlow()

    private val _homeLaunchers = MutableStateFlow<List<HomeLauncherInfo>>(emptyList())
    val homeLaunchers: StateFlow<List<HomeLauncherInfo>> = _homeLaunchers.asStateFlow()

    private val _recentPackages = MutableStateFlow<List<String>>(emptyList())
    val recentPackages: StateFlow<List<String>> = _recentPackages.asStateFlow()

    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            refreshApps()
        }
    }

    fun start() {
        refreshApps()
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        ContextCompat.registerReceiver(
            context,
            packageReceiver,
            filter,
            ContextCompat.RECEIVER_EXPORTED
        )
    }

    fun refreshApps() {
        scope.launch {
            val mainIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            val resolved = pm.queryIntentActivities(mainIntent, 0)
            val selfPkg = context.packageName
            val seen = HashSet<String>()
            val loaded = resolved.mapNotNull { info ->
                val ai = info.activityInfo ?: return@mapNotNull null
                val pkg = ai.packageName ?: return@mapNotNull null
                val act = ai.name.orEmpty()
                if (pkg == selfPkg || !seen.add(pkg)) return@mapNotNull null
                val label = info.loadLabel(pm).toString().takeIf { it.isNotBlank() } ?: pkg
                val drawable = runCatching { info.loadIcon(pm) }.getOrNull()
                val bitmap = drawableToBitmap(drawable, 128)
                val appFlags = ai.applicationInfo?.flags ?: 0
                val sys = (appFlags and ApplicationInfo.FLAG_SYSTEM) != 0 &&
                    (appFlags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0
                InstalledAppInfo(
                    packageName = pkg,
                    activityName = act,
                    label = label,
                    icon = bitmap,
                    category = classifyCategory(pkg, label),
                    isSystemApp = sys
                )
            }.sortedBy { it.label.lowercase() }

            _apps.value = loaded
            loadHomeLaunchersSync()
        }
    }

    fun refreshHomeLaunchers() {
        scope.launch { loadHomeLaunchersSync() }
    }

    private fun loadHomeLaunchersSync() {
        val homeIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
        }
        val defaultPkg = runCatching {
            pm.resolveActivity(homeIntent, PackageManager.MATCH_DEFAULT_ONLY)
                ?.activityInfo?.packageName
        }.getOrNull()

        val resolved = runCatching {
            pm.queryIntentActivities(homeIntent, PackageManager.MATCH_ALL)
        }.getOrDefault(emptyList())

        val selfPkg = context.packageName
        val seen = HashSet<String>()
        val list = resolved.mapNotNull { info ->
            val ai = info.activityInfo ?: return@mapNotNull null
            val pkg = ai.packageName ?: return@mapNotNull null
            val act = ai.name ?: return@mapNotNull null
            if (pkg == "android" || act.contains("ResolverActivity") || act.contains("FallbackHome")) {
                return@mapNotNull null
            }
            if (!seen.add("$pkg/$act")) return@mapNotNull null
            val label = info.loadLabel(pm).toString().takeIf { it.isNotBlank() } ?: pkg
            val drawable = runCatching { info.loadIcon(pm) }.getOrNull()
            HomeLauncherInfo(
                packageName = pkg,
                activityName = act,
                label = label,
                icon = drawableToBitmap(drawable, 128),
                isSelf = pkg == selfPkg,
                isDefault = pkg == defaultPkg
            )
        }.sortedWith(
            compareByDescending<HomeLauncherInfo> { it.isDefault }
                .thenByDescending { it.isSelf }
                .thenBy { it.label.lowercase() }
        )

        _homeLaunchers.value = list
    }

    fun launchHomeLauncher(launcher: HomeLauncherInfo): Boolean {
        val homeIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            component = ComponentName(launcher.packageName, launcher.activityName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        }
        val started = runCatching {
            context.startActivity(homeIntent)
            true
        }.getOrDefault(false)
        if (started) return true
        return launchAppFullscreen(launcher.packageName)
    }

    fun findApp(packageName: String): InstalledAppInfo? =
        _apps.value.firstOrNull { it.packageName == packageName }

    fun findBestYoutubePackage(): String? {
        val candidates = listOf(
            "com.google.android.youtube",
            "app.revanced.android.youtube",
            "com.liskovsoft.smarttubetv.beta",
            "com.liskovsoft.smarttubetv",
            "com.aayt.caster"
        )
        return candidates.firstOrNull { isPackageInstalled(it) }
    }

    fun findBestGoogleMapsPackage(): String? {
        val candidates = listOf(
            "com.google.android.apps.maps",
            "com.waze",
            "com.polstar.navi"
        )
        return candidates.firstOrNull { isPackageInstalled(it) }
    }

    fun isPackageInstalled(packageName: String): Boolean {
        return runCatching {
            pm.getLaunchIntentForPackage(packageName) != null
        }.getOrDefault(false)
    }

    fun recordRecent(packageName: String) {
        if (packageName.isBlank() || packageName == context.packageName) return
        val next = (_recentPackages.value - packageName).toMutableList()
        next.add(0, packageName)
        _recentPackages.value = next.take(8)
        com.dxcar.launcher.DxCarApp.instance?.preferences?.recordRecentDockPackage(packageName)
    }

    private fun buildLauncherIntent(packageName: String): Intent? {
        val knownActivity = findApp(packageName)?.activityName.orEmpty()
        return if (knownActivity.isNotBlank()) {
            Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
                component = ComponentName(packageName, knownActivity)
            }
        } else {
            pm.getLaunchIntentForPackage(packageName)
        }
    }

    fun launchAppFullscreen(packageName: String, forceFullscreenWindowMode: Boolean = true): Boolean {
        val intent = buildLauncherIntent(packageName) ?: return false

        intent.addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED or
                Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
        )

        if (packageName == "com.google.android.apps.maps") {
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }

        val options = ActivityOptions.makeBasic().apply {
            launchDisplayId = Display.DEFAULT_DISPLAY
            if (forceFullscreenWindowMode) {
                runCatching {
                    val method = ActivityOptions::class.java.getMethod(
                        "setLaunchWindowingMode",
                        Int::class.javaPrimitiveType
                    )
                    method.invoke(this, 1) // WINDOWING_MODE_FULLSCREEN
                }
            }
        }

        val ok = runCatching {
            context.startActivity(intent, options.toBundle())
            recordRecent(packageName)
            true
        }.getOrElse {
            runCatching {
                context.startActivity(intent)
                recordRecent(packageName)
                true
            }.getOrDefault(false)
        }

        if (!ok && packageName == "com.google.android.apps.maps") {
            return launchGoogleMapsNavigation(null)
        }
        return ok
    }

    fun launchAppSmart(
        packageName: String,
        avoidDockFreeform: Boolean,
        metrics: ScreenMetrics,
        spec: CoolwalkLayoutSpec
    ): Boolean {
        if (!avoidDockFreeform) {
            return launchAppFullscreen(packageName)
        }
        val bounds = computeAvoidDockBounds(metrics, spec)
        return launchPackageFreeform(packageName, bounds)
    }

    fun computeAvoidDockBounds(metrics: ScreenMetrics, spec: CoolwalkLayoutSpec): Rect {
        val dockPx = (spec.dockThicknessDp * metrics.density).toInt().coerceAtLeast(0)
        return when (spec.dockPlacement) {
            ResolvedDockPlacement.LEFT -> Rect(dockPx, 0, metrics.widthPx, metrics.heightPx)
            ResolvedDockPlacement.RIGHT -> Rect(0, 0, (metrics.widthPx - dockPx).coerceAtLeast(240), metrics.heightPx)
            ResolvedDockPlacement.TOP -> Rect(0, dockPx, metrics.widthPx, metrics.heightPx)
            ResolvedDockPlacement.BOTTOM -> Rect(0, 0, metrics.widthPx, (metrics.heightPx - dockPx).coerceAtLeast(240))
        }
    }

    fun launchGoogleMapsNavigation(destinationQuery: String? = null): Boolean {
        val uri = if (destinationQuery.isNullOrBlank()) {
            Uri.parse("geo:0,0?q=")
        } else {
            Uri.parse("google.navigation:q=${Uri.encode(destinationQuery)}")
        }
        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            setPackage("com.google.android.apps.maps")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val options = ActivityOptions.makeBasic().apply {
            launchDisplayId = Display.DEFAULT_DISPLAY
            runCatching {
                val method = ActivityOptions::class.java.getMethod(
                    "setLaunchWindowingMode",
                    Int::class.javaPrimitiveType
                )
                method.invoke(this, 1)
            }
        }
        val ok = runCatching {
            context.startActivity(intent, options.toBundle())
            recordRecent("com.google.android.apps.maps")
            true
        }.getOrDefault(false)

        return if (ok) true else launchAppFullscreen("com.google.android.apps.maps")
    }

    fun launchGoogleMapsFreeform(bounds: Rect): Boolean {
        val pkg = findBestGoogleMapsPackage() ?: "com.google.android.apps.maps"
        return launchPackageFreeform(pkg, bounds)
    }

    fun launchDialer(): Boolean {
        val intent = Intent(Intent.ACTION_DIAL).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return runCatching {
            context.startActivity(intent)
            true
        }.getOrDefault(false)
    }

    /**
     * Multi-Stage Self-Verifying AOSP Draggable Split-Screen Launcher:
     * - Stage 1: Attempts direct AOSP WINDOWING_MODE_SPLIT_SCREEN_PRIMARY (3) + SECONDARY (4)
     *   (instant draggable divider on AI Box ROMs that allow unprivileged split-stage launch).
     * - Stage 2: Launches leftPackage with unlocked windowing mode (UNDEFINED = 0), waits until
     *   Accessibility confirms leftPackage owns foreground window focus (not CATEGORY_HOME!),
     *   then triggers GLOBAL_ACTION_TOGGLE_SPLIT_SCREEN (with automatic AOSP Recents Split-Screen
     *   menu click fallback for Android 12L/13/14 WMShell), and finally launches rightPackage into
     *   the adjacent secondary stage!
     */
    fun launchSplitShortcut(
        shortcut: SplitScreenShortcut,
        screenWidthPx: Int,
        screenHeightPx: Int,
        dockOffsetLeftPx: Int = 0,
        dockOffsetRightPx: Int = 0,
        dockOffsetTopPx: Int = 0,
        dockOffsetBottomPx: Int = 0
    ) {
        scope.launch(Dispatchers.Main) {
            if (shortcut.engine == SplitLaunchEngine.FREEFORM_EXACT_RATIO) {
                val availW = (screenWidthPx - dockOffsetLeftPx - dockOffsetRightPx).coerceAtLeast(400)
                val availH = (screenHeightPx - dockOffsetTopPx - dockOffsetBottomPx).coerceAtLeast(300)
                val splitW = (availW * shortcut.ratio.leftWeight).toInt()
                    .coerceIn(200, (availW - 200).coerceAtLeast(200))
                val leftX = dockOffsetLeftPx
                val rightX = leftX + splitW
                val leftRect = Rect(leftX, dockOffsetTopPx, rightX, dockOffsetTopPx + availH)
                val rightRect = Rect(rightX, dockOffsetTopPx, leftX + availW, dockOffsetTopPx + availH)
                launchPackageFreeform(shortcut.leftPackage, leftRect)
                delay(360L)
                launchPackageFreeform(shortcut.rightPackage, rightRect)
                recordRecent(shortcut.leftPackage)
                return@launch
            }

            launchAospDraggableSplitPair(shortcut.leftPackage, shortcut.rightPackage)
        }
    }

    suspend fun launchAospDraggableSplitPair(leftPackage: String, rightPackage: String) {
        val acc = DxCarAccessibilityService.instance

        // Stage 1: Try direct AOSP WINDOWING_MODE_SPLIT_SCREEN_PRIMARY (3)
        val stage1PrimaryStarted = tryLaunchInAospSplitStage(leftPackage, primaryStage = true)
        if (stage1PrimaryStarted) {
            delay(480L)
            if (acc?.isSystemInSplitScreenNow() == true) {
                tryLaunchInAospSplitStage(rightPackage, primaryStage = false)
                recordRecent(leftPackage)
                recordRecent(rightPackage)
                return
            }
        }

        // Stage 2: Standard unlocked launch (no WINDOWING_MODE_FULLSCREEN pin!) + Accessibility Split
        if (acc == null) {
            Toast.makeText(
                context,
                "請先點設定最上方「權限設定」開啟【無障礙服務】，即可啟用 AOSP 原生可拖動分割視窗！",
                Toast.LENGTH_LONG
            ).show()
            launchAppFullscreen(leftPackage, forceFullscreenWindowMode = false)
            delay(450L)
            launchPackageAdjacent(rightPackage)
            return
        }

        if (DxCarAccessibilityService.foregroundPackage.value != leftPackage) {
            launchAppFullscreen(leftPackage, forceFullscreenWindowMode = false)
        }

        // Wait until leftPackage genuinely becomes the foreground window (never split CATEGORY_HOME!)
        val deadline = System.currentTimeMillis() + 2_000L
        while (System.currentTimeMillis() < deadline) {
            if (DxCarAccessibilityService.foregroundPackage.value == leftPackage) break
            delay(80L)
        }
        delay(280L)

        // Trigger AOSP Split-Screen (GlobalAction first, then AOSP Recents Split-Screen button fallback)
        acc.enterAospSplitScreenFromCurrentApp()
        delay(380L)

        // Fill secondary stage with rightPackage
        val rightLaunched = tryLaunchInAospSplitStage(rightPackage, primaryStage = false) ||
            launchPackageAdjacent(rightPackage)
        if (!rightLaunched) {
            launchAppFullscreen(rightPackage, forceFullscreenWindowMode = false)
        }
        recordRecent(leftPackage)
        recordRecent(rightPackage)
    }

    private fun tryLaunchInAospSplitStage(packageName: String, primaryStage: Boolean): Boolean {
        val intent = buildLauncherIntent(packageName)?.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
            if (!primaryStage) {
                addFlags(Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT)
            }
        } ?: return false

        return runCatching {
            val options = ActivityOptions.makeBasic().apply {
                launchDisplayId = Display.DEFAULT_DISPLAY
                val setWinMode = ActivityOptions::class.java.getMethod(
                    "setLaunchWindowingMode",
                    Int::class.javaPrimitiveType
                )
                // AOSP WindowConfiguration: 3 = SPLIT_SCREEN_PRIMARY, 4 = SPLIT_SCREEN_SECONDARY
                setWinMode.invoke(this, if (primaryStage) 3 else 4)
                if (primaryStage) {
                    runCatching {
                        val setCreateMode = ActivityOptions::class.java.getMethod(
                            "setSplitScreenCreateMode",
                            Int::class.javaPrimitiveType
                        )
                        setCreateMode.invoke(this, 0) // SPLIT_SCREEN_CREATE_MODE_TOP_OR_LEFT
                    }
                }
            }
            context.startActivity(intent, options.toBundle())
            true
        }.getOrDefault(false)
    }

    fun launchPackageAdjacent(packageName: String): Boolean {
        val intent = buildLauncherIntent(packageName)?.apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT or
                    Intent.FLAG_ACTIVITY_MULTIPLE_TASK
            )
        } ?: return false
        val options = ActivityOptions.makeBasic().apply {
            launchDisplayId = Display.DEFAULT_DISPLAY
        }
        return runCatching {
            context.startActivity(intent, options.toBundle())
            recordRecent(packageName)
            true
        }.getOrElse {
            runCatching {
                context.startActivity(intent)
                recordRecent(packageName)
                true
            }.getOrDefault(false)
        }
    }

    fun launchPackageFreeform(packageName: String, bounds: Rect): Boolean {
        val intent = buildLauncherIntent(packageName) ?: return false
        intent.addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_MULTIPLE_TASK or
                Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
        )
        return runCatching {
            val options = ActivityOptions.makeBasic().apply {
                launchDisplayId = Display.DEFAULT_DISPLAY
                launchBounds = bounds
                runCatching {
                    val method = ActivityOptions::class.java.getMethod(
                        "setLaunchWindowingMode",
                        Int::class.javaPrimitiveType
                    )
                    method.invoke(this, 5) // WINDOWING_MODE_FREEFORM
                }
            }
            context.startActivity(intent, options.toBundle())
            recordRecent(packageName)
            true
        }.getOrElse {
            launchAppFullscreen(packageName)
        }
    }

    /**
     * Triggers Android system PackageInstaller uninstall prompt (supported on API 28+ via REQUEST_DELETE_PACKAGES).
     */
    @Suppress("DEPRECATION")
    fun requestUninstallApp(packageName: String): Boolean {
        val pkgUri = Uri.parse("package:$packageName")
        val uninstallIntent = Intent(Intent.ACTION_UNINSTALL_PACKAGE, pkgUri).apply {
            putExtra(Intent.EXTRA_RETURN_RESULT, false)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val startedPrimary = runCatching {
            context.startActivity(uninstallIntent)
            true
        }.getOrDefault(false)
        if (startedPrimary) return true

        val deleteIntent = Intent(Intent.ACTION_DELETE, pkgUri).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return runCatching {
            context.startActivity(deleteIntent)
            true
        }.getOrDefault(false)
    }

    fun openPackageDetails(packageName: String) {
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.parse("package:$packageName")
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }

    private fun classifyCategory(pkg: String, label: String): AppCategory {
        val lower = "$pkg ${label.lowercase()}"
        return when {
            listOf("maps", "waze", "navi", "gps", "papago", "polstar", "gaode", "autonavi", "tomtom")
                .any { lower.contains(it) } -> AppCategory.NAVIGATION

            listOf("youtube", "spotify", "music", "kkbox", "netflix", "disney", "video", "player", "podcast", "radio", "mixerbox", "bilibili", "twitch", "aayt", "netease", "cloudmusic", "vlc")
                .any { lower.contains(it) } -> AppCategory.MEDIA

            listOf("line", "whatsapp", "telegram", "messenger", "orca", "dialer", "phone", "contacts", "messaging", "wechat", "discord")
                .any { lower.contains(it) } -> AppCategory.COMMUNICATION

            listOf("settings", "system", "files", "chrome", "browser", "vending", "play")
                .any { lower.contains(it) } -> AppCategory.SYSTEM

            else -> AppCategory.ALL
        }
    }

    private fun drawableToBitmap(drawable: Drawable?, sizePx: Int): Bitmap {
        if (drawable is BitmapDrawable && drawable.bitmap != null) {
            return Bitmap.createScaledBitmap(drawable.bitmap, sizePx, sizePx, true)
        }
        val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        drawable?.setBounds(0, 0, canvas.width, canvas.height)
        drawable?.draw(canvas)
        return bmp
    }
}

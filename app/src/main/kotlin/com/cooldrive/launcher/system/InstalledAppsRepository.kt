package com.cooldrive.launcher.system

import android.app.ActivityOptions
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
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
    val label: String,
    val icon: Bitmap,
    val category: AppCategory
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
                val pkg = info.activityInfo?.packageName ?: return@mapNotNull null
                if (pkg == selfPkg || !seen.add(pkg)) return@mapNotNull null
                val label = info.loadLabel(pm).toString().takeIf { it.isNotBlank() } ?: pkg
                val drawable = runCatching { info.loadIcon(pm) }.getOrNull()
                val bitmap = drawableToBitmap(drawable, 128)
                InstalledAppInfo(
                    packageName = pkg,
                    label = label,
                    icon = bitmap,
                    category = classifyCategory(pkg, label)
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
            // Filter out Android's internal FallbackHome / ResolverActivity placeholders
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
        if (packageName == context.packageName) return
        val next = (_recentPackages.value - packageName).toMutableList()
        next.add(0, packageName)
        _recentPackages.value = next.take(8)
    }

    /**
     * Launches an app full-screen immediately (used by Dock & App Drawer).
     */
    fun launchAppFullscreen(packageName: String): Boolean {
        val intent = pm.getLaunchIntentForPackage(packageName) ?: return false
        intent.addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
        )
        return runCatching {
            context.startActivity(intent)
            recordRecent(packageName)
            true
        }.getOrDefault(false)
    }

    /**
     * Attempts to launch an app into Freeform window bounds (supported on many AI Box ROMs
     * when freeform multi-window is enabled) with seamless fallback to full-screen launch.
     */
    fun launchAppInBoundsOrFullscreen(packageName: String, bounds: Rect?): Boolean {
        val intent = pm.getLaunchIntentForPackage(packageName) ?: return false
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching {
            if (bounds != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                val options = ActivityOptions.makeBasic()
                options.launchBounds = bounds
                context.startActivity(intent, options.toBundle())
            } else {
                context.startActivity(intent)
            }
            recordRecent(packageName)
            true
        }.getOrElse {
            launchAppFullscreen(packageName)
        }
    }

    /**
     * Launches real Google Maps directly into turn-by-turn navigation or search.
     */
    fun launchGoogleMapsNavigation(destinationQuery: String? = null): Boolean {
        val uri = if (destinationQuery.isNullOrBlank()) {
            Uri.parse("geo:0,0?q=")
        } else {
            Uri.parse("google.navigation:q=${Uri.encode(destinationQuery)}")
        }
        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            setPackage("com.google.android.apps.maps")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val ok = runCatching {
            context.startActivity(intent)
            recordRecent("com.google.android.apps.maps")
            true
        }.getOrDefault(false)

        return if (ok) true else launchAppFullscreen("com.google.android.apps.maps")
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

    private fun classifyCategory(pkg: String, label: String): AppCategory {
        val lower = "$pkg ${label.lowercase()}"
        return when {
            listOf("maps", "waze", "navi", "gps", "papago", "polstar", "gaode", "autonavi", "tomtom")
                .any { lower.contains(it) } -> AppCategory.NAVIGATION

            listOf("youtube", "spotify", "music", "kkbox", "netflix", "disney", "video", "player", "podcast", "radio", "mixerbox", "bilibili", "twitch", "aayt")
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

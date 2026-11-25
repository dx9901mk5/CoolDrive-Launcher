package com.dxcar.launcher.data

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.res.Configuration
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

@Serializable
enum class AppLanguage(val code: String, val nativeLabel: String) {
    SYSTEM("system", "跟隨系統 / Auto"),
    ZH_TW("zh-TW", "繁體中文"),
    ZH_CN("zh-CN", "简体中文"),
    EN("en", "English"),
    JA("ja", "日本語")
}

@Serializable
enum class DeviceRole {
    AI_BOX_LAUNCHER,
    PHONE_COMPANION
}

@Serializable
enum class DockPositionMode {
    DRIVER_SIDE,
    FORCE_TOP,
    AUTO_GOOGLE_DPI,
    FORCE_LEFT,
    FORCE_RIGHT,
    FORCE_BOTTOM
}

@Serializable
enum class ThemeMode {
    AUTO_TIME,
    AUTO_SUNRISE_SUNSET,
    FOLLOW_SYSTEM,
    FORCE_DAY,
    FORCE_NIGHT
}

@Serializable
enum class MapEngineStyle(val labelZh: String) {
    GOOGLE_TRAFFIC_TILES("Google 繁中地圖"),
    GOOGLE_SATELLITE_HYBRID("Google 衛星混合"),
    OPENFREEMAP_VECTOR_3D("OpenFreeMap 3D 向量"),
    OPENFREEMAP_DARK("深色極簡向量")
}

@Serializable
enum class HomeExperimentMode(
    val shortTitleZh: String,
    val titleZh: String,
    val descZh: String,
    val isAvailable: Boolean = true,
    val showInSettings: Boolean = true
) {
    PLAN_A_MULTI_LAYER(
        shortTitleZh = "方案 A",
        titleZh = "方案 A：多圖層 ＋ 即時路況 ＋ 離線地圖（推薦）",
        descZh = "左側 70% 內建免 Key 地圖，支援繁中標準／衛星混合／3D 向量／離線地圖下載與即時路況開關"
    ),
    @Deprecated("Plan B removed per real-car test feedback; auto-migrated to Plan A")
    PLAN_B_BUILTIN_NAV(
        shortTitleZh = "方案 B（已停用）",
        titleZh = "方案 B（已取消）",
        descZh = "已整合回方案 A",
        isAvailable = false,
        showInSettings = false
    ),
    PLAN_C_APP_AND_MEDIA(
        shortTitleZh = "方案 C",
        titleZh = "方案 C：指定導航 App ＋ 純粹音樂封面控制",
        descZh = "左側 70% 指定真實導航 App（Google 地圖／導航王等），右側 30% 專注大專輯封面、天氣與 6 格整合",
        isAvailable = false,
        showInSettings = false
    ),
    PLAN_D_ROOT_RESERVED(
        shortTitleZh = "方案 B（預留）",
        titleZh = "方案 B：Root 特權進階模式（預留給已 Root 車機）",
        descZh = "預留給已取得 Root (su) 權限的車機開發中；一般未 Root 車機可正常使用方案 A",
        isAvailable = false,
        showInSettings = true
    );

    companion object {
        val visibleEntries: List<HomeExperimentMode>
            get() = entries.filter { it.showInSettings }
    }
}

@Serializable
enum class HomeMapDisplayMode(val labelZh: String, val descZh: String) {
    BUILTIN_LIVE_MAP("內建免 Key 即時地圖（穩固）", "於 70% 主畫面渲染即時路況／多圖層／離線地圖，點 G地圖 展開原版"),
    REAL_GMAPS_FREEFORM_EMBED("真實 Google 地圖本體貼合（70%）", "以 Freeform 視窗將真實 Google 地圖 App 貼合至首頁 70% 比例位置")
}

@Serializable
enum class SplitRatioPreset(val label: String, val leftWeight: Float) {
    R_3_7("3:7", 0.30f),
    R_4_6("4:6", 0.40f),
    R_5_5("5:5", 0.50f),
    R_6_4("6:4", 0.60f),
    R_7_3("7:3", 0.70f)
}

@Serializable
enum class SplitLaunchEngine(val labelZh: String) {
    NATIVE_SPLIT_SCREEN("AOSP 系統原生可拖動分屏（推薦）"),
    STOCK_LAUNCHER_SHORTCUT("原車啟動器已建立分屏捷徑"),
    FREEFORM_EXACT_RATIO("Freeform 自由雙視窗")
}

@Serializable
enum class GeminiVoiceMode(val labelZh: String) {
    SMART_AUTO("智慧雙通：語音辨識直達導航 ＋ Gemini 語意解析（推薦）"),
    GEMINI_APP_DIRECT("直接喚起 Google Gemini 語音助理 App")
}

@Serializable
data class SplitScreenShortcut(
    val id: String,
    val leftPackage: String,
    val rightPackage: String,
    val ratio: SplitRatioPreset = SplitRatioPreset.R_7_3,
    val engine: SplitLaunchEngine = SplitLaunchEngine.NATIVE_SPLIT_SCREEN,
    val label: String = "",
    val stockIntentUri: String = "",
    val stockShortcutPackage: String = "",
    val stockShortcutId: String = ""
)

@Serializable
data class AppFolderItem(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val packages: List<String>
)

@Serializable
enum class NotificationBannerScale(val factor: Float, val label: String) {
    SCALE_0_5X(0.5f, "0.5X"),
    SCALE_0_75X(0.75f, "0.75X"),
    SCALE_1_0X(1.0f, "1X"),
    SCALE_1_5X(1.5f, "1.5X"),
    SCALE_2_0X(2.0f, "2X")
}

@Serializable
enum class OfflineUpdatePolicy(val days: Int, val labelZh: String) {
    DAYS_7(7, "每 7 天提醒更新"),
    DAYS_15(15, "每 15 天提醒更新"),
    DAYS_30(30, "每 30 天提醒更新（推薦）"),
    MANUAL_ONLY(0, "僅手動更新")
}

@Serializable
data class OfflineRegionRecord(
    val regionId: String,
    val titleZh: String,
    val centerLat: Double,
    val centerLon: Double,
    val minLat: Double,
    val maxLat: Double,
    val minLon: Double,
    val maxLon: Double,
    val downloadedAtMs: Long,
    val approxSizeMb: Float = 0f,
    val maplibreRegionId: Long = -1L
)

@Serializable
enum class LauncherStylePreset(val labelZh: String, val descZh: String) {
    COOLWALK_CLASSIC("AA Coolwalk 經典", "原生圓角分割卡片與溫潤層次"),
    OLED_STEALTH("極致純黑 OLED", "深夜低眩光純黑底與細緻邊框"),
    FROSTED_GLASS("流光毛玻璃", "柔和通透懸浮質感與加寬圓角"),
    HIGH_CONTRAST("高對比行車", "強化文字邊界與大字體清晰辨識")
}

@Serializable
enum class GestureAction {
    NONE,
    GO_HOME,
    SWAP_SPLIT_PANES,
    TOGGLE_DAY_NIGHT,
    TOGGLE_DOCK_VISIBILITY,
    MEDIA_PREV_NEXT,
    BRIGHTNESS_AND_VOLUME,
    OPEN_APP_DRAWER,
    OPEN_NOTIFICATIONS
}

@Serializable
data class LauncherConfig(
    val appLanguage: AppLanguage = AppLanguage.SYSTEM,
    val deviceRole: DeviceRole = DeviceRole.AI_BOX_LAUNCHER,
    val roleUserPinned: Boolean = false,
    val dockPositionMode: DockPositionMode = DockPositionMode.DRIVER_SIDE,
    val rightHandDrive: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.AUTO_TIME,
    val dayStartHour: Int = 6,
    val dayStartMinute: Int = 30,
    val nightStartHour: Int = 18,
    val nightStartMinute: Int = 30,
    val stylePreset: LauncherStylePreset = LauncherStylePreset.COOLWALK_CLASSIC,
    val accentColorIndex: Int = 0,
    val homeExperimentMode: HomeExperimentMode = HomeExperimentMode.PLAN_A_MULTI_LAYER,
    val mapEngineStyle: MapEngineStyle = MapEngineStyle.GOOGLE_TRAFFIC_TILES,
    val mapTrafficEnabled: Boolean = true,
    val homeMapDisplayMode: HomeMapDisplayMode = HomeMapDisplayMode.BUILTIN_LIVE_MAP,
    val planCNavPackage: String = "com.google.android.apps.maps",
    val planCOverlayMusicPanelEnabled: Boolean = true,
    val showSixShortcutsInMediaPanel: Boolean = true,
    val primarySplitRatio: Float = 0.70f,
    val is24HourClock: Boolean = false,
    val prefer60FpsEnabled: Boolean = true,
    val autoCheckAndDownloadUpdate: Boolean = true,
    val showHomeDock: Boolean = true,
    val persistentOverlayDockEnabled: Boolean = true,
    val overlayDockOnOtherLaunchers: Boolean = true,
    val overlayAutoCollapseEnabled: Boolean = true,
    val overlayAutoRetractSeconds: Int = 2,
    val overlayAutoRetractMs: Long = 1500L,
    val overlayHandleThicknessDp: Int = 26,
    val overlayHandlePositionPercent: Float = 0.50f,
    val launchAppsAvoidDockFreeform: Boolean = false,
    val globalCallMessageBannerEnabled: Boolean = true,
    val notificationBannerScale: NotificationBannerScale = NotificationBannerScale.SCALE_1_0X,
    val autoReadMessagesTts: Boolean = false,
    val geminiVoiceMode: GeminiVoiceMode = GeminiVoiceMode.SMART_AUTO,
    val geminiApiKey: String = "",
    val multiFingerGesturesEnabled: Boolean = false,
    val twoFingerVerticalAction: GestureAction = GestureAction.BRIGHTNESS_AND_VOLUME,
    val twoFingerHorizontalAction: GestureAction = GestureAction.MEDIA_PREV_NEXT,
    val threeFingerSwipeDownAction: GestureAction = GestureAction.GO_HOME,
    val threeFingerSwipeHorizontalAction: GestureAction = GestureAction.SWAP_SPLIT_PANES,
    val threeFingerTapAction: GestureAction = GestureAction.TOGGLE_DAY_NIGHT,
    val fourFingerPinchAction: GestureAction = GestureAction.TOGGLE_DOCK_VISIBILITY,
    val pinnedDockPackages: List<String> = DEFAULT_PINNED_PACKAGES,
    val recentDockPackages: List<String> = DEFAULT_PINNED_PACKAGES,
    val customShortcutSlots: List<String> = List(6) { "" },
    val splitShortcuts: List<SplitScreenShortcut> = emptyList(),
    val appFolders: List<AppFolderItem> = emptyList(),
    val lastMediaPackage: String = "",
    val lastMediaAppName: String = "",
    val lastMediaTitle: String = "",
    val lastMediaArtist: String = "",
    val offlineUpdatePolicy: OfflineUpdatePolicy = OfflineUpdatePolicy.DAYS_30,
    val offlineRegions: List<OfflineRegionRecord> = emptyList(),
    val dismissedOfflineUpdateAtMs: Long = 0L,
    val phoneTouchpadSensitivity: Float = 1.5f,
    val homeAddress: String = "",
    val workAddress: String = "",
    val companionTargetDeviceAddress: String = "",
    val companionTargetDeviceName: String = "",
    val companionAutoConnect: Boolean = true,
    val autoCycleWifiOnBtConnect: Boolean = false,
    val companionSyncAllApps: Boolean = false,
    val companionAllowedPackages: List<String> = DEFAULT_COMPANION_PACKAGES,
    val quickReplies: List<String> = DEFAULT_QUICK_REPLIES,
    val lastKnownLat: Double = 25.0330,
    val lastKnownLon: Double = 121.5654
) {
    companion object {
        val DEFAULT_PINNED_PACKAGES = listOf(
            "com.google.android.apps.maps",
            "com.google.android.youtube",
            "com.spotify.music",
            "com.google.android.apps.youtube.music",
            "com.android.dialer"
        )

        val DEFAULT_COMPANION_PACKAGES = listOf(
            "jp.naver.line.android",
            "com.linecorp.linelite",
            "com.facebook.orca",
            "com.whatsapp",
            "com.whatsapp.w4b",
            "org.telegram.messenger",
            "com.google.android.apps.messaging",
            "com.samsung.android.messaging",
            "com.android.mms",
            "com.vivo.mms",
            "com.sie.mms",
            "com.bbk.mms",
            "com.iqoo.mms",
            "com.coloros.mms",
            "com.oplus.mms",
            "com.miui.mms",
            "com.tencent.mm",
            "com.instagram.android",
            "com.instagram.barcelona",
            "com.discord",
            "com.google.android.gm",
            "com.microsoft.teams",
            "com.Slack"
        )

        val DEFAULT_QUICK_REPLIES = listOf(
            "開車中，稍後回覆",
            "馬上到！",
            "好，收到",
            "請稍等一下"
        )
    }
}

object DeviceFormFactor {
    fun isCompactPhoneScreen(configuration: Configuration): Boolean {
        return configuration.screenHeightDp > configuration.screenWidthDp &&
            configuration.screenWidthDp < 580
    }

    fun isFoldableInnerDisplay(configuration: Configuration): Boolean {
        val minSide = minOf(configuration.screenWidthDp, configuration.screenHeightDp)
        val maxSide = maxOf(configuration.screenWidthDp, configuration.screenHeightDp)
        if (minSide < 500) return false
        val aspect = maxSide.toFloat() / minSide.toFloat()
        return aspect < 1.45f
    }

    fun isPhoneDevice(configuration: Configuration): Boolean {
        return isCompactPhoneScreen(configuration) || isFoldableInnerDisplay(configuration)
    }

    fun isTabletOrExpandedScreen(configuration: Configuration): Boolean {
        return configuration.screenWidthDp >= 580 || isFoldableInnerDisplay(configuration)
    }

    fun shouldAutoDefaultToPhone(context: Context, configuration: Configuration, config: LauncherConfig): Boolean {
        if (config.roleUserPinned) return false
        if (!isPhoneDevice(configuration)) return false
        val homeIntent = android.content.Intent(android.content.Intent.ACTION_MAIN)
            .addCategory(android.content.Intent.CATEGORY_HOME)
        val defaultPkg = runCatching {
            context.packageManager.resolveActivity(homeIntent, PackageManager.MATCH_DEFAULT_ONLY)
                ?.activityInfo?.packageName
        }.getOrNull()
        return defaultPkg != context.packageName
    }
}

class LauncherPreferences(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("dxcar_launcher_prefs", Context.MODE_PRIVATE)

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val _config = MutableStateFlow(loadConfig().also {
        com.dxcar.launcher.i18n.DxCarI18n.setConfiguredLanguage(it.appLanguage)
    })
    val config: StateFlow<LauncherConfig> = _config.asStateFlow()

    @Suppress("DEPRECATION")
    private fun loadConfig(): LauncherConfig {
        val raw = prefs.getString(KEY_CONFIG_JSON, null)
        if (raw == null) {
            prefs.edit()
                .putBoolean(KEY_MIGRATED_V140, true)
                .putBoolean(KEY_MIGRATED_V150, true)
                .putBoolean(KEY_MIGRATED_V152, true)
                .apply()
            return LauncherConfig()
        }
        var decoded = runCatching { json.decodeFromString<LauncherConfig>(raw) }
            .getOrElse { LauncherConfig() }

        if (decoded.homeExperimentMode != HomeExperimentMode.PLAN_A_MULTI_LAYER) {
            decoded = decoded.copy(homeExperimentMode = HomeExperimentMode.PLAN_A_MULTI_LAYER)
        }

        if (!prefs.getBoolean(KEY_MIGRATED_V150, false)) {
            val safeMode = if (decoded.homeExperimentMode == HomeExperimentMode.PLAN_B_BUILTIN_NAV) {
                HomeExperimentMode.PLAN_A_MULTI_LAYER
            } else {
                decoded.homeExperimentMode
            }
            val safeDock = if (decoded.dockPositionMode == DockPositionMode.AUTO_GOOGLE_DPI) {
                DockPositionMode.DRIVER_SIDE
            } else {
                decoded.dockPositionMode
            }
            decoded = decoded.copy(
                homeExperimentMode = safeMode,
                dockPositionMode = safeDock,
                multiFingerGesturesEnabled = false,
                overlayHandleThicknessDp = decoded.overlayHandleThicknessDp.coerceAtLeast(26)
            )
            prefs.edit()
                .putString(KEY_CONFIG_JSON, json.encodeToString(decoded))
                .putBoolean(KEY_MIGRATED_V140, true)
                .putBoolean(KEY_MIGRATED_V150, true)
                .apply()
        }

        if (!prefs.getBoolean(KEY_MIGRATED_V152, false)) {
            val nextRetractMs = when (decoded.overlayAutoRetractSeconds) {
                0 -> 0L
                7 -> 5000L
                else -> 1500L
            }
            val seededPackages = (decoded.companionAllowedPackages + LauncherConfig.DEFAULT_COMPANION_PACKAGES).distinct()
            decoded = decoded.copy(
                overlayAutoRetractMs = nextRetractMs,
                companionSyncAllApps = false,
                companionAllowedPackages = seededPackages
            )
            prefs.edit()
                .putString(KEY_CONFIG_JSON, json.encodeToString(decoded))
                .putBoolean(KEY_MIGRATED_V152, true)
                .apply()
        }
        return decoded
    }

    fun update(transform: (LauncherConfig) -> LauncherConfig) {
        val next = transform(_config.value)
        _config.value = next
        com.dxcar.launcher.i18n.DxCarI18n.setConfiguredLanguage(next.appLanguage)
        prefs.edit().putString(KEY_CONFIG_JSON, json.encodeToString(next)).apply()
    }

    fun resetToDefaults() {
        val defaults = LauncherConfig()
        _config.value = defaults
        prefs.edit().putString(KEY_CONFIG_JSON, json.encodeToString(defaults)).apply()
    }

    fun recordLastMediaSession(
        packageName: String,
        appName: String,
        title: String,
        artist: String
    ) {
        if (packageName.isBlank()) return
        val cur = _config.value
        if (cur.lastMediaPackage == packageName &&
            cur.lastMediaAppName == appName &&
            cur.lastMediaTitle == title &&
            cur.lastMediaArtist == artist
        ) {
            return
        }
        update {
            it.copy(
                lastMediaPackage = packageName,
                lastMediaAppName = appName,
                lastMediaTitle = title,
                lastMediaArtist = artist
            )
        }
    }

    fun recordRecentDockPackage(packageName: String) {
        if (packageName.isBlank()) return
        update { current ->
            val next = (listOf(packageName) + current.recentDockPackages.filterNot { it == packageName })
                .take(8)
            current.copy(recentDockPackages = next)
        }
    }

    fun setCustomShortcutSlot(slotIndex: Int, packageName: String) {
        if (slotIndex !in 0..5) return
        update { current ->
            val slots = MutableList(6) { idx -> current.customShortcutSlots.getOrElse(idx) { "" } }
            slots[slotIndex] = packageName
            current.copy(customShortcutSlots = slots)
        }
    }

    fun clearCustomShortcutSlot(slotIndex: Int) {
        setCustomShortcutSlot(slotIndex, "")
    }

    fun addSplitShortcut(shortcut: SplitScreenShortcut) {
        update { current ->
            val next = current.splitShortcuts.filterNot { it.id == shortcut.id } + shortcut
            current.copy(splitShortcuts = next)
        }
    }

    fun removeSplitShortcut(shortcutId: String) {
        update { current ->
            val token = "__split__$shortcutId"
            val cleanedSlots = current.customShortcutSlots.map { if (it == token) "" else it }
            current.copy(
                splitShortcuts = current.splitShortcuts.filterNot { it.id == shortcutId },
                customShortcutSlots = cleanedSlots
            )
        }
    }

    fun createAppFolder(name: String, packages: List<String>) {
        val distinctPkgs = packages.filter { it.isNotBlank() }.distinct()
        if (distinctPkgs.isEmpty()) return
        update { current ->
            // Remove those packages from any existing folder first to prevent duplicates
            val cleanedExisting = current.appFolders.mapNotNull { folder ->
                val rem = folder.packages.filterNot { distinctPkgs.contains(it) }
                if (rem.isEmpty()) null else folder.copy(packages = rem)
            }
            val newFolder = AppFolderItem(
                name = name.trim().ifBlank { "應用程式資料夾" },
                packages = distinctPkgs
            )
            current.copy(appFolders = cleanedExisting + newFolder)
        }
    }

    fun addAppToFolder(folderId: String, packageName: String) {
        if (packageName.isBlank()) return
        update { current ->
            val updated = current.appFolders.mapNotNull { folder ->
                when {
                    folder.id == folderId -> folder.copy(packages = (folder.packages + packageName).distinct())
                    folder.packages.contains(packageName) -> {
                        val rem = folder.packages - packageName
                        if (rem.isEmpty()) null else folder.copy(packages = rem)
                    }
                    else -> folder
                }
            }
            current.copy(appFolders = updated)
        }
    }

    fun renameAppFolder(folderId: String, newName: String) {
        val trimmed = newName.trim().ifBlank { return }
        update { current ->
            current.copy(
                appFolders = current.appFolders.map {
                    if (it.id == folderId) it.copy(name = trimmed) else it
                }
            )
        }
    }

    fun removeAppFromFolder(folderId: String, packageName: String) {
        update { current ->
            val updated = current.appFolders.mapNotNull { folder ->
                if (folder.id != folderId) folder
                else {
                    val rem = folder.packages - packageName
                    if (rem.isEmpty()) null else folder.copy(packages = rem)
                }
            }
            current.copy(appFolders = updated)
        }
    }

    fun deleteAppFolder(folderId: String) {
        update { current ->
            current.copy(appFolders = current.appFolders.filterNot { it.id == folderId })
        }
    }

    companion object {
        private const val KEY_CONFIG_JSON = "config_json_v1"
        private const val KEY_MIGRATED_V140 = "migrated_v140_defaults"
        private const val KEY_MIGRATED_V150 = "migrated_v150_defaults"
        private const val KEY_MIGRATED_V152 = "migrated_v152_defaults"
    }
}

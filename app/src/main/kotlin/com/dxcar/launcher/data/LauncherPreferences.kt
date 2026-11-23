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

@Serializable
enum class DeviceRole {
    AI_BOX_LAUNCHER,
    PHONE_COMPANION
}

@Serializable
enum class DockPositionMode {
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
    val isAvailable: Boolean = true
) {
    PLAN_A_MULTI_LAYER(
        shortTitleZh = "方案 A",
        titleZh = "方案 A：多圖層 ＋ 即時路況開關",
        descZh = "左側 70% 內建免 Key 地圖，自由切換繁中標準／衛星混合／3D 向量／深色向量與即時路況開關"
    ),
    PLAN_B_BUILTIN_NAV(
        shortTitleZh = "方案 B",
        titleZh = "方案 B：方案 A ＋ 免 Key 內建導航試做",
        descZh = "整合 Photon 繁中地點搜尋與 OSRM 路線規劃，直接於首頁 70% 繪製藍線導航與中文 TTS 語音提示"
    ),
    PLAN_C_APP_AND_MEDIA(
        shortTitleZh = "方案 C",
        titleZh = "方案 C：指定導航 App ＋ 純粹音樂封面控制",
        descZh = "左側 70% 指定真實導航 App（Google 地圖／導航王等），右側 30% 專注大專輯封面、自動音源辨識與 6 格整合"
    ),
    PLAN_D_ROOT_RESERVED(
        shortTitleZh = "方案 D（保留）",
        titleZh = "方案 D：Root 特權進階模式（保留開發中）",
        descZh = "需取得車機 Root (su) 權限後啟用全域真常駐 Dock 避讓與系統級雙視窗，敬請期待",
        isAvailable = false
    )
}

@Serializable
enum class HomeMapDisplayMode(val labelZh: String, val descZh: String) {
    BUILTIN_LIVE_MAP("內建免 Key 即時地圖（穩固）", "於 70% 主畫面渲染即時路況／多圖層地圖，點 G地圖 展開原版"),
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
    NATIVE_SPLIT_SCREEN("系統原生分割螢幕"),
    FREEFORM_EXACT_RATIO("Freeform 精準比例雙視窗")
}

@Serializable
data class SplitScreenShortcut(
    val id: String,
    val leftPackage: String,
    val rightPackage: String,
    val ratio: SplitRatioPreset = SplitRatioPreset.R_7_3,
    val engine: SplitLaunchEngine = SplitLaunchEngine.NATIVE_SPLIT_SCREEN,
    val label: String = ""
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
    val deviceRole: DeviceRole = DeviceRole.AI_BOX_LAUNCHER,
    val roleUserPinned: Boolean = false,
    val dockPositionMode: DockPositionMode = DockPositionMode.AUTO_GOOGLE_DPI,
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
    val autoCheckAndDownloadUpdate: Boolean = true,
    val showHomeDock: Boolean = true,
    val persistentOverlayDockEnabled: Boolean = true,
    val overlayAutoCollapseEnabled: Boolean = true,
    val overlayAutoRetractSeconds: Int = 4,
    val overlayHandleThicknessDp: Int = 26,
    val overlayHandlePositionPercent: Float = 0.50f,
    val launchAppsAvoidDockFreeform: Boolean = false,
    val globalCallMessageBannerEnabled: Boolean = true,
    val autoReadMessagesTts: Boolean = false,
    val multiFingerGesturesEnabled: Boolean = true,
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
    val homeAddress: String = "",
    val workAddress: String = "",
    val companionTargetDeviceAddress: String = "",
    val companionTargetDeviceName: String = "",
    val companionAutoConnect: Boolean = true,
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
            "com.facebook.orca",
            "com.whatsapp",
            "org.telegram.messenger",
            "com.google.android.apps.messaging",
            "com.samsung.android.messaging",
            "com.android.mms",
            "com.vivo.mms",
            "com.bbk.mms",
            "com.coloros.mms",
            "com.miui.mms",
            "com.tencent.mm",
            "com.instagram.android",
            "com.discord"
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
    /**
     * Compact Smartphone or Folded Cover Display (portrait & narrow < 580dp).
     * Never misclassifies wide landscape AI Box head units (e.g. 1280x720@240dpi = 853x480dp).
     */
    fun isCompactPhoneScreen(configuration: Configuration): Boolean {
        return configuration.screenHeightDp > configuration.screenWidthDp &&
            configuration.screenWidthDp < 580
    }

    /**
     * Unfolded Foldable inner display (nearly square / large inner screen in either orientation).
     * Car AI Box head units are wide landscape (aspect >= 1.45), so they are never misclassified.
     */
    fun isFoldableInnerDisplay(configuration: Configuration): Boolean {
        val minSide = minOf(configuration.screenWidthDp, configuration.screenHeightDp)
        val maxSide = maxOf(configuration.screenWidthDp, configuration.screenHeightDp)
        if (minSide < 500) return false
        val aspect = maxSide.toFloat() / minSide.toFloat()
        return aspect < 1.45f
    }

    /**
     * Tablet or unfolded foldable screen (>= 580dp width or unfolded foldable inner display).
     * When true, the Phone Companion header reveals a tap toggle to switch to Car Launcher mode.
     */
    fun isTabletOrExpandedScreen(configuration: Configuration): Boolean {
        return configuration.screenWidthDp >= 580 || isFoldableInnerDisplay(configuration)
    }

    /**
     * Auto-selects Phone Companion mode on first launch when opened on a smartphone or foldable
     * (whether folded cover screen OR unfolded inner screen) that is NOT set as system HOME launcher.
     */
    fun shouldAutoDefaultToPhone(context: Context, configuration: Configuration, config: LauncherConfig): Boolean {
        if (config.roleUserPinned) return false
        if (!isCompactPhoneScreen(configuration) && !isFoldableInnerDisplay(configuration)) return false
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

    private val _config = MutableStateFlow(loadConfig())
    val config: StateFlow<LauncherConfig> = _config.asStateFlow()

    private fun loadConfig(): LauncherConfig {
        val raw = prefs.getString(KEY_CONFIG_JSON, null)
        if (raw == null) {
            prefs.edit().putBoolean(KEY_MIGRATED_V140, true).apply()
            return LauncherConfig()
        }
        val decoded = runCatching { json.decodeFromString<LauncherConfig>(raw) }
            .getOrElse { LauncherConfig() }

        if (!prefs.getBoolean(KEY_MIGRATED_V140, false)) {
            val migratedRatio = if (kotlin.math.abs(decoded.primarySplitRatio - 0.63f) < 0.005f) {
                0.70f
            } else {
                decoded.primarySplitRatio
            }
            val migrated = decoded.copy(
                primarySplitRatio = migratedRatio,
                is24HourClock = false,
                overlayHandleThicknessDp = decoded.overlayHandleThicknessDp.coerceAtLeast(26)
            )
            prefs.edit()
                .putString(KEY_CONFIG_JSON, json.encodeToString(migrated))
                .putBoolean(KEY_MIGRATED_V140, true)
                .apply()
            return migrated
        }
        return decoded
    }

    fun update(transform: (LauncherConfig) -> LauncherConfig) {
        val next = transform(_config.value)
        _config.value = next
        prefs.edit().putString(KEY_CONFIG_JSON, json.encodeToString(next)).apply()
    }

    fun resetToDefaults() {
        val defaults = LauncherConfig()
        _config.value = defaults
        prefs.edit().putString(KEY_CONFIG_JSON, json.encodeToString(defaults)).apply()
    }

    fun togglePinnedPackage(packageName: String) {
        update { current ->
            val list = current.pinnedDockPackages.toMutableList()
            if (list.contains(packageName)) {
                list.remove(packageName)
            } else {
                if (list.size >= 6) {
                    list.removeAt(list.lastIndex)
                }
                list.add(packageName)
            }
            current.copy(pinnedDockPackages = list)
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

    companion object {
        private const val KEY_CONFIG_JSON = "config_json_v1"
        private const val KEY_MIGRATED_V140 = "migrated_v140_defaults"
    }
}

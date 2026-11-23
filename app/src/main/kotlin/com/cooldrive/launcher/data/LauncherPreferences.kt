package com.cooldrive.launcher.data

import android.content.Context
import android.content.SharedPreferences
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
enum class MapEngineStyle {
    GOOGLE_TRAFFIC_TILES,
    OPENFREEMAP_VECTOR_3D
}

@Serializable
enum class HomeMapDisplayMode(val labelZh: String, val descZh: String) {
    BUILTIN_LIVE_MAP("內建免 Key 即時地圖（穩固）", "於 63% 主畫面渲染即時路況／3D 向量地圖，點 G地圖 展開原版"),
    REAL_GMAPS_FREEFORM_EMBED("真實 Google 地圖本體貼合（63%）", "以 Freeform 視窗將真實 Google 地圖 App 貼合至首頁 63% 比例位置")
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
    val dockPositionMode: DockPositionMode = DockPositionMode.AUTO_GOOGLE_DPI,
    val rightHandDrive: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.AUTO_TIME,
    val dayStartHour: Int = 6,
    val dayStartMinute: Int = 30,
    val nightStartHour: Int = 18,
    val nightStartMinute: Int = 30,
    val stylePreset: LauncherStylePreset = LauncherStylePreset.COOLWALK_CLASSIC,
    val accentColorIndex: Int = 0,
    val mapEngineStyle: MapEngineStyle = MapEngineStyle.GOOGLE_TRAFFIC_TILES,
    val homeMapDisplayMode: HomeMapDisplayMode = HomeMapDisplayMode.BUILTIN_LIVE_MAP,
    val primarySplitRatio: Float = 0.63f,
    val is24HourClock: Boolean = true,
    val autoCheckAndDownloadUpdate: Boolean = true,
    val showHomeDock: Boolean = true,
    val persistentOverlayDockEnabled: Boolean = true,
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

class LauncherPreferences(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("cooldrive_launcher_prefs", Context.MODE_PRIVATE)

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val _config = MutableStateFlow(loadConfig())
    val config: StateFlow<LauncherConfig> = _config.asStateFlow()

    private fun loadConfig(): LauncherConfig {
        val raw = prefs.getString(KEY_CONFIG_JSON, null) ?: return LauncherConfig()
        return runCatching { json.decodeFromString<LauncherConfig>(raw) }
            .getOrElse { LauncherConfig() }
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
    }
}

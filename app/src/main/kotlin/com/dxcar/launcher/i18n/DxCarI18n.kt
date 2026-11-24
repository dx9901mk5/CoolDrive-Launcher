package com.dxcar.launcher.i18n

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import com.dxcar.launcher.data.AppLanguage
import com.dxcar.launcher.data.DockPositionMode
import com.dxcar.launcher.data.GeminiVoiceMode
import com.dxcar.launcher.data.HomeExperimentMode
import com.dxcar.launcher.data.HomeMapDisplayMode
import com.dxcar.launcher.data.LauncherStylePreset
import com.dxcar.launcher.data.MapEngineStyle
import com.dxcar.launcher.data.OfflineUpdatePolicy
import com.dxcar.launcher.data.SplitLaunchEngine
import com.dxcar.launcher.data.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

enum class EffectiveLanguage {
    ZH_TW,
    ZH_CN,
    EN,
    JA
}

val LocalAppLanguage = staticCompositionLocalOf { EffectiveLanguage.ZH_TW }

object DxCarI18n {

    private val _configuredLanguage = MutableStateFlow(AppLanguage.SYSTEM)
    private val _effectiveLanguage = MutableStateFlow(resolveFromSystem())
    val effectiveLanguage: StateFlow<EffectiveLanguage> = _effectiveLanguage.asStateFlow()

    fun setConfiguredLanguage(lang: AppLanguage) {
        _configuredLanguage.value = lang
        _effectiveLanguage.value = resolveEffective(lang)
    }

    fun resolveEffective(lang: AppLanguage): EffectiveLanguage = when (lang) {
        AppLanguage.SYSTEM -> resolveFromSystem()
        AppLanguage.ZH_TW -> EffectiveLanguage.ZH_TW
        AppLanguage.ZH_CN -> EffectiveLanguage.ZH_CN
        AppLanguage.EN -> EffectiveLanguage.EN
        AppLanguage.JA -> EffectiveLanguage.JA
    }

    private fun resolveFromSystem(): EffectiveLanguage {
        val locale = Locale.getDefault()
        val lang = locale.language.lowercase()
        val country = locale.country.uppercase()
        val script = locale.script.lowercase()
        return when (lang) {
            "ja" -> EffectiveLanguage.JA
            "zh" -> when {
                script == "hans" -> EffectiveLanguage.ZH_CN
                script == "hant" -> EffectiveLanguage.ZH_TW
                country in setOf("CN", "SG") -> EffectiveLanguage.ZH_CN
                else -> EffectiveLanguage.ZH_TW
            }
            "en" -> EffectiveLanguage.EN
            else -> EffectiveLanguage.EN
        }
    }

    fun current(): EffectiveLanguage = _effectiveLanguage.value

    fun currentLocale(): Locale = when (current()) {
        EffectiveLanguage.ZH_TW -> Locale.TAIWAN
        EffectiveLanguage.ZH_CN -> Locale.SIMPLIFIED_CHINESE
        EffectiveLanguage.EN -> Locale.US
        EffectiveLanguage.JA -> Locale.JAPAN
    }

    fun speechLanguageTag(): String = when (current()) {
        EffectiveLanguage.ZH_TW -> "zh-TW"
        EffectiveLanguage.ZH_CN -> "zh-CN"
        EffectiveLanguage.EN -> "en-US"
        EffectiveLanguage.JA -> "ja-JP"
    }

    fun nominatimAcceptLanguage(): String = when (current()) {
        EffectiveLanguage.ZH_TW -> "zh-TW,zh;q=0.9,en;q=0.8"
        EffectiveLanguage.ZH_CN -> "zh-CN,zh;q=0.9,en;q=0.8"
        EffectiveLanguage.EN -> "en,zh-TW;q=0.7"
        EffectiveLanguage.JA -> "ja,en;q=0.8"
    }

    fun tr(zhTw: String, zhCn: String, en: String, ja: String): String = when (current()) {
        EffectiveLanguage.ZH_TW -> zhTw
        EffectiveLanguage.ZH_CN -> zhCn
        EffectiveLanguage.EN -> en
        EffectiveLanguage.JA -> ja
    }

    fun tr(zhTw: String, en: String, ja: String): String = when (current()) {
        EffectiveLanguage.ZH_TW -> zhTw
        EffectiveLanguage.ZH_CN -> toSimplifiedChinese(zhTw)
        EffectiveLanguage.EN -> en
        EffectiveLanguage.JA -> ja
    }

    @Composable
    fun str(zhTw: String, zhCn: String, en: String, ja: String): String = when (LocalAppLanguage.current) {
        EffectiveLanguage.ZH_TW -> zhTw
        EffectiveLanguage.ZH_CN -> zhCn
        EffectiveLanguage.EN -> en
        EffectiveLanguage.JA -> ja
    }

    @Composable
    fun str(zhTw: String, en: String, ja: String): String = when (LocalAppLanguage.current) {
        EffectiveLanguage.ZH_TW -> zhTw
        EffectiveLanguage.ZH_CN -> toSimplifiedChinese(zhTw)
        EffectiveLanguage.EN -> en
        EffectiveLanguage.JA -> ja
    }

    /**
     * High-accuracy Traditional -> Simplified Chinese converter tailored for car launcher UI terminology.
     */
    fun toSimplifiedChinese(input: String): String {
        if (input.isEmpty()) return input
        var out = input
        for ((trad, simp) in PHRASE_TRAD_TO_SIMP) {
            if (out.contains(trad)) {
                out = out.replace(trad, simp)
            }
        }
        val sb = StringBuilder(out.length)
        for (ch in out) {
            sb.append(CHAR_TRAD_TO_SIMP[ch] ?: ch)
        }
        return sb.toString()
    }

    // ==================== ENUM LOCALIZATION HELPERS ====================

    fun appLanguageLabel(lang: AppLanguage, currentUi: EffectiveLanguage = current()): String = when (lang) {
        AppLanguage.SYSTEM -> when (currentUi) {
            EffectiveLanguage.ZH_TW -> "跟隨系統"
            EffectiveLanguage.ZH_CN -> "跟随系统"
            EffectiveLanguage.EN -> "Follow System"
            EffectiveLanguage.JA -> "システム設定"
        }
        AppLanguage.ZH_TW -> "繁體中文"
        AppLanguage.ZH_CN -> "简体中文"
        AppLanguage.EN -> "English"
        AppLanguage.JA -> "日本語"
    }

    @Composable
    fun mapEngineLabel(style: MapEngineStyle): String = when (style) {
        MapEngineStyle.GOOGLE_TRAFFIC_TILES -> str("Google 繁中地圖", "Google 中文地图", "Google Maps", "Google マップ")
        MapEngineStyle.GOOGLE_SATELLITE_HYBRID -> str("Google 衛星混合", "Google 卫星混合", "Google Satellite", "Google 衛星写真")
        MapEngineStyle.OPENFREEMAP_VECTOR_3D -> str("OpenFreeMap 3D 向量", "OpenFreeMap 3D 矢量", "OpenFreeMap 3D", "OpenFreeMap 3D")
        MapEngineStyle.OPENFREEMAP_DARK -> str("深色極簡向量", "深色极简矢量", "Dark Vector", "ダークベクター")
    }

    @Composable
    fun experimentTitle(mode: HomeExperimentMode): String = when (mode) {
        HomeExperimentMode.PLAN_A_MULTI_LAYER -> str(
            "方案 A：多圖層 ＋ 即時路況 ＋ 離線地圖（推薦）",
            "方案 A：多图层 ＋ 实时路况 ＋ 离线地图（推荐）",
            "Plan A: Multi-Layer + Live Traffic + Offline Maps (Recommended)",
            "プラン A：マルチレイヤー＋交通状況＋オフライン地図（推奨）"
        )
        HomeExperimentMode.PLAN_B_BUILTIN_NAV -> str("方案 B（已取消）", "Plan B (Disabled)", "プラン B（無効）")
        HomeExperimentMode.PLAN_C_APP_AND_MEDIA -> str(
            "方案 C：指定導航 App ＋ 純粹音樂封面控制",
            "方案 C：指定导航 App ＋ 纯粹音乐封面控制",
            "Plan C: Custom Nav App + Pure Album Art Media",
            "プラン C：指定ナビアプリ＋大型アルバムアート音楽"
        )
        HomeExperimentMode.PLAN_D_ROOT_RESERVED -> str(
            "方案 B：Root 特權進階模式（預留給已 Root 車機）",
            "方案 B：Root 特权进阶模式（预留给已 Root 车机）",
            "Plan B: Root Privilege Mode (Reserved for Rooted Devices)",
            "プラン B：Root 特権モード（Root 済み車載機向け予約）"
        )
    }

    @Composable
    fun experimentDesc(mode: HomeExperimentMode): String = when (mode) {
        HomeExperimentMode.PLAN_A_MULTI_LAYER -> str(
            "左側 70% 內建免 Key 地圖，支援標準／衛星混合／3D 向量／離線地圖下載與即時路況開關",
            "左侧 70% 内置免 Key 地图，支持标准／卫星混合／3D 矢量／离线地图下载与实时路况开关",
            "70% built-in key-free map with Standard, Satellite, 3D Vector, Offline Maps & Live Traffic",
            "左側 70% キー不要内蔵マップ（標準・衛星・3D・オフライン地図＆渋滞情報対応）"
        )
        HomeExperimentMode.PLAN_B_BUILTIN_NAV -> str("已整合回方案 A", "Integrated into Plan A", "プラン A に統合済み")
        HomeExperimentMode.PLAN_C_APP_AND_MEDIA -> str(
            "左側 70% 指定真實導航 App（Google 地圖／導航王等），右側 30% 專注大專輯封面、天氣與 6 格整合",
            "左侧 70% 指定真实导航 App（Google 地图／高德等），右侧 30% 专注大专辑封面、天气与 6 格整合",
            "70% real navigation app on left, 30% large album art, weather & 6 shortcuts on right",
            "左側 70% に任意のナビアプリ、右側 30% に大型ジャケット・天気・6ショートカットを配置"
        )
        HomeExperimentMode.PLAN_D_ROOT_RESERVED -> str(
            "預留給已取得 Root (su) 權限的車機開發中；一般未 Root 車機可完整使用方案 A",
            "预留给已获取 Root (su) 权限的车机开发中；一般未 Root 车机可完整使用方案 A",
            "Reserved for already-rooted AI Box devices (su); non-rooted devices use Plan A normally",
            "Root (su) 権限取得済みの車載機向けに予約中（通常端末はプラン A を利用可能）"
        )
    }

    @Composable
    fun homeMapDisplayLabel(mode: HomeMapDisplayMode): String = when (mode) {
        HomeMapDisplayMode.BUILTIN_LIVE_MAP -> str(
            "內建免 Key 即時地圖（穩固）",
            "Built-in Key-Free Live Map (Stable)",
            "内蔵ライブマップ（安定）"
        )
        HomeMapDisplayMode.REAL_GMAPS_FREEFORM_EMBED -> str(
            "真實 Google 地圖本體貼合（70%）",
            "Real Google Maps Freeform Fit (70%)",
            "Google マップ本体フィット（70%）"
        )
    }

    @Composable
    fun homeMapDisplayDesc(mode: HomeMapDisplayMode): String = when (mode) {
        HomeMapDisplayMode.BUILTIN_LIVE_MAP -> str(
            "於 70% 主畫面渲染即時路況／多圖層／離線地圖，點 G地圖 展開原版",
            "Renders live traffic, layers & offline map in 70% pane; tap Maps to launch full app",
            "70% メイン画面に交通・レイヤー・オフライン地図を表示し、Gマップで本体を起動"
        )
        HomeMapDisplayMode.REAL_GMAPS_FREEFORM_EMBED -> str(
            "以 Freeform 視窗將真實 Google 地圖 App 貼合至首頁 70% 比例位置",
            "Snaps the real Google Maps app into the 70% home window via Freeform mode",
            "Freeform ウィンドウで Google マップ本体をホーム 70% 領域にぴったり配置"
        )
    }

    @Composable
    fun splitEngineLabel(engine: SplitLaunchEngine): String = when (engine) {
        SplitLaunchEngine.NATIVE_SPLIT_SCREEN -> str(
            "AOSP 系統原生可拖動分屏（推薦）",
            "AOSP Native Draggable Split-Screen (Recommended)",
            "AOSP ネイティブ分割画面（推奨）"
        )
        SplitLaunchEngine.STOCK_LAUNCHER_SHORTCUT -> str(
            "原車啟動器已建立分屏捷徑",
            "Stock Launcher Split Shortcut",
            "純正ランチャー分割ショートカット"
        )
        SplitLaunchEngine.FREEFORM_EXACT_RATIO -> str(
            "Freeform 自由雙視窗",
            "Freeform Dual Windows",
            "Freeform デュアルウィンドウ"
        )
    }

    @Composable
    fun geminiVoiceModeLabel(mode: GeminiVoiceMode): String = when (mode) {
        GeminiVoiceMode.SMART_AUTO -> str(
            "智慧雙通：語音辨識直達導航 ＋ Gemini 語意解析（推薦）",
            "Smart Dual: Speech-to-Nav + Gemini AI Parsing (Recommended)",
            "スマート連携：音声認識ナビ ＋ Gemini AI 解析（推奨）"
        )
        GeminiVoiceMode.GEMINI_APP_DIRECT -> str(
            "直接喚起 Google Gemini 語音助理 App",
            "Launch Google Gemini Assistant Directly",
            "Google Gemini アシスタントを直接起動"
        )
    }

    @Composable
    fun offlinePolicyLabel(policy: OfflineUpdatePolicy): String = when (policy) {
        OfflineUpdatePolicy.DAYS_7 -> str("每 7 天提醒更新", "Remind Every 7 Days", "7日ごとに更新通知")
        OfflineUpdatePolicy.DAYS_15 -> str("每 15 天提醒更新", "Remind Every 15 Days", "15日ごとに更新通知")
        OfflineUpdatePolicy.DAYS_30 -> str("每 30 天提醒更新（推薦）", "Every 30 Days (Recommended)", "30日ごとに通知（推奨）")
        OfflineUpdatePolicy.MANUAL_ONLY -> str("僅手動更新", "Manual Only", "手動更新のみ")
    }

    @Composable
    fun dockModeLabel(mode: DockPositionMode, rightHandDrive: Boolean): String = when (mode) {
        DockPositionMode.DRIVER_SIDE -> if (rightHandDrive) {
            str("預設駕駛側（右駕：右側直欄）", "Driver Side (RHD: Right Rail)", "運転席側（右ハンドル：右側）")
        } else {
            str("預設駕駛側（左駕：左側直欄）", "Driver Side (LHD: Left Rail)", "運転席側（左ハンドル：左側）")
        }
        DockPositionMode.FORCE_TOP -> str("放置於上方（頂部橫欄）", "Top Horizontal Bar", "上部バー（横向き）")
        DockPositionMode.AUTO_GOOGLE_DPI -> str("依 DPI 自動判斷", "Auto by Screen DPI", "DPI 自動判定")
        DockPositionMode.FORCE_LEFT -> str("固定左側直欄", "Fixed Left Rail", "左側固定バー")
        DockPositionMode.FORCE_RIGHT -> str("固定右側直欄", "Fixed Right Rail", "右側固定バー")
        DockPositionMode.FORCE_BOTTOM -> str("固定底部橫欄", "Fixed Bottom Bar", "下部固定バー")
    }

    @Composable
    fun stylePresetLabel(preset: LauncherStylePreset): String = when (preset) {
        LauncherStylePreset.COOLWALK_CLASSIC -> str("AA Coolwalk 經典", "AA Coolwalk Classic", "AA Coolwalk クラシック")
        LauncherStylePreset.OLED_STEALTH -> str("極致純黑 OLED", "Pure Black OLED", "ピュアブラック OLED")
        LauncherStylePreset.FROSTED_GLASS -> str("流光毛玻璃", "Frosted Glass", "フロストガラス")
        LauncherStylePreset.HIGH_CONTRAST -> str("高對比行車", "High Contrast Drive", "ハイコントラスト")
    }

    @Composable
    fun themeModeLabel(mode: ThemeMode): String = when (mode) {
        ThemeMode.AUTO_TIME -> str("依時間自動切換", "Auto by Time", "時刻で自動切替")
        ThemeMode.AUTO_SUNRISE_SUNSET -> str("依 GPS 日出／日落自動切換", "Auto by GPS Sunrise/Sunset", "GPS 日の出/日の入り連動")
        ThemeMode.FOLLOW_SYSTEM -> str("跟隨車機系統深淺模式", "Follow System Theme", "システム設定に従う")
        ThemeMode.FORCE_DAY -> str("固定白天模式", "Always Day Mode", "常に昼間モード")
        ThemeMode.FORCE_NIGHT -> str("固定夜晚模式", "Always Night Mode", "常に夜間モード")
    }

    @Composable
    fun accentName(index: Int, fallbackZh: String): String = when (index) {
        0 -> str("Google 藍", "Google Blue", "Google ブルー")
        1 -> str("車載翠綠", "Emerald Green", "エメラルド")
        2 -> str("極光紫", "Aurora Purple", "オーロラパープル")
        3 -> str("跑車琥珀", "Sport Amber", "スポーツアンバー")
        4 -> str("競速紅", "Racing Red", "レーシングレッド")
        else -> str(fallbackZh, fallbackZh, fallbackZh)
    }

    private val PHRASE_TRAD_TO_SIMP = listOf(
        "繁體中文" to "繁体中文",
        "繁中地圖" to "中文地图",
        "繁中語音" to "中文语音",
        "繁中" to "中文",
        "啟動器" to "启动器",
        "車機盒" to "车机盒",
        "車機" to "车机",
        "藍牙" to "蓝牙",
        "熱點" to "热点",
        "無障礙" to "无障碍",
        "權限" to "权限",
        "預設" to "默认",
        "分割視窗" to "分屏窗口",
        "資料夾" to "文件夹",
        "解除安裝" to "卸载",
        "簡訊" to "短信",
        "螢幕" to "屏幕",
        "觸控板" to "触控板",
        "語音" to "语音",
        "導航" to "导航",
        "離線" to "离线",
        "圖示" to "图标",
        "圖層" to "图层",
        "選單" to "菜单",
        "設定" to "设置",
        "關閉" to "关闭",
        "開啟" to "开启",
        "搜尋" to "搜索",
        "下載" to "下载",
        "進度" to "进度",
        "網路" to "网络",
        "連線" to "连接",
        "裝置" to "设备",
        "檔案" to "文件",
        "儲存" to "保存",
        "確定" to "确定",
        "確認" to "确认",
        "編輯" to "编辑",
        "推薦" to "推荐",
        "雙指" to "双指",
        "三指" to "三指",
        "四指" to "四指",
        "單指" to "单指",
        "長壓" to "长按",
        "長按" to "长按",
        "輕點" to "轻点",
        "滑動" to "滑动",
        "縮放" to "缩放",
        "切換" to "切换",
        "介面" to "界面",
        "自動" to "自动",
        "手動" to "手动",
        "天氣" to "天气",
        "當地" to "当地",
        "音樂" to "音乐",
        "專輯" to "专辑",
        "暫停" to "暂停",
        "點讚" to "点赞",
        "來電" to "来电",
        "訊息" to "消息",
        "回覆" to "回复",
        "接聽" to "接听",
        "掛斷" to "挂断",
        "朗讀" to "朗读",
        "測試" to "测试",
        "說明" to "说明",
        "教學" to "教程",
        "多工" to "多任务",
        "首頁" to "首页",
        "側邊" to "侧边",
        "頂部" to "顶部",
        "左側" to "左侧",
        "右側" to "右侧",
        "左駕" to "左舵",
        "右駕" to "右舵",
        "懸浮" to "悬浮",
        "常駐" to "常驻",
        "收合" to "收起",
        "展開" to "展开",
        "鎖定" to "锁定",
        "震動" to "震动",
        "靈敏度" to "灵敏度",
        "移速" to "移速",
        "檢查" to "检查",
        "安裝" to "安装",
        "狀態" to "状态",
        "失敗" to "失败",
        "應用程式" to "应用",
        "手機" to "手机",
        "滑鼠" to "鼠标",
        "游標" to "光标",
        "指標" to "指针",
        "橫幅" to "横幅",
        "圖釘" to "图钉",
        "釘選" to "固定",
        "迴圈" to "循环",
        "無窮" to "无限",
        "無線" to "无线",
        "無限" to "无限",
        "純黑" to "纯黑",
        "衛星" to "卫星",
        "向量" to "矢量",
        "即時" to "实时",
        "路況" to "路况",
        "車速" to "车速",
        "方向" to "方向",
        "夜間" to "夜间",
        "極簡" to "极简",
        "傳統" to "传统",
        "經典" to "经典",
        "高對比" to "高对比",
        "毛玻璃" to "毛玻璃",
        "電量" to "电量",
        "省電" to "省电",
        "待機" to "待机",
        "喚醒" to "唤醒",
        "橋接" to "桥接",
        "通道" to "通道",
        "攔截" to "拦截",
        "過濾" to "过滤",
        "清脆" to "清脆",
        "原廠" to "原厂",
        "原車" to "原车",
        "捷徑" to "快捷方式",
        "九宮格" to "九宫格",
        "比例" to "比例",
        "內建" to "内置",
        "內嵌" to "内嵌",
        "全螢幕" to "全屏",
        "區網" to "局域网",
        "附近" to "附近",
        "周邊" to "周边",
        "北北基桃" to "北北基桃",
        "台灣" to "台湾",
        "臺灣" to "台湾",
        "本島" to "本岛",
        "離島" to "离岛",
        "地區" to "地区",
        "國家" to "国家",
        "區域" to "区域",
        "繪製" to "绘制",
        "目的地" to "目的地",
        "公司" to "公司",
        "回家" to "回家",
        "備援" to "备用",
        "診斷" to "诊断",
        "紀錄" to "记录",
        "更新" to "更新",
        "版本" to "版本",
        "授權" to "授权",
        "未授權" to "未授权",
        "已授權" to "已授权"
    )

    private val CHAR_TRAD_TO_SIMP: Map<Char, Char> = mapOf(
        '體' to '体', '繁' to '繁', '簡' to '简', '啟' to '启', '動' to '动',
        '車' to '车', '機' to '机', '藍' to '蓝', '熱' to '热', '點' to '点', '無' to '无',
        '障' to '障', '礙' to '碍', '權' to '权', '預' to '预', '設' to '设', '視' to '视',
        '窗' to '窗', '資' to '资', '夾' to '夹', '訊' to '讯', '螢' to '荧',
        '觸' to '触', '語' to '语', '導' to '导', '離' to '离', '線' to '线', '圖' to '图',
        '選' to '选', '單' to '单', '開' to '开', '關' to '关', '閉' to '闭', '搜' to '搜',
        '尋' to '寻', '載' to '载', '進' to '进', '網' to '网', '連' to '连', '裝' to '装',
        '檔' to '档', '案' to '案', '儲' to '储', '確' to '确', '認' to '认', '編' to '编',
        '輯' to '辑', '推' to '推', '薦' to '荐', '雙' to '双', '長' to '长', '壓' to '压',
        '輕' to '轻', '滑' to '滑', '縮' to '缩', '切' to '切', '換' to '换', '自' to '自',
        '手' to '手', '氣' to '气', '當' to '当', '音' to '音', '樂' to '乐', '專' to '专',
        '暫' to '暂', '讚' to '赞', '來' to '来', '電' to '电', '回' to '回', '覆' to '复',
        '接' to '接', '聽' to '听', '掛' to '挂', '斷' to '断', '讀' to '读', '測' to '测',
        '試' to '试', '說' to '说', '教' to '教', '學' to '学', '多' to '多', '首' to '首',
        '頁' to '页', '側' to '侧', '邊' to '边', '頂' to '顶', '左' to '左', '右' to '右',
        '駕' to '驾', '懸' to '悬', '駐' to '驻', '展' to '展', '鎖' to '锁', '震' to '震',
        '靈' to '灵', '敏' to '敏', '檢' to '检', '查' to '查', '狀' to '状', '態' to '态',
        '失' to '失', '敗' to '败', '應' to '应', '鼠' to '鼠', '標' to '标', '橫' to '横',
        '釘' to '钉', '迴' to '回', '純' to '纯', '衛' to '卫', '向' to '向', '時' to '时',
        '況' to '况', '間' to '间', '極' to '极', '傳' to '传', '統' to '统', '經' to '经',
        '典' to '典', '對' to '对', '省' to '省', '喚' to '唤', '橋' to '桥', '攔' to '拦',
        '濾' to '滤', '廠' to '厂', '捷' to '捷', '徑' to '径', '宮' to '宫', '內' to '内',
        '區' to '区', '週' to '周', '灣' to '湾', '島' to '岛', '國' to '国', '域' to '域',
        '繪' to '绘', '備' to '备', '診' to '诊', '紀' to '纪', '錄' to '录', '授' to '授',
        '與' to '与', '為' to '为', '於' to '于', '從' to '从', '僅' to '仅', '還' to '还',
        '這' to '这', '個' to '个', '們' to '们', '會' to '会', '沒' to '没', '讓' to '让',
        '給' to '给', '過' to '过', '後' to '后', '裡' to '里', '並' to '并', '則' to '则',
        '將' to '将', '可' to '可', '已' to '已', '未' to '未', '請' to '请', '點' to '点',
        '擊' to '击', '按' to '按', '鈕' to '钮', '列' to '列', '表' to '表', '項' to '项',
        '組' to '组', '合' to '合', '分' to '分', '割' to '割', '比' to '比', '例' to '例',
        '顯' to '显', '示' to '示', '隱' to '隐', '藏' to '藏', '調' to '调', '整' to '整',
        '高' to '高', '低' to '低', '亮' to '亮', '暗' to '暗', '深' to '深', '淺' to '浅',
        '紅' to '红', '綠' to '绿', '藍' to '蓝', '黃' to '黄', '紫' to '紫', '白' to '白',
        '黑' to '黑', '灰' to '灰', '滿' to '满', '空' to '空', '新' to '新', '舊' to '旧',
        '增' to '增', '刪' to '删', '除' to '除', '清' to '清', '重' to '重', '複' to '复',
        '製' to '制', '貼' to '贴', '移' to '移', '拖' to '拖', '曳' to '曳', '放' to '放',
        '大' to '大', '小' to '小', '寬' to '宽', '窄' to '窄', '厚' to '厚', '薄' to '薄',
        '快' to '快', '慢' to '慢', '強' to '强', '弱' to '弱', '穩' to '稳', '定' to '定',
        '許' to '许', '禁' to '禁', '止' to '止', '警' to '警', '告' to '告', '提' to '提',
        '醒' to '醒', '錯' to '错', '誤' to '误', '成' to '成', '功' to '功', '完' to '完',
        '待' to '待', '處' to '处', '理' to '理', '運' to '运', '行' to '行', '作' to '作',
        '業' to '业', '系' to '系', '統' to '统', '硬' to '硬', '軟' to '软', '件' to '件',
        '實' to '实', '際' to '际', '虛' to '虚', '擬' to '拟', '真' to '真', '假' to '假',
        '正' to '正', '常' to '常', '異' to '异', '優' to '优', '化' to '化', '省' to '省',
        '能' to '能', '耗' to '耗', '數' to '数', '據' to '据', '量' to '量', '值' to '值',
        '範' to '范', '圍' to '围', '限' to '限', '制' to '制', '條' to '条', '件' to '件',
        '規' to '规', '則' to '则', '模' to '模', '式' to '式', '風' to '风', '格' to '格',
        '主' to '主', '題' to '题', '配' to '配', '色' to '色', '背' to '背', '景' to '景',
        '前' to '前', '字' to '字', '體' to '体', '號' to '号', '碼' to '码', '名' to '名',
        '稱' to '称', '類' to '类', '別' to '别', '分' to '分', '群' to '群', '標' to '标',
        '籤' to '签', '卡' to '卡', '片' to '片', '框' to '框', '格' to '格', '欄' to '栏',
        '位' to '位', '置' to '置', '距' to '距', '離' to '离', '角' to '角', '度' to '度',
        '圓' to '圆', '方' to '方', '線' to '线', '條' to '条', '圈' to '圈', '環' to '环',
        '箭' to '箭', '頭' to '头', '心' to '心', '星' to '星', '雲' to '云', '端' to '端',
        '地' to '地', '址' to '址', '位' to '位', '經' to '经', '緯' to '纬', '軌' to '轨',
        '跡' to '迹', '路' to '路', '線' to '线', '轉' to '转', '彎' to '弯', '直' to '直',
        '停' to '停', '車' to '车', '場' to '场', '油' to '油', '站' to '站', '充' to '充',
        '超' to '超', '商' to '商', '餐' to '餐', '廳' to '厅', '醫' to '医', '院' to '院',
        '家' to '家', '居' to '居', '辦' to '办', '公' to '公', '室' to '室', '全' to '全',
        '部' to '部', '總' to '总', '計' to '计', '平' to '平', '均' to '均', '最' to '最',
        '更' to '更', '多' to '多', '少' to '少', '詳' to '详', '細' to '细', '情' to '情',
        '簡' to '简', '介' to '介', '說' to '说', '明' to '明', '步' to '步', '驟' to '骤',
        '建' to '建', '議' to '议', '注' to '注', '意' to '意', '事' to '事', '項' to '项',
        '幫' to '帮', '助' to '助', '支' to '支', '援' to '援', '相' to '相', '容' to '容',
        '獨' to '独', '立' to '立', '同' to '同', '步' to '步', '雙' to '双', '向' to '向',
        '傳' to '传', '送' to '送', '收' to '收', '取' to '取', '讀' to '读', '寫' to '写',
        '入' to '入', '出' to '出', '發' to '发', '布' to '布', '佈' to '布', '上' to '上',
        '下' to '下', '中' to '中', '外' to '外', '裡' to '里', '邊' to '边', '旁' to '旁',
        '附' to '附', '近' to '近', '遠' to '远', '程' to '程', '控' to '控', '制' to '制',
        '器' to '器', '播' to '播', '放' to '放', '曲' to '曲', '歌' to '歌', '詞' to '词',
        '手' to '手', '續' to '续', '隨' to '随', '機' to '机', '循' to '循', '環' to '环',
        '靜' to '静', '音' to '音', '麥' to '麦', '克' to '克', '風' to '风', '喇' to '喇',
        '叭' to '叭', '耳' to '耳', '機' to '机', '鐘' to '钟', '錶' to '表', '秒' to '秒',
        '分' to '分', '時' to '时', '天' to '天', '週' to '周', '月' to '月', '年' to '年',
        '今' to '今', '明' to '明', '昨' to '昨', '早' to '早', '晚' to '晚', '午' to '午',
        '夜' to '夜', '日' to '日', '晨' to '晨', '昏' to '昏', '晴' to '晴', '陰' to '阴',
        '雨' to '雨', '雷' to '雷', '雪' to '雪', '霧' to '雾', '濕' to '湿', '溫' to '温',
        '度' to '度', '感' to '感', '體' to '体', '壓' to '压', '風' to '风', '速' to '速'
    )
}

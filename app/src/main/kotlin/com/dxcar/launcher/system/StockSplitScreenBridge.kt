package com.dxcar.launcher.system

import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Process
import android.provider.Settings
import com.dxcar.launcher.data.SplitLaunchEngine
import com.dxcar.launcher.data.SplitRatioPreset
import com.dxcar.launcher.data.SplitScreenShortcut
import java.util.UUID

data class StockSplitScanResult(
    val discoveredShortcuts: List<SplitScreenShortcut>,
    val statusSummaryZh: String,
    val detailReportZh: String
)

/**
 * Scans the AI Box's stock launcher(s) and system tables to discover pre-created Split-Screen pairs:
 * 1. LauncherApps Pinned / Dynamic / Manifest App-Pair Shortcuts (works when DX-Car is set as Default Home!)
 * 2. Stock Launcher ContentProviders (e.g. Launcher3 / Carlinkit / Autochips `favorites` & `apppair` tables)
 * 3. Settings.System & Settings.Global OEM pre-configured dual-window / split-screen package pairs
 * 4. Standard Android System Shortcut Picker (`ACTION_PICK_ACTIVITY` + `ACTION_CREATE_SHORTCUT`)
 */
object StockSplitScreenBridge {

    fun scanStockSplitShortcuts(
        context: Context,
        installedApps: List<InstalledAppInfo>
    ): StockSplitScanResult {
        val pm = context.packageManager
        val installedPkgSet = installedApps.map { it.packageName }.toSet()
        val found = LinkedHashMap<String, SplitScreenShortcut>()
        val notes = mutableListOf<String>()

        // Identify all installed Home Launchers (excluding ourselves) + SystemUI
        val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val stockLauncherPkgs = runCatching {
            pm.queryIntentActivities(homeIntent, PackageManager.MATCH_ALL)
                .mapNotNull { it.activityInfo?.packageName }
                .filter { it != context.packageName && it != "android" }
                .distinct()
        }.getOrDefault(emptyList())

        // 1. Query LauncherApps ShortcutInfo (Android QuickStep App Pairs & OEM Launcher Shortcuts)
        val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as? LauncherApps
        if (launcherApps != null) {
            val hasHostPerm = runCatching { launcherApps.hasShortcutHostPermission() }.getOrDefault(false)
            if (hasHostPerm) {
                val candidatePkgs = (stockLauncherPkgs + listOf("com.android.systemui", "com.android.launcher3")).distinct()
                for (pkg in candidatePkgs) {
                    runCatching {
                        val query = LauncherApps.ShortcutQuery().apply {
                            setPackage(pkg)
                            setQueryFlags(
                                LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED or
                                    LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or
                                    LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST
                            )
                        }
                        val shortcuts = launcherApps.getShortcuts(query, Process.myUserHandle()).orEmpty()
                        for (sc in shortcuts) {
                            val shortLabel = sc.shortLabel?.toString().orEmpty()
                            val longLabel = sc.longLabel?.toString().orEmpty()
                            val idLower = sc.id.lowercase()
                            val combined = "$shortLabel $longLabel $idLower".lowercase()
                            val extractedPkgs = extractInstalledPackagesFromText(
                                "$idLower ${sc.intent?.toUri(Intent.URI_INTENT_SCHEME).orEmpty()}",
                                installedPkgSet
                            )
                            val looksLikeSplit = combined.contains("split") ||
                                combined.contains("pair") ||
                                combined.contains("dual") ||
                                combined.contains("分屏") ||
                                combined.contains("分割") ||
                                combined.contains("雙屏") ||
                                combined.contains("+") ||
                                extractedPkgs.size >= 2

                            if (looksLikeSplit) {
                                val left = extractedPkgs.getOrElse(0) { "com.google.android.apps.maps" }
                                val right = extractedPkgs.getOrElse(1) {
                                    installedApps.firstOrNull { it.packageName != left }?.packageName ?: left
                                }
                                val label = shortLabel.ifBlank { longLabel.ifBlank { "原車分屏捷徑" } }
                                val key = "la_${sc.`package`}_${sc.id}"
                                found[key] = SplitScreenShortcut(
                                    id = UUID.nameUUIDFromBytes(key.toByteArray()).toString(),
                                    leftPackage = left,
                                    rightPackage = right,
                                    ratio = SplitRatioPreset.R_5_5,
                                    engine = SplitLaunchEngine.STOCK_LAUNCHER_SHORTCUT,
                                    label = label,
                                    stockShortcutPackage = sc.`package`,
                                    stockShortcutId = sc.id
                                )
                            }
                        }
                    }
                }
            } else {
                notes += "提示：先將 DX-Car Launcher 設為「系統預設桌面」，即可解鎖 Android 系統級 ShortcutHost 權限讀取已釘選分屏組合"
            }
        }

        // 2. Scan Stock Launcher ContentProviders (`favorites` / `workspace` / `split`)
        val authorities = LinkedHashSet<String>()
        authorities += listOf(
            "com.android.launcher3.settings",
            "com.android.launcher.settings",
            "com.carletter.launcher.settings",
            "com.autochips.launcher.settings"
        )
        for (pkg in stockLauncherPkgs) {
            runCatching {
                @Suppress("DEPRECATION")
                val pi = pm.getPackageInfo(pkg, PackageManager.GET_PROVIDERS)
                pi.providers?.forEach { prov ->
                    prov.authority?.split(";")?.filter { it.isNotBlank() }?.forEach { auth ->
                        authorities += auth
                    }
                }
            }
        }

        var lockedProviderCount = 0
        for (auth in authorities) {
            for (table in listOf("favorites", "favorites_bar", "split_app", "app_pair")) {
                val uri = Uri.parse("content://$auth/$table")
                runCatching {
                    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                        val titleIdx = cursor.getColumnIndex("title")
                        val intentIdx = cursor.getColumnIndex("intent")
                        val itemTypeIdx = cursor.getColumnIndex("itemType")
                        while (cursor.moveToNext()) {
                            val title = if (titleIdx >= 0) cursor.getString(titleIdx).orEmpty() else ""
                            val intentStr = if (intentIdx >= 0) cursor.getString(intentIdx).orEmpty() else ""
                            val itemType = if (itemTypeIdx >= 0) cursor.getInt(itemTypeIdx) else -1
                            val extracted = extractInstalledPackagesFromText(intentStr, installedPkgSet)
                            // AOSP Launcher3 ITEM_TYPE_APP_PAIR == 10, or custom intent containing 2 packages / split action
                            val isSplitRow = itemType == 10 ||
                                extracted.size >= 2 ||
                                intentStr.lowercase().let {
                                    it.contains("split") || it.contains("app_pair") || it.contains("dual")
                                }
                            if (isSplitRow && (extracted.isNotEmpty() || intentStr.isNotBlank())) {
                                val left = extracted.getOrElse(0) { "com.google.android.apps.maps" }
                                val right = extracted.getOrElse(1) {
                                    installedApps.firstOrNull { it.packageName != left }?.packageName ?: left
                                }
                                val leftName = installedApps.firstOrNull { it.packageName == left }?.label ?: "左視窗"
                                val rightName = installedApps.firstOrNull { it.packageName == right }?.label ?: "右視窗"
                                val label = title.ifBlank { "$leftName + $rightName" }
                                val key = "cp_${auth}_${title}_${intentStr.take(80)}"
                                found[key] = SplitScreenShortcut(
                                    id = UUID.nameUUIDFromBytes(key.toByteArray()).toString(),
                                    leftPackage = left,
                                    rightPackage = right,
                                    ratio = SplitRatioPreset.R_5_5,
                                    engine = if (intentStr.isNotBlank()) {
                                        SplitLaunchEngine.STOCK_LAUNCHER_SHORTCUT
                                    } else {
                                        SplitLaunchEngine.NATIVE_SPLIT_SCREEN
                                    },
                                    label = label,
                                    stockIntentUri = intentStr
                                )
                            }
                        }
                    }
                }.onFailure { e ->
                    if (e is SecurityException) lockedProviderCount++
                }
            }
        }

        // 3. Scan Settings.System & Settings.Global for OEM CarBox pre-saved split package pairs
        val settingPairs = mutableListOf<String>()
        for (settingsUri in listOf(Settings.System.CONTENT_URI, Settings.Global.CONTENT_URI)) {
            runCatching {
                context.contentResolver.query(settingsUri, arrayOf("name", "value"), null, null, null)?.use { c ->
                    while (c.moveToNext()) {
                        val name = c.getString(0)?.lowercase().orEmpty()
                        val value = c.getString(1).orEmpty()
                        if (value.length in 6..260 &&
                            (name.contains("split") || name.contains("dual") || name.contains("pair") || name.contains("multi_app"))
                        ) {
                            val pkgs = extractInstalledPackagesFromText(value, installedPkgSet)
                            settingPairs.addAll(pkgs)
                            if (pkgs.size >= 2) {
                                val left = pkgs[0]
                                val right = pkgs[1]
                                val lName = installedApps.firstOrNull { it.packageName == left }?.label ?: left
                                val rName = installedApps.firstOrNull { it.packageName == right }?.label ?: right
                                val key = "sys_${name}_${left}_${right}"
                                found[key] = SplitScreenShortcut(
                                    id = UUID.nameUUIDFromBytes(key.toByteArray()).toString(),
                                    leftPackage = left,
                                    rightPackage = right,
                                    ratio = SplitRatioPreset.R_5_5,
                                    engine = SplitLaunchEngine.NATIVE_SPLIT_SCREEN,
                                    label = "$lName + $rName (原車設定)"
                                )
                            }
                        }
                    }
                }
            }
        }
        val distinctSysPkgs = settingPairs.distinct()
        if (found.isEmpty() && distinctSysPkgs.size >= 2) {
            val left = distinctSysPkgs[0]
            val right = distinctSysPkgs[1]
            val lName = installedApps.firstOrNull { it.packageName == left }?.label ?: left
            val rName = installedApps.firstOrNull { it.packageName == right }?.label ?: right
            found["sys_combined"] = SplitScreenShortcut(
                id = UUID.nameUUIDFromBytes("sys_combined_${left}_${right}".toByteArray()).toString(),
                leftPackage = left,
                rightPackage = right,
                ratio = SplitRatioPreset.R_5_5,
                engine = SplitLaunchEngine.NATIVE_SPLIT_SCREEN,
                label = "$lName + $rName (原車預設組合)"
            )
        }

        val list = found.values.toList()
        val summary = if (list.isNotEmpty()) {
            "✓ 成功從原車桌面／系統設定讀取到 ${list.size} 組已建立的分割視窗組合！"
        } else if (lockedProviderCount > 0) {
            "⚠️ 原車啟動器 (${stockLauncherPkgs.joinToString().ifBlank { "OEM Launcher" }}) 將分屏資料鎖定於系統私有沙盒（非 Root 無法直接讀取其內部資料庫），但您可點「挑選原車系統捷徑」或使用升級版 Android 13 多工分屏引擎！"
        } else {
            "ℹ️ 目前尚未在原車啟動器公開資料庫偵測到已儲存的分屏組合（可點「挑選原車系統捷徑」匯入，或直接在此建立分屏組合）"
        }

        val detail = buildString {
            append(summary)
            if (notes.isNotEmpty()) {
                append("\n")
                append(notes.joinToString("\n"))
            }
        }

        return StockSplitScanResult(
            discoveredShortcuts = list,
            statusSummaryZh = summary,
            detailReportZh = detail
        )
    }

    /**
     * Launches a stock launcher shortcut via LauncherApps or raw URI Intent.
     */
    fun launchStockSplitShortcut(context: Context, shortcut: SplitScreenShortcut): Boolean {
        if (shortcut.stockShortcutPackage.isNotBlank() && shortcut.stockShortcutId.isNotBlank()) {
            val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as? LauncherApps
            val ok = runCatching {
                if (launcherApps != null && launcherApps.hasShortcutHostPermission()) {
                    launcherApps.startShortcut(
                        shortcut.stockShortcutPackage,
                        shortcut.stockShortcutId,
                        null,
                        null,
                        Process.myUserHandle()
                    )
                    true
                } else false
            }.getOrDefault(false)
            if (ok) return true
        }

        if (shortcut.stockIntentUri.isNotBlank()) {
            val ok = runCatching {
                val intent = if (shortcut.stockIntentUri.startsWith("intent:") || shortcut.stockIntentUri.contains("#Intent")) {
                    Intent.parseUri(shortcut.stockIntentUri, Intent.URI_INTENT_SCHEME)
                } else {
                    Intent.parseUri(shortcut.stockIntentUri, 0)
                }
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                true
            }.getOrDefault(false)
            if (ok) return true
        }
        return false
    }

    /**
     * Opens Android's standard System Shortcut Picker (`ACTION_PICK_ACTIVITY` -> `ACTION_CREATE_SHORTCUT`)
     * so if the stock AI Box launcher or vendor tool exposes a Split-Screen shortcut creator, the user can pick it directly!
     */
    fun buildSystemShortcutPickerIntent(): Intent {
        val createShortcut = Intent(Intent.ACTION_CREATE_SHORTCUT)
        return Intent(Intent.ACTION_PICK_ACTIVITY).apply {
            putExtra(Intent.EXTRA_INTENT, createShortcut)
            putExtra(Intent.EXTRA_TITLE, "選擇原車啟動器或系統提供的分屏／捷徑")
        }
    }

    private fun extractInstalledPackagesFromText(raw: String, installedSet: Set<String>): List<String> {
        if (raw.isBlank() || installedSet.isEmpty()) return emptyList()
        val regex = Regex("[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z0-9_]+){2,}")
        return regex.findAll(raw)
            .map { it.value }
            .filter { installedSet.contains(it) }
            .distinct()
            .toList()
    }
}

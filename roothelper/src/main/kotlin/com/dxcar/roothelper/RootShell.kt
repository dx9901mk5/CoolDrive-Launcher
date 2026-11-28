package com.dxcar.roothelper

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

data class ShellResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String
) {
    val isSuccess: Boolean get() = exitCode == 0
}

data class RootStatusInfo(
    val isRooted: Boolean = false,
    val idOutput: String = "",
    val magiskVersion: String = "",
    val socPlatform: String = "",
    val slotSuffix: String = ""
)

data class Display60HzReport(
    val feasible: Boolean,
    val currentModeSummary: String,
    val supportedModesSummary: String,
    val detailMessage: String
)

object RootShell {
    private val SU_CANDIDATES = listOf(
        "su",
        "/debug_ramdisk/su",
        "/sbin/su",
        "/system/bin/su",
        "/system/xbin/su",
        "/product/bin/su"
    )

    @Volatile
    private var workingSuPath: String? = null

    private fun runWithBinary(suBin: String, command: String, timeoutSec: Long): ShellResult {
        val attempts = listOf(
            listOf(suBin, "-c", command),
            listOf(suBin, "0", "/system/bin/sh", "-c", command)
        )
        var lastRes = ShellResult(-1, "", "su execution failed")
        for (argv in attempts) {
            val res = runCatching {
                val process = ProcessBuilder(argv)
                    .redirectErrorStream(false)
                    .start()
                val finished = process.waitFor(timeoutSec, TimeUnit.SECONDS)
                if (!finished) {
                    process.destroyForcibly()
                    return@runCatching ShellResult(-1, "", "Timeout after ${timeoutSec}s")
                }
                val out = process.inputStream.bufferedReader().readText().trim()
                val err = process.errorStream.bufferedReader().readText().trim()
                ShellResult(process.exitValue(), out, err)
            }.getOrElse { e ->
                ShellResult(-1, "", e.message ?: "su execution failed")
            }
            if (res.isSuccess) return res
            lastRes = res
            // If binary does not exist / permission denied immediately, skip second syntax
            if (res.stderr.contains("No such file", ignoreCase = true) ||
                res.stderr.contains("Permission denied", ignoreCase = true)
            ) {
                break
            }
        }
        return lastRes
    }

    suspend fun execSu(command: String, timeoutSec: Long = 15L): ShellResult = withContext(Dispatchers.IO) {
        val cached = workingSuPath
        if (cached != null) {
            val res = runWithBinary(cached, command, timeoutSec)
            if (res.isSuccess) return@withContext res
            workingSuPath = null
        }
        var lastFail = ShellResult(-1, "", "No working su binary")
        for (bin in SU_CANDIDATES) {
            // Do NOT gate on File(bin).exists() because Android 15 SELinux blocks stat() on /product/bin or /debug_ramdisk even when execve() is allowed!
            val perBinTimeout = if (bin == "su" || bin == "/product/bin/su" || bin == "/system/bin/su") {
                timeoutSec.coerceAtLeast(18L)
            } else {
                5L
            }
            val res = runWithBinary(bin, command, perBinTimeout)
            if (res.isSuccess) {
                workingSuPath = bin
                return@withContext res
            }
            lastFail = ShellResult(res.exitCode, res.stdout, "$bin: ${res.stderr.ifBlank { res.stdout }}")
        }
        lastFail
    }

    suspend fun probeRootStatus(): RootStatusInfo {
        val idRes = execSu("id", 20L)
        val isRoot = idRes.isSuccess && idRes.stdout.contains("uid=0")
        val magiskRes = if (isRoot) execSu("magisk -v", 3L) else ShellResult(-1, "", "")
        val propRes = if (isRoot) {
            execSu("getprop ro.board.platform; echo '|'; getprop ro.boot.slot_suffix", 3L)
        } else {
            ShellResult(-1, "", "")
        }
        val parts = propRes.stdout.split("|").map { it.trim() }
        return RootStatusInfo(
            isRooted = isRoot,
            idOutput = idRes.stdout.ifBlank { idRes.stderr },
            magiskVersion = magiskRes.stdout,
            socPlatform = parts.getOrElse(0) { "" },
            slotSuffix = parts.getOrElse(1) { "" }
        )
    }

    suspend fun analyzeDisplay60Hz(): Display60HzReport {
        val res = execSu(
            "dumpsys display | grep -iE 'supportedModes|renderFrameRate|refreshRate|mBaseDisplayInfo' | head -n 15; " +
                "echo '===SF==='; " +
                "dumpsys SurfaceFlinger | grep -iE 'refresh|fps|mode|vsync' | head -n 15",
            6L
        )
        if (!res.isSuccess && res.stdout.isBlank()) {
            return Display60HzReport(
                feasible = false,
                currentModeSummary = "未取得 Root 或無法讀取顯示資訊",
                supportedModesSummary = "",
                detailMessage = "請先確認 Magisk 已授權本輔助 App Root 權限。"
            )
        }
        val text = res.stdout
        val has60 = Regex("""60(\.0+)?\s*(hz|fps)?""", RegexOption.IGNORE_CASE).containsMatchIn(text)
        val currentLine = text.lines().firstOrNull {
            it.contains("refreshRate", ignoreCase = true) ||
                it.contains("renderFrameRate", ignoreCase = true) ||
                it.contains("refresh-rate", ignoreCase = true)
        }?.trim().orEmpty()
        val modesLine = text.lines().firstOrNull {
            it.contains("supportedModes", ignoreCase = true)
        }?.trim().orEmpty()

        val msg = if (has60) {
            "偵測到系統顯示模式包含 60Hz/60fps 能力，可嘗試強制鎖定 min/peak_refresh_rate=60；" +
                "（註：實際投屏至原車螢幕仍受原車主機 CarPlay 接收上限影響，建議同時寄出診斷 Log 讓我們確認原廠投屏參數）。"
        } else {
            "目前未在系統顯示模式中偵測到 60Hz 模式，可能受限於顯示面板或原廠投屏韌體鎖定，建議寄出診斷 Log 進一步判讀。"
        }
        return Display60HzReport(
            feasible = has60,
            currentModeSummary = currentLine.ifBlank { "未擷取到目前更新率欄位" },
            supportedModesSummary = modesLine.ifBlank { "請參考診斷 Log 完整內容" },
            detailMessage = msg
        )
    }

    suspend fun apply60HzSettings(enable: Boolean): ShellResult {
        val cmd = if (enable) {
            "settings put system min_refresh_rate 60.0; " +
                "settings put system peak_refresh_rate 60.0; " +
                "settings put system user_refresh_rate 60.0"
        } else {
            "settings delete system min_refresh_rate; " +
                "settings delete system peak_refresh_rate; " +
                "settings delete system user_refresh_rate"
        }
        return execSu(cmd, 5L)
    }

    suspend fun detectOtaPackages(context: Context): List<String> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val installed = runCatching {
            @Suppress("DEPRECATION")
            pm.getInstalledApplications(PackageManager.MATCH_DISABLED_COMPONENTS)
        }.getOrDefault(emptyList())
        val keywords = listOf("ota", "fota", "update", "upgrade", "systemupdate", "swupdate")
        installed
            .map { it.packageName }
            .filter { pkg ->
                val low = pkg.lowercase(Locale.ROOT)
                !low.startsWith("com.google.android.gms") &&
                    !low.startsWith("com.android.vending") &&
                    keywords.any { k -> low.contains(k) }
            }
            .sorted()
    }

    suspend fun setPackageDisabled(packageName: String, disable: Boolean): ShellResult {
        if (packageName.isBlank()) return ShellResult(-1, "", "Package name is empty")
        val safePkg = packageName.replace(Regex("[^a-zA-Z0-9._]"), "")
        val cmd = if (disable) {
            "pm disable-user --user 0 $safePkg"
        } else {
            "pm enable $safePkg"
        }
        return execSu(cmd, 5L)
    }

    private fun sanitizePrivacyFromLog(raw: String): String {
        if (raw.isBlank()) return raw
        return raw
            .lineSequence()
            .filterNot { line ->
                val low = line.lowercase(Locale.ROOT)
                low.contains("latitude") || low.contains("longitude") ||
                    low.contains("lat=") || low.contains("lon=") ||
                    low.contains("nmea") || low.contains("locationmanager") ||
                    low.contains("getlastknownlocation") || low.contains("ssid") ||
                    low.contains("bssid")
            }
            .joinToString("\n")
            // Redact Bluetooth/Wi-Fi MAC addresses (e.g. AA:BB:CC:DD:EE:FF -> XX:XX:XX:XX:EE:FF)
            .replace(Regex("([0-9A-Fa-f]{2}:){4}([0-9A-Fa-f]{2}:[0-9A-Fa-f]{2})"), "XX:XX:XX:XX:$2")
            // Redact GPS coordinate pairs (e.g. 25.033964, 121.564472) without masking FPS/refreshRate/brightness floats
            .replace(Regex("\\b-?\\d{1,2}\\.\\d{5,}\\s*,\\s*-?\\d{1,3}\\.\\d{5,}\\b"), "[REDACTED_COORD]")
    }

    suspend fun collectDiagnosticReport(context: Context): File = withContext(Dispatchers.IO) {
        val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val dir = File(context.cacheDir, "diag_logs").apply { mkdirs() }
        val outFile = File(dir, "dxcar_root_diag_$ts.txt")

        val commands = listOf(
            "BASIC" to "id; magisk -v; uname -a",
            "BUILD" to "getprop ro.product.model; getprop ro.build.version.release; getprop ro.board.platform; getprop ro.boot.slot_suffix; getprop persist.splitscreen.percent",
            "BT_PACKAGES" to "(pm list packages | grep -iE 'blue|bt|goc|nfore|suding|carlink|zlink|speedplay|phone|dialer|call') || true",
            "BT_PROCESSES_AND_SERVICES" to "(ps -A | grep -iE 'blue|bt|goc|nfore|suding|carlink|zlink|speedplay'; echo '---SERVICES---'; dumpsys activity services | grep -iE 'ServiceRecord.*(blue|bt|goc|nfore|suding|carlink|zlink|speedplay)' | head -n 60) || true",
            "BT_PROPS" to "(getprop | grep -iE 'bt|blue|goc|nfore|suding' | head -n 90) || true",
            "BT_MANAGER_DUMPSYS" to "(dumpsys bluetooth_manager | grep -viE 'address:|name:|bonded|\\[■\\]|\\[ \\]' | head -n 140) || true",
            "BT_OEM_INTENTS" to "(for p in \$(pm list packages | cut -d: -f2 | grep -iE 'goc|nfore|suding.*bt|suding.*blue|btphone|bluetooth' | grep -v '^com.android.bluetooth\$' | head -n 6); do echo \"=== PKG: \$p ===\"; dumpsys package \$p | grep -iE 'Action:|Service|Receiver|Activity' | head -n 40; done) || true",
            "DISPLAY" to "(dumpsys display | grep -iE 'mBaseDisplayInfo|supportedModes|renderFrameRate|refreshRate|DisplayMode' | head -n 45) || true",
            "SURFACEFLINGER" to "(dumpsys SurfaceFlinger | grep -iE 'refresh|fps|mode|vsync|Display' | head -n 50) || true",
            "CONTAINERS" to "(dumpsys activity containers | grep -E 'DefaultTaskDisplayArea|Task=|ActivityRecord|mAppBounds' | head -n 60) || true",
            "SUDING_CONFIG" to "(cat /system/etc/sudingConfig.ini 2>/dev/null; echo '---VENDOR---'; cat /mnt/vendor/qcache/FactoryConfig/sudingConfig.ini 2>/dev/null) || true",
            "OTA_AND_OEM_PKGS" to "(pm list packages | grep -iE 'ota|fota|update|suding|carsyso|sd\\.') || true",
            "LOGCAT_SYSTEM_FILTERED" to "(logcat -d -t 250 -s AndroidRuntime:E SystemServiceManager:I ActivityTaskManager:I BluetoothManagerService:I BluetoothAdapter:I | grep -viE 'location|gps|lat|lon|nmea|ssid|bssid') || true"
        )

        val sb = StringBuilder()
        sb.appendLine("=== DX-Car Root Helper Diagnostic Report (v${BuildConfig.VERSION_NAME}) ===")
        sb.appendLine("Privacy Notice: Only necessary car-box hardware, display, Bluetooth stack architecture, and OTA package info are collected.")
        sb.appendLine("Location/GPS coordinates, personal data, and full device MACs are NOT recorded or transmitted.")
        sb.appendLine("Time: $ts")
        sb.appendLine("Model: ${Build.MANUFACTURER} ${Build.MODEL} (Android ${Build.VERSION.RELEASE}, SDK ${Build.VERSION.SDK_INT})")
        sb.appendLine()

        val results = LinkedHashMap<String, ShellResult>()
        for ((title, cmd) in commands) {
            results[title] = execSu(cmd, 8L)
        }

        // Quick Auto-Verdict: Native Android Bluetooth vs. Vendor Standalone BT Service (gocsdk / nfore / suding)
        val btPkgs = results["BT_PACKAGES"]?.stdout.orEmpty().lowercase(Locale.ROOT)
        val btProcs = results["BT_PROCESSES_AND_SERVICES"]?.stdout.orEmpty().lowercase(Locale.ROOT)
        val btDump = results["BT_MANAGER_DUMPSYS"]?.stdout.orEmpty().lowercase(Locale.ROOT)
        val hasGoc = btPkgs.contains("goc") || btProcs.contains("goc")
        val hasNfore = btPkgs.contains("nfore") || btProcs.contains("nfore")
        val hasSudingBt = btPkgs.contains("suding") || btProcs.contains("suding")
        val hasNativeBt = btDump.contains("enabled: true") || btProcs.contains("com.android.bluetooth")
        val btVerdict = buildString {
            append("BT_ARCHITECTURE_VERDICT: ")
            val vendors = buildList {
                if (hasGoc) add("gocsdk")
                if (hasNfore) add("nfore")
                if (hasSudingBt) add("suding")
            }
            when {
                vendors.isNotEmpty() && hasNativeBt -> append("HYBRID (Native Android BT + Vendor Service: ${vendors.joinToString(", ")})")
                vendors.isNotEmpty() -> append("VENDOR_STANDALONE (${vendors.joinToString(", ")})")
                hasNativeBt -> append("NATIVE_ANDROID_BLUETOOTH")
                else -> append("UNKNOWN (check BT_PACKAGES / BT_MANAGER_DUMPSYS below)")
            }
        }
        sb.appendLine("================ BT_AUTO_VERDICT ================")
        sb.appendLine(btVerdict)
        sb.appendLine()

        for ((title, r) in results) {
            sb.appendLine("================ $title ================")
            sb.appendLine("exitCode=${r.exitCode}")
            if (r.stdout.isNotBlank()) sb.appendLine(sanitizePrivacyFromLog(r.stdout))
            if (r.stderr.isNotBlank()) sb.appendLine("[stderr] ${sanitizePrivacyFromLog(r.stderr)}")
            sb.appendLine()
        }

        val lastCrash = File(dir, "last_crash.txt")
        if (lastCrash.exists()) {
            sb.appendLine("================ LAST_CRASH ================")
            sb.appendLine(sanitizePrivacyFromLog(runCatching { lastCrash.readText() }.getOrDefault("")))
            sb.appendLine()
        }

        outFile.writeText(sb.toString())
        outFile
    }
}

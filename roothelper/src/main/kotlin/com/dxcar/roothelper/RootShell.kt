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
        return runCatching {
            val process = ProcessBuilder(suBin, "-c", command)
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
    }

    suspend fun execSu(command: String, timeoutSec: Long = 5L): ShellResult = withContext(Dispatchers.IO) {
        val cached = workingSuPath
        if (cached != null) {
            return@withContext runWithBinary(cached, command, timeoutSec)
        }
        var lastFail = ShellResult(-1, "", "No working su binary")
        for (bin in SU_CANDIDATES) {
            if (bin != "su" && !File(bin).exists()) continue
            val res = runWithBinary(bin, command, timeoutSec)
            if (res.isSuccess) {
                workingSuPath = bin
                return@withContext res
            }
            lastFail = ShellResult(res.exitCode, res.stdout, "$bin: ${res.stderr.ifBlank { res.stdout }}")
        }
        lastFail
    }

    suspend fun probeRootStatus(): RootStatusInfo {
        val idRes = execSu("id", 4L)
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

    suspend fun collectDiagnosticReport(context: Context): File = withContext(Dispatchers.IO) {
        val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val dir = File(context.cacheDir, "diag_logs").apply { mkdirs() }
        val outFile = File(dir, "dxcar_root_diag_$ts.txt")

        val commands = listOf(
            "BASIC" to "id; magisk -v; uname -a",
            "BUILD" to "getprop ro.product.model; getprop ro.build.version.release; getprop ro.board.platform; getprop ro.boot.slot_suffix; getprop persist.splitscreen.percent",
            "DISPLAY" to "dumpsys display | grep -iE 'mBaseDisplayInfo|supportedModes|renderFrameRate|refreshRate|DisplayMode' | head -n 40",
            "SURFACEFLINGER" to "dumpsys SurfaceFlinger | grep -iE 'refresh|fps|mode|vsync|Display' | head -n 50",
            "CONTAINERS" to "dumpsys activity containers | grep -E 'Task=|ActivityRecord|mode=|bounds=' | head -n 50",
            "SUDING_CONFIG" to "cat /system/etc/sudingConfig.ini 2>/dev/null; echo '---VENDOR---'; cat /mnt/vendor/qcache/FactoryConfig/sudingConfig.ini 2>/dev/null",
            "OTA_AND_OEM_PKGS" to "pm list packages | grep -iE 'ota|fota|update|suding|carsyso|sd\\.'",
            "LOGCAT_TAIL" to "logcat -d -t 300"
        )

        val sb = StringBuilder()
        sb.appendLine("=== DX-Car Root Helper Diagnostic Report ===")
        sb.appendLine("Time: $ts")
        sb.appendLine("Model: ${Build.MANUFACTURER} ${Build.MODEL} (Android ${Build.VERSION.RELEASE}, SDK ${Build.VERSION.SDK_INT})")
        sb.appendLine()

        for ((title, cmd) in commands) {
            sb.appendLine("================ $title ================")
            val r = execSu(cmd, 8L)
            sb.appendLine("exitCode=${r.exitCode}")
            if (r.stdout.isNotBlank()) sb.appendLine(r.stdout)
            if (r.stderr.isNotBlank()) sb.appendLine("[stderr] ${r.stderr}")
            sb.appendLine()
        }

        outFile.writeText(sb.toString())
        outFile
    }
}

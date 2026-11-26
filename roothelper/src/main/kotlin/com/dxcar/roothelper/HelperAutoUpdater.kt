package com.dxcar.roothelper

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

data class HelperUpdateInfo(
    val hasUpdate: Boolean,
    val latestVersion: String,
    val downloadUrl: String,
    val releasePageUrl: String
)

object HelperAutoUpdater {
    const val GITHUB_RELEASES_URL = "https://github.com/dx9901mk5/DX-Car-Launcher/releases"
    private const val API_RELEASES = "https://api.github.com/repos/dx9901mk5/DX-Car-Launcher/releases?per_page=10"

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun checkForUpdate(currentVersion: String): HelperUpdateInfo? = withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder()
                .url(API_RELEASES)
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "DX-Car-Root-Helper")
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext null
                val body = resp.body?.string() ?: return@withContext null
                val releases = json.parseToJsonElement(body).jsonArray
                for (el in releases) {
                    val obj = el.jsonObject
                    val assets = obj["assets"]?.jsonArray ?: continue
                    val helperAsset = assets.firstOrNull { a ->
                        val name = a.jsonObject["name"]?.jsonPrimitive?.content ?: ""
                        name.startsWith("DX-Car-Root-Helper", ignoreCase = true) && name.endsWith(".apk", ignoreCase = true)
                    }?.jsonObject ?: continue

                    val tag = obj["tag_name"]?.jsonPrimitive?.content ?: ""
                    val latestVer = tag.removePrefix("root-helper-v").removePrefix("v").trim()
                    val dlUrl = helperAsset["browser_download_url"]?.jsonPrimitive?.content ?: ""
                    val htmlUrl = obj["html_url"]?.jsonPrimitive?.content ?: GITHUB_RELEASES_URL
                    val newer = compareSemver(latestVer, currentVersion) > 0
                    return@withContext HelperUpdateInfo(
                        hasUpdate = newer,
                        latestVersion = latestVer,
                        downloadUrl = dlUrl,
                        releasePageUrl = htmlUrl
                    )
                }
                null
            }
        }.getOrNull()
    }

    suspend fun downloadAndInstall(context: Context, info: HelperUpdateInfo): String = withContext(Dispatchers.IO) {
        if (info.downloadUrl.isBlank()) return@withContext "下載連結為空"
        val dir = File(context.cacheDir, "diag_logs").apply { mkdirs() }
        val apkFile = File(dir, "DX-Car-Root-Helper-${info.latestVersion}.apk")
        val ok = runCatching {
            val req = Request.Builder()
                .url(info.downloadUrl)
                .header("User-Agent", "DX-Car-Root-Helper")
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use false
                resp.body?.byteStream()?.use { input ->
                    apkFile.outputStream().use { output -> input.copyTo(output) }
                } ?: return@use false
                true
            }
        }.getOrDefault(false)

        if (!ok || !apkFile.exists()) return@withContext "下載輔助 APK 失敗"

        // Try silent root install first; fallback to standard package installer
        val suInstall = RootShell.execSu("pm install -r '${apkFile.absolutePath}'", 30L)
        if (suInstall.isSuccess && suInstall.stdout.contains("Success", ignoreCase = true)) {
            return@withContext "已透過 Root 自動更新至 v${info.latestVersion}"
        }

        withContext(Dispatchers.Main) {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apkFile)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            runCatching { context.startActivity(intent) }
        }
        "已下載 v${info.latestVersion} 至本機，請確認安裝"
    }

    private fun compareSemver(a: String, b: String): Int {
        val pa = a.split(".").map { it.toIntOrNull() ?: 0 }
        val pb = b.split(".").map { it.toIntOrNull() ?: 0 }
        val len = maxOf(pa.size, pb.size)
        for (i in 0 until len) {
            val va = pa.getOrElse(i) { 0 }
            val vb = pb.getOrElse(i) { 0 }
            if (va != vb) return va.compareTo(vb)
        }
        return 0
    }
}

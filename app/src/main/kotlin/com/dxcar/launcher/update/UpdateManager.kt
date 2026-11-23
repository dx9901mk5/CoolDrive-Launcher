package com.dxcar.launcher.update

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.dxcar.launcher.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

@Serializable
private data class GithubReleaseDto(
    @SerialName("tag_name") val tagName: String = "",
    val name: String = "",
    val body: String = "",
    val assets: List<GithubAssetDto> = emptyList()
)

@Serializable
private data class GithubAssetDto(
    val name: String = "",
    @SerialName("browser_download_url") val downloadUrl: String = ""
)

data class UpdateUiState(
    val checking: Boolean = false,
    val downloading: Boolean = false,
    val progress: Float = 0f,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = -1L,
    val hasUpdate: Boolean = false,
    val downloadedReady: Boolean = false,
    val downloadedFilePath: String = "",
    val latestVersion: String = "",
    val releaseNotes: String = "",
    val downloadUrl: String = "",
    val statusMessage: String = ""
)

class UpdateManager(private val context: Context) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    private val _state = MutableStateFlow(UpdateUiState())
    val state: StateFlow<UpdateUiState> = _state.asStateFlow()

    suspend fun checkForUpdate(autoDownload: Boolean = false) = withContext(Dispatchers.IO) {
        if (_state.value.checking || _state.value.downloading) return@withContext
        _state.value = _state.value.copy(checking = true, statusMessage = "正在檢查 GitHub 最新版本…")
        var shouldDownload = false
        runCatching {
            val req = Request.Builder()
                .url("https://api.github.com/repos/$GITHUB_REPO/releases/latest")
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "DX-Car-Launcher/${BuildConfig.VERSION_NAME}")
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    _state.value = UpdateUiState(statusMessage = "目前版本已是最新 (v${BuildConfig.VERSION_NAME})")
                    return@use
                }
                val bodyStr = resp.body?.string().orEmpty()
                val dto = json.decodeFromString<GithubReleaseDto>(bodyStr)
                val latest = dto.tagName.removePrefix("v").trim()
                val apkAsset = dto.assets.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) }
                val newer = isVersionNewer(latest, BuildConfig.VERSION_NAME)
                val available = newer && apkAsset != null
                shouldDownload = available && autoDownload
                _state.value = UpdateUiState(
                    checking = false,
                    hasUpdate = available,
                    latestVersion = latest,
                    releaseNotes = dto.body,
                    downloadUrl = apkAsset?.downloadUrl.orEmpty(),
                    statusMessage = if (available) {
                        if (autoDownload) "發現新版本 v$latest，正在自動下載…" else "發現新版本 v$latest，點擊立即下載更新"
                    } else {
                        "目前已是最新版本 (v${BuildConfig.VERSION_NAME})"
                    }
                )
            }
        }.onFailure { err ->
            _state.value = _state.value.copy(
                checking = false,
                statusMessage = "檢查更新失敗：${err.localizedMessage ?: "請確認網路"}"
            )
        }
        if (shouldDownload) {
            downloadAndInstall(autoLaunchInstaller = true)
        }
    }

    suspend fun downloadAndInstall(autoLaunchInstaller: Boolean = true) = withContext(Dispatchers.IO) {
        val url = _state.value.downloadUrl
        if (url.isBlank() || _state.value.downloading) return@withContext
        _state.value = _state.value.copy(
            downloading = true,
            downloadedReady = false,
            progress = 0f,
            downloadedBytes = 0L,
            totalBytes = -1L,
            statusMessage = "正在連線下載 v${_state.value.latestVersion} 更新檔…"
        )
        runCatching {
            val dir = File(context.cacheDir, "updates").apply { mkdirs() }
            val outFile = File(dir, "DX-Car-Launcher-update.apk")
            val req = Request.Builder().url(url).build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) error("HTTP ${resp.code}")
                val body = resp.body ?: error("Empty APK body")
                val total = body.contentLength()
                var lastUiPush = 0L
                body.byteStream().use { input ->
                    outFile.outputStream().use { output ->
                        val buf = ByteArray(16 * 1024)
                        var copied = 0L
                        while (true) {
                            val read = input.read(buf)
                            if (read <= 0) break
                            output.write(buf, 0, read)
                            copied += read
                            val now = System.currentTimeMillis()
                            if (now - lastUiPush >= 120L || (total > 0 && copied >= total)) {
                                lastUiPush = now
                                val pct = if (total > 0) (copied.toFloat() / total.toFloat()).coerceIn(0f, 1f) else 0f
                                val mbDone = String.format("%.1f", copied / (1024f * 1024f))
                                val msg = if (total > 0) {
                                    val mbTotal = String.format("%.1f", total / (1024f * 1024f))
                                    "下載中 ${(pct * 100).toInt()}%（$mbDone / $mbTotal MB）"
                                } else {
                                    "下載中 $mbDone MB…"
                                }
                                _state.value = _state.value.copy(
                                    progress = pct,
                                    downloadedBytes = copied,
                                    totalBytes = total,
                                    statusMessage = msg
                                )
                            }
                        }
                    }
                }
            }
            _state.value = _state.value.copy(
                downloading = false,
                downloadedReady = true,
                downloadedFilePath = outFile.absolutePath,
                progress = 1f,
                statusMessage = "v${_state.value.latestVersion} 下載完成，點擊立即安裝！"
            )
            if (autoLaunchInstaller) {
                installDownloadedApk()
            }
        }.onFailure { err ->
            _state.value = _state.value.copy(
                downloading = false,
                statusMessage = "下載失敗：${err.localizedMessage ?: "未知錯誤"}"
            )
        }
    }

    fun installDownloadedApk() {
        val path = _state.value.downloadedFilePath
        if (path.isBlank()) return
        val file = File(path)
        if (!file.exists()) return
        installApk(file)
    }

    private fun installApk(apkFile: File) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(intent)
    }

    internal fun isVersionNewer(remote: String, local: String): Boolean {
        val rParts = remote.split(".").map { it.toIntOrNull() ?: 0 }
        val lParts = local.split(".").map { it.toIntOrNull() ?: 0 }
        val maxLen = maxOf(rParts.size, lParts.size)
        for (i in 0 until maxLen) {
            val r = rParts.getOrElse(i) { 0 }
            val l = lParts.getOrElse(i) { 0 }
            if (r != l) return r > l
        }
        return false
    }

    companion object {
        const val GITHUB_REPO = "dx9901mk5/DX-Car-Launcher"
        const val GITHUB_URL = "https://github.com/$GITHUB_REPO"
    }
}

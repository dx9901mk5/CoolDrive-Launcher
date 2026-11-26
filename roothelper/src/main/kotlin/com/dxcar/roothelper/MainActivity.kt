package com.dxcar.roothelper

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                RootHelperScreen()
            }
        }
    }
}

@Composable
private fun RootHelperScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var rootStatus by remember { mutableStateOf(RootStatusInfo()) }
    var config by remember { mutableStateOf(RootHelperConfigStore.load(context)) }
    var hzReport by remember { mutableStateOf<Display60HzReport?>(null) }
    var otaCandidates by remember { mutableStateOf<List<String>>(emptyList()) }
    var busyMessage by remember { mutableStateOf<String?>(null) }
    var updateInfo by remember { mutableStateOf<HelperUpdateInfo?>(null) }

    LaunchedEffect(Unit) {
        rootStatus = RootShell.probeRootStatus()
        otaCandidates = RootShell.detectOtaPackages(context)
        if (config.otaInterceptedPackage.isBlank() && otaCandidates.isNotEmpty()) {
            config = config.copy(otaInterceptedPackage = otaCandidates.first())
            RootHelperConfigStore.save(context, config)
        }
        val upd = HelperAutoUpdater.checkForUpdate("1.0.1")
        updateInfo = upd
        if (upd != null && upd.hasUpdate) {
            busyMessage = "偵測到新版本 v${upd.latestVersion}，自動下載中..."
            busyMessage = HelperAutoUpdater.downloadAndInstall(context, upd)
        }
    }

    fun saveCfg(next: RootHelperConfig) {
        config = next
        RootHelperConfigStore.save(context, next)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0B101B))
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = "DX-Car Root 輔助工具 v1.0.1",
            color = Color.White,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "供 Google Play 版 DX-Car Launcher 偵測並啟用「方案 B（Root 特權模式）」與系統調校",
            color = Color(0xFF9FB0C8),
            fontSize = 13.sp
        )

        if (!busyMessage.isNullOrBlank()) {
            Text(text = busyMessage!!, color = Color(0xFF69F0AE), fontSize = 13.sp)
        }

        // 1. Root Status + Diagnostic Log -> Gmail
        HelperCard(title = "1. Root 權限狀態與車機診斷 Log") {
            Text(
                text = if (rootStatus.isRooted) {
                    "✅ 已取得 Root：${rootStatus.magiskVersion} (${rootStatus.socPlatform} / ${rootStatus.slotSuffix})"
                } else {
                    "❌ 尚未取得 su 權限（${rootStatus.idOutput.ifBlank { "請於 Magisk 授權" }}）"
                },
                color = if (rootStatus.isRooted) Color(0xFF69F0AE) else Color(0xFFFF8A80),
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = {
                    scope.launch { rootStatus = RootShell.probeRootStatus() }
                }) {
                    Text("重新檢查 su")
                }
                OutlinedButton(onClick = {
                    scope.launch {
                        busyMessage = "檢查 GitHub 更新中..."
                        val upd = HelperAutoUpdater.checkForUpdate("1.0.1")
                        updateInfo = upd
                        busyMessage = when {
                            upd == null -> "無法連線 GitHub 檢查更新"
                            upd.hasUpdate -> {
                                busyMessage = "發現新版本 v${upd.latestVersion}，自動下載中..."
                                HelperAutoUpdater.downloadAndInstall(context, upd)
                            }
                            else -> "目前已是最新版 v1.0.1（GitHub：dx9901mk5/DX-Car-Launcher）"
                        }
                    }
                }) {
                    Text("檢查 GitHub 更新")
                }
                OutlinedButton(onClick = {
                    val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(HelperAutoUpdater.GITHUB_RELEASES_URL))
                    runCatching { context.startActivity(intent) }
                }) {
                    Text("開啟 GitHub 頁面")
                }
                Button(
                    onClick = {
                        scope.launch {
                            busyMessage = "正在蒐集系統與顯示診斷 Log..."
                            val file = RootShell.collectDiagnosticReport(context)
                            busyMessage = "已產出 Log：${file.name}"
                            val uri = FileProvider.getUriForFile(
                                context,
                                "${context.packageName}.fileprovider",
                                file
                            )
                            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_EMAIL, arrayOf("dx9901mk5@gmail.com"))
                                putExtra(
                                    Intent.EXTRA_SUBJECT,
                                    "DX-Car Root 診斷 Log (${android.os.Build.MODEL} / Android ${android.os.Build.VERSION.RELEASE})"
                                )
                                putExtra(Intent.EXTRA_TEXT, "請協助判讀車機硬體、60Hz 與 OTA 資訊（附件如附）。")
                                putExtra(Intent.EXTRA_STREAM, uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            val gmailIntent = Intent(sendIntent).setPackage("com.google.android.gm")
                            val launch = if (gmailIntent.resolveActivity(context.packageManager) != null) {
                                gmailIntent
                            } else {
                                Intent.createChooser(sendIntent, "寄送診斷 Log 至 dx9901mk5@gmail.com")
                            }
                            runCatching { context.startActivity(launch) }
                                .onFailure {
                                    Toast.makeText(context, "找不到郵件 App：${file.absolutePath}", Toast.LENGTH_LONG).show()
                                }
                        }
                    }
                ) {
                    Text("產生 Log 並寄送 Gmail (dx9901mk5@gmail.com)")
                }
            }
        }

        // 2. 60Hz / 60fps Feasibility Check & Toggle
        HelperCard(title = "2. 60Hz / 60fps 能力判讀與系統設定") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(onClick = {
                    scope.launch {
                        busyMessage = "正在檢測車機顯示與 SurfaceFlinger 60Hz 能力..."
                        hzReport = RootShell.analyzeDisplay60Hz()
                        busyMessage = null
                    }
                }) {
                    Text("檢測車機是否支援 60Hz")
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("強制 60Hz", color = Color.White, fontSize = 13.sp)
                    Switch(
                        checked = config.force60HzEnabled,
                        enabled = hzReport?.feasible == true || config.force60HzEnabled,
                        onCheckedChange = { enabled ->
                            scope.launch {
                                val res = RootShell.apply60HzSettings(enabled)
                                if (res.isSuccess) {
                                    saveCfg(config.copy(force60HzEnabled = enabled))
                                    busyMessage = if (enabled) "已套用 60Hz 系統更新率設定" else "已恢復系統預設更新率"
                                } else {
                                    busyMessage = "套用失敗：${res.stderr}"
                                }
                            }
                        }
                    )
                }
            }
            hzReport?.let { rep ->
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = if (rep.feasible) "✅ 判斷結果：可行 (${rep.currentModeSummary})" else "⚠️ 判斷結果：未偵測到 60Hz 模式",
                    color = if (rep.feasible) Color(0xFF69F0AE) else Color(0xFFFFD54F),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(text = rep.detailMessage, color = Color(0xFFB0C0D8), fontSize = 12.sp)
            }
        }

        // 3. Plan B UI Customization
        HelperCard(title = "3. 方案 B 介面客製化（維持 0 / 1 / 2 三介面）") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("啟用方案 B Root 模式", color = Color.White, fontSize = 14.sp)
                Switch(
                    checked = config.planBEnabled,
                    onCheckedChange = { saveCfg(config.copy(planBEnabled = it)) }
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Dock 常駐釘選時 UI 0/1/2 整體右移不重疊", color = Color.White, fontSize = 14.sp)
                Switch(
                    checked = config.dockPinnedRightShift,
                    onCheckedChange = { saveCfg(config.copy(dockPinnedRightShift = it)) }
                )
            }
            OutlinedTextField(
                value = config.ui1NavPackage,
                onValueChange = { saveCfg(config.copy(ui1NavPackage = it)) },
                label = { Text("UI 1 左側 70% 導航 App 套件名（預設 Google 地圖）") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { saveCfg(config.copy(ui1NavPackage = "com.google.android.apps.maps")) }) {
                    Text("Google 地圖")
                }
                OutlinedButton(onClick = { saveCfg(config.copy(ui1NavPackage = "com.kingwaytek.naviking3d")) }) {
                    Text("導航王 3D")
                }
            }
            OutlinedTextField(
                value = config.ui2LeftPackage,
                onValueChange = { saveCfg(config.copy(ui2LeftPackage = it)) },
                label = { Text("UI 2 左側預設 App 套件名") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            OutlinedTextField(
                value = config.ui2RightPackage,
                onValueChange = { saveCfg(config.copy(ui2RightPackage = it)) },
                label = { Text("UI 2 右側預設 App 套件名（預設 YouTube）") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Text(
                text = "UI 2 預設分割比例：${(config.ui2SplitRatio * 100).roundToInt()}% : ${100 - (config.ui2SplitRatio * 100).roundToInt()}%（車機端長壓中線可調比例、點兩下左右互換）",
                color = Color(0xFFB0C0D8),
                fontSize = 12.sp
            )
            Slider(
                value = config.ui2SplitRatio,
                onValueChange = { saveCfg(config.copy(ui2SplitRatio = it.coerceIn(0.25f, 0.75f))) },
                valueRange = 0.25f..0.75f
            )
        }

        // 4. OTA Interception & Root-Preserving Upgrade
        HelperCard(title = "4. 原廠 OTA App 攔截與保留 Root 升級") {
            if (otaCandidates.isNotEmpty()) {
                Text(
                    text = "自動偵測疑似 OTA 套件：${otaCandidates.joinToString(", ")}",
                    color = Color(0xFF80D8FF),
                    fontSize = 12.sp
                )
            }
            OutlinedTextField(
                value = config.otaInterceptedPackage,
                onValueChange = { saveCfg(config.copy(otaInterceptedPackage = it)) },
                label = { Text("要攔截並隱藏的原車機 OTA App 套件名稱") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F)),
                    onClick = {
                        scope.launch {
                            val res = RootShell.setPackageDisabled(config.otaInterceptedPackage, true)
                            if (res.isSuccess) {
                                saveCfg(config.copy(otaInterceptActive = true))
                                busyMessage = "已隱藏停用原廠 OTA App：${config.otaInterceptedPackage}"
                            } else {
                                busyMessage = "停用失敗：${res.stderr}"
                            }
                        }
                    }
                ) {
                    Text("確認攔截並隱藏 OTA App")
                }
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            val res = RootShell.setPackageDisabled(config.otaInterceptedPackage, false)
                            if (res.isSuccess) {
                                saveCfg(config.copy(otaInterceptActive = false))
                                busyMessage = "已恢復原廠 OTA App：${config.otaInterceptedPackage}"
                            } else {
                                busyMessage = "恢復失敗：${res.stderr}"
                            }
                        }
                    }
                ) {
                    Text("恢復顯示")
                }
            }
        }
    }
}

@Composable
private fun HelperCard(
    title: String,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF131C2E))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(text = title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            content()
        }
    }
}

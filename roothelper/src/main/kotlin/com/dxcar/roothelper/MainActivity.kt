package com.dxcar.roothelper

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.DeveloperBoard
import androidx.compose.material.icons.rounded.Done
import androidx.compose.material.icons.rounded.GppMaybe
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Navigation
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.SettingsApplications
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Splitscreen
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material.icons.rounded.ViewSidebar
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

private enum class HelperTab(
    val label: String,
    val icon: ImageVector
) {
    OVERVIEW("總覽", Icons.Rounded.Dashboard),
    PLAN_B("方案 B", Icons.Rounded.Splitscreen),
    DISPLAY("60Hz 顯示", Icons.Rounded.Speed),
    OTA("OTA 防護", Icons.Rounded.Shield)
}

private enum class AppPickTarget(val title: String) {
    UI1_NAV("選擇 UI 1 左側預設 App（預設 70%）"),
    UI1_RIGHT("選擇 UI 1 右側預設 App（預設 30%）"),
    UI2_LEFT("選擇 UI 2 左側預設 App"),
    UI2_RIGHT("選擇 UI 2 右側預設 App")
}

private data class InstalledHelperApp(
    val packageName: String,
    val label: String,
    val icon: ImageBitmap?
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installCrashRecorder()
        super.onCreate(savedInstanceState)
        runCatching {
            WindowCompat.setDecorFitsSystemWindows(window, false)
        }
        setContent {
            val systemDark = isSystemInDarkTheme()
            var darkOverride by remember { mutableStateOf<Boolean?>(null) }
            val useDark = darkOverride ?: systemDark

            DisposableEffect(useDark) {
                runCatching {
                    val controller = WindowInsetsControllerCompat(window, window.decorView)
                    controller.isAppearanceLightStatusBars = !useDark
                    controller.isAppearanceLightNavigationBars = !useDark
                }
                onDispose {}
            }

            DxRootHelperTheme(darkTheme = useDark) {
                RootHelperApp(
                    isDark = useDark,
                    onToggleTheme = { darkOverride = !useDark }
                )
            }
        }
    }

    private fun installCrashRecorder() {
        val prev = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching {
                val sw = StringWriter()
                throwable.printStackTrace(PrintWriter(sw))
                val dir = File(cacheDir, "diag_logs").apply { mkdirs() }
                File(dir, "last_crash.txt").writeText(
                    "Version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})\n" +
                        "Device: ${Build.MANUFACTURER} ${Build.MODEL} / Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})\n" +
                        "Thread: ${thread.name}\n\n" +
                        sw.toString()
                )
            }
            prev?.uncaughtException(thread, throwable)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RootHelperApp(
    isDark: Boolean,
    onToggleTheme: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val appVersion = BuildConfig.VERSION_NAME

    var selectedTab by remember { mutableStateOf(HelperTab.OVERVIEW) }
    var rootStatus by remember { mutableStateOf(RootStatusInfo()) }
    var rootProbing by remember { mutableStateOf(true) }
    var config by remember { mutableStateOf(RootHelperConfigStore.load(context)) }
    var hzReport by remember { mutableStateOf<Display60HzReport?>(null) }
    var otaCandidates by remember { mutableStateOf<List<String>>(emptyList()) }
    var installedApps by remember { mutableStateOf<List<InstalledHelperApp>>(emptyList()) }
    var appsLoaded by remember { mutableStateOf(false) }
    var updateInfo by remember { mutableStateOf<HelperUpdateInfo?>(null) }
    var busyLabel by remember { mutableStateOf<String?>(null) }
    var activePickerTarget by remember { mutableStateOf<AppPickTarget?>(null) }
    var lastCrashSummary by remember { mutableStateOf<String?>(null) }
    var showLogConsentDialog by remember { mutableStateOf(false) }

    fun showToastMessage(msg: String) {
        scope.launch {
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(msg)
        }
    }

    fun saveCfg(next: RootHelperConfig) {
        config = next
        RootHelperConfigStore.save(context, next)
    }

    fun refreshRootStatus() {
        scope.launch {
            rootProbing = true
            rootStatus = RootShell.probeRootStatus()
            rootProbing = false
        }
    }

    fun checkUpdate(silentIfLatest: Boolean) {
        scope.launch {
            busyLabel = "檢查 GitHub 更新中…"
            val upd = HelperAutoUpdater.checkForUpdate(appVersion)
            updateInfo = upd
            if (upd == null) {
                busyLabel = null
                if (!silentIfLatest) showToastMessage("無法連線 GitHub 檢查更新")
            } else if (upd.hasUpdate) {
                busyLabel = "下載新版本 v${upd.latestVersion}…"
                val result = HelperAutoUpdater.downloadAndInstall(context, upd)
                busyLabel = null
                showToastMessage(result)
            } else {
                busyLabel = null
                if (!silentIfLatest) showToastMessage("目前已是最新版 v$appVersion")
            }
        }
    }

    fun collectAndSendDiagnosticLog() {
        scope.launch {
            busyLabel = "正在蒐集藍牙架構與系統診斷 Log…"
            val file = RootShell.collectDiagnosticReport(context)
            val verdictLine = runCatching {
                file.readLines().firstOrNull { it.startsWith("BT_ARCHITECTURE_VERDICT:") }.orEmpty()
            }.getOrDefault("")
            busyLabel = null
            showToastMessage("已產出診斷報告：${file.name}")
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
                    "DX-Car 藍牙架構與系統診斷 Log (${Build.MODEL} / Android ${Build.VERSION.RELEASE})"
                )
                putExtra(
                    Intent.EXTRA_TEXT,
                    buildString {
                        appendLine("請協助判讀車機藍牙架構（原生 Android 藍牙 vs. 廠商獨立藍牙服務 gocsdk/nfore/suding）及系統資訊，完整 Log 如附件。")
                        if (verdictLine.isNotBlank()) {
                            appendLine()
                            appendLine(verdictLine)
                        }
                    }
                )
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

    LaunchedEffect(Unit) {
        val crashFile = File(context.cacheDir, "diag_logs/last_crash.txt")
        if (crashFile.exists()) {
            lastCrashSummary = runCatching { crashFile.readText().take(420) }.getOrNull()
        }
        rootProbing = true
        rootStatus = runCatching { RootShell.probeRootStatus() }.getOrDefault(RootStatusInfo())
        rootProbing = false
        otaCandidates = runCatching { RootShell.detectOtaPackages(context) }.getOrDefault(emptyList())
        if (config.otaInterceptedPackage.isBlank() && otaCandidates.isNotEmpty()) {
            saveCfg(config.copy(otaInterceptedPackage = otaCandidates.first()))
        }
        val upd = runCatching { HelperAutoUpdater.checkForUpdate(appVersion) }.getOrNull()
        updateInfo = upd
        if (upd != null && upd.hasUpdate) {
            busyLabel = "偵測到新版本 v${upd.latestVersion}，下載中…"
            val res = runCatching { HelperAutoUpdater.downloadAndInstall(context, upd) }
                .getOrElse { "自動更新失敗：${it.message}" }
            busyLabel = null
            showToastMessage(res)
        }
    }

    // Lazy-load installed apps ONLY when opening Plan B tab or App Picker (avoids startup OOM / icon decode spike!)
    LaunchedEffect(selectedTab, activePickerTarget, appsLoaded) {
        if (!appsLoaded && (selectedTab == HelperTab.PLAN_B || activePickerTarget != null)) {
            installedApps = loadInstalledLaunchableApps(context)
            appsLoaded = true
        }
    }

    val appMap = remember(installedApps) { installedApps.associateBy { it.packageName } }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        val isWideScreen = maxWidth >= 760.dp

        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                Column {
                    TopAppBar(
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                            titleContentColor = MaterialTheme.colorScheme.onSurface
                        ),
                        title = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Rounded.Shield,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                                Column {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = "DX-Car 輔助中心",
                                            style = MaterialTheme.typography.titleLarge,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Surface(
                                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Text(
                                                text = "v$appVersion",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                    Text(
                                        text = "Plan B 雙窗引擎 · 系統調校",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1
                                    )
                                }
                            }
                        },
                        actions = {
                            IconButton(onClick = { refreshRootStatus() }) {
                                Icon(
                                    imageVector = Icons.Rounded.Refresh,
                                    contentDescription = "重新檢查 Root",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(onClick = onToggleTheme) {
                                Icon(
                                    imageVector = if (isDark) Icons.Rounded.LightMode else Icons.Rounded.DarkMode,
                                    contentDescription = "切換淺色／深色外觀",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    )
                    AnimatedVisibility(visible = busyLabel != null || rootProbing) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            LinearProgressIndicator(
                                modifier = Modifier.fillMaxWidth().height(3.dp),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                            )
                            if (!busyLabel.isNullOrBlank()) {
                                Text(
                                    text = busyLabel!!,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }
            },
            bottomBar = {
                if (!isWideScreen) {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        tonalElevation = 0.dp
                    ) {
                        HelperTab.entries.forEach { tab ->
                            NavigationBarItem(
                                selected = selectedTab == tab,
                                onClick = { selectedTab = tab },
                                icon = { Icon(tab.icon, contentDescription = tab.label) },
                                label = { Text(tab.label, maxLines = 1) }
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (isWideScreen) {
                    NavigationRail(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier
                            .fillMaxHeight()
                            .padding(vertical = 8.dp)
                    ) {
                        Spacer(Modifier.height(8.dp))
                        HelperTab.entries.forEach { tab ->
                            NavigationRailItem(
                                selected = selectedTab == tab,
                                onClick = { selectedTab = tab },
                                icon = { Icon(tab.icon, contentDescription = tab.label) },
                                label = { Text(tab.label, maxLines = 1) }
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.TopCenter
                ) {
                    AnimatedContent(
                        targetState = selectedTab,
                        transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(140)) },
                        label = "helperTabContent",
                        modifier = Modifier
                            .widthIn(max = 980.dp)
                            .fillMaxSize()
                    ) { tab ->
                        when (tab) {
                            HelperTab.OVERVIEW -> OverviewTabContent(
                                isWide = isWideScreen,
                                rootStatus = rootStatus,
                                rootProbing = rootProbing,
                                config = config,
                                appVersion = appVersion,
                                updateInfo = updateInfo,
                                lastCrashSummary = lastCrashSummary,
                                onDismissCrash = {
                                    lastCrashSummary = null
                                    runCatching { File(context.cacheDir, "diag_logs/last_crash.txt").delete() }
                                },
                                onRefreshRoot = { refreshRootStatus() },
                                onOpenPlanB = { selectedTab = HelperTab.PLAN_B },
                                onCheckUpdate = { checkUpdate(silentIfLatest = false) },
                                onOpenGithub = {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(HelperAutoUpdater.GITHUB_RELEASES_URL))
                                    runCatching { context.startActivity(intent) }
                                },
                                onSendDiagnosticLog = { showLogConsentDialog = true }
                            )

                            HelperTab.PLAN_B -> PlanBTabContent(
                                isWide = isWideScreen,
                                config = config,
                                appMap = appMap,
                                onUpdateConfig = ::saveCfg,
                                onRequestPickApp = { target -> activePickerTarget = target }
                            )

                            HelperTab.DISPLAY -> Display60HzTabContent(
                                isWide = isWideScreen,
                                config = config,
                                hzReport = hzReport,
                                onAnalyze60Hz = {
                                    scope.launch {
                                        busyLabel = "正在檢測車機顯示與 SurfaceFlinger 60Hz 能力…"
                                        hzReport = RootShell.analyzeDisplay60Hz()
                                        busyLabel = null
                                    }
                                },
                                onToggleForce60Hz = { enabled ->
                                    scope.launch {
                                        busyLabel = if (enabled) "套用 60Hz 更新率中…" else "恢復系統更新率中…"
                                        val res = RootShell.apply60HzSettings(enabled)
                                        busyLabel = null
                                        if (res.isSuccess) {
                                            saveCfg(config.copy(force60HzEnabled = enabled))
                                            showToastMessage(if (enabled) "已套用 60Hz 系統更新率設定" else "已恢復系統預設更新率")
                                        } else {
                                            showToastMessage("套用失敗：${res.stderr.ifBlank { "請確認 Root 授權" }}")
                                        }
                                    }
                                }
                            )

                            HelperTab.OTA -> OtaShieldTabContent(
                                isWide = isWideScreen,
                                config = config,
                                otaCandidates = otaCandidates,
                                onUpdateConfig = ::saveCfg,
                                onDisableOta = {
                                    scope.launch {
                                        busyLabel = "正在停用原廠 OTA 套件…"
                                        val res = RootShell.setPackageDisabled(config.otaInterceptedPackage, true)
                                        busyLabel = null
                                        if (res.isSuccess) {
                                            saveCfg(config.copy(otaInterceptActive = true))
                                            showToastMessage("已停用並隱藏原廠 OTA：${config.otaInterceptedPackage}")
                                        } else {
                                            showToastMessage("停用失敗：${res.stderr.ifBlank { "請確認套件名與 Root 權限" }}")
                                        }
                                    }
                                },
                                onRestoreOta = {
                                    scope.launch {
                                        busyLabel = "正在恢復原廠 OTA 套件…"
                                        val res = RootShell.setPackageDisabled(config.otaInterceptedPackage, false)
                                        busyLabel = null
                                        if (res.isSuccess) {
                                            saveCfg(config.copy(otaInterceptActive = false))
                                            showToastMessage("已恢復原廠 OTA：${config.otaInterceptedPackage}")
                                        } else {
                                            showToastMessage("恢復失敗：${res.stderr.ifBlank { "請確認 Root 權限" }}")
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }

        // Voluntary Privacy-First Diagnostic Log Consent Dialog
        if (showLogConsentDialog) {
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { showLogConsentDialog = false },
                icon = {
                    Icon(
                        imageVector = Icons.Rounded.BugReport,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                },
                title = { Text("遇到問題時寄送診斷 Log") },
                text = {
                    Text(
                        text = "當您在使用上遇到問題需要開發者協助判讀時，可自願選擇將系統診斷 Log 寄送給開發者（dx9901mk5@gmail.com）。\n\n" +
                            "🔒 隱私保護保證：\n" +
                            "• 僅鎖定擷取必要的車機盒子硬體型號、顯示更新率、藍牙架構與 OTA 套件資訊。\n" +
                            "• 絕不紀錄或傳送 GPS 定位座標、個人帳號、聯絡人或隱私資料。\n\n" +
                            "此功能完全自願，若您願意提供協助再點擊下方「同意並產生 Log」。",
                        style = MaterialTheme.typography.bodyMedium,
                        lineHeight = 20.sp
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showLogConsentDialog = false
                            collectAndSendDiagnosticLog()
                        }
                    ) {
                        Text("同意並產生 Log")
                    }
                },
                dismissButton = {
                    OutlinedButton(onClick = { showLogConsentDialog = false }) {
                        Text("暫不使用")
                    }
                }
            )
        }

        // Modern Material 3 ModalBottomSheet Installed App Picker
        val pickerTarget = activePickerTarget
        if (pickerTarget != null) {
            val currentPkg = when (pickerTarget) {
                AppPickTarget.UI1_NAV -> config.ui1NavPackage
                AppPickTarget.UI1_RIGHT -> config.ui1RightPackage
                AppPickTarget.UI2_LEFT -> config.ui2LeftPackage
                AppPickTarget.UI2_RIGHT -> config.ui2RightPackage
            }
            AppPickerBottomSheet(
                title = pickerTarget.title,
                currentPackage = currentPkg,
                installedApps = installedApps,
                onSelectPackage = { pkg ->
                    when (pickerTarget) {
                        AppPickTarget.UI1_NAV -> saveCfg(config.copy(ui1NavPackage = pkg))
                        AppPickTarget.UI1_RIGHT -> saveCfg(config.copy(ui1RightPackage = pkg))
                        AppPickTarget.UI2_LEFT -> saveCfg(config.copy(ui2LeftPackage = pkg))
                        AppPickTarget.UI2_RIGHT -> saveCfg(config.copy(ui2RightPackage = pkg))
                    }
                    activePickerTarget = null
                    val pickedLabel = appMap[pkg]?.label ?: pkg
                    showToastMessage("已更新為「$pickedLabel」")
                },
                onDismiss = { activePickerTarget = null }
            )
        }
    }
}

// ==================== TAB 1: OVERVIEW BENTO DASHBOARD ====================

@Composable
private fun OverviewTabContent(
    isWide: Boolean,
    rootStatus: RootStatusInfo,
    rootProbing: Boolean,
    config: RootHelperConfig,
    appVersion: String,
    updateInfo: HelperUpdateInfo?,
    lastCrashSummary: String?,
    onDismissCrash: () -> Unit,
    onRefreshRoot: () -> Unit,
    onOpenPlanB: () -> Unit,
    onCheckUpdate: () -> Unit,
    onOpenGithub: () -> Unit,
    onSendDiagnosticLog: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val heroAccent = if (rootStatus.isRooted) cs.secondary else cs.error
    val heroBg by animateColorAsState(
        targetValue = if (rootStatus.isRooted) cs.secondaryContainer.copy(alpha = 0.55f) else cs.errorContainer.copy(alpha = 0.50f),
        animationSpec = tween(300),
        label = "rootHeroBg"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (!lastCrashSummary.isNullOrBlank()) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = cs.errorContainer.copy(alpha = 0.6f),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, cs.error.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("偵測到上次異常紀錄（點「診斷 Log 寄送」將自動附上）", style = MaterialTheme.typography.titleSmall, color = cs.onErrorContainer)
                    Text(lastCrashSummary, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, color = cs.onErrorContainer, maxLines = 6, overflow = TextOverflow.Ellipsis)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(onClick = onSendDiagnosticLog, shape = RoundedCornerShape(12.dp)) { Text("寄送錯誤 Log") }
                        OutlinedButton(onClick = onDismissCrash, shape = RoundedCornerShape(12.dp)) { Text("清除提示") }
                    }
                }
            }
        }
        // HERO STATUS CARD
        Surface(
            shape = RoundedCornerShape(26.dp),
            color = cs.surfaceContainer,
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, heroAccent.copy(alpha = 0.35f), RoundedCornerShape(26.dp))
        ) {
            Column(
                modifier = Modifier
                    .background(
                        Brush.linearGradient(
                            colors = listOf(heroBg, cs.surfaceContainer)
                        )
                    )
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = heroAccent.copy(alpha = 0.18f),
                        modifier = Modifier.size(54.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            if (rootProbing) {
                                CircularProgressIndicator(
                                    strokeWidth = 3.dp,
                                    color = cs.primary,
                                    modifier = Modifier.size(26.dp)
                                )
                            } else {
                                Icon(
                                    imageVector = if (rootStatus.isRooted) Icons.Rounded.VerifiedUser else Icons.Rounded.GppMaybe,
                                    contentDescription = null,
                                    tint = heroAccent,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = when {
                                rootProbing -> "正在探測 Root 權限…"
                                rootStatus.isRooted -> "Root 權限已就緒"
                                else -> "尚未取得 su 授權"
                            },
                            style = MaterialTheme.typography.headlineSmall,
                            color = cs.onSurface
                        )
                        Text(
                            text = when {
                                rootProbing -> "檢查 Magisk / su 二進位路徑中"
                                rootStatus.isRooted -> "Magisk ${rootStatus.magiskVersion.ifBlank { "Active" }} · 已可驅動方案 B 雙窗引擎"
                                else -> rootStatus.idOutput.ifBlank { "請先於 Magisk 授予本 App 超級使用者權限" }
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = cs.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    val ctx = LocalContext.current
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (!rootStatus.isRooted) {
                            OutlinedButton(
                                onClick = {
                                    val magiskIntent = ctx.packageManager.getLaunchIntentForPackage("com.topjohnwu.magisk")
                                    if (magiskIntent != null) {
                                        magiskIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        runCatching { ctx.startActivity(magiskIntent) }
                                    } else {
                                        Toast.makeText(ctx, "請開啟車機上的 Magisk App → 超級使用者，將 DX-Car Root 輔助 設為允許", Toast.LENGTH_LONG).show()
                                    }
                                },
                                shape = RoundedCornerShape(14.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)
                            ) {
                                Text("開 Magisk 授權")
                            }
                        }
                        FilledTonalButton(
                            onClick = onRefreshRoot,
                            shape = RoundedCornerShape(14.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)
                        ) {
                            Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("重測")
                        }
                    }
                }

                // 4 HARDWARE TELEMETRY TILES
                val telemetry = listOf(
                    Triple(Icons.Rounded.Security, "Magisk", rootStatus.magiskVersion.ifBlank { "未偵測" }),
                    Triple(Icons.Rounded.Memory, "SoC 平台", rootStatus.socPlatform.ifBlank { Build.HARDWARE }),
                    Triple(Icons.Rounded.DeveloperBoard, "A/B 槽位", rootStatus.slotSuffix.ifBlank { "標準" }),
                    Triple(Icons.Rounded.SettingsApplications, "系統版本", "Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
                )
                if (isWide) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        telemetry.forEach { (icon, title, value) ->
                            TelemetryPill(icon = icon, label = title, value = value, modifier = Modifier.weight(1f))
                        }
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            TelemetryPill(telemetry[0].first, telemetry[0].second, telemetry[0].third, Modifier.weight(1f))
                            TelemetryPill(telemetry[1].first, telemetry[1].second, telemetry[1].third, Modifier.weight(1f))
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            TelemetryPill(telemetry[2].first, telemetry[2].second, telemetry[2].third, Modifier.weight(1f))
                            TelemetryPill(telemetry[3].first, telemetry[3].second, telemetry[3].third, Modifier.weight(1f))
                        }
                    }
                }
            }
        }

        // BENTO ACTION CARDS (2x2 GRID)
        Text(
            text = "快速功能與診斷",
            style = MaterialTheme.typography.titleMedium,
            color = cs.onSurface,
            modifier = Modifier.padding(start = 4.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            BentoActionCard(
                icon = Icons.Rounded.Splitscreen,
                iconTint = cs.primary,
                title = "方案 B 雙窗引擎",
                subtitle = if (config.planBEnabled) "已啟用 · 點擊客製化 App 與比例" else "目前關閉 · 點擊前往啟用",
                badgeText = if (config.planBEnabled) "ON" else "OFF",
                badgePositive = config.planBEnabled,
                onClick = onOpenPlanB,
                modifier = Modifier.weight(1f)
            )
            BentoActionCard(
                icon = Icons.Rounded.BugReport,
                iconTint = cs.tertiary,
                title = "問題診斷 Log（自願寄送）",
                subtitle = "遇問題時可寄給開發者判讀；僅擷取必要盒子資訊，不含定位與個資",
                badgeText = "隱私安全",
                badgePositive = true,
                onClick = onSendDiagnosticLog,
                modifier = Modifier.weight(1f)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            val hasUpd = updateInfo?.hasUpdate == true
            BentoActionCard(
                icon = Icons.Rounded.SystemUpdate,
                iconTint = if (hasUpd) cs.secondary else cs.primary,
                title = "檢查 GitHub 更新",
                subtitle = if (hasUpd) "發現新版 v${updateInfo?.latestVersion}，點擊下載安裝" else "目前版本 v$appVersion（支援 Root 靜默更新）",
                badgeText = if (hasUpd) "NEW" else "v$appVersion",
                badgePositive = hasUpd,
                onClick = onCheckUpdate,
                modifier = Modifier.weight(1f)
            )
            BentoActionCard(
                icon = Icons.AutoMirrored.Rounded.OpenInNew,
                iconTint = cs.onSurfaceVariant,
                title = "GitHub Releases",
                subtitle = "開啟 dx9901mk5/DX-Car-Launcher 發布頁",
                badgeText = "WEB",
                badgePositive = false,
                onClick = onOpenGithub,
                modifier = Modifier.weight(1f)
            )
        }

        // LAUNCHER BRIDGE INFO CARD
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = cs.surfaceContainerLow,
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, cs.outlineVariant.copy(alpha = 0.65f), RoundedCornerShape(22.dp))
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Info,
                    contentDescription = null,
                    tint = cs.primary,
                    modifier = Modifier.size(22.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "主啟動器橋接已待命",
                        style = MaterialTheme.typography.titleSmall,
                        color = cs.onSurface
                    )
                    Text(
                        text = "Google Play 版 DX-Car Launcher 會自動透過 Provider 偵測本輔助 App，解鎖「方案 B：進階雙窗擴充模式」。",
                        style = MaterialTheme.typography.bodySmall,
                        color = cs.onSurfaceVariant
                    )
                }
            }
        }
    }
}

// ==================== TAB 2: PLAN B VISUAL STUDIO ====================

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlanBTabContent(
    isWide: Boolean,
    config: RootHelperConfig,
    appMap: Map<String, InstalledHelperApp>,
    onUpdateConfig: (RootHelperConfig) -> Unit,
    onRequestPickApp: (AppPickTarget) -> Unit
) {
    val cs = MaterialTheme.colorScheme
    var previewUiIndex by remember { mutableIntStateOf(0) } // 0 = UI 1 (70:30), 1 = UI 2 (50:50)

    val ui1NavApp = appMap[config.ui1NavPackage]
    val ui1RightApp = appMap[config.ui1RightPackage]
    val ui2LeftApp = appMap[config.ui2LeftPackage]
    val ui2RightApp = appMap[config.ui2RightPackage]

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. MASTER SWITCHES CARD
        ModernSectionCard(
            icon = Icons.Rounded.Tune,
            title = "方案 B 核心開關",
            subtitle = "維持 0 / 1 / 2 三介面架構，UI 1 與 UI 2 皆支援獨立雙原生 App 分屏"
        ) {
            ModernSwitchTile(
                icon = Icons.Rounded.Splitscreen,
                title = "啟用方案 B 進階雙窗模式",
                subtitle = "開啟後 DX-Car Launcher 設定頁即可切換至方案 B",
                checked = config.planBEnabled,
                onCheckedChange = { onUpdateConfig(config.copy(planBEnabled = it)) }
            )
            HorizontalDivider(color = cs.outlineVariant.copy(alpha = 0.5f))
            ModernSwitchTile(
                icon = Icons.Rounded.ViewSidebar,
                title = "永久顯示 Dock 且所有顯示的 App 自動挪動讓位",
                subtitle = "包含 UI 0 / UI 1 / UI 2 及全螢幕開啟的任何 App 皆自動閃開側邊 Dock 寬度（與本體 App 同步，切回方案 A 自動取消）",
                checked = config.dockPinnedRightShift,
                onCheckedChange = { onUpdateConfig(config.copy(dockPinnedRightShift = it)) }
            )
        }

        // 2. LIVE MINIATURE CAR SCREEN PREVIEW + SPLIT RATIO
        ModernSectionCard(
            icon = Icons.Rounded.Dashboard,
            title = "車機畫面即時預覽與分割比例",
            subtitle = "UI 1 與 UI 2 中央皆有懸浮中線：長壓不放開拖曳比例（附動畫提示）、點兩下左右交換"
        ) {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                val options = listOf("UI 1 · 主雙窗 (預設 70:30)", "UI 2 · 副雙窗 (預設 50:50)")
                options.forEachIndexed { idx, label ->
                    SegmentedButton(
                        selected = previewUiIndex == idx,
                        onClick = { previewUiIndex = idx },
                        shape = SegmentedButtonDefaults.itemShape(index = idx, count = options.size)
                    ) {
                        Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }

            // Live miniature viewport
            val dockWidth by animateDpAsState(
                targetValue = if (config.dockPinnedRightShift) 34.dp else 0.dp,
                animationSpec = tween(260),
                label = "previewDockWidth"
            )
            val activeRatio = if (previewUiIndex == 0) {
                config.ui1SplitRatio.coerceIn(0.25f, 0.75f)
            } else {
                config.ui2SplitRatio.coerceIn(0.25f, 0.75f)
            }
            val leftWeight = activeRatio
            val rightWeight = (1f - leftWeight).coerceAtLeast(0.20f)

            Surface(
                shape = RoundedCornerShape(18.dp),
                color = cs.surfaceContainerLowest,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (isWide) 156.dp else 132.dp)
                    .border(1.2.dp, cs.outlineVariant, RoundedCornerShape(18.dp))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (dockWidth > 0.dp) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = cs.primaryContainer.copy(alpha = 0.7f),
                            modifier = Modifier
                                .width(dockWidth)
                                .fillMaxHeight()
                        ) {
                            Column(
                                modifier = Modifier.fillMaxSize().padding(vertical = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.SpaceBetween
                            ) {
                                Box(Modifier.size(8.dp).clip(CircleShape).background(cs.primary))
                                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                    repeat(3) {
                                        Box(Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).background(cs.onPrimaryContainer.copy(alpha = 0.6f)))
                                    }
                                }
                                Icon(Icons.Rounded.Apps, contentDescription = null, tint = cs.primary, modifier = Modifier.size(14.dp))
                            }
                        }
                    }

                    // Left Window Pane
                    PreviewPaneBox(
                        title = if (previewUiIndex == 0) (ui1NavApp?.label ?: shortPkgLabel(config.ui1NavPackage)) else (ui2LeftApp?.label ?: shortPkgLabel(config.ui2LeftPackage)),
                        ratioText = "${(leftWeight * 100).roundToInt()}%",
                        iconBitmap = if (previewUiIndex == 0) ui1NavApp?.icon else ui2LeftApp?.icon,
                        fallbackIcon = Icons.Rounded.Navigation,
                        tint = cs.primary,
                        modifier = Modifier.weight(leftWeight).fillMaxHeight()
                    )

                    // Center divider indicator
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxHeight().width(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .width(4.dp)
                                .height(36.dp)
                                .clip(CircleShape)
                                .background(cs.primary.copy(alpha = 0.85f))
                        )
                    }

                    // Right Window Pane
                    PreviewPaneBox(
                        title = if (previewUiIndex == 0) "啟動器媒體卡片 (封面/天氣/6捷徑)" else (ui2RightApp?.label ?: shortPkgLabel(config.ui2RightPackage)),
                        ratioText = "${(rightWeight * 100).roundToInt()}%",
                        iconBitmap = if (previewUiIndex == 0) null else ui2RightApp?.icon,
                        fallbackIcon = Icons.Rounded.MusicNote,
                        tint = cs.secondary,
                        modifier = Modifier.weight(rightWeight).fillMaxHeight()
                    )
                }
            }

            // Active UI Split Ratio Slider + Preset Snap Chips
            val leftPct = (activeRatio * 100).roundToInt()
            val rightPct = 100 - leftPct
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (previewUiIndex == 0) "UI 1 預設分割比例" else "UI 2 預設分割比例",
                    style = MaterialTheme.typography.titleSmall,
                    color = cs.onSurface
                )
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = cs.primaryContainer
                ) {
                    Text(
                        text = "$leftPct% : $rightPct%",
                        style = MaterialTheme.typography.labelLarge,
                        color = cs.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }
            Slider(
                value = activeRatio,
                onValueChange = { nextVal ->
                    val clamped = nextVal.coerceIn(0.25f, 0.75f)
                    if (previewUiIndex == 0) {
                        onUpdateConfig(config.copy(ui1SplitRatio = clamped))
                    } else {
                        onUpdateConfig(config.copy(ui2SplitRatio = clamped))
                    }
                },
                valueRange = 0.25f..0.75f
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf(
                    0.30f to "3:7",
                    0.40f to "4:6",
                    0.50f to "5:5 均分",
                    0.60f to "6:4",
                    0.70f to "7:3 導航優先"
                ).forEach { (ratio, label) ->
                    val selected = kotlin.math.abs(activeRatio - ratio) < 0.025f
                    FilterChip(
                        selected = selected,
                        onClick = {
                            if (previewUiIndex == 0) {
                                onUpdateConfig(config.copy(ui1SplitRatio = ratio))
                            } else {
                                onUpdateConfig(config.copy(ui2SplitRatio = ratio))
                            }
                        },
                        label = { Text(label) }
                    )
                }
            }
        }

        // 3. APP ASSIGNMENT CARD (UI 1 NAV + UI 2 DUAL NATIVE APPS)
        ModernSectionCard(
            icon = Icons.Rounded.Apps,
            title = "UI 1 與 UI 2 指定應用程式",
            subtitle = "UI 1 左側為原生地圖、右側為啟動器媒體卡片（可點封面交換）；UI 2 為雙原生視窗"
        ) {
            AppSlotSelectorRow(
                slotBadge = "UI 1 · 原生地圖",
                slotTitle = "UI 1 原生地圖 App（預設 Google 地圖 70%，右側為媒體卡片）",
                packageName = config.ui1NavPackage,
                appInfo = ui1NavApp,
                onClick = { onRequestPickApp(AppPickTarget.UI1_NAV) }
            )
            HorizontalDivider(color = cs.outlineVariant.copy(alpha = 0.5f))
            AppSlotSelectorRow(
                slotBadge = "UI 2 · 左側",
                slotTitle = "UI 2 左側 App（預設地圖）",
                packageName = config.ui2LeftPackage,
                appInfo = ui2LeftApp,
                onClick = { onRequestPickApp(AppPickTarget.UI2_LEFT) }
            )
            HorizontalDivider(color = cs.outlineVariant.copy(alpha = 0.5f))
            AppSlotSelectorRow(
                slotBadge = "UI 2 · 右側",
                slotTitle = "UI 2 右側 App（預設播放器）",
                packageName = config.ui2RightPackage,
                appInfo = ui2RightApp,
                onClick = { onRequestPickApp(AppPickTarget.UI2_RIGHT) }
            )
        }

        // 4. PLAN B OPERATION GUIDE CARD
        ModernSectionCard(
            icon = Icons.Rounded.Info,
            title = "方案 B 操作手勢教學",
            subtitle = "三介面循環切換，點擊 Dock 或 App 清單自動全螢幕不遮擋"
        ) {
            Text(
                text = "• 第 0 介面（0/3）：維持原有自訂小工具看板；進入編輯模式時會鎖定小工具內部點擊，並於每個小工具上方顯示專屬拖曳橫桿。\n" +
                    "• 第 1 介面（1/3）：同方案 A 佈局！左側 70% 調用原生地圖 App（如 Google 地圖），右側 30% 為啟動器媒體卡片（自動擷取封面、左上天氣、下方 6 格捷徑）；點擊專輯封面（或點兩下中線）即可左右交換卡片！\n" +
                    "• 第 2 介面（2/3）：獨立雙原生 App 分割視窗（可自由指定地圖與影音播放器）。\n" +
                    "• 永久顯示 Dock 與自動讓位：開啟「永久顯示 Dock」後，無論在分割畫面或點擊開啟任何全螢幕 App，所有應用程式都會自動向旁挪位閃開 Dock，絕不遮擋（切回方案 A 時會自動取消此開關）。\n" +
                    "• 懸浮中線手勢：長壓不放開出現「發光脈動 ＋ ◀ 比例 ▶」動畫拖曳調比例、點兩下左右交換、左右滑動切換 0 / 1 / 2 介面。",
                style = MaterialTheme.typography.bodyMedium,
                color = cs.onSurfaceVariant,
                lineHeight = 22.sp
            )
        }
    }
}

// ==================== TAB 3: DISPLAY & 60HZ LAB ====================

@Composable
private fun Display60HzTabContent(
    isWide: Boolean,
    config: RootHelperConfig,
    hzReport: Display60HzReport?,
    onAnalyze60Hz: () -> Unit,
    onToggleForce60Hz: (Boolean) -> Unit
) {
    val cs = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        ModernSectionCard(
            icon = Icons.Rounded.Speed,
            title = "車機 60Hz / 60fps 硬體能力判讀",
            subtitle = "透過 Root 讀取 dumpsys display 與 SurfaceFlinger 原始模式清單"
        ) {
            Button(
                onClick = onAnalyze60Hz,
                shape = RoundedCornerShape(14.dp),
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp)
            ) {
                Icon(Icons.Rounded.Speed, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("立即檢測車機是否支援 60Hz")
            }

            if (hzReport != null) {
                val badgeColor = if (hzReport.feasible) cs.secondary else cs.tertiary
                val badgeBg = if (hzReport.feasible) cs.secondaryContainer else cs.tertiaryContainer
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = badgeBg.copy(alpha = 0.45f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, badgeColor.copy(alpha = 0.45f), RoundedCornerShape(18.dp))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = if (hzReport.feasible) Icons.Rounded.CheckCircle else Icons.Rounded.WarningAmber,
                                contentDescription = null,
                                tint = badgeColor,
                                modifier = Modifier.size(22.dp)
                            )
                            Text(
                                text = if (hzReport.feasible) "判斷結果：系統支援 60Hz 模式" else "判斷結果：未偵測到原生 60Hz（硬體鎖定）",
                                style = MaterialTheme.typography.titleSmall,
                                color = cs.onSurface
                            )
                        }
                        Text(
                            text = "目前模式：${hzReport.currentModeSummary}",
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = cs.onSurfaceVariant
                        )
                        Text(
                            text = hzReport.detailMessage,
                            style = MaterialTheme.typography.bodyMedium,
                            color = cs.onSurface
                        )
                    }
                }
            }
        }

        ModernSectionCard(
            icon = Icons.Rounded.Tune,
            title = "系統更新率強制鎖定",
            subtitle = "僅在檢測確認可行或已啟用時開放切換，避免無效寫入系統屬性"
        ) {
            ModernSwitchTile(
                icon = Icons.Rounded.Speed,
                title = "強制鎖定 60Hz 更新率",
                subtitle = "寫入 min/peak/user_refresh_rate = 60.0（實際畫面仍受 CarPlay 通道上限影響）",
                checked = config.force60HzEnabled,
                enabled = hzReport?.feasible == true || config.force60HzEnabled,
                onCheckedChange = onToggleForce60Hz
            )
        }
    }
}

// ==================== TAB 4: OTA SHIELD ====================

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OtaShieldTabContent(
    isWide: Boolean,
    config: RootHelperConfig,
    otaCandidates: List<String>,
    onUpdateConfig: (RootHelperConfig) -> Unit,
    onDisableOta: () -> Unit,
    onRestoreOta: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val shieldColor = if (config.otaInterceptActive) cs.secondary else cs.tertiary

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // STATUS BANNER
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = cs.surfaceContainer,
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, shieldColor.copy(alpha = 0.4f), RoundedCornerShape(24.dp))
        ) {
            Row(
                modifier = Modifier.padding(18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = shieldColor.copy(alpha = 0.16f),
                    modifier = Modifier.size(48.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (config.otaInterceptActive) Icons.Rounded.VerifiedUser else Icons.Rounded.Shield,
                            contentDescription = null,
                            tint = shieldColor,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (config.otaInterceptActive) "已啟用原廠 OTA 攔截防護" else "尚未啟用 OTA 攔截",
                        style = MaterialTheme.typography.titleMedium,
                        color = cs.onSurface
                    )
                    Text(
                        text = if (config.otaInterceptActive) {
                            "已停用 ${config.otaInterceptedPackage}，避免原廠自動更新覆寫 Magisk Root"
                        } else {
                            "建議停用原廠背景 OTA App，避免系統自動覆寫 boot 導致 Root 失效"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = cs.onSurfaceVariant
                    )
                }
            }
        }

        // OTA TARGET SELECTION CARD
        ModernSectionCard(
            icon = Icons.Rounded.SystemUpdate,
            title = "選擇要攔截的原廠 OTA 套件",
            subtitle = "自動掃描車機盒內疑似系統更新套件，點選即可填入"
        ) {
            if (otaCandidates.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    otaCandidates.forEach { pkg ->
                        val selected = config.otaInterceptedPackage == pkg
                        FilterChip(
                            selected = selected,
                            onClick = { onUpdateConfig(config.copy(otaInterceptedPackage = pkg)) },
                            leadingIcon = if (selected) {
                                { Icon(Icons.Rounded.Done, contentDescription = null, modifier = Modifier.size(16.dp)) }
                            } else null,
                            label = {
                                Text(
                                    text = pkg,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        )
                    }
                }
            } else {
                Text(
                    text = "未自動偵測到常見 OTA 套件，您可於下方手動輸入套件名稱。",
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onSurfaceVariant
                )
            }

            OutlinedTextField(
                value = config.otaInterceptedPackage,
                onValueChange = { onUpdateConfig(config.copy(otaInterceptedPackage = it.trim())) },
                label = { Text("目標 OTA 套件名稱 (Package Name)") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            )

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onDisableOta,
                    enabled = config.otaInterceptedPackage.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = cs.error,
                        contentColor = cs.onError
                    ),
                    shape = RoundedCornerShape(14.dp),
                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp)
                ) {
                    Icon(Icons.Rounded.Shield, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("攔截並停用 OTA App")
                }

                OutlinedButton(
                    onClick = onRestoreOta,
                    enabled = config.otaInterceptedPackage.isNotBlank(),
                    shape = RoundedCornerShape(14.dp),
                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp)
                ) {
                    Text("恢復顯示")
                }
            }
        }
    }
}

// ==================== MODERN REUSABLE MATERIAL 3 COMPONENTS ====================

@Composable
private fun ModernSectionCard(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    content: @Composable () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = cs.surfaceContainer,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, cs.outlineVariant.copy(alpha = 0.55f), RoundedCornerShape(24.dp))
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = cs.primaryContainer.copy(alpha = 0.7f),
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(icon, contentDescription = null, tint = cs.primary, modifier = Modifier.size(20.dp))
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = title, style = MaterialTheme.typography.titleMedium, color = cs.onSurface)
                    if (!subtitle.isNullOrBlank()) {
                        Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                    }
                }
            }
            content()
        }
    }
}

@Composable
private fun TelemetryPill(
    icon: ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    val cs = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = cs.surfaceContainerHigh.copy(alpha = 0.85f),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Icon(icon, contentDescription = null, tint = cs.primary, modifier = Modifier.size(14.dp))
                Text(label, style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant, maxLines = 1)
            }
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall,
                color = cs.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun BentoActionCard(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String,
    badgeText: String,
    badgePositive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cs = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = cs.surfaceContainer,
        modifier = modifier
            .border(1.dp, cs.outlineVariant.copy(alpha = 0.55f), RoundedCornerShape(22.dp))
            .clip(RoundedCornerShape(22.dp))
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(13.dp),
                    color = iconTint.copy(alpha = 0.16f),
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(22.dp))
                    }
                }
                Surface(
                    shape = RoundedCornerShape(9.dp),
                    color = if (badgePositive) cs.secondaryContainer else cs.surfaceContainerHighest
                ) {
                    Text(
                        text = badgeText,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (badgePositive) cs.onSecondaryContainer else cs.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = cs.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun ModernSwitchTile(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit
) {
    val cs = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (checked && enabled) cs.primary else cs.onSurfaceVariant,
            modifier = Modifier.size(22.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = if (enabled) cs.onSurface else cs.onSurfaceVariant
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = cs.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            enabled = enabled,
            onCheckedChange = onCheckedChange
        )
    }
}

@Composable
private fun PreviewPaneBox(
    title: String,
    ratioText: String,
    iconBitmap: ImageBitmap?,
    fallbackIcon: ImageVector,
    tint: Color,
    modifier: Modifier = Modifier
) {
    val cs = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = tint.copy(alpha = 0.14f),
        modifier = modifier.border(1.dp, tint.copy(alpha = 0.40f), RoundedCornerShape(12.dp))
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            if (iconBitmap != null) {
                Image(
                    bitmap = iconBitmap,
                    contentDescription = title,
                    modifier = Modifier.size(28.dp).clip(RoundedCornerShape(8.dp))
                )
            } else {
                Icon(fallbackIcon, contentDescription = null, tint = tint, modifier = Modifier.size(26.dp))
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = cs.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = ratioText,
                style = MaterialTheme.typography.labelMedium,
                color = tint
            )
        }
    }
}

@Composable
private fun AppSlotSelectorRow(
    slotBadge: String,
    slotTitle: String,
    packageName: String,
    appInfo: InstalledHelperApp?,
    onClick: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = cs.surfaceContainerHigh,
            modifier = Modifier.size(46.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (appInfo?.icon != null) {
                    Image(
                        bitmap = appInfo.icon,
                        contentDescription = appInfo.label,
                        modifier = Modifier.size(34.dp).clip(RoundedCornerShape(9.dp))
                    )
                } else {
                    Icon(
                        imageVector = Icons.Rounded.Apps,
                        contentDescription = null,
                        tint = cs.primary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = cs.primaryContainer
                ) {
                    Text(
                        text = slotBadge,
                        style = MaterialTheme.typography.labelMedium,
                        color = cs.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                    )
                }
                Text(
                    text = slotTitle,
                    style = MaterialTheme.typography.labelMedium,
                    color = cs.onSurfaceVariant,
                    maxLines = 1
                )
            }
            Spacer(Modifier.height(2.dp))
            Text(
                text = appInfo?.label ?: shortPkgLabel(packageName),
                style = MaterialTheme.typography.titleSmall,
                color = cs.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = packageName.ifBlank { "尚未設定" },
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = cs.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = cs.surfaceContainerHigh
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("更換", style = MaterialTheme.typography.labelMedium, color = cs.primary)
                Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = cs.primary, modifier = Modifier.size(16.dp))
            }
        }
    }
}

// ==================== MODERN MODAL BOTTOM SHEET APP PICKER ====================

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun AppPickerBottomSheet(
    title: String,
    currentPackage: String,
    installedApps: List<InstalledHelperApp>,
    onSelectPackage: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var query by remember { mutableStateOf("") }
    var manualPkg by remember { mutableStateOf(currentPackage) }
    var showManualInput by remember { mutableStateOf(false) }

    val filtered = remember(installedApps, query) {
        val q = query.trim().lowercase()
        if (q.isEmpty()) installedApps
        else installedApps.filter {
            it.label.lowercase().contains(q) || it.packageName.lowercase().contains(q)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = cs.surfaceContainerLow,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = title, style = MaterialTheme.typography.titleLarge, color = cs.onSurface)
                FilledTonalButton(
                    onClick = { showManualInput = !showManualInput },
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(if (showManualInput) "收起手動" else "手動套件名", fontSize = 12.sp)
                }
            }

            // Quick car presets
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf(
                    "Google 地圖" to "com.google.android.apps.maps",
                    "導航王 3D" to "com.kingwaytek.naviking3d",
                    "YouTube" to "com.google.android.youtube",
                    "YT Music" to "com.google.android.apps.youtube.music",
                    "Spotify" to "com.spotify.music"
                ).forEach { (label, pkg) ->
                    FilterChip(
                        selected = currentPackage == pkg,
                        onClick = { onSelectPackage(pkg) },
                        label = { Text(label) }
                    )
                }
            }

            AnimatedVisibility(visible = showManualInput) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = manualPkg,
                        onValueChange = { manualPkg = it.trim() },
                        label = { Text("手動輸入 Package Name") },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        onClick = { if (manualPkg.isNotBlank()) onSelectPackage(manualPkg) },
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text("套用")
                    }
                }
            }

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                placeholder = { Text("搜尋已安裝 App 名稱或套件名…") },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            )

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(340.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(filtered, key = { it.packageName }) { app ->
                    val selected = app.packageName == currentPackage
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = if (selected) cs.primaryContainer.copy(alpha = 0.55f) else cs.surfaceContainer,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { onSelectPackage(app.packageName) }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            if (app.icon != null) {
                                Image(
                                    bitmap = app.icon,
                                    contentDescription = app.label,
                                    modifier = Modifier.size(36.dp).clip(RoundedCornerShape(10.dp))
                                )
                            } else {
                                Icon(Icons.Rounded.Apps, contentDescription = null, tint = cs.primary, modifier = Modifier.size(32.dp))
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = app.label,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = cs.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = app.packageName,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = FontFamily.Monospace,
                                    color = cs.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            if (selected) {
                                Icon(
                                    imageVector = Icons.Rounded.CheckCircle,
                                    contentDescription = "已選擇",
                                    tint = cs.primary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ==================== HELPERS ====================

private fun shortPkgLabel(pkg: String): String = when (pkg) {
    "com.google.android.apps.maps" -> "Google 地圖"
    "com.kingwaytek.naviking3d" -> "導航王 3D"
    "com.google.android.youtube" -> "YouTube"
    "com.google.android.apps.youtube.music" -> "YT Music"
    "com.spotify.music" -> "Spotify"
    "" -> "尚未選擇"
    else -> pkg.substringAfterLast('.')
}

private suspend fun loadInstalledLaunchableApps(context: Context): List<InstalledHelperApp> = withContext(Dispatchers.IO) {
    runCatching {
        val pm = context.packageManager
        val mainIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        @Suppress("DEPRECATION")
        val resolved = pm.queryIntentActivities(mainIntent, PackageManager.MATCH_ALL)
        val seen = HashSet<String>()
        resolved.mapNotNull { ri ->
            val pkg = ri.activityInfo?.packageName ?: return@mapNotNull null
            if (!seen.add(pkg)) return@mapNotNull null
            val label = runCatching { ri.loadLabel(pm).toString() }.getOrDefault(pkg)
            val icon = runCatching { drawableToSafeSoftwareBitmap(ri.loadIcon(pm), 64)?.asImageBitmap() }.getOrNull()
            InstalledHelperApp(packageName = pkg, label = label, icon = icon)
        }.sortedBy { it.label.lowercase() }
    }.getOrDefault(emptyList())
}

/**
 * Always renders into a fresh ARGB_8888 software Bitmap so Android 12–15 HARDWARE Bitmaps
 * or zero-sized OEM drawables can NEVER crash Compose Canvas rendering.
 */
private fun drawableToSafeSoftwareBitmap(drawable: Drawable?, sizePx: Int): Bitmap? {
    if (drawable == null || sizePx <= 0) return null
    return runCatching {
        if (drawable is BitmapDrawable) {
            val src = drawable.bitmap
            if (src != null && !src.isRecycled && src.width > 0 && src.height > 0 && src.config != Bitmap.Config.HARDWARE) {
                return@runCatching Bitmap.createScaledBitmap(src, sizePx, sizePx, true)
            }
        }
        val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        drawable.setBounds(0, 0, sizePx, sizePx)
        drawable.draw(canvas)
        bmp
    }.getOrNull()
}

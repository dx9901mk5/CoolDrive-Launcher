package com.dxcar.launcher.ui

import android.app.Activity
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Intent
import android.os.Bundle
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Navigation
import androidx.compose.material.icons.rounded.OpenInFull
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material.icons.rounded.Work
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.dxcar.launcher.data.LauncherConfig
import com.dxcar.launcher.data.PageZeroWidgetItem
import com.dxcar.launcher.i18n.DxCarI18n.str
import com.dxcar.launcher.map.VehicleLocationState
import com.dxcar.launcher.system.SystemStatus
import com.dxcar.launcher.theme.LocalDxCarColors
import com.dxcar.launcher.weather.LocalWeatherState
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

private const val PAGE_ZERO_WIDGET_HOST_ID = 0x4458

/**
 * Page 0 UI (Left of Page 1 in 0-1-2 order):
 * - Reached by swiping right on the center seam of Page 1.
 * - Lets users add both Built-in Car Widgets (Clock, Speedometer, Weather, Quick Nav, System)
 *   and real Android System AppWidgets (via AppWidgetHost).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PageZeroWidgetBoard(
    config: LauncherConfig,
    sysStatus: SystemStatus,
    location: VehicleLocationState,
    weather: LocalWeatherState,
    isNight: Boolean,
    onAddWidget: (PageZeroWidgetItem) -> Unit,
    onRemoveWidget: (PageZeroWidgetItem) -> Unit,
    onUpdateWidget: (PageZeroWidgetItem) -> Unit = {},
    onQuickNavigateQuery: (String) -> Unit,
    onWeatherClick: () -> Unit,
    onGoPageOne: () -> Unit,
    seamHandle: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalDxCarColors.current
    val context = LocalContext.current
    val appWidgetManager = remember { AppWidgetManager.getInstance(context) }
    val appWidgetHost = remember { AppWidgetHost(context.applicationContext, PAGE_ZERO_WIDGET_HOST_ID) }

    DisposableEffect(appWidgetHost) {
        runCatching { appWidgetHost.startListening() }
        onDispose {
            runCatching { appWidgetHost.stopListening() }
        }
    }

    var showAddWidgetSheet by remember { mutableStateOf(false) }
    var isEditMode by remember { mutableStateOf(false) }
    var pendingBindProvider by remember { mutableStateOf<AppWidgetProviderInfo?>(null) }
    var pendingBindWidgetId by remember { mutableIntStateOf(-1) }

    val bindWidgetLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val id = pendingBindWidgetId
        val prov = pendingBindProvider
        pendingBindWidgetId = -1
        pendingBindProvider = null
        if (result.resultCode == Activity.RESULT_OK && id != -1 && prov != null) {
            onAddWidget(
                PageZeroWidgetItem(
                    type = "APP_WIDGET",
                    appWidgetId = id,
                    label = prov.loadLabel(context.packageManager)
                )
            )
            showAddWidgetSheet = false
        } else if (id != -1) {
            runCatching { appWidgetHost.deleteAppWidgetId(id) }
        }
    }

    fun tryAddSystemAppWidget(info: AppWidgetProviderInfo) {
        val id = appWidgetHost.allocateAppWidgetId()
        val bound = runCatching {
            appWidgetManager.bindAppWidgetIdIfAllowed(id, info.provider)
        }.getOrDefault(false)
        if (bound) {
            onAddWidget(
                PageZeroWidgetItem(
                    type = "APP_WIDGET",
                    appWidgetId = id,
                    label = info.loadLabel(context.packageManager)
                )
            )
            showAddWidgetSheet = false
        } else {
            pendingBindWidgetId = id
            pendingBindProvider = info
            val bindIntent = Intent(AppWidgetManager.ACTION_APPWIDGET_BIND).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.provider)
            }
            runCatching { bindWidgetLauncher.launch(bindIntent) }.onFailure {
                runCatching { appWidgetHost.deleteAppWidgetId(id) }
                pendingBindWidgetId = -1
                pendingBindProvider = null
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.canvasBackground)
            .padding(start = 14.dp, top = 12.dp, bottom = 12.dp, end = 22.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.Widgets,
                        contentDescription = null,
                        tint = colors.accent,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = str(
                            "第 0 介面 · 自訂小工具",
                            "第 0 界面 · 自定义小组件",
                            "Page 0 · Custom Widgets",
                            "ページ 0 · カスタムウィジェット"
                        ),
                        color = colors.primaryText,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (config.pageZeroWidgets.isNotEmpty()) {
                        OutlinedButton(
                            onClick = { isEditMode = !isEditMode },
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(Icons.Rounded.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(5.dp))
                            Text(
                                if (isEditMode) str("完成", "完成", "Done", "完了")
                                else str("編輯", "编辑", "Edit", "編集"),
                                fontSize = 12.sp
                            )
                        }
                    }
                    Button(
                        onClick = { showAddWidgetSheet = true },
                        colors = ButtonDefaults.buttonColors(containerColor = colors.accent, contentColor = colors.onAccent),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(17.dp))
                        Spacer(Modifier.width(5.dp))
                        Text(str("新增小工具", "添加小组件", "Add Widget", "ウィジェット追加"), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    OutlinedButton(
                        onClick = onGoPageOne,
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text(str("回第一介面", "回第一界面", "Page 1", "ページ 1 へ"), fontSize = 12.sp)
                        Spacer(Modifier.width(4.dp))
                        Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null, modifier = Modifier.size(15.dp))
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            if (config.pageZeroWidgets.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .background(colors.cardSurface)
                        .border(1.dp, colors.cardBorder, RoundedCornerShape(24.dp))
                        .clickable { showAddWidgetSheet = true },
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = null, tint = colors.accent, modifier = Modifier.size(44.dp))
                        Text(
                            text = str(
                                "點擊「新增小工具」自訂您的第 0 介面",
                                "点击「添加小组件」自定义您的第 0 界面",
                                "Tap 'Add Widget' to customize your Page 0",
                                "「ウィジェット追加」をタップしてページ 0 を作成"
                            ),
                            color = colors.primaryText,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = str(
                                "支援車速儀表、大字時鐘、天氣、快速導航及手機／車機已安裝的 Android 系統小工具；向畫面左側滑動右側邊條即可返回第一介面",
                                "支持车速仪表、大字时钟、天气、快速导航及已安装的 Android 系统小组件；向左滑动右侧边条即可返回第一界面",
                                "Supports Speedometer, Clock, Weather, Quick Nav, and Android System AppWidgets; swipe left from the right edge to return to Page 1",
                                "速度計・時計・天気・クイックナビや Android ウィジェットに対応。右端を左スワイプでページ 1 に戻ります"
                            ),
                            color = colors.secondaryText,
                            fontSize = 12.sp
                        )
                    }
                }
            } else {
                BoxWithConstraints(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    val density = LocalDensity.current
                    val haptic = LocalHapticFeedback.current
                    val canvasW = maxWidth.value.coerceAtLeast(320f)
                    val canvasH = maxHeight.value.coerceAtLeast(220f)

                    config.pageZeroWidgets.forEachIndexed { idx, widget ->
                        val defaultCol = idx % 3
                        val defaultRow = idx / 3
                        val initX = if (widget.xDp >= 0f) widget.xDp else (defaultCol * 292f).coerceIn(0f, (canvasW - 180f).coerceAtLeast(0f))
                        val initY = if (widget.yDp >= 0f) widget.yDp else (defaultRow * 202f).coerceIn(0f, (canvasH - 130f).coerceAtLeast(0f))

                        var posX by remember(widget.id, widget.xDp) { mutableFloatStateOf(initX) }
                        var posY by remember(widget.id, widget.yDp) { mutableFloatStateOf(initY) }
                        var cardW by remember(widget.id, widget.widthDp) { mutableFloatStateOf(widget.widthDp.coerceIn(180f, 620f)) }
                        var cardH by remember(widget.id, widget.heightDp) { mutableFloatStateOf(widget.heightDp.coerceIn(130f, 440f)) }

                        Box(
                            modifier = Modifier
                                .offset(x = posX.dp, y = posY.dp)
                                .width(cardW.dp)
                                .height(cardH.dp)
                                .clip(RoundedCornerShape(22.dp))
                                .background(colors.cardSurface)
                                .border(
                                    width = if (isEditMode) 1.6.dp else 1.dp,
                                    color = if (isEditMode) colors.accent else colors.cardBorder,
                                    shape = RoundedCornerShape(22.dp)
                                )
                                .pointerInput(widget.id, canvasW, canvasH) {
                                    detectDragGesturesAfterLongPress(
                                        onDragStart = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        },
                                        onDrag = { change, dragAmount ->
                                            change.consume()
                                            val dxDp = dragAmount.x / density.density
                                            val dyDp = dragAmount.y / density.density
                                            posX = (posX + dxDp).coerceIn(0f, (canvasW - cardW).coerceAtLeast(0f))
                                            posY = (posY + dyDp).coerceIn(0f, (canvasH - cardH).coerceAtLeast(0f))
                                        },
                                        onDragEnd = {
                                            onUpdateWidget(widget.copy(xDp = posX, yDp = posY))
                                        }
                                    )
                                }
                        ) {
                            when (widget.type) {
                                "CLOCK" -> BuiltInClockWidget(sysStatus, isNight)
                                "SPEED" -> BuiltInSpeedWidget(location)
                                "WEATHER" -> BuiltInWeatherWidget(weather, onWeatherClick)
                                "QUICK_NAV" -> BuiltInQuickNavWidget(config, onQuickNavigateQuery)
                                "SYSTEM" -> BuiltInSystemWidget(sysStatus)
                                "APP_WIDGET" -> {
                                    val info = remember(widget.appWidgetId) {
                                        if (widget.appWidgetId > 0) {
                                            runCatching { appWidgetManager.getAppWidgetInfo(widget.appWidgetId) }.getOrNull()
                                        } else null
                                    }
                                    if (info != null) {
                                        AndroidView(
                                            factory = { ctx ->
                                                val hostView = appWidgetHost.createView(ctx, widget.appWidgetId, info)
                                                hostView.setAppWidget(widget.appWidgetId, info)
                                                hostView.updateAppWidgetOptions(Bundle())
                                                FrameLayout(ctx).apply {
                                                    layoutParams = ViewGroup.LayoutParams(
                                                        ViewGroup.LayoutParams.MATCH_PARENT,
                                                        ViewGroup.LayoutParams.MATCH_PARENT
                                                    )
                                                    addView(
                                                        hostView,
                                                        FrameLayout.LayoutParams(
                                                            ViewGroup.LayoutParams.MATCH_PARENT,
                                                            ViewGroup.LayoutParams.MATCH_PARENT
                                                        )
                                                    )
                                                }
                                            },
                                            modifier = Modifier.fillMaxSize().padding(8.dp)
                                        )
                                    } else {
                                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                            Text(
                                                text = widget.label.ifBlank { "AppWidget" },
                                                color = colors.secondaryText,
                                                fontSize = 13.sp
                                            )
                                        }
                                    }
                                }
                            }

                            // Bottom-right Resize Handle (always accessible in edit mode or subtle corner)
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(6.dp)
                                    .size(26.dp)
                                    .clip(CircleShape)
                                    .background(colors.cardSurfaceElevated.copy(alpha = 0.88f))
                                    .border(1.dp, colors.accent.copy(alpha = 0.55f), CircleShape)
                                    .pointerInput(widget.id, canvasW, canvasH) {
                                        detectDragGestures(
                                            onDrag = { change, dragAmount ->
                                                change.consume()
                                                val dwDp = dragAmount.x / density.density
                                                val dhDp = dragAmount.y / density.density
                                                cardW = (cardW + dwDp).coerceIn(180f, (canvasW - posX).coerceAtLeast(180f))
                                                cardH = (cardH + dhDp).coerceIn(130f, (canvasH - posY).coerceAtLeast(130f))
                                            },
                                            onDragEnd = {
                                                onUpdateWidget(widget.copy(widthDp = cardW, heightDp = cardH))
                                            }
                                        )
                                    }
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.OpenInFull,
                                    contentDescription = str("縮放大小", "缩放大小", "Resize", "サイズ変更"),
                                    tint = colors.accent,
                                    modifier = Modifier.size(14.dp)
                                )
                            }

                            if (isEditMode) {
                                IconButton(
                                    onClick = {
                                        if (widget.type == "APP_WIDGET" && widget.appWidgetId > 0) {
                                            runCatching { appWidgetHost.deleteAppWidgetId(widget.appWidgetId) }
                                        }
                                        onRemoveWidget(widget)
                                    },
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(6.dp)
                                        .size(28.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xD9EF4444))
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.DeleteOutline,
                                        contentDescription = str("移除", "移除", "Remove", "削除"),
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Right-edge seam handle to swipe back to Page 1
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
        ) {
            seamHandle()
        }

        // Add Widget Picker Modal
        if (showAddWidgetSheet) {
            val installedProviders = remember {
                runCatching { appWidgetManager.installedProviders }.getOrDefault(emptyList())
                    .sortedBy { it.loadLabel(context.packageManager) }
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.58f))
                    .clickable { showAddWidgetSheet = false },
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    color = colors.cardSurface,
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier
                        .fillMaxWidth(0.86f)
                        .fillMaxHeight(0.84f)
                        .border(1.dp, colors.cardBorder, RoundedCornerShape(24.dp))
                        .clickable(enabled = false) {}
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = str("新增小工具至第 0 介面", "添加小组件至第 0 界面", "Add Widget to Page 0", "ページ 0 にウィジェットを追加"),
                                color = colors.primaryText,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold
                            )
                            IconButton(onClick = { showAddWidgetSheet = false }) {
                                Icon(Icons.Rounded.Close, contentDescription = null, tint = colors.primaryText)
                            }
                        }

                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = str("內建車用小工具", "内置车用小组件", "Built-in Car Widgets", "内蔵車載ウィジェット"),
                            color = colors.accent,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(6.dp))

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val builtIns = listOf(
                                "CLOCK" to str("大字時鐘日期", "大字时钟日期", "Clock & Date", "時計・日付"),
                                "SPEED" to str("GPS 車速儀表", "GPS 车速仪表", "GPS Speedometer", "GPS 速度計"),
                                "WEATHER" to str("即時天氣", "实时天气", "Live Weather", "現在の天気"),
                                "QUICK_NAV" to str("快速導航捷徑", "快速导航捷径", "Quick Nav", "クイックナビ"),
                                "SYSTEM" to str("系統連線狀態", "系统连接状态", "System Status", "システム状態")
                            )
                            builtIns.forEach { (typeKey, lbl) ->
                                Button(
                                    onClick = {
                                        onAddWidget(PageZeroWidgetItem(type = typeKey, label = lbl))
                                        showAddWidgetSheet = false
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = colors.cardSurfaceElevated, contentColor = colors.primaryText),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text("＋ $lbl", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }

                        Spacer(Modifier.height(14.dp))
                        Text(
                            text = str("Android 系統小工具 (${installedProviders.size})", "Android 系统小组件 (${installedProviders.size})", "Android System Widgets (${installedProviders.size})", "Android システムウィジェット (${installedProviders.size})"),
                            color = colors.accent,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(6.dp))

                        LazyColumn(
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(installedProviders) { info ->
                                val label = remember(info) { info.loadLabel(context.packageManager) }
                                val pkgLabel = remember(info) {
                                    runCatching {
                                        val appInfo = context.packageManager.getApplicationInfo(info.provider.packageName, 0)
                                        context.packageManager.getApplicationLabel(appInfo).toString()
                                    }.getOrDefault(info.provider.packageName)
                                }
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(colors.cardSurfaceElevated)
                                        .clickable { tryAddSystemAppWidget(info) }
                                        .padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(label, color = colors.primaryText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(pkgLabel, color = colors.secondaryText, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                    Text("＋", color = colors.accent, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BuiltInClockWidget(sysStatus: SystemStatus, isNight: Boolean) {
    val colors = LocalDxCarColors.current
    val timeStr = remember(sysStatus.now) { sysStatus.now.format(DateTimeFormatter.ofPattern("HH:mm")) }
    val dateStr = remember(sysStatus.now) { sysStatus.now.format(DateTimeFormatter.ofPattern("yyyy/MM/dd · EEE")) }
    Column(
        modifier = Modifier.fillMaxSize().padding(18.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.AccessTime, contentDescription = null, tint = colors.accent, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                text = if (isNight) str("夜間行車模式", "夜间行车模式", "Night Mode", "夜間モード") else str("日間行車模式", "日间行车模式", "Day Mode", "昼間モード"),
                color = colors.secondaryText,
                fontSize = 12.sp
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(timeStr, color = colors.primaryText, fontSize = 44.sp, fontWeight = FontWeight.ExtraBold)
        Text(dateStr, color = colors.accent, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun BuiltInSpeedWidget(location: VehicleLocationState) {
    val colors = LocalDxCarColors.current
    val kmh = location.speedKmh.coerceAtLeast(0)
    Column(
        modifier = Modifier.fillMaxSize().padding(18.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Speed, contentDescription = null, tint = colors.accent, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(str("即時 GPS 車速", "实时 GPS 车速", "Live GPS Speed", "リアルタイム車速"), color = colors.secondaryText, fontSize = 12.sp)
        }
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text("$kmh", color = colors.primaryText, fontSize = 48.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.width(8.dp))
            Text("km/h", color = colors.accent, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
        }
    }
}

@Composable
private fun BuiltInWeatherWidget(weather: LocalWeatherState, onWeatherClick: () -> Unit) {
    val colors = LocalDxCarColors.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .clickable { onWeatherClick() }
            .padding(18.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.WbSunny, contentDescription = null, tint = Color(0xFFFBBF24), modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(6.dp))
            Text(weather.locationName, color = colors.secondaryText, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = weather.temperatureText.ifBlank { "--°C" },
            color = colors.primaryText,
            fontSize = 40.sp,
            fontWeight = FontWeight.ExtraBold
        )
        Text(
            text = weather.conditionText.ifBlank { str("點擊開啟天氣", "点击开启天气", "Tap to open Weather", "タップして天気を開く") },
            color = colors.accent,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BuiltInQuickNavWidget(config: LauncherConfig, onQuickNavigateQuery: (String) -> Unit) {
    val colors = LocalDxCarColors.current
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Navigation, contentDescription = null, tint = colors.accent, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(str("一鍵快速導航", "一键快速导航", "Quick Navigation", "クイックナビ"), color = colors.primaryText, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (config.homeAddress.isNotBlank()) {
                Button(onClick = { onQuickNavigateQuery(config.homeAddress) }, shape = RoundedCornerShape(12.dp)) {
                    Icon(Icons.Rounded.Home, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(config.homeShortcutLabel.ifBlank { str("回家", "回家", "Home", "自宅") }, fontSize = 12.sp)
                }
            }
            if (config.workAddress.isNotBlank()) {
                Button(onClick = { onQuickNavigateQuery(config.workAddress) }, shape = RoundedCornerShape(12.dp)) {
                    Icon(Icons.Rounded.Work, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(config.workShortcutLabel.ifBlank { str("公司", "公司", "Work", "職場") }, fontSize = 12.sp)
                }
            }
            config.customNavShortcuts.take(4).forEach { sc ->
                OutlinedButton(onClick = { onQuickNavigateQuery(sc.address) }, shape = RoundedCornerShape(12.dp)) {
                    Text(sc.label, fontSize = 12.sp)
                }
            }
            if (config.homeAddress.isBlank() && config.workAddress.isBlank() && config.customNavShortcuts.isEmpty()) {
                Text(
                    text = str("請於地圖搜尋列設定回家／公司或常用地址", "请在地图搜索栏设置回家／公司或常用地址", "Set Home/Work or custom addresses in Map Search", "地図検索バーで自宅・職場を設定してください"),
                    color = colors.secondaryText,
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
private fun BuiltInSystemWidget(sysStatus: SystemStatus) {
    val colors = LocalDxCarColors.current
    Column(
        modifier = Modifier.fillMaxSize().padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(str("車機連線與系統狀態", "车机连接与系统状态", "Car System Status", "車載システム状態"), color = colors.primaryText, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Text("Wi-Fi: ${if (sysStatus.wifiConnected) sysStatus.wifiSsid.ifBlank { "Connected" } else "Off"}", color = colors.secondaryText, fontSize = 13.sp)
        Text("Bluetooth: ${if (sysStatus.bluetoothConnected) sysStatus.connectedBtName.ifBlank { "Connected" } else "Standby"}", color = colors.secondaryText, fontSize = 13.sp)
        Text("Brightness: ${sysStatus.brightnessPercent}% · Volume: ${sysStatus.mediaVolumePercent}%", color = colors.accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

package com.dxcar.launcher.overlay

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DragIndicator
import androidx.compose.material.icons.rounded.KeyboardDoubleArrowDown
import androidx.compose.material.icons.rounded.KeyboardDoubleArrowUp
import androidx.compose.material.icons.rounded.NearMe
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.ViewInAr
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dxcar.launcher.accessibility.DxCarAccessibilityService
import com.dxcar.launcher.comms.RemoteCursorState
import com.dxcar.launcher.i18n.DxCarI18n
import com.dxcar.launcher.i18n.DxCarI18n.str
import com.dxcar.launcher.system.PermissionHelper
import com.dxcar.launcher.theme.LocalDxCarColors
import com.dxcar.launcher.ui.bouncyClickable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class Map3DAction {
    TILT_UP_3D,
    TILT_DOWN_2D,
    ZOOM_IN,
    ZOOM_OUT
}

object Cursor3DStateHolder {
    private val _active = MutableStateFlow(false)
    val active: StateFlow<Boolean> = _active.asStateFlow()

    private val _showTutorial = MutableStateFlow(false)
    val showTutorial: StateFlow<Boolean> = _showTutorial.asStateFlow()

    private val _builtinMapActive = MutableStateFlow(false)
    val builtinMapActive: StateFlow<Boolean> = _builtinMapActive.asStateFlow()

    private val _builtinMapCommands = MutableSharedFlow<Map3DAction>(extraBufferCapacity = 16)
    val builtinMapCommands: SharedFlow<Map3DAction> = _builtinMapCommands.asSharedFlow()

    fun setActive(enabled: Boolean) {
        _active.value = enabled
        if (!enabled) _showTutorial.value = false
    }

    fun toggle(): Boolean {
        val next = !_active.value
        setActive(next)
        return next
    }

    fun openTutorial() {
        _showTutorial.value = true
    }

    fun closeTutorial() {
        _showTutorial.value = false
    }

    fun setBuiltinMapActive(active: Boolean) {
        _builtinMapActive.value = active
    }

    fun emitBuiltinMapAction(action: Map3DAction) {
        _builtinMapCommands.tryEmit(action)
    }
}

/**
 * Reusable Extra-Large Font Tutorial Modal ("說明字體要特大", Title 24-26sp / Body 20-22sp).
 * Responsive width ensures close button can NEVER be pushed off-screen on narrow foldables or tablets.
 */
@Composable
fun ExtraLargeTutorialModal(
    title: String,
    steps: List<String>,
    highlightNote: String = "",
    actionButtonLabel: String? = null,
    onActionClick: (() -> Unit)? = null,
    onClose: () -> Unit
) {
    val colors = LocalDxCarColors.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.78f))
            .clickable { onClose() },
        contentAlignment = Alignment.Center
    ) {
        Surface(
            color = colors.cardSurface,
            shape = RoundedCornerShape(26.dp),
            shadowElevation = 24.dp,
            modifier = Modifier
                .widthIn(max = 720.dp)
                .fillMaxWidth(0.92f)
                .padding(vertical = 16.dp)
                .border(2.dp, colors.accent, RoundedCornerShape(26.dp))
                .clickable(enabled = false) {}
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = title,
                        color = colors.primaryText,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 10.dp)
                    )
                    Button(
                        onClick = onClose,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.accent,
                            contentColor = colors.onAccent
                        ),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Rounded.Close, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = str("關閉", "关闭", "Close", "閉じる"),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                    }
                }

                steps.forEachIndexed { idx, step ->
                    Surface(
                        color = colors.cardSurfaceElevated,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, colors.cardBorder, RoundedCornerShape(16.dp))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Surface(
                                color = colors.accent,
                                shape = CircleShape,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = "${idx + 1}",
                                        color = colors.onAccent,
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                }
                            }
                            Spacer(Modifier.width(12.dp))
                            Text(
                                text = step,
                                color = colors.primaryText,
                                fontSize = 19.sp,
                                lineHeight = 28.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                if (highlightNote.isNotBlank()) {
                    Surface(
                        color = colors.accent.copy(alpha = 0.16f),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.5.dp, colors.accent, RoundedCornerShape(16.dp))
                    ) {
                        Text(
                            text = highlightNote,
                            color = colors.accent,
                            fontSize = 18.sp,
                            lineHeight = 26.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }

                if (actionButtonLabel != null && onActionClick != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        OutlinedButton(
                            onClick = onActionClick,
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text(actionButtonLabel, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun tutorial3DControlSteps(): List<String> = listOf(
    str(
        "點擊 Dock 上的【3D】圖示後，會出現一個小巧可拖曳的「3D 雙指懸浮條」，完全不遮擋地圖與 Dock，畫面其餘區域皆可正常觸控與返回。",
        "点击 Dock 上的【3D】图标后，会出现一个小巧可拖拽的「3D 双指悬浮条」，完全不遮挡地图与 Dock，画面其余区域均可正常触控与返回。",
        "Tap [3D] on the Dock to summon a compact, draggable 3D Control Bar. Everything outside the bar stays 100% touchable—it never traps the screen or Dock.",
        "ドックの【3D】をタップすると、ドラッグ移動できる小型の「3D ツールバー」が表示されます。バー以外の画面やドックは常に通常通り操作でき、操作不能になりません。"
    ),
    str(
        "一鍵觸發雙指手勢：\n• 3D 仰角（⬆）＝ 傾斜進入 3D 建築俯瞰\n• 2D 平視（⬇）＝ 恢復平面俯視視角\n• 放大（＋）／縮小（－）＝ 快速縮放地圖",
        "一键触发双指手势：\n• 3D 仰角（⬆）＝ 倾斜进入 3D 建筑俯瞰\n• 2D 平视（⬇）＝ 恢复平面俯视视角\n• 放大（＋）／缩小（－）＝ 快速缩放地图",
        "One-tap 2-finger actions:\n• 3D Tilt (⬆) = Tilt into 3D buildings view\n• 2D Flat (⬇) = Return to flat top-down view\n• Zoom + / Zoom − = Smoothly zoom in or out",
        "ワンタップで 2 本指操作：\n• 3D 視点（⬆）＝ 3D 俯瞰アングルへ傾ける\n• 2D 平面（⬇）＝ 真上からの平面視点に戻す\n• 拡大（＋）／縮小（－）＝ 地図をズーム"
    ),
    str(
        "支援「長按連發微調」：長按任一按鈕可連續微調多段角度或縮放倍率；按住頂部握把可將懸浮條拖曳至螢幕任意角落。",
        "支持「长按连发微调」：长按任一按钮可连续微调多段角度或缩放倍率；按住顶部握把可将悬浮条拖拽至屏幕任意角落。",
        "Hold-to-Repeat Fine Tuning: press and hold any button to step multiple increments smoothly; drag the top handle to place the bar anywhere on screen.",
        "長押し連続微調整に対応：ボタンを長押しすると段階的に角度・倍率を微調整できます。上部ハンドルをドラッグして好きな位置へ移動可能です。"
    ),
    str(
        "隨時可安全關閉：點擊懸浮條右上角紅色【✕】或再次點擊 Dock【3D】按鈕皆可立即收起，絕不卡住畫面。",
        "随时可安全关闭：点击悬浮条右上角红色【✕】或再次点击 Dock【3D】按钮均可立即收起，绝不卡住画面。",
        "Always Easy to Close: tap the red [✕] button on the bar or tap the Dock [3D] icon again anytime.",
        "いつでも安全に終了：バー右上の赤い【✕】またはドックの【3D】ボタンで即座に閉じられます。"
    )
)

/**
 * Non-Blocking Compact Draggable 4-Button 3D Gesture Control Bar (WRAP_CONTENT):
 * - NEVER covers the full screen or intercepts touches outside its compact card bounds.
 * - Safe on Foldables (inner & cover screens), Tablets (portrait & landscape), and Car AI Boxes.
 * - Built-in MapLibre Map: directly animates camera tilt/zoom at 60fps (no Accessibility required!).
 * - External Maps (Google Maps / Waze): injects real 2-finger gestures via AccessibilityService
 *   targeting the open map center outside the bar's bounds (post finger-up so Android never cancels!).
 */
@Composable
fun Floating3DControlBar(
    onOpenTutorial: () -> Unit,
    onExit3DMode: () -> Unit,
    onDragDelta: ((dx: Float, dy: Float) -> Unit)? = null,
    resolveExternalGestureAnchor: (() -> Pair<Float, Float>?)? = null,
    modifier: Modifier = Modifier
) {
    val colors = LocalDxCarColors.current
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    val builtinMapActive by Cursor3DStateHolder.builtinMapActive.collectAsState()
    val accessibilityConnected by DxCarAccessibilityService.isConnected.collectAsState()

    var statusFlash by remember { mutableStateOf<String?>(null) }
    var statusSeq by remember { mutableIntStateOf(0) }
    var missingAccPrompt by remember { mutableStateOf(false) }

    LaunchedEffect(statusSeq) {
        if (statusSeq > 0) {
            delay(1500L)
            statusFlash = null
        }
    }

    LaunchedEffect(accessibilityConnected) {
        if (accessibilityConnected) {
            missingAccPrompt = false
        }
    }

    fun actionLabel(action: Map3DAction, count: Int): String {
        val suffix = if (count > 1) " ×$count" else ""
        return when (action) {
            Map3DAction.TILT_UP_3D -> DxCarI18n.tr("🧊 3D 仰角$suffix", "🧊 3D 仰角$suffix", "🧊 3D Tilt$suffix", "🧊 3D 視点$suffix")
            Map3DAction.TILT_DOWN_2D -> DxCarI18n.tr("🗺 2D 平視$suffix", "🗺 2D 平视$suffix", "🗺 2D Flat$suffix", "🗺 2D 平面$suffix")
            Map3DAction.ZOOM_IN -> DxCarI18n.tr("🔍 放大地圖$suffix", "🔍 放大地图$suffix", "🔍 Zoom In$suffix", "🔍 拡大$suffix")
            Map3DAction.ZOOM_OUT -> DxCarI18n.tr("🔍 縮小地圖$suffix", "🔍 缩小地图$suffix", "🔍 Zoom Out$suffix", "🔍 縮小$suffix")
        }
    }

    fun dispatchExternalSteps(action: Map3DAction, steps: Int) {
        val acc = DxCarAccessibilityService.instance
        if (acc == null) {
            missingAccPrompt = true
            statusFlash = null
            return
        }
        missingAccPrompt = false
        val safeSteps = steps.coerceIn(1, 3)
        statusFlash = actionLabel(action, safeSteps)
        statusSeq++
        scope.launch {
            // Wait briefly after ACTION_UP so Android InputDispatcher finishes the physical touch stream
            // before injecting synthetic 2-finger gestures (prevents onCancelled on One UI / Android 14+ foldables).
            delay(45L)
            repeat(safeSteps) { idx ->
                val anchor = resolveExternalGestureAnchor?.invoke()
                val ax = anchor?.first
                val ay = anchor?.second
                when (action) {
                    Map3DAction.TILT_UP_3D -> acc.dispatchTwoFingerTilt(tiltUpTo3D = true, anchorX = ax, anchorY = ay)
                    Map3DAction.TILT_DOWN_2D -> acc.dispatchTwoFingerTilt(tiltUpTo3D = false, anchorX = ax, anchorY = ay)
                    Map3DAction.ZOOM_IN -> acc.dispatchTwoFingerPinch(zoomIn = true, anchorX = ax, anchorY = ay)
                    Map3DAction.ZOOM_OUT -> acc.dispatchTwoFingerPinch(zoomIn = false, anchorX = ax, anchorY = ay)
                }
                if (idx < safeSteps - 1) {
                    delay(370L)
                }
            }
        }
    }

    fun dispatchBuiltinSingle(action: Map3DAction) {
        missingAccPrompt = false
        Cursor3DStateHolder.emitBuiltinMapAction(action)
        statusFlash = actionLabel(action, 1)
        statusSeq++
    }

    Surface(
        color = Color(0xF2111827),
        shape = RoundedCornerShape(20.dp),
        shadowElevation = 14.dp,
        modifier = modifier
            .widthIn(min = 256.dp, max = 304.dp)
            .border(1.5.dp, colors.accent.copy(alpha = 0.85f), RoundedCornerShape(20.dp))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // ROW 1: Fixed Compact Draggable Header (NEVER overflows or clips [?] / [✕] on any screen width!)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (onDragDelta != null) {
                            Modifier.pointerInput(Unit) {
                                detectDragGestures { change, dragAmount ->
                                    change.consume()
                                    onDragDelta(dragAmount.x, dragAmount.y)
                                }
                            }
                        } else {
                            Modifier
                        }
                    ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.DragIndicator,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.55f),
                    modifier = Modifier.size(18.dp)
                )
                Icon(
                    imageVector = Icons.Rounded.ViewInAr,
                    contentDescription = null,
                    tint = colors.accent,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = statusFlash ?: str("3D 雙指快捷", "3D 双指快捷", "3D Controls", "3D 操作バー"),
                    color = if (statusFlash != null) Color(0xFF4ADE80) else Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                // Fixed-size [?] Help button
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.14f))
                        .bouncyClickable(pressedScale = 0.88f, onClick = onOpenTutorial)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.HelpOutline,
                        contentDescription = str("操作教學", "操作教程", "Guide", "ガイド"),
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
                // Prominent Fixed-size Red [✕] Close button (ALWAYS visible, ALWAYS clickable!)
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFEF4444))
                        .bouncyClickable(pressedScale = 0.88f, onClick = onExit3DMode)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = str("關閉 3D 快捷條", "关闭 3D 快捷条", "Close 3D bar", "3Dバーを閉じる"),
                        tint = Color.White,
                        modifier = Modifier.size(19.dp)
                    )
                }
            }

            // ROW 2: 4 Large Car-Friendly Equal-Width Action Tiles (Tap or Hold-Repeat Fine Tuning)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                HoldRepeat3DButton(
                    icon = Icons.Rounded.KeyboardDoubleArrowUp,
                    label = str("3D 仰角", "3D 仰角", "3D Tilt", "3D 視点"),
                    accentTint = colors.accent,
                    isBuiltinDirect = builtinMapActive,
                    onBuiltinTick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        dispatchBuiltinSingle(Map3DAction.TILT_UP_3D)
                    },
                    onExternalCommit = { steps ->
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        dispatchExternalSteps(Map3DAction.TILT_UP_3D, steps)
                    },
                    modifier = Modifier.weight(1f)
                )
                HoldRepeat3DButton(
                    icon = Icons.Rounded.KeyboardDoubleArrowDown,
                    label = str("2D 平視", "2D 平视", "2D Flat", "2D 平面"),
                    accentTint = Color(0xFF38BDF8),
                    isBuiltinDirect = builtinMapActive,
                    onBuiltinTick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        dispatchBuiltinSingle(Map3DAction.TILT_DOWN_2D)
                    },
                    onExternalCommit = { steps ->
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        dispatchExternalSteps(Map3DAction.TILT_DOWN_2D, steps)
                    },
                    modifier = Modifier.weight(1f)
                )
                HoldRepeat3DButton(
                    icon = Icons.Rounded.Add,
                    label = str("放大", "放大", "Zoom +", "拡大"),
                    accentTint = Color(0xFF4ADE80),
                    isBuiltinDirect = builtinMapActive,
                    onBuiltinTick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        dispatchBuiltinSingle(Map3DAction.ZOOM_IN)
                    },
                    onExternalCommit = { steps ->
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        dispatchExternalSteps(Map3DAction.ZOOM_IN, steps)
                    },
                    modifier = Modifier.weight(1f)
                )
                HoldRepeat3DButton(
                    icon = Icons.Rounded.Remove,
                    label = str("縮小", "缩小", "Zoom −", "縮小"),
                    accentTint = Color(0xFFFBBF24),
                    isBuiltinDirect = builtinMapActive,
                    onBuiltinTick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        dispatchBuiltinSingle(Map3DAction.ZOOM_OUT)
                    },
                    onExternalCommit = { steps ->
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        dispatchExternalSteps(Map3DAction.ZOOM_OUT, steps)
                    },
                    modifier = Modifier.weight(1f)
                )
            }

            // ROW 3: Non-blocking Accessibility Reminder (ONLY shown if user tries external app control without Accessibility)
            AnimatedVisibility(visible = missingAccPrompt && !builtinMapActive) {
                Surface(
                    color = Color(0x33F59E0B),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, Color(0xFFF59E0B), RoundedCornerShape(12.dp))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = str(
                                "⚠️ 外接地圖需開啟無障礙",
                                "⚠️ 外接地图需开启无障碍",
                                "⚠️ Enable Accessibility for maps",
                                "⚠️ 外部地図操作にはユーザー補助が必要"
                            ),
                            color = Color(0xFFFDE68A),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Surface(
                            color = Color(0xFFF59E0B),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.bouncyClickable {
                                PermissionHelper.openAccessibilitySettings(context)
                            }
                        ) {
                            Text(
                                text = str("開啟", "开启", "Open", "開く"),
                                color = Color(0xFF111827),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold,
                                maxLines = 1,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HoldRepeat3DButton(
    icon: ImageVector,
    label: String,
    accentTint: Color,
    isBuiltinDirect: Boolean,
    onBuiltinTick: () -> Unit,
    onExternalCommit: (steps: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    var pressed by remember { mutableStateOf(false) }
    var holdStepBadge by remember { mutableIntStateOf(0) }

    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.91f else 1f,
        animationSpec = tween(90),
        label = "btn3dScale"
    )
    val bgColor by animateColorAsState(
        targetValue = if (pressed) accentTint.copy(alpha = 0.34f) else Color.White.copy(alpha = 0.10f),
        animationSpec = tween(110),
        label = "btn3dBg"
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .height(54.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(14.dp))
            .background(bgColor)
            .border(
                width = if (pressed) 1.5.dp else 1.dp,
                color = if (pressed) accentTint else Color.White.copy(alpha = 0.16f),
                shape = RoundedCornerShape(14.dp)
            )
            .pointerInput(isBuiltinDirect) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    pressed = true
                    holdStepBadge = 0
                    var steppedCount = 1

                    val repeatJob = scope.launch {
                        if (isBuiltinDirect) {
                            // Built-in MapLibre supports instant live repeating while holding!
                            onBuiltinTick()
                            delay(340L)
                            while (true) {
                                onBuiltinTick()
                                delay(220L)
                            }
                        } else {
                            // External apps: accumulate 1..3 steps smoothly while holding, then dispatch
                            // cleanly right after ACTION_UP so Android never cancels the injected gesture.
                            delay(360L)
                            while (steppedCount < 3) {
                                steppedCount++
                                holdStepBadge = steppedCount
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                delay(380L)
                            }
                        }
                    }

                    var cancelled = false
                    while (true) {
                        val ev = awaitPointerEvent(PointerEventPass.Main)
                        val ch = ev.changes.firstOrNull { it.id == down.id } ?: ev.changes.firstOrNull()
                        if (ch == null) {
                            cancelled = true
                            break
                        }
                        if (ch.changedToUpIgnoreConsumed()) {
                            ch.consume()
                            break
                        }
                    }

                    repeatJob.cancel()
                    pressed = false
                    holdStepBadge = 0

                    if (!cancelled && !isBuiltinDirect) {
                        onExternalCommit(steppedCount)
                    }
                }
            }
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 2.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = accentTint,
                modifier = Modifier.size(22.dp)
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = if (holdStepBadge > 1) "×$holdStepBadge" else label,
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Non-touchable Car-Side Remote Mouse Pointer driven by Smartphone Touchpad!
 */
@Composable
fun RemoteCarMousePointerOverlay(cursorState: RemoteCursorState) {
    if (!cursorState.visible) return
    val colors = LocalDxCarColors.current
    var showRipple by remember { mutableStateOf(false) }

    LaunchedEffect(cursorState.clickPulseSeq) {
        if (cursorState.clickPulseSeq > 0L) {
            showRipple = true
            delay(280L)
            showRipple = false
        }
    }

    val rippleScale by animateFloatAsState(
        targetValue = if (showRipple) 1.7f else 0.7f,
        animationSpec = tween(260),
        label = "remoteClickRipple"
    )

    Box(modifier = Modifier.fillMaxSize()) {
        if (showRipple) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .graphicsLayer {
                        translationX = cursorState.xPx - 19.dp.toPx()
                        translationY = cursorState.yPx - 19.dp.toPx()
                        scaleX = rippleScale
                        scaleY = rippleScale
                    }
                    .clip(CircleShape)
                    .border(2.5.dp, colors.accent, CircleShape)
            )
        }

        Surface(
            color = Color(0xEE0F172A),
            shape = CircleShape,
            shadowElevation = 12.dp,
            modifier = Modifier
                .size(34.dp)
                .graphicsLayer {
                    translationX = cursorState.xPx - 6.dp.toPx()
                    translationY = cursorState.yPx - 6.dp.toPx()
                }
                .border(2.dp, Color.White, CircleShape)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Rounded.NearMe,
                    contentDescription = "手機遠端滑鼠",
                    tint = colors.accent,
                    modifier = Modifier
                        .size(20.dp)
                        .graphicsLayer { rotationZ = -90f }
                )
            }
        }
    }
}

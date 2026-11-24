package com.dxcar.launcher.overlay

import android.graphics.Path as AndroidPath
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.HelpOutline
import androidx.compose.material.icons.rounded.NearMe
import androidx.compose.material.icons.rounded.ViewInAr
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dxcar.launcher.accessibility.DxCarAccessibilityService
import com.dxcar.launcher.comms.RemoteCursorState
import com.dxcar.launcher.theme.LocalDxCarColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.hypot

object Cursor3DStateHolder {
    private val _active = MutableStateFlow(false)
    val active: StateFlow<Boolean> = _active.asStateFlow()

    private val _showTutorial = MutableStateFlow(false)
    val showTutorial: StateFlow<Boolean> = _showTutorial.asStateFlow()

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
}

private enum class Cursor3DPhase {
    IDLE,
    HOLDING_COUNTDOWN,
    SINGLE_TOUCH_DRAG,
    LOCKED_DUAL_GESTURE
}

/**
 * Reusable Extra-Large Font Tutorial Modal ("說明字體要特大", Title 26sp / Body 22sp).
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
            shape = RoundedCornerShape(28.dp),
            shadowElevation = 24.dp,
            modifier = Modifier
                .fillMaxWidth(0.86f)
                .padding(vertical = 20.dp)
                .border(2.dp, colors.accent, RoundedCornerShape(28.dp))
                .clickable(enabled = false) {}
        ) {
            Column(
                modifier = Modifier
                    .padding(26.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = title,
                        color = colors.primaryText,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.weight(1f).padding(end = 12.dp)
                    )
                    Button(
                        onClick = onClose,
                        colors = ButtonDefaults.buttonColors(containerColor = colors.accent, contentColor = colors.onAccent),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(Icons.Rounded.Close, contentDescription = null, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("關閉說明", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                }

                steps.forEachIndexed { idx, step ->
                    Surface(
                        color = colors.cardSurfaceElevated,
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, colors.cardBorder, RoundedCornerShape(18.dp))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Surface(
                                color = colors.accent,
                                shape = CircleShape,
                                modifier = Modifier.size(36.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = "${idx + 1}",
                                        color = colors.onAccent,
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                }
                            }
                            Spacer(Modifier.width(14.dp))
                            Text(
                                text = step,
                                color = colors.primaryText,
                                fontSize = 22.sp,
                                lineHeight = 32.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                if (highlightNote.isNotBlank()) {
                    Surface(
                        color = colors.accent.copy(alpha = 0.16f),
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.5.dp, colors.accent, RoundedCornerShape(18.dp))
                    ) {
                        Text(
                            text = highlightNote,
                            color = colors.accent,
                            fontSize = 21.sp,
                            lineHeight = 30.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(18.dp)
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
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Text(actionButtonLabel, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

val TUTORIAL_3D_CURSOR_STEPS = listOf(
    "點擊 Dock 上的【3D】圖示後，畫面會出現一個「3D 立體類滑鼠指標」跟著您的手指移動。",
    "一般點擊或直接滑動手指時：維持「單指觸控」（附藍色光軌特效提示），可正常平移地圖或點擊按鈕。",
    "長壓原處滿 2 秒（手指不要離開螢幕！）：游標外圈會亮起綠色十字光環，代表已進入「雙指模擬模式」。",
    "長壓滿 2 秒且手指不放開繼續移動：\n• 往左滑 ＝ 地圖縮小\n• 往右滑 ＝ 地圖放大\n• 往上／往下滑 ＝ 模擬雙指進入或退出 3D 建築俯瞰視角！",
    "手指一放開螢幕，就會自動變回一般「類滑鼠指標」；下次要縮放或切 3D 時，重新長壓 2 秒即可再次啟用。"
)

/**
 * Interactive 3D Mouse-Like Pointer Overlay:
 * - Renders a 3D pointer icon following the user's finger.
 * - Normal drag (< 2s before moving): acts as single-finger touch with luminous cyan trail effect,
 *   forwarding the exact single-finger tap/drag path to the underlying map via Accessibility pass-through.
 * - Long-press 2.0s without lifting finger: locks into Dual-Finger Gesture Mode (green halo):
 *     * Move Left  -> Zoom Out (2-finger pinch)
 *     * Move Right -> Zoom In  (2-finger spread)
 *     * Move Up/Down -> 3D Tilt Up / 2D Tilt Down (2-finger parallel swipe)
 * - Lifting finger resets back to normal 3D mouse pointer until next 2-second hold!
 */
@Composable
fun Interactive3DCursorOverlay(
    showTutorial: Boolean,
    onOpenTutorial: () -> Unit,
    onCloseTutorial: () -> Unit,
    onExit3DMode: () -> Unit,
    onSetWindowPassThrough: (Boolean) -> Unit
) {
    val colors = LocalDxCarColors.current
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    var cursorPos by remember { mutableStateOf(Offset(560f, 340f)) }
    var phase by remember { mutableStateOf(Cursor3DPhase.IDLE) }
    var holdProgress by remember { mutableFloatStateOf(0f) }
    var effectBanner by remember { mutableStateOf("🧊 3D 類滑鼠指標：單指滑動＝平移 · 原地長壓 2 秒不放＝左右縮放／上下 3D 俯瞰") }
    val dragTrail = remember { mutableStateListOf<Offset>() }

    val moveSlopPx = with(density) { 22.dp.toPx() }
    val gestureStepPx = with(density) { 54.dp.toPx() }

    fun injectGestureThroughOverlay(block: (DxCarAccessibilityService, (Boolean) -> Unit) -> Boolean) {
        val acc = DxCarAccessibilityService.instance ?: run {
            effectBanner = "⚠️ 請先在設定最上方「權限設定」開啟【無障礙服務】！"
            return
        }
        onSetWindowPassThrough(true)
        val started = block(acc) {
            scope.launch {
                delay(45L)
                onSetWindowPassThrough(false)
            }
        }
        if (!started) {
            onSetWindowPassThrough(false)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Fullscreen touch-tracking surface (disabled while tutorial dialog is open)
        if (!showTutorial) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val downPos = down.position
                            val downTimeMs = System.currentTimeMillis()
                            cursorPos = downPos
                            dragTrail.clear()
                            dragTrail.add(downPos)
                            phase = Cursor3DPhase.HOLDING_COUNTDOWN
                            holdProgress = 0f

                            var lockedDual = false
                            var singleDragMode = false
                            var stepOrigin = downPos
                            var gestureBusy = false

                            val holdTimer = scope.launch {
                                val targetMs = 2_000L
                                while (true) {
                                    val elapsed = System.currentTimeMillis() - downTimeMs
                                    holdProgress = (elapsed.toFloat() / targetMs.toFloat()).coerceIn(0f, 1f)
                                    if (elapsed >= targetMs) {
                                        if (!singleDragMode) {
                                            lockedDual = true
                                            phase = Cursor3DPhase.LOCKED_DUAL_GESTURE
                                            holdProgress = 1f
                                            stepOrigin = cursorPos
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            effectBanner = "🟢 已啟動雙指模擬（手指勿放開）：⬅縮小 · ➡放大 · ⬆⬇ 3D 俯瞰"
                                        }
                                        break
                                    }
                                    delay(25L)
                                }
                            }

                            while (true) {
                                val ev = awaitPointerEvent(PointerEventPass.Main)
                                val ch = ev.changes.firstOrNull { it.id == down.id } ?: ev.changes.firstOrNull()
                                if (ch == null) break

                                val pos = ch.position
                                cursorPos = pos

                                if (!lockedDual && !singleDragMode) {
                                    val dist = hypot(pos.x - downPos.x, pos.y - downPos.y)
                                    if (dist > moveSlopPx) {
                                        singleDragMode = true
                                        holdTimer.cancel()
                                        holdProgress = 0f
                                        phase = Cursor3DPhase.SINGLE_TOUCH_DRAG
                                        effectBanner = "👆 單指觸控拖曳中（放開後轉交地圖移動）"
                                    }
                                }

                                if (singleDragMode) {
                                    dragTrail.add(pos)
                                    if (dragTrail.size > 28) dragTrail.removeAt(0)
                                } else if (lockedDual && !gestureBusy) {
                                    val dx = pos.x - stepOrigin.x
                                    val dy = pos.y - stepOrigin.y
                                    if (abs(dx) >= gestureStepPx && abs(dx) >= abs(dy)) {
                                        val zoomIn = dx > 0f
                                        stepOrigin = pos
                                        gestureBusy = true
                                        effectBanner = if (zoomIn) "🔍 向右滑：雙指放大地圖" else "🔍 向左滑：雙指縮小地圖"
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        injectGestureThroughOverlay { acc, done ->
                                            acc.dispatchTwoFingerPinch(
                                                zoomIn = zoomIn,
                                                anchorX = downPos.x,
                                                anchorY = downPos.y
                                            ) { ok ->
                                                gestureBusy = false
                                                done(ok)
                                            }
                                        }
                                    } else if (abs(dy) >= gestureStepPx && abs(dy) > abs(dx)) {
                                        val tiltTo3D = dy < 0f
                                        stepOrigin = pos
                                        gestureBusy = true
                                        effectBanner = if (tiltTo3D) "🧊 向上滑：進入 3D 建築俯瞰視角" else "🗺 向下滑：恢復 2D 平面視角"
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        injectGestureThroughOverlay { acc, done ->
                                            acc.dispatchTwoFingerTilt(
                                                tiltUpTo3D = tiltTo3D,
                                                anchorX = downPos.x,
                                                anchorY = downPos.y
                                            ) { ok ->
                                                gestureBusy = false
                                                done(ok)
                                            }
                                        }
                                    }
                                }

                                if (ch.changedToUpIgnoreConsumed()) {
                                    ch.consume()
                                    break
                                }
                                ch.consume()
                            }

                            holdTimer.cancel()
                            val upTimeMs = System.currentTimeMillis()
                            val finalPoints = dragTrail.toList()
                            dragTrail.clear()

                            // Finger lifted: if it was a single tap or single-finger drag, replay onto underlying map!
                            if (!lockedDual) {
                                if (singleDragMode && finalPoints.size >= 2) {
                                    val androidPath = AndroidPath().apply {
                                        moveTo(finalPoints.first().x, finalPoints.first().y)
                                        for (i in 1 until finalPoints.size) {
                                            lineTo(finalPoints[i].x, finalPoints[i].y)
                                        }
                                    }
                                    val dur = (upTimeMs - downTimeMs).coerceIn(120L, 650L)
                                    injectGestureThroughOverlay { acc, done ->
                                        acc.dispatchSinglePathGesture(androidPath, dur, done)
                                    }
                                } else {
                                    injectGestureThroughOverlay { acc, done ->
                                        acc.dispatchSingleTap(downPos.x, downPos.y, done)
                                    }
                                }
                            }

                            // Always return to normal 3D Mouse Pointer state when finger releases!
                            phase = Cursor3DPhase.IDLE
                            holdProgress = 0f
                            effectBanner = "🧊 已回到 3D 類滑鼠指標（需重新長壓 2 秒再次啟用縮放／3D 俯瞰）"
                        }
                    }
            )

            // VISUAL FX CANVAS: Single-touch luminous trail + 2s countdown arc + Locked 3D crosshair
            Canvas(modifier = Modifier.fillMaxSize()) {
                if (dragTrail.size >= 2) {
                    val trailPath = Path().apply {
                        moveTo(dragTrail.first().x, dragTrail.first().y)
                        for (i in 1 until dragTrail.size) {
                            lineTo(dragTrail[i].x, dragTrail[i].y)
                        }
                    }
                    drawPath(
                        path = trailPath,
                        color = Color(0xFF38BDF8).copy(alpha = 0.65f),
                        style = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round)
                    )
                    drawCircle(
                        color = Color(0xFF38BDF8).copy(alpha = 0.28f),
                        radius = 34.dp.toPx(),
                        center = cursorPos
                    )
                }

                when (phase) {
                    Cursor3DPhase.HOLDING_COUNTDOWN -> {
                        if (holdProgress > 0.04f) {
                            val r = 42.dp.toPx()
                            drawCircle(
                                color = Color.Black.copy(alpha = 0.35f),
                                radius = r,
                                center = cursorPos
                            )
                            drawArc(
                                color = Color(0xFF22C55E),
                                startAngle = -90f,
                                sweepAngle = 360f * holdProgress,
                                useCenter = false,
                                topLeft = Offset(cursorPos.x - r, cursorPos.y - r),
                                size = androidx.compose.ui.geometry.Size(r * 2f, r * 2f),
                                style = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round)
                            )
                        }
                    }

                    Cursor3DPhase.LOCKED_DUAL_GESTURE -> {
                        val r = 56.dp.toPx()
                        drawCircle(
                            color = Color(0xFF22C55E).copy(alpha = 0.24f),
                            radius = r,
                            center = cursorPos
                        )
                        drawCircle(
                            color = Color(0xFF22C55E),
                            radius = r,
                            center = cursorPos,
                            style = Stroke(width = 3.5.dp.toPx())
                        )
                        // Crosshair directional ticks
                        val arm = r + 16.dp.toPx()
                        drawLine(Color(0xFF22C55E), Offset(cursorPos.x - arm, cursorPos.y), Offset(cursorPos.x - r * 0.6f, cursorPos.y), strokeWidth = 4.dp.toPx(), cap = StrokeCap.Round)
                        drawLine(Color(0xFF22C55E), Offset(cursorPos.x + r * 0.6f, cursorPos.y), Offset(cursorPos.x + arm, cursorPos.y), strokeWidth = 4.dp.toPx(), cap = StrokeCap.Round)
                        drawLine(Color(0xFF22C55E), Offset(cursorPos.x, cursorPos.y - arm), Offset(cursorPos.x, cursorPos.y - r * 0.6f), strokeWidth = 4.dp.toPx(), cap = StrokeCap.Round)
                        drawLine(Color(0xFF22C55E), Offset(cursorPos.x, cursorPos.y + r * 0.6f), Offset(cursorPos.x, cursorPos.y + arm), strokeWidth = 4.dp.toPx(), cap = StrokeCap.Round)
                    }

                    else -> Unit
                }
            }

            // 3D MOUSE-LIKE POINTER ICON FOLLOWING FINGER
            val badgeOffsetPx = with(density) { 26.dp.toPx() }
            val cursorTint = when (phase) {
                Cursor3DPhase.LOCKED_DUAL_GESTURE -> Color(0xFF22C55E)
                Cursor3DPhase.SINGLE_TOUCH_DRAG -> Color(0xFF38BDF8)
                else -> colors.accent
            }
            Surface(
                color = Color(0xE6111827),
                shape = CircleShape,
                shadowElevation = 12.dp,
                modifier = Modifier
                    .size(52.dp)
                    .graphicsLayer {
                        translationX = cursorPos.x - badgeOffsetPx
                        translationY = cursorPos.y - badgeOffsetPx
                    }
                    .border(2.2.dp, cursorTint, CircleShape)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.ViewInAr,
                        contentDescription = "3D 類滑鼠指標",
                        tint = cursorTint,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }

        // TOP CONTROL PILL WITH PROMINENT [教學] BUTTON & [關閉] BUTTON
        Surface(
            color = Color(0xEB111827),
            shape = RoundedCornerShape(24.dp),
            shadowElevation = 16.dp,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 12.dp, start = 16.dp, end = 16.dp)
                .border(1.5.dp, colors.accent, RoundedCornerShape(24.dp))
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(Icons.Rounded.ViewInAr, contentDescription = null, tint = colors.accent, modifier = Modifier.size(22.dp))
                Text(
                    text = effectBanner,
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
                Button(
                    onClick = onOpenTutorial,
                    colors = ButtonDefaults.buttonColors(containerColor = colors.accent, contentColor = colors.onAccent),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Rounded.HelpOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("教學", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
                }
                OutlinedButton(
                    onClick = onExit3DMode,
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Rounded.Close, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("結束 3D 指標", color = Color.White, fontSize = 13.sp)
                }
            }
        }

        // EXTRA-LARGE FONT TUTORIAL MODAL
        AnimatedVisibility(
            visible = showTutorial,
            enter = fadeIn(tween(160)),
            exit = fadeOut(tween(140))
        ) {
            ExtraLargeTutorialModal(
                title = "🧊 3D 俯瞰類滑鼠指標 · 操作教學",
                steps = TUTORIAL_3D_CURSOR_STEPS,
                highlightNote = "💡 小秘訣：確認已在設定最上方「權限設定」開啟【無障礙輔助服務】，即可在原版 Google 地圖與任何導航軟體上單指操控 3D 視角與縮放！",
                onClose = onCloseTutorial
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

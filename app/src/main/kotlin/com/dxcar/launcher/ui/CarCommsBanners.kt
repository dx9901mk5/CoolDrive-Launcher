package com.dxcar.launcher.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.automirrored.rounded.Reply
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.BluetoothAudio
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.CallEnd
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Done
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dxcar.launcher.comms.CallStatus
import com.dxcar.launcher.comms.CarCallState
import com.dxcar.launcher.comms.CarMessageItem
import com.dxcar.launcher.data.NotificationBannerScale
import com.dxcar.launcher.i18n.DxCarI18n.str
import com.dxcar.launcher.theme.LocalDxCarColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Horizontal touch swipe-to-dismiss wrapper (allows swiping notification/call banners Left or Right to dismiss!).
 */
@Composable
fun SwipeHorizontalToDismissBox(
    resetKey: Any?,
    thresholdDp: Float = 80f,
    onDismissed: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val scope = rememberCoroutineScope()
    val offsetX = remember(resetKey) { Animatable(0f) }

    Box(
        modifier = modifier
            .offset { IntOffset(offsetX.value.roundToInt(), 0) }
            .graphicsLayer {
                alpha = (1f - (abs(offsetX.value) / 460f)).coerceIn(0.15f, 1f)
            }
            .pointerInput(resetKey, thresholdDp) {
                val thresholdPx = thresholdDp.dp.toPx()
                detectHorizontalDragGestures(
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        scope.launch {
                            offsetX.snapTo(offsetX.value + dragAmount)
                        }
                    },
                    onDragEnd = {
                        val cur = offsetX.value
                        if (abs(cur) >= thresholdPx) {
                            val target = if (cur > 0f) 1100f else -1100f
                            scope.launch {
                                offsetX.animateTo(target, tween(165))
                                onDismissed()
                                offsetX.snapTo(0f)
                            }
                        } else {
                            scope.launch {
                                offsetX.animateTo(0f, spring(stiffness = Spring.StiffnessMedium))
                            }
                        }
                    },
                    onDragCancel = {
                        scope.launch { offsetX.animateTo(0f, spring()) }
                    }
                )
            }
    ) {
        content()
    }
}

/**
 * Proportional Incoming Call Banner & Active Call Capsule:
 * - Scales smoothly with NotificationBannerScale (0.5X / 0.75X / 1X / 1.5X / 2X).
 * - Slightly wider than message banner and keeps generous Answer/Decline buttons so driving taps are effortless.
 * - Displays Contact Name prominently with caller phone number underneath.
 */
@Composable
fun IncomingCallBanner(
    callState: CarCallState,
    scale: NotificationBannerScale = NotificationBannerScale.SCALE_1_0X,
    scaleFactorOverride: Float? = null,
    useCarBoxAudio: Boolean = true,
    onToggleAudioRoute: (() -> Unit)? = null,
    onAnswer: () -> Unit,
    onDecline: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalDxCarColors.current
    val s = (scaleFactorOverride ?: scale.factor).coerceIn(0.5f, 2.2f)
    var elapsedSec by remember(callState.startedAtMs, callState.status) { mutableLongStateOf(0L) }

    LaunchedEffect(callState.status, callState.startedAtMs) {
        while (callState.status == CallStatus.ACTIVE && callState.startedAtMs > 0L) {
            elapsedSec = ((System.currentTimeMillis() - callState.startedAtMs) / 1000L).coerceAtLeast(0L)
            delay(1_000L)
        }
    }

    AnimatedVisibility(
        visible = callState.status != CallStatus.IDLE,
        enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
        modifier = modifier
    ) {
        val maxCardW = (450f * s).coerceIn(280f, 780f).dp
        val cornerDp = (20f * s).coerceIn(14f, 28f).dp
        val padH = (14f * s).coerceIn(10f, 22f).dp
        val padV = (11f * s).coerceIn(8f, 18f).dp
        val leadAvatarDp = (40f * s).coerceIn(28f, 60f).dp
        val leadIconDp = (22f * s).coerceIn(16f, 34f).dp
        val callerSp = (19.5f * s).coerceIn(13.5f, 34f).sp
        val subSp = (13f * s).coerceIn(10f, 23f).sp
        // Answer / Decline buttons kept slightly larger with a safe 44.dp minimum floor
        val callBtnDp = (52f * s).coerceIn(44f, 68f).dp
        val callBtnIconDp = (26f * s).coerceIn(22f, 34f).dp

        SwipeHorizontalToDismissBox(
            resetKey = "${callState.status}_${callState.startedAtMs}",
            thresholdDp = (80f * s).coerceIn(52f, 115f),
            onDismissed = onDecline
        ) {
            Surface(
                color = colors.glassOverlay,
                shape = RoundedCornerShape(cornerDp),
                shadowElevation = 16.dp,
                modifier = Modifier
                    .widthIn(max = maxCardW)
                    .fillMaxWidth()
                    .border(
                        width = 1.8.dp,
                        color = if (callState.status == CallStatus.RINGING) colors.callAcceptGreen else colors.accent,
                        shape = RoundedCornerShape(cornerDp)
                    )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = padH, vertical = padV),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(leadAvatarDp)
                            .clip(CircleShape)
                            .background(colors.callAcceptGreen.copy(alpha = 0.22f))
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Call,
                            contentDescription = null,
                            tint = colors.callAcceptGreen,
                            modifier = Modifier.size(leadIconDp)
                        )
                    }

                    Spacer(Modifier.width((10f * s).coerceIn(6f, 16f).dp))

                    val displayTitle = callState.callerName.ifBlank {
                        callState.callerNumber.ifBlank { str("藍牙來電", "蓝牙来电", "Incoming Call", "Bluetooth着信") }
                    }
                    val showDistinctNumber = callState.callerNumber.isNotBlank() &&
                        !callState.callerNumber.equals(displayTitle, ignoreCase = true)

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = displayTitle,
                            color = colors.primaryText,
                            fontSize = callerSp,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        val subtitle = when (callState.status) {
                            CallStatus.RINGING -> {
                                val numPart = if (showDistinctNumber) " · ${callState.callerNumber}" else ""
                                "${callState.sourceApp} · ${str("來電中", "来电中", "Incoming", "着信中")}$numPart"
                            }
                            CallStatus.ACTIVE -> {
                                val timer = "%02d:%02d".format(elapsedSec / 60, elapsedSec % 60)
                                "${callState.sourceApp} · ${str("通話中", "通话中", "In Call", "通話中")} $timer"
                            }
                            CallStatus.IDLE -> ""
                        }
                        Text(
                            text = subtitle,
                            color = colors.secondaryText,
                            fontSize = subSp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(Modifier.width((10f * s).coerceIn(6f, 14f).dp))

                    Row(horizontalArrangement = Arrangement.spacedBy((10f * s).coerceIn(7f, 14f).dp)) {
                        if (onToggleAudioRoute != null) {
                            Surface(
                                color = if (useCarBoxAudio) colors.accent else colors.cardSurfaceElevated,
                                shape = CircleShape,
                                modifier = Modifier
                                    .size(callBtnDp)
                                    .border(1.2.dp, colors.accent, CircleShape)
                                    .bouncyClickable(pressedScale = 0.86f, onClick = onToggleAudioRoute)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = if (useCarBoxAudio) Icons.Rounded.BluetoothAudio else Icons.Rounded.PhoneAndroid,
                                        contentDescription = str(
                                            if (useCarBoxAudio) "音源：車機盒（點此切換）" else "音源：手機（點此切換）",
                                            if (useCarBoxAudio) "音源：车机盒（点此切换）" else "音源：手机（点此切换）",
                                            if (useCarBoxAudio) "Audio: Car Box (Tap to switch)" else "Audio: Phone (Tap to switch)",
                                            if (useCarBoxAudio) "音声：車載機（タップで切替）" else "音声：スマホ（タップで切替）"
                                        ),
                                        tint = if (useCarBoxAudio) colors.onAccent else colors.primaryText,
                                        modifier = Modifier.size(callBtnIconDp)
                                    )
                                }
                            }
                        }
                        if (callState.status == CallStatus.RINGING) {
                            Surface(
                                color = colors.callAcceptGreen,
                                shape = CircleShape,
                                modifier = Modifier
                                    .size(callBtnDp)
                                    .bouncyClickable(pressedScale = 0.86f, onClick = onAnswer)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Rounded.Call,
                                        contentDescription = str("接聽", "接听", "Answer", "応答"),
                                        tint = Color.White,
                                        modifier = Modifier.size(callBtnIconDp)
                                    )
                                }
                            }
                        }

                        Surface(
                            color = colors.callDeclineRed,
                            shape = CircleShape,
                            modifier = Modifier
                                .size(callBtnDp)
                                .bouncyClickable(pressedScale = 0.86f, onClick = onDecline)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Rounded.CallEnd,
                                    contentDescription = str("掛斷", "挂断", "Hang up", "終了"),
                                    tint = Color.White,
                                    modifier = Modifier.size(callBtnIconDp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Compact 1X Default (0.5X / 0.75X / 1X / 1.5X / 2X user-selectable) Incoming Message Banner:
 * - Calibrated so 1X matches the compact top-left red-box footprint (~380.dp max width, crisp scaled font).
 * - Intuitive Left/Right swipe-to-dismiss with NO cluttering hint text.
 * - Single compact "快速回覆" pill button; tapping expands a smooth spring-animated horizontal carousel
 *   whose horizontal drag is isolated so sliding quick replies never dismisses the banner.
 */
@Composable
fun IncomingMessageBanner(
    message: CarMessageItem?,
    quickReplies: List<String>,
    scale: NotificationBannerScale = NotificationBannerScale.SCALE_1_0X,
    scaleFactorOverride: Float? = null,
    onExpandQuickReplies: () -> Unit = {},
    onReadAloud: (CarMessageItem) -> Unit,
    onQuickReply: (CarMessageItem, String) -> Unit,
    onVoiceReply: ((CarMessageItem) -> Unit)? = null,
    onMarkRead: (CarMessageItem) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalDxCarColors.current
    val s = (scaleFactorOverride ?: scale.factor).coerceIn(0.5f, 2.2f)

    AnimatedVisibility(
        visible = message != null,
        enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
        modifier = modifier
    ) {
        val item = message ?: return@AnimatedVisibility
        val appTint = messageBrandColor(item.appPackage, colors.accent)
        var repliesExpanded by remember(item.id) { mutableStateOf(false) }

        val maxCardW = (470f * s).coerceIn(260f, 880f).dp
        val cornerDp = (18f * s).coerceIn(12f, 28f).dp
        val padH = (13f * s).coerceIn(9f, 22f).dp
        val padV = (11f * s).coerceIn(7f, 18f).dp
        val avatarDp = (38f * s).coerceIn(24f, 60f).dp
        val avatarIconDp = (20f * s).coerceIn(13f, 32f).dp
        // Message notification fonts boosted 1.5X per user feedback
        val senderSp = (25.5f * s).coerceIn(15f, 46f).sp
        val badgeSp = (15f * s).coerceIn(11f, 26f).sp
        val bodySp = (22f * s).coerceIn(14f, 40f).sp
        val bodyLineH = (29.5f * s).coerceIn(18.5f, 52f).sp
        val actionBtnDp = (32f * s).coerceIn(24f, 50f).dp
        val actionIconDp = (18f * s).coerceIn(13f, 28f).dp
        val replyBtnSp = (16f * s).coerceIn(11.5f, 26f).sp
        val replyChipSp = (16.5f * s).coerceIn(12f, 27f).sp

        val chevronRotation by animateFloatAsState(
            targetValue = if (repliesExpanded) 180f else 0f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMedium
            ),
            label = "quickReplyChevron"
        )

        SwipeHorizontalToDismissBox(
            resetKey = item.id,
            thresholdDp = (75f * s).coerceIn(48f, 110f),
            onDismissed = onDismiss
        ) {
            Surface(
                color = colors.glassOverlay,
                shape = RoundedCornerShape(cornerDp),
                shadowElevation = 14.dp,
                modifier = Modifier
                    .widthIn(max = maxCardW)
                    .fillMaxWidth()
                    .border(1.5.dp, appTint.copy(alpha = 0.8f), RoundedCornerShape(cornerDp))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = padH, vertical = padV),
                    verticalArrangement = Arrangement.spacedBy((6f * s).coerceIn(4f, 11f).dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Top
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .padding(top = 2.dp)
                                .size(avatarDp)
                                .clip(CircleShape)
                                .background(appTint.copy(alpha = 0.22f))
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.Chat,
                                contentDescription = null,
                                tint = appTint,
                                modifier = Modifier.size(avatarIconDp)
                            )
                        }
                        Spacer(Modifier.width((9f * s).coerceIn(6f, 14f).dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy((6f * s).coerceIn(4f, 10f).dp)
                            ) {
                                Text(
                                    text = item.sender,
                                    color = colors.primaryText,
                                    fontSize = senderSp,
                                    fontWeight = FontWeight.ExtraBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                Surface(
                                    color = appTint.copy(alpha = 0.24f),
                                    shape = RoundedCornerShape((7f * s).coerceIn(5f, 10f).dp)
                                ) {
                                    Text(
                                        text = item.appName,
                                        color = appTint,
                                        fontSize = badgeSp,
                                        fontWeight = FontWeight.ExtraBold,
                                        maxLines = 1,
                                        modifier = Modifier.padding(
                                            horizontal = (6f * s).coerceIn(4f, 10f).dp,
                                            vertical = (1.5f * s).coerceIn(1f, 3f).dp
                                        )
                                    )
                                }
                            }
                            Spacer(Modifier.height((3f * s).coerceIn(2f, 6f).dp))
                            Text(
                                text = item.text,
                                color = colors.primaryText.copy(alpha = 0.94f),
                                fontSize = bodySp,
                                lineHeight = bodyLineH,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy((2f * s).coerceIn(1f, 4f).dp)) {
                            IconButton(
                                onClick = { onReadAloud(item) },
                                modifier = Modifier.size(actionBtnDp)
                            ) {
                                Icon(Icons.AutoMirrored.Rounded.VolumeUp, contentDescription = str("語音朗讀", "语音朗读", "Read aloud", "音声読み上げ"), tint = colors.accent, modifier = Modifier.size(actionIconDp))
                            }
                            if (onVoiceReply != null && item.canReply) {
                                IconButton(
                                    onClick = { onVoiceReply(item) },
                                    modifier = Modifier.size(actionBtnDp)
                                ) {
                                    Icon(Icons.Rounded.Mic, contentDescription = str("語音回覆", "语音回复", "Voice reply", "音声返信"), tint = colors.accent, modifier = Modifier.size(actionIconDp))
                                }
                            }
                            IconButton(
                                onClick = { onMarkRead(item) },
                                modifier = Modifier.size(actionBtnDp)
                            ) {
                                Icon(Icons.Rounded.Done, contentDescription = str("已讀", "已读", "Mark as read", "既読にする"), tint = colors.statusConnected, modifier = Modifier.size(actionIconDp))
                            }
                            IconButton(
                                onClick = onDismiss,
                                modifier = Modifier.size(actionBtnDp)
                            ) {
                                Icon(Icons.Rounded.Close, contentDescription = str("關閉", "关闭", "Dismiss", "閉じる"), tint = colors.secondaryText, modifier = Modifier.size(actionIconDp))
                            }
                        }
                    }

                    // Single compact Quick Reply trigger button -> expands animated horizontal sliding list
                    if (item.canReply && quickReplies.isNotEmpty()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Start
                        ) {
                            Surface(
                                color = if (repliesExpanded) appTint.copy(alpha = 0.24f) else colors.cardSurfaceElevated,
                                shape = RoundedCornerShape((12f * s).coerceIn(8f, 16f).dp),
                                modifier = Modifier
                                    .border(
                                        width = 1.dp,
                                        color = if (repliesExpanded) appTint else colors.cardBorder,
                                        shape = RoundedCornerShape((12f * s).coerceIn(8f, 16f).dp)
                                    )
                                    .bouncyClickable(pressedScale = 0.90f) {
                                        val next = !repliesExpanded
                                        repliesExpanded = next
                                        if (next) onExpandQuickReplies()
                                    }
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy((4f * s).coerceIn(3f, 6f).dp),
                                    modifier = Modifier.padding(
                                        horizontal = (10f * s).coerceIn(7f, 15f).dp,
                                        vertical = (4.5f * s).coerceIn(3f, 8f).dp
                                    )
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Rounded.Reply,
                                        contentDescription = null,
                                        tint = if (repliesExpanded) appTint else colors.accent,
                                        modifier = Modifier.size((14f * s).coerceIn(10f, 20f).dp)
                                    )
                                    Text(
                                        text = str("💬 快速回覆", "💬 快速回复", "💬 Quick Reply", "💬 クイック返信"),
                                        color = colors.primaryText,
                                        fontSize = replyBtnSp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Icon(
                                        imageVector = Icons.Rounded.ExpandMore,
                                        contentDescription = null,
                                        tint = colors.secondaryText,
                                        modifier = Modifier
                                            .size((15f * s).coerceIn(11f, 22f).dp)
                                            .graphicsLayer { rotationZ = chevronRotation }
                                    )
                                }
                            }
                        }

                        AnimatedVisibility(
                            visible = repliesExpanded,
                            enter = expandVertically(
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioLowBouncy,
                                    stiffness = Spring.StiffnessMediumLow
                                )
                            ) + fadeIn(tween(180)),
                            exit = shrinkVertically(tween(150)) + fadeOut(tween(120))
                        ) {
                            val scrollState = rememberScrollState()
                            val chipAppear = remember { Animatable(0f) }
                            LaunchedEffect(Unit) {
                                chipAppear.animateTo(
                                    targetValue = 1f,
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                        stiffness = Spring.StiffnessMediumLow
                                    )
                                )
                            }
                            // Absorb overscroll horizontal drag so sliding quick-reply options NEVER dismisses the banner
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .pointerInput(item.id) {
                                        detectHorizontalDragGestures { change, _ ->
                                            change.consume()
                                        }
                                    }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(scrollState)
                                        .padding(top = 2.dp, bottom = 2.dp),
                                    horizontalArrangement = Arrangement.spacedBy((7f * s).coerceIn(5f, 11f).dp)
                                ) {
                                    quickReplies.forEachIndexed { idx, reply ->
                                        val itemProgress = (chipAppear.value - idx * 0.05f).coerceIn(0f, 1f)
                                        Surface(
                                            color = colors.cardSurfaceElevated,
                                            shape = RoundedCornerShape((13f * s).coerceIn(9f, 18f).dp),
                                            modifier = Modifier
                                                .graphicsLayer {
                                                    val sc = 0.86f + 0.14f * itemProgress
                                                    scaleX = sc
                                                    scaleY = sc
                                                    alpha = itemProgress
                                                    translationX = (1f - itemProgress) * 24f
                                                }
                                                .border(
                                                    1.dp,
                                                    appTint.copy(alpha = 0.55f),
                                                    RoundedCornerShape((13f * s).coerceIn(9f, 18f).dp)
                                                )
                                                .bouncyClickable(pressedScale = 0.90f) {
                                                    repliesExpanded = false
                                                    onQuickReply(item, reply)
                                                }
                                        ) {
                                            Text(
                                                text = reply,
                                                color = colors.primaryText,
                                                fontSize = replyChipSp,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                modifier = Modifier.padding(
                                                    horizontal = (11f * s).coerceIn(8f, 16f).dp,
                                                    vertical = (6f * s).coerceIn(4f, 10f).dp
                                                )
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NotificationCenterSheet(
    messages: List<CarMessageItem>,
    quickReplies: List<String>,
    onReadAloud: (CarMessageItem) -> Unit,
    onQuickReply: (CarMessageItem, String) -> Unit,
    onMarkRead: (CarMessageItem) -> Unit,
    onRemoveMessage: (CarMessageItem) -> Unit,
    onClearAll: () -> Unit,
    onClose: () -> Unit
) {
    val colors = LocalDxCarColors.current
    var expandedReplyMsgId by remember { mutableStateOf<String?>(null) }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f))
            .clickable { onClose() },
        contentAlignment = Alignment.Center
    ) {
        Surface(
            color = colors.cardSurface,
            shape = RoundedCornerShape(28.dp),
            shadowElevation = 18.dp,
            modifier = Modifier
                .fillMaxWidth(0.76f)
                .height(440.dp)
                .border(1.dp, colors.cardBorder, RoundedCornerShape(28.dp))
                .clickable(enabled = false) {}
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = str("訊息與通知中心", "消息与通知中心", "Messages & Notifications", "メッセージ・通知センター"),
                            color = colors.primaryText,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                        if (messages.isNotEmpty()) {
                            Text(
                                text = str(
                                    "←→ 左右滑動移除訊息 · 長壓訊息彈出快速回覆",
                                    "←→ 左右滑动移除消息 · 长按消息弹出快速回复",
                                    "←→ Swipe left/right to remove · Long-press for quick replies",
                                    "←→ 左右スワイプで削除 · 長押しでクイック返信"
                                ),
                                color = colors.secondaryText,
                                fontSize = 11.sp
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (messages.isNotEmpty()) {
                            IconButton(onClick = onClearAll) {
                                Icon(Icons.Rounded.DeleteSweep, contentDescription = str("全部清除", "全部清除", "Clear all", "すべて消去"), tint = colors.secondaryText)
                            }
                        }
                        IconButton(onClick = onClose) {
                            Icon(Icons.Rounded.Close, contentDescription = str("關閉", "关闭", "Close", "閉じる"), tint = colors.primaryText)
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))

                if (messages.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = str(
                                "目前沒有未讀訊息\n手機配對並開啟同步端後，LINE／Messenger／簡訊會即時顯示於此",
                                "当前没有未读消息\n手机配对并开启同步端后，LINE／Messenger／短信会实时显示于此",
                                "No unread messages\nOnce paired and synced, LINE, Messenger & SMS appear here live",
                                "未読メッセージはありません\nスマホをペアリング・同期すると、LINE／Messenger／SMSがここに表示されます"
                            ),
                            color = colors.secondaryText,
                            fontSize = 14.sp
                        )
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(messages, key = { it.id }) { msg ->
                            val tint = messageBrandColor(msg.appPackage, colors.accent)
                            val showReplies = expandedReplyMsgId == msg.id
                            SwipeHorizontalToDismissBox(
                                resetKey = msg.id,
                                thresholdDp = 80f,
                                onDismissed = { onRemoveMessage(msg) }
                            ) {
                                Surface(
                                    color = colors.cardSurfaceElevated,
                                    shape = RoundedCornerShape(18.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .border(
                                            width = if (showReplies) 1.5.dp else 1.dp,
                                            color = if (showReplies) tint else colors.cardBorder,
                                            shape = RoundedCornerShape(18.dp)
                                        )
                                        .combinedClickable(
                                            onClick = {
                                                if (showReplies) expandedReplyMsgId = null
                                            },
                                            onLongClick = {
                                                if (msg.canReply) {
                                                    expandedReplyMsgId = if (showReplies) null else msg.id
                                                }
                                            }
                                        )
                                ) {
                                    Column(
                                        modifier = Modifier.padding(14.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Text(
                                                        text = msg.sender,
                                                        color = colors.primaryText,
                                                        fontSize = 15.sp,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                    Spacer(Modifier.width(8.dp))
                                                    Text(
                                                        text = msg.appName,
                                                        color = tint,
                                                        fontSize = 12.sp,
                                                        fontWeight = FontWeight.SemiBold
                                                    )
                                                }
                                                Spacer(Modifier.height(3.dp))
                                                Text(
                                                    text = msg.text,
                                                    color = colors.secondaryText,
                                                    fontSize = 14.sp
                                                )
                                            }
                                            IconButton(onClick = { onReadAloud(msg) }) {
                                                Icon(Icons.AutoMirrored.Rounded.VolumeUp, contentDescription = str("朗讀", "朗读", "Read aloud", "読み上げ"), tint = colors.accent)
                                            }
                                            IconButton(onClick = { onMarkRead(msg) }) {
                                                Icon(Icons.Rounded.Done, contentDescription = str("已讀", "已读", "Mark as read", "既読"), tint = colors.statusConnected)
                                            }
                                        }

                                        AnimatedVisibility(
                                            visible = msg.canReply && showReplies,
                                            enter = expandVertically() + fadeIn(),
                                            exit = shrinkVertically() + fadeOut()
                                        ) {
                                            Row(
                                                modifier = Modifier.horizontalScroll(rememberScrollState()),
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                quickReplies.forEach { reply ->
                                                    Surface(
                                                        color = tint.copy(alpha = 0.16f),
                                                        shape = RoundedCornerShape(12.dp),
                                                        modifier = Modifier.clickable {
                                                            onQuickReply(msg, reply)
                                                            expandedReplyMsgId = null
                                                        }
                                                    ) {
                                                        Text(
                                                            text = reply,
                                                            color = colors.primaryText,
                                                            fontSize = 12.sp,
                                                            fontWeight = FontWeight.SemiBold,
                                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
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
fun GestureFeedbackPill(
    text: String?,
    showDontShowAgain: Boolean = false,
    onDontShowAgain: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val colors = LocalDxCarColors.current
    AnimatedVisibility(
        visible = !text.isNullOrBlank(),
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier
    ) {
        Surface(
            color = colors.glassOverlay,
            shape = RoundedCornerShape(24.dp),
            shadowElevation = 10.dp,
            modifier = Modifier.border(1.5.dp, colors.accent, RoundedCornerShape(24.dp))
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 11.dp)
            ) {
                Text(
                    text = text.orEmpty(),
                    color = colors.primaryText,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                if (showDontShowAgain && onDontShowAgain != null) {
                    Surface(
                        color = colors.accent.copy(alpha = 0.18f),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .border(1.dp, colors.accent.copy(alpha = 0.55f), RoundedCornerShape(14.dp))
                            .clickable { onDontShowAgain() }
                    ) {
                        Text(
                            text = str("不再顯示", "不再显示", "Don't show", "今後表示しない"),
                            color = colors.accent,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                }
            }
        }
    }
}

fun messageBrandColor(pkg: String, fallback: Color): Color {
    return when {
        pkg.contains("line") -> Color(0xFF06C755)
        pkg.contains("orca") || pkg.contains("messenger") -> Color(0xFF0084FF)
        pkg.contains("whatsapp") -> Color(0xFF25D366)
        pkg.contains("telegram") -> Color(0xFF26A5E4)
        pkg.contains("messaging") -> Color(0xFF3B82F6)
        else -> fallback
    }
}

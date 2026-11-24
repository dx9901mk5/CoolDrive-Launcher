package com.dxcar.launcher.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.CallEnd
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Done
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
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
import com.dxcar.launcher.comms.CallActionCommand
import com.dxcar.launcher.comms.CallStatus
import com.dxcar.launcher.comms.CarCallState
import com.dxcar.launcher.comms.CarMessageItem
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
                alpha = (1f - (abs(offsetX.value) / 520f)).coerceIn(0.15f, 1f)
            }
            .pointerInput(resetKey) {
                val thresholdPx = 95.dp.toPx()
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
                                offsetX.animateTo(target, tween(170))
                                onDismissed()
                                offsetX.snapTo(0f)
                            }
                        } else {
                            scope.launch {
                                offsetX.animateTo(0f, spring())
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
 * Authentic Android Auto floating Incoming Call Banner & Active Call Capsule.
 */
@Composable
fun IncomingCallBanner(
    callState: CarCallState,
    onAnswer: () -> Unit,
    onDecline: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalDxCarColors.current
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
        SwipeHorizontalToDismissBox(
            resetKey = "${callState.status}_${callState.startedAtMs}",
            onDismissed = onDecline
        ) {
            Surface(
                color = colors.glassOverlay,
                shape = RoundedCornerShape(28.dp),
                shadowElevation = 18.dp,
                modifier = Modifier
                    .widthIn(max = 760.dp)
                    .fillMaxWidth()
                    .border(
                        width = 2.dp,
                        color = if (callState.status == CallStatus.RINGING) colors.callAcceptGreen else colors.accent,
                        shape = RoundedCornerShape(28.dp)
                    )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 22.dp, vertical = 18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(60.dp)
                            .clip(CircleShape)
                            .background(colors.callAcceptGreen.copy(alpha = 0.22f))
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Call,
                            contentDescription = null,
                            tint = colors.callAcceptGreen,
                            modifier = Modifier.size(34.dp)
                        )
                    }

                    Spacer(Modifier.width(16.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = callState.callerName.ifBlank { callState.callerNumber.ifBlank { "藍牙來電" } },
                            color = colors.primaryText,
                            fontSize = 34.sp,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        val subtitle = when (callState.status) {
                            CallStatus.RINGING ->
                                "${callState.sourceApp} · 來電中 ${callState.callerNumber}"
                            CallStatus.ACTIVE ->
                                "${callState.sourceApp} · 通話中 %02d:%02d".format(elapsedSec / 60, elapsedSec % 60)
                            CallStatus.IDLE -> ""
                        }
                        Text(
                            text = subtitle,
                            color = colors.secondaryText,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(Modifier.width(14.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (callState.status == CallStatus.RINGING) {
                            Surface(
                                color = colors.callAcceptGreen,
                                shape = CircleShape,
                                modifier = Modifier
                                    .size(62.dp)
                                    .bouncyClickable(pressedScale = 0.86f, onClick = onAnswer)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Rounded.Call,
                                        contentDescription = "接聽",
                                        tint = Color.White,
                                        modifier = Modifier.size(32.dp)
                                    )
                                }
                            }
                        }

                        Surface(
                            color = colors.callDeclineRed,
                            shape = CircleShape,
                            modifier = Modifier
                                .size(62.dp)
                                .bouncyClickable(pressedScale = 0.86f, onClick = onDecline)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Rounded.CallEnd,
                                    contentDescription = "掛斷",
                                    tint = Color.White,
                                    modifier = Modifier.size(32.dp)
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
 * Top-Left Extra-Large (2X Font) Incoming Message Banner with Left/Right Touch Swipe-to-Dismiss:
 * - Positioned in Top-Left corner of Car Launcher & Fullscreen Overlay
 * - 2X Extra-Large Typography (Sender 32sp, Content 28sp, App Badge 20sp, Reply Chips 22sp)
 * - Swipe Left or Right anywhere on the card to dismiss!
 */
@Composable
fun IncomingMessageBanner(
    message: CarMessageItem?,
    quickReplies: List<String>,
    onReadAloud: (CarMessageItem) -> Unit,
    onQuickReply: (CarMessageItem, String) -> Unit,
    onVoiceReply: ((CarMessageItem) -> Unit)? = null,
    onMarkRead: (CarMessageItem) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalDxCarColors.current
    AnimatedVisibility(
        visible = message != null,
        enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
        modifier = modifier
    ) {
        val item = message ?: return@AnimatedVisibility
        val appTint = messageBrandColor(item.appPackage, colors.accent)

        SwipeHorizontalToDismissBox(
            resetKey = item.id,
            onDismissed = onDismiss
        ) {
            Surface(
                color = colors.glassOverlay,
                shape = RoundedCornerShape(26.dp),
                shadowElevation = 18.dp,
                modifier = Modifier
                    .widthIn(max = 780.dp)
                    .fillMaxWidth()
                    .border(2.dp, appTint.copy(alpha = 0.8f), RoundedCornerShape(26.dp))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Top
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .padding(top = 4.dp)
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(appTint.copy(alpha = 0.22f))
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.Chat,
                                contentDescription = null,
                                tint = appTint,
                                modifier = Modifier.size(30.dp)
                            )
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    text = item.sender,
                                    color = colors.primaryText,
                                    fontSize = 32.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                Surface(
                                    color = appTint.copy(alpha = 0.24f),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text(
                                        text = item.appName,
                                        color = appTint,
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                                    )
                                }
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = item.text,
                                color = colors.primaryText.copy(alpha = 0.94f),
                                fontSize = 28.sp,
                                lineHeight = 36.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "⇄ 左右滑動可直接關閉通知",
                                color = colors.secondaryText,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            IconButton(
                                onClick = { onReadAloud(item) },
                                modifier = Modifier.size(50.dp)
                            ) {
                                Icon(Icons.AutoMirrored.Rounded.VolumeUp, contentDescription = "語音朗讀", tint = colors.accent, modifier = Modifier.size(28.dp))
                            }
                            if (onVoiceReply != null && item.canReply) {
                                IconButton(
                                    onClick = { onVoiceReply(item) },
                                    modifier = Modifier.size(50.dp)
                                ) {
                                    Icon(Icons.Rounded.Mic, contentDescription = "語音回覆", tint = colors.accent, modifier = Modifier.size(28.dp))
                                }
                            }
                            IconButton(
                                onClick = { onMarkRead(item) },
                                modifier = Modifier.size(50.dp)
                            ) {
                                Icon(Icons.Rounded.Done, contentDescription = "已讀", tint = colors.statusConnected, modifier = Modifier.size(28.dp))
                            }
                            IconButton(
                                onClick = onDismiss,
                                modifier = Modifier.size(50.dp)
                            ) {
                                Icon(Icons.Rounded.Close, contentDescription = "關閉", tint = colors.secondaryText, modifier = Modifier.size(28.dp))
                            }
                        }
                    }

                    // Quick Reply Chips (2X Extra-Large Font when Direct Reply is supported)
                    if (item.canReply && quickReplies.isNotEmpty()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            quickReplies.forEach { reply ->
                                Surface(
                                    color = colors.cardSurfaceElevated,
                                    shape = RoundedCornerShape(18.dp),
                                    modifier = Modifier
                                        .border(1.2.dp, colors.cardBorder, RoundedCornerShape(18.dp))
                                        .bouncyClickable(pressedScale = 0.90f) { onQuickReply(item, reply) }
                                ) {
                                    Text(
                                        text = "回覆：$reply",
                                        color = colors.primaryText,
                                        fontSize = 22.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
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

@Composable
fun NotificationCenterSheet(
    messages: List<CarMessageItem>,
    quickReplies: List<String>,
    onReadAloud: (CarMessageItem) -> Unit,
    onQuickReply: (CarMessageItem, String) -> Unit,
    onMarkRead: (CarMessageItem) -> Unit,
    onClearAll: () -> Unit,
    onClose: () -> Unit
) {
    val colors = LocalDxCarColors.current
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
                    Text(
                        text = "訊息與通知中心",
                        color = colors.primaryText,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (messages.isNotEmpty()) {
                            IconButton(onClick = onClearAll) {
                                Icon(Icons.Rounded.DeleteSweep, contentDescription = "全部清除", tint = colors.secondaryText)
                            }
                        }
                        IconButton(onClick = onClose) {
                            Icon(Icons.Rounded.Close, contentDescription = "關閉", tint = colors.primaryText)
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))

                if (messages.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = "目前沒有未讀訊息\n手機配對並開啟同步端後，LINE／Messenger／簡訊會即時顯示於此",
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
                            Surface(
                                color = colors.cardSurfaceElevated,
                                shape = RoundedCornerShape(18.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.dp, colors.cardBorder, RoundedCornerShape(18.dp))
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
                                            Icon(Icons.AutoMirrored.Rounded.VolumeUp, contentDescription = "朗讀", tint = colors.accent)
                                        }
                                        IconButton(onClick = { onMarkRead(msg) }) {
                                            Icon(Icons.Rounded.Done, contentDescription = "已讀", tint = colors.statusConnected)
                                        }
                                    }

                                    if (msg.canReply) {
                                        Row(
                                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            quickReplies.forEach { reply ->
                                                Surface(
                                                    color = colors.cardSurface,
                                                    shape = RoundedCornerShape(12.dp),
                                                    modifier = Modifier.clickable { onQuickReply(msg, reply) }
                                                ) {
                                                    Text(
                                                        text = reply,
                                                        color = colors.primaryText,
                                                        fontSize = 12.sp,
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

@Composable
fun GestureFeedbackPill(
    text: String?,
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
            Text(
                text = text.orEmpty(),
                color = colors.primaryText,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 22.dp, vertical = 12.dp)
            )
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

package com.cooldrive.launcher.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cooldrive.launcher.media.ActiveMediaState
import com.cooldrive.launcher.theme.LocalCoolDriveColors

/**
 * Android Auto Coolwalk Media Control Card:
 * - Works with ANY media app running on the AI Box (YouTube, YT Music, Spotify, KKBOX, etc.)
 * - Clicking the card when it is in the Secondary slot smoothly SWAPS it to Primary (AA Coolwalk UX!)
 * - Large car-safe Previous / Play-Pause / Next buttons + session source switcher + 1-tap open YouTube/App fullscreen!
 */
@Composable
fun MediaControlCard(
    media: ActiveMediaState,
    isPrimary: Boolean,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSelectSession: (String) -> Unit,
    onOpenMediaApp: () -> Unit,
    onOpenYoutubeFullscreen: () -> Unit,
    onSwapRequested: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalCoolDriveColors.current
    val artBitmap = remember(media.albumArt) { media.albumArt?.asImageBitmap() }

    val progress = if (media.durationMs > 0L) {
        (media.positionMs.toFloat() / media.durationMs.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.cardSurface)
            .bouncyClickable(enabled = !isPrimary, pressedScale = 0.97f, onClick = onSwapRequested)
    ) {
        // Subtle Ambient Album Art Backdrop Wash (Coolwalk Media Card signature!)
        if (artBitmap != null) {
            Image(
                bitmap = artBitmap,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(if (colors.isNight) 0.24f else 0.16f)
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                colors.cardSurface.copy(alpha = 0.55f),
                                colors.cardSurface.copy(alpha = 0.92f)
                            )
                        )
                    )
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                colors.accent.copy(alpha = if (colors.isNight) 0.16f else 0.10f),
                                colors.cardSurface
                            )
                        )
                    )
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(if (isPrimary) 20.dp else 16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // HEADER ROW: App Source Badge + Swap & Open Fullscreen Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Surface(
                    color = colors.cardSurfaceElevated.copy(alpha = 0.9f),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.bouncyClickable(pressedScale = 0.92f, onClick = onOpenMediaApp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.MusicNote,
                            contentDescription = null,
                            tint = colors.accent,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = media.appName,
                            color = colors.primaryText,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(
                        color = colors.cardSurfaceElevated,
                        shape = CircleShape,
                        modifier = Modifier
                            .size(34.dp)
                            .bouncyClickable(pressedScale = 0.85f, onClick = onSwapRequested)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.SwapHoriz,
                                contentDescription = "左右互換",
                                tint = colors.primaryText,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Surface(
                        color = colors.cardSurfaceElevated,
                        shape = CircleShape,
                        modifier = Modifier
                            .size(34.dp)
                            .bouncyClickable(pressedScale = 0.85f) {
                                if (media.hasSession) onOpenMediaApp() else onOpenYoutubeFullscreen()
                            }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                                contentDescription = "全螢幕展開媒體",
                                tint = colors.primaryText,
                                modifier = Modifier.size(17.dp)
                            )
                        }
                    }
                }
            }

            // MIDDLE: Album Artwork + Track Title + Artist
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(if (isPrimary) 104.dp else 70.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(colors.cardSurfaceElevated)
                        .border(1.dp, colors.cardBorder, RoundedCornerShape(18.dp))
                ) {
                    if (artBitmap != null) {
                        Image(
                            bitmap = artBitmap,
                            contentDescription = "專輯封面",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.Album,
                            contentDescription = null,
                            tint = colors.accent,
                            modifier = Modifier.size(if (isPrimary) 52.dp else 36.dp)
                        )
                    }
                }

                Spacer(Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = media.title,
                        color = colors.primaryText,
                        fontSize = if (isPrimary) 22.sp else 17.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = media.artist,
                        color = colors.secondaryText,
                        fontSize = if (isPrimary) 15.sp else 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (isPrimary && media.album.isNotBlank()) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = media.album,
                            color = colors.secondaryText.copy(alpha = 0.8f),
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // BOTTOM: Progress + Car-Safe Transport Controls + Quick Session Switcher / YT Launch
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (media.durationMs > 0L) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        LinearProgressIndicator(
                            progress = { progress },
                            color = colors.accent,
                            trackColor = colors.cardBorder,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(5.dp)
                                .clip(RoundedCornerShape(4.dp))
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = formatMediaTime(media.positionMs),
                                color = colors.secondaryText,
                                fontSize = 11.sp
                            )
                            Text(
                                text = formatMediaTime(media.durationMs),
                                color = colors.secondaryText,
                                fontSize = 11.sp
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TransportButton(
                        icon = Icons.Rounded.SkipPrevious,
                        contentDescription = "上一首",
                        large = false,
                        onClick = onPrevious
                    )
                    TransportButton(
                        icon = if (media.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = if (media.isPlaying) "暫停" else "播放",
                        large = true,
                        onClick = onPlayPause
                    )
                    TransportButton(
                        icon = Icons.Rounded.SkipNext,
                        contentDescription = "下一首",
                        large = false,
                        onClick = onNext
                    )
                }

                // Multi-session switcher OR Quick YouTube Fullscreen button when idle
                if (media.availableSessions.size > 1) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        media.availableSessions.take(3).forEach { sess ->
                            val selected = sess.packageName == media.packageName
                            Surface(
                                color = if (selected) colors.accent else colors.cardSurfaceElevated,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.bouncyClickable(pressedScale = 0.90f) { onSelectSession(sess.packageName) }
                            ) {
                                Text(
                                    text = sess.appLabel,
                                    color = if (selected) colors.onAccent else colors.secondaryText,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                )
                            }
                        }
                    }
                } else if (!media.hasSession) {
                    Surface(
                        color = Color(0xFFDC2626).copy(alpha = 0.16f),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, Color(0xFFEF4444).copy(alpha = 0.45f), RoundedCornerShape(14.dp))
                            .bouncyClickable(pressedScale = 0.94f, onClick = onOpenYoutubeFullscreen)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.PlayArrow,
                                contentDescription = null,
                                tint = Color(0xFFEF4444),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "全螢幕開啟 YouTube",
                                color = colors.primaryText,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TransportButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    large: Boolean,
    onClick: () -> Unit
) {
    val colors = LocalCoolDriveColors.current
    Surface(
        color = if (large) colors.accent else colors.cardSurfaceElevated,
        shape = CircleShape,
        shadowElevation = if (large) 4.dp else 0.dp,
        modifier = Modifier
            .size(if (large) 62.dp else 48.dp)
            .bouncyClickable(pressedScale = 0.84f, onClick = onClick)
    ) {
        Box(contentAlignment = Alignment.Center) {
            AnimatedContent(
                targetState = icon,
                transitionSpec = {
                    (scaleIn(tween(180)) + fadeIn(tween(150))) togetherWith
                        (scaleOut(tween(140)) + fadeOut(tween(120)))
                },
                label = "transportIconMorph"
            ) { targetIcon ->
                Icon(
                    imageVector = targetIcon,
                    contentDescription = contentDescription,
                    tint = if (large) colors.onAccent else colors.primaryText,
                    modifier = Modifier.size(if (large) 34.dp else 26.dp)
                )
            }
        }
    }
}

private fun formatMediaTime(ms: Long): String {
    val totalSec = (ms / 1000L).coerceAtLeast(0L)
    val min = totalSec / 60L
    val sec = totalSec % 60L
    return "%d:%02d".format(min, sec)
}

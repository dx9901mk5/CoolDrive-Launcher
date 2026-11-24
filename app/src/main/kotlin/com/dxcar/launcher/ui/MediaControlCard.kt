package com.dxcar.launcher.ui

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
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Thunderstorm
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material.icons.rounded.WbCloudy
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dxcar.launcher.data.SplitScreenShortcut
import com.dxcar.launcher.i18n.DxCarI18n.str
import com.dxcar.launcher.media.ActiveMediaState
import com.dxcar.launcher.system.InstalledAppInfo
import com.dxcar.launcher.theme.LocalDxCarColors
import com.dxcar.launcher.weather.LocalWeatherState
import kotlin.math.min

/**
 * Integrated Weather + Large Album-Cover Media Controller Widget:
 * - Top zone (inside the same unified widget card): Live Local Weather read from Google App / GPS fallback.
 * - Center hero zone: Extra-large square Album Cover + remembered last music app one-tap resume.
 * - Bottom zone: Heart/Favorite button + Transport controls + optional 6 custom shortcuts.
 */
@Composable
fun MediaControlCard(
    media: ActiveMediaState,
    weather: LocalWeatherState = LocalWeatherState(),
    isPrimary: Boolean,
    pureMusicMode: Boolean = false,
    showSwapButton: Boolean = true,
    showWeatherStrip: Boolean = true,
    showSixShortcuts: Boolean = false,
    customShortcutSlots: List<String> = emptyList(),
    installedApps: List<InstalledAppInfo> = emptyList(),
    splitShortcuts: List<SplitScreenShortcut> = emptyList(),
    onWeatherClick: () -> Unit = {},
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onToggleFavorite: () -> Unit = {},
    onSelectSession: (String) -> Unit,
    onOpenMediaApp: () -> Unit,
    onOpenYoutubeFullscreen: () -> Unit = {},
    onSwapRequested: () -> Unit = {},
    onCustomSlotEmptyClick: (Int) -> Unit = {},
    onCustomSlotLaunch: (String) -> Unit = {},
    onCustomSlotRemove: (Int, String) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    val colors = LocalDxCarColors.current
    val artBitmap = remember(media.albumArt) { media.albumArt?.asImageBitmap() }
    val sourceIconBitmap = remember(media.appIcon) { media.appIcon?.asImageBitmap() }

    val progress = if (media.durationMs > 0L) {
        (media.positionMs.toFloat() / media.durationMs.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.cardSurface)
            .bouncyClickable(
                enabled = !isPrimary && showSwapButton,
                pressedScale = 0.98f,
                onClick = onSwapRequested
            )
    ) {
        if (artBitmap != null) {
            Image(
                bitmap = artBitmap,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(if (colors.isNight) 0.30f else 0.18f)
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                colors.cardSurface.copy(alpha = 0.44f),
                                colors.cardSurface.copy(alpha = 0.94f)
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

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = if (isPrimary) 14.dp else 10.dp, vertical = 7.dp)
        ) {
            val tightHeight = maxHeight < 450.dp
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(if (tightHeight) 4.dp else 6.dp)
            ) {
                // TOP UNIFIED SUB-SECTION: Local Weather Strip (Google App / Live GPS) + Player Header
                Column(verticalArrangement = Arrangement.spacedBy(if (tightHeight) 4.dp else 6.dp)) {
                    if (showWeatherStrip) {
                        IntegratedLocalWeatherBar(
                            weather = weather,
                            compact = tightHeight,
                            onClick = onWeatherClick
                        )
                    }

                    // Detected / Remembered Player Icon + Name + Session Switcher + Swap/Open
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Surface(
                            color = colors.cardSurfaceElevated.copy(alpha = 0.92f),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .weight(1f, fill = false)
                                .bouncyClickable(pressedScale = 0.92f, onClick = onOpenMediaApp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (sourceIconBitmap != null) {
                                    Image(
                                        bitmap = sourceIconBitmap,
                                        contentDescription = media.appName,
                                        modifier = Modifier
                                            .size(18.dp)
                                            .clip(RoundedCornerShape(5.dp))
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Rounded.MusicNote,
                                        contentDescription = null,
                                        tint = colors.accent,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = if (media.isRememberedLastApp) {
                                        str("上次：${media.appName}", "Last: ${media.appName}", "前回：${media.appName}")
                                    } else {
                                        media.appName
                                    },
                                    color = colors.primaryText,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Spacer(Modifier.width(6.dp))

                        Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (media.availableSessions.size > 1) {
                                media.availableSessions.take(3).forEach { sess ->
                                    val selected = sess.packageName == media.packageName
                                    val sessIcon = remember(sess.appIcon) { sess.appIcon?.asImageBitmap() }
                                    Surface(
                                        color = if (selected) colors.accent.copy(alpha = 0.25f) else colors.cardSurfaceElevated,
                                        shape = CircleShape,
                                        modifier = Modifier
                                            .size(26.dp)
                                            .border(
                                                width = if (selected) 1.5.dp else 0.75.dp,
                                                color = if (selected) colors.accent else colors.cardBorder,
                                                shape = CircleShape
                                            )
                                            .bouncyClickable(pressedScale = 0.86f) { onSelectSession(sess.packageName) }
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            if (sessIcon != null) {
                                                Image(
                                                    bitmap = sessIcon,
                                                    contentDescription = sess.appLabel,
                                                    modifier = Modifier
                                                        .size(17.dp)
                                                        .clip(RoundedCornerShape(5.dp))
                                                )
                                            } else {
                                                Icon(
                                                    imageVector = Icons.Rounded.MusicNote,
                                                    contentDescription = sess.appLabel,
                                                    tint = colors.primaryText,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            if (showSwapButton) {
                                Surface(
                                    color = colors.cardSurfaceElevated,
                                    shape = CircleShape,
                                    modifier = Modifier
                                        .size(28.dp)
                                        .bouncyClickable(pressedScale = 0.85f, onClick = onSwapRequested)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Rounded.SwapHoriz,
                                            contentDescription = "左右互換",
                                            tint = colors.primaryText,
                                            modifier = Modifier.size(15.dp)
                                        )
                                    }
                                }
                            }

                            Surface(
                                color = colors.cardSurfaceElevated,
                                shape = CircleShape,
                                modifier = Modifier
                                    .size(28.dp)
                                    .bouncyClickable(pressedScale = 0.85f) {
                                        if (media.packageName.isNotBlank()) onOpenMediaApp() else onOpenYoutubeFullscreen()
                                    }
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                                        contentDescription = "開啟播放器",
                                        tint = colors.primaryText,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // ENLARGED ALBUM-COVER HERO SECTION (Full-width adaptive cover + protected Track info below!)
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    AlbumCoverFrame(
                        artBitmap = artBitmap,
                        appIconBitmap = sourceIconBitmap,
                        isRememberedResume = media.isRememberedLastApp,
                        onTapCover = {
                            if (media.isRememberedLastApp) onPlayPause() else onOpenMediaApp()
                        },
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .heightIn(min = 56.dp)
                    )
                    Spacer(Modifier.height(if (tightHeight) 4.dp else 6.dp))
                    Text(
                        text = media.title,
                        color = colors.primaryText,
                        fontSize = if (isPrimary) 18.sp else if (tightHeight) 13.sp else 14.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)
                    )
                    Text(
                        text = media.artist,
                        color = if (media.isRememberedLastApp) colors.accent else colors.secondaryText,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)
                    )
                }

                // PROGRESS + LIKE/FAVORITE + TRANSPORT + INTEGRATED 6 SHORTCUTS
                Column(verticalArrangement = Arrangement.spacedBy(if (tightHeight) 4.dp else 6.dp)) {
                    if (media.durationMs > 0L) {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            LinearProgressIndicator(
                                progress = { progress },
                                color = colors.accent,
                                trackColor = colors.cardBorder,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(4.dp)
                                    .clip(RoundedCornerShape(4.dp))
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(formatMediaTime(media.positionMs), color = colors.secondaryText, fontSize = 10.sp)
                                Text(formatMediaTime(media.durationMs), color = colors.secondaryText, fontSize = 10.sp)
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val heartPink = Color(0xFFF43F5E)
                        Surface(
                            color = if (media.isFavorited) heartPink.copy(alpha = 0.22f) else colors.cardSurfaceElevated,
                            shape = CircleShape,
                            modifier = Modifier
                                .size(if (tightHeight) 38.dp else 42.dp)
                                .border(
                                    width = if (media.isFavorited) 1.2.dp else 0.75.dp,
                                    color = if (media.isFavorited) heartPink else colors.cardBorder.copy(alpha = 0.6f),
                                    shape = CircleShape
                                )
                                .bouncyClickable(pressedScale = 0.84f, onClick = onToggleFavorite)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = if (media.isFavorited) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                                    contentDescription = "喜歡／點讚收藏",
                                    tint = when {
                                        media.isFavorited -> heartPink
                                        media.canFavorite -> colors.primaryText
                                        else -> colors.secondaryText.copy(alpha = 0.75f)
                                    },
                                    modifier = Modifier.size(19.dp)
                                )
                            }
                        }

                        TransportButton(
                            icon = Icons.Rounded.SkipPrevious,
                            contentDescription = "上一首",
                            large = false,
                            compact = tightHeight,
                            onClick = onPrevious
                        )
                        TransportButton(
                            icon = if (media.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = if (media.isPlaying) "暫停" else "播放／繼續播放",
                            large = true,
                            compact = tightHeight,
                            onClick = onPlayPause
                        )
                        TransportButton(
                            icon = Icons.Rounded.SkipNext,
                            contentDescription = "下一首",
                            large = false,
                            compact = tightHeight,
                            onClick = onNext
                        )
                    }

                    if (showSixShortcuts) {
                        HorizontalDivider(
                            color = colors.cardBorder.copy(alpha = 0.65f),
                            thickness = 1.dp,
                            modifier = Modifier.padding(vertical = 1.dp)
                        )
                        CustomSixShortcutsSection(
                            slots = customShortcutSlots,
                            installedApps = installedApps,
                            splitShortcuts = splitShortcuts,
                            onSlotEmptyClick = onCustomSlotEmptyClick,
                            onSlotLaunch = onCustomSlotLaunch,
                            onSlotRemove = onCustomSlotRemove
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun IntegratedLocalWeatherBar(
    weather: LocalWeatherState,
    compact: Boolean,
    onClick: () -> Unit
) {
    val colors = LocalDxCarColors.current
    val (icon, iconTint) = when {
        weather.weatherCode >= 95 -> Icons.Rounded.Thunderstorm to Color(0xFFF59E0B)
        weather.weatherCode in 51..82 -> Icons.Rounded.WaterDrop to Color(0xFF38BDF8)
        weather.weatherCode == 0 || weather.weatherCode == 1 -> Icons.Rounded.WbSunny to Color(0xFFFBBF24)
        weather.weatherCode == 2 -> Icons.Rounded.WbCloudy to Color(0xFF93C5FD)
        else -> Icons.Rounded.Cloud to Color(0xFF94A3B8)
    }

    Surface(
        color = colors.cardSurfaceElevated.copy(alpha = 0.88f),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(0.8.dp, colors.cardBorder.copy(alpha = 0.7f), RoundedCornerShape(14.dp))
            .bouncyClickable(pressedScale = 0.96f, onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = if (compact) 5.dp else 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = weather.conditionText,
                    tint = iconTint,
                    modifier = Modifier.size(if (compact) 18.dp else 21.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = weather.temperatureText,
                    color = colors.primaryText,
                    fontSize = if (compact) 15.sp else 17.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = weather.conditionText,
                    color = colors.primaryText,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 6.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.LocationOn,
                    contentDescription = null,
                    tint = colors.accent,
                    modifier = Modifier.size(13.dp)
                )
                Spacer(Modifier.width(2.dp))
                Text(
                    text = buildString {
                        append(weather.locationName)
                        if (weather.detailText.isNotBlank() && !compact) {
                            append(" · ")
                            append(weather.detailText)
                        }
                    },
                    color = colors.secondaryText,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun AlbumCoverFrame(
    artBitmap: androidx.compose.ui.graphics.ImageBitmap?,
    appIconBitmap: androidx.compose.ui.graphics.ImageBitmap?,
    isRememberedResume: Boolean,
    onTapCover: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalDxCarColors.current
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .clip(RoundedCornerShape(22.dp))
            .background(colors.cardSurfaceElevated)
            .border(1.4.dp, colors.cardBorder, RoundedCornerShape(22.dp))
            .bouncyClickable(pressedScale = 0.95f, onClick = onTapCover)
    ) {
        when {
            artBitmap != null -> {
                Image(
                    bitmap = artBitmap,
                    contentDescription = "專輯封面",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            appIconBitmap != null -> {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Image(
                        bitmap = appIconBitmap,
                        contentDescription = "上次播放器圖示",
                        modifier = Modifier
                            .size(58.dp)
                            .clip(RoundedCornerShape(16.dp))
                    )
                    if (isRememberedResume) {
                        Spacer(Modifier.height(6.dp))
                        Surface(
                            color = colors.accent.copy(alpha = 0.20f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Rounded.PlayArrow, contentDescription = null, tint = colors.accent, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(3.dp))
                                Text("點擊繼續播放", color = colors.accent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
            else -> {
                Icon(
                    imageVector = Icons.Rounded.Album,
                    contentDescription = null,
                    tint = colors.accent,
                    modifier = Modifier.size(64.dp)
                )
            }
        }
    }
}

@Composable
private fun TransportButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    large: Boolean,
    compact: Boolean,
    onClick: () -> Unit
) {
    val colors = LocalDxCarColors.current
    val btnSize = when {
        large && compact -> 48.dp
        large -> 54.dp
        compact -> 38.dp
        else -> 42.dp
    }
    Surface(
        color = if (large) colors.accent else colors.cardSurfaceElevated,
        shape = CircleShape,
        shadowElevation = if (large) 4.dp else 0.dp,
        modifier = Modifier
            .size(btnSize)
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
                    modifier = Modifier.size(if (large) 26.dp else 21.dp)
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

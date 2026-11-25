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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.zIndex
import com.dxcar.launcher.data.SplitScreenShortcut
import com.dxcar.launcher.media.ActiveMediaState
import com.dxcar.launcher.system.InstalledAppInfo
import com.dxcar.launcher.theme.LocalDxCarColors
import com.dxcar.launcher.weather.LocalWeatherState

/**
 * Full-Top Enlarged Album-Cover Media Controller Card (Page 1):
 * - Enlarged Album Cover fills the entire upper card area (red-box zone); ONLY tapping the Album Cover
 *   triggers card swap (`onSwapRequested`), leaving all bottom controls & shortcuts 100% operable!
 * - Compact Weather + Location pill overlaid at the Top-Left corner of the Album Cover (clicking opens Google Weather).
 * - Redundant top App-Name header bar removed; Playing App Icon sits right next to the Like (Heart) button
 *   and opens the playing media app on tap!
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

    // Filter out redundant app-name or idle placeholder strings so ONLY real track title & artist appear
    val cleanTitle = media.title.trim().takeIf {
        it.isNotEmpty() &&
            !it.equals(media.appName.trim(), ignoreCase = true) &&
            it != "目前沒有播放中的媒體" &&
            it != "媒體播放器"
    }.orEmpty()

    val cleanArtist = media.artist.trim().takeIf {
        it.isNotEmpty() &&
            !it.equals(media.appName.trim(), ignoreCase = true) &&
            !it.equals(cleanTitle, ignoreCase = true) &&
            !it.startsWith("開啟音樂 App")
    }.orEmpty()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.cardSurface)
    ) {
        if (artBitmap != null) {
            Image(
                bitmap = artBitmap,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(if (colors.isNight) 0.28f else 0.16f)
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                colors.cardSurface.copy(alpha = 0.42f),
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
                .padding(horizontal = if (isPrimary) 12.dp else 8.dp, vertical = 7.dp)
        ) {
            val tightHeight = maxHeight < 450.dp
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(if (tightHeight) 4.dp else 6.dp)
            ) {
                // ENLARGED TOP-TO-BOTTOM ALBUM COVER + TOP-LEFT OVERLAID WEATHER & LOCATION PILL
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .heightIn(min = 72.dp)
                    ) {
                        AlbumCoverFrame(
                            artBitmap = artBitmap,
                            appIconBitmap = sourceIconBitmap,
                            onTapCover = {
                                if (showSwapButton && !pureMusicMode) {
                                    onSwapRequested()
                                } else if (media.isRememberedLastApp) {
                                    onPlayPause()
                                } else {
                                    onOpenMediaApp()
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )

                        if (showWeatherStrip) {
                            OverlaidCornerWeatherBadge(
                                weather = weather,
                                compact = tightHeight,
                                onClick = onWeatherClick,
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .padding(8.dp)
                                    .zIndex(5f)
                            )
                        }
                    }

                    if (cleanTitle.isNotEmpty() || cleanArtist.isNotEmpty()) {
                        Spacer(Modifier.height(if (tightHeight) 3.dp else 5.dp))
                        if (cleanTitle.isNotEmpty()) {
                            Text(
                                text = cleanTitle,
                                color = colors.primaryText,
                                fontSize = if (isPrimary) 17.sp else if (tightHeight) 13.sp else 14.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)
                            )
                        }
                        if (cleanArtist.isNotEmpty()) {
                            Text(
                                text = cleanArtist,
                                color = colors.secondaryText,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)
                            )
                        }
                    }
                }

                // PROGRESS + [APP ICON + LIKE] + TRANSPORT + INTEGRATED 6 SHORTCUTS
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

                    val sideBtnSize = if (tightHeight) 36.dp else 40.dp
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Playing App Icon right next to Like button -> Tap enters Playing App directly!
                        Surface(
                            color = colors.cardSurfaceElevated,
                            shape = CircleShape,
                            modifier = Modifier
                                .size(sideBtnSize)
                                .border(0.8.dp, colors.cardBorder.copy(alpha = 0.7f), CircleShape)
                                .bouncyClickable(pressedScale = 0.84f) {
                                    if (media.packageName.isNotBlank()) onOpenMediaApp() else onOpenYoutubeFullscreen()
                                }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                if (sourceIconBitmap != null) {
                                    Image(
                                        bitmap = sourceIconBitmap,
                                        contentDescription = "開啟播放 App",
                                        modifier = Modifier
                                            .size(if (tightHeight) 22.dp else 25.dp)
                                            .clip(RoundedCornerShape(7.dp))
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Rounded.MusicNote,
                                        contentDescription = "開啟播放 App",
                                        tint = colors.accent,
                                        modifier = Modifier.size(19.dp)
                                    )
                                }
                            }
                        }

                        // Heart / Favorite Button
                        val heartPink = Color(0xFFF43F5E)
                        Surface(
                            color = if (media.isFavorited) heartPink.copy(alpha = 0.22f) else colors.cardSurfaceElevated,
                            shape = CircleShape,
                            modifier = Modifier
                                .size(sideBtnSize)
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
                                    modifier = Modifier.size(18.dp)
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

/**
 * Compact Weather + Location Pill overlaid at the Top-Left corner of the Album Cover (Image 3).
 * Clicking opens Google Weather directly without triggering the cover swap!
 */
@Composable
private fun OverlaidCornerWeatherBadge(
    weather: LocalWeatherState,
    compact: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalDxCarColors.current
    val (icon, iconTint) = when {
        weather.weatherCode >= 95 -> Icons.Rounded.Thunderstorm to Color(0xFFF59E0B)
        weather.weatherCode in 51..82 -> Icons.Rounded.WaterDrop to Color(0xFF38BDF8)
        weather.weatherCode == 0 || weather.weatherCode == 1 -> Icons.Rounded.WbSunny to Color(0xFFFBBF24)
        weather.weatherCode == 2 -> Icons.Rounded.WbCloudy to Color(0xFF93C5FD)
        else -> Icons.Rounded.Cloud to Color(0xFFCBD5E1)
    }

    Surface(
        color = Color(0xBF0F172A),
        shape = RoundedCornerShape(14.dp),
        shadowElevation = 4.dp,
        modifier = modifier
            .widthIn(max = 220.dp)
            .border(0.8.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(14.dp))
            .bouncyClickable(pressedScale = 0.92f, onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = if (compact) 4.dp else 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = weather.conditionText,
                tint = iconTint,
                modifier = Modifier.size(if (compact) 15.dp else 17.dp)
            )
            Text(
                text = weather.temperatureText,
                color = Color.White,
                fontSize = if (compact) 12.5.sp else 13.5.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1
            )
            if (weather.conditionText.isNotBlank()) {
                Text(
                    text = weather.conditionText,
                    color = Color.White.copy(alpha = 0.90f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (weather.locationName.isNotBlank()) {
                Icon(
                    imageVector = Icons.Rounded.LocationOn,
                    contentDescription = null,
                    tint = colors.accent,
                    modifier = Modifier.size(11.dp)
                )
                Text(
                    text = weather.locationName,
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Medium,
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
    onTapCover: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalDxCarColors.current
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(colors.cardSurfaceElevated)
            .border(1.2.dp, colors.cardBorder, RoundedCornerShape(20.dp))
            .bouncyClickable(pressedScale = 0.96f, onClick = onTapCover)
    ) {
        when {
            artBitmap != null -> {
                Image(
                    bitmap = artBitmap,
                    contentDescription = "專輯封面（點擊交換左右視窗）",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            appIconBitmap != null -> {
                Image(
                    bitmap = appIconBitmap,
                    contentDescription = "播放器圖示（點擊交換左右視窗）",
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(18.dp))
                )
            }
            else -> {
                Icon(
                    imageVector = Icons.Rounded.Album,
                    contentDescription = null,
                    tint = colors.accent,
                    modifier = Modifier.size(68.dp)
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
        large && compact -> 46.dp
        large -> 50.dp
        compact -> 36.dp
        else -> 40.dp
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
                    modifier = Modifier.size(if (large) 24.dp else 20.dp)
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

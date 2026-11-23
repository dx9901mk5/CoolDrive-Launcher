package com.dxcar.launcher.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Splitscreen
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dxcar.launcher.data.SplitScreenShortcut
import com.dxcar.launcher.system.AppCategory
import com.dxcar.launcher.system.InstalledAppInfo
import com.dxcar.launcher.theme.LocalDxCarColors

object BuiltInShortcutTokens {
    const val SETTINGS = "__builtin_settings__"
    const val YOUTUBE = "__builtin_youtube__"
    const val DIALER = "__builtin_dialer__"
    const val DAY_NIGHT = "__builtin_daynight__"
    const val SPLIT_GMAPS = "__builtin_split_gmaps__"
    const val SPLIT_PREFIX = "__split__"
}

private data class BuiltInShortcutMeta(
    val token: String,
    val label: String,
    val icon: ImageVector,
    val tint: Color
)

private fun builtInShortcuts(accent: Color): List<BuiltInShortcutMeta> = listOf(
    BuiltInShortcutMeta(BuiltInShortcutTokens.SPLIT_GMAPS, "分割G地圖", Icons.Rounded.Splitscreen, Color(0xFF34A853)),
    BuiltInShortcutMeta(BuiltInShortcutTokens.YOUTUBE, "YouTube", Icons.Rounded.PlayArrow, Color(0xFFEF4444)),
    BuiltInShortcutMeta(BuiltInShortcutTokens.DIALER, "藍牙電話", Icons.Rounded.Phone, Color(0xFF38BDF8)),
    BuiltInShortcutMeta(BuiltInShortcutTokens.DAY_NIGHT, "切換日夜", Icons.Rounded.DarkMode, Color(0xFFF59E0B)),
    BuiltInShortcutMeta(BuiltInShortcutTokens.SETTINGS, "啟動器設定", Icons.Rounded.Settings, accent)
)

/**
 * Right-bottom card featuring 6 user-customizable App Shortcut slots:
 * - Tap empty "+" slot -> opens AppPickerDialog with real App Icons to assign an app
 * - Tap assigned App Icon -> launches that app immediately (and records into Left Recent Dock)
 * - Long-press assigned App Icon -> removes it back to empty "+" slot with spring bounce!
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CustomSixAppBarCard(
    slots: List<String>,
    installedApps: List<InstalledAppInfo>,
    splitShortcuts: List<SplitScreenShortcut> = emptyList(),
    onSlotEmptyClick: (Int) -> Unit,
    onSlotLaunch: (String) -> Unit,
    onSlotRemove: (Int, String) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalDxCarColors.current
    val cardShape = RoundedCornerShape(colors.cardCornerDp.dp)
    val byPkg = remember(installedApps) { installedApps.associateBy { it.packageName } }
    val splitById = remember(splitShortcuts) { splitShortcuts.associateBy { it.id } }
    val builtins = remember(colors.accent) { builtInShortcuts(colors.accent).associateBy { it.token } }
    val normalizedSlots = remember(slots) { List(6) { idx -> slots.getOrElse(idx) { "" } } }
    val allEmpty = remember(normalizedSlots) { normalizedSlots.all { it.isBlank() } }

    Surface(
        color = colors.cardSurface,
        shape = cardShape,
        modifier = modifier
            .fillMaxWidth()
            .height(if (allEmpty) 92.dp else 82.dp)
            .border(colors.cardBorderWidthDp.dp, colors.cardBorder, cardShape)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.Center
        ) {
            if (allEmpty) {
                Text(
                    text = "自訂捷徑列（點擊 ＋ 新增 App · 長按圖示移除）",
                    color = colors.secondaryText,
                    fontSize = 10.sp,
                    modifier = Modifier.padding(start = 4.dp, bottom = 5.dp)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                normalizedSlots.forEachIndexed { index, token ->
                    val installed = byPkg[token]
                    val builtin = builtins[token]
                    val splitSc = if (token.startsWith(BuiltInShortcutTokens.SPLIT_PREFIX)) {
                        splitById[token.removePrefix(BuiltInShortcutTokens.SPLIT_PREFIX)]
                    } else null
                    val isAssigned = installed != null || builtin != null || splitSc != null

                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .weight(1f)
                            .height(54.dp)
                    ) {
                        AnimatedContent(
                            targetState = token to isAssigned,
                            transitionSpec = {
                                (scaleIn(
                                    initialScale = 0.72f,
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                        stiffness = Spring.StiffnessMedium
                                    )
                                ) + fadeIn(tween(180))) togetherWith
                                    (scaleOut(targetScale = 0.72f, animationSpec = tween(140)) + fadeOut(tween(120)))
                            },
                            label = "customSlotAnim_$index"
                        ) { (curToken, assigned) ->
                            if (!assigned) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(RoundedCornerShape(15.dp))
                                        .background(colors.cardSurfaceElevated.copy(alpha = 0.75f))
                                        .border(
                                            width = 1.dp,
                                            color = colors.cardBorder,
                                            shape = RoundedCornerShape(15.dp)
                                        )
                                        .bouncyClickable(pressedScale = 0.86f) {
                                            onSlotEmptyClick(index)
                                        }
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Add,
                                        contentDescription = "新增自訂 App",
                                        tint = colors.secondaryText,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            } else if (installed != null) {
                                val bmp = remember(installed.icon) { installed.icon.asImageBitmap() }
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .size(50.dp)
                                        .clip(RoundedCornerShape(15.dp))
                                        .background(colors.cardSurfaceElevated)
                                        .border(
                                            width = 1.dp,
                                            color = colors.cardBorder.copy(alpha = 0.75f),
                                            shape = RoundedCornerShape(15.dp)
                                        )
                                        .bouncyCombinedClickable(
                                            pressedScale = 0.86f,
                                            onClick = { onSlotLaunch(curToken) },
                                            onLongClick = { onSlotRemove(index, installed.label) }
                                        )
                                ) {
                                    Image(
                                        bitmap = bmp,
                                        contentDescription = installed.label,
                                        modifier = Modifier
                                            .size(38.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                    )
                                }
                            } else if (splitSc != null) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .size(50.dp)
                                        .clip(RoundedCornerShape(15.dp))
                                        .bouncyCombinedClickable(
                                            pressedScale = 0.86f,
                                            onClick = { onSlotLaunch(curToken) },
                                            onLongClick = { onSlotRemove(index, splitSc.label.ifBlank { "分屏捷徑" }) }
                                        )
                                ) {
                                    DualSplitShortcutIcon(
                                        shortcut = splitSc,
                                        appsByPackage = byPkg,
                                        sizeDp = 46
                                    )
                                }
                            } else if (builtin != null) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .size(50.dp)
                                        .clip(RoundedCornerShape(15.dp))
                                        .background(builtin.tint.copy(alpha = 0.18f))
                                        .border(
                                            width = 1.dp,
                                            color = builtin.tint.copy(alpha = 0.55f),
                                            shape = RoundedCornerShape(15.dp)
                                        )
                                        .bouncyCombinedClickable(
                                            pressedScale = 0.86f,
                                            onClick = { onSlotLaunch(curToken) },
                                            onLongClick = { onSlotRemove(index, builtin.label) }
                                        )
                                ) {
                                    Icon(
                                        imageVector = builtin.icon,
                                        contentDescription = builtin.label,
                                        tint = builtin.tint,
                                        modifier = Modifier.size(24.dp)
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

/**
 * Interactive App Picker Overlay triggered when tapping any "+" slot on the Custom 6-App Bar:
 * displays EVERY installed App with its crisp real App Icon + Name, plus built-in car shortcuts,
 * complete with category tabs, bouncy click animations, and spring overscroll bounce!
 */
@Composable
fun AppPickerDialog(
    slotIndex: Int,
    apps: List<InstalledAppInfo>,
    splitShortcuts: List<SplitScreenShortcut> = emptyList(),
    onSelectToken: (String, String) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = LocalDxCarColors.current
    var selectedCategory by remember { mutableStateOf(AppCategory.ALL) }
    val builtins = remember(colors.accent) { builtInShortcuts(colors.accent) }
    val appsByPkg = remember(apps) { apps.associateBy { it.packageName } }

    val filtered = remember(apps, selectedCategory) {
        if (selectedCategory == AppCategory.ALL) apps
        else apps.filter { it.category == selectedCategory }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.62f))
            .clickable { onDismiss() },
        contentAlignment = Alignment.Center
    ) {
        Surface(
            color = colors.cardSurface,
            shape = RoundedCornerShape(28.dp),
            shadowElevation = 22.dp,
            modifier = Modifier
                .fillMaxWidth(0.84f)
                .fillMaxSize(0.84f)
                .border(1.2.dp, colors.cardBorder, RoundedCornerShape(28.dp))
                .clickable(enabled = false) {}
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp, vertical = 16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "選擇要放入第 ${slotIndex + 1} 格的 App",
                            color = colors.primaryText,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "點選下方圖示即可加入捷徑列 · 事後長按圖示即可移除",
                            color = colors.secondaryText,
                            fontSize = 12.sp
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Rounded.Close, contentDescription = "關閉", tint = colors.primaryText)
                    }
                }

                Spacer(Modifier.height(10.dp))

                // Category filter pills
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppCategory.entries.forEach { cat ->
                        val selected = cat == selectedCategory
                        Surface(
                            color = if (selected) colors.accent else colors.cardSurfaceElevated,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .border(
                                    1.dp,
                                    if (selected) colors.accent else colors.cardBorder,
                                    RoundedCornerShape(16.dp)
                                )
                                .bouncyClickable(pressedScale = 0.90f) { selectedCategory = cat }
                        ) {
                            Text(
                                text = cat.labelZh,
                                color = if (selected) colors.onAccent else colors.secondaryText,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                            )
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                // Grid with REAL Spacious App Icons + Rubber-Band Spring Overscroll Bounce
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 138.dp),
                    contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .springBounceOverscroll(maxOverscrollPx = 170f)
                ) {
                    if (selectedCategory == AppCategory.ALL) {
                        items(splitShortcuts, key = { "pick_split_${it.id}" }) { sc ->
                            Surface(
                                color = colors.cardSurfaceElevated,
                                shape = RoundedCornerShape(18.dp),
                                modifier = Modifier
                                    .border(1.dp, Color(0xFF22C55E).copy(alpha = 0.55f), RoundedCornerShape(18.dp))
                                    .clip(RoundedCornerShape(18.dp))
                                    .bouncyClickable(pressedScale = 0.89f) {
                                        onSelectToken(
                                            "${BuiltInShortcutTokens.SPLIT_PREFIX}${sc.id}",
                                            sc.label.ifBlank { "分屏捷徑" }
                                        )
                                    }
                            ) {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    DualSplitShortcutIcon(
                                        shortcut = sc,
                                        appsByPackage = appsByPkg,
                                        sizeDp = 62
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    Text(
                                        text = sc.label.ifBlank { "分屏捷徑" },
                                        color = colors.primaryText,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }

                        items(builtins, key = { it.token }) { item ->
                            Surface(
                                color = colors.cardSurfaceElevated,
                                shape = RoundedCornerShape(18.dp),
                                modifier = Modifier
                                    .border(1.dp, item.tint.copy(alpha = 0.45f), RoundedCornerShape(18.dp))
                                    .clip(RoundedCornerShape(18.dp))
                                    .bouncyClickable(pressedScale = 0.89f) {
                                        onSelectToken(item.token, item.label)
                                    }
                            ) {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier
                                            .size(62.dp)
                                            .clip(RoundedCornerShape(16.dp))
                                            .background(item.tint.copy(alpha = 0.18f))
                                    ) {
                                        Icon(
                                            imageVector = item.icon,
                                            contentDescription = item.label,
                                            tint = item.tint,
                                            modifier = Modifier.size(32.dp)
                                        )
                                    }
                                    Spacer(Modifier.height(8.dp))
                                    Text(
                                        text = item.label,
                                        color = colors.primaryText,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    }

                    items(filtered, key = { it.packageName }) { app ->
                        val bmp = remember(app.icon) { app.icon.asImageBitmap() }
                        Surface(
                            color = colors.cardSurfaceElevated,
                            shape = RoundedCornerShape(18.dp),
                            modifier = Modifier
                                .border(1.dp, colors.cardBorder, RoundedCornerShape(18.dp))
                                .clip(RoundedCornerShape(18.dp))
                                .bouncyClickable(pressedScale = 0.89f) {
                                    onSelectToken(app.packageName, app.label)
                                }
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Image(
                                    bitmap = bmp,
                                    contentDescription = app.label,
                                    modifier = Modifier
                                        .size(62.dp)
                                        .clip(RoundedCornerShape(16.dp))
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    text = app.label,
                                    color = colors.primaryText,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

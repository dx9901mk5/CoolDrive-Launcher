package com.cooldrive.launcher.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.rounded.Splitscreen
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cooldrive.launcher.data.SplitLaunchEngine
import com.cooldrive.launcher.data.SplitRatioPreset
import com.cooldrive.launcher.data.SplitScreenShortcut
import com.cooldrive.launcher.system.InstalledAppInfo
import com.cooldrive.launcher.theme.LocalCoolDriveColors
import java.util.UUID

/**
 * Dual-icon badge representing a saved Split-Screen Shortcut (like [Google Maps | NetEase Music]),
 * used inside both the App Drawer and the 6 Custom Home Shortcut Slots!
 */
@Composable
fun DualSplitShortcutIcon(
    shortcut: SplitScreenShortcut,
    appsByPackage: Map<String, InstalledAppInfo>,
    sizeDp: Int = 54,
    modifier: Modifier = Modifier
) {
    val leftApp = appsByPackage[shortcut.leftPackage]
    val rightApp = appsByPackage[shortcut.rightPackage]
    val leftBmp = remember(leftApp?.icon) { leftApp?.icon?.asImageBitmap() }
    val rightBmp = remember(rightApp?.icon) { rightApp?.icon?.asImageBitmap() }
    val miniSize = (sizeDp * 0.42f).dp

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(sizeDp.dp)
            .clip(RoundedCornerShape((sizeDp * 0.26f).dp))
            .background(Color(0xFFF3F6FB))
            .border(1.dp, Color(0xFFD5DFEE), RoundedCornerShape((sizeDp * 0.26f).dp))
            .padding(3.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            if (leftBmp != null) {
                Image(
                    bitmap = leftBmp,
                    contentDescription = leftApp?.label,
                    modifier = Modifier
                        .size(miniSize)
                        .clip(RoundedCornerShape(6.dp))
                )
            } else {
                Icon(Icons.Rounded.Splitscreen, contentDescription = null, tint = Color(0xFF1A73E8), modifier = Modifier.size(miniSize))
            }
            if (rightBmp != null) {
                Image(
                    bitmap = rightBmp,
                    contentDescription = rightApp?.label,
                    modifier = Modifier
                        .size(miniSize)
                        .clip(RoundedCornerShape(6.dp))
                )
            } else {
                Icon(Icons.Rounded.Splitscreen, contentDescription = null, tint = Color(0xFF22C55E), modifier = Modifier.size(miniSize))
            }
        }
    }
}

/**
 * Split-Screen Shortcut Creator inspired directly by Video 1 ("分屏設定 -> 選擇應用"):
 * - Interactive 5-ratio selector: 3:7, 4:6, 5:5, 6:4, 7:3 with live spring-animated box preview!
 * - Left "+" and Right "+" slots open full App Icon Grid picker
 * - Select Split Engine (Native Split-Screen / Freeform Exact-Ratio Dual Windows)
 * - Tap "儲存" to create a dual-icon Split-Screen Shortcut right in the App Drawer!
 */
@Composable
fun SplitShortcutCreatorDialog(
    apps: List<InstalledAppInfo>,
    onSaveShortcut: (SplitScreenShortcut) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = LocalCoolDriveColors.current
    var selectedRatio by remember { mutableStateOf(SplitRatioPreset.R_7_3) }
    var selectedEngine by remember { mutableStateOf(SplitLaunchEngine.NATIVE_SPLIT_SCREEN) }
    var leftApp by remember { mutableStateOf<InstalledAppInfo?>(null) }
    var rightApp by remember { mutableStateOf<InstalledAppInfo?>(null) }
    var pickingSide by remember { mutableStateOf<String?>(null) } // "LEFT" or "RIGHT"

    val animatedLeftWeight by animateFloatAsState(
        targetValue = selectedRatio.leftWeight,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "splitCreatorLeftWeight"
    )
    val animatedRightWeight = (1f - animatedLeftWeight).coerceAtLeast(0.25f)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.72f))
            .clickable { onDismiss() },
        contentAlignment = Alignment.Center
    ) {
        Surface(
            color = colors.cardSurface,
            shape = RoundedCornerShape(28.dp),
            shadowElevation = 24.dp,
            modifier = Modifier
                .fillMaxWidth(0.86f)
                .fillMaxHeight(0.86f)
                .border(1.2.dp, colors.cardBorder, RoundedCornerShape(28.dp))
                .clickable(enabled = false) {}
        ) {
            AnimatedContent(
                targetState = pickingSide,
                transitionSpec = {
                    (scaleIn(initialScale = 0.95f, animationSpec = tween(200)) + fadeIn(tween(180))) togetherWith
                        (scaleOut(targetScale = 0.95f, animationSpec = tween(140)) + fadeOut(tween(130)))
                },
                label = "splitCreatorStep"
            ) { side ->
                if (side != null) {
                    // STEP B: Pick Left or Right App with REAL App Icons + Rubber-Band Bounce!
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
                                text = if (side == "LEFT") "選擇左側分割應用" else "選擇右側分割應用",
                                color = colors.primaryText,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold
                            )
                            IconButton(onClick = { pickingSide = null }) {
                                Icon(Icons.Rounded.Close, contentDescription = "返回", tint = colors.primaryText)
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = 138.dp),
                            contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalArrangement = Arrangement.spacedBy(18.dp),
                            modifier = Modifier
                                .fillMaxSize()
                                .springBounceOverscroll(maxOverscrollPx = 170f)
                        ) {
                            items(apps, key = { it.packageName }) { app ->
                                val bmp = remember(app.icon) { app.icon.asImageBitmap() }
                                Surface(
                                    color = colors.cardSurfaceElevated,
                                    shape = RoundedCornerShape(18.dp),
                                    modifier = Modifier
                                        .border(1.dp, colors.cardBorder, RoundedCornerShape(18.dp))
                                        .clip(RoundedCornerShape(18.dp))
                                        .bouncyClickable(pressedScale = 0.89f) {
                                            if (side == "LEFT") leftApp = app else rightApp = app
                                            pickingSide = null
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
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // STEP A: Ratio Selector (3:7, 4:6, 5:5, 6:4, 7:3) + Live Animated Left/Right Preview + Save
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 24.dp, vertical = 18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Top Header
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Spacer(Modifier.width(40.dp))
                            Text(
                                text = "分屏設定 · 選擇應用",
                                color = colors.primaryText,
                                fontSize = 21.sp,
                                fontWeight = FontWeight.Bold
                            )
                            IconButton(onClick = onDismiss) {
                                Icon(Icons.Rounded.Close, contentDescription = "關閉", tint = colors.primaryText)
                            }
                        }

                        // Ratio Pills: 3:7 | 4:6 | 5:5 | 6:4 | 7:3 (Exact Video 1 Layout!)
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            SplitRatioPreset.entries.forEach { ratio ->
                                val selected = ratio == selectedRatio
                                Surface(
                                    color = if (selected) Color(0xFF22C55E) else colors.cardSurfaceElevated,
                                    shape = RoundedCornerShape(22.dp),
                                    modifier = Modifier
                                        .border(
                                            width = 1.2.dp,
                                            color = if (selected) Color(0xFF22C55E) else colors.cardBorder,
                                            shape = RoundedCornerShape(22.dp)
                                        )
                                        .bouncyClickable(pressedScale = 0.89f) { selectedRatio = ratio }
                                ) {
                                    Text(
                                        text = ratio.label,
                                        color = if (selected) Color.White else colors.primaryText,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 9.dp)
                                    )
                                }
                            }
                        }

                        // Live Animated Split Preview Boxes (Left + Right)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .padding(vertical = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            SplitSlotBox(
                                sideLabel = "左側畫面 (${(selectedRatio.leftWeight * 100).toInt()}%)",
                                app = leftApp,
                                onClick = { pickingSide = "LEFT" },
                                modifier = Modifier
                                    .weight(animatedLeftWeight)
                                    .fillMaxHeight()
                            )
                            SplitSlotBox(
                                sideLabel = "右側畫面 (${((1f - selectedRatio.leftWeight) * 100).toInt()}%)",
                                app = rightApp,
                                onClick = { pickingSide = "RIGHT" },
                                modifier = Modifier
                                    .weight(animatedRightWeight)
                                    .fillMaxHeight()
                            )
                        }

                        // Bottom Engine Toggle + Save Button
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                SplitLaunchEngine.entries.forEach { eng ->
                                    val selected = eng == selectedEngine
                                    Surface(
                                        color = if (selected) colors.accent else colors.cardSurfaceElevated,
                                        shape = RoundedCornerShape(14.dp),
                                        modifier = Modifier
                                            .border(
                                                1.dp,
                                                if (selected) colors.accent else colors.cardBorder,
                                                RoundedCornerShape(14.dp)
                                            )
                                            .bouncyClickable(pressedScale = 0.91f) { selectedEngine = eng }
                                    ) {
                                        Text(
                                            text = eng.labelZh,
                                            color = if (selected) colors.onAccent else colors.secondaryText,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                        )
                                    }
                                }
                            }

                            val canSave = leftApp != null && rightApp != null
                            Button(
                                onClick = {
                                    val l = leftApp ?: return@Button
                                    val r = rightApp ?: return@Button
                                    val combinedName = "${l.label}/${r.label}"
                                    onSaveShortcut(
                                        SplitScreenShortcut(
                                            id = UUID.randomUUID().toString().take(8),
                                            leftPackage = l.packageName,
                                            rightPackage = r.packageName,
                                            ratio = selectedRatio,
                                            engine = selectedEngine,
                                            label = combinedName
                                        )
                                    )
                                },
                                enabled = canSave,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF22C55E),
                                    contentColor = Color.White
                                ),
                                shape = RoundedCornerShape(18.dp),
                                modifier = Modifier.height(46.dp)
                            ) {
                                Text(
                                    text = "儲存為捷徑",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 14.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SplitSlotBox(
    sideLabel: String,
    app: InstalledAppInfo?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalCoolDriveColors.current
    val bmp = remember(app?.icon) { app?.icon?.asImageBitmap() }

    Surface(
        color = colors.cardSurfaceElevated.copy(alpha = 0.85f),
        shape = RoundedCornerShape(24.dp),
        modifier = modifier
            .border(
                width = 1.5.dp,
                color = if (app != null) colors.accent else colors.cardBorder,
                shape = RoundedCornerShape(24.dp)
            )
            .clip(RoundedCornerShape(24.dp))
            .bouncyClickable(pressedScale = 0.96f, onClick = onClick)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            if (bmp != null && app != null) {
                Image(
                    bitmap = bmp,
                    contentDescription = app.label,
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(16.dp))
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = app.label,
                    color = colors.primaryText,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            } else {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFF22C55E))
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = sideLabel,
                        tint = Color.White,
                        modifier = Modifier.size(34.dp)
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = sideLabel,
                color = colors.secondaryText,
                fontSize = 12.sp
            )
        }
    }
}

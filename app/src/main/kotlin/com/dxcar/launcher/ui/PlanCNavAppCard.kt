package com.dxcar.launcher.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.OpenInFull
import androidx.compose.material.icons.rounded.Splitscreen
import androidx.compose.material.icons.rounded.TurnRight
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dxcar.launcher.comms.ActiveNavBanner
import com.dxcar.launcher.system.AppCategory
import com.dxcar.launcher.system.InstalledAppInfo
import com.dxcar.launcher.theme.LocalDxCarColors
import kotlin.math.roundToInt

/**
 * Plan C Left-Pane Card:
 * Lets the user designate ANY installed navigation App (Google Maps, NaviKing 導航王,
 * Papago, Waze, etc. — rendered with real App Icon + Name) to occupy the left 70% area
 * alongside the pure Music Album-Cover Controller on the right 30%.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PlanCNavAppCard(
    selectedNavPackage: String,
    installedApps: List<InstalledAppInfo>,
    activeNav: ActiveNavBanner?,
    primaryRatio: Float,
    onSelectNavPackage: (String) -> Unit,
    onLaunchFreeformHere: (String) -> Unit,
    onLaunchSplitPanel: (String) -> Unit,
    onLaunchFullscreen: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalDxCarColors.current
    var showAllApps by remember { mutableStateOf(false) }

    val navCandidates = remember(installedApps, showAllApps) {
        val navApps = installedApps.filter { it.category == AppCategory.NAVIGATION }
        if (showAllApps || navApps.isEmpty()) {
            (navApps + installedApps.filterNot { it.category == AppCategory.NAVIGATION }).distinctBy { it.packageName }
        } else {
            navApps
        }
    }

    val selectedApp = remember(installedApps, selectedNavPackage) {
        installedApps.firstOrNull { it.packageName == selectedNavPackage }
            ?: installedApps.firstOrNull { it.category == AppCategory.NAVIGATION }
    }
    val effectivePkg = selectedApp?.packageName ?: selectedNavPackage.ifBlank { "com.google.android.apps.maps" }
    val effectiveLabel = selectedApp?.label ?: "Google 地圖"
    val ratioPct = (primaryRatio * 100).roundToInt()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        colors.accent.copy(alpha = if (colors.isNight) 0.14f else 0.08f),
                        colors.cardSurface
                    )
                )
            )
            .padding(18.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // TOP: Active Turn-by-Turn Green Nav Banner OR Mode Header Badge
            if (activeNav != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(colors.navGreenBanner)
                        .clickable { onLaunchFullscreen(effectivePkg) }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Rounded.TurnRight, contentDescription = null, tint = Color.White, modifier = Modifier.size(32.dp))
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(activeNav.instruction, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (activeNav.etaText.isNotBlank()) {
                            Text(activeNav.etaText, color = Color(0xFFD1FAE5), fontSize = 12.sp, maxLines = 1)
                        }
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = colors.cardSurfaceElevated,
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text(
                            text = "方案 B · 左側原生導航視窗 ($ratioPct%)",
                            color = colors.accent,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }

                    Surface(
                        color = colors.cardSurfaceElevated,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.bouncyClickable { showAllApps = !showAllApps }
                    ) {
                        Text(
                            text = if (showAllApps) "僅顯示導航 App" else "顯示全部 App",
                            color = colors.secondaryText,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            // CENTER: Selected Navigation App Hero Card + Launch Buttons
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    val heroBmp = remember(selectedApp?.icon) { selectedApp?.icon?.asImageBitmap() }
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(72.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(colors.cardSurfaceElevated)
                            .border(1.5.dp, colors.accent.copy(alpha = 0.7f), RoundedCornerShape(20.dp))
                    ) {
                        if (heroBmp != null) {
                            Image(
                                bitmap = heroBmp,
                                contentDescription = effectiveLabel,
                                modifier = Modifier
                                    .size(54.dp)
                                    .clip(RoundedCornerShape(14.dp))
                            )
                        } else {
                            Icon(Icons.Rounded.Map, contentDescription = null, tint = colors.accent, modifier = Modifier.size(40.dp))
                        }
                    }

                    Spacer(Modifier.width(16.dp))

                    Column {
                        Text(
                            text = effectiveLabel,
                            color = colors.primaryText,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "已設為左側導航 App · 可自由於下方切換其他導航軟體",
                            color = colors.secondaryText,
                            fontSize = 12.sp
                        )
                    }
                }

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { onLaunchFreeformHere(effectivePkg) },
                        colors = ButtonDefaults.buttonColors(containerColor = colors.accent, contentColor = colors.onAccent),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(Icons.Rounded.Map, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("開啟左導航 ($ratioPct%) ＋ 右音樂控盤", fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = { onLaunchSplitPanel(effectivePkg) },
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(Icons.Rounded.Splitscreen, contentDescription = null, modifier = Modifier.size(17.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("原生分割並排")
                    }

                    OutlinedButton(
                        onClick = { onLaunchFullscreen(effectivePkg) },
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(Icons.Rounded.OpenInFull, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("全螢幕開啟")
                    }
                }
            }

            // BOTTOM: Horizontal Navigation App Selector (Icon + Name)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "切換左側預設導航 App（點擊立即選定）",
                    color = colors.secondaryText,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(end = 6.dp)
                ) {
                    items(navCandidates, key = { it.packageName }) { appInfo ->
                        val selected = appInfo.packageName == effectivePkg
                        val bmp = remember(appInfo.icon) { appInfo.icon.asImageBitmap() }
                        Surface(
                            color = if (selected) colors.accent.copy(alpha = 0.22f) else colors.cardSurfaceElevated,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .border(
                                    width = if (selected) 1.5.dp else 1.dp,
                                    color = if (selected) colors.accent else colors.cardBorder,
                                    shape = RoundedCornerShape(16.dp)
                                )
                                .bouncyClickable { onSelectNavPackage(appInfo.packageName) }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Image(
                                    bitmap = bmp,
                                    contentDescription = appInfo.label,
                                    modifier = Modifier
                                        .size(30.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = appInfo.label,
                                    color = if (selected) colors.accent else colors.primaryText,
                                    fontSize = 13.sp,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (selected) {
                                    Spacer(Modifier.width(6.dp))
                                    Icon(
                                        imageVector = Icons.Rounded.CheckCircle,
                                        contentDescription = null,
                                        tint = colors.accent,
                                        modifier = Modifier.size(16.dp)
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

package com.dxcar.launcher.map

import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.view.View
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Navigation
import androidx.compose.material.icons.rounded.OpenInFull
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Splitscreen
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Traffic
import androidx.compose.material.icons.rounded.TurnRight
import androidx.compose.material.icons.rounded.Update
import androidx.compose.material.icons.rounded.Work
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.dxcar.launcher.comms.ActiveNavBanner
import com.dxcar.launcher.data.MapEngineStyle
import com.dxcar.launcher.data.OfflineRegionRecord
import com.dxcar.launcher.data.OfflineUpdatePolicy
import com.dxcar.launcher.theme.LocalDxCarColors
import com.dxcar.launcher.ui.bouncyClickable
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapLibreMapOptions
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LiveMapCard(
    location: VehicleLocationState,
    activeNav: ActiveNavBanner?,
    isNight: Boolean,
    mapEngineStyle: MapEngineStyle,
    trafficEnabled: Boolean = true,
    offlineState: OfflineMapUiState = OfflineMapUiState(),
    offlinePresets: List<OfflineRegionPreset> = emptyList(),
    downloadedRegions: List<OfflineRegionRecord> = emptyList(),
    offlineUpdatePolicy: OfflineUpdatePolicy = OfflineUpdatePolicy.DAYS_30,
    isPrimary: Boolean,
    isFullscreen: Boolean,
    homeAddress: String,
    workAddress: String,
    onSelectMapStyle: (MapEngineStyle) -> Unit = {},
    onToggleTraffic: () -> Unit = {},
    onToggleStyle: () -> Unit,
    onSelectOfflinePolicy: (OfflineUpdatePolicy) -> Unit = {},
    onDownloadOfflineRegion: (String) -> Unit = {},
    onCancelOfflineDownload: () -> Unit = {},
    onDeleteOfflineRegion: (String) -> Unit = {},
    onDismissOfflineSuggestion: () -> Unit = {},
    onSwapRequested: () -> Unit,
    onToggleFullscreen: () -> Unit,
    onLaunchGoogleMapsApp: (String?) -> Unit,
    onLaunchRealMapsSplit: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val colors = LocalDxCarColors.current
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var mapLibreMap by remember { mutableStateOf<MapLibreMap?>(null) }
    var followVehicle by remember { mutableStateOf(true) }
    var currentZoom by remember { mutableDoubleStateOf(15.6) }
    var showLayerPicker by remember { mutableStateOf(false) }
    var showOfflineSheet by remember { mutableStateOf(false) }

    val googleNightPaint = remember {
        Paint().apply {
            colorFilter = ColorMatrixColorFilter(
                ColorMatrix(
                    floatArrayOf(
                        0.4822f, -1.2012f, -0.1210f, 0f, 236.2f,
                        -0.3664f, -0.3698f, -0.1238f, 0f, 248.3f,
                        -0.3919f, -1.3156f, 0.7875f, 0f, 276.6f,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
            )
        }
    }

    val mapView = remember {
        val opts = MapLibreMapOptions.createFromAttributes(context).textureMode(true)
        MapView(context, opts).apply {
            onCreate(null)
        }
    }

    LaunchedEffect(mapView, isNight, mapEngineStyle) {
        if (isNight && mapEngineStyle == MapEngineStyle.GOOGLE_TRAFFIC_TILES) {
            mapView.setLayerType(View.LAYER_TYPE_HARDWARE, googleNightPaint)
        } else {
            mapView.setLayerType(View.LAYER_TYPE_NONE, null)
        }
    }

    DisposableEffect(lifecycleOwner, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                Lifecycle.Event.ON_DESTROY -> mapView.onDestroy()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.onPause()
            mapView.onStop()
            mapView.onDestroy()
        }
    }

    LaunchedEffect(mapLibreMap, mapEngineStyle, isNight, trafficEnabled) {
        val map = mapLibreMap ?: return@LaunchedEffect
        val resolved = MapStyleFactory.resolve(mapEngineStyle, isNight, trafficEnabled)
        val builder = when (resolved) {
            is MapStyleFactory.ResolvedMapStyle.StyleUrl -> Style.Builder().fromUri(resolved.url)
            is MapStyleFactory.ResolvedMapStyle.StyleJson -> Style.Builder().fromJson(resolved.json)
        }
        runCatching { map.setStyle(builder) }
    }

    LaunchedEffect(mapLibreMap, location, followVehicle, isPrimary, mapEngineStyle) {
        val map = mapLibreMap ?: return@LaunchedEffect
        if (!followVehicle) return@LaunchedEffect
        val tilt = if (mapEngineStyle == MapEngineStyle.OPENFREEMAP_VECTOR_3D ||
            mapEngineStyle == MapEngineStyle.OPENFREEMAP_DARK
        ) 44.0 else 32.0
        val cam = CameraPosition.Builder()
            .target(LatLng(location.latitude, location.longitude))
            .zoom(currentZoom)
            .bearing(if (location.speedKmh >= 4) location.bearingDeg.toDouble() else 0.0)
            .tilt(if (isPrimary || isFullscreen) tilt else 18.0)
            .build()
        map.animateCamera(CameraUpdateFactory.newCameraPosition(cam), 700)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.cardSurface)
    ) {
        AndroidView(
            factory = {
                mapView.getMapAsync { map ->
                    map.uiSettings.apply {
                        isCompassEnabled = false
                        isLogoEnabled = false
                        isAttributionEnabled = false
                        isTiltGesturesEnabled = true
                        isRotateGesturesEnabled = true
                        isZoomGesturesEnabled = true
                        isScrollGesturesEnabled = true
                    }
                    map.addOnCameraMoveStartedListener { reason ->
                        if (reason == MapLibreMap.OnCameraMoveStartedListener.REASON_API_GESTURE) {
                            followVehicle = false
                        }
                    }
                    map.addOnCameraIdleListener {
                        currentZoom = map.cameraPosition.zoom
                    }
                    val initCam = CameraPosition.Builder()
                        .target(LatLng(location.latitude, location.longitude))
                        .zoom(currentZoom)
                        .tilt(30.0)
                        .build()
                    map.cameraPosition = initCam
                    mapLibreMap = map
                }
                mapView
            },
            modifier = Modifier.fillMaxSize()
        )

        AnimatedVisibility(
            visible = followVehicle,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            VehiclePuckOverlay(
                accentColor = colors.accent,
                bearingDeg = if (location.speedKmh >= 4) 0f else location.bearingDeg
            )
        }

        if (!isPrimary && !isFullscreen) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable { onSwapRequested() }
            )
        }

        // TOP HUD: Google Maps Active Nav Banner OR Plan A Multi-Layer + Offline Map Bar
        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (activeNav != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(colors.navGreenBanner)
                        .clickable { onLaunchGoogleMapsApp(null) }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.TurnRight,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(34.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = activeNav.instruction,
                            color = Color.White,
                            fontSize = if (isPrimary) 18.sp else 14.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (activeNav.etaText.isNotBlank()) {
                            Text(
                                text = activeNav.etaText,
                                color = Color(0xFFD1FAE5),
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            } else if (isPrimary || isFullscreen) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            color = colors.glassOverlay,
                            shape = RoundedCornerShape(24.dp),
                            shadowElevation = 4.dp,
                            modifier = Modifier
                                .weight(1f, fill = false)
                                .border(1.2.dp, colors.cardBorder, RoundedCornerShape(24.dp))
                                .bouncyClickable { onLaunchGoogleMapsApp(null) }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Search,
                                    contentDescription = "搜尋目的地",
                                    tint = colors.accent,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = "Google 地圖導航搜尋…",
                                    color = colors.primaryText,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Spacer(Modifier.width(8.dp))

                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            MapChipButton(
                                icon = Icons.Rounded.CloudDownload,
                                label = if (offlineState.downloadingRegionId != null) {
                                    "下載 ${(offlineState.progress * 100).toInt()}%"
                                } else {
                                    "離線地圖"
                                },
                                highlight = showOfflineSheet || offlineState.updateSuggestion != null || offlineState.downloadingRegionId != null,
                                onClick = { showOfflineSheet = !showOfflineSheet }
                            )
                            MapChipButton(
                                icon = Icons.Rounded.Traffic,
                                label = if (trafficEnabled) "路況 開" else "路況 關",
                                highlight = trafficEnabled &&
                                    (mapEngineStyle == MapEngineStyle.GOOGLE_TRAFFIC_TILES ||
                                        mapEngineStyle == MapEngineStyle.GOOGLE_SATELLITE_HYBRID),
                                onClick = onToggleTraffic
                            )
                            MapChipButton(
                                icon = Icons.Rounded.Home,
                                label = "回家",
                                onClick = { onLaunchGoogleMapsApp(homeAddress.ifBlank { "家" }) }
                            )
                            if (workAddress.isNotBlank()) {
                                MapChipButton(
                                    icon = Icons.Rounded.Work,
                                    label = "公司",
                                    onClick = { onLaunchGoogleMapsApp(workAddress) }
                                )
                            }
                            MapChipButton(
                                icon = Icons.Rounded.Splitscreen,
                                label = "分割G地圖",
                                onClick = onLaunchRealMapsSplit
                            )
                            MapChipButton(
                                icon = Icons.Rounded.Navigation,
                                label = "G地圖",
                                highlight = true,
                                onClick = { onLaunchGoogleMapsApp(null) }
                            )
                        }
                    }

                    // Quick Layer Pills (Plan A)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        MapEngineStyle.entries.forEach { st ->
                            val active = mapEngineStyle == st
                            Surface(
                                color = if (active) colors.accent else colors.glassOverlay.copy(alpha = 0.85f),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .border(1.dp, if (active) colors.accent else colors.cardBorder, RoundedCornerShape(14.dp))
                                    .bouncyClickable { onSelectMapStyle(st) }
                            ) {
                                Text(
                                    text = when (st) {
                                        MapEngineStyle.GOOGLE_TRAFFIC_TILES -> "G標準"
                                        MapEngineStyle.GOOGLE_SATELLITE_HYBRID -> "G衛星"
                                        MapEngineStyle.OPENFREEMAP_VECTOR_3D -> "3D向量"
                                        MapEngineStyle.OPENFREEMAP_DARK -> "深色向量"
                                    },
                                    color = if (active) colors.onAccent else colors.primaryText,
                                    fontSize = 11.sp,
                                    fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                )
                            }
                        }
                    }

                    // Smart Offline Map Update Recommendation Pill (user chooses whether to update!)
                    val suggestion = offlineState.updateSuggestion
                    if (suggestion != null && !showOfflineSheet) {
                        Surface(
                            color = colors.glassOverlay,
                            shape = RoundedCornerShape(16.dp),
                            shadowElevation = 6.dp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.2.dp, colors.accent, RoundedCornerShape(16.dp))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f).padding(end = 8.dp)
                                ) {
                                    Icon(Icons.Rounded.Update, contentDescription = null, tint = colors.accent, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = suggestion.reasonZh,
                                        color = colors.primaryText,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Button(
                                        onClick = { onDownloadOfflineRegion(suggestion.regionId) },
                                        colors = ButtonDefaults.buttonColors(containerColor = colors.accent, contentColor = colors.onAccent),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Text("立即更新", fontSize = 11.sp)
                                    }
                                    OutlinedButton(
                                        onClick = onDismissOfflineSuggestion,
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Text("稍後", fontSize = 11.sp)
                                    }
                                }
                            }
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
                        color = colors.glassOverlay,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.border(1.dp, colors.cardBorder, RoundedCornerShape(16.dp))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Rounded.Navigation, contentDescription = null, tint = colors.accent, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("即時地圖", color = colors.primaryText, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    Surface(
                        color = colors.accent,
                        shape = CircleShape,
                        modifier = Modifier
                            .size(34.dp)
                            .clickable { onSwapRequested() }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.SwapHoriz, contentDescription = "切換為主畫面", tint = colors.onAccent, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }

        // BOTTOM LEFT: Speedometer Pill
        Surface(
            color = colors.glassOverlay,
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(12.dp)
                .border(1.dp, colors.cardBorder, RoundedCornerShape(18.dp))
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                Text(
                    text = "${location.speedKmh}",
                    color = colors.primaryText,
                    fontSize = if (isPrimary) 24.sp else 18.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Spacer(Modifier.width(4.dp))
                Column(modifier = Modifier.padding(bottom = 2.dp)) {
                    Text("km/h", color = colors.secondaryText, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    if (isPrimary) {
                        Text(
                            text = if (location.hasRealFix) "GPS 即時" else "定位中",
                            color = if (location.hasRealFix) colors.statusConnected else colors.secondaryText,
                            fontSize = 9.sp
                        )
                    }
                }
            }
        }

        // BOTTOM RIGHT: Map Controls
        if (isPrimary || isFullscreen) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.End
            ) {
                Surface(
                    color = colors.glassOverlay,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.border(1.dp, colors.cardBorder, RoundedCornerShape(16.dp))
                ) {
                    Column {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(40.dp)
                                .bouncyClickable(pressedScale = 0.85f) {
                                    currentZoom = (currentZoom + 1.0).coerceAtMost(19.5)
                                    mapLibreMap?.animateCamera(CameraUpdateFactory.zoomTo(currentZoom), 300)
                                }
                        ) {
                            Icon(Icons.Rounded.Add, contentDescription = "放大", tint = colors.primaryText)
                        }
                        Box(
                            Modifier
                                .width(24.dp)
                                .height(1.dp)
                                .align(Alignment.CenterHorizontally)
                                .background(colors.cardBorder)
                        )
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(40.dp)
                                .bouncyClickable(pressedScale = 0.85f) {
                                    currentZoom = (currentZoom - 1.0).coerceAtLeast(5.0)
                                    mapLibreMap?.animateCamera(CameraUpdateFactory.zoomTo(currentZoom), 300)
                                }
                        ) {
                            Icon(Icons.Rounded.Remove, contentDescription = "縮小", tint = colors.primaryText)
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SmallGlassCircleButton(
                        icon = Icons.Rounded.Layers,
                        contentDescription = "切換底圖圖層與即時路況",
                        active = showLayerPicker || mapEngineStyle != MapEngineStyle.GOOGLE_TRAFFIC_TILES,
                        onClick = { showLayerPicker = !showLayerPicker }
                    )
                    SmallGlassCircleButton(
                        icon = Icons.Rounded.MyLocation,
                        contentDescription = "置中車位",
                        active = followVehicle,
                        onClick = { followVehicle = true }
                    )
                    SmallGlassCircleButton(
                        icon = Icons.Rounded.OpenInFull,
                        contentDescription = if (isFullscreen) "返回分割" else "全螢幕地圖",
                        active = isFullscreen,
                        onClick = onToggleFullscreen
                    )
                }
            }
        }

        // LAYER PICKER POPUP
        AnimatedVisibility(
            visible = showLayerPicker && (isPrimary || isFullscreen),
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 12.dp, bottom = 68.dp)
        ) {
            Surface(
                color = colors.glassOverlay,
                shape = RoundedCornerShape(20.dp),
                shadowElevation = 10.dp,
                modifier = Modifier
                    .width(290.dp)
                    .border(1.dp, colors.cardBorder, RoundedCornerShape(20.dp))
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("地圖圖層與路況", color = colors.primaryText, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        IconButton(onClick = { showLayerPicker = false }, modifier = Modifier.size(26.dp)) {
                            Icon(Icons.Rounded.Close, contentDescription = "關閉", tint = colors.secondaryText, modifier = Modifier.size(18.dp))
                        }
                    }

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        MapEngineStyle.entries.forEach { styleItem ->
                            val selected = mapEngineStyle == styleItem
                            Surface(
                                color = if (selected) colors.accent else colors.cardSurfaceElevated,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .border(1.dp, if (selected) colors.accent else colors.cardBorder, RoundedCornerShape(12.dp))
                                    .bouncyClickable {
                                        onSelectMapStyle(styleItem)
                                        showLayerPicker = false
                                    }
                            ) {
                                Text(
                                    text = styleItem.labelZh,
                                    color = if (selected) colors.onAccent else colors.primaryText,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(colors.cardSurfaceElevated)
                            .clickable { onToggleTraffic() }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("疊加 Google 即時路況", color = colors.primaryText, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            Text("適用於標準與衛星混合圖層", color = colors.secondaryText, fontSize = 10.sp)
                        }
                        Switch(
                            checked = trafficEnabled,
                            onCheckedChange = { onToggleTraffic() },
                            colors = SwitchDefaults.colors(checkedThumbColor = colors.onAccent, checkedTrackColor = colors.accent)
                        )
                    }
                }
            }
        }

        // PLAN A OFFLINE MAP DOWNLOAD & UPDATE POLICY SHEET
        AnimatedVisibility(
            visible = showOfflineSheet && (isPrimary || isFullscreen),
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 60.dp, start = 14.dp, end = 14.dp, bottom = 14.dp)
        ) {
            val dateFmt = remember { SimpleDateFormat("yyyy/MM/dd", Locale.TAIWAN) }
            val byId = remember(downloadedRegions) { downloadedRegions.associateBy { it.regionId } }

            Surface(
                color = colors.cardSurface.copy(alpha = 0.97f),
                shape = RoundedCornerShape(22.dp),
                shadowElevation = 18.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .border(1.2.dp, colors.cardBorder, RoundedCornerShape(22.dp))
            ) {
                Column(
                    modifier = Modifier
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "方案 A · 離線地圖下載與自動更新判斷",
                                color = colors.primaryText,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "無網路／地下室／山區自動使用離線快取圖磚；可自選更新提醒週期",
                                color = colors.secondaryText,
                                fontSize = 11.sp
                            )
                        }
                        IconButton(onClick = { showOfflineSheet = false }) {
                            Icon(Icons.Rounded.Close, contentDescription = "關閉", tint = colors.primaryText)
                        }
                    }

                    // User-selectable update reminder policy
                    Text("何時提醒更新離線地圖：", color = colors.primaryText, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        OfflineUpdatePolicy.entries.forEach { pol ->
                            val selected = offlineUpdatePolicy == pol
                            Surface(
                                color = if (selected) colors.accent else colors.cardSurfaceElevated,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .border(1.dp, if (selected) colors.accent else colors.cardBorder, RoundedCornerShape(12.dp))
                                    .clickable { onSelectOfflinePolicy(pol) }
                            ) {
                                Text(
                                    text = pol.labelZh,
                                    color = if (selected) colors.onAccent else colors.primaryText,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }

                    if (offlineState.statusMessage.isNotBlank()) {
                        Text(
                            text = offlineState.statusMessage,
                            color = colors.accent,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    if (offlineState.downloadingRegionId != null) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            LinearProgressIndicator(
                                progress = { offlineState.progress },
                                color = colors.accent,
                                trackColor = colors.cardBorder,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp))
                            )
                            OutlinedButton(
                                onClick = onCancelOfflineDownload,
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("取消", fontSize = 11.sp)
                            }
                        }
                    }

                    offlinePresets.forEach { preset ->
                        val rec = byId[preset.id]
                        val isDownloadingThis = offlineState.downloadingRegionId == preset.id
                        Surface(
                            color = colors.cardSurfaceElevated,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(
                                    1.dp,
                                    if (rec != null) colors.statusConnected.copy(alpha = 0.55f) else colors.cardBorder,
                                    RoundedCornerShape(14.dp)
                                )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                    Text(
                                        text = preset.titleZh,
                                        color = colors.primaryText,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = if (rec != null) {
                                            "✓ 已下載 (${dateFmt.format(Date(rec.downloadedAtMs))} · 約 %.1f MB)".format(rec.approxSizeMb)
                                        } else {
                                            preset.subtitleZh
                                        },
                                        color = if (rec != null) colors.statusConnected else colors.secondaryText,
                                        fontSize = 11.sp
                                    )
                                }

                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Button(
                                        onClick = { onDownloadOfflineRegion(preset.id) },
                                        enabled = offlineState.downloadingRegionId == null,
                                        colors = ButtonDefaults.buttonColors(containerColor = colors.accent, contentColor = colors.onAccent),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Text(
                                            text = when {
                                                isDownloadingThis -> "下載中…"
                                                rec != null -> "更新圖資"
                                                else -> "下載離線"
                                            },
                                            fontSize = 12.sp
                                        )
                                    }
                                    if (rec != null) {
                                        IconButton(
                                            onClick = { onDeleteOfflineRegion(preset.id) },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(Icons.Rounded.DeleteOutline, contentDescription = "刪除離線包", tint = colors.secondaryText, modifier = Modifier.size(18.dp))
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
private fun VehiclePuckOverlay(
    accentColor: Color,
    bearingDeg: Float
) {
    Canvas(modifier = Modifier.size(48.dp)) {
        val c = Offset(size.width / 2f, size.height / 2f)
        drawCircle(color = accentColor.copy(alpha = 0.22f), radius = size.minDimension * 0.48f, center = c)
        drawCircle(color = Color.White, radius = size.minDimension * 0.30f, center = c)
        rotate(degrees = bearingDeg, pivot = c) {
            val r = size.minDimension * 0.20f
            val path = Path().apply {
                moveTo(c.x, c.y - r)
                lineTo(c.x + r * 0.78f, c.y + r * 0.85f)
                lineTo(c.x, c.y + r * 0.42f)
                lineTo(c.x - r * 0.78f, c.y + r * 0.85f)
                close()
            }
            drawPath(path = path, color = accentColor)
        }
    }
}

@Composable
private fun MapChipButton(
    icon: ImageVector,
    label: String,
    highlight: Boolean = false,
    onClick: () -> Unit
) {
    val colors = LocalDxCarColors.current
    Surface(
        color = if (highlight) colors.accent else colors.glassOverlay,
        shape = RoundedCornerShape(20.dp),
        shadowElevation = 3.dp,
        modifier = Modifier
            .border(1.dp, if (highlight) colors.accent else colors.cardBorder, RoundedCornerShape(20.dp))
            .bouncyClickable(pressedScale = 0.90f, onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (highlight) colors.onAccent else colors.primaryText,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = label,
                color = if (highlight) colors.onAccent else colors.primaryText,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun SmallGlassCircleButton(
    icon: ImageVector,
    contentDescription: String,
    active: Boolean,
    onClick: () -> Unit
) {
    val colors = LocalDxCarColors.current
    Surface(
        color = if (active) colors.accent else colors.glassOverlay,
        shape = CircleShape,
        shadowElevation = 3.dp,
        modifier = Modifier
            .size(40.dp)
            .border(1.dp, if (active) colors.accent else colors.cardBorder, CircleShape)
            .bouncyClickable(pressedScale = 0.85f, onClick = onClick)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = if (active) colors.onAccent else colors.primaryText,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

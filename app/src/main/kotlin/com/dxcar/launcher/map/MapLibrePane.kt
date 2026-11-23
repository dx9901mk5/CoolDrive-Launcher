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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Navigation
import androidx.compose.material.icons.rounded.OpenInFull
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Splitscreen
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Traffic
import androidx.compose.material.icons.rounded.TurnRight
import androidx.compose.material.icons.rounded.Work
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import com.dxcar.launcher.ui.bouncyClickable
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
import com.dxcar.launcher.data.HomeExperimentMode
import com.dxcar.launcher.data.MapEngineStyle
import com.dxcar.launcher.theme.LocalDxCarColors
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapLibreMapOptions
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LiveMapCard(
    location: VehicleLocationState,
    activeNav: ActiveNavBanner?,
    isNight: Boolean,
    mapEngineStyle: MapEngineStyle,
    trafficEnabled: Boolean = true,
    experimentMode: HomeExperimentMode = HomeExperimentMode.PLAN_A_MULTI_LAYER,
    builtInNavState: BuiltInNavState = BuiltInNavState(),
    isPrimary: Boolean,
    isFullscreen: Boolean,
    homeAddress: String,
    workAddress: String,
    onSelectMapStyle: (MapEngineStyle) -> Unit = {},
    onToggleTraffic: () -> Unit = {},
    onToggleStyle: () -> Unit,
    onSearchBuiltInPlaces: (String) -> Unit = {},
    onStartBuiltInNav: (NavSearchResult) -> Unit = {},
    onStopBuiltInNav: () -> Unit = {},
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
    var showBuiltInSearchPanel by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    // GPU Hue-Preserving Night Mode Paint for authentic Google Maps HD Standard tiles
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

    // Use TextureView mode so hardware ColorMatrixColorFilter applies cleanly at 60fps
    val mapView = remember {
        val opts = MapLibreMapOptions.createFromAttributes(context).textureMode(true)
        MapView(context, opts).apply {
            onCreate(null)
        }
    }

    // Toggle GPU Night Mode filter only for Google Standard HD tiles (not Satellite Hybrid)
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

    // Apply Day/Night, Traffic, or Engine Style changes seamlessly + re-attach built-in Nav route if active
    LaunchedEffect(mapLibreMap, mapEngineStyle, isNight, trafficEnabled) {
        val map = mapLibreMap ?: return@LaunchedEffect
        val resolved = MapStyleFactory.resolve(mapEngineStyle, isNight, trafficEnabled)
        val builder = when (resolved) {
            is MapStyleFactory.ResolvedMapStyle.StyleUrl -> Style.Builder().fromUri(resolved.url)
            is MapStyleFactory.ResolvedMapStyle.StyleJson -> Style.Builder().fromJson(resolved.json)
        }
        runCatching {
            map.setStyle(builder) { style ->
                applyRouteOverlayToStyle(style, builtInNavState)
            }
        }
    }

    // Update Plan B OSRM blue route polyline whenever builtInNavState route changes
    LaunchedEffect(mapLibreMap, builtInNavState.isNavigating, builtInNavState.routePoints) {
        val map = mapLibreMap ?: return@LaunchedEffect
        map.getStyle { style ->
            applyRouteOverlayToStyle(style, builtInNavState)
        }
    }

    // Smooth GPS follow camera
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
        // Live MapLibre OpenGL Canvas
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

        // Centered Vehicle Navigation Chevron when following GPS
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

        // If this card is currently Secondary (small side), tapping an unobtrusive overlay badge swaps it to Primary
        if (!isPrimary && !isFullscreen) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable { onSwapRequested() }
            )
        }

        // TOP HUD: Plan B Built-In Navigation Banner OR Google Maps Green Banner OR Search / Quick Nav Bar
        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (experimentMode == HomeExperimentMode.PLAN_B_BUILTIN_NAV && builtInNavState.isNavigating) {
                val remDistText = if (builtInNavState.remainingDistanceMeters >= 1000) {
                    "%.1f km".format(builtInNavState.remainingDistanceMeters / 1000f)
                } else {
                    "${builtInNavState.remainingDistanceMeters} m"
                }
                val remMin = (builtInNavState.remainingDurationSeconds / 60).coerceAtLeast(1)
                val nextTurnText = if (builtInNavState.distanceToNextTurnMeters >= 1000) {
                    "%.1f km".format(builtInNavState.distanceToNextTurnMeters / 1000f)
                } else {
                    "${builtInNavState.distanceToNextTurnMeters} m"
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(colors.navGreenBanner)
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.TurnRight,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "$nextTurnText · ${builtInNavState.currentInstructionZh}",
                            color = Color.White,
                            fontSize = if (isPrimary) 17.sp else 13.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "前往 ${builtInNavState.destinationName} · 剩 $remDistText · 約 $remMin 分鐘",
                            color = Color(0xFFD1FAE5),
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(onClick = onStopBuiltInNav) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "結束導航",
                            tint = Color.White
                        )
                    }
                }
            } else if (activeNav != null) {
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
                val isPlanB = experimentMode == HomeExperimentMode.PLAN_B_BUILTIN_NAV
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
                            .border(1.dp, colors.cardBorder, RoundedCornerShape(24.dp))
                            .bouncyClickable {
                                if (isPlanB) {
                                    showBuiltInSearchPanel = !showBuiltInSearchPanel
                                } else {
                                    onLaunchGoogleMapsApp(null)
                                }
                            }
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
                                text = if (isPlanB) "搜尋目的地（首頁內建導航）…" else "Google 地圖導航搜尋…",
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
                            onClick = {
                                onLaunchGoogleMapsApp(homeAddress.ifBlank { "家" })
                            }
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
            } else {
                // Compact Secondary Card Header with Swap badge
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
                            Icon(
                                imageVector = Icons.Rounded.Navigation,
                                contentDescription = null,
                                tint = colors.accent,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = "即時地圖",
                                color = colors.primaryText,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
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
                            Icon(
                                imageVector = Icons.Rounded.SwapHoriz,
                                contentDescription = "切換為主畫面",
                                tint = colors.onAccent,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }

        // BOTTOM LEFT: Speedometer Pill (km/h) & GPS status
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
                    Text(
                        text = "km/h",
                        color = colors.secondaryText,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
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

        // BOTTOM RIGHT: Map Controls (Zoom +/-, Recenter, Style Switch, Expand Fullscreen)
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

        // LAYER PICKER POPUP (Plan A / B Multi-Layer + Traffic Toggle)
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
                        Text(
                            text = "地圖圖層與路況",
                            color = colors.primaryText,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
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

        // PLAN B BUILT-IN PLACE SEARCH PANEL (Photon + OSRM Route Launch)
        AnimatedVisibility(
            visible = showBuiltInSearchPanel && experimentMode == HomeExperimentMode.PLAN_B_BUILTIN_NAV,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(top = 64.dp, start = 12.dp, end = 12.dp)
        ) {
            Surface(
                color = colors.glassOverlay,
                shape = RoundedCornerShape(20.dp),
                shadowElevation = 12.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, colors.cardBorder, RoundedCornerShape(20.dp))
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("輸入地點名稱（如：台北101、中正紀念堂）", fontSize = 13.sp) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = colors.primaryText,
                                unfocusedTextColor = colors.primaryText,
                                focusedBorderColor = colors.accent,
                                unfocusedBorderColor = colors.cardBorder
                            ),
                            modifier = Modifier.weight(1f)
                        )
                        Button(
                            onClick = { onSearchBuiltInPlaces(searchQuery) },
                            colors = ButtonDefaults.buttonColors(containerColor = colors.accent, contentColor = colors.onAccent),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            if (builtInNavState.isSearching || builtInNavState.isRouting) {
                                CircularProgressIndicator(color = colors.onAccent, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                            } else {
                                Text("搜尋")
                            }
                        }
                        IconButton(onClick = { showBuiltInSearchPanel = false }) {
                            Icon(Icons.Rounded.Close, contentDescription = "關閉", tint = colors.secondaryText)
                        }
                    }

                    if (builtInNavState.errorMessage.isNotBlank()) {
                        Text(builtInNavState.errorMessage, color = Color(0xFFEF4444), fontSize = 12.sp)
                    }

                    if (builtInNavState.searchResults.isNotEmpty()) {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 220.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(builtInNavState.searchResults) { place ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(colors.cardSurfaceElevated)
                                        .clickable {
                                            showBuiltInSearchPanel = false
                                            onStartBuiltInNav(place)
                                        }
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Rounded.Place, contentDescription = null, tint = colors.accent, modifier = Modifier.size(20.dp))
                                    Spacer(Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(place.name, color = colors.primaryText, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(place.subtitle, color = colors.secondaryText, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                    Text("導航", color = colors.accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun applyRouteOverlayToStyle(style: Style, navState: BuiltInNavState) {
    val srcId = "dxcar-nav-route-src"
    val layerId = "dxcar-nav-route-layer"
    val destSrcId = "dxcar-nav-dest-src"
    val destLayerId = "dxcar-nav-dest-layer"

    if (!navState.isNavigating || navState.routePoints.size < 2) {
        runCatching { style.removeLayer(layerId) }
        runCatching { style.removeSource(srcId) }
        runCatching { style.removeLayer(destLayerId) }
        runCatching { style.removeSource(destSrcId) }
        return
    }

    val coordsJson = navState.routePoints.joinToString(",") { "[${it.longitude},${it.latitude}]" }
    val lineGeoJson = """{"type":"FeatureCollection","features":[{"type":"Feature","geometry":{"type":"LineString","coordinates":[$coordsJson]}}]}"""
    val destGeoJson = """{"type":"FeatureCollection","features":[{"type":"Feature","geometry":{"type":"Point","coordinates":[${navState.destinationLon},${navState.destinationLat}]}}]}"""

    runCatching {
        val existing = style.getSourceAs<GeoJsonSource>(srcId)
        if (existing != null) {
            existing.setGeoJson(lineGeoJson)
        } else {
            style.addSource(GeoJsonSource(srcId, lineGeoJson))
            style.addLayer(
                LineLayer(layerId, srcId).withProperties(
                    PropertyFactory.lineColor(android.graphics.Color.parseColor("#2563EB")),
                    PropertyFactory.lineWidth(8.0f),
                    PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                    PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND)
                )
            )
        }

        val existingDest = style.getSourceAs<GeoJsonSource>(destSrcId)
        if (existingDest != null) {
            existingDest.setGeoJson(destGeoJson)
        } else {
            style.addSource(GeoJsonSource(destSrcId, destGeoJson))
            style.addLayer(
                CircleLayer(destLayerId, destSrcId).withProperties(
                    PropertyFactory.circleColor(android.graphics.Color.parseColor("#EF4444")),
                    PropertyFactory.circleRadius(8.0f),
                    PropertyFactory.circleStrokeColor(android.graphics.Color.WHITE),
                    PropertyFactory.circleStrokeWidth(2.5f)
                )
            )
        }
    }
}

@Composable
private fun MapChipButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    highlight: Boolean = false,
    onClick: () -> Unit
) {
    val colors = LocalDxCarColors.current
    Surface(
        color = if (highlight) colors.accent else colors.glassOverlay,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier
            .border(
                width = 1.dp,
                color = if (highlight) colors.accent else colors.cardBorder,
                shape = RoundedCornerShape(20.dp)
            )
            .bouncyClickable(pressedScale = 0.90f, onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
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
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun SmallGlassCircleButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    active: Boolean,
    onClick: () -> Unit
) {
    val colors = LocalDxCarColors.current
    Surface(
        color = if (active) colors.accent else colors.glassOverlay,
        shape = CircleShape,
        modifier = Modifier
            .size(42.dp)
            .border(1.dp, if (active) colors.accent else colors.cardBorder, CircleShape)
            .bouncyClickable(pressedScale = 0.86f, onClick = onClick)
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

@Composable
private fun VehiclePuckOverlay(
    accentColor: Color,
    bearingDeg: Float
) {
    Canvas(modifier = Modifier.size(44.dp)) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val navBlue = Color(0xFF1A73E8)
        // Outer translucent blue halo ring
        drawCircle(
            color = navBlue.copy(alpha = 0.28f),
            radius = size.minDimension * 0.48f,
            center = Offset(cx, cy)
        )
        // Crisp white base circle
        drawCircle(
            color = Color.White,
            radius = size.minDimension * 0.31f,
            center = Offset(cx, cy)
        )
        // Bold directional Google Blue chevron
        rotate(degrees = bearingDeg, pivot = Offset(cx, cy)) {
            val path = Path().apply {
                moveTo(cx, cy - size.minDimension * 0.22f)
                lineTo(cx + size.minDimension * 0.16f, cy + size.minDimension * 0.15f)
                lineTo(cx, cy + size.minDimension * 0.06f)
                lineTo(cx - size.minDimension * 0.16f, cy + size.minDimension * 0.15f)
                close()
            }
            drawPath(path, color = navBlue)
        }
    }
}

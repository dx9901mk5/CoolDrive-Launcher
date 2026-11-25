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
import androidx.compose.material.icons.rounded.Mic
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import com.dxcar.launcher.i18n.DxCarI18n.str
import com.dxcar.launcher.theme.LocalDxCarColors
import com.dxcar.launcher.ui.bouncyClickable
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LiveMapCard(
    location: VehicleLocationState,
    activeNav: ActiveNavBanner?,
    builtInNavState: BuiltInNavState = BuiltInNavState(),
    isNight: Boolean,
    mapEngineStyle: MapEngineStyle,
    trafficEnabled: Boolean = true,
    offlineState: OfflineMapUiState = OfflineMapUiState(),
    offlinePresets: List<OfflineRegionPreset> = emptyList(),
    downloadedRegions: List<OfflineRegionRecord> = emptyList(),
    offlineUpdatePolicy: OfflineUpdatePolicy = OfflineUpdatePolicy.DAYS_30,
    isPrimary: Boolean,
    isFullscreen: Boolean,
    showSwapButton: Boolean = true,
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
    onVoiceNavClick: () -> Unit = {},
    onSearchBuiltInPlace: (String) -> Unit = {},
    onStartBuiltInNavResult: (NavSearchResult) -> Unit = {},
    onQuickBuiltInNavigateQuery: (String) -> Unit = {},
    onStopBuiltInNav: () -> Unit = {},
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
    var showBuiltInSearchBox by remember { mutableStateOf(false) }
    var builtInQueryText by remember { mutableStateOf("") }

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
        runCatching {
            map.setStyle(builder) { style ->
                applyRouteOverlayToStyle(style, builtInNavState)
            }
        }
    }

    LaunchedEffect(mapLibreMap, builtInNavState.isNavigating, builtInNavState.routePoints) {
        val map = mapLibreMap ?: return@LaunchedEffect
        if (builtInNavState.isNavigating) {
            followVehicle = true
            if (currentZoom < 16.0) currentZoom = 16.3
        }
        map.getStyle { style ->
            applyRouteOverlayToStyle(style, builtInNavState)
        }
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

        if (!isPrimary && !isFullscreen && showSwapButton) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable { onSwapRequested() }
            )
        }

        // TOP HUD: Built-In Plan A Turn-by-Turn Navigation Banner OR Google Maps Banner OR Plan A Search & Gemini Bar
        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (builtInNavState.isNavigating || builtInNavState.isSearching || builtInNavState.isRouting) {
                Surface(
                    color = colors.navGreenBanner,
                    shape = RoundedCornerShape(18.dp),
                    shadowElevation = 10.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (builtInNavState.isSearching || builtInNavState.isRouting) {
                            CircularProgressIndicator(
                                color = Color.White,
                                strokeWidth = 3.dp,
                                modifier = Modifier.size(28.dp)
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Rounded.TurnRight,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(34.dp)
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            val distTxt = if (builtInNavState.distanceToNextTurnMeters >= 1000) {
                                "%.1f km".format(builtInNavState.distanceToNextTurnMeters / 1000f)
                            } else {
                                "${builtInNavState.distanceToNextTurnMeters} m"
                            }
                            Text(
                                text = when {
                                    builtInNavState.isSearching -> str("✨ 正在於內建地圖搜尋目的地…", "✨ Searching destination on built-in map…", "✨ 内蔵マップで目的地を検索中…")
                                    builtInNavState.isRouting -> str("🗺️ 正在規劃內建地圖導航藍線…", "🗺️ Calculating route on built-in map…", "🗺️ 内蔵マップでルートを計算中…")
                                    else -> "$distTxt · ${builtInNavState.currentInstructionZh}"
                                },
                                color = Color.White,
                                fontSize = if (isPrimary) 18.sp else 14.sp,
                                fontWeight = FontWeight.ExtraBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (builtInNavState.isNavigating) {
                                val remKm = "%.1f km".format(builtInNavState.remainingDistanceMeters / 1000f)
                                val remMin = (builtInNavState.remainingDurationSeconds / 60).coerceAtLeast(1)
                                Text(
                                    text = "目的地：${builtInNavState.destinationName} · 剩餘 $remKm · 約 $remMin 分鐘",
                                    color = Color(0xFFD1FAE5),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        IconButton(onClick = onVoiceNavClick) {
                            Icon(Icons.Rounded.Mic, contentDescription = "語音換目的地", tint = Color.White)
                        }
                        IconButton(onClick = onStopBuiltInNav) {
                            Icon(Icons.Rounded.Close, contentDescription = "結束導航", tint = Color.White)
                        }
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
                                .bouncyClickable { showBuiltInSearchBox = !showBuiltInSearchBox }
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
                                    text = "內建地圖導航搜尋…",
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
                                icon = Icons.Rounded.Mic,
                                label = "✨ Gemini 語音導航",
                                highlight = true,
                                onClick = onVoiceNavClick
                            )
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
                            if (homeAddress.isNotBlank()) {
                                MapChipButton(
                                    icon = Icons.Rounded.Home,
                                    label = "回家",
                                    onClick = { onQuickBuiltInNavigateQuery(homeAddress) }
                                )
                            }
                            if (workAddress.isNotBlank()) {
                                MapChipButton(
                                    icon = Icons.Rounded.Work,
                                    label = "公司",
                                    onClick = { onQuickBuiltInNavigateQuery(workAddress) }
                                )
                            }
                            MapChipButton(
                                icon = Icons.Rounded.Navigation,
                                label = "G地圖",
                                highlight = false,
                                onClick = { onLaunchGoogleMapsApp(null) }
                            )
                        }
                    }

                    // Built-in Plan A Destination Search Box (routes directly on Launcher's own Map!)
                    if (showBuiltInSearchBox) {
                        Surface(
                            color = colors.cardSurface.copy(alpha = 0.96f),
                            shape = RoundedCornerShape(18.dp),
                            shadowElevation = 12.dp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.2.dp, colors.accent, RoundedCornerShape(18.dp))
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedTextField(
                                        value = builtInQueryText,
                                        onValueChange = { builtInQueryText = it },
                                        singleLine = true,
                                        placeholder = { Text(str("輸入目的地（直接於本啟動器內建地圖導航）…", "Enter destination (navigate on built-in map)…", "目的地を入力（内蔵マップで案内）…"), fontSize = 12.sp) },
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.weight(1f)
                                    )
                                    Button(
                                        onClick = { onSearchBuiltInPlace(builtInQueryText) },
                                        colors = ButtonDefaults.buttonColors(containerColor = colors.accent, contentColor = colors.onAccent),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Text(str("搜尋", "Search", "検索"), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                    IconButton(onClick = onVoiceNavClick) {
                                        Icon(Icons.Rounded.Mic, contentDescription = "語音輸入", tint = Color(0xFFA78BFA))
                                    }
                                    IconButton(onClick = { showBuiltInSearchBox = false }) {
                                        Icon(Icons.Rounded.Close, contentDescription = "關閉", tint = colors.secondaryText)
                                    }
                                }
                                if (builtInNavState.errorMessage.isNotBlank()) {
                                    Text(builtInNavState.errorMessage, color = Color(0xFFF59E0B), fontSize = 12.sp)
                                }
                                builtInNavState.searchResults.take(4).forEach { res ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(colors.cardSurfaceElevated)
                                            .clickable {
                                                showBuiltInSearchBox = false
                                                onStartBuiltInNavResult(res)
                                            }
                                            .padding(horizontal = 12.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(res.name, color = colors.primaryText, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            Text(res.subtitle, color = colors.secondaryText, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        }
                                        Text(str("開始導航 →", "Start Nav →", "案内開始 →"), color = colors.accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
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
            if (showSwapButton) {
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
                        active = showLayerPicker || trafficEnabled || mapEngineStyle != MapEngineStyle.GOOGLE_TRAFFIC_TILES,
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
                        val pctInt = (offlineState.progress * 100f).toInt().coerceIn(0, 100)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Surface(
                                color = colors.accent.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text(
                                    text = "$pctInt%",
                                    color = colors.accent,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
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

                    // Smart GPS Current-Region & Entire-Country One-Tap Batch Download Buttons!
                    val detectedLoc = remember(location.latitude, location.longitude) {
                        OfflineMapManager.describeDetectedLocation(location.latitude, location.longitude)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { onDownloadOfflineRegion(OfflineMapManager.TOKEN_AUTO_LOCAL_REGION) },
                            enabled = offlineState.downloadingRegionId == null,
                            colors = ButtonDefaults.buttonColors(containerColor = colors.accent, contentColor = colors.onAccent),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("📍 下載所在區域（${detectedLoc.localRegionTitleZh}）", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        Button(
                            onClick = { onDownloadOfflineRegion(OfflineMapManager.TOKEN_AUTO_ENTIRE_COUNTRY) },
                            enabled = offlineState.downloadingRegionId == null,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF22C55E), contentColor = Color.White),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("🌏 全下載所在國家（${detectedLoc.countryTitleZh}）", fontSize = 12.sp, fontWeight = FontWeight.Bold)
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

private fun applyRouteOverlayToStyle(style: Style, navState: BuiltInNavState) {
    val srcId = "dxcar-nav-route-src"
    val casingLayerId = "dxcar-nav-route-casing-layer"
    val layerId = "dxcar-nav-route-layer"
    val destSrcId = "dxcar-nav-dest-src"
    val destLayerId = "dxcar-nav-dest-layer"

    if (!navState.isNavigating || navState.routePoints.size < 2) {
        runCatching { style.removeLayer(layerId) }
        runCatching { style.removeLayer(casingLayerId) }
        runCatching { style.removeSource(srcId) }
        runCatching { style.removeLayer(destLayerId) }
        runCatching { style.removeSource(destSrcId) }
        return
    }

    val coordsJson = navState.routePoints.joinToString(",") { "[${it.longitude},${it.latitude}]" }
    val lineGeoJson =
        """{"type":"FeatureCollection","features":[{"type":"Feature","geometry":{"type":"LineString","coordinates":[$coordsJson]}}]}"""
    val destGeoJson =
        """{"type":"FeatureCollection","features":[{"type":"Feature","geometry":{"type":"Point","coordinates":[${navState.destinationLon},${navState.destinationLat}]}}]}"""

    runCatching {
        val existing = style.getSourceAs<GeoJsonSource>(srcId)
        if (existing != null) {
            existing.setGeoJson(lineGeoJson)
        } else {
            style.addSource(GeoJsonSource(srcId, lineGeoJson))
            style.addLayer(
                LineLayer(casingLayerId, srcId).withProperties(
                    PropertyFactory.lineColor(android.graphics.Color.parseColor("#1E3A8A")),
                    PropertyFactory.lineWidth(12.0f),
                    PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                    PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND)
                )
            )
            style.addLayer(
                LineLayer(layerId, srcId).withProperties(
                    PropertyFactory.lineColor(android.graphics.Color.parseColor("#3B82F6")),
                    PropertyFactory.lineWidth(7.5f),
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
                    PropertyFactory.circleRadius(9.0f),
                    PropertyFactory.circleStrokeColor(android.graphics.Color.WHITE),
                    PropertyFactory.circleStrokeWidth(3.0f)
                )
            )
        }
    }
}

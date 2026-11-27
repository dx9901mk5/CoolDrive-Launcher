package com.dxcar.launcher.map

import android.content.Context
import android.content.pm.LauncherApps
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.net.Uri
import android.os.Process
import android.view.View
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Navigation
import androidx.compose.material.icons.rounded.OpenInFull
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
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
import com.dxcar.launcher.i18n.DxCarI18n
import com.dxcar.launcher.i18n.DxCarI18n.str
import com.dxcar.launcher.i18n.EffectiveLanguage
import com.dxcar.launcher.i18n.LocalAppLanguage
import com.dxcar.launcher.overlay.Cursor3DStateHolder
import com.dxcar.launcher.overlay.Map3DAction
import com.dxcar.launcher.theme.LocalDxCarColors
import com.dxcar.launcher.ui.bouncyClickable
import kotlinx.coroutines.launch
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

private enum class MapConfirmSlot {
    HOME,
    WORK,
    NEW_CUSTOM,
    SEARCH_NAV
}

private data class PendingMapConfirm(
    val slot: MapConfirmSlot,
    val label: String,
    val rawAddress: String,
    val isDetecting: Boolean = true,
    val candidates: List<NavSearchResult> = emptyList(),
    val selectedIndex: Int = 0,
    val pinLat: Double = 0.0,
    val pinLon: Double = 0.0,
    val resolvedTitle: String = "",
    val resolvedSubtitle: String = "",
    val manuallyMoved: Boolean = false
)

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
    val currentLang = LocalAppLanguage.current
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var mapLibreMap by remember { mutableStateOf<MapLibreMap?>(null) }
    var followVehicle by remember { mutableStateOf(true) }
    val livePrefConfigState = remember { com.dxcar.launcher.DxCarApp.instance?.preferences?.config }?.collectAsState()
    val livePrefConfig = livePrefConfigState?.value
    var currentZoom by remember {
        mutableDoubleStateOf(
            com.dxcar.launcher.DxCarApp.instance?.preferences?.config?.value?.lastKnownZoom ?: 15.6
        )
    }
    var showNavShortcutEditor by remember { mutableStateOf(false) }
    var gmapsImportNote by remember { mutableStateOf<String?>(null) }

    fun syncHomeWorkFromGoogleMaps(forceFeedback: Boolean = false) {
        val (detectedHome, detectedWork, hasPerm) = readGoogleMapsHomeWorkShortcuts(context)
        val prefs = com.dxcar.launcher.DxCarApp.instance?.preferences
        if (prefs != null && (!detectedHome.isNullOrBlank() || !detectedWork.isNullOrBlank())) {
            prefs.update { cur ->
                cur.copy(
                    homeAddress = if (cur.homeAddress.isBlank() && !detectedHome.isNullOrBlank()) detectedHome else cur.homeAddress,
                    workAddress = if (cur.workAddress.isBlank() && !detectedWork.isNullOrBlank()) detectedWork else cur.workAddress
                )
            }
        }
        if (forceFeedback) {
            gmapsImportNote = when {
                !detectedHome.isNullOrBlank() || !detectedWork.isNullOrBlank() ->
                    DxCarI18n.tr(
                        "✓ 已從 Google 地圖捷徑偵測並帶入已設定地址",
                        "✓ 已从 Google 地图快捷方式检测并填入已设置地址",
                        "✓ Imported configured Home/Work address from Google Maps shortcuts",
                        "✓ Google マップのショートカットから自宅／職場の住所を取り込みました"
                    )
                !hasPerm ->
                    DxCarI18n.tr(
                        "ℹ️ Google 地圖未開放跨 App 讀取地址（僅預設桌面時可讀取捷徑），請直接在下方輸入回家與公司地址",
                        "ℹ️ Google 地图未开放跨 App 读取地址（仅默认桌面时可读取快捷方式），请直接在下方输入回家与公司地址",
                        "ℹ️ Google Maps doesn't expose raw Home/Work addresses to other apps; please enter them below",
                        "ℹ️ Google マップの自宅／職場住所は他アプリから直接取得できないため、下欄に直接ご入力ください"
                    )
                else ->
                    DxCarI18n.tr(
                        "ℹ️ 未在 Google 地圖捷徑中偵測到住家／公司地址，請直接在下方輸入",
                        "ℹ️ 未在 Google 地图快捷方式中检测到住家／公司地址，请直接在下方输入",
                        "ℹ️ No Home/Work address found in Google Maps shortcuts; please enter below",
                        "ℹ️ Google マップのショートカットに自宅／職場の住所が見つかりませんでした"
                    )
            }
        }
    }
    var manualTiltOverride by remember { mutableStateOf<Double?>(null) }
    var showLayerPicker by remember { mutableStateOf(false) }
    var showOfflineSheet by remember { mutableStateOf(false) }
    var showBuiltInSearchBox by remember { mutableStateOf(false) }
    var builtInQueryText by remember { mutableStateOf("") }
    var pendingMapConfirm by remember { mutableStateOf<PendingMapConfirm?>(null) }
    val latestPendingMapConfirm by rememberUpdatedState(pendingMapConfirm)
    val mapScope = rememberCoroutineScope()

    fun flyMapToPin(lat: Double, lon: Double, zoomLevel: Double = 17.2) {
        val map = mapLibreMap ?: return
        if (lat == 0.0 && lon == 0.0) return
        followVehicle = false
        val cam = CameraPosition.Builder()
            .target(LatLng(lat, lon))
            .zoom(zoomLevel)
            .bearing(0.0)
            .tilt(0.0)
            .build()
        map.animateCamera(CameraUpdateFactory.newCameraPosition(cam), 520)
    }

    fun triggerMapLocationConfirm(
        slot: MapConfirmSlot,
        label: String,
        rawAddress: String,
        preResolved: NavSearchResult? = null
    ) {
        val trimmed = rawAddress.trim()
        if (trimmed.isBlank()) return
        showNavShortcutEditor = false
        showBuiltInSearchBox = false
        followVehicle = false

        val fallbackLat = if (location.latitude != 0.0) location.latitude else (livePrefConfig?.lastKnownLat ?: 25.0330)
        val fallbackLon = if (location.longitude != 0.0) location.longitude else (livePrefConfig?.lastKnownLon ?: 121.5654)

        if (preResolved != null) {
            pendingMapConfirm = PendingMapConfirm(
                slot = slot,
                label = label,
                rawAddress = trimmed,
                isDetecting = false,
                candidates = listOf(preResolved),
                selectedIndex = 0,
                pinLat = preResolved.latitude,
                pinLon = preResolved.longitude,
                resolvedTitle = preResolved.name,
                resolvedSubtitle = preResolved.subtitle
            )
            flyMapToPin(preResolved.latitude, preResolved.longitude)
            return
        }

        pendingMapConfirm = PendingMapConfirm(
            slot = slot,
            label = label,
            rawAddress = trimmed,
            isDetecting = true,
            pinLat = fallbackLat,
            pinLon = fallbackLon,
            resolvedTitle = trimmed,
            resolvedSubtitle = ""
        )

        mapScope.launch {
            val engine = com.dxcar.launcher.DxCarApp.instance?.builtInNavEngine
            val results = engine?.resolveAddressCandidates(trimmed, fallbackLat, fallbackLon).orEmpty()
            val first = results.firstOrNull()
            val cur = latestPendingMapConfirm ?: return@launch
            if (cur.slot != slot || cur.rawAddress != trimmed) return@launch
            if (first != null) {
                pendingMapConfirm = cur.copy(
                    isDetecting = false,
                    candidates = results,
                    selectedIndex = 0,
                    pinLat = first.latitude,
                    pinLon = first.longitude,
                    resolvedTitle = first.name,
                    resolvedSubtitle = first.subtitle,
                    manuallyMoved = false
                )
                flyMapToPin(first.latitude, first.longitude)
            } else {
                pendingMapConfirm = cur.copy(
                    isDetecting = false,
                    candidates = emptyList(),
                    pinLat = fallbackLat,
                    pinLon = fallbackLon,
                    resolvedTitle = trimmed,
                    resolvedSubtitle = "",
                    manuallyMoved = false
                )
                flyMapToPin(fallbackLat, fallbackLon, 15.8)
            }
        }
    }

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

    val mapNorthUpMode = livePrefConfig?.mapNorthUpMode ?: true

    LaunchedEffect(mapLibreMap, location, followVehicle, isPrimary, mapEngineStyle, manualTiltOverride, mapNorthUpMode) {
        val map = mapLibreMap ?: return@LaunchedEffect
        if (!followVehicle) return@LaunchedEffect
        val defaultTilt = if (mapEngineStyle == MapEngineStyle.OPENFREEMAP_VECTOR_3D ||
            mapEngineStyle == MapEngineStyle.OPENFREEMAP_DARK
        ) 44.0 else 32.0
        val resolvedTilt = manualTiltOverride ?: if (isPrimary || isFullscreen) defaultTilt else 18.0
        // North-Up: map stays north (0 deg), puck rotates with vehicle heading.
        // Heading-Up: map rotates with vehicle heading and NEVER snaps back to 0 deg at red lights!
        val resolvedBearing = if (mapNorthUpMode) 0.0 else location.bearingDeg.toDouble()
        val cam = CameraPosition.Builder()
            .target(LatLng(location.latitude, location.longitude))
            .zoom(currentZoom)
            .bearing(resolvedBearing)
            .tilt(resolvedTilt)
            .build()
        map.animateCamera(CameraUpdateFactory.newCameraPosition(cam), 700)
    }

    LaunchedEffect(mapLibreMap) {
        val map = mapLibreMap ?: return@LaunchedEffect
        Cursor3DStateHolder.builtinMapCommands.collect { action ->
            val cur = map.cameraPosition
            val target = cur.target ?: LatLng(location.latitude, location.longitude)
            val nextZoom = when (action) {
                Map3DAction.ZOOM_IN -> (cur.zoom + 1.0).coerceIn(3.0, 19.5)
                Map3DAction.ZOOM_OUT -> (cur.zoom - 1.0).coerceIn(3.0, 19.5)
                else -> cur.zoom
            }
            val nextTilt = when (action) {
                Map3DAction.TILT_UP_3D -> (cur.tilt + 18.0).coerceIn(0.0, 60.0)
                Map3DAction.TILT_DOWN_2D -> (cur.tilt - 18.0).coerceIn(0.0, 60.0)
                else -> cur.tilt
            }
            currentZoom = nextZoom
            if (action == Map3DAction.TILT_UP_3D || action == Map3DAction.TILT_DOWN_2D) {
                manualTiltOverride = nextTilt
            }
            val updated = CameraPosition.Builder(cur)
                .target(target)
                .zoom(nextZoom)
                .tilt(nextTilt)
                .build()
            map.animateCamera(CameraUpdateFactory.newCameraPosition(updated), 260)
        }
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
                        val idleCam = map.cameraPosition
                        val idleZoom = idleCam.zoom
                        currentZoom = idleZoom
                        val confirmState = latestPendingMapConfirm
                        val idleTarget = idleCam.target
                        if (confirmState != null && !confirmState.isDetecting && idleTarget != null) {
                            val movedFar = kotlin.math.abs(idleTarget.latitude - confirmState.pinLat) > 0.00018 ||
                                kotlin.math.abs(idleTarget.longitude - confirmState.pinLon) > 0.00018
                            if (movedFar) {
                                pendingMapConfirm = confirmState.copy(
                                    pinLat = idleTarget.latitude,
                                    pinLon = idleTarget.longitude,
                                    manuallyMoved = true
                                )
                            }
                        } else {
                            val prefs = com.dxcar.launcher.DxCarApp.instance?.preferences
                            if (prefs != null && kotlin.math.abs(prefs.config.value.lastKnownZoom - idleZoom) >= 0.15) {
                                prefs.update { it.copy(lastKnownZoom = idleZoom) }
                            }
                        }
                    }
                    runCatching { map.setPrefetchesTiles(true) }
                    runCatching { map.setPrefetchZoomDelta(4) }
                    runCatching {
                        org.maplibre.android.offline.OfflineManager.getInstance(context)
                            .setMaximumAmbientCacheSize(256L * 1024L * 1024L, null)
                    }
                    val initCam = CameraPosition.Builder()
                        .target(LatLng(location.latitude, location.longitude))
                        .zoom(currentZoom)
                        .bearing(if (mapNorthUpMode) 0.0 else location.bearingDeg.toDouble())
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
            visible = followVehicle && pendingMapConfirm == null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            VehiclePuckOverlay(
                accentColor = colors.accent,
                bearingDeg = if (mapNorthUpMode) location.bearingDeg else 0f
            )
        }

        // INTERACTIVE CENTER TARGET PIN WHEN CONFIRMING AN ENTERED ADDRESS ON THE MAP
        AnimatedVisibility(
            visible = pendingMapConfirm != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Color(0x33EF4444))
                        .border(2.dp, Color(0xFFEF4444), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Place,
                        contentDescription = null,
                        tint = Color(0xFFEF4444),
                        modifier = Modifier.size(34.dp)
                    )
                }
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFEF4444))
                )
            }
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
                                    builtInNavState.isSearching -> str(
                                        "✨ 正在於內建地圖搜尋目的地…",
                                        "✨ 正在于内置地图搜索目的地…",
                                        "✨ Searching destination on built-in map…",
                                        "✨ 内蔵マップで目的地を検索中…"
                                    )
                                    builtInNavState.isRouting -> str(
                                        "🗺️ 正在規劃內建地圖導航藍線…",
                                        "🗺️ 正在规划内置地图导航路线…",
                                        "🗺️ Calculating route on built-in map…",
                                        "🗺️ 内蔵マップでルートを計算中…"
                                    )
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
                                    text = str(
                                        "目的地：${builtInNavState.destinationName} · 剩餘 $remKm · 約 $remMin 分鐘",
                                        "目的地：${builtInNavState.destinationName} · 剩余 $remKm · 约 $remMin 分钟",
                                        "Dest: ${builtInNavState.destinationName} · $remKm left · ~$remMin min",
                                        "目的地：${builtInNavState.destinationName} · 残り $remKm · 約 $remMin 分"
                                    ),
                                    color = Color(0xFFD1FAE5),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        IconButton(onClick = onVoiceNavClick) {
                            Icon(Icons.Rounded.Mic, contentDescription = str("語音換目的地", "语音换目的地", "Voice change destination", "音声で目的地を変更"), tint = Color.White)
                        }
                        IconButton(onClick = onStopBuiltInNav) {
                            Icon(Icons.Rounded.Close, contentDescription = str("結束導航", "结束导航", "Stop navigation", "ナビ終了"), tint = Color.White)
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
                                    contentDescription = str("搜尋目的地", "搜索目的地", "Search destination", "目的地を検索"),
                                    tint = colors.accent,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = str("內建地圖導航搜尋…", "内置地图导航搜索…", "Search built-in map…", "内蔵マップで検索…"),
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
                                label = str("✨ Gemini 語音導航", "✨ Gemini 语音导航", "✨ Gemini Voice Nav", "✨ Gemini 音声ナビ"),
                                highlight = true,
                                onClick = onVoiceNavClick
                            )
                            val dlPct = (offlineState.progress * 100).toInt()
                            MapChipButton(
                                icon = Icons.Rounded.CloudDownload,
                                label = if (offlineState.downloadingRegionId != null) {
                                    str("下載 $dlPct%", "下载 $dlPct%", "DL $dlPct%", "保存中 $dlPct%")
                                } else {
                                    str("離線地圖", "离线地图", "Offline Maps", "オフライン地図")
                                },
                                highlight = showOfflineSheet || offlineState.updateSuggestion != null || offlineState.downloadingRegionId != null,
                                onClick = { showOfflineSheet = !showOfflineSheet }
                            )
                            if (homeAddress.isNotBlank()) {
                                val homeLatSaved = livePrefConfig?.homeLat ?: 0.0
                                val homeLonSaved = livePrefConfig?.homeLon ?: 0.0
                                val homeLbl = livePrefConfig?.homeShortcutLabel?.takeIf { it.isNotBlank() }
                                    ?: str("回家", "回家", "Home", "自宅")
                                MapChipButton(
                                    icon = Icons.Rounded.Home,
                                    label = homeLbl,
                                    onClick = {
                                        if (homeLatSaved != 0.0 && homeLonSaved != 0.0) {
                                            onStartBuiltInNavResult(NavSearchResult(homeLbl, homeAddress, homeLatSaved, homeLonSaved))
                                        } else {
                                            onQuickBuiltInNavigateQuery(homeAddress)
                                        }
                                    }
                                )
                            }
                            if (workAddress.isNotBlank()) {
                                val workLatSaved = livePrefConfig?.workLat ?: 0.0
                                val workLonSaved = livePrefConfig?.workLon ?: 0.0
                                val workLbl = livePrefConfig?.workShortcutLabel?.takeIf { it.isNotBlank() }
                                    ?: str("公司", "公司", "Work", "職場")
                                MapChipButton(
                                    icon = Icons.Rounded.Work,
                                    label = workLbl,
                                    onClick = {
                                        if (workLatSaved != 0.0 && workLonSaved != 0.0) {
                                            onStartBuiltInNavResult(NavSearchResult(workLbl, workAddress, workLatSaved, workLonSaved))
                                        } else {
                                            onQuickBuiltInNavigateQuery(workAddress)
                                        }
                                    }
                                )
                            }
                            MapChipButton(
                                icon = Icons.Rounded.Navigation,
                                label = str("G地圖", "G地图", "G Maps", "Gマップ"),
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
                                        placeholder = {
                                            Text(
                                                str(
                                                    "輸入目的地（直接於本啟動器內建地圖導航）…",
                                                    "输入目的地（直接在本启动器内置地图导航）…",
                                                    "Enter destination (navigate on built-in map)…",
                                                    "目的地を入力（内蔵マップで案内）…"
                                                ),
                                                fontSize = 12.sp
                                            )
                                        },
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.weight(1f)
                                    )
                                    Button(
                                        onClick = {
                                            if (builtInQueryText.isNotBlank()) {
                                                triggerMapLocationConfirm(
                                                    slot = MapConfirmSlot.SEARCH_NAV,
                                                    label = builtInQueryText.trim(),
                                                    rawAddress = builtInQueryText.trim()
                                                )
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = colors.accent, contentColor = colors.onAccent),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Icon(Icons.Rounded.Place, contentDescription = null, modifier = Modifier.size(15.dp))
                                        Spacer(Modifier.width(3.dp))
                                        Text(str("地圖判定", "地图判定", "Locate", "地図確認"), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                    OutlinedButton(
                                        onClick = { onSearchBuiltInPlace(builtInQueryText) },
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Text(str("清單", "列表", "List", "一覧"), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                    IconButton(onClick = onVoiceNavClick) {
                                        Icon(Icons.Rounded.Mic, contentDescription = str("語音輸入", "语音输入", "Voice input", "音声入力"), tint = Color(0xFFA78BFA))
                                    }
                                    IconButton(onClick = { showBuiltInSearchBox = false }) {
                                        Icon(Icons.Rounded.Close, contentDescription = str("關閉", "关闭", "Close", "閉じる"), tint = colors.secondaryText)
                                    }
                                }

                                // QUICK NAVIGATION SHORTCUTS BAR (Home, Work, Custom/+, Settings Gear)
                                LaunchedEffect(showBuiltInSearchBox) {
                                    if (showBuiltInSearchBox && (homeAddress.isBlank() || workAddress.isBlank())) {
                                        syncHomeWorkFromGoogleMaps(forceFeedback = false)
                                    }
                                }
                                val showHomeChip = livePrefConfig?.showHomeNavShortcut ?: true
                                val showWorkChip = livePrefConfig?.showWorkNavShortcut ?: true
                                val homeChipLabel = livePrefConfig?.homeShortcutLabel?.takeIf { it.isNotBlank() }
                                    ?: str("回家", "回家", "Home", "自宅")
                                val workChipLabel = livePrefConfig?.workShortcutLabel?.takeIf { it.isNotBlank() }
                                    ?: str("公司", "公司", "Work", "職場")
                                val customShortcuts = livePrefConfig?.customNavShortcuts ?: emptyList()
                                val maxCustomShortcuts = 8

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (showHomeChip) {
                                        val homeLatSaved = livePrefConfig?.homeLat ?: 0.0
                                        val homeLonSaved = livePrefConfig?.homeLon ?: 0.0
                                        MapChipButton(
                                            icon = Icons.Rounded.Home,
                                            label = if (homeAddress.isNotBlank()) homeChipLabel else "$homeChipLabel (＋)",
                                            highlight = homeAddress.isNotBlank(),
                                            onClick = {
                                                if (homeAddress.isNotBlank()) {
                                                    showBuiltInSearchBox = false
                                                    if (homeLatSaved != 0.0 && homeLonSaved != 0.0) {
                                                        onStartBuiltInNavResult(NavSearchResult(homeChipLabel, homeAddress, homeLatSaved, homeLonSaved))
                                                    } else {
                                                        onQuickBuiltInNavigateQuery(homeAddress)
                                                    }
                                                } else {
                                                    showNavShortcutEditor = true
                                                }
                                            }
                                        )
                                    }
                                    if (showWorkChip) {
                                        val workLatSaved = livePrefConfig?.workLat ?: 0.0
                                        val workLonSaved = livePrefConfig?.workLon ?: 0.0
                                        MapChipButton(
                                            icon = Icons.Rounded.Work,
                                            label = if (workAddress.isNotBlank()) workChipLabel else "$workChipLabel (＋)",
                                            highlight = workAddress.isNotBlank(),
                                            onClick = {
                                                if (workAddress.isNotBlank()) {
                                                    showBuiltInSearchBox = false
                                                    if (workLatSaved != 0.0 && workLonSaved != 0.0) {
                                                        onStartBuiltInNavResult(NavSearchResult(workChipLabel, workAddress, workLatSaved, workLonSaved))
                                                    } else {
                                                        onQuickBuiltInNavigateQuery(workAddress)
                                                    }
                                                } else {
                                                    showNavShortcutEditor = true
                                                }
                                            }
                                        )
                                    }
                                    customShortcuts.take(maxCustomShortcuts).forEach { sc ->
                                        MapChipButton(
                                            icon = Icons.Rounded.Place,
                                            label = sc.label,
                                            highlight = true,
                                            onClick = {
                                                showBuiltInSearchBox = false
                                                if (sc.latitude != 0.0 && sc.longitude != 0.0) {
                                                    onStartBuiltInNavResult(NavSearchResult(sc.label, sc.address, sc.latitude, sc.longitude))
                                                } else {
                                                    onQuickBuiltInNavigateQuery(sc.address)
                                                }
                                            }
                                        )
                                    }
                                    // Blank (+) quick-add slot (up to 8 custom shortcuts, horizontally scrollable)
                                    if (customShortcuts.size < maxCustomShortcuts) {
                                        MapChipButton(
                                            icon = Icons.Rounded.Add,
                                            label = str(
                                                "新增 (${customShortcuts.size}/$maxCustomShortcuts)",
                                                "新增 (${customShortcuts.size}/$maxCustomShortcuts)",
                                                "Add (${customShortcuts.size}/$maxCustomShortcuts)",
                                                "追加 (${customShortcuts.size}/$maxCustomShortcuts)"
                                            ),
                                            highlight = false,
                                            onClick = { showNavShortcutEditor = true }
                                        )
                                    }
                                    // Settings Gear button
                                    MapChipButton(
                                        icon = Icons.Rounded.Settings,
                                        label = str("設定", "设置", "Edit", "設定"),
                                        highlight = showNavShortcutEditor,
                                        onClick = { showNavShortcutEditor = !showNavShortcutEditor }
                                    )
                                }

                                // GEAR MODAL / INLINE EDITOR FOR NAV SHORTCUTS (Home, Work, Custom, Google Maps Import)
                                if (showNavShortcutEditor) {
                                    var editHomeLabel by remember(livePrefConfig?.homeShortcutLabel) {
                                        mutableStateOf(livePrefConfig?.homeShortcutLabel.orEmpty())
                                    }
                                    var editHomeAddr by remember(homeAddress) { mutableStateOf(homeAddress) }
                                    var editWorkLabel by remember(livePrefConfig?.workShortcutLabel) {
                                        mutableStateOf(livePrefConfig?.workShortcutLabel.orEmpty())
                                    }
                                    var editWorkAddr by remember(workAddress) { mutableStateOf(workAddress) }
                                    var newCustomLabel by remember { mutableStateOf("") }
                                    var newCustomAddr by remember { mutableStateOf("") }

                                    Surface(
                                        color = colors.cardSurfaceElevated,
                                        shape = RoundedCornerShape(14.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .border(1.dp, colors.accent.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .padding(12.dp)
                                                .heightIn(max = 260.dp)
                                                .verticalScroll(rememberScrollState()),
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text(
                                                    text = str(
                                                        "快速導航快捷鍵設定",
                                                        "快速导航快捷键设置",
                                                        "Quick Nav Shortcuts Setup",
                                                        "クイックナビ設定"
                                                    ),
                                                    color = colors.primaryText,
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                    OutlinedButton(
                                                        onClick = { syncHomeWorkFromGoogleMaps(forceFeedback = true) },
                                                        shape = RoundedCornerShape(10.dp)
                                                    ) {
                                                        Text(
                                                            str("讀取 G地圖設定", "读取 G地图设置", "Read G Maps", "Gマップ取込"),
                                                            fontSize = 11.sp
                                                        )
                                                    }
                                                    IconButton(
                                                        onClick = { showNavShortcutEditor = false },
                                                        modifier = Modifier.size(28.dp)
                                                    ) {
                                                        Icon(Icons.Rounded.Close, contentDescription = null, tint = colors.secondaryText, modifier = Modifier.size(16.dp))
                                                    }
                                                }
                                            }
                                            if (!gmapsImportNote.isNullOrBlank()) {
                                                Text(gmapsImportNote.orEmpty(), color = colors.accent, fontSize = 11.sp)
                                            }

                                            // Default Shortcut 1: Home (editable + map pin verify + removable)
                                            val homeConfirmed = (livePrefConfig?.homeLat ?: 0.0) != 0.0 && editHomeAddr.trim() == homeAddress.trim()
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                OutlinedTextField(
                                                    value = editHomeLabel,
                                                    onValueChange = { editHomeLabel = it },
                                                    singleLine = true,
                                                    placeholder = { Text(str("回家", "回家", "Home", "自宅"), fontSize = 11.sp) },
                                                    modifier = Modifier.width(82.dp)
                                                )
                                                OutlinedTextField(
                                                    value = editHomeAddr,
                                                    onValueChange = { editHomeAddr = it },
                                                    singleLine = true,
                                                    placeholder = { Text(str("輸入回家地址…", "输入回家地址…", "Home address…", "自宅の住所…"), fontSize = 11.sp) },
                                                    modifier = Modifier.weight(1f)
                                                )
                                                Surface(
                                                    color = if (homeConfirmed) Color(0x2822C55E) else colors.accent.copy(alpha = 0.22f),
                                                    shape = RoundedCornerShape(10.dp),
                                                    modifier = Modifier
                                                        .border(1.dp, if (homeConfirmed) Color(0xFF22C55E) else colors.accent, RoundedCornerShape(10.dp))
                                                        .bouncyClickable {
                                                            val addr = editHomeAddr.trim()
                                                            if (addr.isNotBlank()) {
                                                                com.dxcar.launcher.DxCarApp.instance?.preferences?.update { cur ->
                                                                    cur.copy(homeShortcutLabel = editHomeLabel.trim(), homeAddress = addr)
                                                                }
                                                                triggerMapLocationConfirm(
                                                                    slot = MapConfirmSlot.HOME,
                                                                    label = editHomeLabel.trim().ifBlank { homeChipLabel },
                                                                    rawAddress = addr
                                                                )
                                                            }
                                                        }
                                                ) {
                                                    Row(
                                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 7.dp),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Icon(
                                                            imageVector = if (homeConfirmed) Icons.Rounded.CheckCircle else Icons.Rounded.Place,
                                                            contentDescription = null,
                                                            tint = if (homeConfirmed) Color(0xFF22C55E) else colors.accent,
                                                            modifier = Modifier.size(15.dp)
                                                        )
                                                        Spacer(Modifier.width(3.dp))
                                                        Text(
                                                            text = if (homeConfirmed) str("已定位", "已定位", "Pinned", "確認済") else str("地圖確認", "地图确认", "Verify", "地図確認"),
                                                            color = if (homeConfirmed) Color(0xFF22C55E) else colors.accent,
                                                            fontSize = 11.sp,
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                    }
                                                }
                                                IconButton(
                                                    onClick = {
                                                        com.dxcar.launcher.DxCarApp.instance?.preferences?.update { cur ->
                                                            cur.copy(showHomeNavShortcut = !cur.showHomeNavShortcut)
                                                        }
                                                    },
                                                    modifier = Modifier.size(28.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = if (showHomeChip) Icons.Rounded.DeleteOutline else Icons.Rounded.Add,
                                                        contentDescription = null,
                                                        tint = if (showHomeChip) Color(0xFFEF4444) else colors.accent,
                                                        modifier = Modifier.size(17.dp)
                                                    )
                                                }
                                            }

                                            // Default Shortcut 2: Work (editable + map pin verify + removable)
                                            val workConfirmed = (livePrefConfig?.workLat ?: 0.0) != 0.0 && editWorkAddr.trim() == workAddress.trim()
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                OutlinedTextField(
                                                    value = editWorkLabel,
                                                    onValueChange = { editWorkLabel = it },
                                                    singleLine = true,
                                                    placeholder = { Text(str("公司", "公司", "Work", "職場"), fontSize = 11.sp) },
                                                    modifier = Modifier.width(82.dp)
                                                )
                                                OutlinedTextField(
                                                    value = editWorkAddr,
                                                    onValueChange = { editWorkAddr = it },
                                                    singleLine = true,
                                                    placeholder = { Text(str("輸入公司地址…", "输入公司地址…", "Work address…", "職場の住所…"), fontSize = 11.sp) },
                                                    modifier = Modifier.weight(1f)
                                                )
                                                Surface(
                                                    color = if (workConfirmed) Color(0x2822C55E) else colors.accent.copy(alpha = 0.22f),
                                                    shape = RoundedCornerShape(10.dp),
                                                    modifier = Modifier
                                                        .border(1.dp, if (workConfirmed) Color(0xFF22C55E) else colors.accent, RoundedCornerShape(10.dp))
                                                        .bouncyClickable {
                                                            val addr = editWorkAddr.trim()
                                                            if (addr.isNotBlank()) {
                                                                com.dxcar.launcher.DxCarApp.instance?.preferences?.update { cur ->
                                                                    cur.copy(workShortcutLabel = editWorkLabel.trim(), workAddress = addr)
                                                                }
                                                                triggerMapLocationConfirm(
                                                                    slot = MapConfirmSlot.WORK,
                                                                    label = editWorkLabel.trim().ifBlank { workChipLabel },
                                                                    rawAddress = addr
                                                                )
                                                            }
                                                        }
                                                ) {
                                                    Row(
                                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 7.dp),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Icon(
                                                            imageVector = if (workConfirmed) Icons.Rounded.CheckCircle else Icons.Rounded.Place,
                                                            contentDescription = null,
                                                            tint = if (workConfirmed) Color(0xFF22C55E) else colors.accent,
                                                            modifier = Modifier.size(15.dp)
                                                        )
                                                        Spacer(Modifier.width(3.dp))
                                                        Text(
                                                            text = if (workConfirmed) str("已定位", "已定位", "Pinned", "確認済") else str("地圖確認", "地图确认", "Verify", "地図確認"),
                                                            color = if (workConfirmed) Color(0xFF22C55E) else colors.accent,
                                                            fontSize = 11.sp,
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                    }
                                                }
                                                IconButton(
                                                    onClick = {
                                                        com.dxcar.launcher.DxCarApp.instance?.preferences?.update { cur ->
                                                            cur.copy(showWorkNavShortcut = !cur.showWorkNavShortcut)
                                                        }
                                                    },
                                                    modifier = Modifier.size(28.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = if (showWorkChip) Icons.Rounded.DeleteOutline else Icons.Rounded.Add,
                                                        contentDescription = null,
                                                        tint = if (showWorkChip) Color(0xFFEF4444) else colors.accent,
                                                        modifier = Modifier.size(17.dp)
                                                    )
                                                }
                                            }

                                            // Existing custom shortcuts
                                            customShortcuts.forEach { sc ->
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .clip(RoundedCornerShape(10.dp))
                                                        .background(colors.cardSurface)
                                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Text(
                                                        text = (if (sc.latitude != 0.0) "✓ " else "") + "${sc.label} · ${sc.address}",
                                                        color = colors.primaryText,
                                                        fontSize = 12.sp,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis,
                                                        modifier = Modifier.weight(1f)
                                                    )
                                                    IconButton(
                                                        onClick = {
                                                            com.dxcar.launcher.DxCarApp.instance?.preferences?.update { cur ->
                                                                cur.copy(customNavShortcuts = cur.customNavShortcuts.filterNot { it.id == sc.id })
                                                            }
                                                        },
                                                        modifier = Modifier.size(26.dp)
                                                    ) {
                                                        Icon(Icons.Rounded.DeleteOutline, contentDescription = null, tint = Color(0xFFEF4444), modifier = Modifier.size(16.dp))
                                                    }
                                                }
                                            }

                                            // Add new custom shortcut row (up to 8) -> immediately jumps into Map Pin Detection & Confirmation!
                                            if (customShortcuts.size < 8) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    OutlinedTextField(
                                                        value = newCustomLabel,
                                                        onValueChange = { newCustomLabel = it },
                                                        singleLine = true,
                                                        placeholder = { Text(str("名稱", "名称", "Name", "名称"), fontSize = 11.sp) },
                                                        modifier = Modifier.width(82.dp)
                                                    )
                                                    OutlinedTextField(
                                                        value = newCustomAddr,
                                                        onValueChange = { newCustomAddr = it },
                                                        singleLine = true,
                                                        placeholder = { Text(str("新增自訂導航地址 (${customShortcuts.size}/8)…", "新增自定义导航地址 (${customShortcuts.size}/8)…", "New address (${customShortcuts.size}/8)…", "新しい住所 (${customShortcuts.size}/8)…"), fontSize = 11.sp) },
                                                        modifier = Modifier.weight(1f)
                                                    )
                                                    Button(
                                                        onClick = {
                                                            val addr = newCustomAddr.trim()
                                                            if (addr.isNotBlank()) {
                                                                val lbl = newCustomLabel.trim().ifBlank { addr.take(6) }
                                                                newCustomLabel = ""
                                                                newCustomAddr = ""
                                                                triggerMapLocationConfirm(
                                                                    slot = MapConfirmSlot.NEW_CUSTOM,
                                                                    label = lbl,
                                                                    rawAddress = addr
                                                                )
                                                            }
                                                        },
                                                        shape = RoundedCornerShape(10.dp)
                                                    ) {
                                                        Text(str("＋ 定位", "＋ 定位", "+ Pin", "+ 確認"), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                    }
                                                }
                                            } else {
                                                Text(
                                                    text = str("已達 8 組自訂快捷上限，刪除舊項目即可繼續新增", "已达 8 组自定义快捷上限，删除旧项目即可继续新增", "Reached 8 custom shortcuts limit. Delete one to add more.", "カスタム登録上限(8件)です。不要な項目を削除してください"),
                                                    color = colors.secondaryText,
                                                    fontSize = 11.sp
                                                )
                                            }

                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.End
                                            ) {
                                                Button(
                                                    onClick = {
                                                        val nextHome = editHomeAddr.trim()
                                                        val nextWork = editWorkAddr.trim()
                                                        val needVerifyHome = nextHome.isNotBlank() &&
                                                            (nextHome != homeAddress.trim() || (livePrefConfig?.homeLat ?: 0.0) == 0.0)
                                                        val needVerifyWork = nextWork.isNotBlank() &&
                                                            (nextWork != workAddress.trim() || (livePrefConfig?.workLat ?: 0.0) == 0.0)
                                                        com.dxcar.launcher.DxCarApp.instance?.preferences?.update { cur ->
                                                            cur.copy(
                                                                homeShortcutLabel = editHomeLabel.trim(),
                                                                homeAddress = nextHome,
                                                                homeLat = if (nextHome == cur.homeAddress.trim()) cur.homeLat else 0.0,
                                                                homeLon = if (nextHome == cur.homeAddress.trim()) cur.homeLon else 0.0,
                                                                workShortcutLabel = editWorkLabel.trim(),
                                                                workAddress = nextWork,
                                                                workLat = if (nextWork == cur.workAddress.trim()) cur.workLat else 0.0,
                                                                workLon = if (nextWork == cur.workAddress.trim()) cur.workLon else 0.0
                                                            )
                                                        }
                                                        when {
                                                            needVerifyHome -> triggerMapLocationConfirm(
                                                                slot = MapConfirmSlot.HOME,
                                                                label = editHomeLabel.trim().ifBlank { homeChipLabel },
                                                                rawAddress = nextHome
                                                            )
                                                            needVerifyWork -> triggerMapLocationConfirm(
                                                                slot = MapConfirmSlot.WORK,
                                                                label = editWorkLabel.trim().ifBlank { workChipLabel },
                                                                rawAddress = nextWork
                                                            )
                                                            else -> showNavShortcutEditor = false
                                                        }
                                                    },
                                                    colors = ButtonDefaults.buttonColors(containerColor = colors.accent, contentColor = colors.onAccent),
                                                    shape = RoundedCornerShape(10.dp)
                                                ) {
                                                    Text(
                                                        str("儲存並於地圖確認", "保存并在地图确认", "Save & Verify on Map", "保存して地図で確認"),
                                                        fontSize = 12.sp,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                                if (builtInNavState.errorMessage.isNotBlank()) {
                                    Text(builtInNavState.errorMessage, color = Color(0xFFF59E0B), fontSize = 12.sp)
                                }
                                if (builtInQueryText.isNotBlank() && builtInNavState.searchResults.isEmpty() && !builtInNavState.isSearching) {
                                    OutlinedButton(
                                        onClick = {
                                            val q = builtInQueryText.trim()
                                            showBuiltInSearchBox = false
                                            onLaunchGoogleMapsApp(q)
                                        },
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = str(
                                                "🗺️ 改在 Google 地圖搜尋「${builtInQueryText.trim()}」",
                                                "🗺️ 改在 Google 地图搜索「${builtInQueryText.trim()}」",
                                                "🗺️ Search \"${builtInQueryText.trim()}\" in Google Maps",
                                                "🗺️ Google マップで「${builtInQueryText.trim()}」を検索"
                                            ),
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                                builtInNavState.searchResults.take(4).forEach { res ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(colors.cardSurfaceElevated)
                                            .clickable {
                                                triggerMapLocationConfirm(
                                                    slot = MapConfirmSlot.SEARCH_NAV,
                                                    label = res.name,
                                                    rawAddress = res.subtitle.ifBlank { res.name },
                                                    preResolved = res
                                                )
                                            }
                                            .padding(horizontal = 12.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(res.name, color = colors.primaryText, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            Text(res.subtitle, color = colors.secondaryText, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        }
                                        Surface(
                                            color = colors.accent.copy(alpha = 0.18f),
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Text(
                                                text = str("📍 看地圖", "📍 看地图", "📍 Map", "📍 地図"),
                                                color = colors.accent,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                                            )
                                        }
                                        Surface(
                                            color = colors.accent,
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier.bouncyClickable {
                                                showBuiltInSearchBox = false
                                                onStartBuiltInNavResult(res)
                                            }
                                        ) {
                                            Text(
                                                text = str("導航 →", "导航 →", "Go →", "案内 →"),
                                                color = colors.onAccent,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Smart Offline Map Update Recommendation Pill (user chooses whether to update!)
                    val suggestion = offlineState.updateSuggestion
                    if (suggestion != null && !showOfflineSheet) {
                        val sugTitle = OfflineMapManager.presetTitleStr(suggestion.regionId, suggestion.regionTitleZh)
                        val sugText = when {
                            suggestion.daysAgo > 0L -> str(
                                "「$sugTitle」離線地圖已下載 ${suggestion.daysAgo} 天（超過設定的 ${suggestion.policyDays} 天），建議連網時更新",
                                "「$sugTitle」离线地图已下载 ${suggestion.daysAgo} 天（超过设置的 ${suggestion.policyDays} 天），建议联网时更新",
                                "\"$sugTitle\" offline map is ${suggestion.daysAgo} days old (over ${suggestion.policyDays}-day limit). Update recommended.",
                                "「$sugTitle」オフライン地図は保存から ${suggestion.daysAgo} 日経過（設定 ${suggestion.policyDays} 日超過）。更新を推奨します"
                            )
                            suggestion.isUncachedArea -> {
                                val liveDet = OfflineMapManager.describeDetectedLocation(location.latitude, location.longitude, currentLang)
                                str(
                                    "偵測到車輛進入「${liveDet.localRegionTitleZh}」尚未離線快取，可一鍵下載所在區域地圖",
                                    "检测到车辆进入「${liveDet.localRegionTitleZh}」尚未离线缓存，可一键下载所在区域地图",
                                    "Entered \"${liveDet.localRegionTitleZh}\" without offline cache. Download local map in one tap.",
                                    "オフライン未保存の「${liveDet.localRegionTitleZh}」に入りました。ワンタップで周辺地図を保存できます"
                                )
                            }
                            else -> suggestion.reasonZh
                        }
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
                                        text = sugText,
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
                                        Text(str("立即更新", "立即更新", "Update Now", "今すぐ更新"), fontSize = 11.sp)
                                    }
                                    OutlinedButton(
                                        onClick = onDismissOfflineSuggestion,
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Text(str("稍後", "稍后", "Later", "後で"), fontSize = 11.sp)
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
                            Text(str("即時地圖", "实时地图", "Live Map", "ライブ地図"), color = colors.primaryText, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
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
                        Icon(Icons.Rounded.SwapHoriz, contentDescription = str("切換為主畫面", "切换为主画面", "Swap to primary pane", "メイン画面に切替"), tint = colors.onAccent, modifier = Modifier.size(18.dp))
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
                            text = if (location.hasRealFix) {
                                str("GPS 即時", "GPS 实时", "GPS Live", "GPS 測位中")
                            } else {
                                str("定位中", "定位中", "Locating", "測位待機")
                            },
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
                            Icon(Icons.Rounded.Add, contentDescription = str("放大", "放大", "Zoom in", "拡大"), tint = colors.primaryText)
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
                            Icon(Icons.Rounded.Remove, contentDescription = str("縮小", "缩小", "Zoom out", "縮小"), tint = colors.primaryText)
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SmallGlassCircleButton(
                        icon = if (mapNorthUpMode) Icons.Rounded.Navigation else Icons.Rounded.Explore,
                        contentDescription = if (mapNorthUpMode) {
                            str("正北朝上（點一下切換車頭朝上）", "正北朝上（点一下切换车头朝上）", "North-Up (tap for Heading-Up)", "ノースアップ（タップでヘディングアップ）")
                        } else {
                            str("車頭朝上（點一下切換正北朝上）", "车头朝上（点一下切换正北朝上）", "Heading-Up (tap for North-Up)", "ヘディングアップ（タップでノースアップ）")
                        },
                        active = !mapNorthUpMode,
                        onClick = {
                            com.dxcar.launcher.DxCarApp.instance?.preferences?.update { cur ->
                                cur.copy(mapNorthUpMode = !cur.mapNorthUpMode)
                            }
                            followVehicle = true
                        }
                    )
                    SmallGlassCircleButton(
                        icon = Icons.Rounded.Layers,
                        contentDescription = str("切換底圖圖層與即時路況", "切换底图图层与实时路况", "Map layers & live traffic", "地図レイヤーと交通状況"),
                        active = showLayerPicker || trafficEnabled || mapEngineStyle != MapEngineStyle.GOOGLE_TRAFFIC_TILES,
                        onClick = { showLayerPicker = !showLayerPicker }
                    )
                    SmallGlassCircleButton(
                        icon = Icons.Rounded.MyLocation,
                        contentDescription = str("置中車位", "居中车位", "Center on vehicle", "現在地に戻る"),
                        active = followVehicle,
                        onClick = { followVehicle = true }
                    )
                    SmallGlassCircleButton(
                        icon = Icons.Rounded.OpenInFull,
                        contentDescription = if (isFullscreen) {
                            str("返回分割", "返回分屏", "Exit fullscreen", "分割表示に戻る")
                        } else {
                            str("全螢幕地圖", "全屏地图", "Fullscreen map", "全画面マップ")
                        },
                        active = isFullscreen,
                        onClick = onToggleFullscreen
                    )
                }
            }
        }

        // INTERACTIVE MAP LOCATION DETECTION & CONFIRMATION BAR
        val confirmData = pendingMapConfirm
        AnimatedVisibility(
            visible = confirmData != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(start = 12.dp, end = 12.dp, bottom = 12.dp)
        ) {
            val c = confirmData ?: return@AnimatedVisibility
            Surface(
                color = colors.cardSurface.copy(alpha = 0.96f),
                shape = RoundedCornerShape(20.dp),
                shadowElevation = 14.dp,
                modifier = Modifier
                    .fillMaxWidth(0.86f)
                    .border(1.5.dp, colors.accent, RoundedCornerShape(20.dp))
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Rounded.Place, contentDescription = null, tint = Color(0xFFEF4444), modifier = Modifier.size(20.dp))
                            Text(
                                text = str(
                                    "地圖判定確認 · ${c.label}",
                                    "地图判定确认 · ${c.label}",
                                    "Confirm on Map · ${c.label}",
                                    "地図で位置確認 · ${c.label}"
                                ),
                                color = colors.primaryText,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.ExtraBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Surface(
                            color = when {
                                c.isDetecting -> colors.accent.copy(alpha = 0.2f)
                                c.candidates.isNotEmpty() -> Color(0x2822C55E)
                                else -> Color(0x28F59E0B)
                            },
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(
                                text = when {
                                    c.isDetecting -> str("偵測地點中…", "检测地点中…", "Detecting…", "検出中…")
                                    c.manuallyMoved -> str("已手動微調", "已手动微调", "Adjusted", "微調整済")
                                    c.candidates.isNotEmpty() -> str("✓ 已判定地點", "✓ 已判定地点", "✓ Located", "✓ 検出済")
                                    else -> str("請拖曳地圖選點", "请拖拽地图选点", "Drag map to pin", "地図を動かして指定")
                                },
                                color = when {
                                    c.isDetecting -> colors.accent
                                    c.candidates.isNotEmpty() || c.manuallyMoved -> Color(0xFF22C55E)
                                    else -> Color(0xFFF59E0B)
                                },
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }

                    if (c.isDetecting) {
                        LinearProgressIndicator(
                            color = colors.accent,
                            modifier = Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp))
                        )
                    } else {
                        Text(
                            text = c.resolvedSubtitle.ifBlank { c.resolvedTitle.ifBlank { c.rawAddress } },
                            color = colors.primaryText,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = str(
                                "座標：%.5f, %.5f（可直接拖曳地圖微調紅色大頭針位置）",
                                "坐标：%.5f, %.5f（可直接拖拽地图微调红色大头针位置）",
                                "Coords: %.5f, %.5f (Drag map to fine-tune red pin)",
                                "座標：%.5f, %.5f（地図をドラッグして赤ピン位置を微調整できます）"
                            ).format(Locale.US, c.pinLat, c.pinLon),
                            color = colors.secondaryText,
                            fontSize = 11.sp
                        )
                    }

                    if (!c.isDetecting && c.candidates.size > 1) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            c.candidates.take(3).forEachIndexed { idx, cand ->
                                val selected = idx == c.selectedIndex && !c.manuallyMoved
                                Surface(
                                    color = if (selected) colors.accent else colors.cardSurfaceElevated,
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier
                                        .border(1.dp, if (selected) colors.accent else colors.cardBorder, RoundedCornerShape(10.dp))
                                        .bouncyClickable {
                                            pendingMapConfirm = c.copy(
                                                selectedIndex = idx,
                                                pinLat = cand.latitude,
                                                pinLon = cand.longitude,
                                                resolvedTitle = cand.name,
                                                resolvedSubtitle = cand.subtitle,
                                                manuallyMoved = false
                                            )
                                            flyMapToPin(cand.latitude, cand.longitude)
                                        }
                                ) {
                                    Text(
                                        text = "${idx + 1}. ${cand.name.take(14)}",
                                        color = if (selected) colors.onAccent else colors.primaryText,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                                    )
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = {
                                val prevSlot = c.slot
                                pendingMapConfirm = null
                                followVehicle = true
                                if (prevSlot == MapConfirmSlot.SEARCH_NAV) {
                                    showBuiltInSearchBox = true
                                } else {
                                    showBuiltInSearchBox = true
                                    showNavShortcutEditor = true
                                }
                            },
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(str("返回修改", "返回修改", "Back", "戻る"), fontSize = 12.sp)
                        }
                        Button(
                            enabled = !c.isDetecting && (c.pinLat != 0.0 || c.pinLon != 0.0),
                            onClick = {
                                val prefs = com.dxcar.launcher.DxCarApp.instance?.preferences
                                when (c.slot) {
                                    MapConfirmSlot.HOME -> {
                                        prefs?.update { cur ->
                                            cur.copy(
                                                homeAddress = c.rawAddress,
                                                homeLat = c.pinLat,
                                                homeLon = c.pinLon
                                            )
                                        }
                                        val nextWorkAddr = prefs?.config?.value?.workAddress?.trim().orEmpty()
                                        val nextWorkLat = prefs?.config?.value?.workLat ?: 0.0
                                        if (nextWorkAddr.isNotBlank() && nextWorkLat == 0.0) {
                                            val wLbl = prefs?.config?.value?.workShortcutLabel?.takeIf { it.isNotBlank() }
                                                ?: DxCarI18n.tr("公司", "公司", "Work", "職場")
                                            triggerMapLocationConfirm(MapConfirmSlot.WORK, wLbl, nextWorkAddr)
                                        } else {
                                            pendingMapConfirm = null
                                            followVehicle = true
                                        }
                                    }
                                    MapConfirmSlot.WORK -> {
                                        prefs?.update { cur ->
                                            cur.copy(
                                                workAddress = c.rawAddress,
                                                workLat = c.pinLat,
                                                workLon = c.pinLon
                                            )
                                        }
                                        pendingMapConfirm = null
                                        followVehicle = true
                                    }
                                    MapConfirmSlot.NEW_CUSTOM -> {
                                        prefs?.update { cur ->
                                            cur.copy(
                                                customNavShortcuts = cur.customNavShortcuts + com.dxcar.launcher.data.NavAddressShortcut(
                                                    label = c.label,
                                                    address = c.rawAddress,
                                                    latitude = c.pinLat,
                                                    longitude = c.pinLon
                                                )
                                            )
                                        }
                                        pendingMapConfirm = null
                                        followVehicle = true
                                    }
                                    MapConfirmSlot.SEARCH_NAV -> {
                                        val dest = NavSearchResult(
                                            name = c.resolvedTitle.ifBlank { c.label },
                                            subtitle = c.resolvedSubtitle.ifBlank { c.rawAddress },
                                            latitude = c.pinLat,
                                            longitude = c.pinLon
                                        )
                                        pendingMapConfirm = null
                                        followVehicle = true
                                        onStartBuiltInNavResult(dest)
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = colors.accent, contentColor = colors.onAccent),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = if (c.slot == MapConfirmSlot.SEARCH_NAV) {
                                    str("確認並開始導航", "确认并开始导航", "Confirm & Navigate", "確定して案内開始")
                                } else {
                                    str("確認此地點並儲存", "确认此地点并保存", "Confirm & Save Pin", "この位置で保存")
                                },
                                fontSize = 12.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }
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
                        Text(str("地圖圖層與路況", "地图图层与路况", "Map Layers & Traffic", "地図レイヤーと交通状況"), color = colors.primaryText, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        IconButton(onClick = { showLayerPicker = false }, modifier = Modifier.size(26.dp)) {
                            Icon(Icons.Rounded.Close, contentDescription = str("關閉", "关闭", "Close", "閉じる"), tint = colors.secondaryText, modifier = Modifier.size(18.dp))
                        }
                    }

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        MapEngineStyle.visibleEntries.forEach { styleItem ->
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
                                    text = DxCarI18n.mapEngineLabel(styleItem),
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
                            Text(str("疊加 Google 即時路況", "叠加 Google 实时路况", "Overlay Google Live Traffic", "Google リアルタイム交通状況を重ねる"), color = colors.primaryText, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            Text(str("適用於標準與衛星混合圖層", "适用于标准与卫星混合图层", "Available on Standard & Satellite layers", "標準マップおよび衛星写真レイヤーに対応"), color = colors.secondaryText, fontSize = 10.sp)
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
            val dateLocale = when (currentLang) {
                EffectiveLanguage.ZH_TW -> Locale.TAIWAN
                EffectiveLanguage.ZH_CN -> Locale.SIMPLIFIED_CHINESE
                EffectiveLanguage.EN -> Locale.US
                EffectiveLanguage.JA -> Locale.JAPAN
            }
            val dateFmt = remember(currentLang) { SimpleDateFormat("yyyy/MM/dd", dateLocale) }
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
                                text = str(
                                    "方案 A · 離線地圖下載與自動更新判斷",
                                    "方案 A · 离线地图下载与自动更新判断",
                                    "Plan A · Offline Maps & Smart Update",
                                    "プラン A · オフライン地図と自動更新判定"
                                ),
                                color = colors.primaryText,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = str(
                                    "無網路／地下室／山區自動使用離線快取圖磚；可自選更新提醒週期",
                                    "无网络／地下室／山区自动使用离线缓存图砖；可自选更新提醒周期",
                                    "Auto-uses cached tiles in tunnels, basements & mountains; customizable update reminders",
                                    "圏外・地下・山間部で自動的にキャッシュ地図を使用。更新通知周期を選択可能"
                                ),
                                color = colors.secondaryText,
                                fontSize = 11.sp
                            )
                        }
                        IconButton(onClick = { showOfflineSheet = false }) {
                            Icon(Icons.Rounded.Close, contentDescription = str("關閉", "关闭", "Close", "閉じる"), tint = colors.primaryText)
                        }
                    }

                    // User-selectable update reminder policy
                    Text(str("何時提醒更新離線地圖：", "何时提醒更新离线地图：", "Offline map update reminder:", "オフライン地図の更新通知タイミング："), color = colors.primaryText, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
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
                                    text = DxCarI18n.offlinePolicyLabel(pol),
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
                                Text(str("取消", "取消", "Cancel", "キャンセル"), fontSize = 11.sp)
                            }
                        }
                    }

                    // Smart GPS Current-Region & Entire-Country One-Tap Batch Download Buttons!
                    val detectedLoc = remember(location.latitude, location.longitude, currentLang) {
                        OfflineMapManager.describeDetectedLocation(location.latitude, location.longitude, currentLang)
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
                            Text(
                                str(
                                    "📍 下載所在區域（${detectedLoc.localRegionTitleZh}）",
                                    "📍 下载所在区域（${detectedLoc.localRegionTitleZh}）",
                                    "📍 Download Local (${detectedLoc.localRegionTitleZh})",
                                    "📍 現在地エリアを保存（${detectedLoc.localRegionTitleZh}）"
                                ),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Button(
                            onClick = { onDownloadOfflineRegion(OfflineMapManager.TOKEN_AUTO_ENTIRE_COUNTRY) },
                            enabled = offlineState.downloadingRegionId == null,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF22C55E), contentColor = Color.White),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                str(
                                    "🌏 全下載所在國家（${detectedLoc.countryTitleZh}）",
                                    "🌏 全下载所在国家（${detectedLoc.countryTitleZh}）",
                                    "🌏 Download Country (${detectedLoc.countryTitleZh})",
                                    "🌏 国全体を保存（${detectedLoc.countryTitleZh}）"
                                ),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // One-tap Clear All & Clean Re-download All Offline Maps
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                com.dxcar.launcher.DxCarApp.instance?.offlineMapManager?.deleteAllOfflineRegions()
                            },
                            enabled = offlineState.downloadingRegionId == null,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                str("🗑️ 清空全部離線圖資", "🗑️ 清空全部离线图资", "🗑️ Clear All Maps", "🗑️ 全キャッシュ削除"),
                                color = Color(0xFFEF4444),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        OutlinedButton(
                            onClick = {
                                com.dxcar.launcher.DxCarApp.instance?.offlineMapManager?.clearAndRedownloadAll(
                                    currentLat = location.latitude,
                                    currentLon = location.longitude,
                                    style = mapEngineStyle
                                )
                            },
                            enabled = offlineState.downloadingRegionId == null,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                str("🔄 清空並重新全區下載", "🔄 清空并重新全区下载", "🔄 Reset & Redownload All", "🔄 削除して一括再保存"),
                                color = colors.accent,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
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
                                        text = OfflineMapManager.presetTitleStr(preset.id, preset.titleZh),
                                        color = colors.primaryText,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = if (rec != null) {
                                            val dateStr = dateFmt.format(Date(rec.downloadedAtMs))
                                            val mbStr = "%.1f".format(Locale.US, rec.approxSizeMb)
                                            str(
                                                "✓ 已下載 ($dateStr · 約 $mbStr MB)",
                                                "✓ 已下载 ($dateStr · 约 $mbStr MB)",
                                                "✓ Downloaded ($dateStr · ~$mbStr MB)",
                                                "✓ 保存済み ($dateStr · 約 $mbStr MB)"
                                            )
                                        } else {
                                            OfflineMapManager.presetSubtitleStr(preset.id, preset.subtitleZh)
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
                                                isDownloadingThis -> str("下載中…", "下载中…", "Downloading…", "保存中…")
                                                rec != null -> str("更新圖資", "更新图资", "Update", "更新")
                                                else -> str("下載離線", "下载离线", "Download", "保存")
                                            },
                                            fontSize = 12.sp
                                        )
                                    }
                                    if (rec != null) {
                                        IconButton(
                                            onClick = { onDeleteOfflineRegion(preset.id) },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(Icons.Rounded.DeleteOutline, contentDescription = str("刪除離線包", "删除离线包", "Delete offline map", "オフライン地図を削除"), tint = colors.secondaryText, modifier = Modifier.size(18.dp))
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

/**
 * Attempts to read Home & Work destinations from Google Maps Launcher Shortcuts
 * when DX-Car Launcher holds LauncherApps shortcut host permission (default Home).
 * Returns Triple(detectedHomeAddress, detectedWorkAddress, hasShortcutHostPermission).
 */
private fun readGoogleMapsHomeWorkShortcuts(context: Context): Triple<String?, String?, Boolean> {
    val launcherApps = context.getSystemService(LauncherApps::class.java)
        ?: return Triple(null, null, false)
    val hasPerm = runCatching { launcherApps.hasShortcutHostPermission() }.getOrDefault(false)
    if (!hasPerm) return Triple(null, null, false)

    var detectedHome: String? = null
    var detectedWork: String? = null

    runCatching {
        val query = LauncherApps.ShortcutQuery()
            .setPackage("com.google.android.apps.maps")
            .setQueryFlags(
                LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or
                    LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED or
                    LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST
            )
        val shortcuts = launcherApps.getShortcuts(query, Process.myUserHandle()).orEmpty()
        for (sc in shortcuts) {
            val labelText = "${sc.shortLabel ?: ""} ${sc.longLabel ?: ""} ${sc.id}".lowercase()
            val uriStr = sc.intent?.dataString.orEmpty()
            val extracted = extractMapsShortcutDestination(uriStr)
                ?: sc.intent?.getStringExtra("q")?.trim()?.takeIf { it.isNotBlank() }
                ?: sc.intent?.getStringExtra("daddr")?.trim()?.takeIf { it.isNotBlank() }
            if (extracted.isNullOrBlank()) continue

            val isHome = labelText.contains("home") || labelText.contains("住家") ||
                labelText.contains("回家") || labelText.contains("自宅")
            val isWork = labelText.contains("work") || labelText.contains("公司") ||
                labelText.contains("上班") || labelText.contains("職場") || labelText.contains("勤務")

            if (isHome && detectedHome.isNullOrBlank() && !isGenericHomeWorkWord(extracted)) {
                detectedHome = extracted
            } else if (isWork && detectedWork.isNullOrBlank() && !isGenericHomeWorkWord(extracted)) {
                detectedWork = extracted
            }
        }
    }

    return Triple(detectedHome, detectedWork, true)
}

private fun extractMapsShortcutDestination(rawUri: String): String? {
    if (rawUri.isBlank()) return null
    return runCatching {
        val uri = Uri.parse(rawUri)
        val q = uri.getQueryParameter("q") ?: uri.getQueryParameter("daddr")
        if (!q.isNullOrBlank()) return@runCatching q.trim()
        if (rawUri.startsWith("google.navigation:", ignoreCase = true)) {
            val part = rawUri.substringAfter("q=", "").substringBefore("&")
            if (part.isNotBlank()) return@runCatching Uri.decode(part).trim()
        }
        null
    }.getOrNull()
}

private fun isGenericHomeWorkWord(value: String): Boolean {
    val low = value.trim().lowercase()
    return low in setOf("home", "work", "回家", "住家", "公司", "自宅", "職場")
}

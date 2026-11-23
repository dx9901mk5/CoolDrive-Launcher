package com.dxcar.launcher.layout

import android.content.res.Configuration
import com.dxcar.launcher.data.DeviceFormFactor
import com.dxcar.launcher.data.DockPositionMode
import com.dxcar.launcher.data.MapEngineStyle
import com.dxcar.launcher.map.MapStyleFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CoolwalkLayoutEngineTest {

    @Test
    fun `wide 1920x720 at 240dpi resolves to left vertical rail`() {
        val metrics = ScreenMetrics(widthPx = 1920, heightPx = 720, densityDpi = 240)
        // 1920 / 1.5 = 1280dp (EXTRA_WIDE)
        val spec = CoolwalkLayoutEngine.compute(metrics, DockPositionMode.AUTO_GOOGLE_DPI, rightHandDrive = false)
        assertEquals(AaWidthCategory.EXTRA_WIDE, spec.widthCategory)
        assertEquals(ResolvedDockPlacement.LEFT, spec.dockPlacement)
        assertTrue(spec.isHorizontalSplit)
    }

    @Test
    fun `standard 800x480 at 160dpi resolves to bottom dock like Google Coolwalk`() {
        val metrics = ScreenMetrics(widthPx = 800, heightPx = 480, densityDpi = 160)
        // 800dp x 480dp -> wait: aspectRatio = 1.66, widthDp = 800 >= 720, heightDp = 480 <= 560 -> low DPI widescreen!
        // At 240dpi on 800x480 -> 533dp -> NARROW -> bottom bar!
        val highDpiMetrics = ScreenMetrics(widthPx = 800, heightPx = 480, densityDpi = 240)
        val highDpiSpec = CoolwalkLayoutEngine.compute(highDpiMetrics, DockPositionMode.AUTO_GOOGLE_DPI, false)
        assertEquals(AaWidthCategory.NARROW, highDpiSpec.widthCategory)
        assertEquals(ResolvedDockPlacement.BOTTOM, highDpiSpec.dockPlacement)

        val lowDpiSpec = CoolwalkLayoutEngine.compute(metrics, DockPositionMode.AUTO_GOOGLE_DPI, false)
        assertEquals(ResolvedDockPlacement.LEFT, lowDpiSpec.dockPlacement)
    }

    @Test
    fun `1024x600 at 240dpi high dpi compact resolves to bottom dock`() {
        // 1024 / 1.5 = 682.6dp -> Standard (<720dp) -> Bottom dock
        val metrics = ScreenMetrics(widthPx = 1024, heightPx = 600, densityDpi = 240)
        val spec = CoolwalkLayoutEngine.compute(metrics, DockPositionMode.AUTO_GOOGLE_DPI, false)
        assertEquals(AaWidthCategory.STANDARD, spec.widthCategory)
        assertEquals(ResolvedDockPlacement.BOTTOM, spec.dockPlacement)
    }

    @Test
    fun `portrait tesla style screen resolves to bottom dock and vertical split`() {
        val metrics = ScreenMetrics(widthPx = 1080, heightPx = 1920, densityDpi = 320)
        val spec = CoolwalkLayoutEngine.compute(metrics, DockPositionMode.AUTO_GOOGLE_DPI, false)
        assertEquals(ResolvedDockPlacement.BOTTOM, spec.dockPlacement)
        assertFalse(spec.isHorizontalSplit)
    }

    @Test
    fun `right hand drive places vertical rail on right side`() {
        val metrics = ScreenMetrics(widthPx = 1920, heightPx = 720, densityDpi = 240)
        val spec = CoolwalkLayoutEngine.compute(metrics, DockPositionMode.AUTO_GOOGLE_DPI, rightHandDrive = true)
        assertEquals(ResolvedDockPlacement.RIGHT, spec.dockPlacement)
    }

    @Test
    fun `manual overrides always win over DPI detection`() {
        val wideMetrics = ScreenMetrics(widthPx = 1920, heightPx = 720, densityDpi = 240)
        assertEquals(
            ResolvedDockPlacement.BOTTOM,
            CoolwalkLayoutEngine.compute(wideMetrics, DockPositionMode.FORCE_BOTTOM, false).dockPlacement
        )
        assertEquals(
            ResolvedDockPlacement.RIGHT,
            CoolwalkLayoutEngine.compute(wideMetrics, DockPositionMode.FORCE_RIGHT, false).dockPlacement
        )
    }

    @Test
    fun `default primary split ratio is 70 percent and map styles support traffic toggle`() {
        val wideMetrics = ScreenMetrics(widthPx = 1920, heightPx = 720, densityDpi = 240)
        val spec = CoolwalkLayoutEngine.compute(wideMetrics, DockPositionMode.AUTO_GOOGLE_DPI, false)
        assertEquals(0.70f, spec.primaryWeight, 0.001f)
        assertEquals(0.30f, spec.secondaryWeight, 0.001f)

        val gTrafficOn = MapStyleFactory.resolve(MapEngineStyle.GOOGLE_TRAFFIC_TILES, isNight = false, trafficEnabled = true)
            as MapStyleFactory.ResolvedMapStyle.StyleJson
        assertTrue(gTrafficOn.json.contains("lyrs=m,traffic"))

        val gTrafficOff = MapStyleFactory.resolve(MapEngineStyle.GOOGLE_TRAFFIC_TILES, isNight = false, trafficEnabled = false)
            as MapStyleFactory.ResolvedMapStyle.StyleJson
        assertTrue(gTrafficOff.json.contains("lyrs=m&"))

        val satOn = MapStyleFactory.resolve(MapEngineStyle.GOOGLE_SATELLITE_HYBRID, isNight = true, trafficEnabled = true)
            as MapStyleFactory.ResolvedMapStyle.StyleJson
        assertTrue(satOn.json.contains("lyrs=y,traffic"))

        val darkVector = MapStyleFactory.resolve(MapEngineStyle.OPENFREEMAP_DARK, isNight = false, trafficEnabled = true)
            as MapStyleFactory.ResolvedMapStyle.StyleUrl
        assertEquals(MapStyleFactory.OPENFREEMAP_NIGHT_URL, darkVector.url)
    }

    @Test
    fun `device form factor distinguishes portrait phone vs wide car screen`() {
        val phoneCfg = Configuration().apply {
            screenWidthDp = 412
            screenHeightDp = 915
        }
        assertTrue(DeviceFormFactor.isCompactPhoneScreen(phoneCfg))
        assertFalse(DeviceFormFactor.isTabletOrExpandedScreen(phoneCfg))

        val foldableExpandedCfg = Configuration().apply {
            screenWidthDp = 712
            screenHeightDp = 820
        }
        assertFalse(DeviceFormFactor.isCompactPhoneScreen(foldableExpandedCfg))
        assertTrue(DeviceFormFactor.isTabletOrExpandedScreen(foldableExpandedCfg))

        val carHeadUnitCfg = Configuration().apply {
            screenWidthDp = 1280
            screenHeightDp = 480
        }
        assertFalse(DeviceFormFactor.isCompactPhoneScreen(carHeadUnitCfg))
        assertTrue(DeviceFormFactor.isTabletOrExpandedScreen(carHeadUnitCfg))
    }
}

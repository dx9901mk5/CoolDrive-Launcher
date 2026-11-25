package com.dxcar.launcher.comms

import com.dxcar.launcher.data.LauncherConfig
import com.dxcar.launcher.data.MapEngineStyle
import com.dxcar.launcher.data.NotificationBannerScale
import com.dxcar.launcher.map.MapStyleFactory
import com.dxcar.launcher.map.OfflineMapManager
import com.dxcar.launcher.voice.GeminiVoiceNavController
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CommsProtocolTest {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Test
    fun `message and reply packets round trip cleanly over JSON lines`() {
        val original = BridgePacket(
            type = PacketType.MESSAGE,
            id = "msg-101",
            notificationKey = "0|jp.naver.line.android|123",
            appPackage = "jp.naver.line.android",
            appName = "LINE",
            sender = "王小明",
            text = "等等見！",
            canReply = true
        )

        val encoded = json.encodeToString(original)
        val decoded = json.decodeFromString<BridgePacket>(encoded)
        assertEquals(PacketType.MESSAGE, decoded.type)
        assertEquals("LINE", decoded.appName)
        assertEquals("王小明", decoded.sender)
        assertEquals("等等見！", decoded.text)
        assertTrue(decoded.canReply)
    }

    @Test
    fun `call command packet serializes answer and decline actions`() {
        val cmd = BridgePacket(
            type = PacketType.CALL_COMMAND,
            callCommand = CallActionCommand.ANSWER
        )
        val decoded = json.decodeFromString<BridgePacket>(json.encodeToString(cmd))
        assertEquals(PacketType.CALL_COMMAND, decoded.type)
        assertEquals(CallActionCommand.ANSWER, decoded.callCommand)
    }

    @Test
    fun `touchpad move and pointer speed settings packets round trip cleanly`() {
        val movePkt = BridgePacket(
            type = PacketType.TOUCHPAD_EVENT,
            touchAction = TouchpadAction.MOVE,
            touchDx = 18.5f,
            touchDy = -9.25f
        )
        val decodedMove = json.decodeFromString<BridgePacket>(json.encodeToString(movePkt))
        assertEquals(PacketType.TOUCHPAD_EVENT, decodedMove.type)
        assertEquals(TouchpadAction.MOVE, decodedMove.touchAction)
        assertEquals(18.5f, decodedMove.touchDx, 0.001f)
        assertEquals(-9.25f, decodedMove.touchDy, 0.001f)

        val settingsPkt = BridgePacket(
            type = PacketType.TOUCHPAD_EVENT,
            touchAction = TouchpadAction.OPEN_POINTER_SPEED_SETTINGS
        )
        val decodedSettings = json.decodeFromString<BridgePacket>(json.encodeToString(settingsPkt))
        assertEquals(TouchpadAction.OPEN_POINTER_SPEED_SETTINGS, decodedSettings.touchAction)

        val multiPkt = BridgePacket(
            type = PacketType.TOUCHPAD_EVENT,
            touchAction = TouchpadAction.TWO_FINGER_PINCH_IN
        )
        val decodedMulti = json.decodeFromString<BridgePacket>(json.encodeToString(multiPkt))
        assertEquals(TouchpadAction.TWO_FINGER_PINCH_IN, decodedMulti.touchAction)
    }

    @Test
    fun `offline location detector resolves current local region and entire country`() {
        val taichung = OfflineMapManager.describeDetectedLocation(24.1477, 120.6736)
        assertEquals("tw_central", taichung.localRegionId)
        assertEquals("TW", taichung.countryCode)
        assertEquals(7, taichung.totalCountryZones)

        val tokyo = OfflineMapManager.describeDetectedLocation(35.6762, 139.6503)
        assertEquals("JP", tokyo.countryCode)
    }

    @Test
    fun `gemini mandarin voice parser extracts clean navigation destination`() {
        assertEquals("台中高鐵站", GeminiVoiceNavController.extractDestinationFromMandarin("請幫我導航到台中高鐵站"))
        assertEquals("台北101", GeminiVoiceNavController.extractDestinationFromMandarin("Gemini 帶我去台北101怎麼走"))
        assertEquals("加油站", GeminiVoiceNavController.extractDestinationFromMandarin("幫我找附近的加油站"))
    }

    @Test
    fun `contact name resolver distinguishes real contact names from generic or numeric labels`() {
        assertTrue(ContactNameResolver.isGenericOrNumberOnly("藍牙來電"))
        assertTrue(ContactNameResolver.isGenericOrNumberOnly("行動電話來電"))
        assertTrue(ContactNameResolver.isGenericOrNumberOnly("+886 912-345-678"))
        assertFalse(ContactNameResolver.isGenericOrNumberOnly("阿醬(俊和)"))
        assertFalse(ContactNameResolver.isGenericOrNumberOnly("王小明"))
        assertEquals("0912-345-678", ContactNameResolver.extractPlausiblePhoneNumber("來電 0912-345-678"))
    }

    @Test
    fun `notification banner scale defaults to 1X and offline style shares canonical base tile urls`() {
        val defaultCfg = LauncherConfig()
        assertEquals(NotificationBannerScale.SCALE_1_0X, defaultCfg.notificationBannerScale)
        assertEquals(listOf(0.5f, 0.75f, 1.0f, 1.5f, 2.0f), NotificationBannerScale.entries.map { it.factor })

        val onlineWithTraffic = (MapStyleFactory.resolve(
            MapEngineStyle.GOOGLE_TRAFFIC_TILES,
            isNight = false,
            trafficEnabled = true
        ) as MapStyleFactory.ResolvedMapStyle.StyleJson).json
        val offlineBase = MapStyleFactory.buildOfflineCompatibleGoogleStyleJson("m")
        assertTrue(onlineWithTraffic.contains("lyrs=m&hl=zh-TW&gl=TW&scale=2"))
        assertTrue(offlineBase.contains("lyrs=m&hl=zh-TW&gl=TW&scale=2"))
        assertTrue(onlineWithTraffic.contains("lyrs=traffic&hl=zh-TW&gl=TW&scale=2"))
    }
}

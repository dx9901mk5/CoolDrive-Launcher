package com.dxcar.launcher.comms

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
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
}

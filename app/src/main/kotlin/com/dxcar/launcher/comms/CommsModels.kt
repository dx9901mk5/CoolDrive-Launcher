package com.dxcar.launcher.comms

import kotlinx.serialization.Serializable

@Serializable
enum class PacketType {
    MESSAGE,
    CALL_STATE,
    REPLY_COMMAND,
    MARK_READ_COMMAND,
    CALL_COMMAND,
    DEVICE_INFO,
    PING,
    PONG
}

@Serializable
enum class CallStatus {
    IDLE,
    RINGING,
    ACTIVE
}

@Serializable
enum class CallActionCommand {
    ANSWER,
    DECLINE
}

@Serializable
data class BridgePacket(
    val type: PacketType,
    val id: String = "",
    val notificationKey: String = "",
    val appPackage: String = "",
    val appName: String = "",
    val sender: String = "",
    val text: String = "",
    val phoneNumber: String = "",
    val callStatus: CallStatus = CallStatus.IDLE,
    val callCommand: CallActionCommand? = null,
    val canReply: Boolean = false,
    val timestampMs: Long = System.currentTimeMillis(),
    val deviceName: String = ""
)

data class CarMessageItem(
    val id: String,
    val notificationKey: String,
    val appPackage: String,
    val appName: String,
    val sender: String,
    val text: String,
    val timestampMs: Long,
    val canReply: Boolean,
    val fromPhoneBridge: Boolean,
    val isRead: Boolean = false
)

data class CarCallState(
    val status: CallStatus = CallStatus.IDLE,
    val callerName: String = "",
    val callerNumber: String = "",
    val sourceApp: String = "藍牙電話",
    val startedAtMs: Long = 0L,
    val fromPhoneBridge: Boolean = false
)

data class ActiveNavBanner(
    val instruction: String,
    val distance: String,
    val etaText: String,
    val subText: String,
    val updatedAtMs: Long = System.currentTimeMillis()
)

enum class BridgeConnectionStatus(val labelZh: String) {
    DISABLED("未啟用"),
    LISTENING("等待已配對手機連線…"),
    CONNECTING("正在透過藍牙連線 AI Box…"),
    CONNECTED("已連線同步中"),
    ERROR("藍牙通道未就緒")
}

data class BridgeStatusState(
    val status: BridgeConnectionStatus = BridgeConnectionStatus.DISABLED,
    val peerName: String = "",
    val lastSyncedMs: Long = 0L,
    val recentLogs: List<String> = emptyList()
)

package com.cooldrive.launcher.comms

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.telecom.TelecomManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.cooldrive.launcher.CoolDriveApp
import com.cooldrive.launcher.MainActivity
import com.cooldrive.launcher.R
import com.cooldrive.launcher.data.DeviceRole
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.UUID

/**
 * Classic Bluetooth RFCOMM SPP Bridge between Paired Phone <-> AI Box:
 * - AI Box Role: Listens on SPP UUID for incoming paired phone connection; receives real-time
 *   LINE/Messenger/WhatsApp/SMS/Calls and dispatches Quick Reply / Mark Read / Call Actions back.
 * - Phone Companion Role: Auto-connects to selected paired AI Box; streams phone notifications
 *   and calls; executes remote RemoteInput replies and Telecom call controls from the car.
 */
class BluetoothCompanionService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private var workerJob: Job? = null
    private var serverSocket: BluetoothServerSocket? = null
    @Volatile
    private var activeSocket: BluetoothSocket? = null
    @Volatile
    private var activeWriter: BufferedWriter? = null
    private val writeLock = Any()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        activeService = this
        ensureChannel()
        startForegroundSafe("藍牙同步橋接準備中…")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_RECONNECT -> {
                restartWorker()
            }
            else -> {
                if (workerJob == null || workerJob?.isActive != true) {
                    restartWorker()
                }
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        if (activeService == this) activeService = null
        closeSockets()
        scope.cancel()
        updateState(BridgeConnectionStatus.DISABLED, "")
    }

    private fun restartWorker() {
        workerJob?.cancel()
        closeSockets()

        val app = CoolDriveApp.instance ?: return
        val role = app.preferences.config.value.deviceRole

        // Bind outbound reply/call hooks when acting as AI Box
        if (role == DeviceRole.AI_BOX_LAUNCHER) {
            app.notificationCenter.onReplyDispatch = { item, replyText ->
                if (item.fromPhoneBridge) {
                    sendPacketInternal(
                        BridgePacket(
                            type = PacketType.REPLY_COMMAND,
                            id = item.id,
                            notificationKey = item.notificationKey,
                            text = replyText
                        )
                    )
                } else {
                    BoxNotificationListenerService.instance?.executeReply(
                        item.notificationKey,
                        replyText
                    ) ?: false
                }
            }
            app.notificationCenter.onMarkReadDispatch = { item ->
                if (item.fromPhoneBridge) {
                    sendPacketInternal(
                        BridgePacket(
                            type = PacketType.MARK_READ_COMMAND,
                            id = item.id,
                            notificationKey = item.notificationKey
                        )
                    )
                } else {
                    BoxNotificationListenerService.instance?.executeMarkRead(item.notificationKey)
                }
            }
            app.notificationCenter.onCallActionDispatch = { cmd ->
                val call = app.notificationCenter.activeCall.value
                if (call.fromPhoneBridge) {
                    sendPacketInternal(
                        BridgePacket(
                            type = PacketType.CALL_COMMAND,
                            callCommand = cmd
                        )
                    )
                } else {
                    BoxNotificationListenerService.instance?.executeCallCommand(cmd) ?: false
                }
            }
            workerJob = scope.launch { runAiBoxServerLoop() }
        } else {
            workerJob = scope.launch { runPhoneClientLoop() }
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun runAiBoxServerLoop() {
        val btManager = getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val adapter = btManager?.adapter

        while (scope.isActive) {
            if (adapter == null || !adapter.isEnabled || !hasBluetoothConnectPermission()) {
                updateState(BridgeConnectionStatus.ERROR, "請開啟藍牙並授予連線權限")
                delay(5_000L)
                continue
            }

            try {
                updateState(BridgeConnectionStatus.LISTENING, "")
                updateNotification("AI Box 等待已配對手機連線…")
                appendLog("AI Box 藍牙橋接通道監聽中")

                val srv = adapter.listenUsingInsecureRfcommWithServiceRecord(
                    SERVICE_NAME,
                    BRIDGE_UUID
                )
                serverSocket = srv
                val client = srv.accept() // blocks until paired phone connects
                runCatching { srv.close() }
                serverSocket = null

                val peerName = runCatching { client.remoteDevice?.name }.getOrNull()
                    ?.takeIf { it.isNotBlank() } ?: "已配對手機"
                handleConnectedSocket(client, peerName, isAiBox = true)
            } catch (e: Exception) {
                closeSockets()
                delay(3_000L)
            }
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun runPhoneClientLoop() {
        val btManager = getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val adapter = btManager?.adapter
        val app = CoolDriveApp.instance ?: return

        while (scope.isActive) {
            val cfg = app.preferences.config.value
            val targetAddress = cfg.companionTargetDeviceAddress
            if (targetAddress.isBlank()) {
                updateState(BridgeConnectionStatus.LISTENING, "尚未選擇配對的 AI Box")
                updateNotification("請在 App 內選擇已配對的 AI Box 藍牙裝置")
                delay(4_000L)
                continue
            }

            if (adapter == null || !adapter.isEnabled || !hasBluetoothConnectPermission()) {
                updateState(BridgeConnectionStatus.ERROR, "請開啟藍牙並授予連線權限")
                delay(5_000L)
                continue
            }

            try {
                val device = adapter.getRemoteDevice(targetAddress)
                val targetName = cfg.companionTargetDeviceName.ifBlank {
                    runCatching { device.name }.getOrNull() ?: targetAddress
                }
                updateState(BridgeConnectionStatus.CONNECTING, targetName)
                updateNotification("正在連線至 $targetName…")

                val socket = device.createInsecureRfcommSocketToServiceRecord(BRIDGE_UUID)
                socket.connect()
                handleConnectedSocket(socket, targetName, isAiBox = false)
            } catch (e: Exception) {
                closeSockets()
                updateState(BridgeConnectionStatus.CONNECTING, cfg.companionTargetDeviceName)
                delay(4_500L)
            }
        }
    }

    private fun handleConnectedSocket(socket: BluetoothSocket, peerName: String, isAiBox: Boolean) {
        activeSocket = socket
        activeWriter = BufferedWriter(OutputStreamWriter(socket.outputStream, Charsets.UTF_8))
        val reader = BufferedReader(InputStreamReader(socket.inputStream, Charsets.UTF_8))

        updateState(BridgeConnectionStatus.CONNECTED, peerName)
        updateNotification(
            if (isAiBox) "已連線手機：$peerName（來電與訊息同步中）"
            else "已連線車機：$peerName（通知推播同步中）"
        )
        appendLog("已連線：$peerName")

        // Send device handshake
        sendPacketInternal(
            BridgePacket(
                type = PacketType.DEVICE_INFO,
                deviceName = Build.MODEL
            )
        )

        try {
            while (scope.isActive && socket.isConnected) {
                val line = reader.readLine() ?: break
                if (line.isBlank()) continue
                val packet = runCatching { json.decodeFromString<BridgePacket>(line) }.getOrNull()
                    ?: continue
                dispatchIncomingPacket(packet, isAiBox)
            }
        } catch (_: Exception) {
            // Disconnected; outer loop will re-listen / reconnect automatically
        } finally {
            appendLog("連線中斷，自動重連中…")
            closeSockets()
        }
    }

    private fun dispatchIncomingPacket(packet: BridgePacket, isAiBox: Boolean) {
        val app = CoolDriveApp.instance ?: return

        when (packet.type) {
            PacketType.PING -> {
                sendPacketInternal(BridgePacket(type = PacketType.PONG))
            }
            PacketType.PONG -> Unit
            PacketType.DEVICE_INFO -> {
                if (packet.deviceName.isNotBlank()) {
                    _bridgeState.value = _bridgeState.value.copy(
                        peerName = packet.deviceName,
                        lastSyncedMs = System.currentTimeMillis()
                    )
                }
            }
            PacketType.MESSAGE -> {
                if (isAiBox) {
                    app.notificationCenter.postIncomingMessage(
                        CarMessageItem(
                            id = packet.id.ifBlank { UUID.randomUUID().toString() },
                            notificationKey = packet.notificationKey,
                            appPackage = packet.appPackage,
                            appName = packet.appName.ifBlank { "訊息" },
                            sender = packet.sender.ifBlank { "聯絡人" },
                            text = packet.text,
                            timestampMs = packet.timestampMs,
                            canReply = packet.canReply,
                            fromPhoneBridge = true
                        )
                    )
                    appendLog("收到 [${packet.appName}] ${packet.sender}")
                }
            }
            PacketType.CALL_STATE -> {
                if (isAiBox) {
                    app.notificationCenter.updateCallState(
                        CarCallState(
                            status = packet.callStatus,
                            callerName = packet.sender.ifBlank { packet.phoneNumber.ifBlank { "來電" } },
                            callerNumber = packet.phoneNumber,
                            sourceApp = packet.appName.ifBlank { "手機來電" },
                            fromPhoneBridge = true
                        )
                    )
                    appendLog("來電狀態：${packet.callStatus.name} ${packet.sender}")
                }
            }
            PacketType.REPLY_COMMAND -> {
                if (!isAiBox) {
                    val ok = BoxNotificationListenerService.instance
                        ?.executeReply(packet.notificationKey, packet.text) ?: false
                    appendLog(
                        if (ok) "已回傳車機回覆：${packet.text}"
                        else "無法回覆通知（可能已被清除）"
                    )
                }
            }
            PacketType.MARK_READ_COMMAND -> {
                if (!isAiBox) {
                    BoxNotificationListenerService.instance?.executeMarkRead(packet.notificationKey)
                    appendLog("已標記手機通知為已讀")
                }
            }
            PacketType.CALL_COMMAND -> {
                if (!isAiBox && packet.callCommand != null) {
                    handleRemoteCallCommandOnPhone(packet.callCommand)
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun handleRemoteCallCommandOnPhone(command: CallActionCommand) {
        val handledByNotif =
            BoxNotificationListenerService.instance?.executeCallCommand(command) == true
        if (handledByNotif) {
            appendLog("已透過通知執行通話指令：${command.name}")
            return
        }
        val telecom = getSystemService(Context.TELECOM_SERVICE) as? TelecomManager ?: return
        runCatching {
            when (command) {
                CallActionCommand.ANSWER -> {
                    @Suppress("DEPRECATION")
                    telecom.acceptRingingCall()
                }
                CallActionCommand.DECLINE -> {
                    telecom.endCall()
                }
            }
            appendLog("已透過 Telecom 執行：${command.name}")
        }
    }

    private fun sendPacketInternal(packet: BridgePacket): Boolean {
        val writer = activeWriter ?: return false
        return try {
            val encoded = json.encodeToString(packet)
            synchronized(writeLock) {
                writer.write(encoded)
                writer.newLine()
                writer.flush()
            }
            _bridgeState.value = _bridgeState.value.copy(lastSyncedMs = System.currentTimeMillis())
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun closeSockets() {
        runCatching { activeWriter?.close() }
        runCatching { activeSocket?.close() }
        runCatching { serverSocket?.close() }
        activeWriter = null
        activeSocket = null
        serverSocket = null
    }

    private fun hasBluetoothConnectPermission(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
    }

    private fun updateState(status: BridgeConnectionStatus, peerName: String) {
        _bridgeState.value = _bridgeState.value.copy(
            status = status,
            peerName = peerName.ifBlank { _bridgeState.value.peerName }
        )
    }

    private fun appendLog(message: String) {
        val stamp = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"))
        val next = (listOf("[$stamp] $message") + _bridgeState.value.recentLogs).take(25)
        _bridgeState.value = _bridgeState.value.copy(
            recentLogs = next,
            lastSyncedMs = System.currentTimeMillis()
        )
    }

    private fun ensureChannel() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            "CoolDrive 藍牙來電與訊息同步",
            NotificationManager.IMPORTANCE_LOW
        )
        nm.createNotificationChannel(channel)
    }

    private fun buildNotification(text: String): Notification {
        val pi = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("CoolDrive 藍牙同步橋接")
            .setContentText(text)
            .setContentIntent(pi)
            .setOngoing(true)
            .build()
    }

    private fun startForegroundSafe(text: String) {
        val notif = buildNotification(text)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val type = if (hasBluetoothConnectPermission()) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
            } else {
                0
            }
            runCatching { startForeground(NOTIF_ID, notif, type) }
                .onFailure { startForeground(NOTIF_ID, notif) }
        } else {
            startForeground(NOTIF_ID, notif)
        }
    }

    private fun updateNotification(text: String) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIF_ID, buildNotification(text))
    }

    companion object {
        val BRIDGE_UUID: UUID = UUID.fromString("7b8f4c10-9a2d-4e5b-8f1a-3c6d9e0a1b2c")
        private const val SERVICE_NAME = "CoolDriveBluetoothBridge"
        private const val CHANNEL_ID = "cooldrive_bt_bridge"
        private const val NOTIF_ID = 2002

        const val ACTION_START = "com.cooldrive.launcher.bt.START"
        const val ACTION_RECONNECT = "com.cooldrive.launcher.bt.RECONNECT"
        const val ACTION_STOP = "com.cooldrive.launcher.bt.STOP"

        @Volatile
        private var activeService: BluetoothCompanionService? = null

        private val _bridgeState = MutableStateFlow(BridgeStatusState())
        val bridgeState: StateFlow<BridgeStatusState> = _bridgeState.asStateFlow()

        fun startOrReconnect(context: Context) {
            val intent = Intent(context, BluetoothCompanionService::class.java).apply {
                action = ACTION_RECONNECT
            }
            runCatching {
                ContextCompat.startForegroundService(context, intent)
            }
        }

        fun stop(context: Context) {
            runCatching {
                context.stopService(Intent(context, BluetoothCompanionService::class.java))
            }
        }

        fun sendPacketFromPhone(packet: BridgePacket): Boolean {
            val svc = activeService ?: return false
            return svc.sendPacketInternal(packet)
        }
    }
}

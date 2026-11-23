package com.dxcar.launcher.comms

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
import com.dxcar.launcher.DxCarApp
import com.dxcar.launcher.MainActivity
import com.dxcar.launcher.R
import com.dxcar.launcher.data.DeviceRole
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
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Collections
import java.util.UUID

/**
 * Dual-Transport Companion Bridge between Paired Phone <-> AI Box:
 * 1. Classic Bluetooth RFCOMM SPP (tries BOTH Secure & Insecure RFCOMM for maximum compatibility)
 * 2. Wi-Fi Hotspot / LAN TCP Direct Bridge (Port 47681 + UDP Discovery 47682 + /proc/net/arp scan)
 *    so even if an AI Box firmware hijacks Bluetooth SPP for CarPlay/SpeedPlay, notifications & calls
 *    stream effortlessly whenever the AI Box shares the phone's hotspot or sits on the same Wi-Fi!
 */
class BluetoothCompanionService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private var btJob: Job? = null
    private var lanJob: Job? = null
    private var heartbeatJob: Job? = null

    private var secureServerSocket: BluetoothServerSocket? = null
    private var insecureServerSocket: BluetoothServerSocket? = null
    private var tcpServerSocket: ServerSocket? = null
    private var udpDiscoverySocket: DatagramSocket? = null

    @Volatile
    private var btSocket: BluetoothSocket? = null
    @Volatile
    private var btWriter: BufferedWriter? = null
    @Volatile
    private var lanSocket: Socket? = null
    @Volatile
    private var lanWriter: BufferedWriter? = null

    private val btWriteLock = Any()
    private val lanWriteLock = Any()
    private val seenPacketIds = Collections.synchronizedSet(LinkedHashSet<String>())

    @Volatile
    private var forceOneShotUntilMs: Long = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        activeService = this
        ensureChannel()
        startForegroundSafe("雙通道通知橋接準備中…")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_BT_DISCONNECTED -> {
                closeBtSocketsOnlyClient()
                val app = DxCarApp.instance
                val role = app?.preferences?.config?.value?.deviceRole ?: DeviceRole.AI_BOX_LAUNCHER
                if (role == DeviceRole.PHONE_COMPANION &&
                    lanSocket?.isConnected != true &&
                    !shouldPhoneBridgeRunNow(this)
                ) {
                    appendLog("車機藍牙已斷開，進入待機省電模式（下次連線自動喚醒）")
                    markStandby(this)
                    stopSelf()
                    return START_NOT_STICKY
                }
                updateConnectionSnapshot()
            }
            ACTION_FORCE_SCAN -> {
                forceOneShotUntilMs = System.currentTimeMillis() + 15_000L
                startForegroundSafe("正在搜尋 AI Box（藍牙／Wi-Fi 熱點）…")
                restartWorkers()
            }
            ACTION_RECONNECT -> {
                startForegroundSafe("通知同步橋接運作中…")
                restartWorkers()
            }
            else -> {
                startForegroundSafe("通知同步橋接運作中…")
                if (btJob?.isActive != true || lanJob?.isActive != true) {
                    restartWorkers()
                }
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        if (activeService == this) activeService = null
        closeAllSockets()
        scope.cancel()
        updateConnectionSnapshot()
    }

    private fun restartWorkers() {
        btJob?.cancel()
        lanJob?.cancel()
        heartbeatJob?.cancel()
        closeAllSockets()

        val app = DxCarApp.instance ?: return
        val role = app.preferences.config.value.deviceRole

        if (role == DeviceRole.AI_BOX_LAUNCHER) {
            bindAiBoxOutboundHooks(app)
            btJob = scope.launch { runAiBoxBluetoothLoop() }
            lanJob = scope.launch { runAiBoxLanLoop() }
        } else {
            btJob = scope.launch { runPhoneBluetoothLoop() }
            lanJob = scope.launch { runPhoneLanLoop() }
        }

        heartbeatJob = scope.launch {
            while (isActive) {
                delay(14_000L)
                if (btWriter != null || lanWriter != null) {
                    sendPacketInternal(BridgePacket(type = PacketType.PING, id = UUID.randomUUID().toString()))
                }
                _bridgeState.value = _bridgeState.value.copy(
                    isListenerBound = BoxNotificationListenerService.instance != null
                )
            }
        }
        updateConnectionSnapshot()
    }

    private fun bindAiBoxOutboundHooks(app: DxCarApp) {
        app.notificationCenter.onReplyDispatch = { item, replyText ->
            if (item.fromPhoneBridge) {
                sendPacketInternal(
                    BridgePacket(
                        type = PacketType.REPLY_COMMAND,
                        id = UUID.randomUUID().toString(),
                        notificationKey = item.notificationKey,
                        text = replyText
                    )
                )
            } else {
                BoxNotificationListenerService.instance?.executeReply(item.notificationKey, replyText) ?: false
            }
        }
        app.notificationCenter.onMarkReadDispatch = { item ->
            if (item.fromPhoneBridge) {
                sendPacketInternal(
                    BridgePacket(
                        type = PacketType.MARK_READ_COMMAND,
                        id = UUID.randomUUID().toString(),
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
                        id = UUID.randomUUID().toString(),
                        callCommand = cmd
                    )
                )
            } else {
                BoxNotificationListenerService.instance?.executeCallCommand(cmd) ?: false
            }
        }
    }

    // ==================== CHANNEL A: BLUETOOTH RFCOMM (SECURE + INSECURE) ====================

    @SuppressLint("MissingPermission")
    private suspend fun runAiBoxBluetoothLoop() {
        val btManager = getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val adapter = btManager?.adapter

        while (scope.isActive) {
            if (adapter == null || !adapter.isEnabled || !hasBluetoothConnectPermission()) {
                updateConnectionSnapshot()
                delay(4_000L)
                continue
            }

            try {
                appendLog("AI Box 藍牙 SPP 監聽啟動 (Secure + Insecure)")
                updateConnectionSnapshot()

                val sec = runCatching { adapter.listenUsingRfcommWithServiceRecord(SERVICE_NAME_SEC, BRIDGE_UUID) }.getOrNull()
                val insec = runCatching { adapter.listenUsingInsecureRfcommWithServiceRecord(SERVICE_NAME, BRIDGE_UUID) }.getOrNull()
                secureServerSocket = sec
                insecureServerSocket = insec

                if (sec == null && insec == null) {
                    delay(4_000L)
                    continue
                }

                // Race both Secure and Insecure server sockets cleanly
                val acceptedJobSec = sec?.let { srv ->
                    scope.launch {
                        runCatching {
                            val client = srv.accept()
                            if (btSocket == null) {
                                runCatching { insec?.close() }
                                val name = runCatching { client.remoteDevice?.name }.getOrNull()?.takeIf { it.isNotBlank() } ?: "手機"
                                handleBtSocketSession(client, name, isAiBox = true)
                            } else {
                                runCatching { client.close() }
                            }
                        }
                    }
                }
                val acceptedJobInsec = insec?.let { srv ->
                    scope.launch {
                        runCatching {
                            val client = srv.accept()
                            if (btSocket == null) {
                                runCatching { sec?.close() }
                                val name = runCatching { client.remoteDevice?.name }.getOrNull()?.takeIf { it.isNotBlank() } ?: "手機"
                                handleBtSocketSession(client, name, isAiBox = true)
                            } else {
                                runCatching { client.close() }
                            }
                        }
                    }
                }

                while (scope.isActive &&
                    (acceptedJobSec?.isActive == true || acceptedJobInsec?.isActive == true)
                ) {
                    delay(1_000L)
                }
            } catch (_: Exception) {
                closeBtSockets()
                delay(3_000L)
            }
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun runPhoneBluetoothLoop() {
        val btManager = getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val adapter = btManager?.adapter
        val app = DxCarApp.instance ?: return

        while (scope.isActive) {
            val cfg = app.preferences.config.value
            if (cfg.deviceRole != DeviceRole.PHONE_COMPANION) break

            val targetAddress = cfg.companionTargetDeviceAddress
            val btLinkAlive = targetAddress.isNotBlank() && isTargetBluetoothConnected(this, targetAddress)
            val manualScanActive = System.currentTimeMillis() < forceOneShotUntilMs

            // Event-triggered power saving: do NOT spam RFCOMM connect() when car BT is not connected!
            if (!btLinkAlive && !manualScanActive) {
                if (lanSocket?.isConnected != true && !isWifiHotspotActive(this)) {
                    appendLog("未連接車機藍牙或熱點，進入待機省電模式（連上藍牙自動啟動）")
                    markStandby(this)
                    stopSelf()
                    return
                }
                updateConnectionSnapshot()
                delay(10_000L)
                continue
            }

            if (targetAddress.isBlank() || adapter == null || !adapter.isEnabled || !hasBluetoothConnectPermission()) {
                updateConnectionSnapshot()
                delay(4_000L)
                continue
            }

            try {
                val device = adapter.getRemoteDevice(targetAddress)
                val targetName = cfg.companionTargetDeviceName.ifBlank {
                    runCatching { device.name }.getOrNull() ?: targetAddress
                }
                updateConnectionSnapshot()

                // Try Secure RFCOMM first (much more reliable on Android 12-15 phones), fallback to Insecure
                val socket = runCatching {
                    device.createRfcommSocketToServiceRecord(BRIDGE_UUID).also { it.connect() }
                }.getOrElse {
                    device.createInsecureRfcommSocketToServiceRecord(BRIDGE_UUID).also { it.connect() }
                }

                handleBtSocketSession(socket, targetName, isAiBox = false)
            } catch (_: Exception) {
                closeBtSockets()
                updateConnectionSnapshot()
                delay(4_500L)
            }
        }
    }

    private fun handleBtSocketSession(socket: BluetoothSocket, peerName: String, isAiBox: Boolean) {
        btSocket = socket
        btWriter = BufferedWriter(OutputStreamWriter(socket.outputStream, Charsets.UTF_8))
        val reader = BufferedReader(InputStreamReader(socket.inputStream, Charsets.UTF_8))

        appendLog("藍牙通道已連線：$peerName")
        updateConnectionSnapshot(peerName)

        sendPacketInternal(
            BridgePacket(
                type = PacketType.DEVICE_INFO,
                id = UUID.randomUUID().toString(),
                deviceName = Build.MODEL
            )
        )

        try {
            while (scope.isActive && socket.isConnected) {
                val line = reader.readLine() ?: break
                if (line.isBlank()) continue
                val packet = runCatching { json.decodeFromString<BridgePacket>(line) }.getOrNull() ?: continue
                dispatchIncomingPacket(packet, isAiBox)
            }
        } catch (_: Exception) {
        } finally {
            closeBtSockets()
            appendLog("藍牙通道已斷開")
            updateConnectionSnapshot()
        }
    }

    // ==================== CHANNEL B: WI-FI HOTSPOT / LAN AUTO-DISCOVERY TCP BRIDGE ====================

    private suspend fun runAiBoxLanLoop() {
        // Responder for UDP discovery broadcasts from Phone
        val discoveryJob = scope.launch {
            while (scope.isActive) {
                try {
                    val udp = DatagramSocket(LAN_UDP_PORT).apply { broadcast = true }
                    udpDiscoverySocket = udp
                    val buf = ByteArray(256)
                    while (scope.isActive) {
                        val pkt = DatagramPacket(buf, buf.size)
                        udp.receive(pkt)
                        val msg = String(pkt.data, 0, pkt.length, Charsets.UTF_8)
                        if (msg.startsWith(DISCOVER_QUERY)) {
                            val replyBytes = "$DISCOVER_REPLY|${Build.MODEL}|$LAN_TCP_PORT".toByteArray(Charsets.UTF_8)
                            udp.send(DatagramPacket(replyBytes, replyBytes.size, pkt.address, pkt.port))
                        }
                    }
                } catch (_: Exception) {
                    runCatching { udpDiscoverySocket?.close() }
                    udpDiscoverySocket = null
                    delay(3_000L)
                }
            }
        }

        try {
            while (scope.isActive) {
                try {
                    val srv = ServerSocket(LAN_TCP_PORT).apply { reuseAddress = true }
                    tcpServerSocket = srv
                    appendLog("Wi-Fi 熱點通道監聽中 (:$LAN_TCP_PORT)")
                    while (scope.isActive) {
                        val client = srv.accept()
                        val ip = client.inetAddress?.hostAddress.orEmpty()
                        handleLanSocketSession(client, "手機 ($ip)", isAiBox = true)
                    }
                } catch (_: Exception) {
                    closeLanSockets()
                    delay(3_000L)
                }
            }
        } finally {
            discoveryJob.cancel()
        }
    }

    private suspend fun runPhoneLanLoop() {
        while (scope.isActive) {
            if (lanSocket?.isConnected == true) {
                delay(2_500L)
                continue
            }

            val shouldScanLan = shouldPhoneBridgeRunNow(this) || System.currentTimeMillis() < forceOneShotUntilMs
            if (!shouldScanLan) {
                delay(6_000L)
                continue
            }

            val candidateIps = discoverAiBoxLanIps()
            var connectedAny = false
            for (ip in candidateIps) {
                if (!scope.isActive) break
                val sock = runCatching {
                    Socket().apply {
                        connect(InetSocketAddress(ip, LAN_TCP_PORT), 650)
                    }
                }.getOrNull()
                if (sock != null) {
                    connectedAny = true
                    handleLanSocketSession(sock, "AI Box ($ip)", isAiBox = false)
                    break
                }
            }
            if (!connectedAny) {
                delay(3_500L)
            }
        }
    }

    private fun discoverAiBoxLanIps(): List<String> {
        val found = LinkedHashSet<String>()

        // 1. UDP Broadcast discovery on all local interfaces (covers Hotspot & shared Wi-Fi)
        runCatching {
            DatagramSocket().use { sock ->
                sock.broadcast = true
                sock.soTimeout = 700
                val reqBytes = DISCOVER_QUERY.toByteArray(Charsets.UTF_8)
                val targets = LinkedHashSet<InetAddress>()
                targets.add(InetAddress.getByName("255.255.255.255"))

                val ifaces = NetworkInterface.getNetworkInterfaces()
                while (ifaces.hasMoreElements()) {
                    val ni = ifaces.nextElement()
                    if (!ni.isUp || ni.isLoopback) continue
                    ni.interfaceAddresses.forEach { ia ->
                        ia.broadcast?.let { targets.add(it) }
                        val addr = ia.address
                        if (addr is Inet4Address) {
                            val parts = addr.hostAddress?.split(".")
                            if (parts != null && parts.size == 4) {
                                runCatching {
                                    targets.add(InetAddress.getByName("${parts[0]}.${parts[1]}.${parts[2]}.255"))
                                }
                            }
                        }
                    }
                }

                for (target in targets) {
                    runCatching {
                        sock.send(DatagramPacket(reqBytes, reqBytes.size, target, LAN_UDP_PORT))
                    }
                }

                val buf = ByteArray(256)
                val deadline = System.currentTimeMillis() + 700L
                while (System.currentTimeMillis() < deadline) {
                    val resp = DatagramPacket(buf, buf.size)
                    sock.receive(resp)
                    val text = String(resp.data, 0, resp.length, Charsets.UTF_8)
                    if (text.startsWith(DISCOVER_REPLY)) {
                        resp.address?.hostAddress?.let { found.add(it) }
                    }
                }
            }
        }

        // 2. Scan /proc/net/arp directly for any client connected to the Phone's Wi-Fi Hotspot!
        runCatching {
            val arp = File("/proc/net/arp")
            if (arp.exists() && arp.canRead()) {
                arp.readLines().drop(1).forEach { line ->
                    val cols = line.trim().split(Regex("\\s+"))
                    if (cols.size >= 4) {
                        val ip = cols[0]
                        val flags = cols[2]
                        if (ip.contains(".") && flags != "0x0" && ip != "0.0.0.0") {
                            found.add(ip)
                        }
                    }
                }
            }
        }

        return found.toList()
    }

    private fun handleLanSocketSession(socket: Socket, peerName: String, isAiBox: Boolean) {
        runCatching { socket.tcpNoDelay = true }
        lanSocket = socket
        lanWriter = BufferedWriter(OutputStreamWriter(socket.getOutputStream(), Charsets.UTF_8))
        val reader = BufferedReader(InputStreamReader(socket.inputStream, Charsets.UTF_8))

        appendLog("Wi-Fi 熱點通道已連線：$peerName")
        updateConnectionSnapshot(peerName)

        sendPacketInternal(
            BridgePacket(
                type = PacketType.DEVICE_INFO,
                id = UUID.randomUUID().toString(),
                deviceName = Build.MODEL
            )
        )

        try {
            while (scope.isActive && socket.isConnected && !socket.isClosed) {
                val line = reader.readLine() ?: break
                if (line.isBlank()) continue
                val packet = runCatching { json.decodeFromString<BridgePacket>(line) }.getOrNull() ?: continue
                dispatchIncomingPacket(packet, isAiBox)
            }
        } catch (_: Exception) {
        } finally {
            closeLanSocketsOnlyClient()
            appendLog("Wi-Fi 熱點通道已斷開")
            updateConnectionSnapshot()
        }
    }

    // ==================== PACKET DISPATCH & DEDUPLICATION ====================

    private fun dispatchIncomingPacket(packet: BridgePacket, isAiBox: Boolean) {
        val app = DxCarApp.instance ?: return

        if (packet.id.isNotBlank() &&
            packet.type != PacketType.PING &&
            packet.type != PacketType.PONG &&
            packet.type != PacketType.DEVICE_INFO
        ) {
            if (!seenPacketIds.add(packet.id)) return
            if (seenPacketIds.size > 120) {
                val first = seenPacketIds.firstOrNull()
                if (first != null) seenPacketIds.remove(first)
            }
        }

        when (packet.type) {
            PacketType.PING -> {
                sendPacketInternal(BridgePacket(type = PacketType.PONG, id = packet.id))
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
                    @Suppress("DEPRECATION")
                    telecom.endCall()
                }
            }
            appendLog("已透過 Telecom 執行：${command.name}")
        }
    }

    private fun sendPacketInternal(packet: BridgePacket): Boolean {
        val encoded = json.encodeToString(packet)
        var sent = false

        // Prefer Wi-Fi LAN TCP if open, also try BT if LAN failed
        lanWriter?.let { w ->
            val ok = runCatching {
                synchronized(lanWriteLock) {
                    w.write(encoded)
                    w.newLine()
                    w.flush()
                }
                true
            }.getOrDefault(false)
            if (ok) sent = true else closeLanSocketsOnlyClient()
        }

        if (!sent) {
            btWriter?.let { w ->
                val ok = runCatching {
                    synchronized(btWriteLock) {
                        w.write(encoded)
                        w.newLine()
                        w.flush()
                    }
                    true
                }.getOrDefault(false)
                if (ok) sent = true else closeBtSocketsOnlyClient()
            }
        }

        if (sent) {
            _bridgeState.value = _bridgeState.value.copy(lastSyncedMs = System.currentTimeMillis())
        }
        return sent
    }

    private fun closeBtSocketsOnlyClient() {
        runCatching { btWriter?.close() }
        runCatching { btSocket?.close() }
        btWriter = null
        btSocket = null
    }

    private fun closeBtSockets() {
        closeBtSocketsOnlyClient()
        runCatching { secureServerSocket?.close() }
        runCatching { insecureServerSocket?.close() }
        secureServerSocket = null
        insecureServerSocket = null
    }

    private fun closeLanSocketsOnlyClient() {
        runCatching { lanWriter?.close() }
        runCatching { lanSocket?.close() }
        lanWriter = null
        lanSocket = null
    }

    private fun closeLanSockets() {
        closeLanSocketsOnlyClient()
        runCatching { tcpServerSocket?.close() }
        runCatching { udpDiscoverySocket?.close() }
        tcpServerSocket = null
        udpDiscoverySocket = null
    }

    private fun closeAllSockets() {
        closeBtSockets()
        closeLanSockets()
    }

    private fun hasBluetoothConnectPermission(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
    }

    private fun updateConnectionSnapshot(overridePeer: String = "") {
        val btConnected = btSocket?.isConnected == true && btWriter != null
        val lanConnected = lanSocket?.isConnected == true && lanWriter != null
        val app = DxCarApp.instance
        val role = app?.preferences?.config?.value?.deviceRole ?: DeviceRole.AI_BOX_LAUNCHER

        val transport = when {
            btConnected && lanConnected -> "藍牙 SPP ＋ Wi-Fi 熱點"
            lanConnected -> "Wi-Fi 熱點直連"
            btConnected -> "藍牙 SPP"
            else -> ""
        }
        val status = when {
            btConnected || lanConnected -> BridgeConnectionStatus.CONNECTED
            role == DeviceRole.AI_BOX_LAUNCHER -> BridgeConnectionStatus.LISTENING
            shouldPhoneBridgeRunNow(this) || System.currentTimeMillis() < forceOneShotUntilMs ->
                BridgeConnectionStatus.CONNECTING
            else -> BridgeConnectionStatus.STANDBY
        }
        val currentPeer = overridePeer.ifBlank { _bridgeState.value.peerName }
        _bridgeState.value = _bridgeState.value.copy(
            status = status,
            peerName = currentPeer,
            transportLabel = transport,
            isListenerBound = BoxNotificationListenerService.instance != null
        )

        val notifMsg = when (status) {
            BridgeConnectionStatus.CONNECTED -> "已透過 $transport 連線：$currentPeer"
            BridgeConnectionStatus.LISTENING -> "等待手機藍牙／熱點連線…"
            BridgeConnectionStatus.STANDBY -> "待機省電中（藍牙連上車機自動啟動）"
            else -> "正在連線 AI Box（藍牙／Wi-Fi 熱點）…"
        }
        updateNotification(notifMsg)
    }

    private fun appendLog(message: String) {
        val stamp = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"))
        val next = (listOf("[$stamp] $message") + _bridgeState.value.recentLogs).take(30)
        _bridgeState.value = _bridgeState.value.copy(
            recentLogs = next,
            lastSyncedMs = System.currentTimeMillis()
        )
    }

    private fun ensureChannel() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            "DX-Car Launcher 通知與來電同步橋接",
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
            .setContentTitle("DX-Car Launcher 通知同步橋接")
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
                .onFailure { runCatching { startForeground(NOTIF_ID, notif) } }
        } else {
            runCatching { startForeground(NOTIF_ID, notif) }
        }
    }

    private fun updateNotification(text: String) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIF_ID, buildNotification(text))
    }

    companion object {
        val BRIDGE_UUID: UUID = UUID.fromString("7b8f4c10-9a2d-4e5b-8f1a-3c6d9e0a1b2c")
        private const val SERVICE_NAME = "DxCarBluetoothBridge"
        private const val SERVICE_NAME_SEC = "DxCarBluetoothBridgeSecure"
        private const val CHANNEL_ID = "dxcar_bt_bridge"
        private const val NOTIF_ID = 2002
        private const val LAN_TCP_PORT = 47681
        private const val LAN_UDP_PORT = 47682
        private const val DISCOVER_QUERY = "DXCAR_DISCOVER_V1"
        private const val DISCOVER_REPLY = "DXCAR_BOX_V1"

        const val ACTION_START = "com.dxcar.launcher.bt.START"
        const val ACTION_RECONNECT = "com.dxcar.launcher.bt.RECONNECT"
        const val ACTION_FORCE_SCAN = "com.dxcar.launcher.bt.FORCE_SCAN"
        const val ACTION_BT_DISCONNECTED = "com.dxcar.launcher.bt.BT_DISCONNECTED"
        const val ACTION_STOP = "com.dxcar.launcher.bt.STOP"

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

        /** Manual trigger from Phone UI "立即搜尋重連" button (runs a 15s scan then returns to standby if idle). */
        fun forceReconnect(context: Context) {
            val intent = Intent(context, BluetoothCompanionService::class.java).apply {
                action = ACTION_FORCE_SCAN
            }
            runCatching {
                ContextCompat.startForegroundService(context, intent)
            }
        }

        fun onBluetoothDisconnected(context: Context) {
            if (activeService == null) {
                markStandby(context)
                return
            }
            val intent = Intent(context, BluetoothCompanionService::class.java).apply {
                action = ACTION_BT_DISCONNECTED
            }
            runCatching { context.startService(intent) }
        }

        fun markStandby(context: Context) {
            _bridgeState.value = _bridgeState.value.copy(
                status = BridgeConnectionStatus.STANDBY,
                transportLabel = "",
                isListenerBound = BoxNotificationListenerService.instance != null
            )
        }

        @SuppressLint("MissingPermission")
        fun isTargetBluetoothConnected(context: Context, targetAddress: String): Boolean {
            if (targetAddress.isBlank()) return false
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                return false
            }
            val btManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager ?: return false
            val adapter = btManager.adapter ?: return false
            if (!adapter.isEnabled) return false

            return runCatching {
                val device = adapter.getRemoteDevice(targetAddress)
                val method = device.javaClass.getMethod("isConnected")
                (method.invoke(device) as? Boolean) == true
            }.getOrDefault(false)
        }

        fun isWifiHotspotActive(context: Context): Boolean {
            val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? android.net.wifi.WifiManager
                ?: return false
            return runCatching {
                val m = wm.javaClass.getDeclaredMethod("isWifiApEnabled")
                m.isAccessible = true
                (m.invoke(wm) as? Boolean) == true
            }.getOrDefault(false)
        }

        /** Returns true on Phone only when target car BT is connected OR Wi-Fi Hotspot is active (saves battery!). */
        fun shouldPhoneBridgeRunNow(context: Context): Boolean {
            val app = DxCarApp.instance ?: return false
            val cfg = app.preferences.config.value
            if (cfg.deviceRole == DeviceRole.AI_BOX_LAUNCHER) return true
            return isTargetBluetoothConnected(context, cfg.companionTargetDeviceAddress) ||
                isWifiHotspotActive(context)
        }

        fun stop(context: Context) {
            runCatching {
                context.stopService(Intent(context, BluetoothCompanionService::class.java))
            }
        }

        fun appendExternalLog(message: String) {
            activeService?.appendLog(message) ?: run {
                val stamp = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"))
                val next = (listOf("[$stamp] $message") + _bridgeState.value.recentLogs).take(30)
                _bridgeState.value = _bridgeState.value.copy(recentLogs = next)
            }
        }

        fun syncListenerBoundState(bound: Boolean) {
            _bridgeState.value = _bridgeState.value.copy(isListenerBound = bound)
        }

        fun sendPacketFromPhone(packet: BridgePacket): Boolean {
            val svc = activeService ?: return false
            return svc.sendPacketInternal(packet)
        }
    }
}

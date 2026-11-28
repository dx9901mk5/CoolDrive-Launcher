package com.dxcar.launcher.comms

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.wifi.WifiManager
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
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
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
 * Ultra-Reliable Dual-Transport Companion Bridge between Paired Phone <-> AI Box:
 * 1. All socket writes execute strictly on a dedicated Dispatchers.IO outbound queue
 *    (eliminates Android NetworkOnMainThreadException disconnects when UI or NotificationListener posts!).
 * 2. Classic Bluetooth RFCOMM SPP: tries target device + ANY currently connected bonded car BT device
 *    (Secure + Insecure RFCOMM), plus detects HFP/A2DP audio links so dual-BT AI Boxes stay awake.
 * 3. Bi-Directional Wi-Fi Hotspot / LAN TCP Bridge:
 *    - Phone discovers AI Box (:47681) via UDP broadcast (:47682) + /proc/net/arp + active subnet
 *    - AI Box ALSO discovers Phone (:47683) directly via its Wi-Fi DHCP Gateway IP (100% deterministic on Hotspots!)
 * 4. Queues notifications briefly while waking up so early messages are never lost.
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
    private var writerJob: Job? = null

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

    // Non-blocking IO writer channel so UI & NotificationListener NEVER touch sockets on Main thread!
    private val outboundChannel = Channel<BridgePacket>(
        capacity = 256,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    @Volatile
    private var forceOneShotUntilMs: Long = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        activeService = this
        ensureChannel()
        startForegroundSafe("雙通道通知與觸控橋接準備中…")
        startOutboundWriterLoop()
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
                    !hasOpenTransport() &&
                    !shouldPhoneBridgeRunNow(this)
                ) {
                    appendLog("車機連線已斷開，進入待機省電模式（下次上車自動喚醒）")
                    markStandby(this)
                    stopSelf()
                    return START_NOT_STICKY
                }
                updateConnectionSnapshot()
            }
            ACTION_FORCE_SCAN -> {
                forceOneShotUntilMs = System.currentTimeMillis() + 30_000L
                startForegroundSafe("正在搜尋車機 AI Box（藍牙／Wi-Fi 熱點）…")
                if (hasOpenTransport()) {
                    // Already connected: verify link with PING instead of tearing down working socket!
                    enqueueOutbound(BridgePacket(type = PacketType.PING, id = UUID.randomUUID().toString()))
                    ensureWorkersRunning()
                    updateConnectionSnapshot()
                } else {
                    restartWorkers()
                }
            }
            ACTION_RECONNECT -> {
                startForegroundSafe("通知與觸控橋接運作中…")
                ensureWorkersRunning()
            }
            else -> {
                startForegroundSafe("通知與觸控橋接運作中…")
                ensureWorkersRunning()
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

    private fun startOutboundWriterLoop() {
        if (writerJob?.isActive == true) return
        writerJob = scope.launch(Dispatchers.IO) {
            for (pkt in outboundChannel) {
                if (!isActive) break
                val ok = writePacketOnIoThread(pkt)
                if (!ok && (pkt.type == PacketType.MESSAGE || pkt.type == PacketType.CALL_STATE)) {
                    bufferImportantPacket(pkt)
                }
            }
        }
    }

    private fun bufferImportantPacket(pkt: BridgePacket) {
        synchronized(pendingImportantPackets) {
            if (pendingImportantPackets.none { it.id == pkt.id && pkt.id.isNotBlank() }) {
                pendingImportantPackets.add(pkt)
                while (pendingImportantPackets.size > 12) {
                    pendingImportantPackets.removeAt(0)
                }
            }
        }
    }

    private fun flushBufferedPackets() {
        val copy = synchronized(pendingImportantPackets) {
            if (pendingImportantPackets.isEmpty()) return
            val now = System.currentTimeMillis()
            val fresh = pendingImportantPackets.filter { (now - it.timestampMs) <= 45_000L }
            pendingImportantPackets.clear()
            fresh
        }
        copy.forEach { pkt -> outboundChannel.trySend(pkt) }
    }

    private fun ensureWorkersRunning() {
        startOutboundWriterLoop()
        val app = DxCarApp.instance ?: return
        val role = app.preferences.config.value.deviceRole

        if (role == DeviceRole.AI_BOX_LAUNCHER) {
            bindAiBoxOutboundHooks(app)
            if (btJob?.isActive != true) btJob = scope.launch { runAiBoxBluetoothLoop() }
            if (lanJob?.isActive != true) lanJob = scope.launch { runAiBoxLanLoop() }
        } else {
            if (btJob?.isActive != true) btJob = scope.launch { runPhoneBluetoothLoop() }
            if (lanJob?.isActive != true) lanJob = scope.launch { runPhoneLanLoop() }
        }

        if (heartbeatJob?.isActive != true) {
            heartbeatJob = scope.launch {
                while (isActive) {
                    delay(12_000L)
                    if (hasOpenTransport()) {
                        enqueueOutbound(BridgePacket(type = PacketType.PING, id = UUID.randomUUID().toString()))
                    }
                    RemoteTouchpadController.hideIfIdle()
                    _bridgeState.value = _bridgeState.value.copy(
                        isListenerBound = BoxNotificationListenerService.instance != null
                    )
                }
            }
        }
        updateConnectionSnapshot()
    }

    private fun restartWorkers() {
        btJob?.cancel()
        lanJob?.cancel()
        heartbeatJob?.cancel()
        closeAllSockets()
        btJob = null
        lanJob = null
        heartbeatJob = null
        ensureWorkersRunning()
    }

    private fun bindAiBoxOutboundHooks(app: DxCarApp) {
        app.notificationCenter.onReplyDispatch = { item, replyText ->
            if (item.fromPhoneBridge) {
                enqueueOutbound(
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
                enqueueOutbound(
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
                enqueueOutbound(
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
                updateConnectionSnapshot()
                val sec = runCatching { adapter.listenUsingRfcommWithServiceRecord(SERVICE_NAME_SEC, BRIDGE_UUID) }.getOrNull()
                val insec = runCatching { adapter.listenUsingInsecureRfcommWithServiceRecord(SERVICE_NAME, BRIDGE_UUID) }.getOrNull()
                secureServerSocket = sec
                insecureServerSocket = insec

                if (sec == null && insec == null) {
                    delay(4_000L)
                    continue
                }

                val acceptedJobSec = sec?.let { srv ->
                    scope.launch {
                        runCatching {
                            val client = srv.accept()
                            if (btSocket?.isConnected != true) {
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
                            if (btSocket?.isConnected != true) {
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

            if (btSocket?.isConnected == true && btWriter != null) {
                delay(3_000L)
                continue
            }

            val manualScanActive = System.currentTimeMillis() < forceOneShotUntilMs
            val shouldRun = shouldPhoneBridgeRunNow(this)

            // Never shut down service while LAN socket is already connected or car BT/hotspot is active!
            if (!shouldRun && !manualScanActive && !hasOpenTransport()) {
                appendLog("未偵測到車機藍牙或熱點連線，進入待機省電（上車自動啟動）")
                markStandby(this)
                stopSelf()
                return
            }

            if (adapter == null || !adapter.isEnabled || !hasBluetoothConnectPermission()) {
                updateConnectionSnapshot()
                delay(5_000L)
                continue
            }

            // Build candidate BT devices: explicit target first, plus any currently connected bonded devices!
            val candidates = LinkedHashMap<String, BluetoothDevice>()
            if (cfg.companionTargetDeviceAddress.isNotBlank()) {
                runCatching { adapter.getRemoteDevice(cfg.companionTargetDeviceAddress) }
                    .getOrNull()
                    ?.let { dev ->
                        if (manualScanActive || isDeviceConnectedReflect(dev)) {
                            candidates[dev.address] = dev
                        }
                    }
            }
            runCatching {
                adapter.bondedDevices.orEmpty().forEach { dev ->
                    if (isDeviceConnectedReflect(dev)) {
                        candidates.putIfAbsent(dev.address, dev)
                    }
                }
            }

            if (candidates.isEmpty()) {
                // If already connected over Wi-Fi Hotspot, just sleep quietly without spamming BT!
                delay(if (lanSocket?.isConnected == true) 12_000L else 5_000L)
                continue
            }

            var connectedBt = false
            for ((addr, device) in candidates) {
                if (!scope.isActive || lanSocket?.isConnected == true && !manualScanActive && addr != cfg.companionTargetDeviceAddress) {
                    // When Wi-Fi LAN is already solid, only probe non-selected BT devices on manual scan
                    continue
                }
                val targetName = if (addr == cfg.companionTargetDeviceAddress && cfg.companionTargetDeviceName.isNotBlank()) {
                    cfg.companionTargetDeviceName
                } else {
                    runCatching { device.name }.getOrNull()?.takeIf { it.isNotBlank() } ?: addr
                }
                try {
                    val socket = runCatching {
                        device.createRfcommSocketToServiceRecord(BRIDGE_UUID).also { it.connect() }
                    }.getOrElse {
                        device.createInsecureRfcommSocketToServiceRecord(BRIDGE_UUID).also { it.connect() }
                    }
                    connectedBt = true
                    handleBtSocketSession(socket, targetName, isAiBox = false)
                    break
                } catch (_: Exception) {
                    closeBtSocketsOnlyClient()
                }
            }

            if (!connectedBt) {
                updateConnectionSnapshot()
                delay(if (lanSocket?.isConnected == true) 15_000L else 5_000L)
            }
        }
    }

    private fun handleBtSocketSession(socket: BluetoothSocket, peerName: String, isAiBox: Boolean) {
        btSocket = socket
        btWriter = BufferedWriter(OutputStreamWriter(socket.outputStream, Charsets.UTF_8))
        val reader = BufferedReader(InputStreamReader(socket.inputStream, Charsets.UTF_8))

        appendLog("藍牙 SPP 已連線：$peerName")
        updateConnectionSnapshot(peerName)

        enqueueOutbound(
            BridgePacket(
                type = PacketType.DEVICE_INFO,
                id = UUID.randomUUID().toString(),
                deviceName = Build.MODEL
            )
        )
        if (!isAiBox) flushBufferedPackets()

        try {
            while (scope.isActive && socket.isConnected) {
                val line = reader.readLine() ?: break
                if (line.isBlank()) continue
                val packet = runCatching { json.decodeFromString<BridgePacket>(line) }.getOrNull() ?: continue
                dispatchIncomingPacket(packet, isAiBox)
            }
        } catch (_: Exception) {
        } finally {
            closeBtSocketsOnlyClient()
            appendLog("藍牙 SPP 通道已結束")
            updateConnectionSnapshot()
        }
    }

    // ==================== CHANNEL B: BI-DIRECTIONAL WI-FI HOTSPOT / LAN TCP BRIDGE ====================

    private suspend fun runAiBoxLanLoop() {
        // 1. UDP Broadcast responder (:47682) for Phone discovery
        val discoveryJob = scope.launch {
            while (scope.isActive) {
                try {
                    val udp = DatagramSocket(null).apply {
                        reuseAddress = true
                        broadcast = true
                        bind(InetSocketAddress(LAN_UDP_PORT))
                    }
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

        // 2. Proactive AI Box -> Phone Hotspot Gateway Reverse Connector (:47683)
        //    When AI Box connects to Phone's Wi-Fi Hotspot, AI Box's default gateway IS the Phone!
        val gatewayConnectJob = scope.launch {
            while (scope.isActive) {
                if (lanSocket?.isConnected != true) {
                    val gwIps = resolveWifiGatewayAndBroadcastPeers()
                    for (ip in gwIps) {
                        if (!scope.isActive || lanSocket?.isConnected == true) break
                        val sock = runCatching {
                            Socket().apply { connect(InetSocketAddress(ip, LAN_PHONE_TCP_PORT), 700) }
                        }.getOrNull()
                        if (sock != null) {
                            scope.launch {
                                handleLanSocketSession(sock, "手機 ($ip)", isAiBox = true)
                            }
                            break
                        }
                    }
                }
                delay(4_500L)
            }
        }

        // 3. Multi-client resilient TCP Server (:47681) — handles each client in a child coroutine
        try {
            while (scope.isActive) {
                try {
                    val srv = ServerSocket().apply {
                        reuseAddress = true
                        bind(InetSocketAddress(LAN_TCP_PORT))
                    }
                    tcpServerSocket = srv
                    appendLog("Wi-Fi 通道監聽就緒 (:$LAN_TCP_PORT)")
                    while (scope.isActive) {
                        val client = srv.accept()
                        val ip = client.inetAddress?.hostAddress.orEmpty()
                        scope.launch {
                            handleLanSocketSession(client, "手機 ($ip)", isAiBox = true)
                        }
                    }
                } catch (_: Exception) {
                    runCatching { tcpServerSocket?.close() }
                    tcpServerSocket = null
                    delay(3_000L)
                }
            }
        } finally {
            discoveryJob.cancel()
            gatewayConnectJob.cancel()
        }
    }

    private suspend fun runPhoneLanLoop() {
        // Also host Phone reverse-accept server (:47683) so AI Box can dial straight into Phone Hotspot IP!
        val phoneAcceptJob = scope.launch {
            while (scope.isActive) {
                try {
                    val srv = ServerSocket().apply {
                        reuseAddress = true
                        bind(InetSocketAddress(LAN_PHONE_TCP_PORT))
                    }
                    tcpServerSocket = srv
                    while (scope.isActive) {
                        val client = srv.accept()
                        val ip = client.inetAddress?.hostAddress.orEmpty()
                        if (lanSocket?.isConnected != true) {
                            scope.launch {
                                handleLanSocketSession(client, "AI Box ($ip)", isAiBox = false)
                            }
                        } else {
                            runCatching { client.close() }
                        }
                    }
                } catch (_: Exception) {
                    runCatching { tcpServerSocket?.close() }
                    tcpServerSocket = null
                    delay(4_000L)
                }
            }
        }

        try {
            while (scope.isActive) {
                if (lanSocket?.isConnected == true && lanWriter != null) {
                    delay(2_500L)
                    continue
                }

                val shouldScanLan = shouldPhoneBridgeRunNow(this) || System.currentTimeMillis() < forceOneShotUntilMs
                if (!shouldScanLan) {
                    delay(5_000L)
                    continue
                }

                val candidateIps = discoverAiBoxLanIps()
                var connectedAny = false
                for (ip in candidateIps) {
                    if (!scope.isActive || lanSocket?.isConnected == true) break
                    val sock = runCatching {
                        Socket().apply {
                            connect(InetSocketAddress(ip, LAN_TCP_PORT), 750)
                        }
                    }.getOrNull()
                    if (sock != null) {
                        connectedAny = true
                        handleLanSocketSession(sock, "AI Box ($ip)", isAiBox = false)
                        break
                    }
                }
                if (!connectedAny) {
                    delay(3_000L)
                }
            }
        } finally {
            phoneAcceptJob.cancel()
        }
    }

    private fun resolveWifiGatewayAndBroadcastPeers(): List<String> {
        val ips = LinkedHashSet<String>()
        runCatching {
            val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            val linkProps = cm?.getLinkProperties(cm.activeNetwork)
            linkProps?.routes?.forEach { route ->
                val gw = route.gateway?.hostAddress
                if (!gw.isNullOrBlank() && gw.contains(".") && gw != "0.0.0.0") {
                    ips.add(gw)
                }
            }
        }
        @Suppress("DEPRECATION")
        runCatching {
            val wm = applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            val gwInt = wm?.dhcpInfo?.gateway ?: 0
            if (gwInt != 0) {
                val gwStr = "%d.%d.%d.%d".format(
                    gwInt and 0xff,
                    (gwInt shr 8) and 0xff,
                    (gwInt shr 16) and 0xff,
                    (gwInt shr 24) and 0xff
                )
                if (gwStr != "0.0.0.0") ips.add(gwStr)
            }
        }
        return ips.toList()
    }

    private fun discoverAiBoxLanIps(): List<String> {
        val found = LinkedHashSet<String>()

        // 1. UDP Broadcast discovery on all active interfaces (Hotspot ap0/wlan1/swlan0 + Wi-Fi)
        runCatching {
            DatagramSocket().use { sock ->
                sock.broadcast = true
                sock.soTimeout = 750
                val reqBytes = DISCOVER_QUERY.toByteArray(Charsets.UTF_8)
                val targets = LinkedHashSet<InetAddress>()
                targets.add(InetAddress.getByName("255.255.255.255"))

                val ifaces = NetworkInterface.getNetworkInterfaces()
                while (ifaces != null && ifaces.hasMoreElements()) {
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
                val deadline = System.currentTimeMillis() + 750L
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

        // 2. Also check Wi-Fi Gateway (in case Phone & AI Box share an external router/hotspot)
        found.addAll(resolveWifiGatewayAndBroadcastPeers())

        // 3. Scan /proc/net/arp where readable
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

        // 4. Android Emulator host-loopback bridge (when running dual-emulator tests via `adb forward tcp:47681`)
        val isEmu = android.os.Build.FINGERPRINT.contains("generic", ignoreCase = true) ||
            android.os.Build.HARDWARE.contains("ranchu", ignoreCase = true) ||
            android.os.Build.PRODUCT.contains("sdk_gphone", ignoreCase = true)
        if (isEmu) {
            found.add("10.0.2.2")
        }

        return found.toList()
    }

    private fun handleLanSocketSession(socket: Socket, peerName: String, isAiBox: Boolean) {
        // Close any stale previous client socket cleanly before promoting the fresh one
        val oldSock = lanSocket
        if (oldSock != null && oldSock !== socket) {
            runCatching { lanWriter?.close() }
            runCatching { oldSock.close() }
        }

        runCatching {
            socket.tcpNoDelay = true
            socket.keepAlive = true
        }
        val writer = BufferedWriter(OutputStreamWriter(socket.getOutputStream(), Charsets.UTF_8))
        val reader = BufferedReader(InputStreamReader(socket.inputStream, Charsets.UTF_8))
        lanSocket = socket
        lanWriter = writer

        appendLog("Wi-Fi 通道已穩定連線：$peerName")
        updateConnectionSnapshot(peerName)

        enqueueOutbound(
            BridgePacket(
                type = PacketType.DEVICE_INFO,
                id = UUID.randomUUID().toString(),
                deviceName = Build.MODEL
            )
        )
        if (!isAiBox) flushBufferedPackets()

        try {
            while (scope.isActive && socket.isConnected && !socket.isClosed) {
                val line = reader.readLine() ?: break
                if (line.isBlank()) continue
                val packet = runCatching { json.decodeFromString<BridgePacket>(line) }.getOrNull() ?: continue
                dispatchIncomingPacket(packet, isAiBox)
            }
        } catch (_: Exception) {
        } finally {
            if (lanSocket === socket) {
                closeLanSocketsOnlyClient()
                appendLog("Wi-Fi 通道已斷開")
                updateConnectionSnapshot()
            } else {
                runCatching { socket.close() }
            }
        }
    }

    // ==================== PACKET DISPATCH & IO-SAFE SENDING ====================

    private fun dispatchIncomingPacket(packet: BridgePacket, isAiBox: Boolean) {
        val app = DxCarApp.instance ?: return

        if (packet.id.isNotBlank() &&
            packet.type != PacketType.PING &&
            packet.type != PacketType.PONG &&
            packet.type != PacketType.DEVICE_INFO &&
            packet.type != PacketType.TOUCHPAD_EVENT
        ) {
            if (!seenPacketIds.add(packet.id)) return
            if (seenPacketIds.size > 120) {
                val first = seenPacketIds.firstOrNull()
                if (first != null) seenPacketIds.remove(first)
            }
        }

        when (packet.type) {
            PacketType.PING -> {
                enqueueOutbound(BridgePacket(type = PacketType.PONG, id = packet.id))
            }
            PacketType.PONG -> {
                _bridgeState.value = _bridgeState.value.copy(lastSyncedMs = System.currentTimeMillis())
            }
            PacketType.DEVICE_INFO -> {
                if (packet.deviceName.isNotBlank()) {
                    _bridgeState.value = _bridgeState.value.copy(
                        peerName = packet.deviceName,
                        lastSyncedMs = System.currentTimeMillis()
                    )
                    updateConnectionSnapshot(packet.deviceName)
                }
            }
            PacketType.TOUCHPAD_EVENT -> {
                if (isAiBox) {
                    RemoteTouchpadController.onTouchpadPacket(this, packet)
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
                    val resolvedOnBox = if (ContactNameResolver.isGenericOrNumberOnly(packet.sender)) {
                        ContactNameResolver.resolveDisplayName(
                            this@BluetoothCompanionService,
                            packet.phoneNumber.ifBlank { packet.sender }
                        )
                    } else {
                        null
                    }
                    val bestName = resolvedOnBox
                        ?: packet.sender.ifBlank { packet.phoneNumber.ifBlank { "來電" } }
                    app.notificationCenter.updateCallState(
                        CarCallState(
                            status = packet.callStatus,
                            callerName = bestName,
                            callerNumber = packet.phoneNumber,
                            sourceApp = packet.appName.ifBlank { "手機來電" },
                            fromPhoneBridge = true
                        )
                    )
                    appendLog("來電狀態：${packet.callStatus.name} $bestName")
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
            PacketType.SHARED_LOCATION -> {
                if (isAiBox) {
                    val label = packet.sender.ifBlank { packet.text.ifBlank { "手機分享地點" } }
                    val addr = packet.text.ifBlank { label }
                    val item = SharedLocationItem(
                        id = packet.id.ifBlank { UUID.randomUUID().toString() },
                        label = label,
                        address = addr,
                        latitude = packet.latitude,
                        longitude = packet.longitude,
                        timestampMs = packet.timestampMs
                    )
                    val triggerMode = app.preferences.config.value.sharedLocationNavTrigger
                    when {
                        triggerMode == "BUILTIN" -> {
                            val curLoc = app.locationTracker.location.value
                            val cfg = app.preferences.config.value
                            val sLat = if (curLoc.latitude != 0.0) curLoc.latitude else cfg.lastKnownLat
                            val sLon = if (curLoc.longitude != 0.0) curLoc.longitude else cfg.lastKnownLon
                            app.builtInNavEngine.navigateByQuery(
                                query = item.address.ifBlank { item.label },
                                startLat = sLat,
                                startLon = sLon,
                                explicitDestLat = item.latitude.takeIf { it != 0.0 },
                                explicitDestLon = item.longitude.takeIf { it != 0.0 }
                            )
                            val launchHome = Intent(this@BluetoothCompanionService, MainActivity::class.java).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                                putExtra(MainActivity.EXTRA_ACTION, MainActivity.EXTRA_GO_DASHBOARD)
                            }
                            runCatching { startActivity(launchHome) }
                            appendLog("🧭 已自動以內建地圖導航：$label")
                        }
                        triggerMode == "GOOGLE_MAPS" -> {
                            com.dxcar.launcher.map.SharedLocationResolver.openInGoogleMaps(
                                this@BluetoothCompanionService,
                                item.label,
                                item.address,
                                item.latitude,
                                item.longitude
                            )
                            appendLog("🗺 已自動以 Google 地圖導航：$label")
                        }
                        triggerMode.startsWith("OTHER:") -> {
                            val targetPkg = triggerMode.removePrefix("OTHER:").trim().ifBlank { null }
                            com.dxcar.launcher.map.SharedLocationResolver.openInOtherMapChooser(
                                this@BluetoothCompanionService,
                                targetPkg,
                                item.label,
                                item.address,
                                item.latitude,
                                item.longitude
                            )
                            appendLog("📍 已自動以指定地圖導航：$label")
                        }
                        else -> {
                            app.notificationCenter.postSharedLocation(item)
                            appendLog("📍 收到手機分享地址（請選擇導航 App）：$label")
                        }
                    }
                }
            }
            PacketType.DIAL_PHONE -> {
                if (!isAiBox) {
                    handleRemoteDialPhoneOnPhone(packet.sender, packet.phoneNumber)
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun handleRemoteDialPhoneOnPhone(targetNameOrQuery: String, explicitNumber: String) {
        val directNum = explicitNumber.filter { it.isDigit() || it == '+' || it == '*' || it == '#' }
        val resolved = if (directNum.count { it.isDigit() } >= 3) {
            targetNameOrQuery.ifBlank { directNum } to directNum
        } else {
            ContactNameResolver.findPhoneNumberByContactName(this, targetNameOrQuery)
        }

        if (resolved == null) {
            appendLog("⚠️ 找不到聯絡人「$targetNameOrQuery」，已開啟手機撥號盤")
            runCatching {
                val dialFallback = Intent(Intent.ACTION_DIAL).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(dialFallback)
            }
            return
        }

        val (matchedName, rawPhone) = resolved
        val cleanPhone = rawPhone.filter { it.isDigit() || it == '+' || it == '*' || it == '#' }
        val telUri = android.net.Uri.parse("tel:${android.net.Uri.encode(cleanPhone)}")
        val hasCallPerm = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.CALL_PHONE
        ) == PackageManager.PERMISSION_GRANTED
        val action = if (hasCallPerm) Intent.ACTION_CALL else Intent.ACTION_DIAL
        val ok = runCatching {
            val intent = Intent(action, telUri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
            true
        }.getOrDefault(false)
        appendLog(
            if (ok) "📞 已透過手機${if (hasCallPerm) "直接撥打" else "開啟撥號"}：$matchedName ($cleanPhone)"
            else "⚠️ 無法啟動撥號：$matchedName ($cleanPhone)"
        )
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

    private fun hasOpenTransport(): Boolean {
        val lanOk = lanSocket?.let { it.isConnected && !it.isClosed } == true && lanWriter != null
        val btOk = btSocket?.isConnected == true && btWriter != null
        return lanOk || btOk
    }

    /**
     * Thread-safe, non-blocking enqueue callable from ANY thread (Main UI, NotificationListener, BroadcastReceiver).
     * Never throws NetworkOnMainThreadException!
     */
    private fun enqueueOutbound(packet: BridgePacket): Boolean {
        startOutboundWriterLoop()
        if (hasOpenTransport()) {
            outboundChannel.trySend(packet)
            return true
        }
        if (packet.type == PacketType.MESSAGE ||
            packet.type == PacketType.CALL_STATE ||
            packet.type == PacketType.SHARED_LOCATION
        ) {
            bufferImportantPacket(packet)
            ensureWorkersRunning()
        }
        return false
    }

    /** Executes strictly inside Dispatchers.IO writer coroutine. */
    private fun writePacketOnIoThread(packet: BridgePacket): Boolean {
        val encoded = json.encodeToString(packet)
        var sent = false

        lanWriter?.let { w ->
            val ok = runCatching {
                synchronized(lanWriteLock) {
                    w.write(encoded)
                    w.newLine()
                    w.flush()
                }
                true
            }.getOrDefault(false)
            if (ok) {
                sent = true
            } else {
                closeLanSocketsOnlyClient()
                updateConnectionSnapshot()
            }
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
                if (ok) {
                    sent = true
                } else {
                    closeBtSocketsOnlyClient()
                    updateConnectionSnapshot()
                }
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
        val lanConnected = lanSocket?.let { it.isConnected && !it.isClosed } == true && lanWriter != null
        val app = DxCarApp.instance
        val role = app?.preferences?.config?.value?.deviceRole ?: DeviceRole.AI_BOX_LAUNCHER
        if (!btConnected && !lanConnected && role == DeviceRole.AI_BOX_LAUNCHER) {
            app?.notificationCenter?.clearPhoneBridgeCall()
        }

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
            BridgeConnectionStatus.STANDBY -> "待機省電中（上車連藍牙或熱點自動啟動）"
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
            "DX-Car Launcher 通知與觸控同步橋接",
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
            .setContentTitle("DX-Car Launcher 雙端橋接")
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
        private const val LAN_PHONE_TCP_PORT = 47683
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
            if (activeService?.hasOpenTransport() == true) return
            _bridgeState.value = _bridgeState.value.copy(
                status = BridgeConnectionStatus.STANDBY,
                transportLabel = "",
                isListenerBound = BoxNotificationListenerService.instance != null
            )
        }

        private fun isDeviceConnectedReflect(device: BluetoothDevice): Boolean {
            return runCatching {
                val method = device.javaClass.getMethod("isConnected")
                (method.invoke(device) as? Boolean) == true
            }.getOrDefault(false)
        }

        @SuppressLint("MissingPermission")
        fun hasAnyCarBluetoothConnection(context: Context, targetAddress: String): Boolean {
            // 1. Check AudioManager active Bluetooth HFP / A2DP audio route (works even before BT permission or on dual-BT boxes!)
            runCatching {
                val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                val btAudio = am?.getDevices(AudioManager.GET_DEVICES_OUTPUTS)?.any { dev ->
                    dev.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                        dev.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO
                } == true
                if (btAudio) return true
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                return false
            }
            val btManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager ?: return false
            val adapter = btManager.adapter ?: return false
            if (!adapter.isEnabled) return false

            if (targetAddress.isNotBlank()) {
                val ok = runCatching { isDeviceConnectedReflect(adapter.getRemoteDevice(targetAddress)) }.getOrDefault(false)
                if (ok) return true
            }

            return runCatching {
                adapter.bondedDevices.orEmpty().any { isDeviceConnectedReflect(it) }
            }.getOrDefault(false)
        }

        /**
         * Detects Phone Wi-Fi Hotspot OR shared Wi-Fi LAN on Android 9-16 (vivo/Samsung/Pixel/Xiaomi),
         * bypassing hidden API blocks by inspecting active tethering/Wi-Fi network interfaces!
         */
        fun isWifiHotspotOrLanActive(context: Context): Boolean {
            val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            val apReflect = runCatching {
                val m = wm?.javaClass?.getDeclaredMethod("isWifiApEnabled")
                m?.isAccessible = true
                (m?.invoke(wm) as? Boolean) == true
            }.getOrDefault(false)
            if (apReflect) return true

            // Inspect live network interfaces for Android Hotspot / Wi-Fi Direct / LAN (ap0, wlan1, swlan0, softap, rndis, wlan0)
            runCatching {
                val ifaces = NetworkInterface.getNetworkInterfaces()
                while (ifaces != null && ifaces.hasMoreElements()) {
                    val ni = ifaces.nextElement()
                    if (!ni.isUp || ni.isLoopback) continue
                    val name = ni.name.lowercase()
                    val isHotspotOrWifiIface = name.startsWith("ap") ||
                        name.startsWith("swlan") ||
                        name.startsWith("softap") ||
                        name.startsWith("wlan") ||
                        name.startsWith("rndis") ||
                        name.contains("p2p")
                    if (isHotspotOrWifiIface) {
                        val hasIpv4 = ni.interfaceAddresses.any { it.address is Inet4Address }
                        if (hasIpv4) return true
                    }
                }
            }
            return false
        }

        fun shouldPhoneBridgeRunNow(context: Context): Boolean {
            val app = DxCarApp.instance ?: return false
            val cfg = app.preferences.config.value
            if (cfg.deviceRole == DeviceRole.AI_BOX_LAUNCHER) return true
            if (activeService?.hasOpenTransport() == true) return true
            return hasAnyCarBluetoothConnection(context, cfg.companionTargetDeviceAddress) ||
                isWifiHotspotOrLanActive(context)
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

        private val pendingImportantPackets = Collections.synchronizedList(mutableListOf<BridgePacket>())

        private fun bufferStaticImportantPacket(pkt: BridgePacket) {
            val withId = if (pkt.id.isBlank()) pkt.copy(id = UUID.randomUUID().toString()) else pkt
            synchronized(pendingImportantPackets) {
                if (pendingImportantPackets.none { it.id == withId.id }) {
                    pendingImportantPackets.add(withId)
                    while (pendingImportantPackets.size > 16) {
                        pendingImportantPackets.removeAt(0)
                    }
                }
            }
        }

        fun sendPacketFromPhone(packet: BridgePacket): Boolean {
            val normalized = if (packet.id.isBlank()) packet.copy(id = UUID.randomUUID().toString()) else packet
            val svc = activeService
            if (svc == null) {
                if (normalized.type == PacketType.MESSAGE ||
                    normalized.type == PacketType.CALL_STATE ||
                    normalized.type == PacketType.SHARED_LOCATION
                ) {
                    bufferStaticImportantPacket(normalized)
                }
                DxCarApp.instance?.let { startOrReconnect(it) }
                return false
            }
            return svc.enqueueOutbound(normalized)
        }

        fun sendPacketFromCar(packet: BridgePacket): Boolean {
            val normalized = if (packet.id.isBlank()) packet.copy(id = UUID.randomUUID().toString()) else packet
            val svc = activeService ?: return false
            if (!svc.hasOpenTransport()) return false
            return svc.enqueueOutbound(normalized)
        }
    }
}

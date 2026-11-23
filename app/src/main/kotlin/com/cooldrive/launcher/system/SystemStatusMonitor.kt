package com.cooldrive.launcher.system

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.cooldrive.launcher.comms.BluetoothCompanionService
import com.cooldrive.launcher.comms.BridgeConnectionStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

data class PairedBtDevice(
    val name: String,
    val address: String,
    val isConnected: Boolean
)

data class SystemStatus(
    val time24h: String = "--:--",
    val time12h: String = "--:--",
    val dateLabel: String = "",
    val now: LocalDateTime = LocalDateTime.now(),
    val wifiEnabled: Boolean = false,
    val wifiConnected: Boolean = false,
    val wifiSsid: String = "",
    val wifiSignalLevel: Int = 0, // 0..4
    val bluetoothEnabled: Boolean = false,
    val bluetoothConnected: Boolean = false,
    val connectedBtName: String = "",
    val mediaVolumePercent: Int = 50,
    val brightnessPercent: Int = 70
)

class SystemStatusMonitor(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val wifiManager =
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
    private val bluetoothManager =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val audioManager =
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private val _status = MutableStateFlow(SystemStatus())
    val status: StateFlow<SystemStatus> = _status.asStateFlow()

    private val time24Fmt = DateTimeFormatter.ofPattern("HH:mm", Locale.TAIWAN)
    private val time12Fmt = DateTimeFormatter.ofPattern("h:mm", Locale.TAIWAN)
    private val dateFmt = DateTimeFormatter.ofPattern("M月d日 E", Locale.TRADITIONAL_CHINESE)

    private var started = false
    private val activeAclDevices = LinkedHashMap<String, String>()

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) = refreshNetworkState()
        override fun onLost(network: Network) = refreshNetworkState()
        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) =
            refreshNetworkState()
    }

    private val systemReceiver = object : BroadcastReceiver() {
        @SuppressLint("MissingPermission")
        override fun onReceive(ctx: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_TIME_TICK,
                Intent.ACTION_TIME_CHANGED,
                Intent.ACTION_TIMEZONE_CHANGED -> refreshClock()
                WifiManager.WIFI_STATE_CHANGED_ACTION,
                WifiManager.RSSI_CHANGED_ACTION,
                WifiManager.NETWORK_STATE_CHANGED_ACTION -> refreshNetworkState()
                BluetoothDevice.ACTION_ACL_CONNECTED -> {
                    @Suppress("DEPRECATION")
                    val dev = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                    if (dev != null) {
                        val name = runCatching { dev.name }.getOrNull().orEmpty().ifBlank { dev.address }
                        activeAclDevices[dev.address] = name
                    }
                    refreshBluetoothState()
                }
                BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                    @Suppress("DEPRECATION")
                    val dev = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                    if (dev != null) {
                        activeAclDevices.remove(dev.address)
                    }
                    refreshBluetoothState()
                }
                BluetoothAdapter.ACTION_STATE_CHANGED,
                BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED,
                "android.bluetooth.headsetclient.profile.action.CONNECTION_STATE_CHANGED",
                "android.bluetooth.a2dp-sink.profile.action.CONNECTION_STATE_CHANGED" -> refreshBluetoothState()
            }
        }
    }

    fun start() {
        if (started) return
        started = true

        refreshAll()

        runCatching {
            connectivityManager.registerDefaultNetworkCallback(networkCallback)
        }

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_TIME_TICK)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
            addAction(WifiManager.WIFI_STATE_CHANGED_ACTION)
            addAction(WifiManager.RSSI_CHANGED_ACTION)
            addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION)
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
            addAction(BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED)
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
            addAction("android.bluetooth.headsetclient.profile.action.CONNECTION_STATE_CHANGED")
            addAction("android.bluetooth.a2dp-sink.profile.action.CONNECTION_STATE_CHANGED")
        }
        ContextCompat.registerReceiver(
            context,
            systemReceiver,
            filter,
            ContextCompat.RECEIVER_EXPORTED
        )

        // Periodic lightweight refresh to keep clock, signal, BT & volume exact
        scope.launch {
            while (isActive) {
                refreshClock()
                refreshBluetoothState()
                refreshAudioAndBrightness()
                delay(5_000L)
            }
        }
    }

    fun refreshAll() {
        refreshClock()
        refreshNetworkState()
        refreshBluetoothState()
        refreshAudioAndBrightness()
    }

    private fun refreshClock() {
        val now = LocalDateTime.now()
        _status.value = _status.value.copy(
            now = now,
            time24h = now.format(time24Fmt),
            time12h = now.format(time12Fmt),
            dateLabel = now.format(dateFmt)
        )
    }

    @Suppress("DEPRECATION")
    @SuppressLint("MissingPermission")
    fun refreshNetworkState() {
        val activeNetwork = connectivityManager.activeNetwork
        val caps = activeNetwork?.let { connectivityManager.getNetworkCapabilities(it) }
        val wifiConnected = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        val wifiEnabled = runCatching { wifiManager?.isWifiEnabled == true }.getOrDefault(wifiConnected)

        var ssid = ""
        var signalLevel = if (wifiConnected) 3 else 0
        runCatching {
            val info: WifiInfo? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                caps?.transportInfo as? WifiInfo ?: wifiManager?.connectionInfo
            } else {
                wifiManager?.connectionInfo
            }
            if (info != null && wifiConnected) {
                val rawSsid = info.ssid?.removePrefix("\"")?.removeSuffix("\"") ?: ""
                if (rawSsid.isNotBlank() && rawSsid != "<unknown ssid>") {
                    ssid = rawSsid
                }
                signalLevel = WifiManager.calculateSignalLevel(info.rssi, 5).coerceIn(1, 4)
            }
        }

        _status.value = _status.value.copy(
            wifiEnabled = wifiEnabled,
            wifiConnected = wifiConnected,
            wifiSsid = ssid,
            wifiSignalLevel = signalLevel
        )
    }

    @SuppressLint("MissingPermission")
    fun refreshBluetoothState() {
        val adapter = bluetoothManager?.adapter
        if (adapter == null) {
            _status.value = _status.value.copy(
                bluetoothEnabled = false,
                bluetoothConnected = false,
                connectedBtName = ""
            )
            return
        }

        val enabled = runCatching { adapter.isEnabled }.getOrDefault(false)
        if (!enabled) {
            _status.value = _status.value.copy(
                bluetoothEnabled = false,
                bluetoothConnected = false,
                connectedBtName = ""
            )
            return
        }

        val hasBtPermission = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED

        if (!hasBtPermission) {
            _status.value = _status.value.copy(
                bluetoothEnabled = true,
                bluetoothConnected = false,
                connectedBtName = ""
            )
            return
        }

        var connected = activeAclDevices.isNotEmpty()
        var connectedName = activeAclDevices.values.lastOrNull().orEmpty()

        // Check Companion SPP bridge directly
        val bridge = BluetoothCompanionService.bridgeState.value
        if (bridge.status == BridgeConnectionStatus.CONNECTED) {
            connected = true
            if (connectedName.isBlank()) connectedName = bridge.peerName
        }

        // Check Android AudioManager active Bluetooth audio endpoints (works on almost all AI Boxes!)
        runCatching {
            val audioDevices = audioManager.getDevices(AudioManager.GET_DEVICES_ALL)
            val btAudio = audioDevices.firstOrNull { dev ->
                dev.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                    dev.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                    (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                        (dev.type == AudioDeviceInfo.TYPE_BLE_HEADSET || dev.type == AudioDeviceInfo.TYPE_BLE_SPEAKER))
            }
            if (btAudio != null) {
                connected = true
                if (connectedName.isBlank()) {
                    connectedName = btAudio.productName?.toString().orEmpty()
                }
            }
        }

        runCatching {
            // Adapter-level global connection state (covers HFP Client / A2DP Sink / PBAP / MAP on AI Box)
            val globalState = runCatching {
                adapter.javaClass.getMethod("getConnectionState").invoke(adapter) as? Int
            }.getOrDefault(BluetoothAdapter.STATE_DISCONNECTED)
            if (globalState == BluetoothAdapter.STATE_CONNECTED) {
                connected = true
            }

            val profiles = listOf(
                BluetoothProfile.HEADSET,
                BluetoothProfile.A2DP,
                5,  // PAN
                11, // A2DP_SINK (AI Box / Automotive)
                12, // AVRCP_CONTROLLER (AI Box / Automotive)
                16, // HEADSET_CLIENT / HFP Client (AI Box / Automotive)
                17, // PBAP_CLIENT
                18  // MAP_CLIENT
            )
            for (profile in profiles) {
                val state = runCatching { adapter.getProfileConnectionState(profile) }
                    .getOrDefault(BluetoothProfile.STATE_DISCONNECTED)
                if (state == BluetoothProfile.STATE_CONNECTED) {
                    connected = true
                    break
                }
            }

            // GATT connected devices (for BLE dual-mode phones paired to AI Box)
            runCatching {
                val gattDevs = bluetoothManager?.getConnectedDevices(BluetoothProfile.GATT).orEmpty() +
                    bluetoothManager?.getConnectedDevices(BluetoothProfile.GATT_SERVER).orEmpty()
                if (gattDevs.isNotEmpty()) {
                    connected = true
                    if (connectedName.isBlank()) {
                        connectedName = gattDevs.first().name ?: gattDevs.first().address
                    }
                }
            }

            val bonded = adapter.bondedDevices.orEmpty()
            for (dev in bonded) {
                val isDevConnected = isDeviceConnectedReflect(dev)
                if (isDevConnected) {
                    connected = true
                    connectedName = dev.name ?: dev.address
                    break
                }
            }
            if (connected && connectedName.isBlank()) {
                connectedName = bonded.firstOrNull()?.name ?: "已連線手機"
            }
        }

        _status.value = _status.value.copy(
            bluetoothEnabled = true,
            bluetoothConnected = connected,
            connectedBtName = connectedName
        )
    }

    @SuppressLint("MissingPermission")
    fun getPairedBluetoothDevices(): List<PairedBtDevice> {
        val adapter = bluetoothManager?.adapter ?: return emptyList()
        val hasPerm = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
        if (!hasPerm) return emptyList()

        return runCatching {
            adapter.bondedDevices.orEmpty().map { device ->
                PairedBtDevice(
                    name = device.name?.takeIf { it.isNotBlank() } ?: device.address,
                    address = device.address,
                    isConnected = isDeviceConnectedReflect(device)
                )
            }.sortedByDescending { it.isConnected }
        }.getOrDefault(emptyList())
    }

    private fun isDeviceConnectedReflect(device: BluetoothDevice): Boolean {
        return runCatching {
            val method = device.javaClass.getMethod("isConnected")
            (method.invoke(device) as? Boolean) == true
        }.getOrDefault(false)
    }

    fun refreshAudioAndBrightness() {
        val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        val curVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val volPct = ((curVol * 100f) / maxVol).toInt().coerceIn(0, 100)

        val curBright = runCatching {
            Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, 180)
        }.getOrDefault(180)
        val brightPct = ((curBright * 100f) / 255f).toInt().coerceIn(5, 100)

        _status.value = _status.value.copy(
            mediaVolumePercent = volPct,
            brightnessPercent = brightPct
        )
    }

    fun setMediaVolumePercent(percent: Int): Int {
        val clamped = percent.coerceIn(0, 100)
        val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        val targetIndex = ((clamped / 100f) * maxVol).toInt().coerceIn(0, maxVol)
        runCatching {
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetIndex, 0)
        }
        val actualPct = ((targetIndex * 100f) / maxVol).toInt()
        _status.value = _status.value.copy(mediaVolumePercent = actualPct)
        return actualPct
    }

    fun adjustMediaVolumeByDelta(deltaPercent: Int): Int {
        return setMediaVolumePercent(_status.value.mediaVolumePercent + deltaPercent)
    }

    fun setBrightnessPercent(percent: Int): Pair<Int, Boolean> {
        val clamped = percent.coerceIn(5, 100)
        if (!Settings.System.canWrite(context)) {
            _status.value = _status.value.copy(brightnessPercent = clamped)
            return clamped to false
        }
        val value255 = ((clamped / 100f) * 255f).toInt().coerceIn(10, 255)
        runCatching {
            Settings.System.putInt(
                context.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS_MODE,
                Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
            )
            Settings.System.putInt(
                context.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS,
                value255
            )
        }
        _status.value = _status.value.copy(brightnessPercent = clamped)
        return clamped to true
    }

    fun adjustBrightnessByDelta(deltaPercent: Int): Pair<Int, Boolean> {
        return setBrightnessPercent(_status.value.brightnessPercent + deltaPercent)
    }

    companion object {
        fun openWifiSettings(context: Context) {
            val intents = listOf(
                Intent(Settings.ACTION_WIFI_SETTINGS),
                Intent(Settings.ACTION_WIRELESS_SETTINGS),
                Intent(Settings.ACTION_SETTINGS)
            )
            launchFirstWorking(context, intents)
        }

        fun openBluetoothSettings(context: Context) {
            val intents = listOf(
                Intent(Settings.ACTION_BLUETOOTH_SETTINGS),
                Intent(Settings.ACTION_SETTINGS)
            )
            launchFirstWorking(context, intents)
        }

        fun openSystemSettings(context: Context) {
            launchFirstWorking(context, listOf(Intent(Settings.ACTION_SETTINGS)))
        }

        fun openHomeLauncherSettings(context: Context) {
            val intents = listOf(
                Intent(Settings.ACTION_HOME_SETTINGS),
                Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS),
                Intent(Settings.ACTION_SETTINGS)
            )
            launchFirstWorking(context, intents)
        }

        private fun launchFirstWorking(context: Context, intents: List<Intent>) {
            for (intent in intents) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                val launched = runCatching {
                    context.startActivity(intent)
                    true
                }.getOrDefault(false)
                if (launched) return
            }
        }
    }
}

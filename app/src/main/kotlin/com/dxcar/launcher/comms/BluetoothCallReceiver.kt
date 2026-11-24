package com.dxcar.launcher.comms

import android.annotation.SuppressLint
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager
import com.dxcar.launcher.DxCarApp
import com.dxcar.launcher.data.DeviceRole
import com.dxcar.launcher.overlay.PersistentDockOverlayService
import com.dxcar.launcher.system.WifiHotspotFastRestarter

class BluetoothCallReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        val app = DxCarApp.instance

        when (action) {
            Intent.ACTION_BOOT_COMPLETED -> {
                if (app != null) {
                    val cfg = app.preferences.config.value
                    if (cfg.deviceRole == DeviceRole.AI_BOX_LAUNCHER && cfg.persistentOverlayDockEnabled) {
                        PersistentDockOverlayService.start(context)
                    }
                    if (cfg.companionAutoConnect) {
                        if (cfg.deviceRole == DeviceRole.AI_BOX_LAUNCHER ||
                            BluetoothCompanionService.shouldPhoneBridgeRunNow(context)
                        ) {
                            BluetoothCompanionService.startOrReconnect(context)
                        } else {
                            BluetoothCompanionService.markStandby(context)
                        }
                    }
                }
            }

            BluetoothDevice.ACTION_ACL_CONNECTED -> {
                val connectedDevice = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                }
                val cfg = app?.preferences?.config?.value

                app?.systemStatusMonitor?.refreshBluetoothState()
                if (cfg?.companionAutoConnect == true) {
                    if (cfg.deviceRole == DeviceRole.AI_BOX_LAUNCHER) {
                        BluetoothCompanionService.startOrReconnect(context)
                    } else {
                        val targetAddr = cfg.companionTargetDeviceAddress.trim()
                        val matchesTarget = targetAddr.isBlank() ||
                            connectedDevice?.address.equals(targetAddr, ignoreCase = true)
                        if (matchesTarget) {
                            BluetoothCompanionService.appendExternalLog("偵測到車機藍牙連線，自動喚醒通知同步")
                            BluetoothCompanionService.startOrReconnect(context)
                        }
                    }
                }
            }

            BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                app?.systemStatusMonitor?.refreshBluetoothState()
                val cfg = app?.preferences?.config?.value
                if (cfg?.deviceRole == DeviceRole.PHONE_COMPANION) {
                    BluetoothCompanionService.onBluetoothDisconnected(context)
                }
            }

            "android.bluetooth.headsetclient.profile.action.CONNECTION_STATE_CHANGED" -> {
                val state = intent.getIntExtra(BluetoothProfile.EXTRA_STATE, BluetoothProfile.STATE_DISCONNECTED)
                app?.systemStatusMonitor?.refreshBluetoothState()
                if (state == BluetoothProfile.STATE_CONNECTED) {
                    val cfg = app?.preferences?.config?.value
                    if (cfg == null || cfg.deviceRole == DeviceRole.AI_BOX_LAUNCHER) {
                        if (cfg?.companionAutoConnect == true) {
                            BluetoothCompanionService.startOrReconnect(context)
                        }
                    }
                }
            }

            TelephonyManager.ACTION_PHONE_STATE_CHANGED -> {
                val stateStr = intent.getStringExtra(TelephonyManager.EXTRA_STATE) ?: return
                @Suppress("DEPRECATION")
                val number = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER).orEmpty()
                val status = when (stateStr) {
                    TelephonyManager.EXTRA_STATE_RINGING -> CallStatus.RINGING
                    TelephonyManager.EXTRA_STATE_OFFHOOK -> CallStatus.ACTIVE
                    else -> CallStatus.IDLE
                }
                val role = app?.preferences?.config?.value?.deviceRole
                if (role == DeviceRole.PHONE_COMPANION) {
                    BluetoothCompanionService.sendPacketFromPhone(
                        BridgePacket(
                            type = PacketType.CALL_STATE,
                            sender = number.ifBlank { "行動電話來電" },
                            phoneNumber = number,
                            appName = "行動電話",
                            callStatus = status
                        )
                    )
                } else {
                    app?.notificationCenter?.updateCallState(
                        CarCallState(
                            status = status,
                            callerName = number.ifBlank { "藍牙來電" },
                            callerNumber = number,
                            sourceApp = "藍牙電話",
                            fromPhoneBridge = false
                        )
                    )
                }
            }

            // Hidden AOSP/Automotive HFP Client broadcast on AI Boxes
            "android.bluetooth.headsetclient.profile.action.CALL_CHANGED" -> {
                @Suppress("DEPRECATION")
                val callObj = intent.extras?.get("android.bluetooth.headsetclient.extra.CALL")
                if (callObj != null && app != null) {
                    val number = runCatching {
                        callObj.javaClass.getMethod("getNumber").invoke(callObj) as? String
                    }.getOrNull().orEmpty()
                    val rawState = runCatching {
                        callObj.javaClass.getMethod("getState").invoke(callObj) as? Int
                    }.getOrNull() ?: -1

                    // BluetoothHeadsetClientCall states:
                    // 0 = ACTIVE, 1 = HELD, 2 = DIALING, 3 = ALERTING, 4 = INCOMING, 5 = WAITING, 7 = TERMINATED
                    val mapped = when (rawState) {
                        4, 5 -> CallStatus.RINGING
                        0, 1, 2, 3 -> CallStatus.ACTIVE
                        else -> CallStatus.IDLE
                    }
                    app.notificationCenter.updateCallState(
                        CarCallState(
                            status = mapped,
                            callerName = number.ifBlank { "藍牙免持來電" },
                            callerNumber = number,
                            sourceApp = "藍牙 HFP",
                            fromPhoneBridge = false
                        )
                    )
                }
            }
        }
    }
}

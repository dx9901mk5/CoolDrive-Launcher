package com.cooldrive.launcher.comms

import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager
import com.cooldrive.launcher.CoolDriveApp
import com.cooldrive.launcher.data.DeviceRole
import com.cooldrive.launcher.overlay.PersistentDockOverlayService

class BluetoothCallReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        val app = CoolDriveApp.instance

        when (action) {
            Intent.ACTION_BOOT_COMPLETED -> {
                if (app != null) {
                    val cfg = app.preferences.config.value
                    if (cfg.deviceRole == DeviceRole.AI_BOX_LAUNCHER && cfg.persistentOverlayDockEnabled) {
                        PersistentDockOverlayService.start(context)
                    }
                    if (cfg.companionAutoConnect) {
                        BluetoothCompanionService.startOrReconnect(context)
                    }
                }
            }

            BluetoothDevice.ACTION_ACL_CONNECTED -> {
                app?.systemStatusMonitor?.refreshBluetoothState()
                if (app?.preferences?.config?.value?.companionAutoConnect == true) {
                    BluetoothCompanionService.startOrReconnect(context)
                }
            }

            BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                app?.systemStatusMonitor?.refreshBluetoothState()
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

package com.dxcar.launcher.comms

import android.annotation.SuppressLint
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.telephony.TelephonyManager
import com.dxcar.launcher.DxCarApp
import com.dxcar.launcher.data.DeviceRole
import com.dxcar.launcher.overlay.PersistentDockOverlayService
import java.util.UUID

class BluetoothCallReceiver : BroadcastReceiver() {

    companion object {
        @Volatile
        var lastPhoneTelephonyStatus: CallStatus = CallStatus.IDLE
    }

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
                val rawExtraNum = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)?.trim().orEmpty()
                val status = when (stateStr) {
                    TelephonyManager.EXTRA_STATE_RINGING -> CallStatus.RINGING
                    TelephonyManager.EXTRA_STATE_OFFHOOK -> CallStatus.ACTIVE
                    else -> CallStatus.IDLE
                }
                val role = app?.preferences?.config?.value?.deviceRole

                fun resolveCallerMultiSource(ctx: Context, knownNum: String): ResolvedCallerInfo? {
                    var effNum = knownNum.trim()
                    var resolvedContact = if (effNum.isNotBlank()) {
                        ContactNameResolver.resolveDisplayName(ctx, effNum)
                    } else {
                        null
                    }
                    // Source 2: Android 10+ Play-compliant CallScreeningService (exact incoming handle!)
                    if (effNum.isBlank() || resolvedContact == null) {
                        val screened = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                            PhoneCallScreeningService.recentScreenedCaller()
                        } else null
                        if (screened != null) {
                            if (effNum.isBlank() && screened.phoneNumber.isNotBlank()) effNum = screened.phoneNumber
                            if (resolvedContact == null && !ContactNameResolver.isGenericOrNumberOnly(screened.displayName)) {
                                resolvedContact = screened.displayName
                            }
                        }
                    }
                    // Source 3: Active OEM dialer notification (including inflated RemoteViews on vivo/Samsung/Xiaomi!)
                    if (effNum.isBlank() || resolvedContact == null) {
                        val notifCaller = BoxNotificationListenerService.instance?.scanActiveCallNotificationOnPhone()
                        if (notifCaller != null) {
                            if (effNum.isBlank() && notifCaller.phoneNumber.isNotBlank()) effNum = notifCaller.phoneNumber
                            if (resolvedContact == null && !ContactNameResolver.isGenericOrNumberOnly(notifCaller.displayName)) {
                                resolvedContact = notifCaller.displayName
                            }
                        }
                    }
                    // Source 4: Recent CallLog (Direct APK flavor when READ_CALL_LOG is granted)
                    if (effNum.isBlank() || resolvedContact == null) {
                        val recentLog = ContactNameResolver.queryRecentCallLogCaller(ctx)
                        if (recentLog != null) {
                            if (effNum.isBlank() && recentLog.phoneNumber.isNotBlank()) effNum = recentLog.phoneNumber
                            if (resolvedContact == null && !ContactNameResolver.isGenericOrNumberOnly(recentLog.displayName)) {
                                resolvedContact = recentLog.displayName
                            }
                        }
                    }
                    // Cross-fill: Name <-> Number via ContactsContract
                    if (resolvedContact == null && effNum.isNotBlank()) {
                        resolvedContact = ContactNameResolver.resolveDisplayName(ctx, effNum)
                    }
                    if (effNum.isBlank() && !resolvedContact.isNullOrBlank()) {
                        effNum = ContactNameResolver.findPhoneNumberByContactName(ctx, resolvedContact)?.second.orEmpty()
                    }
                    val bestDisplay = resolvedContact ?: effNum
                    return if (bestDisplay.isNotBlank()) ResolvedCallerInfo(bestDisplay, effNum) else null
                }

                fun dispatchPhoneCallSnapshot(knownNum: String): Boolean {
                    val info = if (status != CallStatus.IDLE) resolveCallerMultiSource(context, knownNum) else null
                    val effNum = info?.phoneNumber.orEmpty()
                    val bestDisplay = info?.displayName?.ifBlank { effNum } ?: "行動電話來電"
                    BluetoothCompanionService.sendPacketFromPhone(
                        BridgePacket(
                            type = PacketType.CALL_STATE,
                            id = UUID.randomUUID().toString(),
                            sender = bestDisplay,
                            phoneNumber = effNum,
                            appName = "行動電話",
                            callStatus = status
                        )
                    )
                    return effNum.isNotBlank() || !ContactNameResolver.isPureGenericPlaceholder(bestDisplay)
                }

                if (role == DeviceRole.PHONE_COMPANION) {
                    val callCaptureOn = app.preferences.config.value.phoneCallCaptureEnabled
                    if (status != CallStatus.IDLE && !callCaptureOn) return
                    lastPhoneTelephonyStatus = status
                    if (status == CallStatus.IDLE && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                        PhoneCallScreeningService.clearScreenedCaller()
                    }
                    val resolvedImmediately = dispatchPhoneCallSnapshot(rawExtraNum)
                    if (status == CallStatus.IDLE) {
                        // Send a second delayed IDLE so late OEM dialer / missed-call notifications can never re-stick the Car banner!
                        Handler(Looper.getMainLooper()).postDelayed({
                            if (lastPhoneTelephonyStatus == CallStatus.IDLE) {
                                BluetoothCompanionService.sendPacketFromPhone(
                                    BridgePacket(
                                        type = PacketType.CALL_STATE,
                                        id = UUID.randomUUID().toString(),
                                        callStatus = CallStatus.IDLE
                                    )
                                )
                            }
                        }, 650L)
                    }
                    // If Android's first broadcast arrived before dialer notification / CallScreening / CallLog populated,
                    // re-probe at 450ms AND 1200ms so caller name + phone number ALWAYS show up!
                    if (status == CallStatus.RINGING && !resolvedImmediately) {
                        val appCtx = context.applicationContext
                        val handler = Handler(Looper.getMainLooper())
                        for (delayMs in listOf(450L, 1200L)) {
                            handler.postDelayed({
                                if (lastPhoneTelephonyStatus != CallStatus.RINGING) return@postDelayed
                                val delayed = resolveCallerMultiSource(appCtx, "")
                                if (delayed != null) {
                                    BluetoothCompanionService.sendPacketFromPhone(
                                        BridgePacket(
                                            type = PacketType.CALL_STATE,
                                            id = UUID.randomUUID().toString(),
                                            sender = delayed.displayName,
                                            phoneNumber = delayed.phoneNumber,
                                            appName = "行動電話",
                                            callStatus = CallStatus.RINGING
                                        )
                                    )
                                }
                            }, delayMs)
                        }
                    }
                } else {
                    val callCaptureOn = app?.preferences?.config?.value?.phoneCallCaptureEnabled == true
                    if (status != CallStatus.IDLE && !callCaptureOn) return
                    val resolvedContact = if (status != CallStatus.IDLE && rawExtraNum.isNotBlank()) {
                        ContactNameResolver.resolveDisplayName(context, rawExtraNum)
                    } else {
                        null
                    }
                    app?.notificationCenter?.updateCallState(
                        CarCallState(
                            status = status,
                            callerName = resolvedContact ?: rawExtraNum.ifBlank { "藍牙來電" },
                            callerNumber = rawExtraNum,
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
                    }.getOrNull()?.trim().orEmpty()
                    val hfpCallerName = runCatching {
                        callObj.javaClass.getMethod("getName").invoke(callObj) as? String
                    }.getOrNull()?.trim().orEmpty()
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
                    if (mapped != CallStatus.IDLE && app.preferences.config.value.phoneCallCaptureEnabled != true) {
                        return
                    }
                    val resolvedContact = if (mapped != CallStatus.IDLE) {
                        hfpCallerName.takeIf { !ContactNameResolver.isGenericOrNumberOnly(it) }
                            ?: ContactNameResolver.resolveDisplayName(context, number)
                    } else {
                        null
                    }
                    app.notificationCenter.updateCallState(
                        CarCallState(
                            status = mapped,
                            callerName = resolvedContact ?: hfpCallerName.ifBlank { number.ifBlank { "藍牙免持來電" } },
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

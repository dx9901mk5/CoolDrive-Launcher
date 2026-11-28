package com.dxcar.launcher.comms

import android.os.Build
import android.telecom.Call
import android.telecom.CallScreeningService
import androidx.annotation.RequiresApi
import com.dxcar.launcher.DxCarApp
import com.dxcar.launcher.data.DeviceRole
import java.util.UUID

/**
 * Google Play-compliant Android 10+ CallScreeningService for Phone Companion mode:
 * Receives the exact incoming phone number (`Call.Details.handle`) BEFORE the first ring
 * without requiring READ_CALL_LOG, resolves the Contact Name via READ_CONTACTS, and immediately
 * forwards both Contact Name + Phone Number to the Car AI Box. Never blocks or silences calls.
 */
@RequiresApi(Build.VERSION_CODES.Q)
class PhoneCallScreeningService : CallScreeningService() {

    override fun onScreenCall(callDetails: Call.Details) {
        // Always allow the call immediately without blocking or silencing
        runCatching {
            respondToCall(callDetails, CallResponse.Builder().build())
        }

        val direction = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            callDetails.callDirection
        } else {
            Call.Details.DIRECTION_INCOMING
        }
        if (direction != Call.Details.DIRECTION_INCOMING) return

        val app = DxCarApp.instance ?: return
        val cfg = app.preferences.config.value
        if (cfg.deviceRole != DeviceRole.PHONE_COMPANION || !cfg.phoneCallCaptureEnabled) return

        val rawHandle = callDetails.handle?.schemeSpecificPart?.trim().orEmpty()
        val cleanNum = ContactNameResolver.extractPlausiblePhoneNumber(rawHandle).ifBlank { rawHandle }
        val cnapName = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            callDetails.callerDisplayName?.trim().orEmpty()
        } else {
            ""
        }

        val contactName = if (cleanNum.isNotBlank()) {
            ContactNameResolver.resolveDisplayName(this, cleanNum)
        } else {
            null
        }
        val bestDisplay = contactName
            ?: cnapName.takeIf { !ContactNameResolver.isGenericOrNumberOnly(it) }
            ?: cleanNum.ifBlank { "行動電話來電" }

        if (cleanNum.isNotBlank() || !ContactNameResolver.isPureGenericPlaceholder(bestDisplay)) {
            lastScreenedCaller = ScreenedCallerSnapshot(
                displayName = bestDisplay,
                phoneNumber = cleanNum,
                timestampMs = System.currentTimeMillis()
            )
        }

        BluetoothCallReceiver.lastPhoneTelephonyStatus = CallStatus.RINGING
        val ok = BluetoothCompanionService.sendPacketFromPhone(
            BridgePacket(
                type = PacketType.CALL_STATE,
                id = UUID.randomUUID().toString(),
                sender = bestDisplay,
                phoneNumber = cleanNum,
                appName = "行動電話",
                callStatus = CallStatus.RINGING
            )
        )
        BluetoothCompanionService.appendExternalLog(
            if (ok) "來電辨識轉傳：$bestDisplay ($cleanNum)" else "已排入來電辨識：$bestDisplay ($cleanNum)"
        )
    }

    data class ScreenedCallerSnapshot(
        val displayName: String,
        val phoneNumber: String,
        val timestampMs: Long
    )

    companion object {
        @Volatile
        private var lastScreenedCaller: ScreenedCallerSnapshot? = null

        fun recentScreenedCaller(maxAgeMs: Long = 60_000L): ResolvedCallerInfo? {
            val snap = lastScreenedCaller ?: return null
            if (System.currentTimeMillis() - snap.timestampMs > maxAgeMs) return null
            if (snap.phoneNumber.isBlank() && ContactNameResolver.isPureGenericPlaceholder(snap.displayName)) {
                return null
            }
            return ResolvedCallerInfo(snap.displayName, snap.phoneNumber)
        }

        fun clearScreenedCaller() {
            lastScreenedCaller = null
        }
    }
}

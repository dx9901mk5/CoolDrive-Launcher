package com.dxcar.launcher.comms

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.widget.Toast
import com.dxcar.launcher.DxCarApp
import com.dxcar.launcher.data.DeviceRole
import java.util.UUID

/**
 * Car-side Translucent Dialer Proxy Activity:
 * Registers DX-Car Launcher as a Phone / Call-capable handler on the AI Box so that
 * Car Gemini / steering-wheel voice commands ("打電話給 XXX" / ACTION_CALL / ACTION_DIAL / tel:)
 * recognize that the Car App can place calls, intercept the call seamlessly without covering the map,
 * and forward it over Bluetooth/Wi-Fi Bridge to the Smartphone's original Phone App to place the call!
 *
 * Automatically ENABLED in Car Mode (AI_BOX_LAUNCHER) and DISABLED in Phone Mode (PHONE_COMPANION)
 * so it NEVER intercepts normal calls on the user's smartphone.
 */
class CarDialerBridgeActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleDialIntent(intent)
        finish()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        handleDialIntent(intent)
        finish()
    }

    private fun handleDialIntent(intent: Intent?) {
        val app = DxCarApp.instance
        val cfg = app?.preferences?.config?.value

        // Safety guard: if invoked accidentally on a phone, immediately forward to the phone's native dialer
        if (cfg?.deviceRole == DeviceRole.PHONE_COMPANION) {
            syncComponentEnabled(this, DeviceRole.PHONE_COMPANION)
            val dataUri = intent?.data
            if (dataUri != null) {
                BluetoothCompanionService.launchNativePhoneDialer(
                    context = this,
                    rawPhone = dataUri.schemeSpecificPart.orEmpty(),
                    directCall = intent.action == Intent.ACTION_CALL
                )
            }
            return
        }

        val (resolvedName, resolvedNumber) = extractTargetFromIntent(intent)
        val cleanNum = resolvedNumber.filter { it.isDigit() || it == '+' || it == '*' || it == '#' }

        if (cleanNum.isBlank() && resolvedName.isBlank()) {
            // Bare ACTION_DIAL / CALL_BUTTON without a target number -> open paired phone dialer or notify
            val sent = BluetoothCompanionService.sendPacketFromCar(
                BridgePacket(
                    type = PacketType.DIAL_PHONE,
                    id = UUID.randomUUID().toString(),
                    sender = "",
                    phoneNumber = ""
                )
            )
            val msg = if (sent) {
                "📞 已於配對手機開啟撥號盤"
            } else {
                "⚠️ 請先連線手機同步端，即可透過車機語音／方向盤撥打手機電話"
            }
            Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
            return
        }

        val displayName = resolvedName
            .ifBlank { ContactNameResolver.resolveDisplayName(this, cleanNum).orEmpty() }
            .ifBlank { ContactNameResolver.formatPhoneNumberDisplay(cleanNum) }

        val sentToPhone = BluetoothCompanionService.sendPacketFromCar(
            BridgePacket(
                type = PacketType.DIAL_PHONE,
                id = UUID.randomUUID().toString(),
                sender = displayName,
                phoneNumber = cleanNum
            )
        )

        if (sentToPhone) {
            val formattedNum = ContactNameResolver.formatPhoneNumberDisplay(cleanNum)
            val toastLabel = if (formattedNum.isNotBlank() && !displayName.equals(formattedNum, ignoreCase = true)) {
                "$displayName ($formattedNum)"
            } else {
                displayName
            }
            app?.ttsAnnouncer?.speak("正在透過手機撥打給$displayName")
            Toast.makeText(this, "📞 已傳送至手機原始電話撥打：$toastLabel", Toast.LENGTH_LONG).show()
        } else {
            // Phone bridge not connected right now — fallback to car box's own OEM dialer if present (excluding self!)
            val fallbackOk = BluetoothCompanionService.launchNativePhoneDialer(
                context = this,
                rawPhone = cleanNum,
                directCall = false
            )
            val msg = if (fallbackOk) {
                "📞 手機同步未連線，已改開本機撥號：$displayName"
            } else {
                "⚠️ 尚未連線手機端同步，無法透過手機撥打：$displayName"
            }
            Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
        }
    }

    private fun extractTargetFromIntent(intent: Intent?): Pair<String, String> {
        if (intent == null) return "" to ""
        val data: Uri? = intent.data
        if (data != null) {
            when (data.scheme?.lowercase()) {
                "tel", "voicemail" -> {
                    val decoded = runCatching {
                        java.net.URLDecoder.decode(data.schemeSpecificPart.orEmpty(), "UTF-8")
                    }.getOrDefault(data.schemeSpecificPart.orEmpty()).trim()
                    val num = ContactNameResolver.extractPlausiblePhoneNumber(decoded).ifBlank { decoded }
                    return "" to num
                }
                "content" -> {
                    // Google Assistant / Gemini may pass content://com.android.contacts/data/<id>
                    val fromContactUri = runCatching {
                        contentResolver.query(
                            data,
                            arrayOf(
                                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                                ContactsContract.CommonDataKinds.Phone.NUMBER
                            ),
                            null,
                            null,
                            null
                        )?.use { c ->
                            if (c.moveToFirst()) {
                                val nIdx = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                                val pIdx = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                                val nm = if (nIdx >= 0) c.getString(nIdx)?.trim().orEmpty() else ""
                                val ph = if (pIdx >= 0) c.getString(pIdx)?.trim().orEmpty() else ""
                                nm to ph
                            } else null
                        }
                    }.getOrNull()
                    if (fromContactUri != null && (fromContactUri.first.isNotBlank() || fromContactUri.second.isNotBlank())) {
                        return fromContactUri
                    }
                }
            }
        }

        val extraNum = intent.getStringExtra(Intent.EXTRA_PHONE_NUMBER)?.trim()
            ?: intent.getStringExtra("phone")?.trim()
            ?: intent.getStringExtra("number")?.trim()
            ?: ""
        val extraName = intent.getStringExtra("name")?.trim()
            ?: intent.getStringExtra(Intent.EXTRA_TEXT)?.trim()
            ?: ""
        val parsedNum = ContactNameResolver.extractPlausiblePhoneNumber(extraNum.ifBlank { extraName })
        return extraName to parsedNum
    }

    companion object {
        fun syncComponentEnabled(context: Context, role: DeviceRole) {
            runCatching {
                val pm = context.packageManager
                val comp = ComponentName(context, CarDialerBridgeActivity::class.java)
                val targetState = if (role == DeviceRole.AI_BOX_LAUNCHER) {
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                } else {
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                }
                if (pm.getComponentEnabledSetting(comp) != targetState) {
                    pm.setComponentEnabledSetting(
                        comp,
                        targetState,
                        PackageManager.DONT_KILL_APP
                    )
                }
            }
        }
    }
}

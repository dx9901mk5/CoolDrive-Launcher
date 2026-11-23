package com.dxcar.launcher.comms

import android.app.Notification
import android.app.PendingIntent
import android.app.RemoteInput
import android.content.Intent
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.dxcar.launcher.DxCarApp
import com.dxcar.launcher.data.DeviceRole
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

data class NotificationActionHandle(
    val sbnKey: String,
    val replyAction: Notification.Action?,
    val remoteInput: RemoteInput?,
    val markReadAction: Notification.Action?,
    val answerAction: Notification.Action?,
    val declineAction: Notification.Action?
)

/**
 * Dual-role NotificationListenerService:
 * - On AI Box:
 *   1) Powers MediaControlBridge (active system MediaSessions: YouTube, Spotify, KKBOX, etc.)
 *   2) Extracts live Google Maps Turn-by-Turn navigation instructions for the Home Map HUD
 *   3) Captures AI Box local Bluetooth dialer/SMS/message notifications
 * - On Paired Phone (Companion Mode):
 *   1) Captures LINE / Messenger / WhatsApp / Telegram / SMS / VoIP Call notifications
 *   2) Streams them instantly via Bluetooth SPP to the AI Box
 *   3) Executes RemoteInput Quick/Voice Replies & Mark-as-Read commands sent back from AI Box
 */
class BoxNotificationListenerService : NotificationListenerService() {

    override fun onListenerConnected() {
        super.onListenerConnected()
        instance = this
        DxCarApp.instance?.mediaControlBridge?.refreshSessions()
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        if (instance == this) instance = null
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val statusBarNotif = sbn ?: return
        val pkg = statusBarNotif.packageName ?: return
        if (pkg == packageName) return

        val app = DxCarApp.instance ?: return
        val config = app.preferences.config.value
        val notif = statusBarNotif.notification ?: return
        val extras = notif.extras ?: Bundle.EMPTY

        // 1. Live Google Maps Turn-by-Turn extraction (AI Box mode)
        if (config.deviceRole == DeviceRole.AI_BOX_LAUNCHER && pkg == GOOGLE_MAPS_PACKAGE) {
            extractGoogleMapsNav(statusBarNotif, notif, extras)?.let { nav ->
                app.notificationCenter.updateActiveNavigation(nav)
                return
            }
        }

        // Ignore silent group summary shells
        if ((notif.flags and Notification.FLAG_GROUP_SUMMARY) != 0) return

        val title = extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)?.toString()
            ?: extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
            ?: ""
        val text = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
            ?: extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
            ?: ""

        if (title.isBlank() && text.isBlank()) return

        val handle = extractActionHandle(statusBarNotif.key, notif)
        actionHandles[statusBarNotif.key] = handle

        val isCallNotif = notif.category == Notification.CATEGORY_CALL ||
            handle.answerAction != null ||
            (isDialerOrVoipCall(pkg, title, text))

        val appLabel = resolveAppLabel(pkg)

        if (config.deviceRole == DeviceRole.PHONE_COMPANION) {
            handlePhoneCompanionNotification(
                app = app,
                pkg = pkg,
                appLabel = appLabel,
                sbnKey = statusBarNotif.key,
                title = title,
                text = text,
                isCall = isCallNotif,
                canReply = handle.replyAction != null && handle.remoteInput != null
            )
        } else {
            handleAiBoxLocalNotification(
                app = app,
                pkg = pkg,
                appLabel = appLabel,
                sbnKey = statusBarNotif.key,
                title = title,
                text = text,
                isCall = isCallNotif,
                canReply = handle.replyAction != null && handle.remoteInput != null
            )
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        val statusBarNotif = sbn ?: return
        actionHandles.remove(statusBarNotif.key)
        val app = DxCarApp.instance ?: return
        if (statusBarNotif.packageName == GOOGLE_MAPS_PACKAGE) {
            app.notificationCenter.updateActiveNavigation(null)
        }
        val config = app.preferences.config.value
        if (config.deviceRole == DeviceRole.PHONE_COMPANION &&
            statusBarNotif.notification?.category == Notification.CATEGORY_CALL
        ) {
            BluetoothCompanionService.sendPacketFromPhone(
                BridgePacket(type = PacketType.CALL_STATE, callStatus = CallStatus.IDLE)
            )
        }
    }

    private fun handlePhoneCompanionNotification(
        app: DxCarApp,
        pkg: String,
        appLabel: String,
        sbnKey: String,
        title: String,
        text: String,
        isCall: Boolean,
        canReply: Boolean
    ) {
        val allowed = app.preferences.config.value.companionAllowedPackages
        if (isCall) {
            BluetoothCompanionService.sendPacketFromPhone(
                BridgePacket(
                    type = PacketType.CALL_STATE,
                    id = UUID.randomUUID().toString(),
                    notificationKey = sbnKey,
                    appPackage = pkg,
                    appName = appLabel,
                    sender = title.ifBlank { "未知來電" },
                    text = text,
                    callStatus = CallStatus.RINGING
                )
            )
            return
        }

        if (!allowed.contains(pkg)) return

        BluetoothCompanionService.sendPacketFromPhone(
            BridgePacket(
                type = PacketType.MESSAGE,
                id = UUID.randomUUID().toString(),
                notificationKey = sbnKey,
                appPackage = pkg,
                appName = appLabel,
                sender = title.ifBlank { appLabel },
                text = text,
                canReply = canReply
            )
        )
    }

    private fun handleAiBoxLocalNotification(
        app: DxCarApp,
        pkg: String,
        appLabel: String,
        sbnKey: String,
        title: String,
        text: String,
        isCall: Boolean,
        canReply: Boolean
    ) {
        if (isCall) {
            app.notificationCenter.updateCallState(
                CarCallState(
                    status = CallStatus.RINGING,
                    callerName = title.ifBlank { "藍牙來電" },
                    callerNumber = text,
                    sourceApp = appLabel,
                    fromPhoneBridge = false
                )
            )
            return
        }

        val allowed = app.preferences.config.value.companionAllowedPackages
        if (!allowed.contains(pkg)) return

        app.notificationCenter.postIncomingMessage(
            CarMessageItem(
                id = UUID.randomUUID().toString(),
                notificationKey = sbnKey,
                appPackage = pkg,
                appName = appLabel,
                sender = title.ifBlank { appLabel },
                text = text,
                timestampMs = System.currentTimeMillis(),
                canReply = canReply,
                fromPhoneBridge = false
            )
        )
    }

    private fun extractGoogleMapsNav(
        sbn: StatusBarNotification,
        notif: Notification,
        extras: Bundle
    ): ActiveNavBanner? {
        val isOngoing = (notif.flags and Notification.FLAG_ONGOING_EVENT) != 0 || sbn.isOngoing
        if (!isOngoing) return null

        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        val subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString().orEmpty()

        if (title.isBlank() && text.isBlank()) return null

        // Typical Google Maps notification format:
        // title = "300 公尺 · 忠孝東路四段" or "向右轉進入 建國高架"
        // text = "12 分鐘 (6.5 公里) · 預計抵達 15:25"
        return ActiveNavBanner(
            instruction = title.ifBlank { text },
            distance = extractLeadingDistance(title),
            etaText = text.ifBlank { subText },
            subText = subText
        )
    }

    private fun extractLeadingDistance(title: String): String {
        val regex = Regex("""(\d+(?:\.\d+)?\s*(?:公尺|公里|m|km|ft|mi))""", RegexOption.IGNORE_CASE)
        return regex.find(title)?.value.orEmpty()
    }

    private fun extractActionHandle(key: String, notif: Notification): NotificationActionHandle {
        var replyAct: Notification.Action? = null
        var remoteIn: RemoteInput? = null
        var markReadAct: Notification.Action? = null
        var answerAct: Notification.Action? = null
        var declineAct: Notification.Action? = null

        notif.actions?.forEach { action ->
            val titleLower = action.title?.toString()?.lowercase().orEmpty()
            val inputs = action.remoteInputs
            if (!inputs.isNullOrEmpty() && replyAct == null) {
                replyAct = action
                remoteIn = inputs.first()
            } else if (listOf("已讀", "read", "mark").any { titleLower.contains(it) }) {
                markReadAct = action
            } else if (listOf("接聽", "answer", "accept").any { titleLower.contains(it) }) {
                answerAct = action
            } else if (listOf("掛斷", "拒接", "decline", "reject", "hang").any { titleLower.contains(it) }) {
                declineAct = action
            }
        }

        return NotificationActionHandle(
            sbnKey = key,
            replyAction = replyAct,
            remoteInput = remoteIn,
            markReadAction = markReadAct,
            answerAction = answerAct,
            declineAction = declineAct
        )
    }

    private fun isDialerOrVoipCall(pkg: String, title: String, text: String): Boolean {
        val combined = "$pkg $title $text".lowercase()
        return combined.contains("來電") ||
            combined.contains("incoming call") ||
            combined.contains("語音通話") ||
            pkg.contains("dialer") ||
            pkg.contains("telecom") ||
            pkg.contains("btphone")
    }

    private fun resolveAppLabel(pkg: String): String {
        return when (pkg) {
            "jp.naver.line.android" -> "LINE"
            "com.facebook.orca" -> "Messenger"
            "com.whatsapp" -> "WhatsApp"
            "org.telegram.messenger" -> "Telegram"
            "com.google.android.apps.messaging", "com.samsung.android.messaging" -> "簡訊"
            "com.tencent.mm" -> "WeChat"
            else -> runCatching {
                val ai = packageManager.getApplicationInfo(pkg, 0)
                packageManager.getApplicationLabel(ai).toString()
            }.getOrElse { pkg }
        }
    }

    fun executeReply(notificationKey: String, replyText: String): Boolean {
        val handle = actionHandles[notificationKey] ?: return false
        val action = handle.replyAction ?: return false
        val remoteInput = handle.remoteInput ?: return false
        return runCatching {
            val fillIn = Intent()
            val bundle = Bundle().apply {
                putCharSequence(remoteInput.resultKey, replyText)
            }
            RemoteInput.addResultsToIntent(arrayOf(remoteInput), fillIn, bundle)
            action.actionIntent.send(this, 0, fillIn)
            true
        }.getOrDefault(false)
    }

    fun executeMarkRead(notificationKey: String): Boolean {
        val handle = actionHandles[notificationKey]
        val fired = runCatching {
            handle?.markReadAction?.actionIntent?.send()
            handle?.markReadAction != null
        }.getOrDefault(false)
        if (!fired) {
            runCatching { cancelNotification(notificationKey) }
        }
        return true
    }

    fun executeCallCommand(command: CallActionCommand): Boolean {
        val handle = actionHandles.values.lastOrNull {
            it.answerAction != null || it.declineAction != null
        } ?: return false
        val pending: PendingIntent = when (command) {
            CallActionCommand.ANSWER -> handle.answerAction?.actionIntent
            CallActionCommand.DECLINE -> handle.declineAction?.actionIntent
        } ?: return false
        return runCatching {
            pending.send()
            true
        }.getOrDefault(false)
    }

    companion object {
        const val GOOGLE_MAPS_PACKAGE = "com.google.android.apps.maps"

        @Volatile
        var instance: BoxNotificationListenerService? = null

        private val actionHandles = ConcurrentHashMap<String, NotificationActionHandle>()
    }
}

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
        BluetoothCompanionService.syncListenerBoundState(true)
        DxCarApp.instance?.mediaControlBridge?.refreshSessions()
        runCatching {
            activeNotifications?.forEach { sbn ->
                val pkg = sbn.packageName ?: return@forEach
                val ex = sbn.notification?.extras ?: Bundle.EMPTY
                val t = ex.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
                val tx = ex.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
                    ?: ex.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
                val sub = ex.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString().orEmpty()
                DxCarApp.instance?.weatherRepository?.tryIngestNotification(pkg, t, tx, sub)
            }
        }
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        if (instance == this) {
            instance = null
            BluetoothCompanionService.syncListenerBoundState(false)
        }
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

        // Ignore silent group summary shells & noisy progress/Join upload notifications
        if ((notif.flags and Notification.FLAG_GROUP_SUMMARY) != 0) return
        val hasProgress = extras.getInt(Notification.EXTRA_PROGRESS_MAX, 0) > 0 ||
            extras.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE, false)
        val isNoisyUtility = pkg == "com.joaomgcd.join" ||
            (hasProgress && (notif.flags and Notification.FLAG_ONGOING_EVENT) != 0)
        if (isNoisyUtility) return

        val rawTitle = extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)?.toString()
            ?: extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
            ?: ""
        var callPersonUriNumber = ""
        val callPersonName = runCatching {
            val person = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                extras.getParcelable(Notification.EXTRA_CALL_PERSON, android.app.Person::class.java)
            } else {
                @Suppress("DEPRECATION")
                extras.getParcelable("android.callPerson") as? android.app.Person
            }
            val peopleList = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                extras.getParcelableArrayList(Notification.EXTRA_PEOPLE_LIST, android.app.Person::class.java)
            } else {
                @Suppress("DEPRECATION")
                extras.getParcelableArrayList(Notification.EXTRA_PEOPLE_LIST)
            }
            val primaryPerson = person ?: peopleList?.firstOrNull()
            callPersonUriNumber = ContactNameResolver.extractPlausiblePhoneNumber(primaryPerson?.uri)
            primaryPerson?.name?.toString()?.trim().orEmpty()
        }.getOrDefault("")

        val rawText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
            ?: extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
            ?: ""
        val subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString().orEmpty()
        val tickerStr = notif.tickerText?.toString()?.trim().orEmpty()

        // Feed Google App / System Weather notifications to the Media-Top Weather Widget!
        app.weatherRepository.tryIngestNotification(pkg, rawTitle, rawText, subText)

        // Cache MediaStyle notification metadata & artwork as fallback for MediaControlBridge
        if (notif.category == Notification.CATEGORY_TRANSPORT ||
            extras.containsKey(Notification.EXTRA_MEDIA_SESSION)
        ) {
            val largeBmp = runCatching {
                val icon = notif.getLargeIcon()
                if (icon != null) {
                    val d = icon.loadDrawable(this)
                    if (d is android.graphics.drawable.BitmapDrawable && d.bitmap != null) {
                        d.bitmap
                    } else if (d != null) {
                        val w = d.intrinsicWidth.coerceAtLeast(128)
                        val h = d.intrinsicHeight.coerceAtLeast(128)
                        val b = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
                        val c = android.graphics.Canvas(b)
                        d.setBounds(0, 0, w, h)
                        d.draw(c)
                        b
                    } else null
                } else {
                    @Suppress("DEPRECATION")
                    extras.getParcelable(Notification.EXTRA_LARGE_ICON) as? android.graphics.Bitmap
                }
            }.getOrNull()
            if (rawTitle.isNotBlank() || largeBmp != null) {
                cachedMediaNotifications[pkg] = CachedMediaNotification(
                    title = rawTitle,
                    artist = rawText.ifBlank { subText },
                    largeIcon = largeBmp
                )
            }
            app.mediaControlBridge.refreshSessions()
        }

        val handle = extractActionHandle(statusBarNotif.key, notif)
        actionHandles[statusBarNotif.key] = handle
        cacheFavoriteMediaActionIfPresent(pkg, notif)

        val isMissedOrEnded = isMissedOrEndedCall(rawTitle, rawText)
        if (isMissedOrEnded) {
            if (lastActiveCallKey != null || isDialerOrVoipCall(pkg, rawTitle, rawText)) {
                lastActiveCallKey = null
                if (config.deviceRole == DeviceRole.PHONE_COMPANION) {
                    BluetoothCompanionService.sendPacketFromPhone(
                        BridgePacket(type = PacketType.CALL_STATE, callStatus = CallStatus.IDLE)
                    )
                } else {
                    app.notificationCenter.updateCallState(CarCallState(status = CallStatus.IDLE))
                }
            }
            return
        }

        val templateName = extras.getString(Notification.EXTRA_TEMPLATE).orEmpty()
        val isCallStyleNotif = templateName.contains("CallStyle", ignoreCase = true)
        val isCallNotif = notif.category == Notification.CATEGORY_CALL ||
            isCallStyleNotif ||
            handle.answerAction != null ||
            (notif.fullScreenIntent != null && isDialerOrVoipCall(pkg, rawTitle, rawText)) ||
            isDialerOrVoipCall(pkg, rawTitle, rawText)

        // Extract custom OEM InCallUI RemoteViews strings (vivo OriginOS / Xiaomi / Samsung) AND all extras strings
        // on EVERY call notification so both Contact Name AND Phone Number are reliably captured on Android 14/15/16!
        @Suppress("DEPRECATION")
        val remoteViewStrings = if (isCallNotif) {
            ContactNameResolver.extractRemoteViewsStrings(
                context = this,
                sourcePackage = pkg,
                notif.headsUpContentView,
                notif.bigContentView,
                notif.contentView
            )
        } else {
            emptyList()
        }
        val extraBundleStrings = if (isCallNotif) {
            ContactNameResolver.extractAllStringsFromExtras(extras)
        } else {
            emptyList()
        }
        val allCallStrings = (remoteViewStrings + extraBundleStrings).distinct()
        val rvCandidateTitle = allCallStrings.firstOrNull {
            !ContactNameResolver.isGenericOrNumberOnly(it) && !isCallActionLabel(it)
        }.orEmpty().ifBlank {
            allCallStrings.firstOrNull {
                !ContactNameResolver.isPureGenericPlaceholder(it) && !isCallActionLabel(it)
            }.orEmpty()
        }
        val rvCandidateNum = allCallStrings.firstNotNullOfOrNull {
            ContactNameResolver.extractPlausiblePhoneNumber(it).takeIf { n -> n.isNotBlank() }
        }.orEmpty()
        val infoText = extras.getCharSequence(Notification.EXTRA_INFO_TEXT)?.toString().orEmpty()
        val extraNumber = callPersonUriNumber
            .ifBlank { rvCandidateNum }
            .ifBlank { ContactNameResolver.extractPlausiblePhoneNumber(subText) }
            .ifBlank { ContactNameResolver.extractPlausiblePhoneNumber(infoText) }
            .ifBlank { ContactNameResolver.extractPlausiblePhoneNumber(tickerStr) }

        val title = when {
            callPersonName.isNotBlank() && ContactNameResolver.isGenericOrNumberOnly(rawTitle) -> callPersonName
            rawTitle.isNotBlank() && !ContactNameResolver.isPureGenericPlaceholder(rawTitle) -> rawTitle
            rvCandidateTitle.isNotBlank() -> rvCandidateTitle
            callPersonName.isNotBlank() -> callPersonName
            extraNumber.isNotBlank() && isCallNotif -> extraNumber
            rawTitle.isNotBlank() -> rawTitle
            tickerStr.isNotBlank() && isCallNotif -> tickerStr
            else -> ""
        }
        val text = rawText.ifBlank { extraNumber.ifBlank { subText } }

        if (!isCallNotif && title.isBlank() && text.isBlank()) return

        val appLabel = resolveAppLabel(pkg)
        val explicitCallNum = extraNumber
        val detectedCallStatus = if (isCallNotif && isOngoingActiveCall(title, text, subText, notif, handle)) {
            CallStatus.ACTIVE
        } else {
            CallStatus.RINGING
        }

        if (config.deviceRole == DeviceRole.PHONE_COMPANION) {
            handlePhoneCompanionNotification(
                app = app,
                pkg = pkg,
                appLabel = appLabel,
                sbnKey = statusBarNotif.key,
                title = title,
                text = text,
                explicitPhoneNumber = explicitCallNum,
                isCall = isCallNotif,
                callStatus = detectedCallStatus,
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
                explicitPhoneNumber = explicitCallNum,
                isCall = isCallNotif,
                callStatus = detectedCallStatus,
                canReply = handle.replyAction != null && handle.remoteInput != null
            )
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        val statusBarNotif = sbn ?: return
        actionHandles.remove(statusBarNotif.key)
        val app = DxCarApp.instance ?: return
        val pkg = statusBarNotif.packageName.orEmpty()
        if (pkg == GOOGLE_MAPS_PACKAGE) {
            app.notificationCenter.updateActiveNavigation(null)
        }
        val notif = statusBarNotif.notification
        val wasCall = statusBarNotif.key == lastActiveCallKey ||
            notif?.category == Notification.CATEGORY_CALL ||
            isDialerOrVoipCall(pkg, "", "")
        if (wasCall) {
            lastActiveCallKey = null
            val config = app.preferences.config.value
            if (config.deviceRole == DeviceRole.PHONE_COMPANION) {
                BluetoothCompanionService.sendPacketFromPhone(
                    BridgePacket(type = PacketType.CALL_STATE, callStatus = CallStatus.IDLE)
                )
            } else {
                app.notificationCenter.updateCallState(CarCallState(status = CallStatus.IDLE))
            }
        }
    }

    private fun handlePhoneCompanionNotification(
        app: DxCarApp,
        pkg: String,
        appLabel: String,
        sbnKey: String,
        title: String,
        text: String,
        explicitPhoneNumber: String = "",
        isCall: Boolean,
        callStatus: CallStatus = CallStatus.RINGING,
        canReply: Boolean
    ) {
        val cfg = app.preferences.config.value
        if (isCall) {
            if (!cfg.phoneCallCaptureEnabled) return
            lastActiveCallKey = sbnKey
            var extractedNum = explicitPhoneNumber
                .ifBlank { ContactNameResolver.extractPlausiblePhoneNumber(title) }
                .ifBlank { ContactNameResolver.extractPlausiblePhoneNumber(text) }
            var resolvedName = if (ContactNameResolver.isGenericOrNumberOnly(title)) {
                ContactNameResolver.resolveDisplayName(this, extractedNum.ifBlank { title })
                    ?: ContactNameResolver.resolveDisplayName(this, text)
            } else {
                null
            }
            val nonGenericTitle = title.takeIf { !ContactNameResolver.isPureGenericPlaceholder(it) }.orEmpty()
            // Merge PhoneCallScreeningService exact number if notification lacked raw digits
            val screened = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                PhoneCallScreeningService.recentScreenedCaller()
            } else {
                null
            }
            if (extractedNum.isBlank() && screened != null && screened.phoneNumber.isNotBlank()) {
                extractedNum = screened.phoneNumber
            }
            if (resolvedName == null && screened != null && !ContactNameResolver.isGenericOrNumberOnly(screened.displayName)) {
                resolvedName = screened.displayName
            }
            if (resolvedName == null && nonGenericTitle.isBlank() && extractedNum.isBlank()) {
                val recentLog = ContactNameResolver.queryRecentCallLogCaller(this)
                if (recentLog != null) {
                    extractedNum = recentLog.phoneNumber
                    if (!ContactNameResolver.isGenericOrNumberOnly(recentLog.displayName)) {
                        resolvedName = recentLog.displayName
                    }
                }
            }
            // Contacts show Contact Name + Phone Number underneath; backfill phone number from Contacts if we only got the name!
            val finalCallerName = resolvedName
                ?: nonGenericTitle.ifBlank { extractedNum.ifBlank { "未知來電" } }
            if (extractedNum.isBlank() && !ContactNameResolver.isGenericOrNumberOnly(finalCallerName)) {
                val byName = ContactNameResolver.findPhoneNumberByContactName(this, finalCallerName)
                if (byName != null && byName.second.isNotBlank()) {
                    extractedNum = byName.second
                }
            }
            val ok = BluetoothCompanionService.sendPacketFromPhone(
                BridgePacket(
                    type = PacketType.CALL_STATE,
                    id = UUID.randomUUID().toString(),
                    notificationKey = sbnKey,
                    appPackage = pkg,
                    appName = appLabel,
                    sender = finalCallerName,
                    phoneNumber = extractedNum,
                    text = text,
                    callStatus = callStatus
                )
            )
            BluetoothCompanionService.appendExternalLog(
                if (ok) "已轉傳來電 [$appLabel] $finalCallerName ($extractedNum)" else "已排入來電傳送 [$appLabel] $finalCallerName"
            )
            return
        }

        if (!cfg.companionSyncAllApps && !cfg.companionAllowedPackages.contains(pkg)) return
        // Skip noisy OS, camera processing, backup progress & Android Auto background status shells when syncing all apps
        if (cfg.companionSyncAllApps && (
                pkg == "android" ||
                    pkg == "com.android.systemui" ||
                    pkg == "com.android.camera" ||
                    pkg == "com.vivo.camera" ||
                    pkg == "com.google.android.apps.photos" ||
                    pkg == "com.android.providers.downloads" ||
                    pkg == "com.google.android.projection.gearhead"
                )
        ) return

        val ok = BluetoothCompanionService.sendPacketFromPhone(
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
        BluetoothCompanionService.appendExternalLog(
            if (ok) "已送出 [$appLabel] $title" else "攔截 [$appLabel] 但尚未連上車機"
        )
    }

    private fun handleAiBoxLocalNotification(
        app: DxCarApp,
        pkg: String,
        appLabel: String,
        sbnKey: String,
        title: String,
        text: String,
        explicitPhoneNumber: String = "",
        isCall: Boolean,
        callStatus: CallStatus = CallStatus.RINGING,
        canReply: Boolean
    ) {
        val cfg = app.preferences.config.value
        if (isCall) {
            if (!cfg.phoneCallCaptureEnabled) return
            lastActiveCallKey = sbnKey
            val extractedNum = explicitPhoneNumber
                .ifBlank { ContactNameResolver.extractPlausiblePhoneNumber(title) }
                .ifBlank { ContactNameResolver.extractPlausiblePhoneNumber(text) }
            val resolvedName = if (ContactNameResolver.isGenericOrNumberOnly(title)) {
                ContactNameResolver.resolveDisplayName(this, extractedNum.ifBlank { title })
            } else {
                null
            }
            val nonGenericTitle = title.takeIf { !ContactNameResolver.isPureGenericPlaceholder(it) }.orEmpty()
            app.notificationCenter.updateCallState(
                CarCallState(
                    status = callStatus,
                    callerName = resolvedName ?: nonGenericTitle.ifBlank { extractedNum.ifBlank { "藍牙來電" } },
                    callerNumber = extractedNum,
                    sourceApp = appLabel,
                    fromPhoneBridge = false
                )
            )
            runCatching { cancelNotification(sbnKey) }
            return
        }

        if (cfg.phoneCallCaptureEnabled && pkg != packageName && pkg != GOOGLE_MAPS_PACKAGE) {
            runCatching { cancelNotification(sbnKey) }
        }

        val allowed = cfg.companionAllowedPackages
        if (!cfg.companionSyncAllApps && !allowed.contains(pkg)) return

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

    private fun isMissedOrEndedCall(title: String, text: String): Boolean {
        val combined = "${title.lowercase()} ${text.lowercase()}"
        val endedTokens = listOf(
            "未接來電", "未接来电", "未接通話", "未接通话", "未接",
            "已拒絕", "已拒绝", "通話結束", "通话结束", "已掛斷", "已挂断",
            "語音信箱", "语音信箱", "不在着信",
            "missed call", "missed", "declined", "call ended", "ended", "voicemail"
        )
        return endedTokens.any { combined.contains(it) }
    }

    private fun isOngoingActiveCall(
        title: String,
        text: String,
        subText: String,
        notif: Notification,
        handle: NotificationActionHandle
    ): Boolean {
        val combined = "${title.lowercase()} ${text.lowercase()} ${subText.lowercase()}"
        if (handle.answerAction != null) return false
        val activeTokens = listOf(
            "通話中", "通话中", "正在通話", "正在通话", "撥號中", "拨号中", "呼叫中",
            "ongoing", "in call", "active call", "dialing", "通話時間", "通話保留"
        )
        if (activeTokens.any { combined.contains(it) }) return true
        val hasChrono = notif.extras?.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER, false) == true
        if (hasChrono && handle.declineAction != null) return true
        if (Regex("""\b\d{1,2}:\d{2}\b""").containsMatchIn(combined) && handle.declineAction != null) return true
        return false
    }

    private fun isCallActionLabel(str: String): Boolean {
        val low = str.trim().lowercase()
        return low in setOf("接聽", "掛斷", "拒接", "靜音", "擴音", "免持", "answer", "decline", "reject", "hang up", "mute", "speaker")
    }

    private fun isDialerOrVoipCall(pkg: String, title: String, text: String): Boolean {
        if (isMissedOrEndedCall(title, text)) return false
        val pkgLower = pkg.lowercase()
        val combined = "$pkgLower ${title.lowercase()} ${text.lowercase()}"
        return combined.contains("來電") ||
            combined.contains("来电") ||
            combined.contains("incoming call") ||
            combined.contains("語音通話") ||
            combined.contains("免費通話") ||
            pkgLower.contains("incallui") ||
            pkgLower.contains("dialer") ||
            pkgLower.contains("telecom") ||
            pkgLower == "com.android.phone" ||
            pkgLower == "com.android.contacts" ||
            pkgLower == "com.carsyso.bluetooth" ||
            pkgLower.contains("vivo.phone") ||
            pkgLower.contains("vivo.dialer") ||
            pkgLower.contains("bbk.incallui") ||
            pkgLower.contains("iqoo.dialer") ||
            pkgLower.contains("btphone")
    }

    /**
     * Actively scans all current status bar notifications for an active incoming/ongoing call
     * (used by BluetoothCallReceiver when PHONE_STATE fires before/without EXTRA_INCOMING_NUMBER on Play flavor).
     */
    fun scanActiveCallNotificationOnPhone(): ResolvedCallerInfo? {
        val list = runCatching { activeNotifications }.getOrNull() ?: return null
        for (sbn in list) {
            val pkg = sbn.packageName ?: continue
            if (pkg == packageName) continue
            val notif = sbn.notification ?: continue
            val extras = notif.extras ?: Bundle.EMPTY
            val rawTitle = extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)?.toString()
                ?: extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
                ?: ""
            val rawText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
                ?: extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
                ?: ""
            if (isMissedOrEndedCall(rawTitle, rawText)) continue
            val templateName = extras.getString(Notification.EXTRA_TEMPLATE).orEmpty()
            val handle = extractActionHandle(sbn.key, notif)
            val isCall = notif.category == Notification.CATEGORY_CALL ||
                templateName.contains("CallStyle", ignoreCase = true) ||
                handle.answerAction != null ||
                isDialerOrVoipCall(pkg, rawTitle, rawText)
            if (!isCall) continue

            var personUriNum = ""
            val personName = runCatching {
                val p = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                    extras.getParcelable(Notification.EXTRA_CALL_PERSON, android.app.Person::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    extras.getParcelable("android.callPerson") as? android.app.Person
                }
                personUriNum = ContactNameResolver.extractPlausiblePhoneNumber(p?.uri)
                p?.name?.toString()?.trim().orEmpty()
            }.getOrDefault("")

            @Suppress("DEPRECATION")
            val rvStrings = ContactNameResolver.extractRemoteViewsStrings(
                context = this,
                sourcePackage = pkg,
                notif.headsUpContentView,
                notif.bigContentView,
                notif.contentView
            )
            val allStrings = (listOf(rawTitle, rawText) + rvStrings + ContactNameResolver.extractAllStringsFromExtras(extras)).distinct()
            var foundNum = personUriNum.ifBlank {
                allStrings.firstNotNullOfOrNull {
                    ContactNameResolver.extractPlausiblePhoneNumber(it).takeIf { n -> n.isNotBlank() }
                }.orEmpty()
            }
            val foundName = personName.takeIf { !ContactNameResolver.isGenericOrNumberOnly(it) }
                ?: rawTitle.takeIf { !ContactNameResolver.isGenericOrNumberOnly(it) && !isCallActionLabel(it) }
                ?: allStrings.firstOrNull { !ContactNameResolver.isGenericOrNumberOnly(it) && !isCallActionLabel(it) }
                ?: ContactNameResolver.resolveDisplayName(this, foundNum)
                ?: ""

            if (foundNum.isBlank() && foundName.isNotBlank()) {
                foundNum = ContactNameResolver.findPhoneNumberByContactName(this, foundName)?.second.orEmpty()
            }
            val bestDisplay = foundName.ifBlank { foundNum }
            if (bestDisplay.isNotBlank() && !ContactNameResolver.isPureGenericPlaceholder(bestDisplay)) {
                return ResolvedCallerInfo(bestDisplay, foundNum)
            }
        }
        return null
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

    private fun cacheFavoriteMediaActionIfPresent(pkg: String, notif: Notification) {
        val favKeywords = listOf(
            "喜歡", "紅心", "愛心", "收藏", "點讚", "按讚", "讚",
            "love", "like", "heart", "favorite", "favourite", "thumb"
        )
        val unlikeKeywords = listOf("取消喜歡", "取消收藏", "已收藏", "unlike", "unfavorite", "remove")
        val actions = notif.actions ?: return
        for (act in actions) {
            val title = act.title?.toString()?.trim().orEmpty()
            val lower = title.lowercase()
            if (favKeywords.any { lower.contains(it) }) {
                val isAlreadyLiked = unlikeKeywords.any { lower.contains(it) }
                favoriteMediaActions[pkg] = act to isAlreadyLiked
                DxCarApp.instance?.mediaControlBridge?.refreshSessions()
                return
            }
        }
    }

    fun hasFavoriteNotificationAction(pkg: String): Boolean = favoriteMediaActions.containsKey(pkg)

    fun isFavoriteMarkedInNotification(pkg: String): Boolean = favoriteMediaActions[pkg]?.second == true

    fun executeFavoriteNotificationAction(pkg: String): Boolean {
        val action = favoriteMediaActions[pkg]?.first ?: return false
        return runCatching {
            action.actionIntent.send()
            true
        }.getOrDefault(false)
    }

    data class CachedMediaNotification(
        val title: String,
        val artist: String,
        val largeIcon: android.graphics.Bitmap?
    )

    companion object {
        const val GOOGLE_MAPS_PACKAGE = "com.google.android.apps.maps"

        @Volatile
        var lastActiveCallKey: String? = null

        @Volatile
        var instance: BoxNotificationListenerService? = null

        private val actionHandles = ConcurrentHashMap<String, NotificationActionHandle>()
        private val favoriteMediaActions = ConcurrentHashMap<String, Pair<Notification.Action, Boolean>>()
        private val cachedMediaNotifications = ConcurrentHashMap<String, CachedMediaNotification>()

        fun getCachedMediaNotification(pkg: String): CachedMediaNotification? =
            cachedMediaNotifications[pkg]
    }
}

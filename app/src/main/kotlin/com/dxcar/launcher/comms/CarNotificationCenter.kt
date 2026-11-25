package com.dxcar.launcher.comms

import com.dxcar.launcher.data.LauncherPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

class CarNotificationCenter(
    private val preferences: LauncherPreferences,
    private val ttsAnnouncer: TtsAnnouncer
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _activeCall = MutableStateFlow(CarCallState())
    val activeCall: StateFlow<CarCallState> = _activeCall.asStateFlow()

    private val _popupMessage = MutableStateFlow<CarMessageItem?>(null)
    val popupMessage: StateFlow<CarMessageItem?> = _popupMessage.asStateFlow()

    private val _messages = MutableStateFlow<List<CarMessageItem>>(emptyList())
    val messages: StateFlow<List<CarMessageItem>> = _messages.asStateFlow()

    private val _activeNavigation = MutableStateFlow<ActiveNavBanner?>(null)
    val activeNavigation: StateFlow<ActiveNavBanner?> = _activeNavigation.asStateFlow()

    private var popupDismissJob: Job? = null

    /** Callback set by BluetoothCompanionService / BoxNotificationListenerService for remote replies */
    var onReplyDispatch: ((CarMessageItem, String) -> Boolean)? = null
    var onMarkReadDispatch: ((CarMessageItem) -> Unit)? = null
    var onCallActionDispatch: ((CallActionCommand) -> Boolean)? = null

    fun postIncomingMessage(item: CarMessageItem) {
        // Deduplicate rapid identical bursts within 3 seconds
        val existing = _messages.value.firstOrNull()
        if (existing != null &&
            existing.sender == item.sender &&
            existing.text == item.text &&
            (item.timestampMs - existing.timestampMs) < 3_000L
        ) {
            return
        }

        val updated = (listOf(item) + _messages.value).take(30)
        _messages.value = updated
        _popupMessage.value = item

        if (preferences.config.value.autoReadMessagesTts) {
            ttsAnnouncer.speakMessage(item.appName, item.sender, item.text)
        }

        schedulePopupDismiss(item.id)
    }

    fun dismissPopupMessage() {
        popupDismissJob?.cancel()
        _popupMessage.value = null
    }

    fun pausePopupDismiss() {
        popupDismissJob?.cancel()
    }

    private fun schedulePopupDismiss(messageId: String) {
        popupDismissJob?.cancel()
        popupDismissJob = scope.launch {
            delay(10_000L)
            if (_popupMessage.value?.id == messageId) {
                _popupMessage.value = null
            }
        }
    }

    fun markMessageRead(item: CarMessageItem) {
        _messages.value = _messages.value.map {
            if (it.id == item.id) it.copy(isRead = true) else it
        }
        if (_popupMessage.value?.id == item.id) {
            dismissPopupMessage()
        }
        onMarkReadDispatch?.invoke(item)
    }

    fun removeMessage(item: CarMessageItem) {
        _messages.value = _messages.value.filterNot { it.id == item.id }
        if (_popupMessage.value?.id == item.id) {
            dismissPopupMessage()
        }
    }

    fun clearAllMessages() {
        _messages.value = emptyList()
        dismissPopupMessage()
    }

    fun sendQuickReply(item: CarMessageItem, replyText: String): Boolean {
        val dispatched = onReplyDispatch?.invoke(item, replyText) ?: false
        markMessageRead(item)
        return dispatched
    }

    fun readMessageAloud(item: CarMessageItem) {
        ttsAnnouncer.speakMessage(item.appName, item.sender, item.text)
    }

    fun updateCallState(state: CarCallState) {
        val current = _activeCall.value
        if (state.status == CallStatus.IDLE) {
            _activeCall.value = CarCallState(status = CallStatus.IDLE)
            return
        }
        val startedAt = when {
            state.status == CallStatus.ACTIVE && current.status != CallStatus.ACTIVE ->
                System.currentTimeMillis()
            state.status == CallStatus.ACTIVE ->
                current.startedAtMs.takeIf { it > 0 } ?: System.currentTimeMillis()
            state.status == CallStatus.RINGING && current.status == CallStatus.RINGING && current.startedAtMs > 0 ->
                current.startedAtMs
            state.status == CallStatus.RINGING ->
                System.currentTimeMillis()
            else -> 0L
        }

        // Priority hierarchy: Real Contact Name > Actual Phone Number > Generic Placeholder ("藍牙來電")
        val incomingHasRealName = !ContactNameResolver.isGenericOrNumberOnly(state.callerName)
        val currentHasRealName = current.status != CallStatus.IDLE &&
            !ContactNameResolver.isGenericOrNumberOnly(current.callerName)

        val incomingNumFromName = if (!incomingHasRealName && !ContactNameResolver.isPureGenericPlaceholder(state.callerName)) {
            ContactNameResolver.extractPlausiblePhoneNumber(state.callerName).ifBlank { state.callerName }
        } else {
            ""
        }
        val currentNumFromName = if (current.status != CallStatus.IDLE &&
            !currentHasRealName &&
            !ContactNameResolver.isPureGenericPlaceholder(current.callerName)
        ) {
            ContactNameResolver.extractPlausiblePhoneNumber(current.callerName).ifBlank { current.callerName }
        } else {
            ""
        }

        val mergedNumber = state.callerNumber
            .ifBlank { incomingNumFromName }
            .ifBlank { if (current.status != CallStatus.IDLE) current.callerNumber else "" }
            .ifBlank { currentNumFromName }

        val mergedName = when {
            incomingHasRealName -> state.callerName
            currentHasRealName -> current.callerName
            mergedNumber.isNotBlank() -> mergedNumber
            !ContactNameResolver.isPureGenericPlaceholder(state.callerName) -> state.callerName
            current.status != CallStatus.IDLE && !ContactNameResolver.isPureGenericPlaceholder(current.callerName) -> current.callerName
            state.callerName.isNotBlank() -> state.callerName
            else -> current.callerName.ifBlank { "藍牙來電" }
        }
        val mergedSource = if (incomingHasRealName || current.status == CallStatus.IDLE) {
            state.sourceApp
        } else {
            current.sourceApp.ifBlank { state.sourceApp }
        }

        _activeCall.value = state.copy(
            callerName = mergedName,
            callerNumber = mergedNumber,
            sourceApp = mergedSource,
            startedAtMs = startedAt
        )
    }

    fun performCallAction(action: CallActionCommand) {
        onCallActionDispatch?.invoke(action)
        when (action) {
            CallActionCommand.ANSWER -> {
                if (_activeCall.value.status == CallStatus.RINGING) {
                    updateCallState(_activeCall.value.copy(status = CallStatus.ACTIVE))
                }
            }
            CallActionCommand.DECLINE -> {
                updateCallState(CarCallState(status = CallStatus.IDLE))
            }
        }
    }

    fun updateActiveNavigation(banner: ActiveNavBanner?) {
        _activeNavigation.value = banner
    }

    fun triggerSimulatedCall() {
        updateCallState(
            CarCallState(
                status = CallStatus.RINGING,
                callerName = "王小明（模擬來電）",
                callerNumber = "0912-345-678",
                sourceApp = "藍牙配對手機",
                fromPhoneBridge = true
            )
        )
    }

    fun triggerSimulatedMessage() {
        postIncomingMessage(
            CarMessageItem(
                id = UUID.randomUUID().toString(),
                notificationKey = "sim_${System.currentTimeMillis()}",
                appPackage = "jp.naver.line.android",
                appName = "LINE",
                sender = "陳小美",
                text = "請問大概幾點會到？我先幫你把晚餐買好了！",
                timestampMs = System.currentTimeMillis(),
                canReply = true,
                fromPhoneBridge = true
            )
        )
    }
}

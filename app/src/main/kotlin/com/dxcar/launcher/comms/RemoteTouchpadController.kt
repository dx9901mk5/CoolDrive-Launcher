package com.dxcar.launcher.comms

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import com.dxcar.launcher.accessibility.DxCarAccessibilityService
import com.dxcar.launcher.overlay.PersistentDockOverlayService
import com.dxcar.launcher.system.SystemStatusMonitor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class RemoteCursorState(
    val visible: Boolean = false,
    val xPx: Float = 640f,
    val yPx: Float = 360f,
    val clickPulseSeq: Long = 0L,
    val lastActiveMs: Long = 0L
)

/**
 * Receives live touchpad events sent from the Smartphone Companion and drives:
 * 1. A smooth, car-wide floating mouse cursor on the AI Box screen
 * 2. Accessibility tap / long-press / scroll / Back / Home actions
 * 3. Instant opening of the AI Box's System Pointer Speed / Mouse Sensitivity Settings screen
 */
object RemoteTouchpadController {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val _cursorState = MutableStateFlow(RemoteCursorState())
    val cursorState: StateFlow<RemoteCursorState> = _cursorState.asStateFlow()

    private var initializedCenter = false

    fun onTouchpadPacket(context: Context, packet: BridgePacket) {
        val action = packet.touchAction ?: return
        val dm = context.resources.displayMetrics
        val maxW = dm.widthPixels.toFloat().coerceAtLeast(480f)
        val maxH = dm.heightPixels.toFloat().coerceAtLeast(320f)

        if (!initializedCenter) {
            initializedCenter = true
            _cursorState.value = _cursorState.value.copy(
                xPx = maxW * 0.5f,
                yPx = maxH * 0.5f
            )
        }

        // Ensure overlay window is running so the mouse pointer shows across all apps
        mainHandler.post {
            PersistentDockOverlayService.start(context)
        }

        val now = System.currentTimeMillis()
        when (action) {
            TouchpadAction.MOVE -> {
                val cur = _cursorState.value
                val nextX = (cur.xPx + packet.touchDx).coerceIn(4f, maxW - 8f)
                val nextY = (cur.yPx + packet.touchDy).coerceIn(4f, maxH - 8f)
                _cursorState.value = cur.copy(
                    visible = true,
                    xPx = nextX,
                    yPx = nextY,
                    lastActiveMs = now
                )
            }

            TouchpadAction.CLICK -> {
                val cur = _cursorState.value
                _cursorState.value = cur.copy(
                    visible = true,
                    clickPulseSeq = cur.clickPulseSeq + 1L,
                    lastActiveMs = now
                )
                mainHandler.post {
                    val acc = DxCarAccessibilityService.instance
                    if (acc != null) {
                        acc.dispatchSingleTap(cur.xPx, cur.yPx)
                    } else {
                        Toast.makeText(
                            context,
                            "請於車機設定開啟「無障礙服務」以啟用遠端滑鼠點擊",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }

            TouchpadAction.LONG_CLICK -> {
                val cur = _cursorState.value
                _cursorState.value = cur.copy(
                    visible = true,
                    clickPulseSeq = cur.clickPulseSeq + 1L,
                    lastActiveMs = now
                )
                mainHandler.post {
                    DxCarAccessibilityService.instance?.dispatchLongPress(cur.xPx, cur.yPx)
                }
            }

            TouchpadAction.SCROLL_UP -> {
                val cur = _cursorState.value
                _cursorState.value = cur.copy(visible = true, lastActiveMs = now)
                mainHandler.post {
                    DxCarAccessibilityService.instance?.dispatchScrollSwipe(cur.xPx, cur.yPx, scrollUp = true)
                }
            }

            TouchpadAction.SCROLL_DOWN -> {
                val cur = _cursorState.value
                _cursorState.value = cur.copy(visible = true, lastActiveMs = now)
                mainHandler.post {
                    DxCarAccessibilityService.instance?.dispatchScrollSwipe(cur.xPx, cur.yPx, scrollUp = false)
                }
            }

            TouchpadAction.BACK -> {
                mainHandler.post {
                    DxCarAccessibilityService.instance?.goBack()
                }
            }

            TouchpadAction.HOME -> {
                mainHandler.post {
                    DxCarAccessibilityService.instance?.goHome()
                }
            }

            TouchpadAction.OPEN_POINTER_SPEED_SETTINGS -> {
                mainHandler.post {
                    SystemStatusMonitor.openPointerSpeedSettings(context)
                    Toast.makeText(
                        context,
                        "🖱️ 已從手機喚出車機「滑鼠／指標速度（靈敏度）」設定頁面",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    fun hideIfIdle(nowMs: Long = System.currentTimeMillis()) {
        val cur = _cursorState.value
        if (cur.visible && (nowMs - cur.lastActiveMs) > 6_500L) {
            _cursorState.value = cur.copy(visible = false)
        }
    }
}

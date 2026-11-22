package com.cooldrive.launcher.gestures

import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.abs
import kotlin.math.hypot

sealed interface MultiFingerEvent {
    data class TwoFingerBrightnessDelta(val deltaPercent: Int) : MultiFingerEvent
    data class TwoFingerVolumeDelta(val deltaPercent: Int) : MultiFingerEvent
    data object TwoFingerSwipeLeft : MultiFingerEvent
    data object TwoFingerSwipeRight : MultiFingerEvent
    data object ThreeFingerSwipeDown : MultiFingerEvent
    data object ThreeFingerSwipeLeft : MultiFingerEvent
    data object ThreeFingerSwipeRight : MultiFingerEvent
    data object ThreeFingerTap : MultiFingerEvent
    data object FourFingerPinch : MultiFingerEvent
    data object FourFingerSpread : MultiFingerEvent
}

/**
 * Car-optimized multi-finger gesture detector installed at the Initial pointer pass
 * ONLY when 2+ pointers are simultaneously active, so normal 1-finger touches, clicks,
 * scrolling, and map single-finger panning pass through with zero interference.
 */
fun Modifier.coolDriveMultiFingerGestures(
    enabled: Boolean,
    onGesture: (MultiFingerEvent) -> Unit
): Modifier {
    if (!enabled) return this
    return this.pointerInput(enabled) {
        awaitPointerEventScope {
            while (true) {
                val firstEvent = awaitPointerEvent(PointerEventPass.Initial)
                val activeInitial = firstEvent.changes.filter { it.pressed }
                if (activeInitial.size < 2) continue

                val widthPx = size.width.toFloat().coerceAtLeast(1f)
                var maxFingers = activeInitial.size
                var startCentroid = centroidOf(activeInitial.map { it.position })
                var lastCentroid = startCentroid
                var startSpread = spreadOf(activeInitial.map { it.position }, startCentroid)
                var lastSpread = startSpread
                var accumulatedBrightnessPx = 0f
                var accumulatedVolumePx = 0f
                var verticalDragConsumed = false
                val startTimeMs = System.currentTimeMillis()

                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val pressed = event.changes.filter { it.pressed }
                    if (pressed.isEmpty()) break

                    if (pressed.size > maxFingers) {
                        maxFingers = pressed.size
                        val pts = pressed.map { it.position }
                        startCentroid = centroidOf(pts)
                        lastCentroid = startCentroid
                        startSpread = spreadOf(pts, startCentroid)
                        lastSpread = startSpread
                    }

                    if (pressed.size >= 2) {
                        val pts = pressed.map { it.position }
                        val centroid = centroidOf(pts)
                        val delta = centroid - lastCentroid
                        lastCentroid = centroid
                        lastSpread = spreadOf(pts, centroid)

                        // Live 2-finger vertical drag: Left half = Brightness, Right half = Volume
                        if (maxFingers == 2 && pressed.size == 2) {
                            val totalDx = centroid.x - startCentroid.x
                            val totalDy = centroid.y - startCentroid.y
                            if (abs(totalDy) > 36f && abs(totalDy) > abs(totalDx) * 1.4f) {
                                verticalDragConsumed = true
                                pressed.forEach { it.consume() }
                                val stepPx = 22f
                                if (startCentroid.x < widthPx * 0.5f) {
                                    accumulatedBrightnessPx -= delta.y
                                    val steps = (accumulatedBrightnessPx / stepPx).toInt()
                                    if (steps != 0) {
                                        accumulatedBrightnessPx -= steps * stepPx
                                        onGesture(MultiFingerEvent.TwoFingerBrightnessDelta(steps * 4))
                                    }
                                } else {
                                    accumulatedVolumePx -= delta.y
                                    val steps = (accumulatedVolumePx / stepPx).toInt()
                                    if (steps != 0) {
                                        accumulatedVolumePx -= steps * stepPx
                                        onGesture(MultiFingerEvent.TwoFingerVolumeDelta(steps * 4))
                                    }
                                }
                            }
                        } else if (maxFingers >= 3) {
                            // Prevent underlying views from reacting during 3/4-finger car gestures
                            pressed.forEach { it.consume() }
                        }
                    }
                }

                // Gesture lifted -> classify discrete multi-finger gestures
                val dx = lastCentroid.x - startCentroid.x
                val dy = lastCentroid.y - startCentroid.y
                val dist = hypot(dx, dy)
                val durationMs = System.currentTimeMillis() - startTimeMs
                val spreadDelta = lastSpread - startSpread

                when {
                    maxFingers >= 4 -> {
                        if (spreadDelta < -45f) {
                            onGesture(MultiFingerEvent.FourFingerPinch)
                        } else if (spreadDelta > 45f) {
                            onGesture(MultiFingerEvent.FourFingerSpread)
                        } else if (dist < 60f) {
                            onGesture(MultiFingerEvent.FourFingerPinch)
                        }
                    }

                    maxFingers == 3 -> {
                        when {
                            dy > 85f && abs(dy) > abs(dx) ->
                                onGesture(MultiFingerEvent.ThreeFingerSwipeDown)

                            dx > 85f && abs(dx) > abs(dy) ->
                                onGesture(MultiFingerEvent.ThreeFingerSwipeRight)

                            dx < -85f && abs(dx) > abs(dy) ->
                                onGesture(MultiFingerEvent.ThreeFingerSwipeLeft)

                            dist < 50f && durationMs < 480L ->
                                onGesture(MultiFingerEvent.ThreeFingerTap)
                        }
                    }

                    maxFingers == 2 && !verticalDragConsumed -> {
                        if (abs(dx) > 95f && abs(dx) > abs(dy) * 1.4f) {
                            if (dx > 0f) {
                                onGesture(MultiFingerEvent.TwoFingerSwipeRight)
                            } else {
                                onGesture(MultiFingerEvent.TwoFingerSwipeLeft)
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun centroidOf(points: List<Offset>): Offset {
    if (points.isEmpty()) return Offset.Zero
    var sx = 0f
    var sy = 0f
    for (p in points) {
        sx += p.x
        sy += p.y
    }
    return Offset(sx / points.size, sy / points.size)
}

private fun spreadOf(points: List<Offset>, centroid: Offset): Float {
    if (points.isEmpty()) return 0f
    var sum = 0f
    for (p in points) {
        sum += hypot(p.x - centroid.x, p.y - centroid.y)
    }
    return sum / points.size
}

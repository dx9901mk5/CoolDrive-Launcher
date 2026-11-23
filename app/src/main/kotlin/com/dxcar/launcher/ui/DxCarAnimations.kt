package com.dxcar.launcher.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Velocity
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sign

/**
 * Tactile spring-scale click modifier for car touchscreens:
 * smoothly compresses to [pressedScale] on touch-down and springs back with subtle bounce on release,
 * while preserving Compose's standard Material ripple indication.
 */
fun Modifier.bouncyClickable(
    enabled: Boolean = true,
    pressedScale: Float = 0.92f,
    onClick: () -> Unit
): Modifier = composed {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) pressedScale else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "bouncyClickScale"
    )
    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .clickable(
            interactionSource = interactionSource,
            indication = LocalIndication.current,
            enabled = enabled,
            onClick = onClick
        )
}

@OptIn(ExperimentalFoundationApi::class)
fun Modifier.bouncyCombinedClickable(
    enabled: Boolean = true,
    pressedScale: Float = 0.92f,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit
): Modifier = composed {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) pressedScale else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "bouncyCombinedScale"
    )
    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .combinedClickable(
            interactionSource = interactionSource,
            indication = LocalIndication.current,
            enabled = enabled,
            onLongClick = onLongClick,
            onClick = onClick
        )
}

/**
 * Real physics-driven rubber-band overscroll bounce for LazyVerticalGrid / Scrollable lists:
 * works consistently across Android 9–16 AI Boxes!
 * - Pulling past top or bottom applies progressive rubber-band resistance up to [maxOverscrollPx].
 * - Flinging into top/bottom gives a crisp momentum impulse and springs back smoothly.
 */
fun Modifier.springBounceOverscroll(
    maxOverscrollPx: Float = 180f
): Modifier = composed {
    val scope = rememberCoroutineScope()
    val offsetY = remember { Animatable(0f) }

    val connection = remember(maxOverscrollPx) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                // If currently overscrolled and user drags back toward neutral, unwind overscroll first
                val cur = offsetY.value
                if (source == NestedScrollSource.UserInput && cur != 0f && sign(cur) != sign(available.y)) {
                    val next = if (abs(available.y) >= abs(cur)) 0f else cur + available.y
                    val consumedY = next - cur
                    scope.launch { offsetY.snapTo(next) }
                    return Offset(0f, consumedY)
                }
                return Offset.Zero
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                if (source == NestedScrollSource.UserInput && available.y != 0f) {
                    val cur = offsetY.value
                    // Progressive rubber-band damping as user pulls further from rest
                    val ratio = (1f - (abs(cur) / maxOverscrollPx)).coerceIn(0.12f, 1f)
                    val dampedDelta = available.y * 0.48f * ratio
                    val target = (cur + dampedDelta).coerceIn(-maxOverscrollPx, maxOverscrollPx)
                    scope.launch { offsetY.snapTo(target) }
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (offsetY.value != 0f) {
                    offsetY.animateTo(
                        targetValue = 0f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMediumLow
                        )
                    )
                }
                return Velocity.Zero
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                if (abs(available.y) > 220f && offsetY.value == 0f) {
                    // Edge-hit bounce impulse when flinging into top or bottom of list
                    val impulse = (available.y * 0.032f).coerceIn(-maxOverscrollPx * 0.55f, maxOverscrollPx * 0.55f)
                    offsetY.animateTo(
                        targetValue = impulse,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = Spring.StiffnessHigh
                        )
                    )
                }
                if (offsetY.value != 0f) {
                    offsetY.animateTo(
                        targetValue = 0f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMediumLow
                        )
                    )
                }
                return Velocity.Zero
            }
        }
    }

    this
        .nestedScroll(connection)
        .graphicsLayer {
            translationY = offsetY.value
        }
}

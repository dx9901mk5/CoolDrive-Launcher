package com.dxcar.launcher.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.dxcar.launcher.data.LauncherStylePreset

data class AccentPreset(
    val nameZh: String,
    val dayColor: Color,
    val nightColor: Color
)

val DxCarAccents = listOf(
    AccentPreset("Google 藍", Color(0xFF1A73E8), Color(0xFF8AB4F8)),
    AccentPreset("車載翠綠", Color(0xFF0F9D58), Color(0xFF34D399)),
    AccentPreset("極光紫", Color(0xFF7C3AED), Color(0xFFA78BFA)),
    AccentPreset("跑車琥珀", Color(0xFFD97706), Color(0xFFFBBF24)),
    AccentPreset("競速紅", Color(0xFFDC2626), Color(0xFFF87171))
)

@Immutable
data class DxCarPalette(
    val isNight: Boolean,
    val canvasBackground: Color,
    val dockBackground: Color,
    val dockIconContainer: Color,
    val dockActiveIndicator: Color,
    val cardSurface: Color,
    val cardSurfaceElevated: Color,
    val cardBorder: Color,
    val primaryText: Color,
    val secondaryText: Color,
    val accent: Color,
    val onAccent: Color,
    val navGreenBanner: Color,
    val callAcceptGreen: Color,
    val callDeclineRed: Color,
    val statusConnected: Color,
    val statusDisconnected: Color,
    val glassOverlay: Color,
    val cardCornerDp: Int = 24,
    val cardBorderWidthDp: Float = 1f
)

val LocalDxCarColors = staticCompositionLocalOf {
    buildDxCarPalette(isNight = true, accentIndex = 0)
}

fun buildDxCarPalette(
    isNight: Boolean,
    accentIndex: Int,
    stylePreset: LauncherStylePreset = LauncherStylePreset.COOLWALK_CLASSIC
): DxCarPalette {
    val preset = DxCarAccents.getOrElse(accentIndex) { DxCarAccents.first() }
    val base = if (isNight) {
        DxCarPalette(
            isNight = true,
            canvasBackground = Color(0xFF090C12),
            dockBackground = Color(0xFF10141E),
            dockIconContainer = Color(0xFF1B2232),
            dockActiveIndicator = preset.nightColor,
            cardSurface = Color(0xFF161C27),
            cardSurfaceElevated = Color(0xFF202838),
            cardBorder = Color(0xFF2B3548),
            primaryText = Color(0xFFF3F6FC),
            secondaryText = Color(0xFF9BA8BE),
            accent = preset.nightColor,
            onAccent = Color(0xFF081226),
            navGreenBanner = Color(0xFF0B6E4F),
            callAcceptGreen = Color(0xFF22C55E),
            callDeclineRed = Color(0xFFEF4444),
            statusConnected = Color(0xFF4ADE80),
            statusDisconnected = Color(0xFF64748B),
            glassOverlay = Color(0xE6121722)
        )
    } else {
        DxCarPalette(
            isNight = false,
            canvasBackground = Color(0xFFE7EDF5),
            dockBackground = Color(0xFFF4F7FB),
            dockIconContainer = Color(0xFFE2E8F2),
            dockActiveIndicator = preset.dayColor,
            cardSurface = Color(0xFFFFFFFF),
            cardSurfaceElevated = Color(0xFFF3F6FA),
            cardBorder = Color(0xFFD2DCEB),
            primaryText = Color(0xFF111827),
            secondaryText = Color(0xFF4B5563),
            accent = preset.dayColor,
            onAccent = Color(0xFFFFFFFF),
            navGreenBanner = Color(0xFF0D7A56),
            callAcceptGreen = Color(0xFF16A34A),
            callDeclineRed = Color(0xFFDC2626),
            statusConnected = Color(0xFF16A34A),
            statusDisconnected = Color(0xFF94A3B8),
            glassOverlay = Color(0xF2FFFFFF)
        )
    }

    return when (stylePreset) {
        LauncherStylePreset.COOLWALK_CLASSIC -> base
        LauncherStylePreset.OLED_STEALTH -> if (isNight) {
            base.copy(
                canvasBackground = Color(0xFF000000),
                dockBackground = Color(0xFF05070B),
                dockIconContainer = Color(0xFF12161F),
                cardSurface = Color(0xFF0A0D13),
                cardSurfaceElevated = Color(0xFF141923),
                cardBorder = Color(0xFF1F2737),
                cardCornerDp = 22
            )
        } else {
            base.copy(
                canvasBackground = Color(0xFFDFE6F0),
                cardSurface = Color(0xFFFAFCFF),
                cardCornerDp = 22
            )
        }
        LauncherStylePreset.FROSTED_GLASS -> if (isNight) {
            base.copy(
                canvasBackground = Color(0xFF0B1220),
                dockBackground = Color(0xD9162238),
                cardSurface = Color(0xCC1A2740),
                cardSurfaceElevated = Color(0xCC243454),
                cardBorder = preset.nightColor.copy(alpha = 0.34f),
                glassOverlay = Color(0xD9131E33),
                cardCornerDp = 30,
                cardBorderWidthDp = 1.4f
            )
        } else {
            base.copy(
                canvasBackground = Color(0xFFDCE7F7),
                dockBackground = Color(0xE6F7FAFF),
                cardSurface = Color(0xE6FFFFFF),
                cardSurfaceElevated = Color(0xE6EFF5FF),
                cardBorder = preset.dayColor.copy(alpha = 0.28f),
                glassOverlay = Color(0xEBFFFFFF),
                cardCornerDp = 30,
                cardBorderWidthDp = 1.4f
            )
        }
        LauncherStylePreset.HIGH_CONTRAST -> if (isNight) {
            base.copy(
                canvasBackground = Color(0xFF000000),
                dockBackground = Color(0xFF0A0E17),
                cardSurface = Color(0xFF111827),
                cardBorder = Color(0xFF60A5FA),
                primaryText = Color(0xFFFFFFFF),
                secondaryText = Color(0xFFD1D5DB),
                cardCornerDp = 18,
                cardBorderWidthDp = 1.8f
            )
        } else {
            base.copy(
                canvasBackground = Color(0xFFDCE3EC),
                cardSurface = Color(0xFFFFFFFF),
                cardBorder = Color(0xFF1E3A8A),
                primaryText = Color(0xFF000000),
                secondaryText = Color(0xFF1F2937),
                cardCornerDp = 18,
                cardBorderWidthDp = 1.8f
            )
        }
    }
}

@Composable
fun DxCarTheme(
    isNight: Boolean,
    accentIndex: Int = 0,
    stylePreset: LauncherStylePreset = LauncherStylePreset.COOLWALK_CLASSIC,
    content: @Composable () -> Unit
) {
    val target = buildDxCarPalette(
        isNight = isNight,
        accentIndex = accentIndex,
        stylePreset = stylePreset
    )
    val animSpec = tween<Color>(durationMillis = 420)

    val canvasBg by animateColorAsState(target.canvasBackground, animSpec, label = "canvasBg")
    val dockBg by animateColorAsState(target.dockBackground, animSpec, label = "dockBg")
    val cardSurf by animateColorAsState(target.cardSurface, animSpec, label = "cardSurf")
    val cardElev by animateColorAsState(target.cardSurfaceElevated, animSpec, label = "cardElev")
    val primaryTxt by animateColorAsState(target.primaryText, animSpec, label = "primaryTxt")
    val secondaryTxt by animateColorAsState(target.secondaryText, animSpec, label = "secondaryTxt")
    val accent by animateColorAsState(target.accent, animSpec, label = "accent")

    val animatedPalette = target.copy(
        canvasBackground = canvasBg,
        dockBackground = dockBg,
        cardSurface = cardSurf,
        cardSurfaceElevated = cardElev,
        primaryText = primaryTxt,
        secondaryText = secondaryTxt,
        accent = accent,
        dockActiveIndicator = accent
    )

    val materialScheme = if (isNight) {
        darkColorScheme(
            primary = animatedPalette.accent,
            onPrimary = animatedPalette.onAccent,
            background = animatedPalette.canvasBackground,
            surface = animatedPalette.cardSurface,
            surfaceVariant = animatedPalette.cardSurfaceElevated,
            onBackground = animatedPalette.primaryText,
            onSurface = animatedPalette.primaryText,
            onSurfaceVariant = animatedPalette.secondaryText,
            outline = animatedPalette.cardBorder
        )
    } else {
        lightColorScheme(
            primary = animatedPalette.accent,
            onPrimary = animatedPalette.onAccent,
            background = animatedPalette.canvasBackground,
            surface = animatedPalette.cardSurface,
            surfaceVariant = animatedPalette.cardSurfaceElevated,
            onBackground = animatedPalette.primaryText,
            onSurface = animatedPalette.primaryText,
            onSurfaceVariant = animatedPalette.secondaryText,
            outline = animatedPalette.cardBorder
        )
    }

    CompositionLocalProvider(LocalDxCarColors provides animatedPalette) {
        MaterialTheme(
            colorScheme = materialScheme,
            content = content
        )
    }
}

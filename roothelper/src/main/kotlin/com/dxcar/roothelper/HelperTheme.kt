package com.dxcar.roothelper

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Modern Material 3 (Android 15/16) tonal color system tailored for DX-Car Root Helper.
 * Uses crisp Coolwalk Cobalt / Electric Sky / Emerald semantic tokens and layered surface containers.
 */
private val DxHelperDarkColors = darkColorScheme(
    primary = Color(0xFF60C5FF),
    onPrimary = Color(0xFF00344E),
    primaryContainer = Color(0xFF0A4B6E),
    onPrimaryContainer = Color(0xFFCDEEFF),
    secondary = Color(0xFF4ADE80),
    onSecondary = Color(0xFF00391B),
    secondaryContainer = Color(0xFF0F4D2C),
    onSecondaryContainer = Color(0xFFC2FAD6),
    tertiary = Color(0xFFFBBF24),
    onTertiary = Color(0xFF412D00),
    tertiaryContainer = Color(0xFF5D4200),
    onTertiaryContainer = Color(0xFFFFE08D),
    error = Color(0xFFFF8A80),
    onError = Color(0xFF5C0B0B),
    errorContainer = Color(0xFF7F1D1D),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF0B1018),
    onBackground = Color(0xFFE6EDF7),
    surface = Color(0xFF0B1018),
    onSurface = Color(0xFFE6EDF7),
    surfaceVariant = Color(0xFF1A2435),
    onSurfaceVariant = Color(0xFF9EB0C9),
    surfaceContainerLowest = Color(0xFF070B12),
    surfaceContainerLow = Color(0xFF101825),
    surfaceContainer = Color(0xFF151F30),
    surfaceContainerHigh = Color(0xFF1B273B),
    surfaceContainerHighest = Color(0xFF23324A),
    outline = Color(0xFF475A77),
    outlineVariant = Color(0xFF26354D)
)

private val DxHelperLightColors = lightColorScheme(
    primary = Color(0xFF026AA2),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD2EEFF),
    onPrimaryContainer = Color(0xFF001E31),
    secondary = Color(0xFF0D7D44),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFC4F7D8),
    onSecondaryContainer = Color(0xFF00210E),
    tertiary = Color(0xFF9A6700),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFE5A3),
    onTertiaryContainer = Color(0xFF2D1C00),
    error = Color(0xFFC62828),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFF3F6FB),
    onBackground = Color(0xFF111827),
    surface = Color(0xFFF3F6FB),
    onSurface = Color(0xFF111827),
    surfaceVariant = Color(0xFFE2E9F3),
    onSurfaceVariant = Color(0xFF4B5B73),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF8FAFE),
    surfaceContainer = Color(0xFFFFFFFF),
    surfaceContainerHigh = Color(0xFFEDF2F9),
    surfaceContainerHighest = Color(0xFFE2EAF5),
    outline = Color(0xFF7B8CA6),
    outlineVariant = Color(0xFFD0DCEB)
)

private val DxHelperTypography = Typography(
    headlineSmall = TextStyle(
        fontSize = 22.sp,
        lineHeight = 28.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.2).sp
    ),
    titleLarge = TextStyle(
        fontSize = 19.sp,
        lineHeight = 25.sp,
        fontWeight = FontWeight.Bold
    ),
    titleMedium = TextStyle(
        fontSize = 16.sp,
        lineHeight = 22.sp,
        fontWeight = FontWeight.SemiBold
    ),
    titleSmall = TextStyle(
        fontSize = 14.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.SemiBold
    ),
    bodyMedium = TextStyle(
        fontSize = 14.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.Normal
    ),
    bodySmall = TextStyle(
        fontSize = 12.sp,
        lineHeight = 17.sp,
        fontWeight = FontWeight.Normal
    ),
    labelLarge = TextStyle(
        fontSize = 13.sp,
        lineHeight = 18.sp,
        fontWeight = FontWeight.SemiBold
    ),
    labelMedium = TextStyle(
        fontSize = 12.sp,
        lineHeight = 16.sp,
        fontWeight = FontWeight.SemiBold
    )
)

@Composable
fun DxRootHelperTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DxHelperDarkColors else DxHelperLightColors
    MaterialTheme(
        colorScheme = colorScheme,
        typography = DxHelperTypography,
        content = content
    )
}

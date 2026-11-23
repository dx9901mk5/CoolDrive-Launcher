package com.dxcar.launcher.layout

import com.dxcar.launcher.data.DockPositionMode
import kotlin.math.max
import kotlin.math.roundToInt

enum class AaWidthCategory(val labelZh: String) {
    NARROW("窄螢幕 (<600dp)"),
    STANDARD("標準寬度 (600-929dp)"),
    WIDE("寬螢幕 (930-1279dp)"),
    EXTRA_WIDE("超寬車機 (≥1280dp)")
}

enum class ResolvedDockPlacement(val labelZh: String) {
    LEFT("左側直立 Dock"),
    RIGHT("右側直立 Dock (右駕)"),
    BOTTOM("底部橫向 Dock")
}

data class ScreenMetrics(
    val widthPx: Int,
    val heightPx: Int,
    val densityDpi: Int
) {
    val density: Float
        get() = max(0.5f, densityDpi / 160f)

    val widthDp: Float
        get() = widthPx / density

    val heightDp: Float
        get() = heightPx / density

    val aspectRatio: Float
        get() = if (heightPx > 0) widthPx.toFloat() / heightPx.toFloat() else 1.77f

    val isLandscape: Boolean
        get() = widthPx >= heightPx
}

data class CoolwalkLayoutSpec(
    val metrics: ScreenMetrics,
    val widthCategory: AaWidthCategory,
    val dockPlacement: ResolvedDockPlacement,
    val isHorizontalSplit: Boolean,
    val primaryWeight: Float,
    val secondaryWeight: Float,
    val supportsThirdSubCard: Boolean,
    val dockThicknessDp: Int,
    val cardCornerRadiusDp: Int,
    val reasonZh: String
)

/**
 * Pure Google Android Auto Coolwalk Adaptive-Responsive layout engine.
 *
 * Follows Google's Design for Driving width-bucket & DPI heuristics:
 * - Wide / Extra-Wide (>= 930dp): Always Vertical Side Rail (driver side).
 * - Standard (600..929dp):
 *     * If low-DPI widescreen (widthDp >= 720dp AND aspectRatio >= 1.55 AND heightDp <= 560dp,
 *       e.g., 1280x720@160-240dpi or 800x480@110dpi custom DPI), snaps to Vertical Side Rail
 *       just like Coolwalk on landscape car displays with low DPI.
 *     * Otherwise (e.g., 800x480@160dpi compact or 1024x600@240dpi high-DPI standard),
 *       places Horizontal Dock at the Bottom to preserve map horizontal working space.
 * - Narrow (< 600dp) or Portrait / Square (aspectRatio < 1.35): Bottom Horizontal Dock.
 */
object CoolwalkLayoutEngine {

    fun compute(
        metrics: ScreenMetrics,
        dockMode: DockPositionMode,
        rightHandDrive: Boolean,
        customPrimaryRatio: Float = 0.63f
    ): CoolwalkLayoutSpec {
        val widthDp = metrics.widthDp
        val heightDp = metrics.heightDp
        val ratio = metrics.aspectRatio

        val category = when {
            widthDp < 600f -> AaWidthCategory.NARROW
            widthDp < 930f -> AaWidthCategory.STANDARD
            widthDp < 1280f -> AaWidthCategory.WIDE
            else -> AaWidthCategory.EXTRA_WIDE
        }

        val sideRailPlacement = if (rightHandDrive) {
            ResolvedDockPlacement.RIGHT
        } else {
            ResolvedDockPlacement.LEFT
        }

        val (placement, reason) = when (dockMode) {
            DockPositionMode.FORCE_LEFT ->
                ResolvedDockPlacement.LEFT to "手動固定左側直立 Dock"

            DockPositionMode.FORCE_RIGHT ->
                ResolvedDockPlacement.RIGHT to "手動固定右側直立 Dock"

            DockPositionMode.FORCE_BOTTOM ->
                ResolvedDockPlacement.BOTTOM to "手動固定底部橫向 Dock"

            DockPositionMode.AUTO_GOOGLE_DPI -> {
                when {
                    !metrics.isLandscape || ratio < 1.35f ->
                        ResolvedDockPlacement.BOTTOM to
                            "直向或方型比例 (${String.format("%.2f", ratio)}) -> Google 模式底部 Dock"

                    widthDp >= 930f ->
                        sideRailPlacement to
                            "寬度 ${widthDp.roundToInt()}dp ≥ 930dp (Wide) -> Google 模式側邊直立 Dock"

                    widthDp >= 720f && ratio >= 1.55f && heightDp <= 560f ->
                        sideRailPlacement to
                            "低 DPI 寬螢幕 (${widthDp.roundToInt()}×${heightDp.roundToInt()}dp, ${metrics.densityDpi}dpi) -> 側邊直立 Dock"

                    else ->
                        ResolvedDockPlacement.BOTTOM to
                            "標準/高 DPI 螢幕 (${widthDp.roundToInt()}dp, ${metrics.densityDpi}dpi) -> Google 模式底部 Dock"
                }
            }
        }

        val clampedPrimary = customPrimaryRatio.coerceIn(0.54f, 0.72f)
        val isHorizontalSplit = metrics.isLandscape && widthDp >= 560f
        val supportsThirdSubCard = isHorizontalSplit
        val dockThickness = when {
            placement == ResolvedDockPlacement.BOTTOM && heightDp < 420f -> 64
            placement == ResolvedDockPlacement.BOTTOM -> 72
            widthDp >= 1000f -> 84
            else -> 76
        }
        val cardRadius = if (widthDp >= 800f) 24 else 18

        return CoolwalkLayoutSpec(
            metrics = metrics,
            widthCategory = category,
            dockPlacement = placement,
            isHorizontalSplit = isHorizontalSplit,
            primaryWeight = clampedPrimary,
            secondaryWeight = 1f - clampedPrimary,
            supportsThirdSubCard = supportsThirdSubCard,
            dockThicknessDp = dockThickness,
            cardCornerRadiusDp = cardRadius,
            reasonZh = reason
        )
    }
}

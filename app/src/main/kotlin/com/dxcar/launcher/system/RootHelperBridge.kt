package com.dxcar.launcher.system

import android.content.Context
import android.content.Intent
import android.net.Uri
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class PlanBHelperConfig(
    val planBEnabled: Boolean = true,
    val dockPinnedRightShift: Boolean = true,
    val ui1NavPackage: String = "com.google.android.apps.maps",
    val ui1RightPackage: String = "com.google.android.youtube",
    val ui1SplitRatio: Float = 0.70f,
    val ui2LeftPackage: String = "com.google.android.apps.maps",
    val ui2RightPackage: String = "com.google.android.youtube",
    val ui2SplitRatio: Float = 0.50f,
    val force60HzEnabled: Boolean = false,
    val otaInterceptedPackage: String = "",
    val otaInterceptActive: Boolean = false
)

/**
 * Play-compliant bridge to the standalone `com.dxcar.roothelper` companion APK.
 * Contains zero `su` or root execution code in the Play Store launcher itself.
 */
object RootHelperBridge {
    const val HELPER_PACKAGE = "com.dxcar.roothelper"
    const val GITHUB_RELEASES_URL = "https://github.com/dx9901mk5/DX-Car-Launcher/releases"
    private val PROVIDER_URI = Uri.parse("content://com.dxcar.roothelper.provider/config")

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun isRootHelperInstalled(context: Context): Boolean {
        return runCatching {
            context.packageManager.getPackageInfo(HELPER_PACKAGE, 0)
            true
        }.getOrDefault(false)
    }

    fun readPlanBConfig(context: Context): PlanBHelperConfig {
        if (!isRootHelperInstalled(context)) return PlanBHelperConfig(planBEnabled = false)
        return runCatching {
            context.contentResolver.query(PROVIDER_URI, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val raw = cursor.getString(0) ?: return@use PlanBHelperConfig()
                    json.decodeFromString<PlanBHelperConfig>(raw)
                } else {
                    PlanBHelperConfig()
                }
            } ?: PlanBHelperConfig()
        }.getOrDefault(PlanBHelperConfig())
    }

    fun openHelperOrGithub(context: Context) {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(HELPER_PACKAGE)
        if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { context.startActivity(launchIntent) }
            return
        }
        val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse(GITHUB_RELEASES_URL)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(webIntent) }
    }

    fun applyPlanBPage(
        context: Context,
        page: Int,
        screenWidth: Int,
        screenHeight: Int,
        dockOffsetLeft: Int,
        dockOffsetRight: Int,
        dockOffsetTop: Int,
        dockOffsetBottom: Int,
        ui1Ratio: Float,
        ui1Swapped: Boolean = false,
        ui2Ratio: Float,
        ui2Swapped: Boolean
    ): Boolean {
        if (!isRootHelperInstalled(context)) return false
        return runCatching {
            val extras = android.os.Bundle().apply {
                putInt("page", page)
                putInt("screenWidth", screenWidth)
                putInt("screenHeight", screenHeight)
                putInt("dockOffsetLeft", dockOffsetLeft)
                putInt("dockOffsetRight", dockOffsetRight)
                putInt("dockOffsetTop", dockOffsetTop)
                putInt("dockOffsetBottom", dockOffsetBottom)
                putFloat("ui1Ratio", ui1Ratio)
                putBoolean("ui1Swapped", ui1Swapped)
                putFloat("ui2Ratio", ui2Ratio)
                putBoolean("ui2Swapped", ui2Swapped)
            }
            val res = context.contentResolver.call(PROVIDER_URI, "apply_plan_b_page", null, extras)
            res?.getBoolean("ok", false) == true
        }.getOrDefault(false)
    }

    fun updateUi1Ratio(context: Context, ratio: Float) {
        if (!isRootHelperInstalled(context)) return
        runCatching {
            val extras = android.os.Bundle().apply {
                putFloat("ratio", ratio.coerceIn(0.25f, 0.75f))
            }
            context.contentResolver.call(PROVIDER_URI, "update_ui1_ratio", null, extras)
        }
    }

    fun updateUi2Ratio(context: Context, ratio: Float) {
        if (!isRootHelperInstalled(context)) return
        runCatching {
            val extras = android.os.Bundle().apply {
                putFloat("ratio", ratio.coerceIn(0.25f, 0.75f))
            }
            context.contentResolver.call(PROVIDER_URI, "update_ui2_ratio", null, extras)
        }
    }

    fun parkPlanBWindows(context: Context, screenWidth: Int, screenHeight: Int) {
        if (!isRootHelperInstalled(context)) return
        runCatching {
            val extras = android.os.Bundle().apply {
                putInt("screenWidth", screenWidth)
                putInt("screenHeight", screenHeight)
            }
            context.contentResolver.call(PROVIDER_URI, "park_plan_b_windows", null, extras)
        }
    }

    fun launchFullscreenApp(
        context: Context,
        packageName: String,
        screenWidth: Int,
        screenHeight: Int
    ): Boolean {
        if (!isRootHelperInstalled(context) || packageName.isBlank()) return false
        return runCatching {
            val extras = android.os.Bundle().apply {
                putString("packageName", packageName)
                putInt("screenWidth", screenWidth)
                putInt("screenHeight", screenHeight)
            }
            val res = context.contentResolver.call(PROVIDER_URI, "launch_fullscreen_app", null, extras)
            res?.getBoolean("ok", false) == true
        }.getOrDefault(false)
    }
}

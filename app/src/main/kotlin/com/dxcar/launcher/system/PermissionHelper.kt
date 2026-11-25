package com.dxcar.launcher.system

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.service.notification.NotificationListenerService
import androidx.core.content.ContextCompat
import com.dxcar.launcher.BuildConfig
import com.dxcar.launcher.accessibility.DxCarAccessibilityService
import com.dxcar.launcher.comms.BoxNotificationListenerService

data class PermissionHealth(
    val isDefaultHome: Boolean,
    val hasOverlay: Boolean,
    val hasNotificationListener: Boolean,
    val hasAccessibility: Boolean,
    val hasLocation: Boolean,
    val hasBluetooth: Boolean,
    val hasPhoneState: Boolean,
    val hasContacts: Boolean = false,
    val hasCallLog: Boolean = true,
    val hasWriteSettings: Boolean,
    val hasIgnoreBatteryOpt: Boolean,
    val hasPostNotifications: Boolean
) {
    fun summaryForPhone(): Pair<Int, Int> {
        val checks = listOf(
            hasNotificationListener,
            hasBluetooth && hasPhoneState && hasPostNotifications,
            hasContacts && hasCallLog,
            hasIgnoreBatteryOpt
        )
        return checks.count { it } to checks.size
    }

    fun summaryForCar(): Pair<Int, Int> {
        val checks = listOf(
            isDefaultHome,
            hasOverlay,
            hasNotificationListener,
            hasAccessibility,
            hasLocation && hasBluetooth && hasPhoneState,
            hasWriteSettings,
            hasIgnoreBatteryOpt
        )
        return checks.count { it } to checks.size
    }
}

object PermissionHelper {

    fun check(context: Context): PermissionHealth {
        val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val defaultHomePkg = context.packageManager
            .resolveActivity(homeIntent, PackageManager.MATCH_DEFAULT_ONLY)
            ?.activityInfo?.packageName
        val isDefaultHome = defaultHomePkg == context.packageName

        val hasOverlay = Settings.canDrawOverlays(context)

        val enabledListeners = Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners"
        ).orEmpty()
        val listenerComp = ComponentName(context, BoxNotificationListenerService::class.java)
        val hasNotifListener = enabledListeners.contains(listenerComp.flattenToString()) ||
            enabledListeners.contains(listenerComp.flattenToShortString())

        val enabledAcc = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ).orEmpty()
        val accComp = ComponentName(context, DxCarAccessibilityService::class.java)
        val hasAcc = enabledAcc.contains(accComp.flattenToString()) ||
            enabledAcc.contains(accComp.flattenToShortString())

        val hasLocation = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val hasBluetooth = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

        val hasPhoneState = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_PHONE_STATE
        ) == PackageManager.PERMISSION_GRANTED

        val hasContacts = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED

        val hasCallLog = if (BuildConfig.ENABLE_GITHUB_AUTO_UPDATE) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_CALL_LOG
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

        val hasWriteSettings = Settings.System.canWrite(context)

        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val hasIgnoreBattery = pm.isIgnoringBatteryOptimizations(context.packageName)

        val hasPostNotif = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

        return PermissionHealth(
            isDefaultHome = isDefaultHome,
            hasOverlay = hasOverlay,
            hasNotificationListener = hasNotifListener,
            hasAccessibility = hasAcc,
            hasLocation = hasLocation,
            hasBluetooth = hasBluetooth,
            hasPhoneState = hasPhoneState,
            hasContacts = hasContacts,
            hasCallLog = hasCallLog,
            hasWriteSettings = hasWriteSettings,
            hasIgnoreBatteryOpt = hasIgnoreBattery,
            hasPostNotifications = hasPostNotif
        )
    }

    fun openOverlayPermission(context: Context) {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${context.packageName}")
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }

    fun openNotificationListenerSettings(context: Context) {
        val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }

    fun openAccessibilitySettings(context: Context) {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }

    fun openWriteSettingsPermission(context: Context) {
        val intent = Intent(
            Settings.ACTION_MANAGE_WRITE_SETTINGS,
            Uri.parse("package:${context.packageName}")
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }

    fun requestIgnoreBatteryOptimizations(context: Context) {
        val intent = Intent(
            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
            Uri.parse("package:${context.packageName}")
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
            .onFailure {
                runCatching {
                    context.startActivity(
                        Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
            }
    }

    fun openInstallUnknownApps(context: Context) {
        val intent = Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}")
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }

    fun openAppDetailsSettings(context: Context) {
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.parse("package:${context.packageName}")
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }

    fun openDeveloperSettings(context: Context) {
        val devIntent = Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val ok = runCatching {
            context.startActivity(devIntent)
            true
        }.getOrDefault(false)
        if (!ok) {
            runCatching {
                context.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }
    }

    fun isFreeformSupported(context: Context): Boolean {
        val globalEnabled = runCatching {
            Settings.Global.getInt(context.contentResolver, "enable_freeform_support", 0) == 1
        }.getOrDefault(false)
        val featureEnabled = runCatching {
            context.packageManager.hasSystemFeature(PackageManager.FEATURE_FREEFORM_WINDOW_MANAGEMENT)
        }.getOrDefault(false)
        return globalEnabled || featureEnabled
    }

    fun requestNotificationListenerRebind(context: Context) {
        runCatching {
            NotificationListenerService.requestRebind(
                ComponentName(context, BoxNotificationListenerService::class.java)
            )
        }
    }
}

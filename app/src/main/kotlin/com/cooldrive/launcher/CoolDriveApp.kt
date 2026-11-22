package com.cooldrive.launcher

import android.app.Application
import com.cooldrive.launcher.comms.CarNotificationCenter
import com.cooldrive.launcher.comms.TtsAnnouncer
import com.cooldrive.launcher.data.LauncherPreferences
import com.cooldrive.launcher.map.LocationTracker
import com.cooldrive.launcher.media.MediaControlBridge
import com.cooldrive.launcher.system.InstalledAppsRepository
import com.cooldrive.launcher.system.SystemStatusMonitor
import com.cooldrive.launcher.update.UpdateManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.maplibre.android.MapLibre

class CoolDriveApp : Application() {

    lateinit var preferences: LauncherPreferences
        private set
    lateinit var systemStatusMonitor: SystemStatusMonitor
        private set
    lateinit var installedAppsRepository: InstalledAppsRepository
        private set
    lateinit var locationTracker: LocationTracker
        private set
    lateinit var mediaControlBridge: MediaControlBridge
        private set
    lateinit var ttsAnnouncer: TtsAnnouncer
        private set
    lateinit var notificationCenter: CarNotificationCenter
        private set
    lateinit var updateManager: UpdateManager
        private set

    private val _isLauncherForeground = MutableStateFlow(false)
    val isLauncherForeground: StateFlow<Boolean> = _isLauncherForeground.asStateFlow()

    override fun onCreate() {
        super.onCreate()
        instance = this

        runCatching { MapLibre.getInstance(this) }

        preferences = LauncherPreferences(this)
        systemStatusMonitor = SystemStatusMonitor(this).apply { start() }
        installedAppsRepository = InstalledAppsRepository(this).apply { start() }
        locationTracker = LocationTracker(this, preferences).apply { startIfPermitted() }
        mediaControlBridge = MediaControlBridge(this).apply { start() }
        ttsAnnouncer = TtsAnnouncer(this)
        notificationCenter = CarNotificationCenter(preferences, ttsAnnouncer)
        updateManager = UpdateManager(this)
    }

    fun setLauncherForeground(foreground: Boolean) {
        _isLauncherForeground.value = foreground
    }

    companion object {
        @Volatile
        var instance: CoolDriveApp? = null
            private set
    }
}

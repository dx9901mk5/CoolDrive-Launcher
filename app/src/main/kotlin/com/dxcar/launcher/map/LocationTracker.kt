package com.dxcar.launcher.map

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import androidx.core.content.ContextCompat
import com.dxcar.launcher.data.LauncherPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.roundToInt

data class VehicleLocationState(
    val latitude: Double = 25.0330,
    val longitude: Double = 121.5654,
    val speedKmh: Int = 0,
    val bearingDeg: Float = 0f,
    val accuracyMeters: Float = 0f,
    val hasRealFix: Boolean = false,
    val providerName: String = ""
)

/**
 * Pure Android LocationManager GPS tracker (zero GMS dependency so it runs reliably
 * on both GMS and non-GMS Chinese AI Box firmwares).
 */
class LocationTracker(
    private val context: Context,
    private val preferences: LauncherPreferences
) {
    private val locationManager =
        context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager

    private val _location = MutableStateFlow(
        VehicleLocationState(
            latitude = preferences.config.value.lastKnownLat,
            longitude = preferences.config.value.lastKnownLon,
            bearingDeg = preferences.config.value.lastKnownBearingDeg
        )
    )
    val location: StateFlow<VehicleLocationState> = _location.asStateFlow()

    private var started = false
    private var lastPersistMs = 0L
    private var hasPersistedSessionFix = false

    private val listener = object : LocationListener {
        override fun onLocationChanged(loc: Location) {
            onNewLocation(loc)
        }

        @Deprecated("Deprecated in Java")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
        override fun onProviderEnabled(provider: String) = Unit
        override fun onProviderDisabled(provider: String) = Unit
    }

    @SuppressLint("MissingPermission")
    fun startIfPermitted() {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val lm = locationManager
        if (!granted || started || lm == null) return
        started = true

        runCatching {
            val providers = listOf(
                LocationManager.GPS_PROVIDER,
                LocationManager.NETWORK_PROVIDER,
                LocationManager.PASSIVE_PROVIDER
            )
            var bestLast: Location? = null
            for (provider in providers) {
                if (runCatching { lm.isProviderEnabled(provider) }.getOrDefault(false)) {
                    val last = runCatching { lm.getLastKnownLocation(provider) }.getOrNull()
                    if (last != null && (bestLast == null || last.time > bestLast.time)) {
                        bestLast = last
                    }
                    if (provider != LocationManager.PASSIVE_PROVIDER) {
                        lm.requestLocationUpdates(
                            provider,
                            1000L,
                            1.5f,
                            listener
                        )
                    }
                }
            }
            bestLast?.let { onNewLocation(it) }
        }
    }

    fun stop() {
        if (!started) return
        started = false
        runCatching { locationManager?.removeUpdates(listener) }
    }

    private fun onNewLocation(loc: Location) {
        val speedKmh = if (loc.hasSpeed()) (loc.speed * 3.6f).roundToInt().coerceAtLeast(0) else 0
        val bearing = if (loc.hasBearing() && speedKmh >= 3) loc.bearing else _location.value.bearingDeg
        _location.value = VehicleLocationState(
            latitude = loc.latitude,
            longitude = loc.longitude,
            speedKmh = speedKmh,
            bearingDeg = bearing,
            accuracyMeters = loc.accuracy,
            hasRealFix = true,
            providerName = loc.provider ?: "GPS"
        )
        // Persist street-level GPS state (15m / heading change, throttled 5s) so offline map boots immediately at last position
        val cur = preferences.config.value
        val nowMs = android.os.SystemClock.elapsedRealtime()
        val movedEnough = kotlin.math.abs(cur.lastKnownLat - loc.latitude) > 0.00015 ||
            kotlin.math.abs(cur.lastKnownLon - loc.longitude) > 0.00015 ||
            kotlin.math.abs(cur.lastKnownBearingDeg - bearing) > 8f
        if (!hasPersistedSessionFix || (movedEnough && nowMs - lastPersistMs >= 5000L)) {
            hasPersistedSessionFix = true
            lastPersistMs = nowMs
            preferences.update {
                it.copy(
                    lastKnownLat = loc.latitude,
                    lastKnownLon = loc.longitude,
                    lastKnownBearingDeg = bearing
                )
            }
        }
    }
}

package com.dxcar.launcher.theme

import com.dxcar.launcher.data.LauncherConfig
import com.dxcar.launcher.data.ThemeMode
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Locale
import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

data class SolarTimes(
    val sunriseMinutesOfDay: Int,
    val sunsetMinutesOfDay: Int
) {
    fun formatSunrise(): String = formatMinutes(sunriseMinutesOfDay)
    fun formatSunset(): String = formatMinutes(sunsetMinutesOfDay)

    companion object {
        fun formatMinutes(totalMinutes: Int): String {
            val wrapped = ((totalMinutes % 1440) + 1440) % 1440
            return String.format(Locale.US, "%02d:%02d", wrapped / 60, wrapped % 60)
        }
    }
}

/**
 * Pure Day/Night theme resolver supporting:
 * 1. Custom scheduled time windows (e.g., 06:30 Day / 18:30 Night)
 * 2. Offline astronomical Sunrise/Sunset calculation from GPS coordinates (NOAA algorithm)
 * 3. System uiMode follow (links with head unit headlight dimmer when supported)
 * 4. Manual Force Day / Force Night
 */
object DayNightEngine {

    fun isNightNow(
        config: LauncherConfig,
        now: LocalDateTime = LocalDateTime.now(),
        systemIsDark: Boolean = true,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): Boolean {
        return when (config.themeMode) {
            ThemeMode.FORCE_DAY -> false
            ThemeMode.FORCE_NIGHT -> true
            ThemeMode.FOLLOW_SYSTEM -> systemIsDark
            ThemeMode.AUTO_TIME -> {
                val nowMin = now.hour * 60 + now.minute
                val dayStart = config.dayStartHour * 60 + config.dayStartMinute
                val nightStart = config.nightStartHour * 60 + config.nightStartMinute
                isNightForWindow(nowMin, dayStart, nightStart)
            }
            ThemeMode.AUTO_SUNRISE_SUNSET -> {
                val solar = calculateSolarTimes(
                    date = now.toLocalDate(),
                    latitude = config.lastKnownLat,
                    longitude = config.lastKnownLon,
                    tzOffsetHours = zoneId.rules.getOffset(now).totalSeconds / 3600.0
                )
                val nowMin = now.hour * 60 + now.minute
                isNightForWindow(nowMin, solar.sunriseMinutesOfDay, solar.sunsetMinutesOfDay)
            }
        }
    }

    internal fun isNightForWindow(nowMin: Int, dayStartMin: Int, nightStartMin: Int): Boolean {
        return if (dayStartMin < nightStartMin) {
            // Normal schedule: Day is [dayStart, nightStart), Night otherwise
            nowMin < dayStartMin || nowMin >= nightStartMin
        } else if (dayStartMin > nightStartMin) {
            // Inverted schedule: Night is [nightStart, dayStart)
            nowMin in nightStartMin until dayStartMin
        } else {
            false
        }
    }

    /**
     * Offline NOAA-inspired solar sunrise/sunset approximation (accurate within ~2 minutes
     * for mid-latitudes like Taiwan 22°N–25°N, zero network or API key required).
     */
    fun calculateSolarTimes(
        date: LocalDate,
        latitude: Double,
        longitude: Double,
        tzOffsetHours: Double
    ): SolarTimes {
        val dayOfYear = date.dayOfYear
        val gamma = 2.0 * PI / 365.0 * (dayOfYear - 1)

        // Equation of time (minutes)
        val eqTime = 229.18 * (
            0.000075 +
                0.001868 * cos(gamma) -
                0.032077 * sin(gamma) -
                0.014615 * cos(2 * gamma) -
                0.040849 * sin(2 * gamma)
            )

        // Solar declination angle (radians)
        val decl = 0.006918 -
            0.399912 * cos(gamma) +
            0.070257 * sin(gamma) -
            0.006758 * cos(2 * gamma) +
            0.000907 * sin(2 * gamma) -
            0.002697 * cos(3 * gamma) +
            0.00148 * sin(3 * gamma)

        val latRad = Math.toRadians(latitude.coerceIn(-66.0, 66.0))
        val zenithRad = Math.toRadians(90.833)
        val cosHa = (cos(zenithRad) / (cos(latRad) * cos(decl))) - tan(latRad) * tan(decl)

        // Clamp for polar regions
        val clampedCosHa = cosHa.coerceIn(-1.0, 1.0)
        val haDeg = Math.toDegrees(acos(clampedCosHa))

        val solarNoonMinutes = 720.0 - 4.0 * longitude - eqTime + tzOffsetHours * 60.0
        val sunrise = (solarNoonMinutes - haDeg * 4.0).toInt().coerceIn(0, 1439)
        val sunset = (solarNoonMinutes + haDeg * 4.0).toInt().coerceIn(0, 1439)

        return SolarTimes(sunriseMinutesOfDay = sunrise, sunsetMinutesOfDay = sunset)
    }
}

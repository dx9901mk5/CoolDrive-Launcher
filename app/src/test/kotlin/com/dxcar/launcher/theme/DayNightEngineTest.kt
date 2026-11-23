package com.dxcar.launcher.theme

import com.dxcar.launcher.data.LauncherConfig
import com.dxcar.launcher.data.ThemeMode
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset

class DayNightEngineTest {

    @Test
    fun `auto time schedule switches cleanly between day and night`() {
        val config = LauncherConfig(
            themeMode = ThemeMode.AUTO_TIME,
            dayStartHour = 6,
            dayStartMinute = 30,
            nightStartHour = 18,
            nightStartMinute = 30
        )

        assertTrue(DayNightEngine.isNightNow(config, LocalDateTime.of(2026, 11, 22, 6, 29)))
        assertFalse(DayNightEngine.isNightNow(config, LocalDateTime.of(2026, 11, 22, 6, 30)))
        assertFalse(DayNightEngine.isNightNow(config, LocalDateTime.of(2026, 11, 22, 12, 0)))
        assertFalse(DayNightEngine.isNightNow(config, LocalDateTime.of(2026, 11, 22, 18, 29)))
        assertTrue(DayNightEngine.isNightNow(config, LocalDateTime.of(2026, 11, 22, 18, 30)))
        assertTrue(DayNightEngine.isNightNow(config, LocalDateTime.of(2026, 11, 22, 23, 45)))
    }

    @Test
    fun `taipei solar calculation yields realistic sunrise and sunset`() {
        // Taipei summer solstice (~05:05 sunrise, ~18:45 sunset)
        val summer = DayNightEngine.calculateSolarTimes(
            date = LocalDate.of(2026, 6, 21),
            latitude = 25.0330,
            longitude = 121.5654,
            tzOffsetHours = 8.0
        )
        assertTrue("Summer sunrise ${summer.formatSunrise()}", summer.sunriseMinutesOfDay in (4 * 60 + 45)..(5 * 60 + 30))
        assertTrue("Summer sunset ${summer.formatSunset()}", summer.sunsetMinutesOfDay in (18 * 60 + 25)..(19 * 60 + 10))

        // Taipei winter solstice (~06:35 sunrise, ~17:10 sunset)
        val winter = DayNightEngine.calculateSolarTimes(
            date = LocalDate.of(2026, 12, 21),
            latitude = 25.0330,
            longitude = 121.5654,
            tzOffsetHours = 8.0
        )
        assertTrue("Winter sunrise ${winter.formatSunrise()}", winter.sunriseMinutesOfDay in (6 * 60 + 15)..(7 * 60))
        assertTrue("Winter sunset ${winter.formatSunset()}", winter.sunsetMinutesOfDay in (16 * 60 + 50)..(17 * 60 + 35))
    }

    @Test
    fun `force day and force night ignore time`() {
        val midnight = LocalDateTime.of(2026, 11, 22, 2, 0)
        val noon = LocalDateTime.of(2026, 11, 22, 12, 0)
        assertFalse(
            DayNightEngine.isNightNow(
                LauncherConfig(themeMode = ThemeMode.FORCE_DAY),
                midnight,
                zoneId = ZoneOffset.ofHours(8)
            )
        )
        assertTrue(
            DayNightEngine.isNightNow(
                LauncherConfig(themeMode = ThemeMode.FORCE_NIGHT),
                noon,
                zoneId = ZoneOffset.ofHours(8)
            )
        )
    }
}

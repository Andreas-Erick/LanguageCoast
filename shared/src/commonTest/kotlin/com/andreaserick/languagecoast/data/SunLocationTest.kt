package com.andreaserick.languagecoast.data

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class SunLocationTest {

    private fun repository(): SettingsRepository = memorySettings()

    @Test
    fun aLocationIsKeptToOneDecimal() {
        assertEquals(SunLocation(47.4, 8.5), SunLocation.rounded(47.37689, 8.54169))
        assertEquals(SunLocation(-33.9, 151.2), SunLocation.rounded(-33.8688, 151.2093))
        assertNull(SunLocation.rounded(91.0, 0.0))
        assertNull(SunLocation.rounded(Double.NaN, 0.0))
    }

    @Test
    fun onlyWhatWasStoredIsReadBack() {
        assertEquals(SunLocation(47.4, 8.5), SunLocation.decode(SunLocation(47.4, 8.5).encode()))
        assertNull(SunLocation.decode(null))
        assertNull(SunLocation.decode("47.4"))
        assertNull(SunLocation.decode("north,east"))
        assertNull(SunLocation.decode("200,0"))
    }

    @Test
    fun aLocationIsOnlyKeptWhileTheSwitchIsOnAndForgottenWhenItGoesOff() = runTest {
        val settings = repository()

        // A reading before the switch is on is not stored.
        settings.setSunLocation(SunLocation(47.4, 8.5))
        assertNull(settings.sunLocation.first())

        settings.setSunUsesLocation(true)
        settings.setSunLocation(SunLocation(47.4, 8.5))
        assertEquals(SunLocation(47.4, 8.5), settings.sunLocation.first())

        settings.setSunUsesLocation(false)
        assertFalse(settings.sunUsesLocation.first())
        assertNull(settings.sunLocation.first())
    }

    @Test
    fun theLocationIsLeftOutOfBackups() = runTest {
        val settings = repository()
        settings.setSunUsesLocation(true)
        settings.setSunLocation(SunLocation(47.4, 8.5))

        val backup = SettingsSnapshot.encode(settings.backupSettings())

        assertFalse("47.4" in backup || "sun" in backup.lowercase(), backup)
    }
}

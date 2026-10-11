package com.andreaserick.languagecoast.data

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import okio.FileSystem
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

class SettingsRepositoryTest {

    private val clock = object : Clock {
        override fun now() = Instant.parse("2026-10-09T12:00:00Z")
    }

    /** A repository with its own new file, so tests don't share settings. */
    private fun repository(): SettingsRepository {
        val path = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "settings-${Random.nextLong()}.preferences_pb"
        return DataStoreSettingsRepository(createSettingsDataStore(path.toString()), clock, TimeZone.UTC)
    }

    @Test
    fun recordedSessionsAppearInStudyDays() = runTest {
        val settings = repository()
        assertTrue(settings.studyDays.first().isEmpty())

        settings.recordStudySession()

        assertEquals(setOf(LocalDate(2026, 10, 9)), settings.studyDays.first())
    }

    // One write per test: on Windows, DataStore can't replace a file it still has open,
    // so a second write to the same file fails there (not on Android or Linux CI).

    @Test
    fun remindersDefaultToOnAtASurpriseTime() = runTest {
        assertEquals(ReminderSettings(enabled = true, time = null), repository().reminderSettings.first())
    }

    @Test
    fun fixedReminderTimeIsStored() = runTest {
        val settings = repository()
        settings.setReminderSettings(ReminderSettings(enabled = true, time = LocalTime(7, 45)))
        assertEquals(ReminderSettings(enabled = true, time = LocalTime(7, 45)), settings.reminderSettings.first())
    }

    @Test
    fun disabledRemindersAreStored() = runTest {
        val settings = repository()
        settings.setReminderSettings(ReminderSettings(enabled = false, time = null))
        assertEquals(ReminderSettings(enabled = false, time = null), settings.reminderSettings.first())
    }
}

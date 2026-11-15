package com.andreaserick.languagecoast.data

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import okio.FileSystem
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
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

    // Keystrokes are saved one field at a time in a single edit each (see setProfileField), which DataStore
    // serializes, so later keystrokes can't be overwritten by earlier ones. One write here, as the note below says.
    @Test
    fun aProfileFieldIsStoredOnItsOwn() = runTest {
        val settings = repository()

        settings.setProfileField("city", "Oslo")

        assertEquals(Profile(city = "Oslo"), settings.profile.first())
        assertEquals(mapOf("city" to "Oslo"), settings.backupSettings().profile)
    }

    @Test
    fun anAnswerAboutTheUserIsStoredOnItsOwn() = runTest {
        val settings = repository()

        settings.setPersonalAnswer("pets_which", "a cat called Milo")

        assertEquals(mapOf("pets_which" to "a cat called Milo"), settings.personalAnswers.first())
        assertEquals(mapOf("pets_which" to "a cat called Milo"), settings.backupSettings().personalAnswers)
    }

    @Test
    fun anIslandWrittenAboutTheUserIsRememberedWithItsFingerprint() = runTest {
        val settings = repository()

        settings.markPersonalIslandBuilt(coastId = 3, islandId = "pets", fingerprint = "-12345")

        assertEquals(mapOf("3:pets" to "-12345"), settings.personalIslandsBuilt.first())
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

    // The tests below write more than once, so they keep their settings in memory (see MemoryDataStore).

    @Test
    fun settingsAreEmptyUntilSomethingIsStored() = runTest {
        val settings = memorySettings()
        assertTrue(settings.isEmpty())

        settings.setNativeLanguage("Dutch")

        assertFalse(settings.isEmpty())
    }

    @Test
    fun storedValuesThatCannotBeReadCountAsMissingInsteadOfStoppingTheApp() = runTest {
        val store = MemoryDataStore()
        // What a hand-edited backup could leave behind before restores were checked.
        store.edit { preferences ->
            preferences[stringPreferencesKey("last_study_date")] = "11/05/2026"
            preferences[stringSetPreferencesKey("study_days")] = setOf("2026-10-08", "yesterday")
            preferences[stringPreferencesKey("freeze_used_on")] = "soon"
            preferences[intPreferencesKey("reminder_minute_of_day")] = 1_500
        }
        val settings = memorySettings(store)

        assertNull(settings.lastStudyDate.first())
        assertEquals(setOf(LocalDate(2026, 10, 8)), settings.studyDays.first())
        assertNull(settings.freezeUsedOn.first())
        assertEquals(ReminderSettings(enabled = true, time = null), settings.reminderSettings.first())

        // What every start and every finished session do must get through as well.
        settings.refreshStreak()
        settings.recordStudySession()

        assertEquals(LocalDate(2026, 10, 9), settings.lastStudyDate.first())
        assertEquals(1, settings.streakCount.first())
        assertEquals(setOf(LocalDate(2026, 10, 8), LocalDate(2026, 10, 9)), settings.studyDays.first())
    }

    @Test
    fun aStreakCountsAsStudyDaysButOnlyAYearOfThem() = runTest {
        val store = MemoryDataStore()
        store.edit { preferences ->
            preferences[stringPreferencesKey("last_study_date")] = "2026-10-09"
            preferences[intPreferencesKey("streak_count")] = 3
        }
        val settings = memorySettings(store)
        assertEquals(setOf(LocalDate(2026, 10, 7), LocalDate(2026, 10, 8), LocalDate(2026, 10, 9)), settings.studyDays.first())

        // A value no streak can reach, left behind by a hand-edited backup: reading it must not take the app down.
        store.edit { it[intPreferencesKey("streak_count")] = 2_000_000_000 }

        val days = settings.studyDays.first()
        assertEquals(366, days.size)
        assertTrue(LocalDate(2026, 10, 9) in days)
        assertTrue(LocalDate(2025, 10, 9) in days)
        assertFalse(LocalDate(2025, 10, 8) in days)
    }

    @Test
    fun restoringWritesOnlyDatesAndTimesThatCanBeReadBack() = runTest {
        val settings = memorySettings()

        settings.restoreSettings(
            BackupSettings(
                nativeLanguage = "Dutch", streakCount = -2, lastStudyDate = "11/05/2026", studyDays = listOf("2026-10-08", "yesterday"),
                reminderMinuteOfDay = 1_500, freezeUsedOn = "soon"
            )
        )

        assertEquals("Dutch", settings.nativeLanguage.first())
        assertEquals(0, settings.streakCount.first())
        assertNull(settings.lastStudyDate.first())
        assertEquals(listOf("2026-10-08"), settings.backupSettings().studyDays)
        assertNull(settings.backupSettings().reminderMinuteOfDay)
        assertNull(settings.backupSettings().freezeUsedOn)
    }

    @Test
    fun aSnapshotOfTheSettingsRestoresThemOnAnEmptyStore() = runTest {
        val old = memorySettings()
        old.setNativeLanguage("Dutch")
        old.setActiveCoastId(2)
        old.setReminderSettings(ReminderSettings(enabled = true, time = LocalTime(7, 45)))
        old.recordStudySession()
        old.markStarterTopicAdded(coastId = 2, topic = "Greetings")
        old.setProfileField("name", "Anna")
        old.setApiKey("AIza-test")
        val snapshot = SettingsSnapshot.encode(old.backupSettings())

        // The phone's own backup brought the cards and the snapshot back, but not the settings file.
        val restored = memorySettings()
        assertTrue(restored.isEmpty())
        restored.restoreSettings(SettingsSnapshot.decode(snapshot)!!)

        assertEquals("Dutch", restored.nativeLanguage.first())
        assertEquals(2, restored.activeCoastId.first())
        assertEquals(1, restored.streakCount.first())
        assertEquals(LocalDate(2026, 10, 9), restored.lastStudyDate.first())
        assertEquals(ReminderSettings(enabled = true, time = LocalTime(7, 45)), restored.reminderSettings.first())
        assertEquals(setOf("2:Greetings"), restored.starterTopicsAdded.first())
        assertEquals(Profile(name = "Anna"), restored.profile.first())
        // The key was never in the snapshot.
        assertFalse("AIza-test" in snapshot)
        assertEquals("", restored.apiKey.first())
    }
}

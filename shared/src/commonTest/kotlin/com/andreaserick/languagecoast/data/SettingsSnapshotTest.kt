package com.andreaserick.languagecoast.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SettingsSnapshotTest {

    private val settings = BackupSettings(
        nativeLanguage = "Dutch",
        activeCoastId = 2,
        translationProvider = "Claude",
        streakCount = 6,
        lastStudyDate = "2026-10-09",
        studyDays = listOf("2026-10-08", "2026-10-09"),
        reminderMinuteOfDay = 465,
        freezeUsedOn = "2026-10-05",
        starterTopicsAdded = listOf("2:Greetings"),
        profile = mapOf("name" to "Anna"),
        personalAnswers = mapOf("pets_which" to "a cat called Milo"),
        personalIslandsBuilt = mapOf("2:pets" to "-12345"),
        pace = "fast"
    )

    @Test
    fun aSnapshotReadsBackExactly() {
        assertEquals(settings, SettingsSnapshot.decode(SettingsSnapshot.encode(settings)))
        assertEquals(BackupSettings(), SettingsSnapshot.decode(SettingsSnapshot.encode(BackupSettings())))
    }

    @Test
    fun fieldsFromANewerVersionAreIgnored() {
        val json = SettingsSnapshot.encode(settings).replaceFirst("{", "{\"futureField\":true,")

        assertEquals(settings, SettingsSnapshot.decode(json))
    }

    @Test
    fun whatIsNotASnapshotIsPassedOver() {
        assertNull(SettingsSnapshot.decode(""))
        assertNull(SettingsSnapshot.decode("not json"))
        assertNull(SettingsSnapshot.decode("[1, 2]"))
        assertNull(SettingsSnapshot.decode("{\"streakCount\":\"six\"}"))
        // A file that was cut off while it was written.
        assertNull(SettingsSnapshot.decode(SettingsSnapshot.encode(settings).dropLast(12)))
    }

    @Test
    fun aSnapshotWithADateOrTimeThatCannotBeReadIsPassedOverWhole() {
        assertNull(SettingsSnapshot.decode(SettingsSnapshot.encode(settings.copy(lastStudyDate = "11/05/2026"))))
        assertNull(SettingsSnapshot.decode(SettingsSnapshot.encode(settings.copy(studyDays = listOf("yesterday")))))
        assertNull(SettingsSnapshot.decode(SettingsSnapshot.encode(settings.copy(freezeUsedOn = "soon"))))
        assertNull(SettingsSnapshot.decode(SettingsSnapshot.encode(settings.copy(reminderMinuteOfDay = 1_500))))
        assertNull(SettingsSnapshot.decode(SettingsSnapshot.encode(settings.copy(streakCount = -1))))
        assertNull(SettingsSnapshot.decode(SettingsSnapshot.encode(settings.copy(streakCount = 2_000_000_000))))
    }

    @Test
    fun theFileNameIsTheOneBothPlatformsUse() {
        assertEquals("settings-snapshot.json", SettingsSnapshot.FILE_NAME)
    }
}

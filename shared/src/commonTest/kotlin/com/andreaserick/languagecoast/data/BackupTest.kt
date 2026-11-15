package com.andreaserick.languagecoast.data

import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.Test

class BackupTest {

    private val backup = Backup(
        createdAt = 1_760_000_000_000,
        settings = BackupSettings(nativeLanguage = "English", activeCoastId = 1, streakCount = 4, lastStudyDate = "2026-10-10",
            studyDays = listOf("2026-10-09", "2026-10-10"), reminderMinuteOfDay = 1_200, studyReversed = true),
        coasts = listOf(BackupCoast(1, "German", 100)),
        islands = listOf(BackupIsland(3, coastId = 1, name = "Travel", creationDate = 200, emoji = "✈️", lastStudied = 300)),
        cards = listOf(
            BackupCard(7, islandId = 3, nativeText = "Where is the station?", targetText = "Wo ist der Bahnhof?",
                stability = 4.2, difficulty = 5.1, lastReviewed = 400, due = 500, alternatives = listOf("Wo befindet sich der Bahnhof?"), note = "Formal")
        )
    )

    @Test
    fun aBackupReadsBackExactly() {
        assertEquals(backup, decodeBackup(encodeBackup(backup)))
    }

    @Test
    fun unknownFieldsFromNewerBackupsAreIgnored() {
        val json = encodeBackup(backup).replaceFirst("{", "{\"futureField\":true,")

        assertEquals(backup, decodeBackup(json))
    }

    @Test
    fun otherFilesAreRefused() {
        val error = assertFailsWith<BackupException> { decodeBackup("Front\tBack\nHello\tHola") }
        assertEquals("That file isn't a Language Coast backup.", error.message)

        assertFailsWith<BackupException> { decodeBackup(encodeBackup(backup.copy(format = "something-else"))) }
    }

    @Test
    fun backupsFromANewerVersionAreRefused() {
        val error = assertFailsWith<BackupException> { decodeBackup(encodeBackup(backup.copy(version = BACKUP_VERSION + 1))) }

        assertEquals("This backup is from a newer version of Language Coast. Update the app first.", error.message)
    }

    @Test
    fun cardsWithoutTheirIslandAreRefused() {
        val damaged = backup.copy(cards = backup.cards + backup.cards.first().copy(cardId = 8, islandId = 99))

        assertFailsWith<BackupException> { decodeBackup(encodeBackup(damaged)) }
    }

    // A file that was edited by hand or made by another tool: restored unchecked, one such value would stop the app at every start.
    @Test
    fun aDateOrReminderTimeThatCannotBeReadIsRefused() {
        fun refused(settings: BackupSettings) {
            val error = assertFailsWith<BackupException> { decodeBackup(encodeBackup(backup.copy(settings = settings))) }
            assertEquals("This backup is damaged and can't be restored.", error.message)
        }

        refused(backup.settings.copy(lastStudyDate = "11/05/2026"))
        refused(backup.settings.copy(studyDays = listOf("2026-10-09", "yesterday")))
        refused(backup.settings.copy(freezeUsedOn = "2026-13-40"))
        refused(backup.settings.copy(reminderMinuteOfDay = 1_500))
        refused(backup.settings.copy(reminderMinuteOfDay = -1))
        refused(backup.settings.copy(streakCount = -3))
        // A streak nobody can have studied: every one of its days would be counted at each start.
        refused(backup.settings.copy(streakCount = 2_000_000_000))
        refused(backup.settings.copy(streakCount = 36_501))
    }

    @Test
    fun aLongStreakIsStillAStreak() {
        val longStreak = backup.copy(settings = backup.settings.copy(streakCount = 36_500))

        assertEquals(longStreak, decodeBackup(encodeBackup(longStreak)))
    }

    @Test
    fun theEdgesOfADayAreValidReminderTimes() {
        val midnight = backup.copy(settings = backup.settings.copy(reminderMinuteOfDay = 0))
        val lastMinute = backup.copy(settings = backup.settings.copy(reminderMinuteOfDay = 1_439, lastStudyDate = null, freezeUsedOn = "2026-10-08"))

        assertEquals(midnight, decodeBackup(encodeBackup(midnight)))
        assertEquals(lastMinute, decodeBackup(encodeBackup(lastMinute)))
    }

    @Test
    fun twoCoastsForOneLanguageAreRefused() {
        val doubled = backup.copy(coasts = backup.coasts + BackupCoast(2, "German", 150))

        assertFailsWith<BackupException> { decodeBackup(encodeBackup(doubled)) }
    }
}

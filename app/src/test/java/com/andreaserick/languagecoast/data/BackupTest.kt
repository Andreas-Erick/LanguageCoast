package com.andreaserick.languagecoast.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

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
        val error = assertThrows(BackupException::class.java) { decodeBackup("Front\tBack\nHello\tHola") }
        assertEquals("That file isn't a Language Coast backup.", error.message)

        assertThrows(BackupException::class.java) { decodeBackup(encodeBackup(backup.copy(format = "something-else"))) }
    }

    @Test
    fun backupsFromANewerVersionAreRefused() {
        val error = assertThrows(BackupException::class.java) { decodeBackup(encodeBackup(backup.copy(version = BACKUP_VERSION + 1))) }

        assertEquals("This backup is from a newer version of Language Coast. Update the app first.", error.message)
    }

    @Test
    fun cardsWithoutTheirIslandAreRefused() {
        val damaged = backup.copy(cards = backup.cards + backup.cards.first().copy(cardId = 8, islandId = 99))

        assertThrows(BackupException::class.java) { decodeBackup(encodeBackup(damaged)) }
    }
}

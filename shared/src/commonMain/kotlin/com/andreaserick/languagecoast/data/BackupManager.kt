package com.andreaserick.languagecoast.data

import kotlin.time.Clock

/** Makes backups of everything in the app and restores them. */
class BackupManager(
    private val flashcards: FlashcardRepository,
    private val settings: SettingsRepository,
    private val clock: Clock
) {
    /** A backup of everything in the app right now. */
    suspend fun createBackup(): Backup {
        val contents = flashcards.getAllContent()
        return Backup(
            createdAt = clock.now().toEpochMilliseconds(),
            settings = settings.backupSettings(),
            coasts = contents.map { it.coast.toBackup() },
            islands = contents.flatMap { it.islands }.map { it.toBackup() },
            cards = contents.flatMap { it.cards }.map { it.toBackup() }
        )
    }

    /** Replaces everything in the app with [backup]. The API keys stay as they are. */
    suspend fun restore(backup: Backup) {
        flashcards.replaceAll(
            coasts = backup.coasts.map { Coast(it.coastId, it.language, it.creationDate) },
            islands = backup.islands.map { LanguageIsland(it.islandId, it.coastId, it.name, it.creationDate, it.emoji, it.lastStudied) },
            cards = backup.cards.map {
                Flashcard(
                    cardId = it.cardId,
                    islandId = it.islandId,
                    nativeText = it.nativeText,
                    targetText = it.targetText,
                    notes = it.notes,
                    stability = it.stability,
                    difficulty = it.difficulty,
                    lastReviewed = it.lastReviewed,
                    due = it.due,
                    alternatives = it.alternatives,
                    note = it.note
                )
            }
        )
        settings.restoreSettings(backup.settings)
    }
}

private fun Coast.toBackup() = BackupCoast(coastId, language, creationDate)

private fun LanguageIsland.toBackup() = BackupIsland(islandId, coastId, name, creationDate, emoji, lastStudied)

private fun Flashcard.toBackup() = BackupCard(
    cardId = cardId,
    islandId = islandId,
    nativeText = nativeText,
    targetText = targetText,
    notes = notes,
    stability = stability,
    difficulty = difficulty,
    lastReviewed = lastReviewed,
    due = due,
    alternatives = alternatives,
    note = note
)

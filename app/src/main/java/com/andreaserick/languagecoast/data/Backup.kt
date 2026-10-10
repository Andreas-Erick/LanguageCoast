package com.andreaserick.languagecoast.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.time.Clock
import javax.inject.Inject

/** Written into every backup, so restoring can tell a backup from any other JSON file. */
const val BACKUP_FORMAT = "language-coast-backup"

/** The newest backup version this app can read. Bump it when the format changes in a way older apps can't read. */
const val BACKUP_VERSION = 1

/**
 * Everything in the app, as one file for moving to a new phone: every coast, island and card with its
 * review progress, the streak and study days, and the settings. API keys are left out, since backup
 * files are often kept in shared places like Google Drive. Rows keep their IDs, so cards stay on their
 * islands and the active coast stays the same.
 */
@Serializable
data class Backup(
    val format: String = BACKUP_FORMAT,
    val version: Int = BACKUP_VERSION,
    /** When the backup was made (epoch millis). */
    val createdAt: Long,
    val settings: BackupSettings,
    val coasts: List<BackupCoast>,
    val islands: List<BackupIsland>,
    val cards: List<BackupCard>
)

/** The settings in a [Backup]: all of [SettingsRepository] except the API keys. */
@Serializable
data class BackupSettings(
    val nativeLanguage: String = SettingsDefaults.NATIVE_LANGUAGE,
    val activeCoastId: Int? = null,
    val translationProvider: String? = null,
    val geminiModel: String? = null,
    val openRouterModel: String? = null,
    val streakCount: Int = 0,
    /** The day of the last study session (ISO, e.g. "2026-10-11"), which the streak counts from. */
    val lastStudyDate: String? = null,
    /** Days with a completed study session (ISO dates). */
    val studyDays: List<String> = emptyList(),
    val reminderEnabled: Boolean = true,
    /** The reminder's fixed time in minutes after midnight, or null for a random time. */
    val reminderMinuteOfDay: Int? = null,
    val studyReversed: Boolean = false
)

@Serializable
data class BackupCoast(
    @SerialName("id") val coastId: Int,
    val language: String,
    val creationDate: Long
)

@Serializable
data class BackupIsland(
    @SerialName("id") val islandId: Int,
    val coastId: Int,
    val name: String,
    val creationDate: Long,
    val emoji: String? = null,
    val lastStudied: Long? = null
)

/** A [Flashcard] with its review progress ([stability], [difficulty], [lastReviewed], [due]). */
@Serializable
data class BackupCard(
    @SerialName("id") val cardId: Int,
    val islandId: Int,
    val nativeText: String,
    val targetText: String,
    val notes: String? = null,
    val stability: Double? = null,
    val difficulty: Double? = null,
    val lastReviewed: Long? = null,
    val due: Long? = null,
    val alternatives: List<String> = emptyList(),
    val note: String? = null
)

/** Why a file can't be restored, in words for the user. */
class BackupException(message: String) : Exception(message)

private val backupJson = Json {
    // Newer backups of the same version may add fields this app doesn't know yet.
    ignoreUnknownKeys = true
    encodeDefaults = true
}

/** [backup] as JSON. */
fun encodeBackup(backup: Backup): String = backupJson.encodeToString(Backup.serializer(), backup)

/**
 * The backup in [text]. Throws [BackupException] if it isn't a Language Coast backup, was made by a newer
 * version of the app, or has islands or cards whose coast or island is missing.
 */
fun decodeBackup(text: String): Backup {
    val backup = try {
        backupJson.decodeFromString(Backup.serializer(), text.removePrefix("﻿"))
    } catch (e: SerializationException) {
        throw BackupException("That file isn't a Language Coast backup.")
    } catch (e: IllegalArgumentException) {
        throw BackupException("That file isn't a Language Coast backup.")
    }
    if (backup.format != BACKUP_FORMAT) throw BackupException("That file isn't a Language Coast backup.")
    if (backup.version > BACKUP_VERSION) throw BackupException("This backup is from a newer version of Language Coast. Update the app first.")

    val coastIds = backup.coasts.mapTo(HashSet()) { it.coastId }
    val islandIds = backup.islands.mapTo(HashSet()) { it.islandId }
    val isComplete = coastIds.size == backup.coasts.size &&
        islandIds.size == backup.islands.size &&
        backup.cards.distinctBy { it.cardId }.size == backup.cards.size &&
        backup.islands.all { it.coastId in coastIds } &&
        backup.cards.all { it.islandId in islandIds }
    if (!isComplete) throw BackupException("This backup is damaged and can't be restored.")
    return backup
}

/** What a [Backup] holds, for the restore confirmation. */
data class BackupSummary(val createdAt: Long, val coastCount: Int, val islandCount: Int, val cardCount: Int, val streakCount: Int)

/** The counts shown before restoring [this]. */
fun Backup.summary() = BackupSummary(createdAt, coasts.size, islands.size, cards.size, settings.streakCount)

/** Makes backups of everything in the app and restores them. */
class BackupManager @Inject constructor(
    private val flashcards: FlashcardRepository,
    private val settings: SettingsRepository,
    private val clock: Clock
) {
    /** A backup of everything in the app right now. */
    suspend fun createBackup(): Backup {
        val contents = flashcards.getAllContent()
        return Backup(
            createdAt = clock.millis(),
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

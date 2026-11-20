package com.andreaserick.languagecoast.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

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
    val localAiModel: String? = null,
    val openAiModel: String? = null,
    val claudeModel: String? = null,
    val groqModel: String? = null,
    val deepSeekModel: String? = null,
    val streakCount: Int = 0,
    /** The day of the last study session (ISO, e.g. "2026-10-11"), which the streak counts from. */
    val lastStudyDate: String? = null,
    /** Days with a completed study session (ISO dates). */
    val studyDays: List<String> = emptyList(),
    val reminderEnabled: Boolean = true,
    /** The reminder's fixed time in minutes after midnight, or null for a random time. */
    val reminderMinuteOfDay: Int? = null,
    val studyReversed: Boolean = false,
    /** Islands to study per week; null means the default. */
    val weeklyGoal: Int? = null,
    /** The day a streak freeze last stood in for a missed day (ISO date). */
    val freezeUsedOn: String? = null,
    val themeMode: String? = null,
    val hapticsEnabled: Boolean = true,
    val soundsEnabled: Boolean = true,
    /** Starter topics added whole to a coast, as "coastId:topic". */
    val starterTopicsAdded: List<String> = emptyList(),
    /** The user's name, city and so on, by placeholder (see [Profile.fillers]). */
    val profile: Map<String, String> = emptyMap(),
    /** What the user answered about themselves, by question key (see [PersonalQuestion.key]). */
    val personalAnswers: Map<String, String> = emptyMap(),
    /** The islands about the user written on a coast, as "coastId:islandId" to the fingerprint of the facts used. */
    val personalIslandsBuilt: Map<String, String> = emptyMap(),
    /** How sentences about the user are worded (see [Wording.key]); null if they haven't said. */
    val wording: String? = null,
    /** How much new a daily session brings (see [Pace.key]); null means the default. */
    val pace: String? = null
)

@Serializable
data class BackupCoast(
    @SerialName("id") val coastId: Int,
    val language: String,
    val creationDate: Long,
    /** Why the user learns the language, if they said. */
    val goal: String? = null,
    /** How many starter sentences the daily session has offered on the coast. */
    val bankOffered: Int = 0,
    /** Where the language will be used, if the user said. */
    val region: String? = null
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
    val note: String? = null,
    val lapses: Int = 0
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
 * Whether every date and time in these settings can be read back, and the streak is a count. The app writes
 * them itself, but a file can be edited by hand or made by another tool, and one value that can't be read
 * must not end up in the settings.
 */
internal fun BackupSettings.isReadable(): Boolean =
    // A hundred years of days: more than that is not a streak anyone studied.
    streakCount in 0..36_500 &&
        (weeklyGoal == null || weeklyGoal in 1..365) &&
        (lastStudyDate == null || parseDateOrNull(lastStudyDate) != null) &&
        (freezeUsedOn == null || parseDateOrNull(freezeUsedOn) != null) &&
        studyDays.all { parseDateOrNull(it) != null } &&
        (reminderMinuteOfDay == null || reminderMinuteOfDay in MINUTES_OF_DAY)

/** Maximum allowed JSON nesting depth before parsing, preventing StackOverflowError on crafted payloads. */
private const val MAX_JSON_NESTING_DEPTH = 64

/** Valid epoch millisecond range for backup timestamps (1970-01-01 to 2200-01-01 UTC). */
private const val MIN_VALID_EPOCH_MILLIS = 0L
private const val MAX_VALID_EPOCH_MILLIS = 7_258_118_400_000L

/**
 * Checks in O(N) time without recursion that [text] does not exceed [maxDepth] levels of nested JSON
 * objects or arrays, preventing StackOverflowError in recursive-descent JSON parsers.
 */
internal fun isJsonNestingSafe(text: String, maxDepth: Int = MAX_JSON_NESTING_DEPTH): Boolean {
    var depth = 0
    var inString = false
    var escaped = false
    for (ch in text) {
        if (inString) {
            if (escaped) {
                escaped = false
            } else if (ch == '\\') {
                escaped = true
            } else if (ch == '"') {
                inString = false
            }
            continue
        }
        when (ch) {
            '"' -> inString = true
            '{', '[' -> {
                depth++
                if (depth > maxDepth) return false
            }
            '}', ']' -> if (depth > 0) depth--
        }
    }
    return true
}

/**
 * The backup in [text]. Throws [BackupException] if it isn't a Language Coast backup, was made by a newer
 * version of the app, has islands or cards whose coast or island is missing, has two coasts for one
 * language, has invalid FSRS numbers or timestamps, or has a date or reminder time that can't be read
 * ([isReadable]). Nothing is restored from such a file, so what is in the app stays as it is.
 */
fun decodeBackup(text: String): Backup {
    val clean = text.removePrefix("\uFEFF")
    if (!isJsonNestingSafe(clean)) throw BackupException("That file isn't a Language Coast backup.")
    val backup = try {
        backupJson.decodeFromString(Backup.serializer(), clean)
    } catch (e: SerializationException) {
        throw BackupException("That file isn't a Language Coast backup.")
    } catch (e: IllegalArgumentException) {
        throw BackupException("That file isn't a Language Coast backup.")
    }
    if (backup.format != BACKUP_FORMAT) throw BackupException("That file isn't a Language Coast backup.")
    if (backup.version < 1) throw BackupException("That file isn't a Language Coast backup.")
    if (backup.version > BACKUP_VERSION) throw BackupException("This backup is from a newer version of Language Coast. Update the app first.")

    val coastIds = backup.coasts.mapTo(HashSet()) { it.coastId }
    val islandIds = backup.islands.mapTo(HashSet()) { it.islandId }
    val validTimestamp = backup.createdAt in MIN_VALID_EPOCH_MILLIS..MAX_VALID_EPOCH_MILLIS
    val coastsValid = backup.coasts.all { coast ->
        coast.language.isNotBlank() &&
            coast.creationDate in MIN_VALID_EPOCH_MILLIS..MAX_VALID_EPOCH_MILLIS &&
            coast.bankOffered >= 0
    }
    val islandsValid = backup.islands.all { island ->
        island.coastId in coastIds &&
            island.name.isNotBlank() &&
            island.creationDate in MIN_VALID_EPOCH_MILLIS..MAX_VALID_EPOCH_MILLIS &&
            (island.lastStudied == null || island.lastStudied in MIN_VALID_EPOCH_MILLIS..MAX_VALID_EPOCH_MILLIS)
    }
    val cardsValid = backup.cards.all { card ->
        card.islandId in islandIds &&
            card.lapses >= 0 &&
            (card.stability == null || (card.stability.isFinite() && card.stability > 0.0)) &&
            (card.difficulty == null || (card.difficulty.isFinite() && card.difficulty in 1.0..10.0)) &&
            (card.due == null || card.due in MIN_VALID_EPOCH_MILLIS..MAX_VALID_EPOCH_MILLIS) &&
            (card.lastReviewed == null || card.lastReviewed in MIN_VALID_EPOCH_MILLIS..MAX_VALID_EPOCH_MILLIS)
    }
    val isComplete = validTimestamp &&
        coastsValid &&
        coastIds.size == backup.coasts.size &&
        // The app keeps one coast per language; two would push each other out while being restored.
        backup.coasts.distinctBy { it.language }.size == backup.coasts.size &&
        backup.settings.isReadable() &&
        islandIds.size == backup.islands.size &&
        backup.cards.distinctBy { it.cardId }.size == backup.cards.size &&
        islandsValid &&
        cardsValid
    if (!isComplete) throw BackupException("This backup is damaged and can't be restored.")
    return backup
}

/** What a [Backup] holds, for the restore confirmation. */
data class BackupSummary(val createdAt: Long, val coastCount: Int, val islandCount: Int, val cardCount: Int, val streakCount: Int)

/** The counts shown before restoring [this]. */
fun Backup.summary() = BackupSummary(createdAt, coasts.size, islands.size, cards.size, settings.streakCount)

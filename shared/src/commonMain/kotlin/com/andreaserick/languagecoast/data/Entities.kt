package com.andreaserick.languagecoast.data

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlin.time.Clock

/**
 * A "Coast": everything the user studies in one target language (e.g. the "German Coast").
 * Each coast has its own [LanguageIsland]s.
 *
 * @property coastId Unique identifier for the coast (auto-generated).
 * @property language The target language studied on this coast (one coast per language).
 * @property creationDate Timestamp of when the coast was created.
 * @property goal Why the user learns this language, in their own words (e.g. "A trip to Lisbon in March"), or null
 *     if they haven't said. The daily session's new sentences are picked to fit it.
 * @property bankOffered How many starter sentences ([PhraseBank]) the daily session has offered on this coast. The
 *     basics come first: until [BASICS_FROM_BANK] of them were offered, new sentences come from the bank, not the AI.
 * @property region Where the language will be used (e.g. "Mexico", "Switzerland"), so translations use that place's
 *     words, forms and register; null for no preference.
 */
@Entity(
    tableName = "coasts",
    indices = [Index(value = ["language"], unique = true)]
)
data class Coast(
    @PrimaryKey(autoGenerate = true) val coastId: Int = 0,
    val language: String,
    val creationDate: Long = Clock.System.now().toEpochMilliseconds(),
    val goal: String? = null,
    @ColumnInfo(defaultValue = "0") val bankOffered: Int = 0,
    val region: String? = null
) {
    val displayName: String get() = "$language Coast"
}

/**
 * A sentence the app suggests learning next on a coast, prepared ahead of the daily session so it opens
 * without waiting for the AI. Kept until the user keeps it (it becomes a card and the row is deleted) or
 * skips it (it stays, marked [skipped], so it isn't suggested twice).
 *
 * @property nativeText The sentence in the user's language.
 * @property targetText Its translation in the coast's language.
 * @property category The island it would go on.
 * @property emoji An emoji for [category], if the AI gave one.
 * @property alternatives Another way to say it (the first translation, when the natural-speech check rewrote it).
 * @property note Why the translation was rewritten, if it was; see [StarterTranslator].
 */
@Entity(
    tableName = "suggested_sentences",
    foreignKeys = [
        ForeignKey(
            entity = Coast::class,
            parentColumns = ["coastId"],
            childColumns = ["coastId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("coastId")]
)
data class SuggestedSentence(
    @PrimaryKey(autoGenerate = true) val suggestionId: Int = 0,
    val coastId: Int,
    val nativeText: String,
    val targetText: String,
    val category: String,
    val emoji: String? = null,
    val skipped: Boolean = false,
    val creationDate: Long = Clock.System.now().toEpochMilliseconds(),
    @ColumnInfo(defaultValue = "") val alternatives: List<String> = emptyList(),
    val note: String? = null
)

/**
 * A [Coast] with what the coast overview shows about it.
 *
 * @property dueCount Cards on this coast that are due (see [isDue]) at the time the query was given.
 * @property lastStudied When an island on this coast last finished a study session, or null if never.
 * @property islandsStudiedRecently Islands that finished a study session since the time the query was given.
 */
data class CoastSummary(
    @Embedded val coast: Coast,
    val islandCount: Int,
    val cardCount: Int,
    val dueCount: Int = 0,
    val lastStudied: Long? = null,
    val islandsStudiedRecently: Int = 0
)

/**
 * Represents a "Language Island" or category used to group related flashcards.
 *
 * @property islandId Unique identifier for the island (auto-generated).
 * @property coastId The ID of the [Coast] this island belongs to.
 * @property name The display name of the category (e.g., "Food", "Travel"), unique within its coast.
 * @property creationDate Timestamp of when the island was created.
 * @property emoji An emoji picturing the category (e.g. "🍽️"), or null to use [islandEmoji]'s fallback.
 * @property lastStudied When a study session on this island was last completed, or null if never.
 */
@Entity(
    tableName = "language_islands",
    foreignKeys = [
        ForeignKey(
            entity = Coast::class,
            parentColumns = ["coastId"],
            childColumns = ["coastId"],
            onDelete = ForeignKey.CASCADE // If a coast is deleted, its islands (and their cards) are also removed.
        )
    ],
    indices = [Index("coastId")]
)
data class LanguageIsland(
    @PrimaryKey(autoGenerate = true) val islandId: Int = 0,
    val coastId: Int,
    val name: String,
    val creationDate: Long = Clock.System.now().toEpochMilliseconds(),
    val emoji: String? = null,
    val lastStudied: Long? = null
)

/** A [Flashcard] with the name and emoji of the island it is on, for lists across islands. */
data class RecentCard(
    @Embedded val card: Flashcard,
    val islandName: String,
    val islandEmoji: String?
) {
    /** The island's emoji, or the one guessed from its name (as [islandEmoji] does for islands). */
    val emoji: String get() = islandEmoji ?: emojiForCategory(islandName)
}

/** A [Flashcard] with the island and coast it is on, for searching across all coasts. */
data class PlacedCard(
    @Embedded val card: Flashcard,
    val islandName: String,
    val islandEmoji: String?,
    val coastId: Int,
    val language: String
) {
    /** The island's emoji, or the one guessed from its name (as [islandEmoji] does for islands). */
    val emoji: String get() = islandEmoji ?: emojiForCategory(islandName)
}

/** A [LanguageIsland] with its number of cards, and how many of them are due (see [isDue]). */
data class IslandSummary(
    @Embedded val island: LanguageIsland,
    val cardCount: Int,
    val dueCount: Int = 0
)

/**
 * Represents an individual study card containing a native phrase and its target translation.
 *
 * @property cardId Unique identifier for the flashcard (auto-generated).
 * @property islandId The ID of the [LanguageIsland] this card belongs to.
 * @property nativeText The phrase in the user's native language.
 * @property targetText The translated phrase in the language being studied.
 * @property notes Optional extra information or context for the card.
 * @property stability Spaced repetition memory stability in days, or null if the card was never reviewed.
 * @property difficulty Spaced repetition difficulty from 1 to 10, or null if the card was never reviewed.
 * @property lastReviewed When the card was last graded (epoch millis), or null if never.
 * @property due When the card should next be studied (epoch millis), or null for a new card, which is due right away.
 * @property alternatives Other correct translations (from the AI, for longer sentences), shown on the back when studying.
 * @property note How [alternatives] differ from [targetText] (e.g. formal vs. informal), if there are any.
 * @property lapses How often the card was graded Again; see [isLeaky].
 */
@Entity(
    tableName = "flashcards",
    foreignKeys = [
        ForeignKey(
            entity = LanguageIsland::class,
            parentColumns = ["islandId"],
            childColumns = ["islandId"],
            onDelete = ForeignKey.CASCADE // If an island is deleted, all its associated cards are also removed.
        )
    ],
    indices = [Index("islandId")]
)
data class Flashcard(
    @PrimaryKey(autoGenerate = true) val cardId: Int = 0,
    val islandId: Int,
    val nativeText: String,
    val targetText: String,
    val notes: String? = null,
    val stability: Double? = null,
    val difficulty: Double? = null,
    val lastReviewed: Long? = null,
    val due: Long? = null,
    @ColumnInfo(defaultValue = "") val alternatives: List<String> = emptyList(),
    val note: String? = null,
    @ColumnInfo(defaultValue = "0") val lapses: Int = 0
)

package com.andreaserick.languagecoast.data

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A "Coast": everything the user studies in one target language (e.g. the "German Coast").
 * Each coast has its own [LanguageIsland]s.
 *
 * @property coastId Unique identifier for the coast (auto-generated).
 * @property language The target language studied on this coast (one coast per language).
 * @property creationDate Timestamp of when the coast was created.
 */
@Entity(
    tableName = "coasts",
    indices = [Index(value = ["language"], unique = true)]
)
data class Coast(
    @PrimaryKey(autoGenerate = true) val coastId: Int = 0,
    val language: String,
    val creationDate: Long = System.currentTimeMillis()
) {
    val displayName: String get() = "$language Coast"
}

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
    val creationDate: Long = System.currentTimeMillis(),
    val emoji: String? = null,
    val lastStudied: Long? = null
)

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
    val due: Long? = null
)

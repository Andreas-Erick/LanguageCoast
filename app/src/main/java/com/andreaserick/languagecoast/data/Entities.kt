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

/** A [Coast] with the number of islands and cards on it, for the coast overview. */
data class CoastSummary(
    @Embedded val coast: Coast,
    val islandCount: Int,
    val cardCount: Int
)

/**
 * Represents a "Language Island" or category used to group related flashcards.
 *
 * @property islandId Unique identifier for the island (auto-generated).
 * @property coastId The ID of the [Coast] this island belongs to.
 * @property name The display name of the category (e.g., "Food", "Travel"), unique within its coast.
 * @property creationDate Timestamp of when the island was created.
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
    val creationDate: Long = System.currentTimeMillis()
)

/**
 * Represents an individual study card containing a native phrase and its target translation.
 *
 * @property cardId Unique identifier for the flashcard (auto-generated).
 * @property islandId The ID of the [LanguageIsland] this card belongs to.
 * @property nativeText The phrase in the user's native language.
 * @property targetText The translated phrase in the language being studied.
 * @property notes Optional extra information or context for the card.
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
    val notes: String? = null
)

/**
 * Tracks the study progress and Spaced Repetition System (SRS) data for a specific card.
 *
 * @property progressId Unique identifier for this progress entry.
 * @property cardId The ID of the [Flashcard] this progress refers to.
 * @property lastReviewed Timestamp of the last time the user studied this card.
 * @property nextReview Timestamp of when the card is scheduled to be reviewed next.
 */
@Entity(
    tableName = "study_progress",
    foreignKeys = [
        ForeignKey(
            entity = Flashcard::class,
            parentColumns = ["cardId"],
            childColumns = ["cardId"],
            onDelete = ForeignKey.CASCADE // If a card is deleted, its progress tracking is also removed.
        )
    ],
    indices = [Index("cardId")]
)
data class StudyProgress(
    @PrimaryKey(autoGenerate = true) val progressId: Int = 0,
    val cardId: Int,
    val lastReviewed: Long = 0L,
    val nextReview: Long = 0L
)

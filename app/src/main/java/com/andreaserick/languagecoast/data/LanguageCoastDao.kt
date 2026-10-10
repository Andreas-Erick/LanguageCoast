package com.andreaserick.languagecoast.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/** SQL for [isDue]: new cards, and reviewed cards whose due time has passed at `:now`. */
private const val DUE = "(flashcards.due IS NULL OR flashcards.due <= :now)"

/** Room queries and writes for coasts, islands and flashcards. */
@Dao
interface LanguageCoastDao {

    // --- Coasts ---
    /** Inserts [coast] and returns its new ID, or -1 if a coast for its language already exists. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCoast(coast: Coast): Long

    /** Every coast with its island, card and due-card counts, oldest first; see [FlashcardRepository.observeCoastSummaries]. */
    @Query(
        """
        SELECT coasts.*,
            (SELECT COUNT(*) FROM language_islands WHERE language_islands.coastId = coasts.coastId) AS islandCount,
            (SELECT COUNT(*) FROM flashcards
                INNER JOIN language_islands ON flashcards.islandId = language_islands.islandId
                WHERE language_islands.coastId = coasts.coastId) AS cardCount,
            (SELECT COUNT(*) FROM flashcards
                INNER JOIN language_islands ON flashcards.islandId = language_islands.islandId
                WHERE language_islands.coastId = coasts.coastId AND $DUE) AS dueCount,
            (SELECT MAX(lastStudied) FROM language_islands WHERE language_islands.coastId = coasts.coastId) AS lastStudied,
            (SELECT COUNT(*) FROM language_islands
                WHERE language_islands.coastId = coasts.coastId AND language_islands.lastStudied >= :studiedSince) AS islandsStudiedRecently
        FROM coasts ORDER BY creationDate ASC
        """
    )
    fun getCoastSummaries(studiedSince: Long, now: Long): Flow<List<CoastSummary>>

    /** Every coast, oldest first. */
    @Query("SELECT * FROM coasts ORDER BY creationDate ASC")
    fun getAllCoasts(): Flow<List<Coast>>

    /** The coast with ID [coastId], or null once it is deleted. */
    @Query("SELECT * FROM coasts WHERE coastId = :coastId")
    fun getCoast(coastId: Int): Flow<Coast?>

    /** The coast for [language], or null if there is none. */
    @Query("SELECT * FROM coasts WHERE language = :language LIMIT 1")
    suspend fun getCoastByLanguage(language: String): Coast?

    /** The coast that island [islandId] belongs to. */
    @Query(
        "SELECT coasts.* FROM coasts INNER JOIN language_islands ON language_islands.coastId = coasts.coastId " +
            "WHERE language_islands.islandId = :islandId"
    )
    fun getCoastForIsland(islandId: Int): Flow<Coast?>

    /** Deletes [coast]; its islands and cards are removed by cascade. */
    @Delete
    suspend fun deleteCoast(coast: Coast)

    // --- Islands ---
    /** Inserts [island] and returns its new ID. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIsland(island: LanguageIsland): Long

    /** The islands on coast [coastId], newest first. */
    @Query("SELECT * FROM language_islands WHERE coastId = :coastId ORDER BY creationDate DESC")
    fun getIslandsForCoast(coastId: Int): Flow<List<LanguageIsland>>

    /** The islands on coast [coastId] with their card and due-card counts at [now], newest first. */
    @Query(
        """
        SELECT language_islands.*,
            (SELECT COUNT(*) FROM flashcards WHERE flashcards.islandId = language_islands.islandId) AS cardCount,
            (SELECT COUNT(*) FROM flashcards WHERE flashcards.islandId = language_islands.islandId AND $DUE) AS dueCount
        FROM language_islands WHERE coastId = :coastId ORDER BY creationDate DESC
        """
    )
    fun getIslandSummaries(coastId: Int, now: Long): Flow<List<IslandSummary>>

    /** The island named [name] on coast [coastId], or null if there is none. */
    @Query("SELECT * FROM language_islands WHERE coastId = :coastId AND name = :name LIMIT 1")
    suspend fun getIslandByName(coastId: Int, name: String): LanguageIsland?

    /** The island with ID [islandId], or null if it doesn't exist. */
    @Query("SELECT * FROM language_islands WHERE islandId = :islandId")
    suspend fun getIsland(islandId: Int): LanguageIsland?

    /** The island with ID [islandId], or null once it is deleted. */
    @Query("SELECT * FROM language_islands WHERE islandId = :islandId")
    fun observeIsland(islandId: Int): Flow<LanguageIsland?>

    /** Sets when a study session on island [islandId] was last completed. */
    @Query("UPDATE language_islands SET lastStudied = :time WHERE islandId = :islandId")
    suspend fun setIslandLastStudied(islandId: Int, time: Long)

    /** Deletes [island]; its cards are removed by cascade. */
    @Delete
    suspend fun deleteIsland(island: LanguageIsland)

    // --- Flashcards ---
    /** Inserts [flashcard], replacing any card with the same ID, and returns its ID. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFlashcard(flashcard: Flashcard): Long

    /** The cards on island [islandId]. */
    @Query("SELECT * FROM flashcards WHERE islandId = :islandId")
    fun getCardsForIsland(islandId: Int): Flow<List<Flashcard>>

    /** The [limit] cards most recently added to coast [coastId], newest first, with their island's name and emoji. */
    @Query(
        """
        SELECT flashcards.*, language_islands.name AS islandName, language_islands.emoji AS islandEmoji
        FROM flashcards INNER JOIN language_islands ON flashcards.islandId = language_islands.islandId
        WHERE language_islands.coastId = :coastId
        ORDER BY flashcards.cardId DESC LIMIT :limit
        """
    )
    fun getRecentCards(coastId: Int, limit: Int): Flow<List<RecentCard>>

    /** Number of cards on island [islandId]. */
    @Query("SELECT COUNT(*) FROM flashcards WHERE islandId = :islandId")
    suspend fun countCardsInIsland(islandId: Int): Int

    /** Saves every field of [flashcard] over the stored card with the same ID. */
    @Update
    suspend fun updateFlashcard(flashcard: Flashcard)

    /** Replaces the translation and alternatives of card [cardId], leaving its other fields as they are. */
    @Query("UPDATE flashcards SET targetText = :targetText, alternatives = :alternatives WHERE cardId = :cardId")
    suspend fun setTranslation(cardId: Int, targetText: String, alternatives: List<String>)

    /** Replaces the user-editable fields of card [cardId], leaving its review progress as it is. */
    @Query(
        """
        UPDATE flashcards SET nativeText = :nativeText, targetText = :targetText, alternatives = :alternatives,
            note = :note, islandId = :islandId
        WHERE cardId = :cardId
        """
    )
    suspend fun editCard(cardId: Int, nativeText: String, targetText: String, alternatives: List<String>, note: String?, islandId: Int)

    /** Number of cards on all coasts that are due at [now]. */
    @Query("SELECT COUNT(*) FROM flashcards WHERE $DUE")
    suspend fun countDueCards(now: Long): Int

    /** Deletes [flashcard]. */
    @Delete
    suspend fun deleteFlashcard(flashcard: Flashcard)

    // --- Snapshots for undoing deletes ---
    /** The islands on coast [coastId], read once rather than observed. */
    @Query("SELECT * FROM language_islands WHERE coastId = :coastId")
    suspend fun getIslandsForCoastOnce(coastId: Int): List<LanguageIsland>

    /** The cards on any of [islandIds], read once rather than observed. */
    @Query("SELECT * FROM flashcards WHERE islandId IN (:islandIds)")
    suspend fun getCardsForIslandsOnce(islandIds: List<Int>): List<Flashcard>

    /** Inserts [coasts] with their IDs, replacing rows with the same ID. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCoasts(coasts: List<Coast>)

    /** Inserts [islands] with their IDs, replacing rows with the same ID. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertIslands(islands: List<LanguageIsland>)

    /** Inserts [cards] with their IDs, replacing rows with the same ID. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFlashcards(cards: List<Flashcard>)

    /** Puts back everything in [content] with its original IDs, parents before children. */
    @Transaction
    suspend fun restore(content: DeletedContent) {
        insertCoasts(content.coasts)
        insertIslands(content.islands)
        insertFlashcards(content.cards)
    }
}

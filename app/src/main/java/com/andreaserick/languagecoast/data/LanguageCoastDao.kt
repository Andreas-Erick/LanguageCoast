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

@Dao
interface LanguageCoastDao {

    // --- Coasts ---
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCoast(coast: Coast): Long

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

    @Query("SELECT * FROM coasts ORDER BY creationDate ASC")
    fun getAllCoasts(): Flow<List<Coast>>

    @Query("SELECT * FROM coasts WHERE coastId = :coastId")
    fun getCoast(coastId: Int): Flow<Coast?>

    @Query("SELECT * FROM coasts WHERE language = :language LIMIT 1")
    suspend fun getCoastByLanguage(language: String): Coast?

    @Query(
        "SELECT coasts.* FROM coasts INNER JOIN language_islands ON language_islands.coastId = coasts.coastId " +
            "WHERE language_islands.islandId = :islandId"
    )
    fun getCoastForIsland(islandId: Int): Flow<Coast?>

    @Delete
    suspend fun deleteCoast(coast: Coast)

    // --- Islands ---
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIsland(island: LanguageIsland): Long

    @Query("SELECT * FROM language_islands WHERE coastId = :coastId ORDER BY creationDate DESC")
    fun getIslandsForCoast(coastId: Int): Flow<List<LanguageIsland>>

    @Query(
        """
        SELECT language_islands.*,
            (SELECT COUNT(*) FROM flashcards WHERE flashcards.islandId = language_islands.islandId) AS cardCount,
            (SELECT COUNT(*) FROM flashcards WHERE flashcards.islandId = language_islands.islandId AND $DUE) AS dueCount
        FROM language_islands WHERE coastId = :coastId ORDER BY creationDate DESC
        """
    )
    fun getIslandSummaries(coastId: Int, now: Long): Flow<List<IslandSummary>>

    @Query("SELECT * FROM language_islands WHERE coastId = :coastId AND name = :name LIMIT 1")
    suspend fun getIslandByName(coastId: Int, name: String): LanguageIsland?

    @Query("SELECT * FROM language_islands WHERE islandId = :islandId")
    suspend fun getIsland(islandId: Int): LanguageIsland?

    @Query("UPDATE language_islands SET lastStudied = :time WHERE islandId = :islandId")
    suspend fun setIslandLastStudied(islandId: Int, time: Long)

    @Delete
    suspend fun deleteIsland(island: LanguageIsland)

    // --- Flashcards ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFlashcard(flashcard: Flashcard): Long

    @Query("SELECT * FROM flashcards WHERE islandId = :islandId")
    fun getCardsForIsland(islandId: Int): Flow<List<Flashcard>>

    @Query(
        """
        SELECT flashcards.*, language_islands.name AS islandName, language_islands.emoji AS islandEmoji
        FROM flashcards INNER JOIN language_islands ON flashcards.islandId = language_islands.islandId
        WHERE language_islands.coastId = :coastId
        ORDER BY flashcards.cardId DESC LIMIT :limit
        """
    )
    fun getRecentCards(coastId: Int, limit: Int): Flow<List<RecentCard>>

    @Query("SELECT COUNT(*) FROM flashcards WHERE islandId = :islandId")
    suspend fun countCardsInIsland(islandId: Int): Int

    @Update
    suspend fun updateFlashcard(flashcard: Flashcard)

    @Query("UPDATE flashcards SET targetText = :targetText, alternatives = :alternatives WHERE cardId = :cardId")
    suspend fun setTranslation(cardId: Int, targetText: String, alternatives: List<String>)

    @Query("SELECT COUNT(*) FROM flashcards WHERE $DUE")
    suspend fun countDueCards(now: Long): Int

    @Delete
    suspend fun deleteFlashcard(flashcard: Flashcard)

    // --- Snapshots for undoing deletes ---
    @Query("SELECT * FROM language_islands WHERE coastId = :coastId")
    suspend fun getIslandsForCoastOnce(coastId: Int): List<LanguageIsland>

    @Query("SELECT * FROM flashcards WHERE islandId IN (:islandIds)")
    suspend fun getCardsForIslandsOnce(islandIds: List<Int>): List<Flashcard>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCoasts(coasts: List<Coast>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertIslands(islands: List<LanguageIsland>)

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

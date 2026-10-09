package com.andreaserick.languagecoast.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

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
                WHERE language_islands.coastId = coasts.coastId) AS cardCount
        FROM coasts ORDER BY creationDate ASC
        """
    )
    fun getCoastSummaries(): Flow<List<CoastSummary>>

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

    @Query("SELECT * FROM language_islands WHERE coastId = :coastId AND name = :name LIMIT 1")
    suspend fun getIslandByName(coastId: Int, name: String): LanguageIsland?

    @Delete
    suspend fun deleteIsland(island: LanguageIsland)

    // --- Flashcards ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFlashcard(flashcard: Flashcard): Long

    @Query("SELECT * FROM flashcards WHERE islandId = :islandId")
    fun getCardsForIsland(islandId: Int): Flow<List<Flashcard>>

    @Delete
    suspend fun deleteFlashcard(flashcard: Flashcard)

    // --- Study Progress ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStudyProgress(progress: StudyProgress)
}

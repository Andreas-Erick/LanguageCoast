package com.andreaserick.languagecoast.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface LanguageCoastDao {

    // --- Islands ---
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIsland(island: LanguageIsland): Long

    @Query("SELECT * FROM language_islands ORDER BY creationDate DESC")
    fun getAllIslands(): Flow<List<LanguageIsland>>

    @Query("SELECT * FROM language_islands WHERE name = :name LIMIT 1")
    suspend fun getIslandByName(name: String): LanguageIsland?

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
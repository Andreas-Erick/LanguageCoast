package com.andreaserick.languagecoast.data

import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/** Source of truth for islands and flashcards. */
interface FlashcardRepository {
    fun observeIslands(): Flow<List<LanguageIsland>>
    fun observeCards(islandId: Int): Flow<List<Flashcard>>

    /** Saves a card into the island named [category], creating the island if needed. */
    suspend fun addFlashcard(nativeText: String, targetText: String, category: String)
    suspend fun deleteIsland(island: LanguageIsland)
    suspend fun deleteFlashcard(card: Flashcard)
}

class OfflineFlashcardRepository @Inject constructor(
    private val dao: LanguageCoastDao
) : FlashcardRepository {

    override fun observeIslands(): Flow<List<LanguageIsland>> = dao.getAllIslands()

    override fun observeCards(islandId: Int): Flow<List<Flashcard>> = dao.getCardsForIsland(islandId)

    override suspend fun addFlashcard(nativeText: String, targetText: String, category: String) {
        val islandId = dao.getIslandByName(category)?.islandId
            ?: dao.insertIsland(LanguageIsland(name = category)).toInt()
        dao.insertFlashcard(Flashcard(islandId = islandId, nativeText = nativeText, targetText = targetText))
    }

    override suspend fun deleteIsland(island: LanguageIsland) = dao.deleteIsland(island)

    override suspend fun deleteFlashcard(card: Flashcard) = dao.deleteFlashcard(card)
}

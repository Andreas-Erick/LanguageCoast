package com.andreaserick.languagecoast.data

import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/** Source of truth for coasts, islands and flashcards. */
interface FlashcardRepository {
    fun observeCoastSummaries(): Flow<List<CoastSummary>>
    fun observeCoasts(): Flow<List<Coast>>
    fun observeCoast(coastId: Int): Flow<Coast?>
    fun observeCoastForIsland(islandId: Int): Flow<Coast?>

    /** Creates the coast for [language], or returns the existing one's ID. */
    suspend fun addCoast(language: String): Int
    suspend fun deleteCoast(coast: Coast)

    fun observeIslands(coastId: Int): Flow<List<LanguageIsland>>
    fun observeCards(islandId: Int): Flow<List<Flashcard>>

    /** Saves a card into the island named [category] on coast [coastId], creating the island if needed. */
    suspend fun addFlashcard(coastId: Int, nativeText: String, targetText: String, category: String)
    suspend fun deleteIsland(island: LanguageIsland)
    suspend fun deleteFlashcard(card: Flashcard)
}

class OfflineFlashcardRepository @Inject constructor(
    private val dao: LanguageCoastDao
) : FlashcardRepository {

    override fun observeCoastSummaries(): Flow<List<CoastSummary>> = dao.getCoastSummaries()

    override fun observeCoasts(): Flow<List<Coast>> = dao.getAllCoasts()

    override fun observeCoast(coastId: Int): Flow<Coast?> = dao.getCoast(coastId)

    override fun observeCoastForIsland(islandId: Int): Flow<Coast?> = dao.getCoastForIsland(islandId)

    override suspend fun addCoast(language: String): Int =
        dao.getCoastByLanguage(language)?.coastId
            ?: dao.insertCoast(Coast(language = language)).toInt()

    override suspend fun deleteCoast(coast: Coast) = dao.deleteCoast(coast)

    override fun observeIslands(coastId: Int): Flow<List<LanguageIsland>> = dao.getIslandsForCoast(coastId)

    override fun observeCards(islandId: Int): Flow<List<Flashcard>> = dao.getCardsForIsland(islandId)

    override suspend fun addFlashcard(coastId: Int, nativeText: String, targetText: String, category: String) {
        val islandId = dao.getIslandByName(coastId, category)?.islandId
            ?: dao.insertIsland(LanguageIsland(coastId = coastId, name = category)).toInt()
        dao.insertFlashcard(Flashcard(islandId = islandId, nativeText = nativeText, targetText = targetText))
    }

    override suspend fun deleteIsland(island: LanguageIsland) = dao.deleteIsland(island)

    override suspend fun deleteFlashcard(card: Flashcard) = dao.deleteFlashcard(card)
}

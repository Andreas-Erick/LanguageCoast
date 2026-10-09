package com.andreaserick.languagecoast.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/** Everything removed by one delete, so it can be put back with [FlashcardRepository.restore]. */
data class DeletedContent(
    val coasts: List<Coast> = emptyList(),
    val islands: List<LanguageIsland> = emptyList(),
    val cards: List<Flashcard> = emptyList()
)

/** A card just saved by [FlashcardRepository.addFlashcard]; [createdIsland] is the island made for it, if any. */
data class AddedCard(
    val card: Flashcard,
    val createdIsland: LanguageIsland?
)

/** Source of truth for coasts, islands and flashcards. */
interface FlashcardRepository {
    /** @param studiedSince Islands that finished a session at or after this time count as studied recently. */
    fun observeCoastSummaries(studiedSince: Long): Flow<List<CoastSummary>>
    fun observeCoasts(): Flow<List<Coast>>
    fun observeCoast(coastId: Int): Flow<Coast?>
    fun observeCoastForIsland(islandId: Int): Flow<Coast?>

    /** Creates the coast for [language], or returns the existing one's ID. */
    suspend fun addCoast(language: String): Int

    /** Deletes [coast] with its islands and cards, returning them for [restore]. */
    suspend fun deleteCoast(coast: Coast): DeletedContent

    fun observeIslands(coastId: Int): Flow<List<LanguageIsland>>
    fun observeIslandSummaries(coastId: Int): Flow<List<IslandSummary>>
    fun observeCards(islandId: Int): Flow<List<Flashcard>>

    /**
     * Saves a card into the island named [category] on coast [coastId], creating the island
     * (with [emoji], or one guessed from the name) if needed.
     */
    suspend fun addFlashcard(
        coastId: Int,
        nativeText: String,
        targetText: String,
        category: String,
        emoji: String? = null
    ): AddedCard

    /** Removes a card added by [addFlashcard], and its island if that was created for it and is now empty. */
    suspend fun undoAdd(added: AddedCard)

    /** Deletes [island] with its cards, returning them for [restore]. */
    suspend fun deleteIsland(island: LanguageIsland): DeletedContent
    suspend fun deleteFlashcard(card: Flashcard): DeletedContent

    /** Puts back what a delete removed, with the original IDs. */
    suspend fun restore(content: DeletedContent)

    /** Records that a study session on [islandId] was completed at [time] (epoch millis). */
    suspend fun markIslandStudied(islandId: Int, time: Long)

    /** Every coast with its islands and cards, oldest coast first, for exporting. */
    suspend fun getAllContent(): List<CoastContent>
}

class OfflineFlashcardRepository @Inject constructor(
    private val dao: LanguageCoastDao
) : FlashcardRepository {

    override fun observeCoastSummaries(studiedSince: Long): Flow<List<CoastSummary>> =
        dao.getCoastSummaries(studiedSince)

    override fun observeCoasts(): Flow<List<Coast>> = dao.getAllCoasts()

    override fun observeCoast(coastId: Int): Flow<Coast?> = dao.getCoast(coastId)

    override fun observeCoastForIsland(islandId: Int): Flow<Coast?> = dao.getCoastForIsland(islandId)

    override suspend fun addCoast(language: String): Int =
        dao.getCoastByLanguage(language)?.coastId
            ?: dao.insertCoast(Coast(language = language)).toInt()

    override suspend fun deleteCoast(coast: Coast): DeletedContent {
        val islands = dao.getIslandsForCoastOnce(coast.coastId)
        val content = DeletedContent(listOf(coast), islands, dao.getCardsForIslandsOnce(islands.map { it.islandId }))
        dao.deleteCoast(coast) // Cascades to islands and cards.
        return content
    }

    override fun observeIslands(coastId: Int): Flow<List<LanguageIsland>> = dao.getIslandsForCoast(coastId)

    override fun observeIslandSummaries(coastId: Int): Flow<List<IslandSummary>> = dao.getIslandSummaries(coastId)

    override fun observeCards(islandId: Int): Flow<List<Flashcard>> = dao.getCardsForIsland(islandId)

    override suspend fun addFlashcard(
        coastId: Int,
        nativeText: String,
        targetText: String,
        category: String,
        emoji: String?
    ): AddedCard {
        val existing = dao.getIslandByName(coastId, category)
        val island = existing ?: LanguageIsland(coastId = coastId, name = category, emoji = emoji ?: emojiForCategory(category))
            .let { it.copy(islandId = dao.insertIsland(it).toInt()) }
        val card = Flashcard(islandId = island.islandId, nativeText = nativeText, targetText = targetText)
        return AddedCard(
            card = card.copy(cardId = dao.insertFlashcard(card).toInt()),
            createdIsland = island.takeIf { existing == null }
        )
    }

    override suspend fun undoAdd(added: AddedCard) {
        dao.deleteFlashcard(added.card)
        val island = added.createdIsland ?: return
        if (dao.countCardsInIsland(island.islandId) == 0) dao.deleteIsland(island)
    }

    override suspend fun deleteIsland(island: LanguageIsland): DeletedContent {
        val content = DeletedContent(islands = listOf(island), cards = dao.getCardsForIslandsOnce(listOf(island.islandId)))
        dao.deleteIsland(island) // Cascades to cards.
        return content
    }

    override suspend fun deleteFlashcard(card: Flashcard): DeletedContent {
        dao.deleteFlashcard(card)
        return DeletedContent(cards = listOf(card))
    }

    override suspend fun restore(content: DeletedContent) = dao.restore(content)

    override suspend fun markIslandStudied(islandId: Int, time: Long) = dao.setIslandLastStudied(islandId, time)

    override suspend fun getAllContent(): List<CoastContent> =
        dao.getAllCoasts().first().map { coast ->
            val islands = dao.getIslandsForCoastOnce(coast.coastId)
            CoastContent(coast, islands, dao.getCardsForIslandsOnce(islands.map { it.islandId }))
        }
}

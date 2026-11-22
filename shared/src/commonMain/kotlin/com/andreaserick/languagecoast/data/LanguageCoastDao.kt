package com.andreaserick.languagecoast.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
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
            (SELECT COUNT(*) FROM flashcards
                INNER JOIN language_islands ON flashcards.islandId = language_islands.islandId
                WHERE language_islands.coastId = coasts.coastId AND flashcards.due IS NULL) AS newCount,
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

    /** Stores why the user learns the language of coast [coastId] and where they will use it; null clears either. */
    @Query("UPDATE coasts SET goal = :goal, region = :region WHERE coastId = :coastId")
    suspend fun setCoastGoal(coastId: Int, goal: String?, region: String?)

    /** Adds [count] to how many starter sentences the daily session has offered on coast [coastId]. */
    @Query("UPDATE coasts SET bankOffered = bankOffered + :count WHERE coastId = :coastId")
    suspend fun addBankOffered(coastId: Int, count: Int)

    // --- Suggested sentences ---
    /** The sentences suggested for coast [coastId] that weren't skipped, oldest first. */
    @Query("SELECT * FROM suggested_sentences WHERE coastId = :coastId AND skipped = 0 ORDER BY suggestionId ASC")
    fun getSuggestions(coastId: Int): Flow<List<SuggestedSentence>>

    /** Every suggestion for coast [coastId], skipped or not, read once. */
    @Query("SELECT * FROM suggested_sentences WHERE coastId = :coastId")
    suspend fun getSuggestionsOnce(coastId: Int): List<SuggestedSentence>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSuggestions(suggestions: List<SuggestedSentence>)

    /** Marks suggestion [suggestionId] skipped, so it stays known but isn't offered again. */
    @Query("UPDATE suggested_sentences SET skipped = 1 WHERE suggestionId = :suggestionId")
    suspend fun skipSuggestion(suggestionId: Int)

    @Query("DELETE FROM suggested_sentences WHERE suggestionId = :suggestionId")
    suspend fun deleteSuggestion(suggestionId: Int)

    /** Drops the suggestions for coast [coastId] that weren't skipped, e.g. once its goal changes. */
    @Query("DELETE FROM suggested_sentences WHERE coastId = :coastId AND skipped = 0")
    suspend fun clearPendingSuggestions(coastId: Int)

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
            (SELECT COUNT(*) FROM flashcards WHERE flashcards.islandId = language_islands.islandId AND $DUE) AS dueCount,
            (SELECT COUNT(*) FROM flashcards WHERE flashcards.islandId = language_islands.islandId AND flashcards.due IS NULL) AS newCount
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

    /** Every card with its island and coast, by coast (oldest first), then island name, then the order cards were added. */
    @Query(
        """
        SELECT flashcards.*, language_islands.name AS islandName, language_islands.emoji AS islandEmoji,
            coasts.coastId AS coastId, coasts.language AS language
        FROM flashcards
            INNER JOIN language_islands ON flashcards.islandId = language_islands.islandId
            INNER JOIN coasts ON language_islands.coastId = coasts.coastId
        ORDER BY coasts.creationDate, language_islands.name, flashcards.cardId
        """
    )
    fun getAllPlacedCards(): Flow<List<PlacedCard>>

    /** The cards of coast [coastId] with their island and coast, by island name, then the order cards were added. */
    @Query(
        """
        SELECT flashcards.*, language_islands.name AS islandName, language_islands.emoji AS islandEmoji,
            coasts.coastId AS coastId, coasts.language AS language
        FROM flashcards
            INNER JOIN language_islands ON flashcards.islandId = language_islands.islandId
            INNER JOIN coasts ON language_islands.coastId = coasts.coastId
        WHERE coasts.coastId = :coastId
        ORDER BY language_islands.name, flashcards.cardId
        """
    )
    fun getPlacedCardsForCoast(coastId: Int): Flow<List<PlacedCard>>

    /** The cards of island [islandId] with their island and coast, in the order they were added. */
    @Query(
        """
        SELECT flashcards.*, language_islands.name AS islandName, language_islands.emoji AS islandEmoji,
            coasts.coastId AS coastId, coasts.language AS language
        FROM flashcards
            INNER JOIN language_islands ON flashcards.islandId = language_islands.islandId
            INNER JOIN coasts ON language_islands.coastId = coasts.coastId
        WHERE flashcards.islandId = :islandId
        ORDER BY flashcards.cardId
        """
    )
    fun getPlacedCardsForIsland(islandId: Int): Flow<List<PlacedCard>>

    /** The cards of coast [coastId] as a practice sheet needs them, by island name, then the order cards were added. */
    @Query(
        """
        SELECT flashcards.islandId AS islandId, language_islands.name AS islandName,
            flashcards.targetText AS targetText, flashcards.nativeText AS nativeText, flashcards.alternatives AS alternatives,
            flashcards.stability AS stability, flashcards.difficulty AS difficulty, flashcards.lastReviewed AS lastReviewed,
            flashcards.lapses AS lapses,
            COALESCE(NULLIF(TRIM(flashcards.notes), ''), NULLIF(TRIM(flashcards.note), '')) AS note
        FROM flashcards INNER JOIN language_islands ON flashcards.islandId = language_islands.islandId
        WHERE language_islands.coastId = :coastId
        ORDER BY language_islands.name, flashcards.cardId
        """
    )
    suspend fun getWorksheetCards(coastId: Int): List<WorksheetSourceCard>

    /** Every island on every coast, newest first. */
    @Query("SELECT * FROM language_islands ORDER BY creationDate DESC")
    fun getAllIslands(): Flow<List<LanguageIsland>>

    /** Number of cards on island [islandId]. */
    @Query("SELECT COUNT(*) FROM flashcards WHERE islandId = :islandId")
    suspend fun countCardsInIsland(islandId: Int): Int

    /**
     * Saves the scheduling fields of card [cardId] after it was graded, leaving its texts as they are: a
     * correction made a moment before the grade must not be written over with the session's older copy.
     */
    @Query(
        "UPDATE flashcards SET stability = :stability, difficulty = :difficulty, lastReviewed = :lastReviewed, " +
            "due = :due, lapses = :lapses WHERE cardId = :cardId"
    )
    suspend fun setReview(cardId: Int, stability: Double?, difficulty: Double?, lastReviewed: Long?, due: Long?, lapses: Int)

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

    /** Studied cards due at [now] and cards never studied, on all coasts; see [DueCounts]. */
    @Query(
        "SELECT (SELECT COUNT(*) FROM flashcards WHERE flashcards.due IS NOT NULL AND flashcards.due <= :now) AS reviews, " +
            "(SELECT COUNT(*) FROM flashcards WHERE flashcards.due IS NULL) AS newCards"
    )
    suspend fun countDue(now: Long): DueCounts

    /** Deletes [flashcard]. */
    @Delete
    suspend fun deleteFlashcard(flashcard: Flashcard)

    // --- Snapshots for undoing deletes ---
    /** The islands on coast [coastId], read once rather than observed. */
    @Query("SELECT * FROM language_islands WHERE coastId = :coastId")
    suspend fun getIslandsForCoastOnce(coastId: Int): List<LanguageIsland>

    /** The cards on any of [islandIds] (chunked for SQLite's 999-parameter limit), read once rather than observed. */
    @Query("SELECT * FROM flashcards WHERE islandId IN (:islandIds)")
    suspend fun getCardsForIslandsChunk(islandIds: List<Int>): List<Flashcard>

    /** The cards on any of [islandIds], safely chunked to avoid SQLite's bind-parameter cap. */
    suspend fun getCardsForIslandsOnce(islandIds: List<Int>): List<Flashcard> =
        if (islandIds.isEmpty()) emptyList()
        else islandIds.chunked(DELETE_CHUNK).flatMap { getCardsForIslandsChunk(it) }

    /** The single card with [cardId], or null if it was deleted. */
    @Query("SELECT * FROM flashcards WHERE cardId = :cardId LIMIT 1")
    suspend fun getCardById(cardId: Int): Flashcard?

    /** Inserts [coasts] with their IDs, replacing rows with the same ID. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCoasts(coasts: List<Coast>)

    /** Inserts [islands] with their IDs, replacing rows with the same ID. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertIslands(islands: List<LanguageIsland>)

    /** Inserts [cards] with their IDs, replacing rows with the same ID. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFlashcards(cards: List<Flashcard>)

    /** Deletes every coast, island and card. */
    @Query("DELETE FROM coasts")
    suspend fun deleteAllCoasts()

    /** Replaces everything with [coasts], [islands] and [cards], keeping their IDs; all or nothing. */
    @Transaction
    suspend fun replaceAll(coasts: List<Coast>, islands: List<LanguageIsland>, cards: List<Flashcard>) {
        deleteAllCoasts() // Cascades to islands and cards.
        insertCoasts(coasts)
        insertIslands(islands)
        insertFlashcards(cards)
    }

    /** Puts back everything in [content] with its original IDs, parents before children. */
    @Transaction
    suspend fun restore(content: DeletedContent) {
        insertCoasts(content.coasts)
        insertIslands(content.islands)
        insertFlashcards(content.cards)
    }

    // --- Importing many cards at once ---
    /**
     * Saves [cards] on coast [coastId], each on the island it names (or [defaultIsland]), creating the islands
     * that don't exist yet. One transaction: all or nothing, far quicker than a write per card, and a single
     * change for everything that observes the tables.
     */
    @Transaction
    suspend fun insertImportedCards(coastId: Int, cards: List<ImportedCard>, defaultIsland: String): List<AddedCard> {
        val islands = HashMap<String, LanguageIsland>()
        getIslandsForCoastOnce(coastId).forEach { islands[it.name] = it }
        val created = HashSet<Int>()
        return cards.map { card ->
            val name = card.island ?: defaultIsland
            val island = islands[name] ?: LanguageIsland(coastId = coastId, name = name, emoji = emojiForCategory(name))
                .let { it.copy(islandId = insertIsland(it).toInt()) }
                .also {
                    islands[name] = it
                    created += it.islandId
                }
            val flashcard = Flashcard(
                islandId = island.islandId,
                nativeText = card.nativeText,
                targetText = card.targetText,
                alternatives = card.alternatives,
                note = card.note
            )
            // Like a card added on its own: the first card of a new island carries that island, so undoing can remove it.
            AddedCard(flashcard.copy(cardId = insertFlashcard(flashcard).toInt()), createdIsland = island.takeIf { created.remove(it.islandId) })
        }
    }

    /** Deletes the cards with [cardIds]: at most [DELETE_CHUNK] at a time, which is what one statement takes. */
    @Query("DELETE FROM flashcards WHERE cardId IN (:cardIds)")
    suspend fun deleteFlashcardsById(cardIds: List<Int>)

    /** Deletes the islands with [islandIds] that have no cards (left). */
    @Query(
        "DELETE FROM language_islands WHERE islandId IN (:islandIds) " +
            "AND NOT EXISTS (SELECT 1 FROM flashcards WHERE flashcards.islandId = language_islands.islandId)"
    )
    suspend fun deleteEmptyIslands(islandIds: List<Int>)

    /** Removes the cards with [cardIds], and those of [createdIslandIds] that are empty afterwards; all or nothing. */
    @Transaction
    suspend fun deleteImportedCards(cardIds: List<Int>, createdIslandIds: List<Int>) {
        cardIds.chunked(DELETE_CHUNK).forEach { deleteFlashcardsById(it) }
        createdIslandIds.chunked(DELETE_CHUNK).forEach { deleteEmptyIslands(it) }
    }

    /**
     * Replaces [replacedCardIds] with [newCards] on island [name] (with [emoji]) of coast [coastId] in one
     * atomic transaction, returning the new card IDs.
     */
    @Transaction
    suspend fun replaceIslandCards(
        coastId: Int,
        name: String,
        emoji: String,
        replacedCardIds: List<Int>,
        newCards: List<ProposedSentence>
    ): List<Int> {
        replacedCardIds.chunked(DELETE_CHUNK).forEach { deleteFlashcardsById(it) }
        val existing = getIslandByName(coastId, name)
        val island = existing ?: LanguageIsland(coastId = coastId, name = name, emoji = emoji)
            .let { it.copy(islandId = insertIsland(it).toInt()) }
        return newCards.map { sentence ->
            insertFlashcard(
                Flashcard(
                    islandId = island.islandId,
                    nativeText = sentence.nativeText,
                    targetText = sentence.targetText,
                    alternatives = sentence.alternatives,
                    note = sentence.note
                )
            ).toInt()
        }
    }
}

/** How many values one SQL statement is given at most; SQLite's own limit was 999 for a long time. */
private const val DELETE_CHUNK = 900

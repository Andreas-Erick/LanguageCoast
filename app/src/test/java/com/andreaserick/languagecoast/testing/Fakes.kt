package com.andreaserick.languagecoast.testing

import com.andreaserick.languagecoast.data.AddedCard
import com.andreaserick.languagecoast.data.Coast
import com.andreaserick.languagecoast.data.CoastSummary
import com.andreaserick.languagecoast.data.DeletedContent
import com.andreaserick.languagecoast.data.Flashcard
import com.andreaserick.languagecoast.data.FlashcardRepository
import com.andreaserick.languagecoast.data.IslandSummary
import com.andreaserick.languagecoast.data.LanguageIsland
import com.andreaserick.languagecoast.data.ReminderSettings
import com.andreaserick.languagecoast.data.SettingsDefaults
import com.andreaserick.languagecoast.data.SettingsRepository
import com.andreaserick.languagecoast.data.TranslationRequest
import com.andreaserick.languagecoast.data.TranslationResult
import com.andreaserick.languagecoast.data.Translator
import com.andreaserick.languagecoast.data.emojiForCategory
import com.andreaserick.languagecoast.notifications.ReminderScheduler
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** A clock fixed at noon UTC on 2026-10-09 (a Friday). */
val TEST_CLOCK: Clock = Clock.fixed(Instant.parse("2026-10-09T12:00:00Z"), ZoneOffset.UTC)

/** In-memory [FlashcardRepository]. */
class FakeFlashcardRepository : FlashcardRepository {
    val coasts = MutableStateFlow<List<Coast>>(emptyList())
    val islands = MutableStateFlow<List<LanguageIsland>>(emptyList())
    val cards = MutableStateFlow<List<Flashcard>>(emptyList())

    override fun observeCoastSummaries(studiedSince: Long): Flow<List<CoastSummary>> =
        combine(coasts, islands, cards) { coasts, islands, cards ->
            coasts.map { coast ->
                val coastIslands = islands.filter { it.coastId == coast.coastId }
                val islandIds = coastIslands.mapTo(HashSet()) { it.islandId }
                CoastSummary(
                    coast = coast,
                    islandCount = islandIds.size,
                    cardCount = cards.count { it.islandId in islandIds },
                    lastStudied = coastIslands.mapNotNull { it.lastStudied }.maxOrNull(),
                    islandsStudiedRecently = coastIslands.count { (it.lastStudied ?: Long.MIN_VALUE) >= studiedSince }
                )
            }
        }

    override fun observeCoasts(): Flow<List<Coast>> = coasts

    override fun observeCoast(coastId: Int): Flow<Coast?> =
        coasts.map { all -> all.firstOrNull { it.coastId == coastId } }

    override fun observeCoastForIsland(islandId: Int): Flow<Coast?> =
        combine(coasts, islands) { coasts, islands ->
            val island = islands.firstOrNull { it.islandId == islandId }
            coasts.firstOrNull { it.coastId == island?.coastId }
        }

    override suspend fun addCoast(language: String): Int =
        coasts.value.firstOrNull { it.language == language }?.coastId
            ?: seedCoast(nextId(coasts.value.map { it.coastId }), language).coastId

    override suspend fun deleteCoast(coast: Coast): DeletedContent {
        val coastIslands = islands.value.filter { it.coastId == coast.coastId }
        val ids = coastIslands.mapTo(HashSet()) { it.islandId }
        val content = DeletedContent(listOf(coast), coastIslands, cards.value.filter { it.islandId in ids })
        coasts.update { it - coast }
        islands.update { all -> all.filterNot { it.islandId in ids } }
        cards.update { all -> all.filterNot { it.islandId in ids } }
        return content
    }

    override fun observeIslands(coastId: Int): Flow<List<LanguageIsland>> =
        islands.map { all -> all.filter { it.coastId == coastId } }

    override fun observeIslandSummaries(coastId: Int): Flow<List<IslandSummary>> =
        combine(islands, cards) { islands, cards ->
            islands.filter { it.coastId == coastId }.map { island ->
                IslandSummary(island, cardCount = cards.count { it.islandId == island.islandId })
            }
        }

    override fun observeCards(islandId: Int): Flow<List<Flashcard>> =
        cards.map { all -> all.filter { it.islandId == islandId } }

    override suspend fun addFlashcard(
        coastId: Int,
        nativeText: String,
        targetText: String,
        category: String,
        emoji: String?
    ): AddedCard {
        val existing = islands.value.firstOrNull { it.coastId == coastId && it.name == category }
        val island = existing ?: LanguageIsland(
            islandId = nextId(islands.value.map { it.islandId }),
            coastId = coastId,
            name = category,
            emoji = emoji ?: emojiForCategory(category)
        ).also { new -> islands.update { it + new } }
        val card = Flashcard(
            cardId = nextId(cards.value.map { it.cardId }),
            islandId = island.islandId,
            nativeText = nativeText,
            targetText = targetText
        )
        cards.update { it + card }
        return AddedCard(card, createdIsland = island.takeIf { existing == null })
    }

    override suspend fun undoAdd(added: AddedCard) {
        cards.update { it - added.card }
        val island = added.createdIsland ?: return
        if (cards.value.none { it.islandId == island.islandId }) islands.update { all -> all.filterNot { it.islandId == island.islandId } }
    }

    override suspend fun deleteIsland(island: LanguageIsland): DeletedContent {
        val content = DeletedContent(islands = listOf(island), cards = cards.value.filter { it.islandId == island.islandId })
        islands.update { all -> all.filterNot { it.islandId == island.islandId } }
        cards.update { all -> all.filterNot { it.islandId == island.islandId } }
        return content
    }

    override suspend fun deleteFlashcard(card: Flashcard): DeletedContent {
        cards.update { it - card }
        return DeletedContent(cards = listOf(card))
    }

    override suspend fun restore(content: DeletedContent) {
        coasts.update { (it + content.coasts).sortedBy { coast -> coast.coastId } }
        islands.update { (it + content.islands).sortedBy { island -> island.islandId } }
        cards.update { (it + content.cards).sortedBy { card -> card.cardId } }
    }

    override suspend fun markIslandStudied(islandId: Int, time: Long) {
        islands.update { all -> all.map { if (it.islandId == islandId) it.copy(lastStudied = time) else it } }
    }

    /** Adds a coast with an explicit ID. */
    fun seedCoast(coastId: Int, language: String): Coast =
        Coast(coastId = coastId, language = language).also { new -> coasts.update { it + new } }

    /**
     * Seeds an island with cards whose native/target texts are "n<i>"/"t<i>".
     * The island goes on coast [coastId], which is created as a Spanish coast if it doesn't exist.
     */
    fun seed(islandId: Int, cardCount: Int, coastId: Int = 1) {
        if (coasts.value.none { it.coastId == coastId }) seedCoast(coastId, "Spanish")
        islands.update { it + LanguageIsland(islandId = islandId, coastId = coastId, name = "Island $islandId") }
        cards.update { existing ->
            val firstId = nextId(existing.map { it.cardId })
            existing + (1..cardCount).map { i ->
                Flashcard(cardId = firstId + i - 1, islandId = islandId, nativeText = "n$i", targetText = "t$i")
            }
        }
    }

    private fun nextId(ids: List<Int>) = (ids.maxOrNull() ?: 0) + 1
}

/** In-memory [SettingsRepository] that counts streak updates. */
class FakeSettingsRepository : SettingsRepository {
    override val nativeLanguage = MutableStateFlow(SettingsDefaults.NATIVE_LANGUAGE)
    override val activeCoastId = MutableStateFlow<Int?>(null)
    override val apiKey = MutableStateFlow("")
    override val geminiModel = MutableStateFlow(SettingsDefaults.GEMINI_MODEL)
    override val streakCount = MutableStateFlow(0)
    override val studyDays = MutableStateFlow<Set<LocalDate>>(emptySet())
    override val reminderSettings = MutableStateFlow(ReminderSettings())

    var studySessionsRecorded = 0
        private set
    var streakRefreshes = 0
        private set

    override suspend fun setNativeLanguage(language: String) { nativeLanguage.value = language }
    override suspend fun setActiveCoastId(coastId: Int) { activeCoastId.value = coastId }
    override suspend fun setApiKey(key: String) { apiKey.value = key }
    override suspend fun setGeminiModel(model: String) { geminiModel.value = model }
    override suspend fun setReminderSettings(reminder: ReminderSettings) { reminderSettings.value = reminder }

    override suspend fun recordStudySession() {
        studySessionsRecorded++
    }

    override suspend fun refreshStreak() {
        streakRefreshes++
    }
}

/** [ReminderScheduler] that only counts calls. */
class FakeReminderScheduler : ReminderScheduler {
    var reschedules = 0
        private set

    override suspend fun ensureScheduled() = Unit

    override suspend fun reschedule() {
        reschedules++
    }
}

/** [Translator] that returns a canned result and remembers the last request. */
class FakeTranslator(
    var result: TranslationResult = TranslationResult("Hola", "Greetings", isSuccess = true)
) : Translator {
    var lastRequest: TranslationRequest? = null
        private set

    override suspend fun translateAndCategorize(request: TranslationRequest): TranslationResult {
        lastRequest = request
        return result
    }
}

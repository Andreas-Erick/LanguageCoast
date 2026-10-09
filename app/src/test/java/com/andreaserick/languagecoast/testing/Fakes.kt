package com.andreaserick.languagecoast.testing

import com.andreaserick.languagecoast.data.Coast
import com.andreaserick.languagecoast.data.CoastSummary
import com.andreaserick.languagecoast.data.Flashcard
import com.andreaserick.languagecoast.data.FlashcardRepository
import com.andreaserick.languagecoast.data.LanguageIsland
import com.andreaserick.languagecoast.data.SettingsDefaults
import com.andreaserick.languagecoast.data.SettingsRepository
import com.andreaserick.languagecoast.data.TranslationRequest
import com.andreaserick.languagecoast.data.TranslationResult
import com.andreaserick.languagecoast.data.Translator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/** In-memory [FlashcardRepository]. */
class FakeFlashcardRepository : FlashcardRepository {
    val coasts = MutableStateFlow<List<Coast>>(emptyList())
    val islands = MutableStateFlow<List<LanguageIsland>>(emptyList())
    val cards = MutableStateFlow<List<Flashcard>>(emptyList())

    override fun observeCoastSummaries(): Flow<List<CoastSummary>> =
        combine(coasts, islands, cards) { coasts, islands, cards ->
            coasts.map { coast ->
                val islandIds = islands.filter { it.coastId == coast.coastId }.mapTo(HashSet()) { it.islandId }
                CoastSummary(coast, islandCount = islandIds.size, cardCount = cards.count { it.islandId in islandIds })
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
            ?: Coast(coastId = coasts.value.size + 1, language = language).also { new -> coasts.update { it + new } }.coastId

    override suspend fun deleteCoast(coast: Coast) {
        coasts.update { it - coast }
        islands.value.filter { it.coastId == coast.coastId }.forEach { deleteIsland(it) }
    }

    override fun observeIslands(coastId: Int): Flow<List<LanguageIsland>> =
        islands.map { all -> all.filter { it.coastId == coastId } }

    override fun observeCards(islandId: Int): Flow<List<Flashcard>> =
        cards.map { all -> all.filter { it.islandId == islandId } }

    override suspend fun addFlashcard(coastId: Int, nativeText: String, targetText: String, category: String) {
        val island = islands.value.firstOrNull { it.coastId == coastId && it.name == category }
            ?: LanguageIsland(islandId = islands.value.size + 1, coastId = coastId, name = category)
                .also { new -> islands.update { it + new } }
        cards.update { it + Flashcard(cardId = it.size + 1, islandId = island.islandId, nativeText = nativeText, targetText = targetText) }
    }

    override suspend fun deleteIsland(island: LanguageIsland) {
        islands.update { it - island }
        cards.update { all -> all.filterNot { it.islandId == island.islandId } }
    }

    override suspend fun deleteFlashcard(card: Flashcard) {
        cards.update { it - card }
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
            existing + (1..cardCount).map { i ->
                Flashcard(cardId = existing.size + i, islandId = islandId, nativeText = "n$i", targetText = "t$i")
            }
        }
    }
}

/** In-memory [SettingsRepository] that counts streak updates. */
class FakeSettingsRepository : SettingsRepository {
    override val nativeLanguage = MutableStateFlow(SettingsDefaults.NATIVE_LANGUAGE)
    override val activeCoastId = MutableStateFlow<Int?>(null)
    override val apiKey = MutableStateFlow("")
    override val geminiModel = MutableStateFlow(SettingsDefaults.GEMINI_MODEL)
    override val streakCount = MutableStateFlow(0)

    var studySessionsRecorded = 0
        private set
    var streakRefreshes = 0
        private set

    override suspend fun setNativeLanguage(language: String) { nativeLanguage.value = language }
    override suspend fun setActiveCoastId(coastId: Int) { activeCoastId.value = coastId }
    override suspend fun setApiKey(key: String) { apiKey.value = key }
    override suspend fun setGeminiModel(model: String) { geminiModel.value = model }

    override suspend fun recordStudySession() {
        studySessionsRecorded++
    }

    override suspend fun refreshStreak() {
        streakRefreshes++
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

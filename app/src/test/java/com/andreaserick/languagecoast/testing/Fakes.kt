package com.andreaserick.languagecoast.testing

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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/** In-memory [FlashcardRepository]. */
class FakeFlashcardRepository : FlashcardRepository {
    val islands = MutableStateFlow<List<LanguageIsland>>(emptyList())
    val cards = MutableStateFlow<List<Flashcard>>(emptyList())

    override fun observeIslands(): Flow<List<LanguageIsland>> = islands

    override fun observeCards(islandId: Int): Flow<List<Flashcard>> =
        cards.map { all -> all.filter { it.islandId == islandId } }

    override suspend fun addFlashcard(nativeText: String, targetText: String, category: String) {
        val island = islands.value.firstOrNull { it.name == category }
            ?: LanguageIsland(islandId = islands.value.size + 1, name = category).also { new -> islands.update { it + new } }
        cards.update { it + Flashcard(cardId = it.size + 1, islandId = island.islandId, nativeText = nativeText, targetText = targetText) }
    }

    override suspend fun deleteIsland(island: LanguageIsland) {
        islands.update { it - island }
        cards.update { all -> all.filterNot { it.islandId == island.islandId } }
    }

    override suspend fun deleteFlashcard(card: Flashcard) {
        cards.update { it - card }
    }

    /** Seeds an island with cards whose native/target texts are "n<i>"/"t<i>". */
    fun seed(islandId: Int, cardCount: Int) {
        islands.update { it + LanguageIsland(islandId = islandId, name = "Island $islandId") }
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
    override val targetLanguage = MutableStateFlow(SettingsDefaults.TARGET_LANGUAGE)
    override val apiKey = MutableStateFlow("")
    override val geminiModel = MutableStateFlow(SettingsDefaults.GEMINI_MODEL)
    override val streakCount = MutableStateFlow(0)

    var studySessionsRecorded = 0
        private set
    var streakRefreshes = 0
        private set

    override suspend fun setNativeLanguage(language: String) { nativeLanguage.value = language }
    override suspend fun setTargetLanguage(language: String) { targetLanguage.value = language }
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

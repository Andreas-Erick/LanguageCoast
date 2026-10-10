package com.andreaserick.languagecoast.ui.create

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.andreaserick.languagecoast.data.AddedCard
import com.andreaserick.languagecoast.data.Coast
import com.andreaserick.languagecoast.data.DEFAULT_CATEGORY
import com.andreaserick.languagecoast.data.FlashcardRepository
import com.andreaserick.languagecoast.data.Language
import com.andreaserick.languagecoast.data.RecentCard
import com.andreaserick.languagecoast.data.SettingsDefaults
import com.andreaserick.languagecoast.data.SettingsRepository
import com.andreaserick.languagecoast.data.TranslationEngine
import com.andreaserick.languagecoast.data.TranslationProvider
import com.andreaserick.languagecoast.data.TranslationRequest
import com.andreaserick.languagecoast.data.Translator
import com.andreaserick.languagecoast.data.availableCoastLanguages
import com.andreaserick.languagecoast.data.islandEmoji
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import java.time.Clock
import javax.inject.Inject

/** Category used for manual cards when the user leaves the category blank. */
const val DEFAULT_MANUAL_CATEGORY = DEFAULT_CATEGORY

/** How long the save feedback card stays on screen; long enough to read the translation and undo it. */
const val RESULT_VISIBLE_MILLIS = 8_000L

/** How many recently added cards the screen lists. */
const val RECENT_CARDS = 3

data class CreateUiState(
    val nativeSentence: String = "",
    val targetSentence: String = "", // Only used in manual mode
    val category: String = "",
    val isManualMode: Boolean = false,
    val isSaving: Boolean = false,
    val result: SaveResult? = null,
    val isLoadingCoasts: Boolean = true,
    val coasts: List<Coast> = emptyList(),
    /** The coast new cards are added to; null until the user has started a coast. */
    val selectedCoast: Coast? = null,
    /** Languages a new coast can be started for. */
    val availableLanguages: List<Language> = emptyList(),
    val nativeLanguage: String = SettingsDefaults.NATIVE_LANGUAGE,
    val streakCount: Int = 0,
    /** Whether any coast has cards at all, i.e. whether there is anything to review. */
    val hasCards: Boolean = false,
    /** Cards due across all coasts (see [isDue]). */
    val dueCount: Int = 0,
    /** How many coasts have due cards. */
    val dueCoastCount: Int = 0,
    /** The cards most recently added to the selected coast, newest first. */
    val recentCards: List<RecentCard> = emptyList()
) {
    /** True when the selected coast is in the user's native language, so there's nothing to translate. */
    val isSameLanguage: Boolean get() = selectedCoast?.language == nativeLanguage
}

/** Outcome of the last save, shown as a feedback card. */
sealed interface SaveResult {
    /** A card was saved; [added] identifies it so the save can be undone. */
    data class Saved(val added: AddedCard, val category: String, val emoji: String, val manual: Boolean) : SaveResult {
        val nativeText: String get() = added.card.nativeText
        val targetText: String get() = added.card.targetText
    }
    data class Error(val message: String) : SaveResult
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class CreateViewModel @Inject constructor(
    private val flashcards: FlashcardRepository,
    private val settings: SettingsRepository,
    private val translator: Translator,
    private val clock: Clock
) : ViewModel() {

    // Compose state rather than StateFlow so text fields update synchronously while typing.
    var uiState by mutableStateOf(CreateUiState())
        private set

    private var dismissJob: Job? = null

    init {
        viewModelScope.launch {
            combine(
                flashcards.observeCoasts(),
                settings.activeCoastId,
                settings.nativeLanguage
            ) { coasts, activeCoastId, nativeLanguage ->
                Triple(coasts, activeCoastId, nativeLanguage)
            }.collect { (coasts, activeCoastId, nativeLanguage) ->
                uiState = uiState.copy(
                    isLoadingCoasts = false,
                    coasts = coasts,
                    // Fall back to the first coast if none was picked yet or the active one was deleted.
                    selectedCoast = coasts.firstOrNull { it.coastId == activeCoastId } ?: coasts.firstOrNull(),
                    availableLanguages = availableCoastLanguages(coasts, nativeLanguage),
                    nativeLanguage = nativeLanguage
                )
            }
        }
        viewModelScope.launch {
            // Counted at the time the screen opens; reviewing changes the cards and with them the counts.
            combine(
                flashcards.observeCoastSummaries(studiedSince = 0, now = clock.millis()),
                settings.streakCount
            ) { coasts, streak -> coasts to streak }
                .collect { (coasts, streak) ->
                    uiState = uiState.copy(
                        streakCount = streak,
                        hasCards = coasts.any { it.cardCount > 0 },
                        dueCount = coasts.sumOf { it.dueCount },
                        dueCoastCount = coasts.count { it.dueCount > 0 }
                    )
                }
        }
        viewModelScope.launch {
            selectedCoastId()
                .flatMapLatest { coastId -> if (coastId == null) flowOf(emptyList()) else flashcards.observeRecentCards(coastId, RECENT_CARDS) }
                .collect { uiState = uiState.copy(recentCards = it) }
        }
        viewModelScope.launch { settings.refreshStreak() }
    }

    /** The coast new cards go to, as shown in the coast picker. */
    private fun selectedCoastId(): Flow<Int?> =
        combine(flashcards.observeCoasts(), settings.activeCoastId) { coasts, activeCoastId ->
            (coasts.firstOrNull { it.coastId == activeCoastId } ?: coasts.firstOrNull())?.coastId
        }.distinctUntilChanged()

    /**
     * Picks where to start reviewing: the island with the most due cards, on the selected coast if it
     * has any due, otherwise on the coast with the most. [onOpen] gets the island's ID and name.
     */
    fun startReview(onOpen: (islandId: Int, islandName: String) -> Unit) {
        viewModelScope.launch {
            val now = clock.millis()
            val coasts = flashcards.observeCoastSummaries(studiedSince = 0, now = now).first().filter { it.dueCount > 0 }
            val coast = coasts.firstOrNull { it.coast.coastId == uiState.selectedCoast?.coastId }
                ?: coasts.maxByOrNull { it.dueCount }
                ?: return@launch
            val island = flashcards.observeIslandSummaries(coast.coast.coastId, now).first()
                .filter { it.dueCount > 0 }
                .maxByOrNull { it.dueCount }
                ?: return@launch
            onOpen(island.island.islandId, island.island.name)
        }
    }

    /** Makes [coast] the one new cards are added to. */
    fun onCoastSelected(coast: Coast) {
        viewModelScope.launch { settings.setActiveCoastId(coast.coastId) }
    }

    /** Starts a coast for [language] and selects it. */
    fun addCoast(language: String) {
        viewModelScope.launch {
            val coastId = flashcards.addCoast(language)
            settings.setActiveCoastId(coastId)
        }
    }

    /** Updates the sentence typed in the native language. */
    fun onNativeSentenceChange(value: String) {
        uiState = uiState.copy(nativeSentence = value)
    }

    /** Updates the translation typed in manual mode. */
    fun onTargetSentenceChange(value: String) {
        uiState = uiState.copy(targetSentence = value)
    }

    /** Updates the category (island) typed for the card. */
    fun onCategoryChange(value: String) {
        uiState = uiState.copy(category = value)
    }

    /** Switches between translating automatically and typing the translation by hand. */
    fun onManualModeChange(manual: Boolean) {
        uiState = uiState.copy(isManualMode = manual)
    }

    /**
     * Translates the typed sentence (unless in manual mode) and saves it as a card, then shows the result.
     * The form is cleared only if the card was saved. Does nothing while a save is running.
     */
    fun save() {
        val state = uiState
        if (state.nativeSentence.isBlank() || state.isSaving) return
        dismissJob?.cancel()
        uiState = state.copy(isSaving = true, result = null)

        viewModelScope.launch {
            val result = try {
                createCard(state)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                SaveResult.Error(e.message ?: "Something went wrong.")
            }

            uiState = if (result is SaveResult.Saved) {
                uiState.copy(nativeSentence = "", targetSentence = "", category = "", isSaving = false, result = result)
            } else {
                uiState.copy(isSaving = false, result = result)
            }
            scheduleResultDismissal()
        }
    }

    /** Removes the card that was just saved (and its island, if that was created for it). */
    fun undoLastSave() {
        val saved = uiState.result as? SaveResult.Saved ?: return
        dismissJob?.cancel()
        uiState = uiState.copy(result = null)
        viewModelScope.launch { flashcards.undoAdd(saved.added) }
    }

    /** Hides the feedback card after [RESULT_VISIBLE_MILLIS], restarting the timer on every save. */
    private fun scheduleResultDismissal() {
        dismissJob?.cancel()
        dismissJob = viewModelScope.launch {
            delay(RESULT_VISIBLE_MILLIS)
            uiState = uiState.copy(result = null)
        }
    }

    /**
     * Saves a card for [state], translating it first unless in manual mode. Missing input, a missing API key
     * or a failed translation are returned as [SaveResult.Error] rather than thrown.
     */
    private suspend fun createCard(state: CreateUiState): SaveResult {
        val nativeText = state.nativeSentence.trim()
        val coast = state.selectedCoast ?: return SaveResult.Error("Start a coast first!")
        if (state.isSameLanguage) return SaveResult.Error(sameLanguageMessage(coast.language))

        if (state.isManualMode) {
            if (state.targetSentence.isBlank()) return SaveResult.Error("Translation cannot be empty in manual mode.")
            val category = state.category.trim().ifBlank { DEFAULT_MANUAL_CATEGORY }
            val added = flashcards.addFlashcard(coast.coastId, nativeText, state.targetSentence.trim(), category)
            return saved(added, coast.coastId, category, manual = true)
        }

        val engine = when (settings.translationProvider.first()) {
            TranslationProvider.OnDevice -> TranslationEngine.OnDevice
            TranslationProvider.Gemini -> {
                val apiKey = settings.apiKey.first()
                if (apiKey.isBlank()) return SaveResult.Error("Please enter a Gemini API key in Settings first!")
                TranslationEngine.Gemini(apiKey, settings.geminiModel.first())
            }
            TranslationProvider.OpenRouter -> {
                val apiKey = settings.openRouterKey.first()
                if (apiKey.isBlank()) return SaveResult.Error("Please enter an OpenRouter API key in Settings first!")
                val model = settings.openRouterModel.first().trim().ifBlank { SettingsDefaults.OPENROUTER_MODEL }
                TranslationEngine.OpenRouter(apiKey, model)
            }
        }

        val translation = translator.translateAndCategorize(
            TranslationRequest(
                engine = engine,
                nativeSentence = nativeText,
                nativeLanguage = settings.nativeLanguage.first(),
                targetLanguage = coast.language,
                userCategory = state.category.trim(),
                existingCategories = flashcards.observeIslands(coast.coastId).first().map { it.name }
            )
        )
        if (!translation.isSuccess) return SaveResult.Error(translation.errorMessage ?: "AI Translation Failed.")

        val added = flashcards.addFlashcard(
            coastId = coast.coastId,
            nativeText = nativeText,
            targetText = translation.translatedText,
            category = translation.finalCategory,
            emoji = translation.emoji,
            alternatives = translation.alternatives,
            note = translation.note
        )
        return saved(added, coast.coastId, translation.finalCategory, manual = false)
    }

    /** The feedback for a saved card, with the emoji of the island it went into. */
    private suspend fun saved(added: AddedCard, coastId: Int, category: String, manual: Boolean): SaveResult.Saved {
        // An existing island keeps its own emoji; look it up so the feedback card matches the island.
        val island = added.createdIsland
            ?: flashcards.observeIslands(coastId).first().first { it.islandId == added.card.islandId }
        return SaveResult.Saved(added, category, islandEmoji(island), manual)
    }
}

/** Explains why cards can't be added when a coast's language is the user's native language. */
fun sameLanguageMessage(language: String) =
    "$language is your native language, so there's nothing to translate. Pick another coast or change your native language in Settings."

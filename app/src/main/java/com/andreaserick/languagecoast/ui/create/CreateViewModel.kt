package com.andreaserick.languagecoast.ui.create

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.andreaserick.languagecoast.data.AddedCard
import com.andreaserick.languagecoast.data.Coast
import com.andreaserick.languagecoast.data.FlashcardRepository
import com.andreaserick.languagecoast.data.Language
import com.andreaserick.languagecoast.data.SettingsDefaults
import com.andreaserick.languagecoast.data.SettingsRepository
import com.andreaserick.languagecoast.data.TranslationRequest
import com.andreaserick.languagecoast.data.Translator
import com.andreaserick.languagecoast.data.availableCoastLanguages
import com.andreaserick.languagecoast.data.islandEmoji
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Category used for manual cards when the user leaves the category blank. */
const val DEFAULT_MANUAL_CATEGORY = "My Words"

/** How long the save feedback card stays on screen; long enough to read the translation and undo it. */
const val RESULT_VISIBLE_MILLIS = 8_000L

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
    val nativeLanguage: String = SettingsDefaults.NATIVE_LANGUAGE
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

@HiltViewModel
class CreateViewModel @Inject constructor(
    private val flashcards: FlashcardRepository,
    private val settings: SettingsRepository,
    private val translator: Translator
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
    }

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

    fun onNativeSentenceChange(value: String) {
        uiState = uiState.copy(nativeSentence = value)
    }

    fun onTargetSentenceChange(value: String) {
        uiState = uiState.copy(targetSentence = value)
    }

    fun onCategoryChange(value: String) {
        uiState = uiState.copy(category = value)
    }

    fun onManualModeChange(manual: Boolean) {
        uiState = uiState.copy(isManualMode = manual)
    }

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

        val apiKey = settings.apiKey.first()
        if (apiKey.isBlank()) return SaveResult.Error("Please enter an API Key in Settings first!")

        val translation = translator.translateAndCategorize(
            TranslationRequest(
                apiKey = apiKey,
                modelName = settings.geminiModel.first(),
                nativeSentence = nativeText,
                nativeLanguage = settings.nativeLanguage.first(),
                targetLanguage = coast.language,
                userCategory = state.category.trim(),
                existingCategories = flashcards.observeIslands(coast.coastId).first().map { it.name }
            )
        )
        if (!translation.isSuccess) return SaveResult.Error("AI Translation Failed.")

        val added = flashcards.addFlashcard(
            coast.coastId, nativeText, translation.translatedText, translation.finalCategory, translation.emoji
        )
        return saved(added, coast.coastId, translation.finalCategory, manual = false)
    }

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

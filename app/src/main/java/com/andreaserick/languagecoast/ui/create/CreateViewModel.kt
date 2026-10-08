package com.andreaserick.languagecoast.ui.create

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.andreaserick.languagecoast.data.FlashcardRepository
import com.andreaserick.languagecoast.data.SettingsRepository
import com.andreaserick.languagecoast.data.TranslationRequest
import com.andreaserick.languagecoast.data.Translator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Category used for manual cards when the user leaves the category blank. */
const val DEFAULT_MANUAL_CATEGORY = "My Words"

data class CreateUiState(
    val nativeSentence: String = "",
    val targetSentence: String = "", // Only used in manual mode
    val category: String = "",
    val isManualMode: Boolean = false,
    val isSaving: Boolean = false,
    val result: SaveResult? = null
)

/** Outcome of the last save, shown as a feedback card. */
sealed interface SaveResult {
    data class Saved(val category: String, val manual: Boolean) : SaveResult
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
        }
    }

    private suspend fun createCard(state: CreateUiState): SaveResult {
        val nativeText = state.nativeSentence.trim()

        if (state.isManualMode) {
            if (state.targetSentence.isBlank()) return SaveResult.Error("Translation cannot be empty in manual mode.")
            val category = state.category.trim().ifBlank { DEFAULT_MANUAL_CATEGORY }
            flashcards.addFlashcard(nativeText, state.targetSentence.trim(), category)
            return SaveResult.Saved(category, manual = true)
        }

        val apiKey = settings.apiKey.first()
        if (apiKey.isBlank()) return SaveResult.Error("Please enter an API Key in Settings first!")

        val translation = translator.translateAndCategorize(
            TranslationRequest(
                apiKey = apiKey,
                modelName = settings.geminiModel.first(),
                nativeSentence = nativeText,
                nativeLanguage = settings.nativeLanguage.first(),
                targetLanguage = settings.targetLanguage.first(),
                userCategory = state.category.trim(),
                existingCategories = flashcards.observeIslands().first().map { it.name }
            )
        )
        if (!translation.isSuccess) return SaveResult.Error("AI Translation Failed.")

        flashcards.addFlashcard(nativeText, translation.translatedText, translation.finalCategory)
        return SaveResult.Saved(translation.finalCategory, manual = false)
    }
}

package com.andreaserick.languagecoast.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.andreaserick.languagecoast.data.FlashcardRepository
import com.andreaserick.languagecoast.data.Language
import com.andreaserick.languagecoast.data.Languages
import com.andreaserick.languagecoast.data.SettingsDefaults
import com.andreaserick.languagecoast.data.SettingsRepository
import com.andreaserick.languagecoast.data.nativeLanguageOptions
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val nativeLanguage: String = SettingsDefaults.NATIVE_LANGUAGE,
    /** Languages offered as native language; excludes languages that have a coast. */
    val nativeLanguageOptions: List<Language> = Languages.ALL,
    val geminiModel: String = SettingsDefaults.GEMINI_MODEL,
    val savedApiKey: String = "",
    val apiKeySaved: Boolean = false
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val flashcards: FlashcardRepository
) : ViewModel() {

    private val apiKeySaved = MutableStateFlow(false)

    val uiState: StateFlow<SettingsUiState> = combine(
        settings.nativeLanguage,
        flashcards.observeCoasts(),
        settings.geminiModel,
        settings.apiKey,
        apiKeySaved
    ) { native, coasts, model, apiKey, saved ->
        SettingsUiState(native, nativeLanguageOptions(coasts), model, apiKey, saved)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    /** Changes the native language, unless the user already studies [language] on a coast. */
    fun setNativeLanguage(language: String) {
        viewModelScope.launch {
            if (nativeLanguageOptions(flashcards.observeCoasts().first()).none { it.name == language }) return@launch
            settings.setNativeLanguage(language)
        }
    }

    fun setGeminiModel(model: String) {
        viewModelScope.launch { settings.setGeminiModel(model) }
    }

    fun saveApiKey(key: String) {
        viewModelScope.launch {
            settings.setApiKey(key.trim())
            apiKeySaved.value = true
        }
    }

    /** Hides the "saved" confirmation once the user edits the key again. */
    fun onApiKeyEdited() {
        apiKeySaved.value = false
    }
}

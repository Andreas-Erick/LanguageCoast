package com.andreaserick.languagecoast.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.andreaserick.languagecoast.data.SettingsDefaults
import com.andreaserick.languagecoast.data.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val nativeLanguage: String = SettingsDefaults.NATIVE_LANGUAGE,
    val geminiModel: String = SettingsDefaults.GEMINI_MODEL,
    val savedApiKey: String = "",
    val apiKeySaved: Boolean = false
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settings: SettingsRepository
) : ViewModel() {

    private val apiKeySaved = MutableStateFlow(false)

    val uiState: StateFlow<SettingsUiState> = combine(
        settings.nativeLanguage,
        settings.geminiModel,
        settings.apiKey,
        apiKeySaved
    ) { native, model, apiKey, saved ->
        SettingsUiState(native, model, apiKey, saved)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    fun setNativeLanguage(language: String) {
        viewModelScope.launch { settings.setNativeLanguage(language) }
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

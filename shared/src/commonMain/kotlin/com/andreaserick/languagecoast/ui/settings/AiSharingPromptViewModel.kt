package com.andreaserick.languagecoast.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.andreaserick.languagecoast.data.SettingsRepository
import com.andreaserick.languagecoast.data.TranslationProvider
import com.andreaserick.languagecoast.data.providerAwaitingPermission
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Backs [AiSharingPrompt]: which provider has a key but was never allowed to receive text, and allowing it. */
class AiSharingPromptViewModel(private val settings: SettingsRepository) : ViewModel() {

    /** The picked provider when it has a key and no permission yet, otherwise null. */
    val awaiting: StateFlow<TranslationProvider?> =
        settings.providerAwaitingPermission().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Stores that text may be sent to [provider]; the prompt closes as [awaiting] turns null. */
    fun allow(provider: TranslationProvider) {
        viewModelScope.launch { settings.allowAiSharing(provider) }
    }
}

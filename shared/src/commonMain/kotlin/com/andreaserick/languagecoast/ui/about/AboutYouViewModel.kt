package com.andreaserick.languagecoast.ui.about

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.andreaserick.languagecoast.data.PersonalIslands
import com.andreaserick.languagecoast.data.Profile
import com.andreaserick.languagecoast.data.SettingsRepository
import com.andreaserick.languagecoast.data.TranslationProvider
import com.andreaserick.languagecoast.data.Wording
import com.andreaserick.languagecoast.ui.coast.engineProblem
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * What the user has told the app about themselves.
 *
 * @property profile The basics: name, job, where they live and come from.
 * @property answers The answered questions, by [com.andreaserick.languagecoast.data.PersonalQuestion.key].
 * @property provider Who would write islands from the answers, to say where they go.
 * @property engineProblem Why [provider] can't write islands yet (its key is missing, or it isn't allowed to be sent
 *     text), in words for the user; null when it can, and for on-device translation, which never writes.
 */
data class AboutYouUiState(
    val profile: Profile = Profile(),
    val answers: Map<String, String> = emptyMap(),
    val provider: TranslationProvider = TranslationProvider.OnDevice,
    /** How sentences about the user are worded where a language marks the speaker's gender. */
    val wording: Wording = Wording.Unspecified,
    val engineProblem: String? = null
) {
    /** How many of the questions and basics are filled in, and how many there are. */
    val answeredCount: Int get() = profile.fillers().size + answers.count { it.value.isNotBlank() }
    val questionCount: Int get() = Profile.PLACEHOLDERS.size + PersonalIslands.QUESTIONS.size
}

/** The "About you" screen: the basics and the questions behind the islands about the user, saved as they are typed. */
class AboutYouViewModel(private val settings: SettingsRepository) : ViewModel() {

    val uiState: StateFlow<AboutYouUiState> =
        combine(settings.profile, settings.personalAnswers, settings.translationProvider, settings.wording, settings.engineProblem(), ::AboutYouUiState)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AboutYouUiState())

    /** Stores one of the basics ([placeholder], see [Profile.PLACEHOLDERS]) as [value]. */
    fun setProfileField(placeholder: String, value: String) {
        viewModelScope.launch { settings.setProfileField(placeholder, value) }
    }

    /** Stores how sentences about the user are worded; picking the current one again clears it. */
    fun setWording(wording: Wording) {
        viewModelScope.launch { settings.setWording(if (wording == uiState.value.wording) Wording.Unspecified else wording) }
    }

    /** Stores the answer to the question with [key]; blank clears it. */
    fun setAnswer(key: String, value: String) {
        viewModelScope.launch { settings.setPersonalAnswer(key, value) }
    }
}

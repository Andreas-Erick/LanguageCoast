package com.andreaserick.languagecoast.ui.about

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.andreaserick.languagecoast.data.PersonalIslands
import com.andreaserick.languagecoast.data.Profile
import com.andreaserick.languagecoast.data.SettingsRepository
import com.andreaserick.languagecoast.data.TranslationProvider
import com.andreaserick.languagecoast.data.Wording
import com.andreaserick.languagecoast.ui.coast.engineProblem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
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
 * @property isLoading True until initial values have loaded from DataStore (prevents flashing 0 of 86 on launch).
 */
data class AboutYouUiState(
    val profile: Profile = Profile(),
    val answers: Map<String, String> = emptyMap(),
    val provider: TranslationProvider = TranslationProvider.OnDevice,
    /** How sentences about the user are worded where a language marks the speaker's gender. */
    val wording: Wording = Wording.Unspecified,
    val engineProblem: String? = null,
    val isLoading: Boolean = true
) {
    /** How many of the questions and basics are filled in, and how many there are. */
    val answeredCount: Int get() = profile.fillers().size + answers.count { it.value.isNotBlank() }
    val questionCount: Int get() = Profile.PLACEHOLDERS.size + PersonalIslands.QUESTIONS.size
}

/** The "About you" screen: the basics and the questions behind the islands about the user, saved with debounced writes. */
class AboutYouViewModel(private val settings: SettingsRepository) : ViewModel() {

    private val pendingWrites = HashMap<String, Job>()
    private val pendingProfile = HashMap<String, String>()
    private val pendingAnswers = HashMap<String, String>()

    val uiState: StateFlow<AboutYouUiState> =
        combine(settings.profile, settings.personalAnswers, settings.translationProvider, settings.wording, settings.engineProblem()) {
                profile, answers, provider, wording, problem ->
            AboutYouUiState(profile, answers, provider, wording, problem, isLoading = false)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AboutYouUiState())

    /** Stores one of the basics ([placeholder], see [Profile.PLACEHOLDERS]) as [value], debounced to avoid per-keystroke disk writes. */
    fun setProfileField(placeholder: String, value: String) {
        val key = "profile:$placeholder"
        pendingProfile[placeholder] = value
        pendingWrites.remove(key)?.cancel()
        pendingWrites[key] = viewModelScope.launch {
            delay(WRITE_DEBOUNCE_MILLIS)
            pendingProfile.remove(placeholder)
            settings.setProfileField(placeholder, value)
        }
    }

    /** Stores how sentences about the user are worded; picking the current one again clears it. */
    fun setWording(wording: Wording) {
        viewModelScope.launch { settings.setWording(if (wording == uiState.value.wording) Wording.Unspecified else wording) }
    }

    /** Stores the answer to the question with [key]; blank clears it, debounced to avoid per-keystroke disk writes. */
    fun setAnswer(key: String, value: String) {
        val writeKey = "answer:$key"
        pendingAnswers[key] = value
        pendingWrites.remove(writeKey)?.cancel()
        pendingWrites[writeKey] = viewModelScope.launch {
            delay(WRITE_DEBOUNCE_MILLIS)
            pendingAnswers.remove(key)
            settings.setPersonalAnswer(key, value)
        }
    }

    override fun onCleared() {
        val profileSnapshot = pendingProfile.toMap()
        val answerSnapshot = pendingAnswers.toMap()
        pendingProfile.clear()
        pendingAnswers.clear()
        if (profileSnapshot.isNotEmpty() || answerSnapshot.isNotEmpty()) {
            val flushJob = SupervisorJob()
            CoroutineScope(flushJob + Dispatchers.Default).launch {
                try {
                    profileSnapshot.forEach { (field, value) -> settings.setProfileField(field, value) }
                    answerSnapshot.forEach { (key, value) -> settings.setPersonalAnswer(key, value) }
                } finally {
                    flushJob.cancel()
                }
            }
        }
        super.onCleared()
    }

    private companion object {
        const val WRITE_DEBOUNCE_MILLIS = 300L
    }
}

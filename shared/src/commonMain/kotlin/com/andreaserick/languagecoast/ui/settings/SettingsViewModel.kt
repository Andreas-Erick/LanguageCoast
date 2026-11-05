package com.andreaserick.languagecoast.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.andreaserick.languagecoast.data.DeviceHardwareProfile
import com.andreaserick.languagecoast.data.FlashcardRepository
import com.andreaserick.languagecoast.data.Language
import com.andreaserick.languagecoast.data.Languages
import com.andreaserick.languagecoast.data.LocalAiCatalog
import com.andreaserick.languagecoast.data.LocalModelController
import com.andreaserick.languagecoast.data.MODEL_LIST_PROVIDERS
import com.andreaserick.languagecoast.data.ModelFitLevel
import com.andreaserick.languagecoast.data.ModelInstallStatus
import com.andreaserick.languagecoast.data.ModelLister
import com.andreaserick.languagecoast.data.ModelListing
import com.andreaserick.languagecoast.data.ModelRecommendation
import com.andreaserick.languagecoast.data.NoOpLocalModelController
import com.andreaserick.languagecoast.data.ReminderSettings
import com.andreaserick.languagecoast.data.SettingsDefaults
import com.andreaserick.languagecoast.data.SettingsRepository
import com.andreaserick.languagecoast.data.TranslationProvider
import com.andreaserick.languagecoast.data.Profile
import com.andreaserick.languagecoast.data.checkForNewModels
import com.andreaserick.languagecoast.data.nativeLanguageOptions
import com.andreaserick.languagecoast.data.recommendModelsForHardware
import com.andreaserick.languagecoast.notifications.ReminderScheduler
import com.andreaserick.languagecoast.ui.theme.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalTime

data class SettingsUiState(
    val nativeLanguage: String = SettingsDefaults.NATIVE_LANGUAGE,
    /** Languages offered as native language; excludes languages that have a coast. */
    val nativeLanguageOptions: List<Language> = Languages.ALL,
    val translationProvider: TranslationProvider = TranslationProvider.OnDevice,
    val geminiModel: String = SettingsDefaults.GEMINI_MODEL,
    /** The saved Gemini API key. */
    val savedApiKey: String = "",
    /** True right after the Gemini key was saved, until it is edited again. */
    val apiKeySaved: Boolean = false,
    val openRouterModel: String = SettingsDefaults.OPENROUTER_MODEL,
    val savedOpenRouterKey: String = "",
    /** True right after the OpenRouter key was saved, until it is edited again. */
    val openRouterSaved: Boolean = false,
    val openAiModel: String = SettingsDefaults.OPENAI_MODEL,
    val savedOpenAiKey: String = "",
    val openAiSaved: Boolean = false,
    val claudeModel: String = SettingsDefaults.CLAUDE_MODEL,
    val savedClaudeKey: String = "",
    val claudeSaved: Boolean = false,
    val groqModel: String = SettingsDefaults.GROQ_MODEL,
    val savedGroqKey: String = "",
    val groqSaved: Boolean = false,
    val deepSeekModel: String = SettingsDefaults.DEEPSEEK_MODEL,
    val savedDeepSeekKey: String = "",
    val deepSeekSaved: Boolean = false,
    val localAiModel: String = SettingsDefaults.LOCAL_AI_MODEL,
    val hardwareProfile: DeviceHardwareProfile? = null,
    val isScanningHardware: Boolean = false,
    val modelRecommendations: List<ModelRecommendation> = defaultRecommendations(),
    val modelInstallStates: Map<String, ModelInstallStatus> = emptyMap(),
    /** The saved Hugging Face token, which gated local models are downloaded with. */
    val savedHuggingFaceToken: String = "",
    val huggingFaceTokenSaved: Boolean = false,
    val reminder: ReminderSettings = ReminderSettings(),
    val themeMode: ThemeMode = ThemeMode.System,
    val hapticsEnabled: Boolean = true,
    val soundsEnabled: Boolean = true,
    /** Islands to study per week. */
    val weeklyGoal: Int = SettingsDefaults.WEEKLY_GOAL,
    /** The user's name, city and so on, for the blanks in starter sentences. */
    val profile: Profile = Profile(),
    /** A model whose download is waiting for the user to accept that it goes over mobile data. */
    val meteredWarningModelId: String? = null,
    /** The models offered for each cloud provider that has a list of them. */
    val modelOptions: Map<TranslationProvider, List<String>> = MODEL_LIST_PROVIDERS.associateWith(SettingsDefaults::models),
    val modelCheck: ModelCheckState = ModelCheckState()
)

/**
 * "Check for new models" in Settings.
 *
 * @property checking The provider being asked for its models right now, or null.
 * @property provider The provider the last check was for, whose model picker shows [message].
 * @property message What the last check found, or why it couldn't.
 */
data class ModelCheckState(
    val checking: TranslationProvider? = null,
    val provider: TranslationProvider? = null,
    val message: String? = null,
    val failed: Boolean = false
)

private fun defaultRecommendations(): List<ModelRecommendation> =
    LocalAiCatalog.MODELS.mapIndexed { index, model ->
        ModelRecommendation(
            model = model,
            fitLevel = if (index == 0) ModelFitLevel.BEST_MATCH else ModelFitLevel.GREAT_FIT,
            fitScore = 95 - index * 5,
            reason = model.summary,
            recommendedBackend = "GPU / CPU",
            hasEnoughStorage = true
        )
    }

/** Time suggested when switching reminders from "any time" to a fixed time. */
val DEFAULT_REMINDER_TIME = LocalTime(18, 0)

/** Downloads at least this large ask before going over mobile data. */
const val METERED_WARNING_MB = 300L

/** How long picking a model waits for the hardware scan. */
private const val HARDWARE_SCAN_WAIT_MILLIS = 5_000L

class SettingsViewModel(
    private val settings: SettingsRepository,
    private val flashcards: FlashcardRepository,
    private val reminderScheduler: ReminderScheduler,
    private val localModels: LocalModelController = NoOpLocalModelController(),
    private val modelLister: ModelLister = ModelLister { _, _ -> ModelListing.Failed("Checking for new models isn't available here.") }
) : ViewModel() {

    private val apiKeySaved = MutableStateFlow(false)
    private val openRouterSaved = MutableStateFlow(false)
    private val openAiSaved = MutableStateFlow(false)
    private val claudeSaved = MutableStateFlow(false)
    private val groqSaved = MutableStateFlow(false)
    private val deepSeekSaved = MutableStateFlow(false)
    private val huggingFaceTokenSaved = MutableStateFlow(false)
    private val meteredWarning = MutableStateFlow<String?>(null)
    private val modelCheck = MutableStateFlow(ModelCheckState())

    /** The model lists of the cloud providers, and how checking them for new models is going. */
    private data class ModelLists(val options: Map<TranslationProvider, List<String>>, val check: ModelCheckState)

    private val modelLists = combine(
        combine(MODEL_LIST_PROVIDERS.map { provider -> settings.modelOptions(provider).map { provider to it } }) { it.toMap() },
        modelCheck,
        ::ModelLists
    )

    /** The provider, the reminder, the profile and the model lists: what doesn't belong to one provider. */
    private data class General(val provider: TranslationProvider, val reminder: ReminderSettings, val profile: Profile, val models: ModelLists)

    /** The look-and-feel and goal preferences. */
    private data class Preferences(
        val themeMode: ThemeMode,
        val hapticsEnabled: Boolean,
        val soundsEnabled: Boolean,
        val weeklyGoal: Int,
        val meteredWarningModelId: String?
    )

    private val preferences = combine(
        settings.themeMode,
        settings.hapticsEnabled,
        settings.soundsEnabled,
        settings.weeklyGoal,
        meteredWarning
    ) { theme, haptics, sounds, goal, metered -> Preferences(ThemeMode.fromKey(theme), haptics, sounds, goal, metered) }

    private data class CloudProviderState(
        val geminiModel: String,
        val geminiKey: String,
        val geminiSaved: Boolean,
        val openRouterModel: String,
        val openRouterKey: String,
        val openRouterSaved: Boolean,
        val openAiModel: String,
        val openAiKey: String,
        val openAiSaved: Boolean,
        val claudeModel: String,
        val claudeKey: String,
        val claudeSaved: Boolean,
        val groqModel: String,
        val groqKey: String,
        val groqSaved: Boolean,
        val deepSeekModel: String,
        val deepSeekKey: String,
        val deepSeekSaved: Boolean
    )

    private data class LocalAiState(
        val activeModelId: String,
        val hardwareProfile: DeviceHardwareProfile?,
        val isScanning: Boolean,
        val installStates: Map<String, ModelInstallStatus>,
        val huggingFaceToken: Pair<String, Boolean>
    )

    private val cloudState = combine(
        combine(
            combine(settings.geminiModel, settings.apiKey, apiKeySaved, ::Triple),
            combine(settings.openRouterModel, settings.openRouterKey, openRouterSaved, ::Triple),
            combine(settings.openAiModel, settings.openAiKey, openAiSaved, ::Triple),
            ::Triple
        ),
        combine(
            combine(settings.claudeModel, settings.claudeKey, claudeSaved, ::Triple),
            combine(settings.groqModel, settings.groqKey, groqSaved, ::Triple),
            combine(settings.deepSeekModel, settings.deepSeekKey, deepSeekSaved, ::Triple),
            ::Triple
        )
    ) { (gemini, openRouter, openAi), (claude, groq, deepSeek) ->
        CloudProviderState(
            geminiModel = gemini.first,
            geminiKey = gemini.second,
            geminiSaved = gemini.third,
            openRouterModel = openRouter.first,
            openRouterKey = openRouter.second,
            openRouterSaved = openRouter.third,
            openAiModel = openAi.first,
            openAiKey = openAi.second,
            openAiSaved = openAi.third,
            claudeModel = claude.first,
            claudeKey = claude.second,
            claudeSaved = claude.third,
            groqModel = groq.first,
            groqKey = groq.second,
            groqSaved = groq.third,
            deepSeekModel = deepSeek.first,
            deepSeekKey = deepSeek.second,
            deepSeekSaved = deepSeek.third
        )
    }

    private val localAiState = combine(
        settings.localAiModel,
        localModels.hardwareProfile,
        localModels.isScanning,
        localModels.installStates,
        combine(settings.huggingFaceToken, huggingFaceTokenSaved, ::Pair),
        ::LocalAiState
    )

    val uiState: StateFlow<SettingsUiState> = combine(
        combine(settings.nativeLanguage, flashcards.observeCoasts(), ::Pair),
        cloudState,
        localAiState,
        combine(settings.translationProvider, settings.reminderSettings, settings.profile, modelLists, ::General),
        preferences
    ) { (native, coasts), cloud, local, (provider, reminder, profile, models), prefs ->
        val recommendations = local.hardwareProfile?.let(::recommendModelsForHardware) ?: defaultRecommendations()
        SettingsUiState(
            nativeLanguage = native,
            nativeLanguageOptions = nativeLanguageOptions(coasts),
            translationProvider = provider,
            geminiModel = cloud.geminiModel,
            savedApiKey = cloud.geminiKey,
            apiKeySaved = cloud.geminiSaved,
            openRouterModel = cloud.openRouterModel,
            savedOpenRouterKey = cloud.openRouterKey,
            openRouterSaved = cloud.openRouterSaved,
            openAiModel = cloud.openAiModel,
            savedOpenAiKey = cloud.openAiKey,
            openAiSaved = cloud.openAiSaved,
            claudeModel = cloud.claudeModel,
            savedClaudeKey = cloud.claudeKey,
            claudeSaved = cloud.claudeSaved,
            groqModel = cloud.groqModel,
            savedGroqKey = cloud.groqKey,
            groqSaved = cloud.groqSaved,
            deepSeekModel = cloud.deepSeekModel,
            savedDeepSeekKey = cloud.deepSeekKey,
            deepSeekSaved = cloud.deepSeekSaved,
            localAiModel = local.activeModelId,
            hardwareProfile = local.hardwareProfile,
            isScanningHardware = local.isScanning,
            modelRecommendations = recommendations,
            modelInstallStates = local.installStates,
            savedHuggingFaceToken = local.huggingFaceToken.first,
            huggingFaceTokenSaved = local.huggingFaceToken.second,
            reminder = reminder,
            themeMode = prefs.themeMode,
            hapticsEnabled = prefs.hapticsEnabled,
            soundsEnabled = prefs.soundsEnabled,
            weeklyGoal = prefs.weeklyGoal,
            meteredWarningModelId = prefs.meteredWarningModelId,
            profile = profile,
            modelOptions = models.options,
            modelCheck = models.check
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    /** Changes the native language, unless the user already studies [language] on a coast. */
    fun setNativeLanguage(language: String) {
        viewModelScope.launch {
            if (nativeLanguageOptions(flashcards.observeCoasts().first()).none { it.name == language }) return@launch
            settings.setNativeLanguage(language)
        }
    }

    /**
     * Asks the provider picked for translations which models it offers now, adds the new ones to its list and
     * removes the ones it no longer has. Does nothing while a check is running. What came of it arrives in
     * [SettingsUiState.modelCheck].
     */
    fun checkForNewModels() {
        if (modelCheck.value.checking != null) return
        viewModelScope.launch {
            val provider = settings.translationProvider.first()
            modelCheck.value = ModelCheckState(checking = provider)
            val outcome = checkForNewModels(provider, settings, modelLister)
            modelCheck.value = ModelCheckState(provider = provider, message = outcome.message, failed = outcome.failed)
        }
    }

    /** Changes where translations come from. Picking Local AI also picks the model that fits the phone best. */
    fun setTranslationProvider(provider: TranslationProvider) {
        viewModelScope.launch {
            settings.setTranslationProvider(provider)
            if (provider == TranslationProvider.LocalAi) pickBestLocalModel()
        }
    }

    /**
     * Makes the model that fits the phone best the active one, unless an installed model is already
     * active. Prefers an installed model that fits over one that would need downloading.
     */
    private suspend fun pickBestLocalModel() {
        val current = settings.localAiModel.first()
        if (localModels.resolveInstalledModelPath(current) != null) return
        if (localModels.hardwareProfile.value == null) localModels.scanHardware()
        val profile = withTimeoutOrNull(HARDWARE_SCAN_WAIT_MILLIS) { localModels.hardwareProfile.filterNotNull().first() } ?: return
        val fitting = recommendModelsForHardware(profile).filter { it.fitLevel != ModelFitLevel.NOT_RECOMMENDED }
        val pick = fitting.firstOrNull { localModels.resolveInstalledModelPath(it.model.id) != null }
            ?: fitting.firstOrNull { it.fitLevel == ModelFitLevel.BEST_MATCH }
            ?: return
        settings.setLocalAiModel(pick.model.id)
    }

    /** Sets whether the app is dark, light or follows the phone. */
    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { settings.setThemeMode(mode.key) }
    }

    fun setHapticsEnabled(enabled: Boolean) {
        viewModelScope.launch { settings.setHapticsEnabled(enabled) }
    }

    fun setSoundsEnabled(enabled: Boolean) {
        viewModelScope.launch { settings.setSoundsEnabled(enabled) }
    }

    /** Sets how many islands the user aims to study each week. */
    fun setWeeklyGoal(islands: Int) {
        viewModelScope.launch { settings.setWeeklyGoal(islands) }
    }

    /** Stores one of the profile's blanks ([placeholder], see [Profile.PLACEHOLDERS]) as [value]. */
    fun setProfileField(placeholder: String, value: String) {
        viewModelScope.launch { settings.setProfileField(placeholder, value) }
    }

    /** Scans or re-scans the user's phone hardware to recommend compatible local AI models. */
    fun scanHardware() {
        localModels.scanHardware()
    }

    /**
     * Downloads and installs a local `.litertlm` model and sets it as the active Local AI model. Over mobile
     * data a large download first asks (see [SettingsUiState.meteredWarningModelId]), unless [allowMetered].
     */
    fun installLocalModel(modelId: String, allowMetered: Boolean = false) {
        val size = LocalAiCatalog.byId(modelId)?.sizeMb ?: 0L
        if (!allowMetered && size >= METERED_WARNING_MB && localModels.isOnMeteredNetwork()) {
            meteredWarning.value = modelId
            return
        }
        meteredWarning.value = null
        viewModelScope.launch {
            localModels.installModel(modelId, settings.huggingFaceToken.first().trim().ifEmpty { null })
            settings.setLocalAiModel(modelId)
        }
    }

    /** Closes the mobile-data warning without downloading. */
    fun dismissMeteredWarning() {
        meteredWarning.value = null
    }

    /** Saves the Hugging Face token that gated local models are downloaded with. */
    fun saveHuggingFaceToken(token: String) {
        viewModelScope.launch {
            settings.setHuggingFaceToken(token.trim())
            huggingFaceTokenSaved.value = true
        }
    }

    fun onHuggingFaceTokenEdited() {
        huggingFaceTokenSaved.value = false
    }

    /** Cancels an active download for [modelId]. */
    fun cancelLocalModelInstall(modelId: String) {
        localModels.cancelInstall(modelId)
    }

    /** Deletes the installed `.litertlm` file for [modelId] to free storage space. */
    fun deleteLocalModel(modelId: String) {
        localModels.deleteModel(modelId)
    }

    /** Selects [modelId] as the active Local AI model used when translating. */
    fun setLocalAiModel(modelId: String) {
        viewModelScope.launch { settings.setLocalAiModel(modelId) }
    }

    /** Saves the OpenRouter model ID as typed; a blank one means [SettingsDefaults.OPENROUTER_MODEL] when translating. */
    fun setOpenRouterModel(model: String) {
        viewModelScope.launch { settings.setOpenRouterModel(model) }
    }

    /** Saves the OpenRouter API key and shows the "saved" confirmation. */
    fun saveOpenRouterKey(key: String) {
        viewModelScope.launch {
            settings.setOpenRouterKey(key.trim())
            openRouterSaved.value = true
        }
    }

    /** Hides the "saved" confirmation once the user edits the OpenRouter key again. */
    fun onOpenRouterKeyEdited() {
        openRouterSaved.value = false
    }

    /** Changes the Gemini model used for translations. */
    fun setGeminiModel(model: String) {
        viewModelScope.launch { settings.setGeminiModel(model) }
    }

    /** Saves the Gemini API key and shows the "saved" confirmation. */
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

    /** Changes the OpenAI model used for translations. */
    fun setOpenAiModel(model: String) {
        viewModelScope.launch { settings.setOpenAiModel(model) }
    }

    /** Saves the OpenAI API key. */
    fun saveOpenAiKey(key: String) {
        viewModelScope.launch {
            settings.setOpenAiKey(key.trim())
            openAiSaved.value = true
        }
    }

    fun onOpenAiKeyEdited() {
        openAiSaved.value = false
    }

    /** Changes the Anthropic Claude model used for translations. */
    fun setClaudeModel(model: String) {
        viewModelScope.launch { settings.setClaudeModel(model) }
    }

    /** Saves the Anthropic Claude API key. */
    fun saveClaudeKey(key: String) {
        viewModelScope.launch {
            settings.setClaudeKey(key.trim())
            claudeSaved.value = true
        }
    }

    fun onClaudeKeyEdited() {
        claudeSaved.value = false
    }

    /** Changes the Groq model used for translations. */
    fun setGroqModel(model: String) {
        viewModelScope.launch { settings.setGroqModel(model) }
    }

    /** Saves the Groq API key. */
    fun saveGroqKey(key: String) {
        viewModelScope.launch {
            settings.setGroqKey(key.trim())
            groqSaved.value = true
        }
    }

    fun onGroqKeyEdited() {
        groqSaved.value = false
    }

    /** Changes the DeepSeek model used for translations. */
    fun setDeepSeekModel(model: String) {
        viewModelScope.launch { settings.setDeepSeekModel(model) }
    }

    /** Saves the DeepSeek API key. */
    fun saveDeepSeekKey(key: String) {
        viewModelScope.launch {
            settings.setDeepSeekKey(key.trim())
            deepSeekSaved.value = true
        }
    }

    fun onDeepSeekKeyEdited() {
        deepSeekSaved.value = false
    }

    /** Turns the daily study reminder on or off. */
    fun setRemindersEnabled(enabled: Boolean) = updateReminder { it.copy(enabled = enabled) }

    /** Sets a fixed reminder time, or null for a random time during the day. */
    fun setReminderTime(time: LocalTime?) = updateReminder { it.copy(time = time) }

    /** Saves the reminder settings changed by [transform] and reschedules the reminder to match. */
    private fun updateReminder(transform: (ReminderSettings) -> ReminderSettings) {
        viewModelScope.launch {
            settings.setReminderSettings(transform(settings.reminderSettings.first()))
            reminderScheduler.reschedule()
        }
    }
}

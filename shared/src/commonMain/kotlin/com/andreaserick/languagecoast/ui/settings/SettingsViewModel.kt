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
import com.andreaserick.languagecoast.data.Pace
import com.andreaserick.languagecoast.data.SunLocation
import com.andreaserick.languagecoast.data.TranslationProvider
import com.andreaserick.languagecoast.data.Profile
import com.andreaserick.languagecoast.data.checkForNewModels
import com.andreaserick.languagecoast.data.nativeLanguageOptions
import com.andreaserick.languagecoast.data.recommendModelsForHardware
import com.andreaserick.languagecoast.data.sharingNotAllowedMessage
import com.andreaserick.languagecoast.notifications.ReminderScheduler
import com.andreaserick.languagecoast.ui.theme.ThemeMode
import kotlinx.coroutines.Job
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
    val themeMode: ThemeMode = ThemeMode.Sun,
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
    val modelCheck: ModelCheckState = ModelCheckState(),
    /** The cloud providers the user allowed text to be sent to, by [TranslationProvider.name]. */
    val aiSharingAllowed: Set<String> = emptySet(),
    val keyCheck: KeyCheckState = KeyCheckState()
) {
    /** The saved API key of cloud [provider], or "" if it has none. */
    fun savedKey(provider: TranslationProvider): String = when (provider) {
        TranslationProvider.Gemini -> savedApiKey
        TranslationProvider.OpenAI -> savedOpenAiKey
        TranslationProvider.Claude -> savedClaudeKey
        TranslationProvider.Groq -> savedGroqKey
        TranslationProvider.DeepSeek -> savedDeepSeekKey
        TranslationProvider.OpenRouter -> savedOpenRouterKey
        TranslationProvider.OnDevice, TranslationProvider.LocalAi -> ""
    }

    /** Whether [provider]'s key was saved in this visit and not edited since. */
    fun keyJustSaved(provider: TranslationProvider): Boolean = when (provider) {
        TranslationProvider.Gemini -> apiKeySaved
        TranslationProvider.OpenAI -> openAiSaved
        TranslationProvider.Claude -> claudeSaved
        TranslationProvider.Groq -> groqSaved
        TranslationProvider.DeepSeek -> deepSeekSaved
        TranslationProvider.OpenRouter -> openRouterSaved
        TranslationProvider.OnDevice, TranslationProvider.LocalAi -> false
    }

    /** Whether the user allowed text to be sent to [provider]. What runs on the phone needs no permission. */
    fun sharingAllowed(provider: TranslationProvider): Boolean = !provider.isCloud || provider.name in aiSharingAllowed
}

/**
 * "Check for new models" in Settings.
 *
 * @property checking The provider being asked for its models right now, or null.
 * @property provider The provider the last check was for, whose model picker shows [message].
 * @property message What the last check found, or why it couldn't.
 * @property needsPermission Whether [message] asks to allow sending to [provider] first; it goes once that is allowed.
 */
data class ModelCheckState(
    val checking: TranslationProvider? = null,
    val provider: TranslationProvider? = null,
    val message: String? = null,
    val failed: Boolean = false,
    val needsPermission: Boolean = false
)

/**
 * Whether the key just saved for a provider works: saving one asks the provider, so that a mistyped key
 * shows here and not later, in the middle of a translation.
 *
 * @property provider The provider whose key was checked last, or is being checked.
 * @property checking Whether the provider is being asked right now.
 * @property message What came of it, once it is known: the key works, it was refused, or it couldn't be checked.
 */
data class KeyCheckState(
    val provider: TranslationProvider? = null,
    val checking: Boolean = false,
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
            recommendedBackend = "CPU",
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
    private val keyCheck = MutableStateFlow(KeyCheckState())

    /** The check of the key saved last; a newer save or a removal ends it. */
    private var keyCheckJob: Job? = null

    /** The model lists of the cloud providers, and how checking them for new models and checking a saved key are going. */
    private data class ModelLists(val options: Map<TranslationProvider, List<String>>, val check: ModelCheckState, val keyCheck: KeyCheckState)

    private val modelLists = combine(
        combine(MODEL_LIST_PROVIDERS.map { provider -> settings.modelOptions(provider).map { provider to it } }) { it.toMap() },
        modelCheck,
        keyCheck,
        ::ModelLists
    )

    /** The provider, the reminder, the profile, the model lists and the permissions: what doesn't belong to one provider. */
    private data class General(
        val provider: TranslationProvider,
        val reminder: ReminderSettings,
        val profile: Profile,
        val models: ModelLists,
        val sharingAllowed: Set<String>
    )

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
            combine(settings.model(TranslationProvider.Gemini), settings.key(TranslationProvider.Gemini), apiKeySaved, ::Triple),
            combine(settings.model(TranslationProvider.OpenRouter), settings.key(TranslationProvider.OpenRouter), openRouterSaved, ::Triple),
            combine(settings.model(TranslationProvider.OpenAI), settings.key(TranslationProvider.OpenAI), openAiSaved, ::Triple),
            ::Triple
        ),
        combine(
            combine(settings.model(TranslationProvider.Claude), settings.key(TranslationProvider.Claude), claudeSaved, ::Triple),
            combine(settings.model(TranslationProvider.Groq), settings.key(TranslationProvider.Groq), groqSaved, ::Triple),
            combine(settings.model(TranslationProvider.DeepSeek), settings.key(TranslationProvider.DeepSeek), deepSeekSaved, ::Triple),
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
        combine(settings.translationProvider, settings.reminderSettings, settings.profile, modelLists, settings.aiSharingAllowed, ::General),
        preferences
    ) { (native, coasts), cloud, local, (provider, reminder, profile, models, sharingAllowed), prefs ->
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
            // "Allow it first" has nothing left to say once the provider is allowed.
            modelCheck = models.check.takeUnless { it.needsPermission && it.provider?.name.orEmpty() in sharingAllowed } ?: ModelCheckState(),
            aiSharingAllowed = sharingAllowed,
            keyCheck = models.keyCheck
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
     * [SettingsUiState.modelCheck]. A provider the user hasn't allowed sending to is not asked: the request
     * would carry the saved key.
     */
    fun checkForNewModels() {
        if (modelCheck.value.checking != null) return
        viewModelScope.launch {
            val provider = settings.translationProvider.first()
            // A key saved before the app asked for permission: until the user allows it the provider gets nothing, not even the key.
            // Without a key the check itself says to save one first, and saving is the step that asks for the permission.
            if (settings.key(provider).first().isNotBlank() && provider.name !in settings.aiSharingAllowed.first()) {
                modelCheck.value =
                    ModelCheckState(provider = provider, message = sharingNotAllowedMessage(provider), failed = true, needsPermission = true)
                return@launch
            }
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

    /** Sets whether the app follows the sun, follows the phone, or is dark or light. */
    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { settings.setThemeMode(mode.key) }
    }

    /** Whether Follow the sun uses the phone's location, and the location last read (null until one is). */
    val sunLocation: StateFlow<Pair<Boolean, SunLocation?>> =
        combine(settings.sunUsesLocation, settings.sunLocation) { uses, location -> uses to location }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false to null)

    /** Switches the use of the phone's location for Follow the sun on or off; off forgets the location. */
    fun setSunUsesLocation(enabled: Boolean) {
        viewModelScope.launch { settings.setSunUsesLocation(enabled) }
    }

    /** How much new a day's session brings; null until it is read, so a Fast or Sprint pick never shows Steady first. */
    val pace: StateFlow<Pace?> = settings.pace.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Stores the pace picked in Settings. */
    fun setPace(pace: Pace) {
        viewModelScope.launch { settings.setPace(pace) }
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
     * A download the user didn't agree to have on mobile data never uses it: one started on Wi-Fi waits for
     * Wi-Fi to come back when it drops.
     */
    fun installLocalModel(modelId: String, allowMetered: Boolean = false) {
        val size = LocalAiCatalog.byId(modelId)?.sizeMb ?: 0L
        if (!allowMetered && size >= METERED_WARNING_MB && localModels.isOnMeteredNetwork()) {
            meteredWarning.value = modelId
            return
        }
        meteredWarning.value = null
        viewModelScope.launch {
            localModels.installModel(
                modelId,
                settings.huggingFaceToken.first().trim().ifEmpty { null },
                // A download too small to ask about may go over any connection.
                allowMetered = allowMetered || size < METERED_WARNING_MB
            )
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
            settings.setHuggingFaceToken(withoutWhitespace(token))
            huggingFaceTokenSaved.value = true
        }
    }

    /** Takes the Hugging Face token off the phone. */
    fun removeHuggingFaceToken() {
        viewModelScope.launch {
            settings.setHuggingFaceToken("")
            huggingFaceTokenSaved.value = false
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
        viewModelScope.launch { settings.setModel(TranslationProvider.OpenRouter, model) }
    }

    /**
     * Saves [key] as cloud [provider]'s API key, shows the "saved" confirmation and asks the provider whether
     * the key works (see [SettingsUiState.keyCheck]). The key is stored even if that check fails: it may only
     * be the connection. A provider the user hasn't allowed sending to is not asked, since asking sends the key.
     *
     * @param allowSharing The user has just allowed text to be sent to [provider]; stored along with the key.
     */
    fun saveKey(provider: TranslationProvider, key: String, allowSharing: Boolean = false) {
        val cleaned = withoutWhitespace(key)
        viewModelScope.launch {
            // The permission goes first: a key stored without it would make the app ask for it once more.
            if (allowSharing) settings.allowAiSharing(provider)
            storeKey(provider, cleaned)
            savedFlag(provider)?.value = true
            if (provider.name in settings.aiSharingAllowed.first()) checkKey(provider, cleaned) else forgetKeyCheck(provider)
        }
    }

    /** Takes [provider]'s key off the phone, and with it the permission to send text there. */
    fun removeKey(provider: TranslationProvider) {
        forgetKeyCheck(provider)
        // "Allow sending first" under the model list is advice for a saved key; without one it would point at nothing.
        if (modelCheck.value.needsPermission && modelCheck.value.provider == provider) modelCheck.value = ModelCheckState()
        viewModelScope.launch {
            settings.removeApiKey(provider)
            savedFlag(provider)?.value = false
        }
    }

    /** Stores that the user allowed text to be sent to [provider], whose key is saved already. */
    fun allowSharing(provider: TranslationProvider) {
        viewModelScope.launch { settings.allowAiSharing(provider) }
    }

    /** Hides the "saved" confirmation and the result of the key's check once the user edits [provider]'s key again. */
    fun onKeyEdited(provider: TranslationProvider) {
        savedFlag(provider)?.value = false
        forgetKeyCheck(provider)
    }

    /**
     * Asks [provider] whether it accepts [key], by listing its models with it. OpenRouter has no such list,
     * so its key shows only when translating.
     */
    private fun checkKey(provider: TranslationProvider, key: String) {
        keyCheckJob?.cancel()
        if (provider !in MODEL_LIST_PROVIDERS || key.isEmpty()) {
            keyCheck.value = KeyCheckState()
            return
        }
        keyCheck.value = KeyCheckState(provider = provider, checking = true)
        keyCheckJob = viewModelScope.launch {
            keyCheck.value = when (val listing = modelLister.list(provider, key)) {
                is ModelListing.Found -> KeyCheckState(provider = provider, message = "${provider.label} accepted the key.")
                // The lister's own words: the key was refused, or the provider couldn't be reached.
                is ModelListing.Failed -> KeyCheckState(provider = provider, message = listing.message, failed = true)
            }
        }
    }

    /** Drops what is shown about [provider]'s key check, and stops one that is still running. */
    private fun forgetKeyCheck(provider: TranslationProvider) {
        if (keyCheck.value.provider != provider) return
        keyCheckJob?.cancel()
        keyCheck.value = KeyCheckState()
    }

    private suspend fun storeKey(provider: TranslationProvider, key: String) = when (provider) {
        TranslationProvider.Gemini -> settings.setKey(TranslationProvider.Gemini, key)
        TranslationProvider.OpenAI -> settings.setKey(TranslationProvider.OpenAI, key)
        TranslationProvider.Claude -> settings.setKey(TranslationProvider.Claude, key)
        TranslationProvider.Groq -> settings.setKey(TranslationProvider.Groq, key)
        TranslationProvider.DeepSeek -> settings.setKey(TranslationProvider.DeepSeek, key)
        TranslationProvider.OpenRouter -> settings.setKey(TranslationProvider.OpenRouter, key)
        TranslationProvider.OnDevice, TranslationProvider.LocalAi -> Unit
    }

    /** The "saved" confirmation of cloud [provider]'s key. */
    private fun savedFlag(provider: TranslationProvider): MutableStateFlow<Boolean>? = when (provider) {
        TranslationProvider.Gemini -> apiKeySaved
        TranslationProvider.OpenAI -> openAiSaved
        TranslationProvider.Claude -> claudeSaved
        TranslationProvider.Groq -> groqSaved
        TranslationProvider.DeepSeek -> deepSeekSaved
        TranslationProvider.OpenRouter -> openRouterSaved
        TranslationProvider.OnDevice, TranslationProvider.LocalAi -> null
    }

    /**
     * A pasted key or token without the spaces and line breaks that come along from a web page or a mail:
     * a key has none of its own, and one inside it makes the request fail before it is sent.
     */
    private fun withoutWhitespace(secret: String): String = secret.filterNot { it.isWhitespace() || it.isISOControl() }

    /** Saves the OpenRouter API key and shows the "saved" confirmation. */
    fun saveOpenRouterKey(key: String) = saveKey(TranslationProvider.OpenRouter, key)

    /** Hides the "saved" confirmation once the user edits the OpenRouter key again. */
    fun onOpenRouterKeyEdited() = onKeyEdited(TranslationProvider.OpenRouter)

    /** Changes the Gemini model used for translations. */
    fun setGeminiModel(model: String) {
        viewModelScope.launch { settings.setModel(TranslationProvider.Gemini, model) }
    }

    /** Saves the Gemini API key and shows the "saved" confirmation. */
    fun saveApiKey(key: String) = saveKey(TranslationProvider.Gemini, key)

    /** Hides the "saved" confirmation once the user edits the key again. */
    fun onApiKeyEdited() = onKeyEdited(TranslationProvider.Gemini)

    /** Changes the OpenAI model used for translations. */
    fun setOpenAiModel(model: String) {
        viewModelScope.launch { settings.setModel(TranslationProvider.OpenAI, model) }
    }

    /** Changes the Anthropic Claude model used for translations. */
    fun setClaudeModel(model: String) {
        viewModelScope.launch { settings.setModel(TranslationProvider.Claude, model) }
    }

    /** Changes the Groq model used for translations. */
    fun setGroqModel(model: String) {
        viewModelScope.launch { settings.setModel(TranslationProvider.Groq, model) }
    }

    /** Changes the DeepSeek model used for translations. */
    fun setDeepSeekModel(model: String) {
        viewModelScope.launch { settings.setModel(TranslationProvider.DeepSeek, model) }
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

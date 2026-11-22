package com.andreaserick.languagecoast.data

import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.todayIn
import okio.Path.Companion.toPath
import kotlin.time.Clock

/** Languages a new coast can be started for: not the native language and without a coast yet. */
fun availableCoastLanguages(coasts: List<Coast>, nativeLanguage: String): List<Language> =
    Languages.ALL.filter { language ->
        language.name != nativeLanguage && coasts.none { it.language == language.name }
    }

/**
 * Languages the user can switch their native language to: not one they already study on a coast,
 * since cards would then be "translated" into the same language.
 */
fun nativeLanguageOptions(coasts: List<Coast>): List<Language> =
    Languages.ALL.filter { language -> coasts.none { it.language == language.name } }

/**
 * When the daily study reminder is sent.
 *
 * @property enabled Whether reminders are sent at all.
 * @property time A fixed time of day, or null for a random time during the day.
 */
data class ReminderSettings(
    val enabled: Boolean = true,
    val time: LocalTime? = null
)

/**
 * Bounded per-coast memory of recent practice sheets, so back-to-back sheets rotate across due/slipping
 * cards ([anchoredCards]) and steer clear of sentences just printed ([targetSentences]).
 */
data class WorksheetHistory(
    val anchoredCards: List<String> = emptyList(),
    val targetSentences: List<String> = emptyList()
)

/** User preferences: native language, active coast, translation provider, reminders and the study streak. */
interface SettingsRepository {
    /** The language the user's cards are translated from. */
    val nativeLanguage: Flow<String>
    /** The coast new cards are added to, or null if none was picked yet. */
    val activeCoastId: Flow<Int?>
    /** Where translations come from. Until one is picked: on-device where available and no Gemini key was saved, otherwise Gemini. */
    val translationProvider: Flow<TranslationProvider>
    /** The Gemini API key. */
    val apiKey: Flow<String>
    /** The Gemini model used for translations. */
    val geminiModel: Flow<String>
    /** The OpenRouter API key. */
    val openRouterKey: Flow<String>
    /** The OpenRouter model used for translations. */
    val openRouterModel: Flow<String>
    /** The selected Local AI model ID from [LocalAiCatalog]. */
    val localAiModel: Flow<String>
    /** The OpenAI API key. */
    val openAiKey: Flow<String>
    /** The OpenAI model used for translations. */
    val openAiModel: Flow<String>
    /** The Anthropic Claude API key. */
    val claudeKey: Flow<String>
    /** The Anthropic Claude model used for translations. */
    val claudeModel: Flow<String>
    /** The Groq API key. */
    val groqKey: Flow<String>
    /** A Hugging Face access token, for downloading gated local models. */
    val huggingFaceToken: Flow<String>
    /** The Groq model used for translations. */
    val groqModel: Flow<String>
    /** The DeepSeek API key. */
    val deepSeekKey: Flow<String>
    /** The DeepSeek model used for translations. */
    val deepSeekModel: Flow<String>
    /**
     * The models offered for [provider] in Settings: what the last "Check for new models" found the provider
     * to offer, or the app's own list ([SettingsDefaults.models]) if it was never checked.
     */
    fun modelOptions(provider: TranslationProvider): Flow<List<String>>
    /** Consecutive days with a completed study session; [refreshStreak] resets it once a day is missed. */
    val streakCount: Flow<Int>
    /** Days with a completed study session; at least the current streak's days, plus recent days recorded since. */
    val studyDays: Flow<Set<LocalDate>>
    /** The day of the last completed study session, or null if there was none yet. */
    val lastStudyDate: Flow<LocalDate?>
    /** Starter topics added whole to a coast, as "coastId:topic" (see [starterTopicKey]). */
    val starterTopicsAdded: Flow<Set<String>>
    /** The user's name, city and so on, for the blanks in starter sentences. */
    val profile: Flow<Profile>
    /** What the user answered about themselves, by [PersonalQuestion.key]; unanswered questions are left out. */
    val personalAnswers: Flow<Map<String, String>>
    /**
     * The islands about the user written on a coast: [personalIslandKey] to the [PersonalIsland.fingerprint]
     * of the facts they were written from, so a later change to the answers shows.
     */
    val personalIslandsBuilt: Flow<Map<String, String>>
    /** How sentences about the user are worded where a language marks the speaker's gender. */
    val wording: Flow<Wording>
    /** How much new a daily session brings (see [Pace]). */
    val pace: Flow<Pace>
    /** The levels whose reaching the user has been congratulated on, as "coastId:level" (see [levelKey]). */
    val levelsCelebrated: Flow<Set<String>>
    /** Whether the user waved away Today's invitation to tell the app about themselves. */
    val aboutYouInviteDismissed: Flow<Boolean>
    /** Whether and when the daily study reminder is shown. */
    val reminderSettings: Flow<ReminderSettings>
    /** Whether cards are studied the other way round: from the coast's language to the native language. */
    val studyReversed: Flow<Boolean>
    /** How many islands the user aims to study each week. */
    val weeklyGoal: Flow<Int>
    /** The day a streak freeze last stood in for a missed day, or null if none was ever used. */
    val freezeUsedOn: Flow<LocalDate?>
    /** "sun", "system", "dark" or "light". */
    val themeMode: Flow<String>
    /** Whether Follow the sun goes by the phone's approximate location rather than its time zone. */
    val sunUsesLocation: Flow<Boolean>
    /** The approximate location last read for Follow the sun, or null; see [SunLocation]. */
    val sunLocation: Flow<SunLocation?>
    val hapticsEnabled: Flow<Boolean>
    val soundsEnabled: Flow<Boolean>
    /**
     * The cloud providers the user allowed the app to send text to, by [TranslationProvider.name]. A saved
     * key alone is not permission: a cloud provider that isn't in here is not used (see [currentEngine]).
     */
    val aiSharingAllowed: Flow<Set<String>>

    /** Stores [language] as the user's native language. */
    suspend fun setNativeLanguage(language: String)
    /** Makes coast [coastId] the one new cards are added to. */
    suspend fun setActiveCoastId(coastId: Int)
    /** Stores where translations come from. */
    suspend fun setTranslationProvider(provider: TranslationProvider)
    /** Stores the Gemini API key. */
    suspend fun setApiKey(key: String)
    /** Stores the Gemini model used for translations. */
    suspend fun setGeminiModel(model: String)
    /** Stores the OpenRouter API key. */
    suspend fun setOpenRouterKey(key: String)
    /** Stores the OpenRouter model used for translations. */
    suspend fun setOpenRouterModel(model: String)
    /** Stores the active Local AI model ID. */
    suspend fun setLocalAiModel(modelId: String)
    /** Stores the OpenAI API key. */
    suspend fun setOpenAiKey(key: String)
    /** Stores the OpenAI model used for translations. */
    suspend fun setOpenAiModel(model: String)
    /** Stores the Anthropic Claude API key. */
    suspend fun setClaudeKey(key: String)
    /** Stores the Anthropic Claude model used for translations. */
    suspend fun setClaudeModel(model: String)
    /** Stores the Groq API key. */
    suspend fun setGroqKey(key: String)
    /** Stores the Hugging Face access token. */
    suspend fun setHuggingFaceToken(token: String)
    /** Stores the Groq model used for translations. */
    suspend fun setGroqModel(model: String)
    /** Stores the DeepSeek API key. */
    suspend fun setDeepSeekKey(key: String)
    /** Stores the DeepSeek model used for translations. */
    suspend fun setDeepSeekModel(model: String)
    /** Stores the models offered for [provider], as a check for new models found them. */
    suspend fun setModelOptions(provider: TranslationProvider, models: List<String>)
    /** Stores whether and when the daily study reminder is shown. */
    suspend fun setReminderSettings(reminder: ReminderSettings)
    /** Stores whether cards are studied the other way round. */
    suspend fun setStudyReversed(reversed: Boolean)
    suspend fun setWeeklyGoal(islands: Int)
    suspend fun setThemeMode(mode: String)
    /** Switching it off also forgets the location. */
    suspend fun setSunUsesLocation(enabled: Boolean)
    suspend fun setSunLocation(location: SunLocation)
    suspend fun setHapticsEnabled(enabled: Boolean)
    suspend fun setSoundsEnabled(enabled: Boolean)

    /** Records that starter [topic] was added whole to coast [coastId]. */
    suspend fun markStarterTopicAdded(coastId: Int, topic: String)

    /** Stores the user's [profile]. */
    suspend fun setProfile(profile: Profile)

    /**
     * Stores one blank of the profile ([placeholder], see [Profile.PLACEHOLDERS]) as [value], in one atomic edit.
     * Fields are saved as they are typed, so a read-modify-write of the whole profile would let two keystrokes race.
     */
    suspend fun setProfileField(placeholder: String, value: String)

    /** Stores the answer to the question with [key] ([PersonalQuestion.key]) in one atomic edit; blank clears it. */
    suspend fun setPersonalAnswer(key: String, value: String)

    /** Records that the island about the user with [islandId] was written on coast [coastId] from facts with [fingerprint]. */
    suspend fun markPersonalIslandBuilt(coastId: Int, islandId: String, fingerprint: String)

    /** Stores how sentences about the user are worded. */
    suspend fun setWording(wording: Wording)

    /** Stores how much new a daily session brings. */
    suspend fun setPace(pace: Pace)

    /** Records that reaching [level] on coast [coastId] was celebrated, so it isn't again. */
    suspend fun markLevelCelebrated(coastId: Int, level: Level)

    /** Records that Today's invitation to tell the app about oneself was waved away. */
    suspend fun dismissAboutYouInvite()

    /** Records that the user allowed the app to send text to cloud [provider]. */
    suspend fun allowAiSharing(provider: TranslationProvider)

    /** Clears [provider]'s saved API key and withdraws the permission to send text to it. */
    suspend fun removeApiKey(provider: TranslationProvider)

    /** Whether nothing at all is stored: a fresh install, or settings lost when the phone was restored from a backup. */
    suspend fun isEmpty(): Boolean

    /** Records a completed study session for today and updates the streak. */
    suspend fun recordStudySession()

    /**
     * Resets the streak if more than a full day has passed since the last session. One missed day a
     * week is covered by a streak freeze, which keeps the streak alive (see [refreshedStreak]).
     */
    suspend fun refreshStreak()

    /** Everything stored here except the API keys, for a [Backup]. */
    suspend fun backupSettings(): BackupSettings

    /** Replaces everything stored here with [backup], except the API keys, which are kept. */
    suspend fun restoreSettings(backup: BackupSettings)

    /** Moves any legacy plaintext secrets out of DataStore into hardware-backed [SecretStore]. */
    suspend fun migrateLegacySecrets()

    /** Recent practice-sheet anchors and target sentences for coast [coastId], bounded in size. */
    fun worksheetHistory(coastId: Int): Flow<WorksheetHistory>

    /** Appends [anchoredCards] and [targetSentences] to coast [coastId]'s bounded practice-sheet history. */
    suspend fun recordWorksheet(coastId: Int, anchoredCards: List<String>, targetSentences: List<String>)
}

/** [SettingsRepository] backed by Preferences DataStore and hardware-backed [SecretStore]; days are counted in [timeZone]. */
class DataStoreSettingsRepository(
    private val dataStore: DataStore<Preferences>,
    private val clock: Clock,
    private val timeZone: TimeZone,
    private val secretStore: SecretStore = InMemorySecretStore()
) : SettingsRepository {

    override val nativeLanguage: Flow<String> =
        dataStore.data.map { it[SettingKeys.NATIVE_LANG] ?: SettingsDefaults.NATIVE_LANGUAGE }

    override val activeCoastId: Flow<Int?> =
        dataStore.data.map { it[SettingKeys.ACTIVE_COAST_ID] }

    override val translationProvider: Flow<TranslationProvider> = combine(dataStore.data, secretStore.version) { preferences, _ ->
        val hasGeminiKey = !readSecret(preferences, SettingKeys.API_KEY).isBlank()
        preferences[SettingKeys.TRANSLATION_PROVIDER]?.let { name -> TranslationProvider.entries.firstOrNull { it.name == name } }
            // A provider this phone can't run (Local AI in settings that came from another phone) counts as none picked.
            ?.takeUnless { (it == TranslationProvider.LocalAi && !localAiAvailable) || (it == TranslationProvider.OnDevice && !onDeviceTranslationAvailable) }
            // Before providers could be picked, Gemini was the only one; keep it for users who set it up.
            ?: if (onDeviceTranslationAvailable && !hasGeminiKey) TranslationProvider.OnDevice else TranslationProvider.Gemini
    }

    override val apiKey: Flow<String> = secretFlow(SettingKeys.API_KEY)

    override val geminiModel: Flow<String> =
        // A model picked in an earlier version may be retired by now; show and use its replacement.
        dataStore.data.map { normalizeGeminiModel(it[SettingKeys.GEMINI_MODEL] ?: SettingsDefaults.GEMINI_MODEL) }

    override val openRouterKey: Flow<String> = secretFlow(SettingKeys.OPENROUTER_KEY)

    override val openRouterModel: Flow<String> =
        dataStore.data.map { it[SettingKeys.OPENROUTER_MODEL] ?: SettingsDefaults.OPENROUTER_MODEL }

    override val localAiModel: Flow<String> =
        dataStore.data.map { it[SettingKeys.LOCAL_AI_MODEL] ?: SettingsDefaults.LOCAL_AI_MODEL }

    override val openAiKey: Flow<String> = secretFlow(SettingKeys.OPENAI_KEY)

    override val openAiModel: Flow<String> =
        dataStore.data.map { it[SettingKeys.OPENAI_MODEL] ?: SettingsDefaults.OPENAI_MODEL }

    override val claudeKey: Flow<String> = secretFlow(SettingKeys.CLAUDE_KEY)

    override val claudeModel: Flow<String> =
        dataStore.data.map { it[SettingKeys.CLAUDE_MODEL] ?: SettingsDefaults.CLAUDE_MODEL }

    override val groqKey: Flow<String> = secretFlow(SettingKeys.GROQ_KEY)

    override val huggingFaceToken: Flow<String> = secretFlow(SettingKeys.HUGGING_FACE_TOKEN)

    override val groqModel: Flow<String> =
        dataStore.data.map { it[SettingKeys.GROQ_MODEL] ?: SettingsDefaults.GROQ_MODEL }

    override val deepSeekKey: Flow<String> = secretFlow(SettingKeys.DEEPSEEK_KEY)

    override val deepSeekModel: Flow<String> =
        dataStore.data.map { it[SettingKeys.DEEPSEEK_MODEL] ?: SettingsDefaults.DEEPSEEK_MODEL }

    override val streakCount: Flow<Int> =
        dataStore.data.map { it[SettingKeys.STREAK_COUNT] ?: 0 }

    override val studyDays: Flow<Set<LocalDate>> = dataStore.data.map { preferences ->
        val recorded = preferences[SettingKeys.STUDY_DAYS].orEmpty().mapNotNullTo(HashSet<LocalDate>()) { parseDateOrNull(it) }
        // Older versions only stored the streak, so its days count as studied too.
        val lastStudy = preferences[SettingKeys.LAST_STUDY_DATE]?.let(::parseDateOrNull)
        val streak = preferences[SettingKeys.STREAK_COUNT] ?: 0
        // No reader looks back further than a month; a streak of any size must not cost more than this.
        if (lastStudy != null) (0 until minOf(streak, 366)).forEach { recorded += lastStudy.minus(it, DateTimeUnit.DAY) }
        recorded
    }

    override val lastStudyDate: Flow<LocalDate?> = dataStore.data.map { preferences -> preferences[SettingKeys.LAST_STUDY_DATE]?.let(::parseDateOrNull) }

    override val starterTopicsAdded: Flow<Set<String>> = dataStore.data.map { it[SettingKeys.STARTER_TOPICS_ADDED].orEmpty() }

    override val profile: Flow<Profile> = dataStore.data.map { it.readProfile() }

    override suspend fun setProfile(profile: Profile) {
        dataStore.edit { preferences ->
            SettingKeys.PROFILE_KEYS.forEach { (placeholder, key) ->
                val value = profile.fillers()[placeholder]
                if (value == null) preferences.remove(key) else preferences[key] = value
            }
        }
    }

    override suspend fun setProfileField(placeholder: String, value: String) {
        val key = SettingKeys.PROFILE_KEYS[placeholder] ?: return
        dataStore.edit { preferences -> if (value.isBlank()) preferences.remove(key) else preferences[key] = value }
    }

    override suspend fun markStarterTopicAdded(coastId: Int, topic: String) {
        dataStore.edit { it[SettingKeys.STARTER_TOPICS_ADDED] = it[SettingKeys.STARTER_TOPICS_ADDED].orEmpty() + starterTopicKey(coastId, topic) }
    }

    override val personalAnswers: Flow<Map<String, String>> = dataStore.data.map { it.readAnswers() }

    override val personalIslandsBuilt: Flow<Map<String, String>> = dataStore.data.map { it.readBuilt() }

    override val wording: Flow<Wording> = dataStore.data.map { Wording.fromKey(it[SettingKeys.WORDING]) }
    override val pace: Flow<Pace> = dataStore.data.map { Pace.fromKey(it[SettingKeys.PACE]) }
    override val aboutYouInviteDismissed: Flow<Boolean> = dataStore.data.map { it[SettingKeys.ABOUT_YOU_INVITE_DISMISSED] ?: false }

    override suspend fun setWording(wording: Wording) {
        dataStore.edit { preferences -> if (wording == Wording.Unspecified) preferences.remove(SettingKeys.WORDING) else preferences[SettingKeys.WORDING] = wording.key }
    }
    override suspend fun setPace(pace: Pace) = set(SettingKeys.PACE, pace.key)
    override val levelsCelebrated: Flow<Set<String>> = dataStore.data.map { it[SettingKeys.LEVELS_CELEBRATED].orEmpty() }
    override suspend fun markLevelCelebrated(coastId: Int, level: Level) {
        dataStore.edit { it[SettingKeys.LEVELS_CELEBRATED] = it[SettingKeys.LEVELS_CELEBRATED].orEmpty() + levelKey(coastId, level) }
    }
    override suspend fun dismissAboutYouInvite() = set(SettingKeys.ABOUT_YOU_INVITE_DISMISSED, true)

    override suspend fun setPersonalAnswer(key: String, value: String) {
        val preference = SettingKeys.ANSWER_KEYS[key] ?: return
        dataStore.edit { preferences -> if (value.isBlank()) preferences.remove(preference) else preferences[preference] = value }
    }

    override suspend fun markPersonalIslandBuilt(coastId: Int, islandId: String, fingerprint: String) {
        dataStore.edit { preferences ->
            val others = preferences.readBuilt() - personalIslandKey(coastId, islandId)
            preferences[SettingKeys.PERSONAL_ISLANDS_BUILT] = (others + (personalIslandKey(coastId, islandId) to fingerprint)).mapTo(HashSet()) { "${it.key}=${it.value}" }
        }
    }

    override val reminderSettings: Flow<ReminderSettings> = dataStore.data.map { preferences ->
        ReminderSettings(
            enabled = preferences[SettingKeys.REMINDER_ENABLED] ?: true,
            // A minute outside the day can't be a time; such a value counts as "no fixed time".
            time = preferences[SettingKeys.REMINDER_MINUTE_OF_DAY]?.takeIf { it in MINUTES_OF_DAY }?.let { LocalTime.fromSecondOfDay(it * 60) }
        )
    }

    override val studyReversed: Flow<Boolean> = dataStore.data.map { it[SettingKeys.STUDY_REVERSED] ?: false }

    override val weeklyGoal: Flow<Int> = dataStore.data.map { it[SettingKeys.WEEKLY_GOAL] ?: SettingsDefaults.WEEKLY_GOAL }

    override val freezeUsedOn: Flow<LocalDate?> = dataStore.data.map { preferences -> preferences[SettingKeys.FREEZE_USED_ON]?.let(::parseDateOrNull) }

    override val themeMode: Flow<String> = dataStore.data.map { it[SettingKeys.THEME_MODE] ?: SettingsDefaults.THEME_MODE }

    override val sunUsesLocation: Flow<Boolean> = dataStore.data.map { it[SettingKeys.SUN_USES_LOCATION] ?: false }

    override val sunLocation: Flow<SunLocation?> = dataStore.data.map { SunLocation.decode(it[SettingKeys.SUN_LOCATION]) }

    override suspend fun setSunUsesLocation(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[SettingKeys.SUN_USES_LOCATION] = enabled
            if (!enabled) preferences.remove(SettingKeys.SUN_LOCATION)
        }
    }

    override suspend fun setSunLocation(location: SunLocation) {
        dataStore.edit { preferences ->
            // A reading that arrives just after the switch went off is not kept.
            if (preferences[SettingKeys.SUN_USES_LOCATION] == true) preferences[SettingKeys.SUN_LOCATION] = location.encode()
        }
    }

    override val hapticsEnabled: Flow<Boolean> = dataStore.data.map { it[SettingKeys.HAPTICS_ENABLED] ?: true }

    override val soundsEnabled: Flow<Boolean> = dataStore.data.map { it[SettingKeys.SOUNDS_ENABLED] ?: true }

    override val aiSharingAllowed: Flow<Set<String>> = dataStore.data.map { it[SettingKeys.AI_SHARING_ALLOWED].orEmpty() }

    override suspend fun allowAiSharing(provider: TranslationProvider) {
        if (!provider.isCloud) return
        dataStore.edit { it[SettingKeys.AI_SHARING_ALLOWED] = it[SettingKeys.AI_SHARING_ALLOWED].orEmpty() + provider.name }
    }

    override suspend fun removeApiKey(provider: TranslationProvider) {
        val key = SettingKeys.apiKeyPreference(provider) ?: return
        secretStore.remove(key.name)
        dataStore.edit { preferences ->
            preferences.remove(key)
            val allowed = preferences[SettingKeys.AI_SHARING_ALLOWED].orEmpty() - provider.name
            if (allowed.isEmpty()) preferences.remove(SettingKeys.AI_SHARING_ALLOWED) else preferences[SettingKeys.AI_SHARING_ALLOWED] = allowed
        }
    }

    override suspend fun isEmpty(): Boolean =
        dataStore.data.first().asMap().isEmpty() && SettingKeys.SECRET_KEYS.all { secretStore.get(it.name).isNullOrEmpty() }

    override suspend fun setNativeLanguage(language: String) = set(SettingKeys.NATIVE_LANG, language)
    override suspend fun setStudyReversed(reversed: Boolean) = set(SettingKeys.STUDY_REVERSED, reversed)
    override suspend fun setWeeklyGoal(islands: Int) = set(SettingKeys.WEEKLY_GOAL, islands)
    override suspend fun setThemeMode(mode: String) = set(SettingKeys.THEME_MODE, mode)
    override suspend fun setHapticsEnabled(enabled: Boolean) = set(SettingKeys.HAPTICS_ENABLED, enabled)
    override suspend fun setSoundsEnabled(enabled: Boolean) = set(SettingKeys.SOUNDS_ENABLED, enabled)
    override suspend fun setActiveCoastId(coastId: Int) = set(SettingKeys.ACTIVE_COAST_ID, coastId)
    override suspend fun setTranslationProvider(provider: TranslationProvider) = set(SettingKeys.TRANSLATION_PROVIDER, provider.name)
    override suspend fun setApiKey(key: String) = setKey(TranslationProvider.Gemini, key)
    override suspend fun setGeminiModel(model: String) = set(SettingKeys.GEMINI_MODEL, model)
    override suspend fun setOpenRouterKey(key: String) = setKey(TranslationProvider.OpenRouter, key)
    override suspend fun setOpenRouterModel(model: String) = set(SettingKeys.OPENROUTER_MODEL, model)
    override suspend fun setLocalAiModel(modelId: String) = set(SettingKeys.LOCAL_AI_MODEL, modelId)
    override suspend fun setOpenAiKey(key: String) = setKey(TranslationProvider.OpenAI, key)
    override suspend fun setOpenAiModel(model: String) = set(SettingKeys.OPENAI_MODEL, model)
    override suspend fun setClaudeKey(key: String) = setKey(TranslationProvider.Claude, key)
    override suspend fun setClaudeModel(model: String) = set(SettingKeys.CLAUDE_MODEL, model)
    override suspend fun setGroqKey(key: String) = setKey(TranslationProvider.Groq, key)
    override suspend fun setHuggingFaceToken(token: String) {
        // A blank token is no token: removing it leaves nothing of it behind.
        if (token.isBlank()) {
            secretStore.remove(SettingKeys.HUGGING_FACE_TOKEN.name)
        } else {
            secretStore.put(SettingKeys.HUGGING_FACE_TOKEN.name, token)
        }
        // Clear any legacy plaintext copy from DataStore.
        dataStore.edit { it.remove(SettingKeys.HUGGING_FACE_TOKEN) }
    }
    override suspend fun setGroqModel(model: String) = set(SettingKeys.GROQ_MODEL, model)
    override suspend fun setDeepSeekKey(key: String) = setKey(TranslationProvider.DeepSeek, key)
    override suspend fun setDeepSeekModel(model: String) = set(SettingKeys.DEEPSEEK_MODEL, model)

    override fun modelOptions(provider: TranslationProvider): Flow<List<String>> =
        dataStore.data.map { preferences ->
            preferences[modelOptionsKey(provider)]?.lines()?.filter { it.isNotBlank() }?.takeIf { it.isNotEmpty() }
                ?: SettingsDefaults.models(provider)
        }

    override suspend fun setModelOptions(provider: TranslationProvider, models: List<String>) =
        set(modelOptionsKey(provider), models.joinToString("\n"))

    private fun modelOptionsKey(provider: TranslationProvider) = SettingKeys.modelOptions(provider)

    override fun worksheetHistory(coastId: Int): Flow<WorksheetHistory> =
        dataStore.data.map { preferences ->
            WorksheetHistory(
                anchoredCards = preferences[worksheetAnchorsKey(coastId)]?.lines()?.filter { it.isNotBlank() }.orEmpty(),
                targetSentences = preferences[worksheetSentencesKey(coastId)]?.lines()?.filter { it.isNotBlank() }.orEmpty()
            )
        }

    override suspend fun recordWorksheet(coastId: Int, anchoredCards: List<String>, targetSentences: List<String>) {
        if (anchoredCards.isEmpty() && targetSentences.isEmpty()) return
        dataStore.edit { preferences ->
            if (anchoredCards.isNotEmpty()) {
                val anchorsKey = worksheetAnchorsKey(coastId)
                val existing = preferences[anchorsKey]?.lines()?.filter { it.isNotBlank() }.orEmpty()
                val merged = mergeBoundedRing(existing, anchoredCards, WORKSHEET_ANCHORS_KEPT)
                preferences[anchorsKey] = merged.joinToString("\n")
            }
            if (targetSentences.isNotEmpty()) {
                val sentencesKey = worksheetSentencesKey(coastId)
                val existing = preferences[sentencesKey]?.lines()?.filter { it.isNotBlank() }.orEmpty()
                val merged = mergeBoundedRing(existing, targetSentences, WORKSHEET_SENTENCES_KEPT)
                preferences[sentencesKey] = merged.joinToString("\n")
            }
        }
    }

    private fun worksheetAnchorsKey(coastId: Int) = SettingKeys.worksheetAnchors(coastId)
    private fun worksheetSentencesKey(coastId: Int) = SettingKeys.worksheetSentences(coastId)

    override suspend fun setReminderSettings(reminder: ReminderSettings) {
        dataStore.edit { preferences ->
            preferences[SettingKeys.REMINDER_ENABLED] = reminder.enabled
            val time = reminder.time
            if (time == null) preferences.remove(SettingKeys.REMINDER_MINUTE_OF_DAY)
            else preferences[SettingKeys.REMINDER_MINUTE_OF_DAY] = time.hour * 60 + time.minute
        }
    }

    override suspend fun recordStudySession() {
        dataStore.edit { preferences ->
            val today = clock.todayIn(timeZone)
            val lastStudy = preferences[SettingKeys.LAST_STUDY_DATE]?.let(::parseDateOrNull)
            val freezeUsedOn = preferences[SettingKeys.FREEZE_USED_ON]?.let(::parseDateOrNull)
            preferences[SettingKeys.STREAK_COUNT] = nextStreak(lastStudy, preferences[SettingKeys.STREAK_COUNT] ?: 0, today, freezeUsedOn)
            preferences[SettingKeys.LAST_STUDY_DATE] = today.toString()
            // Keep a few weeks of study days for the weekly overview.
            val cutoff = today.minus(STUDY_DAYS_KEPT, DateTimeUnit.DAY)
            preferences[SettingKeys.STUDY_DAYS] = (preferences[SettingKeys.STUDY_DAYS].orEmpty() + today.toString())
                .filterTo(HashSet()) { day -> parseDateOrNull(day)?.let { it >= cutoff } == true }
        }
    }

    override suspend fun refreshStreak() {
        dataStore.edit { preferences ->
            val lastStudy = preferences[SettingKeys.LAST_STUDY_DATE]?.let(::parseDateOrNull) ?: return@edit
            val freezeUsedOn = preferences[SettingKeys.FREEZE_USED_ON]?.let(::parseDateOrNull)
            val refreshed = refreshedStreak(lastStudy, preferences[SettingKeys.STREAK_COUNT] ?: 0, freezeUsedOn, clock.todayIn(timeZone))
            preferences[SettingKeys.STREAK_COUNT] = refreshed.streak
            if (refreshed.freezeUsedOn == null) preferences.remove(SettingKeys.FREEZE_USED_ON) else preferences[SettingKeys.FREEZE_USED_ON] = refreshed.freezeUsedOn.toString()
        }
    }

    override suspend fun backupSettings(): BackupSettings {
        val preferences = dataStore.data.first()
        return SettingKeys.backedUp.fold(BackupSettings()) { backup, setting -> setting.saveInto(backup, preferences) }
    }

    override suspend fun restoreSettings(backup: BackupSettings) {
        dataStore.edit { preferences -> SettingKeys.backedUp.forEach { it.loadFrom(backup, preferences) } }
    }

    /** Stores [value] under [key]. */
    private suspend fun <T> set(key: Preferences.Key<T>, value: T) {
        dataStore.edit { it[key] = value }
    }

    /** Stores [provider]'s API key in [secretStore]. A blank one removes the key and revokes AI sharing consent. */
    private suspend fun setKey(provider: TranslationProvider, key: String) {
        val preference = SettingKeys.apiKeyPreference(provider) ?: return
        if (key.isBlank()) {
            removeApiKey(provider)
        } else {
            secretStore.put(preference.name, key)
            // Remove any legacy plaintext copy from DataStore if present.
            if (dataStore.data.first()[preference] != null) {
                dataStore.edit { it.remove(preference) }
            }
        }
    }

    /** Moves any legacy plaintext secrets from DataStore into hardware-backed [secretStore] and deletes the plaintext copies. */
    override suspend fun migrateLegacySecrets() {
        val preferences = dataStore.data.first()
        val toRemove = SettingKeys.SECRET_KEYS.filter { key ->
            val legacy = preferences[key]
            if (!legacy.isNullOrBlank()) {
                if (secretStore.get(key.name).isNullOrBlank()) {
                    secretStore.put(key.name, legacy)
                }
                true
            } else {
                legacy != null
            }
        }
        if (toRemove.isNotEmpty()) {
            dataStore.edit { mutable -> toRemove.forEach { mutable.remove(it) } }
        }
    }

    private fun secretFlow(key: Preferences.Key<String>): Flow<String> =
        combine(dataStore.data, secretStore.version) { preferences, _ -> readSecret(preferences, key) }

    private fun readSecret(preferences: Preferences, key: Preferences.Key<String>): String =
        secretStore.get(key.name) ?: preferences[key] ?: ""

    private fun Preferences.readProfile(): Profile =
        SettingKeys.PROFILE_KEYS.entries.fold(Profile()) { profile, (placeholder, key) -> profile.with(placeholder, this[key].orEmpty()) }

    private fun Preferences.readAnswers(): Map<String, String> =
        SettingKeys.ANSWER_KEYS.mapNotNull { (question, key) -> this[key]?.takeIf { it.isNotBlank() }?.let { question to it } }.toMap()

    private fun Preferences.readBuilt(): Map<String, String> = SettingKeys.readBuilt(this[SettingKeys.PERSONAL_ISLANDS_BUILT])

    private companion object {
        const val STUDY_DAYS_KEPT = 28
        const val WORKSHEET_ANCHORS_KEPT = 80
        const val WORKSHEET_SENTENCES_KEPT = 60

        fun mergeBoundedRing(existing: List<String>, added: List<String>, limit: Int): List<String> {
            val sanitized = added.map { it.replace('\n', ' ').replace('\r', ' ').trim() }.filter { it.isNotEmpty() }
            val addedSet = sanitized.toHashSet()
            return (existing.filterNot { it in addedSet } + sanitized.distinct()).takeLast(limit)
        }
    }
}

/** The entry in [SettingsRepository.personalIslandsBuilt] for the island about the user with [islandId] on coast [coastId]. */
fun personalIslandKey(coastId: Int, islandId: String): String = "$coastId:$islandId"

/** The entry in [SettingsRepository.starterTopicsAdded] for [topic] on coast [coastId]. */
fun starterTopicKey(coastId: Int, topic: String): String = "$coastId:$topic"

/**
 * The DataStore holding the settings, stored in the file at [path]. Only one may exist per file. A file that
 * can't be read any more is replaced by an empty one: the cards are in the database, and the app must still start.
 */
fun createSettingsDataStore(path: String): DataStore<Preferences> =
    PreferenceDataStoreFactory.createWithPath(
        corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
        produceFile = { path.toPath() }
    )

/** The minutes of a day, which a reminder's fixed time is stored as. */
internal val MINUTES_OF_DAY = 0..1439

/**
 * [text] as a date, or null if it isn't one. A stored value that can't be read (from a hand-edited backup,
 * say) then counts as missing, instead of stopping the app at every start.
 */
internal fun parseDateOrNull(text: String): LocalDate? = try {
    LocalDate.parse(text)
} catch (e: IllegalArgumentException) {
    null
}

/**
 * The single target language from before coasts existed. Only read by [AppDatabase.migration2To3]
 * to name the coast that existing islands are moved onto.
 */
internal suspend fun DataStore<Preferences>.readLegacyTargetLanguage(): String =
    data.first()[stringPreferencesKey("target_language")] ?: "Spanish"

/**
 * Streak after completing a session [today]: unchanged if already studied today (or, after the phone's
 * clock was set back, on a day yet to come), incremented if the
 * last session was yesterday (or the day before, with a streak freeze on yesterday), otherwise restarted at 1.
 */
internal fun nextStreak(lastStudy: LocalDate?, currentStreak: Int, today: LocalDate, freezeUsedOn: LocalDate? = null): Int {
    val yesterday = today.minus(1, DateTimeUnit.DAY)
    return when {
        lastStudy == null -> 1
        // Today, or a day that hasn't come yet (the clock was set back, or a flight went west): nothing was missed.
        lastStudy >= today -> currentStreak
        lastStudy == yesterday -> currentStreak + 1
        lastStudy == yesterday.minus(1, DateTimeUnit.DAY) && freezeUsedOn == yesterday -> currentStreak + 1
        else -> 1
    }
}

/** A streak is broken once a full calendar day passes without a session. */
internal fun isStreakBroken(lastStudy: LocalDate, today: LocalDate): Boolean =
    lastStudy < today.minus(1, DateTimeUnit.DAY)

/** The streak after a refresh, and the day the freeze now covers (unchanged if none was used). */
data class RefreshedStreak(val streak: Int, val freezeUsedOn: LocalDate?)

/** A freeze can stand in for one missed day this often. */
const val FREEZE_EVERY_DAYS = 7

/**
 * The streak as of [today]: kept while no full day was missed; kept with a freeze on the one missed day
 * (yesterday) if the last freeze was at least [FREEZE_EVERY_DAYS] ago; otherwise lost.
 */
internal fun refreshedStreak(lastStudy: LocalDate, streak: Int, freezeUsedOn: LocalDate?, today: LocalDate): RefreshedStreak {
    if (!isStreakBroken(lastStudy, today)) return RefreshedStreak(streak, freezeUsedOn)
    val yesterday = today.minus(1, DateTimeUnit.DAY)
    val missedOnlyYesterday = lastStudy == yesterday.minus(1, DateTimeUnit.DAY)
    return when {
        missedOnlyYesterday && freezeUsedOn == yesterday -> RefreshedStreak(streak, freezeUsedOn)
        missedOnlyYesterday && streak > 0 && (freezeUsedOn == null || freezeUsedOn <= today.minus(FREEZE_EVERY_DAYS, DateTimeUnit.DAY)) ->
            RefreshedStreak(streak, yesterday)
        else -> RefreshedStreak(0, freezeUsedOn)
    }
}

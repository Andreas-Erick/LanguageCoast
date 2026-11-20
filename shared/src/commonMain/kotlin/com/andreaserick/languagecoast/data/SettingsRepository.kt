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
    val localAiModel: Flow<String> get() = flowOf(SettingsDefaults.LOCAL_AI_MODEL)
    /** The OpenAI API key. */
    val openAiKey: Flow<String> get() = flowOf("")
    /** The OpenAI model used for translations. */
    val openAiModel: Flow<String> get() = flowOf(SettingsDefaults.OPENAI_MODEL)
    /** The Anthropic Claude API key. */
    val claudeKey: Flow<String> get() = flowOf("")
    /** The Anthropic Claude model used for translations. */
    val claudeModel: Flow<String> get() = flowOf(SettingsDefaults.CLAUDE_MODEL)
    /** The Groq API key. */
    val groqKey: Flow<String> get() = flowOf("")
    /** A Hugging Face access token, for downloading gated local models. */
    val huggingFaceToken: Flow<String> get() = flowOf("")
    /** The Groq model used for translations. */
    val groqModel: Flow<String> get() = flowOf(SettingsDefaults.GROQ_MODEL)
    /** The DeepSeek API key. */
    val deepSeekKey: Flow<String> get() = flowOf("")
    /** The DeepSeek model used for translations. */
    val deepSeekModel: Flow<String> get() = flowOf(SettingsDefaults.DEEPSEEK_MODEL)
    /**
     * The models offered for [provider] in Settings: what the last "Check for new models" found the provider
     * to offer, or the app's own list ([SettingsDefaults.models]) if it was never checked.
     */
    fun modelOptions(provider: TranslationProvider): Flow<List<String>> = flowOf(SettingsDefaults.models(provider))
    /** Consecutive days with a completed study session; [refreshStreak] resets it once a day is missed. */
    val streakCount: Flow<Int>
    /** Days with a completed study session; at least the current streak's days, plus recent days recorded since. */
    val studyDays: Flow<Set<LocalDate>>
    /** The day of the last completed study session, or null if there was none yet. */
    val lastStudyDate: Flow<LocalDate?> get() = flowOf(null)
    /** Starter topics added whole to a coast, as "coastId:topic" (see [starterTopicKey]). */
    val starterTopicsAdded: Flow<Set<String>> get() = flowOf(emptySet())
    /** The user's name, city and so on, for the blanks in starter sentences. */
    val profile: Flow<Profile> get() = flowOf(Profile())
    /** What the user answered about themselves, by [PersonalQuestion.key]; unanswered questions are left out. */
    val personalAnswers: Flow<Map<String, String>> get() = flowOf(emptyMap())
    /**
     * The islands about the user written on a coast: [personalIslandKey] to the [PersonalIsland.fingerprint]
     * of the facts they were written from, so a later change to the answers shows.
     */
    val personalIslandsBuilt: Flow<Map<String, String>> get() = flowOf(emptyMap())
    /** How sentences about the user are worded where a language marks the speaker's gender. */
    val wording: Flow<Wording> get() = flowOf(Wording.Unspecified)
    /** How much new a daily session brings (see [Pace]). */
    val pace: Flow<Pace> get() = flowOf(Pace.Steady)
    /** The levels whose reaching the user has been congratulated on, as "coastId:level" (see [levelKey]). */
    val levelsCelebrated: Flow<Set<String>> get() = flowOf(emptySet())
    /** Whether the user waved away Today's invitation to tell the app about themselves. */
    val aboutYouInviteDismissed: Flow<Boolean> get() = flowOf(false)
    /** Whether and when the daily study reminder is shown. */
    val reminderSettings: Flow<ReminderSettings>
    /** Whether cards are studied the other way round: from the coast's language to the native language. */
    val studyReversed: Flow<Boolean>
    /** How many islands the user aims to study each week. */
    val weeklyGoal: Flow<Int> get() = flowOf(SettingsDefaults.WEEKLY_GOAL)
    /** The day a streak freeze last stood in for a missed day, or null if none was ever used. */
    val freezeUsedOn: Flow<LocalDate?> get() = flowOf(null)
    /** "system", "dark" or "light". */
    val themeMode: Flow<String> get() = flowOf(SettingsDefaults.THEME_MODE)
    val hapticsEnabled: Flow<Boolean> get() = flowOf(true)
    val soundsEnabled: Flow<Boolean> get() = flowOf(true)
    /**
     * The cloud providers the user allowed the app to send text to, by [TranslationProvider.name]. A saved
     * key alone is not permission: a cloud provider that isn't in here is not used (see [currentEngine]).
     */
    val aiSharingAllowed: Flow<Set<String>> get() = flowOf(emptySet())

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
    suspend fun setLocalAiModel(modelId: String) = Unit
    /** Stores the OpenAI API key. */
    suspend fun setOpenAiKey(key: String) = Unit
    /** Stores the OpenAI model used for translations. */
    suspend fun setOpenAiModel(model: String) = Unit
    /** Stores the Anthropic Claude API key. */
    suspend fun setClaudeKey(key: String) = Unit
    /** Stores the Anthropic Claude model used for translations. */
    suspend fun setClaudeModel(model: String) = Unit
    /** Stores the Groq API key. */
    suspend fun setGroqKey(key: String) = Unit
    /** Stores the Hugging Face access token. */
    suspend fun setHuggingFaceToken(token: String) = Unit
    /** Stores the Groq model used for translations. */
    suspend fun setGroqModel(model: String) = Unit
    /** Stores the DeepSeek API key. */
    suspend fun setDeepSeekKey(key: String) = Unit
    /** Stores the DeepSeek model used for translations. */
    suspend fun setDeepSeekModel(model: String) = Unit
    /** Stores the models offered for [provider], as a check for new models found them. */
    suspend fun setModelOptions(provider: TranslationProvider, models: List<String>) = Unit
    /** Stores whether and when the daily study reminder is shown. */
    suspend fun setReminderSettings(reminder: ReminderSettings)
    /** Stores whether cards are studied the other way round. */
    suspend fun setStudyReversed(reversed: Boolean)
    suspend fun setWeeklyGoal(islands: Int) = Unit
    suspend fun setThemeMode(mode: String) = Unit
    suspend fun setHapticsEnabled(enabled: Boolean) = Unit
    suspend fun setSoundsEnabled(enabled: Boolean) = Unit

    /** Records that starter [topic] was added whole to coast [coastId]. */
    suspend fun markStarterTopicAdded(coastId: Int, topic: String) = Unit

    /** Stores the user's [profile]. */
    suspend fun setProfile(profile: Profile) = Unit

    /**
     * Stores one blank of the profile ([placeholder], see [Profile.PLACEHOLDERS]) as [value], in one atomic edit.
     * Fields are saved as they are typed, so a read-modify-write of the whole profile would let two keystrokes race.
     */
    suspend fun setProfileField(placeholder: String, value: String) = Unit

    /** Stores the answer to the question with [key] ([PersonalQuestion.key]) in one atomic edit; blank clears it. */
    suspend fun setPersonalAnswer(key: String, value: String) = Unit

    /** Records that the island about the user with [islandId] was written on coast [coastId] from facts with [fingerprint]. */
    suspend fun markPersonalIslandBuilt(coastId: Int, islandId: String, fingerprint: String) = Unit

    /** Stores how sentences about the user are worded. */
    suspend fun setWording(wording: Wording) = Unit

    /** Stores how much new a daily session brings. */
    suspend fun setPace(pace: Pace) = Unit

    /** Records that reaching [level] on coast [coastId] was celebrated, so it isn't again. */
    suspend fun markLevelCelebrated(coastId: Int, level: Level) = Unit

    /** Records that Today's invitation to tell the app about oneself was waved away. */
    suspend fun dismissAboutYouInvite() = Unit

    /** Records that the user allowed the app to send text to cloud [provider]. */
    suspend fun allowAiSharing(provider: TranslationProvider) = Unit

    /** Clears [provider]'s saved API key and withdraws the permission to send text to it. */
    suspend fun removeApiKey(provider: TranslationProvider) = Unit

    /** Whether nothing at all is stored: a fresh install, or settings lost when the phone was restored from a backup. */
    suspend fun isEmpty(): Boolean = false

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
    suspend fun migrateLegacySecrets() {}

    /** Recent practice-sheet anchors and target sentences for coast [coastId], bounded in size. */
    fun worksheetHistory(coastId: Int): Flow<WorksheetHistory> = flowOf(WorksheetHistory())

    /** Appends [anchoredCards] and [targetSentences] to coast [coastId]'s bounded practice-sheet history. */
    suspend fun recordWorksheet(coastId: Int, anchoredCards: List<String>, targetSentences: List<String>) = Unit
}

/** [SettingsRepository] backed by Preferences DataStore and hardware-backed [SecretStore]; days are counted in [timeZone]. */
class DataStoreSettingsRepository(
    private val dataStore: DataStore<Preferences>,
    private val clock: Clock,
    private val timeZone: TimeZone,
    private val secretStore: SecretStore = InMemorySecretStore()
) : SettingsRepository {

    override val nativeLanguage: Flow<String> =
        dataStore.data.map { it[NATIVE_LANG] ?: SettingsDefaults.NATIVE_LANGUAGE }

    override val activeCoastId: Flow<Int?> =
        dataStore.data.map { it[ACTIVE_COAST_ID] }

    override val translationProvider: Flow<TranslationProvider> = combine(dataStore.data, secretStore.version) { preferences, _ ->
        val hasGeminiKey = !readSecret(preferences, API_KEY).isBlank()
        preferences[TRANSLATION_PROVIDER]?.let { name -> TranslationProvider.entries.firstOrNull { it.name == name } }
            // A provider this phone can't run (Local AI in settings that came from another phone) counts as none picked.
            ?.takeUnless { (it == TranslationProvider.LocalAi && !localAiAvailable) || (it == TranslationProvider.OnDevice && !onDeviceTranslationAvailable) }
            // Before providers could be picked, Gemini was the only one; keep it for users who set it up.
            ?: if (onDeviceTranslationAvailable && !hasGeminiKey) TranslationProvider.OnDevice else TranslationProvider.Gemini
    }

    override val apiKey: Flow<String> = secretFlow(API_KEY)

    override val geminiModel: Flow<String> =
        // A model picked in an earlier version may be retired by now; show and use its replacement.
        dataStore.data.map { normalizeGeminiModel(it[GEMINI_MODEL] ?: SettingsDefaults.GEMINI_MODEL) }

    override val openRouterKey: Flow<String> = secretFlow(OPENROUTER_KEY)

    override val openRouterModel: Flow<String> =
        dataStore.data.map { it[OPENROUTER_MODEL] ?: SettingsDefaults.OPENROUTER_MODEL }

    override val localAiModel: Flow<String> =
        dataStore.data.map { it[LOCAL_AI_MODEL] ?: SettingsDefaults.LOCAL_AI_MODEL }

    override val openAiKey: Flow<String> = secretFlow(OPENAI_KEY)

    override val openAiModel: Flow<String> =
        dataStore.data.map { it[OPENAI_MODEL] ?: SettingsDefaults.OPENAI_MODEL }

    override val claudeKey: Flow<String> = secretFlow(CLAUDE_KEY)

    override val claudeModel: Flow<String> =
        dataStore.data.map { it[CLAUDE_MODEL] ?: SettingsDefaults.CLAUDE_MODEL }

    override val groqKey: Flow<String> = secretFlow(GROQ_KEY)

    override val huggingFaceToken: Flow<String> = secretFlow(HUGGING_FACE_TOKEN)

    override val groqModel: Flow<String> =
        dataStore.data.map { it[GROQ_MODEL] ?: SettingsDefaults.GROQ_MODEL }

    override val deepSeekKey: Flow<String> = secretFlow(DEEPSEEK_KEY)

    override val deepSeekModel: Flow<String> =
        dataStore.data.map { it[DEEPSEEK_MODEL] ?: SettingsDefaults.DEEPSEEK_MODEL }

    override val streakCount: Flow<Int> =
        dataStore.data.map { it[STREAK_COUNT] ?: 0 }

    override val studyDays: Flow<Set<LocalDate>> = dataStore.data.map { preferences ->
        val recorded = preferences[STUDY_DAYS].orEmpty().mapNotNullTo(HashSet<LocalDate>()) { parseDateOrNull(it) }
        // Older versions only stored the streak, so its days count as studied too.
        val lastStudy = preferences[LAST_STUDY_DATE]?.let(::parseDateOrNull)
        val streak = preferences[STREAK_COUNT] ?: 0
        // No reader looks back further than a month; a streak of any size must not cost more than this.
        if (lastStudy != null) (0 until minOf(streak, 366)).forEach { recorded += lastStudy.minus(it, DateTimeUnit.DAY) }
        recorded
    }

    override val lastStudyDate: Flow<LocalDate?> = dataStore.data.map { preferences -> preferences[LAST_STUDY_DATE]?.let(::parseDateOrNull) }

    override val starterTopicsAdded: Flow<Set<String>> = dataStore.data.map { it[STARTER_TOPICS_ADDED].orEmpty() }

    override val profile: Flow<Profile> = dataStore.data.map { it.readProfile() }

    override suspend fun setProfile(profile: Profile) {
        dataStore.edit { preferences ->
            PROFILE_KEYS.forEach { (placeholder, key) ->
                val value = profile.fillers()[placeholder]
                if (value == null) preferences.remove(key) else preferences[key] = value
            }
        }
    }

    override suspend fun setProfileField(placeholder: String, value: String) {
        val key = PROFILE_KEYS[placeholder] ?: return
        dataStore.edit { preferences -> if (value.isBlank()) preferences.remove(key) else preferences[key] = value }
    }

    override suspend fun markStarterTopicAdded(coastId: Int, topic: String) {
        dataStore.edit { it[STARTER_TOPICS_ADDED] = it[STARTER_TOPICS_ADDED].orEmpty() + starterTopicKey(coastId, topic) }
    }

    override val personalAnswers: Flow<Map<String, String>> = dataStore.data.map { it.readAnswers() }

    override val personalIslandsBuilt: Flow<Map<String, String>> = dataStore.data.map { it.readBuilt() }

    override val wording: Flow<Wording> = dataStore.data.map { Wording.fromKey(it[WORDING]) }
    override val pace: Flow<Pace> = dataStore.data.map { Pace.fromKey(it[PACE]) }
    override val aboutYouInviteDismissed: Flow<Boolean> = dataStore.data.map { it[ABOUT_YOU_INVITE_DISMISSED] ?: false }

    override suspend fun setWording(wording: Wording) {
        dataStore.edit { preferences -> if (wording == Wording.Unspecified) preferences.remove(WORDING) else preferences[WORDING] = wording.key }
    }
    override suspend fun setPace(pace: Pace) = set(PACE, pace.key)
    override val levelsCelebrated: Flow<Set<String>> = dataStore.data.map { it[LEVELS_CELEBRATED].orEmpty() }
    override suspend fun markLevelCelebrated(coastId: Int, level: Level) {
        dataStore.edit { it[LEVELS_CELEBRATED] = it[LEVELS_CELEBRATED].orEmpty() + levelKey(coastId, level) }
    }
    override suspend fun dismissAboutYouInvite() = set(ABOUT_YOU_INVITE_DISMISSED, true)

    override suspend fun setPersonalAnswer(key: String, value: String) {
        val preference = ANSWER_KEYS[key] ?: return
        dataStore.edit { preferences -> if (value.isBlank()) preferences.remove(preference) else preferences[preference] = value }
    }

    override suspend fun markPersonalIslandBuilt(coastId: Int, islandId: String, fingerprint: String) {
        dataStore.edit { preferences ->
            val others = preferences.readBuilt() - personalIslandKey(coastId, islandId)
            preferences[PERSONAL_ISLANDS_BUILT] = (others + (personalIslandKey(coastId, islandId) to fingerprint)).mapTo(HashSet()) { "${it.key}=${it.value}" }
        }
    }

    override val reminderSettings: Flow<ReminderSettings> = dataStore.data.map { preferences ->
        ReminderSettings(
            enabled = preferences[REMINDER_ENABLED] ?: true,
            // A minute outside the day can't be a time; such a value counts as "no fixed time".
            time = preferences[REMINDER_MINUTE_OF_DAY]?.takeIf { it in MINUTES_OF_DAY }?.let { LocalTime.fromSecondOfDay(it * 60) }
        )
    }

    override val studyReversed: Flow<Boolean> = dataStore.data.map { it[STUDY_REVERSED] ?: false }

    override val weeklyGoal: Flow<Int> = dataStore.data.map { it[WEEKLY_GOAL] ?: SettingsDefaults.WEEKLY_GOAL }

    override val freezeUsedOn: Flow<LocalDate?> = dataStore.data.map { preferences -> preferences[FREEZE_USED_ON]?.let(::parseDateOrNull) }

    override val themeMode: Flow<String> = dataStore.data.map { it[THEME_MODE] ?: SettingsDefaults.THEME_MODE }

    override val hapticsEnabled: Flow<Boolean> = dataStore.data.map { it[HAPTICS_ENABLED] ?: true }

    override val soundsEnabled: Flow<Boolean> = dataStore.data.map { it[SOUNDS_ENABLED] ?: true }

    override val aiSharingAllowed: Flow<Set<String>> = dataStore.data.map { it[AI_SHARING_ALLOWED].orEmpty() }

    override suspend fun allowAiSharing(provider: TranslationProvider) {
        if (!provider.isCloud) return
        dataStore.edit { it[AI_SHARING_ALLOWED] = it[AI_SHARING_ALLOWED].orEmpty() + provider.name }
    }

    override suspend fun removeApiKey(provider: TranslationProvider) {
        val key = apiKeyPreference(provider) ?: return
        secretStore.remove(key.name)
        dataStore.edit { preferences ->
            preferences.remove(key)
            val allowed = preferences[AI_SHARING_ALLOWED].orEmpty() - provider.name
            if (allowed.isEmpty()) preferences.remove(AI_SHARING_ALLOWED) else preferences[AI_SHARING_ALLOWED] = allowed
        }
    }

    override suspend fun isEmpty(): Boolean =
        dataStore.data.first().asMap().isEmpty() && SECRET_KEYS.all { secretStore.get(it.name).isNullOrEmpty() }

    override suspend fun setNativeLanguage(language: String) = set(NATIVE_LANG, language)
    override suspend fun setStudyReversed(reversed: Boolean) = set(STUDY_REVERSED, reversed)
    override suspend fun setWeeklyGoal(islands: Int) = set(WEEKLY_GOAL, islands)
    override suspend fun setThemeMode(mode: String) = set(THEME_MODE, mode)
    override suspend fun setHapticsEnabled(enabled: Boolean) = set(HAPTICS_ENABLED, enabled)
    override suspend fun setSoundsEnabled(enabled: Boolean) = set(SOUNDS_ENABLED, enabled)
    override suspend fun setActiveCoastId(coastId: Int) = set(ACTIVE_COAST_ID, coastId)
    override suspend fun setTranslationProvider(provider: TranslationProvider) = set(TRANSLATION_PROVIDER, provider.name)
    override suspend fun setApiKey(key: String) = setKey(TranslationProvider.Gemini, key)
    override suspend fun setGeminiModel(model: String) = set(GEMINI_MODEL, model)
    override suspend fun setOpenRouterKey(key: String) = setKey(TranslationProvider.OpenRouter, key)
    override suspend fun setOpenRouterModel(model: String) = set(OPENROUTER_MODEL, model)
    override suspend fun setLocalAiModel(modelId: String) = set(LOCAL_AI_MODEL, modelId)
    override suspend fun setOpenAiKey(key: String) = setKey(TranslationProvider.OpenAI, key)
    override suspend fun setOpenAiModel(model: String) = set(OPENAI_MODEL, model)
    override suspend fun setClaudeKey(key: String) = setKey(TranslationProvider.Claude, key)
    override suspend fun setClaudeModel(model: String) = set(CLAUDE_MODEL, model)
    override suspend fun setGroqKey(key: String) = setKey(TranslationProvider.Groq, key)
    override suspend fun setHuggingFaceToken(token: String) {
        // A blank token is no token: removing it leaves nothing of it behind.
        if (token.isBlank()) {
            secretStore.remove(HUGGING_FACE_TOKEN.name)
        } else {
            secretStore.put(HUGGING_FACE_TOKEN.name, token)
        }
        // Clear any legacy plaintext copy from DataStore.
        dataStore.edit { it.remove(HUGGING_FACE_TOKEN) }
    }
    override suspend fun setGroqModel(model: String) = set(GROQ_MODEL, model)
    override suspend fun setDeepSeekKey(key: String) = setKey(TranslationProvider.DeepSeek, key)
    override suspend fun setDeepSeekModel(model: String) = set(DEEPSEEK_MODEL, model)

    override fun modelOptions(provider: TranslationProvider): Flow<List<String>> =
        dataStore.data.map { preferences ->
            preferences[modelOptionsKey(provider)]?.lines()?.filter { it.isNotBlank() }?.takeIf { it.isNotEmpty() }
                ?: SettingsDefaults.models(provider)
        }

    override suspend fun setModelOptions(provider: TranslationProvider, models: List<String>) =
        set(modelOptionsKey(provider), models.joinToString("\n"))

    /** One model id per line. Not part of a backup: a check for new models brings the list back. */
    private fun modelOptionsKey(provider: TranslationProvider) = stringPreferencesKey("model_options_${provider.name.lowercase()}")

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

    /** Not part of a backup: local freshness ring buffers per coast. */
    private fun worksheetAnchorsKey(coastId: Int) = stringPreferencesKey("worksheet_anchors_$coastId")
    private fun worksheetSentencesKey(coastId: Int) = stringPreferencesKey("worksheet_sentences_$coastId")

    override suspend fun setReminderSettings(reminder: ReminderSettings) {
        dataStore.edit { preferences ->
            preferences[REMINDER_ENABLED] = reminder.enabled
            val time = reminder.time
            if (time == null) preferences.remove(REMINDER_MINUTE_OF_DAY)
            else preferences[REMINDER_MINUTE_OF_DAY] = time.hour * 60 + time.minute
        }
    }

    override suspend fun recordStudySession() {
        dataStore.edit { preferences ->
            val today = clock.todayIn(timeZone)
            val lastStudy = preferences[LAST_STUDY_DATE]?.let(::parseDateOrNull)
            val freezeUsedOn = preferences[FREEZE_USED_ON]?.let(::parseDateOrNull)
            preferences[STREAK_COUNT] = nextStreak(lastStudy, preferences[STREAK_COUNT] ?: 0, today, freezeUsedOn)
            preferences[LAST_STUDY_DATE] = today.toString()
            // Keep a few weeks of study days for the weekly overview.
            val cutoff = today.minus(STUDY_DAYS_KEPT, DateTimeUnit.DAY)
            preferences[STUDY_DAYS] = (preferences[STUDY_DAYS].orEmpty() + today.toString())
                .filterTo(HashSet()) { day -> parseDateOrNull(day)?.let { it >= cutoff } == true }
        }
    }

    override suspend fun refreshStreak() {
        dataStore.edit { preferences ->
            val lastStudy = preferences[LAST_STUDY_DATE]?.let(::parseDateOrNull) ?: return@edit
            val freezeUsedOn = preferences[FREEZE_USED_ON]?.let(::parseDateOrNull)
            val refreshed = refreshedStreak(lastStudy, preferences[STREAK_COUNT] ?: 0, freezeUsedOn, clock.todayIn(timeZone))
            preferences[STREAK_COUNT] = refreshed.streak
            if (refreshed.freezeUsedOn == null) preferences.remove(FREEZE_USED_ON) else preferences[FREEZE_USED_ON] = refreshed.freezeUsedOn.toString()
        }
    }

    override suspend fun backupSettings(): BackupSettings {
        val preferences = dataStore.data.first()
        return BackupSettings(
            nativeLanguage = preferences[NATIVE_LANG] ?: SettingsDefaults.NATIVE_LANGUAGE,
            activeCoastId = preferences[ACTIVE_COAST_ID],
            translationProvider = preferences[TRANSLATION_PROVIDER],
            geminiModel = preferences[GEMINI_MODEL],
            openRouterModel = preferences[OPENROUTER_MODEL],
            localAiModel = preferences[LOCAL_AI_MODEL],
            openAiModel = preferences[OPENAI_MODEL],
            claudeModel = preferences[CLAUDE_MODEL],
            groqModel = preferences[GROQ_MODEL],
            deepSeekModel = preferences[DEEPSEEK_MODEL],
            streakCount = preferences[STREAK_COUNT] ?: 0,
            lastStudyDate = preferences[LAST_STUDY_DATE],
            studyDays = preferences[STUDY_DAYS].orEmpty().sorted(),
            reminderEnabled = preferences[REMINDER_ENABLED] ?: true,
            reminderMinuteOfDay = preferences[REMINDER_MINUTE_OF_DAY],
            studyReversed = preferences[STUDY_REVERSED] ?: false,
            weeklyGoal = preferences[WEEKLY_GOAL],
            freezeUsedOn = preferences[FREEZE_USED_ON],
            themeMode = preferences[THEME_MODE],
            hapticsEnabled = preferences[HAPTICS_ENABLED] ?: true,
            soundsEnabled = preferences[SOUNDS_ENABLED] ?: true,
            starterTopicsAdded = preferences[STARTER_TOPICS_ADDED].orEmpty().sorted(),
            profile = preferences.readProfile().fillers(),
            personalAnswers = preferences.readAnswers(),
            personalIslandsBuilt = preferences.readBuilt(),
            wording = preferences[WORDING],
            pace = preferences[PACE]
        )
    }

    override suspend fun restoreSettings(backup: BackupSettings) {
        dataStore.edit { preferences ->
            // Sets [key] to [value], or removes it so its default applies.
            fun <T> put(key: Preferences.Key<T>, value: T?) {
                if (value == null) preferences.remove(key) else preferences[key] = value
            }
            put(NATIVE_LANG, backup.nativeLanguage)
            put(ACTIVE_COAST_ID, backup.activeCoastId)
            // A provider this version doesn't know falls back to the default.
            put(TRANSLATION_PROVIDER, backup.translationProvider?.takeIf { name -> TranslationProvider.entries.any { it.name == name } })
            put(GEMINI_MODEL, backup.geminiModel?.let(::normalizeGeminiModel))
            put(OPENROUTER_MODEL, backup.openRouterModel)
            put(LOCAL_AI_MODEL, backup.localAiModel)
            put(OPENAI_MODEL, backup.openAiModel)
            put(CLAUDE_MODEL, backup.claudeModel)
            put(GROQ_MODEL, backup.groqModel)
            put(DEEPSEEK_MODEL, backup.deepSeekModel)
            // A file can hold anything; only dates and times that can be read back are written (see [isReadable]).
            put(STREAK_COUNT, backup.streakCount.coerceAtLeast(0))
            put(LAST_STUDY_DATE, backup.lastStudyDate?.takeIf { parseDateOrNull(it) != null })
            put(STUDY_DAYS, backup.studyDays.filterTo(HashSet<String>()) { parseDateOrNull(it) != null })
            put(REMINDER_ENABLED, backup.reminderEnabled)
            put(REMINDER_MINUTE_OF_DAY, backup.reminderMinuteOfDay?.takeIf { it in MINUTES_OF_DAY })
            put(STUDY_REVERSED, backup.studyReversed)
            put(WEEKLY_GOAL, backup.weeklyGoal)
            put(FREEZE_USED_ON, backup.freezeUsedOn?.takeIf { parseDateOrNull(it) != null })
            put(THEME_MODE, backup.themeMode)
            put(HAPTICS_ENABLED, backup.hapticsEnabled)
            put(SOUNDS_ENABLED, backup.soundsEnabled)
            put(STARTER_TOPICS_ADDED, backup.starterTopicsAdded.toSet().takeIf { it.isNotEmpty() })
            PROFILE_KEYS.forEach { (placeholder, key) -> put(key, backup.profile[placeholder]?.takeIf { it.isNotBlank() }) }
            ANSWER_KEYS.forEach { (question, key) -> put(key, backup.personalAnswers[question]?.takeIf { it.isNotBlank() }) }
            put(WORDING, backup.wording?.takeIf { key -> Wording.entries.any { it.key == key && it != Wording.Unspecified } })
            put(PACE, backup.pace?.takeIf { key -> Pace.entries.any { it.key == key } })
            put(PERSONAL_ISLANDS_BUILT, backup.personalIslandsBuilt.mapTo(HashSet()) { "${it.key}=${it.value}" }.takeIf { it.isNotEmpty() })
        }
    }

    /** Stores [value] under [key]. */
    private suspend fun <T> set(key: Preferences.Key<T>, value: T) {
        dataStore.edit { it[key] = value }
    }

    /** Stores [provider]'s API key in [secretStore]. A blank one removes the key and revokes AI sharing consent. */
    private suspend fun setKey(provider: TranslationProvider, key: String) {
        val preference = apiKeyPreference(provider) ?: return
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
        val toRemove = SECRET_KEYS.filter { key ->
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
        PROFILE_KEYS.entries.fold(Profile()) { profile, (placeholder, key) -> profile.with(placeholder, this[key].orEmpty()) }

    private fun Preferences.readAnswers(): Map<String, String> =
        ANSWER_KEYS.mapNotNull { (question, key) -> this[key]?.takeIf { it.isNotBlank() }?.let { question to it } }.toMap()

    /** Entries are stored as "coastId:islandId=fingerprint". */
    private fun Preferences.readBuilt(): Map<String, String> =
        this[PERSONAL_ISLANDS_BUILT].orEmpty().mapNotNull { entry ->
            entry.substringBefore('=', "").takeIf { it.isNotEmpty() }?.let { it to entry.substringAfter('=') }
        }.toMap()

    private companion object {
        val NATIVE_LANG = stringPreferencesKey("native_language")
        val ACTIVE_COAST_ID = intPreferencesKey("active_coast_id")
        val API_KEY = stringPreferencesKey("gemini_api_key")
        val GEMINI_MODEL = stringPreferencesKey("gemini_model")
        val TRANSLATION_PROVIDER = stringPreferencesKey("translation_provider")
        val OPENROUTER_KEY = stringPreferencesKey("openrouter_api_key")
        val OPENROUTER_MODEL = stringPreferencesKey("openrouter_model")
        val LOCAL_AI_MODEL = stringPreferencesKey("local_ai_model")
        val OPENAI_KEY = stringPreferencesKey("openai_api_key")
        val OPENAI_MODEL = stringPreferencesKey("openai_model")
        val CLAUDE_KEY = stringPreferencesKey("claude_api_key")
        val CLAUDE_MODEL = stringPreferencesKey("claude_model")
        val GROQ_KEY = stringPreferencesKey("groq_api_key")
        // Like the API keys, never part of a backup.
        val HUGGING_FACE_TOKEN = stringPreferencesKey("hugging_face_token")
        val GROQ_MODEL = stringPreferencesKey("groq_model")
        val DEEPSEEK_KEY = stringPreferencesKey("deepseek_api_key")
        val DEEPSEEK_MODEL = stringPreferencesKey("deepseek_model")
        val STREAK_COUNT = intPreferencesKey("streak_count")
        val LAST_STUDY_DATE = stringPreferencesKey("last_study_date")
        val STUDY_DAYS = stringSetPreferencesKey("study_days")
        val REMINDER_ENABLED = booleanPreferencesKey("reminder_enabled")
        val REMINDER_MINUTE_OF_DAY = intPreferencesKey("reminder_minute_of_day")
        val STUDY_REVERSED = booleanPreferencesKey("study_reversed")
        val WEEKLY_GOAL = intPreferencesKey("weekly_goal")
        val FREEZE_USED_ON = stringPreferencesKey("freeze_used_on")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val HAPTICS_ENABLED = booleanPreferencesKey("haptics_enabled")
        val SOUNDS_ENABLED = booleanPreferencesKey("sounds_enabled")
        val STARTER_TOPICS_ADDED = stringSetPreferencesKey("starter_topics_added")
        /** One key per profile blank, named after the placeholder. */
        val PROFILE_KEYS: Map<String, Preferences.Key<String>> =
            Profile.PLACEHOLDERS.associateWith { stringPreferencesKey("profile_" + it.replace(' ', '_')) }
        /** One key per question the app asks about the user. */
        val ANSWER_KEYS: Map<String, Preferences.Key<String>> =
            PersonalIslands.QUESTIONS.associate { it.key to stringPreferencesKey("about_" + it.key) }
        val PERSONAL_ISLANDS_BUILT = stringSetPreferencesKey("personal_islands_built")
        val WORDING = stringPreferencesKey("about_wording")
        val PACE = stringPreferencesKey("study_pace")
        /** Not part of a backup: after a restore a reached level is simply congratulated on once more. */
        val LEVELS_CELEBRATED = stringSetPreferencesKey("levels_celebrated")
        val ABOUT_YOU_INVITE_DISMISSED = booleanPreferencesKey("about_you_invite_dismissed")
        /** Not part of a backup, like the keys it goes with: permission is given on the phone that sends the text. */
        val AI_SHARING_ALLOWED = stringSetPreferencesKey("ai_sharing_allowed")
        const val STUDY_DAYS_KEPT = 28
        const val WORKSHEET_ANCHORS_KEPT = 80
        const val WORKSHEET_SENTENCES_KEPT = 60

        fun mergeBoundedRing(existing: List<String>, added: List<String>, limit: Int): List<String> {
            val sanitized = added.map { it.replace('\n', ' ').replace('\r', ' ').trim() }.filter { it.isNotEmpty() }
            val addedSet = sanitized.toHashSet()
            return (existing.filterNot { it in addedSet } + sanitized.distinct()).takeLast(limit)
        }

        /** The preference holding [provider]'s API key; null for the providers that need none. */
        fun apiKeyPreference(provider: TranslationProvider): Preferences.Key<String>? = when (provider) {
            TranslationProvider.Gemini -> API_KEY
            TranslationProvider.OpenAI -> OPENAI_KEY
            TranslationProvider.Claude -> CLAUDE_KEY
            TranslationProvider.Groq -> GROQ_KEY
            TranslationProvider.DeepSeek -> DEEPSEEK_KEY
            TranslationProvider.OpenRouter -> OPENROUTER_KEY
            TranslationProvider.OnDevice, TranslationProvider.LocalAi -> null
        }

        val SECRET_KEYS: List<Preferences.Key<String>> = listOf(
            API_KEY, OPENROUTER_KEY, OPENAI_KEY, CLAUDE_KEY, GROQ_KEY, DEEPSEEK_KEY, HUGGING_FACE_TOKEN
        )
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

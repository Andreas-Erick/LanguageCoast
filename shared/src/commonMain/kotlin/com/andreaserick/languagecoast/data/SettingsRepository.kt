package com.andreaserick.languagecoast.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.flow.Flow
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
    /** Consecutive days with a completed study session; [refreshStreak] resets it once a day is missed. */
    val streakCount: Flow<Int>
    /** Days with a completed study session; at least the current streak's days, plus recent days recorded since. */
    val studyDays: Flow<Set<LocalDate>>
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
    /** Stores whether and when the daily study reminder is shown. */
    suspend fun setReminderSettings(reminder: ReminderSettings)
    /** Stores whether cards are studied the other way round. */
    suspend fun setStudyReversed(reversed: Boolean)
    suspend fun setWeeklyGoal(islands: Int) = Unit
    suspend fun setThemeMode(mode: String) = Unit
    suspend fun setHapticsEnabled(enabled: Boolean) = Unit
    suspend fun setSoundsEnabled(enabled: Boolean) = Unit

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
}

/** [SettingsRepository] backed by Preferences DataStore; days are counted in [timeZone]. */
class DataStoreSettingsRepository(
    private val dataStore: DataStore<Preferences>,
    private val clock: Clock,
    private val timeZone: TimeZone
) : SettingsRepository {

    override val nativeLanguage: Flow<String> =
        dataStore.data.map { it[NATIVE_LANG] ?: SettingsDefaults.NATIVE_LANGUAGE }

    override val activeCoastId: Flow<Int?> =
        dataStore.data.map { it[ACTIVE_COAST_ID] }

    override val translationProvider: Flow<TranslationProvider> = dataStore.data.map { preferences ->
        preferences[TRANSLATION_PROVIDER]?.let { name -> TranslationProvider.entries.firstOrNull { it.name == name } }
            // Before providers could be picked, Gemini was the only one; keep it for users who set it up.
            ?: if (onDeviceTranslationAvailable && preferences[API_KEY].isNullOrBlank()) TranslationProvider.OnDevice else TranslationProvider.Gemini
    }

    override val apiKey: Flow<String> =
        dataStore.data.map { it[API_KEY] ?: "" }

    override val geminiModel: Flow<String> =
        dataStore.data.map { it[GEMINI_MODEL] ?: SettingsDefaults.GEMINI_MODEL }

    override val openRouterKey: Flow<String> =
        dataStore.data.map { it[OPENROUTER_KEY] ?: "" }

    override val openRouterModel: Flow<String> =
        dataStore.data.map { it[OPENROUTER_MODEL] ?: SettingsDefaults.OPENROUTER_MODEL }

    override val localAiModel: Flow<String> =
        dataStore.data.map { it[LOCAL_AI_MODEL] ?: SettingsDefaults.LOCAL_AI_MODEL }

    override val openAiKey: Flow<String> =
        dataStore.data.map { it[OPENAI_KEY] ?: "" }

    override val openAiModel: Flow<String> =
        dataStore.data.map { it[OPENAI_MODEL] ?: SettingsDefaults.OPENAI_MODEL }

    override val claudeKey: Flow<String> =
        dataStore.data.map { it[CLAUDE_KEY] ?: "" }

    override val claudeModel: Flow<String> =
        dataStore.data.map { it[CLAUDE_MODEL] ?: SettingsDefaults.CLAUDE_MODEL }

    override val groqKey: Flow<String> =
        dataStore.data.map { it[GROQ_KEY] ?: "" }

    override val huggingFaceToken: Flow<String> =
        dataStore.data.map { it[HUGGING_FACE_TOKEN] ?: "" }

    override val groqModel: Flow<String> =
        dataStore.data.map { it[GROQ_MODEL] ?: SettingsDefaults.GROQ_MODEL }

    override val deepSeekKey: Flow<String> =
        dataStore.data.map { it[DEEPSEEK_KEY] ?: "" }

    override val deepSeekModel: Flow<String> =
        dataStore.data.map { it[DEEPSEEK_MODEL] ?: SettingsDefaults.DEEPSEEK_MODEL }

    override val streakCount: Flow<Int> =
        dataStore.data.map { it[STREAK_COUNT] ?: 0 }

    override val studyDays: Flow<Set<LocalDate>> = dataStore.data.map { preferences ->
        val recorded = preferences[STUDY_DAYS].orEmpty().mapTo(HashSet()) { LocalDate.parse(it) }
        // Older versions only stored the streak, so its days count as studied too.
        val lastStudy = preferences[LAST_STUDY_DATE]?.let { LocalDate.parse(it) }
        val streak = preferences[STREAK_COUNT] ?: 0
        if (lastStudy != null) (0 until streak).forEach { recorded += lastStudy.minus(it, DateTimeUnit.DAY) }
        recorded
    }

    override val reminderSettings: Flow<ReminderSettings> = dataStore.data.map { preferences ->
        ReminderSettings(
            enabled = preferences[REMINDER_ENABLED] ?: true,
            time = preferences[REMINDER_MINUTE_OF_DAY]?.let { LocalTime.fromSecondOfDay(it * 60) }
        )
    }

    override val studyReversed: Flow<Boolean> = dataStore.data.map { it[STUDY_REVERSED] ?: false }

    override val weeklyGoal: Flow<Int> = dataStore.data.map { it[WEEKLY_GOAL] ?: SettingsDefaults.WEEKLY_GOAL }

    override val freezeUsedOn: Flow<LocalDate?> = dataStore.data.map { preferences -> preferences[FREEZE_USED_ON]?.let { LocalDate.parse(it) } }

    override val themeMode: Flow<String> = dataStore.data.map { it[THEME_MODE] ?: SettingsDefaults.THEME_MODE }

    override val hapticsEnabled: Flow<Boolean> = dataStore.data.map { it[HAPTICS_ENABLED] ?: true }

    override val soundsEnabled: Flow<Boolean> = dataStore.data.map { it[SOUNDS_ENABLED] ?: true }

    override suspend fun setNativeLanguage(language: String) = set(NATIVE_LANG, language)
    override suspend fun setStudyReversed(reversed: Boolean) = set(STUDY_REVERSED, reversed)
    override suspend fun setWeeklyGoal(islands: Int) = set(WEEKLY_GOAL, islands)
    override suspend fun setThemeMode(mode: String) = set(THEME_MODE, mode)
    override suspend fun setHapticsEnabled(enabled: Boolean) = set(HAPTICS_ENABLED, enabled)
    override suspend fun setSoundsEnabled(enabled: Boolean) = set(SOUNDS_ENABLED, enabled)
    override suspend fun setActiveCoastId(coastId: Int) = set(ACTIVE_COAST_ID, coastId)
    override suspend fun setTranslationProvider(provider: TranslationProvider) = set(TRANSLATION_PROVIDER, provider.name)
    override suspend fun setApiKey(key: String) = set(API_KEY, key)
    override suspend fun setGeminiModel(model: String) = set(GEMINI_MODEL, model)
    override suspend fun setOpenRouterKey(key: String) = set(OPENROUTER_KEY, key)
    override suspend fun setOpenRouterModel(model: String) = set(OPENROUTER_MODEL, model)
    override suspend fun setLocalAiModel(modelId: String) = set(LOCAL_AI_MODEL, modelId)
    override suspend fun setOpenAiKey(key: String) = set(OPENAI_KEY, key)
    override suspend fun setOpenAiModel(model: String) = set(OPENAI_MODEL, model)
    override suspend fun setClaudeKey(key: String) = set(CLAUDE_KEY, key)
    override suspend fun setClaudeModel(model: String) = set(CLAUDE_MODEL, model)
    override suspend fun setGroqKey(key: String) = set(GROQ_KEY, key)
    override suspend fun setHuggingFaceToken(token: String) = set(HUGGING_FACE_TOKEN, token)
    override suspend fun setGroqModel(model: String) = set(GROQ_MODEL, model)
    override suspend fun setDeepSeekKey(key: String) = set(DEEPSEEK_KEY, key)
    override suspend fun setDeepSeekModel(model: String) = set(DEEPSEEK_MODEL, model)

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
            val lastStudy = preferences[LAST_STUDY_DATE]?.let { LocalDate.parse(it) }
            val freezeUsedOn = preferences[FREEZE_USED_ON]?.let { LocalDate.parse(it) }
            preferences[STREAK_COUNT] = nextStreak(lastStudy, preferences[STREAK_COUNT] ?: 0, today, freezeUsedOn)
            preferences[LAST_STUDY_DATE] = today.toString()
            // Keep a few weeks of study days for the weekly overview.
            val cutoff = today.minus(STUDY_DAYS_KEPT, DateTimeUnit.DAY)
            preferences[STUDY_DAYS] = (preferences[STUDY_DAYS].orEmpty() + today.toString())
                .filterTo(HashSet()) { LocalDate.parse(it) >= cutoff }
        }
    }

    override suspend fun refreshStreak() {
        dataStore.edit { preferences ->
            val lastStudy = preferences[LAST_STUDY_DATE]?.let { LocalDate.parse(it) } ?: return@edit
            val freezeUsedOn = preferences[FREEZE_USED_ON]?.let { LocalDate.parse(it) }
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
            soundsEnabled = preferences[SOUNDS_ENABLED] ?: true
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
            put(GEMINI_MODEL, backup.geminiModel)
            put(OPENROUTER_MODEL, backup.openRouterModel)
            put(LOCAL_AI_MODEL, backup.localAiModel)
            put(OPENAI_MODEL, backup.openAiModel)
            put(CLAUDE_MODEL, backup.claudeModel)
            put(GROQ_MODEL, backup.groqModel)
            put(DEEPSEEK_MODEL, backup.deepSeekModel)
            put(STREAK_COUNT, backup.streakCount)
            put(LAST_STUDY_DATE, backup.lastStudyDate)
            put(STUDY_DAYS, backup.studyDays.toSet())
            put(REMINDER_ENABLED, backup.reminderEnabled)
            put(REMINDER_MINUTE_OF_DAY, backup.reminderMinuteOfDay)
            put(STUDY_REVERSED, backup.studyReversed)
            put(WEEKLY_GOAL, backup.weeklyGoal)
            put(FREEZE_USED_ON, backup.freezeUsedOn)
            put(THEME_MODE, backup.themeMode)
            put(HAPTICS_ENABLED, backup.hapticsEnabled)
            put(SOUNDS_ENABLED, backup.soundsEnabled)
        }
    }

    /** Stores [value] under [key]. */
    private suspend fun <T> set(key: Preferences.Key<T>, value: T) {
        dataStore.edit { it[key] = value }
    }

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
        const val STUDY_DAYS_KEPT = 28
    }
}

/** The DataStore holding the settings, stored in the file at [path]. Only one may exist per file. */
fun createSettingsDataStore(path: String): DataStore<Preferences> =
    PreferenceDataStoreFactory.createWithPath(produceFile = { path.toPath() })

/**
 * The single target language from before coasts existed. Only read by [AppDatabase.migration2To3]
 * to name the coast that existing islands are moved onto.
 */
internal suspend fun DataStore<Preferences>.readLegacyTargetLanguage(): String =
    data.first()[stringPreferencesKey("target_language")] ?: "Spanish"

/**
 * Streak after completing a session [today]: unchanged if already studied today, incremented if the
 * last session was yesterday (or the day before, with a streak freeze on yesterday), otherwise restarted at 1.
 */
internal fun nextStreak(lastStudy: LocalDate?, currentStreak: Int, today: LocalDate, freezeUsedOn: LocalDate? = null): Int {
    val yesterday = today.minus(1, DateTimeUnit.DAY)
    return when {
        lastStudy == null -> 1
        lastStudy == today -> currentStreak
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

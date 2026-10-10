package com.andreaserick.languagecoast.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject

/** Default values and the options offered in Settings. */
object SettingsDefaults {
    const val NATIVE_LANGUAGE = "English"
    const val GEMINI_MODEL = "gemini-2.5-flash"

    /** OpenRouter's Auto Router, which picks a suitable model for each request. */
    const val OPENROUTER_MODEL = "openrouter/auto"

    val GEMINI_MODELS = listOf(
        "gemini-2.5-flash",
        "gemini-3.5-flash",
        "gemini-3.1-pro-preview",
        "gemini-3.1-flash-lite-preview"
    )
}

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
    /** Where translations come from. Until one is picked: Gemini if a Gemini key was saved, otherwise on-device. */
    val translationProvider: Flow<TranslationProvider>
    /** The Gemini API key. */
    val apiKey: Flow<String>
    /** The Gemini model used for translations. */
    val geminiModel: Flow<String>
    /** The OpenRouter API key. */
    val openRouterKey: Flow<String>
    /** The OpenRouter model used for translations. */
    val openRouterModel: Flow<String>
    /** Consecutive days with a completed study session; [refreshStreak] resets it once a day is missed. */
    val streakCount: Flow<Int>
    /** Days with a completed study session; at least the current streak's days, plus recent days recorded since. */
    val studyDays: Flow<Set<LocalDate>>
    /** Whether and when the daily study reminder is shown. */
    val reminderSettings: Flow<ReminderSettings>
    /** Whether cards are studied the other way round: from the coast's language to the native language. */
    val studyReversed: Flow<Boolean>

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
    /** Stores whether and when the daily study reminder is shown. */
    suspend fun setReminderSettings(reminder: ReminderSettings)
    /** Stores whether cards are studied the other way round. */
    suspend fun setStudyReversed(reversed: Boolean)

    /** Records a completed study session for today and updates the streak. */
    suspend fun recordStudySession()

    /** Resets the streak if more than a full day has passed since the last session. */
    suspend fun refreshStreak()

    /** Everything stored here except the API keys, for a [Backup]. */
    suspend fun backupSettings(): BackupSettings

    /** Replaces everything stored here with [backup], except the API keys, which are kept. */
    suspend fun restoreSettings(backup: BackupSettings)
}

/** [SettingsRepository] backed by Preferences DataStore. */
class DataStoreSettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    private val clock: Clock
) : SettingsRepository {

    override val nativeLanguage: Flow<String> =
        dataStore.data.map { it[NATIVE_LANG] ?: SettingsDefaults.NATIVE_LANGUAGE }

    override val activeCoastId: Flow<Int?> =
        dataStore.data.map { it[ACTIVE_COAST_ID] }

    override val translationProvider: Flow<TranslationProvider> = dataStore.data.map { preferences ->
        preferences[TRANSLATION_PROVIDER]?.let { name -> TranslationProvider.entries.firstOrNull { it.name == name } }
            // Before providers could be picked, Gemini was the only one; keep it for users who set it up.
            ?: if (preferences[API_KEY].isNullOrBlank()) TranslationProvider.OnDevice else TranslationProvider.Gemini
    }

    override val apiKey: Flow<String> =
        dataStore.data.map { it[API_KEY] ?: "" }

    override val geminiModel: Flow<String> =
        dataStore.data.map { it[GEMINI_MODEL] ?: SettingsDefaults.GEMINI_MODEL }

    override val openRouterKey: Flow<String> =
        dataStore.data.map { it[OPENROUTER_KEY] ?: "" }

    override val openRouterModel: Flow<String> =
        dataStore.data.map { it[OPENROUTER_MODEL] ?: SettingsDefaults.OPENROUTER_MODEL }

    override val streakCount: Flow<Int> =
        dataStore.data.map { it[STREAK_COUNT] ?: 0 }

    override val studyDays: Flow<Set<LocalDate>> = dataStore.data.map { preferences ->
        val recorded = preferences[STUDY_DAYS].orEmpty().mapTo(HashSet()) { LocalDate.parse(it, DATE_FORMATTER) }
        // Older versions only stored the streak, so its days count as studied too.
        val lastStudy = preferences[LAST_STUDY_DATE]?.let { LocalDate.parse(it, DATE_FORMATTER) }
        val streak = preferences[STREAK_COUNT] ?: 0
        if (lastStudy != null) (0 until streak).forEach { recorded += lastStudy.minusDays(it.toLong()) }
        recorded
    }

    override val reminderSettings: Flow<ReminderSettings> = dataStore.data.map { preferences ->
        ReminderSettings(
            enabled = preferences[REMINDER_ENABLED] ?: true,
            time = preferences[REMINDER_MINUTE_OF_DAY]?.let { LocalTime.ofSecondOfDay(it * 60L) }
        )
    }

    override val studyReversed: Flow<Boolean> = dataStore.data.map { it[STUDY_REVERSED] ?: false }

    override suspend fun setNativeLanguage(language: String) = set(NATIVE_LANG, language)
    override suspend fun setStudyReversed(reversed: Boolean) = set(STUDY_REVERSED, reversed)
    override suspend fun setActiveCoastId(coastId: Int) = set(ACTIVE_COAST_ID, coastId)
    override suspend fun setTranslationProvider(provider: TranslationProvider) = set(TRANSLATION_PROVIDER, provider.name)
    override suspend fun setApiKey(key: String) = set(API_KEY, key)
    override suspend fun setGeminiModel(model: String) = set(GEMINI_MODEL, model)
    override suspend fun setOpenRouterKey(key: String) = set(OPENROUTER_KEY, key)
    override suspend fun setOpenRouterModel(model: String) = set(OPENROUTER_MODEL, model)

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
            val today = LocalDate.now(clock)
            val lastStudy = preferences[LAST_STUDY_DATE]?.let { LocalDate.parse(it, DATE_FORMATTER) }
            preferences[STREAK_COUNT] = nextStreak(lastStudy, preferences[STREAK_COUNT] ?: 0, today)
            preferences[LAST_STUDY_DATE] = today.format(DATE_FORMATTER)
            // Keep a few weeks of study days for the weekly overview.
            val cutoff = today.minusDays(STUDY_DAYS_KEPT)
            preferences[STUDY_DAYS] = (preferences[STUDY_DAYS].orEmpty() + today.format(DATE_FORMATTER))
                .filterTo(HashSet()) { !LocalDate.parse(it, DATE_FORMATTER).isBefore(cutoff) }
        }
    }

    override suspend fun refreshStreak() {
        dataStore.edit { preferences ->
            val lastStudy = preferences[LAST_STUDY_DATE]?.let { LocalDate.parse(it, DATE_FORMATTER) } ?: return@edit
            if (isStreakBroken(lastStudy, LocalDate.now(clock))) {
                preferences[STREAK_COUNT] = 0
            }
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
            streakCount = preferences[STREAK_COUNT] ?: 0,
            lastStudyDate = preferences[LAST_STUDY_DATE],
            studyDays = preferences[STUDY_DAYS].orEmpty().sorted(),
            reminderEnabled = preferences[REMINDER_ENABLED] ?: true,
            reminderMinuteOfDay = preferences[REMINDER_MINUTE_OF_DAY],
            studyReversed = preferences[STUDY_REVERSED] ?: false
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
            put(STREAK_COUNT, backup.streakCount)
            put(LAST_STUDY_DATE, backup.lastStudyDate)
            put(STUDY_DAYS, backup.studyDays.toSet())
            put(REMINDER_ENABLED, backup.reminderEnabled)
            put(REMINDER_MINUTE_OF_DAY, backup.reminderMinuteOfDay)
            put(STUDY_REVERSED, backup.studyReversed)
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
        val STREAK_COUNT = intPreferencesKey("streak_count")
        val LAST_STUDY_DATE = stringPreferencesKey("last_study_date")
        val STUDY_DAYS = stringSetPreferencesKey("study_days")
        val REMINDER_ENABLED = booleanPreferencesKey("reminder_enabled")
        val REMINDER_MINUTE_OF_DAY = intPreferencesKey("reminder_minute_of_day")
        val STUDY_REVERSED = booleanPreferencesKey("study_reversed")
        const val STUDY_DAYS_KEPT = 28L
        val DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE
    }
}

/**
 * The single target language from before coasts existed. Only read by [AppDatabase.migration2To3]
 * to name the coast that existing islands are moved onto.
 */
internal suspend fun DataStore<Preferences>.readLegacyTargetLanguage(): String =
    data.first()[stringPreferencesKey("target_language")] ?: "Spanish"

/**
 * Streak after completing a session [today]: unchanged if already studied today,
 * incremented if the last session was yesterday, otherwise restarted at 1.
 */
internal fun nextStreak(lastStudy: LocalDate?, currentStreak: Int, today: LocalDate): Int = when {
    lastStudy == null -> 1
    lastStudy == today -> currentStreak
    lastStudy == today.minusDays(1) -> currentStreak + 1
    else -> 1
}

/** A streak is broken once a full calendar day passes without a session. */
internal fun isStreakBroken(lastStudy: LocalDate, today: LocalDate): Boolean =
    lastStudy.isBefore(today.minusDays(1))

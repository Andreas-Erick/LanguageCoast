package com.andreaserick.languagecoast.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject

/** Default values and the options offered in Settings. */
object SettingsDefaults {
    const val NATIVE_LANGUAGE = "English"
    const val GEMINI_MODEL = "gemini-2.5-flash"

    val SUPPORTED_LANGUAGES = listOf(
        "English",
        "Spanish",
        "French",
        "German",
        "Italian",
        "Japanese",
        "Korean",
        "Icelandic",
        "Norwegian"
    )

    val GEMINI_MODELS = listOf(
        "gemini-2.5-flash",
        "gemini-3.5-flash",
        "gemini-3.1-pro-preview",
        "gemini-3.1-flash-lite-preview"
    )
}

/** Supported languages a new coast can be started for: not the native language and without a coast yet. */
fun availableCoastLanguages(coasts: List<Coast>, nativeLanguage: String): List<String> =
    SettingsDefaults.SUPPORTED_LANGUAGES.filter { language ->
        language != nativeLanguage && coasts.none { it.language == language }
    }

/** User preferences: native language, active coast, Gemini configuration and the study streak. */
interface SettingsRepository {
    val nativeLanguage: Flow<String>
    /** The coast new cards are added to, or null if none was picked yet. */
    val activeCoastId: Flow<Int?>
    val apiKey: Flow<String>
    val geminiModel: Flow<String>
    val streakCount: Flow<Int>

    suspend fun setNativeLanguage(language: String)
    suspend fun setActiveCoastId(coastId: Int)
    suspend fun setApiKey(key: String)
    suspend fun setGeminiModel(model: String)

    /** Records a completed study session for today and updates the streak. */
    suspend fun recordStudySession()

    /** Resets the streak if more than a full day has passed since the last session. */
    suspend fun refreshStreak()
}

class DataStoreSettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    private val clock: Clock
) : SettingsRepository {

    override val nativeLanguage: Flow<String> =
        dataStore.data.map { it[NATIVE_LANG] ?: SettingsDefaults.NATIVE_LANGUAGE }

    override val activeCoastId: Flow<Int?> =
        dataStore.data.map { it[ACTIVE_COAST_ID] }

    override val apiKey: Flow<String> =
        dataStore.data.map { it[API_KEY] ?: "" }

    override val geminiModel: Flow<String> =
        dataStore.data.map { it[GEMINI_MODEL] ?: SettingsDefaults.GEMINI_MODEL }

    override val streakCount: Flow<Int> =
        dataStore.data.map { it[STREAK_COUNT] ?: 0 }

    override suspend fun setNativeLanguage(language: String) = set(NATIVE_LANG, language)
    override suspend fun setActiveCoastId(coastId: Int) = set(ACTIVE_COAST_ID, coastId)
    override suspend fun setApiKey(key: String) = set(API_KEY, key)
    override suspend fun setGeminiModel(model: String) = set(GEMINI_MODEL, model)

    override suspend fun recordStudySession() {
        dataStore.edit { preferences ->
            val today = LocalDate.now(clock)
            val lastStudy = preferences[LAST_STUDY_DATE]?.let { LocalDate.parse(it, DATE_FORMATTER) }
            preferences[STREAK_COUNT] = nextStreak(lastStudy, preferences[STREAK_COUNT] ?: 0, today)
            preferences[LAST_STUDY_DATE] = today.format(DATE_FORMATTER)
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

    private suspend fun <T> set(key: Preferences.Key<T>, value: T) {
        dataStore.edit { it[key] = value }
    }

    private companion object {
        val NATIVE_LANG = stringPreferencesKey("native_language")
        val ACTIVE_COAST_ID = intPreferencesKey("active_coast_id")
        val API_KEY = stringPreferencesKey("gemini_api_key")
        val GEMINI_MODEL = stringPreferencesKey("gemini_model")
        val STREAK_COUNT = intPreferencesKey("streak_count")
        val LAST_STUDY_DATE = stringPreferencesKey("last_study_date")
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

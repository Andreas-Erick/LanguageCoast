package com.andreaserick.languagecoast.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** Single app-wide DataStore instance for user preferences. */
val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/**
 * Reads and writes user preferences (languages, Gemini configuration, study streak).
 */
class SettingsManager(private val context: Context) {

    companion object {
        const val DEFAULT_NATIVE_LANGUAGE = "English"
        const val DEFAULT_TARGET_LANGUAGE = "Spanish"
        const val DEFAULT_GEMINI_MODEL = "gemini-2.5-flash"

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

        private val NATIVE_LANG = stringPreferencesKey("native_language")
        private val TARGET_LANG = stringPreferencesKey("target_language")
        private val API_KEY = stringPreferencesKey("gemini_api_key")
        private val GEMINI_MODEL = stringPreferencesKey("gemini_model")
        private val STREAK_COUNT = intPreferencesKey("streak_count")
        private val LAST_STUDY_DATE = stringPreferencesKey("last_study_date")
        private val DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE
    }

    val nativeLanguageFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[NATIVE_LANG] ?: DEFAULT_NATIVE_LANGUAGE
    }

    val targetLanguageFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[TARGET_LANG] ?: DEFAULT_TARGET_LANGUAGE
    }

    val apiKeyFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[API_KEY] ?: ""
    }

    val geminiModelFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[GEMINI_MODEL] ?: DEFAULT_GEMINI_MODEL
    }

    val streakCountFlow: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[STREAK_COUNT] ?: 0
    }

    suspend fun saveNativeLanguage(language: String) {
        context.dataStore.edit { preferences ->
            preferences[NATIVE_LANG] = language
        }
    }

    suspend fun saveTargetLanguage(language: String) {
        context.dataStore.edit { preferences ->
            preferences[TARGET_LANG] = language
        }
    }

    suspend fun saveApiKey(key: String) {
        context.dataStore.edit { preferences ->
            preferences[API_KEY] = key
        }
    }

    suspend fun saveGeminiModel(model: String) {
        context.dataStore.edit { preferences ->
            preferences[GEMINI_MODEL] = model
        }
    }

    /**
     * Records a completed study session for today. Increments the streak if the last
     * session was yesterday, keeps it if it was today, and restarts it at 1 otherwise.
     */
    suspend fun updateStreak() {
        context.dataStore.edit { preferences ->
            val today = LocalDate.now()
            val lastStudyString = preferences[LAST_STUDY_DATE]
            val currentStreak = preferences[STREAK_COUNT] ?: 0

            if (lastStudyString == null) {
                preferences[STREAK_COUNT] = 1
            } else {
                val lastStudyDate = LocalDate.parse(lastStudyString, DATE_FORMATTER)
                when {
                    lastStudyDate.isEqual(today) -> Unit // Already counted today
                    lastStudyDate.isEqual(today.minusDays(1)) -> preferences[STREAK_COUNT] = currentStreak + 1
                    else -> preferences[STREAK_COUNT] = 1
                }
            }
            preferences[LAST_STUDY_DATE] = today.format(DATE_FORMATTER)
        }
    }

    /** Resets the streak to 0 if more than a full day has passed since the last session. */
    suspend fun checkStreakReset() {
        context.dataStore.edit { preferences ->
            val lastStudyString = preferences[LAST_STUDY_DATE] ?: return@edit
            val lastStudyDate = LocalDate.parse(lastStudyString, DATE_FORMATTER)
            val today = LocalDate.now()

            if (lastStudyDate.isBefore(today.minusDays(1))) {
                preferences[STREAK_COUNT] = 0
            }
        }
    }
}

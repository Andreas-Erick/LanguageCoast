package com.andreaserick.languagecoast.data

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey

/**
 * A preference that goes into a [Backup] (and the settings snapshot) and comes back from one.
 *
 * @property save Puts the stored value (null when it isn't set) into a backup.
 * @property load The value a backup holds for it, made safe to store: a backup file can hold anything. Null removes
 *     the preference, so its default applies.
 */
internal class BackedUp<T : Any>(
    val key: Preferences.Key<T>,
    private val save: BackupSettings.(T?) -> BackupSettings,
    private val load: (BackupSettings) -> T?
) {
    fun saveInto(backup: BackupSettings, preferences: Preferences): BackupSettings = backup.save(preferences[key])

    fun loadFrom(backup: BackupSettings, preferences: MutablePreferences) {
        val value = load(backup)
        if (value == null) preferences.remove(key) else preferences[key] = value
    }
}

/**
 * Every preference the settings are stored under, each declared once, and each either backed up (with how it goes
 * into a backup and back) or kept on this phone only, with the reason. A new setting can't be left out of backups
 * by accident: it has to be declared one way or the other here, and [DataStoreSettingsRepository.backupSettings] and
 * [DataStoreSettingsRepository.restoreSettings] follow from this list.
 */
internal object SettingKeys {
    private val backedUpKeys = ArrayList<BackedUp<*>>()
    private val deviceOnlyKeys = ArrayList<Preferences.Key<*>>()

    /** The preferences a backup carries. */
    val backedUp: List<BackedUp<*>> get() = backedUpKeys

    /** The preferences that stay on this phone. */
    val deviceOnly: List<Preferences.Key<*>> get() = deviceOnlyKeys

    private fun <T : Any> backedUp(key: Preferences.Key<T>, save: BackupSettings.(T?) -> BackupSettings, load: (BackupSettings) -> T?): Preferences.Key<T> {
        backedUpKeys += BackedUp(key, save, load)
        return key
    }

    private fun <T : Any> deviceOnly(key: Preferences.Key<T>): Preferences.Key<T> {
        deviceOnlyKeys += key
        return key
    }

    private fun date(text: String?): String? = text?.takeIf { parseDateOrNull(it) != null }

    // ---------- Backed up ----------

    val NATIVE_LANG = backedUp(stringPreferencesKey("native_language"), { copy(nativeLanguage = it ?: SettingsDefaults.NATIVE_LANGUAGE) }) { it.nativeLanguage }
    val ACTIVE_COAST_ID = backedUp(intPreferencesKey("active_coast_id"), { copy(activeCoastId = it) }) { it.activeCoastId }

    /** A provider this version doesn't know falls back to the default. */
    val TRANSLATION_PROVIDER = backedUp(stringPreferencesKey("translation_provider"), { copy(translationProvider = it) }) { backup ->
        backup.translationProvider?.takeIf { name -> TranslationProvider.entries.any { it.name == name } }
    }
    val GEMINI_MODEL = backedUp(stringPreferencesKey("gemini_model"), { copy(geminiModel = it) }) { it.geminiModel?.let(::normalizeGeminiModel) }
    val OPENROUTER_MODEL = backedUp(stringPreferencesKey("openrouter_model"), { copy(openRouterModel = it) }) { it.openRouterModel }
    val LOCAL_AI_MODEL = backedUp(stringPreferencesKey("local_ai_model"), { copy(localAiModel = it) }) { it.localAiModel }
    val OPENAI_MODEL = backedUp(stringPreferencesKey("openai_model"), { copy(openAiModel = it) }) { it.openAiModel }
    val CLAUDE_MODEL = backedUp(stringPreferencesKey("claude_model"), { copy(claudeModel = it) }) { it.claudeModel }
    val GROQ_MODEL = backedUp(stringPreferencesKey("groq_model"), { copy(groqModel = it) }) { it.groqModel }
    val DEEPSEEK_MODEL = backedUp(stringPreferencesKey("deepseek_model"), { copy(deepSeekModel = it) }) { it.deepSeekModel }

    val STREAK_COUNT = backedUp(intPreferencesKey("streak_count"), { copy(streakCount = it ?: 0) }) { it.streakCount.coerceAtLeast(0) }
    val LAST_STUDY_DATE = backedUp(stringPreferencesKey("last_study_date"), { copy(lastStudyDate = it) }) { date(it.lastStudyDate) }
    val STUDY_DAYS = backedUp(stringSetPreferencesKey("study_days"), { copy(studyDays = it.orEmpty().sorted()) }) { backup ->
        backup.studyDays.filterTo(HashSet()) { parseDateOrNull(it) != null }
    }
    val FREEZE_USED_ON = backedUp(stringPreferencesKey("freeze_used_on"), { copy(freezeUsedOn = it) }) { date(it.freezeUsedOn) }

    val REMINDER_ENABLED = backedUp(booleanPreferencesKey("reminder_enabled"), { copy(reminderEnabled = it ?: true) }) { it.reminderEnabled }
    /** A minute outside the day can't be a time; such a value counts as "no fixed time". */
    val REMINDER_MINUTE_OF_DAY = backedUp(intPreferencesKey("reminder_minute_of_day"), { copy(reminderMinuteOfDay = it) }) { backup ->
        backup.reminderMinuteOfDay?.takeIf { it in MINUTES_OF_DAY }
    }
    val STUDY_REVERSED = backedUp(booleanPreferencesKey("study_reversed"), { copy(studyReversed = it ?: false) }) { it.studyReversed }
    val WEEKLY_GOAL = backedUp(intPreferencesKey("weekly_goal"), { copy(weeklyGoal = it) }) { it.weeklyGoal }
    val THEME_MODE = backedUp(stringPreferencesKey("theme_mode"), { copy(themeMode = it) }) { it.themeMode }
    val HAPTICS_ENABLED = backedUp(booleanPreferencesKey("haptics_enabled"), { copy(hapticsEnabled = it ?: true) }) { it.hapticsEnabled }
    val SOUNDS_ENABLED = backedUp(booleanPreferencesKey("sounds_enabled"), { copy(soundsEnabled = it ?: true) }) { it.soundsEnabled }

    val STARTER_TOPICS_ADDED = backedUp(stringSetPreferencesKey("starter_topics_added"), { copy(starterTopicsAdded = it.orEmpty().sorted()) }) { backup ->
        backup.starterTopicsAdded.toSet().takeIf { it.isNotEmpty() }
    }

    /** One key per profile blank, named after the placeholder. */
    val PROFILE_KEYS: Map<String, Preferences.Key<String>> = Profile.PLACEHOLDERS.associateWith { placeholder ->
        backedUp(
            stringPreferencesKey("profile_" + placeholder.replace(' ', '_')),
            { value -> value?.trim()?.takeIf { it.isNotEmpty() }?.let { copy(profile = profile + (placeholder to it)) } ?: this }
        ) { it.profile[placeholder]?.takeIf { value -> value.isNotBlank() } }
    }

    /** One key per answer about the user (questions and per-island extra notes). */
    val ANSWER_KEYS: Map<String, Preferences.Key<String>> = PersonalIslands.ANSWER_KEYS.associateWith { question ->
        backedUp(
            stringPreferencesKey("about_$question"),
            { value -> if (value.isNullOrBlank()) this else copy(personalAnswers = personalAnswers + (question to value)) }
        ) { it.personalAnswers[question]?.takeIf { value -> value.isNotBlank() } }
    }

    /** Entries are stored as "coastId:islandId=fingerprint". */
    val PERSONAL_ISLANDS_BUILT = backedUp(stringSetPreferencesKey("personal_islands_built"), { copy(personalIslandsBuilt = readBuilt(it)) }) { backup ->
        backup.personalIslandsBuilt.mapTo(HashSet()) { "${it.key}=${it.value}" }.takeIf { it.isNotEmpty() }
    }
    val WORDING = backedUp(stringPreferencesKey("about_wording"), { copy(wording = it) }) { backup ->
        backup.wording?.takeIf { key -> Wording.entries.any { it.key == key && it != Wording.Unspecified } }
    }
    val PACE = backedUp(stringPreferencesKey("study_pace"), { copy(pace = it) }) { backup -> backup.pace?.takeIf { key -> Pace.entries.any { it.key == key } } }

    // ---------- On this phone only ----------

    /** The API keys and the Hugging Face token are secrets: never in a file the user can share. Kept in [SecretStore]; these are only legacy plaintext copies. */
    val API_KEY = deviceOnly(stringPreferencesKey("gemini_api_key"))
    val OPENROUTER_KEY = deviceOnly(stringPreferencesKey("openrouter_api_key"))
    val OPENAI_KEY = deviceOnly(stringPreferencesKey("openai_api_key"))
    val CLAUDE_KEY = deviceOnly(stringPreferencesKey("claude_api_key"))
    val GROQ_KEY = deviceOnly(stringPreferencesKey("groq_api_key"))
    val DEEPSEEK_KEY = deviceOnly(stringPreferencesKey("deepseek_api_key"))
    val HUGGING_FACE_TOKEN = deviceOnly(stringPreferencesKey("hugging_face_token"))

    /** Permission is given on the phone that sends the text, like the keys it goes with. */
    val AI_SHARING_ALLOWED = deviceOnly(stringSetPreferencesKey("ai_sharing_allowed"))

    /** A location belongs to the phone it was read on. */
    val SUN_USES_LOCATION = deviceOnly(booleanPreferencesKey("sun_uses_location"))
    val SUN_LOCATION = deviceOnly(stringPreferencesKey("sun_location"))

    /** After a restore a reached level is simply congratulated on once more. */
    val LEVELS_CELEBRATED = deviceOnly(stringSetPreferencesKey("levels_celebrated"))

    /** After a restore the invitation may show once more; it is one tap to wave away. */
    val ABOUT_YOU_INVITE_DISMISSED = deviceOnly(booleanPreferencesKey("about_you_invite_dismissed"))

    /** One model id per line. A check for new models brings the list back. */
    fun modelOptions(provider: TranslationProvider) = stringPreferencesKey("model_options_${provider.name.lowercase()}")

    /** Freshness ring buffers for each coast's practice sheets. */
    fun worksheetAnchors(coastId: Int) = stringPreferencesKey("worksheet_anchors_$coastId")
    fun worksheetSentences(coastId: Int) = stringPreferencesKey("worksheet_sentences_$coastId")

    /** The secrets, in [SecretStore] by these keys' names. */
    val SECRET_KEYS: List<Preferences.Key<String>> = listOf(API_KEY, OPENROUTER_KEY, OPENAI_KEY, CLAUDE_KEY, GROQ_KEY, DEEPSEEK_KEY, HUGGING_FACE_TOKEN)

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

    /** [entries] ("coastId:islandId=fingerprint") as a map. */
    fun readBuilt(entries: Set<String>?): Map<String, String> =
        entries.orEmpty().mapNotNull { entry ->
            entry.substringBefore('=', "").takeIf { it.isNotEmpty() }?.let { it to entry.substringAfter('=') }
        }.toMap()
}

package com.andreaserick.languagecoast.data

/** Default values and the options offered in Settings. */
object SettingsDefaults {
    const val NATIVE_LANGUAGE = "English"
    const val GEMINI_MODEL = "gemini-3.5-flash-lite"
    const val OPENAI_MODEL = "gpt-4.1-mini"
    const val CLAUDE_MODEL = "claude-haiku-5-5"
    const val GROQ_MODEL = "llama-3.3-70b-versatile"
    const val DEEPSEEK_MODEL = "deepseek-chat"
    const val LOCAL_AI_MODEL = LocalAiCatalog.DEFAULT_MODEL_ID

    /** Islands to study per week. */
    const val WEEKLY_GOAL = 5

    /** "sun", "system", "classic", "dark" or "light"; by default the app follows the sun through the day. */
    const val THEME_MODE = "sun"
    val THEME_MODES = listOf("sun", "system", "classic", "dark", "light")
    val WEEKLY_GOALS = listOf(3, 5, 7, 10)

    /** OpenRouter's Auto Router, which picks a suitable model for each request. */
    const val OPENROUTER_MODEL = "openrouter/auto"

    /**
     * Checked against the live model list on 2026-11-03. Google retires models within a year or so, and the
     * 2.0 and 2.5 ones returned 404; [normalizeGeminiModel] maps those to these, and a 404 falls back to the
     * replacement Google names in its error.
     */
    val GEMINI_MODELS = listOf(
        "gemini-3.5-flash-lite",
        "gemini-3.8-flash",
        "gemini-3.1-pro-preview",
        "gemini-4-argon"
    )

    val OPENAI_MODELS = listOf(
        "gpt-4.1-mini",
        "gpt-4.1-nano",
        "gpt-4o-mini",
        "gpt-4o"
    )

    /**
     * The current Claude models, smallest first, then the earlier small one for those who had it picked.
     * Taken from Anthropic's model reference as it stood on 2026-11-03, not tried with a key; "Check for
     * new models" in Settings asks Anthropic itself and keeps the list current after that.
     */
    val CLAUDE_MODELS = listOf(
        "claude-haiku-5-5",
        "claude-sonnet-5-5",
        "claude-opus-5-5",
        "claude-fable-5-2",
        "claude-haiku-4-5"
    )

    val GROQ_MODELS = listOf(
        "llama-3.3-70b-versatile",
        "llama-3.1-8b-instant",
        "gemma2-9b-it",
        "qwen/qwen3-32b"
    )

    val DEEPSEEK_MODELS = listOf(
        "deepseek-chat",
        "deepseek-reasoner"
    )

    /**
     * The models the app offers for [provider] until "Check for new models" in Settings has asked the
     * provider itself (see [checkForNewModels]); empty for a provider whose model isn't picked from a list.
     */
    fun models(provider: TranslationProvider): List<String> = when (provider) {
        TranslationProvider.Gemini -> GEMINI_MODELS
        TranslationProvider.OpenAI -> OPENAI_MODELS
        TranslationProvider.Claude -> CLAUDE_MODELS
        TranslationProvider.Groq -> GROQ_MODELS
        TranslationProvider.DeepSeek -> DEEPSEEK_MODELS
        else -> emptyList()
    }

    /** The model [provider] starts with; "" for a provider whose model isn't picked from a list. */
    fun model(provider: TranslationProvider): String = when (provider) {
        TranslationProvider.Gemini -> GEMINI_MODEL
        TranslationProvider.OpenAI -> OPENAI_MODEL
        TranslationProvider.Claude -> CLAUDE_MODEL
        TranslationProvider.Groq -> GROQ_MODEL
        TranslationProvider.DeepSeek -> DEEPSEEK_MODEL
        else -> ""
    }
}

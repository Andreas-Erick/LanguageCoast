package com.andreaserick.languagecoast.data

/** Default values and the options offered in Settings. */
object SettingsDefaults {
    const val NATIVE_LANGUAGE = "English"
    const val GEMINI_MODEL = "gemini-2.5-flash"
    const val OPENAI_MODEL = "gpt-4.1-mini"
    const val CLAUDE_MODEL = "claude-haiku-4-5"
    const val GROQ_MODEL = "llama-3.3-70b-versatile"
    const val DEEPSEEK_MODEL = "deepseek-chat"
    const val LOCAL_AI_MODEL = LocalAiCatalog.DEFAULT_MODEL_ID

    /** OpenRouter's Auto Router, which picks a suitable model for each request. */
    const val OPENROUTER_MODEL = "openrouter/auto"

    val GEMINI_MODELS = listOf(
        "gemini-2.5-flash",
        "gemini-2.5-flash-lite",
        "gemini-2.5-pro",
        "gemini-2.0-flash"
    )

    val OPENAI_MODELS = listOf(
        "gpt-4.1-mini",
        "gpt-4.1-nano",
        "gpt-4o-mini",
        "gpt-4o"
    )

    val CLAUDE_MODELS = listOf(
        "claude-haiku-4-5",
        "claude-sonnet-4-5",
        "claude-3-5-haiku-latest",
        "claude-3-7-sonnet-latest"
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
}

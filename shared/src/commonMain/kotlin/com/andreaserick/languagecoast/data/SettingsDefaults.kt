package com.andreaserick.languagecoast.data

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

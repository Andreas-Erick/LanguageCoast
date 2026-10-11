package com.andreaserick.languagecoast.data

actual val onDeviceTranslationAvailable = false

actual fun onDeviceLanguage(language: String): String? = null

/** On-device translation isn't built for iPhone yet, so it explains which providers to use instead. */
actual class OnDeviceTranslator actual constructor() : Translator {
    actual override suspend fun translateAndCategorize(request: TranslationRequest): TranslationResult =
        TranslationResult.failure("On-device translation isn't available on iPhone yet. Pick Gemini or OpenRouter in Settings, or type the translation yourself.")
}

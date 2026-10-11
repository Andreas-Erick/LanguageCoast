package com.andreaserick.languagecoast.data

/** On-device translation isn't built for iPhone yet, so it explains which providers to use instead. */
actual class OnDeviceTranslator actual constructor() : Translator {
    override suspend fun translateAndCategorize(request: TranslationRequest): TranslationResult =
        TranslationResult.failure("On-device translation isn't available on iPhone yet. Pick Gemini or OpenRouter in Settings, or type the translation yourself.")
}

package com.andreaserick.languagecoast.data

actual val onDeviceTranslationAvailable = false

actual fun onDeviceLanguage(language: String): String? = null

/** On-device translation isn't built for iPhone yet and isn't offered there; should it be asked anyway, this says what to do instead. */
actual class OnDeviceTranslator actual constructor() : Translator {
    actual override suspend fun translateAndCategorize(request: TranslationRequest): TranslationResult =
        TranslationResult.failure("On-device translation isn't available on iPhone yet. Pick an AI provider in Settings, or type the translation yourself.")
}

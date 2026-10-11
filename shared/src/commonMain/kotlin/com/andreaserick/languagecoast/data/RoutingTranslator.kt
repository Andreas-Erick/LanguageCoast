package com.andreaserick.languagecoast.data

/** The production [Translator]: hands each request to the translator for its [TranslationRequest.engine]. */
class RoutingTranslator(
    private val onDevice: OnDeviceTranslator,
    private val gemini: GeminiTranslator,
    private val openRouter: OpenRouterTranslator
) : Translator {
    override suspend fun translateAndCategorize(request: TranslationRequest): TranslationResult = when (request.engine) {
        TranslationEngine.OnDevice -> onDevice.translateAndCategorize(request)
        is TranslationEngine.Gemini -> gemini.translateAndCategorize(request)
        is TranslationEngine.OpenRouter -> openRouter.translateAndCategorize(request)
    }
}

/** Whether this platform can translate on the device itself; iPhone can't yet. */
expect val onDeviceTranslationAvailable: Boolean

/** The on-device translator's code for [language] (an English language name), or null if it doesn't cover it. */
expect fun onDeviceLanguage(language: String): String?

/** Translates on the device itself: free and offline, but it only translates and can't pick a category. */
expect class OnDeviceTranslator() : Translator {
    override suspend fun translateAndCategorize(request: TranslationRequest): TranslationResult
}

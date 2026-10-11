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

/** Translates on the device itself: free and offline, but it only translates and can't pick a category. */
expect class OnDeviceTranslator() : Translator

package com.andreaserick.languagecoast.data

actual val localAiAvailable = false

/** Local `.litertlm` models run through LiteRT-LM on Android. iPhone doesn't offer them; should one be asked anyway, this says what to do instead. */
actual class LocalAiTranslator actual constructor() : Translator {
    actual override suspend fun translateAndCategorize(request: TranslationRequest): TranslationResult =
        TranslationResult.failure(
            "Local AI isn't available on iPhone yet. Pick an AI provider in Settings."
        )

    actual override suspend fun complete(engine: TranslationEngine, prompt: String): Completion =
        Completion.failure("Local AI isn't available on iPhone yet. Pick an AI provider in Settings.")

    actual override suspend fun prepare(engine: TranslationEngine) = Unit
}

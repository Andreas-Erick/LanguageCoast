package com.andreaserick.languagecoast.data

actual val localAiAvailable = false

/** Local `.litertlm` models run through LiteRT-LM on Android; iOS explains which providers to use instead. */
actual class LocalAiTranslator actual constructor() : Translator {
    actual override suspend fun translateAndCategorize(request: TranslationRequest): TranslationResult =
        TranslationResult.failure(
            "Local AI models aren't available on iPhone yet. Pick Gemini, OpenAI, Claude, Groq or OpenRouter in Settings."
        )

    actual override suspend fun complete(engine: TranslationEngine, prompt: String): Completion =
        Completion.failure("Local AI models aren't available on iPhone yet. Pick a cloud provider in Settings.")

    actual override suspend fun prepare(engine: TranslationEngine) = Unit
}

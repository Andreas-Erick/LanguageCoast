package com.andreaserick.languagecoast.data

/**
 * The production [Translator]: hands each request to the translator for its [TranslationRequest.engine].
 * A job that runs for minutes (starter topics, islands, a practice sheet, Today's sentences) reads its engine
 * once and keeps it, key included. So each request to a cloud provider is checked against [settings] first:
 * once the key is removed, and the permission with it, nothing more is sent.
 */
class RoutingTranslator(
    private val onDevice: OnDeviceTranslator,
    private val localAi: LocalAiTranslator,
    private val cloud: CloudTranslator,
    private val settings: SettingsRepository
) : Translator {
    override suspend fun translateAndCategorize(request: TranslationRequest): TranslationResult {
        settings.withdrawn(request.engine)?.let { return TranslationResult.failure(it) }
        return translatorFor(request.engine).translateAndCategorize(request)
    }

    override suspend fun complete(engine: TranslationEngine, prompt: String): Completion =
        settings.withdrawn(engine)?.let { Completion.failure(it) } ?: translatorFor(engine).complete(engine, prompt)

    override suspend fun complete(engine: TranslationEngine, prompt: String, onPartialText: ((String) -> Unit)?): Completion =
        settings.withdrawn(engine)?.let { Completion.failure(it) } ?: translatorFor(engine).complete(engine, prompt, onPartialText)

    override suspend fun prepare(engine: TranslationEngine) = translatorFor(engine).prepare(engine)

    private fun translatorFor(engine: TranslationEngine): Translator = when (engine) {
        TranslationEngine.OnDevice -> onDevice
        is TranslationEngine.LocalAi -> localAi
        is TranslationEngine.Cloud -> cloud
    }
}

/**
 * Why cloud [engine] may not be sent anything any more, in words for the user: its key was removed, and with
 * it the permission to send text to its provider. Null while it may be used, and for what runs on the phone.
 */
internal suspend fun SettingsRepository.withdrawn(engine: TranslationEngine): String? {
    val provider = (engine as? TranslationEngine.Cloud)?.provider ?: return null
    return (currentEngine(provider) as? EngineChoice.Missing)?.message
}

/** Whether this platform can translate on the device itself; iPhone can't yet. */
expect val onDeviceTranslationAvailable: Boolean

/** The on-device translator's code for [language] (an English language name), or null if it doesn't cover it. */
expect fun onDeviceLanguage(language: String): String?

/** Translates on the device itself: free and offline, but it only translates and can't pick a category. */
expect class OnDeviceTranslator() : Translator {
    override suspend fun translateAndCategorize(request: TranslationRequest): TranslationResult
}

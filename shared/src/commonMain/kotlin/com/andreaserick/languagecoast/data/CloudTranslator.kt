package com.andreaserick.languagecoast.data

import io.ktor.client.HttpClient

/**
 * [Translator] for every cloud provider: Gemini, OpenAI, Claude, Groq, DeepSeek and OpenRouter. It picks the
 * provider's [CloudWire] and hands the request to the [CloudTransport], which treats them all alike.
 *
 * A translation keeps the two minutes of [Patience.Sentence]: the screen that asks for it has no way out while it
 * waits. Everything else ([complete]) may take as long as a model that thinks over a batch needs.
 */
class CloudTranslator internal constructor(private val transport: CloudTransport) : Translator {

    constructor(client: HttpClient) : this(CloudTransport(client))

    override suspend fun translateAndCategorize(request: TranslationRequest): TranslationResult {
        val wire = wireFor(request.engine) ?: return TranslationResult.failure(CANNOT_ANSWER)
        keyMissing(request.engine, wire)?.let { return TranslationResult.failure(it) }
        return transport.ask(wire, buildTranslationPrompt(request), Patience.Sentence)
            .toTranslation("${wire.providerName}'s answer couldn't be read. Try again or pick another model.")
    }

    override suspend fun complete(engine: TranslationEngine, prompt: String): Completion = complete(engine, prompt, null)

    override suspend fun complete(engine: TranslationEngine, prompt: String, onPartialText: ((String) -> Unit)?): Completion {
        val wire = wireFor(engine) ?: return Completion.failure(CANNOT_ANSWER)
        keyMissing(engine, wire)?.let { return Completion.failure(it) }
        return transport.ask(wire, prompt, Patience.Batch, onPartialText)
    }

    private fun keyMissing(engine: TranslationEngine, wire: CloudWire): String? =
        if (apiKeyOf(engine).isBlank()) "${wire.providerName} needs an API key. Add one in Settings." else null

    private companion object {
        const val CANNOT_ANSWER = "This provider can't answer here."
    }
}

private fun apiKeyOf(engine: TranslationEngine): String = when (engine) {
    is TranslationEngine.Gemini -> engine.apiKey
    is TranslationEngine.OpenAI -> engine.apiKey
    is TranslationEngine.Claude -> engine.apiKey
    is TranslationEngine.Groq -> engine.apiKey
    is TranslationEngine.DeepSeek -> engine.apiKey
    is TranslationEngine.OpenRouter -> engine.apiKey
    TranslationEngine.OnDevice, is TranslationEngine.LocalAi -> ""
}.trim()

/** The wire of cloud [engine], with its key and model (the provider's default for a blank one); null for what runs on the phone. */
internal fun wireFor(engine: TranslationEngine): CloudWire? {
    val key = apiKeyOf(engine)
    fun TranslationEngine.modelOr(default: String, model: String) = model.trim().ifBlank { default }
    return when (engine) {
        is TranslationEngine.Gemini -> GeminiWire(key, engine.model)
        is TranslationEngine.OpenAI -> ChatWire.openAi(key, engine.modelOr(SettingsDefaults.OPENAI_MODEL, engine.model))
        is TranslationEngine.Claude -> ClaudeWire(key, engine.modelOr(SettingsDefaults.CLAUDE_MODEL, engine.model))
        is TranslationEngine.Groq -> ChatWire.groq(key, engine.modelOr(SettingsDefaults.GROQ_MODEL, engine.model))
        is TranslationEngine.DeepSeek -> ChatWire.deepSeek(key, engine.modelOr(SettingsDefaults.DEEPSEEK_MODEL, engine.model))
        is TranslationEngine.OpenRouter -> ChatWire.openRouter(key, engine.modelOr(SettingsDefaults.OPENROUTER_MODEL, engine.model))
        TranslationEngine.OnDevice, is TranslationEngine.LocalAi -> null
    }
}

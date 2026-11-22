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

private fun apiKeyOf(engine: TranslationEngine): String = (engine as? TranslationEngine.Cloud)?.apiKey?.trim().orEmpty()

/** The wire of cloud [engine], with its key and model (the provider's default for a blank one); null for what runs on the phone. */
internal fun wireFor(engine: TranslationEngine): CloudWire? {
    val cloud = engine as? TranslationEngine.Cloud ?: return null
    val provider = CLOUD_PROVIDERS.getValue(cloud.provider)
    return provider.wire(cloud.apiKey.trim(), provider.modelOrDefault(cloud.model))
}

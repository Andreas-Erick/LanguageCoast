package com.andreaserick.languagecoast.data

import androidx.datastore.preferences.core.Preferences
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull

/*
 * Everything the app knows about each cloud provider, in one place. Adding a provider is an entry in
 * [TranslationProvider], an entry in [CLOUD_PROVIDERS], and its two preferences in [SettingKeys] (with the
 * model in [BackupSettings], whose format stays fixed). The rest of the app asks here instead of naming providers.
 */

/**
 * How a cloud provider lists the models it offers: the endpoint, how the key goes along, and which of the
 * listed models answer a text prompt with text (see [parseModelListing]).
 */
internal class ModelListEndpoint(
    val url: String,
    val headers: (apiKey: String) -> Map<String, String>,
    val isUsable: (name: String, entry: JsonObject) -> Boolean
)

/**
 * What Settings says about a provider's key: the field's title, where a key comes from, and how one starts.
 *
 * @property site What the provider calls the place where keys are made, for "Open …" in the guided key setup.
 */
class KeyCopy(val title: String, val intro: String, val placeholder: String, val site: String)

/**
 * One cloud provider.
 *
 * @property company Who receives the text the app sends.
 * @property keyPreference Where the API key is kept: its name in [SecretStore] (and, from old versions, DataStore).
 * @property modelPreference Where the picked model is kept.
 * @property defaultModel The model a new user starts with, and the one a blank model stands for.
 * @property models The models offered until "Refresh model list" asks the provider; empty when the model is typed.
 * @property normalizeModel Turns a stored model that the provider has retired into its replacement.
 * @property wire Builds the provider's wire for a key and a model.
 * @property capacity How much the provider's models take on at once, for a model where it is known.
 * @property modelList How to ask the provider for its models; null when it has no such list.
 * @property keyUrl The page where a key is made.
 * @property privacyPolicyUrl The provider's privacy policy.
 * @property keyCopy What Settings says about the key.
 * @property modelHint A sentence under the model picker, or null.
 */
class CloudProvider internal constructor(
    val provider: TranslationProvider,
    val company: String,
    internal val keyPreference: Preferences.Key<String>,
    internal val modelPreference: Preferences.Key<String>,
    val defaultModel: String,
    val models: List<String>,
    val normalizeModel: (String) -> String = { it },
    internal val wire: (apiKey: String, model: String) -> CloudWire,
    val capacity: (model: String?) -> ProviderCapacity = { ProviderCapacity.Cloud },
    internal val modelList: ModelListEndpoint?,
    val keyUrl: String,
    val privacyPolicyUrl: String,
    val keyCopy: KeyCopy,
    val modelHint: String? = null
) {
    /** Whether the model is picked from a list that "Refresh model list" keeps current, rather than typed. */
    val hasModelList: Boolean get() = modelList != null

    /** [model] as it is sent: the provider's default for a blank one, and a retired one's replacement. */
    fun modelOrDefault(model: String): String = normalizeModel(model.trim().ifBlank { defaultModel })
}

private fun bearer(key: String) = mapOf("Authorization" to "Bearer $key")

/** Every cloud provider, in the order Settings shows them. */
val CLOUD_PROVIDERS: Map<TranslationProvider, CloudProvider> = listOf(
    CloudProvider(
        provider = TranslationProvider.Gemini,
        company = "Google",
        keyPreference = SettingKeys.API_KEY,
        modelPreference = SettingKeys.GEMINI_MODEL,
        defaultModel = SettingsDefaults.GEMINI_MODEL,
        models = SettingsDefaults.GEMINI_MODELS,
        normalizeModel = ::normalizeGeminiModel,
        wire = { key, model -> GeminiWire(key, model) },
        // One page holds them all: 1000 is the most the API returns at once.
        modelList = ModelListEndpoint("https://generativelanguage.googleapis.com/v1beta/models?pageSize=1000", { mapOf("x-goog-api-key" to it) }) { name, entry ->
            val methods = (entry["supportedGenerationMethods"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.content }
            name.startsWith("gemini-") && (methods == null || "generateContent" in methods) &&
                listOf("live", "vision", "-exp").none { it in name } && !SNAPSHOT.containsMatchIn(name)
        },
        keyUrl = "https://aistudio.google.com/apikey",
        privacyPolicyUrl = "https://policies.google.com/privacy",
        keyCopy = KeyCopy("Google Gemini API key", "Google AI Studio gives you a key that is free to start with.", "AIzaSy...", "Google AI Studio"),
        modelHint = "The default model answers in under a second and is plenty for translating, sorting cards onto islands and " +
            "suggesting alternatives. The larger models think before they answer, so they take a few seconds longer."
    ),
    CloudProvider(
        provider = TranslationProvider.OpenAI,
        company = "OpenAI",
        keyPreference = SettingKeys.OPENAI_KEY,
        modelPreference = SettingKeys.OPENAI_MODEL,
        defaultModel = SettingsDefaults.OPENAI_MODEL,
        models = SettingsDefaults.OPENAI_MODELS,
        wire = { key, model -> ChatWire.openAi(key, model) },
        modelList = ModelListEndpoint("https://api.openai.com/v1/models", ::bearer) { name, _ ->
            (name.startsWith("gpt-") || name.startsWith("chatgpt-") || Regex("^o\\d").containsMatchIn(name)) &&
                OPENAI_OTHER_ENDPOINTS.none { it in name } && !SNAPSHOT.containsMatchIn(name)
        },
        keyUrl = "https://platform.openai.com/api-keys",
        privacyPolicyUrl = "https://openai.com/policies/privacy-policy/",
        keyCopy = KeyCopy("OpenAI API key", "Create a key in your OpenAI account.", "sk-proj-...", "the OpenAI Platform")
    ),
    CloudProvider(
        provider = TranslationProvider.Claude,
        company = "Anthropic",
        keyPreference = SettingKeys.CLAUDE_KEY,
        modelPreference = SettingKeys.CLAUDE_MODEL,
        defaultModel = SettingsDefaults.CLAUDE_MODEL,
        models = SettingsDefaults.CLAUDE_MODELS,
        wire = { key, model -> ClaudeWire(key, model) },
        // Every Claude 5 model thinks when a request doesn't say otherwise, and Language Coast never does; the earlier
        // Claude models only think when a request asks them to, so they answer like other cloud models.
        capacity = { model -> if (model != null && isClaudeBeforeFive(model)) ProviderCapacity.Cloud else ProviderCapacity.Thinking },
        modelList = ModelListEndpoint(
            "https://api.anthropic.com/v1/models?limit=1000",
            { mapOf("x-api-key" to it, "anthropic-version" to "2023-06-01") }
        ) { name, _ -> name.startsWith("claude-") },
        keyUrl = "https://platform.claude.com/settings/keys",
        privacyPolicyUrl = "https://www.anthropic.com/legal/privacy",
        keyCopy = KeyCopy("Anthropic Claude API key", "Create a key in your Anthropic account.", "sk-ant-...", "the Claude Console")
    ),
    CloudProvider(
        provider = TranslationProvider.Groq,
        company = "Groq",
        keyPreference = SettingKeys.GROQ_KEY,
        modelPreference = SettingKeys.GROQ_MODEL,
        defaultModel = SettingsDefaults.GROQ_MODEL,
        models = SettingsDefaults.GROQ_MODELS,
        wire = { key, model -> ChatWire.groq(key, model) },
        modelList = ModelListEndpoint("https://api.groq.com/openai/v1/models", ::bearer) { _, entry ->
            (entry["active"] as? JsonPrimitive)?.booleanOrNull != false
        },
        keyUrl = "https://console.groq.com/keys",
        privacyPolicyUrl = "https://groq.com/privacy-policy/",
        keyCopy = KeyCopy("Groq API key", "Create a key in your Groq account.", "gsk_...", "GroqCloud"),
        modelHint = "Groq runs open models such as Llama and Qwen, and answers quickly."
    ),
    CloudProvider(
        provider = TranslationProvider.DeepSeek,
        company = "DeepSeek",
        keyPreference = SettingKeys.DEEPSEEK_KEY,
        modelPreference = SettingKeys.DEEPSEEK_MODEL,
        defaultModel = SettingsDefaults.DEEPSEEK_MODEL,
        models = SettingsDefaults.DEEPSEEK_MODELS,
        wire = { key, model -> ChatWire.deepSeek(key, model) },
        modelList = ModelListEndpoint("https://api.deepseek.com/models", ::bearer) { _, _ -> true },
        keyUrl = "https://platform.deepseek.com/api_keys",
        privacyPolicyUrl = "https://cdn.deepseek.com/policies/en-US/deepseek-privacy-policy.html",
        keyCopy = KeyCopy("DeepSeek API key", "Create a key in your DeepSeek account.", "sk-...", "the DeepSeek Platform")
    ),
    CloudProvider(
        provider = TranslationProvider.OpenRouter,
        company = "OpenRouter",
        keyPreference = SettingKeys.OPENROUTER_KEY,
        modelPreference = SettingKeys.OPENROUTER_MODEL,
        defaultModel = SettingsDefaults.OPENROUTER_MODEL,
        // Any of hundreds of models, typed by its id; there is no list to keep current.
        models = emptyList(),
        wire = { key, model -> ChatWire.openRouter(key, model) },
        modelList = null,
        keyUrl = "https://openrouter.ai/settings/keys",
        privacyPolicyUrl = "https://openrouter.ai/privacy",
        keyCopy = KeyCopy("OpenRouter API key", "One key for models from many companies, paid from your OpenRouter credits.", "sk-or-...", "OpenRouter"),
        // The placeholder shows the id that stands for a blank field; the hint says what that means without the id.
        modelHint = "Leave as is to let OpenRouter choose. To pick one yourself, find its ID on openrouter.ai/models."
    )
).associateBy { it.provider }

/** [provider]'s entry in [CLOUD_PROVIDERS]; null for what runs on the phone. */
val TranslationProvider.cloud: CloudProvider? get() = CLOUD_PROVIDERS[this]

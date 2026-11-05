package com.andreaserick.languagecoast.data

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.longOrNull
import kotlin.time.Instant

/*
 * "Check for new models" in Settings. The models offered for a cloud provider are a list in the app
 * ([SettingsDefaults]), and providers retire models faster than the app is updated. The check asks the
 * provider what it offers right now, keeps the models this app can talk to, adds the ones the list didn't
 * have, drops the ones the provider no longer has, and moves the user off a model that is gone.
 */

/** The providers whose model is picked from a list, which a check can bring up to date. */
val MODEL_LIST_PROVIDERS = listOf(
    TranslationProvider.Gemini,
    TranslationProvider.OpenAI,
    TranslationProvider.Claude,
    TranslationProvider.Groq,
    TranslationProvider.DeepSeek
)

/**
 * A model a provider offers right now.
 *
 * @property created When it came out (seconds since 1970), where the provider says; newer models are listed first.
 */
data class LiveModel(val id: String, val created: Long? = null)

/** What a provider answered when asked for its models. */
sealed interface ModelListing {
    /** The models this app can use, already without the provider's speech, image and embedding models. */
    data class Found(val models: List<LiveModel>) : ModelListing
    data class Failed(val message: String) : ModelListing
}

/** Asks a provider which models it offers. */
fun interface ModelLister {
    suspend fun list(provider: TranslationProvider, apiKey: String): ModelListing
}

/** The [ModelLister] of the app: each provider's own "list models" endpoint, with the user's API key. */
class ProviderModelLister(private val client: HttpClient) : ModelLister {

    override suspend fun list(provider: TranslationProvider, apiKey: String): ModelListing {
        val key = apiKey.trim()
        if (provider !in MODEL_LIST_PROVIDERS) return ModelListing.Failed("${provider.label} has no list of models to check.")
        if (key.isEmpty()) return ModelListing.Failed("Save your ${provider.label} API key first, then check again.")
        return try {
            val response = when (provider) {
                // One page holds them all: 1000 is the most either API returns at once.
                TranslationProvider.Claude -> client.get("https://api.anthropic.com/v1/models?limit=1000") {
                    header("x-api-key", key)
                    header("anthropic-version", "2023-06-01")
                }
                TranslationProvider.Gemini -> client.get("https://generativelanguage.googleapis.com/v1beta/models?pageSize=1000") {
                    header("x-goog-api-key", key)
                }
                TranslationProvider.OpenAI -> client.get("https://api.openai.com/v1/models") { header("Authorization", "Bearer $key") }
                TranslationProvider.Groq -> client.get("https://api.groq.com/openai/v1/models") { header("Authorization", "Bearer $key") }
                else -> client.get("https://api.deepseek.com/models") { header("Authorization", "Bearer $key") }
            }
            parseModelListing(provider, response.status.value, response.bodyAsText())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logError(TAG, "Couldn't list ${provider.label}'s models", e)
            ModelListing.Failed("Couldn't reach ${provider.label}. Check your internet connection and try again.")
        }
    }

    private companion object {
        const val TAG = "ProviderModelLister"
    }
}

private val modelJson = Json { ignoreUnknownKeys = true }

/**
 * Reads [provider]'s answer to "list models" (HTTP [status], [body]) and keeps the models this app can use.
 * OpenAI, Groq, DeepSeek and Anthropic answer `{"data": [{"id": …}]}`; Gemini answers
 * `{"models": [{"name": "models/…", "supportedGenerationMethods": […]}]}`.
 */
internal fun parseModelListing(provider: TranslationProvider, status: Int, body: String): ModelListing {
    if (status >= 400) {
        return ModelListing.Failed(
            when (status) {
                400, 401, 403 -> "${provider.label} didn't accept the API key. Check your key and try again."
                429 -> "${provider.label} is busy or rate-limited. Wait a moment and try again."
                else -> "${provider.label} couldn't list its models (HTTP $status). Try again later."
            }
        )
    }
    val root = try {
        modelJson.parseToJsonElement(body) as? JsonObject
    } catch (e: SerializationException) {
        null
    }
    val entries = (root?.get("data") ?: root?.get("models")) as? JsonArray
        ?: return ModelListing.Failed("${provider.label}'s list of models couldn't be read. Try again later.")

    val models = entries.mapNotNull { entry ->
        val model = entry as? JsonObject ?: return@mapNotNull null
        val id = (model.text("id") ?: model.text("name"))?.removePrefix("models/")?.trim().orEmpty()
        if (id.isEmpty() || !isUsableModel(provider, id, model)) return@mapNotNull null
        LiveModel(id, created = model.created())
    }.distinctBy { it.id }
    if (models.isEmpty()) return ModelListing.Failed("${provider.label} listed no models this app can use. Try again later.")
    return ModelListing.Found(models)
}

private fun JsonObject.text(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

/** OpenAI and Groq give `created` in seconds; Anthropic gives `created_at` as a date and time. */
private fun JsonObject.created(): Long? =
    (this["created"] as? JsonPrimitive)?.longOrNull
        ?: text("created_at")?.let { runCatching { Instant.parse(it).epochSeconds }.getOrNull() }

/** Words in a model's id that say it doesn't answer a chat prompt with text. */
private val NOT_CHAT = listOf(
    "embed", "audio", "realtime", "tts", "transcribe", "whisper", "image", "moderation", "guard", "robotics", "computer-use", "orpheus"
)

/** OpenAI's models for other endpoints than chat completions, which is the one this app calls. */
private val OPENAI_OTHER_ENDPOINTS = listOf("search", "instruct", "codex", "deep-research", "-pro")

/** A dated or numbered copy of a model that is also listed under its plain name: "gpt-4o-2024-08-06", "gpt-4-0613", "gemini-2.0-flash-001". */
private val SNAPSHOT = Regex("-(\\d{4}-\\d{2}-\\d{2}|\\d{2}-\\d{4}|\\d{2}-\\d{2}|\\d{3,4})(-preview)?$")

/**
 * Whether this app can use the model [id] that [provider] listed as [entry]: a model that answers a text
 * prompt with text, under its plain name. The lists also hold speech, image and embedding models, and dated
 * copies of the same model, which would only clutter the picker (or fail when picked).
 */
internal fun isUsableModel(provider: TranslationProvider, id: String, entry: JsonObject = JsonObject(emptyMap())): Boolean {
    val name = id.lowercase()
    if (NOT_CHAT.any { it in name }) return false
    return when (provider) {
        TranslationProvider.Claude -> name.startsWith("claude-")
        TranslationProvider.DeepSeek -> true
        TranslationProvider.Groq -> (entry["active"] as? JsonPrimitive)?.booleanOrNull != false
        TranslationProvider.OpenAI ->
            (name.startsWith("gpt-") || name.startsWith("chatgpt-") || Regex("^o\\d").containsMatchIn(name)) &&
                OPENAI_OTHER_ENDPOINTS.none { it in name } && !SNAPSHOT.containsMatchIn(name)
        TranslationProvider.Gemini -> {
            val methods = (entry["supportedGenerationMethods"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.content }
            name.startsWith("gemini-") && (methods == null || "generateContent" in methods) &&
                listOf("live", "vision", "-exp").none { it in name } && !SNAPSHOT.containsMatchIn(name)
        }
        else -> false
    }
}

/**
 * A model list brought up to date.
 *
 * @property models The list to offer from now on: the known models the provider still has, then the new ones.
 * @property added Models that weren't on the list, newest first.
 * @property removed Models the provider no longer offers.
 * @property selected The model to use: the one that was selected, or its stand-in if that one is gone.
 */
data class ModelListUpdate(val models: List<String>, val added: List<String>, val removed: List<String>, val selected: String)

/** Whether [live] is the dated copy of [alias], as Anthropic lists "claude-haiku-4-5" as "claude-haiku-4-5-20251001". */
private fun isSnapshotOf(live: String, alias: String): Boolean =
    live.length == alias.length + 9 && live.startsWith("$alias-") && live.takeLast(8).all { it.isDigit() }

/**
 * Brings the list [known] up to date with what the provider offers ([live], never empty).
 *
 * @param selected The model the user has picked; it stays on the list while the provider has it.
 * @param preferred The app's default model, picked instead of a [selected] that is gone if it is still there.
 */
fun updateModelList(known: List<String>, live: List<LiveModel>, selected: String, preferred: String): ModelListUpdate {
    fun isLive(id: String) = live.any { it.id == id || isSnapshotOf(it.id, id) }
    val before = (known + selected).filter { it.isNotBlank() }.distinct()
    val kept = before.filter(::isLive)
    val added = live
        .filter { model -> kept.none { it == model.id || isSnapshotOf(model.id, it) } }
        .sortedWith(compareByDescending<LiveModel> { it.created ?: 0L }.thenByDescending { it.id })
        .map { it.id }
    val models = kept + added
    val stillSelected = selected.takeIf { it in kept } ?: preferred.takeIf { it in models } ?: models.first()
    return ModelListUpdate(models, added, removed = known.filterNot { it in kept }, selected = stillSelected)
}

/** The API key of [provider], and the model picked for it; null for a provider without a model list. */
private fun SettingsRepository.keyOf(provider: TranslationProvider): Flow<String>? = when (provider) {
    TranslationProvider.Gemini -> apiKey
    TranslationProvider.OpenAI -> openAiKey
    TranslationProvider.Claude -> claudeKey
    TranslationProvider.Groq -> groqKey
    TranslationProvider.DeepSeek -> deepSeekKey
    else -> null
}

private fun SettingsRepository.modelOf(provider: TranslationProvider): Flow<String>? = when (provider) {
    TranslationProvider.Gemini -> geminiModel
    TranslationProvider.OpenAI -> openAiModel
    TranslationProvider.Claude -> claudeModel
    TranslationProvider.Groq -> groqModel
    TranslationProvider.DeepSeek -> deepSeekModel
    else -> null
}

private suspend fun SettingsRepository.pickModel(provider: TranslationProvider, model: String) = when (provider) {
    TranslationProvider.Gemini -> setGeminiModel(model)
    TranslationProvider.OpenAI -> setOpenAiModel(model)
    TranslationProvider.Claude -> setClaudeModel(model)
    TranslationProvider.Groq -> setGroqModel(model)
    TranslationProvider.DeepSeek -> setDeepSeekModel(model)
    else -> Unit
}

/** How a check for new models went, in words for the user. */
data class ModelCheckOutcome(val message: String, val failed: Boolean = false)

/**
 * Checks [provider]'s models with the user's key and stores the list brought up to date. A check that
 * fails (no key, no network, an answer that can't be read) changes nothing.
 */
suspend fun checkForNewModels(provider: TranslationProvider, settings: SettingsRepository, lister: ModelLister): ModelCheckOutcome {
    val key = settings.keyOf(provider)?.first()
    val selected = settings.modelOf(provider)?.first()
    if (key == null || selected == null) return ModelCheckOutcome("${provider.label} has no list of models to check.", failed = true)

    val live = when (val listing = lister.list(provider, key)) {
        is ModelListing.Failed -> return ModelCheckOutcome(listing.message, failed = true)
        is ModelListing.Found -> listing.models
    }
    if (live.isEmpty()) return ModelCheckOutcome("${provider.label} listed no models this app can use. Try again later.", failed = true)

    val known = settings.modelOptions(provider).first()
    val update = updateModelList(known, live, selected, preferred = SettingsDefaults.model(provider))
    settings.setModelOptions(provider, update.models)
    if (update.selected != selected) settings.pickModel(provider, update.selected)

    fun models(count: Int) = if (count == 1) "1 model" else "$count models"
    val changes = when {
        update.added.isEmpty() && update.removed.isEmpty() -> "Your ${provider.label} models are up to date."
        update.removed.isEmpty() -> "Added ${models(update.added.size)} new to ${provider.label}."
        update.added.isEmpty() -> "Removed ${models(update.removed.size)} that ${provider.label} no longer offers."
        else -> "Added ${models(update.added.size)} and removed ${models(update.removed.size)} that ${provider.label} no longer offers."
    }
    val moved = if (update.selected == selected) "" else " Your model $selected is gone, so ${update.selected} is selected now."
    return ModelCheckOutcome(changes + moved)
}

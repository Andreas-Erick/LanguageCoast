package com.andreaserick.languagecoast.data

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf

/** The translation engine for the provider picked in Settings, or why it can't be used yet. */
sealed interface EngineChoice {
    data class Ready(val engine: TranslationEngine) : EngineChoice

    /** The provider needs setting up first; [message] says what is missing. */
    data class Missing(val message: String) : EngineChoice
}

/** What the user is told when cloud [provider] has no API key yet. */
fun missingKeyMessage(provider: TranslationProvider): String = "${provider.label} needs an API key. Add one in Settings."

/** What the user is told when cloud [provider] has a key, but they haven't allowed sending text to it yet. */
fun sharingNotAllowedMessage(provider: TranslationProvider): String =
    "Allow Language Coast to send text to ${provider.label} first. You can do that under Settings."

/** The engine for the provider picked in Settings, with its key and model from Settings. */
suspend fun SettingsRepository.currentEngine(): EngineChoice = currentEngine(translationProvider.first())

/**
 * The engine for [provider], with its key and model from Settings. A cloud provider is only ready once it
 * has a key and the user has allowed sending text to it ([SettingsRepository.aiSharingAllowed]). Everything
 * that sends text asks here first, so nothing reaches a provider without that permission.
 */
suspend fun SettingsRepository.currentEngine(provider: TranslationProvider): EngineChoice {
    if (provider.isCloud) {
        if (keyOf(provider).first().isBlank()) return EngineChoice.Missing(missingKeyMessage(provider))
        if (provider.name !in aiSharingAllowed.first()) return EngineChoice.Missing(sharingNotAllowedMessage(provider))
    }
    val engine = when (provider) {
        TranslationProvider.OnDevice -> TranslationEngine.OnDevice
        TranslationProvider.LocalAi -> TranslationEngine.LocalAi(modelId = localAiModel.first().ifBlank { SettingsDefaults.LOCAL_AI_MODEL })
        TranslationProvider.Gemini -> TranslationEngine.Gemini(apiKey.first(), geminiModel.first())
        TranslationProvider.OpenAI -> TranslationEngine.OpenAI(openAiKey.first(), openAiModel.first().trim().ifBlank { SettingsDefaults.OPENAI_MODEL })
        TranslationProvider.Claude -> TranslationEngine.Claude(claudeKey.first(), claudeModel.first().trim().ifBlank { SettingsDefaults.CLAUDE_MODEL })
        TranslationProvider.Groq -> TranslationEngine.Groq(groqKey.first(), groqModel.first().trim().ifBlank { SettingsDefaults.GROQ_MODEL })
        TranslationProvider.DeepSeek -> TranslationEngine.DeepSeek(deepSeekKey.first(), deepSeekModel.first().trim().ifBlank { SettingsDefaults.DEEPSEEK_MODEL })
        TranslationProvider.OpenRouter ->
            TranslationEngine.OpenRouter(openRouterKey.first(), openRouterModel.first().trim().ifBlank { SettingsDefaults.OPENROUTER_MODEL })
    }
    return EngineChoice.Ready(engine)
}

/** The saved API key of [provider]; "" for the providers that need none (on-device translation and Local AI). */
fun SettingsRepository.keyOf(provider: TranslationProvider): Flow<String> = when (provider) {
    TranslationProvider.OnDevice, TranslationProvider.LocalAi -> flowOf("")
    TranslationProvider.Gemini -> apiKey
    TranslationProvider.OpenAI -> openAiKey
    TranslationProvider.Claude -> claudeKey
    TranslationProvider.Groq -> groqKey
    TranslationProvider.DeepSeek -> deepSeekKey
    TranslationProvider.OpenRouter -> openRouterKey
}

/**
 * Whether the provider picked in Settings can translate right now: always for on-device translation and
 * Local AI, and for a cloud provider once it has a key and the user has allowed sending text to it. Emits
 * again when the provider, its key or the permission changes.
 */
@OptIn(ExperimentalCoroutinesApi::class)
fun SettingsRepository.engineReady(): Flow<Boolean> =
    translationProvider.flatMapLatest { provider ->
        if (!provider.isCloud) flowOf(true)
        else combine(keyOf(provider), aiSharingAllowed) { key, allowed -> key.isNotBlank() && provider.name in allowed }
    }.distinctUntilChanged()

/**
 * The picked provider while it is a cloud provider with a key saved that the user hasn't allowed sending
 * text to yet, which is when the app asks for that permission; otherwise null.
 */
@OptIn(ExperimentalCoroutinesApi::class)
fun SettingsRepository.providerAwaitingPermission(): Flow<TranslationProvider?> =
    translationProvider.flatMapLatest { provider ->
        if (!provider.isCloud) flowOf<TranslationProvider?>(null)
        else combine(keyOf(provider), aiSharingAllowed) { key, allowed -> provider.takeIf { key.isNotBlank() && provider.name !in allowed } }
    }.distinctUntilChanged()

/**
 * A cloud engine that can stand in when the picked provider gives up: Gemini if its key is set, else the
 * first other cloud provider with a key. Null if none is set up (a provider the user hasn't allowed sending
 * text to doesn't count).
 */
suspend fun SettingsRepository.fallbackCloudEngine(): Pair<TranslationProvider, TranslationEngine>? {
    val order = listOf(TranslationProvider.Gemini) + TranslationProvider.entries.filter { it.isCloud && it != TranslationProvider.Gemini }
    for (provider in order) {
        val choice = currentEngine(provider)
        if (choice is EngineChoice.Ready) return provider to choice.engine
    }
    return null
}

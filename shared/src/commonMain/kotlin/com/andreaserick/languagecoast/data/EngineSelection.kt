package com.andreaserick.languagecoast.data

import kotlinx.coroutines.flow.first

/** The translation engine for the provider picked in Settings, or why it can't be used yet. */
sealed interface EngineChoice {
    data class Ready(val engine: TranslationEngine) : EngineChoice

    /** The provider needs setting up first; [message] says what is missing. */
    data class Missing(val message: String) : EngineChoice
}

/** The engine for the provider picked in Settings, with its key and model from Settings. */
suspend fun SettingsRepository.currentEngine(): EngineChoice = currentEngine(translationProvider.first())

/** The engine for [provider], with its key and model from Settings. */
suspend fun SettingsRepository.currentEngine(provider: TranslationProvider): EngineChoice {
    fun missing(name: String) = EngineChoice.Missing("Please enter $name API key in Settings first!")
    return when (provider) {
        TranslationProvider.OnDevice -> EngineChoice.Ready(TranslationEngine.OnDevice)
        TranslationProvider.LocalAi -> {
            val modelId = localAiModel.first().ifBlank { SettingsDefaults.LOCAL_AI_MODEL }
            EngineChoice.Ready(TranslationEngine.LocalAi(modelId = modelId))
        }
        TranslationProvider.Gemini -> {
            val key = apiKey.first()
            if (key.isBlank()) missing("a Gemini") else EngineChoice.Ready(TranslationEngine.Gemini(key, geminiModel.first()))
        }
        TranslationProvider.OpenAI -> {
            val key = openAiKey.first()
            if (key.isBlank()) missing("an OpenAI")
            else EngineChoice.Ready(TranslationEngine.OpenAI(key, openAiModel.first().trim().ifBlank { SettingsDefaults.OPENAI_MODEL }))
        }
        TranslationProvider.Claude -> {
            val key = claudeKey.first()
            if (key.isBlank()) missing("an Anthropic Claude")
            else EngineChoice.Ready(TranslationEngine.Claude(key, claudeModel.first().trim().ifBlank { SettingsDefaults.CLAUDE_MODEL }))
        }
        TranslationProvider.Groq -> {
            val key = groqKey.first()
            if (key.isBlank()) missing("a Groq")
            else EngineChoice.Ready(TranslationEngine.Groq(key, groqModel.first().trim().ifBlank { SettingsDefaults.GROQ_MODEL }))
        }
        TranslationProvider.DeepSeek -> {
            val key = deepSeekKey.first()
            if (key.isBlank()) missing("a DeepSeek")
            else EngineChoice.Ready(TranslationEngine.DeepSeek(key, deepSeekModel.first().trim().ifBlank { SettingsDefaults.DEEPSEEK_MODEL }))
        }
        TranslationProvider.OpenRouter -> {
            val key = openRouterKey.first()
            if (key.isBlank()) missing("an OpenRouter")
            else EngineChoice.Ready(TranslationEngine.OpenRouter(key, openRouterModel.first().trim().ifBlank { SettingsDefaults.OPENROUTER_MODEL }))
        }
    }
}

/**
 * A cloud engine that can stand in when the picked provider gives up: Gemini if its key is set, else the
 * first other cloud provider with a key. Null if none is set up.
 */
suspend fun SettingsRepository.fallbackCloudEngine(): Pair<TranslationProvider, TranslationEngine>? {
    val order = listOf(TranslationProvider.Gemini) + TranslationProvider.entries.filter { it.isCloud && it != TranslationProvider.Gemini }
    for (provider in order) {
        val choice = currentEngine(provider)
        if (choice is EngineChoice.Ready) return provider to choice.engine
    }
    return null
}

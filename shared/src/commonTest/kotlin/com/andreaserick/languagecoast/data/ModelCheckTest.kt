package com.andreaserick.languagecoast.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ModelCheckTest {

    private fun ids(listing: ModelListing) = (listing as ModelListing.Found).models.map { it.id }
    private fun message(listing: ModelListing) = (listing as ModelListing.Failed).message

    @Test
    fun claudesListIsReadWithWhenEachModelCameOut() {
        val body = """
            {"data":[
              {"type":"model","id":"claude-opus-5-5","display_name":"Claude Opus 5.5","created_at":"2026-09-10T00:00:00Z"},
              {"type":"model","id":"claude-haiku-4-5-20251001","display_name":"Claude Haiku 4.5","created_at":"2025-10-15T00:00:00Z"},
              {"type":"model","id":"not-a-claude-model"}
            ],"has_more":false,"first_id":"claude-opus-5-5","last_id":"claude-haiku-4-5-20251001"}
        """.trimIndent()

        val models = (parseModelListing(TranslationProvider.Claude, 200, body) as ModelListing.Found).models

        assertEquals(listOf("claude-opus-5-5", "claude-haiku-4-5-20251001"), models.map { it.id })
        assertTrue(models[0].created!! > models[1].created!!)
    }

    @Test
    fun openAisListKeepsTheChatModelsUnderTheirPlainNames() {
        val all = listOf(
            "gpt-4o", "gpt-4o-mini", "gpt-4.1", "gpt-4.1-mini", "gpt-4.1-nano", "gpt-5", "gpt-5-mini", "gpt-5-chat-latest", "o3", "o4-mini",
            "gpt-4-turbo", "gpt-3.5-turbo", "chatgpt-4o-latest",
            // Dated copies of the same models.
            "gpt-4o-2024-08-06", "gpt-4o-mini-2024-07-18", "gpt-4-0613", "gpt-3.5-turbo-0125", "gpt-4-1106-preview", "o3-2025-04-16",
            // Models for speech, pictures, search and other endpoints.
            "gpt-4o-audio-preview", "gpt-4o-realtime-preview", "gpt-4o-mini-tts", "gpt-4o-transcribe", "gpt-image-1", "dall-e-3", "whisper-1",
            "tts-1", "text-embedding-3-small", "omni-moderation-latest", "gpt-4o-search-preview", "gpt-3.5-turbo-instruct", "gpt-5-codex",
            "o3-deep-research", "o1-pro", "gpt-5-pro", "computer-use-preview", "davinci-002", "sora-2"
        )
        val body = """{"object":"list","data":[${all.mapIndexed { index, id -> """{"id":"$id","object":"model","created":${1700000000 + index},"owned_by":"openai"}""" }.joinToString(",")}]}"""

        assertEquals(
            listOf(
                "gpt-4o", "gpt-4o-mini", "gpt-4.1", "gpt-4.1-mini", "gpt-4.1-nano", "gpt-5", "gpt-5-mini", "gpt-5-chat-latest", "o3", "o4-mini",
                "gpt-4-turbo", "gpt-3.5-turbo", "chatgpt-4o-latest"
            ),
            ids(parseModelListing(TranslationProvider.OpenAI, 200, body))
        )
    }

    @Test
    fun geminisListKeepsTheModelsThatAnswerATextPrompt() {
        fun model(name: String, vararg methods: String) =
            """{"name":"models/$name","displayName":"$name","supportedGenerationMethods":[${methods.joinToString(",") { "\"$it\"" }}]}"""
        val body = """{"models":[
            ${model("gemini-3.5-flash-lite", "generateContent", "countTokens")},
            ${model("gemini-3.8-flash", "generateContent")},
            ${model("gemini-3.1-pro-preview", "generateContent")},
            ${model("gemini-flash-latest", "generateContent")},
            ${model("gemini-3.5-flash-001", "generateContent")},
            ${model("gemini-3.5-flash-preview-09-2026", "generateContent")},
            ${model("gemini-3.5-flash-preview-05-20", "generateContent")},
            ${model("gemini-3.5-flash-image", "generateContent")},
            ${model("gemini-3.5-flash-preview-tts", "generateContent")},
            ${model("gemini-3.5-flash-native-audio-latest", "bidiGenerateContent")},
            ${model("gemini-live-3.5-flash", "bidiGenerateContent")},
            ${model("gemini-3.0-flash-exp", "generateContent")},
            ${model("gemini-embedding-001", "embedContent")},
            ${model("gemma-3-27b-it", "generateContent")},
            ${model("imagen-4.0-generate-001", "predict")},
            ${model("gemini-only-counts", "countTokens")}
        ]}"""

        assertEquals(
            listOf("gemini-3.5-flash-lite", "gemini-3.8-flash", "gemini-3.1-pro-preview", "gemini-flash-latest"),
            ids(parseModelListing(TranslationProvider.Gemini, 200, body))
        )
    }

    @Test
    fun groqsListLeavesOutSpeechAndSafetyModelsAndTheOnesSwitchedOff() {
        val body = """{"object":"list","data":[
            {"id":"llama-3.3-70b-versatile","active":true,"created":1733447754},
            {"id":"openai/gpt-oss-120b","active":true,"created":1754408224},
            {"id":"qwen/qwen3-32b","active":true,"created":1748396646},
            {"id":"gemma2-9b-it","active":false,"created":1693721698},
            {"id":"whisper-large-v3","active":true,"created":1693721698},
            {"id":"meta-llama/llama-guard-4-12b","active":true,"created":1746743847},
            {"id":"playai-tts","active":true,"created":1740682771}
        ]}"""

        assertEquals(
            listOf("llama-3.3-70b-versatile", "openai/gpt-oss-120b", "qwen/qwen3-32b"),
            ids(parseModelListing(TranslationProvider.Groq, 200, body))
        )
        assertEquals(
            listOf("deepseek-chat", "deepseek-reasoner"),
            ids(parseModelListing(TranslationProvider.DeepSeek, 200, """{"object":"list","data":[{"id":"deepseek-chat","object":"model"},{"id":"deepseek-reasoner","object":"model"}]}"""))
        )
    }

    @Test
    fun anAnswerThatIsNoListSaysWhy() {
        assertTrue("didn't accept the API key" in message(parseModelListing(TranslationProvider.Claude, 401, """{"type":"error"}""")))
        // Gemini answers a wrong key with 400.
        assertTrue("didn't accept the API key" in message(parseModelListing(TranslationProvider.Gemini, 400, "{}")))
        assertTrue("rate-limited" in message(parseModelListing(TranslationProvider.OpenAI, 429, "")))
        assertTrue("HTTP 503" in message(parseModelListing(TranslationProvider.Groq, 503, "")))
        assertTrue("couldn't be read" in message(parseModelListing(TranslationProvider.OpenAI, 200, "<html>")))
        assertTrue("couldn't be read" in message(parseModelListing(TranslationProvider.OpenAI, 200, """{"something":"else"}""")))
        // A list without a single model the app can use is no reason to empty the picker.
        assertTrue("no models this app can use" in message(parseModelListing(TranslationProvider.OpenAI, 200, """{"data":[{"id":"whisper-1"}]}""")))
    }

    @Test
    fun theListKeepsWhatIsStillOfferedAddsTheNewAndDropsTheGone() {
        val known = listOf("claude-haiku-4-5", "claude-sonnet-4-5", "claude-3-5-haiku-latest", "claude-3-7-sonnet-latest")
        val live = listOf(
            LiveModel("claude-haiku-4-5-20251001", created = 100),
            LiveModel("claude-sonnet-5-5", created = 300),
            LiveModel("claude-opus-5-5", created = 400),
            LiveModel("claude-haiku-5-5", created = 400)
        )

        val update = updateModelList(known, live, selected = "claude-haiku-4-5", preferred = "claude-haiku-4-5")

        // The name the app knows stands for the dated copy the provider lists; new models come newest first.
        assertEquals(listOf("claude-haiku-4-5", "claude-opus-5-5", "claude-haiku-5-5", "claude-sonnet-5-5"), update.models)
        assertEquals(listOf("claude-opus-5-5", "claude-haiku-5-5", "claude-sonnet-5-5"), update.added)
        assertEquals(listOf("claude-sonnet-4-5", "claude-3-5-haiku-latest", "claude-3-7-sonnet-latest"), update.removed)
        assertEquals("claude-haiku-4-5", update.selected)
    }

    @Test
    fun aSelectedModelThatIsGoneGivesWayToTheDefaultOrTheFirst() {
        val live = listOf(LiveModel("claude-haiku-4-5-20251001"), LiveModel("claude-opus-5-5"))
        val known = listOf("claude-haiku-4-5", "claude-3-7-sonnet-latest")

        assertEquals("claude-haiku-4-5", updateModelList(known, live, selected = "claude-3-7-sonnet-latest", preferred = "claude-haiku-4-5").selected)
        // The default is gone as well: the first model on the list.
        val update = updateModelList(known, listOf(LiveModel("claude-opus-5-5")), selected = "claude-3-7-sonnet-latest", preferred = "claude-haiku-4-5")
        assertEquals(listOf("claude-opus-5-5") to "claude-opus-5-5", update.models to update.selected)

        // A selected model the list didn't have (picked in an earlier version) stays while it is offered.
        val kept = updateModelList(listOf("gpt-4.1-mini"), listOf(LiveModel("gpt-4.1-mini"), LiveModel("gpt-4o")), selected = "gpt-4o", preferred = "gpt-4.1-mini")
        assertEquals(listOf("gpt-4.1-mini", "gpt-4o") to "gpt-4o", kept.models to kept.selected)
        assertEquals(emptyList<String>() to emptyList<String>(), kept.added to kept.removed)

        // Nothing new, nothing gone.
        val same = updateModelList(listOf("a", "b"), listOf(LiveModel("b"), LiveModel("a")), selected = "b", preferred = "a")
        assertEquals(ModelListUpdate(listOf("a", "b"), emptyList(), emptyList(), "b"), same)
    }
}

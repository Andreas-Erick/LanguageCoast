package com.andreaserick.languagecoast.data

import kotlin.test.assertNull
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class TranslationResponseParserTest {

    @Test
    fun parsesWellFormedResponse() {
        val result = parseTranslationResponse("TRANSLATION: ¿Dónde está la estación?\nCATEGORY: Travel")

        assertTrue(result.isSuccess)
        assertEquals("¿Dónde está la estación?", result.translatedText)
        assertEquals("Travel", result.finalCategory)
    }

    @Test
    fun stripsMarkdownBoldAndTrailingLines() {
        val result = parseTranslationResponse("**TRANSLATION:** hestur, hesturinn, hestar\n**CATEGORY:** Animals\n\nHope this helps!")

        assertTrue(result.isSuccess)
        assertEquals("hestur, hesturinn, hestar", result.translatedText)
        assertEquals("Animals", result.finalCategory)
    }

    @Test
    fun parsesCodeFencedCaseInsensitiveAndReorderedLabels() {
        val result = parseTranslationResponse(
            """
            ```text
            - translation: [¿Cuánto cuesta esto?]
            - emoji: 🛍️
            - category: [Shopping]
            ```
            """.trimIndent()
        )

        assertTrue(result.isSuccess)
        assertEquals("¿Cuánto cuesta esto?", result.translatedText)
        assertEquals("Shopping", result.finalCategory)
        assertEquals("🛍️", result.emoji)
    }

    @Test
    fun failsWhenLabelsAreMissing() {
        assertFalse(parseTranslationResponse("Hola").isSuccess)
        assertFalse(parseTranslationResponse("TRANSLATION: Hola").isSuccess)
    }

    @Test
    fun placeholderCategoryFallsBackOrFails() {
        // Small local models write things like "(empty)" when they have no category: never an island name.
        listOf("(empty)", "none", "N/A", "[the category]").forEach { placeholder ->
            val reply = "TRANSLATION: Wo ist das Meer?\nCATEGORY: $placeholder"
            assertFalse(parseTranslationResponse(reply).isSuccess, placeholder)
            val withFallback = parseTranslationResponse(reply, fallbackCategory = "Travel")
            assertTrue(withFallback.isSuccess, placeholder)
            assertEquals("Travel", withFallback.finalCategory)
            assertEquals("Wo ist das Meer?", withFallback.translatedText)
        }
    }

    @Test
    fun promptNeverLeavesTheUserCategoryLabelEmpty() {
        val prompt = buildTranslationPrompt(request(existingCategories = emptyList()))
        assertFalse(prompt.contains("User Category: "), prompt)
        assertTrue(prompt.contains("did not choose a category"))
    }

    @Test
    fun failsWhenFieldsAreEmpty() {
        assertFalse(parseTranslationResponse("TRANSLATION:\nCATEGORY: Greetings").isSuccess)
        assertFalse(parseTranslationResponse("TRANSLATION: Hola\nCATEGORY:   ").isSuccess)
    }

    @Test
    fun promptListsExistingCategoriesAndLanguages() {
        val prompt = buildTranslationPrompt(request(existingCategories = listOf("Travel", "Food")))

        assertTrue(prompt.contains("from English into Icelandic"))
        assertTrue(prompt.contains("existing categories: Travel, Food"))
        // No stray quote after the input (a past bug).
        assertTrue(prompt.lines().contains("Input text: horse"))
    }

    @Test
    fun promptAsksForNewCategoryWhenNoneExist() {
        val prompt = buildTranslationPrompt(request(existingCategories = emptyList()))

        assertTrue(prompt.contains("You have no existing categories yet"))
    }

    @Test
    fun parsesAlternativesAndNote() {
        val result = parseTranslationResponse(
            "TRANSLATION: Wollen wir morgen ins Kino gehen?\nCATEGORY: Plans\nEMOJI: 🎬\n" +
                "ALTERNATIVES: Sollen wir morgen ins Kino gehen? || Hast du Lust, morgen ins Kino zu gehen?\n" +
                "NOTE: The second one is a more casual invitation."
        )

        assertEquals(
            listOf("Sollen wir morgen ins Kino gehen?", "Hast du Lust, morgen ins Kino zu gehen?"),
            result.alternatives
        )
        assertEquals("The second one is a more casual invitation.", result.note)
        assertEquals("🎬", result.emoji)
    }

    @Test
    fun noneMeansNoAlternativesAndNoNote() {
        val result = parseTranslationResponse("TRANSLATION: Hallo\nCATEGORY: Greetings\nALTERNATIVES: NONE\nNOTE: NONE")

        assertTrue(result.alternatives.isEmpty())
        assertEquals(null, result.note)
    }

    @Test
    fun alternativesRepeatingTheTranslationAreDropped() {
        val result = parseTranslationResponse("TRANSLATION: Hallo\nCATEGORY: Greetings\nALTERNATIVES: Hallo || Hallo || Servus\nNOTE: Servus is southern.")

        assertEquals(listOf("Servus"), result.alternatives)
    }

    @Test
    fun onlyLongerSentencesAskForAlternatives() {
        assertFalse(buildTranslationPrompt(request(emptyList())).contains("ALTERNATIVES:"))

        val long = buildTranslationPrompt(request(emptyList()).copy(nativeSentence = "Do you want to go to the cinema tomorrow?"))
        assertTrue(long.contains("ALTERNATIVES: [alternative 1] || [alternative 2]"))
        assertTrue(long.contains("explaining how they differ"))
        assertTrue(long.lines().contains("Input text: Do you want to go to the cinema tomorrow?"))
    }

    @Test
    fun normalizesRetiredGeminiModelsToCurrentIds() {
        assertEquals("gemini-3.5-flash-lite", normalizeGeminiModel("gemini-2.5-flash"))
        assertEquals("gemini-3.5-flash-lite", normalizeGeminiModel("models/gemini-2.0-flash"))
        assertEquals("gemini-3.5-flash-lite", normalizeGeminiModel(""))
        assertEquals("gemini-3.1-pro-preview", normalizeGeminiModel("gemini-2.5-pro"))
        assertEquals("gemini-3.8-flash", normalizeGeminiModel(" gemini-3.8-flash "))
        assertEquals("gemini-3.5-flash", normalizeGeminiModel("gemini-3.5-flash"))
    }

    @Test
    fun readsTheReplacementModelOutOfGooglesNotFoundError() {
        val body = """{"error":{"code":404,"message":"This model models/gemini-2.5-pro is no longer available to new users. """ +
            """Please update your code to use models/gemini-3.1-pro-preview for the latest features and improvements.","status":"NOT_FOUND"}}"""
        assertEquals("gemini-3.1-pro-preview", suggestedGeminiModel(body))
        assertEquals(null, suggestedGeminiModel("""{"error":{"code":404,"message":"models/foo is not found"}}"""))
    }

    @Test
    fun recommendsBestMatchLocalModelForDeviceHardware() {
        val midRangeProfile = DeviceHardwareProfile(
            deviceName = "Pixel 8a",
            socOrProcessor = "Tensor G3",
            cpuCores = 8,
            primaryAbi = "arm64-v8a",
            isArm64 = true,
            totalRamMb = 7_800L,
            availableRamMb = 4_200L,
            freeStorageMb = 24_000L,
            totalStorageMb = 128_000L,
            gpuAccelerationSupported = true
        )

        val recommendations = recommendModelsForHardware(midRangeProfile)
        assertEquals(LocalAiCatalog.MODELS.size, recommendations.size)
        val bestMatch = recommendations.firstOrNull { it.fitLevel == ModelFitLevel.BEST_MATCH }
        assertNotNull(bestMatch)
        assertEquals("GPU (OpenCL)", bestMatch.recommendedBackend)
        assertTrue(bestMatch.hasEnoughStorage)

        val lowStorageProfile = midRangeProfile.copy(freeStorageMb = 400L)
        val lowStorageRecs = recommendModelsForHardware(lowStorageProfile)
        assertTrue(lowStorageRecs.all { !it.hasEnoughStorage && it.fitLevel == ModelFitLevel.NOT_RECOMMENDED })
    }

    private fun request(existingCategories: List<String>) = TranslationRequest(
        engine = TranslationEngine.OnDevice,
        nativeSentence = "horse",
        nativeLanguage = "English",
        targetLanguage = "Icelandic",
        userCategory = "",
        existingCategories = existingCategories
    )

    @Test
    fun correctedSentenceAndItsNoteAreRead() {
        val result = parseTranslationResponse(
            "TRANSLATION: Where is the beach?\nCORRECTED: Wo ist der Strand?\nNOTE: \"Strand\" is masculine.\nCATEGORY: Travel\nEMOJI: ✈️"
        )
        assertTrue(result.isSuccess)
        assertEquals("Where is the beach?", result.translatedText)
        assertEquals("Wo ist der Strand?", result.corrected)
        assertEquals("\"Strand\" is masculine.", result.note)
    }

    @Test
    fun correctSentenceHasNoCorrection() {
        val result = parseTranslationResponse("TRANSLATION: Where is the beach?\nCORRECTED: NONE\nNOTE: NONE\nCATEGORY: Travel\nEMOJI: ✈️")
        assertTrue(result.isSuccess)
        assertNull(result.corrected)
        assertNull(result.note)
    }
}

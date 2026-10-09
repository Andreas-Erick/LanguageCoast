package com.andreaserick.languagecoast.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

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
    fun failsWhenLabelsAreMissing() {
        assertFalse(parseTranslationResponse("Hola").isSuccess)
        assertFalse(parseTranslationResponse("TRANSLATION: Hola").isSuccess)
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

    private fun request(existingCategories: List<String>) = TranslationRequest(
        engine = TranslationEngine.OnDevice,
        nativeSentence = "horse",
        nativeLanguage = "English",
        targetLanguage = "Icelandic",
        userCategory = "",
        existingCategories = existingCategories
    )
}

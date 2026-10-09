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

    private fun request(existingCategories: List<String>) = TranslationRequest(
        engine = TranslationEngine.OnDevice,
        nativeSentence = "horse",
        nativeLanguage = "English",
        targetLanguage = "Icelandic",
        userCategory = "",
        existingCategories = existingCategories
    )
}

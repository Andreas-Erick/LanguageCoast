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
}

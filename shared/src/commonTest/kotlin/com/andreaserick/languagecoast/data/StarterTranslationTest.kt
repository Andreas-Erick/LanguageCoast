package com.andreaserick.languagecoast.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StarterTranslationTest {

    private val greetings = PhraseBank.theme("Greetings")!!

    @Test
    fun promptNumbersTheEntriesAndCarriesTheTopicsRules() {
        val prompt = buildStarterTranslationPrompt(PhraseBank.theme("Phone Settings")!!, "English", "German", null, listOf("Wi-Fi", "Turn off"))

        assertTrue("1. Wi-Fi" in prompt)
        assertTrue("2. Turn off" in prompt)
        assertTrue("system settings" in prompt)
        assertTrue("[number] || [the entry in German]" in prompt)
        assertFalse("the entry in English" in prompt)
    }

    @Test
    fun promptAsksForBothSidesWhenTheUserIsNotEnglishSpeaking() {
        val prompt = buildStarterTranslationPrompt(greetings, "Dutch", "German", null, listOf("Hello, how are you?"))

        assertTrue("[number] || [the entry in Dutch] || [the entry in German]" in prompt)
        assertTrue("front of the card" in prompt)
    }

    @Test
    fun promptsCarryTheRegionWhenOneIsGiven() {
        val translate = buildStarterTranslationPrompt(greetings, "English", "Spanish", "Mexico", listOf("Hello!"))
        val check = buildNaturalCheckPrompt(greetings, "English", "Spanish", "Mexico", listOf(ProposedSentence("Hello!", "¡Hola!", "Greetings")))
        val suggest = buildSuggestionPrompt("Spanish", "English", null, 3, emptyList(), emptyList(), 0, region = "Mexico")

        assertTrue("Spanish as spoken in Mexico" in translate)
        assertTrue("register of Mexico" in translate)
        assertTrue("native speaker of Spanish from Mexico" in check)
        assertTrue("1. Hello! => ¡Hola!" in check)
        assertTrue("use it in Mexico" in suggest)
        assertTrue("the places where it is spoken" in buildNaturalCheckPrompt(greetings, "English", "Spanish", null, emptyList()))
    }

    @Test
    fun naturalCheckParsesFixesAndIgnoresNone() {
        assertTrue(parseNaturalCheck("NONE", 3).isEmpty())
        val fixes = parseNaturalCheck("2 || ¿Qué onda? || In Mexico this is the casual greeting.\n3 || NONE || fine", 3)

        assertEquals(mapOf(2 to ("¿Qué onda?" to "In Mexico this is the casual greeting.")), fixes)
    }

    @Test
    fun everyTopicHasRulesAndTheDefaultAsksForPoliteAddress() {
        PhraseBank.THEMES.forEach { assertTrue(topicRules(it.category).isNotBlank(), it.category) }
        assertTrue("polite form" in topicRules("Restaurant"))
        assertTrue("digits" in topicRules("Numbers"))
    }

    @Test
    fun parsesNumberedLinesAndToleratesDecoration() {
        val parsed = parseStarterTranslations(
            """
            Here you go:
            1 || Hallo, wie geht's?
            2. || [Guten Morgen!]
            - 3) || **Guten Abend!**
            4 Guten Tag (no separator, dropped)
            5 || Schönen Tag noch!
            5 || repeated number, dropped
            7 || out of range, dropped
            """.trimIndent(),
            count = 6,
            bothSides = false
        )

        assertEquals(mapOf(1 to listOf("Hallo, wie geht's?"), 2 to listOf("Guten Morgen!"), 3 to listOf("Guten Abend!"), 5 to listOf("Schönen Tag noch!")), parsed)
    }

    @Test
    fun parsesBothSidesAndDropsLinesMissingOne() {
        val parsed = parseStarterTranslations("1 || Hallo, hoe gaat het? || Hallo, wie geht's?\n2 || Goedemorgen!", count = 2, bothSides = true)

        assertEquals(listOf("Hallo, hoe gaat het?", "Hallo, wie geht's?"), parsed[1])
        assertNull(parsed[2])
    }

    @Test
    fun parsingKeepsTheLastTwoSidesIfAModelAddsTheEnglishFirst() {
        val parsed = parseStarterTranslations("1 || Hello || Hallo || Hallo, wie geht's?", count = 1, bothSides = true)

        assertEquals(listOf("Hallo", "Hallo, wie geht's?"), parsed[1])
    }
}

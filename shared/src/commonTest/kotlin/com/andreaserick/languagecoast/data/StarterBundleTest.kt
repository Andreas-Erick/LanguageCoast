package com.andreaserick.languagecoast.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StarterBundleTest {

    @Test
    fun theShippedSentencesAreTheBanksOwnGreetings() {
        val greetings = assertNotNull(PhraseBank.theme(StarterBundle.CATEGORY)).sentences
        assertTrue(StarterBundle.SENTENCES.all { it in greetings })
    }

    @Test
    fun everyLanguageIsOneACoastCanBeInAndHasEverySentence() {
        val names = Languages.ALL.map { it.name }.toSet()
        StarterBundle.TRANSLATIONS.forEach { (language, sentences) ->
            assertTrue(language in names, "$language is not a coast language")
            assertEquals(StarterBundle.SENTENCES.size, sentences.size, language)
            assertTrue(sentences.all { it.isNotBlank() }, language)
        }
    }

    @Test
    fun bothSidesComeFromTheTable() {
        val first = StarterBundle.firstSentences("German", "Spanish")

        assertEquals(listOf("Hallo, wie geht's?", "Vielen Dank.", "Ja, bitte."), first.map { it.nativeText })
        assertEquals(listOf("Hola, ¿qué tal?", "Muchas gracias.", "Sí, por favor."), first.map { it.targetText })
        assertTrue(first.all { it.category == StarterBundle.CATEGORY })
    }

    @Test
    fun nothingWhereALanguageIsMissingOrBothAreTheSame() {
        assertTrue(StarterBundle.firstSentences("English", "Portuguese").isEmpty())
        assertTrue(StarterBundle.firstSentences("Albanian", "German").isEmpty())
        assertTrue(StarterBundle.firstSentences("German", "German").isEmpty())
    }

    @Test
    fun aGoalPreviewNamesTheTopicItPointsTo() {
        val (theme, sentence) = assertNotNull(PhraseBank.goalPreview("I want to speak and understand people", mapOf(PhraseBank.LANGUAGE_PLACEHOLDER to "German")))

        assertEquals("Getting By", theme.category)
        assertEquals("Could we speak German, please? I'm learning it.", sentence)
    }

    @Test
    fun aGoalThatNamesNoTopicHasNoPreview() {
        assertNull(PhraseBank.goalPreview("Just for fun"))
        assertNull(PhraseBank.goalPreview(""))
    }
}

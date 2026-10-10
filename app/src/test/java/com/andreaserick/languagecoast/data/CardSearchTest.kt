package com.andreaserick.languagecoast.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CardSearchTest {

    private val card = Flashcard(
        islandId = 1, nativeText = "Where is the train station?", targetText = "Wo ist der Bahnhof?",
        alternatives = listOf("Wo befindet sich der Bahnhof?")
    )

    @Test
    fun wordsAreLowercaseWithoutPunctuation() {
        assertEquals(listOf("wo", "ist", "der", "strand"), normalizedWords("  Wo ist  der Strand? "))
        assertEquals(listOf("dónde", "está"), normalizedWords("¿Dónde está?"))
        assertTrue(isSameSentence("hola amigo", "¡Hola, amigo!"))
        assertFalse(isSameSentence("cafe", "café"))
    }

    @Test
    fun searchFindsNativeTextTranslationAndAlternatives() {
        assertTrue(matchesSearch(card, "train"))
        assertTrue(matchesSearch(card, "BAHNHOF"))
        assertTrue(matchesSearch(card, "befindet"))
    }

    @Test
    fun searchWordsMayBePartialButMustAllMatch() {
        assertTrue(matchesSearch(card, "bahn"))
        assertTrue(matchesSearch(card, "station wo"))
        assertFalse(matchesSearch(card, "station airport"))
    }

    @Test
    fun anEmptySearchFindsNothing() {
        assertFalse(matchesSearch(card, ""))
        assertFalse(matchesSearch(card, " ?! "))
    }
}

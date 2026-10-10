package com.andreaserick.languagecoast.ui.components

import com.andreaserick.languagecoast.data.Flashcard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CardEditTest {

    private val card = Flashcard(
        cardId = 7, islandId = 1, nativeText = "Do you want to come?", targetText = "Willst du kommen?",
        alternatives = listOf("Möchtest du kommen?"), note = "Möchtest is more polite."
    )

    @Test
    fun textsAreTrimmedAndTheIslandIsKept() {
        val edit = cardEditOf(card, "  Do you want to come along? ", " Kommst du mit? ", "Möchtest du kommen?", islandId = 2)!!

        assertEquals("Do you want to come along?", edit.nativeText)
        assertEquals("Kommst du mit?", edit.targetText)
        assertEquals(2, edit.islandId)
        assertEquals("Möchtest is more polite.", edit.note)
    }

    @Test
    fun alternativesAreOnePerLineWithoutBlanksRepeatsOrTheTranslation() {
        val edit = cardEditOf(card, card.nativeText, "Willst du kommen?", "a\n\n  b \na\nWillst du kommen?\n", islandId = 1)!!

        assertEquals(listOf("a", "b"), edit.alternatives)
    }

    @Test
    fun theNoteGoesWithTheLastAlternative() {
        val edit = cardEditOf(card, card.nativeText, card.targetText, " \n", islandId = 1)!!

        assertEquals(emptyList<String>(), edit.alternatives)
        assertNull(edit.note)
    }

    @Test
    fun blankTextsCannotBeSaved() {
        assertNull(cardEditOf(card, " ", card.targetText, "", islandId = 1))
        assertNull(cardEditOf(card, card.nativeText, "", "", islandId = 1))
    }
}

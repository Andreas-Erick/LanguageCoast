package com.andreaserick.languagecoast.ui.study

import com.andreaserick.languagecoast.data.Flashcard
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.Test

class AnswerMatchTest {

    @Test
    fun caseAndPunctuationAndSpacesDontMatter() {
        assertTrue(answerMatches("wo ist der strand", "Wo ist der Strand?"))
        assertTrue(answerMatches("  Hola,   amigo ", "¡Hola, amigo!"))
        assertTrue(answerMatches("hestur hesturinn hestar", "hestur, hesturinn, hestar"))
    }

    @Test
    fun wordsAndAccentsMustMatch() {
        assertFalse(answerMatches("Wo ist das Strand", "Wo ist der Strand?"))
        assertFalse(answerMatches("Gruss", "Gruß"))
        assertFalse(answerMatches("cafe", "café"))
        assertFalse(answerMatches("Wo ist", "Wo ist der Strand?"))
    }

    @Test
    fun anAlternativeCountsAsCorrect() {
        val card = Flashcard(
            islandId = 1, nativeText = "Do you want to come?", targetText = "Möchtest du kommen?",
            alternatives = listOf("Willst du kommen?")
        )

        assertTrue(isCorrectAnswer("willst du kommen", card))
        assertTrue(isCorrectAnswer("Möchtest du kommen", card))
        assertFalse(isCorrectAnswer("Kommst du?", card))
    }

    @Test
    fun reversedTheNativeTextIsTheAnswer() {
        val card = Flashcard(
            islandId = 1, nativeText = "Do you want to come?", targetText = "Möchtest du kommen?",
            alternatives = listOf("Willst du kommen?")
        )

        assertTrue(isCorrectAnswer("do you want to come", card, reversed = true))
        assertFalse(isCorrectAnswer("Möchtest du kommen?", card, reversed = true))
        assertFalse(isCorrectAnswer("Willst du kommen?", card, reversed = true))
    }

    @Test
    fun oneLineOfACardWithSeveralCountsWhenTyped() {
        val card = Flashcard(cardId = 1, islandId = 1, nativeText = "dog", targetText = "der Hund\npl. die Hunde", alternatives = listOf("der Köter"))

        assertTrue(isCorrectAnswer("der hund", card))
        assertTrue(isCorrectAnswer("der Köter", card))
        assertFalse(isCorrectAnswer("die Hunde", card))
        assertTrue(isCorrectAnswer("dog", card, reversed = true))
    }

    @Test
    fun aListOfMeaningsIsAnsweredByAnyOneOfThem() {
        val card = Flashcard(cardId = 1, islandId = 1, nativeText = "to run\n(verb)", targetText = "1. laufen\n2. rennen\n3. leiten")

        assertTrue(isCorrectAnswer("rennen", card))
        assertTrue(isCorrectAnswer("1. laufen 2. rennen 3. leiten", card))
        assertFalse(isCorrectAnswer("gehen", card))
        // Reversed, the native side is the answer, and one of its lines is enough as well.
        assertTrue(isCorrectAnswer("to run", card, reversed = true))
        assertFalse(isCorrectAnswer("laufen", card, reversed = true))
    }

    @Test
    fun aCardWithOneLineIsCheckedAsBefore() {
        val card = Flashcard(cardId = 1, islandId = 1, nativeText = "Where is the beach?", targetText = "Wo ist der Strand?")

        assertTrue(isCorrectAnswer("wo ist der strand", card))
        assertFalse(isCorrectAnswer("wo ist der", card))
    }
}

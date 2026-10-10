package com.andreaserick.languagecoast.ui.study

import com.andreaserick.languagecoast.data.Flashcard
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

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
}

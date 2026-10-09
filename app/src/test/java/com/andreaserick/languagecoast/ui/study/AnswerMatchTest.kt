package com.andreaserick.languagecoast.ui.study

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
}

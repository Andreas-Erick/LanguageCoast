package com.andreaserick.languagecoast.ui.components

import kotlin.test.Test
import kotlin.test.assertEquals

class SupportTest {

    @Test
    fun percentEncode_keepsLettersDigitsAndUnreservedMarks() {
        assertEquals("Az09-._~", percentEncode("Az09-._~"))
    }

    @Test
    fun percentEncode_writesEverythingElseAsUtf8Bytes() {
        assertEquals("a%20b%26c%3Fd%3De", percentEncode("a b&c?d=e"))
        assertEquals("%C3%BC", percentEncode("ü"))
    }

    @Test
    fun mailtoUri_encodesSubjectAndBody_withCrLfLineBreaks() {
        assertEquals(
            "mailto:me@example.com?subject=Report%20an%20AI%20answer&body=Card%3A%20Hallo%0D%0AAnswer%3A%20Hi",
            mailtoUri("me@example.com", "Report an AI answer", "Card: Hallo\nAnswer: Hi")
        )
    }
}

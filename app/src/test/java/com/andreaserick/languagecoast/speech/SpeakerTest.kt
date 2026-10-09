package com.andreaserick.languagecoast.speech

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SpeakerTest {

    @Test
    fun languagesMapToTheirVoiceLocale() {
        assertEquals("de", speechLocale("German")?.language)
        assertEquals("is", speechLocale("Icelandic")?.language)
    }

    @Test
    fun norwegianUsesBokmalVoices() {
        assertEquals("nb", speechLocale("Norwegian")?.language)
    }

    @Test
    fun unknownLanguagesHaveNoLocale() {
        assertNull(speechLocale("Klingon"))
    }
}

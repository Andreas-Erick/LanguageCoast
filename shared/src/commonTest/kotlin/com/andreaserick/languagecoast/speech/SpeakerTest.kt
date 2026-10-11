package com.andreaserick.languagecoast.speech

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SpeakerTest {

    @Test
    fun languagesMapToTheirVoiceLanguage() {
        assertEquals("de", speechLanguageCode("German"))
        assertEquals("is", speechLanguageCode("Icelandic"))
    }

    @Test
    fun norwegianUsesBokmalVoices() {
        assertEquals("nb", speechLanguageCode("Norwegian"))
    }

    @Test
    fun unknownLanguagesHaveNoVoice() {
        assertNull(speechLanguageCode("Klingon"))
    }
}

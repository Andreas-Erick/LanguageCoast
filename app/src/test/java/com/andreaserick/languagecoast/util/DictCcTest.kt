package com.andreaserick.languagecoast.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DictCcTest {

    @Test
    fun pairUsesCodesInAlphabeticalOrder() {
        assertEquals("enes", getDictCcPrefix("English", "Spanish"))
        assertEquals("enes", getDictCcPrefix("Spanish", "English"))
        assertEquals("bgde", getDictCcPrefix("German", "Bulgarian"))
        assertEquals("daen", getDictCcPrefix("English", "Danish"))
    }

    @Test
    fun germanPairsAreUsedForGermanSpeakers() {
        assertEquals("dees", getDictCcPrefix("German", "Spanish"))
        assertEquals("deen", getDictCcPrefix("German", "English"))
    }

    @Test
    fun pairWithoutEnglishOrGermanFallsBackToEnglishDictionary() {
        // dict.cc has no French/Spanish dictionary.
        assertEquals("enes", getDictCcPrefix("French", "Spanish"))
        // ...and Japanese speakers learning Icelandic get the English one too.
        assertEquals("enis", getDictCcPrefix("Japanese", "Icelandic"))
    }

    @Test
    fun languagesMissingFromDictCcHaveNoLookup() {
        assertNull(getDictCcPrefix("English", "Japanese"))
        assertNull(dictCcSearchUrl("ねこ", "German", "Korean"))
    }

    @Test
    fun searchUrlEncodesTheWord() {
        assertEquals(
            "https://enis.dict.cc/?s=h%C3%BAs",
            dictCcSearchUrl("hús", "English", "Icelandic")
        )
    }
}

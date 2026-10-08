package com.andreaserick.languagecoast.util

import org.junit.Assert.assertEquals
import org.junit.Test

class DictCcTest {

    @Test
    fun englishIsAlwaysSecond() {
        assertEquals("esen", getDictCcPrefix("English", "Spanish"))
        assertEquals("esen", getDictCcPrefix("Spanish", "English"))
    }

    @Test
    fun nonEnglishPairKeepsNativeFirst() {
        assertEquals("dees", getDictCcPrefix("German", "Spanish"))
    }

    @Test
    fun searchUrlEncodesTheWord() {
        assertEquals(
            "https://isen.dict.cc/?s=h%C3%BAs",
            dictCcSearchUrl("hús", "English", "Icelandic")
        )
    }
}

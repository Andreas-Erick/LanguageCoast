package com.andreaserick.languagecoast.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LanguagesTest {

    private fun searchNames(query: String) = Languages.ALL.search(query).map { it.name }

    @Test
    fun emptyQueryReturnsEverything() {
        assertEquals(Languages.ALL, Languages.ALL.search("  "))
    }

    @Test
    fun searchMatchesEnglishAndNativeNamesIgnoringCaseAndAccents() {
        assertEquals(listOf("German"), searchNames("deutsch"))
        assertEquals(listOf("Icelandic"), searchNames("islenska"))
        assertEquals(listOf("Czech"), searchNames("cestina"))
        assertEquals(listOf("Greek"), searchNames("ελλ"))
    }

    @Test
    fun namesStartingWithQueryComeFirst() {
        assertEquals("Russian", searchNames("rus").first())

        val s = searchNames("s")
        // Swedish starts with "s"; Russian only contains it.
        assertTrue(s.indexOf("Swedish") < s.indexOf("Russian"))
        // Native names count as prefixes too: Finnish is "Suomi".
        assertTrue(s.indexOf("Finnish") < s.indexOf("Russian"))
    }

    @Test
    fun unknownQueryFindsNothing() {
        assertTrue(searchNames("klingon").isEmpty())
    }

    @Test
    fun catalogIsSortedAndHasUniqueNamesAndCodes() {
        assertEquals(Languages.ALL.sortedBy { it.name }, Languages.ALL)
        assertEquals(Languages.ALL.size, Languages.ALL.map { it.name }.toSet().size)
        assertEquals(Languages.ALL.size, Languages.ALL.map { it.code }.toSet().size)
    }

    @Test
    fun everyLanguageOfferedBeforeIsStillAvailable() {
        // Coasts and settings store these names, so removing one would orphan existing data.
        listOf("English", "Spanish", "French", "German", "Italian", "Japanese", "Korean", "Icelandic", "Norwegian")
            .forEach { assertTrue(it, Languages.byName(it) != null) }
    }
}

package com.andreaserick.languagecoast.data

import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.Test

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
    fun flagIsTheCountryNotTheLanguageCode() {
        assertEquals("🇩🇪", Languages.byName("German")?.flag)
        assertEquals("🇯🇵", Languages.byName("Japanese")?.flag) // JP, not JA
        assertEquals("🇬🇧", Languages.byName("English")?.flag) // GB, not EN
    }

    @Test
    fun esperantoShowsTheWorld() {
        assertEquals("🌍", Languages.byName("Esperanto")?.flag)
        assertEquals(listOf("Esperanto"), Languages.ALL.filter { it.flagCountry == null }.map { it.name })
    }

    @Test
    fun flagCountriesAreTwoCapitalLetters() {
        Languages.ALL.mapNotNull { it.flagCountry }.forEach { country ->
            assertTrue(country.length == 2 && country.all { it in 'A'..'Z' }, country)
        }
    }

    @Test
    fun everyLanguageOfferedBeforeIsStillAvailable() {
        // Coasts and settings store these names, so removing one would orphan existing data.
        listOf("English", "Spanish", "French", "German", "Italian", "Japanese", "Korean", "Icelandic", "Norwegian")
            .forEach { assertTrue(Languages.byName(it) != null, it) }
    }
}

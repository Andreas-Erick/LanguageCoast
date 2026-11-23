package com.andreaserick.languagecoast.data

import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
    fun languagesNotWrittenInLatinLettersAreMarked() {
        // Reading text from a photo only knows Latin letters, so it is offered for the others only.
        assertEquals(
            listOf("Bulgarian", "Greek", "Japanese", "Korean", "Russian", "Serbian", "Ukrainian"),
            Languages.ALL.filterNot { it.latinScript }.map { it.name }
        )
        assertFalse(Languages.isLatinScript("Russian"))
        assertTrue(Languages.isLatinScript("German"))
        // Every one of them shows it in its own name.
        Languages.ALL.filterNot { it.latinScript }.forEach { language ->
            assertTrue(language.nativeName.none { it in 'a'..'z' || it in 'A'..'Z' }, language.name)
        }
    }

    @Test
    fun onlyJapaneseIsWrittenWithoutSpacesBetweenWords() {
        // The gap exercise and tapping a word cut a sentence at its spaces.
        assertEquals(listOf("Japanese"), Languages.ALL.filterNot { it.spaceSeparated }.map { it.name })
        assertFalse(Languages.isSpaceSeparated("Japanese"))
        assertTrue(Languages.isSpaceSeparated("Korean"))
    }

    @Test
    fun aLanguageTheAppDoesNotKnowCountsAsLatinAndSpaced() {
        assertTrue(Languages.isLatinScript("Klingon"))
        assertTrue(Languages.isSpaceSeparated("Klingon"))
    }

    @Test
    fun everyLanguageOfferedBeforeIsStillAvailable() {
        // Coasts and settings store these names, so removing one would orphan existing data.
        listOf("English", "Spanish", "French", "German", "Italian", "Japanese", "Korean", "Icelandic", "Norwegian")
            .forEach { assertTrue(Languages.byName(it) != null, it) }
    }

    @Test
    fun aLanguageNamesItsCodeForPrintingInItsOwnLettersAndAnUnknownOneNone() {
        // Japanese needs to say so, or its kanji are drawn in their Chinese shapes on a phone set to another language.
        assertEquals("ja", Languages.code("Japanese"))
        assertEquals("de", Languages.code("German"))
        assertEquals(null, Languages.code("Klingon"))
    }
}

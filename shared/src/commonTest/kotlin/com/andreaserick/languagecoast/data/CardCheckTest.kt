package com.andreaserick.languagecoast.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CardCheckTest {

    @Test
    fun promptShowsBothSidesAndTheRegion() {
        val prompt = buildCardCheckPrompt("I am hot.", "Ich bin heiß.", "English", "German", "Austria")

        assertTrue("native speaker of German from Austria" in prompt)
        assertTrue("English: I am hot." in prompt)
        assertTrue("German: Ich bin heiß." in prompt)
        assertTrue("VERDICT:" in prompt)
        assertTrue("Read back" !in prompt)
    }

    @Test
    fun promptCarriesTheReadingWhenThereIsOne() {
        val prompt = buildCardCheckPrompt("Sorry to bother you.", "Guten Morgen", "English", "German", null, reading = "Good morning")

        assertTrue("Read back word for word, the German says: Good morning" in prompt)
        assertTrue("as read back" in prompt)
    }

    @Test
    fun theReadingPromptDoesNotSayWhatTheCardShouldMean() {
        val prompt = buildCardReadingPrompt("Guten Morgen", "German", "English")

        assertTrue(prompt.startsWith("German text: Guten Morgen"))
        assertTrue("word for word" in prompt)
        // The instruction comes last, so what a local engine appends doesn't read as part of the text.
        assertTrue(prompt.endsWith("nothing else."))
    }

    @Test
    fun theReadingIsTheReplyWithoutItsWrapping() {
        assertEquals("Good morning", parseCardReading("Good morning", "Guten Morgen"))
        assertEquals("Excuse me, to disturb you.", parseCardReading("\"Excuse me, to disturb you.\"\n\nNote: this is literal.", "x"))
        assertEquals("Good morning", parseCardReading("English translation: [Good morning]", "Guten Morgen"))
    }

    @Test
    fun aReadingThatOnlyRepeatsTheCardIsNoReading() {
        assertNull(parseCardReading("Guten Morgen", "Guten Morgen"))
        assertNull(parseCardReading("guten morgen.", "Guten Morgen"))
        assertNull(parseCardReading("  \n", "Guten Morgen"))
    }

    @Test
    fun theVerdictLineDecides() {
        assertEquals(CardVerdict.Fine, parseCardCheck("VERDICT: FINE", "Hallo"))
        assertEquals(CardVerdict.Fine, parseCardCheck("VERDICT: [FINE]\nBETTER: NONE", "Hallo"))
        assertEquals(
            CardVerdict.Better("Entschuldigen Sie die Störung.", "The card says good morning.", currentIsWrong = true),
            parseCardCheck("VERDICT: CHANGE\nBETTER: Entschuldigen Sie die Störung.\nWHY: The card says good morning.\nCURRENT: WRONG", "Guten Morgen")
        )
        // CHANGE without saying to what is no verdict.
        assertNull(parseCardCheck("VERDICT: CHANGE", "Guten Morgen"))
    }

    @Test
    fun promptWithoutARegionAsksAboutWhereverTheLanguageIsSpoken() {
        assertTrue("the places where it is spoken" in buildCardCheckPrompt("Hello", "Hallo", "English", "German", " "))
    }

    @Test
    fun fineIsReadHoweverTheModelDressesIt() {
        assertEquals(CardVerdict.Fine, parseCardCheck("FINE", "Hallo"))
        assertEquals(CardVerdict.Fine, parseCardCheck("**Fine.**", "Hallo"))
        assertEquals(CardVerdict.Fine, parseCardCheck("FINE: nothing to change", "Hallo"))
    }

    @Test
    fun aWrongTranslationIsToldApartFromAnUnnaturalOne() {
        val wrong = parseCardCheck("BETTER: [Mir ist heiß.]\nWHY: \"Ich bin heiß\" means you are aroused.\nCURRENT: WRONG", "Ich bin heiß.")
        assertEquals(CardVerdict.Better("Mir ist heiß.", "\"Ich bin heiß\" means you are aroused.", currentIsWrong = true), wrong)

        val stiff = parseCardCheck("better: Servus!\nwhy: In Austria this is the everyday greeting.\ncurrent: OK", "Guten Tag!")
        assertEquals(CardVerdict.Better("Servus!", "In Austria this is the everyday greeting.", currentIsWrong = false), stiff)
    }

    @Test
    fun aSuggestionWithoutAVerdictOnTheCurrentOneKeepsIt() {
        val verdict = parseCardCheck("BETTER: Servus!\nWHY: More usual.", "Guten Tag!") as CardVerdict.Better

        assertEquals(false, verdict.currentIsWrong)
    }

    @Test
    fun repeatingTheCardsTranslationCountsAsFine() {
        assertEquals(CardVerdict.Fine, parseCardCheck("BETTER: guten tag\nWHY: It is fine.\nCURRENT: OK", "Guten Tag!"))
    }

    @Test
    fun chatterGivesNoVerdict() {
        assertNull(parseCardCheck("I think this card could be improved in several ways.", "Hallo"))
        assertNull(parseCardCheck("", "Hallo"))
    }
}

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

    @Test
    fun theAlternativePromptShowsTheCardAndWhatTheLearnerProposes() {
        val prompt = buildAlternativeCheckPrompt("Where is the station?", "Wo ist der Bahnhof?", "Wo befindet sich der Bahnhof?", "English", "German", "Austria")

        assertTrue("native speaker of German from Austria" in prompt)
        assertTrue("English: Where is the station?" in prompt)
        assertTrue("German: Wo ist der Bahnhof?" in prompt)
        assertTrue("correct as well: Wo befindet sich der Bahnhof?" in prompt)
        assertTrue("VERDICT:" in prompt && "FIXED:" in prompt)
        assertTrue("the places where it is spoken" in buildAlternativeCheckPrompt("Hi", "Hallo", "Servus", "English", "German", region = " "))
    }

    @Test
    fun anAlternativeThatIsCorrectComesWithHowItDiffers() {
        val formal = parseAlternativeCheck("VERDICT: YES\nWHY: \"befindet sich\" is more formal than \"ist\".\nFIXED: NONE", "Wo befindet sich der Bahnhof?")
        assertEquals(AlternativeVerdict.Correct("\"befindet sich\" is more formal than \"ist\"."), formal)

        // Interchangeable wordings have nothing worth a note.
        assertEquals(AlternativeVerdict.Correct(null), parseAlternativeCheck("**Verdict:** [Yes]\nWhy: NONE", "Servus"))
        assertEquals(AlternativeVerdict.Correct(null), parseAlternativeCheck("Yes, that works too.", "Servus"))
    }

    @Test
    fun anAlternativeThatIsWrongComesWithTheReasonAndAFixIfThereIsOne() {
        val slip = parseAlternativeCheck("VERDICT: NO\nWHY: \"Bahnhof\" is masculine.\nFIXED: \"Wo ist der Bahnhof bitte?\"", "Wo ist die Bahnhof bitte?")
        assertEquals(AlternativeVerdict.Incorrect("\"Bahnhof\" is masculine.", "Wo ist der Bahnhof bitte?"), slip)

        val other = parseAlternativeCheck("VERDICT: NO\nWHY: That asks for the airport.\nFIXED: NONE", "Wo ist der Flughafen?")
        assertEquals(AlternativeVerdict.Incorrect("That asks for the airport.", null), other)

        // A fix that only repeats the sentence is no fix.
        val same = parseAlternativeCheck("VERDICT: NO\nWHY: Nobody says that.\nFIXED: wo ist der flughafen", "Wo ist der Flughafen?")
        assertEquals(AlternativeVerdict.Incorrect("Nobody says that.", null), same)
    }

    @Test
    fun aYesThatChangesTheSentenceCountsAsANo() {
        val verdict = parseAlternativeCheck("VERDICT: YES\nWHY: Only the article was off.\nFIXED: Wo ist der Bahnhof bitte?", "Wo ist die Bahnhof bitte?")

        assertEquals(AlternativeVerdict.Incorrect("Only the article was off.", "Wo ist der Bahnhof bitte?"), verdict)
    }

    @Test
    fun anAlternativeAnswerWithoutAVerdictGivesNone() {
        assertNull(parseAlternativeCheck("Let me think about that sentence.", "Servus"))
        assertNull(parseAlternativeCheck("VERDICT: MAYBE\nWHY: Hard to say.", "Servus"))
        // "NONE" starts like "NO" but isn't one.
        assertNull(parseAlternativeCheck("NONE", "Servus"))
        assertNull(parseAlternativeCheck("", "Servus"))
    }
}

package com.andreaserick.languagecoast.data

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StudyLogicTest {

    private fun placed(id: Int, coastId: Int, island: String = "Travel", native: String = "n$id") =
        PlacedCard(Flashcard(cardId = id, islandId = 1, nativeText = native, targetText = "t$id"), island, null, coastId, "German")

    @Test
    fun leakyCardsAreThoseForgottenTwiceOrRatedHard() {
        assertTrue(isLeaky(Flashcard(islandId = 1, nativeText = "a", targetText = "b", lapses = 2)))
        assertTrue(isLeaky(Flashcard(islandId = 1, nativeText = "a", targetText = "b", difficulty = 8.0)))
        assertTrue(!isLeaky(Flashcard(islandId = 1, nativeText = "a", targetText = "b", lapses = 1, difficulty = 5.0)))
    }

    @Test
    fun coastsTakeTurnsAndKeepTheirOwnOrder() {
        val mixed = interleaveByCoast(listOf(placed(1, 1), placed(2, 1), placed(3, 2), placed(4, 1), placed(5, 3)))
        assertEquals(listOf(1, 3, 5, 2, 4), mixed.map { it.card.cardId })
    }

    @Test
    fun clozeBlanksTheLongestWordAndKeepsPunctuation() {
        val cloze = clozeOf("Wo ist der Bahnhof?")!!
        assertEquals("Bahnhof", cloze.word)
        assertEquals("Wo ist der ", cloze.before)
        assertEquals("?", cloze.after)
        assertEquals("Wo ist der _______?", cloze.display)
        assertNull(clozeOf("Bahnhof"))
    }

    @Test
    fun answersAreExactAlmostOrWrong() {
        assertEquals(AnswerMatch.Exact, checkAnswer("wo ist der bahnhof", listOf("Wo ist der Bahnhof?")))
        assertEquals(AnswerMatch.Almost("Wo ist der Bahnhof?", "Wo ist der Bahnhoff"), checkAnswer("Wo ist der Bahnhoff", listOf("Wo ist der Bahnhof?")))
        assertEquals(AnswerMatch.Wrong, checkAnswer("Wo ist das Meer", listOf("Wo ist der Bahnhof?")))
        // Short answers get no slack: "Haus" is not "Maus".
        assertEquals(AnswerMatch.Wrong, checkAnswer("Maus", listOf("Haus")))
        // Alternatives count too.
        assertEquals(AnswerMatch.Exact, checkAnswer("Zahlen, bitte", listOf("Die Rechnung, bitte.", "Zahlen, bitte.")))
    }

    @Test
    fun islandIsSuggestedFromSharedWords() {
        val cards = listOf(
            placed(1, 1, "Travel", "Where is the train station?"),
            placed(2, 1, "Travel", "One ticket to Berlin, please."),
            placed(3, 1, "Restaurant", "A table for two, please.")
        )
        assertEquals("Travel", suggestIsland("Which train goes to the station?", cards))
        assertEquals("Restaurant", suggestIsland("Could we have a table outside?", cards))
        assertNull(suggestIsland("Hello", cards))
    }

    @Test
    fun weekStartsOnMonday() {
        assertEquals(LocalDate(2026, 11, 2), startOfWeek(LocalDate(2026, 11, 5)))
        assertEquals(LocalDate(2026, 11, 2), startOfWeek(LocalDate(2026, 11, 2)))
    }

    @Test
    fun pastedTextIsSplitIntoSentencesWithoutBulletsOrRepeats() {
        val text = "Where is the beach? It is far.\n- The water is cold!\nWhere is the beach?\nOk"
        assertEquals(listOf("Where is the beach?", "It is far.", "The water is cold!"), splitSentences(text))
    }

    @Test
    fun japaneseTextIsSplitAtItsOwnSentenceMarks() {
        // No space follows the full-width marks, so a pasted paragraph would otherwise become a single card.
        val text = "すみません、駅はどこですか？ありがとうございます。いい天気ですね！"
        assertEquals(listOf("すみません、駅はどこですか？", "ありがとうございます。", "いい天気ですね！"), splitSentences(text))
        // Western marks inside a line still need the space after them, so "3.5" and "e.g." stay whole.
        assertEquals(listOf("It costs 3.50 euros."), splitSentences("It costs 3.50 euros."))
    }

    @Test
    fun oneLineOfASideWithSeveralIsEnoughToType() {
        assertEquals(listOf("Guten Morgen"), acceptedAnswers("Guten Morgen"))
        val side = "der Hund\npl. die Hunde"
        assertEquals(listOf(side, "der Hund", "pl. die Hunde"), acceptedAnswers(side))
        assertEquals(AnswerMatch.Exact, checkAnswer("der hund", acceptedAnswers(side)))
        assertEquals(AnswerMatch.Exact, checkAnswer("der Hund pl. die Hunde", acceptedAnswers(side)))
        // A list of meanings is typed without its numbers.
        assertEquals(AnswerMatch.Exact, checkAnswer("home", acceptedAnswers("1. house\n2. home\n- flat")))
        assertEquals(AnswerMatch.Wrong, checkAnswer("die Katze", acceptedAnswers(side)))
    }

    @Test
    fun listItemsAreToldFromOtherLines() {
        assertTrue(isListItem("1. house"))
        assertTrue(isListItem("2) home"))
        assertTrue(isListItem("- flat"))
        assertFalse(isListItem("der Hund"))
        assertFalse(isListItem("1.50 Euro"))
    }

    @Test
    fun aSlipInOneLineOfASideWithSeveralIsStillAlmost() {
        val side = "der Hund\npl. die Hunde\nDer Hund bellt laut."

        assertEquals(AnswerMatch.Almost("Der Hund bellt laut.", "Der Hund belt laut"), checkAnswer("Der Hund belt laut", acceptedAnswers(side)))
        assertEquals(AnswerMatch.Exact, checkAnswer("pl die hunde", acceptedAnswers(side)))
    }

    @Test
    fun emptyLinesAndWindowsLineEndingsDoNotCountAsAnswers() {
        assertEquals(listOf("der Hund\r\n\npl. die Hunde", "der Hund", "pl. die Hunde"), acceptedAnswers("der Hund\r\n\npl. die Hunde"))
        // One line with space around it is one answer, not two.
        assertEquals(listOf(" Guten Morgen\n"), acceptedAnswers(" Guten Morgen\n"))
        assertEquals(AnswerMatch.Wrong, checkAnswer(" ", acceptedAnswers("der Hund\n\npl. die Hunde")))
    }

    @Test
    fun theSameSentenceIsTheSameHoweverItIsBrokenIntoLines() {
        assertTrue(isSameSentence("der Hund pl. die Hunde", "der Hund\npl. die Hunde"))
        assertFalse(isSameSentence("der Hund", "der Hund\npl. die Hunde"))
    }

    @Test
    fun aGapIsMadeInASideWithSeveralLinesWithoutLosingThem() {
        val cloze = clozeOf("der Hund\nDer Hund bellt furchtbar.")!!

        assertEquals("furchtbar", cloze.word)
        assertEquals("der Hund\nDer Hund bellt ", cloze.before)
        assertEquals("der Hund\nDer Hund bellt furchtbar.", cloze.before + cloze.word + cloze.after)
    }
}

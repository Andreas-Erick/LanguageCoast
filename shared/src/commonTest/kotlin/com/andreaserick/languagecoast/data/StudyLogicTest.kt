package com.andreaserick.languagecoast.data

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
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
}

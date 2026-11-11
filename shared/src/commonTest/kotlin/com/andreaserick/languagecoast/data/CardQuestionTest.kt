package com.andreaserick.languagecoast.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CardQuestionTest {

    private val card = Flashcard(
        cardId = 1, islandId = 1, nativeText = "I'm helping the man.", targetText = "Ich helfe dem Mann.",
        alternatives = listOf("Ich helfe dem Herrn."), note = "Herr is more formal."
    )

    @Test
    fun thePromptHoldsTheWholeCardAndEndsWithTheInstruction() {
        val prompt = buildCardQuestionPrompt(card, "English", "German", "Austria", emptyList(), "  Why dem and not den? ")

        assertTrue("English: I'm helping the man." in prompt)
        assertTrue("German: Ich helfe dem Mann." in prompt)
        assertTrue("Ich helfe dem Herrn." in prompt)
        assertTrue("Note on the card: Herr is more formal." in prompt)
        assertTrue("German in Austria" in prompt)
        assertTrue("The learner asks: Why dem and not den?\n" in prompt)
        assertFalse("Earlier in this conversation" in prompt)
        // Whatever an engine appends must come after the instruction, not after the question.
        assertTrue(prompt.lines().last().startsWith("Answer the learner's question"))
        assertTrue("in English" in prompt.lines().last())
    }

    @Test
    fun aCardWithoutExtrasAndARegionLeavesThoseLinesOut() {
        val plain = card.copy(alternatives = emptyList(), note = null)
        val prompt = buildCardQuestionPrompt(plain, "English", "German", " ", emptyList(), "When would I say this?")

        assertFalse("Other correct translations" in prompt)
        assertFalse("Note on the card" in prompt)
        assertFalse("will use German in" in prompt)
    }

    @Test
    fun aFollowUpIsSentWithTheLatestExchangesOnly() {
        val earlier = (1..6).map { CardExchange("question $it", "answer $it") }
        val prompt = buildCardQuestionPrompt(card, "English", "German", null, earlier, "And in the plural?")

        assertTrue("Earlier in this conversation:" in prompt)
        assertFalse("question 2" in prompt)
        assertTrue("Learner: question 3\nYou: answer 3" in prompt)
        assertTrue("Learner: question 6\nYou: answer 6" in prompt)
        assertTrue(prompt.indexOf("answer 6") < prompt.indexOf("The learner asks: And in the plural?"))
    }

    @Test
    fun aCardWithSeveralLinesKeepsThePromptFlushLeft() {
        val prompt = buildCardQuestionPrompt(card.copy(targetText = "der Hund\npl. die Hunde"), "English", "German", null, emptyList(), "Why?")

        assertTrue(prompt.lines().none { it.startsWith(" ") })
    }

    @Test
    fun anAnswerIsShownWithoutTheMarkdownAModelUsedAnyway() {
        assertEquals("\"dem\" is dative.", parseCardAnswer("  **\"dem\"** is dative.\n"))
        assertEquals("Grammar\n• helfen takes the dative\n• dem is masculine", parseCardAnswer("## Grammar\n- helfen takes the dative\n* dem is masculine"))
        assertEquals("One.\n\nTwo.", parseCardAnswer("One.\n\n\n\nTwo."))
        // A dash inside a sentence is not a bullet.
        assertEquals("dem - not den", parseCardAnswer("dem - not den"))
        assertNull(parseCardAnswer(" \n```\n"))
    }

    @Test
    fun thePromptAsksInTheLearnersLanguageWhateverItIs() {
        val prompt = buildCardQuestionPrompt(card, "Icelandic", "Spanish", null, emptyList(), "Why?")

        assertTrue(prompt.startsWith("You are a friendly Spanish teacher. A learner whose native language is Icelandic"))
        assertTrue("Icelandic: I'm helping the man." in prompt)
        assertTrue("Spanish: Ich helfe dem Mann." in prompt)
        assertTrue("in Icelandic, in at most 120 words" in prompt)
    }

    @Test
    fun upToTheRememberedNumberOfExchangesAllAreSent() {
        val earlier = (1..CARD_QUESTION_MEMORY).map { CardExchange("question $it", "answer $it") }
        val prompt = buildCardQuestionPrompt(card, "English", "German", null, earlier, "And now?")

        earlier.forEach { assertTrue("Learner: ${it.question}\nYou: ${it.answer}" in prompt) }
        // The question being asked comes once, after what was said before.
        assertEquals(1, Regex("The learner asks:").findAll(prompt).count())
    }

    @Test
    fun theStartersAreQuestionsWorthAsking() {
        assertEquals(3, CARD_QUESTION_STARTERS.size)
        assertEquals(CARD_QUESTION_STARTERS, CARD_QUESTION_STARTERS.distinct())
        assertTrue(CARD_QUESTION_STARTERS.all { it.isNotBlank() && it.length <= 30 })
    }

    @Test
    fun anAnswersOwnLinesAndListsAreKept() {
        assertEquals("1. \"laufen\" is to walk\n2. \"rennen\" is to run", parseCardAnswer("1. \"laufen\" is to walk\n2. \"rennen\" is to run"))
        assertEquals("  • nested", parseCardAnswer("x\n  - nested")!!.substringAfter("\n"))
        assertEquals("plain", parseCardAnswer("```\nplain\n```"))
        assertNull(parseCardAnswer(""))
    }
}

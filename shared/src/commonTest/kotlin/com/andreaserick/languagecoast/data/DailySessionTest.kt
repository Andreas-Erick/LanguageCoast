package com.andreaserick.languagecoast.data

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DailySessionTest {

    private val today = LocalDate(2026, 10, 9)

    @Test
    fun planCapsReviewsAndNewSentences() {
        val plan = dailyPlan(dueOnCoast = 40, readySuggestions = 8, lastStudy = today, today = today)

        assertEquals(DAILY_REVIEW_CAP, plan.reviewCount)
        assertEquals(DAILY_NEW_SENTENCES, plan.newCount)
        assertEquals(40 - DAILY_REVIEW_CAP, plan.leftOver)
        assertFalse(plan.isWelcomeBack)
        assertFalse(plan.isEmpty)
    }

    @Test
    fun planTakesWhatIsThereWhenBelowTheCaps() {
        val plan = dailyPlan(dueOnCoast = 2, readySuggestions = 1, lastStudy = null, today = today)

        assertEquals(2, plan.reviewCount)
        assertEquals(1, plan.newCount)
        assertEquals(0, plan.leftOver)
        assertTrue(plan.minutes >= 1)
    }

    @Test
    fun planIsEmptyWithNothingDueAndNothingReady() {
        assertTrue(dailyPlan(dueOnCoast = 0, readySuggestions = 0, lastStudy = today, today = today).isEmpty)
    }

    @Test
    fun daysAwayMakeTheNextSessionAWelcomeBack() {
        assertFalse(isWelcomeBack(lastStudy = null, today = today))
        assertFalse(isWelcomeBack(lastStudy = LocalDate(2026, 10, 7), today = today))
        assertTrue(isWelcomeBack(lastStudy = LocalDate(2026, 10, 6), today = today))

        val plan = dailyPlan(dueOnCoast = 40, readySuggestions = 8, lastStudy = LocalDate(2026, 9, 1), today = today)
        assertTrue(plan.isWelcomeBack)
        assertEquals(WELCOME_BACK_REVIEW_CAP, plan.reviewCount)
        assertEquals(WELCOME_BACK_NEW_SENTENCES, plan.newCount)
    }

    @Test
    fun suggestionPromptCarriesGoalCategoriesAndSentencesToAvoid() {
        val prompt = buildSuggestionPrompt(
            targetLanguage = "German",
            nativeLanguage = "English",
            goal = "A trip to Berlin in May",
            count = 5,
            avoid = listOf("Where is the train station?"),
            existingCategories = listOf("Travel", "Greetings"),
            cardCount = 3
        )

        assertTrue("A trip to Berlin in May" in prompt)
        assertTrue("Travel, Greetings" in prompt)
        assertTrue("Where is the train station?" in prompt)
        assertTrue("beginner" in prompt)
        assertTrue("Suggest 5 sentences" in prompt)
    }

    @Test
    fun suggestionPromptWithoutAGoalAsksForEverydayConversation() {
        val prompt = buildSuggestionPrompt("German", "English", goal = "  ", count = 3, avoid = emptyList(), existingCategories = emptyList(), cardCount = 100)

        assertTrue("everyday conversation" in prompt)
        assertTrue("know the basics" in prompt)
    }

    @Test
    fun parsesOneSentencePerLineAndToleratesDecoration() {
        val parsed = parseSuggestionResponse(
            """
            Here you go:
            1. Where is the station? || Wo ist der Bahnhof? || Travel || ✈️
            - **[Two beers, please.]** || [Zwei Bier, bitte.] || [Restaurant] || [🍺]
            Not a sentence line
            I love this city. || Ich liebe diese Stadt. || Feelings
            where is the station || Wo ist der Bahnhof? || Travel || ✈️
            Only two parts || here
            """.trimIndent()
        )

        assertEquals(3, parsed.size)
        assertEquals(ProposedSentence("Where is the station?", "Wo ist der Bahnhof?", "Travel", "✈️"), parsed[0])
        assertEquals(ProposedSentence("Two beers, please.", "Zwei Bier, bitte.", "Restaurant", "🍺"), parsed[1])
        assertEquals("Feelings", parsed[2].category)
        assertNull(parsed[2].emoji)
    }

    @Test
    fun parsingDropsEmojiThatIsAWord() {
        val parsed = parseSuggestionResponse("Hello || Hallo || Greetings || wave")

        assertNull(parsed.single().emoji)
    }

    @Test
    fun bankPicksThemesTheGoalPointsToFirst() {
        val picked = PhraseBank.pick(goal = "Dinner with my girlfriend's parents in Rome", avoid = emptyList(), count = 6)

        assertEquals(6, picked.size)
        // Food and family lead; the first two sentences come from those themes, taking turns.
        assertEquals(setOf("Restaurant", "Small Talk"), picked.take(2).map { it.category }.toSet())
        assertTrue(picked.all { it.targetText.isEmpty() })
    }

    @Test
    fun bankSkipsSentencesTheUserAlreadyHasAndNeverRepeats() {
        val fillers = mapOf(PhraseBank.LANGUAGE_PLACEHOLDER to "German")
        val usable = PhraseBank.THEMES.filter { it.inPool }.flatMap { theme -> theme.sentences.mapNotNull { PhraseBank.fill(it, fillers) } }
        val avoid = usable.take(10)
        val picked = PhraseBank.pick(goal = null, avoid = avoid, count = 1000, fillers = fillers)

        assertTrue(picked.none { it.nativeText in avoid })
        assertEquals(picked.size, picked.distinctBy { it.nativeText }.size)
        assertEquals(usable.size - avoid.size, picked.size)
        assertTrue(picked.none { PhraseBank.PLACEHOLDER.containsMatchIn(it.nativeText) })
    }

    @Test
    fun bankHasAHundredToTwoHundredSentencesWithNoRepeats() {
        val all = PhraseBank.THEMES.flatMap { it.sentences }

        assertTrue(all.size in 400..800, "bank has ${all.size} sentences")
        assertEquals(all.size, all.distinctBy { normalizedWords(it) }.size)
        assertTrue(PhraseBank.THEMES.all { it.sentences.size >= 10 })
        // Word lists stay out of the daily session's pool.
        val pooled = PhraseBank.pick(goal = null, avoid = emptyList(), count = 1000, fillers = mapOf("language" to "German"))
        assertTrue(pooled.none { PhraseBank.theme(it.category)?.inPool == false })
        assertTrue(PhraseBank.THEMES.count { !it.inPool } >= 5)
        assertTrue(pooled.any { it.category == "Time" })
    }

    @Test
    fun placeholdersAreFilledInOrLeaveTheSentenceUnusable() {
        assertEquals(listOf("word", "language"), PhraseBank.placeholders("How do you say [word] in [language]?"))
        assertEquals("Could we speak German, please? I'm learning it.", PhraseBank.fill("Could we speak [language], please? I'm learning it.", mapOf("language" to "German")))
        assertEquals("I live in Oslo.", PhraseBank.fill("I live in [city].", mapOf("city" to " Oslo ")))
        assertNull(PhraseBank.fill("I live in [city].", mapOf("city" to " ")))
        assertNull(PhraseBank.fill("I'm from [country].", emptyMap()))
        assertEquals("Hello!", PhraseBank.fill("Hello!", emptyMap()))
    }

    @Test
    fun everyPlaceholderInTheBankIsAKnownOne() {
        val known = setOf("name", "city", "country", "language", "number", "time", "word", "job", "place", "day", "month")
        val used = PhraseBank.THEMES.flatMap { it.sentences }.flatMap { PhraseBank.placeholders(it) }.toSet()

        assertTrue(used.isNotEmpty())
        assertTrue((used - known).isEmpty(), "unknown placeholders: ${used - known}")
    }
}

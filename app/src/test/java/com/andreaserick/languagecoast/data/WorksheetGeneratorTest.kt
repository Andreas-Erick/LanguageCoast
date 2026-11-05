package com.andreaserick.languagecoast.data

import com.andreaserick.languagecoast.testing.FakeFlashcardRepository
import com.andreaserick.languagecoast.testing.FakeSettingsRepository
import com.andreaserick.languagecoast.testing.FakeTranslator
import com.andreaserick.languagecoast.testing.TEST_CLOCK
import com.andreaserick.languagecoast.testing.TEST_TIME_ZONE
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.random.Random

@OptIn(ExperimentalCoroutinesApi::class)
class WorksheetGeneratorTest {

    private val flashcards = FakeFlashcardRepository()
    private val settings = FakeSettingsRepository()
    private val translator = FakeTranslator()
    private val generator = WorksheetGenerator(translator, settings, flashcards, TEST_CLOCK, TEST_TIME_ZONE, Random(1))

    /** A German coast (used in Austria) with two islands of three cards each, and another coast that must stay out of it. */
    @Before
    fun setUp() {
        flashcards.coasts.value = listOf(Coast(coastId = 1, language = "German", region = "Austria"), Coast(coastId = 2, language = "Spanish"))
        flashcards.islands.value = listOf(
            LanguageIsland(islandId = 1, coastId = 1, name = "Travel"),
            LanguageIsland(islandId = 2, coastId = 1, name = "Food"),
            LanguageIsland(islandId = 3, coastId = 2, name = "Saludos")
        )
        flashcards.cards.value = listOf(
            Flashcard(cardId = 1, islandId = 1, nativeText = "Where is the station?", targetText = "Wo ist der Bahnhof?"),
            Flashcard(cardId = 2, islandId = 1, nativeText = "I am going to the beach.", targetText = "Ich gehe zum Strand."),
            Flashcard(cardId = 3, islandId = 1, nativeText = "The train is late.", targetText = "Der Zug ist spät.", alternatives = listOf("Der Zug hat Verspätung.")),
            Flashcard(cardId = 4, islandId = 2, nativeText = "The child is eating an apple.", targetText = "Das Kind isst einen Apfel."),
            Flashcard(cardId = 5, islandId = 2, nativeText = "I would like a coffee.", targetText = "Ich möchte einen Kaffee."),
            Flashcard(cardId = 6, islandId = 2, nativeText = "The bread is good.", targetText = "Das Brot ist gut."),
            Flashcard(cardId = 7, islandId = 3, nativeText = "Good morning", targetText = "Buenos días")
        )
        settings.apiKey.value = "key"
    }

    private fun lines(vararg pairs: Pair<String, String>) = Completion(pairs.joinToString("\n") { "${it.first} || ${it.second}" })

    @Test
    fun theSheetIsWrittenFromThatCoastsCardsOnly() = runTest {
        translator.completion = lines(
            "Wo ist das Kind?" to "Where is the child?",
            "Das Kind geht zum Bahnhof." to "The child is going to the station.",
            "Der Kaffee ist gut." to "The coffee is good."
        )

        val result = generator.generate(1, WorksheetOptions(count = 3, difficulty = WorksheetDifficulty.Easy, direction = WorksheetDirection.ToNative))

        val sheet = (result as WorksheetResult.Ready).worksheet
        assertEquals("German Coast", sheet.coastName)
        assertEquals("German" to "English", sheet.targetLanguage to sheet.nativeLanguage)
        assertEquals(3, sheet.sentences.size)
        assertEquals(LocalDate(2026, 10, 9), sheet.date)
        assertEquals("Gemini", sheet.providerLabel)
        assertEquals(6 to 2, sheet.cardCount to sheet.islandCount)
        assertEquals(WorksheetDirection.ToNative, sheet.options.direction)

        // One request was enough. It shows the coast's cards under their islands, and nothing of the Spanish coast.
        val prompt = translator.prompts.single()
        assertTrue("[Travel]" in prompt && "[Food]" in prompt)
        assertTrue("Der Zug ist spät. = The train is late." in prompt)
        assertFalse("Buenos días" in prompt)
        assertTrue("Level: easy" in prompt)
        assertTrue("a native speaker from Austria" in prompt)
        // It asks for a few more than the sheet needs, to have a choice.
        assertTrue("Write 5 new German sentences" in prompt)
    }

    @Test
    fun ofTooManySentencesTheOnesClosestToTheCardsAreKept() = runTest {
        translator.completion = lines(
            "Morgen regnet es in Hamburg." to "It will rain in Hamburg tomorrow.",
            "Wo ist das Kind?" to "Where is the child?",
            "Wo ist der Bahnhof?" to "Where is the station?",
            "Das Kind geht zum Bahnhof." to "The child is going to the station.",
            "Der Kaffee ist gut." to "The coffee is good.",
            "Der Zug hat Verspätung" to "The train is delayed."
        )

        val sheet = (generator.generate(1, WorksheetOptions(count = 3)) as WorksheetResult.Ready).worksheet

        // Left out: the sentence with words the coast doesn't have, and the copies of a card and of a card's alternative.
        assertEquals(listOf("Wo ist das Kind?", "Das Kind geht zum Bahnhof.", "Der Kaffee ist gut."), sheet.sentences.map { it.targetText })
    }

    @Test
    fun aModelThatSendsTooFewIsAskedForTheRest() = runTest {
        val answers = ArrayDeque(
            listOf(
                lines("Wo ist das Kind?" to "Where is the child?", "Der Kaffee ist gut." to "The coffee is good."),
                lines("wo ist das Kind" to "Where's the child?", "Das Brot ist spät." to "The bread is late."),
                lines("Ich gehe zum Bahnhof." to "I am going to the station.")
            )
        )
        translator.completionFor = { answers.removeFirstOrNull() ?: Completion("Nothing more.") }
        val progress = mutableListOf<Int>()

        val sheet = (generator.generate(1, WorksheetOptions(count = 4)) { progress += it } as WorksheetResult.Ready).worksheet

        // The repeat of a sentence already written doesn't count.
        assertEquals(listOf("Wo ist das Kind?", "Der Kaffee ist gut.", "Das Brot ist spät.", "Ich gehe zum Bahnhof."), sheet.sentences.map { it.targetText })
        assertEquals(listOf(2, 3, 4), progress)
        assertEquals(3, translator.prompts.size)
        assertTrue("Do not repeat any of these, which are already on the sheet: Wo ist das Kind? | Der Kaffee ist gut." in translator.prompts[1])
        assertTrue("Write 4 new German sentences" in translator.prompts[1])
    }

    @Test
    fun aLongSheetIsWrittenABatchAtATime() = runTest {
        var asked = 0
        translator.completionFor = { prompt ->
            asked++
            val batch = Regex("Write (\\d+) new").find(prompt)!!.groupValues[1].toInt()
            Completion((1..batch).joinToString("\n") { "Kind Satz $asked $it || Sentence $asked $it" })
        }
        val progress = mutableListOf<Int>()

        val sheet = (generator.generate(1, WorksheetOptions(count = 500)) { progress += it } as WorksheetResult.Ready).worksheet

        assertEquals(500, sheet.sentences.size)
        assertEquals(500, sheet.sentences.distinctBy { it.targetText }.size)
        // 510 sentences, ten of them to spare, 25 per request.
        assertEquals(21, translator.prompts.size)
        assertEquals(worksheetRequests(500, isLocal = false), translator.prompts.size)
        assertTrue("Write 25 new German sentences" in translator.prompts.first())
        assertTrue("Write 10 new German sentences" in translator.prompts.last())
        assertEquals(listOf(25, 50, 75), progress.take(3))
        assertEquals(500, progress.last())
        // A request names the latest sentences on the sheet, not all of them.
        val avoided = translator.prompts.last().lines().single { it.startsWith("8. ") }
        assertEquals(60, avoided.split(" | ").size)
        assertTrue(avoided.endsWith("Kind Satz 20 25"))
    }

    @Test
    fun aCoastThatRunsOutOfSentencesGivesAShorterSheetInsteadOfAskingForever() = runTest {
        var asked = 0
        translator.completionFor = {
            asked++
            // Two answers with new sentences, then only repeats.
            val answer = minOf(asked, 2)
            Completion((1..25).joinToString("\n") { "Kind Satz $answer $it || Sentence $answer $it" })
        }

        val sheet = (generator.generate(1, WorksheetOptions(count = 500)) as WorksheetResult.Ready).worksheet

        assertEquals(50, sheet.sentences.size)
        assertEquals(4, translator.prompts.size)
    }

    @Test
    fun aSheetWithFewerSentencesThanAskedForIsStillASheet() = runTest {
        translator.completion = lines("Wo ist das Kind?" to "Where is the child?")

        val sheet = (generator.generate(1, WorksheetOptions(count = 10)) as WorksheetResult.Ready).worksheet

        assertEquals(1, sheet.sentences.size)
        // It gave up after asking again twice.
        assertEquals(3, translator.prompts.size)
    }

    @Test
    fun aLocalModelIsAskedForAFewSentencesAtATime() = runTest {
        settings.translationProvider.value = TranslationProvider.LocalAi
        var asked = 0
        translator.completionFor = {
            asked++
            Completion((1..5).joinToString("\n") { "Satz $asked $it Kind || Sentence $asked $it" })
        }

        val sheet = (generator.generate(1, WorksheetOptions(count = 10)) as WorksheetResult.Ready).worksheet

        assertEquals(10, sheet.sentences.size)
        assertEquals("Local AI", sheet.providerLabel)
        assertEquals(2, translator.prompts.size)
        assertTrue(translator.prompts.all { "Write 5 new German sentences" in it })
    }

    @Test
    fun withoutEnoughCardsOrAModelThereIsNoSheet() = runTest {
        fun message(result: WorksheetResult) = (result as WorksheetResult.Failed).message

        // The Spanish coast has one card.
        assertTrue("at least 5 cards" in message(generator.generate(2, WorksheetOptions())))
        assertTrue("gone" in message(generator.generate(99, WorksheetOptions())))

        settings.translationProvider.value = TranslationProvider.OnDevice
        assertTrue("can't write sentences" in message(generator.generate(1, WorksheetOptions())))

        settings.translationProvider.value = TranslationProvider.Gemini
        settings.apiKey.value = ""
        assertTrue("API key" in message(generator.generate(1, WorksheetOptions())))
        assertTrue(translator.prompts.isEmpty())
    }

    @Test
    fun aProviderThatFailsOrRamblesIsReported() = runTest {
        translator.completion = Completion.failure("No internet connection.")
        assertEquals(WorksheetResult.Failed("No internet connection."), generator.generate(1, WorksheetOptions()))
        // An unreachable provider isn't asked again.
        assertEquals(1, translator.prompts.size)

        translator.completion = Completion("Sure! Here are some thoughts about German.")
        assertEquals(WorksheetResult.Failed("Gemini's answer couldn't be read. Try again."), generator.generate(1, WorksheetOptions()))
    }

    @Test
    fun aLargeCoastIsShownAsASampleThatChangesWithEachRequest() = runTest {
        flashcards.cards.value = (1..450).map { Flashcard(cardId = it, islandId = 1, nativeText = "native $it", targetText = "ziel$it") }
        val answers = ArrayDeque(listOf(lines("ziel1 ziel2" to "one two"), lines("ziel3 ziel4" to "three four")))
        translator.completionFor = { answers.removeFirstOrNull() ?: Completion("") }

        generator.generate(1, WorksheetOptions(count = 3))

        val shown = translator.prompts.take(2).map { prompt -> prompt.lines().filter { it.startsWith("ziel") && " = native " in it } }
        assertEquals(listOf(400, 400), shown.map { it.size })
        assertTrue(shown[0] != shown[1])
        // The sample keeps the coast's order.
        assertEquals(shown[0].sortedBy { it.substringAfter("native ").toInt() }, shown[0])
    }

    @Test
    fun aLargeCoastIsShownAFewWholeIslandsAtATime() = runTest {
        // 50 islands of 10 cards: 500 cards, of which a prompt shows 400.
        flashcards.islands.value = (1..50).map { LanguageIsland(islandId = it, coastId = 1, name = "Insel $it") }
        flashcards.cards.value = (1..500).map { Flashcard(cardId = it, islandId = (it - 1) / 10 + 1, nativeText = "native $it", targetText = "ziel$it") }
        val answers = ArrayDeque(listOf(lines("ziel1 ziel2" to "one two"), lines("ziel3 ziel4" to "three four")))
        translator.completionFor = { answers.removeFirstOrNull() ?: Completion("") }

        generator.generate(1, WorksheetOptions(count = 3))

        fun islands(prompt: String) = prompt.lines().filter { it.startsWith("[Insel ") }
        val first = translator.prompts[0]
        // Forty islands with all ten of their cards each, not eight cards of every island.
        assertEquals(40, islands(first).size)
        assertEquals(400, first.lines().count { it.startsWith("ziel") && " = native " in it })
        // The islands take turns: the ten that were left out come first in the next prompt.
        assertEquals(50, (islands(first) + islands(translator.prompts[1])).distinct().size)
    }

    /** The lines of [prompt] that are cards of the island called [island]. */
    private fun cardsOf(prompt: String, island: String): List<String> =
        prompt.substringAfter("[$island]\n").substringBefore("\n\n").lines().filter { " = native " in it }

    @Test
    fun aHugeIslandLeavesRoomForTheOthers() = runTest {
        // An imported deck of 3000 cards next to two islands of 100.
        flashcards.islands.value = listOf("Deck", "Reisen", "Essen").mapIndexed { index, name -> LanguageIsland(islandId = index + 1, coastId = 1, name = name) }
        flashcards.cards.value = (1..3200).map {
            Flashcard(cardId = it, islandId = when { it <= 3000 -> 1; it <= 3100 -> 2; else -> 3 }, nativeText = "native $it", targetText = "ziel$it")
        }
        translator.completionFor = { Completion("") }

        generator.generate(1, WorksheetOptions(count = 3))

        assertEquals(2, translator.prompts.size)
        translator.prompts.forEach { prompt ->
            assertEquals(400, prompt.lines().count { it.startsWith("ziel") && " = native " in it })
            // Every island has enough cards in the prompt to build sentences from, whichever came first.
            assertTrue(cardsOf(prompt, "Deck").size >= 40)
            assertTrue(cardsOf(prompt, "Reisen").size >= 40)
            assertTrue(cardsOf(prompt, "Essen").size >= 40)
        }
    }

    @Test
    fun aCoastOfThousandsOfCardsGivesALongSheetThatGetsAroundAllItsIslands() = runTest {
        // 6000 cards on 120 islands, and the longest sheet there is.
        flashcards.islands.value = (1..120).map { LanguageIsland(islandId = it, coastId = 1, name = "Insel $it") }
        flashcards.cards.value = (1..6000).map {
            Flashcard(cardId = it, islandId = (it - 1) / 50 + 1, nativeText = "native sentence number $it", targetText = "ziel$it satz", alternatives = listOf("anders$it"))
        }
        var asked = 0
        translator.completionFor = { prompt ->
            asked++
            val batch = Regex("Write (\\d+) new").find(prompt)!!.groupValues[1].toInt()
            Completion((1..batch).joinToString("\n") { "ziel$it satz $asked || Sentence $asked $it" })
        }

        val sheet = (generator.generate(1, WorksheetOptions(count = 500)) as WorksheetResult.Ready).worksheet

        assertEquals(500, sheet.sentences.size)
        assertEquals(6000 to 120, sheet.cardCount to sheet.islandCount)
        assertEquals(21, translator.prompts.size)
        // No prompt grows with the coast: 400 cards of ten islands, whatever the coast holds.
        translator.prompts.forEach { prompt ->
            assertEquals(400, prompt.lines().count { it.startsWith("ziel") && " = native " in it })
            assertEquals(10, prompt.lines().count { it.startsWith("[Insel ") })
            assertTrue(prompt.length < 30_000)
        }
        val shown = translator.prompts.flatMap { prompt -> prompt.lines().filter { it.startsWith("[Insel ") } }.distinct()
        assertEquals(120, shown.size)
    }

    @Test
    fun cardsWithLongTextsDontMakeAHugePrompt() = runTest {
        // An imported deck whose cards are paragraphs: 200 cards of about 1000 characters.
        val paragraph = "wort ".repeat(200).trim()
        flashcards.cards.value = (1..200).map { Flashcard(cardId = it, islandId = 1, nativeText = "native $it", targetText = "$paragraph ziel$it") }
        translator.completion = lines("wort ziel1" to "word one")

        generator.generate(1, WorksheetOptions(count = 3))

        val prompt = translator.prompts.first()
        val shown = prompt.lines().count { " = native " in it }
        // About 40 000 characters of cards, not all 200 000.
        assertTrue(shown in 30..40)
        assertTrue(prompt.length < 45_000)
    }

    @Test
    fun aRateLimitedProviderIsWaitedForInsteadOfCuttingTheSheetShort() = runTest {
        val answers = ArrayDeque(
            listOf(
                lines("Wo ist das Kind?" to "Where is the child?", "Der Kaffee ist gut." to "The coffee is good."),
                Completion.busy("Claude is busy or rate-limited. Wait a moment and try again.", retryAfterSeconds = 20),
                Completion.busy("Claude is busy or rate-limited. Wait a moment and try again.", retryAfterSeconds = 300),
                lines("Das Brot ist spät." to "The bread is late.", "Ich gehe zum Bahnhof." to "I am going to the station.")
            )
        )
        translator.completionFor = { answers.removeFirst() }

        val sheet = (generator.generate(1, WorksheetOptions(count = 4)) as WorksheetResult.Ready).worksheet

        assertEquals(4, sheet.sentences.size)
        // The request that was turned away was sent again as it was, after the wait the provider named (a minute at most).
        assertEquals(4, translator.prompts.size)
        assertEquals(translator.prompts[1], translator.prompts[2])
        assertEquals(translator.prompts[1], translator.prompts[3])
        assertEquals(80_000L, testScheduler.currentTime)
    }

    @Test
    fun aProviderThatStaysBusyEndsTheSheetWithWhatThereIs() = runTest {
        val busy = Completion.busy("Claude is busy or rate-limited. Wait a moment and try again.", retryAfterSeconds = 10)

        // Busy from the start: no sheet, and the reason, after four waits.
        translator.completion = busy
        assertEquals(WorksheetResult.Failed("Claude is busy or rate-limited. Wait a moment and try again."), generator.generate(1, WorksheetOptions()))
        assertEquals(5, translator.prompts.size)
        assertEquals(40_000L, testScheduler.currentTime)

        // Busy after the first answer: a shorter sheet.
        translator.prompts.clear()
        var asked = 0
        translator.completionFor = { if (asked++ == 0) lines("Wo ist das Kind?" to "Where is the child?") else busy }
        val sheet = (generator.generate(1, WorksheetOptions(count = 10)) as WorksheetResult.Ready).worksheet
        assertEquals(1, sheet.sentences.size)
    }
}

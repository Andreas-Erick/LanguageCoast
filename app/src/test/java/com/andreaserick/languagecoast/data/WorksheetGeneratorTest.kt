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

    /** The cards [prompt] asks for a sentence from, one per numbered line, each as "Island: card". */
    private fun anchors(prompt: String): List<String> =
        prompt.substringAfter("numbered line below").substringAfter("\n\n").substringBefore("\n\nWhat matters").lines().map { it.substringAfter(". ") }

    /** Every card of the German coast as a numbered line names it: "Travel: Wo ist der Bahnhof?". */
    private fun coastCards(): Set<String> {
        val names = flashcards.islands.value.filter { it.coastId == 1 }.associate { it.islandId to it.name }
        return flashcards.cards.value.filter { it.islandId in names }.mapTo(HashSet()) { "${names.getValue(it.islandId)}: ${it.targetText}" }
    }

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
        assertTrue("3. It is short and simple: 3 to 7 words in one clause. " in prompt)
        assertTrue(prompt.startsWith("You are a native German speaker from Austria"))
        // It asks for a few more than the sheet needs, to have a choice: a sentence from each of five cards.
        assertEquals(5, anchors(prompt).size)
        // The sentences are read in the coast's language here, so no hint is asked for; the words that are on no card are.
        assertFalse("hint" in prompt)
        assertTrue(
            prompt.endsWith(
                "[number] || [German sentence] || [English translation] || " +
                    "[the German words that are not on the flashcards, each with its English meaning, as \"word = meaning; word = meaning\", or - if there are none]"
            )
        )
    }

    @Test
    fun everySentenceIsBuiltFromOneOfTheCoastsCards() = runTest {
        translator.completion = Completion("")

        generator.generate(1, WorksheetOptions(count = 3))

        // Five sentences, each from another card, of both islands and of this coast only.
        val anchors = anchors(translator.prompts.first())
        assertEquals(5, anchors.size)
        assertEquals(5, anchors.distinct().size)
        assertTrue(anchors.toString(), anchors.all { it in coastCards() })
        assertEquals(setOf("Food", "Travel"), anchors.mapTo(HashSet()) { it.substringBefore(": ") })
        assertTrue("Write one new German sentence for each numbered line below" in translator.prompts.first())
    }

    @Test
    fun sentencesComeBackTiedToTheirCardsAndIslands() = runTest {
        // The model answers the numbered lines last to first: the number, not the place, says which card a sentence is from.
        translator.completionFor = { prompt ->
            val answers = anchors(prompt).mapIndexed { index, anchor ->
                val card = anchor.substringAfter(": ")
                val translation = prompt.lines().first { it.startsWith("$card = ") }.substringAfter(" = ")
                "${index + 1} || Heute: $card || Today: $translation"
            }
            Completion(answers.reversed().joinToString("\n"))
        }

        val sheet = (generator.generate(1, WorksheetOptions(count = 3)) as WorksheetResult.Ready).worksheet

        assertEquals(3, sheet.sentences.size)
        sheet.sentences.forEach { sentence ->
            val card = checkNotNull(sentence.card) { "${sentence.targetText} came back without its card" }
            assertEquals("Heute: ${card.targetText}", sentence.targetText)
            // The card is the learner's own, with both its sides, and the sentence practises its island.
            val own = flashcards.cards.value.single { it.targetText == card.targetText }
            assertEquals(own.nativeText, card.nativeText)
            assertEquals(flashcards.islands.value.single { it.islandId == own.islandId }.name, sentence.island)
            assertEquals("Your card: ${own.targetText} = ${own.nativeText}", sheet.cardLine(sentence))
        }
        // The first page can say what the sheet practises.
        assertTrue(sheet.focusLine.orEmpty(), sheet.focusLine.orEmpty().startsWith("Built from your cards on "))
    }

    @Test
    fun wordsOfASentenceThatAreOnNoCardBecomeItsWordHelp() = runTest {
        translator.completion = Completion(
            """
            1 || Der Bahnhof liegt am Strand. || The station is by the beach. || liegen = to lie; der Bahnhof = the station; morgen = tomorrow
            2 || Kommen Sie zum Bahnhof? || Are you coming to the station? (formal) || kommen = to come
            3 || Das Brot ist spät. || The bread is late. || -
            """.trimIndent()
        )

        val sheet = (generator.generate(1, WorksheetOptions(count = 3)) as WorksheetResult.Ready).worksheet

        val (first, second, third) = sheet.sentences
        // Left out: a word the coast has, and a word that isn't in the sentence.
        assertEquals(listOf(WordHelp("liegen", "to lie")), first.newWords)
        assertEquals("liegen = to lie", sheet.wordHelp(first))
        assertEquals(listOf(WordHelp("kommen", "to come")), second.newWords)
        assertEquals(emptyList<WordHelp>(), third.newWords)
        // The hint is kept apart from the translation, and shown with it where the sentence is the one to translate.
        assertEquals("Are you coming to the station?" to "formal", second.nativeText to second.hint)
        assertEquals("Are you coming to the station? (formal)", sheet.question(second))
        assertTrue(sheet.sentences.all { it.card != null })
    }

    @Test
    fun ofAnIslandTheCardsThatNeedPracticeAreBuiltFromBeforeFreshOnes() = runTest {
        // One card of each island was studied long ago and forgotten more than once; nobody has studied the others.
        flashcards.cards.value = flashcards.cards.value.map { if (it.cardId == 2 || it.cardId == 5) studied(it, daysAgo = 100, lapses = 4) else it }
        translator.completion = Completion("")

        generator.generate(1, WorksheetOptions(count = 3))

        // The islands take turns, and each starts with its card that is slipping.
        val anchors = anchors(translator.prompts.first())
        assertEquals(listOf("Food: Ich möchte einen Kaffee.", "Travel: Ich gehe zum Strand."), anchors.take(2))
        assertEquals(5, anchors.distinct().size)
    }

    @Test
    fun noCardIsBuiltFromTwiceWhileItsIslandHasOthers() = runTest {
        // Thirty cards on each island and a sheet of forty: two requests of 25 sentences.
        flashcards.cards.value = (1..60).map { Flashcard(cardId = it, islandId = if (it <= 30) 1 else 2, nativeText = "native $it", targetText = "ziel$it") }
        var asked = 0
        translator.completionFor = { prompt ->
            asked++
            Completion(anchors(prompt).indices.joinToString("\n") { "${it + 1} || Kind Satz $asked ${it + 1} || Sentence $asked ${it + 1}" })
        }

        val sheet = (generator.generate(1, WorksheetOptions(count = 40)) as WorksheetResult.Ready).worksheet

        assertEquals(2, translator.prompts.size)
        val anchors = translator.prompts.flatMap { anchors(it) }
        assertEquals(50, anchors.size)
        assertEquals(50, anchors.distinct().size)
        // So every sentence of the sheet is from another card.
        assertEquals(40, sheet.sentences.mapNotNull { it.card }.distinct().size)
    }

    @Test
    fun theRequestSaysHowTheLearnerSpeaksOfThemselvesAndTheLevelTheyHaveReached() = runTest {
        translator.completion = Completion("")

        // Nothing said under About you, and none of the coast's islands is on the route: the request says neither.
        generator.generate(1, WorksheetOptions(count = 3))
        val plain = translator.prompts.first()
        assertFalse("The learner is a" in plain)
        assertTrue("Their native language is English.\n" in plain)
        assertTrue(" Its words and grammar suit the learner, whose level the flashcards show.\n" in plain)

        // A woman, on a coast that has a starter topic of the route on it. One card of the route among the
        // coast's own says nothing yet about where she stands.
        translator.prompts.clear()
        settings.wording.value = Wording.Feminine
        flashcards.islands.value += LanguageIsland(islandId = 4, coastId = 1, name = "Greetings")
        flashcards.cards.value += Flashcard(cardId = 8, islandId = 4, nativeText = "Good morning", targetText = "Guten Morgen")
        settings.markStarterTopicAdded(1, "Greetings")

        generator.generate(1, WorksheetOptions(count = 3))

        val few = translator.prompts.first()
        assertTrue("- The learner is a woman: wherever German marks the speaker's gender" in few)
        assertFalse("level is about" in few)

        // Once half of the sheet's cards are the route's, the request says the level she has reached on it.
        translator.prompts.clear()
        val ownCards = flashcards.cards.value.count { it.islandId != 4 && it.islandId in flashcards.islands.value.filter { island -> island.coastId == 1 }.map { island -> island.islandId } }
        flashcards.cards.value += (1 until ownCards).map { Flashcard(cardId = 100 + it, islandId = 4, nativeText = "Good morning $it", targetText = "Guten Morgen $it") }

        generator.generate(1, WorksheetOptions(count = 3))

        val prompt = translator.prompts.first()
        assertTrue("Their native language is English and their level is about A1.\n" in prompt)
        assertTrue(" Its words and grammar suit level A1.\n" in prompt)

        // A sheet from the islands that are not the route's says no level, whatever else is on the coast.
        translator.prompts.clear()
        val ownIslands = flashcards.islands.value.filter { it.coastId == 1 && it.islandId != 4 }.mapTo(HashSet()) { it.islandId }
        generator.generate(1, WorksheetOptions(count = 3, islandIds = ownIslands))
        assertFalse("level is about" in translator.prompts.first())
    }

    @Test
    fun aSheetCanBeMadeFromThePickedIslandsOnly() = runTest {
        flashcards.cards.value += listOf(
            Flashcard(cardId = 8, islandId = 2, nativeText = "The soup is hot.", targetText = "Die Suppe ist heiß."),
            Flashcard(cardId = 9, islandId = 2, nativeText = "We eat at eight.", targetText = "Wir essen um acht.")
        )
        translator.completion = lines("Der Kaffee ist gut." to "The coffee is good.", "Das Kind isst Brot." to "The child is eating bread.", "Die Suppe ist gut." to "The soup is good.")

        val result = generator.generate(1, WorksheetOptions(count = 3, islandIds = setOf(2)))

        // Only the Food island's cards are shown to the model and counted on the sheet.
        val sheet = (result as WorksheetResult.Ready).worksheet
        assertEquals(5 to 1, sheet.cardCount to sheet.islandCount)
        val prompt = translator.prompts.single()
        assertTrue("[Food]" in prompt)
        assertFalse("[Travel]" in prompt)
        assertFalse("Bahnhof" in prompt)
        assertTrue(WorksheetOptions().includes(1))
        assertFalse(WorksheetOptions(islandIds = setOf(2)).includes(1))
    }

    @Test
    fun pickedIslandsWithTooFewCardsSayToPickMore() = runTest {
        val result = generator.generate(1, WorksheetOptions(count = 3, islandIds = setOf(1)))

        assertEquals("A practice sheet needs at least 5 cards to make new sentences from. Pick more islands.", (result as WorksheetResult.Failed).message)
        assertTrue(translator.prompts.isEmpty())
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
        assertTrue("- Do not repeat any of these, which are already in the exercise: Wo ist das Kind? | Der Kaffee ist gut." in translator.prompts[1])
        assertFalse("Do not repeat any of these" in translator.prompts[0])
        // Six were wanted and two came: the next request is for four.
        assertEquals(listOf(6, 4), translator.prompts.take(2).map { anchors(it).size })
    }

    @Test
    fun aLongSheetIsWrittenABatchAtATime() = runTest {
        var asked = 0
        translator.completionFor = { prompt ->
            asked++
            Completion((1..anchors(prompt).size).joinToString("\n") { "$it || Kind Satz $asked $it || Sentence $asked $it || -" })
        }
        val progress = mutableListOf<Int>()

        val sheet = (generator.generate(1, WorksheetOptions(count = 500)) { progress += it } as WorksheetResult.Ready).worksheet

        assertEquals(500, sheet.sentences.size)
        assertEquals(500, sheet.sentences.distinctBy { it.targetText }.size)
        // 510 sentences, ten of them to spare, 25 per request.
        assertEquals(21, translator.prompts.size)
        assertEquals(worksheetRequests(500, WorksheetBatch.Cloud), translator.prompts.size)
        assertEquals(25, anchors(translator.prompts.first()).size)
        assertEquals(10, anchors(translator.prompts.last()).size)
        // Six cards and 510 sentences: the cards come round again and again, and every sentence still has one.
        assertTrue(sheet.sentences.all { it.card != null })
        assertEquals(listOf(25, 50, 75), progress.take(3))
        assertEquals(500, progress.last())
        // A request names the latest sentences on the sheet, not all of them.
        val avoided = translator.prompts.last().lines().single { it.startsWith("- Do not repeat any of these") }
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
    fun aModelThatThinksFirstIsAskedForADozenSentencesAtATime() = runTest {
        settings.translationProvider.value = TranslationProvider.Claude
        settings.claudeKey.value = "sk-ant-key"
        var asked = 0
        translator.completionFor = { prompt ->
            asked++
            Completion((1..anchors(prompt).size).joinToString("\n") { "$it || Kind Satz $asked $it || Sentence $asked $it || -" })
        }

        // The usual sheet, ten sentences and two to spare, is one request.
        generator.generate(1, WorksheetOptions(count = 10))
        assertEquals(listOf(12), translator.prompts.map { anchors(it).size })

        // Twenty are 25 with the spares. Not 25 in one request: over that many hard sentences a top model
        // ran out of time and room, and the sheet failed. And not 12, 12 and one request for the last one:
        // the last few go with the request before them.
        translator.prompts.clear()
        val sheet = (generator.generate(1, WorksheetOptions(count = 20)) as WorksheetResult.Ready).worksheet
        assertEquals(20, sheet.sentences.size)
        assertEquals("Claude", sheet.providerLabel)
        assertEquals(listOf(12, 13), translator.prompts.map { anchors(it).size })
        assertEquals(2, worksheetRequests(20, WorksheetBatch.Thinking))

        translator.prompts.clear()
        generator.generate(1, WorksheetOptions(count = 30))
        assertEquals(listOf(12, 12, 13), translator.prompts.map { anchors(it).size })
        assertEquals(3, worksheetRequests(30, WorksheetBatch.Thinking))
    }

    @Test
    fun aRequestThatIsTooMuchForTheModelIsMadeAgainWithHalfAsManySentences() = runTest {
        val tooSlow = Completion.tooLarge("Gemini took too long to answer. Try again or pick a faster model.")
        // A model that can't manage more than six sentences in the time a request has.
        var answered = 0
        translator.completionFor = { prompt ->
            val size = anchors(prompt).size
            if (size > 6) tooSlow
            else {
                answered++
                Completion((1..size).joinToString("\n") { "$it || Kind Satz $answered $it || Sentence $answered $it || -" })
            }
        }
        val progress = mutableListOf<Int>()

        val sheet = (generator.generate(1, WorksheetOptions(count = 20)) { progress += it } as WorksheetResult.Ready).worksheet

        // 25 at once, then 12, then six at a time until the sheet is written: the sheet is whole, where it used
        // to end with "took too long" after the first request. The one sentence still to spare gets no request of its own.
        assertEquals(20, sheet.sentences.size)
        assertEquals(listOf(25, 12, 6, 6, 6, 6), translator.prompts.map { anchors(it).size })
        assertEquals(listOf(6, 12, 18, 20), progress)

        // The usual sheet of ten asks for twelve: half of that is six, not twelve again.
        translator.prompts.clear()
        assertEquals(10, (generator.generate(1, WorksheetOptions(count = 10)) as WorksheetResult.Ready).worksheet.sentences.size)
        assertEquals(listOf(12, 6, 6), translator.prompts.map { anchors(it).size })

        // A model that manages nothing at all is given up on once three at a time were too much as well,
        // and the sheet says what the model said.
        translator.prompts.clear()
        translator.completionFor = { tooSlow }
        val failed = generator.generate(1, WorksheetOptions(count = 20)) as WorksheetResult.Failed
        assertEquals(tooSlow.errorMessage, failed.message)
        assertEquals(listOf(25, 12, 6, 3), translator.prompts.map { anchors(it).size })

        // Any other failure of the first request ends the sheet at once, as before: asking for less wouldn't help.
        translator.prompts.clear()
        translator.completionFor = { Completion.failure("Couldn't reach Gemini. Check your internet connection and try again.") }
        assertTrue(generator.generate(1, WorksheetOptions(count = 20)) is WorksheetResult.Failed)
        assertEquals(1, translator.prompts.size)
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
        assertTrue(translator.prompts.all { anchors(it).size == 5 })
        // A small model keeps to three fields better than to four: it isn't asked for the words that are on no card.
        assertTrue(translator.prompts.all { it.endsWith("[number] || [German sentence] || [English translation]") })
        assertFalse(translator.prompts.any { "word = meaning" in it || "list them after the translation" in it })
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
            Completion((1..anchors(prompt).size).joinToString("\n") { "$it || ziel$it satz $asked || Sentence $asked $it" })
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
        // And so does the sheet: it has sentences built from the cards of every island.
        assertEquals(120, sheet.sentences.mapNotNull { it.island }.distinct().size)
    }

    @Test
    fun aCoastOfTensOfThousandsOfCardsGivesTheSameSheetAsASmallOne() = runTest {
        // 60 000 cards: an imported deck of 40 000 on one island and 20 000 on 400 more, next to a coast as large that must stay out of it.
        flashcards.islands.value = (1..401).map { LanguageIsland(islandId = it, coastId = 1, name = if (it == 1) "Deck" else "Insel $it") } +
            LanguageIsland(islandId = 402, coastId = 2, name = "Mazo")
        flashcards.cards.value = (1..60_000).map {
            Flashcard(
                cardId = it, islandId = if (it <= 40_000) 1 else (it - 40_001) / 50 + 2,
                nativeText = "native sentence number $it", targetText = "ziel$it satz", alternatives = if (it % 10 == 0) listOf("anders$it") else emptyList()
            )
        } + (1..60_000).map { Flashcard(cardId = 60_000 + it, islandId = 402, nativeText = "nativo $it", targetText = "meta$it frase") }
        var asked = 0
        translator.completionFor = { prompt ->
            asked++
            // The first line of every answer copies a card, which the sheet can do without.
            Completion((1..anchors(prompt).size).joinToString("\n") { if (it == 1) "1 || Ziel$asked satz! || Copy $asked" else "$it || ziel$it satz $asked || Sentence $asked $it" })
        }

        val sheet = (generator.generate(1, WorksheetOptions(count = 500)) as WorksheetResult.Ready).worksheet

        assertEquals(500, sheet.sentences.size)
        assertEquals(60_000 to 401, sheet.cardCount to sheet.islandCount)
        assertEquals(21, translator.prompts.size)
        // Ten spare sentences, so ten of the copies could be left out, and were.
        assertEquals(11, sheet.sentences.count { it.nativeText.startsWith("Copy") })
        translator.prompts.forEach { prompt ->
            assertEquals(400, prompt.lines().count { it.startsWith("ziel") && " = native " in it })
            assertFalse("meta" in prompt)
            assertTrue(prompt.length < 30_000)
        }
        // The deck doesn't crowd the small islands out, and they all get their turn.
        assertTrue(translator.prompts.all { prompt -> prompt.lines().count { it.startsWith("[Insel ") } >= 9 })
        val shown = translator.prompts.flatMap { prompt -> prompt.lines().filter { it.startsWith("[Insel ") } }.distinct()
        assertTrue(shown.size > 150)

        // A local model's prompt stays as short as ever.
        translator.prompts.clear()
        settings.translationProvider.value = TranslationProvider.LocalAi
        assertEquals(500, (generator.generate(1, WorksheetOptions(count = 500)) as WorksheetResult.Ready).worksheet.sentences.size)
        assertEquals(100, translator.prompts.size)
        assertTrue(translator.prompts.maxOf { it.length }.toString(), translator.prompts.all { it.length < 8_000 })
    }

    /** [card] as it is after studying it [daysAgo] days ago, with a memory FSRS expects to last two days. */
    private fun studied(card: Flashcard, daysAgo: Int, lapses: Int = 0) = card.copy(
        stability = 2.0, difficulty = 5.0, lastReviewed = TEST_CLOCK.now().toEpochMilliseconds() - daysAgo * 86_400_000L, lapses = lapses
    )

    /** How many sentences [prompt] asks for from each island's cards, in the order it first names the islands. */
    private fun quotas(prompt: String): List<Pair<String, Int>> = anchors(prompt).groupingBy { it.substringBefore(": ") }.eachCount().toList()

    @Test
    fun aCoastNobodyHasStudiedSharesTheSheetByWhatIsOnItsIslands() = runTest {
        translator.completion = lines("Wo ist das Kind?" to "Where is the child?")

        generator.generate(1, WorksheetOptions(count = 10))

        // Twelve sentences, two islands of three cards.
        assertEquals(listOf("Food" to 6, "Travel" to 6), quotas(translator.prompts.first()))
        // The islands take turns down the list, so the sheet is mixed.
        val anchors = anchors(translator.prompts.first())
        assertEquals(List(6) { listOf("Food", "Travel") }.flatten(), anchors.map { it.substringBefore(": ") })
        // An island of three cards with six sentences to give: its cards come round a second time, each of them.
        assertEquals(coastCards().associateWith { 2 }, anchors.groupingBy { it }.eachCount())
    }

    @Test
    fun theIslandWhoseCardsAreSlippingGetsMostOfTheSheet() = runTest {
        // Food was studied long ago and forgotten more than once; Travel was studied today.
        flashcards.cards.value = flashcards.cards.value.map { card ->
            when (card.islandId) {
                1 -> studied(card, daysAgo = 0)
                2 -> studied(card, daysAgo = 100, lapses = 4)
                else -> card
            }
        }
        translator.completion = lines("Wo ist das Kind?" to "Where is the child?")

        generator.generate(1, WorksheetOptions(count = 10))

        val (food, travel) = quotas(translator.prompts.first()).let { it.toMap().getValue("Food") to it.toMap().getValue("Travel") }
        assertEquals(12, food + travel)
        assertTrue("Food $food, Travel $travel", food in 8..10 && travel in 2..4)
        // The coast fits a prompt, so Travel's cards are still there, for their words.
        assertTrue("Wo ist der Bahnhof? = Where is the station?" in translator.prompts.first())
    }

    @Test
    fun ofAHugeIslandThePromptShowsTheCardsThatWereStudied() = runTest {
        // An imported deck of 3000 cards, 30 of them studied and overdue, next to two islands of 100 nobody has studied.
        flashcards.islands.value = listOf("Deck", "Reisen", "Essen").mapIndexed { index, name -> LanguageIsland(islandId = index + 1, coastId = 1, name = name) }
        flashcards.cards.value = (1..3200).map {
            val card = Flashcard(cardId = it, islandId = when { it <= 3000 -> 1; it <= 3100 -> 2; else -> 3 }, nativeText = "native $it", targetText = "ziel$it")
            if (it % 100 == 0 && it <= 3000) studied(card, daysAgo = 50) else card
        }
        translator.completionFor = { Completion("") }

        generator.generate(1, WorksheetOptions(count = 10))

        translator.prompts.forEach { prompt ->
            val asked = quotas(prompt).toMap()
            assertEquals(12, asked.values.sum())
            // The deck has the cards that need practice, so it gets more than the other two together.
            assertTrue(asked.toString(), asked.getValue("Deck") > asked.getOrDefault("Reisen", 0) + asked.getOrDefault("Essen", 0))
            val deck = cardsOf(prompt, "Deck")
            assertTrue((100..3000 step 100).all { "ziel$it = native $it" in deck })
            // And the deck's sentences are built from those thirty, not from the 2970 cards nobody has studied.
            val built = anchors(prompt).filter { it.startsWith("Deck: ") }.map { it.removePrefix("Deck: ziel").toInt() }
            assertTrue(built.toString(), built.all { it % 100 == 0 })
        }
        // A second request doesn't go back to the cards the first one took.
        val deckCards = translator.prompts.flatMap { prompt -> anchors(prompt).filter { it.startsWith("Deck: ") } }
        assertEquals(deckCards.size, deckCards.distinct().size)
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
        assertTrue("$shown", shown in 30..40)
        assertTrue("${prompt.length} characters, ${anchors(prompt).sumOf { it.length }} of them in the numbered lines", prompt.length < 45_000)
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

    @Test
    fun spareSelectionDropsOffAnchorAndUnglossedLinesAndKeepsInflectedWordHelp() = runTest {
        translator.completionFor = { prompt ->
            val dealt = anchors(prompt)
            // 5 anchors dealt for count = 3: line 1 ignores its anchor completely, line 2 leaves its new word unglossed,
            // and lines 3..5 honour their anchors (including an umlauted verb with dictionary-form word help).
            val replies = dealt.mapIndexed { idx, anchor ->
                val cardText = anchor.substringAfter(": ")
                val nativeSide = prompt.lines().first { it.startsWith("$cardText = ") }.substringAfter(" = ")
                when (idx) {
                    0 -> "1 || Morgen scheint die Sonne hell. || Tomorrow the sun shines brightly. || morgen = tomorrow; Sonne = sun"
                    1 -> "2 || Leider: $cardText || Unfortunately: $nativeSide || -"
                    2 -> "3 || Fährt das Kind? $cardText || Does the child go? $nativeSide || fahren = to go"
                    else -> "${idx + 1} || Heute: $cardText || Today: $nativeSide || heute = today"
                }
            }
            Completion(replies.joinToString("\n"))
        }

        val sheet = (generator.generate(1, WorksheetOptions(count = 3, difficulty = WorksheetDifficulty.Medium)) as WorksheetResult.Ready).worksheet

        assertEquals(3, sheet.sentences.size)
        assertFalse(sheet.sentences.any { "Sonne" in it.targetText })
        assertFalse(sheet.sentences.any { "Leider" in it.targetText })
        // Dictionary-form "fahren" was kept for surface "Fährt" because accent-folded stem matching linked them.
        assertEquals(listOf(WordHelp("fahren", "to go")), sheet.sentences.first().newWords)
    }

    @Test
    fun consecutiveSheetsRotateAcrossStudiedCardsAndAvoidPreviousSheetSentences() = runTest {
        flashcards.islands.value = listOf(LanguageIsland(islandId = 1, coastId = 1, name = "Travel"))
        flashcards.cards.value = (1..10).map { idx ->
            studied(
                Flashcard(cardId = idx, islandId = 1, nativeText = "native $idx", targetText = "Station$idx ist nah."),
                daysAgo = 30,
                lapses = 2
            )
        }
        translator.completionFor = { prompt ->
            val dealt = anchors(prompt)
            Completion(
                dealt.mapIndexed { idx, anchor ->
                    val cardText = anchor.substringAfter(": ")
                    "${idx + 1} || Heute $cardText || Today $cardText || heute = today"
                }.joinToString("\n")
            )
        }

        val firstSheet = (generator.generate(1, WorksheetOptions(count = 3)) as WorksheetResult.Ready).worksheet
        val firstCards = firstSheet.sentences.mapNotNull { it.card?.targetText }.toSet()
        assertEquals(3, firstCards.size)

        translator.prompts.clear()
        val secondSheet = (generator.generate(1, WorksheetOptions(count = 3)) as WorksheetResult.Ready).worksheet
        val secondCards = secondSheet.sentences.mapNotNull { it.card?.targetText }.toSet()

        // Second sheet rotates to studied cards that were not anchored on the first sheet, and tells the prompt to avoid Sheet #1's sentences.
        assertEquals(3, secondCards.size)
        assertTrue("Expected fresh studied anchors, got $firstCards vs $secondCards", (firstCards intersect secondCards).isEmpty())
        val secondPrompt = translator.prompts.first()
        firstSheet.sentences.forEach { sentence ->
            assertTrue(sentence.targetText in secondPrompt)
        }
    }

    @Test
    fun goalCardNotesAndEstimatedCustomDeckLevelReachThePrompt() = runTest {
        flashcards.setCoastGoal(1, goal = "Working in a Vienna hospital", region = "Austria")
        flashcards.islands.value = listOf(LanguageIsland(islandId = 1, coastId = 1, name = "Imported Deck"))
        flashcards.cards.value = (1..60).map { idx ->
            Flashcard(
                cardId = idx,
                islandId = 1,
                nativeText = "When does the train arrive $idx?",
                targetText = "Wann kommt der Zug heute an $idx?",
                stability = 10.0,
                difficulty = 5.0,
                lastReviewed = TEST_CLOCK.now().toEpochMilliseconds() - 5 * 86_400_000L,
                note = if (idx <= 40) "Polite Sie form" else null
            )
        }
        translator.completion = Completion("")

        generator.generate(1, WorksheetOptions(count = 20))

        val prompt = translator.prompts.first()
        assertTrue("Their native language is English and their level is about A2. Why they are learning German: \"Working in a Vienna hospital\"." in prompt)
        assertTrue("Its words and grammar suit level A2." in prompt)
        assertTrue("(Note: Polite Sie form)" in prompt)
    }

    @Test
    fun largeHardSheetUsesSmallerBatchesStreamsLiveProgressAndCompletesAllSentences() = runTest {
        flashcards.islands.value = listOf(
            LanguageIsland(islandId = 1, coastId = 1, name = "Travel"),
            LanguageIsland(islandId = 2, coastId = 1, name = "Food")
        )
        flashcards.cards.value = (1..40).map { idx ->
            Flashcard(
                cardId = idx,
                islandId = if (idx <= 20) 1 else 2,
                nativeText = "When does train $idx leave?",
                targetText = "Wann fährt der Zug Nummer $idx ab?"
            )
        }
        translator.streamLines = true

        // Both Claude and Gemini finish all 35 hard sentences even when each batch drops a couple of lines,
        // and stream sentence-by-sentence progress inside every batch.
        for (provider in listOf(TranslationProvider.Claude, TranslationProvider.Gemini)) {
            // Seed 60 historical sentences before each provider's run: a new large sheet must cap history in the
            // prompt so old runs don't flood or crowd out current-sheet sentences.
            settings.recordWorksheet(
                coastId = 1,
                anchoredCards = (1..10).map { "Travel\tWann fährt der Zug Nummer $it ab?" },
                targetSentences = (1..60).map { "Alter Satz Nummer $it für gestern." }
            )
            settings.translationProvider.value = provider
            settings.claudeKey.value = "sk-ant-key"
            settings.apiKey.value = "gem-key"
            translator.prompts.clear()
            var call = 0
            translator.completionFor = { prompt ->
                call++
                val dealt = anchors(prompt)
                // Simulate realistic hard-sheet attrition: model returns ~75% usable lines per batch.
                val usable = maxOf(3, dealt.size * 3 / 4)
                Completion(
                    (1..usable).joinToString("\n") { i ->
                        val cardText = dealt[i - 1].substringAfter(": ")
                        "$i || Weil es spät ist ($call-$i), $cardText || Because it is late ($call-$i), $cardText || weil = because; spät = late"
                    }
                )
            }
            val progress = mutableListOf<Int>()

            val result = generator.generate(1, WorksheetOptions(count = 35, difficulty = WorksheetDifficulty.Hard)) {
                progress += it
            }

            val sheet = (result as WorksheetResult.Ready).worksheet
            assertEquals("Provider $provider", 35, sheet.sentences.size)
            val expectedMaxBatch = WorksheetBatch.of(provider, WorksheetDifficulty.Hard).sentences
            assertTrue(
                "Provider $provider asked too many at once: ${translator.prompts.map { anchors(it).size }}",
                translator.prompts.all { anchors(it).size <= expectedMaxBatch + expectedMaxBatch / 4 }
            )
            // Streaming progress ticked up one sentence at a time rather than jumping only between batches.
            assertEquals((1..35).toList(), progress.distinct())
            // First prompt only carried a bounded tail of past-sheet history, not all 60 old sentences.
            assertFalse("Alter Satz Nummer 1 für gestern." in translator.prompts.first())
            assertTrue("Alter Satz Nummer 60 für gestern." in translator.prompts.first())
        }
    }

    @Test
    fun failedBatchRestoresDealtPriorityAnchorsForHalvedRetry() = runTest {
        flashcards.cards.value = flashcards.cards.value.map {
            if (it.cardId == 2 || it.cardId == 5) studied(it, daysAgo = 90, lapses = 4) else it
        }
        var call = 0
        translator.completionFor = { prompt ->
            call++
            val dealt = anchors(prompt)
            if (call == 1) Completion.tooLarge("Too slow")
            else Completion(dealt.mapIndexed { idx, a -> "${idx + 1} || Heute: ${a.substringAfter(": ")} ($call-$idx) || Today ($call-$idx) || -" }.joinToString("\n"))
        }

        val sheet = (generator.generate(1, WorksheetOptions(count = 4, difficulty = WorksheetDifficulty.Hard)) as WorksheetResult.Ready).worksheet

        assertEquals(4, sheet.sentences.size)
        // The overdue studied cards dealt in the failed first attempt were restored and still led the halved retry.
        assertEquals(listOf("Food: Ich möchte einen Kaffee.", "Travel: Ich gehe zum Strand."), anchors(translator.prompts[1]).take(2))
    }
}

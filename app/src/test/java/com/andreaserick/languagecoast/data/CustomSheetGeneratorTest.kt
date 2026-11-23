package com.andreaserick.languagecoast.data

import com.andreaserick.languagecoast.testing.FakeFlashcardRepository
import com.andreaserick.languagecoast.testing.FakeSettingsRepository
import com.andreaserick.languagecoast.testing.FakeTranslator
import com.andreaserick.languagecoast.testing.TEST_CLOCK
import com.andreaserick.languagecoast.testing.TEST_TIME_ZONE
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.random.Random

/** Custom sheets through [WorksheetGenerator]: the same coast as in [WorksheetGeneratorTest], a brief in place of the translation task. */
@OptIn(ExperimentalCoroutinesApi::class)
class CustomSheetGeneratorTest {

    private val flashcards = FakeFlashcardRepository()
    private val settings = FakeSettingsRepository()
    private val translator = FakeTranslator()
    private val generator = WorksheetGenerator(translator, settings, flashcards, TEST_CLOCK, TEST_TIME_ZONE, Random(1))

    private val plural = WorksheetOptions(count = 3, kind = SheetKind.Custom, brief = "Plural from the singular")

    @Before
    fun setUp() {
        flashcards.coasts.value = listOf(Coast(coastId = 1, language = "German", region = "Austria"), Coast(coastId = 2, language = "Spanish"))
        flashcards.islands.value = listOf(
            LanguageIsland(islandId = 1, coastId = 1, name = "Travel"),
            LanguageIsland(islandId = 2, coastId = 1, name = "Food"),
            LanguageIsland(islandId = 3, coastId = 2, name = "Saludos")
        )
        flashcards.cards.value = listOf(
            Flashcard(cardId = 1, islandId = 1, nativeText = "the station", targetText = "der Bahnhof"),
            Flashcard(cardId = 2, islandId = 1, nativeText = "the beach", targetText = "der Strand"),
            Flashcard(cardId = 3, islandId = 1, nativeText = "the train", targetText = "der Zug"),
            Flashcard(cardId = 4, islandId = 2, nativeText = "the apple", targetText = "der Apfel"),
            Flashcard(cardId = 5, islandId = 2, nativeText = "the coffee", targetText = "der Kaffee"),
            Flashcard(cardId = 6, islandId = 2, nativeText = "the bread", targetText = "das Brot"),
            Flashcard(cardId = 7, islandId = 3, nativeText = "Good morning", targetText = "Buenos días")
        )
        settings.apiKey.value = "key"
    }

    /** A reply of short-answer exercises, one line each, all with the same instruction. */
    private fun shortAnswers(vararg pairs: Pair<String, String>) =
        Completion(pairs.joinToString("\n") { "short || Write the plural. || ${it.first} || ${it.second} || -" })

    /** How many exercises [prompt] asks for. */
    private fun asked(prompt: String): Int = Regex("Write (\\d+) exercises?").find(prompt)!!.groupValues[1].toInt()

    @Test
    fun aCustomSheetIsWrittenFromTheBriefWithTheCoastsCardsAsItsWords() = runTest {
        settings.wording.value = Wording.Feminine
        translator.completion = shortAnswers("der Bahnhof" to "die Bahnhöfe", "der Zug" to "die Züge", "der Apfel" to "die Äpfel", "das Brot" to "die Brote")

        val sheet = (generator.generate(1, plural) as WorksheetResult.Ready).worksheet

        assertTrue(sheet.isCustom)
        assertEquals(listOf("der Bahnhof", "der Zug", "der Apfel"), sheet.exercises.map { it.task })
        assertEquals("die Bahnhöfe", sheet.exercises.first().answer)
        assertTrue(sheet.sentences.isEmpty())
        assertEquals(3, sheet.exerciseCount)
        assertEquals(6 to 2, sheet.cardCount to sheet.islandCount)
        assertEquals("german-coast-custom-practice-2026-10-09.pdf", sheet.fileName)

        // One request, for a few to spare, with the brief, the coast's cards as words, the region and the wording.
        val prompt = translator.prompts.single()
        assertEquals(5, asked(prompt))
        assertTrue("\"Plural from the singular\"" in prompt)
        assertTrue("der Zug = the train" in prompt)
        assertFalse("Buenos días" in prompt)
        assertTrue(prompt.startsWith("You are a native German speaker from Austria"))
        assertTrue(speakerRule(Wording.Feminine, "German") in prompt)
        assertTrue(prompt.endsWith("short || [instruction in English] || [task in German] || [answer in German] || " +
            "[the German words of the task that are not on the flashcards, each with its English meaning, as \"word = meaning; word = meaning\", or - if there are none]"))
        // A custom sheet's tasks stay out of the translation sheets' history: they aren't sentences to avoid there.
        assertTrue(settings.storedWorksheetHistory.value.isEmpty())
    }

    @Test
    fun aModelThatSendsTooFewExercisesIsAskedForTheRestAndRepeatsDontCount() = runTest {
        val answers = ArrayDeque(
            listOf(
                shortAnswers("der Zug" to "die Züge", "der Apfel" to "die Äpfel"),
                shortAnswers("Der Zug" to "die Züge", "das Brot" to "die Brote"),
                shortAnswers("der Strand" to "die Strände")
            )
        )
        translator.completionFor = { answers.removeFirstOrNull() ?: Completion("Nothing more.") }
        val progress = mutableListOf<Int>()

        val sheet = (generator.generate(1, plural.copy(count = 4)) { progress += it } as WorksheetResult.Ready).worksheet

        assertEquals(listOf("der Zug", "der Apfel", "das Brot", "der Strand"), sheet.exercises.map { it.task })
        assertEquals(listOf(2, 3, 4), progress)
        assertEquals(listOf(6, 4, 3), translator.prompts.map(::asked))
        assertTrue("- Do not repeat any of these, which are already in the exercise: der Zug | der Apfel" in translator.prompts[1])
        assertFalse("Do not repeat any of these" in translator.prompts[0])
    }

    @Test
    fun aRequestThatIsTooMuchIsMadeAgainWithHalfAsManyAndABusyProviderIsWaitedFor() = runTest {
        val tooSlow = Completion.tooLarge("Gemini took too long to answer. Try again or pick a faster model.")
        var answered = 0
        var busy = true
        translator.completionFor = { prompt ->
            val size = asked(prompt)
            when {
                size > 6 -> tooSlow
                busy -> { busy = false; Completion.busy("Claude is busy or rate-limited. Wait a moment and try again.", retryAfterSeconds = 20) }
                else -> {
                    answered++
                    Completion((1..size).joinToString("\n") { "short || Write the plural. || Wort $answered $it || Wörter $answered $it" })
                }
            }
        }

        val sheet = (generator.generate(1, plural.copy(count = 10)) as WorksheetResult.Ready).worksheet

        assertEquals(10, sheet.exercises.size)
        assertEquals(listOf(12, 6, 6, 6), translator.prompts.map(::asked))
        assertEquals(20_000L, testScheduler.currentTime)
    }

    @Test
    fun aReplyWithNoUsableExerciseFailsWithAMessageThatSaysWhatToTry() = runTest {
        // Every exercise shows its answer, or isn't an exercise at all.
        translator.completion = Completion(
            "Sure! Here is some grammar.\nshort || Write the plural. || die Züge (der Zug) || die Züge\nthe plural || der Zug || die Züge"
        )
        assertEquals(WorksheetResult.Failed(CUSTOM_SHEET_UNUSABLE), generator.generate(1, plural))
        assertEquals("Couldn't make exercises from that brief. Try saying which grammar to practise.", CUSTOM_SHEET_UNUSABLE)

        // A provider that can't be reached says so, and isn't asked again.
        translator.prompts.clear()
        translator.completion = Completion.failure("No internet connection.")
        assertEquals(WorksheetResult.Failed("No internet connection."), generator.generate(1, plural))
        assertEquals(1, translator.prompts.size)
    }

    @Test
    fun aCustomSheetNeedsCardsToTakeItsWordsFrom() = runTest {
        val failed = generator.generate(2, plural) as WorksheetResult.Failed
        assertTrue(failed.message, "custom sheet" in failed.message && "at least 5 cards" in failed.message)
        assertTrue(translator.prompts.isEmpty())
    }

    @Test
    fun aLocalModelIsAskedForAFewExercisesAtATimeWithoutWordHelp() = runTest {
        settings.translationProvider.value = TranslationProvider.LocalAi
        var round = 0
        translator.completionFor = {
            round++
            Completion((1..5).joinToString("\n") { "short || Write the plural. || Wort $round $it || Wörter $round $it" })
        }

        val sheet = (generator.generate(1, plural.copy(count = 10)) as WorksheetResult.Ready).worksheet

        assertEquals(10, sheet.exercises.size)
        assertEquals(listOf(5, 5), translator.prompts.map(::asked))
        assertTrue(translator.prompts.all { it.endsWith("short || [instruction in English] || [task in German] || [answer in German]") })
    }
}

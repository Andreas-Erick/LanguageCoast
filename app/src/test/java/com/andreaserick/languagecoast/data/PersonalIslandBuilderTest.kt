package com.andreaserick.languagecoast.data

import androidx.lifecycle.SavedStateHandle
import com.andreaserick.languagecoast.testing.FakeFlashcardRepository
import com.andreaserick.languagecoast.testing.FakeSettingsRepository
import com.andreaserick.languagecoast.testing.FakeSpeaker
import com.andreaserick.languagecoast.testing.FakeTranslator
import com.andreaserick.languagecoast.testing.FakeWorksheetPrinter
import com.andreaserick.languagecoast.testing.MainDispatcherRule
import com.andreaserick.languagecoast.testing.TEST_CLOCK
import com.andreaserick.languagecoast.testing.TEST_TIME_ZONE
import com.andreaserick.languagecoast.ui.coast.CoastViewModel
import com.andreaserick.languagecoast.ui.coast.PersonalIslandState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PersonalIslandBuilderTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val flashcards = FakeFlashcardRepository()
    private val settings = FakeSettingsRepository().apply { apiKey.value = "key" }
    private val translator = FakeTranslator()
    private val starterTranslator = StarterTranslator(translator)
    private val builder = PersonalIslandBuilder(flashcards, settings, translator, starterTranslator, CoroutineScope(Job()))

    /** Answers the writing prompt with [lines] and the natural-speech check with NONE. */
    private fun modelWrites(vararg lines: String) {
        translator.completionFor = { prompt ->
            if ("language island" in prompt) Completion(lines.mapIndexed { i, line -> "${i + 1} || $line" }.joinToString("\n")) else Completion("NONE")
        }
    }

    private fun coastViewModel(coastId: Int) = CoastViewModel(
        SavedStateHandle(mapOf("coastId" to coastId, "coastName" to "")), flashcards, settings, TEST_CLOCK, TEST_TIME_ZONE,
        StarterImporter(flashcards, settings, starterTranslator, CoroutineScope(Job())), builder,
        WorksheetGenerator(translator, settings, flashcards, TEST_CLOCK, TEST_TIME_ZONE, background = UnconfinedTestDispatcher()), FakeWorksheetPrinter(),
        translator, FakeSpeaker()
    )

    private fun orders(vararg islandIds: String) = islandIds.map { IslandOrder(it) }

    @Test
    fun anIslandIsWrittenFromTheAnswersAndRemembered() = runTest {
        flashcards.coasts.value = listOf(Coast(coastId = 1, language = "Spanish", region = "Mexico", goal = "Moving to Oaxaca"))
        settings.profile.value = Profile(name = "Anna", job = "nurse")
        settings.setPersonalAnswer("work_place", "at a hospital")
        modelWrites("I work at a hospital. || Trabajo en un hospital.", "I am a nurse. || Soy enfermera.")

        builder.run(1, orders("work", "pets")) // Nothing is known about pets, so that island is left out.

        val island = flashcards.islands.value.single()
        assertEquals("My Work or Studies", island.name)
        assertEquals("💼", island.emoji)
        assertEquals(listOf("I work at a hospital.", "I am a nurse."), flashcards.cards.value.map { it.nativeText })
        assertEquals("Trabajo en un hospital.", flashcards.cards.value.first().targetText)
        val work = PersonalIslands.byId("work")!!
        assertEquals(mapOf("1:work" to work.fingerprint(settings.profile.value, settings.personalAnswers.value)), settings.personalIslandsBuilt.value)
        val progress = builder.progress.value!!
        assertTrue(progress.isFinished)
        assertEquals(1, progress.total)
        assertEquals(2, progress.added)
        assertNull(progress.error)
        // One prompt to write, one to read it as a native speaker would.
        assertEquals(2, translator.prompts.size)
        assertTrue("- Your job: nurse" in translator.prompts[0])
        assertTrue("- Where do you work or study?: at a hospital" in translator.prompts[0])
        assertTrue("Spanish as spoken in Mexico" in translator.prompts[0])
        assertFalse("Anna" in translator.prompts[0]) // The name is no fact for this island, so it isn't sent.
        assertTrue("native speaker of Spanish from Mexico" in translator.prompts[1])
    }

    @Test
    fun theNaturalCheckRewritesWhatANativeWouldSayDifferently() = runTest {
        flashcards.seedCoast(coastId = 1, language = "Spanish")
        settings.setPersonalAnswer("pets_which", "a cat called Milo")
        translator.completionFor = { prompt ->
            if ("language island" in prompt) Completion("1 || I have a cat. || Yo tengo un gato.") else Completion("1 || Tengo un gato. || The pronoun is dropped in speech.")
        }

        builder.run(1, orders("pets"))

        val card = flashcards.cards.value.single()
        assertEquals("Tengo un gato.", card.targetText)
        assertEquals(listOf("Yo tengo un gato."), card.alternatives)
        assertEquals("The pronoun is dropped in speech.", card.note)
    }

    @Test
    fun writingAnIslandAgainReplacesOnlyTheCardsNeverStudied() = runTest {
        flashcards.seedCoast(coastId = 1, language = "German")
        settings.setPersonalAnswer("pets_which", "a cat called Milo")
        modelWrites("I have a cat. || Ich habe eine Katze.", "His name is Milo. || Er heißt Milo.")
        builder.run(1, orders("pets"))
        val studied = flashcards.cards.value.first().copy(lastReviewed = 1L)
        flashcards.saveReview(studied)

        settings.setPersonalAnswer("pets_which", "a dog called Rex")
        modelWrites("I have a dog. || Ich habe einen Hund.", "I have a cat. || Ich habe eine Katze.")
        builder.run(1, orders("pets"))

        // The studied card stays, the unstudied one about Milo goes, and the sentence already there isn't added twice.
        assertEquals(listOf("I have a cat.", "I have a dog."), flashcards.cards.value.map { it.nativeText })
        assertEquals(1, flashcards.islands.value.size)
        assertTrue("I have a cat." in translator.prompts.last { "language island" in it }.substringAfter("already have"))
    }

    @Test
    fun anIslandTheUserMadeByHandIsOnlyAddedTo() = runTest {
        flashcards.seedCoast(coastId = 1, language = "German")
        flashcards.addFlashcard(1, "My dog is old.", "Mein Hund ist alt.", "Pets")
        settings.setPersonalAnswer("pets_which", "a dog called Rex")
        modelWrites("I have a dog. || Ich habe einen Hund.")

        builder.run(1, orders("pets"))

        assertEquals(listOf("My dog is old.", "I have a dog."), flashcards.cards.value.map { it.nativeText })
    }

    @Test
    fun aModelOnThePhoneIsAskedInRoundsUntilItHasNoMore() = runTest {
        flashcards.seedCoast(coastId = 1, language = "German")
        settings.translationProvider.value = TranslationProvider.LocalAi
        settings.setPersonalAnswer("pets_which", "a cat called Milo")
        var round = 0
        translator.completionFor = { prompt ->
            if ("language island" !in prompt) Completion("NONE")
            else {
                round++
                val lines = if (round == 1) PersonalIslandBuilder.LOCAL_SENTENCES_PER_PROMPT else 2
                Completion((1..lines).joinToString("\n") { "$it || Sentence $round $it. || Satz $round $it." })
            }
        }

        builder.run(1, orders("pets"))

        assertEquals(2, round)
        assertEquals(PersonalIslandBuilder.LOCAL_SENTENCES_PER_PROMPT + 2, flashcards.cards.value.size)
        assertTrue("Write up to ${PersonalIslandBuilder.LOCAL_SENTENCES_PER_PROMPT} sentences" in translator.prompts.first())
    }

    @Test
    fun aSentenceTheModelDidNotTranslateIsLeftOut() = runTest {
        flashcards.seedCoast(coastId = 1, language = "German")
        settings.setPersonalAnswer("pets_which", "a cat called Milo")
        modelWrites("I have a cat. || I have a cat.", "His name is Milo. || Er heißt Milo.")

        builder.run(1, orders("pets"))

        assertEquals(listOf("His name is Milo."), flashcards.cards.value.map { it.nativeText })
    }

    @Test
    fun withoutAnAiModelNothingIsWrittenAndTheReasonIsGiven() = runTest {
        flashcards.seedCoast(coastId = 1, language = "German")
        settings.translationProvider.value = TranslationProvider.OnDevice
        settings.setPersonalAnswer("pets_which", "a cat called Milo")

        builder.run(1, orders("pets"))

        assertEquals(ON_DEVICE_CANNOT_ANSWER, builder.progress.value?.error)
        assertTrue(flashcards.cards.value.isEmpty())
        assertTrue(translator.prompts.isEmpty())
    }

    @Test
    fun aProviderThatFailsStopsTheRunAndKeepsTheOldCards() = runTest {
        flashcards.seedCoast(coastId = 1, language = "German")
        settings.setPersonalAnswer("pets_which", "a cat called Milo")
        modelWrites("I have a cat. || Ich habe eine Katze.")
        builder.run(1, orders("pets"))
        settings.setPersonalAnswer("pets_which", "a dog called Rex")
        translator.completionFor = { Completion.failure("The key was refused.") }

        builder.run(1, orders("pets"))

        assertEquals(listOf("I have a cat."), flashcards.cards.value.map { it.nativeText })
        assertTrue(builder.progress.value!!.error!!.startsWith("The key was refused."))
    }

    @Test
    fun aRateLimitIsWaitedOutInsteadOfEndingTheRun() = runTest {
        flashcards.seedCoast(coastId = 1, language = "German")
        settings.setPersonalAnswer("pets_which", "a cat called Milo")
        var asked = 0
        translator.completionFor = { prompt ->
            asked++
            when {
                // The provider's per-minute limit is used up when the first request arrives.
                asked == 1 -> Completion.busy("Gemini rate limit or quota reached. Wait a moment or switch to another provider.", retryAfterSeconds = 15)
                "language island" in prompt -> Completion("1 || I have a cat. || Ich habe eine Katze.")
                else -> Completion("NONE")
            }
        }

        builder.run(1, orders("pets"))

        assertEquals(listOf("I have a cat."), flashcards.cards.value.map { it.nativeText })
        assertNull(builder.progress.value!!.error)
        // The refused request went out again unchanged after the wait.
        assertEquals(translator.prompts[0], translator.prompts[1])
    }

    @Test
    fun aKeyWithoutPermissionWritesNothingAndSaysSo() = runTest {
        flashcards.seedCoast(coastId = 1, language = "German")
        settings.setPersonalAnswer("pets_which", "a cat called Milo")
        settings.aiSharingAllowed.value = emptySet()

        builder.run(1, orders("pets"))

        // The answers about the user stay on the phone until the user has allowed sending text to the provider.
        assertEquals("Allow Language Coast to send text to Gemini first. You can do that under Settings.", builder.progress.value?.error)
        assertTrue(translator.prompts.isEmpty())
        assertTrue(flashcards.cards.value.isEmpty())
    }

    @Test
    fun whatWasWrittenAndWhatWasKeptCanBeLookedOverAndRemoved() = runTest {
        flashcards.seedCoast(coastId = 1, language = "German")
        val viewModel = coastViewModel(coastId = 1)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        settings.setPersonalAnswer("pets_which", "a cat called Milo")
        modelWrites("I have a cat. || Ich habe eine Katze.", "His name is Milo. || Er heißt Milo.")
        builder.run(1, orders("pets"))

        assertEquals(listOf("I have a cat.", "His name is Milo."), viewModel.uiState.value.buildReview.added.map { it.nativeText })
        assertTrue(viewModel.uiState.value.buildReview.kept.isEmpty())

        // The second sentence isn't how the user would say it: unticked, it is removed.
        viewModel.removeCards(setOf(viewModel.uiState.value.buildReview.added.last().cardId))
        assertEquals(listOf("I have a cat."), flashcards.cards.value.map { it.nativeText })
        assertEquals(1, viewModel.uiState.value.buildReview.added.size)

        // After a rewrite, the studied card that stayed is shown too: the answer behind it changed.
        flashcards.saveReview(flashcards.cards.value.single().copy(lastReviewed = 1L, due = 1L))
        settings.setPersonalAnswer("pets_which", "a dog called Rex")
        modelWrites("I have a dog. || Ich habe einen Hund.")
        builder.run(1, orders("pets"))

        assertEquals(listOf("I have a dog."), viewModel.uiState.value.buildReview.added.map { it.nativeText })
        assertEquals(listOf("I have a cat."), viewModel.uiState.value.buildReview.kept.map { it.nativeText })
    }

    @Test
    fun anIslandIsTakenUpALevelFromTheSameAnswersWithoutReplacingAnything() = runTest {
        flashcards.seedCoast(coastId = 1, language = "German")
        settings.wording.value = Wording.Feminine
        settings.setPersonalAnswer("pets_which", "a cat called Milo")
        modelWrites("I have a cat. || Ich habe eine Katze.")
        builder.run(1, orders("pets"))
        modelWrites("I got Milo because my flat felt empty. || Ich habe Milo geholt, weil meine Wohnung leer war.")

        builder.run(1, listOf(IslandOrder("pets", Level.B1)))

        assertEquals(2, flashcards.cards.value.size)
        assertEquals(1, flashcards.islands.value.size)
        assertEquals(setOf("1:pets", "1:pets@B1"), settings.personalIslandsBuilt.value.keys)
        val prompt = translator.prompts.last { "language island" in it }
        assertTrue("Level B1" in prompt)
        assertTrue("say more about the same facts" in prompt)
        assertTrue("I have a cat." in prompt.substringAfter("already have"))
        assertTrue("The learner is a woman" in prompt)
        assertTrue(builder.progress.value!!.keptCardIds.isEmpty())
    }

    @Test
    fun aThemeIslandNeedsNoAnswersAndAnIslandSkipsWhatAStarterTopicSays() = runTest {
        flashcards.seedCoast(coastId = 1, language = "German")
        flashcards.addFlashcard(1, "My name is Anna.", "Ich heiße Anna.", "About Me")
        settings.setPersonalAnswer("details_age", "34")
        modelWrites("I am 34 years old. || Ich bin 34 Jahre alt.", "My name is Anna. || Ich heiße Anna.")

        builder.run(1, orders("details", "opinions"))

        // "My name is Anna." is on the About Me island already, so Personal Details doesn't repeat it.
        val details = flashcards.islands.value.first { it.name == "Personal Details" }
        assertEquals(listOf("I am 34 years old."), flashcards.cards.value.filter { it.islandId == details.islandId }.map { it.nativeText })
        assertTrue("My name is Anna." in translator.prompts.first().substringAfter("already have"))
        assertTrue(flashcards.islands.value.any { it.name == "Opinions & Reasons" })
        assertEquals(2, builder.progress.value!!.done)
        assertTrue("sentences on one theme" in translator.prompts.first { "\"Opinions & Reasons\"" in it })
    }

    @Test
    fun aRunCanBeStoppedAndKeepsWhatItWrote() = runTest {
        flashcards.seedCoast(coastId = 1, language = "German")
        settings.setPersonalAnswer("pets_which", "a cat called Milo")
        settings.setPersonalAnswer("food_favourites", "lasagne")
        val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
        val slowTranslator = object : Translator by translator {
            override suspend fun complete(engine: TranslationEngine, prompt: String): Completion {
                if ("\"Food I Like\"" in prompt) gate.await()
                return if ("language island" in prompt) Completion("1 || I have a cat. || Ich habe eine Katze.") else Completion("NONE")
            }
        }
        val stoppable = PersonalIslandBuilder(flashcards, settings, slowTranslator, StarterTranslator(slowTranslator), CoroutineScope(UnconfinedTestDispatcher(testScheduler)))

        stoppable.start(1, orders("pets", "food"))
        assertTrue(stoppable.isRunning)
        stoppable.stop()

        val progress = stoppable.progress.value!!
        assertTrue(progress.isFinished)
        assertTrue(progress.stopped)
        assertEquals(1, progress.done)
        assertEquals(listOf("I have a cat."), flashcards.cards.value.map { it.nativeText })
        assertFalse(stoppable.isRunning)
    }

    @Test
    fun anIslandBecomesATalkToRehearse() = runTest {
        flashcards.coasts.value = listOf(Coast(coastId = 1, language = "Spanish", region = "Mexico"))
        flashcards.addFlashcard(1, "I have a cat.", "Tengo un gato.", "Pets")
        flashcards.addFlashcard(1, "His name is Milo.", "Se llama Milo.", "Pets")
        val viewModel = coastViewModel(coastId = 1)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        translator.completion = Completion("TALK:\nTengo un gato y se llama Milo.\nMEANING:\nI have a cat and his name is Milo.")

        viewModel.writeTalk(flashcards.islands.value.single())

        val talk = viewModel.uiState.value.talk!!
        assertEquals("Tengo un gato y se llama Milo.", talk.talk?.targetText)
        assertEquals("I have a cat and his name is Milo.", talk.talk?.nativeText)
        assertTrue("- Tengo un gato. (I have a cat.)" in translator.prompts.last())
        assertTrue("Spanish as spoken in Mexico" in translator.prompts.last())

        translator.completion = Completion("I can't do that.")
        viewModel.writeTalk(flashcards.islands.value.single())
        assertEquals("Gemini's answer couldn't be read. Try again.", viewModel.uiState.value.talk?.error)

        viewModel.dismissTalk()
        assertNull(viewModel.uiState.value.talk)
    }

    @Test
    fun theCoastHasARouteThatStartsAtA1() = runTest {
        flashcards.seedCoast(coastId = 1, language = "German")
        val viewModel = coastViewModel(coastId = 1)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }

        val route = viewModel.uiState.value.route!!
        assertEquals(Level.A1, route.current.level)
        assertEquals("Greetings", route.next?.name)

        settings.setPersonalAnswer("pets_which", "a cat called Milo")
        modelWrites("I have a cat. || Ich habe eine Katze.")
        builder.run(1, orders("pets"))

        val pets = viewModel.uiState.value.route!!.stop(aboutStopId("pets"))!!
        assertEquals(RouteStop.State.Added, pets.state)
        assertEquals(1, pets.cards)
        assertEquals(0, pets.learned)
        assertEquals(RouteStop.State.Ready, viewModel.uiState.value.route!!.stop(levelUpStopId("pets", Level.B1))?.state)
        assertEquals(listOf("Your pets: kind, name, age" to "a cat called Milo"), viewModel.uiState.value.islandFacts["pets"])
    }

    @Test
    fun reachingALevelIsCelebratedOnceAndShowsOnMyCoasts() = runTest {
        flashcards.seedCoast(coastId = 1, language = "German")
        val viewModel = coastViewModel(coastId = 1)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        assertNull(viewModel.uiState.value.levelReached)
        assertEquals(Level.A1, viewModel.uiState.value.route!!.islandLevels()["Greetings"])

        // Every island of A1 on the coast, with its one card studied.
        val a1 = viewModel.uiState.value.route!!.levels.first()
        settings.setPersonalAnswer("details_age", "34")
        for (stop in a1.stops) {
            val card = flashcards.addFlashcard(1, "n ${stop.name}", "t ${stop.name}", stop.name).card
            flashcards.saveReview(card.copy(lastReviewed = 1L, due = Long.MAX_VALUE))
            if (stop.kind == RouteStop.Kind.Topic) settings.markStarterTopicAdded(1, stop.name)
            else settings.markPersonalIslandBuilt(1, "details", PersonalIslands.byId("details")!!.fingerprint(settings.profile.value, settings.personalAnswers.value))
        }

        assertEquals(Level.A1, viewModel.uiState.value.levelReached)
        assertEquals(Level.A2, viewModel.uiState.value.route!!.current.level)
        val route = observeRoute(flashcards, settings, 1, 0L).first()
        assertTrue(route.levels.first().isComplete)

        viewModel.celebrated(Level.A1)

        assertNull(viewModel.uiState.value.levelReached)
        assertEquals(setOf("1:A1"), settings.levelsCelebrated.value)
    }

    @Test
    fun aCoastOffersWhatIsAnsweredAndNoticesChangedAnswers() = runTest {
        flashcards.seedCoast(coastId = 1, language = "German")
        val viewModel = coastViewModel(coastId = 1)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        fun state(id: String) = viewModel.uiState.value.personalIslands.firstOrNull { it.island.id == id }?.state

        // Nothing answered: every island waits for its questions.
        assertEquals(PersonalIslands.ALL.size, viewModel.uiState.value.personalIslands.size)
        assertEquals(PersonalIslandState.Locked, state("pets"))

        settings.setPersonalAnswer("pets_which", "a cat called Milo")
        assertEquals(PersonalIslandState.Ready, state("pets"))
        assertEquals(listOf("Your pets: kind, name, age" to "a cat called Milo"), viewModel.uiState.value.personalIslands.first { it.island.id == "pets" }.facts)

        modelWrites("I have a cat. || Ich habe eine Katze.")
        builder.run(1, orders("pets"))
        assertNull(state("pets")) // Written and up to date: no longer offered.

        settings.setPersonalAnswer("pets_like", "he sleeps all day")
        assertEquals(PersonalIslandState.Changed, state("pets"))

        // Deleted from the coast, it can be written afresh.
        flashcards.deleteIsland(flashcards.islands.value.single())
        assertEquals(PersonalIslandState.Ready, state("pets"))
    }
}

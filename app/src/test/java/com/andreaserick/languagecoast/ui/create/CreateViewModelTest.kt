package com.andreaserick.languagecoast.ui.create

import com.andreaserick.languagecoast.data.DAILY_NEW_SENTENCES
import com.andreaserick.languagecoast.data.DAILY_REVIEW_CAP
import com.andreaserick.languagecoast.data.SentenceSuggester
import com.andreaserick.languagecoast.data.SettingsDefaults
import com.andreaserick.languagecoast.data.StarterTranslator
import com.andreaserick.languagecoast.data.SuggestedSentence
import com.andreaserick.languagecoast.data.SuggestionPool
import com.andreaserick.languagecoast.data.TranslationEngine
import com.andreaserick.languagecoast.data.TranslationProvider
import com.andreaserick.languagecoast.data.TranslationResult
import com.andreaserick.languagecoast.testing.FakeFlashcardRepository
import com.andreaserick.languagecoast.testing.FakeSettingsRepository
import com.andreaserick.languagecoast.testing.FakeTranslator
import com.andreaserick.languagecoast.testing.MainDispatcherRule
import com.andreaserick.languagecoast.testing.TEST_CLOCK
import com.andreaserick.languagecoast.testing.TEST_TIME_ZONE
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class CreateViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val flashcards = FakeFlashcardRepository()
    private val settings = FakeSettingsRepository()
    private val translator = FakeTranslator()
    private lateinit var viewModel: CreateViewModel

    /** A pool whose background top-ups never run, so they can't race the assertions on the fakes. */
    private fun pool() = SuggestionPool(flashcards, settings, SentenceSuggester(translator, settings, StarterTranslator(translator)), TEST_CLOCK, CoroutineScope(Job().apply { cancel() }))

    @Before
    fun setUp() {
        flashcards.seedCoast(coastId = 1, language = "Spanish")
        viewModel = CreateViewModel(flashcards, settings, translator, TEST_CLOCK, TEST_TIME_ZONE, pool())
    }

    @Test
    fun planCapsTodaysReviewsAndNewSentences() = runTest {
        flashcards.seed(islandId = 1, cardCount = DAILY_REVIEW_CAP + 3, coastId = 1)
        flashcards.addSuggestions((1..5).map { SuggestedSentence(coastId = 1, nativeText = "n$it", targetText = "t$it", category = "C") })

        val plan = viewModel.uiState.plan!!

        assertEquals(DAILY_REVIEW_CAP, plan.reviewCount)
        assertEquals(DAILY_NEW_SENTENCES, plan.newCount)
        assertEquals(3, plan.leftOver)
        assertFalse(plan.isWelcomeBack)
        assertEquals(5, viewModel.uiState.readySuggestions)
    }

    @Test
    fun startingACoastAsksForItsGoal() = runTest {
        viewModel.addCoast("German")

        val prompt = viewModel.uiState.goalPrompt!!
        assertEquals("German", prompt.language)
        assertEquals(prompt.coastId, settings.activeCoastId.value)

        viewModel.setGoal(prompt, "  A trip to Berlin  ")

        assertNull(viewModel.uiState.goalPrompt)
        assertEquals("A trip to Berlin", flashcards.coasts.value.first { it.language == "German" }.goal)
    }

    @Test
    fun changingTheGoalThrowsAwayTheSentencesMadeForTheOldOne() = runTest {
        val coast = flashcards.coasts.value.single()
        flashcards.addSuggestions(listOf(SuggestedSentence(coastId = 1, nativeText = "n", targetText = "t", category = "C")))
        flashcards.addSuggestions(listOf(SuggestedSentence(coastId = 1, nativeText = "s", targetText = "u", category = "C", skipped = true)))

        viewModel.setGoal(coast, "Work")

        // The pending one goes; the skipped one stays so it isn't suggested again.
        assertEquals(listOf("s"), flashcards.suggestions.value.map { it.nativeText })
        assertEquals("Work", flashcards.coasts.value.single().goal)
    }

    @Test
    fun theRegionIsStoredWithTheGoalAndReachesTheTranslation() = runTest {
        val coast = flashcards.coasts.value.single()
        settings.apiKey.value = "key"

        viewModel.setGoal(coast, "A trip", " Mexico ")
        viewModel.onNativeSentenceChange("Hello")
        viewModel.save()

        assertEquals("Mexico", flashcards.coasts.value.single().region)
        assertEquals("Mexico", translator.lastRequest?.targetRegion)
    }

    @Test
    fun sameGoalAgainKeepsThePreparedSentences() = runTest {
        flashcards.setCoastGoal(1, "Work", null)
        flashcards.addSuggestions(listOf(SuggestedSentence(coastId = 1, nativeText = "n", targetText = "t", category = "C")))

        viewModel.setGoal(flashcards.coasts.value.single(), "Work ")

        assertEquals(1, flashcards.suggestions.value.size)
    }

    @Test
    fun starterSentencesBecomeCardsOnTheirTopicsIslands() = runTest {
        settings.apiKey.value = "key"
        translator.result = TranslationResult("Hola", "Restaurant", isSuccess = true)

        viewModel.addStarterSentences(listOf(StarterSentence("A table for two, please.", "Restaurant", "🍽️"), StarterSentence("I live in Oslo.", "About Me", "🙋")))

        val batch = viewModel.uiState.batch!!
        assertTrue(batch.isDone)
        assertEquals(0, batch.failed)
        assertEquals(listOf("A table for two, please.", "I live in Oslo."), flashcards.cards.value.map { it.nativeText })
        // The island takes the topic's emoji when the AI gives none and keeps the topic as the category.
        assertEquals("🍽️", flashcards.islands.value.single { it.name == "Restaurant" }.emoji)
        assertEquals("About Me", translator.lastRequest?.userCategory)
        assertEquals("English", translator.lastRequest?.nativeLanguage)
        assertTrue(viewModel.uiState.coastSentences.contains("i live in oslo"))
    }

    @Test
    fun starterSentencesAreTranslatedIntoTheUsersLanguageFirst() = runTest {
        settings.apiKey.value = "key"
        settings.nativeLanguage.value = "Dutch"

        viewModel.addStarterSentences(listOf(StarterSentence("Hello, how are you?", "Greetings")))

        // The canned translator answers "Hola" to everything: the English was turned into "Dutch" first, then that was translated.
        val card = flashcards.cards.value.single()
        assertEquals("Hola", card.nativeText)
        assertEquals("Hola", card.targetText)
        assertEquals(2, translator.requests)
    }

    @Test
    fun manualSaveUsesDefaultCategoryAndClearsForm() = runTest {
        viewModel.onManualModeChange(true)
        viewModel.onNativeSentenceChange("  Hello  ")
        viewModel.onTargetSentenceChange("Hola")

        viewModel.save()

        val card = flashcards.cards.value.single()
        assertEquals("Hello", card.nativeText)
        assertEquals("Hola", card.targetText)
        assertEquals(DEFAULT_MANUAL_CATEGORY, flashcards.islands.value.single().name)
        val result = viewModel.uiState.result as SaveResult.Saved
        assertEquals(DEFAULT_MANUAL_CATEGORY, result.category)
        assertEquals("📝", result.emoji)
        assertEquals("Hello", result.nativeText)
        assertEquals("Hola", result.targetText)
        assertTrue(result.manual)
        assertEquals("", viewModel.uiState.nativeSentence)
        assertFalse(viewModel.uiState.isSaving)
    }

    @Test
    fun manualSaveWithoutTranslationShowsErrorAndKeepsInput() = runTest {
        viewModel.onManualModeChange(true)
        viewModel.onNativeSentenceChange("Hello")

        viewModel.save()

        assertTrue(viewModel.uiState.result is SaveResult.Error)
        assertEquals("Hello", viewModel.uiState.nativeSentence)
        assertTrue(flashcards.cards.value.isEmpty())
    }

    @Test
    fun aiSaveWithoutApiKeyShowsErrorAndDoesNotCallTranslator() = runTest {
        viewModel.onNativeSentenceChange("Hello")

        viewModel.save()

        assertEquals(SaveResult.Error("Please enter a Gemini API key in Settings first!"), viewModel.uiState.result)
        assertNull(translator.lastRequest)
    }

    @Test
    fun aiSaveUsesSelectedCoastLanguageAndOnlyItsIslands() = runTest {
        settings.apiKey.value = "key"
        flashcards.seed(islandId = 1, cardCount = 1, coastId = 1)
        flashcards.seedCoast(coastId = 2, language = "Icelandic")
        flashcards.seed(islandId = 2, cardCount = 1, coastId = 2)
        viewModel.onCoastSelected(flashcards.coasts.value.last())
        viewModel.onNativeSentenceChange("horse")

        viewModel.save()

        val request = translator.lastRequest!!
        assertEquals(TranslationEngine.Gemini("key", SettingsDefaults.GEMINI_MODEL), request.engine)
        assertEquals("Icelandic", request.targetLanguage)
        assertEquals(listOf("Island 2"), request.existingCategories)
        val result = viewModel.uiState.result as SaveResult.Saved
        assertEquals("Greetings", result.category)
        assertFalse(result.manual)
        val newIsland = flashcards.islands.value.single { it.name == "Greetings" }
        assertEquals(2, newIsland.coastId)
    }

    @Test
    fun firstCoastIsSelectedUntilAnotherIsPicked() = runTest {
        flashcards.seedCoast(coastId = 2, language = "German")
        assertEquals("Spanish", viewModel.uiState.selectedCoast?.language)

        viewModel.onCoastSelected(flashcards.coasts.value.last())

        assertEquals(2, settings.activeCoastId.value)
        assertEquals("German", viewModel.uiState.selectedCoast?.language)
    }

    @Test
    fun withoutCoastsSavingAsksToStartOne() = runTest {
        flashcards.coasts.value = emptyList()
        viewModel.onManualModeChange(true)
        viewModel.onNativeSentenceChange("Hello")
        viewModel.onTargetSentenceChange("Hola")

        viewModel.save()

        assertEquals(SaveResult.Error("Start a coast first!"), viewModel.uiState.result)
        assertTrue(flashcards.cards.value.isEmpty())
    }

    @Test
    fun coastInNativeLanguageBlocksSavingInBothModes() = runTest {
        // e.g. a coast migrated from an old setup whose target language equalled the native one
        settings.nativeLanguage.value = "Spanish"
        settings.apiKey.value = "key"
        assertTrue(viewModel.uiState.isSameLanguage)

        viewModel.onNativeSentenceChange("Hola")
        viewModel.save()
        assertEquals(SaveResult.Error(sameLanguageMessage("Spanish")), viewModel.uiState.result)
        assertNull(translator.lastRequest)

        viewModel.onManualModeChange(true)
        viewModel.onTargetSentenceChange("Hola")
        viewModel.save()
        assertTrue(flashcards.cards.value.isEmpty())
    }

    @Test
    fun aiEmojiIsUsedForNewIslands() = runTest {
        settings.apiKey.value = "key"
        translator.result = TranslationResult("hestur", "Animals", isSuccess = true, emoji = "🐴")
        viewModel.onNativeSentenceChange("horse")

        viewModel.save()

        assertEquals("🐴", flashcards.islands.value.single().emoji)
        assertEquals("🐴", (viewModel.uiState.result as SaveResult.Saved).emoji)
    }

    @Test
    fun undoRemovesTheCardAndTheIslandCreatedForIt() = runTest {
        flashcards.seed(islandId = 1, cardCount = 1, coastId = 1)
        viewModel.onManualModeChange(true)

        // New island: undo removes the card and the island.
        viewModel.onNativeSentenceChange("Hello")
        viewModel.onTargetSentenceChange("Hola")
        viewModel.onCategoryChange("Greetings")
        viewModel.save()
        viewModel.undoLastSave()
        assertEquals(listOf("Island 1"), flashcards.islands.value.map { it.name })
        assertEquals(1, flashcards.cards.value.size)
        assertNull(viewModel.uiState.result)

        // Existing island: undo removes only the card.
        viewModel.onNativeSentenceChange("Bye")
        viewModel.onTargetSentenceChange("Adiós")
        viewModel.onCategoryChange("Island 1")
        viewModel.save()
        viewModel.undoLastSave()
        assertEquals(listOf("Island 1"), flashcards.islands.value.map { it.name })
        assertEquals(listOf("n1"), flashcards.cards.value.map { it.nativeText })
    }

    @Test
    fun addingCoastFromCreateSelectsIt() = runTest {
        viewModel.addCoast("Korean")

        assertEquals("Korean", viewModel.uiState.selectedCoast?.language)
        assertFalse("Korean" in viewModel.uiState.availableLanguages.map { it.name })
    }

    @Test
    fun failedTranslationShowsErrorAndSavesNothing() = runTest {
        settings.apiKey.value = "key"
        translator.result = TranslationResult.FAILURE
        viewModel.onNativeSentenceChange("Hello")

        viewModel.save()

        assertEquals(SaveResult.Error("AI Translation Failed."), viewModel.uiState.result)
        assertTrue(flashcards.cards.value.isEmpty())
    }

    @Test
    fun resultCardIsHiddenAfterTimeout() = runTest {
        viewModel.onManualModeChange(true)
        viewModel.onNativeSentenceChange("Hello")
        viewModel.onTargetSentenceChange("Hola")

        viewModel.save()
        advanceTimeBy(RESULT_VISIBLE_MILLIS - 1)
        assertTrue(viewModel.uiState.result is SaveResult.Saved)

        advanceTimeBy(2)
        assertNull(viewModel.uiState.result)
    }

    @Test
    fun blankSentenceIsIgnored() = runTest {
        viewModel.onNativeSentenceChange("   ")

        viewModel.save()

        assertNull(viewModel.uiState.result)
        assertNull(translator.lastRequest)
    }

    @Test
    fun onDeviceTranslationNeedsNoKey() = runTest {
        settings.translationProvider.value = TranslationProvider.OnDevice
        viewModel.onNativeSentenceChange("Hello")

        viewModel.save()

        assertEquals(TranslationEngine.OnDevice, translator.lastRequest!!.engine)
        assertTrue(viewModel.uiState.result is SaveResult.Saved)
    }

    @Test
    fun openRouterUsesItsKeyAndFallsBackToTheAutoModel() = runTest {
        settings.translationProvider.value = TranslationProvider.OpenRouter
        settings.openRouterKey.value = "sk-or-key"
        settings.openRouterModel.value = "  "
        viewModel.onNativeSentenceChange("Hello")

        viewModel.save()

        assertEquals(TranslationEngine.OpenRouter("sk-or-key", SettingsDefaults.OPENROUTER_MODEL), translator.lastRequest!!.engine)
    }

    @Test
    fun openRouterWithoutKeyShowsError() = runTest {
        settings.translationProvider.value = TranslationProvider.OpenRouter
        settings.apiKey.value = "a Gemini key doesn't count"
        viewModel.onNativeSentenceChange("Hello")

        viewModel.save()

        assertEquals(SaveResult.Error("Please enter an OpenRouter API key in Settings first!"), viewModel.uiState.result)
        assertNull(translator.lastRequest)
    }

    @Test
    fun translatorErrorMessageIsShown() = runTest {
        settings.translationProvider.value = TranslationProvider.OnDevice
        translator.result = TranslationResult.failure("On-device translation doesn't cover Latin.")
        viewModel.onNativeSentenceChange("Hello")

        viewModel.save()

        assertEquals(SaveResult.Error("On-device translation doesn't cover Latin."), viewModel.uiState.result)
        assertTrue(flashcards.cards.value.isEmpty())
    }

    @Test
    fun todaySummaryCountsDueCardsAndCoastsWithThem() = runTest {
        flashcards.seed(islandId = 1, cardCount = 2, coastId = 1)
        flashcards.seedCoast(coastId = 2, language = "Icelandic")
        flashcards.seed(islandId = 2, cardCount = 1, coastId = 2)
        settings.streakCount.value = 4
        val viewModel = CreateViewModel(flashcards, settings, translator, TEST_CLOCK, TEST_TIME_ZONE, pool())

        assertTrue(viewModel.uiState.hasCards)
        assertEquals(3, viewModel.uiState.dueCount)
        assertEquals(2, viewModel.uiState.dueCoastCount)
        assertEquals(4, viewModel.uiState.streakCount)
    }

    @Test
    fun recentCardsAreTheNewestOnTheSelectedCoast() = runTest {
        flashcards.seed(islandId = 1, cardCount = 4, coastId = 1)
        flashcards.seedCoast(coastId = 2, language = "Icelandic")
        flashcards.seed(islandId = 2, cardCount = 1, coastId = 2)
        settings.activeCoastId.value = 1
        val viewModel = CreateViewModel(flashcards, settings, translator, TEST_CLOCK, TEST_TIME_ZONE, pool())

        assertEquals(listOf("n4", "n3", "n2"), viewModel.uiState.recentCards.map { it.card.nativeText })
        assertEquals("Island 1", viewModel.uiState.recentCards.first().islandName)

        viewModel.onCoastSelected(flashcards.coasts.value.last())

        assertEquals(listOf("n1"), viewModel.uiState.recentCards.map { it.card.nativeText })
    }

    @Test
    fun alternativesAreStoredWithTheCard() = runTest {
        settings.apiKey.value = "key"
        translator.result = TranslationResult(
            "Wollen wir ins Kino?", "Plans", isSuccess = true,
            alternatives = listOf("Sollen wir ins Kino?"), note = "Both are fine."
        )
        viewModel.onNativeSentenceChange("Shall we go to the cinema tonight?")
        viewModel.save()

        // Stored with the card, so they show when studying it; the preview hides as usual.
        val card = flashcards.cards.value.single()
        assertEquals(listOf("Sollen wir ins Kino?"), card.alternatives)
        assertEquals("Both are fine.", card.note)
        advanceTimeBy(RESULT_VISIBLE_MILLIS + 1)
        assertNull(viewModel.uiState.result)
    }

    @Test
    fun typingASentenceTheCoastAlreadyHasShowsWhereItIs() = runTest {
        flashcards.addFlashcard(coastId = 1, nativeText = "Where is the station?", targetText = "¿Dónde está la estación?", category = "Travel")

        viewModel.onNativeSentenceChange("where is the station")
        val duplicate = viewModel.uiState.duplicate!!
        assertEquals("Travel", duplicate.islandName)
        assertEquals("¿Dónde está la estación?", duplicate.card.targetText)

        viewModel.onNativeSentenceChange("where is the stations")
        assertNull(viewModel.uiState.duplicate)
    }

    @Test
    fun cardsOnOtherCoastsAreNotDuplicates() = runTest {
        flashcards.seedCoast(coastId = 2, language = "German")
        flashcards.addFlashcard(coastId = 2, nativeText = "Hello", targetText = "Hallo", category = "Greetings")

        viewModel.onNativeSentenceChange("Hello")

        assertNull(viewModel.uiState.duplicate)
    }

    @Test
    fun aCardSavedWhileTypingIsFoundToo() = runTest {
        viewModel.onNativeSentenceChange("Hello")
        assertNull(viewModel.uiState.duplicate)

        flashcards.addFlashcard(coastId = 1, nativeText = "Hello!", targetText = "¡Hola!", category = "Greetings")

        assertEquals("¡Hola!", viewModel.uiState.duplicate?.card?.targetText)
    }
}

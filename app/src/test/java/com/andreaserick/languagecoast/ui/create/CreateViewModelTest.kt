package com.andreaserick.languagecoast.ui.create

import com.andreaserick.languagecoast.data.SettingsDefaults
import com.andreaserick.languagecoast.data.TranslationEngine
import com.andreaserick.languagecoast.data.TranslationProvider
import com.andreaserick.languagecoast.data.TranslationResult
import com.andreaserick.languagecoast.testing.FakeFlashcardRepository
import com.andreaserick.languagecoast.testing.FakeSettingsRepository
import com.andreaserick.languagecoast.testing.FakeTranslator
import com.andreaserick.languagecoast.testing.MainDispatcherRule
import com.andreaserick.languagecoast.testing.TEST_CLOCK
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

    @Before
    fun setUp() {
        flashcards.seedCoast(coastId = 1, language = "Spanish")
        viewModel = CreateViewModel(flashcards, settings, translator, TEST_CLOCK)
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
        val viewModel = CreateViewModel(flashcards, settings, translator, TEST_CLOCK)

        assertTrue(viewModel.uiState.hasCards)
        assertEquals(3, viewModel.uiState.dueCount)
        assertEquals(2, viewModel.uiState.dueCoastCount)
        assertEquals(4, viewModel.uiState.streakCount)
    }

    @Test
    fun startReviewOpensTheIslandWithTheMostDueCardsOnTheSelectedCoast() = runTest {
        flashcards.seed(islandId = 1, cardCount = 1, coastId = 1)
        flashcards.seed(islandId = 2, cardCount = 3, coastId = 1)
        flashcards.seedCoast(coastId = 2, language = "Icelandic")
        flashcards.seed(islandId = 3, cardCount = 5, coastId = 2)
        settings.activeCoastId.value = 1
        val viewModel = CreateViewModel(flashcards, settings, translator, TEST_CLOCK)
        var opened: Pair<Int, String>? = null

        viewModel.startReview { id, name -> opened = id to name }

        assertEquals(2 to "Island 2", opened)
    }

    @Test
    fun startReviewFallsBackToTheCoastWithTheMostDueCards() = runTest {
        flashcards.seed(islandId = 1, cardCount = 1, coastId = 1)
        // Nothing on the selected coast is due.
        flashcards.cards.update { all -> all.map { it.copy(due = Long.MAX_VALUE) } }
        flashcards.seedCoast(coastId = 2, language = "Icelandic")
        flashcards.seed(islandId = 2, cardCount = 2, coastId = 2)
        settings.activeCoastId.value = 1
        val viewModel = CreateViewModel(flashcards, settings, translator, TEST_CLOCK)
        var opened: Pair<Int, String>? = null

        viewModel.startReview { id, name -> opened = id to name }

        assertEquals(2 to "Island 2", opened)
    }

    @Test
    fun recentCardsAreTheNewestOnTheSelectedCoast() = runTest {
        flashcards.seed(islandId = 1, cardCount = 4, coastId = 1)
        flashcards.seedCoast(coastId = 2, language = "Icelandic")
        flashcards.seed(islandId = 2, cardCount = 1, coastId = 2)
        settings.activeCoastId.value = 1
        val viewModel = CreateViewModel(flashcards, settings, translator, TEST_CLOCK)

        assertEquals(listOf("n4", "n3", "n2"), viewModel.uiState.recentCards.map { it.card.nativeText })
        assertEquals("Island 1", viewModel.uiState.recentCards.first().islandName)

        viewModel.onCoastSelected(flashcards.coasts.value.last())

        assertEquals(listOf("n1"), viewModel.uiState.recentCards.map { it.card.nativeText })
    }

    @Test
    fun alternativesStayOnScreenAndCanReplaceTheTranslation() = runTest {
        settings.apiKey.value = "key"
        translator.result = TranslationResult(
            "Wollen wir ins Kino?", "Plans", isSuccess = true,
            alternatives = listOf("Sollen wir ins Kino?"), note = "Both are fine."
        )
        viewModel.onNativeSentenceChange("Shall we go to the cinema tonight?")
        viewModel.save()

        val saved = viewModel.uiState.result as SaveResult.Saved
        assertEquals(listOf("Sollen wir ins Kino?"), saved.alternatives)
        assertEquals("Both are fine.", saved.note)
        // Stored with the card, so they show when studying it.
        assertEquals(listOf("Sollen wir ins Kino?"), flashcards.cards.value.single().alternatives)
        assertEquals("Both are fine.", flashcards.cards.value.single().note)
        // Not hidden after the usual timeout, so there is time to compare.
        advanceTimeBy(RESULT_VISIBLE_MILLIS + 1)
        assertTrue(viewModel.uiState.result is SaveResult.Saved)

        viewModel.useAlternative("Sollen wir ins Kino?")

        val switched = viewModel.uiState.result as SaveResult.Saved
        assertEquals("Sollen wir ins Kino?", switched.targetText)
        assertEquals(listOf("Wollen wir ins Kino?"), switched.alternatives)
        assertEquals("Sollen wir ins Kino?", flashcards.cards.value.single().targetText)
        assertEquals(listOf("Wollen wir ins Kino?"), flashcards.cards.value.single().alternatives)

        viewModel.undoLastSave()
        assertTrue(flashcards.cards.value.isEmpty())
    }
}

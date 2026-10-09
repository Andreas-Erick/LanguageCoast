package com.andreaserick.languagecoast.ui.create

import com.andreaserick.languagecoast.data.TranslationResult
import com.andreaserick.languagecoast.testing.FakeFlashcardRepository
import com.andreaserick.languagecoast.testing.FakeSettingsRepository
import com.andreaserick.languagecoast.testing.FakeTranslator
import com.andreaserick.languagecoast.testing.MainDispatcherRule
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
        viewModel = CreateViewModel(flashcards, settings, translator)
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

        assertEquals(SaveResult.Error("Please enter an API Key in Settings first!"), viewModel.uiState.result)
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
        assertEquals("key", request.apiKey)
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
}

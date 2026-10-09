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
        assertEquals(SaveResult.Saved(DEFAULT_MANUAL_CATEGORY, manual = true), viewModel.uiState.result)
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
        assertEquals(SaveResult.Saved("Greetings", manual = false), viewModel.uiState.result)
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

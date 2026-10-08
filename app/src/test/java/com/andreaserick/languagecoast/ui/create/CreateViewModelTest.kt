package com.andreaserick.languagecoast.ui.create

import com.andreaserick.languagecoast.data.TranslationResult
import com.andreaserick.languagecoast.testing.FakeFlashcardRepository
import com.andreaserick.languagecoast.testing.FakeSettingsRepository
import com.andreaserick.languagecoast.testing.FakeTranslator
import com.andreaserick.languagecoast.testing.MainDispatcherRule
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
    fun aiSaveSendsSettingsAndExistingIslandsToTranslator() = runTest {
        settings.apiKey.value = "key"
        settings.targetLanguage.value = "Icelandic"
        flashcards.seed(islandId = 1, cardCount = 1)
        viewModel.onNativeSentenceChange("horse")

        viewModel.save()

        val request = translator.lastRequest!!
        assertEquals("key", request.apiKey)
        assertEquals("Icelandic", request.targetLanguage)
        assertEquals(listOf("Island 1"), request.existingCategories)
        assertEquals(SaveResult.Saved("Greetings", manual = false), viewModel.uiState.result)
        assertTrue(flashcards.cards.value.any { it.targetText == "Hola" })
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
    fun blankSentenceIsIgnored() = runTest {
        viewModel.onNativeSentenceChange("   ")

        viewModel.save()

        assertNull(viewModel.uiState.result)
        assertNull(translator.lastRequest)
    }
}

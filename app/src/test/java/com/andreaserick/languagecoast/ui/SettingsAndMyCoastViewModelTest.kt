package com.andreaserick.languagecoast.ui

import com.andreaserick.languagecoast.testing.FakeFlashcardRepository
import com.andreaserick.languagecoast.testing.FakeSettingsRepository
import com.andreaserick.languagecoast.testing.MainDispatcherRule
import com.andreaserick.languagecoast.ui.mycoast.MyCoastViewModel
import com.andreaserick.languagecoast.ui.settings.SettingsViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsAndMyCoastViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val flashcards = FakeFlashcardRepository()
    private val settings = FakeSettingsRepository()

    @Test
    fun savingApiKeyTrimsItAndShowsConfirmationUntilEdited() = runTest {
        val viewModel = SettingsViewModel(settings)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }

        viewModel.saveApiKey("  my-key  ")

        assertEquals("my-key", settings.apiKey.value)
        assertEquals("my-key", viewModel.uiState.value.savedApiKey)
        assertTrue(viewModel.uiState.value.apiKeySaved)

        viewModel.onApiKeyEdited()
        assertFalse(viewModel.uiState.value.apiKeySaved)
    }

    @Test
    fun languageAndModelSelectionsArePersisted() = runTest {
        val viewModel = SettingsViewModel(settings)

        viewModel.setNativeLanguage("German")
        viewModel.setTargetLanguage("Icelandic")
        viewModel.setGeminiModel("gemini-3.5-flash")

        assertEquals("German", settings.nativeLanguage.value)
        assertEquals("Icelandic", settings.targetLanguage.value)
        assertEquals("gemini-3.5-flash", settings.geminiModel.value)
    }

    @Test
    fun myCoastRefreshesStreakAndListsIslands() = runTest {
        flashcards.seed(islandId = 1, cardCount = 2)
        settings.streakCount.value = 3

        val viewModel = MyCoastViewModel(flashcards, settings)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }

        assertEquals(1, settings.streakRefreshes)
        assertEquals(listOf("Island 1"), viewModel.uiState.value.islands.map { it.name })
        assertEquals(3, viewModel.uiState.value.streakCount)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun deletingIslandRemovesItAndItsCards() = runTest {
        flashcards.seed(islandId = 1, cardCount = 2)
        val viewModel = MyCoastViewModel(flashcards, settings)

        viewModel.deleteIsland(flashcards.islands.value.single())

        assertTrue(flashcards.islands.value.isEmpty())
        assertTrue(flashcards.cards.value.isEmpty())
    }
}

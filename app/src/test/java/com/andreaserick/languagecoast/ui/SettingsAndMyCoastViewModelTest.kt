package com.andreaserick.languagecoast.ui

import androidx.lifecycle.SavedStateHandle
import com.andreaserick.languagecoast.data.SettingsDefaults
import com.andreaserick.languagecoast.testing.FakeFlashcardRepository
import com.andreaserick.languagecoast.testing.FakeSettingsRepository
import com.andreaserick.languagecoast.testing.MainDispatcherRule
import com.andreaserick.languagecoast.ui.coast.CoastViewModel
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
        viewModel.setGeminiModel("gemini-3.5-flash")

        assertEquals("German", settings.nativeLanguage.value)
        assertEquals("gemini-3.5-flash", settings.geminiModel.value)
    }

    @Test
    fun myCoastRefreshesStreakAndListsCoastsWithCounts() = runTest {
        flashcards.seedCoast(coastId = 1, language = "German")
        flashcards.seed(islandId = 1, cardCount = 2, coastId = 1)
        flashcards.seed(islandId = 2, cardCount = 3, coastId = 1)
        flashcards.seedCoast(coastId = 2, language = "Icelandic")
        settings.streakCount.value = 3

        val viewModel = MyCoastViewModel(flashcards, settings)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }

        val state = viewModel.uiState.value
        assertEquals(1, settings.streakRefreshes)
        assertEquals(listOf("German Coast", "Icelandic Coast"), state.coasts.map { it.coast.displayName })
        assertEquals(listOf(2, 0), state.coasts.map { it.islandCount })
        assertEquals(listOf(5, 0), state.coasts.map { it.cardCount })
        assertEquals(3, state.streakCount)
        assertFalse(state.isLoading)
    }

    @Test
    fun newCoastLanguagesExcludeNativeLanguageAndExistingCoasts() = runTest {
        flashcards.seedCoast(coastId = 1, language = "German")
        val viewModel = MyCoastViewModel(flashcards, settings)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }

        val available = viewModel.uiState.value.availableLanguages
        assertFalse(SettingsDefaults.NATIVE_LANGUAGE in available)
        assertFalse("German" in available)
        assertTrue("Icelandic" in available)
    }

    @Test
    fun addingCoastMakesItActive() = runTest {
        val viewModel = MyCoastViewModel(flashcards, settings)

        viewModel.addCoast("Norwegian")

        val coast = flashcards.coasts.value.single()
        assertEquals("Norwegian", coast.language)
        assertEquals(coast.coastId, settings.activeCoastId.value)
    }

    @Test
    fun deletingCoastRemovesItsIslandsAndCardsOnly() = runTest {
        flashcards.seedCoast(coastId = 1, language = "German")
        flashcards.seed(islandId = 1, cardCount = 2, coastId = 1)
        flashcards.seedCoast(coastId = 2, language = "Icelandic")
        flashcards.seed(islandId = 2, cardCount = 1, coastId = 2)
        val viewModel = MyCoastViewModel(flashcards, settings)

        viewModel.deleteCoast(flashcards.coasts.value.first())

        assertEquals(listOf("Icelandic"), flashcards.coasts.value.map { it.language })
        assertEquals(listOf(2), flashcards.islands.value.map { it.islandId })
        assertEquals(1, flashcards.cards.value.size)
    }

    @Test
    fun coastShowsOnlyItsOwnIslandsAndBecomesActive() = runTest {
        flashcards.seedCoast(coastId = 1, language = "German")
        flashcards.seed(islandId = 1, cardCount = 1, coastId = 1)
        flashcards.seedCoast(coastId = 2, language = "Icelandic")
        flashcards.seed(islandId = 2, cardCount = 1, coastId = 2)

        val viewModel = CoastViewModel(SavedStateHandle(mapOf("coastId" to 2, "coastName" to "")), flashcards, settings)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }

        assertEquals("Icelandic Coast", viewModel.uiState.value.coastName)
        assertEquals(listOf("Island 2"), viewModel.uiState.value.islands.map { it.name })
        assertEquals(2, settings.activeCoastId.value)
    }

    @Test
    fun deletingIslandRemovesItAndItsCards() = runTest {
        flashcards.seed(islandId = 1, cardCount = 2)
        val viewModel = CoastViewModel(SavedStateHandle(mapOf("coastId" to 1, "coastName" to "")), flashcards, settings)

        viewModel.deleteIsland(flashcards.islands.value.single())

        assertTrue(flashcards.islands.value.isEmpty())
        assertTrue(flashcards.cards.value.isEmpty())
    }
}

package com.andreaserick.languagecoast.ui.settings

import com.andreaserick.languagecoast.data.ExportFormat
import com.andreaserick.languagecoast.testing.FakeFlashcardRepository
import com.andreaserick.languagecoast.testing.FakeSettingsRepository
import com.andreaserick.languagecoast.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CardTransferViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val flashcards = FakeFlashcardRepository()
    private val settings = FakeSettingsRepository()

    private fun TestScope.createViewModel(): CardTransferViewModel {
        val viewModel = CardTransferViewModel(flashcards, settings)
        // uiState is only computed while collected (WhileSubscribed).
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        return viewModel
    }

    private val CardTransferViewModel.preview get() = uiState.value.importPreview

    @Test
    fun exportContainsOnlyTheChosenCoast() = runTest {
        flashcards.seed(islandId = 1, cardCount = 2, coastId = 1)
        flashcards.seedCoast(coastId = 2, language = "Icelandic")
        flashcards.seed(islandId = 2, cardCount = 1, coastId = 2)
        val viewModel = createViewModel()

        val one = viewModel.buildExport(coastId = 2, format = ExportFormat.Anki)
        val all = viewModel.buildExport(coastId = null, format = ExportFormat.Anki)

        assertEquals(1, one.cardCount)
        assertTrue("Icelandic Coast::Island 2" in one.text)
        assertTrue("Spanish Coast" !in one.text)
        assertEquals(3, all.cardCount)
    }

    @Test
    fun exportFileNameNamesTheLanguage() = runTest {
        flashcards.seedCoast(coastId = 1, language = "Haitian Creole")
        val viewModel = createViewModel()

        assertEquals("language-coast-haitian-creole.txt", viewModel.exportFileName(1, ExportFormat.Anki))
        assertEquals("language-coast.md", viewModel.exportFileName(null, ExportFormat.Markdown))
    }

    @Test
    fun importPreviewTargetsTheActiveCoast() = runTest {
        flashcards.seedCoast(coastId = 1, language = "Spanish")
        flashcards.seedCoast(coastId = 2, language = "German")
        settings.activeCoastId.value = 2
        val viewModel = createViewModel()

        viewModel.onImportFileRead("Hello,Hallo\nBye,Tschüss\n")

        assertEquals(2, viewModel.preview?.coastId)
        assertEquals(2, viewModel.preview?.cardsToImport?.size)
        assertFalse(viewModel.preview!!.skipFirstRow)
    }

    @Test
    fun importAddsCardsIntoTheirIslandsAndSkipsDuplicates() = runTest {
        flashcards.seedCoast(coastId = 1, language = "German")
        flashcards.addFlashcard(1, "Hello", "Hallo", "Greetings")
        val viewModel = createViewModel()
        viewModel.onImportFileRead("hello,hallo,Greetings\nBread,Brot,Food\nBread,Brot,Food\nYes,Ja\n")
        var result: ImportResult? = null

        viewModel.confirmImport { result = it }

        assertEquals(2, result!!.added.size)
        assertEquals(2, result!!.duplicates)
        assertEquals(listOf("Greetings", "Food", DEFAULT_IMPORT_ISLAND), flashcards.islands.value.map { it.name })
        assertNull(viewModel.preview)
    }

    @Test
    fun swapAndHeaderOptionsApplyToTheImport() = runTest {
        flashcards.seedCoast(coastId = 1, language = "German")
        val viewModel = createViewModel()
        viewModel.onImportFileRead("German,English\nHallo,Hello\n")

        // A first row naming the coast's language and the native language is taken as a header.
        assertTrue(viewModel.preview!!.skipFirstRow)
        viewModel.setSwapColumns(true)
        viewModel.confirmImport {}

        val card = flashcards.cards.value.single()
        assertEquals("Hello", card.nativeText)
        assertEquals("Hallo", card.targetText)
    }

    @Test
    fun undoRemovesImportedCardsAndTheIslandsCreatedForThem() = runTest {
        flashcards.seedCoast(coastId = 1, language = "German")
        flashcards.addFlashcard(1, "Hello", "Hallo", "Greetings")
        val viewModel = createViewModel()
        viewModel.onImportFileRead("Hi,Hi,Greetings\nBread,Brot,Food\nCheese,Käse,Food\n")
        var result: ImportResult? = null
        viewModel.confirmImport { result = it }

        viewModel.undoImport(result!!)

        assertEquals(listOf("Hello"), flashcards.cards.value.map { it.nativeText })
        assertEquals(listOf("Greetings"), flashcards.islands.value.map { it.name })
    }

    @Test
    fun fileWithoutCardsShowsAMessage() = runTest {
        flashcards.seedCoast(coastId = 1, language = "German")
        val viewModel = createViewModel()

        viewModel.onImportFileRead("just one column\n")

        assertNull(viewModel.preview)
        assertNotNull(viewModel.uiState.value.message)
        viewModel.messageShown()
        assertNull(viewModel.uiState.value.message)
    }

    @Test
    fun importNeedsACoast() = runTest {
        val viewModel = createViewModel()

        viewModel.onImportFileRead("Hello,Hallo\n")

        assertNull(viewModel.preview)
        assertEquals("Start a coast first, then import cards into it.", viewModel.uiState.value.message)
    }
}

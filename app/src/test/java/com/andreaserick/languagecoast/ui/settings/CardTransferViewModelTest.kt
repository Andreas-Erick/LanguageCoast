package com.andreaserick.languagecoast.ui.settings

import com.andreaserick.languagecoast.data.AnkiNote
import com.andreaserick.languagecoast.data.ExportFormat
import com.andreaserick.languagecoast.data.ankiNote
import com.andreaserick.languagecoast.testing.FakeFlashcardRepository
import com.andreaserick.languagecoast.testing.FakeSettingsRepository
import com.andreaserick.languagecoast.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.update
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
        assertEquals("language-coast-haitian-creole.csv", viewModel.exportFileName(1, ExportFormat.Csv))
        assertEquals("language-coast.md", viewModel.exportFileName(null, ExportFormat.Markdown))
    }

    @Test
    fun aCoastExportedAsCsvImportsBackWithItsHeaderRowLeftOut() = runTest {
        flashcards.seedCoast(coastId = 1, language = "German")
        flashcards.seed(islandId = 1, cardCount = 2, coastId = 1)
        flashcards.cards.update { all -> all.map { if (it.cardId == 1) it.copy(alternatives = listOf("a1", "a2"), note = "a2 is formal.") else it } }
        flashcards.seedCoast(coastId = 2, language = "Icelandic")
        flashcards.seed(islandId = 2, cardCount = 1, coastId = 2)
        val viewModel = createViewModel()

        val export = viewModel.buildExport(coastId = 1, format = ExportFormat.Csv)

        assertEquals(2, export.cardCount)
        assertEquals(
            listOf("English,German,Island,Alternatives,Note", "n1,t1,Island 1,a1 / a2,a2 is formal.", "n2,t2,Island 1,,", ""),
            export.text.removePrefix("\uFEFF").split("\r\n")
        )

        // Into the other coast, which has "n1 / t1" already.
        settings.activeCoastId.value = 2
        viewModel.onImportFileRead(export.text)
        assertEquals(true, viewModel.preview?.skipFirstRow)
        var result: ImportResult? = null
        viewModel.confirmImport { result = it }

        assertEquals(1, result?.duplicates)
        assertEquals(listOf("n2"), result?.added?.map { it.card.nativeText })
        assertEquals(4, viewModel.buildExport(coastId = null, format = ExportFormat.Csv).cardCount)
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
        assertTrue(viewModel.preview!!.mayHaveHeader)
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
    fun theNotesOfAnAnkiPackageAreImportedOntoIslandsNamedAfterTheirDecks() = runTest {
        flashcards.seedCoast(coastId = 1, language = "German")
        flashcards.addFlashcard(1, "Good morning", "Guten Morgen", "Greetings")
        val viewModel = createViewModel()

        viewModel.onAnkiPackageRead(
            listOf(
                // A first note that reads like a header row is still a card: a package has no header.
                ankiNote("English\u001FGerman", "Languages"),
                ankiNote("Good morning\u001FGuten Morgen", "German::Greetings"),
                ankiNote("<b>bread</b>\u001F[sound:brot.mp3]\u001Fdas Brot", "German\u001FFood"),
                ankiNote("water\u001Fdas Wasser<br><br>Also: Wasser", "Default"),
                ankiNote("only one side\u001F", "German::Food")
            )
        )

        val preview = viewModel.preview!!
        assertFalse(preview.skipFirstRow)
        assertFalse(preview.mayHaveHeader)
        assertEquals(4, preview.cardsToImport.size)
        var result: ImportResult? = null
        viewModel.confirmImport { result = it }

        assertEquals(1, result?.duplicates)
        assertEquals(listOf("English", "bread", "water"), result?.added?.map { it.card.nativeText })
        assertEquals(listOf("Greetings", "Languages", "Food", DEFAULT_IMPORT_ISLAND), flashcards.islands.value.map { it.name })
        val water = flashcards.cards.value.single { it.nativeText == "water" }
        assertEquals("das Wasser" to listOf("Wasser"), water.targetText to water.alternatives)
    }

    @Test
    fun aPackageWithoutUsableNotesOrOneThatCannotBeReadSaysSo() = runTest {
        flashcards.seedCoast(coastId = 1, language = "German")
        val viewModel = createViewModel()

        viewModel.onAnkiPackageRead(listOf(ankiNote("Please update to the latest Anki version.\u001F", "Default")))
        assertNull(viewModel.preview)
        assertTrue("No cards found in that package" in viewModel.uiState.value.message.orEmpty())

        viewModel.messageShown()
        viewModel.onImportFileUnreadable("That Anki package couldn't be read.")
        assertEquals("That Anki package couldn't be read.", viewModel.uiState.value.message)

        viewModel.messageShown()
        viewModel.onImportFileUnreadable()
        assertEquals("That file couldn't be read.", viewModel.uiState.value.message)
    }

    @Test
    fun aPackedOrBinaryFileIsNotReadAsText() = runTest {
        flashcards.seedCoast(coastId = 1, language = "German")
        val viewModel = createViewModel()

        // What a zip looks like read as text: enough commas and line breaks for rows of garbage.
        viewModel.onImportFileRead("PK\u0003\u0004\u0014\u0000collection.anki2,x\ny,z\n")

        assertNull(viewModel.preview)
        assertTrue("packed, not text" in viewModel.uiState.value.message.orEmpty())

        viewModel.messageShown()
        viewModel.onImportFileRead("SQLite format 3\u0000,a\nb,c\n")
        assertNull(viewModel.preview)
        assertTrue("isn't plain text" in viewModel.uiState.value.message.orEmpty())
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

    @Test
    fun importedAlternativesAreStoredWithTheCard() = runTest {
        flashcards.seedCoast(coastId = 1, language = "German")
        val viewModel = createViewModel()
        viewModel.onImportFileRead("\"Do you want to come?\"\t\"Möchtest du kommen?\n\nAlso: Willst du kommen?\nNote: Casual.\"\n")

        viewModel.confirmImport {}

        val card = flashcards.cards.value.single()
        assertEquals("Möchtest du kommen?", card.targetText)
        assertEquals(listOf("Willst du kommen?"), card.alternatives)
        assertEquals("Casual.", card.note)
    }

    @Test
    fun swappingSidesDropsAlternatives() = runTest {
        flashcards.seedCoast(coastId = 1, language = "German")
        val viewModel = createViewModel()
        viewModel.onImportFileRead("\"Möchtest du kommen?\n\nAlso: Willst du kommen?\"\t\"Do you want to come?\"\n")

        viewModel.setSwapColumns(true)
        viewModel.confirmImport {}

        assertTrue(flashcards.cards.value.single().alternatives.isEmpty())
    }

    @Test
    fun importingADeckAgainGivesItsCrampedCardsTheirLineBreaks() = runTest {
        flashcards.seedCoast(coastId = 1, language = "German")
        // As imported before imports kept line breaks, and reviewed since.
        val old = flashcards.addFlashcard(1, "dog", "der Hund pl. die Hunde", "Animals").card
        flashcards.cards.update { cards -> cards.map { it.copy(lapses = 3) } }
        flashcards.addFlashcard(1, "cat", "die Katze", "Animals")
        val viewModel = createViewModel()

        viewModel.onAnkiPackageRead(
            listOf(
                AnkiNote(listOf("dog", "der Hund<br>pl. die Hunde"), "German::Animals"),
                AnkiNote(listOf("dog", "der Hund<br>pl. die Hunde"), "German::Animals"),
                AnkiNote(listOf("cat", "die Katze"), "German::Animals"),
                AnkiNote(listOf("bird", "der Vogel<br>pl. die Vögel"), "German::Animals")
            )
        )
        var result: ImportResult? = null
        viewModel.confirmImport { result = it }

        assertEquals(3, result?.duplicates)
        assertEquals(1, result?.reformatted)
        assertEquals(listOf("bird"), result?.added?.map { it.card.nativeText })
        val dog = flashcards.cards.value.single { it.nativeText == "dog" }
        assertEquals("der Hund\npl. die Hunde", dog.targetText)
        // The same card, with its reviews.
        assertEquals(old.cardId to 3, dog.cardId to dog.lapses)
        assertEquals("die Katze", flashcards.cards.value.single { it.nativeText == "cat" }.targetText)
    }

    @Test
    fun aCardThatHasItsLinesAlreadyIsOnlySkipped() = runTest {
        flashcards.seedCoast(coastId = 1, language = "German")
        val viewModel = createViewModel()
        val notes = listOf(AnkiNote(listOf("dog", "der Hund<br>pl. die Hunde"), "German::Animals"))

        viewModel.onAnkiPackageRead(notes)
        var first: ImportResult? = null
        viewModel.confirmImport { first = it }
        assertEquals(1 to 0, first?.added?.size to first?.reformatted)
        val imported = flashcards.cards.value.single()
        assertEquals("der Hund\npl. die Hunde", imported.targetText)

        viewModel.onAnkiPackageRead(notes)
        var second: ImportResult? = null
        viewModel.confirmImport { second = it }

        assertEquals(Triple(0, 1, 0), Triple(second?.added?.size, second?.duplicates, second?.reformatted))
        assertEquals(listOf(imported), flashcards.cards.value)
    }

    @Test
    fun aCardGivenItsLineBreaksKeepsItsAlternativesNoteAndIsland() = runTest {
        flashcards.seedCoast(coastId = 1, language = "German")
        val old = flashcards.addFlashcard(1, "dog", "der Hund pl. die Hunde", "Pets", alternatives = listOf("der Köter"), note = "Köter is rude.").card
        val viewModel = createViewModel()

        // The deck files the card somewhere else by now; the card stays where the user has it.
        viewModel.onAnkiPackageRead(listOf(AnkiNote(listOf("dog", "der Hund<br>pl. die Hunde"), "German::Animals")))
        var result: ImportResult? = null
        viewModel.confirmImport { result = it }

        assertEquals(1, result?.reformatted)
        val dog = flashcards.cards.value.single()
        assertEquals("der Hund\npl. die Hunde", dog.targetText)
        assertEquals(Triple(listOf("der Köter"), "Köter is rude.", old.islandId), Triple(dog.alternatives, dog.note, dog.islandId))
        assertEquals(listOf("Pets"), flashcards.islands.value.map { it.name })
    }

    @Test
    fun aDeckImportedWithItsSidesSwappedGetsItsLineBreaksTheSameWay() = runTest {
        flashcards.seedCoast(coastId = 1, language = "German")
        flashcards.addFlashcard(1, "dog (animal)", "der Hund", "Animals")
        val viewModel = createViewModel()

        // The deck has German on the front.
        viewModel.onAnkiPackageRead(listOf(AnkiNote(listOf("der Hund", "dog<br>(animal)"), "German::Animals")))
        viewModel.setSwapColumns(true)
        var result: ImportResult? = null
        viewModel.confirmImport { result = it }

        assertEquals(1 to 1, result?.duplicates to result?.reformatted)
        assertEquals("dog\n(animal)" to "der Hund", flashcards.cards.value.single().let { it.nativeText to it.targetText })
    }

    @Test
    fun undoingAnImportTakesBackItsNewCardsAndLeavesTheLineBreaksItGave() = runTest {
        flashcards.seedCoast(coastId = 1, language = "German")
        flashcards.addFlashcard(1, "dog", "der Hund pl. die Hunde", "Animals")
        val viewModel = createViewModel()

        viewModel.onAnkiPackageRead(
            listOf(
                AnkiNote(listOf("dog", "der Hund<br>pl. die Hunde"), "German::Animals"),
                AnkiNote(listOf("bird", "der Vogel"), "German::Birds")
            )
        )
        var result: ImportResult? = null
        viewModel.confirmImport { result = it }
        viewModel.undoImport(result!!)

        assertEquals(listOf("dog" to "der Hund\npl. die Hunde"), flashcards.cards.value.map { it.nativeText to it.targetText })
        assertEquals(listOf("Animals"), flashcards.islands.value.map { it.name })
    }

    @Test
    fun aCardWithOtherWordsIsNotTakenForTheSameCard() = runTest {
        flashcards.seedCoast(coastId = 1, language = "German")
        flashcards.addFlashcard(1, "dog", "der Hund", "Animals")
        val viewModel = createViewModel()

        viewModel.onAnkiPackageRead(listOf(AnkiNote(listOf("dog", "der Hund<br>pl. die Hunde"), "German::Animals")))
        var result: ImportResult? = null
        viewModel.confirmImport { result = it }

        assertEquals(Triple(1, 0, 0), Triple(result?.added?.size, result?.duplicates, result?.reformatted))
        assertEquals(listOf("der Hund", "der Hund\npl. die Hunde"), flashcards.cards.value.map { it.targetText })
    }

    @Test
    fun aTextFileWithLinesInItsFieldsIsPreviewedAndImportedWithThem() = runTest {
        flashcards.seedCoast(coastId = 1, language = "German")
        val viewModel = createViewModel()

        viewModel.onImportFileRead("#separator:tab\n#html:true\ndog\t<div>der Hund</div><div>pl. die Hunde</div>\nto run\t1. laufen 2. rennen 3. leiten\n")

        assertEquals(listOf("der Hund\npl. die Hunde", "1. laufen\n2. rennen\n3. leiten"), viewModel.preview?.cardsToImport?.map { it.targetText })
        viewModel.confirmImport {}
        assertEquals(listOf("der Hund\npl. die Hunde", "1. laufen\n2. rennen\n3. leiten"), flashcards.cards.value.map { it.targetText })
    }
}

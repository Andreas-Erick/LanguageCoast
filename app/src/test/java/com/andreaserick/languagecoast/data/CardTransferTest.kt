package com.andreaserick.languagecoast.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CardTransferTest {

    private val german = CoastContent(
        coast = Coast(coastId = 1, language = "German"),
        islands = listOf(
            LanguageIsland(islandId = 1, coastId = 1, name = "Greetings", creationDate = 1, emoji = "👋"),
            LanguageIsland(islandId = 2, coastId = 1, name = "Small Talk", creationDate = 2, emoji = "💬"),
            LanguageIsland(islandId = 3, coastId = 1, name = "Empty", creationDate = 3)
        ),
        cards = listOf(
            Flashcard(cardId = 1, islandId = 1, nativeText = "Good morning", targetText = "Guten Morgen"),
            Flashcard(cardId = 2, islandId = 2, nativeText = "How are you?", targetText = "Wie geht's?"),
            Flashcard(cardId = 3, islandId = 1, nativeText = "Say \"hi\"\tloudly", targetText = "Sag \"hallo\"")
        )
    )

    @Test
    fun ankiExportHasHeadersAndOneSubdeckPerIsland() {
        val lines = exportCards(listOf(german), ExportFormat.Anki, "English").lines()

        assertEquals("#separator:tab", lines[0])
        assertTrue("#deck column:3" in lines)
        assertTrue("Good morning\tGuten Morgen\tGerman Coast::Greetings" in lines)
        assertTrue("How are you?\tWie geht's?\tGerman Coast::Small Talk" in lines)
        // Fields with tabs or quotes are quoted, with quotes doubled.
        assertTrue("\"Say \"\"hi\"\"\tloudly\"\t\"Sag \"\"hallo\"\"\"\tGerman Coast::Greetings" in lines)
    }

    @Test
    fun ankiExportReadsBackIntoTheSameCardsAndIslands() {
        val imported = parseCardFile(exportCards(listOf(german), ExportFormat.Anki, "English"))

        assertEquals(
            listOf(
                ImportedCard("Good morning", "Guten Morgen", "Greetings"),
                ImportedCard("Say \"hi\"\tloudly", "Sag \"hallo\"", "Greetings"),
                ImportedCard("How are you?", "Wie geht's?", "Small Talk")
            ),
            imported
        )
    }

    @Test
    fun markdownExportHasATablePerIslandAndSkipsEmptyIslands() {
        val markdown = exportCards(listOf(german), ExportFormat.Markdown, "English")

        assertTrue(markdown.startsWith("# German Coast\n"))
        assertTrue("## 👋 Greetings" in markdown)
        assertTrue("| English | German |" in markdown)
        assertTrue("| Good morning | Guten Morgen |" in markdown)
        assertTrue("Empty" !in markdown)
    }

    @Test
    fun markdownEscapesPipesAndLineBreaks() {
        val content = german.copy(cards = listOf(Flashcard(cardId = 1, islandId = 1, nativeText = "a | b", targetText = "line\nbreak")))

        assertTrue("| a \\| b | line<br>break |" in exportCards(listOf(content), ExportFormat.Markdown, "English"))
    }

    @Test
    fun plainCsvUsesAThirdColumnAsTheIsland() {
        val cards = parseCardFile("Hello,Hallo,Greetings\n\"Yes, please\",\"Ja, bitte\"\n")

        assertEquals(
            listOf(ImportedCard("Hello", "Hallo", "Greetings"), ImportedCard("Yes, please", "Ja, bitte", null)),
            cards
        )
    }

    @Test
    fun semicolonSeparatedSpreadsheetExportsAreDetected() {
        assertEquals(listOf(ImportedCard("Hello", "Hallo", null)), parseCardFile("Hello;Hallo\r\n"))
    }

    @Test
    fun ankiNotesExportSkipsTagsAndStripsHtml() {
        val file = "#separator:tab\n#html:true\n#tags column:3\nGood&nbsp;<b>morning</b>\tGuten<br>Morgen\tgreeting verb\n"

        assertEquals(listOf(ImportedCard("Good morning", "Guten Morgen", null)), parseCardFile(file))
    }

    @Test
    fun metadataColumnsBeforeTheFieldsAreSkipped() {
        val file = "#separator:tab\n#notetype column:1\n#deck column:2\nBasic\tSpanish::Food\tBread\tPan\n"

        assertEquals(listOf(ImportedCard("Bread", "Pan", "Food")), parseCardFile(file))
    }

    @Test
    fun quotedFieldsMayContainLineBreaks() {
        assertEquals(listOf(ImportedCard("two\nlines", "zwei", null)), parseCardFile("\"two\nlines\",zwei\n"))
    }

    @Test
    fun rowsMissingASideAndBlankLinesAreSkipped() {
        val cards = parseCardFile("﻿Hello,Hallo\n\nonly one side\n,Hallo\n")

        assertEquals(listOf(ImportedCard("Hello", "Hallo", null)), cards)
    }

    private val withAlternatives = german.copy(
        cards = listOf(
            Flashcard(
                cardId = 1, islandId = 1, nativeText = "Do you want to come?", targetText = "Möchtest du kommen?",
                alternatives = listOf("Willst du kommen?", "Möchten Sie kommen?"), note = "Sie is formal."
            )
        )
    )

    @Test
    fun alternativesAndNoteGoOnTheBackAndComeBackOnImport() {
        val anki = exportCards(listOf(withAlternatives), ExportFormat.Anki, "English")

        assertTrue("\"Möchtest du kommen?\n\nAlso: Willst du kommen? / Möchten Sie kommen?\nNote: Sie is formal.\"" in anki)
        assertEquals(
            listOf(
                ImportedCard(
                    "Do you want to come?", "Möchtest du kommen?", "Greetings",
                    alternatives = listOf("Willst du kommen?", "Möchten Sie kommen?"), note = "Sie is formal."
                )
            ),
            parseCardFile(anki)
        )
    }

    @Test
    fun alternativesSurviveAnAnkiExportWithHtmlLineBreaks() {
        val file = "#separator:tab\n#html:true\nDo you want to come?\tMöchtest du kommen?<br><br>Also: Willst du kommen?<br>Note: Casual.\n"

        val card = parseCardFile(file).single()

        assertEquals("Möchtest du kommen?", card.targetText)
        assertEquals(listOf("Willst du kommen?"), card.alternatives)
        assertEquals("Casual.", card.note)
    }

    @Test
    fun markdownShowsAlternativesUnderTheTranslation() {
        val markdown = exportCards(listOf(withAlternatives), ExportFormat.Markdown, "English")

        assertTrue(
            "| Do you want to come? | Möchtest du kommen?<br>Also: Willst du kommen? / Möchten Sie kommen?<br>Note: Sie is formal. |" in markdown
        )
    }
}

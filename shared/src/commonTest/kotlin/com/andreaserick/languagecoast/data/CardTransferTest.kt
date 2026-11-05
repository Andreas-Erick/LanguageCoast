package com.andreaserick.languagecoast.data

import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.Test

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
    fun csvExportHasAHeaderRowAndAColumnForEverythingOnACard() {
        val csv = exportCards(listOf(withAlternatives), ExportFormat.Csv, "English")

        // The byte order mark tells Excel the file is UTF-8; rows end as RFC 4180 asks.
        assertTrue(csv.startsWith("\uFEFFEnglish,German,Island,Alternatives,Note\r\n"))
        assertEquals(
            "Do you want to come?,Möchtest du kommen?,Greetings,Willst du kommen? / Möchten Sie kommen?,Sie is formal.",
            csv.removePrefix("\uFEFF").split("\r\n")[1]
        )
        assertTrue(csv.endsWith("\r\n"))
    }

    @Test
    fun csvExportQuotesWhatWouldBreakARowAndSkipsEmptyIslands() {
        val tricky = german.copy(
            cards = german.cards + Flashcard(cardId = 4, islandId = 2, nativeText = "Yes, please", targetText = "Ja,\nbitte ", note = "A note without alternatives")
        )

        val rows = exportCards(listOf(tricky), ExportFormat.Csv, "English").removePrefix("\uFEFF").split("\r\n")

        assertEquals(
            listOf(
                "English,German,Island,Alternatives,Note",
                "Good morning,Guten Morgen,Greetings,,",
                "\"Say \"\"hi\"\"\tloudly\",\"Sag \"\"hallo\"\"\",Greetings,,",
                "How are you?,Wie geht's?,Small Talk,,",
                "\"Yes, please\",\"Ja,\nbitte \",Small Talk,,A note without alternatives",
                ""
            ),
            rows
        )
    }

    @Test
    fun csvExportOfSeveralCoastsSaysWhichLanguageARowIsIn() {
        val icelandic = CoastContent(
            coast = Coast(coastId = 2, language = "Icelandic"),
            islands = listOf(LanguageIsland(islandId = 9, coastId = 2, name = "Animals", creationDate = 1)),
            cards = listOf(Flashcard(cardId = 9, islandId = 9, nativeText = "horse", targetText = "hestur, hesturinn, hestar"))
        )

        val rows = exportCards(listOf(withAlternatives, icelandic), ExportFormat.Csv, "English").removePrefix("\uFEFF").split("\r\n")

        assertEquals("English,Translation,Island,Alternatives,Note,Language", rows[0])
        assertTrue(rows[1].endsWith(",Sie is formal.,German"))
        assertEquals("horse,\"hestur, hesturinn, hestar\",Animals,,,Icelandic", rows[2])
    }

    @Test
    fun csvExportReadsBackIntoTheSameCards() {
        val content = german.copy(cards = german.cards + withAlternatives.cards.single().copy(cardId = 4, islandId = 2))

        val imported = parseCardFile(exportCards(listOf(content), ExportFormat.Csv, "English"))

        assertEquals(
            listOf(
                // The header row comes back as a row; the import's "First row is a header" switch leaves it out.
                ImportedCard("English", "German", "Island"),
                ImportedCard("Good morning", "Guten Morgen", "Greetings"),
                ImportedCard("Say \"hi\"\tloudly", "Sag \"hallo\"", "Greetings"),
                ImportedCard("How are you?", "Wie geht's?", "Small Talk"),
                ImportedCard(
                    "Do you want to come?", "Möchtest du kommen?", "Small Talk",
                    alternatives = listOf("Willst du kommen?", "Möchten Sie kommen?"), note = "Sie is formal."
                )
            ),
            imported
        )
    }

    @Test
    fun aFourthColumnIsOnlyReadAsAlternativesWhenTheFileSaysSo() {
        // Somebody else's spreadsheet: its fourth and fifth columns could hold anything.
        assertEquals(
            listOf(ImportedCard("Hello", "Hallo", "Greetings")),
            parseCardFile("Hello,Hallo,Greetings,tag1 / tag2,seen in a film")
        )
        // Its columns titled as this app titles them, in any case and with a semicolon as the separator.
        assertEquals(
            listOf(
                ImportedCard("Front", "Back", "Deck"),
                ImportedCard("Hello", "Hallo", "Greetings", alternatives = listOf("Servus", "Grüß Gott"), note = "Both are southern.")
            ),
            parseCardFile("Front;Back;Deck;alternatives;NOTE\nHello;Hallo;Greetings;Servus / Grüß Gott;Both are southern.")
        )
    }

    @Test
    fun textInAnyScriptCountsAsTextWhateverItsLineEndings() {
        assertEquals(ImportFileKind.Text, importFileKind("Hello,Hallo\r\nCheese;Käse\tFood\n"))
        assertEquals(ImportFileKind.Text, importFileKind("\uFEFF#separator:tab\nGood morning\tおはよう\n"))
        assertEquals(ImportFileKind.Text, importFileKind(exportCards(listOf(german), ExportFormat.Csv, "English")))
        assertEquals(ImportFileKind.Text, importFileKind(""))
        // A file saved in another 8-bit encoding has its umlauts replaced, but is still text.
        assertEquals(ImportFileKind.Text, importFileKind("K\uFFFDse,cheese\nT\uFFFDr,door\n"))
    }

    @Test
    fun anAnkiPackageOrAnotherBinaryFileIsNotTakenForText() {
        // An .apkg is a zip: it starts with "PK", and what follows has commas and line breaks in it like a CSV.
        val apkg = "PK\u0003\u0004\u0014\u0000\u0000\u0000\bcollection.anki2,x\uFFFD\ny,\u0001z\n"
        assertEquals(ImportFileKind.Archive, importFileKind(apkg))
        assertTrue(parseCardFile(apkg).isNotEmpty())

        // An SQLite database, a picture: no zip, but full of control characters.
        assertEquals(ImportFileKind.Binary, importFileKind("SQLite format 3\u0000\u0010\u0000\u0001\u0001,a\nb,c"))
        assertEquals(ImportFileKind.Binary, importFileKind("\u0001\u0002,\u0003\n\u0004,\u0005 some text"))
        // UTF-16, as Excel's "Unicode Text" writes it, reads as letters with NULs between them.
        assertEquals(ImportFileKind.Binary, importFileKind("H\u0000e\u0000l\u0000l\u0000o\u0000,\u0000H\u0000a\u0000l\u0000l\u0000o\u0000"))
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

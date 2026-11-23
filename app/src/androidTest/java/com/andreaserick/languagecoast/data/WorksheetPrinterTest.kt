package com.andreaserick.languagecoast.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Draws practice sheets on a device and reads the PDFs back with Android's own PDF reader.
 *
 * To look at the sheets, run with `-e keepPdfs true`: they then stay in the app's cache, under `shared/`.
 */
@RunWith(AndroidJUnit4::class)
class WorksheetPrinterTest {

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val printer = AndroidWorksheetPrinter(context)
    private val made = mutableListOf<File>()

    @After
    fun tearDown() {
        if (InstrumentationRegistry.getArguments().getString("keepPdfs") != "true") made.forEach { it.delete() }
    }

    private fun sheet(
        sentences: List<WorksheetSentence>,
        coast: String = "German",
        options: WorksheetOptions = WorksheetOptions(count = sentences.size)
    ) = Worksheet(
        coastName = "$coast Coast",
        targetLanguage = coast,
        nativeLanguage = "English",
        options = options,
        sentences = sentences,
        date = LocalDate(2026, 11, 5),
        providerLabel = "Gemini",
        cardCount = 70,
        islandCount = 7
    )

    /**
     * Prints [worksheet] and returns the file with the pages Android's PDF reader finds in it. The reader is
     * the one PDF viewers on a phone are built on, so the first and the last page are also drawn with it, to
     * see that there is something on them.
     */
    private fun print(worksheet: Worksheet): Pair<WorksheetPdf, Int> = runBlocking {
        val pdf = checkNotNull(printer.print(worksheet)) { "No PDF was made" }
        val file = File(pdf.path).also { made += it }
        assertTrue(file.length() > 1_000)
        val pages = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
            PdfRenderer(descriptor).use { reader ->
                listOf(0, reader.pageCount - 1).forEach { index ->
                    reader.openPage(index).use { page ->
                        val picture = Bitmap.createBitmap(page.width / 2, page.height / 2, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
                        page.render(picture, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        val pixels = IntArray(picture.width * picture.height).also { picture.getPixels(it, 0, picture.width, 0, 0, picture.width, picture.height) }
                        val inked = pixels.count { it != Color.WHITE }
                        assertTrue("Page ${index + 1} of ${pdf.fileName} came out blank", inked > pixels.size / 200)
                    }
                }
                reader.pageCount
            }
        }
        pdf to pages
    }

    @Test
    fun tenShortSentencesFitOnePageWithTheAnswersOnTheNext() {
        // As a sheet comes out of the app: every sentence built from a card, so the first page says which islands
        // the sheet practises and every answer has its card under it.
        val (pdf, pages) = print(sheet(FROM_CARDS.map { it.copy(newWords = emptyList(), hint = null) }))

        assertEquals("german-coast-practice-2026-11-05.pdf", pdf.fileName)
        assertEquals(File(context.cacheDir, "shared"), File(pdf.path).parentFile)
        assertEquals(2, pdf.pageCount)
        assertEquals(2, pages)
    }

    @Test
    fun aSheetBuiltFromCardsPrintsItsWordHelpItsHintsAndTheCards() {
        // Dated another day than the plain sheet, so that with keepPdfs every file stays to be looked at.
        val written = sheet(FROM_CARDS).copy(date = LocalDate(2026, 11, 6))
        assertEquals("Built from your cards on Small Talk (5), Directions (2), Restaurant (2) and Making Plans (1).", written.focusLine)
        assertEquals("Can you help me, please? (formal)", written.question(FROM_CARDS[3]))
        assertEquals("wohnen = to live · Zürich = Zurich", written.wordHelp(FROM_CARDS[4]))

        // Word help under a sentence and the card under every answer take room: how many pages that makes depends on the paper.
        val (pdf, pages) = print(written)
        assertTrue("A sheet and its answer key are two pages at least, got ${pdf.pageCount}", pdf.pageCount >= 2)
        assertEquals(pdf.pageCount, pages)

        // Read the other way, the sentences are the German ones: the word help stands under them, and no hint is needed.
        val read = written.copy(options = written.options.copy(direction = WorksheetDirection.ToNative), date = LocalDate(2026, 11, 7))
        assertEquals("Können Sie mir bitte helfen?", read.question(FROM_CARDS[3]))
        val (readPdf, readPages) = print(read)
        assertTrue(readPdf.pageCount >= 2)
        assertEquals(readPdf.pageCount, readPages)
    }

    @Test
    fun theLongestSheetRunsOverManyPagesAndSaysHowMany() {
        val most = WORKSHEET_COUNT_RANGE.last
        val long = (1..most).map { EVERYDAY_LONG[it % EVERYDAY_LONG.size].let { s -> s.copy(targetText = "${s.targetText} ($it)") } }
        val (pdf, pages) = print(
            sheet(long, coast = "Icelandic", options = WorksheetOptions(count = most, difficulty = WorksheetDifficulty.Hard, direction = WorksheetDirection.ToNative))
        )

        assertTrue("$most long sentences need dozens of pages, got ${pdf.pageCount}", pdf.pageCount > 50)
        assertEquals(pdf.pageCount, pages)
    }

    @Test
    fun otherScriptsAndASingleSentenceArePrintedToo() {
        val scripts = listOf(
            WorksheetSentence("أين محطة القطار؟", "Where is the train station?"),
            WorksheetSentence("我想要一杯咖啡，谢谢。", "I would like a cup of coffee, thank you."),
            WorksheetSentence("Где находится ближайшая аптека?", "Where is the nearest pharmacy?"),
            WorksheetSentence("Πού είναι η παραλία;", "Where is the beach?"),
            WorksheetSentence("Tôi muốn một ly cà phê sữa đá.", "I would like an iced coffee with milk."),
            WorksheetSentence("駅はどこですか。", "Where is the station?")
        )
        val (many, manyPages) = print(sheet(scripts, coast = "Norwegian (Bokmål) and many more scripts", options = WorksheetOptions(count = 6, direction = WorksheetDirection.ToNative)))
        assertEquals(2 to 2, many.pageCount to manyPages)

        val (one, onePages) = print(sheet(EVERYDAY.take(1), coast = "Latin", options = WorksheetOptions(count = 3, difficulty = WorksheetDifficulty.Easy)))
        assertEquals(2 to 2, one.pageCount to onePages)
    }

    /** A custom sheet of [exercises], as the generator hands it over. */
    private fun customSheet(exercises: List<Exercise>, brief: String = "Plural from the singular, and the dative after mit") = Worksheet(
        coastName = "German Coast",
        targetLanguage = "German",
        nativeLanguage = "English",
        options = WorksheetOptions(count = exercises.size, kind = SheetKind.Custom, brief = brief),
        sentences = emptyList(),
        date = LocalDate(2026, 11, 8),
        providerLabel = "Claude",
        cardCount = 70,
        islandCount = 7,
        exercises = exercises
    )

    @Test
    fun aShortCustomSheetIsOnePageOfExercisesAndOneOfAnswers() {
        val sheet = customSheet(SHORT_ANSWERS.take(3))
        val (pdf, pages) = print(sheet)

        assertEquals("german-coast-custom-practice-2026-11-08.pdf", pdf.fileName)
        // Three exercises fit one page with room to spare: the second page is there because the answers start one of their own.
        assertEquals(2 to 2, pdf.pageCount to pages)
    }

    @Test
    fun aCustomSheetPrintsAnswerBoxesAsTallAsItsAnswersOverAsManyPagesAsTheyTake() {
        // Ten exercises, two instructions, one a conjugation table: grouped and numbered as customSheetLayout says.
        val ten = customSheet(SHORT_ANSWERS).copy(date = LocalDate(2026, 11, 9))
        assertEquals(listOf("Write the plural.", null, null, null, null, null, "Put it in the dative.", null, null, "Conjugate in the present tense."),
            customSheetLayout(ten.exercises).map { it.instruction })
        val (pdf, pages) = print(ten)
        assertTrue("A sheet and its answer key are two pages at least, got ${pdf.pageCount}", pdf.pageCount >= 2)
        assertEquals(pdf.pageCount, pages)

        // The longest sheet, with a long brief that is shortened on the first page.
        val most = WORKSHEET_COUNT_RANGE.last
        val long = customSheet((1..most).map { SHORT_ANSWERS[it % SHORT_ANSWERS.size].let { e -> e.copy(task = "${e.task} ($it)") } }, brief = "Plural ".repeat(60))
            .copy(date = LocalDate(2026, 11, 10))
        val (longPdf, longPages) = print(long)
        assertTrue("$most exercises need dozens of pages, got ${longPdf.pageCount}", longPdf.pageCount > 20)
        assertEquals(longPdf.pageCount, longPages)
    }

    @Test
    fun multipleChoicePrintsBetweenGapFillsAndShortAnswersWithItsOptionsOnOneLineAndItsLetterInTheKey() {
        val mixed = customSheet(SHORT_ANSWERS.take(3) + CHOICES + GAP_FILLS.take(2), brief = "Articles after prepositions, and plurals")
            .copy(date = LocalDate(2026, 11, 12))
        val rows = customSheetLayout(mixed.exercises)
        assertEquals(
            listOf(ExerciseForm.GapFill, ExerciseForm.GapFill, ExerciseForm.MultipleChoice, ExerciseForm.MultipleChoice, ExerciseForm.MultipleChoice, ExerciseForm.ShortAnswer),
            rows.take(6).map { it.exercise.form }
        )
        assertEquals("a) den     b) dem     c) der", choiceLine(rows[2].exercise.options))
        assertEquals(listOf("dem Zug", "dem alten Mann", "b) dem", "a) der", "c) dem", "die Hunde"), rows.take(6).map { keyAnswer(it.exercise) })

        // Eight exercises fit the first page; the key starts the second.
        val (pdf, pages) = print(mixed)
        assertEquals(2 to 2, pdf.pageCount to pages)
    }

    @Test
    fun gapFillsAreWrittenInTheirGapSoAMixedSheetOfTwelveFitsOnePageBeforeItsAnswers() {
        // Gap fills given last still print first, each answered in its gap rather than in a box of its own.
        val mixed = customSheet(SHORT_ANSWERS.take(2) + GAP_FILLS, brief = "Nominative → dative").copy(date = LocalDate(2026, 11, 11))
        val rows = customSheetLayout(mixed.exercises)
        assertEquals(GAP_FILLS, rows.take(GAP_FILLS.size).map { it.exercise })
        assertEquals(listOf("Put it in the dative.", null, null, null, null, null, null, null, null, null, "Write the plural.", null), rows.map { it.instruction })

        val (pdf, pages) = print(mixed)
        assertEquals("german-coast-custom-practice-2026-11-11.pdf", pdf.fileName)
        assertEquals(2 to 2, pdf.pageCount to pages)
    }

    @Test
    fun aRunOfExercisesThatGoesOnOverAPageSaysItsInstructionAgainAtTheTopOfTheNext() {
        // Forty plurals share one instruction, printed above the first; the run carries on past the first page.
        val plurals = (1..40).map { SHORT_ANSWERS[it % 6].copy(task = "${SHORT_ANSWERS[it % 6].task} ($it)", wordHelp = emptyList()) }
        val sheet = customSheet(plurals, brief = "Plural from the singular").copy(date = LocalDate(2026, 11, 13))
        assertEquals(listOf("Write the plural.") + List(39) { null }, customSheetLayout(sheet.exercises).map { it.instruction })
        val (pdf, pages) = print(sheet)
        assertTrue("Forty exercises take more than one page, got ${pdf.pageCount}", pdf.pageCount >= 3)
        assertEquals(pdf.pageCount, pages)

        // The second page opens with the instruction in grey text, so the first exercise's sunlit number badge sits below
        // it: none of the badge's fill is in the page's first ten points, where a page without the instruction has it.
        val top = ParcelFileDescriptor.open(File(pdf.path), ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
            PdfRenderer(descriptor).use { reader ->
                reader.openPage(1).use { page ->
                    val picture = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
                    page.render(picture, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                    IntArray(picture.width * 10).also { picture.getPixels(it, 0, picture.width, 0, PAGE_MARGIN, picture.width, 10) }
                }
            }
        }
        assertTrue("The second page starts with something", top.any { it != Color.WHITE })
        assertTrue("The second page starts with a number badge, not the instruction", top.none { it == BADGE_FILL })
    }

    private companion object {
        /** The printer's page margin, in points; a page drawn one pixel a point puts its first line there. */
        const val PAGE_MARGIN = 52

        /** The pale sun fill of an exercise's number badge. */
        val BADGE_FILL = 0xFFFDE9DA.toInt()

        val CHOICES = listOf(
            Exercise(ExerciseForm.MultipleChoice, "Pick the right article.", "Ich fahre mit ___ Zug nach Wien.", "dem", listOf("den", "dem", "der")),
            Exercise(ExerciseForm.MultipleChoice, "Pick the right article.", "Sie wohnt in ___ Schweiz.", "der", listOf("der", "die", "das")),
            Exercise(ExerciseForm.MultipleChoice, "Pick the right article.", "Wir helfen ___ Kind bei den Hausaufgaben.", "dem", listOf("das", "den", "dem"))
        )

        val GAP_FILLS = listOf(
            Exercise(ExerciseForm.GapFill, "Put it in the dative.", "Ich fahre mit ___ (der Zug) nach Wien.", "dem Zug", wordHelp = listOf(WordHelp("Wien", "Vienna"))),
            Exercise(ExerciseForm.GapFill, "Put it in the dative.", "Ich gebe ___ (der alte Mann) das Buch.", "dem alten Mann"),
            Exercise(ExerciseForm.GapFill, "Put it in the dative.", "Er hilft ___ (die Frau).", "der Frau"),
            Exercise(ExerciseForm.GapFill, "Put it in the dative.", "Wir wohnen bei ___ (die Eltern).", "den Eltern"),
            Exercise(ExerciseForm.GapFill, "Put it in the dative.", "Sie dankt ___ (das Kind) für das Bild.", "dem Kind"),
            Exercise(ExerciseForm.GapFill, "Put it in the dative.", "Nach ___ (die Arbeit) gehe ich nach Hause.", "der Arbeit"),
            Exercise(ExerciseForm.GapFill, "Put it in the dative.", "Das Fahrrad gehört ___ (mein Bruder).", "meinem Bruder"),
            Exercise(ExerciseForm.GapFill, "Put it in the dative.", "Seit ___ (ein Jahr) lerne ich Deutsch.", "einem Jahr"),
            Exercise(ExerciseForm.GapFill, "Put it in the dative.", "Wir sitzen in ___ (der Garten).", "dem Garten"),
            Exercise(ExerciseForm.GapFill, "Put it in the dative.", "Ich spreche mit ___ (die Lehrerin).", "der Lehrerin")
        )

        val SHORT_ANSWERS = listOf(
            Exercise(ExerciseForm.ShortAnswer, "Write the plural.", "der Hund", "die Hunde"),
            Exercise(ExerciseForm.ShortAnswer, "Write the plural.", "die Maus", "die Mäuse", wordHelp = listOf(WordHelp("Maus", "mouse"))),
            Exercise(ExerciseForm.ShortAnswer, "Write the plural.", "das Haus", "die Häuser"),
            Exercise(ExerciseForm.ShortAnswer, "Write the plural.", "der Apfel", "die Äpfel"),
            Exercise(ExerciseForm.ShortAnswer, "Write the plural.", "das Kind", "die Kinder"),
            Exercise(ExerciseForm.ShortAnswer, "Write the plural.", "die Stadt", "die Städte"),
            Exercise(ExerciseForm.ShortAnswer, "Put it in the dative.", "Ich fahre mit (der Zug) nach Wien.", "Ich fahre mit dem Zug nach Wien."),
            Exercise(ExerciseForm.ShortAnswer, "Put it in the dative.", "Sie spricht mit (die Kinder) über den Ausflug, der morgen früh beginnt.", "Sie spricht mit den Kindern über den Ausflug, der morgen früh beginnt."),
            Exercise(ExerciseForm.ShortAnswer, "Put it in the dative.", "mit (das Auto)", "mit dem Auto"),
            Exercise(ExerciseForm.ShortAnswer, "Conjugate in the present tense.", "sein", "ich bin\ndu bist\ner ist\nwir sind\nihr seid\nsie sind")
        )

        val EVERYDAY = listOf(
            WorksheetSentence("Wo ist der nächste Bahnhof?", "Where is the nearest train station?"),
            WorksheetSentence("Ich möchte einen Kaffee, bitte.", "I would like a coffee, please."),
            WorksheetSentence("Wie lange dauert es zu Fuß?", "How long does it take on foot?"),
            WorksheetSentence("Können Sie mir bitte helfen?", "Can you help me, please?"),
            WorksheetSentence("Ich komme aus Österreich und wohne in Zürich.", "I come from Austria and live in Zurich."),
            WorksheetSentence("Das Wetter ist heute sehr schön.", "The weather is very nice today."),
            WorksheetSentence("Sprechen Sie Englisch?", "Do you speak English?"),
            WorksheetSentence("Ich verstehe das nicht.", "I don't understand that."),
            WorksheetSentence("Wo sind die Toiletten?", "Where are the toilets?"),
            WorksheetSentence("Wir treffen uns morgen um acht Uhr.", "We are meeting tomorrow at eight o'clock.")
        )

        /**
         * The same ten sentences as a sheet has them now: each built from one of the learner's cards, three with
         * words that are on no card, and two with a hint at which "you" is meant.
         */
        val FROM_CARDS = listOf(
            EVERYDAY[0].copy(card = WorksheetCard("Directions", "Wo ist der Bahnhof?", "Where is the station?"), newWords = listOf(WordHelp("nächste", "nearest"))),
            EVERYDAY[1].copy(card = WorksheetCard("Restaurant", "Einen Kaffee, bitte.", "A coffee, please.")),
            EVERYDAY[2].copy(card = WorksheetCard("Directions", "Ich gehe zu Fuß.", "I am going on foot."), newWords = listOf(WordHelp("dauern", "to take (time)"))),
            EVERYDAY[3].copy(card = WorksheetCard("Small Talk", "Kannst du mir helfen?", "Can you help me?"), hint = "formal"),
            EVERYDAY[4].copy(
                card = WorksheetCard("Small Talk", "Ich komme aus Österreich.", "I come from Austria."),
                newWords = listOf(WordHelp("wohnen", "to live"), WordHelp("Zürich", "Zurich"))
            ),
            EVERYDAY[5].copy(card = WorksheetCard("Small Talk", "Das Wetter ist schön.", "The weather is nice.")),
            EVERYDAY[6].copy(card = WorksheetCard("Small Talk", "Ich spreche ein bisschen Deutsch.", "I speak a little German."), hint = "formal"),
            EVERYDAY[7].copy(card = WorksheetCard("Small Talk", "Ich verstehe.", "I understand.")),
            EVERYDAY[8].copy(card = WorksheetCard("Restaurant", "Wo ist die Toilette?", "Where is the toilet?")),
            EVERYDAY[9].copy(card = WorksheetCard("Making Plans", "Treffen wir uns morgen?", "Shall we meet tomorrow?"))
        )

        val EVERYDAY_LONG = listOf(
            WorksheetSentence(
                "Entschuldigung, können Sie mir bitte sagen, wie lange es zu Fuß bis zum nächsten Bahnhof dauert?",
                "Excuse me, can you please tell me how long it takes on foot to the nearest train station?"
            ),
            WorksheetSentence(
                "Ich komme aus Österreich, aber ich wohne seit drei Jahren in Zürich, weil ich dort arbeite.",
                "I come from Austria, but I have lived in Zurich for three years because I work there."
            ),
            WorksheetSentence(
                "Wenn das Wetter morgen schön ist, treffen wir uns um acht Uhr am Strand und trinken zusammen einen Kaffee, und danach gehen wir gemeinsam zu Fuß in die Stadt.",
                "If the weather is nice tomorrow, we will meet at the beach at eight o'clock and have a coffee together, and afterwards we will walk into town together."
            ),
            WorksheetSentence("Sprechen Sie Englisch, oder verstehen Sie mich nicht?", "Do you speak English, or do you not understand me?")
        )
    }
}

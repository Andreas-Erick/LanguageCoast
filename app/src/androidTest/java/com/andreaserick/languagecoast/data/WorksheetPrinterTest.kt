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
        val (pdf, pages) = print(sheet(EVERYDAY))

        assertEquals("german-coast-practice-2026-11-05.pdf", pdf.fileName)
        assertEquals(File(context.cacheDir, "shared"), File(pdf.path).parentFile)
        assertEquals(2, pdf.pageCount)
        assertEquals(2, pages)
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

    private companion object {
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

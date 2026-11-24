package com.andreaserick.languagecoast.data

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.fonts.Font
import android.graphics.fonts.FontFamily
import android.graphics.fonts.FontStyle
import android.graphics.pdf.PdfDocument
import android.os.Build
import android.os.LocaleList
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.andreaserick.languagecoast.resources.Res
import java.io.File
import java.io.IOException
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.toJavaLocalDate

/**
 * Draws practice sheets with Android's own PDF writer: the sentences with lines to write on, then the
 * answer key on a page of its own. The file goes to the folder of the cache that the app's FileProvider
 * shares, so a PDF viewer can be handed it.
 */
class AndroidWorksheetPrinter(private val context: Context) : WorksheetPrinter {
    override val isAvailable = true

    override suspend fun print(worksheet: Worksheet): WorksheetPdf? = withContext(Dispatchers.IO) {
        try {
            val paper = Paper.of(Locale.getDefault())
            val pages = SheetLayout(worksheet, paper, loadFonts()).pages()
            val document = PdfDocument()
            try {
                pages.forEachIndexed { index, draw ->
                    val page = document.startPage(PdfDocument.PageInfo.Builder(paper.width, paper.height, index + 1).create())
                    draw(page.canvas)
                    document.finishPage(page)
                }
                val file = File(File(context.cacheDir, SHARED_FOLDER).apply { mkdirs() }, worksheet.fileName)
                file.outputStream().use(document::writeTo)
                WorksheetPdf(file.absolutePath, worksheet.fileName, pages.size)
            } finally {
                document.close()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            logError(TAG, "Couldn't write the practice sheet", e)
            null
        } catch (e: RuntimeException) {
            logError(TAG, "Couldn't draw the practice sheet", e)
            null
        }
    }

    /**
     * The app's typeface in the weights the sheet uses. Nunito is one variable font file among the shared
     * resources; Android wants a file to read it from, so it is copied to the cache once. Without it the
     * sheet falls back to the phone's own typeface, as it does for scripts Nunito doesn't cover (Greek,
     * Arabic, Chinese, ...). Where Android lets an app say so (10 and later), that fallback is named as the
     * phone's sans-serif in the same weight, so those letters match the rest of the sheet.
     */
    private suspend fun loadFonts(): Fonts {
        val file = File(context.cacheDir, "nunito.ttf")
        val readable = try {
            if (!file.exists() || file.length() == 0L) file.writeBytes(Res.readBytes("font/nunito.ttf"))
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logError(TAG, "Couldn't read the app's font", e)
            false
        }
        fun weight(value: Int, fallback: Typeface): Typeface {
            if (!readable) return fallback
            return runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val font = Font.Builder(file).setFontVariationSettings("'wght' $value").setWeight(value).build()
                    Typeface.CustomFallbackBuilder(FontFamily.Builder(font).build())
                        .setStyle(FontStyle(value, FontStyle.FONT_SLANT_UPRIGHT))
                        .setSystemFallback("sans-serif")
                        .build()
                } else {
                    Typeface.Builder(file).setFontVariationSettings("'wght' $value").build()
                }
            }.getOrNull() ?: fallback
        }
        return Fonts(
            medium = weight(500, Typeface.DEFAULT),
            semiBold = weight(600, Typeface.DEFAULT),
            bold = weight(700, Typeface.DEFAULT_BOLD),
            extraBold = weight(800, Typeface.DEFAULT_BOLD)
        )
    }

    private companion object {
        const val TAG = "WorksheetPrinter"

        /** The cache folder listed in the app's `file_paths.xml`. */
        const val SHARED_FOLDER = "shared"
    }
}

private class Fonts(val medium: Typeface, val semiBold: Typeface, val bold: Typeface, val extraBold: Typeface)

/** A page size in points (1/72 inch). */
private class Paper(val width: Int, val height: Int) {
    companion object {
        /** The countries that print on Letter paper; everywhere else it is A4. */
        private val LETTER_COUNTRIES = setOf("US", "CA", "MX", "PH", "CL", "CO", "VE", "GT", "CR", "PA", "DO", "PR")

        fun of(locale: Locale): Paper = if (locale.country in LETTER_COUNTRIES) Paper(612, 792) else Paper(595, 842)
    }
}

// Ink for paper: the light theme's text colours, and the sunlit accent as the one spot of colour.
private const val INK = 0xFF1F1C33.toInt()
private const val INK_SOFT = 0xFF4E4A66.toInt()
private const val WRITING_LINE = 0xFFC9C5D9.toInt()
private const val HAIRLINE = 0xFFDEDBE8.toInt()
private const val WAVE = 0xFFC4C2DC.toInt()
private const val WAVE_SOFT = 0xFFABA7CF.toInt()
private const val SUN = 0xFFF29A5B.toInt()
private const val SUN_PALE = 0xFFFDE9DA.toInt()

/** Something drawn below what came before it on a page: a sentence with its lines, an answer, a heading. */
private class Block(val height: Float, val draw: (canvas: Canvas, top: Float) -> Unit)

/**
 * Lays a [Worksheet] out on [paper]: the sentences from the first page on, each with lines to write its
 * translation on and never split over two pages, then the answer key starting on a new page. A custom
 * sheet has its exercises in their place, grouped by form as [customSheetLayout] says, each with a box to
 * write the answer in.
 */
private class SheetLayout(private val sheet: Worksheet, private val paper: Paper, private val fonts: Fonts) {
    private val margin = 52f
    private val left = margin
    private val right = paper.width - margin
    private val contentWidth = right - left

    /** Where a page's content ends: above the footer. */
    private val bottom = paper.height - margin - 20f

    /** How far text is indented past its number. */
    private val numberColumn = 30f

    private fun paint(typeface: Typeface, size: Float, color: Int, spacing: Float = 0f) =
        TextPaint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG or Paint.LINEAR_TEXT_FLAG).apply {
            this.typeface = typeface
            textSize = size
            this.color = color
            letterSpacing = spacing
        }

    private val label = paint(fonts.extraBold, 7.5f, INK_SOFT, spacing = 0.14f)
    private val coastTitle = paint(fonts.extraBold, 20f, INK)
    private val dateText = paint(fonts.semiBold, 10.5f, INK)
    private val heading = paint(fonts.extraBold, 16f, INK)
    private val chipText = paint(fonts.bold, 8.5f, INK)
    private val instruction = paint(fonts.medium, 10f, INK_SOFT)
    private val number = paint(fonts.extraBold, 8.5f, INK).apply { textAlign = Paint.Align.CENTER }

    /**
     * The languages of the sentences, for the paints that draw them: the coast's first, then the learner's,
     * then the phone's. A letter that several scripts share gets its shape from the first that writes it,
     * so Japanese kanji are drawn as kanji and not as the Chinese hanzi a phone set to English falls back to.
     */
    private val sentenceLocales = LocaleList(
        *(listOf(sheet.targetLanguage, sheet.nativeLanguage).mapNotNull { Languages.code(it)?.let(Locale::forLanguageTag) } + Locale.getDefault())
            .distinct()
            .toTypedArray()
    )

    private fun sentencePaint(typeface: Typeface, size: Float, color: Int) = paint(typeface, size, color).apply { textLocales = sentenceLocales }

    /** Three digits only fit a sentence's badge a size smaller. */
    private val smallNumber = paint(fonts.extraBold, 7f, INK).apply { textAlign = Paint.Align.CENTER }
    private val sentenceText = sentencePaint(fonts.semiBold, 12.5f, INK)
    /** A multiple-choice exercise's options: as clear as its task, a little smaller. */
    private val optionText = sentencePaint(fonts.medium, 11.5f, INK)
    private val answerNumber = paint(fonts.extraBold, 9.5f, INK_SOFT).apply { textAlign = Paint.Align.RIGHT }
    private val answerText = sentencePaint(fonts.semiBold, 11.5f, INK)
    private val answerQuestion = sentencePaint(fonts.medium, 9f, INK_SOFT)
    private val footer = paint(fonts.semiBold, 7.5f, INK_SOFT)

    /** How far an answer is indented past its number: room for the sheet's largest one. */
    private val answerIndent = maxOf(26f, answerNumber.measureText("${sheet.exerciseCount}") + 12f)

    private fun stroke(color: Int, width: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        this.color = color
        strokeWidth = width
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private fun fill(color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }

    /** [text] wrapped to [width]; a script written right to left starts from the right, unless [alignment] says otherwise. */
    private fun wrap(
        text: String,
        paint: TextPaint,
        width: Float,
        lineSpacing: Float = 1.12f,
        alignment: Layout.Alignment = Layout.Alignment.ALIGN_NORMAL
    ): StaticLayout =
        StaticLayout.Builder.obtain(text, 0, text.length, paint, ceil(width).toInt())
            .setLineSpacing(0f, lineSpacing)
            .setIncludePad(false)
            .setAlignment(alignment)
            .build()

    private val StaticLayout.isRightToLeft: Boolean get() = getParagraphDirection(0) == Layout.DIR_RIGHT_TO_LEFT

    private fun Canvas.drawLayout(layout: StaticLayout, x: Float, top: Float) {
        save()
        translate(x, top)
        layout.draw(this)
        restore()
    }

    /** Every page as a function that draws it. */
    fun pages(): List<(Canvas) -> Unit> {
        val pages = ArrayList<MutableList<Pair<Block, Float>>>()
        var cursor = 0f

        fun newPage() {
            pages += ArrayList<Pair<Block, Float>>()
            cursor = margin
        }

        /** Whether [block] goes on a new page. A block taller than a page still gets one, rather than being pushed on forever. */
        fun opensPage(block: Block) = cursor + block.height > bottom && pages.last().isNotEmpty()

        fun place(block: Block, gapAfter: Float) {
            if (opensPage(block)) newPage()
            pages.last() += block to cursor
            cursor += block.height + gapAfter
        }

        newPage()
        place(sheetHeader(), gapAfter = 20f)
        if (sheet.isCustom) {
            val rows = customSheetLayout(sheet.exercises)
            fun block(row: CustomSheetRow) = when (row.exercise.form) {
                ExerciseForm.MultipleChoice -> multipleChoice(row)
                else -> customExercise(row)
            }
            rows.forEach { row ->
                val block = block(row)
                // An exercise at the top of a page brings its run's instruction along: the one above the run is a page back.
                place(if (opensPage(block)) block(row.openingAPage()) else block, gapAfter = 13f)
            }
            newPage()
            place(answerHeader(), gapAfter = 18f)
            rows.forEach {
                val exercise = it.exercise
                // A gap fill's key reads as the whole sentence, so the answer can be checked in its place; so does a
                // multiple-choice sentence with a gap (a question without one is left as it is).
                val gapped = exercise.form == ExerciseForm.GapFill || exercise.form == ExerciseForm.MultipleChoice
                val asked = if (gapped) filledGapFill(exercise.task, exercise.answer) else exercise.task
                place(answer(it.number, keyAnswer(exercise), asked, card = null), gapAfter = 9f)
            }
        } else {
            // Ten one-line sentences without word help, the usual sheet, fit on the first page of either paper size with
            // these gaps, and their answers, each with its card, on the next.
            sheet.sentences.forEachIndexed { index, sentence ->
                place(exercise(index + 1, sheet.question(sentence), sheet.wordHelp(sentence), sheet.answer(sentence)), gapAfter = 13f)
            }

            newPage()
            place(answerHeader(), gapAfter = 18f)
            sheet.sentences.forEachIndexed { index, sentence ->
                place(answer(index + 1, sheet.answer(sentence), sheet.question(sentence), sheet.cardLine(sentence)), gapAfter = 9f)
            }
        }

        return pages.mapIndexed { index, blocks ->
            { canvas: Canvas ->
                blocks.forEach { (block, top) -> block.draw(canvas, top) }
                drawFooter(canvas, index + 1, pages.size)
            }
        }
    }

    /** The top of the first page: the mark, the coast and the date, then what to do and the sheet's settings. */
    private fun sheetHeader(): Block {
        val custom = sheet.isCustom
        val titleText = if (custom) "Custom practice" else "Translate into ${sheet.toLanguage}"
        val title = wrap(titleText, heading, contentWidth)
        val count = sheet.exerciseCount
        val chips = if (custom) {
            listOf(sheet.options.difficulty.label, if (count == 1) "1 exercise" else "$count exercises")
        } else {
            listOf(
                "From ${sheet.fromLanguage}",
                sheet.options.difficulty.label,
                if (count == 1) "1 sentence" else "$count sentences"
            )
        }
        val chipWidths = chips.map { chipText.measureText(it) + 2 * CHIP_PADDING }
        val chipsWidth = chipWidths.sum() + CHIP_GAP * (chips.size - 1)
        // What to do, then what the sheet practises: the islands whose cards need it most, or the learner's brief.
        val helpText = if (custom) {
            val forms = sheet.exercises.map { it.form }.toSet()
            val written = forms - ExerciseForm.MultipleChoice
            val write = when {
                written.isEmpty() -> null
                written == setOf(ExerciseForm.GapFill) -> "write each answer in its gap"
                ExerciseForm.GapFill in written -> "write each answer in its gap, or in the box under its exercise"
                else -> "write each answer in the box under its exercise"
            }
            val where = when {
                ExerciseForm.MultipleChoice !in forms -> "${write!!.replaceFirstChar { it.uppercaseChar() }}."
                write == null -> "Circle the right option of each exercise."
                else -> "Circle the right option where there are three, and $write."
            }
            "Practising: “${shortened(WorksheetCardDealer.oneLine(sheet.options.brief), BRIEF_ON_SHEET)}”. $where The answers are at the end."
        } else {
            listOfNotNull("Write each sentence in ${sheet.toLanguage} on the lines under it. The answers are at the end.", sheet.focusLine)
                .joinToString(" ")
        }
        val help = wrap(helpText, instruction, contentWidth, lineSpacing = 1.25f)
        // The settings sit at the end of the title's line; with long language names they go under it instead.
        val beside = heading.measureText(titleText) + 16f + chipsWidth <= contentWidth
        val titleTop = BANNER_HEIGHT + 18f
        val chipsTop = if (beside) titleTop + (title.height - CHIP_HEIGHT) / 2f else titleTop + title.height + 8f
        val helpTop = if (beside) titleTop + title.height + 7f else chipsTop + CHIP_HEIGHT + 9f
        return Block(helpTop + help.height) { canvas, top ->
            drawBanner(canvas, top, if (custom) "CUSTOM SHEET" else "PRACTICE SHEET")
            canvas.drawLayout(title, left, top + titleTop)
            var x = if (beside) right - chipsWidth else left
            chips.forEachIndexed { index, chip ->
                val width = chipWidths[index]
                canvas.drawRoundRect(RectF(x, top + chipsTop, x + width, top + chipsTop + CHIP_HEIGHT), CHIP_HEIGHT / 2, CHIP_HEIGHT / 2, fill(SUN_PALE))
                canvas.drawText(chip, x + CHIP_PADDING, top + chipsTop + CHIP_HEIGHT / 2 - (chipText.ascent() + chipText.descent()) / 2, chipText)
                x += width + CHIP_GAP
            }
            canvas.drawLayout(help, left, top + helpTop)
        }
    }

    /** The top of the answer key: the same banner, then who wrote the answers and how far to trust them. */
    private fun answerHeader(): Block {
        val title = wrap("Answer key", heading, contentWidth)
        val cards = if (sheet.cardCount == 1) "1 card" else "${sheet.cardCount} cards"
        val islands = if (sheet.islandCount == 1) "1 island" else "${sheet.islandCount} islands"
        // A model that leaves out a line's number leaves that answer without its card.
        val check = if (sheet.sentences.all { it.card != null }) "check your card, shown under each answer." else "check your cards."
        val noteText = if (sheet.isCustom) {
            "Written by ${sheet.providerLabel} from your brief, with words from the $cards on the $islands of your ${sheet.coastName}. " +
                "Another answer can be right too, and AI can make mistakes: when in doubt, check a grammar book or a dictionary."
        } else {
            "Written by ${sheet.providerLabel} from the $cards on the $islands of your ${sheet.coastName}. " +
                "Another wording can be right too, and AI can make mistakes: when in doubt, $check"
        }
        val note = wrap(noteText, instruction, contentWidth, lineSpacing = 1.25f)
        val titleTop = BANNER_HEIGHT + 18f
        val noteTop = titleTop + title.height + 6f
        return Block(noteTop + note.height) { canvas, top ->
            drawBanner(canvas, top, "ANSWER KEY")
            canvas.drawLayout(title, left, top + titleTop)
            canvas.drawLayout(note, left, top + noteTop)
        }
    }

    /**
     * The band both parts of the sheet open with: the sun-on-horizon mark and the coast's name on the left,
     * [kind] and the date on the right, and the app's wave line under them.
     */
    private fun drawBanner(canvas: Canvas, top: Float, kind: String) {
        drawMark(canvas, left, top, height = 30f)
        val textLeft = left + 50f
        canvas.drawText("LANGUAGE COAST", textLeft, top + 9f, label)
        // The date keeps its place; a long coast name is shrunk to fit beside it rather than run into it.
        val date = sheet.date.toJavaLocalDate().format(DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG))
        val rightColumn = maxOf(dateText.measureText(date), label.measureText(kind))
        val name = TextPaint(coastTitle)
        val room = right - rightColumn - 16f - textLeft
        while (name.measureText(sheet.coastName) > room && name.textSize > 11f) name.textSize -= 0.5f
        canvas.drawText(sheet.coastName, textLeft, top + 29f, name)
        canvas.drawText(kind, right - label.measureText(kind), top + 9f, label)
        canvas.drawText(date, right - dateText.measureText(date), top + 29f, dateText)
        // The wave line the app draws under its screen titles: five waves across.
        val length = contentWidth / 5
        canvas.drawPath(wavePath(left, right, top + BANNER_HEIGHT - 3f, amplitude = 2.4f, crest = left + length / 4, length = length), stroke(WAVE, 1f))
    }

    /**
     * A numbered sentence with lines under it to write the translation on: as many as the longer of the
     * sentence and its [answer] needs ([translationWritingLines]). A sentence written right to left has its
     * number at the right, where it starts. [help], the words the learner hasn't met, goes in small print
     * between the sentence and the lines.
     */
    private fun exercise(index: Int, question: String, help: String?, answer: String): Block {
        val text = wrap(question, sentenceText, contentWidth - numberColumn)
        val helpText = help?.let { wrap("Word help: $it", answerQuestion, contentWidth - numberColumn) }
        val helpHeight = helpText?.let { it.height + HELP_GAP } ?: 0f
        val lineCount = translationWritingLines(question, answer)
        val firstLine = text.height + helpHeight + 22f
        val line = stroke(WRITING_LINE, 0.7f)
        val mirrored = text.isRightToLeft
        return Block(firstLine + (lineCount - 1) * WRITING_LINE_GAP) { canvas, top ->
            // The badge sits on the sentence's first line.
            val middle = top + text.getLineBottom(0) / 2f
            val badge = if (mirrored) right - BADGE_RADIUS else left + BADGE_RADIUS
            val textLeft = if (mirrored) left else left + numberColumn
            canvas.drawCircle(badge, middle, BADGE_RADIUS, fill(SUN_PALE))
            val digits = if (index < 100) number else smallNumber
            canvas.drawText("$index", badge, middle - (digits.ascent() + digits.descent()) / 2, digits)
            canvas.drawLayout(text, textLeft, top)
            helpText?.let { canvas.drawLayout(it, textLeft, top + text.height + HELP_GAP) }
            repeat(lineCount) { i ->
                val y = top + firstLine + i * WRITING_LINE_GAP
                canvas.drawLine(textLeft, y, textLeft + contentWidth - numberColumn, y, line)
            }
        }
    }

    /**
     * A custom sheet's exercise: its instruction when it is the first of a run that shares one, the numbered
     * task, word help in small print, and an empty box to write the answer in, with a line for each line the
     * key answer takes ([answerBoxLines]). The instruction is part of the block, so it never sits alone at
     * the bottom of a page. A gap fill is answered in its gap, printed as long as its answer needs
     * ([printedGapFill]), so it has no box, and the base form in brackets stays right after the gap.
     */
    private fun customExercise(row: CustomSheetRow): Block {
        val exercise = row.exercise
        val gapFill = exercise.form == ExerciseForm.GapFill
        val instructionText = row.instruction?.let { wrap(it, instruction, contentWidth, lineSpacing = 1.2f) }
        val instructionHeight = instructionText?.let { it.height + INSTRUCTION_GAP } ?: 0f
        val task = if (gapFill) printedGapFill(exercise.task, exercise.answer) else exercise.task
        // A gap fill gets more room between its lines, so the handwriting in a gap doesn't touch the line above.
        val text = wrap(task, sentenceText, contentWidth - numberColumn, lineSpacing = if (gapFill) 1.6f else 1.12f)
        val help = exercise.wordHelp.takeIf { it.isNotEmpty() }?.joinToString(" · ") { "${it.word} = ${it.meaning}" }
        val helpText = help?.let { wrap("Word help: $it", answerQuestion, contentWidth - numberColumn) }
        val helpHeight = helpText?.let { it.height + HELP_GAP } ?: 0f
        val lines = if (gapFill) 0 else answerBoxLines(exercise.answer)
        val boxTop = instructionHeight + text.height + helpHeight + if (gapFill) 0f else 8f
        val boxHeight = if (gapFill) 0f else lines * WRITING_LINE_GAP + 6f
        val mirrored = text.isRightToLeft
        val box = stroke(WRITING_LINE, 0.8f)
        // The same ink as the box and a translation's lines: a paler hairline was lost when printed.
        val line = stroke(WRITING_LINE, 0.7f)
        return Block(boxTop + boxHeight) { canvas, top ->
            instructionText?.let { canvas.drawLayout(it, left, top) }
            val taskTop = top + instructionHeight
            val middle = taskTop + text.getLineBottom(0) / 2f
            val badge = if (mirrored) right - BADGE_RADIUS else left + BADGE_RADIUS
            val textLeft = if (mirrored) left else left + numberColumn
            canvas.drawCircle(badge, middle, BADGE_RADIUS, fill(SUN_PALE))
            val digits = if (row.number < 100) number else smallNumber
            canvas.drawText("${row.number}", badge, middle - (digits.ascent() + digits.descent()) / 2, digits)
            canvas.drawLayout(text, textLeft, taskTop)
            helpText?.let { canvas.drawLayout(it, textLeft, taskTop + text.height + HELP_GAP) }
            if (gapFill) return@Block
            val boxRect = RectF(textLeft, top + boxTop, textLeft + contentWidth - numberColumn, top + boxTop + boxHeight)
            canvas.drawRoundRect(boxRect, ANSWER_BOX_RADIUS, ANSWER_BOX_RADIUS, box)
            // A taller box gets faint lines to keep the handwriting straight.
            for (i in 1 until lines) {
                val y = boxRect.top + 3f + i * WRITING_LINE_GAP
                canvas.drawLine(boxRect.left + 8f, y, boxRect.right - 8f, y, line)
            }
        }
    }

    /**
     * A multiple-choice exercise: its instruction when it starts a run, the numbered task, and its three
     * options lettered on one line under it ([choiceLine]), to circle. There is nothing to write, so there
     * is no box, and no word help, which would point at the right option.
     */
    private fun multipleChoice(row: CustomSheetRow): Block {
        val exercise = row.exercise
        val instructionText = row.instruction?.let { wrap(it, instruction, contentWidth, lineSpacing = 1.2f) }
        val instructionHeight = instructionText?.let { it.height + INSTRUCTION_GAP } ?: 0f
        val text = wrap(printedChoiceTask(exercise.task), sentenceText, contentWidth - numberColumn)
        // Wrapped all the same, so that three long options still fit the page.
        val options = wrap(choiceLine(exercise.options), optionText, contentWidth - numberColumn)
        val optionsTop = instructionHeight + text.height + OPTIONS_GAP
        val mirrored = text.isRightToLeft
        return Block(optionsTop + options.height) { canvas, top ->
            instructionText?.let { canvas.drawLayout(it, left, top) }
            val taskTop = top + instructionHeight
            val middle = taskTop + text.getLineBottom(0) / 2f
            val badge = if (mirrored) right - BADGE_RADIUS else left + BADGE_RADIUS
            val textLeft = if (mirrored) left else left + numberColumn
            canvas.drawCircle(badge, middle, BADGE_RADIUS, fill(SUN_PALE))
            val digits = if (row.number < 100) number else smallNumber
            canvas.drawText("${row.number}", badge, middle - (digits.ascent() + digits.descent()) / 2, digits)
            canvas.drawLayout(text, textLeft, taskTop)
            canvas.drawLayout(options, textLeft, top + optionsTop)
        }
    }

    /**
     * An answer with the sentence it belongs to in small print under it, on the side the answer starts from,
     * and under that [card], the learner's own card the sentence was built from: what they can check the
     * answer against, and what to study if they missed it.
     */
    private fun answer(index: Int, answer: String, question: String, card: String?): Block {
        val width = contentWidth - answerIndent
        val text = wrap(answer, answerText, width)
        val sameSide = wrap(question, answerQuestion, width).isRightToLeft == text.isRightToLeft
        val asked = wrap(question, answerQuestion, width, alignment = if (sameSide) Layout.Alignment.ALIGN_NORMAL else Layout.Alignment.ALIGN_OPPOSITE)
        val cardText = card?.let { wrap(it, answerQuestion, width) }
        val cardTop = text.height + 2f + asked.height + 1f
        return Block(if (cardText == null) text.height + 2f + asked.height else cardTop + cardText.height) { canvas, top ->
            canvas.drawText("$index", left + answerIndent - 10f, top + text.getLineBaseline(0), answerNumber)
            canvas.drawLayout(text, left + answerIndent, top)
            canvas.drawLayout(asked, left + answerIndent, top + text.height + 2f)
            cardText?.let { canvas.drawLayout(it, left + answerIndent, top + cardTop) }
        }
    }

    private fun drawFooter(canvas: Canvas, page: Int, pageCount: Int) {
        val lineY = paper.height - margin - 6f
        canvas.drawLine(left, lineY, right, lineY, stroke(HAIRLINE, 0.7f))
        val baseline = paper.height - margin + 6f
        canvas.drawText("Language Coast · ${sheet.coastName}", left, baseline, footer)
        val pages = "Page $page of $pageCount"
        canvas.drawText(pages, right - footer.measureText(pages), baseline, footer)
    }

    /**
     * The app's mark, a sun on the horizon over two waves, [height] points high with its top left corner at
     * ([x], [top]). The shapes follow the launcher icon's vector, in ink that suits white paper.
     */
    private fun drawMark(canvas: Canvas, x: Float, top: Float, height: Float) {
        // The mark fills the icon's 108-unit viewport from (22.5, 30.5) to (85.5, 77.8).
        val scale = height / 47.3f
        canvas.save()
        canvas.translate(x - 22.5f * scale, top - 30.5f * scale)
        canvas.scale(scale, scale)
        val horizon = wavePath(from = 25f, to = 83f, middle = 58.5f, amplitude = 3.4f, crest = 54.8f, length = 33.8f)
        // The sun is the part of its disc above the horizon wave.
        canvas.save()
        canvas.clipPath(Path(horizon).apply {
            lineTo(83f, 20f)
            lineTo(25f, 20f)
            close()
        })
        canvas.drawCircle(54f, 46f, 15.5f, fill(SUN))
        canvas.restore()
        canvas.drawPath(horizon, stroke(INK, 5f))
        canvas.drawPath(wavePath(from = 32f, to = 76f, middle = 72.5f, amplitude = 2.8f, crest = 49.1f, length = 29.4f), stroke(WAVE_SOFT, 5f))
        canvas.restore()
    }

    /** A wave from [from] to [to] around the height [middle], with a crest at [crest] and one every [length]. */
    private fun wavePath(from: Float, to: Float, middle: Float, amplitude: Float, crest: Float, length: Float): Path = Path().apply {
        val steps = 120
        for (i in 0..steps) {
            val x = from + (to - from) * i / steps
            val y = middle - amplitude * cos(2 * PI * (x - crest) / length).toFloat()
            if (i == 0) moveTo(x, y) else lineTo(x, y)
        }
    }

    private companion object {
        /** The height of the band at the top of both parts, down to its wave line. */
        const val BANNER_HEIGHT = 46f

        /** Between a sentence and the word help under it. */
        const val HELP_GAP = 3f
        const val CHIP_HEIGHT = 17f
        const val CHIP_PADDING = 8f
        const val CHIP_GAP = 6f
        const val BADGE_RADIUS = 9.5f
        const val WRITING_LINE_GAP = 24f

        /** Between an instruction and the first exercise it is for. */
        const val INSTRUCTION_GAP = 6f

        /** Between a multiple-choice task and the line of its options. */
        const val OPTIONS_GAP = 7f

        /** The rounded corners of a custom sheet's answer box. */
        const val ANSWER_BOX_RADIUS = 6f

        /** The first page quotes this much of a custom sheet's brief; the whole of it was the learner's own to type. */
        const val BRIEF_ON_SHEET = 120
    }
}

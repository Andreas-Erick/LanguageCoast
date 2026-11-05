package com.andreaserick.languagecoast.data

/** A coast with its islands and cards, as exported. */
data class CoastContent(
    val coast: Coast,
    val islands: List<LanguageIsland>,
    val cards: List<Flashcard>
)

/** One card read from an import file; [island] is null when the file doesn't say. */
data class ImportedCard(
    val nativeText: String,
    val targetText: String,
    val island: String?,
    val alternatives: List<String> = emptyList(),
    val note: String? = null
)

/** File formats cards can be exported to. */
enum class ExportFormat(val label: String, val extension: String, val mimeType: String) {
    /** Tab-separated text with Anki's file headers: one note per card, one subdeck per island. */
    Anki("Anki (.txt)", "txt", "text/plain"),

    /** Comma-separated values for a spreadsheet: one row per card, with a header row. */
    Csv("CSV (.csv)", "csv", "text/csv"),
    Markdown("Markdown (.md)", "md", "text/markdown")
}

/** Writes [contents] in [format]; [nativeLanguage] names the native column where the format has headings. */
fun exportCards(contents: List<CoastContent>, format: ExportFormat, nativeLanguage: String): String = when (format) {
    ExportFormat.Anki -> ankiExport(contents)
    ExportFormat.Csv -> csvExport(contents, nativeLanguage)
    ExportFormat.Markdown -> markdownExport(contents, nativeLanguage)
}

/**
 * A CSV file for spreadsheets: a header row, then one row per card with its island, alternatives and note
 * in columns of their own, so they can be sorted and filtered. Several coasts in one file get a last
 * column that says which language a row is in. Written as RFC 4180 asks (commas, quotes doubled, CRLF),
 * with a byte order mark, without which Excel misreads accented letters. [parseCardFile] reads it back.
 */
private fun csvExport(contents: List<CoastContent>, nativeLanguage: String): String = buildString {
    val oneCoast = contents.singleOrNull()
    fun row(fields: List<String>) {
        append(fields.joinToString(",") { csvField(it) })
        append("\r\n")
    }
    append(BYTE_ORDER_MARK)
    row(listOfNotNull(nativeLanguage, oneCoast?.coast?.language ?: CSV_TRANSLATION, CSV_ISLAND, CSV_ALTERNATIVES, CSV_NOTE, CSV_LANGUAGE.takeIf { oneCoast == null }))
    contents.forEach { content ->
        content.islandsWithCards().forEach { (island, cards) ->
            cards.forEach { card ->
                row(
                    listOfNotNull(
                        card.nativeText, card.targetText, island.name, card.alternatives.joinToString(ALTERNATIVES_SEPARATOR), card.note.orEmpty(),
                        content.coast.language.takeIf { oneCoast == null }
                    )
                )
            }
        }
    }
}

/** Quotes a field that contains a comma, quote or line break, or starts or ends with a space, doubling the quotes inside. */
private fun csvField(text: String): String =
    if (text.any { it == ',' || it == '"' || it == '\n' || it == '\r' } || text != text.trim()) "\"${text.replace("\"", "\"\"")}\"" else text

private const val BYTE_ORDER_MARK = "\uFEFF"

// The CSV export's column titles after the native language. The last two tell an import that the file is this app's.
private const val CSV_TRANSLATION = "Translation"
private const val CSV_ISLAND = "Island"
private const val CSV_ALTERNATIVES = "Alternatives"
private const val CSV_NOTE = "Note"
private const val CSV_LANGUAGE = "Language"

/**
 * Anki imports this with File > Import: the headers set the separator and put each card into the
 * deck "German Coast::Greetings", which Anki shows as a subdeck. [parseCardFile] reads it back.
 */
private fun ankiExport(contents: List<CoastContent>): String = buildString {
    appendLine("#separator:tab")
    appendLine("#html:false")
    appendLine("#notetype:Basic")
    appendLine("#deck column:3")
    appendLine("#columns:Front\tBack\tDeck")
    contents.forEach { content ->
        content.islandsWithCards().forEach { (island, cards) ->
            val deck = "${content.coast.displayName}$DECK_SEPARATOR${island.name}"
            cards.forEach { card ->
                appendLine(listOf(card.nativeText, cardBack(card), deck).joinToString("\t") { tsvField(it) })
            }
        }
    }
}

/** A Markdown document: one heading per coast, and per island a table of native text and translation. */
private fun markdownExport(contents: List<CoastContent>, nativeLanguage: String): String = buildString {
    contents.forEachIndexed { index, content ->
        if (index > 0) appendLine()
        appendLine("# ${content.coast.displayName}")
        content.islandsWithCards().forEach { (island, cards) ->
            appendLine()
            appendLine("## ${islandEmoji(island)} ${island.name}")
            appendLine()
            appendLine("| ${markdownCell(nativeLanguage)} | ${markdownCell(content.coast.language)} |")
            appendLine("| --- | --- |")
            cards.forEach { appendLine("| ${markdownCell(it.nativeText)} | ${markdownCell(cardBack(it)).replace("<br><br>", "<br>")} |") }
        }
    }
}

/**
 * The back of [card] as exported: the translation, then its alternatives and note on their own lines
 * ("Also: a / b", "Note: ..."). [splitCardBack] reads this back on import.
 */
private fun cardBack(card: Flashcard): String = buildString {
    append(card.targetText)
    if (card.alternatives.isNotEmpty()) {
        append("\n\n$ALSO_PREFIX").append(card.alternatives.joinToString(ALTERNATIVES_SEPARATOR))
        card.note?.let { append("\n$NOTE_PREFIX").append(it) }
    }
}

private const val ALSO_PREFIX = "Also: "
private const val NOTE_PREFIX = "Note: "
private const val ALTERNATIVES_SEPARATOR = " / "

/** Splits an exported card back (see [cardBack]) into the translation, its alternatives and its note. */
internal fun splitCardBack(back: String): Triple<String, List<String>, String?> {
    val start = back.indexOf("\n$ALSO_PREFIX")
    if (start < 0) return Triple(back.trim(), emptyList(), null)
    val extras = back.substring(start).lines().map { it.trim() }
    val alternatives = extras.firstOrNull { it.startsWith(ALSO_PREFIX) }?.removePrefix(ALSO_PREFIX)
        ?.split(ALTERNATIVES_SEPARATOR)?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty()
    val note = extras.firstOrNull { it.startsWith(NOTE_PREFIX) }?.removePrefix(NOTE_PREFIX)?.trim()?.takeIf { it.isNotEmpty() }
    return Triple(back.substring(0, start).trim(), alternatives, note)
}

/** Islands in the order they were created, each with its cards in the order they were added; empty islands are left out. */
private fun CoastContent.islandsWithCards(): List<Pair<LanguageIsland, List<Flashcard>>> {
    val cardsByIsland = cards.sortedBy { it.cardId }.groupBy { it.islandId }
    return islands.sortedBy { it.creationDate }.mapNotNull { island -> cardsByIsland[island.islandId]?.let { island to it } }
}

/** Quotes a field that contains a tab, line break or quote, doubling the quotes inside (as Anki expects). */
private fun tsvField(text: String): String =
    if (text.any { it == '\t' || it == '\n' || it == '\r' || it == '"' }) "\"${text.replace("\"", "\"\"")}\"" else text

/** Escapes pipes and turns line breaks into `<br>`, so [text] fits in one Markdown table cell. */
private fun markdownCell(text: String): String =
    text.replace("|", "\\|").replace("\r\n", "<br>").replace("\n", "<br>")

private const val DECK_SEPARATOR = "::"

/** What a file picked for import is, going by its content once read as text. */
enum class ImportFileKind {
    /** Text, which [parseCardFile] can look for cards in. */
    Text,

    /** A zip archive: an Anki package (.apkg, .colpkg, read with [ankiNoteCards]) or a spreadsheet's own format (.xlsx, .ods). */
    Archive,

    /** Some other file that isn't text, or text in an encoding that isn't UTF-8 (such as UTF-16). */
    Binary
}

/**
 * Tells a text file from one that only looks like text to a parser. An Anki package is a zip holding a
 * database; read as text, its bytes have enough commas, tabs and line breaks in them that [parseCardFile]
 * would find dozens of "cards" of garbage. A zip starts with "PK"; other binary files give themselves away
 * by NUL and other control characters, which text never has.
 */
fun importFileKind(text: String): ImportFileKind {
    val start = text.removePrefix(BYTE_ORDER_MARK).take(FILE_KIND_SAMPLE)
    if (start.startsWith(ZIP_SIGNATURE)) return ImportFileKind.Archive
    val controls = start.count { it < ' ' && it != '\t' && it != '\n' && it != '\r' }
    return if ('\u0000' in start || controls * 50 > start.length) ImportFileKind.Binary else ImportFileKind.Text
}

/** The first bytes of every zip file. */
private const val ZIP_SIGNATURE = "PK\u0003\u0004"

/** How much of a file [importFileKind] looks at. */
private const val FILE_KIND_SAMPLE = 4_000

/**
 * Reads cards from a CSV or tab-separated file: the first column is the native text, the second the
 * translation. Anki's file headers (e.g. `#separator:tab`, `#html:true`, `#deck column:3`) are honoured,
 * so files exported by [exportCards] or by Anki itself come back with their islands. Without headers,
 * a third column is read as the island; columns titled "Alternatives" and "Note" in the first row, as in
 * this app's CSV export, are read as those. Rows missing either text are skipped.
 */
fun parseCardFile(text: String): List<ImportedCard> {
    val lines = text.removePrefix(BYTE_ORDER_MARK).lines()
    val headers = lines.takeWhile { it.startsWith("#") }
        .mapNotNull { line -> line.drop(1).split(":", limit = 2).takeIf { it.size == 2 } }
        .associate { (key, value) -> key.trim().lowercase() to value.trim() }
    val body = lines.dropWhile { it.startsWith("#") }.joinToString("\n")

    val separator = headers["separator"]?.let(::separatorFromHeader) ?: detectSeparator(body)
    val isHtml = headers["html"].equals("true", ignoreCase = true)
    // The 0-based index of the column an Anki header like `#deck column:3` names, if present.
    fun column(key: String) = headers["$key column"]?.toIntOrNull()?.minus(1)
    val deckColumn = column("deck")
    val metadataColumns = listOfNotNull(deckColumn, column("notetype"), column("tags"), column("guid")).toSet()

    // The plain text of a field, without HTML if the file says its fields hold HTML.
    fun clean(field: String) = if (isHtml) htmlToText(field) else field.trim()

    val records = splitRecords(body, separator)
    // A fourth column could be anything in somebody else's file; this app's CSV export says in its first row what it holds.
    val titles = if (headers.isEmpty()) records.firstOrNull().orEmpty().map { it.trim() } else emptyList()
    fun titled(title: String) = titles.indexOfFirst { it.equals(title, ignoreCase = true) }.takeIf { it > 2 }
    val alternativesColumn = titled(CSV_ALTERNATIVES)
    val noteColumn = titled(CSV_NOTE)

    return records.mapIndexedNotNull { row, record ->
        val fields = record.filterIndexed { index, _ -> index !in metadataColumns }
        val nativeText = clean(fields.getOrNull(0).orEmpty())
        // Anki keeps the back's line breaks as <br>; turn them back into lines to find the alternatives.
        val back = fields.getOrNull(1).orEmpty().let { if (isHtml) it.replace(HTML_BREAK, "\n") else it }
        val (translation, backAlternatives, backNote) = splitCardBack(back)
        // The first row holds the columns' titles, not a card's alternatives and note.
        fun cell(column: Int?) = if (row == 0) "" else column?.let(fields::getOrNull).orEmpty()
        val alternatives = backAlternatives + cell(alternativesColumn).split(ALTERNATIVES_SEPARATOR)
        val note = backNote ?: cell(noteColumn)
        val targetText = clean(translation)
        if (nativeText.isEmpty() || targetText.isEmpty()) return@mapIndexedNotNull null
        val island = when {
            deckColumn != null -> record.getOrNull(deckColumn)?.substringAfterLast(DECK_SEPARATOR)
            headers.isEmpty() -> fields.getOrNull(2)
            else -> null
        }
        ImportedCard(
            nativeText = nativeText,
            targetText = targetText,
            island = island?.trim()?.takeIf { it.isNotEmpty() },
            alternatives = alternatives.map(::clean).filter { it.isNotEmpty() },
            note = clean(note).takeIf { it.isNotEmpty() }
        )
    }
}

/** The separator an Anki `#separator:` header names, either by name (`tab`, `comma`, …) or as the character itself. */
private fun separatorFromHeader(value: String): Char? = when (value.lowercase()) {
    "tab" -> '\t'
    "comma" -> ','
    "semicolon" -> ';'
    "space" -> ' '
    "pipe" -> '|'
    "colon" -> ':'
    else -> value.singleOrNull()
}

/** Tab if the first row has one, otherwise semicolon or comma (spreadsheets in many locales export with semicolons). */
private fun detectSeparator(body: String): Char {
    // The first row's characters outside quotes; a quoted field may span several lines.
    val firstRow = StringBuilder()
    var inQuotes = false
    for (c in body.trimStart()) {
        if (c == '"') inQuotes = !inQuotes
        else if (c == '\n' && !inQuotes) break
        else if (!inQuotes) firstRow.append(c)
    }
    return when {
        '\t' in firstRow -> '\t'
        firstRow.count { it == ';' } > firstRow.count { it == ',' } -> ';'
        else -> ','
    }
}

/** Splits [text] into records of fields. Quoted fields may contain the separator, line breaks and doubled quotes. */
private fun splitRecords(text: String, separator: Char): List<List<String>> {
    val records = mutableListOf<List<String>>()
    var fields = mutableListOf<String>()
    val field = StringBuilder()
    var inQuotes = false
    var i = 0
    // Finishes the current field and starts the next one.
    fun endField() {
        fields += field.toString()
        field.clear()
    }
    // Finishes the current record; records with only blank fields are dropped.
    fun endRecord() {
        endField()
        if (fields.any { it.isNotBlank() }) records += fields
        fields = mutableListOf()
    }
    while (i < text.length) {
        val c = text[i]
        when {
            inQuotes && c == '"' && text.getOrNull(i + 1) == '"' -> { field.append('"'); i++ }
            c == '"' && (inQuotes || field.isEmpty()) -> inQuotes = !inQuotes
            inQuotes -> field.append(c)
            c == separator -> endField()
            c == '\n' -> endRecord()
            c == '\r' -> Unit
            else -> field.append(c)
        }
        i++
    }
    endRecord()
    return records
}

private val HTML_BREAK = Regex("<br\\s*/?>", RegexOption.IGNORE_CASE)
private val HTML_TAG = Regex("<[^>]+>")
private val WHITESPACE = Regex("\\s+")

/** Anki fields may hold HTML: drop the tags and decode the common entities. */
private fun htmlToText(html: String): String =
    decodeHtmlEntities(html.replace(HTML_BREAK, " ").replace(HTML_TAG, "")).replace(WHITESPACE, " ").trim()

/** [text] with the HTML entities Anki writes turned back into their characters. */
internal fun decodeHtmlEntities(text: String): String = text
    .replace("&nbsp;", " ")
    .replace("&lt;", "<")
    .replace("&gt;", ">")
    .replace("&quot;", "\"")
    .replace("&#39;", "'")
    .replace("&amp;", "&")

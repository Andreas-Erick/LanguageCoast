package com.andreaserick.languagecoast.data

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

/*
 * Importing an Anki package: a deck exported from Anki as .apkg, or a whole collection as .colpkg. A package
 * is a zip that holds the collection as an SQLite database, in one of three files (see
 * [ANKI_COLLECTION_FILES]). Each platform unpacks the zip and reads the database with what it has; what the
 * rows mean, and how a note becomes a card here, is decided in this file.
 *
 * A note is a list of fields (Front and Back for the usual note type, more for others), stored as HTML. Its
 * first two fields with any text become the card's two sides, and the deck its first card is in names the
 * island. Pictures, sounds, styling, tags and Anki's review history are left behind.
 */

/**
 * The files a package can hold its collection in, the one to read first. Anki since 2.1.50 writes the first,
 * compressed with zstd, unless "Support older Anki versions" is ticked when exporting, which gives the
 * second. A package with either also has the third, but only as a stub with one note that asks to update
 * Anki, so it must only be read when it is the sole one (packages from Anki 2.0 and from other tools).
 */
val ANKI_COLLECTION_FILES = listOf("collection.anki21b", "collection.anki21", "collection.anki2")

/** The collection file that is compressed with zstd. */
const val ANKI_COMPRESSED_COLLECTION = "collection.anki21b"

/** What separates a note's fields in the database, and in the newer format also the levels of a deck's name. */
const val ANKI_SEPARATOR = '\u001F'

/** What separates the levels of a deck's name everywhere else ("German::Greetings"). */
private const val ANKI_DECK_LEVELS = "::"

/** The deck every collection has, for cards that aren't filed anywhere: not a topic, so not an island. */
private const val ANKI_DEFAULT_DECK = "Default"

/**
 * A note as read from a package's database.
 *
 * @property fields Its fields in order, as the HTML Anki stores.
 * @property deck The full name of the deck its first card is in, in either format's spelling; null if unknown.
 */
data class AnkiNote(val fields: List<String>, val deck: String? = null)

/** The note in the `notes` row whose `flds` column is [fields], with its first card in [deck]. */
fun ankiNote(fields: String, deck: String?): AnkiNote = AnkiNote(fields.split(ANKI_SEPARATOR), deck)

/**
 * The decks' names by ID from the older format, where they are a JSON object in the `col` table:
 * `{"1": {"id": 1, "name": "Default", ...}, ...}`. Empty if [decksJson] isn't that.
 */
fun ankiDeckNames(decksJson: String): Map<Long, String> {
    val decks = try {
        Json.parseToJsonElement(decksJson) as? JsonObject ?: return emptyMap()
    } catch (e: SerializationException) {
        return emptyMap()
    }
    return decks.mapNotNull { (key, deck) ->
        val id = key.toLongOrNull() ?: return@mapNotNull null
        val name = (deck as? JsonObject)?.get("name")?.let { runCatching { it.jsonPrimitive.content }.getOrNull() } ?: return@mapNotNull null
        id to name
    }.toMap()
}

/**
 * The cards [notes] make: the first field with any text is the native side, the next one the translation
 * (the import's "Swap sides" turns a deck around that has them the other way). A back written by this app's
 * own Anki export gives its alternatives and note back (see [splitCardBack]). Notes with fewer than two
 * fields of text are left out, among them the stub note of a package that needs a newer reader.
 */
fun ankiNoteCards(notes: List<AnkiNote>): List<ImportedCard> = notes.mapNotNull { note ->
    val sides = note.fields.map(::ankiFieldText).filter { it.isNotEmpty() }
    if (sides.size < 2) return@mapNotNull null
    val (translation, alternatives, cardNote) = splitCardBack(sides[1])
    val targetText = oneLine(translation)
    if (targetText.isEmpty()) return@mapNotNull null
    ImportedCard(
        nativeText = oneLine(sides[0]),
        targetText = targetText,
        island = note.deck?.let(::ankiIsland),
        alternatives = alternatives,
        note = cardNote
    )
}

/** The island for a card from [deck]: the last level of its name ("German::Greetings" gives "Greetings"). */
internal fun ankiIsland(deck: String): String? =
    deck.split(ANKI_DECK_LEVELS, ANKI_SEPARATOR.toString()).last().trim().takeIf { it.isNotEmpty() && it != ANKI_DEFAULT_DECK }

/**
 * The text of a note's field: its HTML without tags, sounds and pictures, with cloze deletions filled in.
 * Line breaks and blocks stay as lines, each without space around it; empty lines are dropped.
 */
internal fun ankiFieldText(html: String): String {
    val text = html
        .replace(HIDDEN_BLOCK, "")
        .replace(SOUND, "")
        .replace(CLOZE, "$1")
        .replace(LINE_END, "\n")
        .replace(TAG, "")
    return decodeHtmlEntities(text.replace(NUMERIC_ENTITY) { match -> entityChar(match.groupValues[1]) ?: match.value })
        .lines()
        .map { it.replace(SPACES, " ").trim() }
        .filter { it.isNotEmpty() }
        .joinToString("\n")
}

private fun oneLine(text: String): String = text.lines().map { it.trim() }.filter { it.isNotEmpty() }.joinToString(" ")

/** The character of `&#228;` or `&#xE4;`, given what is between `&#` and `;`; null if that is no character. */
private fun entityChar(code: String): String? {
    val value = if (code.startsWith("x", ignoreCase = true)) code.drop(1).toIntOrNull(16) else code.toIntOrNull()
    return value?.takeIf { it in 0x20..0xFFFF }?.toChar()?.toString()
}

/** `<style>` and `<script>` with what is in them, which is never part of a card's text. */
private val HIDDEN_BLOCK = Regex("<(style|script)\\b[^>]*>[\\s\\S]*?</\\1>", RegexOption.IGNORE_CASE)

/** `[sound:hello.mp3]`, how Anki attaches audio to a field. */
private val SOUND = Regex("\\[sound:[^\\]]*\\]")

/** A cloze deletion, `{{c1::answer}}` or `{{c1::answer::hint}}`; the answer is its first group. */
private val CLOZE = Regex("\\{\\{c\\d+::(.*?)(?:::.*?)?\\}\\}")

/** What ends a line in a field: a break, or the start or end of a block. */
private val LINE_END = Regex("<br\\s*/?>|</?(?:div|p|li|tr|h[1-6])\\b[^>]*>", RegexOption.IGNORE_CASE)
private val TAG = Regex("<[^>]+>")
private val NUMERIC_ENTITY = Regex("&#(x?[0-9a-fA-F]+);")
private val SPACES = Regex("[ \\t\u00A0]+")

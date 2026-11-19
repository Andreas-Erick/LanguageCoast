package com.andreaserick.languagecoast.data

import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import com.github.luben.zstd.ZstdInputStreamNoFinalizer
import java.io.File
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipException
import java.util.zip.ZipInputStream

/** What a zip file picked for import turned out to be. */
sealed interface AnkiPackageContent {
    /** An Anki package, with its notes in the order they were made. */
    data class Notes(val notes: List<AnkiNote>) : AnkiPackageContent

    /** A zip without a collection in it: some other archive, or a spreadsheet's own format. */
    data object NotAPackage : AnkiPackageContent

    /** A package whose collection couldn't be unpacked or read: a damaged file, or a format newer than this app knows. */
    data object Unreadable : AnkiPackageContent
}

/** Total unpacked bytes allowed across all extracted collection entries (guards against ZIP bombs). */
private const val MAX_COLLECTION_BYTES = 512L * 1024 * 1024

/** Maximum ZIP entries scanned before aborting (guards against million-entry ZIP flood DoS). */
private const val MAX_ZIP_ENTRIES = 50_000

/** Notes read from one package at most. */
private const val MAX_NOTES = 100_000

/** Decks read from one package at most. */
private const val MAX_DECKS = 10_000

/** Maximum character length of one note's raw `flds` string to avoid single-row OOM. */
private const val MAX_FIELD_CHARS = 100_000

/**
 * Reads the notes of the Anki package (.apkg or .colpkg) that [input] is, a zip from its first byte.
 *
 * The zip is read as it streams in, since a content URI gives no file to jump around in: the collection
 * files are copied into [workFolder] (the newer format's through zstd) and everything else is passed over.
 * Anki writes the collection before the media, so the reading stops at the first media entry rather than
 * unpack a deck's worth of pictures on the way to the end. The best collection found (see
 * [ANKI_COLLECTION_FILES]) is then opened with the phone's own SQLite. [workFolder] is emptied afterwards.
 */
fun readAnkiPackage(input: InputStream, workFolder: File): AnkiPackageContent {
    workFolder.deleteRecursively()
    workFolder.mkdirs()
    val found = HashMap<String, File>()
    var totalExtractedBytes = 0L
    var scannedEntries = 0
    return try {
        ZipInputStream(input).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                scannedEntries++
                if (scannedEntries > MAX_ZIP_ENTRIES) throw IOException("Too many zip entries")
                val name = entry.name
                when {
                    // The names are fixed, so nothing in the zip decides where a file is written.
                    // Reject duplicate collection entries to avoid amplification or shadowing.
                    name in ANKI_COLLECTION_FILES -> {
                        if (name in found) throw IOException("Duplicate collection entry: $name")
                        val remaining = (MAX_COLLECTION_BYTES - totalExtractedBytes).coerceAtLeast(0L)
                        found[name] = File(workFolder, name).also { file ->
                            file.outputStream().use { output ->
                                val written = if (name == ANKI_COMPRESSED_COLLECTION) {
                                    ZstdInputStreamNoFinalizer(KeptOpen(zip)).use { it.copyLimitedTo(output, remaining) }
                                } else {
                                    zip.copyLimitedTo(output, remaining)
                                }
                                totalExtractedBytes += written
                            }
                        }
                    }
                    found.isNotEmpty() && (name == "media" || name.all { it.isDigit() }) -> break
                }
            }
        }
        val collection = ANKI_COLLECTION_FILES.firstNotNullOfOrNull { found[it] } ?: return AnkiPackageContent.NotAPackage
        AnkiPackageContent.Notes(readNotes(collection))
    } catch (e: ZipException) {
        logError(TAG, "The package isn't a readable zip", e)
        AnkiPackageContent.Unreadable
    } catch (e: IOException) {
        // Also what zstd throws for a collection it can't unpack.
        logError(TAG, "The package's collection couldn't be unpacked", e)
        AnkiPackageContent.Unreadable
    } catch (e: SQLiteException) {
        logError(TAG, "The package's collection couldn't be read", e)
        AnkiPackageContent.Unreadable
    } catch (e: LinkageError) {
        // The zstd library is native code; a phone it can't be loaded on can still read the older format.
        logError(TAG, "zstd isn't available on this device", e)
        AnkiPackageContent.Unreadable
    } catch (e: RuntimeException) {
        // Whatever else a file that isn't what it claims to be sets off. The zip reader refuses an entry whose
        // name isn't UTF-8 (IllegalArgumentException), as older Windows tools wrote them: before any collection
        // was found, that is some other archive. A row too large for the phone's cursor (IllegalStateException)
        // is a package that can't be read. Either way the user gets a message, not a closed app.
        logError(TAG, "The file couldn't be read as a package", e)
        if (found.isEmpty()) AnkiPackageContent.NotAPackage else AnkiPackageContent.Unreadable
    } finally {
        workFolder.deleteRecursively()
    }
}

/**
 * The notes in the collection [file], each with the name of the deck its first card is in. The file is this
 * app's own copy and is opened for writing: the newer format is left in write-ahead-log mode, which SQLite
 * can't open read-only. Verifies that `notes` and `cards` are real tables (not crafted SQLite VIEWs).
 */
private fun readNotes(file: File): List<AnkiNote> =
    SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READWRITE or SQLiteDatabase.NO_LOCALIZED_COLLATORS).use { database ->
        if (!hasRealTable(database, "notes") || !hasRealTable(database, "cards")) {
            throw SQLiteException("Collection is missing required tables")
        }
        val decks = deckNames(database)
        val notes = ArrayList<AnkiNote>()
        database.rawQuery(
            "SELECT substr(n.flds, 1, $MAX_FIELD_CHARS), (SELECT c.did FROM cards c WHERE c.nid = n.id ORDER BY c.ord LIMIT 1) FROM notes n ORDER BY n.id LIMIT $MAX_NOTES",
            null
        ).use { cursor ->
            while (cursor.moveToNext()) {
                notes += ankiNote(cursor.getString(0).orEmpty(), if (cursor.isNull(1)) null else decks[cursor.getLong(1)])
            }
        }
        notes
    }

private fun hasRealTable(database: SQLiteDatabase, tableName: String): Boolean =
    database.rawQuery("SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = ?", arrayOf(tableName)).use { it.moveToFirst() }

/**
 * The decks' names by ID. The newer format has a table for them; its name column is sorted with a collation
 * of Anki's own, which this SQLite doesn't have, so the rows are read as they come and never compared. The
 * older format keeps them as JSON in the collection's one `col` row.
 */
private fun deckNames(database: SQLiteDatabase): Map<Long, String> {
    val hasDeckTable = hasRealTable(database, "decks")
    if (!hasDeckTable) {
        if (!hasRealTable(database, "col")) return emptyMap()
        return database.rawQuery("SELECT decks FROM col LIMIT 1", null).use { cursor ->
            if (cursor.moveToFirst()) ankiDeckNames(cursor.getString(0).orEmpty()) else emptyMap()
        }
    }
    val names = HashMap<Long, String>()
    database.rawQuery("SELECT id, name FROM decks LIMIT $MAX_DECKS", null).use { cursor ->
        while (cursor.moveToNext()) names[cursor.getLong(0)] = cursor.getString(1).orEmpty()
    }
    return names
}

/** Copies the stream to [output], giving up once [maxBytes] have been written; returns bytes written. */
private fun InputStream.copyLimitedTo(output: OutputStream, maxBytes: Long): Long {
    val buffer = ByteArray(64 * 1024)
    var copied = 0L
    while (true) {
        val read = read(buffer)
        if (read == -1) break
        copied += read
        if (copied > maxBytes) throw IOException("The collection is larger than $MAX_COLLECTION_BYTES bytes")
        output.write(buffer, 0, read)
    }
    return copied
}

/** [stream] for a reader that closes what it reads from when it is done, which a zip being read entry by entry must survive. */
private class KeptOpen(stream: InputStream) : FilterInputStream(stream) {
    override fun close() = Unit
}

private const val TAG = "AnkiPackageReader"

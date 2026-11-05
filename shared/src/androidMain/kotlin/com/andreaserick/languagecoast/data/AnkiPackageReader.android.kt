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

/** A collection larger than this once unpacked is refused; a real one of this size would hold millions of notes. */
private const val MAX_COLLECTION_BYTES = 512L * 1024 * 1024

/** Notes read from one package at most. */
private const val MAX_NOTES = 100_000

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
    return try {
        val found = HashMap<String, File>()
        ZipInputStream(input).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val name = entry.name
                when {
                    // The names are fixed, so nothing in the zip decides where a file is written.
                    name in ANKI_COLLECTION_FILES -> found[name] = File(workFolder, name).also { file ->
                        file.outputStream().use { output ->
                            if (name == ANKI_COMPRESSED_COLLECTION) ZstdInputStreamNoFinalizer(KeptOpen(zip)).use { it.copyLimitedTo(output) }
                            else zip.copyLimitedTo(output)
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
    } finally {
        workFolder.deleteRecursively()
    }
}

/**
 * The notes in the collection [file], each with the name of the deck its first card is in. The file is this
 * app's own copy and is opened for writing: the newer format is left in write-ahead-log mode, which SQLite
 * can't open read-only.
 */
private fun readNotes(file: File): List<AnkiNote> =
    SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READWRITE or SQLiteDatabase.NO_LOCALIZED_COLLATORS).use { database ->
        val decks = deckNames(database)
        val notes = ArrayList<AnkiNote>()
        database.rawQuery(
            "SELECT n.flds, (SELECT c.did FROM cards c WHERE c.nid = n.id ORDER BY c.ord LIMIT 1) FROM notes n ORDER BY n.id LIMIT $MAX_NOTES",
            null
        ).use { cursor ->
            while (cursor.moveToNext()) {
                notes += ankiNote(cursor.getString(0).orEmpty(), if (cursor.isNull(1)) null else decks[cursor.getLong(1)])
            }
        }
        notes
    }

/**
 * The decks' names by ID. The newer format has a table for them; its name column is sorted with a collation
 * of Anki's own, which this SQLite doesn't have, so the rows are read as they come and never compared. The
 * older format keeps them as JSON in the collection's one `col` row.
 */
private fun deckNames(database: SQLiteDatabase): Map<Long, String> {
    val hasDeckTable = database.rawQuery("SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = 'decks'", null).use { it.moveToFirst() }
    if (!hasDeckTable) {
        return database.rawQuery("SELECT decks FROM col", null).use { cursor ->
            if (cursor.moveToFirst()) ankiDeckNames(cursor.getString(0).orEmpty()) else emptyMap()
        }
    }
    val names = HashMap<Long, String>()
    database.rawQuery("SELECT id, name FROM decks", null).use { cursor ->
        while (cursor.moveToNext()) names[cursor.getLong(0)] = cursor.getString(1).orEmpty()
    }
    return names
}

/** Copies the stream to [output], giving up once it is larger than any collection would be. */
private fun InputStream.copyLimitedTo(output: OutputStream) {
    val buffer = ByteArray(64 * 1024)
    var copied = 0L
    while (true) {
        val read = read(buffer)
        if (read == -1) break
        copied += read
        if (copied > MAX_COLLECTION_BYTES) throw IOException("The collection is larger than $MAX_COLLECTION_BYTES bytes")
        output.write(buffer, 0, read)
    }
}

/** [stream] for a reader that closes what it reads from when it is done, which a zip being read entry by entry must survive. */
private class KeptOpen(stream: InputStream) : FilterInputStream(stream) {
    override fun close() = Unit
}

private const val TAG = "AnkiPackageReader"

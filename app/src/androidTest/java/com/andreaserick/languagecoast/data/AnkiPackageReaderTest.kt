package com.andreaserick.languagecoast.data

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Reads Anki packages on a device, with its SQLite and the zstd library as shipped. The packages are in the
 * test's assets, made by `androidTest/fixtures/make_anki_packages.py` to match what Anki writes in each of
 * its formats.
 *
 * To read other packages as well, e.g. real decks, put them in the app's cache under `anki-test/` (with
 * `run-as`); they must then give at least one card each.
 */
@RunWith(AndroidJUnit4::class)
class AnkiPackageReaderTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context: Context = instrumentation.targetContext
    private val workFolder = File(context.cacheDir, "anki-reader-test")

    private fun read(asset: String): AnkiPackageContent =
        instrumentation.context.assets.open("anki/$asset").use { readAnkiPackage(it, workFolder) }

    private fun cards(asset: String): List<ImportedCard> = ankiNoteCards((read(asset) as AnkiPackageContent.Notes).notes)

    /** The cards every package of the fixtures holds, whatever its format. */
    private val expected = listOf(
        ImportedCard("Good morning", "Guten Morgen", "Greetings"),
        ImportedCard("Thank you very much", "Vielen Dank", "Greetings"),
        ImportedCard(
            "Do you want to come?", "Möchtest du kommen?", "Greetings",
            alternatives = listOf("Willst du kommen?", "Möchten Sie kommen?"), note = "Sie is formal."
        ),
        // The deck of the note's first card, though its second card comes first in the table.
        ImportedCard("bread", "das Brot", "Food & Drink"),
        ImportedCard("The cheese is good.", "Der Käse ist gut.", "Food & Drink"),
        ImportedCard("water", "das Wasser", null)
    )

    @Test
    fun aPackageInTheOldestFormatIsRead() {
        assertEquals(expected, cards("legacy.apkg"))
    }

    @Test
    fun theRealCollectionIsReadAndNotTheStubThatAsksToUpdateAnki() {
        assertEquals(expected, cards("legacy21.apkg"))
    }

    @Test
    fun aCompressedPackageInTheNewestFormatIsRead() {
        val notes = (read("modern.apkg") as AnkiPackageContent.Notes).notes

        assertEquals(7, notes.size)
        // Deck names come from their own table, whose levels are separated as fields are.
        assertEquals("German\u001FGreetings", notes.first().deck)
        assertEquals(expected, ankiNoteCards(notes))
    }

    @Test
    fun aDamagedPackageIsUnreadableRatherThanReadFromItsStub() {
        assertEquals(AnkiPackageContent.Unreadable, read("broken.apkg"))
    }

    /** A zip holding [names], each with a few bytes in it. */
    private fun zipOf(vararg names: String): ByteArray = ByteArrayOutputStream().also { bytes ->
        ZipOutputStream(bytes).use { archive ->
            names.forEach { name ->
                archive.putNextEntry(ZipEntry(name))
                archive.write("<workbook/>".toByteArray())
                archive.closeEntry()
            }
        }
    }.toByteArray()

    @Test
    fun aZipWithoutACollectionIsNotAPackage() {
        // A spreadsheet's own format is a zip too.
        assertEquals(AnkiPackageContent.NotAPackage, readAnkiPackage(ByteArrayInputStream(zipOf("xl/workbook.xml", "media")), workFolder))
        // So is a file that only starts like a zip.
        assertEquals(AnkiPackageContent.NotAPackage, readAnkiPackage(ByteArrayInputStream(byteArrayOf(80, 75, 3, 4, 1, 2, 3)), workFolder))
    }

    @Test
    fun aZipWithFileNamesInAnotherCodePageIsNotAPackage() {
        // Older Windows tools wrote names in the PC's own code page. "Ü" in Latin-1 is a byte that is not UTF-8,
        // which the zip reader refuses with an exception of its own kind.
        val bytes = ByteArrayOutputStream()
        ZipOutputStream(bytes, Charsets.ISO_8859_1).use { archive ->
            archive.putNextEntry(ZipEntry("Übung.txt"))
            archive.write("Hallo".toByteArray())
            archive.closeEntry()
        }

        assertEquals(AnkiPackageContent.NotAPackage, readAnkiPackage(ByteArrayInputStream(bytes.toByteArray()), workFolder))
        assertFalse(workFolder.exists())
    }

    @Test
    fun aFileNamedToClimbOutOfTheWorkFolderIsNotUnpacked() {
        val content = readAnkiPackage(ByteArrayInputStream(zipOf("../collection.anki2", "media")), workFolder)

        // Android refuses such a zip outright; either way nothing of it becomes notes or a file outside the folder.
        assertFalse(content is AnkiPackageContent.Notes)
        assertFalse(File(context.cacheDir, "collection.anki2").exists())
    }

    @Test
    fun nothingIsLeftBehindInTheWorkFolder() {
        read("modern.apkg")
        read("broken.apkg")

        assertFalse(workFolder.exists())
    }

    @Test
    fun maliciousSqliteViewInsteadOfNotesTableIsRejected() {
        val dbFile = File(context.cacheDir, "malicious-view-test.sqlite").apply { delete() }
        try {
            android.database.sqlite.SQLiteDatabase.openOrCreateDatabase(dbFile, null).use { db ->
                db.execSQL("CREATE VIEW notes AS SELECT 1 AS id, 'a\u001Fb' AS flds")
                db.execSQL("CREATE VIEW cards AS SELECT 1 AS id, 1 AS nid, 0 AS ord, 1 AS did")
            }
            val bytes = ByteArrayOutputStream().also { out ->
                ZipOutputStream(out).use { archive ->
                    archive.putNextEntry(ZipEntry("collection.anki2"))
                    archive.write(dbFile.readBytes())
                    archive.closeEntry()
                }
            }.toByteArray()

            assertEquals(AnkiPackageContent.Unreadable, readAnkiPackage(ByteArrayInputStream(bytes), workFolder))
            assertFalse(workFolder.exists())
        } finally {
            dbFile.delete()
        }
    }

    @Test
    fun packagesPutInTheCacheForTestingGiveCards() {
        val extra = File(context.cacheDir, "anki-test").listFiles().orEmpty().sortedBy { it.name }
        extra.forEach { file ->
            val content = file.inputStream().use { readAnkiPackage(it, workFolder) }
            assertTrue("${file.name} couldn't be read: $content", content is AnkiPackageContent.Notes)
            val notes = (content as AnkiPackageContent.Notes).notes
            val cards = ankiNoteCards(notes)
            assertTrue("${file.name} gave no cards", cards.isNotEmpty())
        }
    }
}

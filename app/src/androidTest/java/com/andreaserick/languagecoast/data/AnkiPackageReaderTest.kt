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
    fun packagesPutInTheCacheForTestingGiveCards() {
        val extra = File(context.cacheDir, "anki-test").listFiles().orEmpty().sortedBy { it.name }
        extra.forEach { file ->
            val content = file.inputStream().use { readAnkiPackage(it, workFolder) }
            assertTrue("${file.name} couldn't be read: $content", content is AnkiPackageContent.Notes)
            val notes = (content as AnkiPackageContent.Notes).notes
            val cards = ankiNoteCards(notes)
            println("ANKI ${file.name}: ${notes.size} notes, ${cards.size} cards, islands ${cards.map { it.island }.distinct().take(6)}")
            cards.take(3).forEach { println("ANKI   ${it.nativeText.take(50)} | ${it.targetText.take(50)} | ${it.island}") }
            assertTrue("${file.name} gave no cards", cards.isNotEmpty())
        }
    }
}

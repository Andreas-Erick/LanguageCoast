package com.andreaserick.languagecoast.data

import android.content.Context
import android.util.Log
import androidx.room.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Reads the cards of a very large coast from a real database, the way a practice sheet does: 60 000 cards
 * on 300 islands, next to another coast whose cards must stay out of it.
 */
@RunWith(AndroidJUnit4::class)
class WorksheetCardsQueryTest {

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private var database: AppDatabase? = null

    @Before
    fun setUp() {
        context.deleteDatabase(TEST_DB)
    }

    @After
    fun tearDown() {
        database?.close()
        context.deleteDatabase(TEST_DB)
    }

    @Test
    fun aCoastOfTensOfThousandsOfCardsIsReadForASheet() = runBlocking {
        val settings = createSettingsDataStore(context.cacheDir.resolve("worksheet-query-test-${System.nanoTime()}.preferences_pb").path)
        val builder = Room.databaseBuilder<AppDatabase>(context, TEST_DB).setDriver(AndroidSQLiteDriver())
        val dao = buildAppDatabase(builder, settings).also { database = it }.languageCoastDao()

        dao.insertCoasts(listOf(Coast(coastId = 1, language = "German"), Coast(coastId = 2, language = "Spanish")))
        // Names that sort differently from the islands' IDs, to see the order of the result.
        dao.insertIslands(
            (1..ISLANDS).map { LanguageIsland(islandId = it, coastId = 1, name = "Insel ${(ISLANDS - it).toString().padStart(3, '0')}") } +
                LanguageIsland(islandId = ISLANDS + 1, coastId = 2, name = "Saludos")
        )
        (1..CARDS).chunked(5_000).forEach { ids ->
            dao.insertFlashcards(ids.map { id ->
                Flashcard(
                    cardId = id, islandId = (id - 1) % ISLANDS + 1,
                    nativeText = "The native side of card number $id", targetText = "Die Seite $id der Karte in der Zielsprache",
                    notes = "A note that a sheet has no use for",
                    stability = if (id % 3 == 0) id / 1000.0 else null, difficulty = if (id % 3 == 0) 6.5 else null,
                    lastReviewed = if (id % 3 == 0) 1_700_000_000_000 + id else null, due = if (id % 3 == 0) 1_800_000_000_000 else null,
                    lapses = id % 4,
                    alternatives = if (id % 10 == 0) listOf("Eine andere Fassung $id", "Noch eine $id") else emptyList()
                )
            })
        }
        dao.insertFlashcards((1..500).map { Flashcard(cardId = CARDS + it, islandId = ISLANDS + 1, nativeText = "Good morning $it", targetText = "Buenos días $it") })

        val start = System.nanoTime()
        val cards = dao.getWorksheetCards(1)
        Log.i(TAG, "Read ${cards.size} cards in ${(System.nanoTime() - start) / 1_000_000} ms")

        assertEquals(CARDS, cards.size)
        assertTrue(cards.none { it.targetText.startsWith("Buenos") })
        assertEquals(ISLANDS, cards.distinctBy { it.islandId }.size)
        // By island name, and within an island in the order the cards were added.
        assertEquals("Insel 000" to ISLANDS, cards.first().islandName to cards.first().islandId)
        assertEquals(cards.map { it.islandName }.sorted(), cards.map { it.islandName })
        val firstIsland = cards.takeWhile { it.islandId == ISLANDS }
        assertEquals(CARDS / ISLANDS, firstIsland.size)
        assertEquals("Die Seite $ISLANDS der Karte in der Zielsprache", firstIsland.first().targetText)
        assertEquals("The native side of card number $ISLANDS", firstIsland.first().nativeText)
        assertEquals(listOf("Eine andere Fassung $ISLANDS", "Noch eine $ISLANDS"), firstIsland.first().alternatives)
        assertEquals(CARDS / 10, cards.count { it.alternatives.isNotEmpty() })
        // How well a card is remembered comes along: card 300 was studied, card 301 (the first of the island named last) never.
        assertEquals(listOf<Any?>(0.3, 6.5, 1_700_000_000_300, 0), firstIsland.first().let { listOf(it.stability, it.difficulty, it.lastReviewed, it.lapses) })
        assertEquals(listOf<Any?>(null, null, null, 1), cards.last { it.islandId == 1 && it.targetText.startsWith("Die Seite 301 ") }.let { listOf(it.stability, it.difficulty, it.lastReviewed, it.lapses) })
        assertEquals(CARDS / 3, cards.count { it.stability != null && it.lastReviewed != null })

        assertEquals(500, dao.getWorksheetCards(2).size)
        assertTrue(dao.getWorksheetCards(99).isEmpty())
    }

    private companion object {
        const val TAG = "WorksheetCardsQueryTest"
        const val TEST_DB = "worksheet-query-test"
        const val CARDS = 60_000
        const val ISLANDS = 300
    }
}

package com.andreaserick.languagecoast.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Opens a hand-built v2 database with the current Room schema to check the migrations up to the current version. */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

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
    fun existingIslandsAndCardsMoveOntoCoastForLegacyTargetLanguage() = runBlocking {
        createV2Database { db ->
            db.execSQL("INSERT INTO language_islands (islandId, name, creationDate) VALUES (1, 'Greetings', 0), (2, 'Travel', 0)")
            db.execSQL(
                "INSERT INTO flashcards (cardId, islandId, nativeText, targetText) VALUES " +
                    "(1, 1, 'Good morning', 'Guten Morgen'), (2, 2, 'Where is the train station?', 'Wo ist der Bahnhof?')"
            )
        }

        val dao = openMigratedDatabase(legacyTargetLanguage = "German").languageCoastDao()

        val coast = dao.getAllCoasts().first().single()
        assertEquals("German", coast.language)
        assertEquals(listOf("Greetings", "Travel"), dao.getIslandsForCoast(coast.coastId).first().map { it.name }.sorted())
        assertEquals("Guten Morgen", dao.getCardsForIsland(1).first().single().targetText)
        assertEquals(coast, dao.getCoastForIsland(2).first())
        val summary = dao.getCoastSummaries(studiedSince = 0, now = 0).first().single()
        assertEquals(2, summary.islandCount)
        assertEquals(2, summary.cardCount)
        assertEquals(null, summary.lastStudied)
        assertEquals(0, summary.islandsStudiedRecently)

        // v4 columns start empty, so islands fall back to a guessed emoji and "not studied yet".
        val islands = dao.getIslandSummaries(coast.coastId, now = 0).first()
        assertEquals(listOf(1, 1), islands.map { it.cardCount })
        assertTrue(islands.all { it.island.emoji == null && it.island.lastStudied == null })
        assertEquals("👋", islandEmoji(islands.single { it.island.name == "Greetings" }.island))

        // v5: existing cards start as new cards, due right away.
        val card = dao.getCardsForIsland(1).first().single()
        assertTrue(card.stability == null && card.difficulty == null && card.lastReviewed == null && card.due == null)
        // v6: no alternatives or note yet.
        assertTrue(card.alternatives.isEmpty() && card.note == null)
        assertEquals(listOf(1, 1), dao.getIslandSummaries(coast.coastId, now = 0).first().map { it.dueCount })
        assertEquals(2, dao.getCoastSummaries(studiedSince = 0, now = 0).first().single().dueCount)
    }

    @Test
    fun emptyDatabaseGetsNoCoast() = runBlocking {
        createV2Database {}

        val dao = openMigratedDatabase(legacyTargetLanguage = "German").languageCoastDao()

        assertTrue(dao.getAllCoasts().first().isEmpty())
    }

    @Test
    fun deletingMigratedCoastCascadesToIslandsAndCards() = runBlocking {
        createV2Database { db ->
            db.execSQL("INSERT INTO language_islands (islandId, name, creationDate) VALUES (1, 'Greetings', 0)")
            db.execSQL("INSERT INTO flashcards (cardId, islandId, nativeText, targetText) VALUES (1, 1, 'Hi', 'Hallo')")
        }
        val dao = openMigratedDatabase(legacyTargetLanguage = "German").languageCoastDao()

        dao.deleteCoast(dao.getAllCoasts().first().single())

        assertTrue(dao.getIslandsForCoast(1).first().isEmpty())
        assertTrue(dao.getCardsForIsland(1).first().isEmpty())
    }

    /** Creates the database exactly as schema version 2 did, then lets [seed] add rows. */
    private fun createV2Database(seed: (SQLiteDatabase) -> Unit) {
        SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(TEST_DB), null).use { db ->
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `language_islands` (`islandId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`name` TEXT NOT NULL, `creationDate` INTEGER NOT NULL)"
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `flashcards` (`cardId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`islandId` INTEGER NOT NULL, `nativeText` TEXT NOT NULL, `targetText` TEXT NOT NULL, `notes` TEXT, " +
                    "FOREIGN KEY(`islandId`) REFERENCES `language_islands`(`islandId`) ON UPDATE NO ACTION ON DELETE CASCADE)"
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_flashcards_islandId` ON `flashcards` (`islandId`)")
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `study_progress` (`progressId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`cardId` INTEGER NOT NULL, `lastReviewed` INTEGER NOT NULL, `nextReview` INTEGER NOT NULL, " +
                    "FOREIGN KEY(`cardId`) REFERENCES `flashcards`(`cardId`) ON UPDATE NO ACTION ON DELETE CASCADE)"
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_study_progress_cardId` ON `study_progress` (`cardId`)")
            seed(db)
            db.version = 2
        }
    }

    /** Opens the test database with the current schema; Room runs the migrations and validates the result. */
    private fun openMigratedDatabase(legacyTargetLanguage: String): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, TEST_DB)
            .addMigrations(
                AppDatabase.MIGRATION_1_2,
                AppDatabase.migration2To3 { legacyTargetLanguage },
                AppDatabase.MIGRATION_3_4,
                AppDatabase.MIGRATION_4_5,
                AppDatabase.MIGRATION_5_6
            )
            .build()
            .also { database = it }

    private companion object {
        const val TEST_DB = "migration-test"
    }
}

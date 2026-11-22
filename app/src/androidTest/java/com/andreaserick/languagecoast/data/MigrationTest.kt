package com.andreaserick.languagecoast.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.room.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
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

/**
 * Opens hand-built older databases with the current Room schema. Room compares the migrated tables with the schema it
 * was compiled with when it opens the database, so each test also checks that the migrations end in that schema
 * (kept in shared/schemas/). v2 runs every migration; v9 is where the users of v3.x start.
 */
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

    @Test
    fun v9CoastsCardsAndSuggestionsKeepTheirDataAndGetEmptyV10Fields() = runBlocking {
        createV9Database { db ->
            db.execSQL("INSERT INTO coasts (coastId, language, creationDate, goal, bankOffered) VALUES (1, 'German', 0, 'Travel', 12)")
            db.execSQL("INSERT INTO language_islands (islandId, coastId, name, creationDate, emoji, lastStudied) VALUES (1, 1, 'Greetings', 0, '👋', 5)")
            db.execSQL(
                "INSERT INTO flashcards (cardId, islandId, nativeText, targetText, stability, difficulty, lastReviewed, due, " +
                    "alternatives, note, lapses) VALUES (1, 1, 'Good morning', 'Guten Morgen', 3.5, 4.2, 100, 200, 'Morgen!', 'Formal', 2)"
            )
            db.execSQL(
                "INSERT INTO suggested_sentences (suggestionId, coastId, nativeText, targetText, category, emoji, skipped, creationDate) " +
                    "VALUES (1, 1, 'Thank you', 'Danke', 'Basics', '🙏', 0, 0)"
            )
        }

        val dao = openMigratedDatabase(legacyTargetLanguage = "German").languageCoastDao()

        val coast = dao.getAllCoasts().first().single()
        assertEquals("Travel", coast.goal)
        assertEquals(12, coast.bankOffered)
        assertEquals(null, coast.region)
        val card = dao.getCardsForIsland(1).first().single()
        assertEquals(3.5, card.stability!!, 0.0)
        assertEquals(200L, card.due)
        assertEquals(listOf("Morgen!"), card.alternatives)
        assertEquals(2, card.lapses)
        val suggestion = dao.getSuggestionsOnce(1).single()
        assertEquals("Danke", suggestion.targetText)
        assertTrue(suggestion.alternatives.isEmpty() && suggestion.note == null)
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

    /** Creates the database as schema version 9 had it (shared/schemas/…/10.json without the v10 columns). */
    private fun createV9Database(seed: (SQLiteDatabase) -> Unit) {
        SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(TEST_DB), null).use { db ->
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `coasts` (`coastId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `language` TEXT NOT NULL, " +
                    "`creationDate` INTEGER NOT NULL, `goal` TEXT, `bankOffered` INTEGER NOT NULL DEFAULT 0)"
            )
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_coasts_language` ON `coasts` (`language`)")
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `language_islands` (`islandId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`coastId` INTEGER NOT NULL, `name` TEXT NOT NULL, `creationDate` INTEGER NOT NULL, `emoji` TEXT, `lastStudied` INTEGER, " +
                    "FOREIGN KEY(`coastId`) REFERENCES `coasts`(`coastId`) ON UPDATE NO ACTION ON DELETE CASCADE )"
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_language_islands_coastId` ON `language_islands` (`coastId`)")
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `flashcards` (`cardId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `islandId` INTEGER NOT NULL, " +
                    "`nativeText` TEXT NOT NULL, `targetText` TEXT NOT NULL, `notes` TEXT, `stability` REAL, `difficulty` REAL, " +
                    "`lastReviewed` INTEGER, `due` INTEGER, `alternatives` TEXT NOT NULL DEFAULT '', `note` TEXT, " +
                    "`lapses` INTEGER NOT NULL DEFAULT 0, " +
                    "FOREIGN KEY(`islandId`) REFERENCES `language_islands`(`islandId`) ON UPDATE NO ACTION ON DELETE CASCADE )"
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_flashcards_islandId` ON `flashcards` (`islandId`)")
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `suggested_sentences` (`suggestionId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`coastId` INTEGER NOT NULL, `nativeText` TEXT NOT NULL, `targetText` TEXT NOT NULL, `category` TEXT NOT NULL, " +
                    "`emoji` TEXT, `skipped` INTEGER NOT NULL, `creationDate` INTEGER NOT NULL, " +
                    "FOREIGN KEY(`coastId`) REFERENCES `coasts`(`coastId`) ON UPDATE NO ACTION ON DELETE CASCADE)"
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_suggested_sentences_coastId` ON `suggested_sentences` (`coastId`)")
            seed(db)
            db.version = 9
        }
    }

    /**
     * Opens the test database as the app does, with settings from before coasts that name [legacyTargetLanguage];
     * Room runs the migrations and validates the result.
     */
    private fun openMigratedDatabase(legacyTargetLanguage: String): AppDatabase {
        val settings = createSettingsDataStore(context.cacheDir.resolve("migration-test-${System.nanoTime()}.preferences_pb").path)
        runBlocking { settings.edit { it[stringPreferencesKey("target_language")] = legacyTargetLanguage } }
        val builder = Room.databaseBuilder<AppDatabase>(context, TEST_DB).setDriver(AndroidSQLiteDriver())
        return buildAppDatabase(builder, settings).also { database = it }
    }

    private companion object {
        const val TEST_DB = "migration-test"
    }
}

package com.andreaserick.languagecoast.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [Coast::class, LanguageIsland::class, Flashcard::class, StudyProgress::class],
    version = 4,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun languageCoastDao(): LanguageCoastDao

    companion object {
        const val NAME = "language_coast_database"

        /** v2 adds indexes on the foreign-key columns so cascading deletes don't scan whole tables. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_flashcards_islandId` ON `flashcards` (`islandId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_study_progress_cardId` ON `study_progress` (`cardId`)")
            }
        }

        /**
         * v3 adds coasts (one per target language) and moves every existing island onto a coast
         * for the language the user was studying before, read lazily via [legacyTargetLanguage].
         */
        fun migration2To3(legacyTargetLanguage: () -> String) = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `coasts` (`coastId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`language` TEXT NOT NULL, `creationDate` INTEGER NOT NULL)"
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_coasts_language` ON `coasts` (`language`)")

                val hasIslands = db.query("SELECT 1 FROM `language_islands` LIMIT 1").use { it.moveToFirst() }
                if (hasIslands) {
                    db.execSQL(
                        "INSERT INTO `coasts` (`coastId`, `language`, `creationDate`) VALUES (1, ?, ?)",
                        arrayOf<Any>(legacyTargetLanguage(), System.currentTimeMillis())
                    )
                }

                // SQLite cannot add a foreign-key column with ALTER TABLE, so rebuild the islands table.
                // Room only enables foreign keys after migrations, so dropping the old table does not
                // cascade into flashcards.
                db.execSQL(
                    "CREATE TABLE `language_islands_new` (`islandId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`coastId` INTEGER NOT NULL, `name` TEXT NOT NULL, `creationDate` INTEGER NOT NULL, " +
                        "FOREIGN KEY(`coastId`) REFERENCES `coasts`(`coastId`) ON UPDATE NO ACTION ON DELETE CASCADE)"
                )
                db.execSQL(
                    "INSERT INTO `language_islands_new` (`islandId`, `coastId`, `name`, `creationDate`) " +
                        "SELECT `islandId`, 1, `name`, `creationDate` FROM `language_islands`"
                )
                db.execSQL("DROP TABLE `language_islands`")
                db.execSQL("ALTER TABLE `language_islands_new` RENAME TO `language_islands`")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_language_islands_coastId` ON `language_islands` (`coastId`)")
            }
        }

        /** v4 adds an emoji and the time of the last completed study session to islands; both start empty. */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `language_islands` ADD COLUMN `emoji` TEXT")
                db.execSQL("ALTER TABLE `language_islands` ADD COLUMN `lastStudied` INTEGER")
            }
        }
    }
}

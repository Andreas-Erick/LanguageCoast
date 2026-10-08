package com.andreaserick.languagecoast.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [LanguageIsland::class, Flashcard::class, StudyProgress::class],
    version = 2,
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
    }
}

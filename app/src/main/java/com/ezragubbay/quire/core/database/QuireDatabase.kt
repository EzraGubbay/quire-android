package com.ezragubbay.quire.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities =
        [
            CourseEntity::class,
            CourseCountsEntity::class,
            CourseSessionEntity::class,
            CourseFileEntity::class,
            NoteEntity::class,
            DownloadEntity::class,
        ],
    version = QuireDatabase.VERSION,
    exportSchema = true,
)
abstract class QuireDatabase : RoomDatabase() {
    abstract fun courseDao(): CourseDao

    abstract fun sessionDao(): SessionDao

    abstract fun fileDao(): FileDao

    abstract fun noteDao(): NoteDao

    abstract fun searchDao(): SearchDao

    abstract fun downloadDao(): DownloadDao

    companion object {
        const val VERSION = 2
        const val NAME = "quire.db"
    }
}

/** 1 -> 2: adds the `downloads` table (download engine, E5-S1). */
val MIGRATION_1_2: Migration =
    object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `downloads` (`fileId` TEXT NOT NULL, `courseSlug` TEXT NOT NULL, " +
                    "`format` TEXT NOT NULL, `state` TEXT NOT NULL, `progressBytes` INTEGER NOT NULL, " +
                    "`totalBytes` INTEGER NOT NULL, `localPath` TEXT, `etag` TEXT, `error` TEXT, " +
                    "`updatedAt` INTEGER NOT NULL, PRIMARY KEY(`fileId`))",
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_downloads_courseSlug` ON `downloads` (`courseSlug`)")
        }
    }

/** Every migration between exported schema versions. */
val ALL_MIGRATIONS: Array<Migration> = arrayOf(MIGRATION_1_2)

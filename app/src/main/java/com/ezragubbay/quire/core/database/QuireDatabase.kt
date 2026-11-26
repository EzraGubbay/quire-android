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
            ReadingPositionEntity::class,
            ThreadEntity::class,
            UploadEntity::class,
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

    abstract fun readingPositionDao(): ReadingPositionDao

    abstract fun threadDao(): ThreadDao

    abstract fun accountCacheDao(): AccountCacheDao

    abstract fun uploadDao(): UploadDao

    companion object {
        const val VERSION = 5
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

/** 2 -> 3: adds the `reading_positions` table (PDF viewer resume, E5-S2). */
val MIGRATION_2_3: Migration =
    object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `reading_positions` (`fileId` TEXT NOT NULL, `page` INTEGER NOT NULL, " +
                    "`updatedAt` INTEGER NOT NULL, PRIMARY KEY(`fileId`))",
            )
        }
    }

/** 3 -> 4: adds the `threads` table (chat thread cache, E6-S2). */
val MIGRATION_3_4: Migration =
    object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `threads` (`id` TEXT NOT NULL, `courseSlug` TEXT NOT NULL, " +
                    "`title` TEXT NOT NULL, `documentId` TEXT, `createdAt` TEXT NOT NULL, " +
                    "`updatedAt` TEXT NOT NULL, PRIMARY KEY(`id`))",
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_threads_courseSlug` ON `threads` (`courseSlug`)")
        }
    }

/** 4 -> 5: adds the `uploads` table (upload queue, E7). */
val MIGRATION_4_5: Migration =
    object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `uploads` (`id` TEXT NOT NULL, `courseSlug` TEXT NOT NULL, " +
                    "`target` TEXT NOT NULL, `format` TEXT NOT NULL, `displayName` TEXT NOT NULL, " +
                    "`localPath` TEXT NOT NULL, `sizeBytes` INTEGER NOT NULL, `state` TEXT NOT NULL, " +
                    "`progressBytes` INTEGER NOT NULL, `serverUploadId` TEXT, `resultId` TEXT, `error` TEXT, " +
                    "`serverFailed` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`id`))",
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_uploads_courseSlug` ON `uploads` (`courseSlug`)")
        }
    }

/** Every migration between exported schema versions. */
val ALL_MIGRATIONS: Array<Migration> =
    arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)

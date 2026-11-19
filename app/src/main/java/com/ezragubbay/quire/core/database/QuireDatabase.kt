package com.ezragubbay.quire.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration

@Database(
    entities =
        [
            CourseEntity::class,
            CourseCountsEntity::class,
            CourseSessionEntity::class,
            CourseFileEntity::class,
            NoteEntity::class,
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

    companion object {
        const val VERSION = 1
        const val NAME = "quire.db"
    }
}

/** Every migration between exported schema versions; empty while the schema is at version 1. */
val ALL_MIGRATIONS: Array<Migration> = emptyArray()

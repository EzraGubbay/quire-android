package com.ezragubbay.quire.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
abstract class CourseDao {
    @Query("SELECT * FROM courses ORDER BY semesterKey DESC, name COLLATE NOCASE")
    abstract fun observeAll(): Flow<List<CourseEntity>>

    @Query("SELECT * FROM courses ORDER BY semesterKey DESC, name COLLATE NOCASE")
    abstract suspend fun getAll(): List<CourseEntity>

    @Query("SELECT * FROM courses WHERE slug = :slug")
    abstract fun observe(slug: String): Flow<CourseEntity?>

    @Query("SELECT * FROM course_counts WHERE courseSlug = :slug")
    abstract fun observeCounts(slug: String): Flow<CourseCountsEntity?>

    /** `@Upsert` updates in place; REPLACE would delete the row and cascade away its children. */
    @Upsert
    protected abstract suspend fun upsert(courses: List<CourseEntity>)

    @Upsert
    protected abstract suspend fun upsert(course: CourseEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertCounts(counts: CourseCountsEntity)

    @Query("DELETE FROM courses WHERE slug NOT IN (:keep)")
    protected abstract suspend fun deleteNotIn(keep: List<String>)

    /**
     * Upserts the server's course list and deletes courses it no longer returns (children cascade). Upsert is
     * an in-place update (not delete+insert) so detail counts and children of surviving courses are kept.
     */
    @Transaction
    open suspend fun replaceAll(courses: List<CourseEntity>) {
        upsert(courses)
        deleteNotIn(courses.map { it.slug })
    }

    @Transaction
    open suspend fun upsertWithCounts(
        course: CourseEntity,
        counts: CourseCountsEntity,
    ) {
        upsert(course)
        upsertCounts(counts)
    }
}

@Dao
abstract class SessionDao {
    @Query("SELECT * FROM sessions WHERE courseSlug = :slug ORDER BY date DESC, kind, number DESC")
    abstract fun observe(slug: String): Flow<List<CourseSessionEntity>>

    @Query("SELECT * FROM sessions WHERE courseSlug = :slug")
    abstract suspend fun getForCourse(slug: String): List<CourseSessionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun insertAll(rows: List<CourseSessionEntity>)

    @Query("DELETE FROM sessions WHERE courseSlug = :slug")
    protected abstract suspend fun deleteForCourse(slug: String)

    @Transaction
    open suspend fun replaceForCourse(
        slug: String,
        rows: List<CourseSessionEntity>,
    ) {
        deleteForCourse(slug)
        insertAll(rows)
    }
}

@Dao
abstract class FileDao {
    @Query("SELECT * FROM files WHERE courseSlug = :slug ORDER BY title COLLATE NOCASE")
    abstract fun observe(slug: String): Flow<List<CourseFileEntity>>

    @Query("SELECT * FROM files WHERE courseSlug = :slug")
    abstract suspend fun getForCourse(slug: String): List<CourseFileEntity>

    /** Id and title of every cached file, for labelling Downloads rows. */
    @Query("SELECT fileId, title FROM files")
    abstract fun observeTitles(): Flow<List<FileTitle>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun insertAll(rows: List<CourseFileEntity>)

    @Query("DELETE FROM files WHERE courseSlug = :slug")
    protected abstract suspend fun deleteForCourse(slug: String)

    @Transaction
    open suspend fun replaceForCourse(
        slug: String,
        rows: List<CourseFileEntity>,
    ) {
        deleteForCourse(slug)
        insertAll(rows)
    }
}

/** Projection of [CourseFileEntity] used to label downloads. */
data class FileTitle(
    val fileId: String,
    val title: String,
)

@Dao
abstract class NoteDao {
    @Query("SELECT * FROM notes WHERE courseSlug = :slug ORDER BY updatedAt DESC")
    abstract fun observe(slug: String): Flow<List<NoteEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun insertAll(rows: List<NoteEntity>)

    @Query("DELETE FROM notes WHERE courseSlug = :slug")
    protected abstract suspend fun deleteForCourse(slug: String)

    @Transaction
    open suspend fun replaceForCourse(
        slug: String,
        rows: List<NoteEntity>,
    ) {
        deleteForCourse(slug)
        insertAll(rows)
    }
}

@Dao
abstract class SearchDao {
    /**
     * Cached courses whose name or code contains [pattern] (a [escapeLike]-escaped fragment), case-insensitively
     * (SQLite `LIKE` folds ASCII; Hebrew has no case). Prefix matches rank before substring matches.
     */
    @Query(
        "SELECT * FROM courses WHERE name LIKE '%' || :pattern || '%' ESCAPE '\\' " +
            "OR code LIKE '%' || :pattern || '%' ESCAPE '\\' " +
            "ORDER BY CASE WHEN name LIKE :pattern || '%' ESCAPE '\\' " +
            "OR code LIKE :pattern || '%' ESCAPE '\\' THEN 0 ELSE 1 END, " +
            "semesterKey DESC, name COLLATE NOCASE LIMIT :limit",
    )
    abstract suspend fun searchCourses(
        pattern: String,
        limit: Int,
    ): List<CourseEntity>

    /** Cached files whose title or type label contains [pattern] (see [searchCourses]); prefix matches first. */
    @Query(
        "SELECT * FROM files WHERE title LIKE '%' || :pattern || '%' ESCAPE '\\' " +
            "OR type LIKE '%' || :pattern || '%' ESCAPE '\\' " +
            "ORDER BY CASE WHEN title LIKE :pattern || '%' ESCAPE '\\' THEN 0 ELSE 1 END, " +
            "title COLLATE NOCASE LIMIT :limit",
    )
    abstract suspend fun searchFiles(
        pattern: String,
        limit: Int,
    ): List<CourseFileEntity>
}

/** Escapes `\`, `%` and `_` so a user query is matched literally by the `LIKE ... ESCAPE '\'` search queries. */
fun escapeLike(query: String): String = query.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")

@Dao
abstract class DownloadDao {
    @Query("SELECT * FROM downloads WHERE fileId = :fileId")
    abstract fun observe(fileId: String): Flow<DownloadEntity?>

    @Query("SELECT * FROM downloads ORDER BY courseSlug, updatedAt DESC")
    abstract fun observeAll(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE fileId = :fileId")
    abstract suspend fun get(fileId: String): DownloadEntity?

    @Query("SELECT COALESCE(SUM(progressBytes), 0) FROM downloads")
    abstract fun observeStorageBytes(): Flow<Long>

    @Query("SELECT COALESCE(SUM(progressBytes), 0) FROM downloads")
    abstract suspend fun storageBytes(): Long

    @Query("SELECT * FROM downloads")
    abstract suspend fun getAll(): List<DownloadEntity>

    @Upsert
    abstract suspend fun upsert(download: DownloadEntity)

    /** No-ops when the row was removed meanwhile, so a late worker write cannot resurrect a removed download. */
    @Update
    abstract suspend fun update(download: DownloadEntity): Int

    @Query("DELETE FROM downloads WHERE fileId = :fileId")
    abstract suspend fun delete(fileId: String)

    @Query("DELETE FROM downloads")
    abstract suspend fun deleteAll()
}

@Dao
abstract class ReadingPositionDao {
    @Query("SELECT * FROM reading_positions WHERE fileId = :fileId")
    abstract suspend fun get(fileId: String): ReadingPositionEntity?

    @Upsert
    abstract suspend fun upsert(position: ReadingPositionEntity)
}

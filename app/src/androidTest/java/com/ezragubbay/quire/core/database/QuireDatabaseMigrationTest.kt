package com.ezragubbay.quire.core.database

import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * Migration-test scaffold. Exported schemas (`app/schemas/`) are wired in as androidTest assets, so each future
 * schema bump adds its `Migration` to [ALL_MIGRATIONS] and a `migrate(N, N+1)` case below.
 */
class QuireDatabaseMigrationTest {
    @get:Rule
    val helper =
        MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            QuireDatabase::class.java,
        )

    @Test
    fun createsCurrentSchemaAndAppliesAllMigrations() {
        helper.createDatabase(TEST_DB, 1).close()
        helper.runMigrationsAndValidate(TEST_DB, QuireDatabase.VERSION, true, *ALL_MIGRATIONS)
    }

    @Test
    fun migrate4To5_addsTheUploadsTable() {
        helper.createDatabase(TEST_DB, 4).close()
        val db = helper.runMigrationsAndValidate(TEST_DB, 5, true, MIGRATION_4_5)
        db.execSQL(
            "INSERT INTO uploads (id, courseSlug, target, format, displayName, localPath, sizeBytes, state, " +
                "progressBytes, serverUploadId, resultId, error, serverFailed, createdAt, updatedAt) " +
                "VALUES ('u1', 'c1', 'document', 'pdf', 'a.pdf', '/x', 1, 'QUEUED', 0, NULL, NULL, NULL, 0, 1, 1)",
        )
        db.query("SELECT COUNT(*) FROM uploads").use {
            it.moveToFirst()
            assertEquals(1, it.getInt(0))
        }
        db.close()
    }

    private companion object {
        const val TEST_DB = "migration-test"
    }
}

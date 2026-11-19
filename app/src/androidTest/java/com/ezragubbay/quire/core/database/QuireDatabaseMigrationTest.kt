package com.ezragubbay.quire.core.database

import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
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

    private companion object {
        const val TEST_DB = "migration-test"
    }
}

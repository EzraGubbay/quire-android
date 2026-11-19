package com.ezragubbay.quire.feature.viewer.pdf

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.ezragubbay.quire.core.database.QuireDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ReadingPositionStoreTest {
    private lateinit var db: QuireDatabase
    private lateinit var store: ReadingPositionStore

    @Before
    fun setUp() {
        db =
            Room
                .inMemoryDatabaseBuilder(
                    ApplicationProvider.getApplicationContext<Context>(),
                    QuireDatabase::class.java,
                ).allowMainThreadQueries()
                .build()
        store = ReadingPositionStore(db.readingPositionDao()) { 7L }
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun unknownFileHasNoPosition() = runBlocking { assertNull(store.lastPage("f1")) }

    @Test
    fun savedPageIsRestoredPerFile() =
        runBlocking {
            store.save("f1", 4)
            store.save("f2", 9)
            assertEquals(4, store.lastPage("f1"))
            assertEquals(9, store.lastPage("f2"))
        }

    @Test
    fun savingAgainOverwrites() =
        runBlocking {
            store.save("f1", 4)
            store.save("f1", 2)
            assertEquals(2, store.lastPage("f1"))
        }
}

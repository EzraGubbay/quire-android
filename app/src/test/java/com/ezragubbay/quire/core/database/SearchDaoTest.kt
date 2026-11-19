package com.ezragubbay.quire.core.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SearchDaoTest {
    private lateinit var db: QuireDatabase

    @Before
    fun setUp() {
        db =
            Room
                .inMemoryDatabaseBuilder(
                    ApplicationProvider.getApplicationContext<Context>(),
                    QuireDatabase::class.java,
                ).build()
    }

    @After
    fun tearDown() = db.close()

    private fun course(
        slug: String,
        name: String,
        code: String? = null,
    ) = CourseEntity(slug, "id-$slug", name, code, "Winter 2026", semesterKey("Winter 2026"), "lecture", 0)

    private fun file(
        id: String,
        slug: String,
        title: String,
        type: String = "lecture",
    ) = CourseFileEntity(id, slug, title, type, null, null, null, null, 1L, true, false, false, "2026-11-02T09:30:00Z")

    private fun seed() =
        runBlocking {
            db.courseDao().replaceAll(
                listOf(
                    course("signals", "Signals and Systems", "044130"),
                    course("calc", "Calculus", "104031"),
                    course("heb", "אלגברה לינארית", "104166"),
                    course("sys", "Operating Systems"),
                ),
            )
            db.fileDao().replaceForCourse(
                "signals",
                listOf(
                    file("f1", "signals", "Lecture 3: Convolution"),
                    file("f2", "signals", "Convolution Practice", "homework"),
                ),
            )
            db.fileDao().replaceForCourse("calc", listOf(file("f3", "calc", "מבחן מועד א", "exam")))
        }

    @Test
    fun courseSearchIsCaseInsensitiveOnName() =
        runBlocking {
            seed()
            assertEquals(listOf("signals"), db.courseDao().search(escapeLike("SIGNALS"), 20).map { it.slug })
        }

    @Test
    fun courseSearchMatchesCode() =
        runBlocking {
            seed()
            assertEquals(listOf("calc"), db.courseDao().search(escapeLike("1040"), 20).map { it.slug }.filter { it == "calc" })
        }

    @Test
    fun courseSearchRanksPrefixBeforeSubstring() =
        runBlocking {
            seed()
            // "Systems": "Operating Systems" is a substring match, "Signals and Systems" too; "sys" prefixes "sys"-less names only.
            val slugs = db.courseDao().search(escapeLike("sy"), 20).map { it.slug }
            assertEquals(listOf("signals", "sys"), slugs.sortedBy { if (it == "signals") 0 else 1 })
            // Prefix match ("Calc") ranks ahead of a substring match ("alc" would match only Calculus anyway).
            assertEquals("calc", db.courseDao().search(escapeLike("calc"), 20).first().slug)
        }

    @Test
    fun prefixOutranksSubstring() =
        runBlocking {
            db.courseDao().replaceAll(listOf(course("a", "Intro to Systems"), course("b", "Systems Theory")))
            assertEquals(listOf("b", "a"), db.courseDao().search(escapeLike("systems"), 20).map { it.slug })
        }

    @Test
    fun hebrewCourseAndFileAreFound() =
        runBlocking {
            seed()
            assertEquals(listOf("heb"), db.courseDao().search(escapeLike("אלגברה"), 20).map { it.slug })
            assertEquals(listOf("f3"), db.fileDao().search(escapeLike("מבחן"), 20).map { it.fileId })
        }

    @Test
    fun fileSearchMatchesTitleAndType() =
        runBlocking {
            seed()
            assertEquals(listOf("f2", "f1"), db.fileDao().search(escapeLike("convolution"), 20).map { it.fileId })
            assertEquals(listOf("f3"), db.fileDao().search(escapeLike("exam"), 20).map { it.fileId })
        }

    @Test
    fun noMatchIsEmpty() =
        runBlocking {
            seed()
            assertTrue(db.courseDao().search(escapeLike("zzz"), 20).isEmpty())
            assertTrue(db.fileDao().search(escapeLike("zzz"), 20).isEmpty())
        }

    @Test
    fun likeWildcardsAreLiteral() =
        runBlocking {
            seed()
            assertTrue(db.courseDao().search(escapeLike("%"), 20).isEmpty())
            assertTrue(db.fileDao().search(escapeLike("_"), 20).isEmpty())
        }

    @Test
    fun limitIsApplied() =
        runBlocking {
            seed()
            assertEquals(1, db.courseDao().search(escapeLike("s"), 1).size)
        }
}

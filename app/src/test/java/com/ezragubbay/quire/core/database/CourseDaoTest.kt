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
class CourseDaoTest {
    private lateinit var db: QuireDatabase

    @Before
    fun setUp() {
        db =
            Room
                .inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), QuireDatabase::class.java)
                .build()
    }

    @After
    fun tearDown() = db.close()

    private fun course(slug: String) = CourseEntity(slug, "id-$slug", slug, null, "Winter 2026", semesterKey("Winter 2026"), "lecture", 0)

    private fun file(
        id: String,
        slug: String,
    ) = CourseFileEntity(id, slug, id, "lecture", null, null, null, null, 1L, true, false, false, "2026-11-02T09:30:00Z")

    @Test
    fun deletingCourseCascadesToChildren() =
        runBlocking {
            db.courseDao().replaceAll(listOf(course("a"), course("b")))
            db.fileDao().replaceForCourse("a", listOf(file("f1", "a")))
            db.fileDao().replaceForCourse("b", listOf(file("f2", "b")))

            db.courseDao().replaceAll(listOf(course("b")))

            assertEquals(listOf("f2"), db.fileDao().getForCourse("b").map { it.fileId })
            assertTrue(db.fileDao().getForCourse("a").isEmpty())
        }

    @Test
    fun replaceForCourseOnlyTouchesThatCourse() =
        runBlocking {
            db.courseDao().replaceAll(listOf(course("a"), course("b")))
            db.fileDao().replaceForCourse("a", listOf(file("f1", "a"), file("f2", "a")))
            db.fileDao().replaceForCourse("b", listOf(file("f3", "b")))
            db.fileDao().replaceForCourse("a", listOf(file("f2", "a")))

            assertEquals(listOf("f2"), db.fileDao().getForCourse("a").map { it.fileId })
            assertEquals(listOf("f3"), db.fileDao().getForCourse("b").map { it.fileId })
        }
}

package com.ezragubbay.quire.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class TopLevelDestinationTest {
    @Test
    fun order_isCoursesDownloadsChats() {
        assertEquals(
            listOf(TopLevelDestination.Courses, TopLevelDestination.Downloads, TopLevelDestination.Chats),
            TopLevelDestination.entries,
        )
    }

    @Test
    fun labels_matchDestinations() {
        assertEquals(
            listOf("Courses", "Downloads", "Chats"),
            TopLevelDestination.entries.map { it.label },
        )
    }

    @Test
    fun routes_mapToTheirDestination() {
        assertSame(TopLevelDestination.Courses, TopLevelDestination.fromRoute(CoursesRoute))
        assertSame(TopLevelDestination.Downloads, TopLevelDestination.fromRoute(DownloadsRoute))
        assertSame(TopLevelDestination.Chats, TopLevelDestination.fromRoute(ChatsRoute))
    }

    @Test
    fun eachDestination_ownsItsRoute() {
        assertEquals(CoursesRoute, TopLevelDestination.Courses.route)
        assertEquals(DownloadsRoute, TopLevelDestination.Downloads.route)
        assertEquals(ChatsRoute, TopLevelDestination.Chats.route)
    }

    @Test
    fun settingsAndUnknownRoutes_areNotTopLevel() {
        assertNull(TopLevelDestination.fromRoute(SettingsRoute))
        assertNull(TopLevelDestination.fromRoute(null))
        assertNull(TopLevelDestination.fromRoute("courses"))
    }

    @Test
    fun startDestination_isCourses() {
        assertSame(TopLevelDestination.Courses, TopLevelDestination.Start)
        assertEquals(CoursesRoute, TopLevelDestination.Start.route)
    }

    @Test
    fun testTags_followContract() {
        assertEquals(
            listOf("nav-courses", "nav-downloads", "nav-chats"),
            TopLevelDestination.entries.map { it.navTag },
        )
        assertEquals(
            listOf("screen-courses", "screen-downloads", "screen-chats"),
            TopLevelDestination.entries.map { it.screenTag },
        )
    }
}

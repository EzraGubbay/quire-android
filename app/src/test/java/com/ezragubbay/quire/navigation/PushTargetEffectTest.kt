package com.ezragubbay.quire.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.navigation.NavGraph
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.ezragubbay.quire.push.PushTarget
import com.ezragubbay.quire.push.PushTargetMissing
import com.ezragubbay.quire.push.PushTargetResolution
import com.ezragubbay.quire.push.PushTargetResolver
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** [PushTargetEffect] over a stand-in shell graph (same routes, empty screens) and a controllable resolver. */
@RunWith(RobolectricTestRunner::class)
class PushTargetEffectTest {
    @get:Rule
    val rule = createComposeRule()

    private var target by mutableStateOf<PushTarget?>(null)
    private val consumed = mutableListOf<PushTarget>()
    private lateinit var nav: NavHostController

    private fun setContent(resolver: PushTargetResolver) {
        rule.setContent {
            val controller = rememberNavController().also { nav = it }
            val snackbar = remember { SnackbarHostState() }
            PushTargetEffect(target, resolver, controller, snackbar) {
                consumed += it
                // What AppViewModel.onPushTargetConsumed does.
                if (target == it) target = null
            }
            Box {
                NavHost(controller, startDestination = CoursesRoute) {
                    composable<CoursesRoute> {}
                    composable<ChatsRoute> {}
                    composable<CourseRoute> {}
                    composable<ViewerRoute> {}
                    composable<NoteReaderRoute> {}
                }
                SnackbarHost(snackbar) { Snackbar(it, Modifier.testTag(PUSH_TARGET_MISSING_TAG)) }
            }
        }
        rule.waitForIdle()
    }

    /** The shell back stack as route class names, bottom first. */
    private fun stack(): List<String> =
        nav.currentBackStack.value
            .map { it.destination }
            .filter { it !is NavGraph }
            .map {
                it.route
                    .orEmpty()
                    .substringBefore('/')
                    .substringBefore('?')
                    .substringAfterLast('.')
            }

    private fun tap(newTarget: PushTarget) {
        rule.runOnIdle { target = newTarget }
        rule.waitForIdle()
    }

    @Test
    fun waitsForTheCheck_thenOpensCoursesCourseFile_thenConsumesOnce() {
        val check = CompletableDeferred<PushTargetResolution>()
        setContent { check.await() }
        val file = PushTarget.File("c", "f1")

        tap(file)
        assertEquals(listOf("CoursesRoute"), stack())
        assertTrue("not consumed before navigating", consumed.isEmpty())

        check.complete(PushTargetResolution(file))
        rule.waitForIdle()

        assertEquals(listOf("CoursesRoute", "CourseRoute", "ViewerRoute"), stack())
        assertEquals(listOf<PushTarget>(file), consumed)
    }

    @Test
    fun missingFile_opensTheCourse_andShowsTheSnackbar() {
        setContent { PushTargetResolution(PushTarget.Course("c"), PushTargetMissing.File) }

        tap(PushTarget.File("c", "gone"))

        assertEquals(listOf("CoursesRoute", "CourseRoute"), stack())
        assertEquals(1, rule.onAllNodesWithTag(PUSH_TARGET_MISSING_TAG).fetchSemanticsNodes().size)
    }

    @Test
    fun replacesTheStack_keepingTheCoursesEntry() {
        setContent(PushTargetResolver.Unchecked)
        val courses = rule.runOnIdle { nav.currentBackStackEntry!!.id }
        rule.runOnIdle {
            nav.navigate(ChatsRoute)
            nav.navigate(ViewerRoute("other", "x"))
        }
        rule.waitForIdle()

        tap(PushTarget.Note("c", "n1"))

        assertEquals(listOf("CoursesRoute", "CourseRoute", "NoteReaderRoute"), stack())
        val bottom = nav.currentBackStack.value.first { it.destination !is NavGraph }
        assertEquals("Courses (and its ViewModel) survive", courses, bottom.id)
    }

    @Test
    fun home_resetsToCourses() {
        setContent(PushTargetResolver.Unchecked)
        rule.runOnIdle { nav.navigate(CourseRoute("c")) }
        rule.waitForIdle()

        tap(PushTarget.Home)

        assertEquals(listOf("CoursesRoute"), stack())
        assertEquals(listOf<PushTarget>(PushTarget.Home), consumed)
    }
}

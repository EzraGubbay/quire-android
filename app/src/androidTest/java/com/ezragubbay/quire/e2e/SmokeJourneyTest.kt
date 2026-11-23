package com.ezragubbay.quire.e2e

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ezragubbay.quire.feature.notes.LatexEngineTest
import com.ezragubbay.quire.testing.Smoke
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.model.FrameworkMethod
import org.junit.runners.model.TestClass

/**
 * Guards the smoke subset that `./gradlew -PtestBuildType=minified connectedMinifiedAndroidTest` runs against the
 * R8-minified build: launch -> pair -> courses -> open PDF -> chat stream -> chat math. The subset is not a copy of those journeys;
 * it is the existing journey cases tagged [Smoke], plus [LatexEngineTest] (chat math: JLaTeXMath's reflective macros,
 * which only R8 can break). This test fails if a step loses its case (or a case is tagged twice
 * for the same step), so the minified run can never silently shrink or drift.
 */
@RunWith(AndroidJUnit4::class)
class SmokeJourneyTest {
    @Test
    fun smokeSubset_coversLaunchPairCoursesOpenPdfChatStreamAndChatMath_exactlyOncePerStep() {
        val tagged =
            listOf(
                AuthJourneyTest::class.java,
                CoursesJourneyTest::class.java,
                ReaderJourneyTest::class.java,
                ChatJourneyTest::class.java,
                LatexEngineTest::class.java,
            ).flatMap { cls -> TestClass(cls).getAnnotatedMethods(Test::class.java).filter { it.isSmoke() } }

        val steps = tagged.map { it.getAnnotation(Smoke::class.java).step }
        assertEquals(listOf("chat-math", "chat-stream", "courses", "launch", "open-pdf", "pair"), steps.sorted())
    }

    private fun FrameworkMethod.isSmoke() = getAnnotation(Smoke::class.java) != null
}

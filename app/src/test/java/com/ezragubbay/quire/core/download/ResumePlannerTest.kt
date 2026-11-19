package com.ezragubbay.quire.core.download

import org.junit.Assert.assertEquals
import org.junit.Test

class ResumePlannerTest {
    @Test
    fun freshDownloadSendsNoConditionalHeaders() {
        assertEquals(RangeRequest(null, null), ResumePlanner.plan(0, null))
    }

    @Test
    fun partialWithEtagResumesWithRangeAndIfRange() {
        assertEquals(RangeRequest("bytes=1024-", "\"abc\""), ResumePlanner.plan(1024, "\"abc\""))
    }

    @Test
    fun partialWithoutValidatorRestarts() {
        assertEquals(RangeRequest(null, null), ResumePlanner.plan(1024, null))
        assertEquals(RangeRequest(null, null), ResumePlanner.plan(1024, " "))
    }

    @Test
    fun okMeansWholeBodyRestart() {
        assertEquals(ResumeOutcome.Restart(500), ResumePlanner.interpret(200, null, 500, 100))
        assertEquals(ResumeOutcome.Restart(-1), ResumePlanner.interpret(200, null, -1, 0))
    }

    @Test
    fun partialAtTheRightOffsetAppends() {
        assertEquals(ResumeOutcome.Append(500), ResumePlanner.interpret(206, "bytes 100-499/500", 400, 100))
        assertEquals(ResumeOutcome.Append(-1), ResumePlanner.interpret(206, "bytes 100-499/*", 400, 100))
    }

    @Test
    fun partialAtTheWrongOffsetOrMalformedIsUnsatisfiable() {
        assertEquals(ResumeOutcome.Unsatisfiable, ResumePlanner.interpret(206, "bytes 0-499/500", 500, 100))
        assertEquals(ResumeOutcome.Unsatisfiable, ResumePlanner.interpret(206, null, 400, 100))
        assertEquals(ResumeOutcome.Unsatisfiable, ResumePlanner.interpret(206, "garbage", 400, 100))
    }

    @Test
    fun rangeNotSatisfiableAndOtherCodes() {
        assertEquals(ResumeOutcome.Unsatisfiable, ResumePlanner.interpret(416, null, -1, 100))
        assertEquals(ResumeOutcome.Unexpected(404), ResumePlanner.interpret(404, null, -1, 0))
    }
}

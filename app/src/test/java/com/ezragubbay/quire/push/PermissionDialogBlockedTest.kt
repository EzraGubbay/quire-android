package com.ezragubbay.quire.push

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PermissionDialogBlockedTest {
    private fun blocked(
        granted: Boolean = false,
        requestedBefore: Boolean = true,
        rationaleBefore: Boolean = false,
        rationaleAfter: Boolean = false,
    ) = permissionDialogWasBlocked(granted, requestedBefore, rationaleBefore, rationaleAfter)

    @Test
    fun deniedTwiceBefore_noDialog_opensSettings() {
        // Android stopped asking: rationale false before and after, and the app had asked before.
        assertTrue(blocked())
    }

    @Test
    fun granted_isNeverBlocked() {
        assertFalse(blocked(granted = true))
    }

    @Test
    fun firstEverRequest_dismissedWithoutAnswer_isNotBlocked() {
        // Rationale stays false either way, but the app never asked: the dialog did show.
        assertFalse(blocked(requestedBefore = false))
    }

    @Test
    fun firstDenial_dialogShowed_isNotBlocked() {
        // The user just denied once: the rationale turns true.
        assertFalse(blocked(requestedBefore = false, rationaleAfter = true))
    }

    @Test
    fun secondAsk_dialogShowed_isNotBlocked() {
        // Denied once before (rationale true), the dialog shows again; denying now makes it permanent.
        assertFalse(blocked(rationaleBefore = true, rationaleAfter = false))
        assertFalse(blocked(rationaleBefore = true, rationaleAfter = true))
    }
}

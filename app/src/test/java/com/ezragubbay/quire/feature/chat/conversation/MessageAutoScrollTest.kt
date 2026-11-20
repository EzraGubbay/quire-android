package com.ezragubbay.quire.feature.chat.conversation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageAutoScrollTest {
    @Test
    fun atBottom_alwaysFollows() {
        assertTrue(nextFollowTail(current = false, atBottom = true, userDragging = false))
        assertTrue(nextFollowTail(current = false, atBottom = true, userDragging = true))
    }

    @Test
    fun userDragAwayFromBottom_detaches() =
        assertFalse(nextFollowTail(current = true, atBottom = false, userDragging = true))

    @Test
    fun contentGrowingOffBottom_withoutDrag_keepsFollowing() =
        // The last delta pushes us off the bottom momentarily; with no user drag the choice is unchanged,
        // so the follow-up scroll effect re-pins to the tail (no stop-following flicker while streaming).
        assertTrue(nextFollowTail(current = true, atBottom = false, userDragging = false))

    @Test
    fun detachedAndIdle_staysDetached() =
        assertFalse(nextFollowTail(current = false, atBottom = false, userDragging = false))
}

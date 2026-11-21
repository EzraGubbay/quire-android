package com.ezragubbay.quire.feature.chat.conversation

/**
 * Whether the message list should keep auto-scrolling to the newest content ("follow the tail").
 *
 * Pure so the conversation's auto-scroll never mutates remembered state during composition (the earlier
 * state-write-in-composition drove unbounded recomposition + scroll and corrupted the LazyColumn
 * subcomposition on-device — "Unbalanced enter/exit"). The caller drives this from an effect off the list's
 * scroll state only:
 *  - resting at the bottom re-engages following (covers the jump-to-latest action and scrolling back down);
 *  - a user drag away from the bottom disengages it (so reading history is not yanked back down);
 *  - otherwise the current choice stands — content growing in place while pinned to the bottom keeps following,
 *    and a programmatic scroll (which emits no drag) never fights itself.
 */
internal fun nextFollowTail(
    current: Boolean,
    atBottom: Boolean,
    userDragging: Boolean,
): Boolean =
    when {
        atBottom -> true
        userDragging -> false
        else -> current
    }

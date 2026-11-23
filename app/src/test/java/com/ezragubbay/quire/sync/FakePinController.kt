package com.ezragubbay.quire.sync

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakePinController(
    pinned: Set<String> = emptySet(),
    capWarning: Boolean = false,
) : PinController {
    val pinned = MutableStateFlow(pinned)
    val capWarning = MutableStateFlow(capWarning)
    val calls = mutableListOf<String>()

    override fun observePinned(): Flow<Set<String>> = pinned

    override fun observeCapWarning(): Flow<Boolean> = capWarning

    override suspend fun pin(slug: String) {
        calls += "pin:$slug"
        pinned.value += slug
    }

    override suspend fun unpin(
        slug: String,
        removeFiles: Boolean,
    ) {
        calls += "unpin:$slug:$removeFiles"
        pinned.value -= slug
    }
}

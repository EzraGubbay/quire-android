package com.ezragubbay.quire.core.data.session

import javax.inject.Inject
import javax.inject.Singleton

/**
 * In-memory server URL and token for a pairing attempt that is not persisted yet. While pairing there is no
 * stored session, but the network layer still needs a server (for `POST /auth/pair`) and the freshly minted
 * token (for the validating `GET /me`). The session providers consult this first; the pairer clears it as soon as
 * the attempt ends, so an unverified token never reaches disk.
 */
@Singleton
class PendingPairing
    @Inject
    constructor() {
        @Volatile
        var serverUrl: String? = null

        @Volatile
        var token: String? = null

        fun clear() {
            serverUrl = null
            token = null
        }
    }

package com.ezragubbay.quire

import org.junit.Assert.assertEquals
import org.junit.Test

class AppIdentityTest {
    @Test
    fun rootTagIsStable() {
        assertEquals("app-root", APP_ROOT_TAG)
    }

    @Test
    fun applicationIdMatchesPackage() {
        assertEquals("com.ezragubbay.quire", BuildConfig.APPLICATION_ID)
    }
}

package com.ezragubbay.quire.feature.update

import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.content.pm.PackageInfo
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class ObtainiumLauncherTest {
    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val pm = app.packageManager
    private val shadow = shadowOf(pm)

    private fun install(
        pkg: String,
        showAppInfo: Boolean,
    ) {
        shadow.installPackage(PackageInfo().apply { packageName = pkg })
        val component = ComponentName(pkg, "$pkg.MainActivity")
        shadow.addOrUpdateActivity(
            ActivityInfo().apply {
                packageName = pkg
                name = component.className
                exported = true
            },
        )
        shadow.addIntentFilterForActivity(
            component,
            IntentFilter(if (showAppInfo) Intent.ACTION_SHOW_APP_INFO else Intent.ACTION_MAIN).apply {
                addCategory(if (showAppInfo) Intent.CATEGORY_DEFAULT else Intent.CATEGORY_LAUNCHER)
            },
        )
    }

    private fun intent() = ObtainiumLauncher.intentFor(pm, "com.ezragubbay.quire", RELEASE)

    @Test
    fun obtainiumWithShowAppInfo_opensQuiresOwnPage() {
        install("dev.imranr.obtainium", showAppInfo = true)
        val intent = intent()
        assertEquals(Intent.ACTION_SHOW_APP_INFO, intent.action)
        assertEquals("dev.imranr.obtainium", intent.`package`)
        assertEquals("com.ezragubbay.quire", intent.getStringExtra(Intent.EXTRA_PACKAGE_NAME))
        assertTrue(ObtainiumLauncher.isInstalled(pm))
    }

    @Test
    fun olderObtainium_fallsBackToItsLaunchIntent() {
        install("dev.imranr.obtainium", showAppInfo = false)
        val intent = intent()
        assertEquals(Intent.ACTION_MAIN, intent.action)
        assertEquals("dev.imranr.obtainium", intent.component?.packageName)
    }

    @Test
    fun fdroidFlavourOnly_targetsTheFdroidPackage() {
        install("dev.imranr.obtainium.fdroid", showAppInfo = true)
        assertEquals("dev.imranr.obtainium.fdroid", intent().`package`)
        assertTrue(ObtainiumLauncher.isInstalled(pm))
    }

    @Test
    fun noObtainium_opensTheReleasePageInTheBrowser() {
        val intent = intent()
        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertEquals(RELEASE, intent.dataString)
        assertFalse(ObtainiumLauncher.isInstalled(pm))
    }

    @Test
    fun open_neverThrows_whenNothingCanHandleIt() {
        // Make startActivity throw ActivityNotFoundException for the Obtainium and the browser attempt alike.
        shadowOf(app).checkActivities(true)
        ObtainiumLauncher.open(app, RELEASE)
        assertNull(shadowOf(app).nextStartedActivity)
    }

    private companion object {
        const val RELEASE = "https://github.com/EzraGubbay/quire-android/releases/tag/v1.2.0"
    }
}

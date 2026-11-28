package com.ezragubbay.quire.feature.update

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.net.toUri

/**
 * Takes the user to Obtainium (which installs Quire updates), else to the release page.
 * Needs the `<queries>` package entries in the manifest to see Obtainium on Android 11+.
 */
object ObtainiumLauncher {
    /** Stable build first, then the F-Droid flavour. */
    val PACKAGES = listOf("dev.imranr.obtainium", "dev.imranr.obtainium.fdroid")

    fun isInstalled(pm: PackageManager): Boolean =
        PACKAGES.any {
            try {
                pm.getPackageInfo(it, 0)
                true
            } catch (_: PackageManager.NameNotFoundException) {
                false
            }
        }

    /**
     * Quire's own page in Obtainium (`SHOW_APP_INFO`, Obtainium 1.7.5+), else Obtainium's launch intent,
     * else the release page in a browser.
     */
    fun intentFor(
        pm: PackageManager,
        selfPackage: String,
        releaseUrl: String,
    ): Intent =
        PACKAGES.firstNotNullOfOrNull { resolvableAppInfo(pm, it, selfPackage) }
            ?: PACKAGES.firstNotNullOfOrNull { pm.getLaunchIntentForPackage(it) }
            ?: Intent(Intent.ACTION_VIEW, releaseUrl.toUri())

    /** Launches [intentFor]; if that fails, the browser; if that fails too, does nothing. */
    fun open(
        context: Context,
        releaseUrl: String,
    ) {
        val primary = intentFor(context.packageManager, context.packageName, releaseUrl)
        try {
            context.startActivity(primary.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: ActivityNotFoundException) {
            try {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW, releaseUrl.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            } catch (_: ActivityNotFoundException) {
                // No browser either: nothing more to try.
            }
        }
    }

    private fun resolvableAppInfo(
        pm: PackageManager,
        pkg: String,
        selfPackage: String,
    ): Intent? =
        Intent(Intent.ACTION_SHOW_APP_INFO)
            .setPackage(pkg)
            .putExtra(Intent.EXTRA_PACKAGE_NAME, selfPackage)
            .takeIf { pm.resolveActivity(it, 0) != null }
}

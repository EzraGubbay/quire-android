package com.ezragubbay.quire.push

import android.util.Log
import androidx.annotation.VisibleForTesting

/**
 * Push logging. Lines carry ids, types and exception class names only: never tokens, payload values or exception
 * messages (Firebase messages can quote the token). [sink] is swapped in JVM tests, where `android.util.Log` is a stub.
 */
object PushLog {
    private const val TAG = "QuirePush"

    @VisibleForTesting
    @Volatile
    var sink: (priority: Int, message: String) -> Unit = { priority, message -> Log.println(priority, TAG, message) }

    fun info(message: String) {
        sink(Log.INFO, message)
    }

    fun warn(message: String) {
        sink(Log.WARN, message)
    }
}

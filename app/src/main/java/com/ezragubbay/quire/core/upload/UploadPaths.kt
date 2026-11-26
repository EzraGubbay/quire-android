package com.ezragubbay.quire.core.upload

import java.io.File

/** Where picked files are staged: `<filesDir>/uploads/<id>` (app-private, survives process death and reboot). */
class UploadPaths(
    filesDir: File,
) {
    val root: File = File(filesDir, "uploads")

    fun staged(id: String): File = File(root, id.replace(Regex("[^A-Za-z0-9._-]"), "_"))

    /** Removes every staged copy (sign-out). */
    fun deleteAll() {
        root.deleteRecursively()
    }
}

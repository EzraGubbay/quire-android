package com.ezragubbay.quire.feature.viewer.share

import java.io.InputStream
import java.io.OutputStream

/** Copies a stream's bytes to another. Pure; callers own opening and closing. */
object StreamCopy {
    private const val BUFFER = 64 * 1024

    /** Returns the number of bytes copied. */
    fun copy(
        input: InputStream,
        output: OutputStream,
    ): Long {
        val buffer = ByteArray(BUFFER)
        var total = 0L
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            output.write(buffer, 0, read)
            total += read
        }
        output.flush()
        return total
    }
}

package com.ezragubbay.quire.feature.viewer.pdf

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.LruCache
import androidx.core.graphics.createBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * A local PDF rendered with the platform [PdfRenderer] (one page open at a time, so renders are serialised and run
 * off the main thread). Bitmaps are cached in an [LruCache] bounded by [BitmapCachePolicy].
 */
class PdfDocument private constructor(
    private val descriptor: ParcelFileDescriptor,
    private val renderer: PdfRenderer,
    private val cacheBytes: Int,
    val pageCount: Int,
    /** Height / width of the first page; the placeholder ratio for pages that are not rendered yet. */
    val firstAspect: Float,
) {
    private data class Key(
        val index: Int,
        val width: Int,
    )

    private val mutex = Mutex()
    private var closed = false
    private val cache =
        object : LruCache<Key, Bitmap>(cacheBytes) {
            override fun sizeOf(
                key: Key,
                value: Bitmap,
            ) = value.byteCount
        }

    /** Renders page [index] at [targetWidth] px (shrunk to the cache budget); null when closed or on failure. */
    suspend fun render(
        index: Int,
        targetWidth: Int,
    ): Bitmap? =
        withContext(Dispatchers.Default) {
            mutex.withLock {
                if (closed || index !in 0 until pageCount) return@withLock null
                renderer.openPage(index).use { page ->
                    val aspect = page.height.toFloat() / page.width
                    val width = BitmapCachePolicy.fitWidth(targetWidth, aspect, cacheBytes / 2)
                    val key = Key(index, width)
                    cache.get(key) ?: run {
                        val bitmap = createBitmap(width, (width * aspect).toInt().coerceAtLeast(1))
                        bitmap.eraseColor(Color.WHITE)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        cache.put(key, bitmap)
                        bitmap
                    }
                }
            }
        }

    suspend fun close() =
        mutex.withLock {
            if (closed) return@withLock
            closed = true
            cache.evictAll()
            renderer.close()
            descriptor.close()
        }

    companion object {
        /** Opens [file]; throws if it is not a readable PDF. */
        suspend fun open(
            file: File,
            cacheBytes: Int,
        ): PdfDocument =
            withContext(Dispatchers.IO) {
                val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                try {
                    val renderer = PdfRenderer(descriptor)
                    val aspect =
                        if (renderer.pageCount > 0) {
                            renderer.openPage(0).use { it.height.toFloat() / it.width }
                        } else {
                            1f
                        }
                    PdfDocument(descriptor, renderer, cacheBytes, renderer.pageCount, aspect)
                } catch (e: java.io.IOException) {
                    descriptor.close()
                    throw e
                } catch (e: SecurityException) {
                    descriptor.close()
                    throw e
                }
            }
    }
}

package com.ezragubbay.quire.feature.auth.scan

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.NotFoundException
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer

/** Decodes QR codes from camera luminance frames with bundled ZXing (no Play Services). */
class QrAnalyzer(
    private val onText: (String) -> Unit,
) : ImageAnalysis.Analyzer {
    private val reader =
        MultiFormatReader().apply {
            setHints(mapOf(DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE)))
        }

    override fun analyze(image: ImageProxy) {
        try {
            val plane = image.planes[0]
            val buffer = plane.buffer
            val data = ByteArray(buffer.remaining()).also { buffer.get(it) }
            // The Y plane may be padded: rowStride >= width.
            val source =
                PlanarYUVLuminanceSource(data, plane.rowStride, image.height, 0, 0, image.width, image.height, false)
            onText(reader.decodeWithState(BinaryBitmap(HybridBinarizer(source))).text)
        } catch (_: NotFoundException) {
            // No QR in this frame.
        } catch (_: RuntimeException) {
            // Malformed frame; try the next one.
        } finally {
            reader.reset()
            image.close()
        }
    }
}

package com.rameshwx.httprequestwiththread.data.image

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import javax.inject.Inject

fun interface ProductImageDecoder {
    fun decode(bytes: ByteArray): Bitmap?
}

class AndroidProductImageDecoder @Inject constructor() : ProductImageDecoder {
    override fun decode(bytes: ByteArray): Bitmap? {
        if (bytes.isEmpty()) return null

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, MAX_DECODED_EDGE)
            inPreferredConfig = Bitmap.Config.RGB_565
        }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
    }

    private fun sampleSize(width: Int, height: Int, maxEdge: Int): Int {
        var sample = 1
        while (maxOf(width / (sample * 2), height / (sample * 2)) > maxEdge) {
            sample *= 2
        }
        return sample
    }

    private companion object {
        const val MAX_DECODED_EDGE = 192
    }
}

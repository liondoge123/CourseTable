package com.coursetable.app.importer

import android.graphics.Bitmap
import android.graphics.Color

/** Lightweight alternate view for faded print. No native library/model dependency. */
object OcrImagePreprocessor {
    fun highContrast(source: Bitmap): Bitmap {
        val pixels = IntArray(source.width * source.height)
        source.getPixels(pixels, 0, source.width, 0, 0, source.width, source.height)
        val histogram = IntArray(256)
        for (p in pixels) histogram[gray(p)]++
        // Otsu separates the printed ink from pale grid lines and background.
        val total = pixels.size
        val sum = histogram.indices.sumOf { it.toLong() * histogram[it] }
        var backgroundCount = 0L; var backgroundSum = 0L; var best = -1.0; var threshold = 160
        for (i in 0..254) {
            backgroundCount += histogram[i]; backgroundSum += i.toLong() * histogram[i]
            val foregroundCount = total - backgroundCount
            if (backgroundCount == 0L || foregroundCount == 0L) continue
            val difference = backgroundSum.toDouble() / backgroundCount - (sum - backgroundSum).toDouble() / foregroundCount
            val variance = backgroundCount * foregroundCount.toDouble() * difference * difference
            if (variance > best) { best = variance; threshold = i }
        }
        // Retain anti-aliasing near the threshold; hard binarization can erase thin strokes.
        for (i in pixels.indices) {
            val value = ((gray(pixels[i]) - threshold + 35) * 255 / 70).coerceIn(0, 255)
            pixels[i] = Color.rgb(value, value, value)
        }
        return Bitmap.createBitmap(pixels, source.width, source.height, Bitmap.Config.ARGB_8888)
    }
    private fun gray(p: Int) = (Color.red(p) * 299 + Color.green(p) * 587 + Color.blue(p) * 114) / 1000
}

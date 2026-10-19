package com.coursetable.app.importer

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Rect
import kotlin.math.abs
import kotlin.math.roundToInt

object OcrImagePreprocessor {
    /** Hashes only shortlist periods; every pixel is compared before discarding a copy. */
    internal fun exactRepeatedHeight(source: Bitmap): Int? {
        if (source.height < 192) return null
        val row = IntArray(source.width)
        val hashes = IntArray(source.height)
        for (y in hashes.indices) {
            source.getPixels(row, 0, row.size, 0, y, row.size, 1)
            hashes[y] = row.contentHashCode()
        }
        if (hashes.toSet().size < 4) return null
        val reference = IntArray(source.width)
        for (height in 96..source.height / 2) {
            if (source.height % height != 0 || hashes.indices.any { hashes[it] != hashes[it % height] }) continue
            var identical = true
            for (y in height until source.height) {
                source.getPixels(row, 0, row.size, 0, y, row.size, 1)
                source.getPixels(reference, 0, reference.size, 0, y % height, reference.size, 1)
                if (!row.contentEquals(reference)) { identical = false; break }
            }
            if (identical) return height
        }
        return null
    }
    fun highContrast(source: Bitmap): Bitmap {
        val pixels = IntArray(source.width * source.height)
        source.getPixels(pixels, 0, source.width, 0, 0, source.width, source.height)
        val histogram = IntArray(256)
        pixels.forEach { histogram[gray(it)]++ }
        val sum = histogram.indices.sumOf { it.toLong() * histogram[it] }
        var n = 0L; var subtotal = 0L; var best = -1.0; var threshold = 160
        for (i in 0..254) {
            n += histogram[i]; subtotal += i.toLong() * histogram[i]
            val foreground = pixels.size - n
            if (n == 0L || foreground == 0L) continue
            val difference = subtotal.toDouble() / n - (sum - subtotal).toDouble() / foreground
            val variance = n * foreground.toDouble() * difference * difference
            if (variance > best) { best = variance; threshold = i }
        }
        for (i in pixels.indices) {
            val value = ((gray(pixels[i]) - threshold + 35) * 255 / 70).coerceIn(0, 255)
            pixels[i] = Color.rgb(value, value, value)
        }
        return Bitmap.createBitmap(pixels, source.width, source.height, Bitmap.Config.ARGB_8888)
    }

    /** Projection score of long ruled lines; only apply a clearly better small angle. */
    fun deskew(source: Bitmap): Bitmap {
        val step = (source.width / 600).coerceAtLeast(1)
        val width = source.width / step; val height = source.height / step
        if (width < 100 || height < 100) return source
        val points = mutableListOf<Pair<Int, Int>>()
        for (y in 0 until height step 2) for (x in 0 until width step 2) {
            val pixel = source.getPixel(x * step, y * step)
            if (gray(pixel) < 160 && maxOf(Color.red(pixel), Color.green(pixel), Color.blue(pixel)) - minOf(Color.red(pixel), Color.green(pixel), Color.blue(pixel)) < 25) points += x to y
        }
        fun score(angle: Double): Double {
            val rows = IntArray(height + width / 8 + 4)
            val slope = kotlin.math.tan(Math.toRadians(angle))
            for ((x, y) in points) {
                val row = (y - slope * (x - width / 2)).roundToInt() + width / 16
                if (row in rows.indices) rows[row]++
            }
            return rows.filter { it > width / 8 }.sumOf { it.toDouble() * it }
        }
        val baseline = score(0.0)
        val best = (-8..8).map { it * .5 }.maxBy { score(it) }
        if (abs(best) < .5 || score(best) <= baseline * 1.3) return source
        val rotated = Bitmap.createBitmap(source, 0, 0, source.width, source.height, Matrix().apply { setRotate((-best).toFloat()) }, true)
        return try { Bitmap.createBitmap(rotated.width, rotated.height, Bitmap.Config.ARGB_8888).also { output ->
            android.graphics.Canvas(output).apply { drawColor(Color.WHITE); drawBitmap(rotated, 0f, 0f, null) }
        } } finally { if(rotated !== source) rotated.recycle() }
    }

    /** Find ink rows independently of ML Kit, excluding solid grid lines. */
    fun inkLines(source: Bitmap, region: Rect): List<Rect> {
        val result = mutableListOf<Rect>()
        val histogram = IntArray(256)
        val row = IntArray(region.width())
        var samples = 0
        for (y in region.top until region.bottom step 2) {
            source.getPixels(row, 0, row.size, region.left, y, row.size, 1)
            for (x in row.indices step 2) { histogram[gray(row[x])]++; samples++ }
        }
        val totalGray = histogram.indices.sumOf { it.toLong() * histogram[it] }
        var count = 0L; var sum = 0L; var best = -1.0; var threshold = 160
        for (shade in 0..254) {
            count += histogram[shade]; sum += shade.toLong() * histogram[shade]
            val rest = samples - count
            if (count == 0L || rest == 0L) continue
            val difference = sum.toDouble() / count - (totalGray - sum).toDouble() / rest
            val variance = count * rest.toDouble() * difference * difference
            if (variance > best) { best = variance; threshold = shade }
        }
        // Include the selected foreground bin, but keep pale table rules out of the text mask.
        threshold = (threshold + 1).coerceAtMost(225)
        var start = -1; var last = -1
        for (y in region.top until region.bottom) {
            source.getPixels(row, 0, row.size, region.left, y, row.size, 1)
            var ink = 0
            for (x in row.indices step 2) if (gray(row[x]) < threshold) ink++
            val content = ink >= 2 && ink < region.width() * .35
            if (content) { if (start < 0) start = y; last = y }
            if (start >= 0 && (y - last > 3 || y == region.bottom - 1)) {
                if (last - start >= 3) result += Rect(region.left, (start - 2).coerceAtLeast(region.top), region.right, (last + 3).coerceAtMost(region.bottom))
                start = -1
            }
        }
        return result
    }
    private fun gray(pixel: Int) = (Color.red(pixel) * 299 + Color.green(pixel) * 587 + Color.blue(pixel) * 114) / 1000
}

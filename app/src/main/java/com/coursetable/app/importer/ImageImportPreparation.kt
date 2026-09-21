package com.coursetable.app.importer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.RectF
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import java.io.File
import kotlin.math.ceil
import kotlin.math.floor

/** Coordinates always refer to the upright image, independently of preview zoom. */
data class ImageSelection(val left: Float = 0f, val top: Float = 0f, val right: Float = 1f, val bottom: Float = 1f) {
    init {
        require(listOf(left, top, right, bottom).all { it.isFinite() })
        require(left >= 0 && top >= 0 && right <= 1 && bottom <= 1 && right > left && bottom > top)
    }
}

class PreparedImportImage(val directory: File, val original: File, val preview: File, val rawWidth: Int, val rawHeight: Int, val orientation: Int) : java.io.Closeable {
    val width get() = if (orientation in 5..8) rawHeight else rawWidth
    val height get() = if (orientation in 5..8) rawWidth else rawHeight
    override fun close() { directory.deleteRecursively() }
}

object ImageImportPreparation {
    fun orientationMatrix(orientation: Int): Matrix = Matrix().apply {
        when (orientation) {
            2 -> setScale(-1f, 1f)
            3 -> setRotate(180f)
            4 -> setScale(1f, -1f)
            5 -> { setRotate(90f); postScale(-1f, 1f) }
            6 -> setRotate(90f)
            7 -> { setRotate(90f); postScale(1f, -1f) }
            8 -> setRotate(270f)
        }
    }

    /** Only copies/decodes the image; never calls an OCR engine. */
    fun prepare(context: Context, uri: Uri): PreparedImportImage {
        val directory = File(context.cacheDir, "image-selection-${java.util.UUID.randomUUID()}").apply { mkdirs() }
        try {
            val original = File(directory, "original")
            ImportPolicy.copyToFile(context, uri, original, DetectedImportType.IMAGE)
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(original.absolutePath, bounds)
            require(bounds.outWidth > 0 && bounds.outHeight > 0) { "图片格式无法读取" }
            require(bounds.outWidth <= ImportPolicy.MAX_IMAGE_DIMENSION && bounds.outHeight <= ImportPolicy.MAX_IMAGE_DIMENSION) {
                "图片尺寸过大，无法安全导入"
            }
            require(bounds.outWidth.toLong() * bounds.outHeight <= ImportPolicy.MAX_IMAGE_SOURCE_PIXELS) {
                "图片像素过多，无法安全导入"
            }
            val orientation = runCatching { ExifInterface(original.absolutePath).getAttributeInt(ExifInterface.TAG_ORIENTATION, 1) }.getOrDefault(1)
            val prepared = PreparedImportImage(directory, original, File(directory, "preview.jpg"), bounds.outWidth, bounds.outHeight, orientation)
            val bitmap = decodeSelection(prepared, ImageSelection())
            try { prepared.preview.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it) } } finally { bitmap.recycle() }
            return prepared
        } catch (e: Exception) { directory.deleteRecursively(); throw e }
    }

    /** Decode the chosen region first, then downsample it; outside pixels never reach OCR. */
    @Suppress("DEPRECATION")
    fun decodeSelection(image: PreparedImportImage, selection: ImageSelection): Bitmap {
        val matrix = orientationMatrix(image.orientation)
        val rawBounds = RectF(0f, 0f, image.rawWidth.toFloat(), image.rawHeight.toFloat())
        val uprightBounds = RectF(rawBounds)
        matrix.mapRect(uprightBounds)
        matrix.postTranslate(-uprightBounds.left, -uprightBounds.top)
        val inverse = Matrix()
        check(matrix.invert(inverse))
        val crop = RectF(selection.left * image.width, selection.top * image.height, selection.right * image.width, selection.bottom * image.height)
        inverse.mapRect(crop)
        val rect = Rect(floor(crop.left).toInt().coerceIn(0, image.rawWidth - 1), floor(crop.top).toInt().coerceIn(0, image.rawHeight - 1), ceil(crop.right).toInt().coerceIn(1, image.rawWidth), ceil(crop.bottom).toInt().coerceIn(1, image.rawHeight))
        var sample = 1
        while (maxOf(rect.width(), rect.height()) / sample > 4096) sample *= 2
        val decoder = BitmapRegionDecoder.newInstance(image.original.absolutePath, false)
        val decoded = try { decoder.decodeRegion(rect, BitmapFactory.Options().apply { inSampleSize = sample }) ?: error("图片无法裁剪") } finally { decoder.recycle() }
        return try {
            Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, orientationMatrix(image.orientation), true).also { if (it !== decoded) decoded.recycle() }
        } catch (e: Exception) { decoded.recycle(); throw e }
    }

    suspend fun recognize(context: Context, image: PreparedImportImage, selection: ImageSelection, settings: com.coursetable.app.data.AppSettings): VisualImportSession {
        val cropped = decodeSelection(image, selection)
        val file = File(image.directory, "recognition.png")
        try { file.outputStream().use { cropped.compress(Bitmap.CompressFormat.PNG, 100, it) } } finally { cropped.recycle() }
        try {
            val session = VisualTimetableImporter.open(context, Uri.fromFile(file), false, settings)
            return VisualImportSession(session.directory, session.pages, session.result, session.path, image, selection)
        } finally { file.delete() }
    }
}

package com.coursetable.app

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.media.ExifInterface
import android.net.Uri
import androidx.test.platform.app.InstrumentationRegistry
import com.coursetable.app.importer.*
import org.junit.Assert.*
import org.junit.Test
import kotlinx.coroutines.runBlocking
import com.coursetable.app.data.AppSettings
import java.io.File

class ImageImportPreparationTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    @Test fun exifOrientationsCropInUprightCoordinatesWithoutChangingOriginal() {
        val expected = listOf(Color.RED, Color.GREEN, Color.YELLOW, Color.BLUE, Color.RED, Color.BLUE, Color.YELLOW, Color.GREEN)
        for (orientation in 1..8) {
            val file = File(context.cacheDir, "orientation-$orientation.jpg")
            val raw = Bitmap.createBitmap(400, 200, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(raw); val paint = Paint()
            val colors = listOf(Color.RED, Color.GREEN, Color.BLUE, Color.YELLOW)
            colors.forEachIndexed { i, color -> paint.color = color; canvas.drawRect((i % 2) * 200f, (i / 2) * 100f, (i % 2 + 1) * 200f, (i / 2 + 1) * 100f, paint) }
            file.outputStream().use { raw.compress(Bitmap.CompressFormat.JPEG, 100, it) }; raw.recycle()
            ExifInterface(file.absolutePath).apply { setAttribute(ExifInterface.TAG_ORIENTATION, orientation.toString()); saveAttributes() }
            val before = file.readBytes()
            val image = ImageImportPreparation.prepare(context, Uri.fromFile(file))
            try {
                assertEquals(if (orientation in 5..8) 200 else 400, image.width)
                val crop = ImageImportPreparation.decodeSelection(image, ImageSelection(0f, 0f, .5f, .5f))
                try {
                    val actual = crop.getPixel(crop.width / 2, crop.height / 2)
                    val e = expected[orientation - 1]
                    assertTrue("orientation $orientation", kotlin.math.abs(Color.red(actual) - Color.red(e)) < 20 && kotlin.math.abs(Color.green(actual) - Color.green(e)) < 20 && kotlin.math.abs(Color.blue(actual) - Color.blue(e)) < 20)
                } finally { crop.recycle() }
                assertArrayEquals(before, file.readBytes())
            } finally { image.close(); file.delete() }
            assertFalse(image.directory.exists())
        }
    }
    @Test fun longScreenshotCropsBeforeDownsamplingAndExcludesOutsidePixels() {
        val file = File(context.cacheDir, "long-selection.png")
        val bitmap = Bitmap.createBitmap(1200, 9000, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.RED)
        Canvas(bitmap).drawRect(0f, 4050f, 1200f, 4950f, Paint().apply { color = Color.GREEN })
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
        val image = ImageImportPreparation.prepare(context, Uri.fromFile(file))
        try {
            val crop = ImageImportPreparation.decodeSelection(image, ImageSelection(0f, .45f, 1f, .55f))
            try {
                assertEquals(1200, crop.width); assertEquals(900, crop.height)
                assertEquals(Color.GREEN, crop.getPixel(0, 0)); assertEquals(Color.GREEN, crop.getPixel(crop.width - 1, crop.height - 1))
            } finally { crop.recycle() }
        } finally { image.close(); file.delete() }
    }
    @Test fun selectedTableRecognizesOnlyItsOwnRecords() = runBlocking {
        val sample = InstrumentationRegistry.getInstrumentation().context.assets.open("selection-details.jpg").use { BitmapFactory.decodeStream(it) }
        val width = sample.width + 200; val height = sample.height + 400
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.WHITE)
        val canvas = Canvas(bitmap)
        canvas.drawText("干扰课程 星期一 1-2节 1-18周", 10f, 100f, Paint().apply { color = Color.BLACK; textSize = 50f })
        canvas.drawBitmap(sample, 100f, 200f, null)
        val file = File(context.cacheDir, "table-with-outside-text.png")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
        val image = ImageImportPreparation.prepare(context, Uri.fromFile(file))
        try {
            val selection = ImageSelection(100f / width, 200f / height, (100f + sample.width) / width, (200f + sample.height) / height)
            val session = ImageImportPreparation.recognize(context, image, selection, AppSettings())
            try {
                val details = session.result.candidates.joinToString("\n") { it.name }
                assertEquals(details, 14, session.result.candidates.size)
                assertFalse(details.contains("干扰课程"))
                assertEquals(selection, session.selection)
                assertSame(image, session.sourceImage)
            } finally { session.close() }
        } finally { sample.recycle(); image.close(); file.delete() }
    }

}

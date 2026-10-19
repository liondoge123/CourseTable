package com.coursetable.app

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import androidx.test.platform.app.InstrumentationRegistry
import com.coursetable.app.data.AppSettings
import com.coursetable.app.importer.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class OcrRepeatedPageTest {
    @Test fun earlyRecognitionMapsBackToOriginalAndRejectsOneChangedPixel(): Unit = runBlocking {
        val assets = InstrumentationRegistry.getInstrumentation().context.assets
        val original = assets.open("selection-details.jpg").use { BitmapFactory.decodeStream(it) }
        val long = Bitmap.createBitmap(original.width, original.height * 3, Bitmap.Config.ARGB_8888)
        try {
            Canvas(long).apply { repeat(3) { drawBitmap(original, 0f, (it * original.height).toFloat(), null) } }
            val result = ImageTimetableOcr.recognizeRepeatedDetails(long, AppSettings())
            assertNotNull(result)
            val (tokens, parsed) = result!!
            assertEquals(12, parsed.candidates.size)
            assertTrue(tokens.isNotEmpty())
            assertTrue(tokens.all { it.cy >= original.height * 2 })
            assertTrue(parsed.regions.all { it.top >= 0 && it.bottom <= 1f / 3 + .001f })
            assertTrue(parsed.candidates.all { c -> parsed.regions.any { it.id == c.sourceRegion } })
            long.setPixel(3, original.height + 20, Color.RED)
            assertNull(ImageTimetableOcr.recognizeRepeatedDetails(long, AppSettings()))
        } finally { long.recycle(); original.recycle(); OcrEngine.release(); TinyTextRecognizer.release() }
    }
}

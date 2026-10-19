package com.coursetable.app

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Rect
import androidx.test.platform.app.InstrumentationRegistry
import com.coursetable.app.importer.OcrEngine
import com.coursetable.app.importer.OcrImagePreprocessor
import com.coursetable.app.importer.TinyTextRecognizer
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Test

/** Diagnostic experiment: fixture coordinates are test data, never recognition rules. */
class OcrCellDiagnosticsTest {
    @Test fun compareIndependentNameCrops(): Unit = runBlocking {
        val assets = InstrumentationRegistry.getInstrumentation().context.assets
        val expected = assets.open("selection-details.expected.json").bufferedReader().use { JSONObject(it.readText()) }
        val original = assets.open("selection-details.jpg").use { BitmapFactory.decodeStream(it) }
        try {
            val edges = expected.getJSONArray("rowEdges")
            for (i in 0 until edges.length() - 1) {
                val crop = Bitmap.createBitmap(original, 294, edges.getInt(i) + 3, 193, edges.getInt(i + 1) - edges.getInt(i) - 6)
                val contrast = OcrImagePreprocessor.highContrast(crop)
                try {
                    suspend fun read(bitmap: Bitmap, scale: Float) = OcrEngine.recognize(bitmap, maximumScale = scale, useTiny = false)
                        .sortedWith(compareBy({ (it.top / 12).toInt() }, { it.left })).joinToString("") { it.text }
                    val report = JSONObject().put("row", i).put("expected", expected.getJSONArray("names").getString(i))
                        .put("crop2", read(crop, 2f)).put("crop4", read(crop, 4f)).put("contrast", read(contrast, 4f))
                    val lines = OcrImagePreprocessor.inkLines(crop, Rect(0, 0, crop.width, crop.height))
                    val tinyText = StringBuilder()
                    val tinyContrast = StringBuilder()
                    val lineText = StringBuilder()
                    val confidences = org.json.JSONArray()
                    for (line in lines) {
                        val segment = Bitmap.createBitmap(crop, line.left, line.top, line.width(), line.height())
                        try {
                            val prediction = TinyTextRecognizer.recognize(segment)
                            tinyText.append(prediction?.text.orEmpty())
                            confidences.put(prediction?.confidence ?: 0f)
                            lineText.append(read(segment, 4f))
                            val enhanced = OcrImagePreprocessor.highContrast(segment)
                            try { tinyContrast.append(TinyTextRecognizer.recognize(enhanced)?.let { "${it.text}:${it.confidence};" }) }
                            finally { enhanced.recycle() }
                        } finally { if (segment !== crop) segment.recycle() }
                    }
                    report.put("tinyLines", tinyText).put("tinyContrast", tinyContrast).put("lineConfidence", confidences).put("mlLines", lineText)
                    android.util.Log.i("OcrCellDiagnostics", report.toString())
                } finally { crop.recycle(); contrast.recycle() }
            }
        } finally { original.recycle(); OcrEngine.release(); TinyTextRecognizer.release() }
    }
}

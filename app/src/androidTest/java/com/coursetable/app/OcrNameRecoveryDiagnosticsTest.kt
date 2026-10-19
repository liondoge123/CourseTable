package com.coursetable.app

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import androidx.test.platform.app.InstrumentationRegistry
import com.coursetable.app.importer.*
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Test

class OcrNameRecoveryDiagnosticsTest {
    @Test fun compareUncertainWrappedAndShortCells(): Unit = runBlocking {
        val assets = InstrumentationRegistry.getInstrumentation().context.assets
        val expected = assets.open("selection-details.expected.json").bufferedReader().use { JSONObject(it.readText()) }
        val original = assets.open("selection-details.jpg").use { BitmapFactory.decodeStream(it) }
        try {
            for (faded in listOf(false, true)) {
                val image = if (!faded) original else Bitmap.createBitmap(original.width, original.height, Bitmap.Config.ARGB_8888).also {
                    Canvas(it).apply { drawColor(Color.WHITE); drawBitmap(original, 0f, 0f, Paint().apply { alpha = 140 }) }
                }
                try {
                    for (row in listOf(5, 9)) {
                        val edges = expected.getJSONArray("rowEdges")
                        val crop = Bitmap.createBitmap(image, 294, edges.getInt(row) + 3, 193, edges.getInt(row + 1) - edges.getInt(row) - 6)
                        suspend fun read(b: Bitmap) = OcrEngine.recognize(b, 4f, useTiny = false)
                            .sortedWith(compareBy({ (it.top / 12).toInt() }, { it.left })).joinToString("") { it.text }.trim('|')
                        val enhanced = OcrImagePreprocessor.highContrast(crop)
                        try {
                            val report = JSONObject().put("row", row).put("faded", faded).put("whole", read(crop)).put("enhancedWhole", read(enhanced))
                            val lines = JSONArray()
                            for (line in OcrImagePreprocessor.inkLines(crop, Rect(0, 0, crop.width, crop.height))) {
                                val segment = Bitmap.createBitmap(crop, line.left, line.top, line.width(), line.height())
                                val padded = Bitmap.createBitmap(segment.width + 8, segment.height + 8, Bitmap.Config.ARGB_8888).also {
                                    Canvas(it).apply { drawColor(Color.WHITE); drawBitmap(segment, 4f, 4f, null) }
                                }
                                try {
                                    fun asJson(p: TinyTextRecognizer.Prediction?) = if (p == null) JSONObject.NULL else JSONObject().put("text", p.text).put("confidence", p.confidence)
                                    lines.put(JSONObject().put("plain", asJson(TinyTextRecognizer.recognize(segment)))
                                        .put("padded", asJson(TinyTextRecognizer.recognize(padded))))
                                } finally { if (segment !== crop) segment.recycle(); padded.recycle() }
                            }
                            report.put("lines", lines)
                            android.util.Log.i("OcrNameRecovery", report.toString())
                        } finally { crop.recycle(); enhanced.recycle() }
                    }
                } finally { if (image !== original) image.recycle() }
            }
        } finally { original.recycle(); OcrEngine.release(); TinyTextRecognizer.release() }
    }
}

package com.coursetable.app

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.test.platform.app.InstrumentationRegistry
import com.coursetable.app.importer.OcrEngine
import com.coursetable.app.importer.OcrImagePreprocessor
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.json.JSONArray
import org.junit.Test
import java.io.File
import java.text.Normalizer

class OcrBenchmarkTest {
    private fun normalized(text: String, schedule: Boolean = false) = Normalizer.normalize(text, Normalizer.Form.NFKC).replace('、', ',').replace('.', ',').replace('~', '-').replace('—', '-').filter { it.isLetterOrDigit() || schedule && it in "-," }
    @Test fun compareOriginalAndContrastWithoutNewModels(): Unit = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val expected = instrumentation.context.assets.open("selection-details.expected.json").bufferedReader().use { JSONObject(it.readText()) }
        val bitmap = instrumentation.context.assets.open("selection-details.jpg").use { BitmapFactory.decodeStream(it) }
        val report = JSONObject()
        try {
            for (view in listOf("original", "contrast", "tiny")) {
                val contrast = view == "contrast"
                val started = System.currentTimeMillis()
                val names = mutableListOf<String>(); val meetings = mutableListOf<String>()
                suspend fun recognize(x: Int, top: Int, width: Int, height: Int): String {
                    val crop = Bitmap.createBitmap(bitmap, x, top, width, height)
                    val input = if (contrast) OcrImagePreprocessor.highContrast(crop) else crop
                    return try { OcrEngine.recognize(input, useTiny = view == "tiny").sortedWith(compareBy({ (it.top / 12).toInt() }, { it.left })).joinToString("") { it.text }.trim('|') }
                    finally { if (input !== crop) input.recycle(); crop.recycle() }
                }
                val edges = expected.getJSONArray("rowEdges")
                for (i in 0 until edges.length() - 1) names.add(recognize(292, edges.getInt(i) + 2, 197, edges.getInt(i + 1) - edges.getInt(i) - 4))
                val tops = expected.getJSONArray("meetingTops")
                for (i in 0 until tops.length()) meetings.add(recognize(988, tops.getInt(i), 264, 30))
                fun matches(actual: List<String>, key: String) = actual.indices.count { normalized(actual[it], key == "meetings") == normalized(expected.getJSONArray(key).getString(it), key == "meetings") }
                report.put(view, JSONObject().put("nameMatches", matches(names, "names")).put("meetingMatches", matches(meetings, "meetings")).put("names", JSONArray(names)).put("meetings", JSONArray(meetings)).put("milliseconds", System.currentTimeMillis() - started))
            }
            File(instrumentation.targetContext.filesDir, "ocr-benchmark.json").writeText(report.toString(2))
            android.util.Log.i("OcrBenchmark", report.toString())
        } finally { bitmap.recycle() }
    }
}

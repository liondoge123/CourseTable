package com.coursetable.app

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.test.platform.app.InstrumentationRegistry
import com.coursetable.app.importer.OcrEngine
import com.coursetable.app.importer.OcrImagePreprocessor
import com.coursetable.app.importer.fieldErrors
import com.coursetable.app.importer.evaluateOcrSchedules
import com.coursetable.app.data.Course
import com.coursetable.app.data.withScheduledWeeks
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
            // Stream results through instrumentation/logcat; leave no device artifact.
            android.util.Log.i("OcrBenchmark", report.toString())
        } finally { bitmap.recycle() }
    }

    @Test fun compareStructuredScreenshotVariantsWithGroundTruth(): Unit = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val expected = instrumentation.context.assets.open("selection-details.expected.json").bufferedReader().use { JSONObject(it.readText()) }
        val original = instrumentation.context.assets.open("selection-details.jpg").use { BitmapFactory.decodeStream(it) }
        val report = JSONObject()
        try {
            val variants = InstrumentationRegistry.getArguments().getString("ocrVariants")?.split(',') ?: listOf("original", "lowContrast", "tilt", "long")
            for (variant in variants) {
                val input = when (variant) {
                    "lowContrast" -> Bitmap.createBitmap(original.width, original.height, Bitmap.Config.ARGB_8888).also { b ->
                        android.graphics.Canvas(b).apply { drawColor(android.graphics.Color.WHITE); drawBitmap(original, 0f, 0f, android.graphics.Paint().apply { alpha = 140 }) }
                    }
                    "tilt" -> Bitmap.createBitmap(original.width + 80, original.height + 80, Bitmap.Config.ARGB_8888).also { b ->
                        android.graphics.Canvas(b).apply { drawColor(android.graphics.Color.WHITE); rotate(2f, b.width / 2f, b.height / 2f); drawBitmap(original, 40f, 40f, null) }
                    }
                    "long" -> Bitmap.createBitmap(original.width, original.height * 3, Bitmap.Config.ARGB_8888).also { b ->
                        android.graphics.Canvas(b).apply { drawColor(android.graphics.Color.WHITE); repeat(3) { drawBitmap(original, 0f, it * original.height.toFloat(), null) } }
                    }
                    else -> original
                }
                val started = System.nanoTime(); val beforeMemory = android.os.Debug.getPss()
                val upright = OcrImagePreprocessor.deskew(input)
                try {
                    val parsed = com.coursetable.app.importer.ImageTimetableOcr.parseImage(upright, com.coursetable.app.data.AppSettings())
                    val expectedNames = (0 until expected.getJSONArray("names").length()).map { normalized(expected.getJSONArray("names").getString(it)) }.toSet()
                    val actualNames = parsed.candidates.map { normalized(it.name) }.toSet(); val matched = expectedNames.intersect(actualNames).size
                    val truth = expected.getJSONArray("sessions")
                    val expectedSessions = (0 until truth.length()).map { index ->
                        val row = truth.getJSONObject(index)
                        val weeks = row.getJSONArray("weeks")
                        Course(name = row.getString("name"), dayOfWeek = row.getInt("dayOfWeek"),
                            startSection = row.getInt("startSection"), duration = row.getInt("duration"),
                            startWeek = 1, endWeek = 17).withScheduledWeeks((0 until weeks.length()).map { weeks.getInt(it) })
                    }
                    val score = evaluateOcrSchedules(expectedSessions, parsed.candidates)
                    report.put(variant, JSONObject().put("courseRecall", matched.toDouble() / expectedNames.size)
                        .put("exactScheduleRecall", score.recall).put("exactSchedulePrecision", score.precision)
                        .put("wholeTableCorrect", score.wholeTableCorrect).put("missingSessions", score.missing)
                        .put("incorrectSessions", score.incorrect).put("incorrectFlagged", score.incorrectFlagged)
                        .put("reviewInterception", score.reviewInterception ?: JSONObject.NULL)
                        .put("syntheticVariant", variant != "original")
                        .put("coursePrecision", if(actualNames.isEmpty()) 0.0 else matched.toDouble() / actualNames.size)
                        .put("sessionCount", parsed.candidates.size).put("invalidCount", parsed.candidates.count { it.fieldErrors(com.coursetable.app.data.AppSettings()).isNotEmpty() })
                        .put("reviewCount", parsed.candidates.count { it.needsReview }).put("milliseconds", (System.nanoTime() - started) / 1_000_000)
                        .put("sampledPssKb", maxOf(beforeMemory, android.os.Debug.getPss())).put("warnings", JSONArray(parsed.warnings)).put("names", JSONArray(actualNames)))
                    android.util.Log.i("OcrVariantBenchmark", JSONObject().put("variant", variant).put("result", report.getJSONObject(variant)).toString())
                    if (InstrumentationRegistry.getArguments().getString("ocrDetails") == "true") parsed.candidates.forEach { c ->
                        android.util.Log.i("OcrCandidate", JSONObject().put("variant", variant).put("name", c.name).put("day", c.dayOfWeek)
                            .put("section", c.startSection).put("duration", c.duration).put("weeks", c.selectedWeeksCsv.ifBlank { "${c.startWeek}-${c.endWeek}" })
                            .put("alternatives", JSONArray(c.alternatives.map { "${it.original}/${it.candidate}" })).toString())
                    }
                } finally { if(upright !== input) upright.recycle(); if(input !== original) input.recycle() }
            }
            android.util.Log.i("OcrStructuredBenchmark", report.toString())
        } finally { original.recycle(); com.coursetable.app.importer.OcrEngine.release(); com.coursetable.app.importer.TinyTextRecognizer.release() }
    }
}

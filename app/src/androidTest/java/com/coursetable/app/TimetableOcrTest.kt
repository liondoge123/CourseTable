package com.coursetable.app

import android.graphics.BitmapFactory
import androidx.test.platform.app.InstrumentationRegistry
import com.coursetable.app.data.AppSettings
import com.coursetable.app.data.scheduledWeeks
import com.coursetable.app.importer.ImageTimetableOcr
import com.coursetable.app.importer.asCourse
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class TimetableOcrTest {
    @Test fun suppliedScreenshotPreservesMeetingsAndFlagsUncertainNames() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().context
        val expected = context.assets.open("selection-details.expected.json").bufferedReader().use { org.json.JSONObject(it.readText()) }
        val bitmap = context.assets.open("selection-details.jpg").use { BitmapFactory.decodeStream(it) }
        try {
            val parsed = ImageTimetableOcr.parseImage(bitmap, AppSettings(totalWeeks = 18))
            val details = parsed.candidates.joinToString("\n") { "${it.name}: ${it.dayOfWeek} ${it.startSection} ${it.asCourse().scheduledWeeks()}" }
            val truth = expected.getJSONArray("sessions")
            assertEquals(details, truth.length(), parsed.candidates.size)
            assertEquals(details, 11, parsed.candidates.map { it.name }.distinct().size)
            assertTrue(details, parsed.candidates.all { it.name.isNotBlank() })
            val powerMeetings = parsed.candidates.filter { it.sourceRegion == "0-details-5" }
            assertEquals(details, listOf(2 to 6, 4 to 1), powerMeetings.map { it.dayOfWeek to it.startSection })
            assertEquals(listOf(4, 9, 10, 14, 15, 16), parsed.candidates.single { it.name == "中国古代史" }.asCourse().scheduledWeeks())
            val expectedSchedules = (0 until truth.length()).map { i ->
                val row = truth.getJSONObject(i); val weeks = row.getJSONArray("weeks")
                listOf(row.getInt("dayOfWeek"), row.getInt("startSection"), row.getInt("duration"), (0 until weeks.length()).map { weeks.getInt(it) })
            }.toSet()
            val actualSchedules = parsed.candidates.map { listOf(it.dayOfWeek, it.startSection, it.duration, it.asCourse().scheduledWeeks()) }.toSet()
            assertEquals(details, expectedSchedules, actualSchedules)
            var matchingNames = 0
            fun normalized(text: String) = java.text.Normalizer.normalize(text, java.text.Normalizer.Form.NFKC).filter { it.isLetterOrDigit() }
            for (candidate in parsed.candidates) {
                val index = candidate.sourceRegion!!.substringAfterLast('-').toInt()
                val name = expected.getJSONArray("names").getString(index)
                if (normalized(candidate.name) == normalized(name)) matchingNames++
                else {
                    assertTrue(details, candidate.needsReview)
                    assertTrue(details, candidate.alternatives.isNotEmpty())
                }
            }
            // Measured improvement over the previous 4/12 exact-schedule baseline.
            assertTrue(details, matchingNames >= 9)
        } finally { bitmap.recycle() }
    }
    @Test fun scannedPdfKeepsPageRegionsAndReleasesCache() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val bitmap = instrumentation.context.assets.open("selection-details.jpg").use { BitmapFactory.decodeStream(it) }
        val pdf = java.io.File(context.cacheDir, "ocr-regression.pdf")
        try {
            val document = android.graphics.pdf.PdfDocument()
            try {
                repeat(2) { index ->
                    val page = document.startPage(android.graphics.pdf.PdfDocument.PageInfo.Builder(bitmap.width, bitmap.height, index + 1).create())
                    page.canvas.drawBitmap(bitmap, 0f, 0f, null); document.finishPage(page)
                }
                pdf.outputStream().use { document.writeTo(it) }
            } finally { document.close() }
            val session = com.coursetable.app.importer.VisualTimetableImporter.open(context, android.net.Uri.fromFile(pdf), true, AppSettings())
            try {
                assertEquals(2, session.pages.size)
                assertTrue(session.result.candidates.isNotEmpty())
                assertEquals(setOf(0, 1), session.result.regions.map { it.page }.toSet())
                assertTrue(session.pages.all { it.image.exists() })
            } finally { session.close() }
            assertFalse(session.directory.exists())
        } finally { bitmap.recycle(); pdf.delete() }
    }
    @Test fun pdfTextLayerKeepsPageCoordinates() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val document = android.graphics.pdf.PdfDocument()
        val output = java.io.ByteArrayOutputStream()
        try {
            repeat(2) { i ->
                val page = document.startPage(android.graphics.pdf.PdfDocument.PageInfo.Builder(600, 800, i + 1).create())
                val paint = android.graphics.Paint().apply { textSize = 22f }
                page.canvas.drawText("Course${i + 1}", 100f, 150f, paint)
                document.finishPage(page)
            }
            document.writeTo(output)
        } finally { document.close() }
        com.tom_roush.pdfbox.android.PDFBoxResourceLoader.init(context)
        val pages = com.coursetable.app.importer.PdfTextReader.extractByPage(output.toByteArray())
        assertEquals(2, pages.size)
        assertTrue(pages[0].any { it.text.contains("Course1") })
        assertTrue(pages[1].any { it.text.contains("Course2") })
        assertEquals(pages[0].first().cx, pages[1].first().cx, 2f)
        assertEquals(pages[0].first().cy, pages[1].first().cy, 2f)
    }
}

package com.coursetable.app

import android.graphics.BitmapFactory
import androidx.test.platform.app.InstrumentationRegistry
import com.coursetable.app.data.AppSettings
import com.coursetable.app.importer.ImageTimetableOcr
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class TimetableOcrTest {
    @Test fun suppliedScreenshotRecognizesAllMeetings() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().context
        val bitmap = context.assets.open("selection-details.jpg").use { BitmapFactory.decodeStream(it) }
        try {
            val parsed = ImageTimetableOcr.parseImage(bitmap, AppSettings(totalWeeks = 18))
            val details = parsed.candidates.joinToString("\n") { "${it.name}: ${it.dayOfWeek} ${it.startSection} ${it.startWeek}-${it.endWeek} ${it.location}" }
            android.util.Log.i("TimetableResult", details)
            assertEquals(details, 14, parsed.candidates.size)
            assertEquals(details, 11, parsed.candidates.map { it.name }.distinct().size)
            assertTrue(details, parsed.candidates.all { it.name.isNotBlank() })
            val powerMeetings = parsed.candidates.filter { it.sourceRegion == "0-details-5" }
            assertEquals(details, 2, powerMeetings.size)
            assertEquals(details, listOf(2 to 6, 4 to 1), powerMeetings.map { it.dayOfWeek to it.startSection })
            assertEquals(details, listOf(4 to 4, 9 to 10, 14 to 16), parsed.candidates.filter { it.name == "中国古代史" }.map { it.startWeek to it.endWeek })
            assertTrue(details, parsed.candidates.filter { it.name == "模拟集成电路设计" }.all { it.needsReview || it.startWeek == 1 && it.endWeek == 17 })
            val expected = context.assets.open("selection-details.expected.json").bufferedReader().use { org.json.JSONObject(it.readText()).getJSONArray("names") }
            fun nameContent(text: String) = java.text.Normalizer.normalize(text, java.text.Normalizer.Form.NFKC).filter { it.isLetterOrDigit() }
            for (row in 0 until expected.length()) assertTrue(details, parsed.candidates.filter { it.sourceRegion == "0-details-$row" }.all { nameContent(it.name) == nameContent(expected.getString(row)) })
            val schedules = listOf(
                listOf(0, 1, 1, 2, 1, 17), listOf(1, 2, 3, 2, 1, 17), listOf(2, 3, 3, 2, 1, 17),
                listOf(3, 4, 3, 3, 1, 17), listOf(4, 2, 9, 2, 1, 17), listOf(5, 2, 6, 2, 1, 17),
                listOf(5, 4, 1, 2, 1, 17), listOf(6, 5, 6, 2, 2, 17), listOf(7, 5, 3, 3, 1, 17),
                listOf(8, 3, 6, 2, 1, 17), listOf(9, 4, 9, 2, 4, 6), listOf(10, 3, 11, 2, 4, 4),
                listOf(10, 3, 11, 2, 9, 10), listOf(10, 3, 11, 2, 14, 16)
            )
            val actual = parsed.candidates.map { listOf(it.sourceRegion!!.substringAfterLast('-').toInt(), it.dayOfWeek, it.startSection, it.duration, it.startWeek, it.endWeek) }
            assertEquals(details, schedules.toSet(), actual.toSet())
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

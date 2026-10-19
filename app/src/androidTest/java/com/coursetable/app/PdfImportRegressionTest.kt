package com.coursetable.app

import android.util.Base64
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import com.coursetable.app.data.AppSettings
import com.coursetable.app.importer.PdfTextReader
import com.coursetable.app.importer.StructuredTimetableParser
import com.coursetable.app.importer.TextToken
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.Assume.assumeNotNull
import com.coursetable.app.importer.*
import com.coursetable.app.data.scheduledWeeks
import kotlinx.coroutines.runBlocking
import java.io.File

/** Original PDF supplied through instrumentation, never bundled with personal headers. */
class PdfImportRegressionTest {
    @Test fun readsOriginalRotatedPdfAndParsesAllPages() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val encoded = InstrumentationRegistry.getArguments().getString("pdfBase64")
        assumeNotNull(encoded)
        com.tom_roush.pdfbox.android.PDFBoxResourceLoader.init(instrumentation.targetContext)
        val pages = PdfTextReader.extractByPage(Base64.decode(encoded, Base64.DEFAULT))
        assertEquals(2, pages.size)
        var inherited = emptyList<TextToken>()
        val all = mutableListOf<CandidateCourse>()
        pages.forEachIndexed { index, tokens ->
            val items = JSONArray(tokens.filterNot { it.text.contains("学号") || it.text.contains("课表") }.map { t ->
                JSONObject().put("text", t.text).put("x0", t.x0).put("x1", t.x1).put("y0", t.y0).put("y1", t.y1)
            })
            val text = JSONObject().put("page", index).put("width", 842).put("height", 595).put("tokens", items).toString()
            text.chunked(2800).forEachIndexed { chunk, value -> Log.i("PdfFixture", "$index/$chunk:$value") }
            val result = StructuredTimetableParser.extract(tokens, 842f, 595f, AppSettings(totalWeeks = 20), index, inherited, knownTeachers = all.map { it.teacher })
            all += result.candidates
            result.candidates.forEach { Log.i("PdfParsed", "$index|${it.name}|${it.dayOfWeek}|${it.startSection}|${it.duration}|${it.startWeek}|${it.endWeek}|${it.teacher}|${it.location}") }
            inherited = tokens.filter { Regex("星期[一二三四五六日]").matches(it.text.trim()) }.ifEmpty { inherited }
        }
        assertEquals("All 15 scheduled meetings must survive", 15, all.count { it.dayOfWeek in 1..7 })
        assertEquals(3, all.count { it.dayOfWeek == 0 })
        assertEquals(listOf(7,13), all.single { it.name == "创业管理" }.asCourse().scheduledWeeks())
    }

    @Test fun originalPdfUsesTextOnlyThroughTheActualImportPipeline() = runBlocking {
        val encoded = InstrumentationRegistry.getArguments().getString("pdfBase64")
        assumeNotNull(encoded)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val source = File(context.cacheDir,"pdf-regression-${java.util.UUID.randomUUID()}.pdf")
        try {
            source.writeBytes(Base64.decode(encoded,Base64.DEFAULT))
            val session = VisualTimetableImporter.open(context,android.net.Uri.fromFile(source),true,AppSettings(totalWeeks=20))
            try {
                assertEquals("文字层直读",session.path)
                assertEquals(2,session.pages.size)
                val regular = session.result.candidates.filter { it.dayOfWeek in 1..7 }
                assertEquals(15,regular.size)
                assertTrue(regular.all { it.fieldErrors(AppSettings(totalWeeks=20)).isEmpty() })
                assertEquals(3,regular.count { it.startSection >= 9 })
                assertEquals("郑召文,朱轶",regular.single { it.name=="通信原理" && it.dayOfWeek==2 }.teacher)
                assertEquals("京江2号楼2111",regular.single { it.name=="数字信号处理" }.location)
                assertEquals(listOf(7,13),regular.single { it.name=="创业管理" }.asCourse().scheduledWeeks())
                assertEquals(3,session.result.candidates.count { it.dayOfWeek==0 && it.needsReview })
            } finally { session.close() }
            assertFalse(session.directory.exists())
        } finally { source.delete() }
    }
}

package com.coursetable.app.importer

import com.coursetable.app.data.AppSettings
import com.coursetable.app.data.scheduledWeeks
import org.junit.Assert.*
import org.junit.Test

class TimetableRoutingTest {
    private val settings = AppSettings(totalWeeks = 18)
    private fun token(x: Float, y: Float, text: String) = TextToken(x-30, y-9, x+30, y+9, text)
    @Test fun synonymsAndOcrSubstitutionMatchWholeHeadersOnly() {
        listOf("课程", "科目名称", "课程名", "课 程 名 称", "课程名祢").forEach { assertTrue(TimetableHeaders.matches(it, TimetableHeaders.Field.NAME)) }
        listOf("课程设计", "自动控制课程", "课程名称大学数学", "1-16周 星期一 1-2节").forEach { assertFalse(TimetableHeaders.matches(it, TimetableHeaders.Field.NAME)) }
        assertTrue(TimetableHeaders.matches("授课教师", TimetableHeaders.Field.TEACHER))
        assertTrue(TimetableHeaders.matches("时间地点", TimetableHeaders.Field.TIME))
    }
    @Test fun failedDetailsClassificationRetriesWeeklyInsteadOfReturningEmptyDraft() {
        val tokens = listOf(token(50f,980f,"课程名"),token(150f,980f,"时间"),
            token(300f,900f,"星期一"), token(500f,900f,"星期二"), token(50f,800f,"第1-2节"),
            token(300f,800f,"高等数学"),token(300f,775f,"1-16周"))
        val parsed = StructuredTimetableParser.extract(tokens,700f,1000f,settings)
        assertEquals("高等数学", parsed.candidates.single().name)
        assertEquals(TimetableRoute.WEEKLY, parsed.routing!!.selected)
        assertEquals(listOf(TimetableRoute.DETAILS, TimetableRoute.WEEKLY), parsed.routing!!.attempts.map { it.route })
    }
    @Test fun misleadingVisualBlockRetriesCoordinateRouteAndPreservesSparseWeeks() {
        val tokens = listOf(token(300f,900f,"星期一"),token(500f,900f,"星期二"),token(50f,800f,"第1-2节"),token(50f,650f,"第3-4节"),
            token(300f,800f,"高等数学"),token(300f,775f,"4、9-10、14-16周"),token(300f,650f,"大学英语"),token(300f,625f,"1-16周"))
        val block = ImportRegion("bad-merged-fill",0,.28f,.16f,.58f,.40f,TimetableLayout.WEEKLY)
        val parsed = StructuredTimetableParser.extract(tokens,700f,1000f,settings,visualCells=listOf(block))
        assertEquals(2, parsed.candidates.size)
        assertEquals(listOf(4,9,10,14,15,16), parsed.candidates.first().asCourse().scheduledWeeks())
        assertEquals(TimetableRoute.COORDINATE, parsed.routing!!.selected)
    }
    @Test fun noHeadersCannotProduceInventedSchedule() {
        val parsed = StructuredTimetableParser.extract(listOf(token(200f,600f,"高等数学")),700f,1000f,settings)
        assertTrue(parsed.candidates.isEmpty())
        assertEquals(0f, parsed.routing!!.attempts.first().confidence)
        assertTrue(parsed.warnings.any { "粘贴" in it })
    }
}

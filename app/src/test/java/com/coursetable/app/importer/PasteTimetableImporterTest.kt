package com.coursetable.app.importer

import com.coursetable.app.data.AppSettings
import com.coursetable.app.data.Course
import com.coursetable.app.data.scheduledWeeks
import org.junit.Assert.*
import org.junit.Test

class PasteTimetableImporterTest {
    private val settings = AppSettings(totalWeeks = 18)
    private fun parse(text: String) = PasteTimetableImporter.parse(text, settings)
    private val row = """{"name":"通信原理Ⅱ","teacher":"张三","location":"A301","dayOfWeek":3,"startSection":3,"duration":2,"weeks":[4,9,10,14,15,16]}"""

    @Test fun fencedAiJsonKeepsUnicodeAndSparseWeeksInOneMeeting() {
        val candidate = parse("```json\n{\"courses\":[$row]}\n```").candidates.single()
        assertEquals("通信原理Ⅱ", candidate.name)
        assertEquals(listOf(4,9,10,14,15,16), candidate.asCourse().scheduledWeeks())
        assertFalse(candidate.needsReview)
    }

    @Test fun missingFieldsAreRetainedForReviewWithoutInventingTimes() {
        val candidate = parse("""[{"name":"数学"}]""").candidates.single()
        assertEquals(0, candidate.dayOfWeek)
        assertEquals(0, candidate.duration)
        assertEquals(0, candidate.endWeek)
        assertTrue(candidate.needsReview)
        assertTrue(candidate.fieldErrors(settings).size >= 3)
    }

    @Test fun currentTimetableShareRoundTripsAllMeetingsButNoDatabaseIdentity() {
        val course = Course(id = 123, timetableId = 42, courseId = 17, name = "通信原理Ⅱ", teacher = "张三", location = "A301",
            dayOfWeek = 3, startSection = 3, duration = 2, startWeek = 4, endWeek = 16, selectedWeeksCsv = "4,9,10,14,15,16")
        val text = PasteTimetableImporter.share(listOf(course, course.copy(id = 124, dayOfWeek = 5)), "我的课表")
        val actual = parse(text.replace("\n", "\r\n")).candidates
        assertEquals(2, actual.size)
        assertEquals(course.scheduledWeeks(), actual.first().asCourse().scheduledWeeks())
        assertEquals(actual[0].groupKey, actual[1].groupKey)
        assertTrue(actual.all { it.targetCourseId == null && it.draftId == null })
        assertFalse(text.contains("timetableId"))
    }

    @Test fun tabSeparatedDetailsKeepRowsAndSeparateLocationColumns() {
        val result = parse("授课教师\t教室\t课程名\t教学时间\n张三\tA301\t高等数学\t1-16周 星期一 1-2节\n李四\tB302\t大学英语\t4、9-10、14-16周 星期三 3-4节")
        assertEquals(listOf("高等数学", "大学英语"), result.candidates.map { it.name })
        assertEquals(listOf("A301", "B302"), result.candidates.map { it.location })
        assertEquals(listOf(4,9,10,14,15,16), result.candidates.last().asCourse().scheduledWeeks())
    }

    @Test fun tabSeparatedExplicitColumnsAndCsvAreAccepted() {
        val rows = "课程名称\t星期\t开始节数\t结束节数\t周数\n数学\t周一\t1\t2\t1-16"
        assertEquals("数学", parse(rows).candidates.single().name)
        assertEquals(2, parse(rows.replace('\t', ',')).candidates.single().duration)
    }

    @Test fun emptyLeadingWeeklyHeaderCellKeepsColumnAlignment() {
        val result = parse("\t星期一\t星期二\n第1-2节\t高等数学 1-16周 1-2节\t大学英语 1-16周 1-2节")
        assertEquals(listOf("高等数学", "大学英语"), result.candidates.map { it.name })
        assertEquals(listOf(1,2), result.candidates.map { it.dayOfWeek })
    }

    @Test fun malformedOversizeAndUnsupportedInputsFailClearly() {
        val invalid = listOf("", "{\"courses\":", "{\"timetables\":[]}", "{\"courses\":[]}",
            "CourseTable课表分享 v2\n{}", "{\"courses\":[null]}", "{\"courses\":[{\"weeks\":[0,99]}]}",
            "{\"courses\":[{\"startWeek\":1,\"endWeek\":2147483647}]}", "无课表数据",
            "x".repeat(PasteTimetableImporter.MAX_CHARACTERS + 1), "[".repeat(40) + "]".repeat(40),
            "课程名称,星期,开始节数,结束节数,周数\n" + "名".repeat(257) + ",周一,1,2,1-16",
            "[" + List(ImportPolicy.MAX_OUTPUT_COURSES + 1) { "{}" }.joinToString(",") + "]")
        invalid.forEach { source ->
            try { parse(source); fail("Should reject ${source.take(50)}") }
            catch (e: ImportRejectedException) { assertTrue(e.message!!.isNotBlank()) }
        }
    }
}

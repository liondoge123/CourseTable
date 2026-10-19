package com.coursetable.app.importer

import com.coursetable.app.data.Course
import org.junit.Assert.*
import org.junit.Test

class TimetablePostProcessorTest {
    private val candidate = CandidateCourse("高等数孛", "???", "张兰", 1,1,2,1,16,0)
    private fun history(name: String = "高等数学") = Course(name = name, teacher = "张三", dayOfWeek = 1, startSection = 1, duration = 2, startWeek = 1, endWeek = 16)
    @Test fun uniqueHistoryCorrectionIsMarkedAndOriginalIsKept() {
        val actual = TimetablePostProcessor.apply(PdfParseResult(listOf(candidate), emptyList()),listOf(history())).candidates.single()
        assertEquals("高等数学", actual.name)
        assertEquals("张兰", actual.teacher)
        assertEquals(2, actual.alternatives.size)
        assertTrue(actual.needsReview)
        assertTrue(actual.issues.any { "地点" in it })
    }
    @Test fun ambiguousNamesAndDifferentNumeralsAreNeverAutoCorrected() {
        val input = candidate.copy(name = "大学英语Ⅱ", location = "西山操场", teacher = "")
        val result = TimetablePostProcessor.apply(PdfParseResult(listOf(input), emptyList()),listOf(history("大学英语Ⅰ")))
        assertEquals(input, result.candidates.single())
        val ambiguous = TimetablePostProcessor.apply(PdfParseResult(listOf(candidate), emptyList()),listOf(history(),history("高等数宇")))
        assertEquals(candidate.name, ambiguous.candidates.single().name)
    }
}

package com.coursetable.app.importer

import org.junit.Assert.*
import org.junit.Test

class OcrNameReviewTest {
    private fun prediction(text: String, confidence: Float = .99f) = TinyTextRecognizer.Prediction(text, confidence)
    private fun dictionary(text: String) = text.codePoints().toArray().toSet()

    @Test fun legalSingleWeekMustNotBeExpandedByAnotherRecognizer() {
        val result = OcrDecisions.choose("17周", prediction("1-17周"))
        assertEquals("17周", result.text)
        assertNotNull(result.alternative)
        assertEquals("3节", OcrDecisions.choose("3节", prediction("1-3节")).text)
    }

    @Test fun validFullImageTimeStaysSelectedAndCropDifferenceIsReviewable() {
        val original = TextToken(10f, 20f, 200f, 40f, "1-17周 星期一1-2节")
        val crop = original.copy(text = "17周 星期一1-2节")
        val reviewed = reviewOriginalTokens(listOf(original), listOf(crop)).single()
        assertEquals(original.text, reviewed.text)
        assertTrue(reviewed.needsReview)
        assertEquals(crop.text, reviewed.alternatives.single().candidate)
    }

    @Test fun agreeingCropsProposeCorrectionWithTheFullImageReadingRetained() {
        val original = TextToken(10f, 20f, 200f, 40f, "1-17周 星期一12节")
        val crop = original.copy(text = "1-17周 星期一1-2节")
        val reviewed = reviewOriginalTokens(listOf(original), listOf(crop), listOf(crop)).single()
        assertEquals(crop.text, reviewed.text)
        assertTrue(reviewed.needsReview)
        assertEquals(original.text, reviewed.alternatives.single().candidate)
    }

    @Test fun cropConsensusCannotShrinkOrExpandValidWeeksOrDropASectionRange() {
        for ((full, cropText) in listOf("1-17周 星期三3-4节" to "17周 星期三3-4节",
            "17周 星期三3-4节" to "1-17周 星期三3-4节", "1-17周 星期三1-2节" to "1-17周 星期三12节")) {
            val original = TextToken(10f, 20f, 200f, 40f, full)
            val crop = original.copy(text = cropText)
            val reviewed = reviewOriginalTokens(listOf(original), listOf(crop), listOf(crop)).single()
            assertEquals(full, reviewed.text)
            assertTrue(reviewed.needsReview)
        }
    }

    @Test fun highConfidenceWholeLineKeepsTheOtherReadingAsNamedAlternative() {
        val result = reviewedCellName("功專", listOf(prediction("功率")), listOf(""), dictionary("功專率"))
        assertEquals("功率", result.text)
        assertEquals("功專", result.alternative!!.candidate)
        assertEquals(OcrField.NAME, result.alternative.field)
    }

    @Test fun outOfDictionaryOriginalCannotBeOverwrittenEvenAtHighConfidence() {
        val result = reviewedCellName("课程𠮷Ⅱ", listOf(prediction("课程吉II")), listOf(""), dictionary("课程吉II"))
        assertEquals("课程𠮷Ⅱ", result.text)
        assertEquals("课程吉II", result.alternative!!.candidate)
    }

    @Test fun uncertainWrappedTailRequiresCorroboration() {
        val lines = listOf(prediction("电子（实"), prediction("验）", .71f))
        val dictionary = dictionary("电子（实验）电予")
        assertEquals("电予", reviewedCellName("电予", lines, listOf("", "检)"), dictionary).text)
        assertEquals("电子（实验）", reviewedCellName("电予", lines, listOf("", "验)"), dictionary).text)
    }

    @Test fun duplicateSchedulesKeepSourcesButDistinctRoomsWeeksAndTeachersRemain() {
        val a = CandidateCourse("数学", "A301", "张三", 1, 1, 2, 1, 16, 0, sourceRegion = "a")
        val b = a.copy(sourceRegion = "b", needsReview = true)
        val result = deduplicateOcrCandidates(listOf(a, b, a.copy(location = "B301"), a.copy(teacher = "李四"), a.copy(startWeek = 3)))
        assertEquals(4, result.size)
        val merged = result.first()
        assertTrue(merged.needsReview)
        assertEquals(setOf("a", "b"), merged.sourceRegions)
        assertEquals(listOf(b), merged.excludingRegion("a"))
        assertEquals(2, deduplicateOcrCandidates(listOf(a.copy(dayOfWeek = 0), b.copy(dayOfWeek = 0))).size)
    }
}

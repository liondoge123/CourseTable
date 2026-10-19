package com.coursetable.app.importer

import com.coursetable.app.data.Course
import org.junit.Assert.*
import org.junit.Test

class OcrEvaluationTest {
    private val expected = Course(name = "数学", dayOfWeek = 1, startSection = 1, duration = 2, startWeek = 1, endWeek = 16)
    private fun candidate(day: Int = 1, review: Boolean = false) = CandidateCourse(name = "数学", dayOfWeek = day, startSection = 1, duration = 2, startWeek = 1, endWeek = 16, weekType = 0, needsReview = review)

    @Test fun wrongDayAndDuplicateCannotPassAsWholeTableCorrect() {
        val score = evaluateOcrSchedules(listOf(expected), listOf(candidate(), candidate(review = true), candidate(day = 2)))
        assertEquals(1, score.correct)
        assertEquals(2, score.incorrect)
        assertEquals(0.5, score.reviewInterception!!, 0.0)
        assertFalse(score.wholeTableCorrect)
    }

    @Test fun missingRowsAreSeparateFromReviewInterception() {
        val score = evaluateOcrSchedules(listOf(expected), emptyList())
        assertEquals(1, score.missing)
        assertNull(score.reviewInterception)
        assertEquals(0.0, score.recall, 0.0)
        assertTrue(evaluateOcrSchedules(listOf(expected), listOf(candidate())).wholeTableCorrect)
    }
}

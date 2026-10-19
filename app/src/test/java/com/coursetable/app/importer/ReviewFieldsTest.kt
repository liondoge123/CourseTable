package com.coursetable.app.importer

import com.coursetable.app.data.AppSettings
import org.junit.Assert.*
import org.junit.Test

class ReviewFieldsTest {
    private val name = OcrAlternative("原课程", "新课程", "名称分歧", field = OcrField.NAME)
    private val time = OcrAlternative("17周", "1-17周", "时间分歧", field = OcrField.TIME)
    private fun record(fields: Set<ReviewField>?) = CandidateCourse("原课程", dayOfWeek = 1, startSection = 1, duration = 2,
        startWeek = 1, endWeek = 18, weekType = 0, needsReview = true, alternatives = listOf(name), issues = listOf(name.reason), reviewFields = fields)

    @Test fun nameOnlyConfirmationFinishesItsReviewWithoutAGenericWarning() {
        val confirmed = applyNameAlternative(record(setOf(ReviewField.NAME)), name)
        assertFalse(confirmed.needsReview)
        assertEquals(emptySet<ReviewField>(), confirmed.reviewFields)
        assertTrue(confirmed.reviewIssues(AppSettings()).isEmpty())
    }

    @Test fun timeReviewRemainsExplicitAfterNameIsConfirmed() {
        val original = record(setOf(ReviewField.NAME, ReviewField.TIME)).copy(alternatives = listOf(name, time), issues = listOf(name.reason, time.reason))
        val confirmed = applyNameAlternative(original, name)
        assertTrue(confirmed.needsReview)
        assertEquals(setOf(ReviewField.TIME), confirmed.reviewFields)
        assertTrue(ReviewField.TIME.prompt in confirmed.reviewIssues(AppSettings()))
        assertFalse(ReviewField.GENERAL.prompt in confirmed.reviewIssues(AppSettings()))
        assertEquals(listOf(time), confirmed.alternatives)
    }

    @Test fun legacyUnclassifiedWarningsAndInvalidFieldsCannotBeHidden() {
        assertTrue(applyNameAlternative(record(null), name).needsReview)
        val invalid = applyNameAlternative(record(setOf(ReviewField.NAME)).copy(dayOfWeek = 0), name)
        assertTrue(invalid.reviewIssues(AppSettings()).contains("请确认星期"))
    }

    @Test fun rerecognitionDoesNotReopenAnAlreadyConfirmedName() {
        val original = applyNameAlternative(record(setOf(ReviewField.NAME)), name)
        val recognized = record(setOf(ReviewField.NAME, ReviewField.TIME)).copy(alternatives = listOf(name, time), issues = listOf(name.reason, time.reason))
        val result = preserveReviewedFields(original, recognized)
        assertEquals(original.name, result.name)
        assertEquals(setOf(ReviewField.TIME), result.reviewFields)
        assertEquals(listOf(time), result.alternatives)
        assertFalse(name.reason in result.issues)
    }

    @Test fun mergingSourcesRetainsEveryPendingField() {
        val a = record(setOf(ReviewField.NAME)).copy(sourceRegion = "a")
        val b = a.copy(sourceRegion = "b", reviewFields = setOf(ReviewField.TIME))
        assertEquals(setOf(ReviewField.NAME, ReviewField.TIME), ImageTimetableOcr.mergeAcrossPages(listOf(a, b)).single().reviewFields)
    }

    @Test fun confirmedWeeksDoNotKeepTheRecognizersOldMissingWeekMessage() {
        val original = record(emptySet()).copy(name = "已确认", editedFields = setOf("weeks"), selectedWeeksCsv = "4,9,16")
        val recognized = original.copy(startWeek = 0, endWeek = 0, selectedWeeksCsv = "", needsReview = true,
            alternatives = emptyList(), issues = listOf("周次缺失，请选择实际上课周次"), reviewFields = setOf(ReviewField.WEEKS))
        val result = preserveReviewedFields(original, recognized)
        assertEquals(original.selectedWeeksCsv, result.selectedWeeksCsv)
        assertFalse(result.needsReview)
        assertTrue(result.reviewIssues(AppSettings()).isEmpty())
    }
}

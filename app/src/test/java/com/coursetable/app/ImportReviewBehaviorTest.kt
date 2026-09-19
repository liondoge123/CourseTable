package com.coursetable.app

import com.coursetable.app.data.AppSettings
import com.coursetable.app.importer.*
import com.coursetable.app.ui.*
import org.junit.Assert.*
import org.junit.Test

class ImportReviewBehaviorTest {
    private fun course(id: String, start: Int = 1, duration: Int = 2) = CandidateCourse(
        "课程$id", dayOfWeek = 1, startSection = start, duration = duration,
        startWeek = 1, endWeek = 18, weekType = 0, draftId = id)

    @Test fun reviewQueuePrioritizesInvalidFieldsAndConfirmationCannotHideErrors() {
        val advice = course("a").copy(needsReview = true)
        val invalid = course("b").copy(dayOfWeek = 0, needsReview = false)
        val valid = course("c")
        assertEquals(listOf(invalid, advice), reviewQueue(listOf(advice, valid, invalid), AppSettings()))
        assertTrue(invalid.fieldErrors(AppSettings()).isNotEmpty())
    }

    @Test fun reviewListGroupsEveryRecordByWeekdayAndSortsBySection() {
        val settings = AppSettings()
        val records = listOf(
            course("late", start = 5),
            course("tuesday", start = 2).copy(dayOfWeek = 2),
            course("early", start = 1),
            course("invalid-day").copy(dayOfWeek = 0),
            course("invalid-section").copy(startSection = settings.periods.size + 1)
        )

        val groups = groupedReviewCourses(records, settings)

        assertEquals(listOf("待修正", "周一", "周二"), groups.map { it.label })
        assertEquals(listOf("early", "late"), groups.first { it.label == "周一" }.courses.map { it.draftId })
        assertEquals(records.size, groups.sumOf { it.courses.size })
        assertEquals(setOf("invalid-day", "invalid-section"), groups.first().courses.map { it.draftId }.toSet())
    }

    @Test fun reviewListKeepsAllWeeksAndOverlappingRecords() {
        val records = (1..14).map { index ->
            course(index.toString(), start = (index % 6) + 1, duration = 2).copy(
                dayOfWeek = (index % 7) + 1,
                startWeek = if (index % 2 == 0) 1 else 9,
                endWeek = if (index % 2 == 0) 8 else 18,
                weekType = index % 3
            )
        }

        val grouped = groupedReviewCourses(records, AppSettings()).flatMap { it.courses }

        assertEquals(14, grouped.size)
        assertEquals(records.mapNotNull { it.draftId }.toSet(), grouped.mapNotNull { it.draftId }.toSet())
    }
}

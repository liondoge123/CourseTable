package com.coursetable.app.ui

import com.coursetable.app.data.AppSettings
import com.coursetable.app.importer.*
import org.junit.Assert.*
import org.junit.Test

class ImportReviewPresentationTest {
    private val settings = AppSettings()
    private val base = CandidateCourse("数学", dayOfWeek = 1, startSection = 1, duration = 2,
        startWeek = 1, endWeek = 16, weekType = 0)

    @Test fun cachedViewsMatchOriginalFiltersAndQueueAcrossModes() {
        val candidates = listOf(base.copy(draftId = "normal"),
            base.copy(name = "", draftId = "invalid", included = false),
            base.copy(draftId = "suggestion", needsReview = true),
            base.copy(draftId = "issue", issues = listOf("来源提示")),
            base.copy(draftId = "empty-review", needsReview = true, reviewFields = emptySet()),
            base.copy(draftId = "other-day", dayOfWeek = 3))
        for (mode in ImportMode.entries) {
            val rows = ScheduleAnalysis.analyze(candidates, listOf(base.asCourse().copy(id = 5, courseId = 5)), settings, mode)
            val view = ImportReviewPresentation(rows)
            assertEquals(candidates.filter { it.fieldErrors(settings).isNotEmpty() }, view.errors)
            assertEquals(candidates.filter { it.fieldErrors(settings).isEmpty() && it.needsReview }, view.suggestions)
            assertEquals(reviewQueue(candidates, settings), view.queue)
            assertEquals(rows.count { it.requiresTarget }, view.unresolved)
            assertEquals(rows.count { it.included }, view.included)
            assertEquals(rows.count { it.included && it.status == ImportStatus.INVALID }, view.selectedErrors)
            assertEquals(rows.any { it.included && it.conflicts.isNotEmpty() }, view.hasConflicts)
            rows.forEach { assertSame(it, view.rowFor(it.candidate)); assertSame(it, view.rowForDraft(it.candidate.draftId)) }
        }
    }

    @Test fun unassignedIdsAndEqualRecordsRemainAddressableInGroupedSnapshot() {
        val candidates = listOf(base, base.copy(), base.copy(dayOfWeek = 0), base.copy(startSection = Int.MAX_VALUE))
        val rows = ScheduleAnalysis.analyze(candidates, emptyList(), settings, ImportMode.NEW)
        val view = ImportReviewPresentation(rows)
        rows.forEach { assertSame(it, view.rowFor(it.candidate)) }
        assertNull(view.rowForDraft(null))
        assertEquals(listOf("待修正", "周一"), groupedReviewCourses(view.courses, settings).map { it.label })
        assertEquals(4, groupedReviewCourses(view.courses, settings).sumOf { it.courses.size })
        val repeated = ScheduleAnalysis.analyze(listOf(base, base), emptyList(), settings, ImportMode.NEW)
        assertSame(repeated.first(), ImportReviewPresentation(repeated).rowFor(base))
    }

    @Test fun groupingPreservesOriginalDayOrderAndStableSort() {
        val input = (0 until 250).map { i -> base.copy(name = "课程${i % 7}", draftId = "$i",
            dayOfWeek = i % 9, startSection = i % 15, duration = i % 4) }
        val order = compareBy<CandidateCourse>({ it.startSection }, { it.duration }, { it.name }, { it.draftId.orEmpty() })
        fun usable(c: CandidateCourse) = c.dayOfWeek in 1..7 && c.startSection >= 1 && c.duration > 0 &&
            c.startSection.toLong() + c.duration - 1 <= settings.periods.size
        val expected = buildList {
            input.filterNot(::usable).sortedWith(order).takeIf { it.isNotEmpty() }?.let { add(ReviewCourseGroup("待修正", it)) }
            listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日").forEachIndexed { day, label ->
                input.filter { usable(it) && it.dayOfWeek == day + 1 }.sortedWith(order).takeIf { it.isNotEmpty() }
                    ?.let { add(ReviewCourseGroup(label, it)) }
            }
        }
        assertEquals(expected, groupedReviewCourses(input, settings))
    }
}

package com.coursetable.app

import com.coursetable.app.data.Course
import com.coursetable.app.data.scheduledWeeks
import com.coursetable.app.ui.TimetableCourseIndex
import com.coursetable.app.ui.WeekFilter
import org.junit.Assert.*
import org.junit.Test

class TimetableCourseIndexTest {
    private val courses = listOf(
        Course(id = 1, name = "每周", dayOfWeek = 1, startSection = 1, duration = 2, startWeek = 1, endWeek = 18),
        Course(id = 2, name = "单周", dayOfWeek = 2, startSection = 3, duration = 2, startWeek = 2, endWeek = 16, weekType = 1),
        Course(id = 3, name = "双周", dayOfWeek = 3, startSection = 5, duration = 2, startWeek = 1, endWeek = 17, weekType = 2),
        Course(id = 4, name = "散周", dayOfWeek = 4, startSection = 1, duration = 2, startWeek = 1, endWeek = 18,
            selectedWeeksCsv = " 2, 4,9,9,14,60,invalid,0,61 "),
        Course(id = 5, name = "空周次", dayOfWeek = 5, startSection = 1, duration = 1, startWeek = 8, endWeek = 18,
            selectedWeeksCsv = "invalid,0,61"),
        Course(id = 6, name = "旧范围", dayOfWeek = 6, startSection = 1, duration = 1, startWeek = 12, endWeek = 3)
    )

    private fun reference(courses: List<Course>, week: Int, show: Boolean, filter: WeekFilter): List<Course> =
        courses.filter { course ->
            val matches = when (filter) {
                WeekFilter.ALL -> true
                WeekFilter.ODD -> course.scheduledWeeks().any { it % 2 == 1 }
                WeekFilter.EVEN -> course.scheduledWeeks().any { it % 2 == 0 }
            }
            matches && course.lastWeek() >= week && (show || course.visibleOnWeek(week))
        }

    @Test fun indexedWeeksPreserveFilteringOrderAndDimming() {
        val index = TimetableCourseIndex(courses)
        for (week in 1..60) for (show in listOf(false, true)) for (filter in WeekFilter.entries) {
            val result = index.forWeek(week, show, filter)
            assertEquals(reference(courses, week, show, filter), result.map { it.course })
            result.forEach { assertEquals(it.course.visibleOnWeek(week), it.scheduledThisWeek) }
        }
    }

    @Test fun replacementCourseDataBuildsANewIndexWithoutMutatingTheOldOne() {
        val old = TimetableCourseIndex(courses)
        val changed = courses.map { if (it.id == 4L) it.copy(selectedWeeksCsv = "3,5") else it }
        val next = TimetableCourseIndex(changed)
        assertTrue(old.forWeek(9, false, WeekFilter.ALL).any { it.course.id == 4L })
        assertFalse(next.forWeek(9, false, WeekFilter.ALL).any { it.course.id == 4L })
        assertTrue(next.forWeek(5, false, WeekFilter.ALL).any { it.course.id == 4L })
    }

    @Test fun repeatedWeekQueriesReportCachedAndReferenceCosts() {
        val many = (0 until 1000).map { courses[it % courses.size].copy(id = it.toLong()) }
        val started = System.nanoTime()
        val index = TimetableCourseIndex(many)
        val buildNanos = System.nanoTime() - started
        repeat(20) { index.forWeek(it % 18 + 1, true, WeekFilter.ODD); reference(many, it % 18 + 1, true, WeekFilter.ODD) }
        val baselineStart = System.nanoTime()
        val expected = (0 until 100).sumOf { reference(many, it % 18 + 1, true, WeekFilter.ODD).size }
        val baseline = System.nanoTime() - baselineStart
        val cachedStart = System.nanoTime()
        val actual = (0 until 100).sumOf { index.forWeek(it % 18 + 1, true, WeekFilter.ODD).size }
        val cached = System.nanoTime() - cachedStart
        assertEquals(expected, actual)
        println("TimetableCourseIndex courses=1000 queries=100 buildNs=$buildNanos referenceNs=$baseline cachedNs=$cached")
    }
}

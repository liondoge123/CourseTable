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
        }.let { candidates ->
            val accepted = candidates.filter { it.visibleOnWeek(week) }.toMutableList()
            candidates.filterNot { it.visibleOnWeek(week) }
                .sortedWith(compareBy<Course> { course ->
                    course.scheduledWeeks().firstOrNull { it >= week } ?: Int.MAX_VALUE
                }.thenBy { it.id })
                .forEach { course ->
                    if (accepted.none { other ->
                        course.dayOfWeek == other.dayOfWeek &&
                            course.startSection < other.startSection + other.duration &&
                            other.startSection < course.startSection + course.duration
                    }) accepted += course
                }
            candidates.filter { it in accepted }
        }

    private fun course(id: Long, start: Int = 3, duration: Int = 2, day: Int = 1,
        startWeek: Int = 1, endWeek: Int = 18, weekType: Int = 0, weeks: String = "") = Course(
        id = id, name = "课程$id", dayOfWeek = day, startSection = start, duration = duration,
        startWeek = startWeek, endWeek = endWeek, weekType = weekType, selectedWeeksCsv = weeks
    )

    @Test fun scheduledCourseWinsEveryOverlapShapeRegardlessOfInputOrder() {
        val current = course(10, endWeek = 7)
        // Exact match, enclosing, enclosed, overlaps the start, overlaps the end.
        for ((start, duration) in listOf(3 to 2, 2 to 4, 3 to 1, 2 to 2, 4 to 2)) {
            val inactive = course(20, start, duration, startWeek = 8)
            for (input in listOf(listOf(current, inactive), listOf(inactive, current))) {
                val result = TimetableCourseIndex(input).forWeek(1, true, WeekFilter.ALL)
                assertEquals("start=$start duration=$duration input=$input", listOf(current), result.map { it.course })
                assertTrue(result.single().scheduledThisWeek)
                assertEquals(listOf(inactive), TimetableCourseIndex(input).forWeek(8, true, WeekFilter.ALL)
                    .map { it.course })
            }
        }
    }

    @Test fun adjacentSectionsAndOtherDaysStillShowTheirFullDimmedCards() {
        val current = course(1)
        val before = course(2, start = 1, startWeek = 8)
        val after = course(3, start = 5, startWeek = 8)
        val otherDay = course(4, day = 2, startWeek = 8)
        val input = listOf(after, current, otherDay, before)
        val result = TimetableCourseIndex(input).forWeek(1, true, WeekFilter.ALL)
        assertEquals(input, result.map { it.course })
        assertEquals(listOf(false, true, false, false), result.map { it.scheduledThisWeek })
    }

    @Test fun inactiveOverlapUsesNextActualWeekAndRecomputesWhenTheWeekChanges() {
        val sparse = course(1, weeks = "1,12")
        val even = course(2, weekType = 2)
        val odd = course(3, startWeek = 7, weekType = 1)
        val index = TimetableCourseIndex(listOf(sparse, odd, even))
        val fifth = index.forWeek(5, true, WeekFilter.ALL)
        assertEquals(listOf(even), fifth.map { it.course })
        assertFalse(fifth.single().scheduledThisWeek)
        assertEquals(listOf(even), index.forWeek(6, true, WeekFilter.ALL).map { it.course })
        val seventh = index.forWeek(7, true, WeekFilter.ALL)
        assertEquals(listOf(odd), seventh.map { it.course })
        assertTrue(seventh.single().scheduledThisWeek)
        assertEquals(listOf(odd), index.forWeek(5, true, WeekFilter.ODD).map { it.course })
        assertEquals(listOf(even), index.forWeek(5, true, WeekFilter.EVEN).map { it.course })
        assertTrue(index.forWeek(5, false, WeekFilter.ALL).isEmpty())
    }

    @Test fun equalNextWeeksUseStableIdsAndSuppressedCardsDoNotReserveSpace() {
        val first = course(1, start = 1, startWeek = 8)
        val bridge = course(2, start = 2, startWeek = 8)
        val last = course(3, start = 3, startWeek = 8)
        for (input in listOf(listOf(first, bridge, last), listOf(last, bridge, first))) {
            val result = TimetableCourseIndex(input).forWeek(1, true, WeekFilter.ALL)
            assertEquals(input.filter { it != bridge }, result.map { it.course })
            assertTrue(result.none { it.scheduledThisWeek })
        }
    }

    @Test fun realCurrentWeekConflictsAreNotSilentlyHidden() {
        val current = course(1)
        val conflictingCurrent = course(2, start = 4)
        val inactive = course(3, startWeek = 8)
        val input = listOf(inactive, conflictingCurrent, current)
        assertEquals(listOf(conflictingCurrent, current),
            TimetableCourseIndex(input).forWeek(1, true, WeekFilter.ALL).map { it.course })
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

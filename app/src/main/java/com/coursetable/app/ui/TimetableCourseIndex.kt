package com.coursetable.app.ui

import com.coursetable.app.data.Course
import com.coursetable.app.data.scheduledWeeks

internal data class TimetableCourseItem(val course: Course, val scheduledThisWeek: Boolean)

/** Parse sparse week selections once per course-list update, rather than per page. */
internal class TimetableCourseIndex(courses: List<Course>) {
    private class Entry(val course: Course) {
        private val explicitWeeks: Set<Int>?
        val lastWeek: Int
        val hasOdd: Boolean
        val hasEven: Boolean

        init {
            val scheduled = course.scheduledWeeks()
            explicitWeeks = if (course.selectedWeeksCsv.isNotBlank()) scheduled.toHashSet() else null
            lastWeek = if (explicitWeeks != null) scheduled.lastOrNull() ?: course.startWeek else course.lastWeek()
            hasOdd = scheduled.any { it % 2 == 1 }
            hasEven = scheduled.any { it % 2 == 0 }
        }

        fun scheduledOn(week: Int): Boolean = explicitWeeks?.contains(week) ?: course.visibleOnWeek(week)
    }

    private val entries = courses.map(::Entry)

    fun forWeek(week: Int, showNonCurrentWeek: Boolean, filter: WeekFilter): List<TimetableCourseItem> = buildList {
        for (entry in entries) {
            if (entry.lastWeek < week) continue
            if (filter == WeekFilter.ODD && !entry.hasOdd || filter == WeekFilter.EVEN && !entry.hasEven) continue
            val scheduled = entry.scheduledOn(week)
            if (showNonCurrentWeek || scheduled) add(TimetableCourseItem(entry.course, scheduled))
        }
    }
}

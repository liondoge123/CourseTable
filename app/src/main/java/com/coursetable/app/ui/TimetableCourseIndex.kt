package com.coursetable.app.ui

import com.coursetable.app.data.Course
import com.coursetable.app.data.scheduledWeeks

internal data class TimetableCourseItem(val course: Course, val scheduledThisWeek: Boolean)

/** Parse sparse week selections once per course-list update, rather than per page. */
internal class TimetableCourseIndex(courses: List<Course>) {
    private class Entry(val course: Course) {
        private val weeks = course.scheduledWeeks()
        private val explicitWeeks: Set<Int>?
        val lastWeek: Int
        val hasOdd: Boolean
        val hasEven: Boolean

        init {
            explicitWeeks = if (course.selectedWeeksCsv.isNotBlank()) weeks.toHashSet() else null
            lastWeek = if (explicitWeeks != null) weeks.lastOrNull() ?: course.startWeek else course.lastWeek()
            hasOdd = weeks.any { it % 2 == 1 }
            hasEven = weeks.any { it % 2 == 0 }
        }

        fun scheduledOn(week: Int): Boolean = explicitWeeks?.contains(week) ?: course.visibleOnWeek(week)
        fun nextWeek(week: Int): Int = weeks.firstOrNull { it >= week } ?: Int.MAX_VALUE
    }

    private val entries = courses.map(::Entry)

    fun forWeek(week: Int, showNonCurrentWeek: Boolean, filter: WeekFilter): List<TimetableCourseItem> {
        val candidates = entries.filter { entry ->
            entry.lastWeek >= week && when (filter) {
                WeekFilter.ALL -> true
                WeekFilter.ODD -> entry.hasOdd
                WeekFilter.EVEN -> entry.hasEven
            }
        }
        val scheduled = candidates.filter { it.scheduledOn(week) }
        if (!showNonCurrentWeek) return scheduled.map { TimetableCourseItem(it.course, true) }

        // Reserve every scheduled card's full span before placing any dimmed card,
        // including partial overlaps and regardless of the database's drawing order.
        val selected = scheduled.toMutableSet()
        val occupied = mutableMapOf<Int, MutableSet<Int>>()
        fun sections(entry: Entry) = entry.course.run { startSection until startSection + duration }
        fun reserve(entry: Entry) {
            occupied.getOrPut(entry.course.dayOfWeek) { mutableSetOf() }.addAll(sections(entry))
        }
        scheduled.forEach(::reserve)

        // Among non-current courses, prefer the next actual teaching week (including
        // parity and sparse selections). Stable IDs break ties without card flicker.
        candidates.filterNot { it in selected }
            .sortedWith(compareBy<Entry> { it.nextWeek(week) }.thenBy { it.course.id })
            .forEach { entry ->
                val daySections = occupied[entry.course.dayOfWeek].orEmpty()
                if (sections(entry).none { it in daySections }) {
                    selected += entry
                    reserve(entry)
                }
            }

        // Keep the original order and course spans; suppressed cards return when
        // another displayed week makes them scheduled or leaves their span free.
        return candidates.filter { it in selected }
            .map { TimetableCourseItem(it.course, it.scheduledOn(week)) }
    }
}

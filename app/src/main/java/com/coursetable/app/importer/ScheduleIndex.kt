package com.coursetable.app.importer

import com.coursetable.app.data.Course
import com.coursetable.app.data.scheduledWeeks

internal class PreparedSchedule(val course: Course) {
    val name = normalizedName(course.name)
    val weeks = course.scheduledWeeks()
    val weekSet = weeks.toHashSet()
    val fingerprint = listOf(name, course.dayOfWeek, course.startSection, course.duration, weeks.joinToString(",")).joinToString("|")
    val end = course.startSection + course.duration - 1
}

/** Preserve insertion order, including equal-valued occurrences, while narrowing overlap queries. */
internal class ScheduleIndex {
    class Entry(val prepared: PreparedSchedule, val group: String?, val baseline: Boolean, val order: Int)
    private var nextOrder = 0
    private val fingerprints = mutableMapOf<String, MutableList<Entry>>()
    private val sections = mutableMapOf<Pair<Int, Int>, MutableList<Entry>>()
    private val wide = mutableMapOf<Int, MutableList<Entry>>()

    fun add(prepared: PreparedSchedule, group: String? = null, baseline: Boolean = false) {
        val entry = Entry(prepared, group, baseline, nextOrder++)
        fingerprints.getOrPut(prepared.fingerprint) { mutableListOf() }.add(entry)
        val course = prepared.course
        if (course.duration in 1..64 && prepared.end >= course.startSection) {
            for (section in course.startSection..prepared.end) sections.getOrPut(course.dayOfWeek to section) { mutableListOf() }.add(entry)
        } else wide.getOrPut(course.dayOfWeek) { mutableListOf() }.add(entry)
    }

    fun duplicate(prepared: PreparedSchedule, group: String?, target: Long?): Course? =
        fingerprints[prepared.fingerprint].orEmpty().firstOrNull {
            (it.baseline || group == null || it.group == group) && (target == null || it.prepared.course.courseId == target)
        }?.prepared?.course

    fun conflicts(prepared: PreparedSchedule): List<ScheduleConflict> {
        val course = prepared.course
        val pool = linkedSetOf<Entry>()
        if (course.duration in 1..64 && prepared.end >= course.startSection) {
            for (section in course.startSection..prepared.end) pool.addAll(sections[course.dayOfWeek to section].orEmpty())
        } else sections.filterKeys { it.first == course.dayOfWeek }.values.forEach { pool.addAll(it) }
        pool.addAll(wide[course.dayOfWeek].orEmpty())
        return pool.sortedBy { it.order }.mapNotNull { entry ->
            val other = entry.prepared
            if (course.id > 0 && course.id == other.course.id) return@mapNotNull null
            val start = maxOf(course.startSection, other.course.startSection)
            val end = minOf(prepared.end, other.end)
            if (start > end) return@mapNotNull null
            val weeks = prepared.weeks.filter { it in other.weekSet }.distinct().sorted()
            if (weeks.isEmpty()) null else ScheduleConflict(other.course, start..end, weeks)
        }
    }
}

package com.coursetable.app.ui

/** A connected set of overlapping occurrences on one day, after week filtering. */
internal data class TimetableCourseBlock(val items: List<TimetableCourseItem>) {
    val dayOfWeek: Int get() = items.first().course.dayOfWeek
    val startSection: Int get() = items.first().course.startSection
    val endSectionExclusive: Int get() = items.maxOf { it.course.startSection + it.course.duration }
    val duration: Int get() = endSectionExclusive - startSection
    val key: String get() = "$dayOfWeek:${items.map { it.course.id }.sorted().joinToString(",")}"
}

/** Strict interval overlap: adjacent lessons remain separate. Sorting makes ties stable. */
internal fun timetableCourseBlocks(items: List<TimetableCourseItem>): List<TimetableCourseBlock> = buildList {
    val sorted = items.sortedWith(compareBy<TimetableCourseItem> { it.course.dayOfWeek }
        .thenBy { it.course.startSection }
        .thenBy { it.course.startSection + it.course.duration }
        .thenBy { it.course.id })
    var group = mutableListOf<TimetableCourseItem>()
    var end = 0
    for (item in sorted) {
        val course = item.course
        if (group.isNotEmpty() && (course.dayOfWeek != group.first().course.dayOfWeek || course.startSection >= end)) {
            add(TimetableCourseBlock(group.toList()))
            group = mutableListOf()
        }
        if (group.isEmpty()) end = course.startSection + course.duration
        else end = maxOf(end, course.startSection + course.duration)
        group += item
    }
    if (group.isNotEmpty()) add(TimetableCourseBlock(group.toList()))
}
